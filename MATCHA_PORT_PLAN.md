# Matcha workspace port plan — element-web `matcha-beta` → element-x-android

Written 2026-10-24 (Asia/Jakarta) as the handoff brief for the implementing agent. Everything in
"Verified" sections was checked against the actual code and the actual SDK binary on this machine;
everything marked "assumed" was not and must be checked before relying on it.

## 0. Context and sources

| What | Where |
|---|---|
| Web fork (source of truth for behaviour and wire protocol) | `~/Code/mozart-dm-group/element-web`, branch `matcha-beta` = `dcc137bcb1`, 30 commits past upstream `v1.12.32`, remote `bouyant-dev/element-web` |
| Web handoff notes (read first; deployment, protocol summary, design decisions) | `~/Code/mozart-dm-group/element-web/bot.md`, `docs/matcha-beta.md`, `tools/matcha-reminder-bot/README.md` |
| Web protocol code | `apps/web/src/utils/matcha/{threads,reminders,documents,documentCatalog,spaceDocuments,encryption,markdownCommands}.ts` |
| Web view models (behaviour to mirror) | `apps/web/src/viewmodels/room/{RoomThreadTabs,KanbanBoard,RoomDocs,SpaceDocs,RoomReminder}ViewModel.ts` (+ `.test.ts` next to each: port these cases) |
| Web shared UI (for copy and layout, not code) | `packages/shared-components/src/room/{RoomThreadTabs,KanbanBoard,RoomDocs,RoomReminder,SpaceDocsSetup}/` and the `matcha_*` blocks in `packages/shared-components/src/i18n/strings/en_EN.json` |
| This Android fork | `~/Code/element-x-android`, `develop` = `8bfafba4a2` = upstream `v26.10.0` + 5 local two-pane commits (see `TWO_PANE_NOTES.md`), remote `DavidN8/element-x-android` |
| Rust SDK binding in use | `org.matrix.rustcomponents:sdk-android` **26.10.20** (`gradle/libs.versions.toml`, pinned `strictly`) |
| Deployed homeserver / bot | `matchahoneylatte.com`; bot `@matcha-reminder:matchahoneylatte.com` (from `bot.md`) |

**Web working tree is dirty.** Uncommitted changes in the web repo extend the Kanban protocol
(`show_to_room` card field, participant list per card, "filter by person" on the Personal board, new
strings) and an e2e test. Treat them as *proposed*, not shipped: Android reads `show_to_room` if
present (default `true`) and otherwise ignores the WIP. Ask David whether to land the web WIP first.

Branch for this work: `matcha` off `develop` (keeps the two-pane commits). Follow `AGENTS.md`
(three-module features, Presenter/View/State/Event files, `temporary.xml` strings, `@PreviewsDayNight`,
2026 licence headers, ≤500 production lines per PR → one PR per phase below, split further if needed).

## 1. What the web fork added (feature inventory)

All features are gated by the web config flag `matcha_thread_tabs`; the bot by `matcha_reminder_bot`.

1. **Favourites first** — the Favourites chip is the first room-list filter. (`RoomListViewModel.ts` map order.)
2. **Room workspace toolbar** — one horizontal row above the timeline in every joined room: `General` | `Board` | up to 4 active named-thread tabs (newest root first) | `New thread` | `Thread history` (all loaded threads incl. archived, with "load more") | `More` (Space docs, Earlier chat document, Remind me). Selecting a tab shows that thread's timeline in the main area; General shows the room timeline. (`RoomThreadTabsViewModel.ts`, `RoomView.tsx` diff.)
3. **Named threads** — create a thread from the toolbar with a name; rename; archive/restore. Metadata is shared with all members via encrypted timeline events, never room state, never redaction. (`utils/matcha/threads.ts`.)
4. **Kanban board per room** — `todo | doing | done` columns, cards with title, description, optional local deadline, archive/restore, "Load older cards". **Every native thread projects to exactly one card** (card title = thread name; renaming a linked card renames the thread; archiving is shared). Standalone legacy cards still render. Board creation needs room-state permission once. (`KanbanBoardViewModel.ts`.)
5. **Personal page** (`#/personal`) — a permanent sidebar entry above Home: all cards from all currently joined rooms with boards, labelled by source room, opening the source chat/thread, editing the shared cards in place. Full-width layout (room list hidden). (`LoggedInView.tsx`, `SpacePanel.tsx`, `MatrixChat.tsx` diffs.)
6. **Reminders via bot** — "Remind" on a card / "Remind me" in More: dialog with *Remind me in* (N + s/m/h/d) or *Remind me at* (local date-time, timezone shown) plus a message; sends a `!remind …` text message to the room after inviting the bot (explicit confirmation) and waiting for it to join. The bot's notice is the only confirmation. (`utils/matcha/reminders.ts`, `RoomReminderViewModel.ts`, `tools/matcha-reminder-bot/`.)
7. **Space Docs** — per Matrix Space, a list of named Markdown documents co-edited in real time (Yjs CRDT over encrypted custom events), with a reserved `main` document and an encrypted catalog of names; Chats/Docs toggle in the sidebar; a one-time admin "Enable encrypted Docs" setup; "Earlier chat document" = read-only legacy per-room doc. (`documents.ts`, `documentCatalog.ts`, `RoomDocsViewModel.ts`, `SpaceDocsViewModel.ts`.)
8. Infra not ported: GitHub workflow for upstream merges, Docker image, the reminder bot itself (server-side, unchanged).

