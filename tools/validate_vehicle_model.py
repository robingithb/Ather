#!/usr/bin/env python3
"""Audit the supplied Jeep GLB without a renderer, emulator, or third-party packages.

Usage:
    python3 tools/validate_vehicle_model.py /path/to/JeepCompass.glb \
        --output docs/vehicle-model-audit.json

This is a model-specific geometry check, not a replacement for the Khronos glTF
validator. Hinge axes are estimates from mesh edges: the source has no authored
rig, animations, or pivots. It validates bounds, accessor/index ranges, embedded
PNG dimensions, and the direction/invariants of the proposed hinge transforms.
"""

from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
import statistics
import struct
import sys


FORMATS = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2),
           5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}
COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4,
              "MAT2": 4, "MAT3": 9, "MAT4": 16}
IDENTITY = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def bounds(points):
    return {"min": [min(p[k] for p in points) for k in range(3)],
            "max": [max(p[k] for p in points) for k in range(3)]}


def center(box):
    return [(a + b) / 2 for a, b in zip(box["min"], box["max"])]


class Glb:
    def __init__(self, path):
        self.raw = path.read_bytes()
        require(len(self.raw) >= 20, "Truncated GLB")
        magic, version, length = struct.unpack_from("<III", self.raw)
        require(magic == 0x46546C67 and version == 2, "Expected GLB version 2")
        require(length == len(self.raw), "GLB byte length mismatch")
        self.gltf = None
        self.binary = None
        offset = 12
        while offset < length:
            size, kind = struct.unpack_from("<II", self.raw, offset)
            require(offset + 8 + size <= length, "GLB chunk exceeds file")
            chunk = self.raw[offset + 8:offset + 8 + size]
            if kind == 0x4E4F534A:
                self.gltf = json.loads(chunk)
            elif kind == 0x004E4942:
                self.binary = chunk
            offset += 8 + size
        require(self.gltf is not None and self.binary is not None, "Missing JSON/BIN chunk")
        require(not self.gltf.get("skins"), "Model unexpectedly contains skins")
        self.cache = {}
        self.positions = {}
        for index, node in enumerate(self.gltf["nodes"]):
            require(node.get("matrix", IDENTITY) == IDENTITY, f"Node {index}: nonidentity matrix")
            require(node.get("translation", [0, 0, 0]) == [0, 0, 0],
                    f"Node {index}: nonzero translation")
            require(node.get("rotation", [0, 0, 0, 1]) == [0, 0, 0, 1],
                    f"Node {index}: nonidentity rotation")
            require(node.get("scale", [1, 1, 1]) == [1, 1, 1],
                    f"Node {index}: nonunit scale")

    def accessor(self, index):
        if index in self.cache:
            return self.cache[index]
        accessor = self.gltf["accessors"][index]
        require("sparse" not in accessor, "Sparse accessor needs a dedicated decoder")
        view = self.gltf["bufferViews"][accessor["bufferView"]]
        require(view.get("buffer", 0) == 0, "Unexpected external buffer")
        code, width = FORMATS[accessor["componentType"]]
        count = COMPONENTS[accessor["type"]]
        stride = view.get("byteStride", count * width)
        start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
        end = start + (accessor["count"] - 1) * stride + count * width
        require(end <= len(self.binary), f"Accessor {index}: out-of-bounds binary read")
        require(end <= view.get("byteOffset", 0) + view["byteLength"],
                f"Accessor {index}: exceeds its bufferView")
        result = [struct.unpack_from("<" + code * count, self.binary, start + i * stride)
                  for i in range(accessor["count"])]
        require(all(math.isfinite(c) for row in result for c in row),
                f"Accessor {index}: nonfinite geometry")
        self.cache[index] = result
        return result

    def image_bytes(self, image):
        require("bufferView" in image, "Expected embedded image")
        view = self.gltf["bufferViews"][image["bufferView"]]
        offset = view.get("byteOffset", 0)
        return self.binary[offset:offset + view["byteLength"]]


def connected_parts(vertices, indices):
    """Join position-equal vertices across normal/UV seams at 10 microunits."""
    parents = list(range(len(vertices)))

    def find(x):
        while parents[x] != x:
            parents[x] = parents[parents[x]]
            x = parents[x]
        return x

    def join(a, b):
        parents[find(a)] = find(b)

    by_position = {}
    for i, vertex in enumerate(vertices):
        key = tuple(round(x, 5) for x in vertex)
        if key in by_position:
            join(i, by_position[key])
        else:
            by_position[key] = i
    for i in range(0, len(indices), 3):
        a, b, c = indices[i:i + 3]
        join(a, b)
        join(b, c)
    groups = defaultdict(list)
    for i, vertex in enumerate(vertices):
        groups[find(i)].append(vertex)
    return sorted(groups.values(), key=len, reverse=True)


