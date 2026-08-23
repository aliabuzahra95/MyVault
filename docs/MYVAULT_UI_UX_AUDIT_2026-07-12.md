# MyVault Comprehensive UI/UX Audit

**Date:** 12 July 2026  
**Scope:** Visual and interactive product quality only  
**Implementation status:** Audit only; no source code changed

## Audit method and confidence

This audit combines live interaction on the connected Samsung foldable with a read-only inspection of the current Compose design system and screen implementation.

Live device conditions:

- Active cover display: 1080 × 2520 px at 420 dpi.
- Refresh capability: 120 Hz.
- Android font scale: 1.0.
- Themes inspected: light and dark; the original Auto preference was restored afterward.
- Live areas inspected: Study/Home, folder hierarchy, Library, annotation and pinned rows, Courses, note reading, note editor, PDF reader, Qur'an reader, AI Listen, Memorise, Settings, modal sheets, scrolling, tab movement, startup, and loading/opening behaviour.
- The unfolded inner display was off and could not be physically inspected. Foldable large-width behaviour is therefore code-inferred and requires a later unfolded-device pass.
- Keyboard typing was intentionally not tested because the audit was not permitted to edit note content.

Important version qualification:

The APK currently installed on the phone predates the latest Ask AI decommissioning work in the source tree. It still displays old Ask AI launchers. The source tree no longer exposes those launchers. Findings about those particular buttons are therefore deployment-validation items, not recommendations to redesign a feature that has already been removed.

## 1. Executive assessment

MyVault already has a recognisable visual identity. Its restrained blue accent, quiet neutral surfaces, rounded cards, dense information architecture, and strong reading-oriented screens make it feel materially more intentional than a generic Compose prototype.

The product is approximately **7/10 in visible polish** on the tested cover display. Its best screens approach mature-product quality. The note-reading screen, Qur'an reading cards, Settings appearance panel, PDF capability, and consistent blue/neutral palette are credible foundations.

What prevents the whole app from feeling premium is not a lack of styling. It is inconsistent restraint:

- Compact list rows and icon controls frequently fall below comfortable touch sizes.
- Floating navigation and action controls obscure real content.
- Light-theme system status icons are nearly invisible.
- Muted text contrast is too weak, especially at the app's smallest text sizes.
- The editor, PDF reader, Settings dialogs, and Qur'an subsystem each contain one-off sizing and shape decisions.
- Dense surfaces sometimes optimise for the number of visible rows at the expense of rhythm, feedback, and ergonomics.
- Some sophisticated features are visually presented as many equally weighted controls rather than a clear primary action with secondary actions.

The correct direction is refinement and consolidation, not a wholesale redesign.

## 2. Overall product feel

### What feels professional

- The neutral palette is calm and suitable for long reading sessions.
- Accent colour is used consistently for selection, active tools, important actions, and progress.
- Cards generally use quiet borders rather than excessive shadows.
- The reading and Qur'an experiences prioritise content rather than decorative chrome.
- English and Arabic coexist surprisingly well on the tested screen.
- The app handles a genuinely large, real corpus without turning the interface into a generic file manager.
- Root navigation is always visible and labelled.
- Settings communicates configuration clearly and uses good grouping.

### What feels unfinished

- The UI oscillates between very compressed hierarchy rows and very large cards/settings rows.
- Small grey metadata can become almost invisible.
- Some screens have five or more equally prominent icon buttons in the header.
- Press feedback is intentionally disabled in several prominent interactions.
- The bottom navigation, primary FAB, and formerly installed AI FAB occupy overlapping visual territory.
- Many dialogs still use raw Material `AlertDialog` while newer flows use the more distinctive `VaultModal` system.
- Long names frequently end in ellipses without an obvious secondary way to inspect the complete value.

## 3. Strongest UI/UX areas

### Note reading

The reading view has a clear title, useful breadcrumb, metadata, generous body spacing, and strong long-form readability. It feels closer to a document reader than an editor preview. The hierarchy is immediately understandable.

