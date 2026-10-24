/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.protocol

import io.element.android.appconfig.MatchaConfig
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** One shared change to a thread's name or archive state; absent fields are left as they were. */
data class ThreadMetadataPatch(
    val name: String?,
    val archived: Boolean?,
)

/** Where a thread's name comes from. */
enum class ThreadNameSource {
    /** A member named the thread. */
    Explicit,

    /** Nobody named it; the root message text stands in. */
    RootBody,

    /** Neither a name nor a readable root message exists; the name is the root event id. */
    EventId,
}

/** A thread's resolved shared metadata. */
data class ThreadMetadata(
    val name: String,
    val archived: Boolean,
    /** True when a member explicitly archived or restored the thread, as opposed to the default active state. */
    val archiveExplicit: Boolean,
    val nameSource: ThreadNameSource = ThreadNameSource.Explicit,
)

/**
 * Thread names and archive state, shared with the web client through `com.matchahoneylatte.thread.metadata` events.
 * Patches are encrypted timeline events related to the thread root; room state and redactions are never used.
 */
object ThreadMetadataProtocol {
    private val WHITESPACE = Regex("\\s+")

    fun isValidName(value: String?): Boolean = value != null && value.trim().length in 1..MatchaConfig.THREAD_NAME_MAX_LENGTH

    /** Reads a patch for [rootId], or null when [event] is not a valid patch for that thread. */
    fun parsePatch(event: RawEvent, rootId: String): ThreadMetadataPatch? {
        if (event.isRedacted || event.type != MatchaConfig.EVENT_THREAD_METADATA) return null
        val content = event.content
        if (content.longOrNull("version") != 1L || content.referenceTarget() != rootId) return null
        val name = content.stringOrNull("name")?.takeIf(::isValidName)?.trim()
        val archived = content.booleanOrNull("archived")
        if (name == null && archived == null) return null
        return ThreadMetadataPatch(name = name, archived = archived)
    }

    /**
     * The latest value of each field wins independently, so a rename never undoes a concurrent archive.
     *
     * @param rootId the thread root event id, the name of last resort.
     * @param rootBody the root message body, used as the name until someone names the thread.
     * @param patchesNewestFirst the valid patches for this thread in server order, newest first.
     * @param legacyArchived the archive flag of the legacy state event, read only as a fallback.
     */
    fun resolve(
        rootId: String,
        rootBody: String?,
        patchesNewestFirst: List<ThreadMetadataPatch>,
        legacyArchived: Boolean? = null,
    ): ThreadMetadata {
        val name = patchesNewestFirst.firstNotNullOfOrNull { it.name }
        val archived = patchesNewestFirst.firstNotNullOfOrNull { it.archived }
        val shownName = name ?: fallbackName(rootBody, rootId)
        return ThreadMetadata(
            name = shownName,
            archived = archived ?: (legacyArchived == true),
            archiveExplicit = archived != null || legacyArchived != null,
            nameSource = when {
                name != null -> ThreadNameSource.Explicit
                shownName == rootId -> ThreadNameSource.EventId
                else -> ThreadNameSource.RootBody
            },
        )
    }

    fun fallbackName(rootBody: String?, rootId: String): String {
        val collapsed = rootBody?.replace(WHITESPACE, " ")?.trim().orEmpty()
        return if (collapsed.isEmpty()) rootId else collapsed.take(MatchaConfig.THREAD_NAME_MAX_LENGTH)
    }

    /** Content of a patch event changing only the given fields. */
    fun patchContent(rootId: String, name: String? = null, archived: Boolean? = null): String {
        require(name != null || archived != null) { "A patch must change something" }
        require(name == null || isValidName(name)) { "Invalid thread name" }
        return buildJsonObject {
            put("version", 1)
            if (name != null) put("name", name.trim())
            if (archived != null) put("archived", archived)
            putJsonObject("m.relates_to") {
                put("rel_type", REL_TYPE_REFERENCE)
                put("event_id", rootId)
            }
        }.toString()
    }

    /**
     * Content of the hidden first reply that makes the homeserver treat a new root as a thread before anyone replies.
     * It is a thread reply, not a metadata patch: readers only consider `m.reference` relations.
     */
    fun bootstrapReplyContent(rootId: String, name: String): String {
        require(isValidName(name)) { "Invalid thread name" }
        return buildJsonObject {
            put("version", 1)
            put("name", name.trim())
            put("archived", false)
            putJsonObject("m.relates_to") {
                put("rel_type", REL_TYPE_THREAD)
                put("event_id", rootId)
                put("is_falling_back", true)
                putJsonObject("m.in_reply_to") {
                    put("event_id", rootId)
                }
            }
        }.toString()
    }
}
