# Matcha Rust SDK patch

The Rust SDK's timelines (sdk-android 26.10.20) drop custom message-like events, so Element X cannot read the Matcha
cards, thread names and document updates through a timeline. `matcha-ffi-relations-json.patch` adds two FFI methods:

- `Room.fetchRelationsJson(eventId, relationType, from, limit)` wraps the SDK's `Room::relations` (a homeserver request
  whose events the SDK decrypts) and returns each related event as raw JSON. That is the same read the Matcha web client
  does with `fetchRelations`.
- `Room.fetchStateEventContentJson(eventType, stateKey)` reads one state event's content from the homeserver
  (`GET /rooms/{roomId}/state/{eventType}/{stateKey}`), or `null` when the room has none. The sync only stores the state
  it asks for, so this is how the app learns whether a chat has a task board (`com.matchahoneylatte.kanban`) without
  reading its whole history.

Element X calls both by reflection (`libraries/matrix/impl/.../room/RawRelations.kt`), so the app still builds against the
published SDK. Without the patch the Matcha screens show a "history unavailable" warning instead of data, and boards are
found only through the stored state or the history the app reads. New events
still arrive live through the published `subscribeToMessageLikeEvents`.

## Build the patched AAR (arm64 only, ~25 min on an M-series Mac)

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk --locked
git init matrix-rust-sdk && cd matrix-rust-sdk
git remote add origin https://github.com/matrix-org/matrix-rust-sdk.git
git fetch --depth 1 origin 56bc59112822e7eb25943a95e02502855cdeef2d   # the commit behind sdk-android 26.10.20
git checkout FETCH_HEAD && git apply /path/to/element-x-android/docs/matcha/rust-sdk/matcha-ffi-relations-json.patch
cd .. && git clone --depth 1 --branch sdk-v26.10.20 https://github.com/matrix-org/matrix-rust-components-kotlin.git
cd matrix-rust-components-kotlin
ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264 ./scripts/build.sh -p ../matrix-rust-sdk -m sdk -t aarch64-linux-android \
  -o /path/to/element-x-android/libraries/rustsdk/matrix-rust-sdk.aar
```

With `libraries/rustsdk/matrix-rust-sdk.aar` present, debug builds use it automatically (see
`libraries/matrix/impl/build.gradle.kts`). The AAR only contains arm64-v8a, so install the arm64 APK. Release builds keep
the published SDK until the method is upstream or the AAR is published for every ABI.
