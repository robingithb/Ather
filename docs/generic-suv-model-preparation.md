# Updated GenericSUV preparation

The bundled `app/src/main/assets/models/GenericSUV.glb` now uses the supplied
`GenericSUV_new.glb` as its geometry and texture source. Both user GLB files are
left untouched. The existing bundled filename is retained for loader compatibility.

Reproduce the derivative:

```sh
python3 tools/prepare_generic_suv.py /home/robin/Pictures/AETHER/GenericSUV_new.glb \
  app/src/main/assets/models/GenericSUV.glb \
  --legacy-source /home/robin/Pictures/AETHER/GenericSUV.glb \
  --components /home/robin/Pictures/AETHER/modelpoints.txt \
  --manifest docs/generic-suv-model-manifest.json
```

## Component verification and functional restoration

`modelpoints.txt` contains 107 distinct component names. The old complete source
matches all 107; the raw updated source has 68 nodes, omits 40 listed identities,
and adds `SUNROOF.001`. This is an export/topology difference, not a spelling issue.
The updated model merges the mirror housings into the front doors and several
rear lenses into the trunk. It also removes powered overlays and all native lights.

Preparation resolves every listed name without copying merged static geometry:

- Extract each mirror's exact 1,565 existing triangles from its front door,
  preserving the updated source attributes, including glass and OFF indicator lens.
- Partition the merged high-stop OFF, reverse OFF left/right, and inner amber OFF
  left/right surfaces from the trunk. Partitioned triangles are moved into named
  groups with shared source vertex attributes; triangle counts are conserved.
- Restore 32 absent meshes (powered lamp overlays and the deleted tail-bar lens)
  and three native light nodes from the old complete source. Every restored
  triangle is checked against the updated source under its semantic material
  name, so another copy of existing merged surfaces is rejected. Only required
  geometry bufferViews are appended; donor textures are never copied.
- `REAR_LAMP_CLUSTER_L/R` are already empty logical groups in the updated source.
  Their physical red lenses remain in the trunk mesh. These are reported as
  logical groups, not falsely counted as independently rendered meshes.
- Keep `SUNROOF` as the control transform. Its front `SUNROOF.001` glass moves;
  `SUNROOF_REAR_FIXED` retains the rear glass. Closed geometry stays unchanged.
- Preserve the authored hinge origins, including the updated trunk's small rest
  rotation. Attachments use inverse(parent world) × child world transforms.
- Align the fender powered strips/glow to the actual moved updated-model lenses.
  These four deliberate world-transform corrections are listed in the manifest.

`REAR_TURN_OFF_L/R` and both segment-5 ON/glow groups are now outer, body-fixed
surfaces. `REAR_TURN_INNER_OFF_L/R` follow the trunk. The restored canonical
`REAR_BRAKE_ON`, disabled `REAR_BRAKE_GLOW_ON` and `REAR_TAIL_ON` span moving and
fixed rear parts and must be hidden while the trunk is open.

## Reference appearance

The new source still authored the same dark burgundy paint and dark wheel trim
as the old model. The derivative applies a documented PBR profile for the supplied
27 October screenshot: warm orange-red opaque paint, moderate metallic content,
low roughness and glossy clearcoat; polished silver/chrome wheel rings and spokes;
and rough rubber without a lacquer coat. The authored black roof, pillars,
mirrors, housings, trim, bright window surround, texture maps, and neutral tinted
transparent glass remain intact. Dark hubs remain dark.

The manifest records each changed material's original and resulting linear RGBA,
metallic factor, roughness and clearcoat. Source alpha values and alpha modes are
preserved. Every primitive has `Normal` (index 0) and `BatteryXray` (index 1)
material mappings. X-ray uses BLEND clones for non-battery solids while the battery
stays opaque. Normal paint/wheels/interior stay in the opaque depth-writing pass.

## Verification artifacts

- `generic-suv-source-audit.json`: full updated-source geometry, bounds and transforms.
- `generic-suv-bundled-audit.json`: prepared geometry, bounds and transforms.
- `generic-suv-modelpoints-verification.json`: exact renderable/light/logical-group
  classifications for the updated source, old source and prepared bundle.
- `generic-suv-model-manifest.json`: hashes, material changes, partitions, donor
  additions, parenting, alignment corrections, variants and validation results.

Preparation validates the GLB container, every accessor's finite values and buffer
ranges, actual position bounds, all triangle index ranges, source binary-prefix
identity, unchanged image/texture references, source-file hashes, conserved source
triangles, declared donor additions, component names, transforms and variants.
No APK, emulator, physical device or renderer preview is produced by this tool.
The screenshot's final appearance requires the user's device check.

