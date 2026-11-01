# Home gestures and scenic vehicle stage — 2 November 2026

## Vehicle scenery

Arctic now applies the existing mountain panorama and stone-ground images through the same native 3D scenery pipeline as the other themes. It uses neutral versions of those textures with the approved icy-blue backdrop and slate-ground palette. Its ground retains the matte material. Exactly one panorama and floor pair is visible in every theme.

The scene has a narrow feather into the surrounding application background on all four edges. Feather width is capped at 12 dp and 4% of the shorter stage dimension. The broad gradients that previously dimmed the vehicle have not returned; the central scene stays unobscured. This is a soft compositing edge, with no blur filter on the car or its textures.

The 50/50 layout, camera interactions, vehicle materials, lighting, reflection environment, and contact shadows remain in place.

## Home gestures

- Swipe horizontally between the existing finite widget pages. A small page indicator replaces the visible Previous/Next buttons.
- Touch and hold any widget, including its content area, to lift it with haptic feedback. Move over the desired card position and release to place it.
- Hold near the left or right edge for 650 ms to turn a page while carrying the widget. Continued edge dwelling can cross several pages. The first/last page bounds do not wrap.
- The lifted card stays under the finger as pages change; the destination cell is outlined. Only releasing over the widget area commits the reordered list to the existing preferences flow.
- Back, pointer cancellation, additional fingers, or releasing outside the allowed area cancel the move. Consuming the held gesture before child controls receive release prevents accidental widget-button activation.
- Screen-reader custom actions support moving earlier/later and to adjacent pages. Widget controls still accept ordinary taps, and swipe paging remains separate from long-press dragging.
- The widget library also supports swiping between its two pages without Previous/Next buttons. Editing retains Remove; accessible actions and dragging replace its visual reorder arrows.

The number of widgets per page still adapts to the available landscape size. Resizing or opening the library keeps the anchored widget on the corresponding page. No vertically scrolling screen was introduced. Pages are populated automatically as widgets are added, up to the existing twelve-widget limit.

## Verification

Unit coverage exercises edge activation and page bounds, every cross-page drop destination for capacities 1/2/4 and layouts of 2–12 widgets, partial-page clamping, identity preservation, and persistence round trips. Existing layout, preferences, and scenery-palette tests remain part of the complete suite.

Final compilation, unit-test, and lint results are recorded in `scenery-widgets-verification.json`. Logs are in `app/build/reports/scenery-widgets-compile-tests.log` and `app/build/reports/scenery-widgets-lint.log`. No APK is generated or installed. Updated scene appearance, touch gesture arbitration, edge-turn timing, and accessibility interactions still require device validation after deployment.