## 2. Wire protocol (must stay byte-compatible with the web client)

All custom timeline events are sent through the normal send path, so in encrypted rooms the SDK
encrypts them (relation stays in cleartext as usual). Validation rules below are the web's; apply
the same ones when *reading* (drop invalid revisions silently) and when *writing*.

### 2.1 Thread metadata — `com.matchahoneylatte.thread.metadata`

```json
{ "version": 1, "name": "Ship beta", "archived": false,
  "m.relates_to": { "rel_type": "m.reference", "event_id": "$threadRoot" } }
```
- `name` and `archived` are independent optional patches; a patch carries only the fields being changed.
- `name`: trimmed, 1–100 chars. `archived`: boolean.
- **Read**: fetch `m.reference` relations of the root, newest first, at most 3 pages of 100; ignore redacted / wrong type / `version != 1` / wrong relation; take the first valid `name` and the first valid `archived` seen (independently). `archiveExplicit = true` when an `archived` patch (or legacy state) exists.
- Fallback name: root body with whitespace collapsed, cut to 100; else the root event id.
- Legacy fallback for `archived` only: state event `com.matchahoneylatte.thread_archive`, state key = root id, content `{ "archived": true }`. Read only, never written.
- **Create named thread**: send `m.room.message` `{msgtype:"m.text", body:<name>}` → root id; then send `thread.metadata {version:1, name, archived:false}` **as a thread reply** (`m.relates_to: {rel_type:"m.thread", event_id:root, is_falling_back:true, "m.in_reply_to":{event_id:root}}`). That hidden reply makes the server aggregate the thread before any human reply; it is *not* metadata (readers only look at `m.reference` relations). If the second send fails, surface "message sent, thread setup failed".
- Permission (`canManageThreads`): joined ∧ may send `thread.metadata` ∧ (room unencrypted ∨ may send `m.room.encrypted`).
- Thread root validation (`resolveThreadRoot`): id starts with `$`, same room, not a state event, is a thread root (SDK thread list contains it, or it has ≥1 `m.thread` relation).

### 2.2 Kanban — three event types

State `com.matchahoneylatte.kanban`, state key `""`:
```json
{ "version": 1, "event_id": "$boardRoot" }
```
Timeline root `com.matchahoneylatte.kanban.board`: `{ "version": 1 }` (one per board; the state event points at it).

Card revision `com.matchahoneylatte.kanban.card` (always a *complete* card, last revision wins):
```json
{ "version": 1, "card_id": "thread-<sha256hex of threadId>", "title": "…", "description": "…",
  "column": "todo", "archived": false, "thread_id": "$threadRoot",
  "due_at": 1767225600000, "reminder_requested": { "event_id": "$cmd", "at": 1767225600000 },
  "show_to_room": true,
  "m.relates_to": { "rel_type": "m.reference", "event_id": "$boardRoot" } }
```
- `card_id` `/^[a-zA-Z0-9-]{1,80}$/`; `title` non-blank ≤200; `description` ≤4000; `column` ∈ `todo|doing|done`; `archived` boolean; `thread_id` optional `$…`; `due_at` ms epoch, `0 < x ≤ 253402300799999`; `reminder_requested` optional `{event_id, at}`; `show_to_room` optional boolean (web WIP, default `true`).
- **Resolution**: relations of the root, newest first, 100 per page; keep the first (= newest) revision per `card_id`; older pages on demand ("Load older cards"); decryption failure of any revision ⇒ board flagged `unreadable` (shown as a warning, not an error).
- **Thread projection** (`projectThreads`): for every thread root known to the room (SDK thread list ∪ every `thread_id` referenced by a card ∪ legacy archive state keys ∪ the thread the user navigated from), produce one card: `id` = newest linked revision's `card_id` else `"thread-" + sha256(threadId)`; `title` = thread metadata name; `archived` = `metadata.archiveExplicit ? metadata.archived : (linked.archived ?? metadata.archived)`; `description/column/dueAt/reminderRequested/showToRoom` from the newest linked revision (defaults `""`, `todo`); older linked revisions with *different* `card_id`s are listed under "Earlier linked cards". Cards without `thread_id` stay as standalone cards.
- **Writes** (`write`): require joined ∧ may send `kanban.card` ∧ encryption condition; re-read the state pointer and refuse if it changed; for a linked card refuse if another card already links the same thread; if the thread name changed, patch thread metadata first, then re-read it and use its name/archived; then send the full revision. Archive/restore of a linked card also patches `archived` on the thread. Move = revision with new `column`.
- **Add card** (no thread chosen): create a named thread from the title (needs `canManageThreads` ∧ may send message), then write the linked card. Title must be a valid thread name.
- **Create board**: needs may send `kanban.board` ∧ may send state `kanban`; send root, then state pointer.
- **Remind**: run the reminder flow with `message = title`, `at = dueAt`, `threadId`; on success write a revision with `reminder_requested = {event_id: <sent command id>, at}`. A deadline alone never schedules anything; changing a deadline never reschedules.
- **Personal / "all" board**: union over all joined rooms that have a board; cards carry `roomName`; `canEdit` per source room; `hasMore` if any room has more pages; leaving a room drops its cards immediately.

