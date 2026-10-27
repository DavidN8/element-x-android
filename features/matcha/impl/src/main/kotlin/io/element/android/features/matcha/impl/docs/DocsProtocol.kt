/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs

import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.REL_TYPE_REFERENCE
import io.element.android.features.matcha.impl.protocol.RawEvent
import io.element.android.features.matcha.impl.protocol.longOrNull
import io.element.android.features.matcha.impl.protocol.referenceTarget
import io.element.android.features.matcha.impl.protocol.stringOrNull
import io.element.android.libraries.core.data.tryOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64

/** One part of a document update as sent in a `com.matchahoneylatte.docs.update` event. */
data class DocFrame(
    val updateId: String,
    val kind: Kind,
    val chunks: Int,
    /** Null for a commit. */
    val chunk: Int?,
    /** Null for a commit. */
    val data: ByteArray?,
) {
    enum class Kind(val wire: String) { Update("update"), Chunk("chunk"), Commit("commit") }

    override fun equals(other: Any?) = other is DocFrame && updateId == other.updateId && kind == other.kind && chunks == other.chunks &&
        chunk == other.chunk && data contentEquals other.data

    override fun hashCode() = updateId.hashCode()
}

/** How a timeline event relates to one document. */
sealed interface DocFrameResult {
    /** Not a frame of this document, or not related to its anchor: skipped. */
    data object Unrelated : DocFrameResult

    /** A frame of this document that breaks the protocol, so the document can't be trusted. */
    data object Invalid : DocFrameResult

    data class Frame(val senderId: String, val frame: DocFrame) : DocFrameResult
}

/** The parts of one update received so far, from one sender (see [DocsProtocol.packetKey]). */
data class DocPacket(
    val count: Int,
    val parts: Map<Int, ByteArray> = emptyMap(),
    val isCommitted: Boolean = false,
) {
    val isComplete get() = isCommitted && parts.size == count

    /** This packet with [frame] added, or null when the frame contradicts it: another part count, or a part sent again differently. */
    fun plus(frame: DocFrame): DocPacket? {
        if (frame.chunks != count) return null
        var packet = if (frame.kind == DocFrame.Kind.Chunk) this else copy(isCommitted = true)
        if (frame.data != null && frame.chunk != null) {
            val previous = parts[frame.chunk]
            if (previous != null && !previous.contentEquals(frame.data)) return null
            packet = packet.copy(parts = parts + (frame.chunk to frame.data))
        }
        return packet
    }

    /** The parts joined in order; only for a complete packet. */
    fun update(): ByteArray = ByteArrayOutputStream().apply { for (index in 0 until count) write(parts.getValue(index)) }.toByteArray()
}

/** The update is larger than [MatchaConfig.DOCS_MAX_CHUNKS] frames. */
class DocTooLargeException : Exception("Document update too large")

/**
 * The Matcha documents wire protocol, identical to the web client's `documents.ts` and `documentCatalog.ts`: Yjs updates split
 * into frames related to the room's `m.room.create` event, a catalog document of names and the reserved `main` document.
 */
object DocsProtocol {
    const val MAIN_DOCUMENT_ID = "main"
    const val CATALOG_DOCUMENT_ID = "catalog"
    const val EVENT_ROOM_ENCRYPTED = "m.room.encrypted"
    const val EVENT_POWER_LEVELS = "m.room.power_levels"
    const val ENCRYPTION_ALGORITHM = "m.megolm.v1.aes-sha2"

    private const val VERSION = 1L
    private val documentIdPattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val updateIdPattern = Regex("^[a-zA-Z0-9._-]{1,128}$")
    private val base64Pattern = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
    private val maxDataLength = (MatchaConfig.DOCS_CHUNK_BYTES + 2) / 3 * 4

    /** A user-facing document: the reserved main document or a lowercase UUID. The catalog is never one. */
    fun isDocumentId(value: String) = value == MAIN_DOCUMENT_ID || documentIdPattern.matches(value)

    /** Surrounding whitespace is not part of a name. */
    fun isValidName(value: String) = value.trim().length in 1..MatchaConfig.DOCUMENT_NAME_MAX_LENGTH

