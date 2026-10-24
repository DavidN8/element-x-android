# WhatsApp-style design spec for the Matcha fork of Element X Android

Status: v2, 2026-10-24. This version revises v1 after two design reviews. The Decisions log at the end lists every review issue and what changed.
File: `WHATSAPP_DESIGN.md` at the repository root. `MatchaColors.kt`, the three briefs in `docs/matcha/` and code comments refer to it by this path.
Owner: lead product designer. Branch: `matcha`.
Audience: the engineers implementing:
- `MATCHA_PORT_PLAN.md` (the feature plan, §1, §2 and §4);
- `docs/matcha/RESKIN_TRACK_BRIEF.md`;
- `docs/matcha/FEATURE_TRACK_BRIEF.md`;
- `docs/matcha/INTEGRATION_BRIEF.md`.

## How to read this

- **Reference baseline.** The reference is the **stable** WhatsApp Android app, builds 2.24 to 2.26.
  - The Material 3 refresh started in beta 2.23.13.16 (Sep 2023) and rolled out to stable Mar–May 2024. The Meta Design blog describes it (2024-05-09).
  - Lists chips reached stable in Apr 2024, with filled grey unselected chips and a tonal green selected chip. Favourites followed in Jul 2024 and custom lists in Oct 2024.
  - Where stable WhatsApp has no equivalent, a few beta-only patterns are borrowed on purpose. §7.1 lists each one with its build. A beta-only pattern that has a stable equivalent is never copied.
- **Colour values.** The source of truth for colour values is `CMP/colors/MatchaColors.kt`. This spec gives each colour's role, the decisions behind it, and the contrast proof (§7.2). Where this spec's decision column differs from the code, change the code. Feature code never repeats hex values.
- **Approximate values.** "(approx.)" marks a value picked from measurement or reasoning rather than a token. Build it as written, and adjust only after checking on the device.
- **Device.** Samsung Galaxy Z Fold 7.
  - The cover screen uses the phone layout.
  - The inner screen is about 750 × 832 dp (approx.). Its width class is Medium and its height class is not Compact, so `hasTwoPaneWindowSize()` (`DS/utils/WindowSizeClassUtils.kt:35-39`) is true and the two-pane layout applies.
- **Brand rule.** Copy WhatsApp's layout, spacing, colour roles, shapes and interaction patterns.
  - Never copy its name, logo, wordmark, doodle wallpaper, illustrations or icon glyphs.
  - Use `CompoundIcons`, plus the two Material Symbols (Apache-2.0) in §2.8.
  - The Chats title is our own product name in plain text (§6.2, decision 1).
- **Path prefixes.** All paths are relative to `/Users/david/Code/element-group/element-x-android`.

  | Prefix | Path |
  |---|---|
  | `CMP/` | `libraries/compound/src/main/kotlin/io/element/android/compound/` |
  | `DS/` | `libraries/designsystem/src/main/kotlin/io/element/android/libraries/designsystem/` |
  | `H/` | `features/home/impl/src/main/kotlin/io/element/android/features/home/impl/` |
  | `M/` | `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/` |
  | `TC/` | `libraries/textcomposer/impl/src/main/kotlin/io/element/android/libraries/textcomposer/` |
  | `MUI/` | `libraries/matrixui/src/main/kotlin/io/element/android/libraries/matrix/ui/` |
  | `SP/` | `features/space/impl/src/main/kotlin/io/element/android/features/space/impl/` |
  | `AN/` | `appnav/src/main/kotlin/io/element/android/appnav/` |
  | `MX/` | `features/matcha/impl/src/main/kotlin/io/element/android/features/matcha/impl/`. New UI goes in the brief's packages `chatbar/`, `tasks/`, `taskstab/`, `reminders/` and `docs/`, next to `protocol/` and `store/`. |
  | `MA/` | `features/matcha/api/src/main/kotlin/io/element/android/features/matcha/api/` |

- **Line anchors.** All anchors are from branch `matcha` at **`8363bc94af`** (2026-10-24 02:00 +07), and every one was re-read at that commit. The re-skin track commits often, so re-check an anchor before editing near it.
- **Status markers.** ✅ means landed, with the commit. ⏳ means outstanding.

---

## 1. Design philosophy

These 12 rules come from WhatsApp's stated principles (simple, reliable, private; fresh, approachable, simple) and from how the app behaves. Every review checks a screen against them.

1. **The chat comes first; the chrome stays quiet.**
   - Surfaces are white, or near-black in dark mode.
   - There are no gradients, no header bands, no row dividers and no decorative cards. Tone and space separate surfaces, not lines.
   - Feature chrome (the chip bar, warning strips) appears only once a chat actually uses the feature.
2. **Green means "act", "new" or "chosen", and nothing else.** It is used for:
   - the primary action (FAB, send/mic, filled button);
   - unread state (badge, list time, typing);
   - selection (chip, nav pill, selected-row tint);
   - green text actions.

   It also tints your own bubble.

   Exceptions and limits:
   - The Chats tab title is green in light mode, as in WhatsApp. No other header is green.
   - Green is never decorative: no green avatar tints, attach tiles, illustrations or icons used for decoration.
   - No green text ever appears inside a bubble, because it fails contrast on the outgoing tint (§7.2). Inside bubbles, green appears only as icons and bars.
3. **One primary action per screen, placed low.** It is either a bottom-end FAB, the composer, or one full-width pill at the bottom of a sheet. Anything secondary goes in `⋮` or behind a long-press.
4. **Familiar beats new.** Every Matcha feature reuses a pattern WhatsApp users already know:
   - the in-chat chip bar copies the chat-list chips;
   - a task row copies a chat row;
   - the board tabs copy the Media · Docs · Links tabs;
   - the reminder sheet copies "Remind me";
   - the task editor copies WhatsApp's form sheets: a large name field, then icon rows;
   - Docs in a space copy the "Media, links and docs" row.

   Invent nothing that has a WhatsApp equivalent.
5. **Rows are the unit of every list.** A row has a leading avatar or icon tile, a one-line title, one grey line, and trailing meta. Tapping a row opens it; long-pressing it offers its actions.
6. **Shapes carry meaning.** Circles are people, chats and threads. Rounded squares are spaces, documents and FABs. Pills are chips, buttons, search, the composer and badges.
7. **Pick the container by size.**
   - Short choices go in a bottom sheet.
   - Short forms (four fields or fewer) go in a full-height bottom sheet.
   - Long forms go full screen.
   - Dialogs are only for one-line confirmations.
8. **Labels name the outcome.** Write "Create thread", "Set reminder · Tomorrow 09:00" or "Turn on docs". Never write "OK", "Submit" or "Confirm" where the result can be named.
9. **Plain, short words, with one name per thing.**
   - Keep the Matrix names the rest of the app and the Matcha web client already use (Room, Space). "Chat" is the generic word.
   - Use sentence case and no exclamation marks.
   - Use no jargon: "event", "state", "power level", "kanban", "Yjs".
10. **Be honest about state.** Show pending, requested, offline and view-only exactly as they are.
    - "Saved" appears only after the server acknowledges the save.
    - A reminder stays "requested" until the bot answers.
    - There is no fake "delivered" tick, because Matrix has no delivery receipts.
    - A write that fails says so and offers Retry.
11. **Fast and light.** Content appears progressively. Use a skeleton or nothing, never a full-screen spinner while partial content exists. Use only standard Material 3 motion, at 200 ms or less, and never for decoration.
12. **Big screens keep both panes.** On the Fold, the list never hides behind a chat, sheets are centred on the window, and actions stay at the bottom of the pane they act on.

---

## 2. Tokens

### 2.1 Where the palette lives

| What | Where | Status |
|---|---|---|
| Palette | `CMP/colors/MatchaColors.kt`: `MatchaPalette` (named colours), `matchaColorsLight` (`:77`) and `matchaColorsDark` (`:181`), both built with `compoundColors*.copy(...)` | ✅ 0c4c71bebd; reconciled in ae95163aeb. ⏳ Four rows in §2.2 still to change. |
| Theme defaults | `CMP/theme/ElementTheme.kt`: `LocalCompoundColors` (`:82`), the `compoundLight`/`compoundDark` defaults (`:107-108`), the Black-theme copy (`:116-120`). Also `CMP/colors/SemanticColorsLightDark.kt:18-21`. | ✅ 0c4c71bebd |
| Material 3 scheme | `CMP/theme/MaterialColorSchemeLight.kt` and `…Dark.kt`. The surface containers are neutral, not Material's lavender. | ✅ e89f8dee41 |
| Notification avatars | `libraries/matrixmedia/impl/.../DefaultInitialsAvatarBitmapGenerator.kt:44,47` | ✅ 0c4c71bebd |
| Notification accent | `features/enterprise/impl-foss/.../DefaultEnterpriseService.kt:63-66` returns `MatchaPalette.green`. Its test is updated. Android adjusts the accent's contrast for header text itself. | ✅ 64dc7e7163 |
| Splash and window colours | `app/src/main/res/values/colors.xml` | ✅ ff6ea531cd |
| Never edit | `CMP/tokens/generated/**` (`DO_NOT_MODIFY.txt`). The high-contrast palettes are not wired into the app; leave them alone. | |

Previews, screenshots, `ForcedDarkElementTheme`, `ProtectedView` and `SunsetPage` all read the same defaults, so they match the app.

### 2.2 Palette: roles and decisions

- **Hex notation.** `#AARRGGBB` puts alpha first: `#1A111B21` is `#111B21` at 10 %.
- **What this table shows.** It lists every role a screen can ask for. Values are the code's at `8363bc94af`, except rows marked ⏳, which show the target value.

| Role | `SemanticColors` fields | Light | Dark |
|---|---|---|---|
| App background, top bars, lists | `bgCanvasDefault` | `#FFFFFF` | `#0A1014` (Black theme `#000000`) |
| Raised surface: sheets, menus, dialogs, dark nav bar, knock banner, scroll-to-bottom | `bgCanvasDefaultLevel1` | `#FFFFFF` | ⏳ `#12181C` (code: `#0A1014`) |
| Filled field, search pill, unselected chip | `bgSubtleSecondary`, `bgSubtleSecondaryLevel0` | `#F7F5F3` | `#20272B` |
| Pressed or secondary fill | `bgSubtlePrimary`, `bgBadgeSecondary` | `#EFECE9` | `#2A3236` |
| Primary text and icons | `textPrimary`, `iconPrimary`, `textActionPrimary` | `#0A1014` | `#F7F8FA` |
| Secondary text and icons | `textSecondary`, `iconSecondary` | `#5B6368` | `#8D9599` |
| Accent fill that carries icons only (FAB, send/mic, switch, radio, progress, focus) | `bgAccentRest`, `bgSuccessRest`, `iconSuccessPrimary`, `iconAccentTertiary`, `borderAccentPrimary`, `borderSuccessPrimary`, `borderFocused` | `#1DAA61` | `#21C063` |
| Accent fill, hovered and pressed | `bgAccentHovered`/`Pressed`, `bgSuccessHovered`/`Pressed` | `#1A9A58` / `#178C50` | `#1EB05B` / `#1C9F52` |
| Green that carries text or digits (filled buttons; unread and @ badges through `CounterAtom`) | `bgActionPrimaryRest`, `iconAccentPrimary` | `#1B8755` | `#21C063` |
| Filled button, hovered and pressed | `bgActionPrimaryHovered`/`Pressed` | `#187A4C` / `#156B43` | `#1EB05B` / `#1C9F52` |
| Green text | `textActionAccent`, `textActionSuccess`, `textSuccessPrimary` | `#1B8755` | `#21C063` (`textSuccessPrimary`: `#71EB85`) |
| Text and icons on green | `textOnSolidPrimary`, `iconOnSolidPrimary` | `#FFFFFF` | `#0A1014` |
| Tonal green (selected chip, nav pill, selection tint) | `bgAccentSubtle`, `bgAccentSelected`, `bgActionTertiarySelected`, `bgBadgeAccent`, `bgSuccessSubtle`, `borderAccentSubtle`, `borderSuccessSubtle` | `#D9FDD3` | `#103529` |
| Text and icons on tonal green | `textBadgeAccent` | `#15603E` | `#D9FDD3` |
| Dividers (rare: pane edge, nav-bar top edge in light) | `separatorPrimary`, `separatorSecondary` | `#1A111B21` | `#1AFFFFFF` |
| Errors | `textCriticalPrimary`, `iconCriticalPrimary`, `bgCriticalPrimary`, `borderCriticalPrimary` | `#EA0038` | `#FB5061` |
| Links in messages | `textLinkExternal` | ⏳ `#006BA6` (code: `#027EB5`) | `#53BDEB` |
| Warnings | `iconWarningPrimary` / `textWarningPrimary` | `#B37A00` / ⏳ `#8A5A00` (code: `#B37A00`) | `#FFD279` |
| Header gradient (makes every header band flat) | `gradientSubtleStop1` … `6` | transparent | transparent |
| Avatar fill / initials and group sender names, pairs 1–6 | `bgDecorative1..6` / `textDecorative1..6` | ⏳ 1 amber `#FFF0D4`/`#8A5A00`; 2 blue `#D2E8FE`/`#0063CB`; 3 pink `#FFDAE7`/`#D42A66`; 4 purple `#E8E0FF`/`#5E47DE`; 5 orange `#FEE2D8`/`#C4532D`; 6 teal `#CBF2EE`/`#028377` | ⏳ 1 amber `#3A2A08`/`#FFBE4D`; 2 blue `#092642`/`#53A6FD`; 3 pink `#36192A`/`#FF72A1`; 4 purple `#242447`/`#A791FF`; 5 orange `#35221E`/`#FC9775`; 6 teal `#092D2F`/`#42C7B8` |

**Code versus review: every disputed row, decided.**

| Field | 0c4c71bebd | Spec v1 | Code at 8363bc94af | Decision |
|---|---|---|---|---|
| `iconAccentPrimary` (light) | `#1DAA61` | `#1B8755` | `#1B8755` | Keep `#1B8755`. `CounterAtom` (`DS/atomic/atoms/CounterAtom.kt:96`) fills badges with it and draws white digits: 3.01:1 on `#1DAA61`, 4.52:1 on `#1B8755`. |
| Accent and filled-button hovered/pressed | jumped to `#1B8755`/`#15603E` | one step darker | spec values | Keep. A pressed state darkens by one step; it does not jump two tones. |
| `bgSubtlePrimary` (light) | `#EFEDEB` | `#EFECE9` | `#EFECE9` | Keep. |
| `separatorPrimary`/`Secondary` | opaque `#E7E8E9`/`#F0F1F2`; dark `#22292D`/`#182024` | alpha | alpha | Keep alpha, so one line works on white, wallpaper and raised surfaces. |
| `bgCanvasDefaultLevel1` (dark) | `#12181C` | `#0A1014` | `#0A1014` | ⏳ **Back to `#12181C`** (`MatchaPalette.nightRaised`). Level1 is the raised surface of the knock banner (`features/knockrequests/impl/.../KnockRequestsBannerView.kt:73`) and the scroll-to-bottom area (`M/timeline/TimelineView.kt:572`). On `#0A1014` both vanish into the dark wallpaper. `raisedSurface` now reads this field (§2.3). |
| `textSuccessPrimary` (dark) | `#21C063` | `#71EB85` | `#71EB85` | Keep. |
| `bgDecorative1`/`textDecorative1` | green | green | green, dark text `#25D366` | ⏳ **Amber.** Rule 2 forbids decorative green, the pair repeated the own-bubble and selected-chip tones, and `#25D366` is the logo green. |
| `textLinkExternal` (light) | `#027EB5` | not listed | `#027EB5` | ⏳ **`#006BA6`.** `#027EB5` is 4.07:1 on the outgoing bubble; `#006BA6` is 5.18:1 there and 5.75:1 on white. |
| `textWarningPrimary` (light) | `#B37A00` | not listed | `#B37A00` | ⏳ **`#8A5A00`**, which is 5.93:1 instead of 3.68:1. `iconWarningPrimary` stays `#B37A00` (icons need 3:1). |

⏳ **Outstanding `MatchaColors.kt` change, one commit:**
- Add `amberText = #8A5A00`, `amberTint = #FFF0D4`, `amberTintDark = #3A2A08` and `amberOnDark = #FFBE4D` to `MatchaPalette`.
- Change `blueLink` (`:70`) to `#006BA6`.
- Set dark `bgCanvasDefaultLevel1 = nightRaised` (`:200`).
- Set `bgDecorative1`/`textDecorative1` to the amber pair (`:105`, `:111`, `:206`, `:212`).
- Set light `textWarningPrimary = amberText` (`:176`).

**Also overridden.** These fields are overridden in code but have no dedicated role row above. They are listed so nothing is silent; keep them.

| Fields | Light | Dark |
|---|---|---|
| `bgActionPrimaryDisabled`, `iconDisabled`, `textDisabled`, `iconQuaternary(Alpha)` | `#B3B9BD` | `#3E474D` |
| `bgActionSecondaryRest`, `bgActionTertiaryRest` | `#FFFFFF` | `#0A1014` |
| `bgActionTertiaryHovered` | `#F7F5F3` | `#12181C` |
| `bgBadgePrimary` | `#0A1014` | `#F7F8FA` |
| `bgCanvasDisabled` | `#F7F5F3` | `#12181C` |
| `bgSubtleTertiary` | `#FAF9F8` | `#12181C` |
| `bgCriticalHovered`, `bgCriticalSubtle`, `bgCriticalSubtleHovered` | `#C4002F`, `#FFE5EA`, `#FFCCD6` | `#EA0038`, `#3A1820`, `#4A1F29` |
| `bgInfoSubtle` | `#D2E8FE` | `#092642` |
| `borderDisabled`, `borderInteractiveSecondary` | `#E7E8E9` | `#22292D` |
| `borderInteractivePrimary` / `borderInteractiveHovered` | `#8D9599` / `#5B6368` | `#6B7478` / `#8D9599` |
| `gradientActionStop1..4` | `#1DAA61`, `#1DAA61`, `#1B8755`, `#15603E` | `#21C063`, `#21C063`, `#1DAA61`, `#1B8755` |
| `iconPrimaryAlpha`, `iconSecondaryAlpha` | same as `iconPrimary` / `iconSecondary` | same |
| `iconTertiary(Alpha)` | `#8D9599` | `#6B7478` |

**Why there are two greens in light mode.**
- White on `#1DAA61` is 3.01:1: enough for icons and shapes, but it fails WCAG AA for text.
- White on `#1B8755`, WhatsApp's text green, is 4.52:1.
- So a fill that carries only an icon uses `#1DAA61`, and anything with letters or digits uses `#1B8755`.
- Dark mode uses one green, `#21C063`, with `#0A1014` content (8.01:1).

**Avatars keep their coloured initials,** using WhatsApp's own pairs with amber in place of green. Grey silhouettes would make every Matrix user without a photo look the same.

### 2.3 Chat-surface aliases (`DS/theme/ColorAliases.kt`)

✅ Landed in 4883fab532 (`:38-90`). ⏳ Change the definitions below so that every alias with a matching `SemanticColors` field is defined through that field. The Black theme then follows automatically. Add hex only for colours that are genuinely new.

| Alias | Light | Dark | Definition | Used by |
|---|---|---|---|---|
| `messageFromMeBackground` (`:39-40`) | `#D9FDD3` | `#144D37` | hex (new colour) | outgoing bubble |
| `messageFromOtherBackground` (`:42-43`) | `bgCanvasDefault` | `bgSubtleSecondary` | ⏳ fields | incoming bubble |
| `chatWallpaper` (`:46-47`) | `#F5F1EB` | `bgCanvasDefault` | ⏳ hex in light, field in dark | chat and thread background (plain fill) |
| `bubbleMetaText` (`:50-51`) | `#99000000` | `#99FFFFFF` | hex | time, "Edited", sending and sent ticks |
| `bubbleSecondaryText` | `#99000000` | `#B3FFFFFF` | ⏳ new | all other secondary text inside bubbles: quote bodies, "· N replies", file size, "Waiting for this message", "Message removed" |
| `readTick` (`:54-55`) | `#007BFC` | `#53BDEB` | hex | the double tick once read |
| `systemPillBackground` (`:58-59`) | `#E6FFFFFF` | `bgSubtleSecondary` | ⏳ dark from field | day separator, floating date, system events, unread band, loading pill |
| `noticePillBackground` / `noticePillText` (`:62-66`) | `#FFF0D4` / `#99000000` | `bgSubtleSecondary` / `#FFD279` | ⏳ dark background from field | beginning-of-chat notice, warning strips, tombstone banner |
| `composerSurface` (`:69-70`) | `bgCanvasDefault` | `bgSubtleSecondary` | ⏳ fields | composer pill, scroll-to-bottom button, reaction pill |
| `quoteOverlay` (`:73-74`) | `#0D000000` | `#0DFFFFFF` | hex | reply quote, link preview and file block inside bubbles |
| `raisedSurface` (`:77-78`) | `bgCanvasDefaultLevel1` | `bgCanvasDefaultLevel1` | ⏳ field | sheets, menus, dialogs, dark nav bar |
| `rowSelected` (`:81-82`) | `#26C2BDB8` | `#1AFFFFFF` | hex | open chat or task in the two-pane list, pressed rows |
| `mutedBadge` (`:89-90`) | `#0D000000` | `#1AFFFFFF` | hex | unread count of a muted chat (digits in `textSecondary`) |
| `focusHighlight` | `#1F0A1014` | `#1FFFFFFF` | ⏳ new | the jumped-to message (§3.3) |
| `chipOutline` (`:84-86`) | | | ⏳ **delete**. Stable chips have no border (§3.1). | |
| `floatingDateBadgeBackground` (`:114-115`) | `systemPillBackground` | | ✅ | |

⏳ Register the new aliases in the preview map (`:123-143`) and remove the `chipOutline` entry (`:142`).

**Black theme.** It is kept, because it is an existing user setting (`ElementTheme.kt:116-120`). With the definitions above:
- `chatWallpaper` becomes `#000000`;
- incoming bubbles, the composer and pills stay `#20272B`;
- raised surfaces stay `#12181C`;
- the outgoing bubble stays `#144D37`.