### 2.3 Reminders (text commands to the bot)

- Preconditions: joined ∧ may send messages. Bot membership `join` → proceed; `invite` → tell user to wait; otherwise require `canInvite`, confirm with the user, `invite`, then tell user to wait (do not send yet).
- If a `threadId` is given: validate the root, append the thread permalink to the message if absent.
- Draft validation (again at send time): `in` mode `duration` `/^[1-9]\d{0,8}$/`, unit `s|m|h|d`; `at` mode local `YYYY-MM-DDTHH:mm[:ss]` that round-trips (rejects DST gaps); resulting instant `> now`, `≤ now + 365d`; message trimmed 1–2000 chars.
- Send as `m.text`: `!remind <N><unit> <message>` or `!remind at <ISO-8601 UTC with ms, e.g. 2026-12-01T09:00:00.000Z> <message>`.
- Bot acknowledges with an `m.notice`: `Scheduled r-<16 hex> for <ISO UTC>. Cancel with !remind cancel r-….` Other commands: `!remind list`, `!remind cancel <id>`. Delivery is an `m.text` `Reminder for @user: <text>` with `m.mentions`. Nothing is confirmed until the notice arrives; the UI only says "requested".

### 2.4 Docs — `com.matchahoneylatte.docs.update` (Yjs over Matrix)

Anchor = the room's `m.room.create` event id (relation target). Each frame:
```json
{ "version": 1, "doc": "main" | "<uuid v4>" | "catalog", "update_id": "<uuid>", "kind": "update" | "chunk" | "commit",
  "chunks": 1, "chunk": 0, "data": "<base64 of a Yjs binary update, ≤16 KiB raw>",
  "m.relates_to": { "rel_type": "m.reference", "event_id": "$create" } }
```
- `update_id` `/^[a-zA-Z0-9._-]{1,128}$/`; `chunks` 1–256; `kind=update` ⇒ `chunks==1 && chunk==0`; `kind=chunk` ⇒ `chunks≥2`; `kind=commit` ⇒ no `data`/`chunk`, `chunks≥2`; base64 strictly validated.
- Dedupe key = `sender + ":" + update_id`. An update is applied only when committed and all parts present; parts are concatenated in index order; validated by applying to a probe `Y.Doc` first; a conflicting re-send of a part with different bytes is `invalid_update`.
- Document `Y.Text` key `"markdown"`. Catalog = doc id `"catalog"`, `Y.Map` `"documents"`: `id → {title (1–100), createdAt (ms)}`; `main` is implied when absent (`createdAt 0`, no title → shown as "Space notes").
- **Load**: replay *every* page of `m.reference` relations of the anchor before enabling edits; a stalled pagination token is `load_error`; Yjs `pendingStructs/pendingDs` non-null after full history ⇒ `incomplete_update` (editing disabled). Decryption failure ⇒ `decrypt_error`.
- **Send**: debounce 500 ms, merge buffered updates, split into frames, transaction ids `matcha-doc.<uuid>.<index>`, retry the same ids; "Saved" only after server ack. Unsent edits are memory only (warn, offer download).
- `canEditRoomDocument`: joined ∧ may send `docs.update` ∧ encryption condition. Catalog writes additionally require the Space to be encrypted.
- **Space setup** (admin, confirmed once): encryption algorithm `m.megolm.v1.aes-sha2` present; power levels `events["com.matchahoneylatte.docs.update"] == 0` and `events["m.room.encrypted"] == 0`. Setup = enable encryption (if absent), wait for the state to sync, read current PLs fresh, set only those two keys, wait for sync. Needs may send state `m.room.encryption` ∧ `m.room.power_levels`.
- Space resolution: only the *explicitly selected* joined Space (web `spaceForRoomDocuments`), never a guessed parent. Per-chat legacy docs stay anchored at the chat's own create event, read-only.

## 3. SDK capability audit (verified against `sdk-android-26.10.20.aar` by `javap`, and Rust `main` sources)

