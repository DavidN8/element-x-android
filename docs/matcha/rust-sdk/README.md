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

## Build the patched AAR (arm64 only, ~4 min cold on a 14-core M-series Mac)

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk --locked
git init matrix-rust-sdk && cd matrix-rust-sdk
git remote add origin https://github.com/matrix-org/matrix-rust-sdk.git
git fetch --depth 1 origin 56bc59112822e7eb25943a95e02502855cdeef2d   # the commit behind sdk-android 26.10.20
git checkout FETCH_HEAD && git apply /path/to/element-x-android/docs/matcha/rust-sdk/matcha-ffi-relations-json.patch
cd .. && git clone --depth 1 --branch sdk-v26.10.20 https://github.com/matrix-org/matrix-rust-components-kotlin.git
cd matrix-rust-components-kotlin
AAR=/path/to/element-x-android/libraries/rustsdk/matrix-rust-sdk.aar
NDK=$ANDROID_HOME/ndk/26.3.11579264
ANDROID_NDK_HOME=$NDK ./scripts/build.sh -r -p ../matrix-rust-sdk -m sdk -t aarch64-linux-android -o $AAR
# Strip the symbol table (58 MB) from the library inside the AAR.
(cd "$(mktemp -d)" && unzip -q $AAR jni/arm64-v8a/libmatrix_sdk_ffi.so \
  && $NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip --strip-unneeded jni/arm64-v8a/libmatrix_sdk_ffi.so \
  && zip -q $AAR jni/arm64-v8a/libmatrix_sdk_ffi.so)
```

`-r` builds with `small-release-stripped`, the profile the published SDK uses: no debug assertions or overflow checks,
and with the strip step the library is 65 MB instead of 162 MB. While iterating on the patch itself, drop `-r`: the
`reldev` profile rebuilds in about a minute, but keeps debug assertions on and makes the library about 40 MB bigger.

The strip step is needed because the AAR keeps the symbol table for the app to strip, and AGP only strips with the NDK
version it expects (28.2), so with only 26.3 installed it packages the library as it is. Cargo cannot strip earlier:
uniffi reads the symbol table to generate the Kotlin bindings. The unstripped library stays at
`matrix-rust-sdk/target/aarch64-linux-android/small-release-stripped/libmatrix_sdk_ffi.so` for `ndk-stack`.

With `libraries/rustsdk/matrix-rust-sdk.aar` present, debug builds use it automatically (see
`libraries/matrix/impl/build.gradle.kts`). The AAR only contains arm64-v8a, so build and install the arm64 APK only:

```bash
rm -f app/build/intermediates/apk/gplay/debug/*.apk
./gradlew :app:assembleGplayDebug -Pandroid.injected.build.abi=arm64-v8a
adb install -r app/build/intermediates/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk
```

`-Pandroid.injected.build.abi` skips the universal APK and the armeabi-v7a, x86 and x86_64 APKs (those three have no
`libmatrix_sdk_ffi.so` and cannot start). As in Android Studio, it writes the APK to `app/build/intermediates/apk/`, not
`app/build/outputs/apk/`, and an APK left there by an earlier build stays stale, so don't install from it. Deleting
the old APK first makes Gradle package it from scratch: packaging in place appends a changed library and leaves the old
copy behind as dead space, which made the APK 97 MB bigger after one AAR swap.

Release builds keep the published SDK until the method is upstream or the AAR is published for every ABI.