### Qur'an reader

This is the most distinctive screen family. Uthmani Arabic, translation, āyah cards, memorisation state, tafsīr, reflection, audio, and AI Listen are integrated into a coherent reading surface. The Arabic remains visually dominant, which is appropriate.

### Settings appearance system

The Light/Dark/Auto previews and accent swatches are clear, visually pleasing, and easy to understand. Grouped settings cards are predictable and scan well.

### PDF capability

The PDF itself renders sharply. Progress, title, page position, drawing, notes, colours, and annotation tools are immediately available. The feature feels powerful rather than experimental.

### Core palette and shapes

The `VaultColors` and `VaultShapes` foundations are sensible. Light and dark themes share a recognisable identity, and custom accents propagate consistently through primary states.

## 4. Weakest UI/UX areas

### Critical: light system-bar visibility

On every inspected light-theme screen, the status-bar time and system icons were white against the near-white app background. They were effectively invisible. This damages basic orientation and is an accessibility failure.

### High: ergonomic compression

The visual density is often attractive, but the interaction geometry is too small. Shared icon buttons are 38 dp, top-level folder rows are 36 dp, nested rows are 30–32 dp, hierarchy chevrons are 11–13 dp, and Qur'an action buttons are 28 dp. These sizes are difficult to tap reliably and do not meet a comfortable Android interaction standard.

### High: floating controls obscure content

The bottom navigation overlaps list rows/cards on Study, Library, Qur'an, Memorise, and Courses. The create FAB and secondary floating action add further obstruction. The last visible content is readable through or behind translucent navigation rather than being cleanly above it.

### High: low-contrast metadata

The calculated contrast of `textMuted` is approximately:

- 2.96:1 on the light background.
- 3.28:1 on a white light surface.
- 3.42:1 on the dark background.
- 3.18:1 on a dark surface.

This is used with 10.5–12.5 sp text in many places. Dates, counts, subtitles, folder metadata, placeholders, and inactive navigation labels can become difficult to read.

### High: screen-specific design dialects

Settings, the Vault modal system, the editor, PDF overlays, and Qur'an sheets do not always use the same shapes, padding, headers, or action hierarchy. They remain related by colour, but not consistently by component behaviour.

## 5. Design-system consistency

### Existing strengths

- `VaultColors` provides semantic colours rather than arbitrary per-screen values.
- `VaultShapes` provides a coherent radius ladder: 10, 14, 18, 22, and 26 dp plus pills.
- `VaultSpacing` establishes a basic 4–40 dp rhythm.
- Shared components exist for top bars, icon buttons, folder rows, settings rows, search, cards, and modals.

### Main consistency problems

The UI code still contains:

- approximately **1,778 direct dp literals**;
- approximately **110 direct sp literals**;
- **129 direct `RoundedCornerShape` declarations**;
- **44 raw Material AlertDialogs**;
- **16 modal bottom sheets** with multiple visual treatments;
- **33 interactions explicitly removing their indication**.

These numbers do not prove that every literal is wrong. They do show why similar elements drift.

The clearest duplication is Study's `FolderTreeRow` versus Library's private `LibraryHierarchyRow`. They now contain almost the same density and indentation calculations but remain separate implementations. This creates ongoing alignment, accessibility, and animation drift.

## 6. Typography audit

### Strengths

- Main titles are confident and legible.
- Body text in reading and editing is comfortable on the cover display.
- The Qur'an-specific font provides an authentic and high-quality Arabic reading experience.
- Bold hierarchy works well in sparse screens such as Settings and Courses.

### Weaknesses

