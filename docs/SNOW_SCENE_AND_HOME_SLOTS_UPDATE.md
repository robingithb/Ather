# Snow scene, coordinated floors and Home slots — 2 November 2026

This update supersedes the prior Arctic slate scenery and scene-edge feather.

## Vehicle

- Every theme now applies the exact same scenery tint to its panorama and ground. Ground materials are matte, non-metallic and have no clearcoat, reducing the blue reflections that made Midnight's floor disagree with its neutral backdrop. Floors still receive shadows; the car's materials, lighting and reflection probe are unchanged.
- Arctic uses two new image assets: white snow mountains and a matching snow ground texture. Both use a neutral white factor independent of the custom accent. The native scenery loads the two additional image buffers and binds them to Arctic's existing exclusive panorama/floor pair.
- The composited edge gradients have been removed. A 16 dp rounded viewport preserves the original imagery and sharp vehicle detail while giving the scene a clean boundary against the shell. No image or car blur is applied.

## Home

- Every page, including the library preview, keeps two equal-width widget slots at all landscape sizes. A lone widget retains its normal half-page width. Empty slots show a centered, transparent **Add widget** button that opens the existing library.
- On long press, the lifted widget animates to 72% scale over 160 ms. Scaling is anchored to the grabbed point so the miniature remains under the finger. The destination widget uses its full normal dimensions immediately after drop. Gesture cancellation, edge paging and saved ordering remain in place.
- The existing finite horizontal pages, page indicators, twelve-widget limit and responsive text/control sizing remain in use.

## Assets and verification

The built-in image generation tool created both snow assets. Exact prompts, saved resource paths and dimensions are in `assets/arctic-snow-generation.json`. Both images were visually inspected before integration.

The full unit suite includes regression checks for matching floor/backdrop tints across themes and custom accents, neutral Arctic snow colors, and preserving two slots on partial and empty pages. Final compile, test and lint results are in `snow-scene-home-verification.json`; logs are in `app/build/reports/snow-scene-home-compile-tests.log` and `app/build/reports/snow-scene-home-lint.log`.

No APK is created or installed. Updated native rendering, material appearance and drag animation still need on-device confirmation after deployment.