## Recording follow-up: exterior indicator surfaces

The 27 October 23:36 recording exposed a geometry problem as well as excessive
emission. The restored detached front LED was 20 mm behind the visible front lens,
the opposite front strip was shifted sideways, and mirror emitters sat about
30 mm inward from their actual mirror strips. A rear emitter ray audit found an
opaque red trunk lens 4.45–7.54 mm in front of sampled powered-strip triangles.
Increasing emission cannot make a depth-occluded surface visible.

The prepared front, mirror and rear ON emitters now use the exact visible amber
OFF lens surfaces, with the amber LED material. Rear surfaces are partitioned into
the six existing named groups on each side, preserving all source triangle winding
and the entire visible lens outline. They retain ordinary opaque rendering and
depth tests; red lenses and other body geometry remain intact. `verify_indicator_surfaces`
independently compares the prepared world triangles for all 16 emitters against
8 corresponding OFF lens groups, including winding and amber material type.

The runtime must show exactly one state of each lens:

- `FRONT_INDICATOR_L/R` OFF versus `FRONT_INDICATOR_L/R_ON`.
- `MIRROR_INDICATOR_L/R_OFF` versus `MIRROR_INDICATOR_L/R_ON`.
- `REAR_TURN_INNER_OFF_L/R` versus powered segments 1–4.
- `REAR_TURN_OFF_L/R` versus powered segments 5–6.

Fender geometry is unchanged by this follow-up because the recording shows its
existing alignment functioning. The manifest's restored-donor section records
initial restoration provenance; `exterior_indicator_surfaces` records the final
replacement of recessed emitters with source-authored exterior lens geometry.
The source BIN remains an immutable prefix, even where donor accessors cease to
be referenced after a surface replacement.

## Paint and battery findings from the same recording

The diagonal driver-side paint bands are consistent with self-shadow acne.
All BODY and door paint triangles have consistent winding and unit normals, use
the same opaque paint material, and have no color texture or vertex colors.
Central exterior ray samples on both front doors found no overlying surfaces;
rear-door boundary hits were symmetric black trim/plastic edges. The preparation
tool therefore leaves paint geometry intact. Shadow reception and charging-to-Normal
transition changes belong in the renderer.

The battery is a single connected beveled pack, not an authored set of cells.
It spans X ±0.540 m, Y 0.318–0.458 m, Z −0.920–0.720 m. Its actual flat upper
plate is two triangles at Y 0.458 m over X ±0.528 m and Z −0.908–0.708 m.
A separate SOC gauge may sit 0.5 mm above that footprint while the case remains
opaque. Any displayed bands describe state of charge rather than physical cells.
These findings are documented in `generic-suv-video-diagnostics.json`.

## Headlamp projector follow-up

The white powered headlamp projectors had the same depth problem as the amber
strips: each 32-triangle ON projector sat 80 mm behind its actual OFF projector.
All 32 forward-facing samples per side were blocked by opaque housing/body
surfaces 3–28.5 mm outward from the powered surface. The actual exterior OFF
projectors had zero blockers in the same 32-sample audit on each side.

Preparation separates those real surfaces as `HEADLAMP_L_OFF` and
`HEADLAMP_R_OFF`, and uses their exact source attributes, triangles and winding
for `HEADLAMP_L_ON` and `HEADLAMP_R_ON` with the existing white LED material.
The runtime must show the OFF surface when headlights are off and the ON surface
when headlights are on. Clear lamp glass, black housings, physical light positions,
normal depth tests and white emission calibration remain unchanged.
`verify_headlamp_projectors` independently checks both prepared ON/OFF world
surfaces and their opaque white LED material.

DRL geometry was not recessed in the sampled views: 80 forward and 81 samples
from each front-quarter direction found no opaque blockers. The DRL geometry is
therefore preserved. Its final visible brightness is a renderer/device calibration
question and must not be inferred from this geometry audit alone.

## Reverse exterior lenses

Reverse white ON emitters were also behind the opaque red trunk shell. The
rearward-ray audit found 36/36 samples blocked on each side, with the nearest
red lens 4.65–7.19 mm outward. The real OFF reverse lenses had zero blockers in
38 samples per side. Preparation now reuses the exact `REAR_REVERSE_OFF_L/R`
exterior surfaces for `REAR_REVERSE_ON_L/R`, retaining the existing opaque white
LED material and normal depth tests. Their current mutual-exclusion policy must
remain: white ON only in Reverse, corresponding OFF lens otherwise. The real
lenses have 156 triangles each rather than the recessed emitters' 148, adding
16 triangles overall. Source geometry bytes and red shells remain untouched.
`verify_reverse_lenses` independently checks final world triangles and winding.

