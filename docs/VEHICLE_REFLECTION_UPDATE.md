# Vehicle reflection update

The vehicle now uses a more detailed reflection environment and modest material
adjustments to give the orange-red paint, black trim and glass clearer, natural
highlights.

## Implementation

- A static 128 × 128 cubemap with eight mip levels follows the scene's mirrored
  cylindrical panorama mapping and samples the surroundings from body height
  (approximately 0.8 m).
- Subdued samples of the existing wet ground supply lower reflections. The bright
  platform ring is excluded from the probe.
- Soft key, fill and overhead strips are baked into the reflection environment,
  helping highlights describe curved panels. Indirect-light intensity remains
  22,000.
- Paint roughness is 0.235; its colour, metallic factor 0.45 and clearcoat
  roughness 0.07 are retained. Black trim uses roughness 0.16 and coat roughness
  0.06; the grille uses roughness 0.11. Glass retains its existing tint, alpha and
  approximately 0.04 roughness.

Existing lamp and bloom calibration, matte tyre materials, x-ray transparency
and the restored rear-lamp layout are preserved.

## Performance scope

The cubemap is generated, uploaded and prefiltered during environment setup,
then reused. This adds bounded initialization work and persistent probe storage;
steady rendering uses the existing environment-map sampling path. It adds no
per-frame render passes or live scene captures. Geometry and material texture
sample counts remain unchanged. The 128-face RGB16F mip chain uses approximately 0.75 MiB of GPU storage (about 0.56 MiB more than the previous probe).

## Ground shadows

The existing exterior silhouette still casts onto the wet floor through one
1024-pixel PCF shadow map. Tyre/body contact footprints and half-resolution,
low-quality ambient occlusion remain enabled. Cabin and drivetrain groups stay
excluded from shadow work. The reflection update does not add shadow passes.
The final light strips were softened and narrowed after phone review so broad
white floor reflections do not overwhelm the dark contact areas.

## Verification — 30 October 2026

- Offline Gradle `assembleDebug`, `testDebugUnitTest`, and `lintDebug` succeeded
  in 1m 39s: 89 tests, zero failures/errors/skips; lint zero errors and eight
  existing warnings. The old bitmap KTX warning was resolved in the edited code.
- Installed with `adb install -r` on the physical Samsung SM_G990E, Android 16,
  Mali-G78. Final front, side and rear views show shaped paint/trim highlights;
  ground contact shadows remain visible. The accepted headlight halo and the
  85% transparent charging view with green battery animation were checked.
- A short active orbit sample presented about 30.2 fps (99 frames), and an
  active charging sample about 30.1 fps. These are SurfaceFlinger presentation
  samples, not sustained GPU/thermal profiling or a performance guarantee.
- Final logs contain no fatal, JNI or Filament renderer errors. The installed
  APK hash matches the verified build. Current demo preferences and the phone's
  temporary USB stay-awake setting were restored.
- Both original source GLBs retain their hashes. The full prepared BIN chunk is
  byte-identical to the previous asset; exactly eight roughness values change
  across the selected normal/x-ray materials. All other glTF properties and all
  107 supplied component names are retained and verified.

Machine-readable results: [verification](vehicle-reflection-verification.json).
Material scope: [diagnostic](generic-suv-reflection-materials-diagnostic.json).
The scenic source is an LDR image supplemented with calibrated light strips;
this remains a static approximation of the surroundings.