| Need | Available? | How |
|---|---|---|
| Send custom timeline event, get its event id, encrypted in E2EE rooms | **Yes** | `Room.sendRaw(eventType: String, contentJson: String): String` (returns event id; goes through `Room::send_raw`, same encrypt path as `send`). Not wrapped in `JoinedRoom` yet. |
| Send custom / arbitrary state event | **Yes** | `Room.sendStateEventRaw(eventType, contentJson, stateKey): String`. Not wrapped yet. |
| Read custom state event (`com.matchahoneylatte.kanban`) | **Only partially** | `Room.stateEvents(StateEventType.Custom(type))` → `RoomStateEvent.contentJson`, **but** the FFI doc says custom types are empty unless in sliding-sync `required_state`, and `ClientBuilder` exposes no way to add them. Treat the store as a hint; the scan timeline (below) is the source of truth. |
| Read `m.room.power_levels` JSON incl. custom keys | **Yes** | `stateEvents(RoomPowerLevels)` → `contentJson` (standard type, always synced); `RoomPowerLevels.events(): Map<FfiTimelineEventType, Long>`; permission checks `canOwnUserSendMessage(MessageLikeEventType.Custom(..))` / `canOwnUserSendState(StateEventType.Custom(..))`. Android `RoomPermissions.canOwnUserSendMessage(MessageEventType.Other(type))` already maps to Custom. |
| Enable encryption on a room/space | Yes | `JoinedRoom.enableEncryption()`; `getJoinedRoom(spaceId)` works for joined spaces (RustRoomFactory keys on membership only). |
| Enumerate `m.reference` relations of an event, all pages | **Yes** | `Room.loadOrFetchEventWithRelations(eventId, listOf(RelationType.REFERENCE))` → `EventWithRelations(event, relatedEvents)`; the SDK loops `/relations` (recurse, 256/page) until exhausted. |
| **Read the content of a custom related event** | **No** | `TimelineEvent.content()` throws `ClientException` for custom message-like types (`event.rs`: "Unsupported Event Type"). Only `eventId()`, `senderId()`, `timestamp()`, `threadRootEventId()` work. **This is the main gap.** |
| Custom message-like events in a `Timeline` | **Yes** | They become `TimelineItemContent.MsgLike(kind = MsgLikeKind.Other(eventType))`; Android maps them to `UnknownContent`; the decrypted full event JSON is available from `EventTimelineItem.timelineItemDebugInfoProvider().originalJson` (FFI `LazyTimelineItemProvider.debugInfo().originalJson`, also `latestJson()`). Custom *state* events become `StateContent(OtherState.Custom(eventType))` with the same debug JSON. |
| Timeline include/exclude filter by custom type | **Yes** | `TimelineFilter.EventFilter(TimelineEventFilter.Include(listOf(TimelineEventCondition.EventType(TimelineEventType.MessageLike(MessageLikeEventType.Custom("…"))))))`; also `TimelineEventCondition.AnyCustomMessageLikeEvent`, `AnyCustomStateEvent`, `EventType(TimelineEventType.State(StateEventType.Custom("…")))`. Android's `RustTimelineEventFilterFactory` currently excludes only `AnyCustomStateEvent`. |
| Live stream of new custom events with JSON | Yes | `Room.subscribeToMessageLikeEvents(MessageLikeEventType.Custom(type), listener)` → `RoomMessageLikeEvent{eventType, eventId, sender, timestamp, contentJson, encryptionInfo}`. New sync events only (assumed; verify). |
| Native threads | Yes | `JoinedRoom.threadsListService` (items, paginate, reset), `createTimeline(CreateTimelineParams.Threaded(rootId))`, `FeatureFlags.Threads` (labs, default off), `ThreadedMessagesNode`, `ThreadsListNode`, `BaseRoom.threadRootIdForEvent`. |
| Send a thread reply with custom type | Yes | `sendRaw` with the `m.thread` relation in the content. |
| Invite user / membership of bot | Yes | `JoinedRoom.inviteUserById`, `BaseRoom.getUpdatedMember(userId)`, `RoomPermissions.canOwnUserInvite()`. |
| Event permalink | Yes | `BaseRoom.getPermalinkFor(eventId)`. |
| Spaces | Yes | `SpaceService.joinedParents(roomId)`, `topLevelSpacesFlow`, `spaceRoomList(id)`; `features/space` has `SpaceFlowNode` (Root/Settings/Leave/CreateRoom/AddRoom). |
| Decryption retry for history | Automatic | The Rust timeline retries UTDs when keys arrive; the web's manual `decryptMatrixEventWithRetry` hack is not needed. `Timeline.retryDecryption(sessionIds)` exists if ever needed. |
| Local SDK build | Yes | Dropping `libraries/rustsdk/matrix-rust-sdk.aar` switches **debug** builds to it automatically (`libraries/matrix/impl/build.gradle.kts:29-54`). |
| Unknown custom message-like events in the chat today | **Rendered as "Unsupported event" tiles** (`TimelineItemUnknownView`). The fork's card/metadata/doc events would pollute every chat and thread on Android until excluded. Fix in Phase 0 regardless of anything else. |

### 3.1 Consequence: two read paths, pick one per phase

**Path A — scan timeline (no SDK fork). Chosen for v1 (decision 2026-10-24).** Per room, one extra SDK `Timeline`
(`TimelineFocus.Live`, `Include` filter on the four matcha message types + the two matcha state
types, read receipts disabled, `internalIdPrefix = "matcha_"`, never marks read). Parse
`originalJson` of every item; paginate backwards (50 events/page in `RustTimeline`) up to a page cap
with "Load older" for more; live updates arrive as timeline diffs; UTD items set `unreadable`.
One scan feeds thread names, the board and (in a Space) the docs frames. Board root resolution:
the newest `com.matchahoneylatte.kanban` state item seen in the scan, else the single
`kanban.board` root seen, else "no board". Ceiling: cost grows with room history, not with card
count, and a board created long ago needs paging back to it. Mark with
`// ponytail: scan timeline; switch to Path B if boards in busy rooms get slow`.

