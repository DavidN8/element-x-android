# Matcha on iPhone, iPad and desktop: platform plan

Written 2026-10-24 (Asia/Jakarta) as the handoff brief for the implementing agent, the same way `MATCHA_PORT_PLAN.md`
was. It is built from five research reports (the Element X iOS codebase, the Rust SDK on iOS, a feature-by-feature
iOS port map, desktop, and distribution/operations) plus a few checks made on this Mac while writing it.

Labels used below:
- **Verified** — checked against code, a binary or this machine (with the path or command).
- **Assumed** — stated by a source or inferred, not checked. Check before relying on it.
- **Estimate** — a number someone guessed. Effort is given as *focused agent-days* (a Claude agent working with
  review) and *engineer-weeks* (one human engineer). Each estimate states what it assumes.

Where the research reports disagreed, the resolution is written next to the item and collected in Appendix A.

## 0. Context and sources

| What | Where |
|---|---|
| Android fork (reference implementation for every client feature) | `~/Code/element-group/element-x-android`, branch `matcha` (133 commits past `develop`, all 2026-10-24), PR DavidN8/element-x-android#1. Matcha code in `features/matcha/{api,impl}`; ~13.9k production lines, ~6.1k test lines in `features/matcha/impl` |
| Android docs to read | `MATCHA_PORT_PLAN.md` (protocol §2, SDK audit §3), `WHATSAPP_DESIGN.md` (§6.1 supersedes, §6.2 decisions, §6.3 round 2), `docs/matcha/rust-sdk/README.md` + `matcha-ffi-relations-json.patch`, `docs/matcha/*_BRIEF.md`, `docs/matcha/INTEGRATION_NOTES.md` |
| Web fork (source of the wire protocol) | `~/Code/element-group/element-web`, branch `matcha-beta`, remote `bouyant-dev/element-web`, deployed at https://beta-element.matchahoneylatte.com. Read `bot.md`, `docs/matcha-beta.md` |
| Desktop shell | Already inside the web fork: `apps/desktop` (Element Desktop 1.12.32, electron 44.4.5, untouched by the fork). **Verified** (`apps/desktop/package.json`) |
| Upstream iOS app | `element-hq/element-x-ios`, researched at develop `2f9bac8` (2026-10-23). Fork base: tag `release/26.10.7` (commit `a11c16b602`), which pins SDK `exactVersion: 26.10.24`. **Verified** (`gh api repos/element-hq/element-x-ios/contents/project.yml?ref=release/26.10.7`) |
| Rust SDK pins | Android `org.matrix.rustcomponents:sdk-android` 26.10.20 = matrix-rust-sdk `56bc591128`. iOS `element-hq/matrix-rust-components-swift` exactVersion 26.10.24 = matrix-rust-sdk `8de215a0aa` (15 commits ahead, same main line). **Verified** (`project.yml:76-79` in the iOS clone, commit message of the 26.10.24 tag) |
| Homeserver, bot | `matchahoneylatte.com` (password login only: `m.login.password`, `auth_metadata` returns `M_UNRECOGNIZED`; Synapse 1.143.0, `org.matrix.msc4306: false`), bot `@matcha-reminder:<homeserver>` |
| Homeserver discovery (**verified** with curl, 2026-10-24) | `https://matchahoneylatte.com/.well-known/matrix/client` → 404 (nginx). `https://matrix.matchahoneylatte.com/.well-known/matrix/client` → `"base_url":"http://matrix.matchahoneylatte.com"` (plain HTTP). `http://matrix.matchahoneylatte.com/_matrix/client/versions` → 200 from Cloudflare, no redirect. The web config uses `server_name` `matchahoneylatte.com` with base URL `https://matrix.matchahoneylatte.com`. No `rtc_foci` in the well-known; `/_matrix/client/unstable/org.matrix.msc4143/rtc/transports` → 404. Fixed by David in I0a |
| Research clones (temporary; re-clone with `git clone --depth 1` if gone) | `/private/tmp/claude-501/-Users-david-Code-element-x-android/0f5da944-b9d2-4da4-ae37-c45da7570bf3/scratchpad/research/{ios-codebase,ios-sdk,feature-map,desktop,distribution}/` |

