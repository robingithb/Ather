# Vehicle screen — GenericSUV v0.5.1

Historical v0.5.1 implementation record. Current source, model preparation and verification are described in [VEHICLE_REFERENCE_UPDATE.md](VEHICLE_REFERENCE_UPDATE.md); the APK/device results in this document have not been rerun for the 27 October update.

Updated 26 October 2026. Version 0.5.1 (code 8) refines the supplied GenericSUV rendering, camera, scene integration and Overview image. It remains one APK and one application process, sharing the five-tab shell, repository-backed telemetry/HVAC, and Media3 session. The build and physical-device smoke checks passed on the connected Samsung SM_G990E; detailed results and limits are recorded below.

## Model preparation and rendering

The bundled asset is `app/src/main/assets/models/GenericSUV.glb`: 13,460,716 bytes, 107 nodes, 104 meshes, 315,964 triangles, 111 materials and three punctual lights. It contains no animation clips or skins. The source model is Y-up, front +Z, left +X, with tyre contact at Y=0.

`tools/prepare_generic_suv.py` preserves the user's original file and the binary geometry/textures. It retains authored part origins and reparents mirrors, mirror indicators and appropriate rear accessories to their physical panels while preserving every world rest transform. Source/bundled hashes, counts, transforms and checks are in `generic-suv-model-manifest.json`; the original audit is `generic-suv-source-audit.json`.

The model now has explicit `Normal` and `BatteryXray` glTF material variants. Normal preserves all 60 source material definitions: 52 OPAQUE and eight BLEND. The preparation tool adds 51 BLEND clones for non-battery solid materials, giving 111 definitions in total. The battery keeps its opaque material. Original paint, glass, texture and emission factors are preserved. This replaces v0.5.0's conversion of every non-battery material to BLEND, which could produce incorrect depth/sorting in the normal vehicle view. The renderer rebinds material instances after a variant switch without recapturing or resetting moving-part rest transforms.

Filament/gltfio 1.69.5 renders the actual model through a transparent TextureView. Loading reads the GLB off the main thread, uploads resources asynchronously and reports progress; Ready requires completed resource loading and a rendered frame. Failure offers Retry. OpenGL ES 3.0 is required; unavailable 3D does not remove the repository controls.

The Activity-scoped `VehicleSceneViewModel` retains the native scene across tab changes and Activity recreation. Detaching clears the old surface, touch listener and callback; reattachment reuses the model. Backgrounding pauses frames. Retry and owner destruction release native resources. Frames are requested for loading, camera/part/x-ray transitions, gestures, picking and active indicators, capped at approximately 30 FPS. Static scenes stop requesting frames. This is a scheduling target, not a measured device performance result.

The scene uses restrained key/fill lighting and a small cubemap derived from the supplied LDR panorama. This approximates environmental reflection; it is not an HDR capture. Bloom, SSAO, dynamic shadows and MSAA remain disabled.

## Motion, camera and platform

The model's authored node origins support local rotations composed as `currentLocal = authoredRest × localMotion`. There are no pre-rendered animation files or glTF clips. Runtime motion controls:

- Four doors: outward rotations up to 68°, carrying the reparented mirrors/accessories.
- Tailgate: upward rotation up to 70°.
- Bonnet: upward rotation up to 65°.
- Charge flap: outward rotation up to 100°.
- Sunroof: rearward slide of 0.7 model units with a 0.045-unit lift.

Exponential frame-time transitions allow reversals during motion. Reduced-motion settings snap to the target. GPU picking maps visible panels and their reparented accessories to the same actions as the controls; named accessibility actions provide another route.

`VehicleSceneMath.kt` computes focus presets for Overview, Doors, Lights, Windows, Trunk, Charging, Bonnet and Sunroof. Overview, Doors, Lights, Windows, Trunk, Charging and Bonnet now start at a level 8° pitch; Sunroof retains a 60° view to expose the roof. Existing preset yaw values and zoom limits are retained. Focus changes interpolate shortest-arc bearings and target positions, refitting every intermediate angle so the camera does not cut through the model; drag, pinch and explicit orbit/zoom/reset controls remain available. Aspect-aware directional fitting includes relevant opening arcs and constrains pitch/zoom. Numerical projection tests cover viewport sizes, orbit angles and moving bounds; they do not establish final GPU appearance.

`renderer/VehicleScenery.kt` builds native glTF geometry for a panoramic cylinder, floor, platform ring and soft contact-shadow meshes. These objects share the vehicle's Filament camera, so orbiting moves the vehicle and scenery together. The old Canvas-based `VehicleGround.kt` implementation has been removed. The contact shadows are authored translucent geometry approximations, not dynamic shadow-map results.

The native stage spans the full upper row behind the foreground cards. An off-axis camera projection fits the car into the central model area while the panorama and floor remain continuous behind the panels. The foreground center is transparent for vehicle gestures; the five global navigation tabs, six vehicle sections and shared HVAC strip remain in place. The supplied still panorama is mirrored around the cylinder as a lightweight approximation, rather than an authored 360° environment. It is sampled without its fixed, painted platform circle; the visible ring is native scene geometry.