**Path B — FFI patch (upgrade path, exact web parity).** Add to
`bindings/matrix-sdk-ffi/src/event.rs` a method on `TimelineEvent` returning the serialised event
(`serde_json::to_string(&*self.0)`; `AnySyncTimelineEvent` is `Serialize`), optionally a
`Room.getStateEventFromServer(type, key)`; build with
`matrix-rust-components-kotlin/scripts/build.sh -p <rust-sdk checkout at the 26.10.20 commit> -m sdk -t aarch64-linux-android -o <this repo>/libraries/rustsdk` and rename to `matrix-rust-sdk.aar`. Then
`loadOrFetchEventWithRelations(root, [REFERENCE]).relatedEvents.map { it.rawJson() }` returns every
revision in one call, and the state pointer can be read fresh before each write (the web does
this). Needs Rust toolchain + NDK + `cargo-ndk`; must stay API-additive because `element-call-matrix`
is compiled against the pinned SDK (see the `strictly` comment in `libs.versions.toml`).

## 4. Android architecture

```
features/matcha/api      MatchaEntryPoint(s), MatchaConfig consumers, data classes used by home/messages
features/matcha/impl     protocol (pure Kotlin) + store + presenters/views/nodes
features/matcha/test     fakes (FakeMatchaRoomStore, FakeReminderService …)
libraries/matrix/api     + raw-event methods on JoinedRoom, + CreateTimelineParams.MatchaEvents
libraries/matrix/impl    + Rust wrappers for the above
libraries/matrix/test    + FakeJoinedRoom additions
appconfig                + MatchaConfig (bot user id, event type constants, limits)
libraries/featureflag    + FeatureFlags.MatchaWorkspaces (default true in this fork), Threads default true
```

Key pieces in `features/matcha/impl`:

- `protocol/` — `MatchaEvents` (type constants), `ThreadMetadataPatch/ThreadMetadata` + parser, `KanbanCard` + parser/serialiser, `DocFrame` + parser/serialiser, `ReminderCommand` builder, `ThreadCardId` (`"thread-" + sha256hex`). Pure functions over `JsonObject`; port the web `.test.ts` cases 1:1.
- `store/MatchaRoomStore` (`@SingleIn(RoomScope)` or a `SessionScope` map keyed by room id) — owns the scan timeline, exposes `StateFlow<MatchaRoomSnapshot>` (thread metadata map, board root, card revisions newest-first, doc frames, `unreadable`, `hasMore`), `loadOlder()`, `refresh()`. Writers call `JoinedRoom.sendRaw` and rely on the live diff to update the snapshot (web calls `reload()` after each write; here the timeline diff is the reload).
- `threads/` — `ThreadMetadataService` (read via store, write via `sendRaw`, `createNamedThread`, `canManageThreads`), plus the toolbar presenter (`RoomWorkspaceBarPresenter/State/Event/View`).
- `board/` — `KanbanBoardPresenter` (room or all-rooms mode), `KanbanBoardView` (three columns; on phone a column pager or vertical sections; compact cards with status dropdown, source-room chip in all-rooms mode; archived toggle; editor sheet with title/description/deadline/`show to room`), `KanbanBoardNode` (`@ContributesNode(RoomScope::class)`), `PersonalBoardNode` (session scope).
- `reminders/` — `ReminderService` (invite/wait/send), `RoomReminderPresenter` + bottom-sheet view (mode toggle, duration+unit, date-time picker with timezone label, message), result `{eventId, at, message}`.
- `docs/` — Phase 5 only.

Where it plugs in:

- `features/messages/impl/.../MessagesView.kt`: below the top bar (both `MessagesViewTopBar` and `ThreadTopBar` modes) render `RoomWorkspaceBar` when the flag is on and the room is joined. `MessagesFlowNode` gets `NavTarget.Board(initialThreadId: ThreadId?)` → `KanbanBoardNode`, and `NavTarget.SpaceDocs(...)` forwarding to the space flow. Thread tab tap → existing `navigateToThread`; General → pop to the live timeline.
- `features/home/impl/HomeNavigationBarItem`: add `Personal` (icon `CompoundIcons.Compose()`), rendering `PersonalBoardView` in `HomeView` when selected; tapping a card calls the existing `onRoomClick(roomId, eventId=null)` path then the thread (add a `HomeEntryPoint.Callback.navigateToThread(roomId, threadId)` or reuse the permalink click handling in `LoggedInFlowNode`).
- `features/space/impl/SpaceFlowNode`: `NavTarget.Docs(documentId)` + a Docs entry in `SpaceViewTopBar` (Phase 5).
- `libraries/matrix/impl/.../RustTimelineEventFilterFactory`: exclude the matcha message-like types (and the two matcha state types) from the normal chat/thread/pinned timelines.

