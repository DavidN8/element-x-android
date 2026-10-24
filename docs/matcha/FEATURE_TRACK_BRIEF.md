# Matcha feature track — engineering brief

Read with: `MATCHA_PORT_PLAN.md` (what and why, wire protocol §2) and `WHATSAPP_DESIGN.md` (how it looks). This file is the
code contract between the feature track (everything inside `features/matcha/**`) and the integration step that wires it
into `features/messages`, `features/home` and `features/space`.

> **Spec v2 update (2026-10-24 02:10).** `WHATSAPP_DESIGN.md` is now v2. Its §4.0 "Contract" and §6.1 "Supersedes"
> override this brief where they differ. Ownership of the v2 changes, so the two tracks never edit the same file:
> - **Track B owns** every v2 API change in `features/matcha/api` (remove `MatchaChatBarSelection.AllThreads`, add
>   `MatchaRoomEntryPoint.Callback.openRoomDetails()`, add `MatchaRoomController` + `MatchaThreadInfo` implemented as
>   `DefaultMatchaRoomController` in `chatbar/`) **and** the v2 store additions in `store/MatchaRoomStore.kt`
>   (`unreadable`, `warning`, `boardPointerRead`, `loadFailed`, `ThreadMetadata.nameSource`). Track B step 2 starts by
>   adapting step 1's chip bar to these changes if step 1 did not already.
> - **Track C** must not edit `features/matcha/api` or `store/`; it only uses them. If C needs
>   something from the store, it reads `events` and computes it in its own package.
> - Archive has no confirmation dialog (snackbar with Undo). The task editor is one full-height sheet with no thread
>   picker. Reminder quick options do not send on tap; the confirm pill sends. Chips are filled grey when unselected
>   (the shared `PillFilterChip` is restyled at integration; do not fork it).

## Ground rules