No further Black-specific values are needed.

### 2.4 Typography

- **Font.** Roboto, the system font. Compound already uses `FontFamily.Default`.
- **Aliases.** ✅ `aliasAppTitle` (22/27 sp, weight 500) and `aliasChatTitle` (17/22 sp, weight 500) are in `DS/theme/TypographyAliases.kt:26-32` (4883fab532).

| Use | Style | Size / weight |
|---|---|---|
| Chats tab title: the product name `screen_matcha_home_product_name` (fixed for every build variant, §6.2) | `aliasAppTitle`; `textActionAccent` in light, `textPrimary` in dark | 22 / 500 |
| Chats title while a space filter is active (the space name) | `fontHeadingSmMedium`, `textPrimary`, as today (`H/components/HomeTopBar.kt:129-141`) | 20 / 500 |
| Tasks and Spaces tab titles | `aliasAppTitle`, `textPrimary` | 22 / 500 |
| Other screen titles | `aliasScreenTitle` (unchanged) | 20 / 500 |
| Chat top-bar name / subtitle | `aliasChatTitle` / `fontBodyMdRegular`, `textSecondary` | 17 / 500, 14 / 400 |
| Row title / second line | `fontBodyLgMedium` / `fontBodyMdRegular`, `textSecondary` | 16 / 500, 14 / 400 |
| List time and deadline | `fontBodySmRegular`; `fontBodySmMedium` when unread | 12 |
| Message text | `fontBodyLgRegular` (`M/timeline/components/event/TimelineItemTextView.kt:98`, unchanged) | 16 / 400 |
| Sender name at the top of an incoming group bubble | `fontBodyMdMedium` in the sender's `textDecorative*` colour | 14 / 500 |
| Sender name inside a reply quote | `fontBodyMdMedium`, `textPrimary`. The colour lives on the quote bar only (§7.2). | 14 / 500 |
| Time inside a bubble | `fontBodyXsRegular`, `bubbleMetaText` | 11 |
| Chip label | `fontBodyMdMedium` when selected, `fontBodyMdRegular` when not. Weight is the cue that does not depend on colour. | 14 |
| Section header | `fontBodyMdMedium`, `textSecondary` | 14 / 500 |
| Badge digits, nav label | `fontBodySmMedium` (nav label when unselected: `fontBodySmRegular`, ✅ 2d70d95db9) | 12 |
| Day separator, system pill | `fontBodySmRegular`, `textSecondary` | 12 |
| Bottom-sheet title | `fontHeadingMdRegular`, centred, through the new `DS/components/SheetHeader.kt` (§2.5) | 22 / 400 |
| Large name field (task editor) | `fontHeadingMdRegular` | 22 / 400 |

### 2.5 Shape

| Element | Value | Where | Status |
|---|---|---|---|
| Chips | fully rounded, 32 dp tall | `DS/components/PillFilterChip.kt` | ✅ shape; ⏳ fill (§3.1) |
| Search pill | fully rounded, 48 dp, no border | `DS/theme/components/SearchField.kt` | ✅ 3ee240a6a1 |
| Composer pill | 24 dp radius, 48 dp minimum height | `TC/components/TextInputRoundedCornerShape.kt`, `TC/TextComposer.kt:643-661` | ⏳ |
| Send/mic button | 48 dp circle | `TC/TextComposer.kt:546-558` | ⏳ |
| FAB | 56 dp, 16 dp radius (Material 3 default) | `DS/theme/components/FloatingActionButton.kt` | ✅ |
| Message bubble | 10 dp radius (approx.). A tail 8 dp wide and 10 dp tall (approx.) sits on the top outer corner of the first bubble in a run. | `M/timeline/components/MessageEventBubble.kt:57,152-178` | ⏳ |
| Media inside a bubble | 6 dp | `M/timeline/components/event/TimelineItemImageView.kt:68` (10 → 6), `TimelineItemVideoView.kt:76` (already 6), `M/timeline/components/TimelineItemEventRow.kt:887` (10 → 6) | ⏳ |
| Reply quote, link preview, file block | 6 dp | `TimelineItemEventRow.kt:915-960`, `M/timeline/components/event/TimelineItemFileView.kt` | ⏳ |
| Bottom sheets | 24 dp top corners | `DS/theme/components/ModalBottomSheet.kt:65` | ✅ 3ee240a6a1 |
| Sheet header | 56 dp row: optional ✕ or ← at the start (48 dp target), centred title, optional end action | new `DS/components/SheetHeader.kt`, shared by every Matcha sheet and "Choose a space" | ⏳ |
| Dialogs | 28 dp | `DS/theme/components/AlertDialogContent.kt:391` | ✅ |
| Dropdown menus | 12 dp | `DS/theme/components/DropdownMenu.kt` | ✅ |
| Text fields | 8 dp, outlined | `DS/theme/components/TextField.kt` | ✅ |
| Board columns / task cards (wide layout only) | 16 dp / 12 dp | `MX/tasks/` | ⏳ |
| Doc and attach tiles | 12 dp rounded square (doc) / circle (attach) | `MX/docs/`, `M/messagecomposer/AttachmentsBottomSheet.kt` | ⏳ |
| Avatars | people and rooms are circles; spaces are 25 % rounded squares | `DS/components/avatar/AvatarShape.kt:17-33` | unchanged |

### 2.6 Spacing (8 dp grid with half steps)

- **Screen gutter:** 16 dp. Chip rows and list rows start 16 dp in.
- **Chat row:** 52 dp avatar (`AvatarSize.RoomListItem`), a 16 dp gap to the text, 12 dp vertical padding, at least 76 dp tall.
- **Chip row:** 8 dp between chips and 8 dp above and below. The search pill has 16 dp side margins and 8 dp below it.
- **Bubbles:**
  - 2 dp between messages from the same sender, 8 dp when the sender changes.
  - The tail tip sits 8 dp from the screen edge and the bubble body 16 dp from it.
  - Inner padding is 8 dp horizontal and 6 dp top and bottom.
- **Composer:** 6 dp from the screen edges and from the keyboard or navigation bar (approx.).
- **Buttons:** 40 or 48 dp tall. A sheet's primary pill is 48 dp.
- **Two-pane:** keep the existing 2 : 3 weights (`AN/LoggedInTwoPaneView.kt:30-31`).
  - Fold inner portrait (about 750 dp, approx.): a 300 dp list and a 450 dp chat.
  - Fold inner landscape (about 832 dp): 333 dp and 499 dp.
  - ⏳ Fix the comment at `:29` to "Material list-detail proportions (40/60)". The claim that WhatsApp uses the same split was never verified.

### 2.7 Elevation

| Surface | Light | Dark | Status |
|---|---|---|---|
| Top bars, lists, chip bars | none | none | ✅ palette |
| Bubbles, composer, system pills, scroll-to-bottom button | 1 dp shadow (approx.) | none; tone only | ⏳ (iii) |
| FAB | Material 3 default | flat (`ElementFloatingActionButtonDefaults.elevation()`) | ✅ 2d70d95db9 |
| Sheets | no tonal tint | no tonal tint; `raisedSurface` | ⏳ `ModalBottomSheet.kt:68`: `tonalElevation = 0.dp` in both modes, otherwise Material tints the dark sheet |
| Menus, dialogs | Material 3 default shadow | no shadow; `raisedSurface` | ✅ |
| Nav bar | none; a 1 dp `separatorPrimary` top edge | no edge; `raisedSurface`; tonal elevation 0 | ✅ 2d70d95db9 (`DS/theme/components/NavigationBar.kt`) |

### 2.8 Icons

> Round 2: Notes uses `document` and Channels uses `room`; see §6.3, item 7.

- **Style.** 24 dp, outlined. The selected state uses the `…Solid` variant.
- **Material Symbols.** ✅ The two Material Symbols drawables are in `libraries/designsystem/src/main/res/drawable/` (4883fab532, redrawn in ae95163aeb):
  - `ic_matcha_done_all.xml` (`done_all`: the read tick and the "Seen by" action);
  - `ic_matcha_archive.xml` (`archive`).

| Use | Icon |
|---|---|
| Chats tab | `Chat` / `ChatSolid` |
| Tasks tab, Tasks chip, task status "Done" | `CheckCircle` / `CheckCircleSolid` |
| Spaces tab | `Space` / `SpaceSolid` |
| New chat FAB | `ChatNew` |
| New task, new doc, create space, new thread | `Plus` |
| Search | `Search` |
| Overflow | `OverflowVertical`; the "More" chip uses `OverflowHorizontal` |
| All threads | `Threads` / `ThreadsSolid` |
| Remind me, reminder requested | `Notifications` / `NotificationsSolid` |
| Deadline | `Calendar` |
| In progress / To do | `Time` / `Circle` |
| Sending / sent / read | `Time` / `Check` / `ic_matcha_done_all` |
| Emoji / attach / camera / mic / send | `Reaction` / `Attachment` / `TakePhoto` / `MicOn` / `Send` |
| Voice recording: delete | `Delete` (through the existing `VoiceMessageDeleteButtonIcon`) |
| Scroll to bottom | `ChevronDown` |
| Document / edit doc / done | `Document` / `Edit` / `Check` |
| Formatting row | `Bold`, `Italic`, `Strikethrough`, `ListBulleted`, `ListNumbered`, `Quote`, `InlineCode`, `Code`, `Link`; the heading button is a text glyph "H" |
| Archive / archived | `ic_matcha_archive` |
| Offline / warning strip / encryption notice / not encrypted | `Offline` / `Warning` / `Lock` / `LockOff` |
| Waiting for a message (UTD) / message removed | `Time` / `Delete` |

---

## 3. Existing screens

### 3.0 Coverage: every feature module

David asked for WhatsApp's design "for whatever we have". Every module below either gets a bespoke pass (B) or inherits the palette and the global components of §2 only (I). Inherited screens are reviewed once against rules 1–9, with no layout changes.

| Module | Treatment | Notes |
|---|---|---|
| `home` | B, §3.1–3.2 | chat list, search, Spaces tab, nav bar, FAB |
| `messages` (chat, thread, thread list, long-press, pinned list, forward-from) | B, §3.3–3.4 | the pinned-messages list reuses the chat bubbles on the wallpaper |
| `roomdetails` (room and DM info) | B, §3.6 | WhatsApp group and contact info |
| `userprofile` | B, §3.6 | same header and rows as DM info |
| `preferences` (settings) | B, §3.7 | |
| `networkmonitor`, `appnav` sync pill | B, §3.8 | offline strip and sync pill |
| `matcha` | B, §4 | |
| `space` | B, §3.2 | space screen, Docs row |
| `startchat`, `createroom`, `invitepeople`, `invite` | I, plus chat rows | People pickers use §3.1 chat rows (52 dp avatar, no dividers) and the search pill. Selection is a green check badge on the avatar, as in WhatsApp's "New group". The next step is the green FAB. |
| `forward` | I, plus chat rows | the same picker; the send FAB is green with `Send` |
| `joinroom` (invite and join preview) | I | centred header (avatar 64 dp, name, topic); Join is the green pill; Decline is outlined |
| `poll` (create, results) | I | form fields from §2.5; the poll bubble is in §3.3 |
| `location` (share, view) | I | full-screen map; the share action is a green pill |
| `libraries/mediaviewer` | I | already full-screen dark; top-bar actions in white |
| `login`, `ftue`, `lockscreen` (PIN), `verifysession`, `securebackup`, `signedout`, `logout`, `deactivation`, `linknewdevice`, `migration`, `announcement` | I | green pills and green PIN dots come from the palette; illustrations stay Element's (not WhatsApp's) |
| `roomdetailsedit`, `rolesandpermissions`, `securityandprivacy`, `roommembermoderation`, `reportroom`, `leaveroom`, `knockrequests`, `roomdirectory`, `viewfolder`, `licenses`, `rageshake`, `analytics`, `share`, `call`, `callnative`, `roomcall`, `contentscanner` | I | Element Call is a web view and keeps its own look |
| `enterprise`, `cachecleaner`, `roomaliasresolver` | no UI | |

### 3.1 Home: Chats tab

**WhatsApp reference.** The stable Chats tab after the Material 3 refresh (2024), with the stable Lists chips (Apr–Oct 2024): a white flat bar with a left-aligned title, actions at the end, and a search pill under it. The chips scroll away with the list. There are no dividers, the nav bar is at the bottom and the FAB at the bottom end.

**Top bar** (`H/components/HomeTopBar.kt`)

- `:116-126`: the flat bar comes from the palette. Keep `statusBarsPadding`.
- **Title** (`:127-150`):
  - Chats shows `stringResource(screen_matcha_home_product_name)`, a fixed, untranslatable string (§6.2). It does not use `BuildMeta.applicationName`, which reads "… dbg" or "… nightly" on those builds (`app/build.gradle.kts:110`). No `HomeState` change is needed.
  - The title uses `aliasAppTitle` instead of `fontHeadingLgBold` (`:142`).
  - With a space filter active, the title shows the space name, as today.
  - Tasks shows `common_matcha_tasks`. Spaces currently returns `null` (`:135`); show `screen_home_tab_spaces`.
  - Plain text only, never a wordmark.
- **Account avatar** (`:151-158`): remove the navigation icon from the start. Move `NavigationIcon` (32 dp avatar, swipe-to-switch, `RedIndicatorAtom`, `TestTags.homeScreenSettings`) to the **end** of `actions`, before `⋮`.
  - Set `windowInsets` (`:169-173`) so the title starts at 16 dp, and fix `statusEmojiStartOffset` (`:405`).
- **Search icon** (`:195-202`): delete it; the search pill replaces it.
- **`⋮` menu** (`:204-251`): always show it, dropping the `RoomListConfig.HAS_DROP_DOWN_MENU` gate.
  - Items, in order: Create room (`action_create_room`), Create space (`action_create_space`), Settings (`action_open_settings`), then Invite and Report, gated as today.
  - ⏳ Add `CreateRoom`, `CreateSpace` and `Settings` to `H/components/RoomListMenuAction.kt`.
  - Map them in `H/HomeFlowNode.kt:146-155` to the existing `HomeEntryPoint.Callback.navigateToCreateRoom()`, `navigateToCreateSpace()` and `navigateToSettings()`.
- **Space filter button** (`:254-285`): remove `SpaceFilterButton`; it becomes the trailing "Spaces ▾" chip. This also removes the `bgActionPrimaryRest` user at `:273`.

**Search pill.** Add it in the collapsing block at `:175-184`, above `RoomListFiltersView`.
- Shape: `Surface(shape = CircleShape, color = bgSubtleSecondary)`, 48 dp tall, 16 dp side margins.
- Content: the `Search` icon in `iconSecondary`, then `action_search` in `textSecondary`.
- `onClick = onToggleSearch`. It is a fake field that opens the existing overlay.
- Change the bottom padding at `:180` from 16 dp to 8 dp.
- `enterAlways` (`H/HomeView.kt:182`) already pins the title and lets the pill and chips scroll away.

**Filter chips** (`H/filters/`)

- **Order.** **All · Favourites · Unread · Rooms · People · Invites**, then a trailing **Spaces ▾** chip.
  - Favourites comes right after All, for parity with the web fork (plan §1.1). WhatsApp puts Unread first; we deliberately don't.
  - Reorder `RoomListFilter.kt:18-23` to Favourites, Unread, Rooms, People, Invites. This is plan Phase 1.
  - **All** (`common_matcha_all`) is selected while `!state.hasAnyFilterSelected` and sends `ClearSelectedFilters`. It replaces the circular clear button (`RoomListFiltersView.kt:103-118,142-163`). Update `RoomListFiltersViewTest.kt:43`.
- **Single-select, fixed positions**, as in WhatsApp:
  - `DefaultFilterSelectionStrategy.select()` (`:24-28`) becomes `selectedFilters.clear(); selectedFilters.add(filter); filterSelectionStates.value = buildFilters()`. Delete the early return at `:25`; with it in place, tapping People while Rooms is selected does nothing.
  - `buildFilters()` (`:44-59`) becomes `RoomListFilter.entries.map { FilterSelectionState(it, isSelected = it in selectedFilters) }.toSet()`. Delete the subtraction at `:51`; with it in place, selecting Invites hides four chips.
  - `RoomListFilter.incompatibleFilters` (`RoomListFilter.kt:25-32`) is then dead; delete it.
  - Tapping the selected chip again deselects it, which selects All.
  - Delete the reorder animation and the scroll-to-start code (`RoomListFiltersView.kt:70-94,119-137`).
  - Tests: `features/home/impl/src/test/.../filters/RoomListFiltersPresenterTest.kt`. There is no `DefaultFilterSelectionStrategyTest`.
- **Shared chip.** ✅ `DS/components/PillFilterChip.kt` exists (4883fab532). ⏳ Restyle it to the **stable** look:
  - unselected: `bgSubtleSecondary` fill, no border (delete `:96`), label `textSecondary` in `fontBodyMdRegular`;
  - selected: `bgAccentSubtle` fill, label `textBadgeAccent` in `fontBodyMdMedium`;
  - no checkmark; at least a 48 dp touch target (keep `minimumInteractiveComponentSize`);
  - add a `role: Role = Role.RadioButton` parameter in place of the fixed `Role.Tab` (`:89`), and set `selected` semantics only for `RadioButton` and `Tab` (§7.2);
  - update the KDoc (`:45-58`).

  Then replace the private `RoomListFilterView` (`RoomListFiltersView.kt:165-211`) with it, and wrap the chip row in `Modifier.selectableGroup()`.
- **Row padding.** `RoomListFiltersView.kt:97`: change the start content padding from 8 to 16 dp; keep the 8 dp gap.
- **Spaces ▾ chip.** `common_spaces` with `ChevronDown`, `Role.Button`. It opens `H/spacefilters/SpaceFiltersView.kt`. While a space filter is active, the chip uses the selected style and shows the space name.

**Chat row** (`H/components/RoomSummaryRow.kt`)

| Anchor | Change |
|---|---|
| `:74` | `minHeight` 84 → 76 dp |
| `:88` | The selected background becomes `rowSelected`, full width, no inset. Also add `Modifier.semantics { selected = true }`. |
| `:196-221` | Keep the 52 dp avatar and the 16 dp gap; vertical padding → 12 dp |
| `:224-256` | Name: `fontBodyLgMedium`, `textPrimary`, one line. Time: `fontBodySmRegular`, `textSecondary`; when `hasNewContent && !isMuted`, `textActionAccent` in `fontBodySmMedium`. |
| `:297-298`, `:318-319`, `:343-344` | Preview: `maxLines = 1`, no `minLines` |
| `:323-333` | Keep the `Time` icon for "sending" |
| `:351-379` | Trailing items, in order: mute, @ badge, unread badge. The unread badge is `CounterAtom` (`iconAccentPrimary` `#1B8755` with white digits, 4.52:1). A muted chat's badge uses a `mutedBadge` fill with `textSecondary` digits. |
| `:389-402` | `unreadIndicatorCount`: show `numberOfUnreadMessages` for muted rooms. Update `RoomSummaryRowUnreadIndicatorCountTest`. |
| `:455-463` | @ badge: a 20 dp circle in `iconAccentPrimary` with `Mention` at 14 dp in `iconOnSolidPrimary` |
| `:94-123` | Invite row: Accept is the green filled pill, Decline the outlined pill |

Read ticks in the chat-list preview are skipped, because `H/model/LatestEvent.kt` has no send or read state.

**List container** (`H/components/RoomListContentView.kt`)
- Delete the dividers (`:137-139`, `:313-315`).
- Empty states (`:148-192`, `:405-434`):
  - `BigIcon(CompoundIcons.ChatSolid())`, a title, one grey line, then the primary button as a green pill.
  - Change the horizontal padding from 60 to 32 dp.
  - `BigIcon` is the one hero style everywhere in this spec.
- Empty filter (`:387-403`): add a "View all chats" (`screen_matcha_home_view_all_chats`) text button that sends `ClearSelectedFilters`. It needs `filtersState.eventSink`.
- Skeleton row (`H/components/RoomSummaryPlaceholderRow.kt:55`): change `top = 19.dp` to 12 dp.

**Bottom navigation**
- ✅ `NavigationBar` and `NavigationBarItem` are restyled (2d70d95db9): 80 dp, labels always shown, a 64 × 32 dp `bgAccentSubtle` pill, selected icon `textBadgeAccent`, unselected `iconSecondary`, flat with a light-mode top edge.
- ⏳ Replace the floating toolbar (`H/HomeView.kt:352-379`) with `Scaffold(bottomBar = { NavigationBar { … } })`. Delete the inset padding at `:236-238`.
- **Items: Chats · Tasks · Spaces.** The Tasks item appears only while `FeatureFlags.MatchaWorkspaces` is on.
- **Enum.** Append `Tasks` as the **last** entry of `H/HomeNavigationBarItem.kt:15-24`, so the saved ordinal (`H/HomePresenter.kt:103`, `HomeNavigationBarItem.from` `:35-37`) keeps its meaning.
  - Draw the bar from an explicit `val barOrder = listOf(Chats, Tasks, Spaces)`, not `entries` (`HomeView.kt:365`).
  - Tasks uses the label `common_matcha_tasks` and the icons `CheckCircle`/`CheckCircleSolid`.
  - Add the `Tasks` branch to every `when` over the enum: `HomeView.kt:243-246`, `:260-264`, `:292-314`, `HomeTopBar.kt:128-136`, and `HomeNavigationBarItem.icon` (`:26-32`).
- **Chats badge:** `NavigationBarIcon(imageVector, count = state.totalUnreadCount)`. It wraps `CounterAtom`, so it is green with white digits. There is no badge on Tasks or Spaces.
- **Re-tap** keeps scroll-to-top (`HomeView.kt:240-258`). On Tasks, re-tap scrolls the tab's list (the renderer owns its list state, so it is a no-op in v1).

