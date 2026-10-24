# Matcha integration — engineering brief

Read with `WHATSAPP_DESIGN.md` (spec v2; its §4.0 Contract and §6.1 Supersedes override this brief where they differ), `MATCHA_PORT_PLAN.md` and `docs/matcha/FEATURE_TRACK_BRIEF.md`. The three tracks
are merged into branch `matcha` in `/Users/david/Code/element-x-android` before you start. The Matcha screens live in
`features/matcha` behind the interfaces in `features/matcha/api`; your job is to show them in the app. Each item below
is one commit. Gate every entry point with `FeatureFlags.MatchaWorkspaces` (the renderers already render nothing when
it is off; navigation entries and menu actions must also be hidden).

## 1. Reminder bot per homeserver

`MatchaConfig.REMINDER_BOT_USER_ID` hard-codes `@matcha-reminder:matchahoneylatte.com`. Replace it with
`REMINDER_BOT_LOCALPART = "matcha-reminder"` and a helper that builds `@matcha-reminder:<the session's server name>`, so
the production server and local test servers both work. Update every caller and test.

## 2. Chip bar in chats and threads

- `features/messages/impl`: inject `MatchaChatBarRenderer` (room scoped) into `MessagesNode` and `ThreadedMessagesNode`
  the way `KnockRequestsBannerRenderer` is injected and passed to the view. In `MessagesView`'s `topBar` slot, render the
  existing top bar and then the chip bar in a `Column` (spec §4a "Placement"): room mode
  `MatchaChatBarSelection.General`, thread mode `MatchaChatBarSelection.Thread(threadRootId)`. Hidden in selection mode.
- Actions, implemented in `MessagesFlowNode` and passed down through the nodes' callbacks or navigator:
  - `openGeneral`: from a thread, go back to the room timeline (pop everything above `Messages`); no-op in the room.
  - `openThread(id)`: from the room, push `NavTarget.Thread(id, null)`; from a thread or the board, replace the top
    element so threads do not stack (spec §3.4 "Navigation").
  - `openTasks`: push (or replace, from a thread) a new `NavTarget.Tasks(initialThreadId = null)`.
  - `openAllThreads`: push `NavTarget.ThreadsList`.
  - `openSpaceDocs`: null unless the room has at least one joined parent space (`SpaceService.joinedParents`); with one
    parent push a new `NavTarget.SpaceDocs(spaceId)`; with several show a "Choose a space" bottom sheet.
- The spec moves the Threads icon out of the top bar into the "All threads" chip when the flag is on; do that.
- Thread top bar subtitle shows the thread's Matcha name when the flag is on (spec §3.3 "Subtitle").

## 3. Task board in the chat flow

- `NavTarget.Tasks(initialThreadId: ThreadId?)` in `MessagesFlowNode`, resolved with
  `MatchaRoomEntryPoint.createTasksNode(this, buildContext, Params(initialThreadId), callback)`; the callback maps to
  the same actions as the chip bar (openThread replaces the board with the thread, openGeneral pops to `Messages`,
  openAllThreads pushes the list, openSpaceDocs as above).
- Attach sheet "Task" tile (spec §3.3 "Attach sheet"): opens the board with the editor for a new task, if the feature
  track exposed a way to do that; otherwise opens the board.

## 4. "Remind me" on messages

- Add `TimelineItemAction.RemindMe` (spec §4d entry point 1): icon `CompoundDrawables.ic_compound_notifications`,
  ordered after Pin/Unpin in `TimelineItemActionComparator`, built in `ActionListPresenter` when the flag is on, the event
  is remote, and the user can send messages. Handle it in `MessagesPresenter` by setting a `MatchaReminderTarget(message =
  first 200 characters of the message body, eventId = the event, threadId = the thread when in thread mode)` in the
  state; `MessagesView` renders `MatchaReminderRenderer.Render(target, onDismiss)` (inject the renderer like the chip
  bar). Update `ActionListPresenterTest` and `MessagesPresenterTest`.

## 5. Tasks tab on Home

- Add `HomeNavigationBarItem.Tasks` between Chats and Spaces (string key `screen_home_tab_tasks` in
  `features/home/impl/src/main/res/values/temporary.xml`, icon `CheckCircle`/`CheckCircleSolid`), using the extension
  point the re-skin left in `HomeView`. Render `MatchaTasksTabRenderer.Render(contentPadding, onOpenRoom, onOpenThread,
  modifier)` there; inject it in `HomeFlowNode` (session scoped). Title "Tasks" and the FAB per spec §4c (if the feature
  track exposed a "new task" entry, use it; otherwise no FAB on this tab).
- `HomeEntryPoint.Callback.navigateToThread(roomId, threadId)`: `LoggedInFlowNode` implements it with
  `attachRoom(roomId.toRoomIdOrAlias()).attachThread(threadId, null)` (see `RootFlowNode` around its `attachThread` call),
  in its lifecycle scope. `onOpenRoom` uses the existing `navigateToRoom`.
- Update `HomePresenterTest`, `HomeStatePreviewParam` (append), and any test listing the tabs.

## 6. Docs in spaces

- `features/space/impl`: `SpaceFlowNode` gets `NavTarget.Docs(documentId: String? = null)` resolved with
  `MatchaSpaceDocsEntryPoint.createNode(this, buildContext, Params(spaceId), callback)`; callback `close` pops it.
- `SpaceView`: a **Docs** section between the header and the rooms (spec §3.2 and §4e), at minimum one row
  "Docs" with the `Document` tile that opens the list; add a Docs item to the space `⋮` menu. If the feature track
  exposed a section renderer with document rows, use it instead.
- `MessagesFlowNode` `NavTarget.SpaceDocs(spaceId)` for the chip bar's More → Space docs, resolved with the same entry
  point, plus "Earlier chat doc" (`Params(spaceId, legacyChatId = roomId)`) only if the feature track can tell whether a
  legacy doc exists.

## 7. Checks

`./gradlew :app:assembleGplayDebug`, unit tests of every module you touched (at least `:features:messages:impl`,
`:features:home:impl`, `:features:space:impl`, `:appnav`, `:features:matcha:impl`), `ktlintFormat` on touched modules,
`./gradlew :tests:konsist:test`. Fix the dependency graph if Metro reports missing or duplicate bindings: the app build
is the first place the three tracks' bindings meet.

## 8. Spec v2 items owned by integration

- `features/home/api`: `enum class HomeTab { Chats, Tasks, Spaces }`, `HomeEntryPoint.Callback.onTabSelected(tab)` and
  `navigateToThread(roomId, threadId)`; the per-tab empty end pane in `NoRoomSelectedView`.
- Tasks tab: the enum entry is appended last, the bar order is set explicitly (Chats · Tasks · Spaces), the label is
  `common_matcha_tasks`, and Home's Scaffold has no FAB on Tasks (the renderer draws it).
- `MessagesMenuActions` ⋮ fallback items while `MatchaRoomController.hasChatBar` is false (spec §4.0).
- Thread names from `MatchaRoomController.threads` in the thread subtitle, `ThreadTopBar`, the bubble thread footer and
  the thread list.
- Attach-sheet Task tile calls `MatchaRoomController.openNewTask()`.
- Restyle the shared `PillFilterChip` to filled grey when unselected (spec §6.1 item 14) and check the chat list chips.
