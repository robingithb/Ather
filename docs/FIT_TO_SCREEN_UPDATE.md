# Landscape pages without scrolling — 1 November 2026

All demo devices, including the phone, are landscape infotainment displays. Every app page now consumes its available width and height. There are no scrolling containers in the app's Kotlin source. Longer collections and secondary controls use explicit pages or tabs.

## What changed

- **Shell:** five navigation destinations share a finite side rail. Compact landscape uses a 72 dp rail; spacious displays use 88 dp. Climate command feedback shares the status header instead of adding a row that takes space from the screen.
- **Home:** vehicle summary and two cards share one finite page. The route and media controls cannot grow beyond the display. Playback controls scale with the media card's actual width and height; native Compose text fitting keeps labels within their slots.
- **Climate:** the original cabin artwork and `CabinAirflowOverlay` animation remain visible at every demo size, between driver and passenger temperature controls. Fan, Auto, both defrost controls and More occupy a bounded command strip. More opens fixed Airflow, Seat comfort and Air quality pages. All original temperature, fan, airflow, A/C, Sync and seat actions remain connected.
- **Vehicle:** the live 3D model remains beside the selected controls on landscape screens. Access, lights, windows and charging controls use a measured number of rows and explicit Previous/Next buttons. Section and option dialogs are also paged. The renderer, model assets, material behavior and scene animations were not changed in this revision.
- **Media:** artwork, metadata, transport and seek use a finite geometry budget. Library, queue and suggestions use explicit page controls; the scrolling fallback is removed. Category, playback-option and output dialogs also use finite pages. Import, demo audio, favorites, shuffle, repeat and output information remain accessible.
- **Settings:** six categories fit a two-column grid. Each selected category fits a page. Appearance has Theme, Accent and Ambient tabs; themes fit a 2×2 grid and colors fit two rows. Routing and system details have separate tabs. No long settings stack or scrolling color chooser remains.

This uses native layouts, proportional slots, finite row budgets and native text auto-sizing. It does not render a large reference canvas and shrink it into a phone. Primary controls adapt to available space; compact display controls use smaller targets than spacious displays. Text that exceeds an allocated metadata slot can be ellipsized, while its associated action stays visible.

## Verification

The compile-only workflow runs `:app:compileDebugKotlin`, `:app:testDebugUnitTest` and `:app:lintDebug`. It does not package or install an APK.

New tests cover the geometry actually used by Home, Climate, Vehicle and Media, including compact landscape content areas around 500×250 dp, larger displays, and enlarged fonts. Existing playback, climate and vehicle-domain tests are retained. Source review covers all app-owned pages and dialogs; the scroll audit checks the entire `app/src/main/java` tree for vertical/horizontal scroll modifiers, lazy lists and scroll-state containers.

Android Studio previews cover compact, standard and wide landscape displays. Preview source compiles, but the new layouts and animations have not been visually verified on the physical device because this request does not deploy an APK. ADB is connected and reports the Samsung SM-G990E as 1080×2340 pixels at 480 dpi, approximately 780×360 dp in landscape before insets. The animation implementation is retained, rather than replaced with a still image; observing the updated runtime requires deployment.

Final results are recorded in [fit-to-screen-verification.json](fit-to-screen-verification.json). Earlier source reports describe historical layouts, including the scrolling fallback that this update removes.

Final result: compilation passed with no Kotlin compiler warnings; all 153 JVM tests passed. Lint reports zero errors and 41 warnings (DiscouragedApi: 1, AutoboxingStateCreation: 1, UnusedResources: 37, UseKtx: 2, ClickableViewAccessibility: 1). `git diff --check` passes. The full-source scroll audit checked 70 Kotlin files and found zero matches. Existing application/test APK timestamps are unchanged.
