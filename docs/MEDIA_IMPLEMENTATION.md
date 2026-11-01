# Media page

Implemented 31 October 2026 from the supplied Media visual and accompanying design notes.

The Media destination now uses the shared navigation rail and header, a library column,
full player, Up Next queue, and a climate/output sidebar. Midnight, Arctic, Sport, Minimal
and custom accents use the existing composition-scoped palette; artwork retains its
natural colors. The background is the app's low-contrast gradient, with scenic artwork
confined to the album cover. No vehicle model or rendering code was changed.

## Connections and interactions

- A single shell-owned `PlaybackViewModel` controls the existing Media3 service and session.
  Home, Climate and the full Media page consume the same metadata, artwork, play state,
  queue and progress. Settings volume continues to control this service's player.
- Now Playing includes seek, play/pause, previous/next, shuffle, three-state repeat,
  favorites, and the full queue dialog. Up Next follows the player's actual shuffle/repeat
  traversal. Queue rows select real tracks; no nonfunctional reorder handles are shown.
- Songs, Albums and Artists browse the selected audio documents' metadata. Playlists
  exposes Current queue and Favorites within the loaded library. Favorites persist by
  media ID. This is a picker-selected library, not an index of all device music.
- Media library selection and expanded album/artist/playlist survive tab switches.
  All eight library entries, artwork, metadata, progress, transport, climate and output
  controls divide the available display height. No Media container scrolls. On narrow
  displays the full queue remains accessible through its button; at 610dp of content
  width, Up Next also fits as a fourth column. Lists use height-dependent pagination
  with Previous/Next buttons; every track remains reachable. Text fits its measured slot,
  including enlarged fonts. Longer routing information opens a bounded themed dialog.
- Climate +/- and Auto/A/C/Sync act on the same repository as Home, Vehicle and Climate.
  The climate card heading opens the full Climate screen; command feedback uses the
  existing shell status.
- Documents are selected with Android's audio file picker and persisted URI grants.
  Title, artist, album, duration and embedded artwork come from the actual document.
  Choosing files replaces the queue; an append API is also available for future UI.
- Play demo explicitly loads six original, locally synthesized 24-second instrumentals.
  They are labeled as bundled demos. No audio starts automatically on first launch.
  See `app/src/main/assets/media/README.txt` and `tools/generate_demo_audio.py`.
- Up Next fills short/empty queues with clearly labeled demo recommendations and varied
  local cover artwork. Real upcoming items stay first and retain their actual playback
  order. Suggested tracks are not presented as already queued: selecting a recommendation
  explicitly adds the missing originals and starts the chosen track, preserving any user
  documents and existing queue order. No suggestion autoplays. Rows fill their allocated
  page height so a short queue does not leave a large blank card.
- The full-player seek bar uses a slim rounded rail, theme-colored progress, a steady
  thumb footprint with an animated focus/scrub indicator, and aligned tabular time labels.
  Progress samples at 100ms during playback and 500ms while paused; smooth movement is
  isolated to this widget. Scrubbing holds its preview until the controller acknowledges
  the request or a two-second recovery timeout expires. New media/duration/disconnection
  resets the preview. Material3 retains pointer, keyboard, accessibility range/setProgress
  behavior; the custom track mirrors in RTL. The control stays within the existing height
  budget, and all four palettes/custom accents use the shared theme tokens.
- The service persists the queue, current item/playhead, shuffle and repeat. Restoration
  reads providers on IO, skips revoked/missing documents, preserves the matching track's
  playhead and prepares in a paused state. Playback survives tab navigation/backgrounding
  through the foreground media service, subject to Android's normal process lifecycle.

## Available sources

| Source | Phone behavior |
| --- | --- |
| Bundled demo | Real local WAV audio and metadata; opt-in playback |
| Selected device music | Granted, supported audio documents play through Media3 |
| USB files | Android picker can browse a drive when the phone/provider exposes it |
| Bluetooth | System settings connect devices; connected audio outputs are observed |
| FM radio | Disabled and visibly labeled unavailable; requires vehicle hardware/service |

Connected output capabilities do not claim to identify or change Android's active audio
route. Bluetooth remote music browsing/control and external media-host integrations are
not implemented. Bluetooth settings failures and playback/import failures have visible
feedback, with file selection and playback recovery actions.

## Verification

Use compile-only tasks, JVM tests and lint; do not use assemble/build/install tasks for
this request. The checks below do not package an APK or launch an emulator:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' \
  :app:compileDebugSources :app:testDebugUnitTest --console=plain
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' \
  :app:lintDebug --console=plain
```

The initial source/resource compilation passed. All 113 JVM tests passed with zero failures,
errors or skips. Android lint passed with zero errors and the same eight pre-existing
warnings; no new warnings remain. `git diff --check` passed. Existing APK artifact
timestamps and the Vehicle GLB SHA-256 remain unchanged. Results are recorded in
`media-verification.json`. Android Studio previews cover
Midnight tablet, Arctic phone, Sport compact and Minimal enlarged text in
`MediaScreenPreview.kt`; these are preview fixtures, not a claimed runtime screenshot.

During the follow-up to fit the display without scrolling, ADB connected successfully
to the physical Samsung SM_G990E. Read-only display inspection reported 2340×1080 pixels
in landscape, density 480dpi (3×), a 780×360dp app viewport and system font scale 1.0.
The layout accounts for the shared rail, header and display cutout within that viewport.
Additional tests cover player slot/transport budgets and paginated queue boundaries,
resizing and filtering. The follow-up source build and all 122 JVM tests pass. Final lint
reports zero errors and eight pre-existing warnings, with no new warnings. A source
audit finds no scroll containers in Media or its dialogs; `git diff --check` passes.
Current results are in `media-verification.json`.

No APK was created or installed, and no emulator was used.
Consequently, this revision's visual
layout, real audio routing, document-provider interaction, notification/background
behavior and on-device runtime remain unverified. Source/test/lint success cannot prove
that a new revision is running on a phone without deploying it.

## Up Next and seek bar polish verification

The follow-up source/resource compile passed, all 141 JVM tests passed with zero
failures/errors/skips, and final Android lint passed with zero errors and the same
eight existing warnings. There are no new lint warnings. New policy tests cover
demo queue preservation/deduplication, suggestion order/exclusion and seek preview
acknowledgment/timeout behavior. The six original audio files are valid, unclipped
24-second mono 16-bit PCM WAVs; the original three files remain unchanged.

A no-scroll source audit and `git diff --check` pass. The final lint run also compiles
the latest source; its persistent log is `app/build/reports/media-polish-lint.log`.
No APK packaging, installation or emulator was run for this revision. Visual/audio
confirmation of this updated revision on a physical device remains pending deployment.
