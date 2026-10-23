# Climate screen — 23 October 2026

Climate follows the supplied Climate references with the common landscape rail, dark blue surfaces and cyan selection states. It uses the existing application, repository and Media3 session. The same full-height five-tab rail is used on every page. Climate's media-only bar sits at the bottom of the content area to the right of that rail, supports collapse/expand, and keeps playback independent of navigation.

## UI revision — v0.3.1 (code 5)

- Keep Home, Vehicle, Climate, Media and Settings in the same full-height rail across pages; the Climate media bar no longer shortens or undercuts it.
- Center the four equal-width airflow cards across the complete content width, eliminating the unused right-hand gap.
- Remove the Airflow / Flow paused overlay and its pause state and strings. The four cards select the animated airflow route directly. Fan level and Android's reduced-motion setting continue to apply.

This revision builds successfully with `./gradlew --offline --no-daemon :app:assembleDebug`. The output is `app/build/outputs/apk/debug/app-debug.apk`, version 0.3.1 (code 5), for the user's manual testing. No tests or emulator runs were performed for this revision, as requested. Screenshots, video and `climate-qa.json` below are historical v0.3.0 artifacts and do not show these layout adjustments.

## Shared HVAC behavior

The configurable demo scenario is in `app/src/main/assets/demo_vehicle.json`. Numeric UI values come from `ClimateState`, including the temperature range/step, passenger setpoint, fan capability, seat-level capability, air quality and filter status. These are declared mock vehicle readings; the phone does not supply cabin sensors or actuators.

- Driver and passenger temperatures are independent with Sync off. Sync copies the driver setpoint to the passenger and locks passenger adjustments until Sync is disabled.
- Manual fan changes turn Auto off. Fan level zero removes the airflow layer.
- Face, Face + Feet, Feet and Defrost are one exclusive airflow selection.
- A/C and front/rear defrost are repository-backed toggles.
- Each seat has independent stepped heating/cooling controls. Enabling heating clears cooling for that seat, and vice versa. Levels cycle back to zero after the configured maximum.
- Climate preferences survive process restarts. Home and Climate observe the same state. Home's Seats quick control maps to driver seat heating.
- A FIFO command channel preserves the order of rapid commands before the repository performs disk writes. Pending/saved/error feedback is displayed in the shared header.

## Animation assets

`app/src/main/res/xml/climate_airflow_paths.xml` contains 19 normalized cubic paths: eight face-vent streams, six footwell streams and five windshield streams. `app/src/main/java/com/aether/hmi/feature/climate/AirflowOverlay.kt` renders moving cyan dash/streak phases along these paths with a soft trail and a short mode crossfade. Fan level controls speed and intensity. This is a procedural animation resource, rather than a GIF or video; no animation library, 3D engine or second process was added.

The image and paths use identical upward-biased Crop geometry, `BiasAlignment(0f, -0.36f)`, so the actual vents stay in view on wide landscape windows. Paths and drawing geometry are cached. The animation phase is observed by drawing rather than the whole screen, and stops when the screen leaves RESUMED, fan is off, HVAC is unavailable, or motion is disabled. Android's animation duration scale can produce stationary streams. There is no separate on-screen airflow pause control.

The final background is `app/src/main/res/drawable-nodpi/climate_cabin_clean_v2.png`. It was prepared with the built-in imagegen tool from the user's supplied `ChatGPT Image 16 Oct 2026, 00_33_.png`. Painted airflow had to be removed so inactive routes do not remain falsely visible. The original source remains untouched. Two upper vent openings were restored in a second edit to preserve the animation anchors.

### Built-in imagegen prompts

First edit:

> Use case: precise-object-edit. Asset type: clean background plate for a native Android automotive climate-control screen. Input image 1 is the edit target. Remove ONLY the existing illustrated blue airflow beams, streaks, rays and airborne particles that emerge from the air vents and spread over the center console, dashboard, door panels and footwells. Reconstruct the underlying dark cabin materials naturally. Keep all cabin geometry, air vent positions, dashboard contours, steering wheel, center console, seats, windshield, lake, mountains, camera viewpoint and framing unchanged. Keep the thin cyan ambient LED trim lines on the dashboard and doors. Keep the same panoramic 16:9 aspect ratio and crop, dark midnight-blue color palette and photographic quality. Do not add any new airflow, mist, smoke, people, text, controls or interface elements. The vents must remain at exactly the same normalized positions because separate animated paths will be drawn over this plate in the app. Produce one clean opaque background plate.

