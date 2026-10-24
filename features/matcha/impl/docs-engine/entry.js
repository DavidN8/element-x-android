/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

// The Yjs engine of Matcha docs, run in a headless WebView. Kotlin owns the Matrix transport and talks to it through the
// MatchaDocsBridge JavascriptInterface: it calls matchaDocs.handle(id), the engine pulls the request with
// MatchaDocsBridge.request(id) and answers with MatchaDocsBridge.respond(id, json). Documents live in memory only.
import * as Y from "yjs";
import { fromBase64, toBase64 } from "lib0/buffer";

const REMOTE = {};
// Each open document is a target, which the Kotlin side reads and edits, and a shadow mirroring it, on which remote updates are
// checked before they reach the target.
const docs = new Map();

function get(id) {
    const entry = docs.get(id);
    if (!entry) throw new Error("unknown_document");
    return entry;
}

function create() {
    const doc = new Y.Doc();
    doc.getText("markdown");
    doc.getMap("documents");
    return doc;
}

// ponytail: the shadow doubles the memory of each open document; fine for notes of a few hundred kB.
function openEntry() {
    const entry = { target: create(), shadow: create() };
    // Remote updates reach the shadow first, when `apply` checks them; every other change to the target follows here.
    entry.target.on("update", (update, origin) => {
        if (origin !== REMOTE) Y.applyUpdate(entry.shadow, update);
    });
    return entry;
}

function destroyEntry(entry) {
    entry?.target.destroy();
    entry?.shadow.destroy();
}

// Yjs queues valid updates whose causal predecessors are absent; that partial state must never look complete.
function isMissingUpdates(doc) {
    return doc.store.pendingStructs !== null || doc.store.pendingDs !== null;
}

// Runs a local change and returns the binary update it produced, or null when it changed nothing.
function capture(doc, change) {
    const updates = [];
    const listener = (update, origin) => {
        if (origin !== REMOTE) updates.push(update);
    };
    doc.on("update", listener);
    try {
        doc.transact(change);
    } finally {
        doc.off("update", listener);
    }
    return updates.length ? toBase64(Y.mergeUpdates(updates)) : null;
}

const operations = {
    open({ doc }) {
        destroyEntry(docs.get(doc));
        docs.set(doc, openEntry());
        return {};
    },
    close({ doc }) {
        destroyEntry(docs.get(doc));
        docs.delete(doc);
        return {};
    },
    // Applies a remote update after checking it on the shadow, so a malformed update never corrupts the document.
    apply({ doc, update }) {
        const entry = get(doc);
        const bytes = fromBase64(update);
        try {
            Y.applyUpdate(entry.shadow, bytes);
        } catch {
            // A malformed update can be half integrated before it throws: the shadow is copied again from the target.
            entry.shadow.destroy();
            entry.shadow = create();
            Y.applyUpdate(entry.shadow, Y.encodeStateAsUpdate(entry.target));
            return { valid: false };
        }
        Y.applyUpdate(entry.target, bytes, REMOTE);
        return { valid: true, missing: isMissingUpdates(entry.target) };
    },
    // Applies remote updates in order, each checked like `apply`, and stops at the first malformed one: one bridge call for many.
    applyAll({ doc, updates }) {
        let applied = 0;
        while (applied < updates.length && operations.apply({ doc, update: updates[applied] }).valid) applied++;
        return { applied };
    },
    snapshot({ doc }) {
        const { target } = get(doc);
        return {
            text: target.getText("markdown").toString(),
            entries: target.getMap("documents").toJSON(),
            missing: isMissingUpdates(target),
        };
    },
    // Changes are {index, remove, insert} against the current text and must not overlap.
    edit({ doc, changes }) {
        const { target } = get(doc);
        const text = target.getText("markdown");
        const ordered = [...changes].sort((a, b) => b.index - a.index);
        const update = capture(target, () => {
            for (const change of ordered) {
                if (change.remove > 0) text.delete(change.index, change.remove);
                if (change.insert) text.insert(change.index, change.insert);
            }
        });
        return { update };
    },
    setEntry({ doc, key, value }) {
        const { target } = get(doc);
        return { update: capture(target, () => target.getMap("documents").set(key, value)) };
    },
    merge({ updates }) {
        return { update: toBase64(Y.mergeUpdates(updates.map(fromBase64))) };
    },
};

window.matchaDocs = {
    handle(id) {
        let response;
        try {
            const request = JSON.parse(MatchaDocsBridge.request(id));
            const operation = operations[request.op];
            if (!operation) throw new Error("unknown_operation");
            response = { result: operation(request) };
        } catch (error) {
            response = { error: String((error && error.message) || error) };
        }
        MatchaDocsBridge.respond(id, JSON.stringify(response));
    },
};

MatchaDocsBridge.ready();
