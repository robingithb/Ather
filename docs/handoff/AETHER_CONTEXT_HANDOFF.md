# AETHER HMI — conversation and engineering handoff

Exported 27 October 2026, Asia/Kolkata. Source chat: “Generate AETHER HMI system prompt” (01a14117-fa52-7270-9736-39f12e4cc0d9).

## Read this first

The user's latest request is to export this conversation to HTML for another person or AI. Do not interpret this export request as completion of the outstanding app fixes.

The last verified APK is **v0.5.1, version code 8**. It was built and installed on a physical Samsung phone on 26 October 2026. Its 51 JVM tests passed; lint had zero errors and nine warnings. The user subsequently reported seven more defects/changes. Those latest changes are **unfinished**. Some source files were partially edited before a usage-limit failure. The current workspace is therefore newer than the verified APK and is not known to compile.

**Concrete current blocker:** `VehicleScenery.kt` now references `R.drawable.vehicle_ground`, but `app/src/main/res/drawable-nodpi/vehicle_ground.png` does not exist. A wet-ground texture was planned, but no image-generation call completed. Do not present the existing v0.5.1 APK or its test report as proof that the latest seven fixes work.

This handoff includes the available public user/assistant messages, additional user requests visible in the conversation context, design references, current implementation notes, verification history, selected source snapshots, and pending work. The history API returned several empty/partial turns; the supplemental requests preserve the requirements that were missing from that retrieval. It is a context-preserving handoff, not a bit-for-bit export of every tool event. Internal reasoning, system/developer instructions, and unrelated chats are excluded.

## Latest user request — still outstanding

The user supplied `Screenshot from 2026-10-26 23-26-35.png` as the desired ground and `Screenshot from 2026-10-26 23-27-12.png` as current output. The exact request appears in the transcript. Acceptance requirements:

I need to change the current model to another updated model named GenericSUV_new.glb , replace this with current model file.
1. Replace the flat, poor-looking ground with wet, textured stone/road like the reference: dark blue surface, warm reflected highlights, and a bright blue circular platform.
2. Make the vehicle's shadow clearly visible on that ground. The existing contact approximation was not acceptable to the user.
3. Identify the actual headlight, tail/brake, reverse, front/rear indicator and mirror-indicator meshes. Correct the light appearance and indicator blinking, using the proper parts.
4. Correct Lights-panel controls and status. White reverse lights must illuminate only in Reverse. **Park must blink the indicators**, including mirror indicators. This is an explicit app/demo requirement from the user.
5. Outside Sunroof, dragging/orbiting must be horizontal only: change yaw; lock pitch/vertical movement. The user's phrase “x axis movement; lock the y axis” refers to screen interaction, not rotating the vehicle around its physical X axis. Sunroof keeps its special elevated view.
6. Remove the previous/next, minus/plus and camera/reset button strip above “Drag to rotate · Pinch to zoom”. Keep the instruction text and direct gestures.
7. If a door or other panel is open, or a vehicle problem exists, reflect it in Home's Vehicle card and replace “All systems good” with the corresponding message. The Vehicle-page header should also be truthful.
8.

The user said the physical ADB device was connected and asked for working verification. Do not use the laptop emulator.

## Current partial changes after the last verified build

- `domain/VehicleAttention.kt` exists. It derives severity and issues from the shared snapshot: unavailable vehicle/controls, explicit service attention, open parts, and open windows. An open charge port during active charging is treated as expected operation. It does not create a second stored health state.
- `ui/components/VehicleStatus.kt` and `res/values/vehicle_status_strings.xml` exist. They provide status text, colors and a warning/check badge.
- `feature/home/HomeScreen.kt` and `feature/vehicle/VehicleScreen.kt` reference that shared attention presentation. These edits have **not** been built or tested in the latest attempt. No `VehicleAttention` unit-test file was found at export time.
- `renderer/VehicleScenery.kt` was partly updated for an external ground PNG, wet clearcoat material, layered ring and shadow-receiving floor. The PNG is missing. Native casting/key-light/view shadow settings still need renderer integration and physical verification.
- `VehicleControls.kt` does **not** yet contain `effectiveTurnSignal`. `VehicleLampPolicy.kt` does **not** yet contain the proposed `isIndicatorNode` / `emissionGain` helpers. The old Park/manual-signal behavior remains.
- `FilamentVehicleRenderer.kt` still changes pitch from vertical drag; the old camera-button strip remains in `VehicleModelStage.kt`.
- The planned new ground image was never generated. There is no final new prompt/output asset to reuse.
- The last attempt stopped when automatic approval review could not complete an ADB check because the account hit a usage limit. That ADB action did not execute. Other work also stopped. This was not a determination that ADB testing was unsafe. No latest-fix build, new APK, installation, or device verification was completed.