## Interior shadow groups

To stop only interior objects casting shadows, preparation moves complete,
unambiguous interior material primitives into six child renderables:

| New renderable | Movement parent | Material families |
| --- | --- | --- |
| `BODY_INTERIOR` | `BODY` | Interior fabric/atlas, dashboard silver and ambient OFF |
| `Door_FL_INTERIOR` | `Door_FL` | Interior fabric and atlas |
| `Door_FR_INTERIOR` | `Door_FR` | Interior fabric and atlas |
| `Door_BL_INTERIOR` | `Door_BL` | Interior fabric and atlas |
| `Door_BR_INTERIOR` | `Door_BR` | Interior fabric and atlas |
| `Door_Trunk_INTERIOR` | `Door_Trunk` | Interior atlas |

Each carries glTF node `extras.shadowRole = "interior"`. The renderer may identify
these groups by that metadata or the `_INTERIOR` suffix and disable their shadow
casting. Source vertex attributes, indices, winding, material assignments, closed
world transforms and moving-panel parenting are preserved; no geometry is added
or duplicated by this split. `verify_interior_shadow_groups` checks that contract
against the untouched updated source model.

Generic black plastics, exterior chrome, cladding, paint and wheels are not
classified as interior merely because the same material also appears inside.
Existing explicit `DRIVETRAIN_*`, `STEERING_WHEEL` and `DASH_KEYS` renderables can
be classified directly in the renderer without further geometry splitting.

## Main rear lamps: restored layout

The full-cluster main-red remap and its calibration were reverted at the user's
request. The trunk's original red lens primitive remains in `Door_Trunk`;
`TAILLAMP_L/R` and `REAR_TAIL_BAR` retain their earlier geometry and materials.
Canonical `REAR_TAIL_ON` and `REAR_BRAKE_ON` use the original source/donor shapes
and materials from the pre-full-cluster layout. The added auxiliary lamp nodes,
`REAR_TAIL_TRUNK_OFF`, independent main-red materials and emission profiles are
absent. The accepted `HIGH_STOP_ON/OFF` upper strip is preserved.

The original canonical tail/brake overlays span moving and fixed parts, so the
renderer restores their earlier suppression while the trunk is open. The separate
reverse white, exterior amber, headlamp projector and interior shadow contracts
remain in place. The later tyre material refinement is also retained.

`generic-suv-main-rear-revert-diagnostics.json` records exact comparisons with
the earlier rear geometry and the accepted tyre materials/geometry. Previous
main-rear surface and emission-calibration diagnostic files are retained as
historical records of the reverted implementation.

## Tyre material helper

`tools/vehicle_tyre_materials.py` runs after reference paint/wheel profiles and
before material variant creation. It partitions only the existing rubber indices
into satin sidewalls and rough tread, with opaque dielectric charcoal materials.
It adds no vertices, triangles, textures or texture samples and preserves the
silver wheel materials. Its independent serialized-output checks and diagnostics
are included in the model manifest. See `vehicle-tyre-materials-diagnostic.md`.

## Reflection material refinement

The reflection profile makes a small polish adjustment while retaining the orange-red
paint colour and dielectric black trim:

| Material | Roughness | Clearcoat roughness | Other values |
| --- | --- | --- | --- |
| Body paint | 0.235 | 0.07 | Metallic 0.45, coat factor 1, existing colour |
| Gloss-black trim | 0.16 | 0.06 | Existing black colour, metallic 0, coat factor 1 |
| Gloss-black grille | 0.11 | Existing profile | Existing black colour and metallic 0 |

Glass remains at its authored approximately 0.04 roughness with original tint,
alpha and blend mode. Chrome and silver wheel materials, satin tyre sidewalls,
rough tread, all lamp materials and the restored main-rear layout remain unchanged.
The same three material refinements apply to their BatteryXray copies. The model's
geometry, indices, textures and source GLBs are untouched. The current diagnostic
records a complete normalized glTF comparison excluding only the declared roughness
fields, plus the entire BIN hash: `generic-suv-reflection-materials-diagnostic.json`.
Scene lighting and environment reflection detail are controlled separately by the renderer.
