# Vehicle controls and rendering update — 29 October 2026

All six requested fixes are implemented in the native Kotlin/Filament app.

| Request | Result |
| --- | --- |
| Headlights at 50% | Headlamp and DRL emission gain changes from 14,400 to 7,200; native headlamp spot intensity changes from 960 to 480. |
| Remove interior shadows | Six interior groups containing 67,902 triangles no longer cast or receive shadows. Drivetrain, steering wheel and dashboard keys are excluded too. Exterior parts retain floor shadows. |
| Visible reverse white lamps | The powered white emitters now use the actual exterior reverse lens surfaces. The opaque red housing had covered all sampled previous emitter surfaces. Both lamps illuminate in Reverse and turn off in Park. |
| Home Park/Charging cards | Selection comes directly from shared gear and charging state. These two cards are read-only; unavailable telemetry cannot appear active. |
| Charging transparency | The model reaches 70% transparency (30% remaining opacity). Battery and indicator visibility remain intact. Exiting charging immediately restores normal depth-writing materials. |
| Repeating green charge bar | A cached native quad fills from zero to actual charge over 2.2 seconds, holds for 0.8 seconds, and repeats. The numeric state of charge does not animate. Reduced motion displays the actual charge without repeating. |

The existing 2x MSAA plus FXAA configuration is retained. The battery fill updates only its cached transform; it adds no textures, lights or per-frame scene traversal.

## Verification

`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`

- Gradle: **BUILD SUCCESSFUL**, 1 minute 47 seconds.
- JVM tests: **82 passed**, zero failures, errors or skips.
- Lint: **zero errors**, nine existing warnings.
- Tracked whitespace check: clean.
- The APK contains the exact current prepared GLB. All 107 modelpoint identities resolve. Original source GLBs are unchanged.
- Installed and checked on the connected Samsung SM_G990E running Android 16, preserving app data. No emulator was used.
- Verified white reverse lights from the same rear camera in Reverse and Park, the reduced headlights, charging transparency and repeating battery sweep at the displayed 87% charge.
- Verified Home selections for charging + Park, idle Park, Reverse, Drive and charging restored. Both automatic cards have no click action.
- A settled charging presentation sample measured **30.08 fps**, median **33.24 ms**, 95th percentile **33.55 ms** across 126 frames. This is a short check, not sustained performance profiling.

The UI protocol returned the tested demo controls to Park, charging on, charge port open, headlights off and stored turn signal off. USB ADB disconnected after the visual checks and timing sample, so the final independent preference/logcat readback was unavailable. No logs were cleared and no app data was reset.

Detailed results: `docs/vehicle-controls-verification.json`. Model geometry findings: `docs/generic-suv-controls-diagnostics.json`. Historical reports describe earlier builds.