- `InterFallback` is actually `FontFamily.SansSerif`, not a bundled Inter family. The app therefore has less typographic identity than the name suggests and may vary subtly across devices.
- `bodyLarge` is 13.5 sp and `bodyMedium` is 13 sp. The hierarchy depends more on weight than size.
- `labelSmall` is 10.5 sp, bold, and letter-spaced by 0.16 em. It is used frequently enough that the product can look over-labelled and mechanically uppercase.
- Most type roles lack explicit line heights. Screen-specific copies then introduce their own values.
- Reading headings are sometimes extremely large and heavy relative to body text. In the inspected note, consecutive headings competed with the note title.
- Raw separators and quote markers (`---` and `>`) were visible inside the reading experience. Even if they originate in user content, their presentation makes the view feel less finished.
- Memorise overuses bold, widely tracked labels across its focus card, metrics, filters, and section headings.
- Secondary Arabic outside Qur'an content depends on general system fallback, while Qur'an uses dedicated families. Mixed Arabic/English is readable, but baseline and weight consistency should be formalised as design roles.

### Recommended direction

Define explicit roles for:

- app title;
- screen title;
- document title;
- section title;
- dense row title;
- metadata;
- control label;
- Arabic UI label;
- Qur'an Arabic;
- translation;
- tafsīr/body Arabic.

Use a deliberate system-font decision or bundle the intended Latin family. Do not change fonts merely for fashion.

## 7. Spacing and density audit

### Study and Library

The compact hierarchy allows an excellent amount of information to be visible. On the live device, however, nested rows were only around 30 dp high. Press highlights therefore touch adjacent rows, and the visual rhythm becomes a continuous text block rather than a sequence of reliable targets.

The current indentation is much improved over a conventional deeply indented tree, but the tiny chevron/icon/title gaps make nested levels visually fragile. Red subfolder labels provide hierarchy, but their strength competes with the blue primary accent and suggests error/destruction.

### Cards versus rows

Pinned and annotation cards are considerably larger than dense file rows, despite often containing only one or two truncated lines. The app therefore feels sparse at the top of Library and compressed immediately below it.

### Settings

Settings uses comfortable padding and clear grouping, but the rows and cards are large enough that common configuration requires substantial scrolling. This is not inherently bad; the opportunity is to reduce repeated vertical framing rather than shrinking targets.

### Empty space

Courses displayed a large unused lower region when only three concept cards existed. The grid's fixed card strategy created an orphaned half-row and made the screen feel unfinished rather than intentionally calm.

## 8. Icons and visual hierarchy

### Strengths

- The app mainly uses one Material Rounded family.
- Icons usually have understandable meanings and appropriate colour.
- Active states consistently use accent blue.
- The PDF tool icons and note-reader actions are recognisable.

### Problems

- Root headers can display four or five equally weighted 38 dp icon tiles. The title and primary task lose priority.
- Theme, backup, settings, overflow, search, view mode, bookmarking, memorisation, AI Listen, and reader settings are frequently presented at the same visual weight.
- The active theme button is always blue but does not communicate whether blue means “active”, “light”, or simply “available”.
- Search uses a Close icon as its back affordance in the separate Search screen source.
- Qur'an's mic and memorisation buttons are only 28 dp containers.
- Folder chevrons can be 11 dp and remove ripple feedback.
- The bottom navigation disables indication, so taps lack immediate tactile visual confirmation.

Recommended direction: preserve small glyphs but place them in accessibility-sized invisible/transparent hit containers. Move low-frequency header actions into overflow rather than shrinking all controls.

## 9. Motion and animation audit

### Observed motion

- Root tab changes use a 280 ms pager animation and maintain orientation well.
- Folder chevrons rotate over 150 ms; expand/collapse animation is brief and comprehensible.
- Search result expansion, pinned sections, hydration, and PDF preview fading use restrained 100–220 ms transitions.
- Bottom sheets opened smoothly and maintained background context.

### Issues