**FAB**
- `HomeView.kt:259-268` and `:338-350`: use `floatingActionButtonPosition = FabPosition.End` (`:272` is currently `Center`). The FAB sits 16 dp from the end and 16 dp above the nav bar.

  | Tab | FAB | Content description | Action |
  |---|---|---|---|
  | Chats | `ChatNew` | `action_start_chat` | start a chat |
  | Tasks | none in the Home `Scaffold`; `MatchaTasksTabRenderer` draws its own (§4c) | | |
  | Spaces | `Plus` | `action_create_space` | create a space |

- Content padding (`HomeView.kt:275-284,307,320`): the `Scaffold`'s bottom padding now covers the bar and the inset. Change the list's bottom padding from 96 to 88 dp (FAB clearance), and make `lazyColumnContentPadding` horizontal-only so the inset isn't counted twice.

**Search overlay**
- `H/search/RoomListSearchView.kt:106-160`:
  - Drop the hairline (`:109-116`).
  - Use `SearchField` as the title, with a back arrow as the navigation icon.
  - Results reuse `RoomSummaryRow`.
- `H/search/GlobalSearchView.kt`:
  - Use the same field (`:135-141`, `:197-250`) and delete the divider (`:147`).
  - Replace the segmented Chats/Messages selector (`SearchTargetSelector`, `:253-277`) with two `PillFilterChip`s in a `selectableGroup`.
  - Room results (`:447-454`) use the 52 dp `RoomListItem` avatar.

### 3.2 Spaces tab (the WhatsApp Communities pattern)

> Round 2: with the Matcha workspaces on, this tab is the **Channels** tab; see §6.3, items 7 and 8.

**WhatsApp reference.** The stable Communities tab (beta 2.24.24.14, Nov 2024; still current in Sep 2026 coverage): rounded-square container avatars, chat rows, no dividers, a FAB.

**Decision: keep the label "Spaces"; do not relabel it "Communities".**
- "Space" is the name of this concept everywhere else in the app: about 140 Localazy strings, room invites, settings, the web client's "Space docs", and plan §2.4. Renaming only the tab gives one thing two names, which breaks rule 9.
- A Matrix Space is also not a WhatsApp Community: there is no announcements group, and spaces nest and can be public.
- We copy the pattern but not the vocabulary.
- If David wants "Communities" anyway, it is a string-only change (§6.2).

**Changes** (`H/spaces/HomeSpacesView.kt`)
- `:75`: remove the `SpaceHeaderRootView` header.
- `:94`, `:114`: remove the `HorizontalDivider`s.
- Rows (`MUI/components/SpaceRoomItemView.kt:84`): 52 dp squircle avatar, 76 dp tall, title `fontBodyLgMedium`. The second line is the topic, or "N rooms" (`common_matcha_room_count`) in `textSecondary`.
- The empty state (`:124-169`) keeps `BigIcon`; its button becomes a green pill.
- FAB: "Create space" (§3.1).
- Not in v1: WhatsApp's grouped community block, which would need a room list per space.

**Space screen** (`SP/root/SpaceView.kt`)
- Header (`:260-281`, `MUI/components/SpaceHeaderView.kt`): a centred 96 dp squircle avatar, the name in `fontHeadingMdBold`, and the subtitle `common_matcha_space_rooms` ("Space · N rooms") in `textSecondary`.
- Remove the dividers (`:278`, `:335`).
- Sections:
  1. A **Docs** row (§4e, phase vii).
  2. An 8 dp `bgSubtleSecondary` band.
  3. The section header `common_rooms`, then the room rows.
- The `⋮` menu (`:445-510`) gets a **Docs** item in phase vii.

### 3.3 Chat screen

> Round 2: the top bar actions changed; see §6.3, item 1.

**WhatsApp reference.** The stable conversation screen (2.24–2.26):
- a flat white top bar with an avatar, a name-and-subtitle block and call actions;
- a beige wallpaper;
- tailed bubbles with the time and ticks inside;
- pill-shaped day separators and system events;
- a composer pill with a separate round green mic/send button.

Beta-only items are not copied (§7.1).

**Top bar** (`M/topbars/MessagesViewTopBar.kt:67-152`)
- **Height** 64 dp, `bgCanvasDefault`, no gradient.
- **Back arrow** (`:82-87`): phone only; already hidden in two-pane.
- **Avatar:** a new `AvatarSize.ChatTopBar(40.dp)` in `DS/components/avatar/AvatarSize.kt` (after `:41`).
  - Use it in `M/MessagesPresenter.kt:386,392` (avatar data), `MessagesViewTopBar.kt:193` and `M/topbars/ThreadTopBar.kt:110,136`.
  - `TimelineRoom` (32 dp) stays as it is for `JoinRoomView.kt:617,632`, `SpaceView.kt:428` and `IconTitlePlaceholdersRowMolecule.kt:61`.
  - `dmUserStatus` stays on the avatar (`:117`).
- **Line 1:** the name (`aliasChatTitle`, `:178`), then the existing 16 dp icons, kept in place (`:109-145`):
  - Verified in `iconSecondary` (replaces `iconSuccessPrimary` at `:116`; green is not decoration).
  - The red `ErrorSolid` for a verification violation, unchanged. It is a security signal.
  - Shared history (`History` / `UserProfileSolid`) in `iconSecondary` (replaces `iconInfoPrimary` at `:136,:142`).
  - Only the name ellipsizes (`titleModifier`, `:99`).
- **Line 2, the subtitle** (new): `fontBodyMdRegular`, `textSecondary`, one line.
  - Group: `common_matcha_member_count` ("N members"; joined members).
  - DM: nothing; the name centres vertically.
  - Someone typing: DM `screen_matcha_room_typing_dm` ("typing…"); group: the existing `screen_room_typing_notification`, `screen_room_typing_two_members` and `screen_room_typing_many_members`, composed as `TypingNotificationView` does today. Typing text is `textActionAccent`.
  - Thread mode (flag on): the thread's name from `MatchaRoomController.threads` (§4.0). Typing wins over the name.
  - A tombstoned room has no subtitle.
- The typing row in the timeline (`M/timeline/components/TimelineItemVirtualRow.kt:68`) is no longer drawn.
- **Tap target:** the avatar-and-name block opens room info (§3.6).
- **Actions** (`M/MessagesView.kt:512-532`, `MessagesMenuActions`):
  - `CallMenuItem` (`:527`).
  - With the flag on, the Threads icon (`:519-526`) is removed, and a `⋮` (`OverflowVertical`, 48 dp) appears **only while `MatchaRoomController.hasChatBar` is false**. It holds: New thread, Tasks, All threads (when the room has threads), Remind me (§4d gating) and Space docs (when there is a joined parent space).
  - With the flag off, the Threads icon stays and there is no `⋮`.
- **Pinned-message banner** (`M/pinned/banner/PinnedMessagesBannerView.kt`; overlay at `MessagesView.kt:627-655`): keep the behaviour. Restyle it as a `bgCanvasDefault` strip directly under the bars, with a 16 dp `Pin` icon in `iconSecondary`, a one-line preview in `fontBodyMdRegular`, and no border or indicator bar.

**Wallpaper** (`M/MessagesView.kt:244-256`)
- Paint `.background(chatWallpaper)` on the root **before** `.imePadding()` (`:247`). The wallpaper then reaches under the navigation bar and around the composer.
- On the phone, cover the status-bar inset with a `bgCanvasDefault` strip (`Modifier.windowInsetsTopHeight(WindowInsets.statusBars)`), so the status bar matches the top bar.
- In the detail pane the top bar already draws under the status bar (`MessagesViewTopBar.kt:150`).
- `Scaffold(containerColor = Color.Transparent)` (`:256`).
- The composer sheet (`ExpandableBottomSheetLayout`) stays transparent.
- Plain fill in v1. A pattern of our own (CompoundIcons at 4–6 % alpha) is a later option; the doodle is never copied.
- Replace these canvas-coloured cut-out borders with `chatWallpaper`, or drop them:
  - `M/timeline/components/MessagesReactionButton.kt:93-97`
  - `M/timeline/components/receipt/TimelineItemReadReceiptView.kt:146`
  - `M/timeline/TimelineView.kt:523-525`

**Bubbles** (`M/timeline/components/MessageEventBubble.kt`, `TimelineItemEventRow.kt`)
- **Colours** come from the aliases (`MessageEventBubble.kt:181-187`).
- **Shadow:** 1 dp in light only, in the draw block `:96-131`.
- **Radius and tail:**
  - `BUBBLE_RADIUS` 12 → 10 dp (`:57`).
  - In `shape()` (`:152-178`), position First or None draws a tail with a `GenericShape`: top right for your own messages, top left for others. Middle and Last get 10 dp on all corners.
- **Remove** the avatar cut-out (`:118-128`), `avatarRadius` (`:58`), `BubbleState.cutTopStart` (`M/timeline/model/bubble/BubbleState.kt:20`) and `NEGATIVE_MARGIN_FOR_BUBBLE` (`TimelineItemEventRow.kt:157,618-623`).
- **Width:** keep `BUBBLE_WIDTH_RATIO = 0.78f` (`:190`) and cap at 520 dp (approx.).
- **Margins** (`TimelineItemEventRow.kt:611-649`):
  - Own messages: the body sits 16 dp from the end (`:627`).
  - DM incoming: 16 dp from the start (`:629`).
- **Groups** (`:576-592`, `:703-739`):
  - A **28 dp** avatar sits outside the bubble, 8 dp from the start, top-aligned with the first bubble of each run. Change `AvatarSize.TimelineSender` from 32 to 28 dp (`AvatarSize.kt:42`; read at `M/timeline/factories/event/TimelineItemEventFactory.kt:83`).
  - The bubble body starts at 48 dp, and later bubbles in the run align to it.
  - The sender name moves **inside** the bubble as the first child of `CommonLayout` (`:864-960`), in the sender's `textDecorative*`.
- **Spacing** (`:260-264`): 8 dp between runs, 2 dp within one.
- **Pin** (`:651-670`): a 12 dp `Pin` icon in the time row.
- **In-bubble text rule.** Inside any bubble, text uses only:
  - `textPrimary`;
  - `bubbleMetaText`;
  - `bubbleSecondaryText`;
  - `textLinkExternal`;
  - `textDecorative*`, and only for the sender name at the top of an incoming group bubble.

  No green text and no red text: a failed send shows the red error icon, and the time stays `bubbleMetaText`. Drop `textCriticalPrimary` from the time tint at `M/timeline/components/TimelineEventTimestampView.kt:53`. §7.2 has the contrast proof.

**Time and ticks** (`TimelineEventTimestampView.kt:41-134`)
- **Layout:** keep `ContentAvoidingLayout` (`TimelineItemEventRow.kt:809-846`).
- **Order:** [Edited] [pin] time [ticks]. The time is `fontBodyXsRegular` in `bubbleMetaText`, replacing `textSecondary` at `:53`.
- **Ticks** (your own messages only, about 16 × 11 dp):
  - Sending: `Time` at 12 dp, `bubbleMetaText`.
  - Sent: `Check`, `bubbleMetaText`.
  - Read: `ic_matcha_done_all` in `readTick`.
  - Failed: the error icon (`:111-119`).
  - There is no grey double tick.
- **"Read" means:**
  - DM: the other member's receipt is at or after this message.
  - Group: every joined member except you **and except `MatchaConfig.REMINDER_BOT_USER_ID`** has a receipt at or after this message. Bots send no receipts, so counting the bot would mean no group with the bot ever shows a read tick.
  - Rooms with more than 32 joined members (approx.) never show read; they stop at sent.
  - Compute it in one pass, newest to oldest, after grouping in `M/timeline/factories/TimelineItemsFactory.kt:104`. Use the `roomMembers` the factory already receives; receipts come from `factories/event/TimelineItemEventFactory.kt:124,152`.
- **Media overlay** (`TimelineItemEventRow.kt:794-807`): white time and ticks on a dark gradient scrim in the bottom-end corner, replacing the `bgSubtleSecondary` pill (`:803`).
- **Read-receipt avatars:** remove the row under bubbles (`:359-375`) and under system events (`TimelineItemStateEventRow.kt:86-93`).
  - "Who read it" moves to the long-press action **Seen by** (`common_seen_by`, icon `ic_matcha_done_all`), which opens the existing `ReadReceiptBottomSheet`.
  - The action is offered for your own messages only.

**Day separator, unread band, system events**
- **Day separator** (`M/timeline/components/virtual/TimelineItemDaySeparatorView.kt:30-51`):
  - A centred pill: `systemPillBackground`, 8 dp radius, 6 × 12 dp padding, `fontBodySmRegular` in `textSecondary`, 1 dp shadow in light.
  - Give `FloatingDateBadge.kt:124-140` the same values.
  - Keep the floating overlay (`TimelineView.kt:290-295`).
- **Unread band** (`M/timeline/components/virtual/TimelineItemReadMarkerView.kt:28-50`):
  - A full-width 28 dp band in `systemPillBackground` with "N unread messages" (`screen_matcha_room_unread_band`) in `fontBodySmMedium`.
  - **Data:** `TimelineItemReadMarkerModel` (a `data object` today) becomes `data class TimelineItemReadMarkerModel(val unreadCount: Int)`.
  - The count is computed in the same `TimelineItemsFactory` post-pass (`:104`): the event items newer than the marker that you did not send. Show 99+ above 99.
  - Dispatch: `TimelineItemVirtualRow.kt:44`.
- **System and state events:**
  - `M/timeline/components/MessageStateEventContainer.kt:39`: fill with `systemPillBackground` instead of `Color.Transparent`; keep the 8 dp radius (`:29`).
  - Maximum width stays 320 dp (`TimelineItemStateEventRow.kt:70`).
  - Text: `fontBodySmRegular`, centred, up to 3 lines (`event/TimelineItemStateView.kt:22-33`).
  - The collapsed-group header (`group/GroupHeaderView.kt:47-56`) uses the same pill plus a chevron.
- **Beginning of chat** (`virtual/TimelineItemRoomBeginningView.kt`): a `noticePillBackground` pill. Encrypted rooms add a 14 dp `Lock` icon. Text is `noticePillText`, with the existing strings.
- **Jumped-to message** (`M/timeline/components/TimelineItemRow.kt:331-356`, `Modifier.focusedEvent`):
  - Replace `gradientSubtleColors()`, now transparent, and the hairline with a full-width `focusHighlight` rectangle behind the row.
  - It holds for 1 s, then fades out over 600 ms.
  - Own bubbles are already tinted green, so the highlight is a neutral tint.
  - ⏳ Add a preview state to `FocusedEventPreview` (`:358`).

**Reactions** (`TimelineItemReactionsView.kt:61-135`, `MessagesReactionButton.kt:60-120`)
- **One** pill per message, 24 dp tall:
  - `composerSurface` fill with a 1.5 dp `chatWallpaper` ring;
  - up to 3 emoji at 14 sp plus the total count in `fontBodySmRegular`;
  - it overlaps the bubble's bottom edge by 12 dp and starts 12 dp in (approx.).
- Tapping it opens `ReactionSummaryView`.
- Remove the add-reaction and "N more" chips.

**Reply quote** (`TimelineItemEventRow.kt:915-960`; `MUI/messages/reply/InReplyToView.kt:87-132`)
- `quoteOverlay`, 6 dp radius, 4 dp inset, no border (`:933-940`).
- A 4 dp bar at the start in the quoted sender's decorative colour (`bgAccentRest` when quoting yourself).
- The quoted **name is `textPrimary`** (`fontBodyMdMedium`) and the body is `bubbleSecondaryText`.
- The thumbnail sits at the end, 48 dp (approx.).
- The composer's reply preview (`TC/ComposerModeView.kt:136-190`) uses the same block at the top of the composer pill.

**Thread footer** (`TimelineItemEventRow.kt:336-358`, `:425-496`)
- It sits inside the bubble: a 1 dp `separatorPrimary` line, then a 40 dp row with:
  - `ThreadsSolid` at 16 dp in `iconAccentPrimary` (an icon, so 3:1 is enough);
  - the thread name from `MatchaRoomController.threads` in `textPrimary` `fontBodyMdMedium` (when the name is null: `common_matcha_thread_untitled` in `bubbleSecondaryText`; with the flag off: `common_thread`);
  - "· " + `common_replies` in `bubbleSecondaryText`;
  - up to 3 stacked 20 dp avatars at the end.
- Tapping it opens the thread (§3.4).

**Composer** (`TC/TextComposer.kt`, `StandardLayout` `:456-561`)
1. **Pill.** Move the pill's background and shape from `TextInputBox` (`:650-661`) to a new `Column` wrapping the start slot, the input box (`:526-545`) and the trailing icons.
   - `composerSurface` fill, 24 dp radius, at least 48 dp tall, no border (drop `:651`, `:657`), 1 dp shadow in light, 6 dp from the edges.
   - `ComposerModeView` (reply or edit, `:663-667`) moves to the top of this `Column`, so the preview spans the whole pill.
2. **Start slot.** Keep the always-composed, always-enabled `IconButton` at `:486-523`; the comment at `:485` explains that it keeps keyboard focus. Only its icon and click change, and it moves inside the pill:
   - `Idle`: `Reaction` (content description `a11y_matcha_emoji`). It opens the emoji picker for insertion (below).
   - `Recording`: `VoiceMessageDeleteButtonIcon`, which cancels (`VoiceMessageRecorderEvent.Cancel`).
   - `Preview`: `VoiceMessageDeleteButtonIcon(enabled = !isSending)`, which deletes.

   This is WhatsApp's trash at the start of the recording bar. The ⊕ is gone; Attach moves to the trailing slot.
3. **Text field:** 16 sp, the existing "Message…" hint, grows to 6 lines.
4. **Trailing icons inside the pill**, shown only in `Idle`:
   - `Attachment` (`rich_text_editor_a11y_add_attachment`, calls `onAddAttachment`);
   - `TakePhoto` (`screen_room_attachment_source_camera_photo`), hidden while there is text. It is a new `onCameraClick` that sends `MessageComposerEvent.PickAttachmentSource.PhotoFromCamera` (`M/messagecomposer/MessageComposerEvent.kt:27`).

   Hide them with `AnimatedVisibility`, and check on the device that the keyboard keeps focus.
5. **Recording and preview bar.** `TC/components/VoiceMessageRecording.kt` and `VoiceMessagePreview.kt` render inside the same pill, after the trash:
   - Recording: a red 8 dp dot, the timer in `textPrimary` `fontBodyMdRegular`, then the live waveform (`LiveWaveformView`) in `iconSecondary`.
   - Preview: play/pause (24 dp `iconSecondary`), the waveform (played part `iconPrimary`, the rest `iconSecondary`) and the duration.
6. **End button** (`:546-558`, params `:241-323`): a 48 dp circle in `bgAccentRest` in every state.
   - `MicOn` when empty, `Send` with text, `Check` while editing; icons in `iconOnSolidPrimary`.
   - See `TC/components/SendButtonIcon.kt:35` and `TC/components/VoiceMessageRecorderButtonIcon.kt:40`. `:60` uses `bgActionPrimaryRest` today; change it to `bgAccentRest`.
   - Keep the recording gestures.
7. **Not-encrypted badge** (`:470-474`, `NotEncryptedBadge` `:563-583`): keep it, centred above the pill on the wallpaper. Use the 14 dp `LockOff` icon in `iconSecondary` (replacing `iconInfoPrimary` at `:574`) and `fontBodySmRegular` `textSecondary`.
8. **Attachment and EditCaption modes** (the caption composer in the media preview, `:477-482`): the pill has no start slot and no trailing icons, the spacers become 6 dp, and the end button is `Send` or `Check`.
9. **Text-formatting layout** (`:586-640`): untouched; it is behind a feature flag.

**Emoji insertion** (⏳ (iii))
- Add `MessageComposerEvent.InsertEmoji(val unicode: String)`.
- `MessageComposerPresenter` inserts it at the cursor: the Markdown editor replaces `selection` in `MarkdownTextEditorState.text`, and the rich editor uses its state's insert API.
- The picker is `EmojiPickerRenderer.Render(state, onSelectEmoji, selectedEmojis = persistentSetOf())` from `libraries/emoji/api`, shown in a `ModalBottomSheet` from `MessagesView`. The presenter comes from `EmojiPickerPresenter.Factory`, as for reactions.
- The sheet stays open for several emoji, and the keyboard returns when it closes.

**Attach sheet** (`M/messagecomposer/AttachmentsBottomSheet.kt:43-153`)
- A sheet without a title: a grid of 3 columns, or 4 at a window width of 600 dp or more.
- Each tile is a 56 dp circle in a decorative tone with the icon in that pair's content colour and a label under it (12 sp, up to 2 lines, centred).
- The existing strings are reused, and the callbacks are as today. No tile is green (rule 2).

  | Tile | Icon | Pair | Label |
  |---|---|---|---|
  | Gallery | `Image` | purple | `screen_room_attachment_source_gallery` |
  | Camera | `TakePhoto` | pink | `screen_room_attachment_source_camera_photo` |
  | Video | `VideoCall` | orange | `screen_room_attachment_source_camera_video` |
  | Files | `Attachment` | blue | `screen_room_attachment_source_files` |
  | Location | `LocationPin` | teal | `screen_room_attachment_source_location` |
  | Poll | `Polls` | amber | `screen_room_attachment_source_poll` |
  | **Task** (phase v) | `CheckCircle` | purple | `screen_matcha_room_attach_task`. It calls `MatchaRoomController.openNewTask()`. Shown when the user can add tasks (§4b). |
  | Formatting (when flagged) | `TextFormatting` | neutral (`bgSubtleSecondary` / `iconSecondary`) | `screen_room_attachment_source_text_formatting` |

