/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

// Checks the bundled engine: replaying a history through one applyAll call leaves the same document as one apply per update,
// including updates waiting for missing predecessors, and both stop at the same malformed update.
// Run from the repository root after rebuilding the bundle: node features/matcha/impl/docs-engine/parity-check.mjs
import { strict as assert } from "node:assert";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const source = readFileSync(new URL("../src/main/assets/matcha-docs/engine.js", import.meta.url), "utf8");

/** A fresh engine page; calls go through the same request/respond bridge as the WebView. */
function startEngine() {
    const requests = new Map();
    let answer;
    const page = {
        MatchaDocsBridge: { ready() {}, request: (id) => requests.get(id), respond: (id, json) => (answer = JSON.parse(json)) },
        document: {},
        crypto: globalThis.crypto,
        TextEncoder,
        TextDecoder,
        btoa,
        atob,
    };
    page.window = page;
    vm.createContext(page);
    vm.runInContext(source, page);
    let nextId = 0;
    return (op, args = {}) => {
        const id = ++nextId;
        requests.set(id, JSON.stringify({ op, ...args }));
        page.matchaDocs.handle(id);
        if (answer.error) throw new Error(answer.error);
        return answer.result;
    };
}

// Seeded edits; Yjs picks random client ids, so concurrent inserts may order differently from run to run.
let seed = 42;
const random = (n) => (seed = (seed * 1103515245 + 12345) % 2147483648) % n;

/** Two writers editing at once and sharing only some updates: the history has concurrent and causally dependent updates. */
function history(call, count) {
    call("open", { doc: "a" });
    call("open", { doc: "b" });
    const updates = [];
    for (let i = 0; i < count; i++) {
        const writer = random(2) ? "a" : "b";
        const { text } = call("snapshot", { doc: writer });
        const index = random(text.length + 1);
        const remove = Math.min(random(3), text.length - index);
        const { update } = call("edit", { doc: writer, changes: [{ index, remove, insert: `w${writer}${i} ` }] });
        if (!update) continue;
        updates.push(update);
        if (random(3) === 0) call("apply", { doc: writer === "a" ? "b" : "a", update });
    }
    const { update: entry } = call("setEntry", { doc: "a", key: "11111111-1111-4111-8111-111111111111", value: { title: "Plans", createdAt: 5 } });
    updates.push(entry);
    return updates;
}

function replayOneByOne(call, doc, updates) {
    call("open", { doc });
    let applied = 0;
    while (applied < updates.length && call("apply", { doc, update: updates[applied] }).valid) applied++;
    return { applied, snapshot: call("snapshot", { doc }) };
}

function replayAll(call, doc, updates) {
    call("open", { doc });
    const { applied } = call("applyAll", { doc, updates });
    return { applied, snapshot: call("snapshot", { doc }) };
}

const call = startEngine();
const updates = history(call, 400);
const malformed = Buffer.from("garbage").toString("base64");
// In order; shuffled, so updates wait for predecessors; with the newest one held back, so something is missing at the end; and
// with a malformed update in the middle.
const shuffled = [...updates].sort(() => random(3) - 1);
const cases = {
    ordered: updates,
    shuffled,
    missing: shuffled.filter((update) => update !== updates[0]),
    malformed: [...updates.slice(0, 150), malformed, ...updates.slice(150)],
};
for (const [name, list] of Object.entries(cases)) {
    const one = replayOneByOne(call, `${name}-one`, list);
    const all = replayAll(call, `${name}-all`, list);
    assert.deepEqual(all, one, name);
    console.log(`${name}: ${all.applied}/${list.length} applied, missing=${all.snapshot.missing}, ${all.snapshot.text.length} chars`);
}
assert.equal(replayAll(call, "check-malformed", cases.malformed).applied, 150);
assert.equal(replayAll(call, "check-missing", cases.missing).snapshot.missing, true);
assert.equal(replayAll(call, "check-ordered", updates).snapshot.missing, false);
console.log("applyAll matches apply");