- Navigation destinations outside the root pager use default Compose transitions and can feel abrupt beside the carefully animated pager.
- The 280 ms root-page motion is smooth but slightly leisurely for frequent switching.
- Thirty-three interactions remove their indication. The result is less feedback, not more premium restraint.
- Organise mode uses an infinite 120 ms shake. This is visually busy and should be tested for fatigue and reduced-motion behaviour.
- The installed AI Listen sheet began recording immediately after the āyah mic was tapped. The sheet itself was clear, but the transition from browsing to active microphone use was too abrupt for a privacy-sensitive action.
- Motion has no visible central duration/easing policy. Timings are similar, but they are duplicated.

## 10. Interaction-feedback audit

### Good feedback

- Selected navigation items, theme previews, accent swatches, filters, and memorisation states have strong active styling.
- Destructive actions usually request confirmation.
- AI Listen clearly showed recording state, timer, provider, Pause, and Stop.
- PDF page position and progress remain visible.

### Weak feedback

- Dense folder/file rows often rely on a subtle ripple inside a 30–36 dp row.
- Several Qur'an cards and action buttons explicitly remove indication.
- The root bottom navigation has colour/scale animation but no pressed response.
- Save feedback in the editor is a small, widely letter-spaced muted label and can be overlooked.
- The double-tap-on-bottom-navigation shortcut for quick note creation is undiscoverable and risks accidental activation.
- Boolean Settings rows such as “Show full note titles” and “Security lock” end in chevrons rather than switches, so they visually resemble subpages rather than toggles.

## 11. Navigation and orientation

### Strengths

- Root destinations have icon and text labels.
- The active root is clear.
- Note breadcrumbs communicate folder context effectively.
- PDF and deep screens provide a consistent back affordance.
- Qur'an displays the current surah, ayah count, revelation place, and juz.

### Concerns

- Floating bottom navigation covers content instead of occupying a respected layout inset.
- Pressing Back from a non-Study root can animate back to Study rather than follow the user's normal expectation of leaving the root screen.
- The hidden bottom-nav double tap introduces a second undocumented meaning to a standard navigation control.
- Editor breadcrumbs repeat the note title after the title is shown directly below, consuming narrow-width space and truncating aggressively.
- Header action overload makes it harder to identify a screen's primary action.
- On a future unfolded display, the floating nav is capped at 342 dp while content can span roughly tablet width, making navigation visually detached unless an adaptive shell is introduced.

## 12. Performance and perceived fluidity

### Measured device results

- Cold launch: **234 ms** in the sampled `am start -W` run. This is excellent.
- Study/Library-style list scrolling sample: **1 janky frame out of 237 (0.42%)**, median approximately 5 ms.
- PDF scrolling sample: **2 current-deadline janky frames out of 256 (0.78%)**. Median was 16 ms and 90th percentile 17 ms, indicating that the PDF surface often presents at an approximately 60 fps cadence on a 120 Hz display.
- Initial startup frame sample contained several slower frames, but the total startup was so short that no prolonged loading experience was observed.

### Interpretation

The main app is technically fast. Its perceived sluggishness, where present, is more likely caused by:

- controls moving over content;
- state changes without pressed feedback;
- large sheets appearing immediately;
- PDF overlay work and 60 fps cadence on a high-refresh display;
- deep tree composition and very large screen functions;
- layout hierarchy that makes the eye search before acting.

Potential code risks requiring profiling rather than assumption:

- Study and Library construct expanded hierarchy sections inside larger non-lazy blocks.
- PDF viewport updates and annotation overlays can recompose during scrolling.
- Editor rich-text visual transformation processes a large text value.
- The root pager keeps an adjacent page composed, which is reasonable but should be measured with the heavier Qur'an/Library screens.

## 13. Accessibility and ergonomics

### High-priority issues

- Light-theme system icons fail visibility.
- `textMuted` fails comfortable contrast for small normal text.
- Shared `IconBtn` is 38 dp.
- Dense hierarchy rows are 30–36 dp.
- Qur'an action targets are 28 dp.
- Some interactive chevrons have null descriptions and no indication.
- Several icon-only controls depend entirely on content descriptions and have no visible label or tooltip.