- Edit only `features/matcha/**` (api, impl, test). Do not touch other modules; integration happens later.
- Strings: `features/matcha/impl/src/main/res/values/temporary.xml`, keys `screen_matcha_*` (screen specific) or
  `common_matcha_*`. Plain, short, WhatsApp-like wording (see the spec's copy table). Never edit `localazy.xml`.
- Architecture per AGENTS.md: Presenter (Molecule, `@Composable present()`), State (immutable data class with
  `eventSink`), Event (sealed interface), View (stateless), `*StatePreviewParam` providers, `@PreviewsDayNight` +
  `ElementPreview`, `internal` previews named `...Preview`. Use Compound/designsystem components and `ElementTheme` tokens;
  the WhatsApp palette is applied globally by the re-skin track, so do not hard-code hex colours in feature code except
  where the spec says a feature-specific colour is needed.
- Logging with Timber, never log message bodies, card titles or document text.
- New Kotlin files need the 2026 licence header (copy from an existing Matcha file).
- Gating: every renderer/entry point renders nothing when `FeatureFlags.MatchaWorkspaces` is disabled.
- Tests: presenter tests with Molecule + Turbine (see `features/poll/impl/src/test` and `features/knockrequests/impl/src/test`),
  using `FakeJoinedRoom` (its new `sendRawEventResult`, `sendRawStateEventResult`, `getStateEventsJsonResult` lambdas),
  `FakeTimeline`, `FakeThreadsListService`. Do not record screenshots.
- Checks before you finish: `./gradlew :features:matcha:impl:compileDebugKotlin :features:matcha:impl:testDebugUnitTest
  :features:matcha:test:compileDebugKotlin` and `./gradlew :features:matcha:impl:ktlintCheck` (or `ktlintFormat`), and
  `./gradlew :features:matcha:impl:detekt` if available. Fix everything you introduce.
- Commit your work in small, well-described commits (title + body, sentence-style). End each message with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## What already exists (do not rewrite)

- `appconfig/MatchaConfig.kt`: event types, limits, `REMINDER_BOT_USER_ID`.
- `features/matcha/impl/.../protocol/`: `RawEvent`, `ThreadMetadataProtocol` (parse/resolve/patchContent/bootstrapReplyContent),
  `KanbanProtocol` (parseCard/cardContent/boardPointerContent/boardRootContent/threadCardId/latestRevisions/project),
  `ReminderProtocol` (command/isValid/dueAt/withLink/parseAcknowledgement). Unit-tested; extend tests if you extend them.
- `features/matcha/impl/.../store/MatchaRoomStore.kt`, bound once per room in `RoomScope` by `MatchaRoomBindingContainer`:
  - `snapshot: StateFlow<MatchaRoomSnapshot>` with `threads` (all, newest activity first, each with resolved
    `ThreadMetadata`), `board` (`rootId`, projected `cards`) or null, `permissions` (`canSendMessages`, `canManageThreads`,
    `canWriteCards`, `canCreateBoard`, `canInvite`), `isLoaded`, `hasMoreHistory`, `hasMoreThreads`, `isLoadingMore`, `events`.
  - `send(eventType, contentJson): Result<EventId>` (encrypted in E2EE rooms, shown immediately), `sendState(...)`,
    `loadMore()`, `latestRevision(cardId)`, `rememberCreatedBoard(rootId)`.
  - For a room that is not open (the Tasks tab), construct `MatchaRoomStore(joinedRoom, scope)` yourself and call
    `joinedRoom.destroy()` when the scope ends.
- `JoinedRoom.sendRawEvent`, `sendRawStateEvent`, `getStateEventsJson`; `CreateTimelineParams.OnlyEventTypes`.

## Behaviour rules that must match the web client (from the plan §2)

- **Create a named thread**: `store.send("m.room.message", {"msgtype":"m.text","body":name})` → root id, then
  `store.send(EVENT_THREAD_METADATA, ThreadMetadataProtocol.bootstrapReplyContent(root, name))`. If the second send fails,
  show "Message sent, but the thread could not be set up".
- **Rename / archive / restore a thread**: `store.send(EVENT_THREAD_METADATA, patchContent(root, name = …))` or
  `archived = …`. Archive asks for confirmation first; restore does not. Requires `permissions.canManageThreads`.
- **Create the board**: `store.send(EVENT_KANBAN_BOARD, boardRootContent())` → root, `store.sendState(STATE_KANBAN, "",
  boardPointerContent(root))`, `store.rememberCreatedBoard(root)`. Requires `permissions.canCreateBoard`.
- **Add a task** (no thread chosen): create a named thread from the title, then write the linked card
  (`card_id = KanbanProtocol.threadCardId(threadId)`, status To do). Title must be a valid thread name (1–100).
- **Edit a task**: write a complete revision (`KanbanProtocol.cardContent`). If a linked card's title changed, send the
  thread rename patch first. Moving status and archiving are revisions too; archiving a linked card also sends
  `archived = true/false` on its thread. Refuse to link a second card to a thread that already has one.
- **Deadline** is optional, local date and time → epoch millis, must round-trip (reject DST gaps). A deadline never
  schedules a reminder by itself; changing it never reschedules.
- **Remind**: preconditions joined + can send. Bot (`MatchaConfig.REMINDER_BOT_USER_ID`) membership via
  `room.getUpdatedMember(UserId(bot))`: joined → continue; invited → tell the user to wait for the bot to join; otherwise
  require `permissions.canInvite`, confirm, `room.inviteUserById`, then tell the user to wait (do not send yet).
  Send the command as `store.send("m.room.message", {"msgtype":"m.text","body": ReminderProtocol.command(...)})` to get its
  event id. For a card, then write a revision with `reminder_requested = {event_id, at}`. Only the bot's notice
  (`ReminderProtocol.parseAcknowledgement`) confirms scheduling; until then the UI says "Reminder requested".
  Validate again right before sending (`ReminderProtocol.isValid`).

## The API contract (already in `features/matcha/api`, do not change it)

Read these five files; implement exactly them. If a signature truly cannot work, change it minimally and explain why in
your report, because the integration step codes against them and the other feature track uses them too.

- `MatchaChatBarRenderer` with `MatchaChatBarSelection` and `MatchaChatBarActions` (room scoped; rendered by the chat,
  the thread screen and the board).
- `MatchaRoomEntryPoint` (`createTasksNode`, room-scoped node; `Callback` for openThread/openGeneral/openAllThreads/openSpaceDocs).
- `MatchaReminderRenderer` with `MatchaReminderTarget` (room scoped).
- `MatchaTasksTabRenderer` (session scoped, `contentPadding` from Home's scaffold).
- `MatchaSpaceDocsEntryPoint` (session-scoped node for a space's docs list, editor and setup).

Bind implementations with `@ContributesBinding(RoomScope::class)` / `(SessionScope::class)` for renderers and
`@ContributesBinding(AppScope::class)` for entry points (see `DefaultKnockRequestsBannerRenderer` and
`DefaultCreatePollEntryPoint`). Only the full app build validates the dependency graph, and the other track provides
some implementations, so verify your track with the Matcha module tasks, not `:app:assemble*`.

## Tracks

Two feature tracks run at the same time in separate worktrees; each owns different packages under
`features/matcha/impl/.../`. Never edit the other track's packages. Both add strings to the same `temporary.xml`;
append yours in a block commented with your track name so the merge stays simple.

### Track B (worktree `../ex-matcha-b`, branch `matcha-b`): chip bar, board, Tasks tab
1. **Chip bar** (`chatbar/`) per spec §4a, implementing `MatchaChatBarRenderer`. Thread actions (create, rename,
   archive with snackbar Undo, unarchive), the New thread sheet, the thread actions sheet (long-press), More menu (Remind
   me shows `MatchaReminderRenderer`, which track C implements; inject the interface), warning strip. Presenter tests.
2. **Tasks board** (`tasks/`) per spec §4b, implementing `MatchaRoomEntryPoint`: the node draws a top bar (room avatar,
   name, subtitle "Tasks") and the chip bar with `MatchaChatBarSelection.Tasks`, then tabs (phone) or columns (wide),
   task rows, the task actions sheet, the full-screen editor, archived tasks, all board states. Remind uses
   `MatchaReminderRenderer` with a `MatchaReminderTarget(cardId = …)`. Presenter tests.
3. **Tasks tab** (`taskstab/`) per spec §4c, implementing `MatchaTasksTabRenderer`, session scoped; opens a
   `MatchaRoomStore` per joined room with bounded concurrency (ponytail-comment the ceiling) and destroys rooms it no
   longer needs; chips, grouping by deadline, source chat avatar, new-task flow ("Choose a chat" then the editor; reuse
   the board's editor), states. Presenter tests.

### Track C (worktree `../ex-matcha-c`, branch `matcha-c`): reminders, then Space Docs
1. **Reminders** (`reminders/`) per spec §4d, implementing `MatchaReminderRenderer`: the two-step sheet, quick options,
   custom date and time with time zone, bot states (joined, invited/waiting, add bot, cannot invite), send failure, and
   recording `reminder_requested` on the card when `cardId` is set (read-modify-write with `store.latestRevision`).
   Presenter tests with an injectable clock.
2. **Space Docs** (`docs/`) per spec §4e and plan §2.4, implementing `MatchaSpaceDocsEntryPoint`. The Yjs engine runs in
   a headless or embedded WebView from an asset bundle built from the web fork's `yjs` package
   (`~/Code/element-group/element-web`, read-only: never modify that repo; build the bundle into this repo's
   `features/matcha/impl/src/main/assets/matcha-docs/` and commit the built file plus the tiny entry source and the exact
   build command). The Kotlin side owns the Matrix transport exactly like the web `RoomDocumentSession`: anchor = the
   space's `m.room.create` event, full replay of `com.matchahoneylatte.docs.update` frames from the store's `events`,
   chunking/commit, dedupe by sender + update id, 500 ms debounce, stable transaction ids are not available through
   `sendRawEvent` (note this as a known difference), saved only after acknowledgement, the catalog document, setup
   (enable encryption, then set the two power-level keys from the current power levels JSON via `sendRawStateEvent`),
   docs list, editor (preview + edit with a plain Compose text field whose edits are diffed into Y.Text), all states.
   Read the web code in `apps/web/src/utils/matcha/{documents,documentCatalog}.ts` and
   `apps/web/src/viewmodels/room/{RoomDocs,SpaceDocs}ViewModel.ts` first. Presenter/transport tests.

## Done criteria



Every step: builds (`:features:matcha:impl:compileDebugKotlin`), tests pass (`:features:matcha:impl:testDebugUnitTest`),
ktlint clean for the module, previews for all states, committed.

Report at the end: what you built, file list, test counts, anything you could not finish, and every place the
integration step must call (which renderer/entry point, from where, with which arguments).