Config: `appconfig/MatchaConfig.kt` with `REMINDER_BOT_USER_ID: UserId? = UserId("@matcha-reminder:matchahoneylatte.com")`, event type strings, limits (name 100, title 200, description 4000, 365 days, 2000 chars, 16 KiB chunks, 256 chunks). The web reads these from `config.json` at runtime; on Android a compile-time constant is enough for this fork (one deployment).

## 5. Phases

Each phase is one PR against `matcha`, with Turbine presenter tests and parser unit tests; screenshot previews for every new View state. Do not record screenshots locally (CI label does not work from a fork; see `TWO_PANE_NOTES.md`).

### Phase 0 — Plumbing (no visible feature)
Files: `libraries/featureflag/api/.../FeatureFlags.kt`, `appconfig/.../MatchaConfig.kt`, `libraries/matrix/api/.../room/JoinedRoom.kt` + `CreateTimelineParams.kt`, `libraries/matrix/impl/.../room/JoinedRustRoom.kt` + `RustTimelineEventFilterFactory`/`TimelineEventFilterFactory.kt`, `libraries/matrix/test/.../FakeJoinedRoom.kt`.
1. `FeatureFlags.MatchaWorkspaces` (`key = "feature.matcha_workspaces"`, `defaultValue = { true }`, `isFinished = false`, `isInLabs = true`) and flip `Threads.defaultValue` to `{ true }`.
2. `JoinedRoom.sendRaw(eventType: String, content: JsonObject): Result<EventId>`, `sendStateEventRaw(eventType, content, stateKey): Result<EventId>`, `getStateEventJson(type: StateEventType, stateKey): Result<JsonObject?>`, `getRelatedEventIds(eventId, relationType): Result<List<RelatedEventRef(eventId, senderId, timestamp)>>`, `observeMessageLikeEvents(eventType): Flow<RawRoomEvent(json, sender, eventId, timestamp)>`.
3. `CreateTimelineParams.MatchaEvents` → `TimelineFocus.Live(hideThreadedEvents = false)` + `Include` filter listed in §3.1, read receipts `DISABLED`, `internalIdPrefix = "matcha_"`.
4. Exclude the matcha types from every other timeline (`TimelineEventFilterFactory`); keep `AnyCustomStateEvent`.
5. Done when: a debug build shows no "Unsupported event" tiles in a room that already has web-created cards; a unit test proves the exclude list contains the four message types; `sendRaw` round-trips in `MatrixRoomTest`-style fake.

### Phase 1 — Favourites first
`features/home/impl/.../filters/RoomListFilter.kt`: move `Favourites` to the first enum entry (chip order follows `entries`; `incompatibleFilters` unaffected). Update `RoomListFiltersStatePreviewParam` if order-sensitive, and `DefaultFilterSelectionStrategyTest` expectations. One commit.

### Phase 2 — Named threads + room workspace bar
Files: `features/matcha/{api,impl,test}` (`protocol/ThreadMetadata*`, `store/MatchaRoomStore`, `threads/*`), `features/messages/impl/.../MessagesView.kt`, `MessagesFlowNode.kt`, `MessagesState`/`Presenter` (expose `workspaceBarState`), strings in `features/matcha/impl/src/main/res/values/temporary.xml` (`screen_matcha_threads_*`).
- Store: scan timeline + parsers; thread list from `threadsListService` (paginate for History); per-thread `ThreadMetadata` resolved from the scan (`m.reference` metadata keyed by target id, newest first), fallback to root body, legacy state.
- Bar state: `threads` (id, title, archived, canManage), `selectedId`, `canCreate`, `canLinkToBoard`, `busy`, `showHistory`, `hasMore`, `error` (keys/decryption/history/access/network warning merged into one line, as web), `canRemind`, `canOpenSpaceDocs`. Actions: select, create (dialog with validator 1–100), rename, archive (confirm) / restore, loadMore, toggleHistory, openBoard, setReminder, openDocs.
- Thread screen title = metadata name (`ThreadTopBar`), thread list rows show metadata name when present (`ThreadsListPresenter`).
- Port tests from `RoomThreadTabsViewModel.test.ts` and `threads.test.ts` (metadata precedence, legacy fallback, invalid names, cannot manage without permission, create sends root then bootstrap reply, archive hides tab and deselects).