In a live Study hierarchy accessibility dump, 28 of 47 clickable semantics nodes had at least one dimension below 48 dp. Compose semantics merging means this is not a perfect automated accessibility score, but it confirms the scale of the touch-target issue.

### Large text and foldable resilience

The audit used font scale 1.0. Many fixed-height rows and 10–11 sp labels are unlikely to remain comfortable at larger font scales. Long Arabic titles, long PDF filenames, and mixed-language note names already truncate at the default scale.

The unfolded inner screen was not tested. Code uses full-width cards with very few maximum-width reading constraints or two-pane adaptations. Reading line length, Settings cards, and Qur'an cards are likely to become excessively wide.

## 14. Screen-by-screen findings

| Screen/component | What works | Main weakness | Severity | Complexity | Device testing |
|---|---|---|---|---|---|
| Study/Home | Fast, coherent tree, excellent information visibility | 30–36 dp rows, weak metadata, red hierarchy competes with accent, floating controls cover rows | High | Medium | Yes |
| Folder hierarchy | Clear counts and nesting; concise indentation | Tiny chevrons, cramped ripple area, direct hardcoded density | High | Medium | Yes |
| Library | Rich corpus is genuinely navigable; useful annotations/pinned sections | Header overload, severe truncation, top cards too large versus dense rows, duplicated hierarchy component | High | Medium | Yes |
| Annotation cards | Clear category and entry point | Titles and sources truncate so heavily that cards become hard to distinguish | Medium | Small | Yes |
| Pinned file cards | Fast access and calm styling | Large card footprint with little visible information | Medium | Small | Yes |
| Courses | Strong course selector, Continue card, concept model | Fixed grid produces orphaned cards/empty space; definitions truncate; action hierarchy is flat | Medium | Medium | Yes |
| Note reading | Best long-form screen; clear context and comfortable measure | Heading weights are excessive; raw markup visible; edit FAB can cover text | High | Medium | Yes |
| Note editor | Powerful, direct, good body measure | Crowded top bar, redundant breadcrumb/title, toolbar feels utilitarian, floating Structure action overlaps content | High | Large | Yes |
| Structure & Format | Focused retained AI concept is appropriate | Must be retested after installing current source; installed APK still showed retired Ask AI | High validation | Small | Yes |
| PDF reader | Sharp rendering and powerful annotation access | Title/page overlays cover document; bottom toolbar obscures content; 60 fps cadence on 120 Hz | High | Large | Yes |
| PDF highlights/annotations | Colours and actions are immediately available | Always-visible tool chrome competes with reading; active mode hierarchy needs clarity | High | Medium | Yes |
| Qur'an reader | Strongest product identity; excellent Arabic dominance | Too many top/card actions, 28 dp targets, repeated status pills, bottom nav covers content | High | Medium | Yes |
| AI Listen | Clear recording state, large timer, provider control, strong Stop/Pause buttons | Recording starts too abruptly; provider jargon and privacy transition need refinement | High | Medium | Yes |
| Memorise | Excellent overview and progression model | Oversized title/cards, filter row clips, excessive bold/tracking, bottom overlap | High | Medium | Yes |
| Settings | Clear grouping and strongest general-purpose component consistency | Very long page; toggles look like navigation; legacy ChatGPT naming remains in installed build | Medium | Medium | Yes |
| Search | Clear filter concept and simple result cards in source | Close used as Back; folder result row has no click action; empty states are visually thin | High | Small | Yes |
| Dialogs | Confirmations are generally safe | Raw AlertDialog and VaultModal styles coexist extensively | Medium | Medium | Yes |
| Bottom sheets | Context is preserved and sheets animate smoothly | Headers, drag handles, close controls, padding, and corner treatments vary | Medium | Medium | Yes |
| Empty/loading/error states | Restrained and understandable | Mostly generic text/icon/progress states with little MyVault identity or recovery guidance | Medium | Medium | Yes |
| Bottom navigation | Clear labels and selected state; smooth pager continuity | No pressed indication, overlays content, hidden double-tap behaviour | High | Medium | Yes |