**Long-press** (`M/actionlist/ActionListView.kt`)
- Keep the action sheet for v1; the contextual top-bar mode is deferred (§7.1).
- **Emoji tray** (`:233-242`, `EmojiReactionsRow` `:365-440`): fully rounded, 48 dp, `raisedSurface`, with 6 quick emoji and `ReactionAdd`.
- **Rows:** 56 dp, a 24 dp `iconSecondary` icon and a 16 sp label. The `bgActionPrimaryRest` highlight in this file becomes `bgAccentSubtle`.
- **Order:** the full list for `TimelineItemActionComparator.orderedList` (`M/actionlist/model/TimelineItemActionComparator.kt:16-38`). Destructive actions come last.
  1. EndPoll
  2. ViewInTimeline
  3. Reply
  4. ReplyInThread
  5. Forward
  6. CopyText
  7. CopyCaption
  8. CopyLink
  9. Pin
  10. Unpin
  11. **RemindMe** (§4d)
  12. Edit
  13. EditPoll
  14. AddCaption
  15. EditCaption
  16. **ReadBy** ("Seen by")
  17. ViewSource
  18. RemoveCaption (destructive)
  19. ReportContent (destructive)
  20. Redact (destructive)
  21. RetrySending and AbortSending (not rendered)

  Update `TimelineItemActionComparatorTest`.
- **Add to `TimelineItemAction`** (`M/actionlist/model/TimelineItemAction.kt:16-42`):
  - `RemindMe(action_matcha_remind_me, CompoundDrawables.ic_compound_notifications)`;
  - `ReadBy(common_seen_by, designsystem R.drawable.ic_matcha_done_all)`.
- **Selection mode:** a selected row gets a full-width `bgAccentRest` tint at 15 % alpha (`M/timeline/components/TimelineItemRow.kt:104`, where `selectionData.isSelected` is read).
- `M/timeline/components/reactionsummary/ReactionSummaryView.kt`: its `bgActionPrimaryRest` selection becomes `bgAccentSubtle`.

**Scroll to bottom** (`M/timeline/TimelineView.kt:501-610`)
- A 40 dp circle in `composerSurface` (replacing `bgCanvasDefaultLevel1` at `:572`), with a 1 dp shadow in light and `ChevronDown` in `iconSecondary`. It sits 12 dp from the end and 8 dp above the composer.
- **No count badge**, because `TimelineState.newEventState` has no count. When `newEventState == NewEventState.FromOther`, a 10 dp `iconAccentPrimary` dot sits on the top-end edge, and the button's `stateDescription` is `a11y_matcha_new_messages`.

**Chat states**

| State | Where | Design |
|---|---|---|
| No permission to post | `CantSendMessageBanner`, `MessagesView.kt:713-729` | In the composer slot: a 48 dp `composerSurface` pill (composer metrics) with `screen_room_timeline_no_permission_to_post` centred in `fontBodyMdRegular` `textSecondary`. No italics; drop `FontStyle.Italic`. |
| Replaced room (tombstoned) | `SuccessorRoomBanner`, `:733-746` | In the composer slot: a `noticePillBackground` card, 12 dp radius, 6 dp margins, with `screen_room_timeline_tombstoned_room_message` in `noticePillText` and a `textPrimary` text button "Jump to new room" (`screen_room_timeline_tombstoned_room_action`). |
| Identity verification violation | `DisabledComposerView` (`M/messagecomposer/DisabledComposerView.kt`), `:696` | The same pill as "no permission", with its existing text and a 16 dp `ErrorSolid` in `iconCriticalPrimary`. `IdentityChangeStateView` stays above it. |
| Message waiting for keys (UTD) | `event/TimelineItemEncryptedView.kt` | A normal bubble with a 16 dp `Time` icon and the existing text (for example `common_waiting_for_decryption_key`) in `bubbleSecondaryText`, no italics |
| Removed message | `event/TimelineItemRedactedView.kt` | A normal bubble with a 16 dp `Delete` icon and `common_message_removed` in `bubbleSecondaryText`, no italics |
| Loading older messages | `virtual/TimelineLoadingMoreIndicator.kt` | A centred 32 dp `systemPillBackground` circle holding a 16 dp progress ring in `iconAccentPrimary` |
| Live location banner | `LiveLocationSharingBanner`, `MessagesView.kt:648` | The same strip as the pinned banner; its Stop action is a `textPrimary` text button |
| Knock requests banner | `MessagesView.kt:656`; `KnockRequestsBannerView.kt` | A card on `bgCanvasDefaultLevel1`, 12 dp radius, 8 dp margins, under the bars; Accept is the green pill |
| Unencrypted room | `NotEncryptedBadge` | as in Composer, item 7 |
| Media bubble: image, video, location, gallery | `event/TimelineItemImageView.kt`, `…VideoView`, `…LocationView`, `…GalleryView` | Media fills the bubble with a 3 dp inset and 6 dp radius; the gallery is a 2 × 2 grid with 3 dp gaps. Time and ticks sit on the scrim overlay. |
| Voice message | `event/TimelineItemVoiceView.kt` | A 32 dp play/pause icon in `iconSecondary` (no circle), the waveform (played `iconPrimary`, rest `iconSecondary`), and the duration in `bubbleSecondaryText` |
| Audio file | `event/TimelineItemAudioView.kt` | the file block (next row) with a play icon |
| File | `event/TimelineItemFileView.kt` | A `quoteOverlay` block, 6 dp radius: a 40 dp `Attachment` tile in `bgSubtleSecondary`, the name in `textPrimary` on one line, and size · type in `bubbleSecondaryText` |
| Poll | `event/TimelineItemPollView.kt` | The question in `fontBodyLgMedium`. Options have 20 dp radio circles (`iconSecondary`, selected `bgAccentRest`) and 4 dp `bgAccentRest` progress bars on a `quoteOverlay` track. The "View votes" footer is a `textPrimary` text button (no green text in bubbles). |
| Sticker | `event/TimelineItemStickerView.kt` | no bubble, as today; time and ticks in a `systemPillBackground` pill |

### 3.4 Thread screen and thread list

**WhatsApp reference.** WhatsApp has no named threads and no tab bar, so we use its chat-list language: chips for switching and chat rows for lists. Threaded replies exist only in beta (§7.1) and are not used as a layout source.

**Thread screen**
- It is the same `MessagesView` (`M/threads/ThreadedMessagesNode.kt:295`).
- With the flag on:
  - the top bar is the **room** bar (avatar, room name, room actions), with the thread name as the subtitle;
  - the chip bar (§4a) shows the thread selected whenever it is visible;
  - wallpaper, bubbles and composer are identical to the room.
- With the flag off, `M/topbars/ThreadTopBar.kt:84-95` shows the thread's name as the title (from the root text, `common_thread` when it is empty) and the room as the subtitle. It hides the back arrow in two-pane, like `MessagesViewTopBar.kt:84`.
- **Navigation** (`M/MessagesFlowNode.kt:218,371`), per `INTEGRATION_BRIEF.md` §2:
  - Tapping a thread chip from a thread or the board **replaces** the top element; from the room it pushes.
  - General pops to `Messages`.
  - Back from a thread or the board goes to General; back again leaves the chat. The top-bar arrow and system back behave the same.

**All threads** (`M/threads/list/ThreadsListView.kt`, `NavTarget.ThreadsList` pushed by `openAllThreads`)
- **Screen:** full screen, in the end pane on the Fold. It has its own top bar: back, `common_matcha_all_threads`, and the room name as the subtitle.
  - It shows **no chip bar**: the All threads chip is an action that opens this page.
  - `MatchaChatBarSelection.AllThreads` is removed from the API (§4.0).
- **Chips** under the bar (flag on): **Active · Archived** (`common_matcha_active`, `common_matcha_archived`). They are `PillFilterChip`s in a `selectableGroup`, with Active by default.
  - Archived flags come from `MatchaRoomController.threads`.
  - With the flag off there are no chips, and the list is as today.
- **Rows** (`:186-322`): 76 dp, no dividers (delete `:151`).
  - Leading: a 52 dp `bgSubtleSecondary` circle with `Threads` at 24 dp in `iconSecondary`.
  - Line 1: the name (`MatchaRoomController.threads`, else the root text, else `common_matcha_thread_untitled` in `textSecondary`), and the time of the last reply at the end. The time is `textSecondary`; it turns `textActionAccent` once unread data exists (`:214-215` hard-codes false today).
  - Line 2: "Sender: latest message" in `textSecondary` on one line, with a trailing 14 dp `Threads` icon and the reply count.
- **Tap:** opens the thread. **Long-press** opens the thread actions sheet (§4a), with the flag on.
- **Footer:** "Load older threads" (`screen_matcha_threads_list_load_older`), a full-width `textActionAccent` text row on `bgCanvasDefault`, shown while the list has more.
- **States:**

  | State | UI |
  |---|---|
  | Loading | 3 skeleton rows (`RoomSummaryPlaceholderRow` metrics) |
  | Load failed | `BigIcon(Threads)`, `screen_matcha_threads_list_load_error`, green pill `action_retry` |
  | Empty (Active) | `BigIcon(Threads)`, `screen_matcha_threads_list_empty_title`, `screen_matcha_threads_list_empty_body` |
  | Empty (Archived) | `screen_matcha_threads_list_archived_empty` |

### 3.5 Two-pane on the Fold 7 inner screen

**WhatsApp reference.** The side-by-side view on Android tablets and foldables (beta 2.23.5.x, Mar 2023). The list stays at the start with the bottom nav inside it.

- **Pane widths:** keep the 2 : 3 weights (§2.6). A fixed 360 dp list would shrink the chat pane to 240–340 dp on 600–700 dp windows.
- **List pane:** Home, including its top bar, search pill, chips, list, `NavigationBar` and the Chats or Spaces FAB (Tasks draws its own). There is no navigation rail.
- **Selection:** the open chat's row is tinted `rowSelected` and exposes `selected` semantics.
  - On the Tasks tab, the row of the task whose thread is open gets the same tint. The renderer reads the open room and thread from `AppNavigationStateService` (`NavigationState.Thread`, `services/appnavstate/api/.../NavigationState.kt`), so no API change is needed.
- **Divider:** keep the hairline (`AN/LoggedInTwoPaneView.kt:82-88`) in `separatorPrimary`.
- **Top bars** in both panes are flat `bgCanvasDefault` at 64 dp.
- **Empty end pane** (`AN/LoggedInTwoPaneView.kt:64-65`, `AN/twopane/NoRoomSelectedView.kt:39-60`):
  - Keep `IconTitleSubtitleMolecule` and `BigIcon`, and pick the copy by the selected Home tab:

    | Tab | Icon | Title | Subtitle |
    |---|---|---|---|
    | Chats | `Chat` | `common_no_chat_selected` ("No chat selected") | `common_no_room_selected` ("Select a chat to start messaging") |
    | Tasks | `CheckCircle` | `common_matcha_no_task_selected` | `common_matcha_select_task` |
    | Spaces | `Space` | `common_matcha_no_space_selected` | `common_matcha_select_space` |

  - The tab comes from `HomeEntryPoint.Callback.onTabSelected(HomeTab)` (§4.0). `LoggedInFlowNode` keeps it in a `mutableStateOf(HomeTab.Chats)` and passes it to `NoRoomSelectedView(tab)`.
- **End-pane content:** chat, thread, All threads, task board, Archived tasks, Docs list and Docs editor.
- **Sheets:** sheets (task editor, Choose a chat, reminders) use the Material 3 maximum width of 640 dp, centred on the **window**, including the editor opened from the Tasks tab in the list pane.
- **Tasks tab:** the list stays in the list pane; tapping a task opens its chat and thread in the end pane.
- **Compact height** (phone or cover screen in landscape): there is no two-pane, because `hasTwoPaneWindowSize()` needs a non-compact height. The chip bar is hidden there (§4a), leaving the top bar, the timeline and the composer.

### 3.6 Room details and contact info (`features/roomdetails/impl/.../RoomDetailsView.kt`, `features/userprofile`)

**WhatsApp reference.** Stable group info and contact info (2024): a big centred header, a row of outlined action buttons, then sections on white separated by grey bands. It has no dividers and puts the red "Exit" and "Report" rows last.

- **Top bar** (`RoomDetailsTopBar`, `:362`): back, no title (as in WhatsApp), and `⋮` with Edit when the user can edit.
- **Header** (`RoomHeaderSection` `:464`, `DmHeaderSection` `:520`):
  - a centred 120 dp avatar;
  - the name in `fontHeadingMdRegular` `textPrimary`;
  - the subtitle: rooms show `common_space` / `common_room` · `common_matcha_member_count`; DMs show the Matrix ID;
  - the badges (`BadgeList` `:585`) as small tonal chips under the subtitle.
- **Actions row** (`MainActionsSection` `:397-460`):
  - up to 4 equal buttons, each 64 dp tall, 12 dp radius, a `bgCanvasDefault` fill with a 1 dp `borderInteractiveSecondary` border;
  - a 24 dp icon in `textActionAccent` and a label in `fontBodyMdRegular` `textPrimary`;
  - the order: Call, Invite, Search, Share; existing actions only.
- **Sections:** replace each `PreferenceCategory` top divider (`:203`, `:244`, `:296`, `:316`, `:837`) with a private `SectionGap()`, an 8 dp `bgSubtleSecondary` band; set `showTopDivider = false`.
  - The section order:
    1. Topic (`:649`);
    2. "Media and files" (`MediaGalleryItem` `:820`), with the count at the end;
    3. Notifications, Favourite, Pinned messages, Polls, Security and privacy;
    4. a members section headed `common_matcha_member_count`, with a first row "Invite people" (a 40 dp `bgAccentRest` circle with a white `Plus`) and the members row;
    5. last, Leave and Report in `textCriticalPrimary` with red icons.
- **Rows:** a 24 dp `iconSecondary` leading icon, the title in `fontBodyLgRegular`, an optional grey second line, no dividers.
- `userprofile`: the same header and actions (Message, Call, Share) and the same row style.

### 3.7 Settings (`features/preferences/impl/.../root/PreferencesRootView.kt`)

**WhatsApp reference.** Stable Settings (2024): a profile header row, then icon rows without dividers.

- **Top bar:** back, "Settings" (`common_settings`).
- **Profile row** (`UserPreferences`, `:112`): a 64 dp avatar, the display name in `fontBodyLgMedium`, and the Matrix ID in `textSecondary`. It is 88 dp tall, and tapping it opens the profile.
- **Rows:** a 24 dp `iconSecondary` icon at 16 dp, the title in `fontBodyLgRegular` at 72 dp, an optional one-line `textSecondary` subtitle; 56 dp, or 72 dp with a subtitle.
- Delete the `HorizontalDivider`s (`:133`, `:197`, `:217`, `:275`, `:361`). A single 8 dp `bgSubtleSecondary` band separates the profile row from the list, and another separates the list from Sign out.
- **Sign out:** the last row, in `textCriticalPrimary` with a red icon. The version footer is centred `fontBodySmRegular` `textSecondary`.
- **Sub-pages:** they keep `PreferenceCategory`. Its divider (`DS/components/preferences/PreferenceCategory.kt`) is the alpha hairline from the palette, and section titles become `fontBodyMdMedium` `textSecondary`.

### 3.8 Offline, sync and failed writes (all screens)

- **Offline:** keep the existing full-width strip (`features/networkmonitor/api/.../ui/ConnectivityIndicator.kt`, placed by `ConnectivityIndicatorContainer` at `AN/LoggedInFlowNode.kt:676`). It already spans both panes above the top bars and handles the insets.
  - Restyle: 28 dp tall, `bgSubtlePrimary`, a 14 dp `Offline` icon in `iconSecondary` and `common_offline` in `fontBodySmMedium` `textSecondary`.
  - This is the only offline banner; screens never add their own.
- **Sync pill** (`AN/loggedin/SyncStateView.kt`): keep it at top centre. It takes the `systemPillBackground` look of §3.3 with `textSecondary` text.
- **Failed writes** (every Matcha write fails fast while offline; plan §6): no screen disables actions in advance. A failed write shows the snackbar `common_matcha_write_failed` with **Retry** (`action_retry`), which re-runs the same write.
  - The exceptions are sheets: their error shows inline above the pill, and the sheet stays open.
- **Docs editor:** the editor's own offline strip (§4e) is the one exception, because edits wait in memory.

### 3.9 Notifications (plan Phase 6)

- **Accent:** `MatchaPalette.green` (✅ 64dc7e7163). Avatars are the Matcha initials bitmaps (✅).
- **Style:** `MessagingStyle` (`libraries/push/impl/.../notifications/factories/NotificationCreator.kt`).
  - The conversation title is the room name, and lines read "Sender: message", as in WhatsApp.
  - A thread message uses `common_matcha_notification_thread_title` ("Thread name · Room name") as the title, but only when a live `MatchaRoomStore` for that room already knows the name (the room is open, or the Tasks tab holds it). Otherwise it uses the room name, as today.
  - No store is opened for a notification.

---

## 4. New Matcha screens

### 4.0 Contract: screens, owners, API and panes

This table is the contract that the feature track, the integration step and this spec all follow. `FEATURE_TRACK_BRIEF.md` and `INTEGRATION_BRIEF.md` are updated to match (§6.1).

| Screen | Owner (package, class) | Host and call | Phone | Fold inner screen |
|---|---|---|---|---|
| Chip bar, plus the sheets it hosts: New thread, Rename, thread actions, More menu, and the new-task editor when opened from a chat | `MX/chatbar/`: `DefaultMatchaChatBarRenderer` and `DefaultMatchaRoomController` (both `RoomScope`) | `MessagesView`'s `topBar` slot, after the top bar: `MatchaChatBarRenderer.Render(General` or `Thread(id), actions, modifier)`. It is always composed while the flag is on, because it hosts sheets even when it draws no bar. | under the top bar | end pane |
| `⋮` fallback items (New thread, Tasks, All threads, Remind me, Space docs) | features/messages, `MessagesMenuActions` | shown while `MatchaRoomController.hasChatBar` is false; they call `openNewThread()`, `actions.openTasks()`, `openAllThreads()`, a `MatchaReminderTarget()` and `openSpaceDocs` | top bar | end pane |
| Task board: its own top bar and chip bar, tabs, rows, task actions sheet, editor sheet, Archived tasks | `MX/tasks/`: `TasksNode`, through `DefaultMatchaRoomEntryPoint` | `MessagesFlowNode` `NavTarget.Tasks(initialThreadId)` → `createTasksNode(this, buildContext, Params(initialThreadId), callback)` | full screen | end pane; sheets centred on the window |
| All threads | features/messages, `ThreadsListView` | `NavTarget.ThreadsList`, pushed by `openAllThreads` | full screen | end pane |
| Thread names and archive flags in features/messages (thread subtitle, `ThreadTopBar`, bubble footer, thread list) | `MatchaRoomController.threads` | injected into `MessagesPresenter` and `ThreadsListPresenter` | | |
| Reminder sheet | `MX/reminders/`: `DefaultMatchaReminderRenderer` (`RoomScope`) | `MessagesView`: `Render(target, onDismiss)`; the chip bar and the tasks node use it internally | sheet | sheet centred |
| Tasks tab: list, chips including Archived, FAB, Choose a chat sheet, editor sheet, actions sheet | `MX/taskstab/`: `DefaultMatchaTasksTabRenderer` (`SessionScope`) | `HomeView` content when `HomeNavigationBarItem.Tasks`: `Render(contentPadding, onOpenRoom, onOpenThread, modifier)` | Home tab | list pane |
| Opening a task's thread from Home | appnav | `HomeEntryPoint.Callback.navigateToThread(roomId, threadId)` → `LoggedInFlowNode.attachRoom(...)` then `attachThread(threadId, null)` | chat, then the thread | end pane |
| Empty end pane, per tab | appnav, `NoRoomSelectedView` | `HomeEntryPoint.Callback.onTabSelected(HomeTab)` | | end pane |
| Docs list, editor and setup | `MX/docs/`, through `MatchaSpaceDocsEntryPoint.createNode` | `SpaceFlowNode` `NavTarget.Docs(documentId)`; `MessagesFlowNode` `NavTarget.SpaceDocs(spaceId)` | full screen | end pane |
| Docs row in the space screen | features/space, `SpaceView` | opens `NavTarget.Docs()` | row | end pane |

**API changes** (each is minimal; the briefs are updated to match):

1. `MatchaChatBarSelection.AllThreads` is **removed** (`MA/MatchaChatBarRenderer.kt:39-40`). All threads is a page, not a selection.
2. `MatchaRoomEntryPoint.Callback` **gains** `fun openRoomDetails()` (`MA/MatchaRoomEntryPoint.kt:23-35`). The board draws its own room top bar, and tapping its name block opens room info.
3. A **new** `MA/MatchaRoomController.kt`, room scoped. It is how features/messages reaches Matcha state without importing `impl`:
   ```kotlin
   interface MatchaRoomController {
       /** True when this room has something for the chip bar: a named active thread or a board. */
       val hasChatBar: StateFlow<Boolean>
       /** Matcha names and archive flags of the room's threads. */
       val threads: StateFlow<ImmutableMap<ThreadId, MatchaThreadInfo>>
       /** Shows the New thread sheet (hosted by the chip bar renderer). */
       fun openNewThread()
       /** Shows the task editor for a new task in this chat (hosted by the chip bar renderer). */
       fun openNewTask()
   }
   data class MatchaThreadInfo(
       /** The shared name, else the root message text; null when only the event id is known. */
       val name: String?,
       val isArchived: Boolean,
   )
   ```
4. `features/home/api` gains `enum class HomeTab { Chats, Tasks, Spaces }`, plus `HomeEntryPoint.Callback.onTabSelected(tab: HomeTab)` and `navigateToThread(roomId: RoomId, threadId: ThreadId)`.
5. `MatchaTasksTabRenderer` keeps its signature. It draws its own FAB inside `contentPadding`, 16 dp from the end and 16 dp above `contentPadding.bottom`.

