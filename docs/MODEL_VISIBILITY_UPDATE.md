# Vehicle model visibility — 2 November 2026

Removed the Compose gradient mask above the native vehicle scene. That mask tinted all four edges with the application background, obscuring the car with black shading in dark themes and white haze in Arctic. The ready scene now displays directly across its entire model pane.

Arctic now uses an icy blue backdrop and a darker matte slate ground instead of two nearly white surfaces. The scenery palette keeps a substantial luminance separation between the backdrop and ground, including when the user customizes their accent color. The generated materials use the same colors during scene loading as after theme application.

The existing 50/50 layout, camera gestures, part controls, car materials, lighting, contact shadows and climate animation remain in place. Loading and failure messages still appear when required.

Verification results are recorded in `model-visibility-verification.json`. The connected Samsung demo was inspected to confirm the existing dark edge mask; it was returned to Home afterward. No APK is created or installed, so the updated rendering still needs visual confirmation after deployment.
