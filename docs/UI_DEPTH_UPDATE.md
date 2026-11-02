# 2.5D controls and surfaces — 2 November 2026

The shared UI now uses raised faces with native elevation shadows, a restrained vertical face gradient, a fine upper highlight and a darker lower bevel. Buttons depress by 0.8 dp and reduce their shadow over 90 ms while pressed. Disabled controls have no elevation and use quiet neutral faces. No padding, minimum touch size or layout dimensions are added by the effect.

## Coverage

- Shared page actions, icon buttons and dashboard cards, Home transport controls, widget-library choices and climate widget steppers.
- Navigation tabs with restrained elevation inside the existing rail.
- Vehicle action cards and grouped/window panels.
- Climate command buttons, temperature/fan steppers, temperature panels, flow/seat/air tabs, seat controls and toggles.
- Media transport, the prominent play control, page actions/tabs, playback options, sources and pagination controls.
- Settings slider panels, switches and raised thumbs, theme choices, accent choices and dimensional color swatches.

Cards generally use 2–3 dp elevation, ordinary actions 4–5 dp, navigation 1.5–4 dp and prominent play controls 7–8 dp. The same pressed-state interaction source drives styling and the native clickable/selectable/toggleable implementation, preserving button, switch, tab and radio semantics. Animations follow Compose's system motion-duration scale and run only during interaction.

## Themes and fitting

Face gradients use the existing theme palette. Standard label surfaces are adjusted toward the theme's neutral surface when needed to keep primary, secondary and accent text readable, including Arctic with custom accents. Fine highlight strokes are separate from the text face. Color swatches retain their chosen hue with subtle material shading.

All landscape sizes, finite page geometry, the exact Vehicle split, the original car scene and climate animation remain in place. Home still uses two slots per page, transparent Add widget slots and its existing 72% drag miniature. No scrolling screen or bitmap-based blur effect was introduced.

The Home pager intentionally clips drawing at its viewport boundary; exterior widget shadows can be cropped there, while the over-content bevel remains visible. This preserves drag bounds and current fitting. The navigation rail similarly uses low elevation within its existing compact gaps.

## Verification

New contrast tests sample the entire button gradient for all four themes and white, black, red and cyan custom accents. They cover primary/secondary/accent label inks, neutral Midnight faces and disabled-state color behavior. A failing Arctic custom-black-accent case was corrected by enforcing contrast on the new faces.

Final results are in `ui-depth-verification.json`; logs are in `app/build/reports/ui-depth-compile-tests.log` and `app/build/reports/ui-depth-lint.log`. No APK is created or installed. Updated depth appearance, shadow clipping on the actual display, and physical pressed-state feedback require device confirmation after deployment.
