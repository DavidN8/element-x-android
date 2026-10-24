/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

// Checks the bundled engine: replaying a history through applyAll leaves the same document as one apply per update, including
// updates waiting for missing predecessors, and both stop at the same malformed update. Given an earlier bundle, it also replays
// every case through both bundles, checks that they answer every call alike, and times the replays.
// Run from the repository root after rebuilding the bundle:
//   node features/matcha/impl/docs-engine/parity-check.mjs [earlier-engine.js]
import { strict as assert } from "node:assert";
import { readFileSync } from "node:fs";
import { performance } from "node:perf_hooks";
import vm from "node:vm";

const bundles = { new: readFileSync(new URL("../src/main/assets/matcha-docs/engine.js", import.meta.url), "utf8") };
if (process.argv[2]) bundles.old = readFileSync(process.argv[2], "utf8");

/** A fresh engine page; calls go through the same request/respond bridge as the WebView. */
function startEngine(source) {
    const requests = new Map();
    let answer;
    // Yjs draws client ids from crypto. Each open draws from a seed named after the document, so a document gets the same client
    // id in every bundle and run, and concurrent edits order the same way.
    let state = 0;
    const getRandomValues = (array) => {
        for (let i = 0; i < array.length; i++) array[i] = state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
        return array;
    };
    const page = {
        MatchaDocsBridge: { ready() {}, request: (id) => requests.get(id), respond: (id, json) => (answer = JSON.parse(json)) },
        document: {},
        crypto: { getRandomValues },
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
        if (op === "open") state = [...args.doc].reduce((hash, char) => Math.imul(hash, 31) + char.charCodeAt(0), 7) >>> 0;
        const id = ++nextId;
        requests.set(id, JSON.stringify({ op, ...args }));
        page.matchaDocs.handle(id);
        if (answer.error) throw new Error(answer.error);
        return answer.result;
    };
}

const engines = Object.fromEntries(Object.entries(bundles).map(([name, source]) => [name, startEngine(source)]));

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

/** Two writers of a catalog setting the same few entries at once. */
function catalogHistory(call, count) {
    call("open", { doc: "catalog-a" });
    call("open", { doc: "catalog-b" });
    const updates = [];
    for (let i = 0; i < count; i++) {
        const writer = random(2) ? "catalog-a" : "catalog-b";
        const key = `00000000-0000-4000-8000-00000000000${random(8)}`;
        updates.push(call("setEntry", { doc: writer, key, value: { title: `${writer} ${i}`, createdAt: i } }).update);
        if (random(3) === 0) call("apply", { doc: writer === "catalog-a" ? "catalog-b" : "catalog-a", update: updates.at(-1) });
    }
    return updates;
}

/** One writer typing a long note anywhere in it: 2,000 updates of 30 characters each. */
function longNote(call) {
    call("open", { doc: "long" });
    return Array.from({ length: 2000 }, (_, i) => {
        const insert = `line ${i} `.padEnd(29, "-") + "\n";
        return call("edit", { doc: "long", changes: [{ index: random(i * 30 + 1), remove: 0, insert }] }).update;
    });
}

// Local changes made on the text as it is when they run, so the same text gives the same change in every bundle.
const localEdit = (n) => (call, doc) => {
    const { text } = call("snapshot", { doc });
    const index = (n * 7919) % (text.length + 1);
    return call("edit", { doc, changes: [{ index, remove: Math.min(n % 3, text.length - index), insert: `local${n} ` }] });
};
const localEntry = (n) => (call, doc) =>
    call("setEntry", { doc, key: "22222222-2222-4222-8222-222222222222", value: { title: `Local ${n}`, createdAt: n } });

/** Runs the steps one at a time, past malformed updates; a step is an update or a local change. */
function replay(call, doc, steps) {
    call("open", { doc });
    const answers = steps.map((step) => (typeof step === "string" ? call("apply", { doc, update: step }) : step(call, doc)));
    return { answers, snapshot: call("snapshot", { doc }) };
}

/** Applies the updates in batches of a hundred like the Kotlin side, stopping at the first malformed one. */
function replayAll(call, doc, updates) {
    call("open", { doc });
    let applied = 0;
    for (let start = 0; start < updates.length && applied === start; start += 100) {
        applied += call("applyAll", { doc, updates: updates.slice(start, start + 100) }).applied;
    }
    return { applied, snapshot: call("snapshot", { doc }) };
}

/** Replays a case through every bundle, checks applyAll against apply in each, and checks that the bundles agree. */
function check(name, steps) {
    const updates = steps.every((step) => typeof step === "string") ? steps : null;
    const traces = {};
    const times = [];
    for (const [bundle, call] of Object.entries(engines)) {
        const one = replay(call, `${name}-one`, steps);
        const started = performance.now();
        const all = updates && replayAll(call, `${name}-all`, updates);
        times.push(`${bundle} ${Math.round(performance.now() - started)} ms`);
        if (all) {
            const firstInvalid = one.answers.findIndex((answer) => !answer.valid);
            assert.equal(all.applied, firstInvalid < 0 ? updates.length : firstInvalid, `${name} on ${bundle}`);
            const prefix = all.applied === updates.length ? one : replay(call, `${name}-prefix`, updates.slice(0, all.applied));
            assert.deepEqual(all.snapshot, prefix.snapshot, `${name} on ${bundle}`);
        }
        traces[bundle] = { one, all };
    }
    if (traces.old) assert.deepEqual(traces.new, traces.old, `${name}: the bundles disagree`);
    const { one, all } = traces.new;
    const valid = one.answers.filter((answer) => answer.valid !== false).length;
    const applyAll = all ? `, applyAll ${all.applied}/${updates.length} in ${times.join(", ")}` : "";
    console.log(`${name}: ${valid}/${steps.length} valid${applyAll}, missing=${one.snapshot.missing}, ${one.snapshot.text.length} chars`);
    return traces.new;
}

const call = engines.new;
const updates = history(call, 400);
const malformed = Buffer.from("garbage").toString("base64");
// A real update cut short in its delete set, which comes last: its insertions integrate before it throws.
const cutAt = updates.findIndex((update, i) => i >= 250 && Buffer.from(update, "base64").at(-1) !== 0);
const cut = Buffer.from(updates[cutAt], "base64").subarray(0, -1).toString("base64");
const shuffled = [...updates].sort(() => random(3) - 1);
const long = longNote(call);

check("ordered", updates);
check("shuffled", shuffled);
// Updates 20 to 59 come last, so the ones built on them wait until then.
const gap = check("gap", [...updates.slice(0, 20), ...updates.slice(60), ...updates.slice(20, 60)]);
assert(gap.one.answers.some((answer) => answer.missing) && !gap.one.snapshot.missing);
const broken = check("malformed", [...updates.slice(0, 150), malformed, ...updates.slice(150, cutAt), cut, ...updates.slice(cutAt)]);
assert.equal(broken.all.applied, 150);
assert.equal(broken.one.answers.filter((answer) => !answer.valid).length, 2);
check("local", shuffled.flatMap((update, i) => [update, ...(i % 5 ? [] : [localEdit(i)]), ...(i % 50 ? [] : [localEntry(i)])]));
check("catalog", [...catalogHistory(call, 300)].sort(() => random(3) - 1));
const longNoteTrace = check("long note", long);
assert.equal(longNoteTrace.one.snapshot.text.length, 60000);
console.log(`long note: ${Buffer.from(call("merge", { updates: long }).update, "base64").length} bytes once merged`);
console.log(bundles.old ? "applyAll matches apply, and the bundles agree" : "applyAll matches apply");
