#!/usr/bin/env python3
"""Audit and prepare the updated GenericSUV without changing user source files.

python3 tools/prepare_generic_suv.py /path/to/GenericSUV_new.glb \
    app/src/main/assets/models/GenericSUV.glb \
    --legacy-source /path/to/GenericSUV.glb --components /path/to/modelpoints.txt \
    --manifest docs/generic-suv-model-manifest.json

Preserves the complete updated source BIN as an immutable prefix. Existing
merged meshes are partitioned without duplicating triangles. Only absent
powered overlays/lights are imported from the old complete model. Source alpha
is preserved, and explicit Normal/BatteryXray variants retain depth rendering.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import math
from pathlib import Path
import struct


IDENTITY = [1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1.]
COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}
FORMATS = {5121: ("B", 1), 5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}
PARTS = {"Door_FL": "FrontLeftDoor", "Door_FR": "FrontRightDoor", "Door_BL": "RearLeftDoor",
         "Door_BR": "RearRightDoor", "Door_Trunk": "Trunk", "BONNET": "Bonnet",
         "SUNROOF": "Sunroof", "CHARGE_PORT": "ChargePort"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def multiply(a, b):
    return [sum(a[row + 4 * k] * b[k + 4 * column] for k in range(4))
            for column in range(4) for row in range(4)]


def inverse(matrix):
    rows = [[matrix[row + 4 * column] for column in range(4)] +
            [float(row == column) for column in range(4)] for row in range(4)]
    for column in range(4):
        pivot = max(range(column, 4), key=lambda row: abs(rows[row][column]))
        require(abs(rows[pivot][column]) > 1e-12, "Noninvertible node transform")
        rows[column], rows[pivot] = rows[pivot], rows[column]
        factor = rows[column][column]
        rows[column] = [x / factor for x in rows[column]]
        for row in range(4):
            if row != column:
                factor = rows[row][column]
                rows[row] = [a - factor * b for a, b in zip(rows[row], rows[column])]
    return [rows[row][column + 4] for column in range(4) for row in range(4)]


def local_matrix(node):
    if "matrix" in node:
        return node["matrix"]
    x, y, z, w = node.get("rotation", [0, 0, 0, 1])
    norm = math.sqrt(x*x + y*y + z*z + w*w)
    x, y, z, w = [v / norm for v in (x, y, z, w)]
    sx, sy, sz = node.get("scale", [1, 1, 1])
    tx, ty, tz = node.get("translation", [0, 0, 0])
    return [(1-2*y*y-2*z*z)*sx, (2*x*y+2*z*w)*sx, (2*x*z-2*y*w)*sx, 0,
            (2*x*y-2*z*w)*sy, (1-2*x*x-2*z*z)*sy, (2*y*z+2*x*w)*sy, 0,
            (2*x*z+2*y*w)*sz, (2*y*z-2*x*w)*sz, (1-2*x*x-2*y*y)*sz, 0,
            tx, ty, tz, 1]


def point(matrix, value):
    return [sum(matrix[row + 4 * k] * value[k] for k in range(3)) + matrix[row + 12]
            for row in range(3)]


def world_matrices(gltf):
    parents = {}
    for i, node in enumerate(gltf["nodes"]):
        for child in node.get("children", []):
            require(child not in parents, "A node has multiple parents")
            parents[child] = i
    result = {}
    visiting = set()
    def resolve(index):
        if index in result:
            return result[index]
        require(index not in visiting, "Node hierarchy has a cycle")
        visiting.add(index)
        matrix = local_matrix(gltf["nodes"][index])
        if index in parents:
            matrix = multiply(resolve(parents[index]), matrix)
        visiting.remove(index)
        result[index] = matrix
        return matrix
    for index in range(len(gltf["nodes"])):
        resolve(index)
    return result, parents


def bounds(values):
    return {"min": [min(p[k] for p in values) for k in range(3)],
            "max": [max(p[k] for p in values) for k in range(3)]}


class Model:
    def __init__(self, path):
        self.raw = path.read_bytes()
        require(len(self.raw) >= 28, "Truncated GLB header")
        magic, version, size = struct.unpack_from("<III", self.raw)
        require(magic == 0x46546C67 and version == 2 and size == len(self.raw), "Invalid GLB header")
        offset = 12
        self.gltf = self.binary = None
        while offset < size:
            require(offset + 8 <= size, "Truncated GLB chunk header")
            count, kind = struct.unpack_from("<II", self.raw, offset)
            require(count % 4 == 0, "GLB chunk must be four-byte aligned")
            require(offset + count + 8 <= size, "Truncated GLB chunk")
            chunk = self.raw[offset + 8:offset + count + 8]
            if kind == 0x4e4f534a:
                self.gltf = json.loads(chunk)
            elif kind == 0x004e4942:
                self.binary = chunk
            offset += count + 8
        require(self.gltf is not None and self.binary is not None, "Missing GLB chunks")
        self.cache = {}

    def accessor(self, index):
        if index not in self.cache:
            a = self.gltf["accessors"][index]
            require("sparse" not in a, "Sparse accessor unsupported by this audit")
            view = self.gltf["bufferViews"][a["bufferView"]]
            require(view.get("buffer", 0) == 0 and "uri" not in self.gltf["buffers"][0],
                    "Expected self-contained binary buffer")
            code, width = FORMATS[a["componentType"]]
            count = COMPONENTS[a["type"]]
            stride = view.get("byteStride", count * width)
            require(a["count"] > 0 and stride >= count * width, "Invalid accessor count/stride")
            offset = view.get("byteOffset", 0) + a.get("byteOffset", 0)
            end = offset + (a["count"] - 1) * stride + count * width
            require(end <= len(self.binary) and end <= view.get("byteOffset", 0) + view["byteLength"],
                    "Accessor exceeds BIN or bufferView")
            self.cache[index] = [struct.unpack_from("<" + code * count, self.binary, offset + i * stride)
                                 for i in range(a["count"])]
            require(all(math.isfinite(x) for value in self.cache[index] for x in value), "Nonfinite accessor value")
        return self.cache[index]


def audit(model):
    gltf = model.gltf
    world, parents = world_matrices(gltf)
    nodes = []
    total_vertices = total_triangles = total_primitives = 0
    all_world_bounds = []
    for i, node in enumerate(gltf["nodes"]):
        record = {"index": i, "name": node.get("name"), "parent": parents.get(i),
                  "local_rest_column_major": local_matrix(node), "world_rest_column_major": world[i],
                  "extras": node.get("extras", {})}
        if "mesh" in node:
            values, material_indices = [], set()
            for primitive in gltf["meshes"][node["mesh"]]["primitives"]:
                require(primitive.get("mode", 4) == 4, "Expected triangle primitives")
                vertices = model.accessor(primitive["attributes"]["POSITION"])
                indices = [value[0] for value in model.accessor(primitive["indices"])]
                require(len(indices) % 3 == 0 and min(indices) >= 0 and max(indices) < len(vertices),
                        "Invalid triangle indices")
                require(all(math.isfinite(x) for v in vertices for x in v), "Nonfinite geometry")
                actual = bounds(vertices)
                accessor = gltf["accessors"][primitive["attributes"]["POSITION"]]
                require(all(abs(a-b) < 1e-5 for key in ("min", "max") for a,b in zip(actual[key], accessor[key])),
                        "POSITION bounds do not match binary geometry")
                values.extend(vertices[index] for index in sorted(set(indices)))
                total_vertices += len(vertices)
                total_triangles += len(indices) // 3
                total_primitives += 1
                material_indices.add(primitive.get("material"))
            transformed = [point(world[i], vertex) for vertex in values]
            record.update(mesh=node["mesh"], materials=sorted(material_indices),
                          local_bounds=bounds(values), world_bounds=bounds(transformed))
            all_world_bounds.extend([record["world_bounds"]["min"], record["world_bounds"]["max"]])
        nodes.append(record)
    return {"sha256": hashlib.sha256(model.raw).hexdigest(), "bytes": len(model.raw),
            "counts": {"nodes": len(gltf["nodes"]), "meshes": len(gltf["meshes"]),
                       "materials": len(gltf["materials"]), "vertices": total_vertices,
                       "triangles": total_triangles, "primitives": total_primitives,
                       "lights": len(gltf.get("extensions", {}).get("KHR_lights_punctual", {}).get("lights", [])),
                       "animations": len(gltf.get("animations", [])), "skins": len(gltf.get("skins", []))},
            "world_bounds": bounds(all_world_bounds), "nodes": nodes}


def component_names(path):
    names = [line.strip() for line in path.read_text().splitlines() if line.strip()]
    require(names and len(names) == len(set(names)), "modelpoints contains duplicate or empty names")
    require(all(name == name.strip() and not any(c.isspace() for c in name) for name in names),
            "Invalid component identifier in modelpoints")
    return names


def component_report(gltf, expected):
    by_name = {}
    for index, node in enumerate(gltf["nodes"]):
        name = node.get("name")
        require(name and name not in by_name, "Missing or duplicate node name: " + str(name))
        by_name[name] = index
    records = []
    for name in expected:
        index = by_name.get(name)
        node = gltf["nodes"][index] if index is not None else {}
        kind = ("missing" if index is None else "renderable" if "mesh" in node else
                "punctual_light" if "KHR_lights_punctual" in node.get("extensions", {}) else "logical_group")
        records.append({"name": name, "node": index, "kind": kind})
    return {"expected_count": len(expected), "missing": [r["name"] for r in records if r["kind"] == "missing"],
            "extra": sorted(set(by_name) - set(expected)), "components": records,
            "counts_by_kind": {kind: sum(r["kind"] == kind for r in records)
                               for kind in ("renderable", "punctual_light", "logical_group", "missing")}}


def material_summary(material):
    pbr = material.get("pbrMetallicRoughness", {})
    return {"rgba": pbr.get("baseColorFactor", [1, 1, 1, 1]),
            "metallic": pbr.get("metallicFactor", 1), "roughness": pbr.get("roughnessFactor", 1),
            "clearcoat": copy.deepcopy(material.get("extensions", {}).get("KHR_materials_clearcoat")),
            "emissive": material.get("emissiveFactor", [0, 0, 0]),
            "alphaMode": material.get("alphaMode", "OPAQUE")}


def apply_reference_materials(gltf):
    # glTF base colors are linear. These values yield a warm orange-red in sRGB;
    # normal paint remains opaque and the authored neutral tinted glass is kept.
    profiles = {
        "Generic_body_paint": {"baseColorFactor": [.58, .055, .028, 1], "metallicFactor": .45,
                               "roughnessFactor": .235, "clearcoatRoughnessFactor": .07},
        # Polished dielectric trim keeps a slightly broader top-coat reflection.
        "Generic_gloss_black_trim": {"roughnessFactor": .16, "clearcoatRoughnessFactor": .06},
        "Generic_grille_gloss_black": {"roughnessFactor": .11},
        "Generic_wheel_rim_dark": {"baseColorFactor": [.42, .44, .46, 1], "metallicFactor": 1,
                                   "roughnessFactor": .18},
        "Generic_wheel_spoke_gunmetal": {"baseColorFactor": [.50, .52, .54, 1], "metallicFactor": 1,
                                         "roughnessFactor": .19},
        "Generic_wheel_loop_pearl": {"baseColorFactor": [.76, .78, .80, 1], "metallicFactor": 1,
                                     "roughnessFactor": .16},
        # Rubber should have broad, subdued reflections, without a lacquer coat.
        "Generic_tyre_rubber": {"clearcoatFactor": 0},
        "Generic_rubber_black": {"clearcoatFactor": 0},
    }
    changes = []
    for index, material in enumerate(gltf["materials"]):
        profile = profiles.get(material.get("name"))
        if profile is None:
            continue
        before = material_summary(material)
        for key, value in profile.items():
            if key.startswith("clearcoat"):
                material.setdefault("extensions", {}).setdefault("KHR_materials_clearcoat", {})[key] = value
            else:
                material.setdefault("pbrMetallicRoughness", {})[key] = value
        changes.append({"index": index, "name": material["name"], "before": before,
                        "after": material_summary(material)})
    require(len(changes) == len(profiles), "Reference material profile is missing required surfaces")
    return changes


class Preparation:
    def __init__(self, model):
        self.model = model
        self.gltf = copy.deepcopy(model.gltf)
        self.binary = bytearray(model.binary)
        self.original_world, _ = world_matrices(self.gltf)
        self.donor_accessors = {}
        self.donor_views = {}
        self.cache = {}
        self.restored = []
        self.nonduplicate_donor_triangles_checked = 0
        self.splits = []
        self.attachments = {}

    def accessor(self, index):
        return Model.accessor(self, index)

    def by_name(self):
        return {n["name"]: i for i, n in enumerate(self.gltf["nodes"])}

    def append_bytes(self, data):
        self.binary.extend(b"\0" * (-len(self.binary) % 4))
        offset = len(self.binary)
        self.binary.extend(data)
        return offset

    def new_root_node(self, node):
        index = len(self.gltf["nodes"])
        self.gltf["nodes"].append(node)
        self.gltf["scenes"][self.gltf.get("scene", 0)].setdefault("nodes", []).append(index)
        return index

    def new_mesh_node(self, name, primitives, matrix):
        mesh = len(self.gltf["meshes"])
        self.gltf["meshes"].append({"name": name, "primitives": primitives})
        return self.new_root_node({"name": name, "mesh": mesh, "matrix": matrix})

    def set_parent(self, child_name, parent_name):
        by_name = self.by_name()
        child, parent = by_name[child_name], by_name[parent_name]
        world, parents = world_matrices(self.gltf)
        if parents.get(child) == parent:
            self.attachments[child_name] = parent_name
            return
        if child in parents:
            self.gltf["nodes"][parents[child]]["children"].remove(child)
        else:
            for scene in self.gltf["scenes"]:
                if child in scene.get("nodes", []):
                    scene["nodes"].remove(child)
        self.gltf["nodes"][parent].setdefault("children", []).append(child)
        node = self.gltf["nodes"][child]
        for field in ("translation", "rotation", "scale"):
            node.pop(field, None)
        node["matrix"] = multiply(inverse(world[parent]), world[child])
        self.attachments[child_name] = parent_name
        updated, _ = world_matrices(self.gltf)
        require(max(abs(a-b) for a,b in zip(world[child], updated[child])) < 1e-9,
                "Reparenting changed world transform: " + child_name)

    def subset_primitive(self, primitive, indices):
        require(indices and len(indices) % 3 == 0, "Invalid triangle subset")
        offset = self.append_bytes(struct.pack("<" + "I" * len(indices), *indices))
        view = len(self.gltf["bufferViews"])
        self.gltf["bufferViews"].append({"buffer": 0, "byteOffset": offset,
                                         "byteLength": len(indices) * 4, "target": 34963})
        accessor = len(self.gltf["accessors"])
        self.gltf["accessors"].append({"bufferView": view, "componentType": 5125,
                                       "count": len(indices), "type": "SCALAR",
                                       "min": [min(indices)], "max": [max(indices)]})
        result = copy.deepcopy(primitive)
        result["indices"] = accessor
        return result

    def split_primitives(self, node_name, classify):
        """Move existing triangles into named groups, sharing source vertex data."""
        index = self.by_name()[node_name]
        node = self.gltf["nodes"][index]
        mesh = self.gltf["meshes"][node["mesh"]]
        matrix = world_matrices(self.gltf)[0][index]
        remaining, extracted = [], {}
        before_triangles = after_triangles = 0
        for primitive in mesh["primitives"]:
            material = self.gltf["materials"][primitive["material"]]["name"]
            vertices = self.accessor(primitive["attributes"]["POSITION"])
            indices = [value[0] for value in self.accessor(primitive["indices"])]
            groups = {}
            for start in range(0, len(indices), 3):
                triangle = indices[start:start + 3]
                world_points = [point(matrix, vertices[k]) for k in triangle]
                groups.setdefault(classify(material, world_points), []).extend(triangle)
            before_triangles += len(indices) // 3
            for name, subset in groups.items():
                after_triangles += len(subset) // 3
                # Retain unchanged primitive/accessor whenever the whole primitive stays.
                selected = primitive if len(subset) == len(indices) else self.subset_primitive(primitive, subset)
                if name is None:
                    remaining.append(selected)
                else:
                    extracted.setdefault(name, []).append(selected)
        require(before_triangles == after_triangles, "Triangle partition did not conserve geometry")
        mesh["primitives"] = remaining
        require(remaining, "Splitting removed an entire source mesh unexpectedly")
        for name, primitives in extracted.items():
            by_name = self.by_name()
            if name in by_name:
                target = self.gltf["nodes"][by_name[name]]
                require("mesh" not in target, "Split target already renders geometry: " + name)
                target["matrix"] = matrix
                for field in ("translation", "rotation", "scale"):
                    target.pop(field, None)
                target["mesh"] = len(self.gltf["meshes"])
                self.gltf["meshes"].append({"name": name, "primitives": primitives})
            else:
                self.new_mesh_node(name, primitives, matrix)
            self.splits.append({"name": name, "from": node_name,
                                "triangles": sum(self.gltf["accessors"][p["indices"]]["count"] // 3
                                                 for p in primitives)})
            self.set_parent(name, node_name)

    def import_accessor(self, donor, index):
        if index in self.donor_accessors:
            return self.donor_accessors[index]
        accessor = copy.deepcopy(donor.gltf["accessors"][index])
        require("sparse" not in accessor, "Sparse donor accessor unsupported")
        view_index = accessor["bufferView"]
        if view_index not in self.donor_views:
            view = copy.deepcopy(donor.gltf["bufferViews"][view_index])
            require(view.get("buffer", 0) == 0, "External donor buffer")
            offset = view.get("byteOffset", 0)
            data = donor.binary[offset:offset + view["byteLength"]]
            require(len(data) == view["byteLength"], "Truncated donor bufferView")
            view["byteOffset"] = self.append_bytes(data)
            self.donor_views[view_index] = len(self.gltf["bufferViews"])
            self.gltf["bufferViews"].append(view)
        accessor["bufferView"] = self.donor_views[view_index]
        self.donor_accessors[index] = len(self.gltf["accessors"])
        self.gltf["accessors"].append(accessor)
        return self.donor_accessors[index]

    def restore_missing(self, donor, names):
        donor_world, _ = world_matrices(donor.gltf)
        by_name = {n["name"]: i for i,n in enumerate(donor.gltf["nodes"])}
        materials = {m["name"]: i for i,m in enumerate(self.gltf["materials"])}
        # Check semantic material + world triangle signatures against the updated
        # source before importing. This prevents a later export merging an overlay
        # under another name from silently causing double surfaces.
        def signature(points):
            return tuple(sorted(tuple(round(x, 5) for x in value) for value in points))
        needed_materials = {donor.gltf["materials"][p["material"]]["name"]
                            for name in names for p in donor.gltf["meshes"][
                                donor.gltf["nodes"][by_name[name]]["mesh"]]["primitives"]}
        source_triangles = {}
        for source_index, source_node in enumerate(self.model.gltf["nodes"]):
            if "mesh" not in source_node:
                continue
            for primitive in self.model.gltf["meshes"][source_node["mesh"]]["primitives"]:
                material_name = self.model.gltf["materials"][primitive["material"]]["name"]
                if material_name not in needed_materials:
                    continue
                vertices = self.model.accessor(primitive["attributes"]["POSITION"])
                indices = [value[0] for value in self.model.accessor(primitive["indices"])]
                signatures = source_triangles.setdefault(material_name, set())
                for start in range(0, len(indices), 3):
                    signatures.add(signature([point(self.original_world[source_index], vertices[k])
                                              for k in indices[start:start + 3]]))
        for name in names:
            require(name not in self.by_name(), "Donor would duplicate an existing source node: " + name)
            source_index = by_name[name]
            node = donor.gltf["nodes"][source_index]
            primitives = []
            for source in donor.gltf["meshes"][node["mesh"]]["primitives"]:
                material_name = donor.gltf["materials"][source["material"]]["name"]
                vertices = donor.accessor(source["attributes"]["POSITION"])
                indices = [value[0] for value in donor.accessor(source["indices"])]
                for start in range(0, len(indices), 3):
                    key = signature([point(donor_world[source_index], vertices[k]) for k in indices[start:start + 3]])
                    require(key not in source_triangles.get(material_name, set()),
                            "Donor would duplicate a merged source triangle: " + name)
                    self.nonduplicate_donor_triangles_checked += 1
                primitive = copy.deepcopy(source)
                primitive["attributes"] = {key: self.import_accessor(donor, value)
                                           for key, value in source["attributes"].items()}
                primitive["indices"] = self.import_accessor(donor, source["indices"])
                require("targets" not in source, "Unexpected donor morph targets")
                material = donor.gltf["materials"][source["material"]]
                require("Texture" not in json.dumps(material), "Donor restoration must not duplicate textures")
                material_name = material["name"]
                if material_name not in materials:
                    materials[material_name] = len(self.gltf["materials"])
                    self.gltf["materials"].append(copy.deepcopy(material))
                primitive["material"] = materials[material_name]
                primitives.append(primitive)
            self.new_mesh_node(name, primitives, donor_world[source_index])
            self.restored.append({"name": name, "kind": "renderable",
                                  "triangles": sum(donor.gltf["accessors"][p["indices"]]["count"] // 3
                                                   for p in donor.gltf["meshes"][node["mesh"]]["primitives"])})
        lights = "KHR_lights_punctual"
        source_lights = donor.gltf.get("extensions", {}).get(lights, {}).get("lights", [])
        for index, source in enumerate(donor.gltf["nodes"]):
            if lights not in source.get("extensions", {}) or source["name"] in self.by_name():
                continue
            node = copy.deepcopy(source)
            target = self.gltf.setdefault("extensions", {}).setdefault(lights, {}).setdefault("lights", [])
            node["extensions"][lights]["light"] = len(target)
            target.append(copy.deepcopy(source_lights[source["extensions"][lights]["light"]]))
            for field in ("translation", "rotation", "scale", "children"):
                node.pop(field, None)
            node["matrix"] = donor_world[index]
            self.new_root_node(node)
            self.restored.append({"name": node["name"], "kind": "punctual_light"})
        if lights not in self.gltf.setdefault("extensionsUsed", []):
            self.gltf["extensionsUsed"].append(lights)


def remap_exterior_indicator_surfaces(prep):
    """Use actual exterior amber lenses for both mutually exclusive lamp states.

    Detached donor LED strips sit behind opaque body/lens geometry. Reusing the
    visible source lens surface preserves its outline and ordinary depth testing.
    The runtime hides the matching OFF group while the ON emitter is visible.
    """
    gltf = prep.gltf
    led_material = next(i for i,m in enumerate(gltf["materials"])
                        if m["name"] == "Generic_indicator_amber_LED")
    for side, mirror in (("L", "MIRROR_LEFT"), ("R", "MIRROR_RIGHT")):
        off = "MIRROR_INDICATOR_" + side + "_OFF"
        prep.split_primitives(mirror, lambda material, points:
                              off if material == "Generic_indicator_amber_OFF" else None)

    result = []
    delta_triangles = 0
    def install(off_name, selected):
        nonlocal delta_triangles
        names = prep.by_name()
        off_index = names[off_name]
        off_node = gltf["nodes"][off_index]
        off_primitives = gltf["meshes"][off_node["mesh"]]["primitives"]
        world, parents = world_matrices(gltf)
        grouped = {}
        for primitive in off_primitives:
            indices = [value[0] for value in prep.accessor(primitive["indices"])]
            positions = prep.accessor(primitive["attributes"]["POSITION"])
            for start in range(0, len(indices), 3):
                triangle = indices[start:start + 3]
                points = [point(world[off_index], positions[k]) for k in triangle]
                grouped.setdefault(selected(points), []).extend(triangle)
            # Each source OFF primitive is assigned once, with unchanged winding.
            require(sum(len(v) for v in grouped.values()) >= len(indices), "Incomplete lens partition")
        for target_name, subset in grouped.items():
            require(len(off_primitives) == 1, "Expected one amber lens primitive per OFF group")
            source = off_primitives[0]
            all_indices = [value[0] for value in prep.accessor(source["indices"])]
            primitive = (copy.deepcopy(source) if subset == all_indices
                         else prep.subset_primitive(source, subset))
            primitive["material"] = led_material
            target = gltf["nodes"][names[target_name]]
            old_mesh = gltf["meshes"][target["mesh"]]
            old_count = sum(gltf["accessors"][p["indices"]]["count"] // 3 for p in old_mesh["primitives"])
            gltf["meshes"][target["mesh"]] = {"name": target_name, "primitives": [primitive]}
            parent_matrix = world[parents[names[target_name]]] if names[target_name] in parents else IDENTITY
            matrix = multiply(inverse(parent_matrix), world[off_index])
            for field in ("translation", "rotation", "scale"):
                target.pop(field, None)
            target["matrix"] = matrix
            count = len(subset) // 3
            delta_triangles += count - old_count
            result.append({"on": target_name, "off": off_name, "triangles": count,
                           "previous_recessed_emitter_triangles": old_count,
                           "source_vertex_attributes_shared": True,
                           "normal_depth_test": True})
        require(sum(len(v) for v in grouped.values()) ==
                sum(gltf["accessors"][p["indices"]]["count"] for p in off_primitives),
                "Exterior ON groups must cover the OFF lens exactly once")

    for side in ("L", "R"):
        install("FRONT_INDICATOR_" + side, lambda points, side=side: "FRONT_INDICATOR_" + side + "_ON")
        install("MIRROR_INDICATOR_" + side + "_OFF",
                lambda points, side=side: "MIRROR_INDICATOR_" + side + "_ON")
        def inner_name(points, side=side):
            x = abs(sum(p[0] for p in points) / 3)
            segment = 1 + sum(x >= boundary for boundary in (.325, .415, .505))
            return f"REAR_TURN_{side}_ON_{segment}"
        install("REAR_TURN_INNER_OFF_" + side, inner_name)
        def outer_name(points, side=side):
            x = abs(sum(p[0] for p in points) / 3)
            return f"REAR_TURN_{side}_ON_{5 if x < .685 else 6}"
        install("REAR_TURN_OFF_" + side, outer_name)
    require(len(result) == 16, "Missing front/mirror/rear exterior emitter surfaces")
    return result, delta_triangles


def verify_indicator_surfaces(model, contract):
    """Independently compare prepared world triangles for each ON/OFF pair."""
    from collections import Counter, defaultdict
    gltf = model.gltf
    world, _ = world_matrices(gltf)
    by_name = {node["name"]: i for i,node in enumerate(gltf["nodes"])}
    def triangles(name):
        index = by_name[name]
        node = gltf["nodes"][index]
        result = Counter()
        for primitive in gltf["meshes"][node["mesh"]]["primitives"]:
            vertices = model.accessor(primitive["attributes"]["POSITION"])
            indices = [value[0] for value in model.accessor(primitive["indices"])]
            for start in range(0, len(indices), 3):
                # Keep ordered winding as well as the actual exterior surface.
                points = tuple(tuple(round(x, 6) for x in point(world[index], vertices[k]))
                               for k in indices[start:start + 3])
                result[points] += 1
        return result
    grouped = defaultdict(list)
    for record in contract:
        grouped[record["off"]].append(record["on"])
        node = gltf["nodes"][by_name[record["on"]]]
        for primitive in gltf["meshes"][node["mesh"]]["primitives"]:
            material = gltf["materials"][primitive["material"]]
            require(material["name"] == "Generic_indicator_amber_LED" and
                    material.get("alphaMode", "OPAQUE") == "OPAQUE",
                    "Exterior indicator does not use opaque amber LED material")
    for off, on_names in grouped.items():
        emitted = Counter()
        for on in on_names:
            emitted.update(triangles(on))
        require(emitted == triangles(off), "ON/OFF exterior geometry or winding differs: " + off)
    require(len(grouped) == 8 and sum(map(len, grouped.values())) == 16,
            "Incomplete exterior indicator surface contract")
    return {"off_lens_groups": len(grouped), "on_emitter_groups": len(contract),
            "world_triangles_and_winding_match": True,
            "ordinary_opaque_amber_led_materials": True}


def remap_exterior_headlamp_projectors(prep):
    """Move powered projectors onto the actual visible OFF-projector surfaces."""
    gltf = prep.gltf
    led = next(i for i,m in enumerate(gltf["materials"])
               if m["name"] == "Generic_headlamp_projector_LED")
    result = []
    for side in ("L", "R"):
        housing = "HEADLAMP_" + side
        off = housing + "_OFF"
        on = housing + "_ON"
        prep.split_primitives(housing, lambda material, points, off=off:
                              off if material == "Generic_headlamp_projector_OFF" else None)
        names = prep.by_name()
        off_node, on_node = gltf["nodes"][names[off]], gltf["nodes"][names[on]]
        primitives = copy.deepcopy(gltf["meshes"][off_node["mesh"]]["primitives"])
        count = sum(gltf["accessors"][p["indices"]]["count"] // 3 for p in primitives)
        old_count = sum(gltf["accessors"][p["indices"]]["count"] // 3
                        for p in gltf["meshes"][on_node["mesh"]]["primitives"])
        require(count == old_count, "Projector surface replacement changed triangle count")
        for primitive in primitives:
            primitive["material"] = led
        gltf["meshes"][on_node["mesh"]] = {"name": on, "primitives": primitives}
        world, parents = world_matrices(gltf)
        parent = world[parents[names[on]]] if names[on] in parents else IDENTITY
        matrix = multiply(inverse(parent), world[names[off]])
        for field in ("translation", "rotation", "scale"):
            on_node.pop(field, None)
        on_node["matrix"] = matrix
        result.append({"on": on, "off": off, "triangles": count,
                       "previous_projector_recess_meters": .08,
                       "source_vertex_attributes_shared": True, "normal_depth_test": True})
    return result


def verify_headlamp_projectors(model, contract):
    from collections import Counter
    gltf = model.gltf
    world, _ = world_matrices(gltf)
    names = {node["name"]: i for i,node in enumerate(gltf["nodes"])}
    def triangles(name):
        index = names[name]
        node = gltf["nodes"][index]
        result = Counter()
        for primitive in gltf["meshes"][node["mesh"]]["primitives"]:
            positions = model.accessor(primitive["attributes"]["POSITION"])
            indices = [value[0] for value in model.accessor(primitive["indices"])]
            for start in range(0, len(indices), 3):
                result[tuple(tuple(round(x, 6) for x in point(world[index], positions[k]))
                             for k in indices[start:start + 3])] += 1
        return result
    require(len(contract) == 2, "Missing headlamp projector contract")
    for record in contract:
        require(triangles(record["on"]) == triangles(record["off"]),
                "Powered projector geometry/winding differs from the actual lens")
        node = gltf["nodes"][names[record["on"]]]
        for primitive in gltf["meshes"][node["mesh"]]["primitives"]:
            material = gltf["materials"][primitive["material"]]
            require(material["name"] == "Generic_headlamp_projector_LED" and
                    material.get("alphaMode", "OPAQUE") == "OPAQUE",
                    "Projector does not use the ordinary opaque white LED material")
    return {"off_projectors": 2, "on_projectors": 2, "world_triangles_and_winding_match": True,
            "ordinary_opaque_white_led_materials": True, "drl_geometry_unchanged": True}


def remap_exterior_reverse_lenses(prep):
    """Expose reverse LEDs on the actual white lenses, outside the red shell."""
    gltf = prep.gltf
    led = next(i for i,m in enumerate(gltf["materials"])
               if m["name"] == "Generic_reverse_white_LED")
    result, delta_triangles = [], 0
    for side in ("L", "R"):
        off, on = "REAR_REVERSE_OFF_" + side, "REAR_REVERSE_ON_" + side
        names = prep.by_name()
        off_node, on_node = gltf["nodes"][names[off]], gltf["nodes"][names[on]]
        primitives = copy.deepcopy(gltf["meshes"][off_node["mesh"]]["primitives"])
        count = sum(gltf["accessors"][p["indices"]]["count"] // 3 for p in primitives)
        old_count = sum(gltf["accessors"][p["indices"]]["count"] // 3
                        for p in gltf["meshes"][on_node["mesh"]]["primitives"])
        for primitive in primitives:
            primitive["material"] = led
        gltf["meshes"][on_node["mesh"]] = {"name": on, "primitives": primitives}
        world, parents = world_matrices(gltf)
        parent = world[parents[names[on]]] if names[on] in parents else IDENTITY
        matrix = multiply(inverse(parent), world[names[off]])
        for field in ("translation", "rotation", "scale"):
            on_node.pop(field, None)
        on_node["matrix"] = matrix
        delta_triangles += count - old_count
        result.append({"on": on, "off": off, "triangles": count,
                       "previous_recessed_emitter_triangles": old_count,
                       "source_vertex_attributes_shared": True, "normal_depth_test": True})
    return result, delta_triangles


def verify_reverse_lenses(model, contract):
    from collections import Counter
    gltf = model.gltf
    world, _ = world_matrices(gltf)
    names = {node["name"]: i for i,node in enumerate(gltf["nodes"])}
    def triangles(name):
        index = names[name]
        node = gltf["nodes"][index]
        result = Counter()
        for primitive in gltf["meshes"][node["mesh"]]["primitives"]:
            positions = model.accessor(primitive["attributes"]["POSITION"])
            indices = [value[0] for value in model.accessor(primitive["indices"])]
            for start in range(0, len(indices), 3):
                result[tuple(tuple(round(x, 6) for x in point(world[index], positions[k]))
                             for k in indices[start:start + 3])] += 1
        return result
    require(len(contract) == 2, "Missing reverse lens contract")
    for record in contract:
        require(triangles(record["on"]) == triangles(record["off"]),
                "Powered reverse geometry/winding differs from actual exterior lens")
        node = gltf["nodes"][names[record["on"]]]
        for primitive in gltf["meshes"][node["mesh"]]["primitives"]:
            material = gltf["materials"][primitive["material"]]
            require(material["name"] == "Generic_reverse_white_LED" and
                    material.get("alphaMode", "OPAQUE") == "OPAQUE",
                    "Reverse emitter must use ordinary opaque white LED material")
    return {"off_lenses": 2, "on_emitters": 2, "world_triangles_and_winding_match": True,
            "ordinary_opaque_white_led_materials": True, "red_shells_preserved": True}


INTERIOR_MATERIAL_PREFIXES = ("Generic_interior_", "Generic_dash_")
INTERIOR_PARENT_NODES = ("BODY", "Door_FL", "Door_FR", "Door_BL", "Door_BR", "Door_Trunk")


def split_interior_shadow_groups(prep):
    """Separate only complete, semantically unambiguous interior primitives."""
    result = []
    for parent_name in INTERIOR_PARENT_NODES:
        name = parent_name + "_INTERIOR"
        prep.split_primitives(parent_name, lambda material, points, name=name:
                              name if material.startswith(INTERIOR_MATERIAL_PREFIXES) else None)
        node = prep.gltf["nodes"][prep.by_name()[name]]
        node.setdefault("extras", {})["shadowRole"] = "interior"
        primitives = prep.gltf["meshes"][node["mesh"]]["primitives"]
        result.append({"name": name, "parent": parent_name, "shadowRole": "interior",
                       "materials": [prep.gltf["materials"][p["material"]]["name"] for p in primitives],
                       "triangles": sum(prep.gltf["accessors"][p["indices"]]["count"] // 3 for p in primitives),
                       "source_attributes_indices_and_winding_preserved": True})
    return result


def verify_interior_shadow_groups(source, prepared, groups):
    source_names = {n["name"]: i for i,n in enumerate(source.gltf["nodes"])}
    prepared_names = {n["name"]: i for i,n in enumerate(prepared.gltf["nodes"])}
    source_world, _ = world_matrices(source.gltf)
    prepared_world, prepared_parents = world_matrices(prepared.gltf)
    require(len(groups) == len(INTERIOR_PARENT_NODES), "Incomplete interior shadow groups")
    total_triangles = 0
    for group in groups:
        source_index = source_names[group["parent"]]
        source_node = source.gltf["nodes"][source_index]
        expected = [p for p in source.gltf["meshes"][source_node["mesh"]]["primitives"]
                    if source.gltf["materials"][p["material"]]["name"].startswith(INTERIOR_MATERIAL_PREFIXES)]
        index = prepared_names[group["name"]]
        node = prepared.gltf["nodes"][index]
        require(node.get("extras", {}).get("shadowRole") == "interior", "Missing shadowRole contract")
        require(prepared_parents[index] == prepared_names[group["parent"]], "Interior movement parent changed")
        require(max(abs(a-b) for a,b in zip(source_world[source_index], prepared_world[index])) < 1e-9,
                "Interior source world transform changed")
        actual = prepared.gltf["meshes"][node["mesh"]]["primitives"]
        require(len(actual) == len(expected), "Interior primitive count changed")
        for old, new in zip(expected, actual):
            require(old["attributes"] == new["attributes"] and old["indices"] == new["indices"] and
                    old["material"] == new["material"], "Interior geometry, winding or material changed")
        total_triangles += sum(prepared.gltf["accessors"][p["indices"]]["count"] // 3 for p in actual)
    return {"renderable_groups": len(groups), "interior_triangles": total_triangles,
            "source_attributes_indices_materials_and_world_transforms_preserved": True,
            "no_generic_black_plastic_or_exterior_materials_moved": True}


def prepare(source, output, manifest_path, components, legacy_source):
    require(source.resolve() != output.resolve(), "Output must differ from the untouched source")
    require(legacy_source.resolve() != output.resolve(), "Output must differ from the donor source")
    model, donor = Model(source), Model(legacy_source)
    expected = component_names(components)
    # Validate every attribute, not just triangle POSITION/index accessors.
    for candidate in (model, donor):
        for index in range(len(candidate.gltf["accessors"])):
            candidate.accessor(index)
    before, donor_audit = audit(model), audit(donor)
    source_components = component_report(model.gltf, expected)
    donor_components = component_report(donor.gltf, expected)
    require(not donor_components["missing"], "Donor does not satisfy modelpoints")
    prep = Preparation(model)
    gltf = prep.gltf
    # The updated export has physically merged mirrors with doors. Recover their
    # identity by partitioning exactly matching world triangles, never by adding
    # another copy of the mirror. All vertex attributes remain source-authored.
    donor_world, _ = world_matrices(donor.gltf)
    def triangle_key(points):
        return tuple(sorted(tuple(round(x, 5) for x in value) for value in points))
    for name, door in (("MIRROR_LEFT", "Door_FL"), ("MIRROR_RIGHT", "Door_FR")):
        donor_index = next(i for i,n in enumerate(donor.gltf["nodes"]) if n["name"] == name)
        keys = {}
        for primitive in donor.gltf["meshes"][donor.gltf["nodes"][donor_index]["mesh"]]["primitives"]:
            material = donor.gltf["materials"][primitive["material"]]["name"]
            vertices = donor.accessor(primitive["attributes"]["POSITION"])
            indices = [v[0] for v in donor.accessor(primitive["indices"])]
            keys.setdefault(material, set()).update(triangle_key([point(donor_world[donor_index], vertices[k])
                for k in indices[start:start+3]]) for start in range(0, len(indices), 3))
        prep.split_primitives(door, lambda material, pts: name if triangle_key(pts) in keys.get(material, set()) else None)
        record = next(r for r in prep.splits if r["name"] == name)
        expected_triangles = sum(donor.gltf["accessors"][p["indices"]]["count"] // 3
                                for p in donor.gltf["meshes"][donor.gltf["nodes"][donor_index]["mesh"]]["primitives"])
        require(record["triangles"] == expected_triangles, "Not all merged mirror triangles recovered: " + name)
    def trunk_group(material, points):
        if material == "Generic_reverse_white_OFF":
            require(all(p[0] > 0 for p in points) or all(p[0] < 0 for p in points), "Reverse lens crosses center")
            return "REAR_REVERSE_OFF_" + ("L" if points[0][0] > 0 else "R")
        if material == "Generic_indicator_amber_OFF":
            require(all(p[0] > 0 for p in points) or all(p[0] < 0 for p in points), "Indicator lens crosses center")
            return "REAR_TURN_INNER_OFF_" + ("L" if points[0][0] > 0 else "R")
        if material == "Generic_tail_lens_red_OFF" and min(p[1] for p in points) > 1.4:
            return "HIGH_STOP_OFF"
        return None
    prep.split_primitives("Door_Trunk", trunk_group)
    # Missing powered overlays were removed from the updated export, rather than
    # merged: their emissive material definitions are absent as well. Static
    # mirrors / OFF lenses are recovered above, and must never be donor-added.
    missing_overlays = [name for name in expected if name not in prep.by_name() and
                        ("_ON" in name or name == "REAR_TAIL_BAR")]
    prep.restore_missing(donor, missing_overlays)
    # SUNROOF is the transform contract. In the new two-piece export, the front
    # SUNROOF.001 slides over the fixed rear piece; moving both would leave a hole.
    by_name = prep.by_name()
    require("SUNROOF.001" in by_name, "Expected updated two-piece sunroof export")
    rear = gltf["nodes"][by_name["SUNROOF"]]
    fixed = {key: copy.deepcopy(value) for key,value in rear.items() if key != "name"}
    fixed["name"] = "SUNROOF_REAR_FIXED"
    prep.new_root_node(fixed)
    rear.pop("mesh")
    rear["extras"] = {"slideAxis": "z", "travelMeters": -.7,
                       "role": "front sliding panel group; rear glass remains fixed"}
    prep.set_parent("SUNROOF.001", "SUNROOF")
    # Metadata removed by the re-export is explicit and no longer guessed at runtime.
    gltf["nodes"][prep.by_name()["CHARGE_PORT"]]["extras"] = {"hingeAxis": "y", "openAngleDegrees": 100.}
    attachments = {"MIRROR_INDICATOR_L_ON": "MIRROR_LEFT", "MIRROR_INDICATOR_R_ON": "MIRROR_RIGHT"}
    attachments.update({name: "Door_Trunk" for name in prep.by_name() if
                       name.startswith("REAR_REVERSE_") or name.startswith("HIGH_STOP_") or
                       name in {"REAR_LAMP_CLUSTER_L", "REAR_LAMP_CLUSTER_R", "REAR_TAIL_BAR", "REAR_BRAKE_LIGHT"} or
                       (name.startswith("REAR_TURN_") and name.rsplit("_", 1)[-1] in {"1", "2", "3", "4"})})
    for child, parent in attachments.items():
        prep.set_parent(child, parent)
    # New fender lens positions were moved without their old LED/glow overlays.
    # Align each surface to the actual new lens, including its outward-facing side.
    alignment = []
    for side, outward in (("L", 1.), ("R", -1.)):
        off_name = "FENDER_INDICATOR_" + side
        records = {r["name"]: r for r in before["nodes"]}
        target = [(a+b)/2 for a,b in zip(records[off_name]["world_bounds"]["min"], records[off_name]["world_bounds"]["max"])]
        for suffix in ("_ON", "_GLOW_ON"):
            name = off_name + suffix
            original = records[name]["world_bounds"]
            center = [(a+b)/2 for a,b in zip(original["min"], original["max"])]
            delta = [a-b for a,b in zip(target, center)]
            delta[0] += .001 * outward
            node = gltf["nodes"][prep.by_name()[name]]
            matrix = local_matrix(node)
            matrix[12:15] = [a+b for a,b in zip(matrix[12:15], delta)]
            for field in ("translation", "rotation", "scale"):
                node.pop(field, None)
            node["matrix"] = matrix
            alignment.append({"name": name, "translation_world": delta, "reason": "match updated authored fender lens"})
    indicator_surfaces, indicator_triangle_delta = remap_exterior_indicator_surfaces(prep)
    projector_surfaces = remap_exterior_headlamp_projectors(prep)
    reverse_surfaces, reverse_triangle_delta = remap_exterior_reverse_lenses(prep)
    interior_groups = split_interior_shadow_groups(prep)
    refinements = apply_reference_materials(gltf)
    # Lazy same-directory loading also supports library callers using importlib.
    import importlib.util
    helper_spec = importlib.util.spec_from_file_location("vehicle_tyre_materials", Path(__file__).with_name("vehicle_tyre_materials.py"))
    helper = importlib.util.module_from_spec(helper_spec)
    helper_spec.loader.exec_module(helper)
    tyre_refinements = helper.apply_tyre_refinements(prep)
    battery_materials = {p["material"] for p in gltf["meshes"][gltf["nodes"][prep.by_name()["DRIVETRAIN_BATTERY"]]["mesh"]]["primitives"]}
    source_material_count = len(gltf["materials"])
    normal_materials = copy.deepcopy(gltf["materials"])
    xray_materials = {}
    for index, material in enumerate(normal_materials):
        if index not in battery_materials and material.get("alphaMode", "OPAQUE") != "BLEND":
            transparent = copy.deepcopy(material)
            transparent["name"] = material["name"] + "__BatteryXray"
            transparent["alphaMode"] = "BLEND"
            transparent.pop("alphaCutoff", None)
            xray_materials[index] = len(gltf["materials"])
            gltf["materials"].append(transparent)
        else:
            xray_materials[index] = index
    variants = "KHR_materials_variants"
    gltf.setdefault("extensions", {})[variants] = {"variants": [{"name": "Normal"}, {"name": "BatteryXray"}]}
    if variants not in gltf.setdefault("extensionsUsed", []):
        gltf["extensionsUsed"].append(variants)
    for mesh in gltf["meshes"]:
        for primitive in mesh["primitives"]:
            original = primitive["material"]
            primitive.setdefault("extensions", {})[variants] = {"mappings": [
                {"material": original, "variants": [0]}, {"material": xray_materials[original], "variants": [1]}]}
    prep.binary.extend(b"\0" * (-len(prep.binary) % 4))
    gltf["buffers"][0]["byteLength"] = len(prep.binary)
    encoded = json.dumps(gltf, separators=(",", ":"), ensure_ascii=False).encode()
    encoded += b" " * (-len(encoded) % 4)
    payload = (struct.pack("<III", 0x46546c67, 2, 28 + len(encoded) + len(prep.binary)) +
               struct.pack("<II", len(encoded), 0x4e4f534a) + encoded +
               struct.pack("<II", len(prep.binary), 0x004e4942) + prep.binary)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(payload)
    bundled = Model(output)
    after = audit(bundled)
    exterior_verification = verify_indicator_surfaces(bundled, indicator_surfaces)
    projector_verification = verify_headlamp_projectors(bundled, projector_surfaces)
    reverse_verification = verify_reverse_lenses(bundled, reverse_surfaces)
    tyre_verification = helper.verify_tyre_refinements(bundled, tyre_refinements)
    interior_verification = verify_interior_shadow_groups(model, bundled, interior_groups)
    bundled_components = component_report(bundled.gltf, expected)
    require(not bundled_components["missing"], "Prepared asset is missing modelpoints identities")
    require(bundled.binary[:len(model.binary)] == model.binary, "Source BIN geometry/textures changed")
    require(bundled.gltf["images"] == model.gltf["images"] and bundled.gltf["textures"] == model.gltf["textures"],
            "Source texture/image mappings changed")
    require(source.read_bytes() == model.raw and legacy_source.read_bytes() == donor.raw, "User source changed")
    expected_triangles = (before["counts"]["triangles"] + sum(r.get("triangles", 0) for r in prep.restored) +
                          indicator_triangle_delta + reverse_triangle_delta)
    require(after["counts"]["triangles"] == expected_triangles,
            "Triangle count differs from source, declared additions and lens-surface replacements")
    after_world, _ = world_matrices(bundled.gltf)
    aligned = {r["name"] for r in alignment}
    for index, matrix in prep.original_world.items():
        if model.gltf["nodes"][index]["name"] not in aligned:
            require(max(abs(a-b) for a,b in zip(matrix, after_world[index])) < 1e-9,
                    "An original rest transform changed")
    for original, updated in zip(model.gltf["materials"], bundled.gltf["materials"]):
        require(original.get("alphaMode", "OPAQUE") == updated.get("alphaMode", "OPAQUE"), "Source alpha mode changed")
        require(original.get("pbrMetallicRoughness", {}).get("baseColorFactor", [1,1,1,1])[3] ==
                updated.get("pbrMetallicRoughness", {}).get("baseColorFactor", [1,1,1,1])[3], "Source alpha value changed")
    for mesh in bundled.gltf["meshes"]:
        for primitive in mesh["primitives"]:
            index = primitive["material"]
            mappings = primitive["extensions"][variants]["mappings"]
            require(mappings == [{"material": index, "variants": [0]}, {"material": xray_materials[index], "variants": [1]}],
                    "Invalid primitive variant mapping")
            material = bundled.gltf["materials"][xray_materials[index]]
            require(material.get("alphaMode", "OPAQUE") == ("OPAQUE" if index in battery_materials else "BLEND"),
                    "Incorrect x-ray blend mode")
    report = {"modelpoints": str(components), "modelpoints_sha256": hashlib.sha256(components.read_bytes()).hexdigest(),
              "new_source": source_components, "legacy_source": donor_components, "bundled": bundled_components}
    groups = {"movable_parts": PARTS,
              "drivetrain": [n for n in prep.by_name() if n.startswith("DRIVETRAIN_")],
              "mixed_rear_overlays": ["REAR_BRAKE_ON", "REAR_BRAKE_GLOW_ON", "REAR_TAIL_ON"]}
    manifest = {"source": {"path": str(source), **{k: before[k] for k in ("sha256", "bytes", "counts", "world_bounds")}},
                "donor": {"path": str(legacy_source), **{k: donor_audit[k] for k in ("sha256", "bytes", "counts")}},
                "bundled": {k: after[k] for k in ("sha256", "bytes", "counts", "world_bounds")},
                "orientation": {"up": "+Y", "front": "+Z", "left": "+X", "ground_y": 0.},
                "motion_contract": "currentLocal = authoredRest * localMotion; front sunroof translates, rear glass stays fixed",
                "accessories_reparented_preserving_world": prep.attachments, "groups": groups,
                "source_merged_geometry_recovered": prep.splits, "donor_additions": prep.restored,
                "source_binary_preserved_prefix_bytes": len(model.binary),
                "appended_donor_buffer_views": len(prep.donor_views),
                "donor_triangles_checked_for_source_overlap": prep.nonduplicate_donor_triangles_checked,
                "lamp_alignment_corrections": alignment, "reference_material_refinements": refinements,
                "exterior_indicator_surfaces": indicator_surfaces,
                "exterior_indicator_verification": exterior_verification,
                "exterior_headlamp_projectors": projector_surfaces,
                "exterior_headlamp_verification": projector_verification,
                "exterior_reverse_surfaces": reverse_surfaces,
                "exterior_reverse_verification": reverse_verification,
                "reverse_surface_triangle_delta": reverse_triangle_delta,
                "tyre_refinements": tyre_refinements,
                "tyre_verification": tyre_verification,
                "interior_shadow_groups": interior_groups,
                "interior_shadow_verification": interior_verification,
                "indicator_surface_triangle_delta": indicator_triangle_delta,
                "body_blend_material_indices": sorted({xray_materials[i] for i in range(source_material_count) if i not in battery_materials}),
                "opaque_battery_material_indices": sorted(battery_materials),
                "material_variants": [
                    {"index": 0, "name": "Normal", "material_by_original_index": {i: i for i in range(source_material_count)}},
                    {"index": 1, "name": "BatteryXray", "material_by_original_index": xray_materials}],
                "material_defaults": [{"index": i, "name": m.get("name"), **material_summary(m)} for i,m in enumerate(bundled.gltf["materials"])],
                "modelpoints_verification": report, "nodes": after["nodes"],
                "checks": {"new_source_binary_prefix_unchanged": True, "source_textures_unchanged": True,
                           "both_user_source_files_untouched": True, "source_alpha_modes_and_values_preserved": True,
                           "source_rest_transforms_preserved_except_documented_lamp_alignment": True,
                           "merged_triangle_partitions_conserve_source_geometry": True,
                           "all_donor_triangles_absent_from_updated_source": True,
                           "prepared_triangles_equal_source_plus_declared_additions_and_surface_replacements": True,
                           "indicator_on_geometry_matches_exterior_off_lenses": True,
                           "headlamp_on_geometry_matches_exterior_off_projectors": True,
                           "reverse_on_geometry_matches_exterior_off_lenses": True,
                           "tyre_partition_preserves_geometry_and_wheel_metal_materials": True,
                           "interior_primitives_split_without_geometry_or_movement_changes": True,
                           "all_modelpoints_names_resolved": True, "all_primitives_have_normal_and_xray_mappings": True,
                           "emulator_or_device_run": "not performed"},
                "notes": ["GenericSUV_new.glb is the active geometry and texture source; it omits 40 modelpoints names before preparation",
                          "Mirrors, reverse OFF lenses, high-stop OFF and inner/mirror indicator OFF lenses are partitioned from NEW",
                          "Front/mirror/rear amber ON emitters reuse exact exterior OFF lens surfaces in mutually exclusive states",
                          "Recessed donor indicator emitters are replaced; ordinary depth tests remain enabled and red lens shells stay intact",
                          "Headlamp ON projectors reuse exact exterior HEADLAMP_L/R_OFF surfaces; clear glass/housings and DRL remain intact",
                          "Reverse white ON emitters reuse exact exterior REAR_REVERSE_OFF_L/R lens surfaces outside the opaque red shell",
                          "Six *_INTERIOR child renderables carry extras.shadowRole=interior; only complete interior/dash material primitives move",
                          "REAR_LAMP_CLUSTER_L/R are authored empty logical groups; their lens geometry remains in Door_Trunk",
                          "Only declared absent ON/glow overlays, tail-bar lens and three punctual lights are restored from the old donor",
                          "Normal retains opaque solids and source transparent glass/glows; BatteryXray uses BLEND clones, opaque battery",
                          "Reference paint/chrome/rubber PBR changes are derivative-only; real appearance still needs the user's device test",
                          "SUNROOF controls the front SUNROOF.001 only; SUNROOF_REAR_FIXED retains the rear glass",
                          "Fender powered strips are aligned to the moved NEW lens; their stored binary vertices stay untouched",
                          "REAR_TURN_OFF_L/R are body-fixed outer lenses; REAR_TURN_INNER_OFF_L/R follow the trunk",
                          "Main rear red lenses and canonical tail/brake overlays are restored to the pre-full-cluster source/donor geometry",
                          "Mixed canonical REAR_BRAKE_ON/REAR_TAIL_ON overlays must hide while the trunk is open",
                          "Full-cluster auxiliary nodes and independent main-red calibration materials are absent; upper stop is preserved",
                          "Tyre refinements split existing rubber indices into satin sidewalls and rough tread without adding geometry or textures"]}
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.with_name("generic-suv-source-audit.json").write_text(json.dumps(before, indent=2) + "\n")
    manifest_path.with_name("generic-suv-bundled-audit.json").write_text(json.dumps(after, indent=2) + "\n")
    manifest_path.with_name("generic-suv-modelpoints-verification.json").write_text(json.dumps(report, indent=2) + "\n")
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Prepared NEW GenericSUV: {after['counts']['triangles']:,} triangles; {len(prep.restored)} donor additions; "
          f"all {len(expected)} modelpoints resolved; source files untouched")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--components", type=Path, required=True)
    parser.add_argument("--legacy-source", type=Path, required=True,
                        help="Complete old source used only for absent powered overlays and lights")
    args = parser.parse_args()
    prepare(args.source, args.output, args.manifest, args.components, args.legacy_source)


if __name__ == "__main__":
    main()
