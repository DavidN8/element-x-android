/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.protocol

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ThreadMetadataProtocolTest {
    private val root = "\$root:example.org"

    private fun event(content: String, type: String = MatchaConfig.EVENT_THREAD_METADATA, redacted: Boolean = false): RawEvent {
        val unsigned = if (redacted) """, "unsigned": {"redacted_because": {}}""" else ""
        return RawEvent.parse("""{"type": "$type", "event_id": "${'$'}e", "sender": "@a:b", "origin_server_ts": 1, "content": $content$unsigned}""")!!
    }

    @Test
    fun `a patch carries only the fields it changes`() {
        val rename = event("""{"version": 1, "name": "  Ship beta  ", "m.relates_to": {"rel_type": "m.reference", "event_id": "$root"}}""")
        assertThat(ThreadMetadataProtocol.parsePatch(rename, root)).isEqualTo(ThreadMetadataPatch(name = "Ship beta", archived = null))
        val archive = event("""{"version": 1, "archived": true, "m.relates_to": {"rel_type": "m.reference", "event_id": "$root"}}""")
        assertThat(ThreadMetadataProtocol.parsePatch(archive, root)).isEqualTo(ThreadMetadataPatch(name = null, archived = true))
    }

    @Test
    fun `malformed, unrelated and redacted patches are ignored`() {
        val relation = """"m.relates_to": {"rel_type": "m.reference", "event_id": "$root"}"""
        listOf(
            event("""{"version": 2, "name": "x", $relation}"""),
            event("""{"version": 1, "name": "x", "m.relates_to": {"rel_type": "m.reference", "event_id": "${'$'}other"}}"""),
            event("""{"version": 1, "name": "x", "m.relates_to": {"rel_type": "m.thread", "event_id": "$root"}}"""),
            event("""{"version": 1, "name": "x", $relation}""", type = "m.room.message"),
            event("""{"version": 1, "name": "x", $relation}""", redacted = true),
            event("""{"version": 1, "name": "   ", $relation}"""),
            event("""{"version": 1, "name": "${"a".repeat(101)}", $relation}"""),
            event("""{"version": 1, "archived": "yes", $relation}"""),
            event("""{"version": 1, $relation}"""),
        ).forEach { assertThat(ThreadMetadataProtocol.parsePatch(it, root)).isNull() }
    }

    @Test
    fun `the latest value of each field wins independently`() {
        val metadata = ThreadMetadataProtocol.resolve(
            rootId = root,
            rootBody = "Original body",
            patchesNewestFirst = listOf(
                ThreadMetadataPatch(name = null, archived = true),
                ThreadMetadataPatch(name = "Newest name", archived = null),
                ThreadMetadataPatch(name = "Older name", archived = false),
            ),
        )
        assertThat(metadata).isEqualTo(ThreadMetadata(name = "Newest name", archived = true, archiveExplicit = true))
    }

    @Test
    fun `a restore is explicit while the default active state is not`() {
        val restored = ThreadMetadataProtocol.resolve(root, "Body", listOf(ThreadMetadataPatch(name = null, archived = false)))
        assertThat(restored.archiveExplicit).isTrue()
        val untouched = ThreadMetadataProtocol.resolve(root, "Body", emptyList())
        assertThat(untouched).isEqualTo(ThreadMetadata(name = "Body", archived = false, archiveExplicit = false))
    }

    @Test
    fun `the legacy state flag is only a fallback`() {
        assertThat(ThreadMetadataProtocol.resolve(root, "Body", emptyList(), legacyArchived = true).archived).isTrue()
        val patched = ThreadMetadataProtocol.resolve(root, "Body", listOf(ThreadMetadataPatch(null, false)), legacyArchived = true)
        assertThat(patched.archived).isFalse()
    }

    @Test
    fun `the fallback name collapses whitespace, truncates, and falls back to the root id`() {
        assertThat(ThreadMetadataProtocol.fallbackName("Line one\n\n  line   two", root)).isEqualTo("Line one line two")
        assertThat(ThreadMetadataProtocol.fallbackName("x".repeat(150), root)).hasLength(100)
        assertThat(ThreadMetadataProtocol.fallbackName("   ", root)).isEqualTo(root)
        assertThat(ThreadMetadataProtocol.fallbackName(null, root)).isEqualTo(root)
    }

    @Test
    fun `written patches round-trip through the reader`() {
        val content = ThreadMetadataProtocol.patchContent(root, name = " Renamed ")
        val patch = ThreadMetadataProtocol.parsePatch(event(content), root)
        assertThat(patch).isEqualTo(ThreadMetadataPatch(name = "Renamed", archived = null))
        assertThat(Json.parseToJsonElement(content).jsonObject.containsKey("archived")).isFalse()
    }

    @Test
    fun `the bootstrap reply is a hidden thread reply, not a metadata patch`() {
        val content = ThreadMetadataProtocol.bootstrapReplyContent(root, "Plan")
        val relation = Json.parseToJsonElement(content).jsonObject["m.relates_to"]!!.jsonObject
        assertThat(relation["rel_type"]!!.jsonPrimitive.content).isEqualTo("m.thread")
        assertThat(relation["event_id"]!!.jsonPrimitive.content).isEqualTo(root)
        assertThat(ThreadMetadataProtocol.parsePatch(event(content), root)).isNull()
    }
}
