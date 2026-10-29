# Rear revert and headlight glow — 30 October 2026

The main rear-light changes from the previous update are reverted. The accepted upper brake-light glow remains unchanged. Headlight projectors now use stronger native bloom, and reverse emission is 20% higher.

| Change | Result |
| --- | --- |
| Main rear appearance | Removed the full-cluster red lens remap, its auxiliary state nodes and independent calibrated materials. Exact comparison against the earlier rear model found zero differences in geometry, materials, variants or movement parents. Original tail/brake policy and trunk suppression are restored. |
| Upper brake light | Original material and gain 18,000 remain unchanged. |
| Headlight glow | Projector gain changes from 6 to 128, using the same native bloom path as the upper brake light. DRL stays at 2 so the wide front strip does not become a white cloud. |
| Reverse glow | Gain changes from 4 to 4.8, interpreting “20 glowness” as a 20% increase. Reverse-only activation and exterior lens geometry remain intact. |
| Retained changes | Tyre sidewall/tread materials, silver wheels, 85% charging transparency, green battery sweep, interior shadow optimization and existing anti-aliasing are preserved. |

## Verification

`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`

- **BUILD SUCCESSFUL**, 1m 47s.
- **86 JVM tests passed**, zero failures, errors or skips.
- Lint: **zero errors**, 9 existing warnings.
- All 107 modelpoint names resolve. Original source GLBs and the entire accepted model BIN are unchanged.
- The APK contains the exact verified model, and its hash matches the installed Samsung build.
- Physical Samsung SM_G990E checks confirmed headlight On/Off glow, the reverted main rear appearance with the accepted upper stop glow, and Reverse versus Drive white-lamp behavior.
- The checked process log contains no fatal or renderer errors. Demo preferences match the latest saved pre-test baseline, including the user's open sunroof and current HVAC settings. App data was preserved; no emulator was used.

Prepared model SHA-256: `f3c1124e8788f78ff0085d3cddcec3e2d5c2f3f5e3f38f5204738bf303b58d5d`.

Exact selective-revert evidence: `docs/generic-suv-main-rear-revert-diagnostics.json`. Detailed current results: `docs/vehicle-glow-revert-verification.json`. Previous reports describe historical revisions.
