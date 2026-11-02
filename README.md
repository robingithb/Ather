AETHER — Automotive HMI

Current source update (2 November 2026, UI depth): shared raised controls add native elevation shadows, shaded faces, fine bevel highlights and a subtle pressed depression across Home, Vehicle, Climate, Media and Settings. Layout and touch dimensions stay unchanged. See [2.5D UI depth and verification scope](docs/UI_DEPTH_UPDATE.md). No APK is created or installed for verification.

Current source update (2 November 2026, snow/slots): Arctic has new snow mountain and ground assets; every scenery floor shares its backdrop tint and uses a matte material. A rounded scene viewport replaces the artificial edge fade. Home always keeps two slots per page with transparent Add widget actions in vacant slots, and drag previews shrink then restore on drop. See [Snow scene and Home slots](docs/SNOW_SCENE_AND_HOME_SLOTS_UPDATE.md) for implementation and verification scope.

Current source update (2 November 2026, gestures/scenery): Home widget pages and the library support horizontal swiping; long-press dragging moves widgets within and between pages with saved order. Arctic uses image-based mountain/stone scenery, and the vehicle scene has a narrow feather at its edges. See [Home gestures and scenic stage](docs/HOME_GESTURES_AND_SCENERY_UPDATE.md) for behavior and verification limits. No APK is created or installed.

Current source update (2 November 2026): added persistent Home widgets; exact Vehicle 50/50 layout with in-pane card menus; modern Climate temperature dials and inline controls; clear playback options; compact Settings sliders/theme cards. See [Review refinements and verification scope](docs/USER_REVIEW_REFINEMENTS.md). No APK is packaged or installed for these source checks.

Current source update (1 November 2026, page fitting): every landscape demo screen now fits its available display without scrolling. The original animated climate cabin stays visible on compact screens; longer lists and secondary controls use explicit pages/tabs. See [Fit-to-screen update](docs/FIT_TO_SCREEN_UPDATE.md). No APK is created or installed by this verification.

Current source update (1 November 2026): simplified all five screens, removed repeated media/HVAC panels, and added responsive landscape layouts with category-based settings. Every demo device, including a phone, acts as a landscape infotainment display. The app uses one persistent side navigation rail and adapts its content to different landscape sizes and aspect ratios. See [Ergonomics review, implementation and production follow-ups](docs/ERGONOMICS_REVIEW.md). This source-only verification creates no APK.

Current source update (31 October 2026): the Media page now includes library browsing, album artwork, shared transport/seek/shuffle/repeat, favorites, real queue selection, original bundled demo audio and the shared climate/output panel. It follows all four themes and custom accents. Compile-only verification and device limits are documented in [Media implementation](docs/MEDIA_IMPLEMENTATION.md).

Current source update (30 October 2026): Settings now provides Midnight, Arctic, Sport and Minimal themes with persistent app-wide palettes, supplied scenic previews, app brightness/playback volume, connectivity links, navigation preferences and cabin/accent previews. The vehicle GLB is unchanged; only the separate scenery and ground ring receive theme colors. See [Settings implementation and verification](docs/SETTINGS_IMPLEMENTATION.md).

Previous source and device update (30 October 2026): the SUV now has scene-aligned reflections, soft highlights and refined paint/trim roughness for a more realistic car finish. Ground contact and exterior shadows, the accepted headlight halo, rear-light revert, tyre finishes and 85% charging transparency are preserved. Gradle, all 89 JVM tests and lint pass with zero errors (eight existing warnings). Verified on the physical Samsung SM_G990E, including charging and short active rotation samples around 30 fps; current settings restored. See [reflection and shadow verification](docs/VEHICLE_REFLECTION_UPDATE.md). Previous reports below describe historical revisions.
A modern Android digital cockpit concept built with Jetpack Compose, Google Filament, and AndroidX Media3.
Project status: In development. Reference designs and runtime verification are documented separately; implemented features are listed below.

Implemented in v0.5.1 and subsequent source updates: the shared five-tab landscape shell, Home dashboard, Climate controls with animated airflow, the Filament GenericSUV Vehicle screen, shared mock vehicle controls, the complete Media page with same-process Media3 playback and persistent Settings/themes. Vehicle has section-based controls, model rotation/zoom, door/bonnet/trunk/sunroof motion, signal and reverse lamps and a charging battery view. The current simplified shell uses a side navigation rail without a repeated media/HVAC dock. AAOS hardware integration remains planned.

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
- Persistent music playback: A shared Media3 playback service, Home transport controls, and full media screen.
- Vehicle-state simulation: Mock vehicle signals so the app can be demonstrated on an Android phone without vehicle hardware.
- Responsive landscape design: Phones and other demo devices act as infotainment displays; layouts adapt to different landscape sizes and aspect ratios while retaining the side navigation rail.
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
- [x] Complete Settings screen design
- [x] Complete Media screen design
- [x] Implement themes and settings persistence
- [ ] Profile rendering and UI performance on a physical Android phone
- [ ] Explore AAOS integration and document platform limitations
Running the project
The app requests sensor landscape orientation, allowing either landscape rotation for a mounted demo device. Layouts also respond to the available window size, with minimum SDK 26.

Configure demo tablets/head units to landscape in their system/display settings: [Android 17 may ignore app orientation requests on large displays](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored) for apps targeting SDK 37.

With the Android SDK installed and the pinned dependencies already cached, the offline checks below compile sources, run tests and lint without packaging an APK:

```sh
./gradlew --offline --no-daemon :app:compileDebugKotlin :app:compileDebugJavaWithJavac :app:testDebugUnitTest :app:lintDebug
```

The Vehicle model is bundled at `app/src/main/assets/models/GenericSUV.glb`. Physical-device smoke results and screenshots for v0.5.1 are recorded in the Vehicle implementation document and build-verification report.
3D asset note: The supplied vehicle model must be distributed according to its license. If redistribution is not permitted, keep it out of the public repository and document how to supply it locally.

Why this project?
AETHER is a personal learning and portfolio project focused on Android Automotive HMI engineering: native UI architecture, real-time rendering, multimedia integration, performance, and vehicle-oriented state handling. It is not affiliated with or endorsed by any vehicle manufacturer.
License
License to be decided. Third-party models, images, and audio retain their respective licenses.

Climate design, HVAC behavior, airflow resources and background-edit prompts are documented in [CLIMATE_IMPLEMENTATION.md](docs/CLIMATE_IMPLEMENTATION.md).

Vehicle architecture, model preparation, checks and the device test checklist are documented in [VEHICLE_IMPLEMENTATION.md](docs/VEHICLE_IMPLEMENTATION.md).
