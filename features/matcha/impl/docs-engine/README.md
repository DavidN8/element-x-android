# Matcha docs engine

`entry.js` is the Yjs engine behind Space Docs. It is bundled into `../src/main/assets/matcha-docs/engine.js` and runs in a
headless WebView (`docs/engine/WebViewYjsEngine.kt`) with no network or file access. Kotlin owns the Matrix transport; the
engine only holds the Yjs documents (`Y.Text` `"markdown"`, catalog `Y.Map` `"documents"`), checks and applies updates,
reports missing causal history and turns text edits into updates.

The bundle uses the `yjs` 13.6.33 and `lib0` 0.2.119 packages (MIT, Kevin Jahns) already installed in the Matcha web fork, so
both clients run the same CRDT code. Rebuild it from this repository's root after changing `entry.js` or upgrading the web fork's
`yjs` (the web repository is only read):

```sh
WEB=~/Code/mozart-dm-group/element-web/node_modules/.pnpm && NODE_PATH=$WEB/yjs@13.6.33/node_modules $WEB/@esbuild+darwin-arm64@0.27.4/node_modules/@esbuild/darwin-arm64/bin/esbuild features/matcha/impl/docs-engine/entry.js --bundle --minify --format=iife --platform=browser --target=es2017 --legal-comments=none --banner:js="/* Matcha docs engine. Bundles yjs 13.6.33 and lib0 0.2.119, MIT licensed, (c) Kevin Jahns. Source: features/matcha/impl/docs-engine. */" --outfile=features/matcha/impl/src/main/assets/matcha-docs/engine.js
```
