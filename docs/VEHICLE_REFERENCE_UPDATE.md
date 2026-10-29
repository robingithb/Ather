# Vehicle reference update — 27 October 2026

The current source uses the supplied `GenericSUV_new.glb`, prepared as the existing bundled `app/src/main/assets/models/GenericSUV.glb`. The screenshot dated 27 October at 22:29:30 guides the warm orange-red paint, glossy black roof/pillars/mirrors, bright silver wheels and window trim, and neutral tinted glass. Original files under `Pictures/AETHER` and all supplied screenshots remain untouched.

## Model and component audit

`modelpoints.txt` contains 107 unique component names. The updated export initially has 68 nodes, misses 40 listed identities, and adds `SUNROOF.001`. Several static parts were merged into the doors/trunk; several powered lamp overlays and all three punctual lights were removed.

The preparation tool extracts merged mirrors and dark lenses from the new geometry without duplicating their triangles. It restores only absent lamp meshes, the missing tail-bar lens and three light nodes from the earlier source. All 107 requested identities resolve in the derivative, with renderable, light and logical-group identities reported separately. The new source's binary geometry/texture data stays intact as a prefix; added indices and required donor buffer views are appended. The front sunroof moves while the rear glass stays fixed. Fender LEDs align to the updated lens positions.

Audits: `generic-suv-model-manifest.json`, `generic-suv-modelpoints-verification.json`, `generic-suv-source-audit.json` and `generic-suv-bundled-audit.json`. Preparation details are in `generic-suv-model-preparation.md`.

Reproduce from the project root:

```sh
python3 tools/prepare_generic_suv.py /home/robin/Pictures/AETHER/GenericSUV_new.glb \
  app/src/main/assets/models/GenericSUV.glb \
  --legacy-source /home/robin/Pictures/AETHER/GenericSUV.glb \
  --components /home/robin/Pictures/AETHER/modelpoints.txt \
  --manifest docs/generic-suv-model-manifest.json
```

## Appearance and behavior

- Paint uses linear RGBA `[0.58, 0.055, 0.028, 1]`, metallic `0.45`, roughness `0.28`, clearcoat roughness `0.07`. Wheel surfaces use polished silver metallic values; rubber loses its lacquer coat. Black trim and the updated model's neutral transparent glass retain their source materials.
- Normal surfaces stay opaque where authored; charging uses explicit BatteryXray variants. Material switching preserves part transforms. Shared amber material instances retain indicator opacity during x-ray.
- A 64-pixel reflection cubemap with seven mip levels combines scenic colors and broad neutral highlights. Lower-hemisphere lighting excludes the background illustration's painted ring.
- The missing wet-stone texture is supplied at `app/src/main/res/drawable-nodpi/vehicle_ground.png`. The floor uses clearcoat and receives native sun shadows. The model casts shadows; panorama and decorative ring do not. Restrained contact geometry and half-resolution ambient occlusion reinforce grounding. The luminous blue ring remains in the same native camera/scene.
- Park displays automatic hazards, including mirror indicators; manual choices resume in Drive/Reverse. The effective signal drives visibility, timing reset and render scheduling. Reverse LEDs illuminate only in Reverse. Actual LED geometry receives exposure-scaled emission; billboard glow cards remain hidden.
- Ordinary drag changes yaw and locks pitch at 8 degrees; Sunroof retains vertical inspection. Pinch zoom and accessibility camera actions remain. The camera button strip is removed and the gesture hint remains.
- Home and Vehicle share the derived attention state. Open panels/windows, service attention and unavailable data have explicit notices. An open connector during charging is expected operation.

The new model keeps rear indicator segment 5 and outer dark lenses on the fixed quarter panels; inner dark lenses and inner LEDs follow the trunk. The remaining body-spanning brake/tail overlays hide while the trunk is open to avoid detached illuminated geometry.

## Verification boundaries

The current update is checked without APK packaging or device/emulator use. The non-packaging Gradle command is:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon \
  :app:compileDebugKotlin :app:compileDebugJavaWithJavac :app:mergeDebugAssets \
  :app:testDebugUnitTest :app:lintDebug --console=plain
```

The task graph was inspected before execution: resource packaging compiles resources, but no APK packaging, install, connected test or emulator task is included. Current results are recorded in `vehicle-reference-verification.json` after the checks complete. Historical v0.5.1 APK/phone evidence does not validate this update. Final visual appearance and interaction on the user's emulator/device remain for the user to assess.

## Ground asset provenance

Generated using the built-in image-generation tool, then copied unchanged into the project. Final prompt:

> Use case: photorealistic-natural. Asset type: seamless square diffuse surface texture for a native 3D vehicle showroom ground plane. A tileable top-down orthographic view of wet dark blue-black irregular stone paving, night-time premium car platform mood, restrained warm amber reflections and cool blue reflections. Fine grain, hairline slab seams, polished wet patches and shallow glints. Surface fills every pixel at consistent scale, no perspective or horizon, seamless repeated UV edges. Mostly dark slate navy with subtle broad blue and scattered warm amber reflected highlights, avoid bright white patches. Empty ground texture ONLY: no circle, ring, car, vehicle shadow, scenery, text, watermark. The circle and shadows are rendered separately in 3D. Square 1024x1024.

Verified result: Gradle BUILD SUCCESSFUL; 62 JVM tests passed; lint zero errors / 9 warnings. Both supplied GLB sources and the prior APK outputs are unchanged. The merged asset matches the audited model. No device or emulator was used.
