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
const docs = new Map();

function get(id) {
    const doc = docs.get(id);
    if (!doc) throw new Error("unknown_document");
    return doc;
}

function create() {
    const doc = new Y.Doc();
    doc.getText("markdown");
    doc.getMap("documents");
    return doc;
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
        docs.get(doc)?.destroy();
        docs.set(doc, create());
        return {};
    },
    close({ doc }) {
        docs.get(doc)?.destroy();
        docs.delete(doc);
        return {};
    },
    // Applies a remote update after checking it on a copy, so a malformed update never corrupts the document.
    apply({ doc, update }) {
        const target = get(doc);
        const bytes = fromBase64(update);
        const probe = create();
        try {
            Y.applyUpdate(probe, Y.encodeStateAsUpdate(target));
            Y.applyUpdate(probe, bytes);
        } catch {
            return { valid: false };
        } finally {
            probe.destroy();
        }
        Y.applyUpdate(target, bytes, REMOTE);
        return { valid: true, missing: isMissingUpdates(target) };
    },
    // Applies remote updates in order, each checked like `apply`, and stops at the first malformed one: one bridge call for many.
    applyAll({ doc, updates }) {
        let applied = 0;
        while (applied < updates.length && operations.apply({ doc, update: updates[applied] }).valid) applied++;
        return { applied };
    },
    snapshot({ doc }) {
        const target = get(doc);
        return {
            text: target.getText("markdown").toString(),
            entries: target.getMap("documents").toJSON(),
            missing: isMissingUpdates(target),
        };
    },
    // Changes are {index, remove, insert} against the current text and must not overlap.
    edit({ doc, changes }) {
        const target = get(doc);
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
        const target = get(doc);
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
