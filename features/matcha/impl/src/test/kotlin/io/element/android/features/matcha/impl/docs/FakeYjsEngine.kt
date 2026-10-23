/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs

import io.element.android.features.matcha.impl.docs.engine.TextChange
import io.element.android.features.matcha.impl.docs.engine.TextDiff
import io.element.android.features.matcha.impl.docs.engine.YjsDocument
import io.element.android.features.matcha.impl.docs.engine.YjsEngine
import io.element.android.features.matcha.impl.docs.engine.YjsSnapshot
import io.element.android.libraries.core.data.tryOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A stand-in for the Yjs engine. An update is a JSON array of operations `{id, deps, text?, key?, value?}`: an operation applies
 * once every operation it depends on has, like Yjs's pending structs, and the last applied text wins.
 */
class FakeYjsEngine : YjsEngine {
    val documents = mutableListOf<FakeYjsDocument>()
    var openError: Exception? = null

    override suspend fun open(): YjsDocument {
        openError?.let { throw it }
        return FakeYjsDocument().also { documents += it }
    }

    companion object {
        fun operation(id: String, text: String? = null, deps: List<String> = emptyList(), key: String? = null, value: JsonObject? = null) = buildJsonObject {
            put("id", id)
            put("deps", JsonArray(deps.map(::JsonPrimitive)))
            text?.let { put("text", it) }
            key?.let { put("key", it) }
            value?.let { put("value", it) }
        }

        fun update(vararg operations: JsonObject): ByteArray = JsonArray(operations.toList()).toString().toByteArray()
    }
}

class FakeYjsDocument : YjsDocument {
    private var text = ""
    private val entries = LinkedHashMap<String, JsonObject>()
    private val appliedIds = mutableListOf<String>()
    private val pending = mutableListOf<JsonObject>()
    var isClosed = false
    var applyCount = 0
    private var localCount = 0

    override suspend fun apply(update: ByteArray): Boolean {
        val operations = tryOrNull { Json.parseToJsonElement(update.decodeToString()).jsonArray.map { it.jsonObject } } ?: return false
        applyCount++
        pending += operations
        var progress = true
        while (progress) {
            progress = false
            for (operation in pending.toList()) {
                val deps = operation["deps"]!!.jsonArray.map { it.jsonPrimitive.content }
                if (deps.all { it in appliedIds }) {
                    pending -= operation
                    integrate(operation)
                    progress = true
                }
            }
        }
        return true
    }

    private fun integrate(operation: JsonObject) {
        val id = operation["id"]!!.jsonPrimitive.content
        if (id in appliedIds) return
        appliedIds += id
        operation["text"]?.jsonPrimitive?.contentOrNull?.let { text = it }
        operation["key"]?.jsonPrimitive?.contentOrNull?.let { entries[it] = operation["value"]!!.jsonObject }
    }

    override suspend fun snapshot() = YjsSnapshot(text = text, entries = JsonObject(entries), isMissingUpdates = pending.isNotEmpty())

    override suspend fun edit(changes: List<TextChange>): ByteArray? {
        if (changes.isEmpty()) return null
        return local(FakeYjsEngine.operation(nextLocalId(), text = TextDiff.apply(text, changes), deps = appliedIds.takeLast(1)))
    }

    override suspend fun setEntry(key: String, value: JsonObject): ByteArray? {
        return local(FakeYjsEngine.operation(nextLocalId(), deps = appliedIds.takeLast(1), key = key, value = value))
    }

    override suspend fun merge(updates: List<ByteArray>): ByteArray {
        val operations = updates.flatMap { Json.parseToJsonElement(it.decodeToString()).jsonArray }
        return JsonArray(operations).toString().toByteArray()
    }

    override fun close() {
        isClosed = true
    }

    private fun nextLocalId() = "local-${localCount++}-${hashCode()}"

    private fun local(operation: JsonObject): ByteArray {
        integrate(operation)
        return FakeYjsEngine.update(operation)
    }
}
