"""Refine the authored SUV rubber without adding textures or changing geometry.

Call apply_tyre_refinements(prep) after the reference paint/wheel profiles and
before creating material variants. ``prep`` is the Preparation object owned by
prepare_generic_suv.py. The returned diagnostics can be saved in its manifest;
verify_tyre_refinements(Model(output), diagnostics) checks the serialized result.
"""

from __future__ import annotations

from collections import Counter
import copy
import hashlib
import json
import math
import struct


TYRE_MATERIAL = "Generic_tyre_rubber"
TREAD_MATERIAL = "Generic_tyre_tread_rubber"
WHEELS = {"Wheel_BL", "Wheel_BR", "Wheel_FL", "Wheel_FR"}


def _require(condition, message):
    if not condition:
        raise ValueError(message)


def _material_hash(material):
    return hashlib.sha256(json.dumps(material, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def _triangles(indices):
    return Counter(tuple(indices[start:start + 3]) for start in range(0, len(indices), 3))


def _triangle_hash(indices):
    # Sorting ignores draw order while retaining winding and duplicate counts.
    return hashlib.sha256(b"".join(struct.pack("<III", *triangle)
                                  for triangle in sorted(_triangles(indices).elements()))).hexdigest()


def _subset_primitive(prep, primitive, indices):
    # These tyres have fewer than 16k vertices; retaining 16-bit indices halves
    # the extra index storage compared with the generic preparer's uint32 path.
    short = max(indices) <= 65535
    width, code, component_type = (2, "H", 5123) if short else (4, "I", 5125)
    offset = prep.append_bytes(struct.pack("<" + code * len(indices), *indices))
    view = len(prep.gltf["bufferViews"])
    prep.gltf["bufferViews"].append({"buffer": 0, "byteOffset": offset,
                                     "byteLength": len(indices) * width, "target": 34963})
    accessor = len(prep.gltf["accessors"])
    prep.gltf["accessors"].append({"bufferView": view, "componentType": component_type,
                                   "count": len(indices), "type": "SCALAR",
                                   "min": [min(indices)], "max": [max(indices)]})
    selected = copy.deepcopy(primitive)
    selected["indices"] = accessor
    return selected, len(indices) * width


def apply_tyre_refinements(prep):
    """Split only existing rubber triangles into satin sidewall and rough tread.

    The wheel axle is local X. Sidewall faces lie on the outer 28% of each half
    width and have a substantial axial normal; groove walls in the central tread
    remain tread even when their normals point sideways. Existing positions,
    normals, UVs, triangle winding and metallic wheel materials are preserved.
    """
    gltf = prep.gltf
    names = [material.get("name") for material in gltf["materials"]]
    _require(names.count(TYRE_MATERIAL) == 1, "Expected one authored tyre rubber material")
    _require(TREAD_MATERIAL not in names, "Tyre refinement has already been applied")
    _require(not any(name and name.endswith("__BatteryXray") for name in names),
             "Apply tyre refinement before creating material variants")
    sidewall_index = names.index(TYRE_MATERIAL)
    wheel_material_hashes = {material["name"]: _material_hash(material)
                             for material in gltf["materials"]
                             if material.get("name", "").startswith("Generic_wheel_")}
    before_material = copy.deepcopy(gltf["materials"][sidewall_index])
    sidewall = gltf["materials"][sidewall_index]
    alpha = sidewall.get("pbrMetallicRoughness", {}).get("baseColorFactor", [1, 1, 1, 1])[3]
    _require(sidewall.get("alphaMode", "OPAQUE") == "OPAQUE" and alpha == 1,
             "Normal tyre rubber must remain opaque")
    pbr = sidewall.setdefault("pbrMetallicRoughness", {})
    _require("baseColorTexture" not in pbr and "metallicRoughnessTexture" not in pbr and
             "normalTexture" not in sidewall, "Review textured rubber before applying this profile")
    pbr.update(baseColorFactor=[.018, .019, .020, alpha], metallicFactor=0, roughnessFactor=.72)
    if "KHR_materials_clearcoat" in sidewall.get("extensions", {}):
        sidewall["extensions"]["KHR_materials_clearcoat"]["clearcoatFactor"] = 0
    tread = copy.deepcopy(sidewall)
    tread["name"] = TREAD_MATERIAL
    tread["pbrMetallicRoughness"].update(
        baseColorFactor=[.012, .013, .014, alpha], metallicFactor=0, roughnessFactor=.90)
    tread_index = len(gltf["materials"])
    gltf["materials"].append(tread)

    records = []
    appended_index_bytes = 0
    for node in gltf["nodes"]:
        if "mesh" not in node:
            continue
        mesh = gltf["meshes"][node["mesh"]]
        output = []
        for primitive in mesh["primitives"]:
            if primitive.get("material") != sidewall_index:
                output.append(primitive)
                continue
            _require(node.get("name") in WHEELS, "Unexpected non-wheel use of tyre material")
            _require(primitive.get("mode", 4) == 4 and "targets" not in primitive,
                     "Expected static triangle tyre geometry")
            attributes = copy.deepcopy(primitive["attributes"])
            positions = prep.accessor(attributes["POSITION"])
            normals = prep.accessor(attributes["NORMAL"])
            _require(len(positions) == len(normals), "Tyre normal count differs from positions")
            indices = [value[0] for value in prep.accessor(primitive["indices"])]
            _require(indices and len(indices) % 3 == 0 and min(indices) >= 0 and max(indices) < len(positions),
                     "Invalid tyre indices")
            _require(all(math.isfinite(value) for vector in positions + normals for value in vector),
                     "Nonfinite tyre position or normal")
            minimum = min(position[0] for position in positions)
            maximum = max(position[0] for position in positions)
            half_width = (maximum - minimum) / 2
            midpoint = (maximum + minimum) / 2
            _require(half_width > 0, "Degenerate tyre width")
            sidewall_indices, tread_indices = [], []
            for start in range(0, len(indices), 3):
                triangle = indices[start:start + 3]
                lateral_distance = sum(abs(positions[index][0] - midpoint) for index in triangle) / 3
                axial_normal = sum(abs(normals[index][0]) for index in triangle) / 3
                target = sidewall_indices if lateral_distance >= .72 * half_width and axial_normal > .35 else tread_indices
                target.extend(triangle)
            _require(sidewall_indices and tread_indices, "Expected both sidewall and tread geometry")
            _require(_triangles(indices) == _triangles(sidewall_indices) + _triangles(tread_indices),
                     "Tyre partition changed triangle multiplicity or winding")
            first_primitive = len(output)
            for material_index, subset in ((sidewall_index, sidewall_indices), (tread_index, tread_indices)):
                selected, byte_count = _subset_primitive(prep, primitive, subset)
                appended_index_bytes += byte_count
                selected["material"] = material_index
                _require(selected["attributes"] == attributes, "Tyre vertex attributes changed")
                output.append(selected)
            records.append({
                "node": node["name"], "mesh": node["mesh"],
                "sidewall_primitive": first_primitive, "tread_primitive": first_primitive + 1,
                "original_index_accessor": primitive["indices"], "attributes": attributes,
                "vertices": len(positions), "triangles": len(indices) // 3,
                "sidewall_triangles": len(sidewall_indices) // 3, "tread_triangles": len(tread_indices) // 3,
                "triangle_indices_sha256": _triangle_hash(indices),
                "local_axle": "+X", "width_meters": maximum - minimum,
                "source_uv_bounds": {
                    "min": [min(value[axis] for value in prep.accessor(attributes["TEXCOORD_0"])) for axis in range(2)],
                    "max": [max(value[axis] for value in prep.accessor(attributes["TEXCOORD_0"])) for axis in range(2)],
                },
            })
        mesh["primitives"] = output
    _require(len(records) == 4 and {record["node"] for record in records} == WHEELS,
             "Expected one tyre rubber primitive on each of the four wheels")
    _require(all(_material_hash(next(material for material in gltf["materials"] if material.get("name") == name)) == digest
                 for name, digest in wheel_material_hashes.items()), "Metallic wheel materials changed")
    return {
        "method": "existing modeled tread; index-only sidewall/tread material partition",
        "sidewall_material": sidewall_index, "tread_material": tread_index,
        "sidewall_before": before_material, "sidewall_after": copy.deepcopy(sidewall), "tread_after": copy.deepcopy(tread),
        "wheels": records, "wheel_material_sha256": wheel_material_hashes,
        "added_vertices": 0, "added_triangles": 0, "added_texture_samples": 0,
        "added_primitives": 4, "appended_index_bytes": appended_index_bytes,
        "normal_detail": "authored tread geometry and normals retained; no redundant normal map",
        "source_images_and_textures_unchanged": True,
    }


def verify_tyre_refinements(model, report):
    """Verify saved geometry, material profiles, and unchanged silver wheel factors."""
    gltf = model.gltf
    for record in report["wheels"]:
        primitives = gltf["meshes"][record["mesh"]]["primitives"]
        combined = []
        for role, material_key in (("sidewall", "sidewall_material"), ("tread", "tread_material")):
            primitive = primitives[record[role + "_primitive"]]
            _require(primitive["material"] == report[material_key], "Tyre material assignment changed")
            _require(primitive["attributes"] == record["attributes"], "Tyre vertex attributes changed")
            indices = [value[0] for value in model.accessor(primitive["indices"])]
            _require(len(indices) // 3 == record[role + "_triangles"], "Tyre triangle count changed")
            combined.extend(indices)
        _require(_triangle_hash(combined) == record["triangle_indices_sha256"],
                 "Tyre source triangle indices or winding changed")
    for key, expected in (("sidewall_material", "sidewall_after"), ("tread_material", "tread_after")):
        _require(gltf["materials"][report[key]] == report[expected], "Tyre PBR material profile changed")
    for name, digest in report["wheel_material_sha256"].items():
        _require(_material_hash(next(material for material in gltf["materials"] if material.get("name") == name)) == digest,
                 "Metallic wheel material changed")
    return {"four_tyre_partitions_verified": True, "source_vertices_normals_uvs_and_winding_preserved": True,
            "normal_rubber_is_opaque_dielectric": True, "silver_wheel_materials_unchanged": True,
            "additional_texture_samples": 0, "additional_triangles": 0}
