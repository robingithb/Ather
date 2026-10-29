AETHER — Automotive HMI

Current source and device update (30 October 2026): the SUV now has scene-aligned reflections, soft highlights and refined paint/trim roughness for a more realistic car finish. Ground contact and exterior shadows, the accepted headlight halo, rear-light revert, tyre finishes and 85% charging transparency are preserved. Gradle, all 89 JVM tests and lint pass with zero errors (eight existing warnings). Verified on the physical Samsung SM_G990E, including charging and short active rotation samples around 30 fps; current settings restored. See [current reflection and shadow verification](docs/VEHICLE_REFLECTION_UPDATE.md). Previous reports below describe historical revisions.
A modern Android digital cockpit concept built with Jetpack Compose, Google Filament, and AndroidX Media3.
Project status: In development. Reference designs and runtime verification are documented separately; implemented features are listed below.

Implemented in v0.5.1: the shared five-tab landscape shell, Home dashboard, Climate controls with animated airflow, the Filament GenericSUV Vehicle screen, shared mock vehicle controls and same-process Media3 playback. Vehicle has six control panels, model rotation/zoom, door/bonnet/trunk/sunroof motion, signal and reverse lamps, a charging battery view, and a shared HVAC dock. Complete Media/Settings designs and AAOS hardware integration remain planned.

The v0.5.1 Vehicle fixes restore source opaque materials in the normal view and use explicit `Normal`/`BatteryXray` variants for charging. The normal model retains 52 OPAQUE and eight BLEND materials; 51 additional x-ray clones bring the prepared asset to 111 definitions. A native panoramic cylinder, floor, platform ring and contact-shadow meshes share the car's camera across the upper row, with an off-axis projection fitting the car between the controls. Preset views start at 8° pitch except the 60° Sunroof view. Overview now uses the supplied transparent top-view image with correctly oriented door/trunk controls.

Version 0.5.1 builds successfully: all 51 JVM tests pass and lint reports zero errors (nine warnings). Installed and smoke-tested on the connected Samsung SM_G990E: normal geometry, synchronized scene, level views, panel motion and charging material restoration passed. See the verification report for screenshots and limits; sustained frame-rate and memory profiling remain separate work. No emulator was used for this Vehicle revision.

AETHER takes its name from aether, a classical term for the clear upper sky. It represents a clean, immersive, and connected in-vehicle experience. This portfolio project explores how native Android UI, real-time 3D graphics, and automotive-oriented application architecture can work together in a single HMI application.
Design preview
AETHER is one application with five screens, connected through a shared navigation rail:
Screen	Purpose
Home	Dashboard overview, vehicle status, navigation preview, media and climate shortcuts
Vehicle	Interactive 3D vehicle, door and light controls, charging and vehicle information
Climate	Dual-zone temperature, fan speed, airflow direction, defrost, and seat comfort
Media	Music library, playback controls, playlists, queue, and audio source selection
Settings	Themes, display, connectivity, sound, vehicle appearance, and system preferences


Technology stack
- Kotlin + Jetpack Compose: Native HMI layouts, reusable controls, and 2.5D visual effects.
- Google Filament: GLB vehicle rendering, camera interaction, lighting, and procedural door/trunk animation.
- AndroidX Media3 / ExoPlayer: Shared audio playback and media-session integration.
- ViewModel + StateFlow: UI state management and reactive updates.
- Coroutines: Asynchronous work and lifecycle-aware operations.
- Android resources: Themes, colors, icons, and responsive layouts.
Planned highlights
- Premium automotive UI: Dark surfaces, cyan accents, gradients, and custom Compose components.
- Interactive vehicle visualization: Render the supplied GenericSUV GLB model with touch rotation and zoom; explore controllable vehicle parts where supported by the model.
- Climate experience: Dual-zone HVAC mock controls and cabin airflow visualization.
- Persistent music playback: A shared Media3 playback service, compact mini-player, and full media screen.
- Vehicle-state simulation: Mock vehicle signals so the app can be demonstrated on an Android phone without vehicle hardware.
- Responsive landscape design: Adaptive layouts target physical Android phones with different screen sizes.
- Future AAOS exploration: Investigate supported media integration and vehicle-data interfaces separately from the standalone phone demo.
Proposed architecture
AETHER Android App
├── Presentation (Jetpack Compose)
│   ├── Home
│   ├── Vehicle
│   ├── Climate
│   ├── Media
│   └── Settings
├── ViewModels / StateFlow
├── Domain models and use cases
├── Data repositories
│   ├── Mock vehicle data
│   ├── User preferences
│   └── Media library
├── 3D renderer (Google Filament)
└── Media playback (Media3 service/session)
Rendering approach: Compose draws the HMI controls; Filament draws the 3D vehicle inside a dedicated Android rendering surface. Both observe shared application state.
Development roadmap
- [x] Establish project structure and shared Compose design system
- [x] Implement five-screen navigation and responsive landscape layout
- [x] Build Home dashboard and shared mini-player UI
- [x] Load and display the GenericSUV GLB using Filament
- [x] Add vehicle camera rotation, zoom, and supported panel motion
- [x] Connect vehicle controls to a mock state repository
- [x] Build Climate screen and airflow visualizations
- [x] Add shared Media3 playback, media session, file selection and queue
- [ ] Complete Media and Settings screen designs
- [ ] Implement themes and settings persistence
- [ ] Profile rendering and UI performance on a physical Android phone
- [ ] Explore AAOS integration and document platform limitations
Running the project
The app targets Android phones in landscape mode, with minimum SDK 26. With the Android SDK installed and the pinned dependencies already cached, the offline checks are:

```sh
./gradlew --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The Vehicle model is bundled at `app/src/main/assets/models/GenericSUV.glb`. Physical-device smoke results and screenshots for v0.5.1 are recorded in the Vehicle implementation document and build-verification report.
3D asset note: The supplied vehicle model must be distributed according to its license. If redistribution is not permitted, keep it out of the public repository and document how to supply it locally.

Why this project?
AETHER is a personal learning and portfolio project focused on Android Automotive HMI engineering: native UI architecture, real-time rendering, multimedia integration, performance, and vehicle-oriented state handling. It is not affiliated with or endorsed by any vehicle manufacturer.
License
License to be decided. Third-party models, images, and audio retain their respective licenses.

Climate design, HVAC behavior, airflow resources and background-edit prompts are documented in [CLIMATE_IMPLEMENTATION.md](docs/CLIMATE_IMPLEMENTATION.md).

Vehicle architecture, model preparation, checks and the device test checklist are documented in [VEHICLE_IMPLEMENTATION.md](docs/VEHICLE_IMPLEMENTATION.md).
