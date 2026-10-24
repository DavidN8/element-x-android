# Matcha docs engine

`entry.js` is the Yjs engine behind Space Docs. It is bundled into `../src/main/assets/matcha-docs/engine.js` and runs in a
headless WebView (`docs/engine/DefaultYjsEngine.kt`) with no network or file access. Kotlin owns the Matrix transport; the
engine only holds the Yjs documents (`Y.Text` `"markdown"`, catalog `Y.Map` `"documents"`), checks and applies updates,
reports missing causal history and turns text edits into updates.

Each open document has a shadow `Y.Doc` that mirrors it. A remote update is applied to the shadow first: if that throws, the
update is malformed, the document never sees it and the shadow is copied again from the document (a throwing update can be half
integrated). Local edits reach the shadow through the document's `update` events. So checking an update costs about the size
of the update rather than of the whole document, at the price of holding each open document twice.

The bundle uses the `yjs` 13.6.33 and `lib0` 0.2.119 packages (MIT, Kevin Jahns) already installed in the Matcha web fork, so
both clients run the same CRDT code. Rebuild it from this repository's root after changing `entry.js` or upgrading the web fork's
`yjs` (the web repository is only read):

```sh
WEB=~/Code/mozart-dm-group/element-web/node_modules/.pnpm && NODE_PATH=$WEB/yjs@13.6.33/node_modules $WEB/@esbuild+darwin-arm64@0.27.4/node_modules/@esbuild/darwin-arm64/bin/esbuild features/matcha/impl/docs-engine/entry.js --bundle --minify --format=iife --platform=browser --target=es2017 --legal-comments=none --banner:js="/* Matcha docs engine. Bundles yjs 13.6.33 and lib0 0.2.119, MIT licensed, (c) Kevin Jahns. Source: features/matcha/impl/docs-engine. */" --outfile=features/matcha/impl/src/main/assets/matcha-docs/engine.js
```

Then check the bundle: `applyAll`, which replays a history in one bridge call, must leave the same document as one `apply` per
update, and stop at the same malformed update. Given the bundle as it was before the change, the check also replays every case
through both bundles (two writers in order, shuffled, with a gap filled later, with malformed updates, with local edits and
entries in between, a catalog, and a long note) and fails unless they answer every call alike. Two of the malformed updates are
forged so that whether Yjs accepts them depends on what the checking document holds: one follows a local edit, one follows what
a malformed update integrated before it threw. They fail the check if local edits stop reaching the shadow, or if the shadow is
not copied again after a malformed update. It needs only Node:

```sh
git show HEAD:features/matcha/impl/src/main/assets/matcha-docs/engine.js > /tmp/engine-before.js
node features/matcha/impl/docs-engine/parity-check.mjs /tmp/engine-before.js
```

It also times each `applyAll` replay. On the long note (2,000 updates growing to 60,000 characters, 93 kB once merged), Node 22
on an Apple M4 Max took about 2,000 ms when each update was checked on a fresh copy of the whole document, and takes about 20 ms
with the shadow.