def hinge_matrix(pivot, axis, angle_degrees):
    """Column-major T(pivot) * R * T(-pivot), right-handed coordinates."""
    cosine, sine = math.cos(math.radians(angle_degrees)), math.sin(math.radians(angle_degrees))
    if axis == "Y":
        matrix = [cosine, 0, -sine, 0, 0, 1, 0, 0, sine, 0, cosine, 0, 0, 0, 0, 1]
    else:
        matrix = [1, 0, 0, 0, 0, cosine, sine, 0, 0, -sine, cosine, 0, 0, 0, 0, 1]
    rotated = transform(matrix, pivot)
    matrix[12:15] = [a - b for a, b in zip(pivot, rotated)]
    return matrix


def transform(matrix, point):
    return [sum(matrix[k + 4 * j] * point[j] for j in range(3)) + matrix[k + 12]
            for k in range(3)]


def hinge_audit(name, paint):
    if name == "Door_Trunk":
        pivot = list(max(paint, key=lambda p: p[1]))
        axis, degrees = "X", 70
        latch = list(min(paint, key=lambda p: p[1]))
    else:
        side = 1 if name.endswith("L") else -1
        paint = [p for p in paint if side * p[0] > 0]
        front = max(p[2] for p in paint)
        edge = [p for p in paint if p[2] > front - 0.025 and p[1] < 1.18]
        pivot = [statistics.median(p[k] for p in edge) for k in range(3)]
        axis, degrees = "Y", -60 * side
        latch = list(min(paint, key=lambda p: p[2]))
    opened = hinge_matrix(pivot, axis, degrees)
    require(max(abs(a - b) for a, b in zip(hinge_matrix(pivot, axis, 0), IDENTITY)) < 1e-10,
            f"{name}: zero angle does not restore identity")
    pivot_error = max(abs(a - b) for a, b in zip(pivot, transform(opened, pivot)))
    require(pivot_error < 1e-10, f"{name}: hinge pivot moved")
    new_latch = transform(opened, latch)
    if axis == "Y":
        require((new_latch[0] - latch[0]) * (1 if name.endswith("L") else -1) > 0,
                f"{name}: opening swings inward")
    else:
        require(new_latch[1] > latch[1], "Tailgate opening does not move latch upward")
        require(new_latch[2] < latch[2], "Tailgate opening does not move latch rearward")
    old_radius = math.dist(pivot, latch)
    require(abs(math.dist(pivot, new_latch) - old_radius) < 1e-10,
            f"{name}: rotation changed hinge distance")
    return {"pivot_estimate_source_coordinates": pivot, "axis": axis,
            "opening_degrees_example": degrees,
            "transform_column_major_example": opened,
            "latch_before": latch, "latch_after": new_latch,
            "pivot_invariance_error": pivot_error,
            "zero_angle_identity_and_outward_opening_pass": True,
            "confidence": "inferred edge, not an authored physical hinge"}