### Phase 3 — Kanban board + Personal
Files: `features/matcha/impl/board/*`, `MessagesFlowNode` (`NavTarget.Board`), `features/home/impl/HomeNavigationBarItem.kt` + `HomeView.kt` + `HomePresenter`/`HomeState` (`Personal` tab), `HomeEntryPoint.Callback` (navigate to thread in room), `LoggedInFlowNode` (handle it via `attachRoom` + `attachThread`).
- Presenter mirrors `KanbanBoardViewModel.ts`: `cards` (projected), `initialized`, `canCreateBoard`, `canAdd`, `loading`, `busy`, `hasMore`, `showArchived`, `unreadable`, `editor`, `threads` (for the thread picker in the editor), `error`, `canRemind`; actions `createBoard`, `edit`, `save`, `move`, `archive`, `remind`, `loadMore`, `reload`, `openThread`, `openRoom`, `toggleArchived`, `setPersonFilter` (WIP; keep behind the web WIP decision).
- Editor validation and write ordering exactly as §2.2 (pointer check → duplicate-thread check → thread rename patch → re-read metadata → send revision).
- All-rooms mode: iterate joined room ids (`roomListService.allRooms` loaded summaries / `getJoinedRoomIds`), open a `MatchaRoomStore` per room lazily with concurrency 4, keep only rooms with a board, drop on leave. `// ponytail: scans every joined room; cache board presence per room if start-up gets slow`.
- Two-pane: the Board node is a room-level screen and must render inside the end pane like the thread screen; the Personal tab lives in the start pane's Home and opens rooms in the end pane (see `TWO_PANE_NOTES.md` for the attach path).
- Port `KanbanBoardViewModel.test.ts` cases: newest revision wins, invalid revisions dropped, projection of threads to cards, rename syncs thread, archive shared, deadline validation, reminder request persisted, pointer change refuses write, leaving a room drops cards.

### Phase 4 — Reminders
Files: `features/matcha/impl/reminders/*`, hook-ups in the bar (`More → Remind me`) and the board (`Remind`).
- `ReminderService.schedule(room, bot, initial)` implementing §2.3 incl. invite confirmation and the "waiting for the bot to join" info; returns `RoomReminderRequest?`.
- Sheet: segmented `in | at`; `in` → number field + unit dropdown; `at` → date + time pickers (Material 3) and the device timezone id label; message field; "Schedule" enabled only when valid; re-validate on submit (`now` injectable for tests).
- Port `reminders.test.ts` / `RoomReminderViewModel.test.ts` (bounds, DST gap rejection, bot not joined paths, permalink appended once).

### Phase 5 — Space Docs (scope decision required, see §7)
Recommended shape: **a WebView-hosted engine** reusing the web code rather than a Kotlin CRDT.
- Build a single-file bundle from the web repo (`yjs` + the existing CodeMirror/`y-codemirror.next` editor + `renderMarkdown` + `formatMarkdown`) exposing `window.matcha = { load(frames), applyRemote(base64), getText(), getMarkdownHtml(), hasPending(), setReadOnly(bool), catalog(): {id:{title,createdAt}} }` and emitting `onLocalUpdate(base64)` / `onStatus(json)` through `@JavascriptInterface`. Ship it under `features/matcha/impl/src/main/assets/matcha-docs/`. Element X already hosts a WebView for Element Call, so the pattern (asset loading, JS bridge, theme) exists in `features/call`.
- Kotlin owns the Matrix side exactly as `RoomDocumentSession`: anchor = create event id (from `stateEvents(RoomCreate)`), full replay of frames from the scan store (the scan store for a Space is cheap: spaces have almost no other events), chunking/commit/dedupe, 500 ms debounce, transaction ids, `pendingUpdates`, error states, retry; the catalog is the same engine with doc id `catalog`.
- `SpaceDocsSetup` (admin flow) via `enableEncryption()` + `sendStateEventRaw("m.room.power_levels", merged, "")` after reading the current JSON; wait for `roomInfoFlow.isEncrypted` and a PL re-read.
- Entry points: `SpaceFlowNode.NavTarget.Docs(docId)` with a document list (New / Rename) and the editor; from a chat, `More → Space docs` resolves `spaceService.joinedParents(roomId)`: exactly one joined parent → open; several → picker; none → hidden. `More → Earlier chat document` opens the chat's own `main` doc read-only (export = share the Markdown).
- A **read-only v1** (preview + export, no edits) removes the hardest parts (local edits, send/retry, causal guard UI) while still needing the Yjs engine; a **skip** keeps web as the only Docs client. Ask David.

### Phase 6 — Polish and parity checks
- Thread names everywhere Android shows a thread (notifications: `DefaultNotifiableEventResolver` thread id already gated by the Threads flag).
- `MessagesViewTopBar` "Threads" menu entry stays (History covers it, but harmless).
- Verify read receipts are not sent for the scan timeline (`TimelineReadReceiptTracking.DISABLED`) and that opening the Board does not mark the room read (web explicitly suppresses receipts while the board is shown).

## 6. Repo conventions and gotchas (from `AGENTS.md`, `TWO_PANE_NOTES.md`, and this audit)

- New strings only in `temporary.xml`; `screen_roomlist_*` keys are routed to `features/home/impl` by `tools/localazy/config.json`, so use `screen_matcha_*` / `common_*` prefixes for the new module.
- Every new Kotlin file needs the 2026 licence header; `@PreviewsDayNight` + `ElementPreview` + a `*StatePreviewParam` for each View; new states appended at the end of existing providers.
- `JoinedRoom` instances are per `RoomFlowNode`; a `RoomScope`-scoped store dies with the room node, so the Personal board needs its own session-scoped store map with explicit `destroy()` of timelines (`Timeline` is `AutoCloseable`).
- The two-pane layout stashes Home at lifecycle CREATED while a room is open: a Personal tab that keeps flows collecting must survive that (presenters run under `launchMolecule` in the node scope, so they do).
- Rapid room taps are throttled (`FirstThrottler`, 300 ms) and `HomeFlowNode.navigateToRoom` is async; opening a card's room then its thread should go through `LoggedInFlowNode.attachRoom(...)` followed by `attachThread` (both exist).
- `Timeline.paginate` pages 50 events; a scan of a 10k-event room is 200 round trips — the reason Path B exists.
- `sendRaw` bypasses the send queue (returns the event id synchronously, fails offline). Offline card edits therefore fail fast; web has the same semantics ("Unacknowledged edits are memory-only").
- Keep zsh in mind in scripts (`--include=*.kt` must be quoted); `./gradlew :app:assembleFdroidDebug` is the verified build (17 min cold).