**Store additions** (`MX/store/MatchaRoomStore.kt`, phases iv–v; they extend the store, they don't rewrite it):
- `MatchaRoomSnapshot.unreadable: Boolean`: the Matcha timeline holds an event it cannot decrypt.
- `MatchaRoomSnapshot.warning: MatchaWarning?`: one of `Undecryptable`, `HistoryUnavailable`, `NoAccess` or `Network`. These are the plan's merged warning line (plan Phase 2).
- `MatchaRoomSnapshot.boardPointerRead: Boolean`: true after the first **successful** `getStateEventsJson(STATE_KANBAN)` (`:217`). Today a failed read and an absent pointer look the same.
- `MatchaRoomSnapshot.loadFailed: Boolean`: the Matcha timeline could not be opened (`:135-137`).
- `ThreadMetadata.nameSource: Explicit`, `RootBody` or `EventId` (`MX/protocol/ThreadMetadataProtocol.kt:21-26,55-72`).

### 4a. In-chat chip bar (`MX/chatbar/`)

> Round 2: the Tasks and All threads chips moved to the top bar, and the visibility rule changed; see §6.3, item 2.

**Reference.** It mirrors the chat-list chips (§3.1): the same chip, the same row metrics and the same single-select behaviour. WhatsApp has nothing like it, so it must look like something WhatsApp already has.

**When the bar is drawn.** Rule 1: one bar, unless the chat uses the feature.
- `hasChatBar` is true when the room has a board, or at least one active thread whose `nameSource == Explicit`. Most DMs have neither, so DMs show no bar by default.
- The board always draws the bar, whatever `hasChatBar` says.
- Hidden, regardless of the above:
  - in selection mode;
  - when the window height is Compact (phone landscape);
  - while the keyboard is open on a Compact width;
  - when the flag is off;
  - when the room isn't joined.
- **Collapse on scroll** (room and thread timelines only; `enterAlways`): the bar slides up while the user scrolls toward older messages, and comes back on any scroll toward newer messages or at the bottom. Use 200 ms. Implement it with a `nestedScroll` connection on the `Scaffold` content.
- Until `snapshot.isLoaded`, the renderer draws nothing. Once loaded, the bar slides in if `hasChatBar` is true.
- While the bar is hidden, its entries live in the chat's `⋮` (§3.3) and in the attach sheet's Task tile.

**Placement** (`M/MessagesView.kt:258-303`): wrap the top-bar `when` in `Column { <top bar>; chatBarRenderer.Render(...) }`. Scaffold padding pushes the timeline down. Never use the banner overlay at `:627-655`.

**Anatomy**
- 48 dp tall: 8 dp padding, a 32 dp chip, 8 dp padding.
- `bgCanvasDefault`, continuing the top bar, with no divider.
- A `LazyRow` with 16 dp start and end padding and 8 dp between chips, wrapped in `selectableGroup()`.

**Chips, in order**

| # | Chip | Role | Content | Shown when |
|---|---|---|---|---|
| 1 | **General** | `Tab` | label | always; selected on the room timeline |
| 2 | **Tasks** | `Tab` | label, plus the open count (To do + In progress) as trailing text when it is above 0 | always; selected on the board |
| 3–6 | **Threads** | `Tab` | name, at most 160 dp wide, ellipsized; an `EventId` fallback shows `common_matcha_thread_untitled` | up to 4 active threads, newest activity first |
| 7 | **Selected thread** | `Tab` | name, with a leading 16 dp `ic_matcha_archive` when it is archived | only when the open thread isn't among chips 3–6 |
| 8 | **New thread** | `Button` | leading `Plus`, label | `canManageThreads && canSendMessages` |
| 9 | **All threads** | `Button` | leading `Threads`, label | always; it pushes the All threads page |
| 10 | **More** | `Button` | icon-only `OverflowHorizontal`, content description `common_matcha_more` | when any of its items applies |

**Behaviour**
- Tapping a Tab chip switches the content (§3.4), and the chip scrolls into view (`animateScrollToItem`). Re-tapping the selected chip does nothing.
- **Long-pressing a thread chip** opens the **thread actions sheet** (`SheetHeader` with the thread name), with these rows:
  - Rename thread;
  - Archive or Unarchive;
  - Remind me (§4d gating);
  - View task, which opens `NavTarget.Tasks(initialThreadId)` and then the editor for that thread's task.

  Without `canManageThreads`, only Remind me and View task are offered.
- **More** opens a dropdown (`raisedSurface`, 12 dp radius, 48 dp rows with icons):
  - Remind me (`Notifications`);
  - Space docs (`Document`) when `actions.openSpaceDocs != null`;
  - Earlier chat doc (`Document`), only when that legacy doc exists.
- **New thread sheet:**
  - `SheetHeader` ✕ with `common_matcha_new_thread`.
  - An outlined field `screen_matcha_threads_name_hint`, whose counter appears from 80/100.
  - The helper `screen_matcha_threads_name_helper`.
  - The pill `screen_matcha_threads_create`.
  - Errors appear under the field: `common_matcha_error_name_empty` and `common_matcha_error_max_chars` (100).
  - On success the sheet closes and the new chip is selected.
- **Rename** uses the same sheet, with the title `screen_matcha_threads_rename_title` and `action_save`.
- **Archive** has no dialog, as with WhatsApp's chat archiving. It shows the snackbar `screen_matcha_threads_archived_snackbar` with `action_matcha_undo`. The chip leaves the bar, and General is selected if it was selected. Unarchive shows `screen_matcha_threads_unarchived_snackbar` with Undo.

**States**

| State | UI |
|---|---|
| Write in flight | The sheet's pill shows a 20 dp progress ring and ignores further taps; the thread-actions rows are disabled. |
| Create: the first send fails | The sheet stays open, with `screen_matcha_threads_create_failed` above the pill. |
| Create: the metadata send fails | The sheet closes and the new thread opens. The snackbar `screen_matcha_threads_setup_failed` offers Retry, which re-sends the bootstrap reply. |
| Rename, archive, unarchive or Undo fails | snackbar `common_matcha_write_failed` with Retry (§3.8) |
| Offline | the same as a failure; the global strip says Offline |
| Warning | A 32 dp strip under the bar: `noticePillBackground`, a 16 dp `Warning` icon and one line of `noticePillText`. The text is `screen_matcha_threads_warning_undecryptable`, `…_history`, `…_access` or `…_network`, from `snapshot.warning`. |
| No permission to manage threads | New thread is hidden; Rename and Archive are absent |
| Can't send messages | New thread and Remind me are hidden |

**Later, not in v1:** a green dot on thread chips with unread replies.

### 4b. Per-chat task board (`MX/tasks/`)

> Round 2: channel tasks and board creation from every chat; see §6.3, items 3 and 4.

**Reference.**
- Status switching copies the long-standing stable **Media · Docs · Links** tabs: a green label and indicator on the selected tab.
- Rows copy chat rows, and archiving copies chat archiving.
- Tabs, not chips, keep the status switch distinct from the chip bar directly above it.

**Frame**
- `TasksNode` draws **its own top bar**:
  - back on the phone only;
  - the 40 dp room avatar (`AvatarSize.ChatTopBar`) and the room name (`aliasChatTitle`);
  - the subtitle `common_matcha_tasks`;
  - a tap on the name block calls `callback.openRoomDetails()`.

  The room avatar and name come from the room's `roomInfoFlow`. There is no call button and no `⋮`.
- Then `MatchaChatBarRenderer.Render(Tasks, …)`.
- There is no composer.
- The FAB "New task" (`Plus`, content description `screen_matcha_tasks_new`) is shown when `canWriteCards && canManageThreads && canSendMessages`, because adding a task creates its thread.

**Phone layout** (board width below 600 dp, which includes the Fold's end pane)
- **Tabs:** `PrimaryScrollableTabRow`, 48 dp, `bgCanvasDefault`, 16 dp edge padding, scrollable so it survives 200 % text.
  - Each tab is the label (`screen_matcha_tasks_status_todo`, `_doing`, `_done`) followed by its count as a separate `Text` in `textSecondary`. No format string is needed.
  - Selected: the label is `textActionAccent` `fontBodyMdMedium`, with a 3 dp `bgAccentRest` indicator with a rounded top. Unselected: `textSecondary`.
  - Swiping between tabs uses a `HorizontalPager`.
- **Background:** `bgCanvasDefault`, with no wallpaper. A board is a list, not a conversation.
- **List:**
  1. An **Archived** row, only when archived tasks exist: a 52 dp `bgSubtleSecondary` circle with `ic_matcha_archive`, the title `common_matcha_archived`, and the count in `textActionAccent`. It opens Archived tasks, an internal screen of `TasksNode`.
  2. Task rows, by deadline (soonest first, none last), then newest.
  3. A "Load older tasks" footer while `hasMoreHistory` is true.

**Task row** (shared with §4c)

| Part | Spec |
|---|---|
| Height and padding | at least 76 dp; 16 dp start and end; 12 dp vertical |
| Leading | Per chat: a 52 dp `bgSubtleSecondary` circle with a 24 dp status icon: `Circle` (`iconSecondary`), `Time` (`iconAccentPrimary`) or `CheckCircleSolid` (`iconAccentPrimary`). Across chats: the source chat's avatar (§4c). |
| Line 1 | The title in `fontBodyLgMedium`, one line; an `EventId`-fallback title shows `common_matcha_thread_untitled` in `textSecondary`. The deadline trails, in `fontBodySmRegular` with `DateFormatterMode.Upcoming` (§5): `textSecondary` normally, `textCriticalPrimary` with `screen_matcha_tasks_overdue` when it has passed and the task isn't done. |
| Line 2 | The first line of the description in `textSecondary`; else `common_replies`, or `screen_matcha_tasks_no_replies`. A trailing 16 dp `NotificationsSolid` in `iconSecondary` appears once a reminder is requested. |
| Tap | Opens the task's thread. A legacy standalone card opens the editor. |
| Long-press | the task actions sheet |

The **task actions sheet** has:
- `SheetHeader` with the task name, and `screen_matcha_tasks_in_chat` under it (across chats only);
- a radio group headed `screen_matcha_tasks_move_to`: To do, In progress, Done. Tapping one moves the task and closes the sheet;
- rows: Open thread, Remind me, Edit task, Archive or Unarchive.

Without `canWriteCards`, only Open thread and Remind me appear.

**Wide layout** (board width 600 dp or more: tablets; not the Fold)
- Three columns:
  - 16 dp outer padding and 8 dp gaps;
  - each a `bgSubtleSecondary` surface with a 16 dp radius;
  - each at least 240 dp wide, scrolling horizontally when they don't fit.
- **Column header:** 48 dp, the label and count in `fontBodyMdMedium` / `textSecondary`.
- **Cards:** `bgCanvasDefault`, 12 dp radius, 12 dp padding, 8 dp apart.
  - The title is two lines.
  - The meta line holds a 14 dp `Calendar` and the deadline, `NotificationsSolid`, and `Threads` with the count.
- The Archived row spans the top. Tap and long-press behave as on rows. No drag and drop.

**Board states**

| State | UI |
|---|---|
| Looking for the board (`board == null`, and not yet `isLoaded && boardPointerRead`) | 3 skeleton rows and the centred caption `screen_matcha_tasks_looking` ("Looking for this chat's task board…"). **No Create button:** the pointer isn't proven absent yet, and creating now could replace the web board's pointer and orphan its cards. |
| No board, user can create (`canCreateBoard`) | `BigIcon(CheckCircle)`, `screen_matcha_tasks_setup_title`, the body `screen_matcha_tasks_setup_body` ("Every thread in this chat becomes a task, and each new task gets its own thread."), and the green pill `screen_matcha_tasks_setup_action`. No FAB. |
| Creating | The pill shows a ring and ignores taps. **Right before sending, re-read the pointer** (`getStateEventsJson`). |
| A board appears during create | Don't send. Show the snackbar `screen_matcha_tasks_already_has_board` and show the board. |
| Create failed | `screen_matcha_tasks_setup_failed` above the pill |
| No board, user can't create | the same layout with `screen_matcha_tasks_setup_no_permission`, and no button |
| Board, no tasks | `screen_matcha_tasks_board_empty_title` / `…_body`, centred in the tab |
| Loading after the board is known | 3 skeleton rows |
| Store failed to open (`loadFailed`) | `BigIcon(Warning)`, `common_matcha_write_failed`, pill `action_retry` |
| `unreadable` | A strip under the tabs (`noticePillBackground`): `screen_matcha_tasks_unreadable` |
| Read-only (`!canWriteCards`) | the strip `screen_matcha_tasks_read_only`; no FAB |
| Write failed | snackbar `common_matcha_write_failed` with Retry |
| Write refused: the board pointer changed | snackbar `screen_matcha_tasks_board_changed` with Retry; Retry re-reads the board and writes again |
| Write refused: the thread already has a task | the editor error `screen_matcha_tasks_thread_has_task` |
| Offline | the same as Write failed |

**What happens when a board is created.** Every existing thread, including unnamed ones, lands in To do (plan §2.2 projection). The setup body says so before anyone creates it. Unnamed threads show their root text, or "Untitled thread" in grey, so they read as untidy rather than broken.

**Task editor:** one **full-height bottom sheet** everywhere: the board's FAB, Edit task, View task, the attach sheet's Task tile, and the Tasks tab.
- `ModalBottomSheet(skipPartiallyExpanded = true)`, with `imePadding` on its content.
- **Header:** `SheetHeader` with ✕ and `screen_matcha_tasks_new` or `screen_matcha_tasks_edit`.
- **Fields**, scrolling:
  1. **Task name:** borderless, `fontHeadingMdRegular`, hint `screen_matcha_tasks_name_hint`, 1–2 lines, with a counter from 80/100.
     - Edit mode adds the helper `screen_matcha_tasks_thread_note_linked`.
     - When the task is linked to a thread and the user has `canWriteCards` but not `canManageThreads`, the name is read-only, with the helper `screen_matcha_tasks_rename_not_allowed`.
  2. **Description:** borderless, multi-line, `fontBodyLgRegular`, hint `screen_matcha_tasks_description_hint`, with a counter from 3,800/4,000.
  3. A 1 dp divider, then icon rows: a 24 dp icon at 16 dp, the text at 72 dp, 56 dp rows.
     - `CheckCircle` **Status:** three inline `PillFilterChip`s (`RadioButton` role). The default is the tab the FAB was pressed on; To do anywhere else.
     - `Calendar` **Deadline:** `screen_matcha_tasks_deadline_add` in `textActionAccent`. Tapping opens the Material 3 date picker, then the time picker (12 or 24 h per the locale). Once set it shows `DateFormatterMode.Upcoming` text and a trailing ✕ (`a11y_matcha_clear_deadline`).
     - `Notifications` **Reminder** (edit mode, when §4d's gating passes): "Remind me", which opens §4d with the title, `threadId`, `cardId` and `dueAt`. Afterwards it shows `screen_matcha_tasks_reminder_requested`.
     - `Threads` **Thread:** in new mode, the footnote `screen_matcha_tasks_thread_note_new`; in edit mode, `screen_matcha_tasks_open_thread` in `textActionAccent`.
  4. The `show_to_room` switch isn't shown until plan §7.3 is decided.
  5. A collapsed row `screen_matcha_tasks_earlier_cards` ("N earlier cards for this thread"), only when they exist.
- **Footer:** a full-width green pill above the keyboard: `screen_matcha_tasks_add` ("Add task") in new mode, `action_save` in edit mode. It shows a ring while busy.
- **Validation** runs on save. Errors are red supporting text, and focus moves to the first error.

  | Condition | Message |
  |---|---|
  | Empty name | `common_matcha_error_name_empty` |
  | Name too long (100 when linked to a thread, 200 for a legacy card) | `common_matcha_error_max_chars` |
  | Description too long | `common_matcha_error_max_chars` (4,000) |
  | Deadline in the past | `screen_matcha_tasks_deadline_past` (a warning, not blocking) |
  | Deadline out of range | `screen_matcha_tasks_deadline_range` |

- **Closing with changes:** a dialog `screen_matcha_tasks_discard_title`, with `action_matcha_keep_editing` and `action_discard` (red).

**Archived tasks** (an internal screen of `TasksNode`)
- A top bar: back, `screen_matcha_tasks_archived_title`, and the chat name as the subtitle.
- The same rows. A long-press offers Unarchive, Open thread and Edit task.
- Archive and Unarchive show `screen_matcha_tasks_archived_snackbar` / `…_unarchived_snackbar` with Undo.
- Empty: `screen_matcha_tasks_archived_empty`.

### 4c. Tasks bottom tab (`MX/taskstab/`, cross-chat)

> Round 2: only your tasks, People and Chat filters, the table, and the homeserver board lookup; see §6.3, items 5 and 6.

**Reference.** WhatsApp's Calls and Updates tabs: a list of rows that each name their source, and a FAB that starts by choosing a recipient.

- **Top bar:** Home's own bar with the title `common_matcha_tasks`, the account avatar and Home's `⋮` (§3.1). There is no Tasks-specific menu.
- **Chips** (they scroll away with the list):
  - **All · To do · In progress · Done · Archived**, then a trailing **Chat ▾**.
  - All five are `RadioButton` chips in a `selectableGroup`.
  - Chat ▾ is a `Button` that opens a sheet of the chats that have boards (search pill on top, chat rows). While a chat is chosen, Chat ▾ shows its name in the selected style.
  - All is the default.
- **Grouping** (All, To do, In progress): section headers (`fontBodyMdMedium` `textSecondary`, 16 dp start, 40 dp tall): `screen_matcha_tasks_overdue`, `…_section_today`, `…_section_tomorrow`, `…_section_upcoming`, `…_section_no_deadline`. Empty sections are hidden.
  - In All, done tasks sit in a final collapsed row `screen_matcha_tasks_done_section` ("Done · N ›") that expands in place.
  - The Done chip lists done tasks, newest first; Archived lists archived tasks.
- **Row:** the §4b task row, with these differences:
  - the leading element is the **source chat's avatar** at 52 dp, with an 18 dp status badge at the bottom end (the status icon on a `bgCanvasDefault` ring);
  - line 2 is "Chat name · description".
- **Tap:** `onOpenThread(roomId, threadId)` opens the chat with the thread (in the end pane in two-pane), or `onOpenRoom` for a legacy card. **Long-press:** the task actions sheet plus "Open chat" (`screen_matcha_tasks_open_chat`).
- **FAB "New task"**, drawn by the renderer: it opens `screen_matcha_tasks_pick_chat_title`, a searchable sheet of chats where the user can add tasks (board, `canWriteCards`, `canManageThreads`, `canSendMessages`).
  - Picking a chat opens the §4b editor sheet for that chat, using the tab's store for that room.
  - If no chat qualifies, the sheet shows `screen_matcha_tasks_pick_chat_empty`.
- **Editing** a task from a chat that isn't open uses the same sheets and the tab's per-room store. No navigation is needed.
- **States:**

  | State | UI |
  |---|---|
  | First rooms loading | skeleton rows |
  | More rooms still being scanned | the footer `screen_matcha_tasks_checking_chats` in `textSecondary` |
  | Some rooms failed (`loadFailed`) | the footer `screen_matcha_tasks_check_failed` ("Couldn't check N chats") with a `textActionAccent` Retry |
  | Some rooms `unreadable` | the footer note `screen_matcha_tasks_tab_unreadable`; their rows still show |
  | Any room `hasMoreHistory` | the footer row "Load older tasks" (`screen_matcha_tasks_load_older`) |
  | Empty | `BigIcon(CheckCircle)`, `screen_matcha_tasks_tab_empty_title` / `…_body` |
  | Write failed, offline | §3.8 |

- No badge on the tab in v1.

### 4d. Reminders (`MX/reminders/`)

**Reference.** WhatsApp's "Remind me" (a beta borrowing, §7.1): a sheet with ✕, a centred title, one privacy line, then plain option rows.

**Deliberate differences:**
- WhatsApp's reminders are private and local. Ours are visible bot posts, so the sheet says so.
- **Nothing is sent on a single tap.** Choosing an option only fills in the confirm pill, and the pill posts. A misplaced tap would otherwise post `!remind` to everyone, with no way to take it back.

**Gating, the same for every entry point.** An entry point shows only when all of these hold:
- the Matcha flag is on;
- the room is joined;
- `canSendMessages`;
- the bot id is configured (`INTEGRATION_BRIEF.md` §1 helper).

In a **DM**, entry points also need the bot to already be joined. Inviting it would turn the DM into a group: it leaves the People filter and gets group rendering. This is §6.2, decision 5.

**Entry points**
1. Message long-press → **Remind me** (`TimelineItemAction.RemindMe`, §3.3; also requires `isRemote`). `MatchaReminderTarget(message = first 200 characters of the body, eventId, threadId when in thread mode)`.
2. Chip bar More, or chat `⋮` → **Remind me**. In thread mode it attaches `threadId` = the open thread.
3. Thread chip long-press → **Remind me**, with `threadId`.
4. The task actions sheet and the editor → **Remind me**, with the title, `threadId`, `cardId` and `dueAt`.

**Step 0, checking.** While `room.getUpdatedMember(bot)` runs, a centred 20 dp ring and `screen_matcha_reminder_bot_checking` appear. They show after 300 ms, to avoid a flash.

**Step 1** (`ModalBottomSheet`, `SheetHeader` with ✕ and `action_matcha_remind_me`)
- **Helper** (centred, `textSecondary`): `screen_matcha_reminder_helper`.
- **Message field:** outlined, 8 dp radius, at least 56 dp tall, hint `screen_matcha_reminder_message_hint`, prefilled from the target.
  - The footnote `screen_matcha_reminder_link_message` or `…_link_thread` explains that the permalink is appended once when sending; it is never shown in the field.
- **Option rows:** a radio group of 56 dp rows, each showing its resolved time at the end in `textSecondary` (`DateFormatterMode.Upcoming`):
  - In 2 hours, In 8 hours, In 24 hours (`screen_matcha_reminder_in_hours`; the bot's `in` mode, `!remind 2h …`);
  - At the deadline (`screen_matcha_reminder_at_deadline`): task context only, when the deadline is in the future;
  - Pick date and time › (`screen_matcha_reminder_custom`), which opens step 2.

  Nothing is preselected.
- **Footer:** a full-width green pill `screen_matcha_reminder_confirm` ("Set reminder · Today 14:05"). It is disabled until an option is chosen.
  - Tapping it validates (§2.3 rules, re-checked at send time), then shows a ring.
  - On success the sheet closes, with the snackbar `screen_matcha_reminder_requested`.

**Step 2, custom** (← in place of ✕, title `screen_matcha_reminder_custom`)
- **Date** row: tap opens `DatePickerDialog`. **Time** row: tap opens the `TimePicker` dialog. The default is tomorrow at 09:00 (approx.).
- The footnote `screen_matcha_reminder_timezone` shows the device's zone id and offset.
- The footer pill is `screen_matcha_reminder_confirm` with the chosen time, using the bot's `at` mode (UTC ISO).
- Inline errors appear above the pill:

  | Condition | Message |
  |---|---|
  | Not after now | `screen_matcha_reminder_error_past` |
  | More than 365 days ahead | `screen_matcha_reminder_error_too_far` |
  | DST gap | `screen_matcha_reminder_error_dst` |
  | Empty message | `screen_matcha_reminder_error_empty` |
  | Over 2,000 characters | `common_matcha_error_max_chars` |
  | The thread root fails validation (plan §2.3) | `screen_matcha_reminder_error_thread`; the pill is disabled |

**Bot states** (plan §2.3), shown in place of the options:

| Bot membership | UI |
|---|---|
| Being checked | step 0 |
| Joined | step 1 |
| Invited | A ring with `screen_matcha_reminder_bot_waiting` and a `textActionAccent` **Retry**. The sheet watches the room's members and **moves on to step 1 by itself** when the bot joins. Retry re-reads the membership with `getUpdatedMember`. |
| Not in the chat or left, user can invite | The step `screen_matcha_reminder_bot_invite_title`, with the body `screen_matcha_reminder_bot_invite_body` (the bot's id). Pill `screen_matcha_reminder_bot_invite_action`; `action_cancel`. After the invite: Invited. |
| Invite failed | `screen_matcha_reminder_bot_invite_failed` above the pill; the pill retries the invite |
| Not in the chat, user can't invite | `screen_matcha_reminder_bot_no_permission`; no options |
| Banned | `screen_matcha_reminder_bot_banned`; no options |
| Send failed | `screen_matcha_reminder_error_send` above the pill; the options stay selectable |

**Pending and confirmed** (mandatory in v1)
- The UI only ever says "requested". On a task it is `screen_matcha_tasks_reminder_requested` plus the bell on the row.
- Your own `!remind …` message renders as a system pill (`systemPillBackground`): `screen_matcha_reminder_pill_requested`.
- The bot's "Scheduled r-… for …" notice (`ReminderProtocol.parseAcknowledgement`) renders as a pill: `screen_matcha_reminder_pill_confirmed`.
- Either falls back to the raw text when parsing fails.
- The delivered "Reminder for @you: …" stays a normal incoming bubble with mention highlighting.
- **Later:** a 12 dp `NotificationsSolid` in the time row of messages your reminders point at.

### 4e. Space Docs (`MX/docs/`, phase vii)

> Round 2: called **Notes** in the UI, with a Notes tab on Home; see §6.3, item 9.

**Reference.**
- The Docs row copies the stable "Media, links and docs" row of group info.
- The editor's status subtitle copies the chat top bar's subtitle line.
- If David picks the read-only v1 (plan §7.2), ship the same screens without the Edit FAB, with the subtitle "View only".

**Docs row in the space screen** (`SP/root/SpaceView.kt`, between `:281` and `:284`)
- One 72 dp row: a 48 dp rounded-square tile (12 dp radius, `bgSubtleSecondary`) with `Document` in `iconSecondary`, the title `common_matcha_docs`, and the subtitle `common_matcha_docs_row_subtitle`. It opens `NavTarget.Docs()`.
- Preview rows inside the space screen would need a section renderer; they are later, not v1.
- The space screen's `⋮` (`:445-510`) gets a **Docs** item (`common_matcha_docs`).
- Every setup state is shown by the Docs node itself, so the space module never reads the catalog.

**Docs list** (`NavTarget.Docs`)
- Top bar: back, `common_matcha_docs`, and the space name as the subtitle.
- **Rows:**
  - Space notes (`main`, `screen_matcha_docs_main_name`) is pinned first, then the other docs by most recent edit.
  - Each is a 72 dp row with a doc tile and "Edited …" (`screen_matcha_docs_edited`, with `DateFormatterMode.TimeOrDate`).
  - A long-press offers **Rename**. There is no delete; the protocol has none.
- **FAB:** New doc (`screen_matcha_docs_new`), shown when the user can create docs, that is `docs.update` permission ∧ the space is encrypted (plan §2.4).
- **States:**

  | State | UI |
  |---|---|
  | Loading | skeleton rows |
  | Only Space notes exists | the row plus the line `screen_matcha_docs_list_empty` |
  | Catalog `load_error` | `BigIcon(Warning)`, `screen_matcha_docs_list_load_error`, pill `action_retry` |
  | Catalog `decrypt_error` | strip `screen_matcha_docs_list_decrypt_error`; rows show their ids' fallback "Space notes" or the last known name |
  | Offline | the global strip; New and Rename fail per §3.8 |
  | Not set up, admin | `BigIcon(Document)`, `screen_matcha_docs_tip`, green pill `screen_matcha_docs_tip_action`, which opens setup |
  | Not set up, member | `BigIcon(Document)`, `screen_matcha_docs_not_enabled` |

  "Not set up" also covers opening the list from a chat whose space has no docs.

**New doc and Rename sheet**
- `SheetHeader` with ✕ and `screen_matcha_docs_new` / `screen_matcha_docs_rename_title`.
- An outlined field `screen_matcha_docs_name_hint`, and the pill `action_create` / `action_save`.
- Errors: `common_matcha_error_name_empty`, `common_matcha_error_max_chars` (100), or a save failure, `common_matcha_write_failed`, shown inline with the sheet kept open.

**Editor** (full screen; end pane on the Fold; a WebView engine themed through the bridge)
- **Theme bridge:** background `bgCanvasDefault`, text `textPrimary`, Roboto 16/24, links `textLinkExternal`, 16 dp gutters, code in `bgSubtleSecondary`.
- **Top bar:** back, the doc name (`aliasChatTitle`), and a **status subtitle** (`fontBodyMdRegular`):

  | Status | Subtitle |
  |---|---|
  | Loading | `common_loading` |
  | Saving | `common_saving` |
  | Saved (only after the server acknowledges) | `screen_matcha_docs_status_saved` |
  | Offline with unsent edits | `screen_matcha_docs_status_offline` |
  | Send failed | `screen_matcha_docs_status_failed` in `textCriticalPrimary`; tapping it retries |
  | View only | `screen_matcha_docs_status_view_only` |
  | Earlier chat doc | `screen_matcha_docs_status_earlier` |

- **Actions:** `⋮` with Rename and `screen_matcha_docs_share_text` ("Share as text", which shares the Markdown).
- **Modes:**
  - It opens in **preview**. A green FAB with `Edit` (`a11y_matcha_doc_edit`) enters edit mode.
  - In edit mode, a 40 dp `bgAccentRest` circle with `Check` (`action_done`) is the last top-bar action.
- **Formatting row** (edit mode, docked above the keyboard):
  - 48 dp, `bgCanvasDefault`, a 1 dp `separatorPrimary` top edge, scrolling horizontally.
  - 48 dp buttons in `iconSecondary`: H (`a11y_matcha_format_heading`), then the `rich_text_editor_*` buttons (Bold, Italic, Strikethrough, bulleted, numbered, `rich_text_editor_quote`, inline code, code block, link), and checklist (`a11y_matcha_format_checklist`).
  - The active state is tinted `bgAccentSubtle`.
- **Strips** under the top bar (`noticePillBackground`, with a `Warning` or `Offline` icon):
  - offline: `screen_matcha_docs_offline_strip`;
  - `decrypt_error`: `screen_matcha_docs_decrypt_error`;
  - `incomplete_update`: `screen_matcha_docs_incomplete`;
  - `invalid_update`: `screen_matcha_docs_invalid_update`.

  Editing is off while any of the last three applies.
- **Full-page error** (`load_error`): `screen_matcha_docs_load_error_title` / `…_body`, with the pill `action_retry`.
- **Empty doc in preview:** `screen_matcha_docs_doc_empty`, or `…_doc_empty_read_only` when view-only.
- **Leaving with unsent edits:** a dialog `screen_matcha_docs_unsaved_title` / `…_body`, with the buttons `action_leave` (red), `screen_matcha_docs_share_text` and `screen_matcha_docs_stay`.

**Setup screen** (admins, one time)
- Top bar: ✕ and `screen_matcha_docs_setup_title`.
- Content: `BigIcon(Document)`, then three icon rows (a 24 dp icon, the title in `fontBodyLgMedium`, the body in `textSecondary`):
  - `Group`: `…_setup_together_title` / `…_body`;
  - `Lock`: `…_setup_encrypted_title` / `…_body`;
  - `Room`: `…_setup_rooms_title` / `…_body`.
- The green pill `screen_matcha_docs_setup_action`:
  - If encryption is off, it first shows the dialog `screen_matcha_docs_encrypt_dialog_title` / `…_body`, with `action_cancel` and `…_encrypt_dialog_action`.
  - While working: a ring and `screen_matcha_docs_setup_progress`.
  - On error: `screen_matcha_docs_setup_error` above the pill.
  - On success: the Docs list opens.

**From a chat:** More (or `⋮`) → Space docs.
- With one joined parent space, its list opens.
- With several, a "Choose a space" sheet appears (`common_matcha_choose_space`, squircle rows).
- With none, the item is hidden.
- More → Earlier chat doc opens that chat's own `main` doc, view-only.

---

## 5. Copy

**Where strings go.**
- New English strings go only in `temporary.xml`, never in `localazy.xml`.
- **A string used by more than one module goes in `libraries/ui-strings/src/main/res/values/temporary.xml`** as `common_matcha_*`, `action_matcha_*` or `a11y_matcha_*`.
- A string used by one module goes in that module's `temporary.xml`:
  - `features/matcha/impl/...` (new);
  - `features/messages/impl/...` (new);
  - `features/home/impl/...` (exists).
- Every new key contains `matcha`, so a future upstream import can't collide with it. Placeholders are numbered.

**Voice.** Short, sentence case, no exclamation marks, second person, and the outcome on the button.

**Reused as they are; never add a duplicate:**
- Actions: `action_search`, `action_save`, `action_cancel`, `action_create`, `action_done`, `action_edit`, `action_retry`, `action_leave`, `action_share`, `action_send`, `action_discard`, `action_close`, `action_load_more`, `action_start_chat`, `action_create_room`, `action_create_space`, `action_open_settings`.
- Common: `common_loading`, `common_saving`, `common_offline`, `common_sending`, `common_sent`, `common_seen_by`, `common_thread`, `common_threads`, `common_replies` (plural), `common_message_removed`, `common_waiting_for_decryption_key`, `common_space`, `common_spaces`, `common_room`, `common_rooms` (as a section header only), `common_settings`.
- Empty pane: `common_no_chat_selected` is the **title** ("No chat selected") and `common_no_room_selected` the **body** ("Select a chat to start messaging").
- Home tabs: `screen_home_tab_chats`, `screen_home_tab_spaces`.
- Accessibility: `a11y_jump_to_bottom`, `a11y_voice_message_record`.
- Composer and formatting: `rich_text_editor_a11y_add_attachment`, `rich_text_editor_quote` and the other `rich_text_editor_*` formatting keys.
- Attach sheet: `screen_room_attachment_source_gallery`, `_camera_photo`, `_camera_video`, `_files`, `_location`, `_poll`, `_text_formatting`.
- Typing: `screen_room_typing_notification`, `screen_room_typing_two_members`, `screen_room_typing_many_members`.
- Chat banners: `screen_room_timeline_no_permission_to_post`, `screen_room_timeline_tombstoned_room_message`, `screen_room_timeline_tombstoned_room_action`.

**Dates and times.** No date or time strings are hard-coded.
- ⏳ Add `DateFormatterMode.Upcoming` to `libraries/dateformatter/api/.../DateFormatter.kt` (with its implementation and tests). Its output:
  - "Today 14:05";
  - "Tomorrow 09:00";
  - a weekday plus the time within 6 days ("Tue 17:00");
  - otherwise "3 Nov, 17:00", with the year when it differs.

  It takes the relative day words from the platform, like `DefaultDateFormatterDay`.
- The existing `Day` mode treats every future date as a weekday, so it can't be used for these.
- Uses: deadlines, reminder option times, the confirm pill, "Reminder requested for …", and the Tasks-tab sections. Past times keep the chat list's mode.

### Shared (`libraries/ui-strings/.../temporary.xml`)

| Key | Text |
|---|---|
| `common_matcha_all` | All |
| `common_matcha_tasks` | Tasks |
| `common_matcha_more` | More |
| `common_matcha_archived` | Archived |
| `common_matcha_active` | Active |
| `common_matcha_all_threads` | All threads |
| `common_matcha_new_thread` | New thread |
| `common_matcha_space_docs` | Space docs |
| `common_matcha_docs` | Docs |
| `common_matcha_docs_row_subtitle` | Notes and plans for this space |
| `common_matcha_choose_space` | Choose a space |
| `common_matcha_thread_untitled` | Untitled thread |
| `common_matcha_member_count` (plural) | %1$d member / %1$d members |
| `common_matcha_room_count` (plural) | %1$d room / %1$d rooms |
| `common_matcha_space_rooms` (plural) | Space · %1$d room / Space · %1$d rooms |
| `common_matcha_write_failed` | Couldn't save. Check your connection and try again. |
| `common_matcha_error_name_empty` | Enter a name |
| `common_matcha_error_max_chars` | Use up to %1$s characters |
| `common_matcha_no_task_selected` | No task selected |
| `common_matcha_select_task` | Select a task to open its thread |
| `common_matcha_no_space_selected` | No space selected |
| `common_matcha_select_space` | Select a space to see its rooms |
| `common_matcha_notification_thread_title` | %1$s · %2$s |
| `action_matcha_remind_me` | Remind me |
| `action_matcha_archive` | Archive |
| `action_matcha_unarchive` | Unarchive |
| `action_matcha_rename` | Rename |
| `action_matcha_undo` | Undo |
| `action_matcha_keep_editing` | Keep editing |

### Home (`features/home/impl/.../temporary.xml`)

| Key | Text |
|---|---|
| `screen_matcha_home_product_name` (`translatable="false"`) | Matcha (placeholder; §6.2, decision 1) |
| `screen_matcha_home_view_all_chats` | View all chats |
| `a11y_matcha_unread_messages` (plural) | %1$d unread message / %1$d unread messages |
| `a11y_matcha_mentioned` | You were mentioned |

### Chat screen and thread list (`features/messages/impl/.../temporary.xml`)

| Key | Text |
|---|---|
| `screen_matcha_room_typing_dm` | typing… |
| `screen_matcha_room_unread_band` (plural) | %1$d unread message / %1$d unread messages |
| `screen_matcha_room_attach_task` | Task |
| `screen_matcha_threads_list_empty_title` | No threads yet |
| `screen_matcha_threads_list_empty_body` | Start one with New thread. |
| `screen_matcha_threads_list_archived_empty` | No archived threads |
| `screen_matcha_threads_list_load_older` | Load older threads |
| `screen_matcha_threads_list_load_error` | Couldn't load threads |
| `a11y_matcha_emoji` | Emoji |
| `a11y_matcha_message_read` | Read |
| `a11y_matcha_new_messages` | New messages |

### Chip bar and threads (`features/matcha/impl/.../temporary.xml`)

| Key | Text |
|---|---|
| `screen_matcha_threads_general` | General |
| `screen_matcha_threads_earlier_doc` | Earlier chat doc |
| `screen_matcha_threads_name_hint` | Thread name |
| `screen_matcha_threads_name_helper` | Everyone in this chat can see the name. |
| `screen_matcha_threads_create` | Create thread |
| `screen_matcha_threads_rename_title` | Rename thread |
| `screen_matcha_threads_create_failed` | Couldn't create the thread. Check your connection and try again. |
| `screen_matcha_threads_setup_failed` | Message sent, but the thread couldn't be set up. Try again. |
| `screen_matcha_threads_archived_snackbar` | Thread archived |
| `screen_matcha_threads_unarchived_snackbar` | Thread unarchived |
| `screen_matcha_threads_view_task` | View task |
| `screen_matcha_threads_warning_undecryptable` | Some thread names can't be read on this device yet. |
| `screen_matcha_threads_warning_history` | Older thread names aren't available on this device. |
| `screen_matcha_threads_warning_access` | You can't see thread names from before you joined. |
| `screen_matcha_threads_warning_network` | Thread names may be out of date. Check your connection. |
| `a11y_matcha_thread_archived` | Archived thread |
| `a11y_matcha_open_tasks` (plural) | %1$d open task / %1$d open tasks |

### Tasks (`features/matcha/impl/.../temporary.xml`)

| Key | Text |
|---|---|
| `screen_matcha_tasks_status_todo` / `_doing` / `_done` | To do / In progress / Done |
| `screen_matcha_tasks_new` | New task |
| `screen_matcha_tasks_edit` | Edit task |
| `screen_matcha_tasks_add` | Add task |
| `screen_matcha_tasks_name_hint` | Task name |
| `screen_matcha_tasks_description_hint` | Add a description |
| `screen_matcha_tasks_status_label` | Status |
| `screen_matcha_tasks_deadline_label` | Deadline |
| `screen_matcha_tasks_deadline_add` | Add deadline |
| `screen_matcha_tasks_deadline_past` | This time has already passed |
| `screen_matcha_tasks_deadline_range` | Pick a date before the year 10,000 |
| `screen_matcha_tasks_reminder_label` | Reminder |
| `screen_matcha_tasks_reminder_requested` | Reminder requested for %1$s |
| `screen_matcha_tasks_thread_note_new` | A thread with this name will be created in this chat. |
| `screen_matcha_tasks_thread_note_linked` | Renaming the task also renames its thread. |
| `screen_matcha_tasks_rename_not_allowed` | You can't rename threads in this chat, so this name can't change. |
| `screen_matcha_tasks_open_thread` | Open thread |
| `screen_matcha_tasks_open_chat` | Open chat |
| `screen_matcha_tasks_move_to` | Move to |
| `screen_matcha_tasks_in_chat` | In %1$s |
| `screen_matcha_tasks_overdue` | Overdue |
| `screen_matcha_tasks_no_replies` | No replies yet |
| `screen_matcha_tasks_archived_title` | Archived tasks |
| `screen_matcha_tasks_archived_empty` | No archived tasks |
| `screen_matcha_tasks_archived_snackbar` | Task archived |
| `screen_matcha_tasks_unarchived_snackbar` | Task unarchived |
| `screen_matcha_tasks_load_older` | Load older tasks |
| `screen_matcha_tasks_earlier_cards` (plural) | %1$d earlier card for this thread / %1$d earlier cards for this thread |
| `screen_matcha_tasks_looking` | Looking for this chat's task board… |
| `screen_matcha_tasks_setup_title` | Track work in this chat |
| `screen_matcha_tasks_setup_body` | Every thread in this chat becomes a task, and each new task gets its own thread. |
| `screen_matcha_tasks_setup_action` | Create task board |
| `screen_matcha_tasks_setup_failed` | Couldn't create the task board. Try again. |
| `screen_matcha_tasks_setup_no_permission` | Ask an admin to create the task board. |
| `screen_matcha_tasks_already_has_board` | This chat already has a task board. |
| `screen_matcha_tasks_board_changed` | The task board changed. Try again. |
| `screen_matcha_tasks_thread_has_task` | This thread already has a task. |
| `screen_matcha_tasks_board_empty_title` | No tasks yet |
| `screen_matcha_tasks_board_empty_body` | Tap + to add the first one. |
| `screen_matcha_tasks_read_only` | You can see these tasks but can't change them. |
| `screen_matcha_tasks_unreadable` | Some task updates can't be decrypted. This list may be out of date. |
| `screen_matcha_tasks_discard_title` | Discard changes? |
| `screen_matcha_tasks_section_today` / `_tomorrow` / `_upcoming` / `_no_deadline` | Today / Tomorrow / Upcoming / No deadline |
| `screen_matcha_tasks_done_section` (plural) | Done · %1$d |
| `screen_matcha_tasks_filter_chat` | Chat |
| `screen_matcha_tasks_tab_empty_title` | No tasks yet |
| `screen_matcha_tasks_tab_empty_body` | Tasks from your chats show up here. |
| `screen_matcha_tasks_tab_unreadable` | Some tasks can't be decrypted on this device yet. |
| `screen_matcha_tasks_pick_chat_title` | Choose a chat |
| `screen_matcha_tasks_pick_chat_empty` | None of your chats has a task board yet. |
| `screen_matcha_tasks_checking_chats` (plural) | Checking %1$d more chat… / Checking %1$d more chats… |
| `screen_matcha_tasks_check_failed` (plural) | Couldn't check %1$d chat / Couldn't check %1$d chats |
| `a11y_matcha_task_status` | Status: %1$s |
| `a11y_matcha_task_due` | Due %1$s |
| `a11y_matcha_task_reminder` | Reminder requested |
| `a11y_matcha_clear_deadline` | Clear deadline |

### Reminders (`features/matcha/impl/.../temporary.xml`)

| Key | Text |
|---|---|
| `screen_matcha_reminder_helper` | The reminder bot posts it in this chat, so everyone here can see it. |
| `screen_matcha_reminder_message_hint` | What to remind you about |
| `screen_matcha_reminder_link_message` | A link to the message is added. |
| `screen_matcha_reminder_link_thread` | A link to the thread is added. |
| `screen_matcha_reminder_in_hours` (plural) | In %1$d hour / In %1$d hours |
| `screen_matcha_reminder_at_deadline` | At the deadline |
| `screen_matcha_reminder_custom` | Pick date and time |
| `screen_matcha_reminder_date` | Date |
| `screen_matcha_reminder_time` | Time |
| `screen_matcha_reminder_timezone` | Time zone: %1$s |
| `screen_matcha_reminder_confirm` | Set reminder · %1$s |
| `screen_matcha_reminder_requested` | Reminder requested |
| `screen_matcha_reminder_error_empty` | Add a message |
| `screen_matcha_reminder_error_past` | Pick a time in the future |
| `screen_matcha_reminder_error_too_far` | Pick a time within the next year |
| `screen_matcha_reminder_error_dst` | That time doesn't exist in your time zone |
| `screen_matcha_reminder_error_send` | Couldn't request the reminder. Check your connection and try again. |
| `screen_matcha_reminder_error_thread` | This thread can't be found. |
| `screen_matcha_reminder_bot_checking` | Checking the reminder bot… |
| `screen_matcha_reminder_bot_invite_title` | Add the reminder bot? |
| `screen_matcha_reminder_bot_invite_body` | %1$s sends reminders. It joins this chat and can read messages sent after it joins. |
| `screen_matcha_reminder_bot_invite_action` | Add bot |
| `screen_matcha_reminder_bot_invite_failed` | Couldn't add the reminder bot. Try again. |
| `screen_matcha_reminder_bot_waiting` | Waiting for the reminder bot to join… |
| `screen_matcha_reminder_bot_no_permission` | Ask an admin to add the reminder bot to this chat. |
| `screen_matcha_reminder_bot_banned` | The reminder bot is banned from this chat. Ask an admin to unban it. |
| `screen_matcha_reminder_pill_requested` | You requested a reminder for %1$s |
| `screen_matcha_reminder_pill_confirmed` | Reminder set for %1$s |

### Docs (`features/matcha/impl/.../temporary.xml`)

| Key | Text |
|---|---|
| `screen_matcha_docs_main_name` | Space notes |
| `screen_matcha_docs_new` | New doc |
| `screen_matcha_docs_rename_title` | Rename doc |
| `screen_matcha_docs_name_hint` | Doc name |
| `screen_matcha_docs_edited` | Edited %1$s |
| `screen_matcha_docs_list_empty` | Write notes, plans and guides together. |
| `screen_matcha_docs_list_load_error` | Couldn't load the docs |
| `screen_matcha_docs_list_decrypt_error` | Some doc names can't be read on this device yet. |
| `screen_matcha_docs_doc_empty` | This doc is empty. Tap the pencil to start writing. |
| `screen_matcha_docs_doc_empty_read_only` | This doc is empty. |
| `screen_matcha_docs_status_saved` | Saved |
| `screen_matcha_docs_status_offline` | Offline · not saved yet |
| `screen_matcha_docs_status_failed` | Couldn't save · tap to retry |
| `screen_matcha_docs_status_view_only` | View only |
| `screen_matcha_docs_status_earlier` | View only · from this chat |
| `screen_matcha_docs_share_text` | Share as text |
| `screen_matcha_docs_offline_strip` | You're offline. Keep this screen open and your edits will send when you reconnect. |
| `screen_matcha_docs_decrypt_error` | Some edits can't be decrypted on this device yet. Editing is off until they can be. |
| `screen_matcha_docs_incomplete` | Some edits haven't arrived yet. Editing is off until they do. |
| `screen_matcha_docs_invalid_update` | Some edits couldn't be read. Editing is off for this doc. |
| `screen_matcha_docs_load_error_title` | Couldn't load this doc |
| `screen_matcha_docs_load_error_body` | Check your connection and try again. |
| `screen_matcha_docs_unsaved_title` | Changes not saved |
| `screen_matcha_docs_unsaved_body` | Some edits haven't reached the server. If you leave now, they'll be lost. |
| `screen_matcha_docs_stay` | Stay |
| `screen_matcha_docs_tip` | Write notes and plans together in this space. |
| `screen_matcha_docs_tip_action` | Set up docs |
| `screen_matcha_docs_not_enabled` | Docs aren't on for this space yet. Ask an admin to turn them on. |
| `screen_matcha_docs_setup_title` | Docs for this space |
| `screen_matcha_docs_setup_together_title` / `_body` | Write together / Everyone in the space can read and edit docs. |
| `screen_matcha_docs_setup_encrypted_title` / `_body` | End-to-end encrypted / Turning on docs also turns on encryption for this space. You can't turn it off later. |
| `screen_matcha_docs_setup_rooms_title` / `_body` | Rooms stay the same / Room settings and encryption don't change. |
| `screen_matcha_docs_setup_action` | Turn on docs |
| `screen_matcha_docs_setup_progress` | Turning on docs… |
| `screen_matcha_docs_setup_error` | Couldn't turn on docs. Try again. |
| `screen_matcha_docs_encrypt_dialog_title` | Turn on encryption? |
| `screen_matcha_docs_encrypt_dialog_body` | Encryption for this space can't be turned off later. |
| `screen_matcha_docs_encrypt_dialog_action` | Turn on |
| `a11y_matcha_format_heading` | Heading |
| `a11y_matcha_format_checklist` | Checklist |
| `a11y_matcha_doc_edit` | Edit doc |

---

## 6. Implementation order and per-phase checklists

- Each row of §6.0 is one PR of at most 500 production lines; split it if it is larger. Each PR ships on its own.
- Every new or changed View gets `@PreviewsDayNight`, `ElementPreview` and a `*StatePreviewParam`, with new states appended at the end.
- Never record screenshots locally. A PR with preview changes gets the "Record-Screenshots" label, but that label does not work from a fork (`TWO_PANE_NOTES.md`), so ask David.

### 6.0 Mapping to the plan and the briefs

| Spec phase | Plan phase | Brief | PR(s) |
|---|---|---|---|
| — | Phase 0, plumbing | — | done: cec46793a2, 3b3330b2d7, 10b26ea442, 2bb52a3189 |
| (i) Palette and global components | — | Re-skin step 1 | done: 0c4c71bebd, 4883fab532, ae95163aeb, 64dc7e7163, 3ee240a6a1, 2d70d95db9, c72a83784c, ff6ea531cd, e89f8dee41, e99db1ce79, 8363bc94af. ⏳ One follow-up PR: "Finish the Matcha palette and shared components". |
| (ii) Home | Phase 1 (Favourites first) is folded in | Re-skin step 2 | 1. "Make the chat filters single-select WhatsApp chips". 2. "Move Home to a bottom navigation bar and an end FAB". 3. "Restyle chat rows, search and the Spaces tab". |
| (iii) Chat screen | — | Re-skin step 3 | 1. "Restyle bubbles, time and read ticks". 2. "Turn the composer into a WhatsApp-style pill". 3. "Restyle the long-press sheet, banners and chat states". 4. "Restyle room info and settings" (§3.6–3.7). |
| (iv) Chip bar and threads | Phase 2 | Feature track B step 1; integration §2 | "Add the chat chip bar and named threads" |
| (v) Board and Tasks tab | Phase 3 | Track B steps 2–3; integration §3 and §5 | "Add the chat task board", then "Add the Tasks tab" |
| (vi) Reminders | Phase 4 | Track C step 1; integration §1 and §4 | "Add reminders through the reminder bot" |
| (vii) Docs | Phase 5 (blocked on §6.2, decision 3) | Track C step 2; integration §6 | "Add Space docs" |
| (viii) Notifications and parity | Phase 6 | integration follow-up | "Name threads in notifications and check Matcha parity" |

### 6.1 Supersedes

Each item overrides the named plan or brief text. The same PR that lands this spec adds the one-line pointer "Superseded by WHATSAPP_DESIGN.md §6.1, item N" next to each item in the brief, so all tracks build the same thing.

1. **Archive confirmation.** Plan Phase 2 "archive (confirm)" and `FEATURE_TRACK_BRIEF.md:50-51` "Archive asks for confirmation first" become: no dialog, a snackbar with Undo. This is WhatsApp's archive pattern, and it matches the same brief's Track B step 1.
2. **Task editor.** Plan Phase 3 "editor sheet … thread picker" and `FEATURE_TRACK_BRIEF.md:98` "the full-screen editor" become one **full-height sheet** everywhere, with **no thread picker**. A new task always creates its thread; an existing task keeps its thread.
3. **Reminder sheet.** Plan §1.6 and Phase 4 "segmented in | at, number + unit", and any other list of quick options, become: In 2, 8 and 24 hours and At the deadline (`in` mode), and Pick date and time (`at` mode). There is no free duration entry.
4. **Reminder sending.** A quick option no longer sends on tap; the confirm pill sends. The bot's acknowledgement pills are mandatory (formerly "vi-b, optional").
5. **Packages.** Plan §4 `threads/`, `board/` and `PersonalBoard*` become the brief's `chatbar/`, `tasks/` and `taskstab/`.
6. **Tasks tab.** Plan §4 and §7.4 "Personal" with `CompoundIcons.Compose()` becomes **Tasks** with `CheckCircle`. `INTEGRATION_BRIEF.md:52` "between Chats and Spaces" plus the key `screen_home_tab_tasks` becomes: the enum entry is appended last, the bar order is set explicitly, and the label is `common_matcha_tasks`.
7. **Tab names.** Plan §1.2 "Board" and "Thread history" become **Tasks** and **All threads**. All threads is a page, so `MatchaChatBarSelection.AllThreads` is removed.
8. **Bar visibility.** Plan §1.2 "one row … in every joined room" becomes: the bar appears only when the room has a named thread or a board, and always on the board.
9. **Chip order.** Plan §1.2 "newest root first" becomes newest activity first, the store's order.
10. **API.** `FEATURE_TRACK_BRIEF.md:69-79` ("do not change") gains the minimal changes of §4.0: `AllThreads` removed; `Callback.openRoomDetails` added; `MatchaRoomController` and `MatchaThreadInfo` added; `HomeTab` and `HomeEntryPoint.Callback.onTabSelected` and `navigateToThread` added.
11. **Tasks-tab FAB.** `INTEGRATION_BRIEF.md:55-56`: the renderer draws the FAB, so Home's `Scaffold` has none on Tasks.
12. **Attach Task tile.** `INTEGRATION_BRIEF.md:38-39` "opens the board" becomes `MatchaRoomController.openNewTask()`, which opens the editor over the chat.
13. **Docs section.** `INTEGRATION_BRIEF.md:66-68` "Docs section … rows" becomes one Docs row in v1.
14. **Chips.** The v1 spec's outlined chips and the committed `PillFilterChip` border become filled grey, as in stable WhatsApp. This agrees with `RESKIN_TRACK_BRIEF.md:47`.
15. **Filter test name.** Plan Phase 1 "`DefaultFilterSelectionStrategyTest`" becomes `RoomListFiltersPresenterTest`.
16. **Two-pane widths.** The v1 spec's fixed 360 dp list pane becomes the existing 2 : 3 weights.
17. **Reminders in DMs** are offered only when the bot is already joined (§4d).

### 6.2 Open decisions for David

Decisions 2, 4 and 6 were settled by David on 2026-10-24; see §6.3.

| # | Decision | Default in this spec | Blocks |
|---|---|---|---|
| 1 | The product name in the Chats title | "Matcha", as a fixed string (`screen_matcha_home_product_name`). It is never "Element X dbg", and never Element's name in the wordmark position. | (ii) ship |
| 2 | Third tab: name and position (plan §7.4) | "Tasks", in the middle: Chats · Tasks · Spaces | (v) |
| 3 | Docs scope (plan §7.2): skip, read-only, or full | screens designed for full; read-only drops the Edit FAB | (vii) |
| 4 | Web WIP (plan §7.3): `show_to_room` and the people filter | Ignored: no editor switch and no person filter | (v) editor, Tasks-tab filters |
| 5 | Reminders in DMs | Off unless the bot is already joined | (vi) |
| 6 | "Spaces" or "Communities" | Spaces | none (string-only) |
| 7 | Black theme | Kept; the aliases derive from fields | none |
| 8 | The beta borrowings in §7.1 | Accepted | none |

### 6.3 Round 2: what David asked for on 2026-10-24

David reviewed the build on his Fold and asked for the changes below. They override the named sections; each of those sections carries a pointer here.

1. **Chat top bar** (§3.3 Actions, §4.0). With the Matcha workspaces on, the actions are **All threads** (`Threads`), **Tasks** (`CheckCircle`, with the open-task count badge) and **⋮**. Starting a voice or video call moves into ⋮, with the old buttons' rules. While a call is ongoing, its Join button stays in the bar and All threads moves into ⋮, so the chat's name keeps its room on a 360 dp phone. ⋮ offers New thread only to people who can name threads.
2. **Chip bar** (§4a, supersedes §6.1 item 8). The Tasks and All threads chips are gone; the top bar has them. The bar shows **General**, the named threads and **New thread**, and appears only when the chat has an active named thread: renamed, or named when it was created (its hidden first reply marks it). A board alone no longer shows a bar. The board screen always draws it.
3. **Boards from the phone** (§4b). The Tasks icon is in every chat, so a chat without a board reaches the existing Setup state ("Create task board", or "Ask an admin…"). Before creating, the app reads the board pointer from the homeserver, so a board made on the web is never replaced.
4. **Channel tasks** (§4b, §4c, resolves §6.2 decision 4). Ported from the web client's `show_to_room` work. In the web client a group chat is a "channel".
   - A task involves its thread's root sender and repliers, a standalone card's authors, and every member of a DM.
   - On a chat's board, a task shows when it involves you or is shared with the channel. The open-task badge counts only those.
   - The editor has **Show to everyone in channel** with the web client's hint. It is off for new tasks and hidden in DMs.
   - A shared task carries a quiet `Group` icon with the description "Shown to everyone in the channel".
5. **Tasks tab** (§4c). It lists only the tasks that involve you, each with "With Bob, Carol" or "Just you", and gains a **People** filter ("Everyone I work with" or one person) and a **Chat** filter. A List / Table switch shows a table of Task · Chat · Status · Due · People, sortable by column. On a phone the Task column stays put while the rest scrolls sideways. In the two-pane layout, Home takes the whole width while the table shows and nothing is open; opening a task brings back the split.
6. **Every board is found** (§4c). The sync never stores the board pointer, so the app reads it from the homeserver (`fetchStateEventContentJson`, the second method of the Rust SDK patch). The Tasks tab skips chats without a board without reading their history and remembers that answer for ten minutes.
7. **Bottom bar** (§3.1, §3.2, resolves §6.2 decisions 2 and 6). **Chats · Tasks · Notes · Channels**, with plural labels like WhatsApp's. Notes uses `document`, Channels uses `room` (#); Compound has no solid variants, so the selected state is the pill and the label weight. With the flag off the bar stays Chats · Spaces.
8. **Channels tab** (§3.2). It replaces Spaces while the flag is on and follows WhatsApp's Communities tab: each joined space as a header row (opens the space, "N channels"), its joined channels under it as compact chat rows, then **Other channels** for group chats outside any space. DMs are not listed. Invites and the Create space FAB stay as on the Spaces tab.
9. **Notes** (§4e, §5 Docs). The user-facing word "Docs" becomes **Notes** everywhere ("Notes for this space", "Turn on notes", "New note", "Note name"). The main note keeps the name "Space notes", and the wire protocol is unchanged. The **Notes tab** lists every joined space with notes as a section of its notes, offers "Turn on notes" where you can set them up, and has a New note FAB that asks for the space when there are several.

### (i) Palette and global components

- ✅ Everything listed under (i) in §6.0 has landed.
- [ ] `CMP/colors/MatchaColors.kt`: the four ⏳ rows of §2.2.
- [ ] `DS/theme/ColorAliases.kt`: aliases defined through `SemanticColors` fields; `bubbleSecondaryText` and `focusHighlight` added; `chipOutline` deleted (`:84-86`, `:142`); preview map updated.
- [ ] `DS/components/PillFilterChip.kt`: filled unselected style, no border (`:96`), label weight by selection, a `role` parameter (`:89`), KDoc.
- [ ] `DS/theme/components/ModalBottomSheet.kt:68`: `tonalElevation = 0.dp`.
- [ ] New `DS/components/SheetHeader.kt`.
- [ ] `DS/components/avatar/AvatarSize.kt`: add `ChatTopBar(40.dp)`; `TimelineSender` 32 → 28 (`:42`).
- [ ] `libraries/dateformatter`: `DateFormatterMode.Upcoming`, with its implementation and tests.
- [ ] Text-button audit. ✅ The default is green (`DS/theme/components/Button.kt:333-337`), and snackbars opt out (`DS/components/button/ButtonVisuals.kt`). ⏳ Run `grep -rn 'TextButton(' features libraries`; every text button on `bgSubtleSecondary`, `chatWallpaper` or `noticePillBackground` gets `ButtonDefaults.textButtonColors(contentColor = textPrimary)`. Green there is 4.16:1 or lower.
- [ ] `features/networkmonitor/api/.../ui/ConnectivityIndicator.kt` (§3.8).
- [ ] `docs/matcha/*.md`: the §6.1 pointers.

### (ii) Home

- [ ] `H/HomeNavigationBarItem.kt:15-39`: append `Tasks`, `barOrder`, icon branch.
- [ ] `H/HomeView.kt:200-379`: `bottomBar` `NavigationBar`, the FAB at End for Chats and Spaces, padding, and the Tasks branch rendering `MatchaTasksTabRenderer` (integration §5).
- [ ] `H/components/HomeTopBar.kt`: title string, avatar moved to the end, `⋮` always, search pill, no search icon, no `SpaceFilterButton`.
- [ ] `H/components/RoomListMenuAction.kt` and `H/HomeFlowNode.kt:146-155`: `CreateRoom`, `CreateSpace`, `Settings`.
- [ ] `H/filters/RoomListFilter.kt:18-32`: reorder, delete `incompatibleFilters`.
- [ ] `H/filters/selection/DefaultFilterSelectionStrategy.kt:24-28,44-59`.
- [ ] `H/filters/RoomListFiltersView.kt`: the All and Spaces ▾ chips, `PillFilterChip`, `selectableGroup`, padding, dead code removed.
- [ ] Tests: `RoomListFiltersPresenterTest`, `RoomListFiltersViewTest.kt:43`.
- [ ] `H/components/RoomSummaryRow.kt` (§3.1 table) and `RoomSummaryRowUnreadIndicatorCountTest`.
- [ ] `H/components/RoomListContentView.kt`, `H/components/RoomSummaryPlaceholderRow.kt:55`.
- [ ] `H/spaces/HomeSpacesView.kt:75,94,114` and the Spaces title.
- [ ] `H/search/RoomListSearchView.kt`, `H/search/GlobalSearchView.kt`.
- [ ] `features/home/api`: `HomeTab` and `Callback.onTabSelected`.
- [ ] `AN/LoggedInFlowNode.kt` (tab state) and `AN/LoggedInTwoPaneView.kt`: the `:29` comment and the per-tab placeholder at `:64-65`.
- [ ] `AN/twopane/NoRoomSelectedView.kt:39-60`: a tab parameter.
- [ ] `features/home/impl/.../temporary.xml` and the shared keys; `HomeStatePreviewParam` (append), `HomePresenterTest`.

### (iii) Chat screen

- [ ] `M/MessagesView.kt`:
  - wallpaper and status strip (`:244-256`);
  - `MessagesMenuActions` (`:512-532`);
  - composer-slot banners (`:662-746`);
  - pinned banner placement (`:627-655`);
  - the emoji picker sheet.
- [ ] `M/topbars/MessagesViewTopBar.kt`, `M/MessagesPresenter.kt:386,392`, `M/timeline/components/TimelineItemVirtualRow.kt:68`.
- [ ] `M/timeline/components/MessageEventBubble.kt` and `M/timeline/model/bubble/BubbleState.kt:20`.
- [ ] `M/timeline/components/TimelineItemEventRow.kt`: margins, spacing, group avatar and in-bubble name, pin, receipts row removed, thread footer, reactions, reply block, media radius (`:887`).
- [ ] `M/timeline/components/TimelineEventTimestampView.kt:53`.
- [ ] `M/timeline/factories/TimelineItemsFactory.kt:104`: the read and unread-count pass.
- [ ] `M/timeline/model/virtual/TimelineItemReadMarkerModel.kt`, `M/timeline/components/virtual/TimelineItemReadMarkerView.kt`.
- [ ] `M/timeline/components/TimelineItemRow.kt:104,331-358`: selection tint, `focusedEvent` and its preview.
- [ ] `virtual/TimelineItemDaySeparatorView.kt`, `FloatingDateBadge.kt:124-140`, `virtual/TimelineLoadingMoreIndicator.kt`, `virtual/TimelineItemRoomBeginningView.kt`.
- [ ] `TimelineItemStateEventRow.kt`, `MessageStateEventContainer.kt:39`, `event/TimelineItemStateView.kt`, `group/GroupHeaderView.kt`.
- [ ] `event/TimelineItemEncryptedView.kt`, `event/TimelineItemRedactedView.kt`, and the media, file, voice, audio, poll and sticker views (§3.3 chat states).
- [ ] `TimelineItemReactionsView.kt`, `MessagesReactionButton.kt`, `receipt/TimelineItemReadReceiptView.kt:146`, `M/timeline/TimelineView.kt:501-610`.
- [ ] `M/pinned/banner/PinnedMessagesBannerView.kt`.
- [ ] `MUI/messages/reply/InReplyToView.kt:87-132`, `TC/ComposerModeView.kt:136-190`.
- [ ] `TC/TextComposer.kt:456-699`, `TC/components/TextInputRoundedCornerShape.kt`, `SendButtonIcon.kt`, `VoiceMessageRecorderButtonIcon.kt:60`, `VoiceMessageRecording.kt`, `VoiceMessagePreview.kt`.
- [ ] `M/messagecomposer/MessageComposerEvent.kt` (`InsertEmoji`), `MessageComposerPresenter.kt`, `MessageComposerView.kt` (camera and emoji lambdas), `AttachmentsBottomSheet.kt`, `DisabledComposerView.kt`.
- [ ] `M/actionlist/ActionListView.kt`, `model/TimelineItemAction.kt` (`ReadBy`), `model/TimelineItemActionComparator.kt` and its test, `ActionListPresenter.kt`, and `M/MessagesPresenter.kt:406-444` (Seen by → `ReadReceiptBottomSheet`).
- [ ] `M/timeline/components/reactionsummary/ReactionSummaryView.kt`.
- [ ] `features/roomdetails/impl/.../RoomDetailsView.kt`, `features/userprofile` (§3.6); `features/preferences/impl/.../root/PreferencesRootView.kt` (§3.7).
- [ ] `features/messages/impl/.../temporary.xml`.

### (iv) Chip bar and threads

- [ ] `MA/`: remove `AllThreads`; add `MatchaRoomController.kt`. Update the brief.
- [ ] `MX/store/`: `unreadable`, `warning`, `ThreadMetadata.nameSource`, with tests.
- [ ] `MX/chatbar/`: the presenter, state, event, view and `StatePreviewParam`; the New thread and Rename sheet; the thread actions sheet; More; `DefaultMatchaRoomController`; the visibility and collapse rules; the warning strip.
- [ ] Integration §2: `M/MessagesView.kt:258-303`, `M/MessagesFlowNode.kt:218,371`, `MessagesPresenter` (thread subtitle from `threads`), `MessagesMenuActions` `⋮` fallback.
- [ ] `M/topbars/ThreadTopBar.kt:46-100`; `M/threads/list/ThreadsListView.kt` and `ThreadsListPresenter` (chips, names, states, no dividers, load older).
- [ ] Thread footer names (`TimelineItemEventRow.kt:425-496`).
- [ ] Strings: matcha and messages.

### (v) Board and Tasks tab

- [ ] `MA/MatchaRoomEntryPoint.kt`: `Callback.openRoomDetails`.
- [ ] `MX/store/`: `boardPointerRead`, `loadFailed`.
- [ ] `MX/tasks/`:
  - `TasksNode` with its own top bar and the chip bar;
  - the presenter;
  - the view (scrollable tabs below 600 dp, columns from 600 dp, all states);
  - the task row;
  - the actions sheet;
  - the editor sheet;
  - Archived tasks.
- [ ] `MX/taskstab/`: the renderer with its FAB, chips including Archived, grouping, the Choose a chat sheet, the editor and actions sheets, the `AppNavigationStateService` highlight, and the states.
- [ ] Integration §3 and §5: `NavTarget.Tasks`, the attach Task tile (`openNewTask`), the Home Tasks branch, `HomeEntryPoint.Callback.navigateToThread`, `AN/LoggedInFlowNode.kt`.
- [ ] Strings.

### (vi) Reminders

- [ ] `MX/reminders/`: `ReminderService`, the presenter with an injectable clock, and the sheet (steps 0–2, bot states, auto-advance, confirm pill).
- [ ] The system pills for your own `!remind` and the bot's notice (mandatory).
- [ ] Hook-ups:
  - `TimelineItemAction.RemindMe`;
  - the comparator;
  - `ActionListPresenter.kt:225-230`;
  - `M/MessagesPresenter.kt:406-444`;
  - `M/MessagesNavigator.kt:20-31`;
  - chip-bar More and `⋮`;
  - the thread actions sheet;
  - the task sheets.

  All are gated per §4d.
- [ ] Integration §1, the bot id per homeserver. Strings.

### (vii) Docs (scope per §6.2, decision 3)

- [ ] `MX/docs/`: the list (all states), the New and Rename sheet, `DocEditorView` (WebView host, theme bridge, status subtitle, strips, formatting row, unsaved dialog), the setup screen, and the Choose a space sheet.
- [ ] `SP/root/SpaceView.kt`: the Docs row between `:281` and `:284`, the Rooms header, dividers `:278,335` removed, the `⋮` Docs item at `:445-510`. `SP/SpaceFlowNode.kt`: `NavTarget.Docs`.
- [ ] `MessagesFlowNode` `NavTarget.SpaceDocs`; More → Space docs and Earlier chat doc. Strings.

### (viii) Notifications and parity

- [ ] `libraries/push/impl/.../notifications/factories/NotificationCreator.kt`: the thread conversation title (§3.9).
- [ ] Plan Phase 6 checks: the scan timeline sends no read receipts, and opening the board does not mark the room as read.

---

## 7. Non-goals and accessibility

### 7.1 What we deliberately do not copy

- **Brand.** The WhatsApp name, logo, wordmark, the doodle wallpaper artwork, illustrations, animated stickers, glyphs and the app icon.
- **Meta AI.** The AI tab, the AI search ring, the AI FAB and AI chats.
- **Updates/Status and Channels.** No Updates tab, no status rings, no camera action in the Chats bar.
- **Calls tab.** Calls stay the in-chat `CallMenuItem`.
- **WhatsApp-only chat features.** The Archived chats row, chat lock, disappearing messages, payments, broadcast lists, chat themes, the Communities announcements group, "Keep chats archived".
- **Grey "delivered" ticks.** Matrix has no delivery receipts.
- **Private, local reminders.** Ours are visible bot posts, and the copy says so.
- **Beta-only patterns that stable already covers** (as of 2026-10):
  - the floating or label-less bottom bar (2.26.38.10, 2.26.41.7);
  - tail-less round bubbles and frameless media (2.26.10.2);
  - the floating long-press menu (2.26.23.8);
  - the two-row chat bar (2.26.41.11);
  - the chat-list redesign with a "More" lists chip and the "To you" list (2.26.20.2);
  - the logo in the top bar;
  - the profile cover header;
  - the 2026 group-info redesign (2.26.20.7);
  - the iPad sidebar.
- **Deliberate beta borrowings**, used only because stable has no equivalent:

  | Borrowed | Beta build | Used for |
  |---|---|---|
  | "Remind me" sheet layout | 2.25.21.14 (Jul 2025) | §4d step 1 |
  | "Schedule message" date and time step | 2.26.40.7 (Oct 2026) | §4d step 2 |

  Threaded replies (2.25.25.7, Sep 2025) are **not** used as a layout source. The thread screen is the existing Element screen in chat styling.
- **Deferred:**
  - WhatsApp's contextual top-bar mode on long-press;
  - swiping between Home tabs;
  - drag and drop between board columns;
  - a wallpaper pattern;
  - the grouped community blocks;
  - Docs preview rows in the space screen;
  - a Tasks-tab badge;
  - unread dots on thread chips;
  - read ticks in the chat-list preview;
  - a count on the scroll-to-bottom button.
- **Vocabulary.** No renaming of Spaces to Communities, or Rooms to Groups.
- **Avatars.** No grey silhouette placeholders.

### 7.2 Accessibility requirements (must pass in review)

**Contrast** (WCAG 2.1, computed with alpha composited over the named surface)

| Pair | Ratio | Rule |
|---|---|---|
| White on `#1DAA61` | 3.01 | icons and shapes only. **Never text.** |
| White on `#1B8755` | 4.52 | filled-button labels, unread and @ badge digits |
| `#1B8755` on white (green text) | 4.52 | allowed on `#FFFFFF` and `raisedSurface` only |
| `#1B8755` on `#F7F5F3` / on wallpaper `#F5F1EB` | 4.16 / 4.02 ✗ | never: use `textPrimary` there |
| `#0A1014` on `#21C063` | 8.01 | dark green fills |
| `#21C063` on `#0A1014` / `#12181C` / `#20272B` | 8.01 / 7.49 / 6.34 | dark green text |
| `#15603E` on `#D9FDD3` / `#D9FDD3` on `#103529` | 6.83 / 12.12 | selected chip, nav pill |
| `#5B6368` on white / on `#F7F5F3` | 6.12 / 5.63 | secondary text, unselected chips |
| `#8D9599` on `#0A1014` / on `#20272B` | 6.28 / 4.97 | dark secondary text |
| `#8D9599` on `#144D37` (dark outgoing bubble) | 3.21 ✗ | never: use `bubbleSecondaryText` |
| `#EA0038` on white / `#FB5061` on `#0A1014` | 4.61 / 5.85 | errors, overdue (never inside bubbles) |
| `bubbleMetaText` on the outgoing / incoming bubble, light | 5.58 / 5.74 | time inside bubbles |
| `bubbleMetaText` on the outgoing / incoming bubble, dark | 4.65 / 6.40 | time inside bubbles |
| `bubbleSecondaryText` on the outgoing bubble / its quote, light | 5.58 / 5.38 | in-bubble secondary text |
| `bubbleSecondaryText` (`#B3FFFFFF`) on the outgoing bubble / its quote, dark | 5.73 / 5.15 | in-bubble secondary text |
| `bubbleSecondaryText` on the incoming quote, light / dark | 5.53 / 7.29 | quote bodies |
| `textPrimary` on the outgoing quote, light / dark | 15.42 / 8.00 | quoted names |
| `#1B8755` on `#D9FDD3`; `#21C063` on `#144D37` | 4.08 / 4.09 ✗ text | icons only (thread icon) |
| `#1DAA61` on the outgoing quote | 2.43 ✗ | the quote bar only, decorative |
| Light `textDecorative*` on white (lowest: orange) | 4.54 | sender names at the top of incoming bubbles |
| Light `textDecorative*` on the incoming quote (lowest: orange) | 4.06 ✗ | so quoted names are `textPrimary` |
| Dark `textDecorative*` on `#20272B` (lowest: purple) | 5.87 | sender names |
| Link `#006BA6` on white / outgoing bubble | 5.75 / 5.18 | links |
| Link `#53BDEB` on `#144D37` / on `#20272B` | 4.59 / 7.10 | links |
| `readTick` on the outgoing bubble, light / dark | 3.62 / 4.59 | an icon; 3:1 required |
| `noticePillText` on `noticePillBackground`, light / dark | 5.54 / 10.64 | notices, strips |
| `textSecondary` on `systemPillBackground` over wallpaper | 6.07 | pills |
| `#8A5A00` on white / on `#FFF0D4` | 5.93 / 5.27 | warning text, amber initials |
| `iconWarningPrimary` `#B37A00` on white | 3.68 | icons only |
| Muted-badge digits, light / dark | 5.47 / 4.88 | |
| Avatar initials (lowest: orange `#C4532D` on `#FEE2D8`) | 3.70 | identity graphics; at least 3:1 |
| `rowSelected` against white | 1.09 | a tint only; `selected` semantics carry the state |

Any new text-on-colour pair must reach 4.5:1 (3:1 for icons, and for text at 24 sp or larger) before merging.

**Touch targets**
- Every interactive element is at least 48 × 48 dp, even when it is drawn smaller: chips, 24 dp icons, badge-only areas, formatting buttons, the deadline ✕.
- Never set `minimumInteractiveComponentSize` to 0. Rows are at least 56 dp tall.

**Content descriptions**
- Icon-only controls:
  - FABs: `action_start_chat`, `screen_matcha_tasks_new`, `action_create_space`, `screen_matcha_docs_new`;
  - composer: `a11y_matcha_emoji`, `rich_text_editor_a11y_add_attachment`, `screen_room_attachment_source_camera_photo`, `action_send`, `a11y_voice_message_record`;
  - More (`common_matcha_more`), scroll to bottom (`a11y_jump_to_bottom`, with the `stateDescription` `a11y_matcha_new_messages`), the editor's Done (`action_done`), the formatting buttons, the deadline ✕ (`a11y_matcha_clear_deadline`).
- Unread badges (`a11y_matcha_unread_messages`); the @ badge (`a11y_matcha_mentioned`).
- Ticks: `common_sending`, `common_sent`, `a11y_matcha_message_read`.
- Task status, deadline, reminder bell and archived chips: the `a11y_matcha_*` keys in §5.
- Decorative icons inside labelled rows get `contentDescription = null`.

**Semantics**
- In-chat chip bar: General, Tasks and thread chips are `Role.Tab` with a `selected` state. New thread, All threads and More are `Role.Button` with no `selected` state. The row is a `selectableGroup`.
- Home filter chips, Tasks-tab chips, the editor's Status chips and the thread-list chips are `Role.RadioButton` inside `Modifier.selectableGroup()`. Spaces ▾ and Chat ▾ are `Role.Button`.
- Board tabs: Material `Tab` semantics.
- The open chat or task row in two-pane exposes `selected = true`.
- Each task row merges its descendants into one announcement: title, status, deadline, chat.
- Sheets announce their title when they open, and validation errors are live regions.

**Colour is never the only signal**
- Overdue tasks carry the word "Overdue".
- Read state is announced, and also available through Seen by.
- Unread chats show a count as well as the green time.
- Selected chips use the medium weight and unselected chips the regular weight, so the difference is not only colour (fills: 1.02:1).

**Text scaling**
- Layouts survive a 200 % font scale.
- Rows use minimum heights, never fixed ones.
- The chip bar, filter rows and board tabs scroll (`PrimaryScrollableTabRow`).
- Top-bar titles and subtitles ellipsize, and sheets scroll.

**Motion** follows the system animator scale. No custom animation may ignore it.

**Dark mode** is required for every new screen, and is checked in `@PreviewsDayNight` previews.

---

## Decisions log

| # | Critic | Issue (section) | What changed |
|---|---|---|---|
| 1 | completeness, high | The palette had already landed; the spec said "create" and had diverging values (§2.1, §2.2, §6 i) | §2.1 is now a status table pinned to `8363bc94af`. §2.2 has a code-versus-review table that decides every disputed row (iconAccentPrimary `#1B8755`; hover and pressed; separators; Level1 dark → `#12181C`; textSuccessPrimary) and lists every other overridden field. The spec states its own path. |
| 2 | completeness, high | Spec versus the API contract: top-bar slot, AllThreads, Tasks-tab flows, thread names for messages, packages (§3.4, §4) | Added the §4.0 contract table and minimal API changes: AllThreads removed, `openRoomDetails`, `MatchaRoomController` with `MatchaThreadInfo`, `HomeTab`. The board draws its own bar, and the Tasks tab draws its FAB and sheets and gets an Archived chip. The brief's packages are used throughout. |
| 3 | completeness, high | No design for many existing screens (§3) | Added the §3.0 coverage table for every module, plus layout specs for room details and contact info (§3.6) and settings (§3.7). |
| 4 | completeness, high | Create board while the scan is unfinished could orphan the web board (§4b) | Added the "Looking for this chat's task board…" state, `boardPointerRead` in the snapshot, a pointer re-read before create, and the "already has a task board" path. |
| 5 | completeness, medium | In-bubble pairs fail 4.5:1; the §7.2 dark meta figure was wrong | Added `bubbleSecondaryText`, the in-bubble text rule, `textPrimary` quoted names and thread names, and no green or red text in bubbles. §7.2 is recomputed (dark meta 4.65). |
| 6 | completeness, medium | Removing ⊕ would break voice delete and cancel; composer modes undesigned (§3.3) | Kept the focus-safe start `IconButton` (`:486-523`) inside the pill as emoji, trash or cancel. Designed the recording and preview bars, the not-encrypted badge and the caption modes, and added `MessageComposerEvent.InsertEmoji`. |
| 7 | completeness, medium | Chat states undesigned (§3.3) | Added the "Chat states" table: banners, UTD, removed, loading pill, live location, knock, and each media bubble type. Top-bar shield, shared-history and status placement are specified. |
| 8 | completeness, medium | No offline design (all screens) | Added §3.8: the restyled global strip spanning both panes, the sync pill, and the failed-write snackbar with Retry. The state tables gain offline and failure rows. |
| 9 | completeness, medium | Reminder states missing (§4d) | Added the checking step; invite-failed, banned and left rows; auto-advance and Retry; per-entry gating; the thread-mode threadId; the thread validation error; `DateFormatterMode.Upcoming` formats. |
| 10 | completeness, medium | Silent departures from the plan and briefs | Added §6.1 Supersedes (17 items) and a checklist item to annotate the briefs. |
| 11 | completeness, medium | Strings sat in modules their consumers can't reach (§5) | Cross-module strings moved to ui-strings `common_matcha_*`. The thread-list strings live in features/messages, and existing `common_thread` and `common_replies` are reused. |
| 12 | completeness, medium | Duplicate keys and copy gaps (§5) | Reused the existing attach, discard, attachment, quote, camera, send, sending, sent, seen-by and typing keys. Added a lower-case member count, room counts, missing keys and a future date mode. Aligned "Share as text" and "N earlier cards". Dropped the unused no-permission key. Wrote the four warning variants. |
| 13 | completeness, medium | Write errors and busy states missing (§4a, §4b, §4c, §4e) | Added rows for first-send failure, rename and archive failure, Undo failure, busy pills, board create and its failure, write refusals, rename-not-allowed, Docs name failure, catalog errors, docs not set up, `invalid_update`, Tasks-tab per-room failure and unreadable, load older, and thread-list loading and error. Defined "can create docs". |
| 14 | completeness, medium | Two-pane gaps (§3.5) | Per-tab end-pane placeholders through `onTabSelected`; the Tasks highlight through `AppNavigationStateService`; sheets centred on the window; the compact-height rule; the comment fix; the string mapping corrected. |
| 15 | completeness, medium | Single-select change underspecified (§3.1) | Spelled out `select()` and `buildFilters()`, removed `:25` and `:51`, deleted `incompatibleFilters`, corrected the test name, added `RoomListMenuAction` and `HomeFlowNode` wiring, appended Tasks last with an explicit bar order. The title needs no `HomeState`. |
| 16 | completeness, medium | Designs without a data source (§3.3, §4) | The unread count comes from the `TimelineItemsFactory` pass and a data-class marker; the scroll-to-bottom count is dropped (a dot instead); `unreadable`, `warning` and `loadFailed` are added to the snapshot. |
| 17 | completeness, low | Wrong anchors | Fixed PlainTooltip, BubbleState, the timestamp `:53`, MessageStateEventContainer `:29`/`:39` and TextComposer `:486-523`; noted that DefaultEnterpriseService is now done. All anchors re-pinned to `8363bc94af`. |
| 18 | completeness, low | Tokens and checklist entries without files | Named files for the 28 dp sender avatar, media radius, `SheetHeader`, the beginning view, selection tint, pinned banner and `ReadBy` drawable. Wrote the full comparator order with destructive actions last. `rowSelected` and `raisedSurface` targets are listed. |
| 19 | completeness, low | Rule 2 versus the green title; app name | Rule 2 now has the Chats-title exception. The title is a fixed product-name string, flagged as §6.2 decision 1. |
| 20 | completeness, low | No phase mapping or open decisions | Added the §6.0 mapping, §6.2 decisions and the §3.9 notifications section. |
| 21 | completeness, low | Board flood; Black theme | New setup body copy, the fallback-name style, and Black-theme alias values (§2.3). |
| 22 | completeness, low | Chip roles | Roles per chip in §4a and §7.2; RadioButton within `selectableGroup`; a `role` parameter on `PillFilterChip`. |
| 23 | fidelity, high | iconAccentPrimary 3.01:1 badges | Decided `#1B8755` (already applied in ae95163aeb); the other drifted values were decided in the same table. |
| 24 | fidelity, high | In-bubble contrast failures | Same as 5. Thread name and quoted names are `textPrimary`, colour lives on the bar and icon, and `bubbleSecondaryText` is `#99000000` / `#B3FFFFFF`. |
| 25 | fidelity, high | A permanent chip bar is pure chrome | The bar appears only with a named thread or a board (so hidden in most DMs), collapses on scroll, and hides with the IME on Compact and at compact height. `⋮` and the attach tile cover the hidden case. |
| 26 | fidelity, medium | Stale HEAD; §6(i) would redo work | Re-anchored; §6(i) lists the landed commits and only outstanding items; the spec states its file path. |
| 27 | fidelity, medium | `focusedEvent` invisible with transparent gradients | Added a `focusHighlight` neutral tint, held 1 s then faded, plus a preview state. |
| 28 | fidelity, medium | The fixed 360 dp pane | Kept the 2 : 3 weights; the comment claim is fixed. |
| 29 | fidelity, medium | Contract names and behaviours | Contract names used throughout. Archive uses Undo (supersedes the brief). The board draws its own chrome. Editor and reminder options decided and listed in §6.1. |
| 30 | fidelity, medium | Beta sources presented as stable | The baseline is stable only; borrowings listed in §7.1 with builds. Board tabs now reference stable Media · Docs · Links; Create event, About editor and the group-info redesign are dropped as sources. |
| 31 | fidelity, medium | Outlined chips unverified | Switched to stable filled grey chips; `chipOutline` deleted; `PillFilterChip` restyle listed. |
| 32 | fidelity, medium | One-tap public reminder posts | The confirm pill sends; the request and acknowledgement pills are mandatory in v1. |
| 33 | fidelity, medium | Read ticks with the bot or large rooms | The bot is excluded from "read by all", with a 32-member cap. Reminders in DMs only when the bot is already joined (decision 5). |
| 34 | fidelity, low | Filter change incomplete | Same as 15. |
| 35 | fidelity, low | Hex aliases ignore Black and duplicate fields | Aliases are defined through fields where one matches; `raisedSurface` = Level1 (with Level1 dark back to `#12181C`). |
| 36 | fidelity, low | Nav tonal elevation; duplicate badge | Noted as landed (tonal elevation 0 in 2d70d95db9); the Chats badge reuses `NavigationBarIcon`/`CounterAtom`. |
| 37 | fidelity, low | All text buttons turned green | Kept the landed default with the snackbar opt-out; added an audit that keeps `textPrimary` on `#F7F5F3`, wallpaper and notice surfaces. |
| 38 | fidelity, low | Wallpaper under the status bar | Wallpaper on the root with a `bgCanvasDefault` status strip on the phone and a transparent `Scaffold`; it reaches under the navigation bar. |
| 39 | fidelity, low | Top-bar identity icons dropped | Kept after the name; Verified in `iconSecondary`; the violation stays red. |
| 40 | fidelity, low | Decorative green | Pair 1 is amber; Poll and Task tiles are not green; `#25D366` removed. |
| 41 | fidelity, low | Colour-only chip state | Weight cue, RadioButton and Tab roles, and `selected` semantics on the open row. |
| 42 | fidelity, low | "Element X dbg" title and trademark | A fixed product-name string, flagged for David. |
| 43 | fidelity, low | Swapped strings; mixed hero icons | `common_no_chat_selected` is the title; `IconTitleSubtitleMolecule` kept; `BigIcon` everywhere. |
| 44 | fidelity, low | Board tabs truncate at 200 % | `PrimaryScrollableTabRow`, with counts as separate text. |
| 45 | fidelity, low | Changing TimelineRoom affects other screens | Added `AvatarSize.ChatTopBar(40.dp)`, used only by the chat and thread top bars and `MessagesPresenter`. |