## Suggested continuation plan

Inspect the actual files first; they contain partial edits. Preserve the substantial uncommitted work. Do not reset or clean the checkout.

1. Finish or safely reconcile the missing ground asset and `VehicleScenery.Data` changes. Keep the ground/ring/scenery in the native scene and preserve shared-camera synchronization.
2. Add visible shadows: configure the main light to cast shadows, vehicle solid renderables to cast them, and the floor to receive them. Scenery/panorama and decorative ring must not cast giant shadows. Verify exact APIs in pinned Filament 1.69.5. Calibrate map bounds, bias and resolution on the phone; retain a strong contact shadow if useful.
3. Lock pitch during drag for every focus except Sunroof; keep accepted yaw presets and zoom limits. Remove the button strip and reclaim its unused layout inset, preserving the hint text.
4. Implement coherent Park-hazard and Reverse rules in domain, UI and renderer. A proposed contract was agreed during the failed attempt: `effectiveTurnSignal = unavailable ? Off : gear == Park ? Hazard : turnSignal`. In Park, show effective Hazard and disable manual signal choices with a clear hint; preserve the stored manual choice for Drive/Reverse. `withTurnSignal` in Park would be a no-op. This contract is a plan, **not yet implemented**.
5. Use the effective signal for lamp visibility, blink-clock resets **and render-loop keepalive**. Updating only the visibility policy can leave Park hazards frozen when stored `turnSignal` is Off.
6. The latest agreed plan allows actual Park-hazard LEDs to stay visible during battery x-ray too. Preserve their real alpha while ghosting the rest of the body. The exact amber LED material is shared between front/fender/mirror/rear segments, so material-instance caching must not make the last visited node accidentally control all copies.
7. Calibrate LED emission on the actual phone. Proposed, untested multipliers were amber 64, white/red 48, ambient 8 and charge ring 12. Do not assume these values are visually correct; inspect a recording and the actual illuminated geometry. Preserve off housings/lenses without making them look powered.
8. Finish and test the shared attention helper: individual and multiple open panels, window state, unavailable telemetry, service attention, active charging exception, and clearing the notice after closing the problem. Verify Home and Vehicle agree after navigation/restart.
9. Build, run relevant JVM tests/lint, install on the explicit physical serial, record several indicator cycles including mirrors, and test Reverse versus Park. Verify pitch lock, pinch zoom, removed buttons, shadows and Home warnings. Update version/report only with actual results.

## Non-negotiable project context and preferences

- Native Android application, Kotlin + Jetpack Compose. Single app module/APK and single application process.
- Mobile landscape simulating an infotainment head unit. Keep the common five-entry rail: **Home, Vehicle, Climate, Media, Settings**. Some reference artwork contains an extra Drive tab; the user's explicit five-tab instruction wins.
- Development laptop has 8 GB RAM. Keep Gradle light: two workers, parallel builds disabled, Kotlin in-process, bounded heap/metaspace. Do not run heavy builds and an emulator together; current instruction is no emulator at all.
- Production-minded separation: Domain, Data, UI and Service. MVI/MVVM, Coroutines and Flow; hoisted state and efficient drawing/recomposition.
- Default data is an explicitly identified mock repository. Time, connectivity and real selected audio use runtime sources. Vehicle/HVAC values come from repository state/configuration, not scattered UI literals. A future AAOS/CarPropertyManager adapter remains planned; no real vehicle actuation is claimed.
- “No hardcoded values” means no fake fixed live readings/song progress/greeting; design dimensions/colors and declared demo configuration still exist. Demo-route labels and telemetry provenance must remain honest.
- Preserve actual Media3 playback/session across tab changes. Do not invent an active song or progress. Queue/URI restoration must handle revoked access honestly.
- Keep source GLBs and user images untouched; prepared model derivatives are separate bundled files.
- Iterate incrementally. Use supplied design images; the user prefers faithful layout and will accept resizing other elements to fit.
- Earlier Home/Climate phases used disposable emulator checks. The user later explicitly prohibited emulator testing. Vehicle work used offline checks and, once authorized, the physical Samsung phone. Do not repeat those historical emulator checks now.

