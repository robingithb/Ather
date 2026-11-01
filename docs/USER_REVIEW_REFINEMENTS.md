# Home widgets and control-panel refinements — 2 November 2026

This follows the requested five-screen review. All demo layouts remain landscape and fitted to the available window without scrolling. The original climate cabin airflow animation and vehicle renderer/assets remain in use.

| Requested change | Source implementation |
| --- | --- |
| Add widgets on Home | Add widgets opens an in-page library with Navigation, Music, Climate, Vehicle, Battery/range, Clock and a Settings shortcut. Instances can be added, reordered, removed and reset. Their type/order are stored with app preferences. |
| Give Vehicle model and controls equal space | Model/background and controls each own exactly half the feature width after the global navigation rail. |
| Keep Vehicle menus off the model; use cards without paging | Section, lighting, gear and signal choices all stay in the right pane. Access and window controls use complete finite card grids. Vehicle has no dialogs or Previous/Next controls. |
| Modernize driver/passenger temperatures | Native cool-to-warm arc dials display the actual temperature fraction with animated position markers and tinted minus/plus buttons. |
| Replace the full-screen Climate More view | More opens an inline Flow/Seats/Air control deck next to the animated cabin. The bottom command strip stays visible; Done returns to the dials. |
| Make Media playback options clear | Playback and Audio sources form two named groups. Favorite and Shuffle show On/Off; Repeat has explicit Off/All/One selection with an explanation. Common options stay together without an options pager. |
| Reduce Settings slider size | Display and Sound use bounded, compact preference rows with short inline sliders and visible percentages. Other Settings panels also use capped dimensions. |
| Reduce Appearance theme buttons | Four small preview cards share one bounded gallery; theme, accent and ambient actions remain connected. |

## Home widget behavior

The library contains seven app-owned widget types. It supports up to twelve widget instances, including repeated types. Defaults remain Navigation and Music. Compact Home pages display up to two widgets; larger pages display up to four. Additional widgets use explicit Home pages to retain readable cards without scrolling. A page navigator is shown only when there is more than one Home page. This navigator is separate from Vehicle, which has no pagination.

Customization stays alongside the Home preview. The library uses two small pages of choices so compact displays retain usable controls. Previous/Next order buttons and Remove are available while editing. The saved layout is validated, stable IDs prevent order collisions, malformed records recover independently, and theme/sound changes preserve Home widgets.

## Verification scope

Automated verification covers source/resource compilation, domain and layout tests, Android lint, a source audit for scrolling containers, and a merged-manifest landscape check. New tests cover widget persistence encoding and malformed records, duplicates/identifier bounds, moving widgets, retaining layout through appearance/audio changes, widget page anchoring, exact Vehicle 50/50 geometry and complete card grids, plus the inline Climate deck and temperature ranges.

The physical Samsung demo device is connected through ADB and reports 1080×2340 pixels at 480 dpi. No APK is created or installed. New appearance, taps, preference persistence across a real process restart and running animations still require deployment for on-device confirmation. Existing application/test APK timestamps remain unchanged.

Final automated results are recorded in `user-review-refinements-verification.json`; logs are in `app/build/reports/widgets-review-compile-tests.log` and `app/build/reports/widgets-review-lint.log`.

Final automated result: source compilation passed with no Kotlin compiler warnings; all 163 JVM tests passed with zero failures/errors/skips. Android lint passed with zero errors and 58 warnings (DiscouragedApi: 1, PluralsCandidate: 2, AutoboxingStateCreation: 1, UnusedResources: 52, UseKtx: 2, ClickableViewAccessibility: 1). The full-source audit found no scrolling containers in 73 Kotlin files. `git diff --check` and the merged-manifest landscape check pass. No APK was created or installed; visual/device validation remains pending.
