# Aether infotainment simplification — 1 November 2026

The subsequent [fit-to-screen update](FIT_TO_SCREEN_UPDATE.md) supersedes the scrolling/stacked layout descriptions below. Current screens are finite landscape pages, and the original climate cabin animation is visible at every demo size.

This update implements a production-oriented visual hierarchy and adaptive landscape layout. Every demo device, including a phone, acts as a landscape infotainment display. It remains an Android HMI prototype with simulated vehicle data. A successful build does not establish vehicle safety or production readiness.

## Review and comparison

The connected Samsung SM-G990E reports 1080×2340 physical pixels at 480 dpi (approximately 780×360 dp in landscape, before insets). The previously installed app was inspected through ADB at the start of the review. A later ADB reconnect reported the device as unauthorized; renewed USB-debugging authorization is required for further device checks. The previous Settings screen showed theme previews, connectivity, brightness, two volume sliders, routing preferences, software, storage, diagnostics and cabin appearance simultaneously. Home/Climate/Vehicle/Media also carried duplicated media or HVAC controls. Source layouts used viewport scaling, tiny text and some 23–34 dp controls.

[Google's automotive visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles) emphasize glanceable content and recommend 76×76 dp touch targets. Its suggested primary and secondary text sizes are larger than ordinary phone UI. The phone is the landscape infotainment demo display, so its available display area informs layout adaptation; production ergonomics still require validation at the final mounting position and viewing distance.

[Tesla's Model 3 climate documentation](https://www.tesla.com/ownersmanual/model3/en_us/GUID-4F3599A1-20D9-4A49-B4A0-5261F957C096.html) illustrates temperature and Auto as common operations, with an expanded view for detailed climate settings. It also keeps quick climate access at the bottom. A persistent essential control is useful; repeating an entire control panel across pages is unnecessary. Our choice here is one stable Climate destination and no repeated HVAC panels. A vehicle with no physical defrost control would need a separate persistent defrost-access decision before deployment.

## Implemented

| Screen | Default view | Secondary actions |
| --- | --- | --- |
| Home | Read-only vehicle status/range, route preview and one media card with transport | Full media and route details open explicitly |
| Vehicle | Battery, lock and trunk; one section chooser | Access, Lights, Windows and Charging; extra openings and simulator actions expand on demand |
| Climate | Driver/passenger temperature, fan, Auto, front/rear defrost | More controls opens A/C, Sync, airflow, seat comfort and air information |
| Media | One player with large previous/play/next controls | Separate Library and Queue views; More opens favorites, repeat/shuffle, import and output information |
| Settings | Six category entries; one selected detail pane | Display, Sound, Connectivity, Appearance, Navigation, System |

The shell no longer renders the old global dashboard dock, climate media bar or interactive status-chip row. Playback state remains shared between Home and Media. Vehicle rendering, assets, vehicle repository behavior, original audio and persistent appearance preferences are preserved. Existing theme choices remain available in Appearance. Diagnostics in System are explicitly simulated; the old unsupported numerical health score is removed.

## Adaptive landscape behavior

- Layout uses available window constraints, rather than a specific phone model or a scaled reference canvas. Different landscape sizes, densities and aspect ratios are supported.
- One persistent side navigation rail is used at every window size. Navigation itself can scroll when a short display or large text needs more space.
- The activity requests sensor landscape orientation so either landscape rotation can be used on the demo device. It remains resizable to adapt to the available landscape window.
- Demo tablets/head units should have their system/display orientation configured to landscape: [Android 17 may ignore app orientation requests on large displays](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored) for apps targeting SDK 37.
- Home cards stack when narrow or text is enlarged; they grow with wrapped content. Background images follow their cards without determining content height.
- Vehicle switches between a model/control split and a scrollable stack. Climate stacks its temperature zones and shows cabin art only when enough room is available. Media artwork/details reflow and content scrolls. Settings uses a list/detail split on wide windows and category drill-in on narrow ones.
- Compact landscape controls use minimums (48–64 dp); roomy climate/media controls use automotive targets of at least 76 dp. Vehicle and Settings actions are at least 64 dp. This is an incremental ergonomics improvement, not a claim that every head-unit control and spacing meets an OEM standard.
- Added Android Studio previews for compact, standard and wide landscape displays and enlarged text. Preview declarations are available for manual rendering; they are not screenshot-test results.

## Recommended next production work

1. Validate the final head-unit dimensions, mounting position, viewing distance, reach and input hardware. Establish OEM targets for typography, 76 dp or larger touch areas, spacing and contrast across themes. Reserve critical defrost access through hardware or a minimal persistent control.
2. Connect trusted driving/parked state and platform UX restrictions. Appearance configuration, file browsing, diagnostics and demonstration controls should be parked-only where required. Do not use mock gear state as a safety signal.
3. Replace demo commands and confirmations with vehicle APIs, availability states, acknowledgments, failure handling and OEM-approved interlocks. Navigation needs a real routing provider; output information currently describes Android device connections.
4. Validate tasks in a simulator and actual vehicle: glance time, error rate, vibration, day/night legibility, accessibility and safe recovery. Test cold start, audio focus, interruptions, reconnects and sustained renderer performance.

## Verification scope

The requested verification does not package or install an APK. No new on-device layout or audio validation is claimed. The connected device is useful for measuring the current viewport and reviewing the previously installed design. New layouts require deployment before any new on-device visual/runtime result can be claimed. Compile, unit-test and lint results for this source revision are recorded in the accompanying verification JSON and Gradle log.

Final result after the landscape correction: source compilation passed with no Kotlin compiler warnings; all 143 JVM tests passed with zero failures, errors or skips. Android lint passed with zero errors and 12 warnings. The merged manifest confirms sensorLandscape, and every demo size uses the side navigation rail. `git diff --check` passes. No APK was created or installed.

Detailed results: [ergonomics-verification.json](ergonomics-verification.json). Build log: `app/build/reports/landscape-correction-build.log`. The successful offline command used source compilation, unit tests and lint, with one worker and a 768 MiB heap / 512 MiB metaspace. No persistent Gradle settings were changed.
