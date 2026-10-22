# Home dashboard validation — 22 October 2026

Package: `com.aether.hmi`, version `0.2.1` (version code 3), minSdk 26, target/compile SDK 37. One app module, APK and process. The debug APK is approximately 22 MB and its APK Signature Scheme v2 signature verifies.

## Automated checks

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

Eight JVM tests passed, with no skipped tests or failures:

- All greeting period boundaries, including midnight.
- Different local greetings for the same instant in different timezones.
- ETA rolling into the next date and advancing with current time.
- Elapsed ETA duration through a timezone offset change.
- Repository-configured temperature resolution, rather than a fixed UI increment.
- Rapid setpoint changes clamped to the configured supported limits.
- No fabricated telemetry, diagnostic pass or route when the source is unavailable.
- No temperature change from a disconnected climate adapter.

Lint has no errors. Four advisory warnings concern the pinned Gradle version, Android's large-screen orientation policy, and two suggestions to use a SharedPreferences KTX convenience extension. The Media3 service's exported-service warning is narrowly suppressed because its session connection callback explicitly accepts only the app and trusted controllers; the opt-in API is marked at that callback.

## Emulator checks

Earlier behavior checks used Android 16/API 36 with version 0.2.0. The visual revision and the card/dock checks below use version 0.2.1. A separate temporary API 33 emulator was used for the final visual pass. Emulator and Gradle runs were kept separate to reduce peak memory use on the 8 GB machine. Test audio was a generated local WAV selected through the Android document picker; no test song is bundled in the APK.

- Live greetings changed to morning, afternoon, evening and night when the emulator timezone changed to Los Angeles, London, Kolkata and Auckland. The emulator was restored to India time afterward.
- The Home climate summary and persistent dock both changed from the same repository update. The supported increment was applied, and the saved setpoint and A/C state survived stopping and restarting the app.
- Actual selected audio supplied title, duration and advancing progress. Playback stayed active while moving between Home and Vehicle. Pause was confirmed in Android's MediaSession state with the actual file, a real playback position and no player error.
- Exactly one application PID was present during playback. The service has no separate process attribute.
- The selected queue and metadata returned after restarting the app, with a Play action and no automatic playback.
- The revised Home was inspected at 880 × 400 dp and 640 × 320 dp on the API 33 emulator. All seven HMI cards and all dock controls remain visible. Every card independently toggled its cyan highlight on and off at both sizes; selection survived tab changes and resizing. The compact dock successfully adjusted the shared Home/dock temperature, toggled front defrost and opened the queue. Compact controls scale below 48 dp to honor the requested full-layout fit. See `home-fix-qa.json` for the recorded interaction checks.
- Fullscreen bars are reapplied when the activity regains window focus, including after the document picker or rotation.
- In the earlier 0.2.0 pass on API 33, UIAutomator clipped the reported lower-control rectangles to its visible-window frame. A direct tap at the bottom of the actual 48 dp Auto control successfully changed its checked state, confirming the physical touch area works beyond that reported rectangle. This is a limitation of the automated bounds measurement on that emulator, not a failed touch interaction.
- Both temporary emulators were shut down after verification.

## Captures

- [Regular landscape Home](screenshots/home-landscape.png)
- [Compact landscape Home](screenshots/home-compact.png)
- [GPS card highlight](screenshots/home-highlighted.png)
- [Playback check with actual selected test audio](screenshots/home-playback-test.png)

## Scope

Vehicle, route and HVAC values are deliberately supplied by the declared mock configuration in `app/src/main/assets/demo_vehicle.json`. They are not physical vehicle readings. System connectivity and time, plus audio metadata and transport state, use actual runtime sources. The seven HMI cards reproduce the supplied design and only change local visual selection; GPS Active/5G/Online/Charging/Eco Mode/Parked labels are not live sensor assertions. Battery text remains repository-driven. Actual connectivity is shown separately in the header. The scenic album illustration is a fallback visual; no song metadata, playback position or duration is invented.

AAOS hardware integration, real navigation, Filament, and the complete Vehicle/Climate/Media/Settings designs remain future phases. No physical head unit has been tested. Document grants depend on provider support; a revoked or temporary grant can make a restored audio item unavailable, in which case playback reports an error rather than a fake state.