def audit(path):
    model = Glb(path)
    gltf = model.gltf
    nodes = []
    glass = []
    hinges = {}
    all_vertices = []
    primitive_count = vertex_count = triangle_count = 0
    warnings = []
    for node_index, node in enumerate(gltf["nodes"]):
        if "mesh" not in node:
            continue
        points, primitives = [], []
        for primitive_index, primitive in enumerate(gltf["meshes"][node["mesh"]]["primitives"]):
            require(primitive.get("mode", 4) == 4, "Expected triangle primitives")
            vertices = model.accessor(primitive["attributes"]["POSITION"])
            indices = [i[0] for i in model.accessor(primitive["indices"])]
            require(len(indices) % 3 == 0, "Triangle index count is not divisible by 3")
            require(min(indices) >= 0 and max(indices) < len(vertices), "Invalid vertex index")
            actual = bounds(vertices)
            accessor = gltf["accessors"][primitive["attributes"]["POSITION"]]
            for key in ("min", "max"):
                require(max(abs(a - b) for a, b in zip(accessor[key], actual[key])) < 1e-5,
                        f"{node['name']}: POSITION {key} does not match binary vertices")
            material = primitive.get("material")
            primitive_count += 1
            vertex_count += len(vertices)
            triangle_count += len(indices) // 3
            points.extend(vertices)
            primitives.append({"index": primitive_index, "material": material,
                               "vertices": len(vertices), "triangles": len(indices) // 3,
                               "bounds": actual})
            if gltf["materials"][material].get("alphaMode") == "BLEND":
                parts = connected_parts(vertices, indices)
                glass.append({"node": node["name"], "primitive": primitive_index,
                              "material": material,
                              "components": [{"vertices": len(part), "bounds": bounds(part)}
                                             for part in parts]})
            if node["name"].startswith("Door_") and primitive_index == 0:
                hinges[node["name"]] = hinge_audit(node["name"], vertices)
                if node["name"] == "Door_FR":
                    wrong_side = sum(p[0] > 0 for p in vertices)
                    if wrong_side:
                        warnings.append(f"Door_FR painted primitive has {wrong_side} vertices on +X; "
                                        "exclude these when estimating its right-side hinge")
        all_vertices.extend(points)
        nodes.append({"index": node_index, "name": node["name"], "mesh": node["mesh"],
                      "bounds": bounds(points), "primitives": primitives})
    box = bounds(all_vertices)
    size = [b - a for a, b in zip(box["min"], box["max"])]
    model_center = center(box)
    scale = 2 / max(size)
    normalized_half = [x * scale / 2 for x in size]
    radius = math.sqrt(sum(x * x for x in normalized_half))
    images = []
    rgba_bytes = 0
    for image in gltf.get("images", []):
        content = model.image_bytes(image)
        require(content[:8] == b"\x89PNG\r\n\x1a\n", "Expected embedded PNG")
        width, height = struct.unpack_from(">II", content, 16)
        rgba_bytes += width * height * 4
        images.append({"name": image.get("name"), "width": width, "height": height,
                       "embedded_bytes": len(content)})
    wheels = {n["name"]: center(n["bounds"]) for n in nodes if n["name"].startswith("Wheel_")}
    require(all(wheels[f"Wheel_F{s}"][2] > wheels[f"Wheel_B{s}"][2] for s in "LR"),
            "Front wheel names no longer agree with +Z orientation")
    require(all(wheels[f"Wheel_{a}L"][0] > wheels[f"Wheel_{a}R"][0] for a in "FB"),
            "Left wheel names no longer agree with +X orientation")
    return {
        "file": path.name, "sha256": hashlib.sha256(model.raw).hexdigest(),
        "file_bytes": len(model.raw), "generator": gltf.get("asset", {}).get("generator"),
        "counts": {"nodes": len(gltf["nodes"]), "meshes": len(gltf["meshes"]),
                   "materials": len(gltf["materials"]), "primitives": primitive_count,
                   "vertices_in_primitives": vertex_count, "triangles": triangle_count,
                   "animations": len(gltf.get("animations", [])), "skins": len(gltf.get("skins", []))},
        "all_node_transforms_identity": True,
        "source_bounds": box, "source_size": size, "source_center": model_center,
        "orientation": {"up": "+Y", "front": "+Z", "vehicle_left": "+X", "vehicle_right": "-X",
                        "evidence_wheel_centers": wheels},
        "normalization": {"formula": "N = Scale(s) * Translate(-center); final node = N * T(p) * R * T(-p)",
                          "scale_for_longest_dimension_2": scale,
                          "normalized_half_extent": normalized_half,
                          "normalized_bounding_sphere_radius": radius,
                          "sphere_camera_fit_formula": "distance >= radius / sin(min(verticalFov,horizontalFov)/2) * margin",
                          "distance_at_35_degree_limiting_fov_margin_1_15": radius / math.sin(math.radians(17.5)) * 1.15,
                          "fit_note": "Refit expanded bounds while doors/tailgate open; fixed closed bounds can clip moving panels"},
        "hinges": hinges,
        "images": images,
        "texture_memory_estimate": {"rgba8_base_bytes": rgba_bytes,
                                    "rgba8_with_full_mips_bytes_approx": round(rgba_bytes * 4 / 3),
                                    "note": "Uncompressed GPU estimate; decoder/upload staging and geometry are additional"},
        "transparent_components": glass,
        "materials": [{"index": i, "name": m.get("name"), "alphaMode": m.get("alphaMode", "OPAQUE"),
                       "baseColorFactor": m.get("pbrMetallicRoughness", {}).get("baseColorFactor", [1, 1, 1, 1]),
                       "authored_emissive": m.get("emissiveFactor", [0, 0, 0]) != [0, 0, 0]}
                      for i, m in enumerate(gltf["materials"])],
        "limitations": [
            "No authored joints: estimated pivots require visual review on a physical device",
            "Side-door glass is a primitive, not a node; front glass primitive also contains mirror glass",
            "Object_36 material13 includes both front and rear lamp lenses; split components for headlight-only emission",
            "Fixed glass Object_37 includes windshield, roof, and rear quarter windows",
            "Shared material mutation can affect multiple parts; use per-primitive material instances",
        ],
        "warnings": warnings,
        "checks": {"glb_structure_and_accessors": "pass", "position_bounds_match_binary": "pass",
                   "indices_in_range": "pass", "hinge_numerical_invariants": "pass",
                   "emulator_or_device_run": "not performed"},
        "renderable_nodes": nodes,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = audit(args.model)
    serialized = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized)
    else:
        print(serialized, end="")
    counts = report["counts"]
    print(f"PASS: {counts['vertices_in_primitives']:,} vertices, {counts['triangles']:,} triangles, "
          f"{len(report['hinges'])} hinge checks; no renderer or emulator used", file=sys.stderr)


if __name__ == "__main__":
    main()