## 7. Decisions needed from David before/while implementing

1. **Read path** — **DECIDED 2026-10-24 by David: Path A (scan timeline, no SDK fork) first; Path B (FFI patch) stays the upgrade path.** Do not build a custom AAR unless a phase proves Path A too slow.
2. **Docs scope**: skip / read-only + export / full collaborative editing via the WebView engine.
3. **Web WIP**: land or drop the uncommitted `show_to_room` / people-filter change before Android implements the Personal board.
4. **Personal placement**: third bottom tab (Chats | Spaces | Personal) vs. a top-bar entry. Web puts it above Home in the space rail; the bottom toolbar is the Android equivalent.
5. **Bot id / flag defaults**: hardcode the matcha values in this fork (proposed) or read from a build-time property.
6. **Threads flag**: force on in the fork (proposed) — note the SDK comment that changing it needs an app restart (`threadsEnabled` is a client-builder option).

## 8. Appendix A — web → Android file map

| Web | Android (new unless noted) |
|---|---|
| `utils/matcha/threads.ts` | `features/matcha/impl/protocol/ThreadMetadata.kt`, `threads/ThreadMetadataService.kt` |
| `viewmodels/room/RoomThreadTabsViewModel.ts` + `RoomThreadTabsView.tsx` | `threads/RoomWorkspaceBar{Presenter,State,Event,View,StatePreviewParam}.kt` |
| `viewmodels/room/KanbanBoardViewModel.ts` + `KanbanBoardView.tsx` | `board/KanbanBoard{Presenter,State,Event,View,StatePreviewParam,Node}.kt`, `board/PersonalBoardNode.kt` |
| `utils/matcha/reminders.ts`, `RoomReminderViewModel.ts`, `RoomReminderView.tsx` | `reminders/ReminderService.kt`, `reminders/RoomReminder{Presenter,State,Event,View,StatePreviewParam}.kt` |
| `utils/matcha/documents.ts`, `documentCatalog.ts` | `docs/DocumentSession.kt`, `docs/DocumentCatalog.kt`, `assets/matcha-docs/index.html` (+ JS bundle) |
| `viewmodels/room/SpaceDocsViewModel.ts`, `SpaceDocsSetupView.tsx` | `docs/SpaceDocsSetup{Presenter,State,Event,View}.kt` |
| `utils/matcha/spaceDocuments.ts` | `docs/SpaceForRoomDocuments.kt` (uses `SpaceService.joinedParents`) |
| `utils/matcha/encryption.ts` | not needed (SDK retries UTDs) |
| `RoomView.tsx` integration | `MessagesView.kt`, `MessagesFlowNode.kt` |
| `LoggedInView.tsx` / `SpacePanel.tsx` Personal page | `HomeNavigationBarItem.kt`, `HomeView.kt`, `HomePresenter.kt` |
| `RoomListViewModel.ts` favourites order | `features/home/impl/.../filters/RoomListFilter.kt` |
| `matrix-js-sdk.d.ts` event typings | `appconfig/MatchaConfig.kt` constants + protocol data classes |

## 9. Appendix B — scan-timeline reader sketch (Path A)

```kotlin
// libraries/matrix/impl: CreateTimelineParams.MatchaEvents
is CreateTimelineParams.MatchaEvents -> TimelineFocus.Live(hideThreadedEvents = false)
// filter
TimelineFilter.EventFilter(
    TimelineEventFilter.Include(
        MatchaConfig.MESSAGE_TYPES.map { TimelineEventCondition.EventType(TimelineEventType.MessageLike(MessageLikeEventType.Custom(it))) } +
        MatchaConfig.STATE_TYPES.map { TimelineEventCondition.EventType(TimelineEventType.State(StateEventType.Custom(it))) }
    )
)
// features/matcha/impl: MatchaRoomStore
timeline.timelineItems.collect { items ->
    items.filterIsInstance<MatrixTimelineItem.Event>().forEach { item ->
        val json = item.event.timelineItemDebugInfoProvider().originalJson ?: return@forEach // UTD → unreadable
        when (json["type"]) { CARD -> …, BOARD -> …, THREAD_METADATA -> …, DOC_UPDATE -> …, KANBAN_STATE -> … }
    }
}
```
Verify before relying on it: that `Include` keeps custom *state* items (they are mapped to
`StateContent(OtherState.Custom)`), that `originalJson` is the decrypted cleartext in E2EE rooms, and
that the Live focus with an include filter still back-paginates through unrelated history.