Overview uses the supplied transparent `pic5.png`, copied unchanged to `app/src/main/res/drawable-nodpi/vehicle_overview_top.png`. Compose rotates the image 180° because the source nose faces downward. Front-door controls therefore remain above the rear-door controls, left/right mappings stay consistent, and the trunk control remains at the bottom. The diagram's highlights and accessibility labels still reflect repository state. Battery/range span the full right panel above the three status tiles and the supplied top-view image.

## Controls, lamps and charging

Six sections provide Overview, Doors, Lights, Windows, Trunk and Charging. Door controls also expose bonnet and sunroof. State persists alongside existing climate preferences. One FIFO command queue and repository mutex preserve command order and update shared Home data.

Opening a door, tailgate or bonnet unlocks the mock vehicle. Locking closes those six panels while preserving sunroof and charge-flap position. Headlights support Off/Auto/On; Auto follows the configured demo scenario, not a phone light sensor. Other controls provide left/right/hazard indicators, Park/Drive/Reverse, brake lamps, ambient strips, parking brake and four window percentages. Reverse lamps derive from Reverse gear; selecting Park does not enable hazards.

`VehicleLampPolicy.kt` selects actual authored LED meshes and meaningful inverse OFF lenses. Headlamps/DRL/tail LEDs follow headlights; brake, reverse, warm ambient strips and turn segments follow their independent states. Indicators use a 1.2-second cycle: 0.6 seconds on, 0.6 off. All `*_GLOW_ON*` billboard meshes are hidden to avoid solid-looking glow cards at other angles. Lamp RGB/emission factors retain the supplied appearance; native lamp origins/directions are preserved with runtime intensities.

Mock charging requires Park, opens the charge flap, and prevents closing it or selecting Drive/Reverse until charging stops. Stopping closes the flap. Charging selects `BatteryXray` and smoothly ghosts every non-battery material—including inverter, motors and shafts—to 3.5% of its original alpha. Only the real battery mesh remains opaque, with a restrained cyan emission cue. Stopping fades back to the original RGBA/emission and returns to `Normal`, restoring the original opaque/depth-writing materials. Exterior lighting overlays are suppressed during x-ray except a subtle blue charge ring; an idle open port uses white. The green ring is unused. This illustrates mock state without inventing charge progress, battery gain or a real charging session.

**Limitations:** Window percentages are simulated repository values; side glass does not physically lower. Some rear overlays span both fixed and tailgate regions in one mesh. The renderer hides mixed brake/tail overlays, rear OFF lenses and turn segment 5 while the tailgate is open; it cannot independently move their inner geometry. Other reparented tailgate lamps move with it. Device review must assess transparency sorting, clearances and the visual continuity of these transitions.

The shared HVAC strip and Media3 ownership remain in place. No AAOS hardware actuation is claimed.

## Verification and device handoff

**v0.5.1 verified:** debug build passed; all 51 JVM tests passed; lint has zero errors and nine warnings. APK v2 signing verifies, the packaged model matches the audited derivative, and both user source files remain unchanged. Results, hashes and device evidence are in `vehicle-build-verification.json` and `verification/vehicle-v0.5.1/`.

Installed code 8 on physical Samsung SM_G990E (Android 16, Mali-G78/OpenGL ES 3.2), addressed only through ADB serial `RZCT902Y60B`. Screenshots confirm complete Normal geometry, the supplied top-view image, page-wide backdrop, camera-linked scenery, level views and elevated Sunroof view. Bonnet/trunk/sunroof controls, Park-only charging, battery x-ray and opaque restoration passed smoke checks. Home → Vehicle reattachment passed. The app was left on Overview with its prior Drive and Sunroof states restored. Captured app-only logs contain no matching renderer/fatal errors.

These checks are not a sustained frame-rate or native-memory benchmark, exhaustive lighting/picking regression, or verification on other phones. The remaining full-device checklist is retained below.

```sh
./gradlew --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Domain tests cover controls, charging/gear interlocks and unavailable data. Pure scene tests cover transforms, camera/projection behavior and lamp policy/timing. Manifest checks establish source preservation and model structure.

No emulator was launched or used for this change. The broader regression checklist for future device passes is:

1. Confirm loading/retry, opaque paint/body panels, original glass/wheels, supplied top-view image, and alignment with the native platform at actual screen dimensions.
2. Exercise every moving panel, reverse it mid-transition, pick accessories and compare diagram/control state. Test locking with roof/flap open.
3. Review the level 8° presets, Sunroof view, orbit/zoom/reset, open-part framing, seamless scenery and ground contact from front, side and rear.
4. With charging stopped, test headlight modes, left/right/hazards, brake/reverse and ambient lamps. Inspect the open-tailgate overlay limitation.
5. Start/stop charging in Park; verify battery-only opacity, original material restoration and flap/gear interlocks. Confirm windows remain clearly described as simulation.
6. Change HVAC, revisit Home/Climate and verify consistent readings. Keep media playing while changing tabs.
7. Repeatedly switch tabs, background/foreground, recreate the Activity, retry loading and restart the app; check retained state, responsiveness and native-memory stability.

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