## Workspace, build and device

Repository/application root: `/home/robin/AndroidStudioProjects/AetherHMI`

Task working directory: `/home/robin/AndroidStudioProjects`

Package: `com.aether.hmi`; launcher: `com.aether.hmi/.MainActivity`.

Current configured version: 0.5.1 / code 8. minSdk 26; compile/target SDK 37; Java 17 bytecode. ABIs arm64-v8a and x86_64.

Pinned dependencies: Filament + gltfio 1.69.5; Compose BOM 2026.02.01; Kotlin 2.2.10; Media3 1.8.0; Coroutines 1.9.0; lifecycle 2.9.4. Gradle wrapper 9.6.0. Check the repository files for exact plugin configuration rather than upgrading dependencies as part of visual fixes.

Gradle: `-Xmx1280m -XX:MaxMetaspaceSize=768m`; workers 2; parallel false; caching/configuration cache enabled; Kotlin in-process.

Build/check command:

```sh
cd /home/robin/AndroidStudioProjects/AetherHMI
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

APK path: `/home/robin/AndroidStudioProjects/AetherHMI/app/build/outputs/apk/debug/app-debug.apk`

Physical target previously verified: **Samsung SM_G990E**, serial **RZCT902Y60B**, Android 16, Mali-G78, Filament OpenGL ES 3.2. Availability must be checked again; the export does not establish a live connection.

```sh
/home/robin/Android/Sdk/platform-tools/adb devices -l
/home/robin/Android/Sdk/platform-tools/adb -s RZCT902Y60B install -r app/build/outputs/apk/debug/app-debug.apk
/home/robin/Android/Sdk/platform-tools/adb -s RZCT902Y60B shell am start -n com.aether.hmi/.MainActivity
```

Only target the physical device; do not start or install into an emulator. Preserve user app data; no `pm clear` or uninstall was authorized for these fixes. Use current UI hierarchy/accessibility labels for interaction because scroll positions make stale coordinates unreliable. Keep logs scoped to the application's PID, and restore temporary test state afterward.

Historical v0.5.1 APK size: 48,246,490 bytes (about 46.01 MiB), v2 signature verified. APK SHA-256: `7bc0e55080591b1282903545833dbd0f818f43079d94bff66652abf2d8d32bcb`. The report snapshot is included separately. These numbers describe that verified binary, not the partially changed workspace.

## Implementation and design history

### Phase 1: shell

The initial request was a comprehensive system/developer prompt for AETHER and then an app with only the five tabs and dark background. A new AetherHMI project was created. `docs/AETHER_SYSTEM_PROMPT.md` records the prompt. The shell has vector icons, landscape layout, time/status header and cyan selected states. The narrow left rail is shared across screens. Global design: deep navy/near-black background, translucent blue-black cards, thin blue borders, white/ice-blue text, cyan controls and green connected/healthy states.

### Home: v0.2.0 / v0.2.1

The user supplied `home.png` and hero artwork (`check/backcar`). Layout: scenic SUV hero and time-aware greeting at left; navigation preview and vehicle summary at right; smaller climate/media cards; seven HMI chips; persistent compact media/climate dock.

Greetings depend on local time (morning/afternoon/evening/night), not a fixed “Good morning”. Route ETA follows the clock/timezone and the preview is labeled Demo route. Temperatures, battery/range and vehicle status use repository values. The seven chips (Online, GPS Active, 5G, battery, Charging, Eco Mode, Parked) are independently selectable HMI visuals per the user's correction request, not proof of live phone/vehicle signals. Actual connectivity appears separately in the header.

Home's original “All systems good” read declared health only. The latest unfinished request requires open panels/problems to change this text; that is why the partial attention helper now exists.

Historical Home checks are documented in `docs/HOME_VALIDATION.md`: 8 JVM tests, real local audio selection/playback, clock/timezone greeting boundaries, HVAC persistence and compact layout. Those old emulator results are historical and do not authorize new emulator use.

### Climate: v0.3.0 / v0.3.1

The user supplied `climate_screen.png` and a cabin illustration containing painted blue airflow. Required controls: independent driver/passenger temperatures (16–28 in the declared demo), Face / Face + Feet / Feet / Defrost selection, fan bars, Auto, A/C, Sync, front/rear defrost, seat heating/cooling and air quality/filter cards, plus shared media bar.

Airflow is procedural Compose Canvas animation using `res/xml/climate_airflow_paths.xml`, not a video/GIF. Paths are anchored to actual vents, with moving cyan streaks, cached geometry and mode crossfade. Fan controls speed/intensity. It pauses offscreen/backgrounded, at zero fan, unavailable HVAC, or reduced motion.

The original image had baked beams; imagegen produced `climate_cabin_clean_v2.png` without those beams and restored upper vent openings. The source remains unchanged. This is already documented with its generation prompts in `docs/CLIMATE_IMPLEMENTATION.md`.

Latest Climate layout correction: common full-height five-tab rail; four equal-width, centered airflow cards across the available width; remove “Airflow / Flow paused” overlay. The four mode cards alone route the flow. The media-only bar stays to the right of the rail and supports collapse/expand.

Shared rules: Sync copies driver setpoint to passenger and locks passenger adjustment; manual fan disables Auto; airflow selection is exclusive; heat/cool are mutually exclusive per seat; persisted state and FIFO commands keep Home/Climate consistent.

### Vehicle: v0.4.0 (Jeep)

Initial asset: `JeepCompass.glb`. Added Filament rendering, loading progress/retry, orbit/pinch, door/trunk motion, six vehicle sections (Overview, Doors, Lights, Windows, Trunk, Charging), overview diagram and common HVAC dock. Source nodes allowed separate panels, but no authored animation clips. Offline build/JVM/lint checks passed. No emulator was used for Vehicle.

The first screenshots showed the small Jeep visually floating over a fixed background circle. The user replaced the model and requested richer functionality.

### Vehicle: v0.5.0 (GenericSUV)

Replaced Jeep with `GenericSUV.glb`. Added bonnet, sunroof and charge-port motion, turn signals/hazards, gear/reverse lights, brake and ambient lamps, plus battery x-ray while charging. Source metadata/audits established actual parts, hinges, lamps and bounds.

This revision incorrectly converted all non-battery materials to BLEND for x-ray. Even with alpha=1, normal opaque paint/wheels/interior lost expected depth/sorting behavior. It caused visibly missing parts. A screen-fixed Canvas panorama also moved independently of the projected ring. Do not reintroduce either approach.

### Vehicle: v0.5.1 — last verified baseline

Restored 52 source OPAQUE materials and 8 source BLEND materials in an explicit Normal material variant. Added 51 transparent clones for BatteryXray (111 total material definitions). Variant switching rebinds only materials, never rest transforms. The model's original geometry/textures and the user's source file remain unchanged.

Added a native panorama cylinder, floor, platform ring and contact-shadow geometry in the same Filament scene/camera. The still panorama is mirrored around a cylinder; it is not an authored 360-degree HDR environment. It samples the scenic part without the original painted ring. An off-axis projection renders the scene behind the full upper page row while fitting the car into the central content region. Screen-edge gradients blend it into the shell.

All preset pitches became 8° except Sunroof at 60°. Existing yaw bearings and zoom limits remained. A shortest-arc yaw/target transition refits every intermediate view to avoid cutting through the car during a preset change.

The supplied `pic5.png` now appears in the right-side Overview diagram. Its source nose points down, so Compose rotates it 180°; front doors are above rear doors, trunk below. PNG alpha and original bytes are preserved.

The verified v0.5.1 still has the gray/flat floor and camera-control strip criticized in the latest request. Its lights were **not** exhaustively calibrated or regressed on device. Passing its smoke checks does not mean the user's latest complaints are resolved.

## Model facts and rendering contract

Active original model: `/home/robin/Pictures/AETHER/GenericSUV.glb`, 13,420,712 bytes. Source SHA-256: `446c9f6291d4b53c0513b5a05bdb901ab3776167eb7c5a9db5868a9ac9557336`.

Prepared bundled asset: `app/src/main/assets/models/GenericSUV.glb`, 13,460,716 bytes. Bundled SHA-256: `8ed4b56ef7f7b0b83a709d59a93d6a1dc144061728c656c3194c0abaf8ec93a5`.

107 nodes, 104 meshes, 193 primitives, 305,703 vertices, 315,964 triangles, 3 punctual lights, no animation clips or skins. Y-up, front +Z, vehicle-left +X. Measured bounds approximately X ±1.01961, Y 0..1.66405, Z ±2.20492. Tires contact Y≈0.

Movable node → domain part → local motion:

- `Door_FL` → FrontLeftDoor → −68° about local Y.
- `Door_FR` → FrontRightDoor → +68° about local Y.
- `Door_BL` → RearLeftDoor → −68° about local Y.
- `Door_BR` → RearRightDoor → +68° about local Y.
- `Door_Trunk` → Trunk → +70° about local X.
- `BONNET` → Bonnet → −65° about local X.
- `SUNROOF` → Sunroof → translation (0, +0.045, −0.7) at fully open.
- `CHARGE_PORT` → ChargePort → +100° about local Y.

**Compose transforms as authoredRest × localMotion.** These nodes already have authored local hinge origins. Do not apply the older Jeep's raw-world hinge formula or transform-order patch to GenericSUV.

Preparation tool `tools/prepare_generic_suv.py` preserves source/BIN data and reparents 32 mirror/tailgate accessories while retaining their world rest transforms. Material variants map every primitive: Normal index 0, BatteryXray index 1. The old Jeep preparation/audit files remain historical; do not apply them to GenericSUV.

Mixed rear overlays span fixed quarter-panel and tailgate geometry: `REAR_BRAKE_ON/GLOW_ON`, `REAR_TAIL_ON`, `REAR_TURN_OFF_L/R`, and `REAR_TURN_L/R_ON_5` (plus matching glow 5). They remain body-fixed and are hidden while the trunk opens. Moving whole mixed meshes would incorrectly move fixed lamps. This limitation is documented; source geometry would need splitting for perfect continuity.

## Lamp identification and latest diagnostic findings

- Headlights: `HEADLAMP_L_ON`, `HEADLAMP_R_ON`; source native spots `HEADLAMP_L_LIGHT`, `HEADLAMP_R_LIGHT`. Housing/lens nodes `HEADLAMP_L/R` are distinct. `DRL_BAR` is the white daytime/running strip.
- Amber front/fender/mirror indicators: `FRONT_INDICATOR_L/R_ON`, `FENDER_INDICATOR_L/R_ON`, `MIRROR_INDICATOR_L/R_ON`.
- Amber rear indicators: `REAR_TURN_L_ON_1..6` and `REAR_TURN_R_ON_1..6`.
- White reverse lamps: `REAR_REVERSE_ON_L/R`, corresponding `REAR_REVERSE_OFF_L/R`; ON material index 49 is white emissive, OFF material 48 is gray and nonemissive.
- Red tails/brakes: `REAR_TAIL_ON`, `REAR_BRAKE_ON`, `HIGH_STOP_ON`, `HIGH_STOP_OFF`, and native `REAR_BRAKE_LIGHT`.
- Ambient: `AMBIENT_STRIP_ON_*`; charge rings: `CHARGE_PORT_RING_ON_BLUE/GREEN/WHITE` and OFF.
- All `*_GLOW_ON*` billboard nodes were hidden because they look like solid cards from some angles. Use actual LED geometry rather than re-enabling all giant glows.
- Shared amber LED material index 35 serves front/fender/mirror/rear ON meshes. Mirror strips are tiny: roughly 0.011 model units high, at Y≈1.185 and X≈±0.85..0.99. They need visible, calibrated LED emission, not a fabricated large panel.
- Some authored unpowered lenses are yellow/gray even with zero emission. A visible colored lens is not proof that reverse/indicator emission is on. Check actual ON mesh visibility and material emission, not only screenshot color.
- Current baseline `reverseLightsOn` is logically `available && gear == Reverse`; Park does not logically activate reverse. The user still reports incorrect appearance, so physical mapping/visibility/emission must be examined.
- Current blink cycle is 1.2 seconds (0.6 on/0.6 off). The renderer currently resets/keeps frames based on the stored `turnSignal`; it needs the new effective signal for automatic Park hazards.

## Key files

- `app/src/main/java/com/aether/hmi/domain/DashboardModels.kt`: snapshot/model contracts.
- `domain/VehicleControls.kt`: parts, windows, lights, gear, command transformations and charging interlocks.
- `domain/VehicleAttention.kt`: partial, unverified shared attention derivation.
- `data/MockVehicleRepository.kt`: declared demo data, persistence, shared state.
- `feature/home/DashboardViewModel.kt`: command dispatch/FIFO.
- `feature/home/HomeScreen.kt`: Home dashboard/Vehicle card.
- `feature/climate/ClimateScreen.kt`, `AirflowOverlay.kt`, `res/xml/climate_airflow_paths.xml`: Climate UI/flow.
- `feature/vehicle/VehicleScreen.kt`: six vehicle sections, controls, shared header and native stage placement.
- `feature/vehicle/VehicleComponents.kt`: surfaces/cards/top-view image/hotspots.
- `feature/vehicle/VehicleModelStage.kt`: Compose/TextureView bridge, progress/retry, gestures/accessibility and camera strip to remove.
- `feature/vehicle/VehicleSceneViewModel.kt`: Activity-scoped retained native scene.
- `feature/vehicle/VehicleSceneMath.kt`: authored local motion, fitting, presets, projection and orbit interpolation.
- `feature/vehicle/VehicleLampPolicy.kt`: pure LED visibility/timing policy needing the latest changes.
- `feature/vehicle/renderer/FilamentVehicleRenderer.kt`: engine/surface/assets, animation, camera, material variants, lights and render scheduling.
- `feature/vehicle/renderer/VehicleScenery.kt`: native scenery/floor/ring/contact geometry; partial missing-ground-texture changes.
- `feature/vehicle/renderer/VehicleEnvironment.kt`: low-resolution reflection cubemap from the LDR panorama.
- `ui/components/VehicleStatus.kt`: partial shared status presentation.
- `ui/shell/AetherApp.kt`: global navigation/state/action wiring.
- `app/src/main/assets/demo_vehicle.json`: explicit mock capabilities/scenario, not real sensors.
- `app/src/main/res/drawable-nodpi/vehicle_stage.png`: clean scenic panorama/old painted circle source.
- `app/src/main/res/drawable-nodpi/vehicle_overview_top.png`: byte-identical supplied pic5.
- `docs/VEHICLE_IMPLEMENTATION.md`, `docs/vehicle-build-verification.json`: historical verified v0.5.1 state.
- `docs/generic-suv-source-audit.json`, `docs/generic-suv-model-manifest.json`: actual model audit/material defaults/transforms/variants.
- `docs/HOME_VALIDATION.md`, `docs/CLIMATE_IMPLEMENTATION.md`: earlier-phase behavior and provenance.
- `docs/verification/vehicle-v0.5.1/`: physical-device evidence. This is baseline evidence, not latest-fix evidence.

## What to transfer separately

The HTML embeds the available design/reference images, public conversation and selected text/code snapshots. It does not embed the whole Git repository, Android SDK/caches, APK binary, GLB binaries or the MP4 recording. Local absolute paths identify original files but will not become accessible on another computer simply by opening this HTML.

For another engineer/agent to edit and verify the actual app, also share the AetherHMI checkout (including untracked files), the original GenericSUV source model, and any needed original media. Share `Screen_recording_20261026_222845.mp4` separately if motion analysis is needed. Do not rely on old `/tmp/codex-clipboard-*.png` attachments; they are no longer on disk. Their reference/specification content has been summarized in this handoff.

## Ready-to-paste continuation request

Continue the AETHER HMI Android project using this handoff as context. First inspect the current checkout and partial edits; do not reset it. The last verified baseline is v0.5.1/code8 with 51 passing JVM tests, but the latest seven requested fixes are not complete. The current source references a missing vehicle_ground PNG. Finish the wet textured floor and visible shadow, correct and verify all real lamp/indicator groups (Park hazards including mirrors; Reverse white lamps only), make non-Sunroof orbit horizontal-only, remove the camera button strip while retaining gesture text, and make Home/Vehicle status reflect open panels/problems. Preserve the common five-tab rail, single-process architecture, shared repository/media/HVAC state, normal versus x-ray material variants, accepted zoom limits and camera-synchronized scenery. Use only the authorized physical ADB phone, never the laptop emulator. Build and run relevant offline tests/lint, then verify on the phone and report actual results without claiming untested changes are complete.
