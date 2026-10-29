# Tyre material refinement

The four tyres already contain modeled tread and authored surface normals. Each tyre has 11,840 triangles, a local X axle, and a measured width of 0.249403 m. Their rubber material had no base-color, roughness or normal texture. Its uniform linear base color was approximately `[0.043, 0.043, 0.043]` with roughness 0.88, which gave the sidewalls and tread the same gray finish.

The derivative asset now uses two opaque dielectric rubber finishes:

| Surface | Linear base color | Metallic | Roughness | Clearcoat |
| --- | --- | --- | --- | --- |
| Sidewall | `[0.018, 0.019, 0.020]` | 0 | 0.72 | 0 |
| Tread and groove walls | `[0.012, 0.013, 0.014]` | 0 | 0.90 | 0 |

The sidewall retains a broad satin highlight. The tread is darker and more diffuse, so its existing modeled grooves supply the detail. Silver wheel materials are unchanged. The original rubber material used elsewhere on the body is also unchanged by this helper.

The helper partitions existing triangles by their local lateral position and axial normal. It shares the original POSITION, NORMAL and TEXCOORD accessors and preserves every triangle's indices, multiplicity and winding. It appends compact 16-bit index buffers without adding vertices, triangles, subdivision, normal maps, images or texture samples. There are four additional material primitives, one per wheel, and 284,160 appended index bytes. Both finishes are copied into the existing battery x-ray variants by the preparer.

| Wheel | Sidewall triangles | Tread triangles | Total |
| --- | ---: | ---: | ---: |
| Rear left | 4,450 | 7,390 | 11,840 |
| Rear right | 4,406 | 7,434 | 11,840 |
| Front left | 4,409 | 7,431 | 11,840 |
| Front right | 4,470 | 7,370 | 11,840 |

No new texture was appropriate: the supplied tyre UVs span approximately U −49.908…−12.992 and V −163.964…1.322, and the existing mesh already contains the tread relief. Adding a normal map would require a new mapping and could obscure that geometry or produce aliasing at HMI viewing sizes.

## Reproduction and verification

`tools/vehicle_tyre_materials.py` exports `apply_tyre_refinements(prep)`. The preparer calls it after the reference material profiles and before creating Normal/BatteryXray variants. It returns the material profiles, source attribute indices, per-wheel triangle hashes, and metallic wheel material hashes for the manifest. After serialization, `verify_tyre_refinements(Model(output), report)` checks all four partitions and the preserved wheel material factors.

The helper was checked in memory against `GenericSUV_new.glb`. All four triangle partitions conserved the original triangle multiplicities and winding. Existing vertex attributes, source BIN prefix, image/texture mappings and silver wheel factors were preserved. No user source GLB was written. The root task owns final asset generation and application/device validation; this helper's review does not claim a device-measured frame-time or GPU-memory result.