## 15. Code-level causes of visual inconsistency

1. **Token coverage is incomplete.** Core tokens exist, but screen files still contain a large number of literal sizes, radii, colours, and animation timings.
2. **Parallel components exist.** Study and Library use different hierarchy renderers for equivalent structures.
3. **Legacy and premium modal systems coexist.** The app contains many Material AlertDialogs alongside `VaultModal`, `VaultFormModal`, `VaultConfirmModal`, and `VaultActionModal`.
4. **Typography roles are too broad.** Screens repeatedly copy Material roles and override font size, weight, line height, and letter spacing.
5. **Large screens own too much presentation logic.** Editor and PDF viewer are very large files with local UI systems, making cross-product consistency difficult.
6. **Interaction geometry is tied to visual geometry.** Small icons and compact rows also receive small touch targets rather than using larger invisible hit areas.
7. **No adaptive-width layer is visible.** Full-width phone components are reused without explicit compact/medium/expanded layouts.
8. **Motion values are duplicated.** The app has many sensible timings but no shared motion tokens or reduced-motion policy.

## 16. Recommended design-system refinements

### Foundations

- Add explicit system-bar tokens/handling for light and dark icon appearance.
- Introduce contrast-safe `textTertiary` and `textDisabled`; stop using one very faint muted colour for all secondary purposes.
- Define minimum interactive sizes independently from visual glyph sizes.
- Create compact, regular, and comfortable density roles rather than per-screen row math.
- Define screen/content maximum widths for expanded foldable layouts.

### Typography

- Replace `InterFallback` with an honest system-font role or bundle the intended family.
- Add line heights to all frequently used roles.
- Reduce reliance on 10.5 sp tracked uppercase labels.
- Add first-class Arabic UI, Qur'an, translation, and bilingual metadata roles.

### Components

- Unify Study and Library hierarchy rows.
- Create one root header with primary and overflow action slots.
- Create one overlay-safe floating navigation scaffold.
- Migrate common dialogs to Vault modals while retaining system dialogs where platform familiarity is beneficial.
- Standardise bottom-sheet header, drag handle, dismiss action, insets, and maximum height.
- Create a shared empty/loading/error pattern with recovery action support.

### Motion

- Define fast, standard, and navigation durations plus shared easings.
- Restore subtle pressed indication to navigation and cards.
- Provide reduced-motion behaviour for organise shake and large transitions.

## 17. Quick wins

| Change | Priority | Complexity | Expected impact |
|---|---|---:|---|
| Correct light-theme status/navigation-bar icon appearance | Critical | Small | Immediate app-wide professionalism and accessibility |
| Raise muted-text contrast | Critical | Small | Makes metadata and controls readable everywhere |
| Add bottom content insets equal to nav/FAB obstruction | High | Small | Stops content being hidden without redesigning navigation |
| Keep 16–18 dp glyphs but expand icon hit targets to at least 48 dp | High | Small–Medium | Major ergonomic improvement |
| Remove `indication = null` from root nav and primary card actions | High | Small | Better responsiveness and confidence |
| Put low-frequency top-bar actions in overflow | High | Small–Medium | Restores title hierarchy and narrow-width resilience |
| Replace Search Close icon with Back and wire folder results | High | Small | Fixes orientation and interaction clarity |
| Validate that the next installed APK contains no retired Ask AI launchers | High | Small | Aligns running product with current direction |
| Prevent AI Listen from entering recording without a clear ready/start step | High | Small–Medium | Improves privacy, predictability, and trust |

## 18. Medium-size improvements

