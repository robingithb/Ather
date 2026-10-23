AETHER — Automotive HMI
A modern Android digital cockpit concept built with Jetpack Compose, Google Filament, and AndroidX Media3.
Project status: In development. Screenshots are design concepts; features described below are planned unless marked as implemented.

Implemented in v0.3.1: the shared five-tab landscape shell, Home dashboard, shared mock vehicle state, Climate controls with animated airflow, and same-process Media3 playback. Climate uses the same full-height rail as the other pages, with four evenly spaced airflow cards and no separate airflow pause control. Vehicle 3D rendering, complete Media/Settings designs and AAOS hardware integration remain planned. Climate verification and assets are documented below.

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
- Google Filament: Planned GLB/glTF vehicle rendering, camera interaction, lighting, and animation.
- AndroidX Media3 / ExoPlayer: Planned audio playback and media-session integration.
- ViewModel + StateFlow: UI state management and reactive updates.
- Coroutines: Asynchronous work and lifecycle-aware operations.
- Android resources: Themes, colors, icons, and responsive layouts.
Planned highlights
- Premium automotive UI: Dark surfaces, cyan accents, gradients, and custom Compose components.
- Interactive vehicle visualization: Render a Jeep Compass GLB model with touch rotation and zoom; explore controllable vehicle parts where supported by the model.
- Climate experience: Dual-zone HVAC mock controls and cabin airflow visualization.
- Persistent music playback: A shared Media3 playback service, compact mini-player, and full media screen.
- Vehicle-state simulation: Mock vehicle signals so the app can be demonstrated on an Android phone without vehicle hardware.
- Responsive landscape design: Target physical Android phones and emulator testing, with adaptive layouts for different screen sizes.
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
- [ ] Establish project structure and shared Compose design system
- [ ] Implement five-screen navigation and responsive landscape layout
- [ ] Build Home dashboard and shared mini-player UI
- [ ] Load and display the Jeep Compass GLB using Filament
- [ ] Add vehicle camera rotation, zoom, and supported animations
- [ ] Connect vehicle controls to a mock state repository
- [ ] Build Climate screen and airflow visualizations
- [ ] Add Media3 playback, media session, and library browsing
- [ ] Implement themes and settings persistence
- [ ] Profile rendering and UI performance on a physical Android phone
- [ ] Explore AAOS integration and document platform limitations
Running the project
The application is being developed for Android phones in landscape mode and tested with an Android emulator where practical. Build instructions, minimum SDK, required assets, and device test results will be added once the project is runnable.
3D asset note: The Jeep Compass model must be distributed according to its license. If redistribution is not permitted, keep it out of the public repository and document how to supply it locally.

Why this project?
AETHER is a personal learning and portfolio project focused on Android Automotive HMI engineering: native UI architecture, real-time rendering, multimedia integration, performance, and vehicle-oriented state handling. It is not affiliated with or endorsed by any vehicle manufacturer.
License
License to be decided. Third-party models, images, and audio retain their respective licenses.

Climate design, HVAC behavior, airflow resources and background-edit prompts are documented in [CLIMATE_IMPLEMENTATION.md](docs/CLIMATE_IMPLEMENTATION.md).
