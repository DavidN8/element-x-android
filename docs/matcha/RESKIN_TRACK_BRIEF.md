# WhatsApp re-skin track — engineering brief

Read with `WHATSAPP_DESIGN.md` (the spec; it wins on look and behaviour) and AGENTS.md. Work in
`/Users/david/Code/element-group/element-x-android` on branch `matcha`. The feature track works in a separate worktree on
`features/matcha/**` only; never edit that directory.

## Already done (commit `0c4c71bebd`)

- `libraries/compound/.../colors/MatchaColors.kt`: `MatchaPalette` (named colours) plus `matchaColorsLight` /
  `matchaColorsDark` (Compound `SemanticColors.copy(...)` with WhatsApp values). They are now the defaults in
  `ElementTheme` and `SemanticColorsLightDark.default`, so every screen and preview uses them. Header gradients are
  transparent through `gradientSubtleStop*`. If the spec gives a different value for a palette field, change it in
  `MatchaColors.kt` only. Never edit `tokens/generated/**`.

## Rules

- Change the look through, in order of preference: the palette, shared designsystem components, then per-feature
  views. Keep Compound's structure; do not fork components when a parameter or a default change is enough.
- No WhatsApp name, logo, wordmark, doodle artwork or proprietary icons. Use `CompoundIcons` (check
  `libraries/compound/.../tokens/generated/CompoundIcons.kt` for names). The chat wallpaper is a plain tone or our own
  subtle pattern drawn in code.
- Accessibility: touch targets ≥ 48dp, content descriptions on icon-only buttons, text contrast ≥ 4.5:1 (use
  `textActionAccent`/`MatchaPalette.greenText` for green text on white, never `#1DAA61` text).
- Strings: new English strings in the module's `src/main/res/values/temporary.xml` (create it if missing). Never edit
  `localazy.xml`. Key prefixes per AGENTS.md.
- Previews: keep every existing preview compiling; add new states at the end of providers; `@PreviewsDayNight`,
  `ElementPreview`. Do not record or verify Paparazzi screenshots (they will all change; CI re-records them).
- Tests: keep unit tests compiling and passing for every module you touch (`./gradlew :<module>:testDebugUnitTest`).
  Update tests whose expectations encode the old look or behaviour (for example the filter clear button).
- Build check before each commit: `./gradlew :app:assembleGplayDebug` and `ktlintCheck` on touched modules
  (`./gradlew :<module>:ktlintFormat` is fine). Konsist rules apply (`tests/konsist`); run `./gradlew :tests:konsist:test`
  at the end of your step if you added previews or files.
- Commit after each coherent sub-step with a sentence-style title and a body explaining why. End each message with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Leave a short summary at the end: files changed, what is done, what you skipped and why.

## Steps (one agent each, in this order)

1. **Global components** (`libraries/designsystem`, `libraries/compound` theme files, `app/src/main/res` splash
   colours): flat top bars (make `modifiers/Gradient.kt` paint flat, or rely on the transparent stops if that already
   yields a flat canvas), `NavigationBar` + `NavigationBarItem` restyle (canvas container, tonal-green pill indicator,
   filled selected icon, labels always shown, green badge), FAB (rounded square 16dp, accent colours), buttons (filled =
   green pill, text button = green text), text fields (green cursor and focus), dialog and bottom-sheet radii, tooltip on
   an inverse surface, badge/counter colours, list items. Follow the spec's token and component sections.
2. **Home / chat list** (`features/home/impl`, plus `appnav` two-pane placeholder if the spec says so): top bar with the
   app name as a green title and an overflow menu, a search pill under it, WhatsApp chips (All first, then the spec's
   order, single-select, tonal-green selected, no border, no clear button), rows without dividers and with one-line
   previews, green unread time and count badge, bottom `NavigationBar` with Chats and Spaces (structured so a third
   "Tasks" item can be added by the integration step with one enum entry and one `when` branch), FAB at the end above
   the bar, Spaces tab per spec, search overlay restyle, empty states. Update affected tests.
3. **Chat screen** (`features/messages/impl`, `libraries/textcomposer`, related designsystem pieces): top bar per spec,
   wallpaper tone behind the timeline, bubbles (outgoing green tint, incoming surface, tail on the first bubble of a group,
   radius, paddings, max width), time + send state inside the bubble, day separator and system-notice pills, unread
   divider, thread summary row styled as WhatsApp's reply indicator, reactions chip, composer as a rounded pill with a
   separate round green send/mic button, long-press action sheet look, thread screen and thread list consistent with
   the above. Leave room under both top bars (room and thread) for the Matcha chip bar: the integration step will insert
   `MatchaChatBarRenderer.Render(...)` directly below the top bar inside `MessagesView`'s `topBar` slot; do not add
   anything there yourself.