- Consolidate Study and Library hierarchy rows into one accessible density-aware component.
- Harmonise modal and bottom-sheet presentation.
- Rebalance Library annotations/pinned cards against its file density.
- Rework Memorise filters so all choices remain visible or clearly horizontally scrollable.
- Simplify Qur'an card status/action hierarchy while preserving AI Listen.
- Give note headings a calmer scale ladder and render structural separators/quotes intentionally.
- Reframe editor save status and move Structure & Format into an overlay-safe location.
- Add compact and expanded root-header variants.
- Add meaningful skeleton/loading states for PDF, attachments, and large corpus hydration.

## 19. Major redesign candidates

These are bounded subsystem redesigns, not an app-wide visual replacement.

### PDF chrome

Create reading-first, annotation-active, and selection states. In reading mode, title/page controls should collapse or fade; annotation tools should appear only when relevant. This is the largest single opportunity to make a powerful feature feel premium.

### Editor chrome

Separate document navigation, formatting, document state, and Structure & Format into clearer layers. Preserve the editor engine and content model.

### Adaptive foldable shell

On the unfolded display, consider a constrained reading column and optional navigation/library pane rather than stretching phone cards. This requires real unfolded-device design testing.

### Qur'an action hierarchy

Keep AI Listen, memorisation, tafsīr, reflection, and audio, but distinguish primary per-āyah actions from secondary actions. The goal is less visual competition, not feature removal.

## 20. Prioritised implementation roadmap

### Phase 0 — Current-build visual baseline

1. Install the current source build on the device after approval.
2. Capture light/dark screenshots for every major screen.
3. Test cover and unfolded displays plus font scales 1.0, 1.15, and 1.3.
4. Confirm retired Ask AI surfaces are absent and Structure & Format is present.

No design changes should be made before this baseline, because the running APK and source currently differ.

### Phase 1 — Critical accessibility and obstruction fixes

1. System-bar contrast.
2. Muted text contrast.
3. Minimum touch targets.
4. Bottom navigation/FAB safe content insets.
5. AI Listen explicit ready/start interaction.
6. Search orientation and folder-result interaction.

Estimated size: medium. Risk: low if implemented through shared tokens/components.

### Phase 2 — Shared design-system consolidation

1. Typography roles and line heights.
2. Density and touch-target tokens.
3. Shared hierarchy row.
4. Shared root header and action overflow.
5. Modal and bottom-sheet standards.
6. Motion tokens and pressed feedback.

Estimated size: medium–large. Risk: medium because it affects many screens; implement component by component.

### Phase 3 — Highest-value screen refinement

1. Library hierarchy/cards/header.
2. Study hierarchy and management states.
3. Note reading hierarchy and markup presentation.
4. Memorise density and filters.
5. Qur'an card action hierarchy while preserving AI Listen.
6. Courses empty-space and concept grid behaviour.

Estimated size: large but divisible into independent screen stages.

### Phase 4 — Editor and PDF chrome

1. Editor top/bottom action architecture.
2. Overlay-safe Structure & Format.
3. Reading-first PDF chrome.
4. Contextual annotation tools.
5. PDF high-refresh profiling and optimisation.

Estimated size: large. Risk: medium–high. Keep editor/PDF engines unchanged during visual refinement.

### Phase 5 — Foldable, accessibility, and final polish certification

1. Unfolded adaptive layouts and maximum reading widths.
2. Large-font and long-title testing.
3. TalkBack traversal and descriptions.
4. Reduced-motion behaviour.
5. Light/dark/Auto regression matrix.
6. Jank measurement for startup, hierarchy, editor, PDF, and Qur'an.

Estimated size: medium. This is the production-quality completion gate.

## Final judgement

MyVault does not need a new visual identity. It needs its existing identity applied with more discipline.

The greatest improvement will come from four changes: make every interaction comfortably tappable, stop floating chrome from covering content, repair low-contrast/light-system UI, and consolidate equivalent components. Those changes will improve every screen without destabilising the application's distinctive knowledge-management, PDF, note, and Qur'an experiences.

No implementation has been performed. Approval is required before any refinement begins.
