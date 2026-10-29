#!/usr/bin/env python3
"""Prepare the bundled Jeep GLB while preserving the user's source file.

python3 tools/prepare_vehicle_model.py /path/to/JeepCompass.glb \
    app/src/main/assets/models/JeepCompass.glb \
    --report docs/vehicle-model-preparation.json

The original Door_FR painted primitive contains two isolated triangles on the
left side of the vehicle. This deterministic operation moves those triangles
into an identity-transform static sibling named BodyPatch_DoorFR, and compacts
the vertex attributes/index buffers referenced by both resulting primitives.
Other original geometry, materials, embedded textures, and node transforms are
unchanged. Unreferenced original binary data is retained to avoid rewriting any
unrelated buffer views; a small compact replacement buffer is appended.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path
import struct

from validate_vehicle_model import COMPONENTS, FORMATS, Glb, audit, require


PATCH_NAME = "BodyPatch_DoorFR"
MOVING_NODES = {"Door_BL", "Door_BR", "Door_FL", "Door_FR", "Door_Trunk"}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def lighting_components(model):
    """Exact lamp triangle groups; all runs are inclusive, zero-based ordinals."""
    node = next(n for n in model.gltf["nodes"] if n.get("name") == "Object_36")
    primitive = model.gltf["meshes"][node["mesh"]]["primitives"][0]
    positions = model.accessor(primitive["attributes"]["POSITION"])
    indices = [row[0] for row in model.accessor(primitive["indices"])]

    def group(point):
        return ("front" if point[2] > 0 else "rear") + ("_left" if point[0] > 0 else "_right")

    def runs(values):
        result = []
        for value in sorted(set(values)):
            if result and value == result[-1][1] + 1:
                result[-1][1] = value
            else:
                result.append([value, value])
        return result

    components = {}
    for name in ("front_left", "front_right", "rear_left", "rear_right"):
        vertices = [i for i, point in enumerate(positions) if group(point) == name]
        triangles = [i // 3 for i in range(0, len(indices), 3)
                     if group(positions[indices[i]]) == name]
        require(all(all(group(positions[j]) == name for j in indices[i * 3:i * 3 + 3])
                    for i in triangles), "Lamp triangle crosses component boundaries")
        components[name] = {"vertices": len(vertices), "triangles": len(triangles),
                            "triangle_ordinal_runs_inclusive": runs(triangles),
                            "bounds": {"min": [min(positions[i][k] for i in vertices) for k in range(3)],
                                       "max": [max(positions[i][k] for i in vertices) for k in range(3)]}}
    return {"node": "Object_36", "mesh": node["mesh"], "primitive": 0,
            "material": primitive["material"], "position_accessor": primitive["attributes"]["POSITION"],
            "index_accessor": primitive["indices"], "components": components,
            "note": "One primitive/material contains all four lamp lenses; changing its emission also changes rear lenses"}


def prepare(source, destination, report_path):
    require(source.resolve() != destination.resolve(), "Source and bundled output must differ")
    model = Glb(source)
    gltf = copy.deepcopy(model.gltf)
    binary = bytearray(model.binary)
    require(not any(n.get("name") == PATCH_NAME for n in gltf["nodes"]),
            "Input is already prepared; supply the untouched original GLB")
    door_index = next(i for i, n in enumerate(gltf["nodes"]) if n.get("name") == "Door_FR")
    door_mesh = gltf["nodes"][door_index]["mesh"]
    original_primitive = copy.deepcopy(gltf["meshes"][door_mesh]["primitives"][0])
    positions = model.accessor(original_primitive["attributes"]["POSITION"])
    indices = [item[0] for item in model.accessor(original_primitive["indices"])]
    require(original_primitive.get("mode", 4) == 4, "Expected triangles")
    wrong_vertices = {i for i, p in enumerate(positions) if p[0] > 0}
    triangles = [indices[i:i + 3] for i in range(0, len(indices), 3)]
    wrong_triangles = [i for i, triangle in enumerate(triangles)
                       if any(vertex in wrong_vertices for vertex in triangle)]
    require(len(wrong_vertices) == 4 and wrong_triangles == [0, 1],
            "Source anomaly differs from the audited four-vertex/two-triangle patch")
    require(all(all(vertex in wrong_vertices for vertex in triangles[i]) for i in wrong_triangles),
            "Anomalous triangle crosses between body sides; manual review required")
    patch_indices = [vertex for i in wrong_triangles for vertex in triangles[i]]
    door_indices = [vertex for i, triangle in enumerate(triangles)
                    if i not in wrong_triangles for vertex in triangle]

    def append_buffer(payload, target):
        binary.extend(b"\0" * (-len(binary) % 4))
        view = {"buffer": 0, "byteOffset": len(binary), "byteLength": len(payload), "target": target}
        binary.extend(payload)
        gltf["bufferViews"].append(view)
        return len(gltf["bufferViews"]) - 1

    def compact_primitive(selected_indices):
        old_vertices = sorted(set(selected_indices))
        remap = {old: new for new, old in enumerate(old_vertices)}
        primitive = copy.deepcopy(original_primitive)
        primitive["attributes"] = {}
        for semantic, accessor_index in original_primitive["attributes"].items():
            original = gltf["accessors"][accessor_index]
            values = model.accessor(accessor_index)
            selected = [values[old] for old in old_vertices]
            code, _ = FORMATS[original["componentType"]]
            component_count = COMPONENTS[original["type"]]
            payload = b"".join(struct.pack("<" + code * component_count, *row) for row in selected)
            accessor = {key: copy.deepcopy(value) for key, value in original.items()
                        if key not in {"bufferView", "byteOffset", "count", "min", "max", "sparse"}}
            accessor.update(bufferView=append_buffer(payload, 34962), byteOffset=0, count=len(selected))
            if semantic == "POSITION" or "min" in original or "max" in original:
                accessor["min"] = [min(row[k] for row in selected) for k in range(component_count)]
                accessor["max"] = [max(row[k] for row in selected) for k in range(component_count)]
            gltf["accessors"].append(accessor)
            primitive["attributes"][semantic] = len(gltf["accessors"]) - 1
        # Both compacted primitives fit safely in unsigned 16-bit index buffers.
        require(len(old_vertices) <= 65535, "Compacted primitive exceeds uint16 indices")
        remapped = [remap[index] for index in selected_indices]
        payload = struct.pack("<" + "H" * len(remapped), *remapped)
        gltf["accessors"].append({"bufferView": append_buffer(payload, 34963), "byteOffset": 0,
                                  "componentType": 5123, "count": len(remapped), "type": "SCALAR"})
        primitive["indices"] = len(gltf["accessors"]) - 1
        return primitive

    corrected_door = compact_primitive(door_indices)
    patch = compact_primitive(patch_indices)
    gltf["meshes"][door_mesh]["primitives"][0] = corrected_door
    patch_mesh_index = len(gltf["meshes"])
    gltf["meshes"].append({"name": PATCH_NAME + "_mesh", "primitives": [patch]})
    patch_node_index = len(gltf["nodes"])
    gltf["nodes"].append({"name": PATCH_NAME, "mesh": patch_mesh_index})
    parents = [i for i, node in enumerate(gltf["nodes"])
               if door_index in node.get("children", [])]
    require(len(parents) == 1, "Expected one original parent for Door_FR")
    gltf["nodes"][parents[0]]["children"].append(patch_node_index)
    gltf["buffers"][0]["byteLength"] = len(binary)
    json_chunk = json.dumps(gltf, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    json_chunk += b" " * (-len(json_chunk) % 4)
    binary.extend(b"\0" * (-len(binary) % 4))
    output = (struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(json_chunk) + 8 + len(binary)) +
              struct.pack("<II", len(json_chunk), 0x4E4F534A) + json_chunk +
              struct.pack("<II", len(binary), 0x004E4942) + binary)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(output)
    bundled = Glb(destination)
    require(source.read_bytes() == model.raw, "Original source changed unexpectedly")
    require(bundled.binary.startswith(model.binary), "Original binary prefix changed")
    for field in ("materials", "textures", "images", "samplers"):
        require(bundled.gltf.get(field) == model.gltf.get(field), f"Original {field} changed")
    require(bundled.gltf["bufferViews"][:len(model.gltf["bufferViews"])] == model.gltf["bufferViews"],
            "Original buffer views changed")
    for index, mesh in enumerate(model.gltf["meshes"]):
        if index != door_mesh:
            require(bundled.gltf["meshes"][index] == mesh, f"Unrelated mesh {index} changed")
        else:
            require(bundled.gltf["meshes"][index]["primitives"][1:] == mesh["primitives"][1:],
                    "Unrelated Door_FR primitives changed")
    for index, node in enumerate(model.gltf["nodes"]):
        expected = copy.deepcopy(node)
        if index == parents[0]:
            expected["children"].append(patch_node_index)
        require(bundled.gltf["nodes"][index] == expected, f"Original node {index} changed")
    source_audit = audit(source)
    bundled_audit = audit(destination)
    for field in ("triangles", "vertices_in_primitives", "animations", "skins"):
        require(source_audit["counts"][field] == bundled_audit["counts"][field],
                f"Geometry count changed: {field}")
    require(source_audit["source_bounds"] == bundled_audit["source_bounds"], "Global bounds changed")
    require(set(bundled_audit["hinges"]) == MOVING_NODES, "Movable-door set changed")
    require(not bundled_audit["warnings"], "Corrected model still contains audited anomaly")
    # Verify that compaction preserves every selected vertex attribute exactly,
    # including triangle winding and normal/UV seams, not merely bounding boxes.
    for primitive, original_indices in ((corrected_door, door_indices), (patch, patch_indices)):
        new_indices = [i[0] for i in bundled.accessor(primitive["indices"])]
        for semantic, old_accessor in original_primitive["attributes"].items():
            old_values = model.accessor(old_accessor)
            new_values = bundled.accessor(primitive["attributes"][semantic])
            require([new_values[i] for i in new_indices] == [old_values[i] for i in original_indices],
                    f"Compaction altered {semantic} triangle values")
    if report_path:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        audit_path = report_path.with_name("vehicle-model-bundled-audit.json")
        audit_path.write_text(json.dumps(bundled_audit, indent=2) + "\n")
        report = {
            "operation": "Detach two isolated left-side triangles from Door_FR into static BodyPatch_DoorFR",
            "source": {"file": source.name, "sha256": sha256(model.raw), "bytes": len(model.raw),
                       "unchanged": True},
            "bundled": {"file": destination.as_posix(), "sha256": sha256(output), "bytes": len(output)},
            "moved_source_triangles_zero_based": wrong_triangles,
            "moved_source_vertices_zero_based": sorted(wrong_vertices),
            "new_static_node": {"name": PATCH_NAME, "node": patch_node_index, "mesh": patch_mesh_index,
                                "parent": parents[0], "vertices": 4, "triangles": 2},
            "counts_before": source_audit["counts"], "counts_after": bundled_audit["counts"],
            "lighting_components": lighting_components(bundled),
            "checks": {"source_untouched": True, "all_original_binary_prefix_bytes_preserved": True,
                       "materials_textures_and_original_buffer_views_unchanged": True,
                       "triangle_attribute_values_and_winding_preserved": True,
                       "total_triangles_and_vertices_unchanged": True, "global_bounds_unchanged": True,
                       "five_moving_nodes_and_identity_transforms_preserved": True,
                       "door_fr_wrong_side_warning_removed": True,
                       "five_hinge_numerical_checks": "pass", "emulator_or_device_run": "not performed"},
            "note": "Compacted replacement accessors are used by the door and patch; old now-unreferenced bytes remain in BIN to preserve unrelated data",
        }
        report_path.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Prepared {destination}: 2 static triangles separated; "
          f"{bundled_audit['counts']['triangles']:,} total triangles unchanged")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    prepare(args.source, args.destination, args.report)


if __name__ == "__main__":
    main()
