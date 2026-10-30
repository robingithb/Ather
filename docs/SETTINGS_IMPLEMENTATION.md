# Settings and global appearance

The Settings destination provides four persistent themes: Midnight (navy/cyan), Arctic
(white/pale blue), Sport (charcoal/red), and Minimal (neutral slate). The supplied scenic
images are used for the theme gallery and the matching Home hero. Theme changes publish
through the application-owned settings repository and a composition-scoped palette, so
the navigation rail, panels, controls, dialogs, Home, Climate, Media and Vehicle UI update
without restarting playback or recreating the Activity.

The gallery uses Android resources `theme_arctic`, `theme_sport`, and `theme_minimal` copied
from the supplied pic2, pic3, and pic4 images. Midnight uses the existing Home hero.

## Viewport and contrast fixes

The Settings dashboard now fits the landscape viewport without horizontal or vertical
scrolling. Its banner, four theme cards, three middle panels and four lower panels use
proportional native Compose measurements. At approximately 632 × 328 dp of available
content on the connected 780 × 360 dp Samsung phone, all sections stay visible together.
Brightness, playback/guidance sliders, theme selection and appearance swatches remain
inline. Route and avoidance choices open compact dialogs.

Climate controls use fully opaque themed surfaces, so the photograph and airflow cannot
show through the temperature cards. The cabin photograph uses a neutral dark vignette
independent of the UI theme, and airflow uses a bright version of the selected accent
with a dark edge to remain legible over both dark and reflective portions of the image.

The native scenery reapplies its current theme after asynchronous resource loading has
finished assigning the authored glTF factors. Ring radiance is limited to preserve its
hue. Arctic uses a separate pale studio backdrop and shadow-receiving floor; Sport and
Minimal use neutral versions of the scene textures prepared once on the IO dispatcher.
The original source images and the SUV asset are unchanged.

## Preference behavior

- Brightness applies only to the app window; automatic brightness follows the device's
  display setting. Manual brightness retains a small visible minimum.
- Media volume controls the app's Media3 playback gain. Navigation volume and route/map
  preferences are stored app preferences; no navigation audio or routing engine is claimed.
- Connectivity actions open Android settings. Unavailable actions show an explanation.
- Profile, vehicle health and cabin lighting preview are labeled as demo content.
- Accent selection updates the shared palette, with contrast correction for light and dark
  themes. Ambient selection updates only the cabin preview.
- Preferences use SharedPreferences, validate corrupt/legacy values independently, and
  survive process recreation.

## Vehicle preservation

Theme changes affect only the Compose stage fade and the separate generated scenery's
panorama, floor, platform rim, halo, ring and core. The retained renderer receives colors
without reloading the SUV or resetting its camera. Vehicle lighting, reflection probe,
materials, geometry, battery appearance and animation logic are unchanged.

`app/src/main/assets/models/GenericSUV.glb` SHA-256 remains:

```text
88e4c72f9ff1ef8a2d2a760b73e1f5cca063bb1b1fba441bd25edfd875d79883
```

## Verification

Verified on 31 October 2026 after the viewport/contrast/scenery fixes: source/resource
compilation passed; all 105 JVM tests passed
with zero failures, errors or skipped tests; Android lint passed with zero errors and the
same eight existing warnings. `git diff --check` passed. The GLB checksum above and the
existing APK's 30 October 01:15 modification time are unchanged.

Use source/resource compilation, JVM tests and Android lint without packaging an APK.
Run lint separately so the Kotlin compiler's class metadata is released first on an 8 GB machine:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' \
  :app:compileDebugSources :app:testDebugUnitTest --console=plain
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' \
  :app:lintDebug --console=plain
```

The connected physical Samsung SM_G990E was inspected over ADB to reproduce the existing
Settings overflow, Arctic Climate veil, and mismatched vehicle scenery. The phone's original
Minimal theme, appearance preferences and Vehicle tab were restored after inspection.
The updated source was not installed: no emulator, device installation or APK packaging
is part of this revision. Visual confirmation of the updated build remains pending.