Final edit (image 1: clean plate; image 2: original geometry reference):

> Use case: precise-object-edit. Image 1 is the clean cabin plate to edit; image 2 is a geometry reference only. Make exactly one correction to image 1: restore the two small upper center dashboard air-vent openings visible in image 2, centered at approximately (710,295) and (970,295) in the 1672x941 frame, each a slim horizontal rounded grille about 65px wide by 16px high. They sit directly on the upper cyan dashboard trim, above the larger lower center vents. Use realistic dark-black grille openings with fine metallic vanes and a restrained blue trim matching the cabin. No emitted airflow or blue rays. Keep EVERYTHING else in image 1 unchanged, including dimensions, framing, steering wheel, landscape, console, ambient trim and lower vents. Do not add UI, labels, particles, fog, or decorative beams. Output one opaque clean background plate with these two functional-looking upper vents restored at the exact positions.

## Historical verification — v0.3.0

Build, unit tests and lint: `./gradlew --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.

Version 0.3.0 (code 4) builds successfully. Eight existing dashboard tests and eight new climate domain tests pass with no failures or skipped tests. They cover time/ETA behavior, unavailable state, configured setpoint limits/steps, Sync, fan limits/manual override, exclusive mode selection, and seat levels. Lint reports zero errors and three existing advisory warnings: Android large-screen orientation policy and two SharedPreferences KTX suggestions. The debug APK's v2 signature verifies.

The API 33 emulator interaction pass verifies:

- The complete Climate layout, five navigation destinations and media controls at 880 × 400 dp and 640 × 320 dp.
- Moving airflow, pause/resume and preservation of the pause choice across tab changes.
- Exclusive Face / Face + Feet / Feet / Defrost selection and the final selection winning after rapid taps.
- Independent zone adjustments, Sync copying/locking, and consistent Home summary/dock temperatures.
- Manual fan override of Auto, A/C and both defrosters, and mutually exclusive stepped seat heat/cooling.
- The shared media queue, collapse/expand controls, and HVAC settings surviving a real process restart.
- Android's reduced-motion setting producing stationary streams, with movement resuming when the normal animation scale is restored.
- Real local WAV playback continuing from Climate to Home and back, confirmed by Android MediaSession positions advancing from 45.476 to 54.466 to 63.442 seconds. Pause reports state 2, playback reports state 3, and exactly one app PID is present. The generated 90-second test WAV was selected through Android's document picker and is not bundled in the APK. See [playback capture](screenshots/climate-playback-test.png).

See [recorded interaction results](climate-qa.json), [regular landscape](screenshots/climate-landscape.png), [compact landscape](screenshots/climate-compact.png), [airflow demonstration](screenshots/climate-airflow.mp4) and the four `screenshots/climate-flow-*.png` mode captures. Controls scale down on compact phones to keep the requested complete layout visible; this is not a physical head-unit touch-target certification.

Verification used a disposable `AetherClimateQA` emulator, with hardware graphics and nonessential Google background apps/head-up banners disabled after fresh-emulator system stalls. No app crash or ANR was observed in the passing interaction suite. User devices and normal AVDs were not changed. The temporary emulator was shut down after verification.

The build retains two workers, disabled parallel projects, in-process Kotlin compilation and caching. The combined heap plus class metadata cap stays at 2 GB: 1280 MB heap and 768 MB metaspace. This replaces the original 1536/512 split after the larger Compose/lint pass exhausted metadata capacity. Emulator and build runs are kept separate on the 8 GB development machine.

AAOS hardware integration and real cabin sensors remain future adapter work; this screen uses the shared mock repository contract. Real sensor/actuator behavior and performance on a physical phone or head unit have not been validated.
