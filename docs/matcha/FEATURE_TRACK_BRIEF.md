# Matcha feature track — engineering brief

Read with: `MATCHA_PORT_PLAN.md` (what and why, wire protocol §2) and `WHATSAPP_DESIGN.md` (how it looks). This file is the
code contract between the feature track (everything inside `features/matcha/**`) and the integration step that wires it
into `features/messages`, `features/home` and `features/space`.

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

## The API contract (create exactly these in `features/matcha/api`)

```kotlin
package io.element.android.features.matcha.api

/** The horizontal chip bar shown under the top bar of a chat and of a thread. Room scoped. */
interface MatchaChatBarRenderer {
    @Composable
    fun Render(
        /** The thread currently shown, or null when the main chat is shown. */
        selectedThreadId: ThreadId?,
        onOpenChat: () -> Unit,
        onOpenThread: (ThreadId) -> Unit,
        onOpenTasks: (initialThreadId: ThreadId?) -> Unit,
        /** Null hides the entry; the integration step decides whether the room has a space with documents. */
        onOpenSpaceDocs: (() -> Unit)?,
        modifier: Modifier,
    )
}

/** Room-scoped screens. */
interface MatchaRoomEntryPoint : FeatureEntryPoint {
    data class Params(val initialThreadId: ThreadId?) : NodeInputs
    interface Callback : Plugin {
        fun openThread(threadId: ThreadId)
        fun close()
    }
    /** The room's task board. */
    fun createTasksNode(parentNode: Node, buildContext: BuildContext, params: Params, callback: Callback): Node
}

/** The reminder sheet, shown from the chip bar, a task, or a message's long-press menu. Room scoped. */
interface MatchaReminderRenderer {
    @Composable
    fun Render(target: MatchaReminderTarget?, onDismiss: () -> Unit)
}

@Immutable
data class MatchaReminderTarget(
    /** Prefilled message. */
    val message: String,
    /** The message or thread root the reminder is about; its link is appended to the message once. */
    val eventId: EventId? = null,
    val threadId: ThreadId? = null,
    /** Prefilled exact time, e.g. a task's deadline. */
    val dueAt: Long? = null,
    /** Set when reminding about a task, so the card records the request. */
    val cardId: String? = null,
)

/** The cross-chat Tasks tab content on Home. Session scoped. */
interface MatchaTasksTabRenderer {
    @Composable
    fun Render(
        onOpenRoom: (RoomId) -> Unit,
        onOpenThread: (RoomId, ThreadId) -> Unit,
        modifier: Modifier,
    )
}
```

Adapt names of imports to the codebase (`FeatureEntryPoint`, `NodeInputs`, `Plugin` live in `libraries/architecture`;
check how `CreatePollEntryPoint` and `KnockRequestsBannerRenderer` are declared and bound). If you must change a
signature, keep it minimal and document why in your final report: the integration step codes against this list.

## Order of work and done criteria

1. **Chip bar** (`chatbar/`): presenter reads the store; shows General, Tasks, up to the spec's number of active named
   threads (most recent first, the selected one always visible), New thread, History (all threads incl. archived, with
   load more), More (Space docs, Remind me). Dialogs for create/rename; confirm for archive. Errors as snackbars or inline
   text per spec. Renderer `DefaultMatchaChatBarRenderer` bound in `RoomScope`. Tests for presenter behaviour.
2. **Tasks board** (`tasks/`): `TasksNode` (`@ContributesNode(RoomScope::class)`), presenter + view per spec: not-created
   state (create button if permitted, explanation otherwise), loading, empty, list/columns, editor sheet, archived toggle,
   load older, read-only when `!canWriteCards`, opening the thread via callback, Remind via `MatchaReminderRenderer`.
   `DefaultMatchaRoomEntryPoint` bound in `AppScope` (like `DefaultCreatePollEntryPoint`).
3. **Reminder sheet** (`reminders/`): presenter (now injectable for tests) + bottom sheet view per spec (quick options
   modelled on WhatsApp's "Remind me": e.g. In 1 hour / In 2 hours / Tomorrow 9:00 / Custom; custom date and time pickers
   with the timezone shown; message field), the bot flow above, pending → requested states; `DefaultMatchaReminderRenderer`
   bound in `RoomScope`.
4. **Tasks tab** (`taskstab/`): session-scoped presenter that opens stores for joined rooms with bounded concurrency
   (ponytail-comment the ceiling), keeps rooms that have a board, groups or filters per spec, shows the source chat on
   each task, edits shared cards in place, drops a room's tasks as soon as the user leaves it.
   `DefaultMatchaTasksTabRenderer` bound in `SessionScope`.

Report at the end: what you built, file list, test counts, anything you could not finish, and every place the
integration step must call (which renderer/entry point, from where, with which arguments).
