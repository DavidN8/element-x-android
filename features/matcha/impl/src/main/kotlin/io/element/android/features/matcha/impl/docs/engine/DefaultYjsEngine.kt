/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.engine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the bundled Yjs engine (`assets/matcha-docs`, built from the web client's `yjs` package) in a headless WebView that
 * exists only while documents are open. The page has no network or file access and can't navigate; it only ever runs the
 * bundled script, and it talks to Kotlin through the `MatchaDocsBridge` JavascriptInterface.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultYjsEngine(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : YjsEngine {
    // Only touched on the main thread.
    private var host: Host? = null
    private var openDocuments = 0
    private val nextDocumentId = AtomicInteger()

    override suspend fun open(): YjsDocument = withContext(dispatchers.main) {
        val host = host?.takeIf { it.isAlive } ?: Host(context).also { host = it }
        openDocuments++
        val document = WebViewYjsDocument(host, "doc-${nextDocumentId.incrementAndGet()}")
        try {
            host.call("open", document.args())
        } catch (e: YjsEngineException) {
            document.close()
            throw e
        }
        document
    }

    private fun release(host: Host, documentId: String) = sessionCoroutineScope.launch(dispatchers.main) {
        openDocuments--
        if (openDocuments <= 0 && this@DefaultYjsEngine.host === host) {
            openDocuments = 0
            this@DefaultYjsEngine.host = null
            host.destroy()
        } else if (host.isAlive) {
            runCatchingExceptions { host.call("close", buildJsonObject { put("doc", documentId) }) }
        }
    }

    private inner class WebViewYjsDocument(
        private val host: Host,
        private val documentId: String,
    ) : YjsDocument {
        private var isClosed = false

        fun args(builder: MutableMap<String, JsonElement>.() -> Unit = {}) = JsonObject(
            mutableMapOf<String, JsonElement>("doc" to JsonPrimitive(documentId)).apply(builder)
        )

        override suspend fun apply(update: ByteArray): Boolean {
            val result = call("apply", args { put("update", JsonPrimitive(Base64.encode(update))) })
            return (result["valid"] as? JsonPrimitive)?.booleanOrNull == true
        }

        override suspend fun applyAll(updates: List<ByteArray>): Int {
            var applied = 0
            // A replay crosses the bridge once per batch; batches keep each call well within CALL_TIMEOUT.
            for (batch in updates.chunked(APPLY_BATCH_SIZE)) {
                val result = call("applyAll", args { put("updates", JsonArray(batch.map { JsonPrimitive(Base64.encode(it)) })) })
                val count = (result["applied"] as? JsonPrimitive)?.intOrNull ?: 0
                applied += count
                if (count < batch.size) break
            }
            return applied
        }

        override suspend fun snapshot(): YjsSnapshot {
            val result = call("snapshot", args())
            return YjsSnapshot(
                text = (result["text"] as? JsonPrimitive)?.content.orEmpty(),
                entries = result["entries"] as? JsonObject ?: JsonObject(emptyMap()),
                isMissingUpdates = (result["missing"] as? JsonPrimitive)?.booleanOrNull != false,
            )
        }

        override suspend fun edit(changes: List<TextChange>): ByteArray? {
            val changesJson = JsonArray(
                changes.map { change ->
                    buildJsonObject {
                        put("index", change.index)
                        put("remove", change.remove)
                        put("insert", change.insert)
                    }
                }
            )
            return call("edit", args { put("changes", changesJson) }).update()
        }

        override suspend fun setEntry(key: String, value: JsonObject): ByteArray? {
            return call("setEntry", args {
                put("key", JsonPrimitive(key))
                put("value", value)
            }).update()
        }

        override suspend fun merge(updates: List<ByteArray>): ByteArray {
            val encoded = JsonArray(updates.map { JsonPrimitive(Base64.encode(it)) })
            return call("merge", args { put("updates", encoded) }).update() ?: throw YjsEngineException("Empty merge")
        }

        override fun close() {
            if (isClosed) return
            isClosed = true
            release(host, documentId)
        }

        private suspend fun call(operation: String, args: JsonObject): JsonObject {
            if (isClosed) throw YjsEngineException("Document closed")
            return withContext(dispatchers.main) { host.call(operation, args) }
        }

        private fun JsonObject.update(): ByteArray? = (this["update"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.let(Base64::decode)
    }

    /** The WebView and its bridge. Requests are answered in order by the page's single JavaScript thread. */
    @SuppressLint("SetJavaScriptEnabled")
    private class Host(context: Context) {
        private val requests = ConcurrentHashMap<Int, String>()
        private val responses = ConcurrentHashMap<Int, CompletableDeferred<String>>()
        private val nextRequestId = AtomicInteger()
        private val ready = CompletableDeferred<Unit>()

        @Volatile
        var isAlive = true
            private set

        private val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.domStorageEnabled = false
            settings.setGeolocationEnabled(false)
            addJavascriptInterface(Bridge(), BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    // Only the bundled page and script may load.
                    return if (url == ENGINE_URL ||
                        url == ENGINE_SCRIPT_URL) {
                            null
                        } else {
                            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Timber.w("The Matcha docs engine stopped")
                    destroy()
                    return true
                }
            }
            loadUrl(ENGINE_URL)
        }

        suspend fun call(operation: String, args: JsonObject): JsonObject {
            if (!isAlive) throw YjsEngineException("Engine stopped")
            try {
                withTimeout(READY_TIMEOUT) { ready.await() }
            } catch (e: TimeoutCancellationException) {
                throw YjsEngineException("Engine did not start", e)
            }
            val id = nextRequestId.incrementAndGet()
            val response = CompletableDeferred<String>()
            responses[id] = response
            requests[id] = JsonObject(args + ("op" to JsonPrimitive(operation))).toString()
            webView.evaluateJavascript("matchaDocs.handle($id)", null)
            val raw = try {
                withTimeout(CALL_TIMEOUT) { response.await() }
            } catch (e: TimeoutCancellationException) {
                throw YjsEngineException("Engine did not answer $operation", e)
            } finally {
                responses.remove(id)
                requests.remove(id)
            }
            val json = tryOrNull { Json.parseToJsonElement(raw).jsonObject } ?: throw YjsEngineException("Malformed answer to $operation")
            // The page's error message may quote its input, which can be document text: it is never logged.
            if (json.containsKey("error")) throw YjsEngineException("$operation failed")
            return json["result"] as? JsonObject ?: throw YjsEngineException("No result for $operation")
        }

        fun destroy() {
            if (!isAlive) return
            isAlive = false
            ready.completeExceptionally(YjsEngineException("Engine stopped"))
            responses.values.forEach { it.completeExceptionally(YjsEngineException("Engine stopped")) }
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.destroy()
        }

        /** Called by the page on its JavaBridge thread. */
        inner class Bridge {
            @JavascriptInterface
            fun request(id: Int): String = requests.remove(id) ?: "{}"

            @JavascriptInterface
            fun respond(id: Int, response: String) {
                responses[id]?.complete(response)
            }

            @JavascriptInterface
            fun ready() {
                ready.complete(Unit)
            }
        }
    }

    private companion object {
        const val BRIDGE_NAME = "MatchaDocsBridge"
        const val ENGINE_URL = "file:///android_asset/matcha-docs/engine.html"
        const val ENGINE_SCRIPT_URL = "file:///android_asset/matcha-docs/engine.js"
        const val APPLY_BATCH_SIZE = 100
        val READY_TIMEOUT = 10.seconds
        val CALL_TIMEOUT = 30.seconds
    }
}
