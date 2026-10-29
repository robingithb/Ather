# Vehicle lights and tyre update — 29 October 2026

All four requested refinements are implemented and checked on the physical Samsung SM_G990E.

| Change | Result |
| --- | --- |
| Charging transparency | 85% transparent, 15% remaining opacity. Battery and indicators retain their visibility, and the green sweep still fills only to actual charge. |
| White lamp glow | Headlamp/reverse/DRL gains are 6/4/2 respectively, reduced from 7,200/18,000/7,200. Native headlamp spots are 240. The larger DRL is calibrated separately to avoid the previous broad white cloud. The pinned gltfio shader bypasses camera exposure for emissive colour, so the former thousand-scale exposure compensation has been removed from these lamps. Global bloom, exposure and the accepted upper red stop glow remain unchanged. |
| Main rear lamps | Recessed tail and brake emitters were blocked in all sampled outward rays. Four real exterior red-lens regions now provide exclusive dark/tail/brake states. Brake supersedes tail; trunk lamps move with the tailgate, quarter-panel lamps stay fixed. Main rear materials are independent from the upper stop and use bounded, red emission factors to avoid orange/white clipping. |
| Tyres | Charcoal satin sidewalls and darker, rougher tread use the existing modelled grooves and normals. Rubber stays dielectric with no clearcoat. Silver wheel materials are unchanged. No added tyre triangles or texture samples; four extra draw primitives and 284,160 index bytes. |

## Verification

`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`

- **BUILD SUCCESSFUL**.
- **85 JVM tests passed**, zero failures, errors or skips.
- Lint: **zero errors**, 9 existing warnings.
- All 107 modelpoint identities resolve. Source GLBs, original BIN prefix, source textures and silver wheel materials are preserved; geometry/state contracts pass.
- The installed APK hash matches the verified build and contains the exact prepared model.
- Physical-phone checks covered headlight glow, rear tail/brake/Reverse combinations, all lamps off, open-tailgate attachment, tyre materials and the 85%-transparent charging view.
- Charging presentation sample: **30.07 fps**, median **33.26 ms**, p95 **49.91 ms** across 127 frames. This is a short sample, not sustained performance profiling.
- No fatal or renderer errors in the checked process log. Current demo preferences match the saved pre-test state. No app data was cleared; no emulator was used.

Prepared model SHA-256: `054acf26d43ef939022898aa9325fafa2f97224efe625c37dcfa10b5b6cbe6f2`.

Geometry evidence: `docs/generic-suv-main-rear-lamps-diagnostics.json`. Tyre material contract and cost: `docs/vehicle-tyre-materials-diagnostic.md`. Detailed build/device results: `docs/vehicle-lights-tyres-verification.json`. Earlier reports describe historical builds.
