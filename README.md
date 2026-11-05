# Aether — Automotive HMI

An Android digital cockpit concept combining native interfaces, interactive 3D vehicle graphics, music and climate controls.

Aether is a portfolio project exploring automotive Human–Machine Interface (HMI) development. It brings together UI engineering, real-time rendering and shared application state in a landscape infotainment experience.

## Features

| Experience | Highlights |
| --- | --- |
| **Home** | Customizable widgets, dashboard pages, vehicle status, navigation preview, and music and climate shortcuts. |
| **Vehicle** | Interactive 3D model with rotation and zoom, animated doors and sunroof, window and lighting controls, and battery charging visualization. |
| **Climate** | Dual-zone temperature, fan speed, animated airflow, defrost and seat comfort controls. |
| **Music** | Audio playback, album artwork, library browsing, queue management, favorites, shuffle and repeat. |
| **Settings** | Display, sound and connectivity preferences, accent colors, ambient-light previews and saved theme selection. |

Four visual themes — **Midnight, Arctic, Sport and Minimal** — personalize the interface and vehicle scenery.

## Screenshots

<details>
<summary>Explore Vehicle, Climate, Music and Settings</summary>

### Vehicle

![Interactive 3D vehicle](vehiclepage.png)

### Climate

![Dual-zone climate controls](climatepage.png)

### Music

![Music player and library](musicpage.png)

### Settings

![Theme selection and appearance settings](settingspage.png)

</details>

## Technology stack

| Technology | Role |
| --- | --- |
| **Kotlin** | Application development. |
| **Jetpack Compose** | Native UI, reusable components and responsive layouts. |
| **Google Filament** | Real-time 3D vehicle rendering, lighting and camera interaction. |
| **AndroidX Media3 / ExoPlayer** | Music playback and media-session integration. |
| **ViewModel + StateFlow** | Reactive UI state and shared screen data. |
| **Kotlin Coroutines** | Asynchronous operations and lifecycle-aware work. |

## How it works

Jetpack Compose presents the interface, while Filament renders the vehicle in a dedicated Android surface. Shared state connects the controls, vehicle visualization, media playback and saved preferences. The code is organized by feature, with shared data repositories and UI components.

## Run locally

1. Clone the repository and open it in Android Studio.
2. Configure JDK 17 and Android SDK Platform 37, then sync Gradle.
3. Run on an Android 8.0 (API 26) or newer device or emulator in landscape orientation.

## Project scope

Aether currently runs as a standalone Android demo with simulated vehicle data. Future work includes exploring **Android Automotive OS (AAOS)** integration and supported vehicle-data interfaces.

## Assets and license

The project uses a supplied 3D vehicle model and bundled visual assets. Third-party assets remain subject to their respective licenses. A project license has not yet been specified.