    /** Updates are told apart by sender and update id, so a frame or an update sent twice is applied once. */
    fun packetKey(senderId: String, updateId: String) = "$senderId:$updateId"

    /** A frame this device can't decrypt (yet): its relation to [anchor] stays readable. */
    fun isUndecryptedFrame(event: RawEvent, anchor: String) = event.type == EVENT_ROOM_ENCRYPTED && event.content.referenceTarget() == anchor

    /** Splits an update into frames; several frames end with a commit that is sent only after every part. */
    fun frames(update: ByteArray, anchor: String, updateId: String, documentId: String): List<String> {
        val count = (update.size + MatchaConfig.DOCS_CHUNK_BYTES - 1) / MatchaConfig.DOCS_CHUNK_BYTES
        if (count < 1 || count > MatchaConfig.DOCS_MAX_CHUNKS) throw DocTooLargeException()
        fun frame(kind: DocFrame.Kind, chunk: Int?) = buildJsonObject {
            put("version", VERSION)
            put("doc", documentId)
            put("update_id", updateId)
            put("kind", kind.wire)
            put("chunks", count)
            if (chunk != null) {
                put("chunk", chunk)
                val end = minOf(update.size, (chunk + 1) * MatchaConfig.DOCS_CHUNK_BYTES)
                put("data", Base64.encode(update, chunk * MatchaConfig.DOCS_CHUNK_BYTES, end))
            }
            putJsonObject("m.relates_to") {
                put("rel_type", REL_TYPE_REFERENCE)
                put("event_id", anchor)
            }
        }.toString()
        val kind = if (count == 1) DocFrame.Kind.Update else DocFrame.Kind.Chunk
        return List(count) { frame(kind, it) } + if (count > 1) listOf(frame(DocFrame.Kind.Commit, null)) else emptyList()
    }

    /** Reads a frame of [documentId] related to [anchor], validating it exactly like the web client. */
    fun parseFrame(event: RawEvent, anchor: String, documentId: String): DocFrameResult {
        if (event.type != MatchaConfig.EVENT_DOCS_UPDATE || event.content.referenceTarget() != anchor) return DocFrameResult.Unrelated
        val content = event.content
        if (content.stringOrNull("doc") != documentId) return DocFrameResult.Unrelated
        val sender = event.senderId?.takeUnless { event.isRedacted } ?: return DocFrameResult.Invalid
        val frame = parseHeader(content)?.let { header -> parseBody(content, header) } ?: return DocFrameResult.Invalid
        return DocFrameResult.Frame(sender, frame)
    }

    /** The parts every frame has, as a commit; null when any is invalid. */
    private fun parseHeader(content: JsonObject): DocFrame? {
        val updateId = content.stringOrNull("update_id")?.takeIf(updateIdPattern::matches) ?: return null
        val chunks = content.longOrNull("chunks")?.takeIf { it in 1..MatchaConfig.DOCS_MAX_CHUNKS.toLong() } ?: return null
        val kind = DocFrame.Kind.entries.firstOrNull { it.wire == content.stringOrNull("kind") } ?: return null
        if (content.longOrNull("version") != VERSION) return null
        return DocFrame(updateId, kind, chunks.toInt(), chunk = null, data = null)
    }

    private fun parseBody(content: JsonObject, header: DocFrame): DocFrame? {
        val chunks = header.chunks
        if (header.kind == DocFrame.Kind.Commit) {
            return header.takeIf { !content.containsKey("data") && !content.containsKey("chunk") && chunks >= 2 }
        }
        val isSizeValid = when (header.kind) {
            DocFrame.Kind.Update -> chunks == 1
            else -> chunks >= 2
        }
        val chunk = content.longOrNull("chunk")?.takeIf { it in 0 until chunks && (header.kind != DocFrame.Kind.Update || it == 0L) }
        val data = content.stringOrNull("data")?.takeIf { it.isNotEmpty() && it.length <= maxDataLength && base64Pattern.matches(it) }
        if (!isSizeValid || chunk == null || data == null) return null
        val bytes = tryOrNull { Base64.decode(data) }?.takeIf { it.isNotEmpty() && it.size <= MatchaConfig.DOCS_CHUNK_BYTES } ?: return null
        return header.copy(chunk = chunk.toInt(), data = bytes)
    }
}