**This Mac (verified 2026-10-24 with `sw_vers`, `xcodebuild -version`, `xcrun simctl list runtimes`, `swift --version`,
`rustup target list --installed`):** Apple M4 Max, 36 GB RAM, macOS **15.3.1**, Xcode **16.4** (16F6) selected,
Swift 6.1.2, one simulator runtime (iOS 18.6), Rust targets `aarch64-apple-darwin`, `aarch64-linux-android`,
`x86_64-pc-windows-msvc`, `x86_64-unknown-linux-gnu`. The task text said "command-line tools only"; the first research
pass found Xcode 16.4 installed but not selected and no runtimes; by the time of writing it was selected with an iOS 18.6
runtime. Either way it **cannot build Element X iOS**: upstream needs Swift tools 6.2 (`compound-ios/Package.swift:1`,
`Package.swift:1`), the iOS 26 SDK, and CI pins `/Applications/Xcode_27.app` (`ci_scripts/ci_common.sh:34`). Apple
lists Xcode 27 as needing macOS Tahoe 26.6 or later (https://developer.apple.com/support/xcode/). App Store Connect has
accepted only Xcode 26+ / iOS 26 SDK builds since 28 April 2026.

**Devices:** David has a Galaxy Z Fold 7 (Android). Nothing says he owns an iPhone, an iPad or Apple Developer Program
membership.

## 1. Summary

### 1.1 What to build per platform

| Platform | What users get | Route | Builds from | One-off effort (estimate) |
|---|---|---|---|---|
| **iPhone** | Feature parity with the Android fork: named threads + chip bar, chat top bar, task board per chat with channel sharing, Tasks tab with list/table and People filter, reminders, Notes, Channels tab, WhatsApp-style look adapted to iOS | Fork **Element X iOS** (Swift/SwiftUI) and port the Android Matcha code by translation | `DavidN8/element-x-ios` (new), patched SDK from `DavidN8/matrix-rust-components-swift` (new) | 30–46 agent-days; 24–36 engineer-weeks |
| **iPad** | The same app, using the split view upstream already has: board as three columns, full-width tasks table, keyboard shortcuts, pointer | Same binary (`TARGETED_DEVICE_FAMILY = "1,2"`) | same | 2–3 agent-days; 1–2 weeks |
| **macOS** | Two options that cost little: (a) Element Desktop (Electron) with Matcha; (b) the iPad app on Apple Silicon Macs ("Designed for iPad") | (a) is the desktop app; (b) is left switched on as a free extra | (a) web fork `apps/desktop`; (b) the iOS build | (a) shared with desktop below; (b) 1–2 agent-days |
| **Windows** | Element Desktop with Matcha (Element Web look + Matcha features, not the WhatsApp style) | Electron from the web fork's `apps/desktop` | `bouyant-dev/element-web`, new workflow `matcha-desktop.yml` | 8–13 agent-days; 4–6 weeks, for all three desktop OSes together |
| **Linux** | Same, as tar.gz + deb (amd64, arm64), AppImage optional | Electron, same workflow | same | included above |

Rejected routes (with reasons in §6.1): Mac Catalyst, Tauri, Compose Multiplatform desktop from the Android code, a
Rust or Kotlin Multiplatform shared core, PWA as the main desktop app (kept as a stopgap).

### 1.2 Total effort

| Block | Agent-days | Engineer-weeks | Notes |
|---|---|---|---|
| Shared foundations (F1 conformance fixtures; the SDK pipeline is I2) | 2–3 | 1–1.5 | Can start now on this Mac |
| iPhone (I0–I14) | 30–46 | 24–36 | Human figure: senior iOS engineer new to Element X and Matrix. Report 1 gave 14–20 weeks for "an experienced iOS engineer" without saying whether that engineer knows Element X. Assumes the iOS 26 deployment target (decision 16); keeping iOS 18.5 adds about 1 agent-day / 1 week to I12 |
| iPad (P1) | 2–3 | 1–2 | P2 extras optional (+1–3) |
| Mac via the iPad app (M1) | 1–2 | 0.5–1 | Apple Silicon only; macOS 26+ with the iOS 26 target (15.5+ with 18.5) |
| Desktop core (D0–D7) | 8–13 | 4–6 | Linux extras optional (+0.5–4.5) |
| Operations not inside a phase (O1–O3) | 3–7.5 | 1.5–5 | Upper end includes an App Store listing and review rounds. Ad Hoc distribution is not included (+1–1.5, decision 4) |
| **Total one-off** | **46–75** | **32–51** | Calendar time is dominated by Apple gates and CI round trips, see §9 |
| Ongoing | iOS upstream merge 1–2 per month (includes the SDK rebuild); desktop 0.25 per release; ops 0.5 per month | 0.5–1 per month | |

A smaller first cut is possible: iPhone without Notes (I11) and without the re-skin (I12) is 22–35 agent-days.

Calibration (estimate): the Android port was 133 commits between 01:00 and 11:10 +07 on 2026-10-24, run as 5–6
parallel tracks, so about 7–9 agent-days of work. The per-phase sum (30–46 agent-days, 3.3–6.6× Android) is the figure
to plan with; the multiplier is only a cross-check. The translation phases I3–I12 alone come to 22–34.5 agent-days,
2.4–4.9× Android: iOS reuses the finished spec, copy and behaviour, but pays for no local build environment yet, Swift 6
strict concurrency (`SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`), Sourcery mocks and Git LFS snapshots. The rest (I0–I2,
I13–I14: 7.5–11.5) is work Android got from upstream's setup and Element's push and signing: the build environment,
rebrand and CI, the Apple SDK build and hosting, an own push gateway and TestFlight.

### 1.3 Order

1. **This week, no code (David):** the decisions in §1.4; I0a (upgrading this Mac to macOS Tahoe 26.6+ with Xcode 27,
   and fixing the homeserver's discovery and HTTPS, §0); I0b (Apple Developer Program enrolment, the NSE filtering
   entitlement request); a test iPhone; and running *Settings › Notifications › Troubleshoot notifications* on the Fold.
2. **In parallel, agents, no Apple tooling needed:** create `DavidN8/element-x-ios` (decision 19) and the two SDK forks,
   F1 fixtures, I4 protocol package (builds with Swift 6.1 today), I2 on hosted runners, desktop D0–D3, and the
   optional spike S1 (patched xcframework with the installed Xcode 16.4).
3. **iOS** I1 → I14, with I12 (re-skin) running as a parallel track from I1. I1–I12 need only I0a (simulator and CI);
   S4, S5, I13 and I14 need I0b and the iPhone.
4. **iPad** P1, then **Mac** M1 (switch on, check, ship to a TestFlight Mac group).
5. **Desktop** signing, hosting and QA (D4–D7) whenever the Apple account exists (macOS notarisation needs it).
6. **Operations:** O1 Android push independence before sharing Android builds with anyone; O2 App Store only if David
   chooses public distribution.

### 1.4 Decisions David must make first

| # | Decision | Recommended default | Blocks |
|---|---|---|---|
| 1 | Apple Developer Program: individual or organisation | **Individual** ($99/yr, approval in about 1–2 days). Organisation needs a legal entity, a D-U-N-S number, a domain email, and can take weeks | I0b, and through it S4, S5, I13, I14, D4 (push, TestFlight, devices, macOS notarisation) |
| 2 | Build machine | **Upgrade this Mac** to macOS Tahoe 26.6+, install Xcode 27 and an iOS 27 simulator runtime. CI also builds on GitHub-hosted macOS runners (free for public repos) | I0a, and through it every iOS phase from I1 |
| 3 | Test device | **Buy one used iPhone that runs the current iOS (27)**, not just the deployment target (about $150–300, estimate; check Apple's compatibility list before buying). iPad optional: simulators cover layout | S4, S5, S8, I13, I14 |
| 4 | Distribution of iOS v1 (testers) | **Internal TestFlight for David's own devices during the build-out; an external TestFlight group (email invites) for coworkers.** Internal testers must be App Store Connect users on David's team, which coworkers usually are not. External TestFlight needs Beta App Review on the first build of a group, so the rebrand and the §7.2 items it checks come first (+0.5–1 agent-day, in I14). Ad Hoc needs UDID collection, a regenerated profile for each new device and over-the-air hosting (+1–1.5 agent-days, not in the totals). App Store later, after the rebrand and an email to Element | I14, O2 |
| 5 | Product name and IDs | Name **Matcha** (already the default in `WHATSAPP_DESIGN.md` §6.2 decision 1). Bundle/app ID **`com.matchahoneylatte.matcha`**, app group `group.com.matchahoneylatte.matcha`, desktop protocol `com.matchahoneylatte.matcha`. David supplies the icon artwork | I1, D2 |
| 6 | Push gateway host | **Sygnal at `https://push.matchahoneylatte.com`** on the existing server (probably `prod-baremetal-2`, the production box in `~/.claude/CLAUDE.md`; confirm) | I13, O1 |
| 7 | Patched SDK hosting | **Public fork `DavidN8/matrix-rust-components-swift`** with plain version tags that sort above upstream's (`26.10.24` → `26.10.2401`, `26.10.2402`, …), built from a `matcha/<pin>` branch of a `DavidN8/matrix-rust-sdk` fork, and consumed by local path (§3.1). No pre-release tags: ElementCall's release-only range would exclude them. A private repo is awkward for SwiftPM binary targets | I2 |
| 8 | Patched SDK always, or a stock-SDK fallback | **Always patched, no fallback flag** (see §3.2) | I2, I3 |
| 9 | Notes on iOS v1 | **Full editing, like Android**, with the engine spike S4 first. Read-only preview + export if S4 fails | I11 |
| 10 | iOS look | **WhatsApp-for-iOS conventions** (large titles on root tabs, toolbar "+", inset separators, swipe actions, confirm top right in sheets, context menus), not a copy of the Android/Material spec. Tab order stays Chats · Tasks · Notes · Channels; Settings behind the avatar | I12 |
| 11 | Desktop look | **Element Web + Matcha as it is.** A WhatsApp-style web restyle is a separate project, not estimated here | — |
| 12 | Windows signing | **Unsigned for the private beta**; before anyone outside the team installs, choose SSL.com OV + eSigner (~$309/yr, near-zero integration work) or the free Microsoft Store (2–3 agent-days, Store-managed updates). Azure Artifact Signing is not offered in Indonesia | D5 |
| 13 | Desktop release cadence | **Manual dispatch, batched weekly**, not every `matcha-beta` push | D3 |
| 14 | Where the shared fixtures live | **`element-x-android/docs/matcha/fixtures/`**, next to the SDK patch and the Notes engine that iOS already consumes from this repo | F1 |
| 15 | Upstreaming the two FFI methods | **Yes, by David personally, after iOS I5** (matrix-rust-sdk's AI policy forbids agent-authored PRs, see §3.5). Not on the critical path | — |
| 16 | iOS deployment target | **Raise the fork to iOS 26** (`project.yml:15`, `deploymentTarget: iOS: '18.5'` → `'26.0'`, one line). Keeping 18.5 means two looks (Liquid Glass and pre-glass) for every re-skinned surface, and the pre-glass look gets no snapshot coverage because PreviewTests already run at iOS 26.0 (`PreviewTests/SupportingFiles/target.yml:45`). Cost of the default: iPhones that cannot run iOS 26 are left out, and the Mac iPad app needs macOS 26 (assumed from the 18.5 → macOS 15.5 pairing in §5) | I1, I12, M1 |
| 17 | Calls on iOS | **Hide Voice and Video call until the homeserver advertises a LiveKit focus** (today: no `rtc_foci`, MSC4143 transports 404, §0), and drop the `voip` and `audio` background modes while they are hidden (App Review 2.5.4; check first that voice-message playback does not rely on `audio`) | I0b, I6 |
| 18 | Export compliance (legal, David) | **Keep upstream's `ITSAppUsesNonExemptEncryption: false`, leave France out of availability until a French encryption declaration is filed, and David checks whether the US year-end self-classification report applies to him.** The answer goes with every TestFlight upload, internal included | I14, O2 |
| 19 | iOS repository: GitHub fork or standalone | **Standalone public repository `DavidN8/element-x-ios`** (duplicated per GitHub's "Duplicating a repository", which covers Git LFS objects), decided before the first snapshot recording. Per GitHub's "Collaboration with Git LFS", LFS pushes to a fork "count against the parent repository's bandwidth and storage quotas", so a fork's re-recorded snapshots (2,224 + 212 PNGs) would land on element-hq's quota. Measure the LFS size and David's quota first. The same applies to `DavidN8/element-x-android`, which is a GitHub fork: record no LFS screenshots there until it is detached or duplicated | I1, I12 |

## 2. Prerequisites and costs

### 2.1 What is needed

| Item | Needed for | Cost | Lead time | Who |
|---|---|---|---|---|
| Apple Developer Program, individual | APNs push, TestFlight, device installs beyond 7 days, App Store, Developer ID for macOS desktop, 25 Xcode Cloud hours/month | $99 per membership year | ~24–48 h after purchase (organisation: weeks, D-U-N-S) | David |
| macOS Tahoe 26.6+ on this Mac | Xcode 27 | free | ~half a day (download + install) | David (needs his Apple ID) |
| Xcode 27 + iOS 27 simulator runtime | building Element X iOS | free; ~20–40 GB disk (estimate; 312 GB free) | hours | David |
| Homeserver discovery over HTTPS (§0): serve `https://matchahoneylatte.com/.well-known/matrix/client` with `{"m.homeserver":{"base_url":"https://matrix.matchahoneylatte.com"}}` (and `Access-Control-Allow-Origin: *`); change the subdomain's well-known `base_url` to `https://`; turn on Cloudflare's "Always Use HTTPS" for `matrix.matchahoneylatte.com` | login by server name on every client; no passwords or access tokens over plain HTTP. App Transport Security blocks URLSession and WKWebView requests to an `http://` host (the fork's `target.yml` has no `NSAppTransportSecurity` exception, and none should be added) | free | under an hour | David |
| `rustup target add aarch64-apple-ios aarch64-apple-ios-sim` | patched XCFramework | free | minutes | agent |
| A real iPhone on the current iOS (27) | NSE decryption and memory limits, push, background behaviour, TestFlight, S4, S5, S8 | ~$150–300 used (estimate) | shopping | David |
| iPad (optional) | real-device iPad pass | ~$200–350 used (estimate) | shopping | David |
| App IDs `com.matchahoneylatte.matcha`, `.nse`, `.shareextension`; app group | the fork's entitlements | included | minutes | David, agent writes the list |
| APNs token keys (.p8), one sandbox and one production (Apple now offers environment-specific keys) | Sygnal | included | minutes | David |
| `com.apple.developer.usernotifications.filtering` entitlement | NSE silently dropping Matcha events and muted pushes; E2EE call ringing through the NSE (if calls come back, decision 17). Until it is granted, device and TestFlight provisioning fails while the NSE asks for it, so the fork ships it commented out (I1) and restores it on the grant (I13) | free, by request at https://developer.apple.com/contact/request/notification-service | **unknown**, Apple publishes none. Request on day one | David |
| Sygnal (`matrixdotorg/sygnal`, Apache-2.0, v0.15.1) behind TLS on a public address | iOS push (and Android once independent) | free on the existing server | ~1 agent-day | agent + David (DNS, secrets) |
| Firebase project (Android independence, O1) | FCM without Element's `vector-alpha` project | free | minutes | David |
| GitHub Actions | iOS CI, SDK releases, desktop builds | free on public repos (macOS included); private: $0.062/min macOS | — | — |
| Xcode Cloud (alternative CI/TestFlight) | archive + upload | 25 h/month included; 100 h for $49.99/month | — | — |
| Cloudflare R2 bucket + custom domain (e.g. `packages.matchahoneylatte.com`) | desktop downloads and update feeds | 10 GB-month free, then $0.015/GB-month, free egress | ~1 agent-day | agent + David |
| Windows code signing (optional, see decision 12) | SmartScreen and Smart App Control | SSL.com OV $129/yr + eSigner Tier 1 $180/yr (240 signings); Microsoft Store individual account free | SSL.com validation 3–5 days | David |
| Google Play (optional) | public Android | $25 once; new personal accounts need a 12-tester, 14-day closed test | 2+ weeks | David |
| Legal advice on AGPL + trademarks (optional) | App Store confidence | ~$500–2,000 (estimate) | — | David |

### 2.2 Year-1 cost

Minimum: $99 (Apple) + a used iPhone ≈ **$250–400**. Everything else above is free or optional. With Windows signing
add ~$309/yr. Hosting and CI stay at about $0 while the repos are public and three desktop releases are kept on R2.

### 2.3 Accounts and keys the agent must never hold

The APNs .p8 key and the FCM service-account JSON live only on the Sygnal host. The App Store Connect API key, the
Developer ID .p12, the eSigner credentials and the R2 keys live in GitHub *protected environments*, never exposed to
`pull_request` runs. Do not paste any of them into chat, commits or docs.

## 3. Shared foundations

### 3.1 The Rust SDK patch on iOS

Facts (verified by the research):
- The patch (`docs/matcha/rust-sdk/matcha-ffi-relations-json.patch`, `bindings/matrix-sdk-ffi/src/room/mod.rs` only)
  passes `git apply --check` against `8de215a0aa` ("Hunk #1 succeeded at 1455 (offset 22 lines)"). Every symbol it
  uses exists there (`RelationsOptions` in `crates/matrix-sdk/src/room/messages.rs:249`, `Relations` :358,
  `IncludeRelations::RelationsOfType` :242, FFI `RelationType` mod.rs:1463, ruma `get_state_event_for_key::v3`).
  Not compiled.
- Swift names follow UniFFI defaults (`uniffi.toml` has only a Kotlin section):
  `fetchRelationsJson(eventId: String, relationType: RelationType, from: String?, limit: UInt32) async throws -> RelationsJsonPage`,
  `fetchStateEventContentJson(eventType: String, stateKey: String) async throws -> String?`,
  `struct RelationsJsonPage { events: [String]; nextBatch: String? }`. No name mangling.
- Swift cannot look these up by reflection like `RawRelations.kt`. The generated Swift checks UniFFI API checksums
  against the binary at load (`matrix_sdk_ffi.swift:60955`), so the Swift sources and the xcframework must come from
  the same build or the app crashes at launch.
- The published package is a `binaryTarget` zip (108 MB for 26.10.24) for iOS and the iOS simulator only. Those two
  slices also cover iPad and "Designed for iPad" on Mac. No Mac Catalyst (`*-ios-macabi`) target exists in
  `xtask/src/swift.rs`.
- SwiftPM derives package identity from the last URL component (a local package's from its directory name). ElementCall
  **does** use the components package: `element-hq/element-call-ios` 0.1.0-rc.11 `Package.swift:44` (fetched with
  `gh api`) depends on `https://github.com/element-hq/matrix-rust-components-swift` `"26.10.15"..<"100.0.0"` for its
  `ElementCallMatrix` target, next to `matrix-rust-rtc` (:20). **Verified.** So pointing `project.yml`'s `url:` at
  `DavidN8/...` gives one identity two URLs. SwiftPM reports that as a conflicting identity (today a warning that its
  source says "will be escalated to an error", `Sources/PackageGraph/ModulesGraph+Loading.swift:463-516` at
  swift-package-manager `80c1fcb`), and which URL Xcode then fetches is not proven. A pre-release tag such as
  `26.10.24-matcha.1` may also fail ElementCall's release-only range: `Range.contains` excludes pre-releases unless a
  bound has one, and PubGrub's carve-out (`Term.swift:105-140`) is not proven to apply. Therefore:
  1. Tags are plain versions that sort above upstream's: `26.10.2401` for the first patched build of 26.10.24.
  2. The fork consumes the package **by local path**, the override upstream's own `build-sdk` relies on: it symlinks the
     SDK as `matrix-rust-components-swift` because that "allows the build to override the released components in both
     this project and its dependencies (such as ElementCall)" (`Tools/Sources/Commands/BuildSDK.swift:122-124`).
  3. Spike S3 proves resolution (one `matrix-rust-components-swift`, the fork's, and `ElementCallMatrix` compiling
     against it) before I2 is called done.
  Research reports 1 and 2 disagreed on this; report 1 was right (Appendix A). Keep the patch additive anyway.

Local development build (once Xcode 27 is installed; spike S1 tries it with Xcode 16.4 first):

```bash
rustup target add aarch64-apple-ios aarch64-apple-ios-sim
cd ~/Code   # element-x-ios fork is ~/Code/element-x-ios; build-sdk uses the sibling ../matrix-rust-sdk
git clone --depth 1 --branch matcha/26.10.24 https://github.com/DavidN8/matrix-rust-sdk.git
# before I2 creates that branch (S1): fetch 8de215a0aa from matrix-org/matrix-rust-sdk and git apply the patch instead
cd element-x-ios && git submodule deinit -f matrix-rust-components-swift && rmdir matrix-rust-components-swift
swift run tools build-sdk --simulator --profile reldbg   # or --device; no branch arg = use the checked-out tree
# afterwards: rm matrix-rust-components-swift && git submodule update --init   (back to the released fork package)
```

`build-sdk` (`Tools/Sources/Commands/BuildSDK.swift`) runs `cargo xtask swift build-framework`, symlinks the result as
`matrix-rust-components-swift`, rewrites `project.yml` to `path:`, re-runs xcodegen and regenerates the SDK mocks with
`local` (:143-147). Never commit the rewritten `project.yml` or the removed submodule. Build time (estimate): one target
25–30 min cold, two targets 35–60 min on the M4 Max, 5–15 min after a small FFI-only edit.

Hosted build for CI and devices (decision 7):
1. Fork `element-hq/matrix-rust-components-swift` → `DavidN8/matrix-rust-components-swift`. Change
   `packageRepo` in `Tools/Release/Sources/Release.swift:25` to the fork. Fork `matrix-org/matrix-rust-sdk` →
   `DavidN8/matrix-rust-sdk` and commit the patch on a branch `matcha/26.10.24` cut from `8de215a0aa`. Build from that
   branch, not from an uncommitted `git apply` on a detached checkout: `Release.swift` records `git rev-parse HEAD` and
   the branch from `git rev-parse --abbrev-ref HEAD` (swift-command-line-tools `Git.swift`), so a detached, patched tree
   would be released as `matrix-rust-sdk/HEAD 8de215a0` although its binaries are patched.
2. Add `.github/workflows/matcha-release.yml` to the components fork: `runs-on: xcode-27` (GitHub-hosted preview image;
   see S3 for the fallback), `permissions: contents: write`, check out `DavidN8/matrix-rust-sdk` branch
   `matcha/26.10.24` next to the repo, write `GITHUB_TOKEN` to `~/.netrc` for `api.github.com`, then
   `cd Tools/Release && swift run release --custom-version 26.10.2401`. One run builds
   `--release` (`small-release` profile) for `aarch64-apple-ios` + `aarch64-apple-ios-sim`, zips, checksums, copies the
   generated Swift, commits ("Bump to version 26.10.2401 (matrix-rust-sdk/matcha/26.10.24 <sha>)") and creates the
   GitHub release. Estimated 60–90 min on a hosted runner.
3. In the iOS fork: add the components fork as a git submodule at `matrix-rust-components-swift/`, checked out at the
   `26.10.2401` tag (drop that path from `.gitignore:41` first), and set `project.yml` to
   `MatrixRustSDK: path: matrix-rust-components-swift`, the same line `build-sdk` writes. The submodule commit is the
   pin; the binary still comes from the release URL inside its `Package.swift`. If S3 shows the one-line URL route
   (`url: https://github.com/DavidN8/matrix-rust-components-swift`, `exactVersion: 26.10.2401`) resolving to a single
   fork entry with no identity warning, use that instead and drop the submodule.
4. On every SDK bump the fork takes (at the monthly upstream merge, §7.5): cut `matcha/<new pin>` from the new commit and
   apply the patch there, run the workflow with the next tag, move the submodule. About 0.1–0.2 agent-days plus build
   time. Upstream touched `room/mod.rs` 13 times in three months, `messages.rs` twice in six; conflicts should be rare
   and small (estimate).

Keep one copy of the patch, in `docs/matcha/rust-sdk/`; each `matcha/<pin>` branch is that file applied at one commit.
Android's AAR and iOS's xcframework are built from the same file at their own commits.

### 3.2 Read path on iOS (the research disagreed)

- Report 2 said iOS could start on the published SDK, reading Matcha events through a filtered live timeline
  (`TimelineFilter.eventFilter(.include([...]))` + `debugInfo().originalJson`), because the Rust timeline maps
  included custom events to `MsgLikeKind::Other` (`matrix-sdk-ui/src/timeline/event_handler.rs:519-529`).
- Report 1 proposed that path as a compile-flag fallback; report 3 said always ship the patched SDK.
- Android tried exactly that path and it failed on the real server: commit `4394cbf8e3` says the filtered timeline
  "never saw cards, thread names or document updates on a real server: only the board's state pointer came through."
  Android now reads message-like events through `fetchRelatedEventsJson` per anchor
  (`MatchaRoomStore.kt:389,431`), new events through `subscribeToRawMessageLikeEvents` (:287), thread roots through the
  SDK thread list (:294-300), and state and undecryptable events through the filtered timeline (:248-250) plus
  `fetchStateEventContentJson` (:541).

**Resolution:** iOS copies Android's current read path and always builds against the patched SDK, with no
`MATCHA_PATCHED_SDK` flag. A flag would add a code path nobody tests, and without the patch the Matcha screens have no
data anyway. Spike S2 part (b) (optional) explains the disagreement if anyone needs a stock-SDK build later.

Also stale, to fix in a later PR (this plan only creates one file): `MATCHA_PORT_PLAN.md` §3.1 Path B
(`serde_json::to_string(&*self.0)` on FFI `TimelineEvent` cannot work, because ruma's `_Custom` content drops the
content; the shipped patch's `into_raw().json()` is right), and the rust-sdk README line "timelines drop custom
message-like events" (true under the default filter; on a real server the include filter did not help either).

### 3.3 Shared logic: fixtures, not a shared core

Options compared by report 2:

| Option | Covers | Cost | Verdict |
|---|---|---|---|
| (a) Separate ports (Kotlin, Swift, TypeScript) + one set of JSON fixtures | Android, iOS, web and desktop (desktop runs the web code) | fixtures 2–3 agent-days; Swift protocol port 1–2 | **Chosen** |
| (b) Rust `matcha-core` behind UniFFI (+ wasm) | Android, iOS; web unlikely to replace deployed TypeScript | 10–15 agent-days, a second binary per platform, release CI, Rust for every protocol change | Revisit only if shared logic grows into sync or state machines, or matrix-rust-sdk #7229 lands |
| (c) Kotlin Multiplatform protocol module | Android, iOS | 5–8 agent-days, Kotlin/Native needs Xcode, awkward Swift interop without SKIE | Rejected |

The protocol is small (Android `protocol/` 566 lines + `docs/DocsProtocol.kt` 137; web `apps/web/src/utils/matcha`
~1,330 lines with kanban parsing inside `viewmodels/room/KanbanBoardViewModel.ts`). The Notes CRDT is already shared as
one artifact: `features/matcha/impl/src/main/assets/matcha-docs/engine.js` (built from `docs-engine/entry.js` with yjs
13.6.33 and lib0 0.2.119). iOS reuses it unchanged (I11).

### 3.4 Phase F1 — protocol conformance fixtures across web, Android and iOS

Scope: one set of golden JSON cases that all three clients run.

```
element-x-android/docs/matcha/fixtures/
  README.md                 case format, how to add one, the rule "fixture first, then web, Android, iOS"
  thread-metadata/*.json    precedence of name/archived patches, invalid names, legacy archive state, fallback names
  kanban/*.json             newest revision wins, invalid revisions dropped, projection of threads to cards, card ids
                            ("thread-" + sha256hex), validation bounds, show_to_room, participants
  reminders/*.json          command text for in/at modes, DST-gap rejection, bounds, bot notice parsing
  docs-frames/*.json        frame validation, chunk/commit rules, dedupe keys
  SHA256SUMS
```

Each case: `{ "name": "...", "input": { "events": [...], "now": <ms>, "timeZone": "Asia/Jakarta", ... },
"expect": { ... } }` or `"error": "<code>"`. Take 60–100 cases from the web tests (`utils/matcha/*.test.ts`,
`KanbanBoardViewModel.test.ts`), the Android protocol tests (32 cases) and the wire samples in
`MatchaRoomFixtures.kt` and `DocsTestFixtures.kt`.

Runners:
- Android: a JUnit test in `features/matcha/impl/src/test` reading `docs/matcha/fixtures` by relative path (same repo,
  no copy).
- Web: copy to `apps/web/test/matcha-fixtures/` in the web fork, a Vitest runner, and a test that the files match
  their `SHA256SUMS`.
- iOS: copy to `MatchaProtocol/Tests/MatchaProtocolTests/Fixtures/` (the package comes from I4; the runner is added
  here, when F1 lands), a Swift Testing runner, the same checksum test.
- Drift check in web and iOS CI (non-blocking warning):
  `curl -fsSL https://raw.githubusercontent.com/DavidN8/element-x-android/matcha/docs/matcha/fixtures/SHA256SUMS | diff - <local copy>/SHA256SUMS`.

Done when: the same fixture set passes in all three repos' CI. Effort: 2–3 agent-days, 1–1.5 weeks. Moving the web's
kanban parsing out of `KanbanBoardViewModel.ts` is not included and not needed.

### 3.5 Upstreaming the two FFI methods (David, not an agent)

matrix-rust-sdk's `CONTRIBUTING.md` (AI policy, lines 95-125) requires AI use to be disclosed and forbids AI-generated
commit messages, PR text, comments and docs, and "autonomous-looking" agent use. PRs also need DCO sign-off and a
towncrier fragment in `bindings/matrix-sdk-ffi/changelog.d`. So David writes and owns these PRs; an agent may help with
code he reviews.

Two small PRs, resuming the closed threads #6430 ("makes sense!", closed for inactivity) and #6450 ("going in the right
direction"):
1. A server-side state read (`fetch_state_event_content_json`), or the `required_state` route of #6431 so the merged
   `state_events` / `subscribe_to_state_events` serve `com.matchahoneylatte.kanban` from sync.
2. Raw JSON for related events: the paginated `fetch_relations_json` as patched, or `raw_json()` on FFI
   `TimelineEvent` keeping its `Raw`. Explain that ruma's `_Custom` drops content. Include mock-server tests.

Plan for weeks of review latency. Delete both forks' patch steps once both land.

## 4. iOS (iPhone)

### 4.0 How the Android pieces map to Element X iOS

| Android | Element X iOS |
|---|---|
| Molecule Presenter / State / Event, `*StatePreviewParam` | `StateStoreViewModelV2` + `FooScreenModels.swift` (`ViewState`, `ViewAction`, `ViewModelAction`) + `FooScreenCoordinator`; previews via `PreviewProvider, TestablePreview` (Sourcery generates PreviewTests) |
| Appyx nodes, `NavTarget` | Flow coordinators with SwiftState `StateMachine` (`RoomFlowCoordinator` 1,709 lines, `RoomFlowCoordinatorStateMachine` 411) |
| Metro DI, `SessionScope` / `RoomScope` | Init injection; per-account container `UserSessionProtocol` (`Services/Session/UserSessionProtocol.swift:22-36`); no DI framework |
| `features/matcha/api` renderer interfaces | Not needed: one app target, compose views directly |
| `HomeNavigationBarItem`, `HomeView` | `enum HomeTab { case chats, spaces, search }` (`FlowCoordinators/UserSessionFlowCoordinator.swift:24`) and `TabDetails` (:87-147; `badgeCount` exists) |
| `LoggedInTwoPaneView` | Already there: each tab is a `NavigationSplitCoordinator`; at regular width on iOS 26+ a `TabRailView` replaces the tab bar (`NavigationTabCoordinator.swift:348-436`) |
| `FeatureFlags.MatchaWorkspaces`, `FeatureFlags.Threads` | `@UserPreference` in `Application/Settings/AppSettings.swift:362-414`, exposed in Labs through `LabsOptionsProtocol` (`LabsScreenModels.swift:37-43`). `threadsEnabled` defaults to false (:381) and is a client-builder option, so changing it clears the cache / needs a restart |
| `MatchaColors.kt`, `SemanticColorsLightDark.kt` edits | `AppHooks.compoundHook` → `CompoundColors.override(_:with:)` (`compound-ios/Sources/Compound/Colors/CompoundColors.swift:45`); compound-ios itself stays unedited |
| `temporary.xml` strings | `Untranslated.strings` / `.stringsdict` (iOS AGENTS.md, per report 3). Convert `%1$s` → `%1$@` with a one-off script; add the translator comment iOS AGENTS.md asks for |
| Timber | `MXLog`; never message bodies |
| Paparazzi screenshots | PreviewTests (2,224 PNGs, half iPhone SE 3rd gen, half iPad) + UITests (212) in Git LFS |
| detekt / Konsist | SwiftLint + SwiftFormat, run on every build and in git hooks (`swift run tools setup-project`) |

Code layout in the fork (keep upstream edits thin; everything Matcha in new directories):

```
MatchaProtocol/                         local SwiftPM package, swift-tools-version 6.1 (builds with this Mac's Swift 6.1.2 today),
  Sources/MatchaProtocol/               Foundation + CryptoKit only; referenced from project.yml packages like compound-ios
  Tests/MatchaProtocolTests/            ported protocol tests + FixtureTests + Fixtures/ (copied, §3.4)
ElementX/Sources/Matcha/
  MatchaConfig.swift                    event types, limits, bot localpart "matcha-reminder"
  Services/                             MatchaService, MatchaRoomStore, NoBoardAnswers, TaskWriter, ReminderService,
                                        DocsEngine (WKWebView host), RoomDocumentSession, DocumentCatalog
  Screens/                              ChatBar, TasksBoard, TaskEditor, TasksTab, RemindMe, Notes, NotesTab, Channels
ElementX/Resources/MatchaDocs/          engine.html, engine.js (copied unchanged from features/matcha/impl/src/main/assets/matcha-docs/)
UnitTests/Sources/Matcha/
```

Use `Tools/Scripts/createScreen.sh` (template `Tools/Scripts/Templates/SimpleScreenExample`) for new screens. For quick
agent verification without a session, the mock-screen harness `UI_TESTS_SCREEN=<UITestsScreenIdentifier>` launches
one of the mock screens (`ElementX/Sources/UITests/UITestsScreenIdentifier.swift`); add Matcha screens to it.

PR rules for the fork, copied from the Android fork: one PR per phase step, ≤500 production lines (tests can be larger),
sentence-style titles, commits with a title and a body. Follow `element-x-ios/AGENTS.md` for everything else.

### 4.1 Lessons from Android, and what iOS does instead

| What went wrong on Android | Commit | iOS |
|---|---|---|
| Custom message-like events showed as "Unsupported event" tiles | Phase 0 | Not needed for events the device can decrypt (one it cannot is still `m.room.encrypted`; see the UTD row in §8.1 and S2): `RoomTimelineItemFactory.swift:48-49` returns nil for `.other`, custom state renders nil (`RoomStateEventStringBuilder.swift:229`), room-list preview nil (`RoomEventStringBuilder.swift:60-61`). **But** `buildTimelineItemThreadSummary` (:823-858) shows `L10n.commonUnsupportedEvent` when a thread's latest event is the hidden `thread.metadata` reply — fix in I6 |
| The filtered timeline never delivered cards, names or doc frames on a real server | `4394cbf8e3` | Read through relations + live subscription from the start (§3.2) |
| The board pointer is never synced, so boards made elsewhere were missed and could be replaced | `06f5eb22cc`, `10fc6a6183`, `2292995aef` | `fetchStateEventContentJSON` on open, on retry and right before create. A board counts as missing only when the homeserver said so or history was read to the start |
| "No board" cache outlived a board created on this device; wall-clock aging | `940a8c4d18` | Forget the answer when this device creates a board; age it on `ContinuousClock` |
| Hidden bootstrap reply counted as a reply ("1 reply" on an unanswered thread) | `6f47bbaf37`, `a1f348966d` | Store marks threads with the hidden reply (≤1 page of `m.thread` relations); summary, thread list and task rows subtract it |
| Optimistic echoes of types the store never reads back (e.g. `!remind` text) stayed forever | `4302c4ae86` | Echo only the Matcha types the store reads back |
| Duplicate list keys crashed Compose; one hostile card id could crash the board for everyone | `99972930d4` | Namespace every `ForEach`/`List` id (`"task:" + id`, `"archived"`, `"load-older"`); keep cards in a dictionary by id |
| Writing defaults over a card revision not yet read | `2292995aef`, `99972930d4` | Projected cards say whether a revision describes them; never write unless it does |
| Notes editor rolled back fast keystrokes and broke word composition | `584d467a2e` | `EditEchoes`: ignore own edits coming back while newer ones are pending; keep `UITextView.markedTextRange` intact when merging remote changes |
| Doc stayed editable while an update missed its predecessors | `78311901fe` | The causal guard is part of published state: not ready, not editable, `IncompleteUpdate` until the update arrives |
| Doc anchor taken from timeline or frames; any member could redirect it | `defa9a1831` | Anchor at the `m.room.create` id from stored state; fall back only to the id a room v12 room id derives from; otherwise `AnchorError` |
| Notes setup timed out because power-level changes don't always update room info | `9e90604c13` | After setup, poll the stored state twice a second for up to 20 s |
| Markdown preview regexes were cubic / recursive (ANR from one line) | `e95af0e69d` | Port the linear-time `MarkdownParser`; parse off the main actor |
| A quick reminder option posted `!remind` on tap; DMs were offered a bot invite | `2deca1c863` | Only the confirm button sends; in a DM reminders exist only if the bot already joined |
| Threads stacked when opened from All threads | `0cbe18cf63` | A thread picked from All threads returns to General first and opens as the only screen above the chat |
| Read ticks waited for the reminder bot, which never sends receipts | `0cbe18cf63` | Exclude any member whose localpart is `matcha-reminder` from read-tick readers |
| Top-bar task badge counted tasks hidden from the user | `08cb3c9e62` | Badge uses the board's visibility rule |
| Undo/Retry died when the user left the Tasks tab | `10fc6a6183` | List-write tasks live until their banner is dismissed (≤1 min unshown), not in the screen's scope |
| Channels tab rebuilt every row on each room-list update | `e4ca235ec5` | Rebuild only rows whose rooms changed |
| Screenshots could not be recorded from a fork | `TWO_PANE_NOTES.md` | Keep upstream's label-triggered `record-snapshots.yml` (same-repo PRs, which every PR in `DavidN8/element-x-ios` is), in a standalone repository so the LFS objects land on David's quota (decision 19; I1, I12) |

iOS-only traps found by the research:
- `RoomThreadListServiceProxy` `updateItems()` calls `fatalError("This should never fail™")` if a thread root has no
  message details (:66-70) and returns nil latest-message details for `.other`. Matcha roots are `m.room.message`, but
  once the store reads every thread (I5) the Tasks tab can reach any root without details: I5 replaces the
  `fatalError` with skipping the item.
- `sendRaw` bypasses the send queue and fails offline (same as Android), and iOS suspends apps seconds after
  backgrounding: wrap Matcha writes and Notes flushes in `UIApplication.beginBackgroundTask`.
- Liquid Glass (iOS 26+) vs the pre-glass look: with the iOS 26 target (decision 16) there is one look. If David keeps
  18.5, every re-skinned surface has two variants behind `Compound.supportsGlass`, and the pre-glass one is checked by
  hand only (PreviewTests run at iOS 26.0).

### 4.2 Phases

Each phase lists scope, files, done-when (acceptance), tests and effort. Effort columns: agent-days / engineer-weeks
(estimates; the human figure is a senior iOS engineer new to Element X).

#### I0 — Prerequisites (David; agent writes and checks the list) — 0.5 / 0.3

Split in two, because every done-when from I1 to I12 runs on the simulator or hosted CI, which needs no paid membership
(upstream's own CI builds on hosted runners without Element's certificates).

**I0a — build machine and homeserver (gates I1) — 0.25 / 0.1.** macOS Tahoe 26.6+, Xcode 27 and an iOS 27 simulator
runtime on this Mac. The homeserver discovery fix in §2.1, after first checking which base URL the session on the Fold
uses today (for example turn on the `PrintLogsToLogcat` feature flag and look at the scheme of
`matrix.matchahoneylatte.com` in the SDK's request log lines, or read the server's access log). If it is `http://`, its
password and access token have travelled in cleartext, and its writes would break once HTTP redirects: set up key
backup, sign out, apply the fix, sign in again by server name, and change the password.
Done when: `xcodebuild -version` prints Xcode 27.x; `xcrun simctl list runtimes` lists iOS 27;
`curl -s https://matchahoneylatte.com/.well-known/matrix/client` returns the `https://` base URL; `curl -sI
http://matrix.matchahoneylatte.com/_matrix/client/versions` answers with a redirect to `https://`; the Fold's session is
on `https://`.

**I0b — Apple account (gates S4, S5, I13, I14 and D4) — 0.25 / 0.2.** Everything else in §2.1 marked David, plus the
App IDs with capabilities: app — Push Notifications, App Groups, Associated Domains (optional, see I1), Communication
Notifications, Keychain Sharing, background modes (fetch, processing, location; `voip` and `audio` only if calls come
back, decision 17; drop what is unused); NSE — App Groups, Keychain Sharing, and the filtering capability only after
Apple grants it.
Done when: the team ID is known; App IDs and the app group exist; the filtering-entitlement request is sent; two APNs
keys exist.

#### I1 — Fork, minimal rebrand, flags, CI — 2–3 / 1–1.5

1. The repository `DavidN8/element-x-ios` (public; AGPL requires the source anyway; standalone per decision 19) is
   created in week 0, before this phase, so I4 has a home. Base: `release/26.10.7` (pins SDK 26.10.24, §0). Branch
   `matcha` from it. Run `swift run tools setup-project` (git hooks, SwiftLint; needs Swift tools 6.2, so only after I0a).
   Raise the deployment target to iOS 26 (`project.yml:15`, decision 16).
2. `app.yml`: `APP_DISPLAY_NAME: Matcha`, `PRODUCTION_APP_NAME: Matcha`, `BASE_BUNDLE_IDENTIFIER:
   com.matchahoneylatte.matcha`, `APP_GROUP_IDENTIFIER: group.com.matchahoneylatte.matcha` (per `docs/FORKING.md`);
   `DEVELOPMENT_TEAM` is set when I0b gives the team ID (simulator builds and hosted CI do not need it). Bundle IDs
   become `<base>`, `<base>.nse`, `<base>.shareextension`.
3. `ElementX/SupportingFiles/target.yml:117-138` entitlements: remove `CLASSIC_APP_GROUP_IDENTIFIER` (`group.im.vector`)
   and the classic keychain group (they belong to Element's team and break provisioning); remove
   `applinks:element.io`, `app.element.io`, `matrix.to` and `webcredentials:*.element.io`.
   `// ponytail: no associated domains in v1 (homeserver is password-only); add matchahoneylatte.com + an
   apple-app-site-association file when permalinks or OIDC need them.`
   In `NSE/SupportingFiles/target.yml:65` comment out `com.apple.developer.usernotifications.filtering: true` with
   `# matcha: restore when Apple grants the filtering entitlement (I13); until then device and TestFlight provisioning
   fails with it`.
4. `AppSettings.swift`: replace `websiteURL`, `privacyURL`, `acceptableUseURL`, `copyrightURL`, `logoURL`,
   `oAuthRedirectURL` (:194, `https://element.io/oauth/ios/<bundle>`) with matchahoneylatte.com pages (David publishes
   privacy, terms, support and source pages). `pushGatewayBaseURL` waits for I13. Blank Element Call's hard-coded
   PostHog key and Sentry DSN; leave the `Components/Secrets/Secrets.pkl` environment variables unset. Rename
   `BGTaskSchedulerPermittedIdentifiers` (`io.element.elementx.background.*`) to the new base.
5. Flags: `threadsEnabled` default `true` (:381-382); new `matchaWorkspacesEnabled` default `true` in the Feature Flags
   section and in `LabsOptionsProtocol`. Thread subscriptions stay off, as on Android
   (`RustMatrixClientFactory.kt:188` passes `threadSubscriptions = false`; the homeserver reports
   `org.matrix.msc4306: false`): in `ClientFactory.swift:122` pass `.threadsEnabled(enabled: threadsEnabled,
   threadSubscriptions: false)`. Hide the Labs Threads row (`LabsScreenModels.swift:38`) while Matcha is on, because
   turning threads off breaks the chip bar.
6. Icon and Element illustrations replaced with David's artwork (trademark, §7.6). App name never contains "Element".
7. CI: keep `.github/workflows/unit-tests.yml` (`swift run -q tools ci unit-tests`) and upstream's
   `record-snapshots.yml` (a `record-snapshots` PR label, same-repo PRs only, runs `swift run -q tools ci preview-tests
   --record` and commits the PNGs through Git LFS); disable the rest. Upstream runs on `runs-on: xcode-27`; per GitHub's
   runner-images readme this is a hosted preview image (report 1 thought it was a self-hosted label; resolved by spike
   S3). Remove the Codecov and secrets steps. No recording before decision 19 is settled.

Done when: the fork builds and runs on the iOS 27 simulator with the new IDs; at the server picker the string typed is
exactly `matchahoneylatte.com` (the server name, not the `matrix.` subdomain), discovery finds the `https://` base URL
and the password login succeeds; Labs shows the Matcha flag and no Threads row; the unit-test workflow is green in the
fork.
Tests: upstream's unit tests unchanged and green.

#### I2 — Patched SDK package — 2–2.5 / 0.75–1.25

§3.1 hosted build, steps 1–3. It builds on hosted `xcode-27` and never needs local Xcode, so it can start as soon as the
forks exist (week 0); S1 is not a prerequisite. Regenerate the SDK mocks (`Components/SDKMocks`) from the patched Swift
sources and commit them. `Tools/Sources/Commands/GenerateSDKMocks.swift:15` hard-codes
`raw.githubusercontent.com/element-hq/matrix-rust-components-swift/%@/...`, so `generate-sdk-mocks 26.10.2401` would
404: change that format string to `DavidN8/matrix-rust-components-swift` (a one-line fork edit), or run it with
`local` right after a local `build-sdk` (which reads `../matrix-rust-sdk/bindings/apple/generated/swift`).

Done when: S3 has proved package resolution: `xcodebuild -resolvePackageDependencies` succeeds, the graph holds one
`matrix-rust-components-swift` (the fork's `26.10.2401`, by path or by URL per §3.1 step 3) with no conflicting-identity
warning, and `Package.resolved` has no `element-hq/matrix-rust-components-swift` entry; `ElementCallMatrix` compiles
against it; a debug build logs the number of `m.reference` relations `fetchRelationsJson` returns for a web board root
in a test room; `fetchStateEventContentJson("com.matchahoneylatte.kanban", "")` returns that room's pointer.

#### I3 — SDK proxy layer and config — 1–1.5 / 0.5

Files: `ElementX/Sources/Services/Room/RoomProxyProtocol.swift` (`JoinedRoomProxyProtocol`, :65-185),
`JoinedRoomProxy.swift`, `ElementX/Sources/Matcha/MatchaConfig.swift`. Mocks regenerate through Sourcery on build.

Add, each returning `Result<_, RoomProxyError>` per AGENTS.md wrapping rules:
- `sendRawEvent(type:content:)` → event id, over `Room.sendRaw` (:12386 in the bindings).
- `sendRawStateEvent(type:stateKey:content:)` over `sendStateEventRaw` (:12446).
- `stateEventsJSON(type:)` over `stateEvents` (:12629), and `storedStateEventID(type:stateKey:)` for the create event.
- `fetchRelatedEventsJSON(eventID:relation:from:limit:)` and `fetchStateEventContentJSON(type:stateKey:)` over the two
  patched calls.
- `rawMessageLikeEvents(types:)` over `subscribeToMessageLikeEvents` (:13095), holding the `TaskHandle`s for the
  subscription's lifetime.
- `matchaScanTimeline()`: `room.timelineWithConfiguration(.init(focus: .live(hideThreadedEvents: false), filter:
  .eventFilter(.include([the Matcha message-like types, the two Matcha state types, "m.room.encrypted"])),
  internalIdPrefix: "matcha_", trackReadReceipts: .disabled, ...))`, the same types as Android's scan timeline
  (`MatchaRoomStore.kt:248-250`) and the same shape as `JoinedRoomProxy.swift:86-91`, read through `TimelineItemProxy`
  `debugInfo.originalJson` (:169-170). The `m.room.encrypted` items are the source of I5's `Undecryptable` warning and
  its cause (the item's `.unableToDecrypt` content, `RoomTimelineItemFactory.swift:44`).
- The thread list needs no new wrapper: `threadListService()` exists (`JoinedRoomProxy.swift:199`). Each call builds a
  new proxy, so I5's store calls it once and keeps it.
- `MatchaConfig`: the event types of `MATCHA_PORT_PLAN.md` §2, the limits (name 100, title 200, description 4000,
  365 days, 2000 chars, 16 KiB chunks, 256 chunks), the bot localpart, and the bot user id built on the session's own
  homeserver (Android `a3c4a73454`).

Done when: unit tests over `JoinedRoomProxyMock` cover each wrapper's success and error mapping; a manual smoke test
sends a `thread.metadata` reference to a test room and reads it back through relations.

#### I4 — Protocol port (Swift) — 1–2 / 1–1.5 — can start now (week 0, in the new repository)

Create the `MatchaProtocol` local package (§4.0 layout). Translate almost line for line:
`ThreadMetadataProtocol.kt` (129), `KanbanProtocol.kt` (228), `ReminderProtocol.kt` (83), `TaskParticipants.kt` (60),
`RawEvent.kt` (66), `docs/DocsProtocol.kt` (137). Value types; a small tolerant JSON reader over `JSONSerialization` (or
a `JSONValue` enum) because `Codable` throws on the first bad field and the protocol drops invalid revisions silently;
CryptoKit `SHA256` for `"thread-" + sha256hex(threadId)`; the DST-gap check by round-tripping `Calendar.dateComponents`
→ `date(from:)` with an injectable `TimeZone` and clock. Port the 32 protocol tests (455 lines) as Swift Testing
`@Test(arguments:)` tables. The fixture runner and checksum test (§3.4) are added when F1 lands (week 1), not here.
`MatchaProtocol/Package.swift` declares `platforms: [.iOS(.v18), .macOS(.v14)]`: `swift test` on this Mac builds for
macOS, and CryptoKit needs macOS 10.15 or later. Upstream's `swift run tools setup-project` (git hooks, SwiftLint) needs
Swift tools 6.2, so it cannot run here before I0a; run SwiftLint/SwiftFormat on the package once it can.

Done when: the ported tests pass with `swift test --package-path MatchaProtocol` on this Mac (Swift 6.1.2) and in CI.

#### I5 — Room store and session service — 2.5–3.5 / 2.5–3.5

Files: `ElementX/Sources/Matcha/Services/{MatchaService,MatchaRoomStore,NoBoardAnswers,NamedThreadResult}.swift`;
`UserSessionProtocol` gains `matchaService`; `RoomFlowCoordinator` takes the room's store from it;
`Services/Room/RoomThreadListServiceProxy.swift`.

Port `MatchaRoomStore.kt` (790), `NoBoardAnswers.kt`, `NamedThreadResult.kt`, and the 31 store tests (973 lines) with
`MatchaRoomFixtures.kt`:
- Thread roots come from the SDK thread list, as on Android (`MatchaRoomStore.kt:294-300` subscribes to its items and
  pagination, :568-647 builds the projection from "SDK thread list ∪ card thread ids ∪ legacy archive",
  `MATCHA_PORT_PLAN.md` §2.2): subscribe to the `threadListService()` items, read the first page on open and one more
  page with each "load more". In the same PR, replace the `fatalError("This should never fail™")` in
  `RoomThreadListServiceProxy.updateItems()` (:66-70) with skipping the item and an `MXLog.warning` (event id only),
  because the store now reaches every root, including from the Tasks tab.
- Reads: for each anchor (board root, thread roots, document anchor) every page of `fetchRelatedEventsJSON(..., .reference,
  limit: 100)`, at most 4 anchors at once (a `TaskGroup` with a limit, like `MAX_CONCURRENT_ANCHORS`); participants from
  one page of `.thread` relations per thread; live events merged from `rawMessageLikeEvents`; state and undecryptable
  Matcha events from `matchaScanTimeline` plus the server pointer read (lessons table).
- Optimistic sends merged until the remote copy arrives; only for types read back.
- Warnings `Undecryptable` (an `m.room.encrypted` scan item whose cleartext `m.relates_to` targets the board or a
  thread, or an undecryptable thread root without an explicit name, as `MatchaRoomStore.kt:629-640`),
  `HistoryUnavailable`, `NoAccess`, `Network`; `loadFailed`, `retryLoad()`, `readBoardPointer()`, `isBoardResolved`.
- Publishes `CurrentValuePublisher<MatchaRoomSnapshot, Never>` (`Other/CurrentValuePublisher.swift`); snapshot types
  `Sendable`.
- `MatchaService` owns one store per room for the room screen, the Tasks and Notes tabs and any secondary window, and
  cancels it on leave and logout.

Done when: ported tests pass; against a room created on the web, the store lists its named threads, board pointer and
cards; leaving the room drops its store; a failed send leaves nothing behind.

#### I6 — Named threads, chip bar, chat top bar — 2.5–4 / 2–3.5

Files: `Screens/RoomScreen/View/RoomScreen.swift` (`.topBanners` / `.safeAreaInset(edge: .top)` at :73-96; `roomActions`
:298-342), `RoomScreenViewModel.swift`, `RoomScreenModels.swift`, `Screens/ThreadTimelineScreen/View/ThreadTimelineScreen.swift`
(principal title :78-81), `Screens/RoomThreadListScreen/`, `Services/Room/RoomThreadListServiceProxy.swift`,
`Timeline/View/Threads/TimelineThreadSummaryView.swift`, `RoomTimelineItemFactory.swift` (:823-858),
`FlowCoordinators/RoomFlowCoordinator{,StateMachine}.swift`, new `Matcha/Screens/ChatBar/`.

Scope (`WHATSAPP_DESIGN.md` §4a, §6.1 items 1, 7, 9, §6.3 items 1-2):
- Top bar: All threads, Tasks (open-task count badge, visibility rule applied), ⋯ `Menu` with New thread (only if
  `canManageThreads`). Voice call and Video call stay hidden until the homeserver advertises a LiveKit focus (decision
  17); when they return, they go in ⋯, during a call the Join button stays and All threads moves into ⋯, and the
  existing `!isiOSAppOnMac || isNativeCallingEnabled` gate is kept. Remind me is added to ⋯ by I9, Space notes and
  Earlier chat document by I11, so intermediate TestFlight builds have no dead menu items.
- Chip bar under the top bar on `RoomScreen` and `ThreadTimelineScreen`: General · named threads (newest activity
  first) · New thread, shown only when the chat has an active named thread (renamed, or named at creation as marked by
  its hidden first reply). Chips have tab traits. `.contextMenu` on a chip: Rename, Archive (Remind me is added by I9).
  Archive shows a bottom Undo banner (custom; `UserIndicatorController` toasts have no action), no confirmation dialog.
- New thread / Rename: `.sheet` with `.presentationDetents([.medium])`, Cancel left, Create/Save right, inline 1–100
  character validation. Create sends the root, then the hidden `thread.metadata` reply as a thread reply
  (`MATCHA_PORT_PLAN.md` §2.1); a failed second send reads "message sent, thread setup failed" with retry.
- Thread names in the thread title, the thread list rows and the bubble thread summary; summary of a thread whose
  latest event is the hidden reply shows the name, not "Unsupported event"; reply counts subtract the hidden reply; the
  thread list shows a latest message for `.other` roots or none, never a crash.
- Only if S2 finds the "Unable to decrypt" tiles of Matcha events noisy (+0.5 agent-day, inside the range above): hide
  a UTD timeline item whose cleartext `m.relates_to` (read from `debugInfo.originalJson`) is an `m.reference` to a
  known Matcha anchor (board root, a thread root, the document anchor). The store's `Undecryptable` warning still
  reports them.

Tests: view-model tests ported from `ChatBarPresenterTest` (506 lines) and `DefaultMatchaRoomControllerTest` (166);
previews for each state.
Done when: a named thread created on web appears on iOS as a chip and as the thread title; one created on iOS shows its
name on web and Android; an unanswered named thread reads 0 replies on all three.

#### I7 — Task board per chat — 3–5 / 3–4

Files: new `Matcha/Screens/TasksBoard/` (Coordinator, ViewModel, Models, View, Setup, Archived) and `TaskEditor/`;
`RoomFlowCoordinatorStateMachine.swift` (states for the board and archived tasks; events `presentTasksBoard`,
`dismissTasksBoard`); `RoomFlowCoordinator.swift`; `Screens/RoomScreen/ComposerToolbar/View/RoomAttachmentPicker.swift`
(a Task item next to Poll, :41) and its `ComposerToolbarViewAction`.

Scope (§4b, §6.1 item 2, §6.3 items 3-4):
- Compact width: a segmented `Picker` To do · In progress · Done (counts as separate text; a scrollable tab row at
  200 % Dynamic Type). Regular width: three columns.
- Rows like chat rows: status icon, title, one grey line, deadline or Overdue, a quiet `Group` icon ("Shown to everyone
  in the channel") on shared tasks. `.swipeActions` (leading Mark done, trailing Archive); `.contextMenu` (Edit, Move
  to…, Open thread, Archive; Remind me is added by I9).
- Editor: full-height sheet (`.presentationDetents([.large])`), `Form` with title, description
  (`TextField(axis: .vertical)`), status, deadline toggle + `DatePicker` + clear, "Show to everyone in channel" (off for
  new tasks, hidden in DMs) with the web hint as footer. No thread picker: a new task creates its named thread.
- Setup: "Looking for this chat's task board…" until the server pointer read answers, then "Create task board" or "Ask
  an admin…".
- Visibility: a task shows when it involves you (thread root sender and repliers, standalone card authors, every DM
  member) or is shared.
- Writes through the ported `TaskWriter` in this order: re-read pointer → refuse a duplicate thread link → patch the
  thread name → re-read metadata → send the full revision (`MATCHA_PORT_PLAN.md` §2.2).

Tests: ported `TaskBoardPresenterTest` (825 lines), `TaskEditorPresenter`, `TaskWriter`, `DeadlineFormatter`,
`LocalDeadline` tests (about 36 cases).
Done when: a web card appears in the right column on iOS; moving it on iOS moves it on web and Android; a chat without a
board reaches Setup only after the homeserver said there is none; creating a board on iOS never replaces a web board
(test with a mock pointer that appears between checks); a hostile card id like `archived` does not break the list.

Drag and drop between columns is not in this phase (Android deferred it too; P2).

#### I8 — Tasks tab — 2–3 / 2–3

Files: `UserSessionFlowCoordinator.swift` (:24 `HomeTab.tasks`, :87-147 a `TabDetails` entry), new
`TasksTabFlowCoordinator`, `Matcha/Screens/TasksTab/`.

Scope (§4c, §6.3 items 5-6):
- Discovery, ported from `TasksTabRooms.kt` (295): joined rooms from the room summary provider; a stored pointer proves
  a board; otherwise `fetchStateEventContentJSON` 4 rooms at a time, skipping rooms cached as "no board" (10 min); open
  a store only for rooms with a board.
- List mode: tasks that involve you, each with "With Bob, Carol" or "Just you"; filters People ("Everyone I work with" or
  one person), Chat, Archived.
- Table mode: SwiftUI `Table` with `sortOrder: [KeyPathComparator]`, columns Task · Chat · Status · Due · People. On
  compact width (where `Table` shows only its first column) a `Grid` in a horizontal `ScrollView` with the Task column
  pinned.
- Opening a task uses `AppRoute.thread(roomID:threadRootEventID:focusEventID:)` (`AppRoutes.swift:56`). Toolbar "+" →
  Choose a chat → editor.
- Navigation: a plain `NavigationStackCoordinator` like the Search tab, which gives the table the full width for free
  (report 1). The iPad behaviour "split comes back when a task opens" is P1 (report 3's column-visibility approach).
  Resolution of the two reports: simplest first, iPad refinement later.

Tests: ported `TasksTabPresenterTest` (686) and `TasksTabRoomsTest` (125).
Done when: the tab lists tasks from at least three chats, including one whose board was made on the web and never
opened on this device; a board created on this device shows up without waiting for the cache.

#### I9 — Reminders — 1.5–2 / 1.5–2

Files: `Matcha/Screens/RemindMe/`, `Matcha/Services/ReminderService.swift`;
`Screens/Timeline/View/ItemMenu/TimelineItemMenuAction.swift` (:58-79, add `remindMe`),
`TimelineItemMenuActionProvider.swift`, `Screens/Timeline/TimelineInteractionHandler.swift`; `RoomTimelineItemFactory`
plus a capsule cell in `TimelineTableViewController` for the system pills; `Timeline/View/ReadReceipts` for the bot
exclusion.

Scope (§4d, §6.1 items 3, 4, 17): a medium sheet with radio rows In 2 h, In 8 h, In 24 h, At the deadline (only with a
deadline), Pick date and time (graphical `DatePicker` + time + `TimeZone.current` label); a message field; one confirm
button naming the outcome ("Set reminder · Tomorrow 09:00") that is the only thing that sends. Bot membership: joined →
send; invited → "waiting for the bot"; absent → `.alert` to invite (needs `canInvite`), then wait. DMs: only when the bot
already joined. Mandatory pills for your own `!remind` and the bot's `m.notice`. Record `reminder_requested` through the
task writer after a task's reminder is sent. Entry points, all added in this phase: chip context menu, ⋯, message menu,
task rows/sheets.

Tests: ported `RoomReminderPresenterTest` (495) and `ReminderProtocolTest`, with the injectable clock and time zone.
Done when: each entry point sends exactly one `!remind` after confirm; the bot's notice renders as a pill; a DM without
the bot shows "reminders aren't available" and never invites.

#### I10 — Home tabs and Channels — 1–2 / 1–1.5

Files: `UserSessionFlowCoordinator.swift` (`HomeTab.notes`; channels in place of spaces while the flag is on),
`SpacesTabFlowCoordinator.swift:74` (`setSidebarCoordinator` → a new `Matcha/Screens/Channels/ChannelsScreen`),
`Screens/HomeScreen/View/Filters/RoomListFilterModels.swift:14-29` (Favourites first; check first whether the raw value
is persisted).

Scope (§3.2, §6.3 items 7-8): tabs Chats · Tasks · Notes · Channels with plural labels; the Search tab (`role: .search`)
stays; with the flag off the tabs stay upstream's. Channels: each joined top-level space as a header row (rounded-square
avatar, name, "N channels") that opens the space, its joined channels as compact rows (`spaceRoomList(spaceID:)` or
`joinedParents(childID:)`), then "Other channels" for group chats outside any space; no DMs; invites and "+ Create
space" as on the Spaces tab.

Tests: ported channels logic from `HomeSpacesPresenter` (93 lines of channels code); previews from
`ChannelsHomeSpacesStatePreviewParam` (76).
Done when: the four tabs show with the flag on and upstream's tabs with it off; Channels matches Android's for the same
account.

#### I11 — Notes (engine, documents, editor, Notes tab) — 4–6 / 3.5–5

Start with spike S4.

Engine (the research disagreed; see Appendix A): host the **unchanged** `engine.html` + `engine.js` from
`features/matcha/impl/src/main/assets/matcha-docs/` in a hidden `WKWebView` with a `WKScriptMessageHandler` bridge, the
pattern Element X iOS already uses for Element Call (`Screens/CallScreen/View/CallScreen.swift:104-175`). Handle
`webViewWebContentProcessDidTerminate` like Android's `RenderProcessGone` (recreate, replay). This keeps the CRDT
byte-identical with web and Android and keeps JIT. JavaScriptCore (report 3's preference) needs a rebundle with shims
for `window`, `document`, `btoa`/`atob` and `crypto.getRandomValues`, and runs without JIT; use it only if S4 shows
WKWebView lifecycle problems. Do not use `y-crdt/yswift` (last release 2024-04-04, yrs 0.18.2, missing the
pending-structs and merge APIs this protocol needs).

Port: `RoomDocumentSession.kt` (476: ≤16 KiB frames, commit frames, dedupe `sender:update_id`, full replay before
editing, `incomplete_update`/`decrypt_error`, 500 ms debounce, "Saved" only after the server acknowledges),
`DocumentCatalog` (148), `SpaceDocsContext` (87), `TextDiff` (83), `EditEchoes`, `MarkdownParser` (289, the linear
version), `NotesTabReader` (241), `NotesTabPresenter` (205).
- Editor: `UITextView` in `UIViewRepresentable` (exact selection, marked text); preview as `AttributedString` blocks
  from the ported parser (`AttributedString(markdown:)` handles inline markup only); formatting row in
  `ToolbarItemGroup(placement: .keyboard)`; ⌘B/⌘I; export with `ShareLink`.
- Setup (admin, once): `enableEncryption()`, then `sendRawStateEvent("m.room.power_levels", merged JSON, "")` setting
  `events["com.matchahoneylatte.docs.update"] = 0` and `events["m.room.encrypted"] = 0`, then poll stored state.
- Flush pending frames when `scenePhase` becomes `.background`, inside `beginBackgroundTask`. Unsent edits stay
  memory-only, as on web.
- Notes tab (`HomeTab.notes`, `NotesTabFlowCoordinator`): one section per joined space with notes ("Space notes" first),
  "Turn on notes" where you can, "+" → New note (asks for the space when there are several). A Notes row and a ⋯ item
  on `Screens/Spaces/SpaceScreen`, and the chat top bar's ⋯ items Space notes and Earlier chat document (I6 leaves them
  out until this phase).
- The user-facing word is "Notes" everywhere (§6.3 item 9); the wire protocol keeps `docs`.

Tests: the 67 docs tests (1,840 lines) with `FakeYjsEngine` and `DocsTestFixtures`; one engine round-trip test in the
app-hosted unit test target (**assumed** possible: WKWebView needs a host app).
Done when: a note typed on web appears on iOS within seconds and the reverse; fast typing with autocorrect loses no
characters; a frame missing predecessors keeps the doc read-only; killing the web content process recovers without data
loss; the largest real space opens within the S4 budget.

#### I12 — Re-skin, iOS-faithful — 3.5–5.5 / 2.5–5 — parallel track from I1

Assumes the iOS 26 target (decision 16): one look, Liquid Glass. Keeping 18.5 adds the pre-glass variant of every
surface below, about +1 agent-day / +1 week.

0. Draft `WHATSAPP_DESIGN.md` §8 "Apple platforms" (in the Android repo, which holds the spec) in week 1 from Apple's
   Human Interface Guidelines: the iOS divergences of decision 10, the tab order, Liquid Glass, and the brand rule. The
   WhatsApp-iOS patterns in the research are from memory; freeze §8 only after S8 has checked them on the iPhone
   (week 4). Steps 1–3 can start on the draft.
1. Palette: a fork `CompoundHook` (`AppHooks/Hooks/CompoundHook.swift`, applied at `AppCoordinator.swift:78`) maps
   `MatchaColors.kt` roles onto `CompoundColorTokens` key paths with dynamic light/dark colours (same
   compound-design-tokens 11.3.1 as Android); bubble, wallpaper and chip aliases in a small `MatchaColors.swift`.
2. Chat list: `HomeScreen.swift` (title mode at :33 is forced inline on glass; large titles on root tabs per decision
   10), `HomeScreenRoomCell.swift` (52 pt avatars, trailing time green when unread, unread capsule, inset hairline
   separators), swipe actions (Read/Unread, Favourite; Mute, Leave), filter chips in a scrolling list header.
3. Chat: wallpaper; bubbles with a tail on the last of a group (`Timeline/View/Style/TimelineItemBubbleBackground.swift`
   has none), time and ticks inside the bubble (`TimelineItemSendInfoLabel.swift`), `TimelineItemBubbledStylerView.swift`;
   composer pill with leading "+" menu and trailing camera/mic→Send (`MessageComposer.swift`, `ComposerToolbar`); pills
   for dates and system events. Read receipts move to the ticks plus a long-press **Seen by** action, as
   `WHATSAPP_DESIGN.md` specifies (:589, `ReadBy` at :706-716): drop the avatar row under bubbles, add a
   `TimelineItemMenuAction.seenBy` case (own messages only, `TimelineItemMenuAction.swift:58-79`) that sends the existing
   `TimelineViewAction.displayReadReceipts(itemID:)` (`TimelineModels.swift:81`) to open the read-receipts sheet
   (`Timeline/View/ReadReceipts/`). About 0.25 agent-day.
4. Sheets and menus: restyle `TimelineItemMenu.swift` (keep the sheet; the WhatsApp lifted overlay would be another 3–5
   agent-days), confirm top right, `confirmationDialog` for destructive actions, `.sensoryFeedback` on chip selection
   and task done.
5. Room details (`RoomDetailsScreen`, 1,486 lines) and Settings.
6. Re-record PreviewTests and UITests snapshots with upstream's `record-snapshots.yml` (the `record-snapshots` label),
   after decision 19 is settled.

Brand rule unchanged: no WhatsApp name, logo, wordmark, doodle wallpaper, illustrations or glyphs; Compound icons only;
no Updates/Status, no AI, no grey "delivered" ticks.
Done when: §8 is frozen after S8 and David approved it; light and dark (plus pre-glass, if 18.5 is kept) pass review;
Dynamic Type at 200 % keeps every Matcha screen usable; Seen by opens the read-receipts sheet; snapshots are recorded in
the fork.

#### I13 — Push and notifications — 1.5–2.5 / 1–1.5

- Server: Sygnal (§7.3). App: `AppSettings.pushGatewayBaseURL` (:225) → `https://push.matchahoneylatte.com`; the pusher
  app id follows the bundle id automatically (`<base>.ios.prod` release, `.ios.dev` debug; `pusherAppID` :217).
- NSE: `NSE/Sources/NotificationHandler.swift:115-181` already turns custom types into `.unsupportedShouldDiscard`, which
  only works with the filtering entitlement (`NSE/SupportingFiles/target.yml:65`; comment at
  `NotificationServiceExtension.swift:90-93`). Without it, every push shows a banner, including Matcha card, metadata and
  docs frames in E2EE rooms (the server sees only `m.room.encrypted`). Until it is granted the entitlement stays
  commented out (I1; device and TestFlight provisioning fails with it, S5 included), and the choice is only between
  accepting the banners and adding the push-rule mitigation S5 tests. When Apple grants it: enable the capability on
  the NSE App ID (I0b), restore the line in `NSE/SupportingFiles/target.yml:65`, regenerate the profiles.
- Optional (Android has not done it either): thread names in NSE titles ("Thread in <room>",
  `NotificationContentBuilder.swift:41-49,157-170`) through a name cache the app writes into the app group.

Done when: a message in an E2EE room wakes a locked TestFlight build on the real iPhone with decrypted content; the
count of banners caused by a web card move is known (zero with the entitlement).

#### I14 — TestFlight and hardening — 1.5–3 / 1–1.5

- Archive and upload: Xcode Cloud (upstream's `ci_scripts/ci_post_clone.sh` and `ci_post_xcodebuild.sh` already
  generate the project and the "What to Test" notes), or GitHub `xcode-27` with `xcodebuild archive` +
  `-exportArchive` (method app-store-connect, destination upload) and an App Store Connect API key (`ASC_KEY_ID`,
  `ASC_ISSUER_ID`, `ASC_KEY_P8`). fastlane is not needed (upstream removed it).
- An internal group for David's devices; an external group for coworkers (decision 4), which needs Beta App Review on
  its first build: the rebrand, the §7.2 items it checks (demo account, privacy policy, review notes, no Element marks)
  and the Beta App Information in App Store Connect come first (+0.5–1 agent-day, included above, plus 1–2 calendar
  days of review). `ITSAppUsesNonExemptEncryption` per decision 18.
- About/Settings gets a "Source code" row linking the tag of the build (AGPL, §7.6).
- Privacy manifest (`ElementX/SupportingFiles/PrivacyInfo.xcprivacy`) trimmed: with PostHog and Sentry off, drop the
  analytics purposes, Product Interaction and Performance Data; add Other User Content (the reminder bot stores
  `!remind` text).
- Accessibility audits (`AccessibilityTests`), VoiceOver pass on the chip bar, board and table.

Done when: an external tester (not on David's team) installs from TestFlight, logs in, gets pushes, and every Matcha
feature works against web and Android on the same account.

## 5. iPad and Mac

### P1 — iPad layout — 2–3 / 1–2

No new architecture: each tab is already a `NavigationSplitCoordinator` (`.balanced` at regular width, a
`NavigationStack` at compact; `NavigationCoordinators.swift:337-430`), with the tab rail on iOS 26+.
- Layouts key off `horizontalSizeClass` or measured width (the 600 pt board-columns threshold), never the device idiom;
  read the existing `tabViewHorizontalSizeClass` environment value to avoid rail/split layout loops.
- Board: three columns at regular width; Notes, board and threads render in the detail column. Sheets use
  `.presentationSizing(.form)`. Column widths 2 : 3 with `.navigationSplitViewColumnWidth(min:ideal:max:)` (Android §6.1
  item 16).
- Tasks tab: move from the I8 stack to a split whose detail holds the table with `columnVisibility = .detailOnly` while
  nothing is open, back to `.all` when a task opens (§6.3 item 5). Watch for the layout loops the existing code already
  works around.
- Keyboard: `Application.swift` already has `.commands` (⌘, Settings, ⌘K search, a disabled "Start chat"
  `CommandGroup(replacing: .newItem)`), shown in the iPadOS 26 menu bar. Add ⌘N (enable the existing item), ⇧⌘N new
  thread, ⌥⌘N new task, ⌘1–⌘4 tabs, ⌘R Remind me, ⌘↩ save in editors. Arrow keys come with `List(selection:)` /
  `Table(selection:)`.
- Pointer: `.hoverEffect(.highlight)` on chips and rows, the same `.contextMenu`s on right-click, `.help()` on icon
  buttons.
- Stage Manager: test resizing from about a third of the screen to full while a sheet is open and mid-edit.

Done when: on the 13-inch iPad simulator, in both orientations and in a one-third Split View, every Matcha screen works;
iPad snapshots are recorded.

### P2 — iPad extras (optional) — +1–3 agent-days

- `SecondaryWindowType.tasks` and `.note(spaceID:noteID:)` next to the existing `room`, `settings` and `call`
  (`Application/Windowing/WindowManagerProtocol.swift:11`; `UIApplicationSupportsMultipleScenes` is true). All windows
  share `MatchaService` and one document session per note, or edits would be sent twice.
- Drag and drop between board columns (`.draggable` with a `Transferable` card id + `.dropDestination`), about 1
  agent-day.
- The task editor as an `.inspector` beside the board.

### M1 — macOS through the iPad app — 1–2 / 0.5–1

Element X iOS already runs on Apple Silicon Macs as an iPad app (33 `ProcessInfo.processInfo.isiOSAppOnMac` branches:
window sizing `Application.swift:96`, secondary windows, `TimelineItemMacContextMenu.swift`, no QR scanning). Element X
is listed as "Requires macOS 15.5 or later and a Mac with Apple M1 chip or later", matching its iOS 18.5 target; with the
fork's iOS 26 target (decision 16) expect macOS 26 or later (assumed from that pairing; check the App Store Connect
listing). Scope: check the Matcha screens under `isiOSAppOnMac` (WKWebView Notes, context menus, window sizes, the
top-bar call gate), enable a TestFlight tester group for Mac, and leave "iPhone and iPad Apps on Apple Silicon Mac" on in App Store Connect. No Mac Catalyst
(`SUPPORTS_MACCATALYST: false`, `project.yml:56`; the SDK has no macabi slices). Intel Macs and direct download are not
covered; the Electron app covers them.

## 6. Desktop (macOS, Windows, Linux)

### 6.1 Route per OS

| OS | Route | Packages | Signing | Updates |
|---|---|---|---|---|
| macOS | Element Desktop (Electron) from the web fork's `apps/desktop`, universal | dmg + zip | Developer ID + notarisation (same $99 Apple membership) | Squirrel.Mac, `update/macos/releases.json` on R2. Needs a signed app |
| Windows | Same, x64 first (arm64 later) | Squirrel installer (skip MSI) | decision 12 | Squirrel.Windows, `update/win32/x64/RELEASES` on R2 |
| Linux | Same, amd64 + arm64 | tar.gz + deb (+ AppImage, one line) | none | none built in (apt repo later) |

Why this route (report 4): upstream merged element-desktop into the element-web monorepo in March 2026
(`apps/desktop`), the fork already contains it untouched, and the fork's `matcha-beta.yml` already merges upstream's
stable tag daily, so Electron security bumps arrive with no extra repo. Every Matcha feature arrives at once because the
desktop app bundles the same web build; nothing in `apps/web/src/utils/matcha` uses origin-, service-worker- or
Electron-specific APIs. Seshat (encrypted search) ships as prebuilt npm binaries for all six OS/arch targets
(`docs/native-node-modules.md`); no native toolchain. Report 5 suggested forking the standalone element-desktop repo and
skipping Seshat; that repo is archived (README, last commit 2026-03-25), so report 4 wins.

Rejected:
- **Tauri** around the web build: 20–35 agent-days, a new `TauriPlatform` + Rust IPC (keyring, tray, deep links,
  Seshat, updater), and WebKitGTK has no WebRTC by default, so calls break on Linux.
- **Compose Multiplatform desktop** from the Android code: 60–120+ agent-days; 264 of 273 Gradle modules are Android
  modules, Appyx 1.7.1 is Android-only, and only `sdk-android` is published.
- **Mac Catalyst**: 8–15 agent-days, needs `*-ios-macabi` SDK slices the xtask lacks.
- **PWA as the desktop app**: no Seshat, notifications only while open, no tray. Kept as the D0 stopgap.

### 6.2 Phases

#### D0 — PWA stopgap — 0.5–1 / 0.2 — can start now

`apps/web/res/manifest.json`: name and short_name "Matcha", own icons including 192 px and 512 px (Chrome's install
criteria; today it has no 192), drop Element's `related_applications`; favicon. Deployed by the existing matcha-beta
image pipeline. Tell users: install from Chrome/Edge, or Safari "Add to Dock"; no encrypted search; notifications only
while open.

#### D1 — Web-fork rebrand shared by PWA and desktop — 1–2 / 0.5–1

`res/vector-icons/*`, `res/img/element-desktop-logo.svg`, `branding.auth_header_logo_url` / `welcome_background_url` in
config, strings that hard-code "Element" instead of `%(brand)s`. Partial white-label is acceptable
(`docs/config.md` says complete re-branding is not supported).

#### D2 — Desktop variant and config — 1 / 0.5

- `apps/desktop/matcha/release/build.json` (used through `VARIANT_PATH`): `appId com.matchahoneylatte.matcha`, `name
  matcha-desktop`, `productName Matcha`, `protocols [com.matchahoneylatte.matcha]`. The new productName also gives Matcha
  its own userData directory, so it never collides with an installed Element.
- `apps/desktop/matcha/release/config.json`: the beta config plus `update_base_url:
  https://packages.matchahoneylatte.com/desktop/update/`, `web_base_url: https://beta-element.matchahoneylatte.com/`,
  `permalink_prefix: https://beta-element.matchahoneylatte.com` (reminder links use `RoomPermalinkCreator`). **Never
  hand out a build with Element's `update_base_url`: it auto-updates into stock Element.**
- Patch the two hard-coded `element://` registrations so Matcha never takes over Element's links:
  `apps/desktop/src/protocol.ts:17` (`LEGACY_PROTOCOL = "element"`, registered as default handler at :59) and
  `apps/web/src/vector/platform/ElectronPlatform.tsx:469` (`getSSOCallbackUrl` forces `url.protocol = "element"`).
  Verified at those lines.
- Icons: `build/icon.icon` (Icon Composer bundle; needs Xcode `actool` on the macOS runner), `build/icon.ico`,
  `build/icon.png`.
- OIDC: not needed today (password-only). Before any move to MAS/OIDC set `oidc_metadata.client_uri` to a
  matchahoneylatte.com URL (`BasePlatform.ts:454`).

#### D3 — Fork-owned CI — 2–3 / 1–1.5

New `.github/workflows/matcha-desktop.yml` in the web fork. Do not edit or enable upstream's `build_desktop_*.yaml`:
they hard-code `repository: element-hq/element-web` and `docs/matcha-beta.md` says to keep upstream workflows disabled
and unedited.
- Input: `matcha-beta.yml` uploads no web app artifact (only the `matcha-beta-browser-failure` diagnostics); it ships
  the web app as the image `ghcr.io/bouyant-dev/element-web:beta` (its lines 129-176; `Dockerfile.matcha-beta` copies the
  tested build to `/app`). So the prepare job (`permissions: packages: read`) pulls the verified `:beta` image, copies
  the web app out (`id=$(docker create ghcr.io/bouyant-dev/element-web:beta) && docker cp "$id:/app" webapp && docker
  rm "$id"`), records the image digest in the release notes, copies `config.json` over it and runs
  `pnpm run asar-webapp` (as `build_desktop_prepare.yaml` does). Desktop then ships the same bits as the beta site. The
  alternative, an `upload-artifact` step in `matcha-beta.yml`, needs a human to push that workflow edit (`GITHUB_TOKEN`
  cannot push workflow files) and a `workflow_run` download with the run id. The prepare artifact can carry `build/`
  icon overrides.
- Jobs copied from `build_desktop_macos.yaml` (macos-26, universal, sign + notarise + `spctl` when secrets exist),
  `build_desktop_windows.yaml` (windows-2025), `build_desktop_linux.yaml` (ghcr desktop-build-env image, glibc ≤ 2.34
  check), each checking out `bouyant-dev/element-web`.
- `VERSION` set explicitly and monotonic, zero-padded: `1.12.32-matcha.0001`. Without it `set-version` uses the web's
  `v1.12.32-matcha-<sha>`, which Squirrel.Windows misorders (spike S7).
- Trigger: manual dispatch (decision 13); `workflow_run` after a beta publish is the alternative.
- Signing secrets in a protected environment `desktop-signing`.

#### D4 — macOS signing and notarisation — 0.5–1 / 0.5

Developer ID Application certificate exported to `CSC_LINK` / `CSC_KEY_PASSWORD`, plus `APPLE_ID`,
`APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID`. The entitlements (`apps/desktop/build/entitlements.mac.plist`) and
hardened runtime are upstream's. Without signing, Squirrel.Mac cannot update and macOS Sequoia+ users must approve the
app in System Settings › Privacy & Security.

#### D5 — Windows signing — 0 to 1 / 0 to 0.5 (Store route 2–3 / 1)

Per decision 12. SSL.com route: reuse upstream's eSigner CKA steps (`ED_SIGNTOOL_THUMBPRINT` / `SUBJECT_NAME`,
`ESIGNER_*`). Store route: add an `appx` target; Squirrel's updater disables itself when `Update.exe` is absent
(`apps/desktop/src/updater.ts` `available()`). SmartScreen warns even for OV/EV-signed builds until reputation builds.

#### D6 — Downloads and update feeds on R2 — 1 / 0.5

R2 bucket behind `packages.matchahoneylatte.com` (matchahoneylatte.com is already on Cloudflare), mirroring upstream's
`install/` and `update/` layout and `aws s3 cp` deploy step (`docs/packaging.md`). One release in every format is about
2.5–3 GB (dmg 302.8 MB, Windows Setup 219.6 MB, Linux tar.gz 182.1 MB for upstream 1.12.32); keep three releases.

#### D7 — QA across the three OSes — 2–3 / 1–1.5

Login, cross-signing, Seshat search in an encrypted room, notifications and tray, deep links, Notes (Yjs/CodeMirror),
board, reminders, and one auto-update round trip (needs two published versions). Run upstream's desktop Playwright suite
on Linux. Windows and Linux in VMs or CI; macOS on this Mac.

Linux extras (optional): AppImage 0.5, GPG-signed apt repo on R2 1–2, self-hosted Flatpak modelled on Flathub's
`im.riot.Riot` 1–2. Flathub itself would likely reject a minimally changed fork that is not built from source.

Ongoing: about 0.25 agent-day per desktop release, yearly certificate renewals, and reconciling the copied workflow
jobs with upstream's `build_desktop_*` changes about once a quarter (estimate).

## 7. Distribution and operations

### 7.1 iOS channels

| Channel | Who | Review | Limits | Use |
|---|---|---|---|---|
| TestFlight internal | up to 100 App Store Connect users on David's team | none | builds expire after 90 days | David's own devices during the build-out |
| TestFlight external | up to 10,000 by email or public link | Beta App Review on the first build of a group | 90 days | **v1 default for coworkers** (decision 4), after the rebrand and the §7.2 items Beta App Review checks |
| Ad Hoc | 100 iPhones + 100 iPads per membership year, by UDID | none | — | avoids review, but needs UDID collection, a regenerated profile per new device and over-the-air hosting (+1–1.5 agent-days, not in the totals) |
| App Store | public | full App Review | — | optional, decision 4 |
| Mac (iPad app) | TestFlight Mac group or App Store on Apple Silicon | as above | M1+; macOS 26+ with the iOS 26 target (assumed) | M1 |

### 7.2 Review and compliance checklist (before external TestFlight or the App Store)

- 4.1 copycats / 5.2.1 IP: no "Element" in name, subtitle, keywords or icon; Element illustrations replaced; never
  mention WhatsApp in metadata.
- 4.3 spam: lead the description and review notes with Matcha's own features (named threads, task board, Tasks tab,
  reminders, Notes).
- 1.2 user-generated content: upstream has report content, report room, ignore user and account deactivation. David
  must actually answer reports (Synapse event reports admin API) and publish a contact address.
- 5.1.1(i) privacy policy in-app and in App Store Connect, naming the homeserver, Sygnal (sees event and room ids) and
  the bot. 5.1.1(v): keep account deactivation if registration is open.
- 2.1: a demo account on matchahoneylatte.com with a sample board and notes.
- 2.5.2: `engine.js` ships in the bundle, never downloaded. 2.5.4: keep only background modes in use (no `voip` or
  `audio` while calls are hidden, decision 17).
- Export compliance: David's legal choice, decision 18 (default: upstream's `ITSAppUsesNonExemptEncryption: false`,
  France left out until a French encryption declaration is filed, the US year-end self-classification report checked).
- EU Digital Services Act: declare trader status, or leave the EU out. China mainland storefront: commonly unavailable to
  CallKit apps (unverified); leave it out if calls come back.
- Privacy label to match the trimmed manifest (I14). New age-rating questionnaire.
- Screenshots: iPhone and 13-inch iPad. A custom EULA saying the AGPL governs the app.

### 7.3 Push

**iOS.** Element X iOS registers an HTTP pusher with `format: event_id_only` at `pushGatewayBaseURL +
/_matrix/push/v1/notify`; matrix.org's Sygnal only holds Element's APNs credentials (team 7J4U792NQT), so the fork needs
its own. Sygnal config (`sygnal.yaml.sample` field names; ids per decision 5):

```yaml
apps:
  com.matchahoneylatte.matcha.ios.prod:
    type: apns
    keyfile: /secrets/AuthKey_<prod-key-id>.p8
    key_id: <prod-key-id>
    team_id: <team-id>
    topic: com.matchahoneylatte.matcha
    platform: production       # TestFlight and App Store builds
    push_type: alert           # unset by default; Apple needs alert for NSE silencing
  com.matchahoneylatte.matcha.ios.dev:
    type: apns
    keyfile: /secrets/AuthKey_<sandbox-key-id>.p8
    key_id: <sandbox-key-id>
    team_id: <team-id>
    topic: com.matchahoneylatte.matcha
    platform: sandbox          # debug builds run from Xcode
    push_type: alert
  com.matchahoneylatte.matcha.android:   # added in O1
    type: gcm
    api_version: v1
    project_id: <firebase-project-id>
    service_account_file: /secrets/fcm.json
```

Serve it over TLS at a public address (Synapse blocks pushes to private IP ranges by default). No Synapse config
change: clients set pushers per device. If calls come back (decision 17), E2EE call ringing goes NSE →
`CXProvider.reportNewIncomingVoIPPushPayload`, which needs the filtering entitlement; Sygnal needs no VoIP topic.
Monitor Sygnal health and APNs errors such as `DeviceTokenNotForTopic`.

**Android today (not confirmed on the device; the flavour is not settled).** `TWO_PANE_NOTES.md:3` says the build
verified on the Fold was the **fdroid** flavour (`assembleFdroidDebug`), which pushes through UnifiedPush with a
distributor app such as ntfy and has no Firebase (:6); `progress.md:50` points testers at the **gplay** APK. Only the
gplay debug build `io.element.android.x.debug` uses Element's `vector-alpha` Firebase project
(`libraries/pushproviders/firebase/src/debug/res/values/firebase.xml`), pusher app id `im.vector.app.android`, and
`https://matrix.org/_matrix/push/v1/notify` (`FirebaseConfig.kt`). Both flavours share the package id and are signed
with the public `app/signature/debug.keystore`. S6 first records which flavour is on the Fold (Settings › Notifications
› Advanced › Push notification provider offers Firebase only in gplay), then runs *Settings › Notifications ›
Troubleshoot notifications* (FirebaseTokenTest and PushLoopbackTest on gplay; on fdroid the list follows the
UnifiedPush distributor).

#### O1 — Android push independence and a shareable build — 2 / 1–1.5

Own Firebase project for the gplay flavour (replace `firebase.xml` values per variant), `BuildTimeConfig.PUSHER_APP_ID_*` and
`FirebaseConfig.PUSHER_HTTP_URL` → the Sygnal above, rename `APPLICATION_ID` and `APPLICATION_NAME` (today "Element X",
shown as "Element X dbg"), a private release keystore, the patched AAR for every ABI (release builds currently keep the
published SDK), and package registration for Android developer verification (enforced in Indonesia since 30 Sep 2026;
the free hobbyist tier covers up to 20 devices). The rename means a fresh install and sign-in on every device, so set up
key backup first. UnifiedPush (already included, `PUSH_CONFIG_INCLUDE_UNIFIED_PUSH = true`) is the no-Firebase
alternative. Until O1, don't share the Android build beyond David's own devices: anyone can build an "update" signed
with the public debug key.

**Desktop:** no push gateway. Notifications come from the running client's sync, including from the tray.

### 7.4 Release cadence

- iOS: a TestFlight build per merged phase during the build-out; afterwards one after each monthly upstream merge. Never
  let 90 days pass without a build (TestFlight expiry).
- Desktop: manual dispatch, batched weekly (decision 13). Each release rebuilds the bundled web app, so desktop lags the
  beta site by up to a week.
- Web: unchanged (`matcha-beta.yml` merges the latest upstream stable tag daily and deploys).
- Protocol changes on any platform go fixture first (§3.4), then web, Android, iOS.

### 7.5 Staying in sync with upstream

**Element X iOS.** Upstream is busy: about 145 commits a month on `develop`, releases tagged `release/YY.MM.N` every 1–2
weeks, SDK bumped about weekly (26.10.12 → .24 in October). Merge **release tags, not develop**, about monthly:
1. Read the new tag's `project.yml` SDK pin; build and publish the patched SDK for it first (§3.1 step 4).
2. Merge the tag into `matcha`; keep the fork's `MatrixRustSDK` line (`path: matrix-rust-components-swift`, or the
   fork URL if S3 chose it) and move the submodule to the new tag (the one-line conflict on every bump; XcodeGen's
   `include:` cannot override `project.yml`'s own package pin). If the tag bumps ElementCall, re-run
   `xcodebuild -resolvePackageDependencies` and check the graph still holds one `matrix-rust-components-swift`.
3. Resolve conflicts in the hot files (6-month upstream commit counts): `project.yml` 123, `AppSettings.swift` 62,
   `RoomFlowCoordinator.swift` 36, `UserSessionFlowCoordinator.swift` 33, `HomeScreen.swift` 24, `RoomScreen.swift`
   22, `ChatsTabFlowCoordinator.swift` 21, `HomeScreenRoomCell.swift` 17, `RoomTimelineItemFactory.swift` 16,
   `NavigationTabCoordinator.swift` 16, `JoinedRoomProxy.swift` 15. Keep the fork's edits there as thin insertion
   points.
4. Unit tests, PreviewTests (re-record the re-skinned ones), a TestFlight build.
Cost: 1–2 agent-days per month (estimate). Spike S9 measures it once early.

**Element Web / Desktop.** The desktop shell rides on the web fork's daily upstream merge; only the copied CI jobs need
occasional manual reconciliation (`GITHUB_TOKEN` cannot push workflow-file changes during automated merges,
`docs/matcha-beta.md`).

**Element X Android.** Not covered by this research. Recommended: the same monthly release-tag merge, rebuilding the
patched AAR when the SDK pin moves.

**Rust SDK.** The two platforms pin different SDK commits on separate Kotlin and Swift release trains; the same patch
file applies to both, so they can drift without cost. Bump both together when convenient. The treadmill ends when §3.5
lands.

### 7.6 Licence and trademark

- Element X iOS and Android are AGPL-3.0 (or Element's commercial licence); Element Web/Desktop AGPL/GPL; Sygnal and
  matrix-rust-sdk Apache-2.0. Every IPA, APK or installer conveys object code (AGPL §6): tag each shipped build, link the
  tag from About/Settings and the store description (as the web beta does with `terms_and_conditions_links`), keep the
  licence notices, mark modified files.
- App Store terms vs AGPL: there is precedent for removal on a copyright holder's complaint (VLC, January 2011) and no
  ruling. Mitigations: a custom EULA naming the AGPL, an email to licensing@element.io, or staying on TestFlight/Ad Hoc.
- Element's trademark policy forbids its marks in the product name, branding or presentation of a fork; "based on
  Element X (AGPL-3.0)" is allowed. The Android build breaks this today (name, `appicon/element`, illustrations,
  element.io URLs in `appconfig/LearnMoreConfig.kt`); fix it in O3 before any external distribution.

#### O2 — App Store listing and review (optional) — 2.5–4.5 / 2–3

§7.2 in App Store Connect, then 1–2 expected rejection rounds (4.1, 4.3 or 1.2 questions) over 1–4 calendar weeks.

#### O3 — Legal hygiene across both mobile apps — 1 / 0.5

Source link per build, modification notices, trademark sweep of Android and iOS, the email to Element.

## 8. Risks, spikes and open questions

### 8.1 Risks and mitigations

| Risk | Effect | Mitigation |
|---|---|---|
| No usable iOS build environment until the macOS upgrade | Agents can write Swift but not compile or test the app; unverified SwiftUI is usually wrong | Upgrade first (I0a, decision 2). Meanwhile do F1, I4 (Swift 6.1 package), I2 (hosted runners), desktop, S1. GitHub `xcode-27` runners as the second path |
| Homeserver discovery points at plain HTTP (§0) | Login by server name fails (apex well-known 404); typing the subdomain sends passwords and access tokens over `http://`; App Transport Security blocks URLSession and WKWebView requests to that host | I0a: apex well-known with the `https://` base URL, subdomain well-known fixed, Cloudflare "Always Use HTTPS"; check the Fold's session; I1 done-when names the string typed at login |
| Filtering entitlement refused or slow | Banners for Matcha events in E2EE rooms; no silent drop for muted accounts; no NSE call ringing; while the NSE asks for it, device and TestFlight provisioning fails | Request on day one; ship it commented out (I1) and restore it on the grant (I13); S5 measures the noise and tests the push-rule mitigation |
| ElementCall pulls `element-hq/matrix-rust-components-swift` itself (`element-call-ios` `Package.swift:44`) | Two URLs for one package identity; a pre-release tag outside ElementCall's range; resolution fails or silently uses the stock SDK | Plain version tags (`26.10.2401`), local-path override like `build-sdk`, resolution proved in S3 before I2 is done (§3.1) |
| Undecryptable Matcha events (inferred, not verified) | A Matcha event the device cannot decrypt is still `m.room.encrypted` and probably shows as an "Unable to decrypt" tile in the chat; likely on Android too | S2 counts UTD tiles in a web-made board room viewed from a fresh device; if noisy, hide UTD items whose cleartext `m.relates_to` is an `m.reference` to a known Matcha anchor (I6), and do the same on Android |
| The patched SDK blocks every upstream SDK bump (no reflection fallback on iOS) | A failed rebase blocks the whole app build | Additive one-hunk patch; rebuild as step 1 of the monthly merge; CI release workflow; upstream it (§3.5) |
| UniFFI checksum mismatch between Swift sources and binary | Crash at launch | Publish both only from one `Release.swift` run; never hand-edit |
| Fork drift (≈145 upstream commits/month, busy hot files) | Monthly conflicts, worse in re-skinned views | Merge release tags monthly; Matcha code in new directories; thin insertion points; S9 rehearsal |
| Snapshot churn (2,224 + 212 PNGs) and LFS storage | Slow reviews, flaky CI; a GitHub fork's LFS pushes count against element-hq's quota | Upstream's `record-snapshots` label workflow; a standalone repository (decision 19); the iOS 26 target keeps one look (decision 16) |
| WKWebView content process killed in the background | Notes engine state lost | Recreate and replay, as Android does for `RenderProcessGone` (I11) |
| iOS suspends the app right after backgrounding; `sendRaw` bypasses the send queue | Lost task edits or note frames | `beginBackgroundTask` around writes and flushes; memory-only unsent edits with a warning |
| Tasks tab fan-out (a server read per joined room, then a store per board room) | Memory and battery on iPhone | 4 at a time, 10-min "no board" cache, stores only for board rooms; measure on David's account |
| App Review rejection (4.1, 4.3, 5.2.1) | No public listing; Beta App Review can also block the external TestFlight group | TestFlight for v1 (decision 4); rebrand fully and work through §7.2 before Beta App Review |
| AGPL vs App Store complaint | Removal | Custom EULA, email Element, or stay off the App Store |
| Element trademark (Android named "Element X dbg" today) | Takedown on external distribution | O3 before sharing anything |
| Android push rides on Element's Firebase and gateway | Silent break if Element rotates keys; immediate break on rename; matrix.org sees push metadata | O1 |
| Desktop build keeps Element's `update_base_url` | Users auto-updated into stock Element | D2 sets it before the first build is handed out |
| Squirrel.Windows prerelease ordering | Updates blocked or misordered | Zero-padded monotonic `VERSION`; S7 |
| SmartScreen / Smart App Control on Windows | Warnings or blocked installs | Decision 12; Store route avoids warnings |
| Signing secrets in a public repo's Actions | Leak | Protected environments; none on `pull_request` |
| matrix-rust-sdk AI policy | An agent cannot land the upstream PRs | David authors them (§3.5) |
| The WhatsApp-iOS references are from memory | Wrong design addendum | Draft §8 from the Human Interface Guidelines, freeze it after S8 on the iPhone (I12 step 0) |
| GitHub `xcode-27` image is a preview | Queues or breakage | Fallback `macos-26` + `setup-xcode`, or Xcode Cloud (25 h/month) |

### 8.2 Spikes (cheap ways to find out)

| # | Question | How | Cost | Decides |
|---|---|---|---|---|
| S1 | Can the installed Xcode 16.4 build the patched xcframework now? | `rustup target add` the two iOS targets; in a matrix-rust-sdk checkout at 8de215a0 with the patch, `cargo xtask swift build-framework --release --target aarch64-apple-ios --target aarch64-apple-ios-sim` | 1–2 h (mostly build) | nothing on the critical path: an early compile check of the patch for Apple targets only. I2 builds on hosted `xcode-27` and starts as soon as the forks exist; nothing built locally with Xcode 16.4 is usable by the Xcode 27 app build |
| S2 | (a) How many "Unable to decrypt" tiles do Matcha events leave in the chat? (b, optional) Why did the include-filter timeline miss custom events on Android when the Rust code suggests it should keep them? | (a) Log in on a fresh device (simulator, no key backup restored) and open a board room made on the web; count UTD tiles in the room timeline and how many relate to Matcha anchors. (b) In the same room, a scan timeline with `.include` of the Matcha message types; count items | 2–3 h after I3 | (a) whether I6 hides Matcha UTD items (and Android should too); (b) only whether a stock-SDK build could ever work |
| S3 | Does the GitHub-hosted `runs-on: xcode-27` image run the fork's unit tests and the SDK release, and does the package graph resolve to one `matrix-rust-components-swift`, the fork's? | Push I1's workflow; time a run. Run `xcodebuild -resolvePackageDependencies` with the local-path setup and with the URL route (§3.1 step 3); read the warnings and `Package.resolved`; build `ElementCallMatrix` | 0.5–1 day | CI choice (fallback macos-26 or Xcode Cloud); path vs URL route; I2 is not done until it passes |
| S4 | (needs the iPhone and I0b) Is the unchanged `engine.js` in a WKWebView fast enough on a real iPhone for the largest real space's full replay? | A test screen loading `engine.html`, replaying the frames of David's biggest space; measure open-to-editable time; kill the content process mid-way | 0.5 day | WKWebView vs JavaScriptCore; full Notes vs read-only v1 (decision 9) |
| S5 | How noisy are pushes without the filtering entitlement, and does a push rule muting `content.m\.relates_to.rel_type == m.reference` help? | Dev build on the real iPhone; move cards and type a note on web; count banners; then add the rule. Note: it also mutes in-room verification and poll responses, and cannot tell the hidden `m.thread` setup reply from a real reply | 0.5–1 day after I13 | whether to ship before Apple grants the entitlement |
| S6 | Which Android flavour is on the Fold, and does it get pushes today? | First record the flavour (Push notification provider list, §7.3), then Troubleshoot notifications | 10 min | urgency and scope of O1 |
| S7 | Does Squirrel.Windows order `1.12.32-matcha.0001` → `.0002` correctly? | Publish two test releases to a scratch R2 prefix; update a Windows VM | 0.5 day | the VERSION scheme |
| S8 | Do the WhatsApp-iOS patterns in §I12 match the current WhatsApp iOS app? | Look at it on the iPhone (week 4); screenshots for §8 | 1 h | freezing the §8 draft |
| S9 | What does a monthly upstream merge really cost? | After I6, merge the next upstream release tag into a throwaway branch; count conflicts and time | 0.5 day | the ongoing budget |

### 8.3 Open questions (not answered by the research)

1. Is David an individual or does he have a registered company? (Apple account type, seller name, Windows signing
   eligibility.) Is he located in Indonesia? (Assumed from the Asia/Jakarta dating; affects Azure signing, Android
   developer verification, possibly PSE registration.)
2. Closed group only, or a public App Store listing? Is registration on matchahoneylatte.com open (triggers 5.1.1(v))?
3. Which host runs Sygnal, and which domain?
4. Is "macOS" satisfied by Electron plus the iPad app, or does David want something native later? (None exists.)
5. Should desktop eventually get the WhatsApp-style restyle (a web-fork redesign, not estimated)?
6. Does Android show notifications for Matcha events in E2EE rooms today? (Not researched; check alongside S6.)
7. Intel Macs, Windows arm64, Linux arm64 on day one, or later?

Calls on iOS: decision 17.

## 9. Timeline and checklist

### 9.1 Timeline (estimate)

Assumes 2–3 parallel agent tracks, David reviewing daily, Apple enrolment approved within two days, and the Mac upgraded
in week 0. The Android port's one-day pace does not carry over: each iOS change goes through slower builds and CI.

| Week | Shared / ops | iOS | iPad / Mac | Desktop | Gates |
|---|---|---|---|---|---|
| 0 | Decisions §1.4; Apple enrolment; entitlement request; homeserver discovery fix (I0a); S6 | Create `DavidN8/element-x-ios` (decision 19) and the two SDK forks; I4 protocol package (Swift 6.1, this Mac); I2 starts on hosted runners; S1 (optional) | — | D0 PWA; D2 variant | I0a: macOS 26.6 + Xcode 27 installed; iPhone ordered |
| 1 | F1 fixtures (+ the I4 fixture runner); Sygnal deployed | I0b done; I1 rebrand + CI; I2 done after S3; I3 proxy; I12 §8 drafted from the HIG | — | D1 rebrand; D3 CI; D6 R2 | I0b: team ID, App IDs, APNs keys |
| 2 | — | I5 store; I6 threads; S2; I12 palette + chat list | — | D4 macOS signing; D5; D7 QA → **first desktop beta** | Developer ID cert |
| 3 | — | I7 board; I9 reminders; I12 chat screen | — | S7; weekly releases begin | — |
| 4 | — | I8 Tasks tab; I10 tabs + Channels; S4; S8 → §8 frozen | — | — | real iPhone in hand |
| 5 | — | I11 Notes; I13 push (S5) | — | — | filtering entitlement (unknown) |
| 6 | O1 Android independence | I12 finish + snapshots; I14 → **internal TestFlight**, then Beta App Review → **external TestFlight** | P1 iPad; M1 Mac group | — | Beta App Review (1–2 days) |
| 7–8 | O3; O2 if chosen | S9 then first monthly upstream merge | P2 if wanted | monthly cadence | App Review (if O2) |

### 9.2 Checklist

David:
- [ ] Decisions 1–19 in §1.4 answered (or defaults accepted), including the legal ones (18) and the repository type (19) before the first snapshot recording
- [ ] Apple Developer Program (individual) enrolled; team ID recorded in the fork's `app.yml` (I0b)
- [ ] Filtering entitlement requested at https://developer.apple.com/contact/request/notification-service
- [ ] Mac upgraded to macOS Tahoe 26.6+; Xcode 27 + iOS 27 runtime installed and selected (I0a)
- [ ] Homeserver discovery over HTTPS: apex well-known, subdomain well-known `base_url` → `https://`, Cloudflare "Always Use HTTPS"; the Fold's session checked and moved to `https://` (I0a)
- [ ] Test iPhone on the current iOS bought; iPad optional
- [ ] App IDs `com.matchahoneylatte.matcha{,.nse,.shareextension}`, app group, two APNs keys created (I0b); the NSE filtering capability only after Apple grants it
- [ ] Icon artwork and product name supplied
- [ ] Privacy, terms, support and source-code pages on matchahoneylatte.com
- [ ] DNS for `push.` and `packages.matchahoneylatte.com`; secrets placed on the Sygnal host and in GitHub environments
- [ ] Android flavour on the Fold recorded, then Troubleshoot notifications run (S6)
- [ ] Windows signing route chosen before any public desktop build (decision 12)
- [ ] Upstream FFI PRs to matrix-rust-sdk written personally (later, §3.5)

Agent:
- [ ] F1 fixtures in `docs/matcha/fixtures/` + runners in Android, web and iOS, with checksum and drift checks
- [ ] I2: `DavidN8/matrix-rust-sdk` branch `matcha/26.10.24`; `DavidN8/matrix-rust-components-swift` with `matcha-release.yml` and release `26.10.2401`; submodule at `matrix-rust-components-swift/`; resolution proved in S3; mocks from the fork (S1 optional)
- [ ] Week 0: `DavidN8/element-x-ios` created (standalone, decision 19); I4 in it
- [ ] I1: branch `matcha` from `release/26.10.7`; iOS 26 target; app.yml, entitlements (NSE filtering commented out),
      AppSettings URLs, telemetry off, flags (`threadSubscriptions: false`, Labs Threads row hidden), upstream's
      unit-tests and record-snapshots workflows kept
- [ ] I3–I11 in order, each phase ≤500 production lines per PR, with ported tests and previews; I12 in parallel
- [ ] Every row of §4.1 has a test or an acceptance check that proves iOS avoids it
- [ ] I13 Sygnal + `pushGatewayBaseURL`, filtering entitlement restored once granted; I14 TestFlight internal then
      external (Beta App Review), Source code row, trimmed privacy manifest
- [ ] P1 iPad; M1 Mac TestFlight group
- [ ] D0–D7: manifest, rebrand, variant, `update_base_url`, `element://` patches, `matcha-desktop.yml` (web app from the
      `:beta` image), signing, R2, QA with an auto-update round trip
- [ ] O1 Android push independence and release build; O3 legal sweep
- [ ] Follow-ups in the Android repo: correct `MATCHA_PORT_PLAN.md` §3.1 Path B and the rust-sdk README line (§3.2);
      add `WHATSAPP_DESIGN.md` §8; hide Matcha UTD items there too if S2 finds them noisy

## Appendix A — where the research disagreed, and the resolution

| Topic | Positions | Resolution |
|---|---|---|
| Build tools on this Mac | Task text: command-line tools only. Report 1: Xcode 16.4 installed, not selected, no runtimes. Reports 2, 5: `xcodebuild` fails | Checked 2026-10-24: Xcode 16.4 selected, iOS 18.6 runtime, Swift 6.1.2. All agree it cannot build Element X iOS (Swift tools 6.2, iOS 26 SDK, Xcode 27) |
| Can macOS 15.3.1 run Xcode 27 | Report 1: probably not (assumed). Reports 2, 5: Apple's support page says Tahoe 26.6+ | Upgrade required |
| Patched SDK needed from the start | Report 2: start on the published SDK via the include-filter timeline. Report 1: keep that as a compile-flag fallback. Report 3: always patched | Always patched, no flag, because Android's commit `4394cbf8e3` saw the filtered timeline miss cards, names and doc frames on the real server (§3.2). S2 part (b) optional |
| Notes engine host | Reports 1, 2: WKWebView with the unchanged engine. Report 3: JavaScriptCore + ~20 lines of shims, WKWebView as fallback | WKWebView first (unchanged artifact, JIT, an existing bridge pattern); JSC only if S4 shows lifecycle problems |
| iOS effort | Report 1: 37 (30–45) agent-days, 14–20 weeks ("an experienced iOS engineer"; it did not say whether that engineer knows Element X). Report 3: 22–34 agent-days, 30–37 weeks (senior engineer new to Element X) | Per-phase sum here: 30–46 agent-days for iPhone (report 3 left out parts of push, distribution and fixtures). Human: 24–36 weeks for a senior iOS engineer new to Element X; report 1's 14–20 is kept only as its own figure |
| Does ElementCall use the components package | Report 1: yes, so swapping the URL conflicts. Report 2: no, ElementCall uses `matrix-rust-rtc`, swapping is safe. | Report 1 is right: `element-call-ios` 0.1.0-rc.11 `Package.swift:44` depends on `element-hq/matrix-rust-components-swift` `"26.10.15"..<"100.0.0"` (verified with `gh api`). Plain version tags, local-path override, resolution proved in S3 (§3.1) |
| Desktop base and effort | Report 5: fork element-desktop, skip Seshat, ~4 agent-days. Report 4: element-desktop is merged into `apps/desktop`, Seshat is prebuilt, 9–13 agent-days | Report 4 (verified `apps/desktop` in the fork); its estimate includes the rebrand, signing and QA that report 5 left out. The per-phase sum here (D0–D7) is 8–13 |
| `xcode-27` runner | Report 1: a custom self-hosted label forks can't use. Reports 2, 5: GitHub-hosted preview image (runner-images readme) | Treat as GitHub-hosted; S3 confirms; fallbacks listed |
| Tasks tab navigation | Report 1: plain stack like Search (full width free). Report 3: split with column visibility | Stack in I8 (iPhone), split behaviour in P1 (iPad) |
| Windows signing default | Report 4: SSL.com + eSigner. Report 5: unsigned beta or Microsoft Store | Unsigned for the private beta; choose SSL.com or the Store before public builds (decision 12) |
| OIDC / associated domains | Reports 1, 5: unknown, may need apple-app-site-association | Report 4 checked: password-only today. Drop associated domains in v1 |
| Root-tab titles | Report 3: large titles (WhatsApp iOS). Upstream forces inline on glass (`HomeScreen.swift:33`) | Large titles by default (decision 10); one line to revert |
| Fixture location | Report 2: open question. Report 3: `docs/matcha/fixtures/` | `element-x-android/docs/matcha/fixtures/` (decision 14) |
| `MATCHA_PORT_PLAN.md` §3.1 Path B | Report 2 found it cannot work for custom events | Noted as stale; fix in a follow-up PR |
