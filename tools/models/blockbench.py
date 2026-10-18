#!/usr/bin/env python3
"""Turns War of the Gods' box models into Blockbench models and back, so they can be edited by hand in Blockbench.

    python3 tools/models/blockbench.py export [NAME ...]
    python3 tools/models/blockbench.py import [FILE.bbmodel ...] [--into DIR]

export writes each model (all of them when none is named) with its texture, and its team mask when it has one, into
tools/models/blockbench/NAME.bbmodel. Open that in Blockbench (it is a "Modded Entity" with box UV), move, resize, add
or remove cubes and groups, paint the texture, and save it.

import turns .bbmodel files (all of tools/models/blockbench/ when none is named) back into the mod's model JSON,
texture and team mask under src/main/resources/assets/warofthegods/, and for Zamorak's staff and Silverlight their
inventory icon, drawn from the model (that part needs Pillow and numpy). The file's name is the model's name. With
--into DIR the files go under DIR instead, laid out as a resource pack's assets/warofthegods/ folder is, so a pack
there shows the change in the game after F3+T without building the mod.

In Blockbench:
- Each group is one of the model's parts, turning about the group's pivot. The game moves parts by their names (the
  demon's "jaw", the centaur's "tail"), so keep those names; new groups just stand still.
- Every cube must be in a group, and cubes cannot be turned on their own: turn their group.
- A group whose name ends in _glow glows in the dark (Silverlight's gem).
- The texture named NAME_team.png is the team mask: white where the team's color is painted over. Paint it as well
  when you move a team-colored area, and keep both textures the size they are.

Only the conversion itself uses nothing beyond Python, so a build can run it without anything installed.
"""

import argparse
import base64
import json
import os
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "warofthegods")
BLOCKBENCH = os.path.join(HERE, "blockbench")
NAMES = ("lesser_demon", "centaur", "zamorak_staff", "silverlight")
ICONS = ("zamorak_staff", "silverlight")
GLOW = "_glow"
# Blockbench's models stand on y = 0 with y up; the game's hang from y = 24 with y down, x the other way about.
FEET = 24.0
NAMESPACE = uuid.UUID("5f0c8a1e-7d3b-4c61-9a2e-0b6d1f4e8c27")


def paths(root, name):
    return {
        "model": os.path.join(root, "models", name + ".json"),
        "texture": os.path.join(root, "textures", "models", name + ".png"),
        "team": os.path.join(root, "textures", "models", name + "_team.png"),
        "icon": os.path.join(root, "textures", "items", name + ".png"),
    }


def number(value):
    """A coordinate as the generator writes it: whole numbers as ints, never -0."""
    value = round(float(value), 4) + 0.0
    return int(value) if value == int(value) else value


def stable_uuid(*parts):
    return str(uuid.uuid5(NAMESPACE, "/".join(str(p) for p in parts)))


def data_url(path):
    with open(path, "rb") as file:
        return "data:image/png;base64," + base64.b64encode(file.read()).decode("ascii")


def box_faces(u, v, w, h, d, mirror):
    """The faces of a box-UV cube, where Blockbench lays them out in the texture."""
    faces = {
        "north": [u + d, v + d, u + d + w, v + d + h],
        "east": [u, v + d, u + d, v + d + h],
        "south": [u + d + w + d, v + d, u + d + w + d + w, v + d + h],
        "west": [u + d + w, v + d, u + d + w + d, v + d + h],
        "up": [u + d + w, v + d, u + d, v],
        "down": [u + d + w + w, v, u + d + w, v + d],
    }
    if mirror:
        faces["east"], faces["west"] = faces["west"], faces["east"]
        for face in faces.values():
            face[0], face[2] = face[2], face[0]
    return {side: {"uv": uv, "texture": 0} for side, uv in faces.items()}


# ---------------------------------------------------------------- export


def to_blockbench(name, model, texture, team=None):
    """The model's JSON as a Blockbench project, with its texture and team mask (paths to PNGs) inside it."""
    tex_w, tex_h = model["texture"]
    elements = []
    groups = {}
    origins = {}
    outliner = []
    for part in model["parts"]:
        px, py, pz = part["pivot"]
        parent = part["parent"]
        if parent is None:
            origin = [-px, FEET - py, pz]
        else:
            base = origins[parent]
            origin = [base[0] - px, base[1] - py, base[2] + pz]
        origin = [number(c) for c in origin]
        origins[part["name"]] = origin
        rx, ry, rz = part["rotation"]
        group = {
            "name": part["name"] + (GLOW if part.get("glow") else ""),
            "origin": origin,
            "rotation": [number(-rx * 57.29577951308232), number(-ry * 57.29577951308232),
                         number(rz * 57.29577951308232)],
            "color": 0,
            "uuid": stable_uuid(name, "group", part["name"]),
            "export": True,
            "mirror_uv": False,
            "isOpen": False,
            "locked": False,
            "visibility": True,
            "autouv": 0,
            "children": [],
        }
        for number_in_part, box in enumerate(part["boxes"]):
            fx, fy, fz = box["from"]
            w, h, d = box["size"]
            u, v = box["uv"]
            cube_uuid = stable_uuid(name, "cube", part["name"], number_in_part)
            elements.append({
                "name": "%s_%d" % (part["name"], number_in_part),
                "box_uv": True,
                "rescale": False,
                "locked": False,
                "render_order": "default",
                "allow_mirror_modeling": True,
                "from": [number(origin[0] - fx - w), number(origin[1] - fy - h), number(origin[2] + fz)],
                "to": [number(origin[0] - fx), number(origin[1] - fy), number(origin[2] + fz + d)],
                "autouv": 0,
                "color": 0,
                "inflate": box.get("inflate", 0.0),
                "mirror_uv": box.get("mirror", False),
                "origin": origin,
                "uv_offset": [u, v],
                "faces": box_faces(u, v, w, h, d, box.get("mirror", False)),
                "type": "cube",
                "uuid": cube_uuid,
            })
            group["children"].append(cube_uuid)
        groups[part["name"]] = group
        if parent is None:
            outliner.append(group)
        else:
            groups[parent]["children"].append(group)
    textures = []
    for texture_name, path in ((name + ".png", texture), (name + "_team.png", team)):
        if path is None:
            continue
        textures.append({
            "path": "",
            "name": texture_name,
            "folder": "",
            "namespace": "",
            "id": str(len(textures)),
            "width": tex_w,
            "height": tex_h,
            "uv_width": tex_w,
            "uv_height": tex_h,
            "particle": False,
            "render_mode": "default",
            "render_sides": "auto",
            "visible": True,
            "mode": "bitmap",
            "saved": True,
            "uuid": stable_uuid(name, "texture", texture_name),
            "source": data_url(path),
        })
    return {
        "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
        "name": name,
        "model_identifier": name,
        "modded_entity_version": "1.12",
        "visible_box": [1, 1, 0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "unhandled_root_fields": {},
        "resolution": {"width": tex_w, "height": tex_h},
        "elements": elements,
        "outliner": outliner,
        "textures": textures,
    }


def export(names):
    os.makedirs(BLOCKBENCH, exist_ok=True)
    for name in names:
        files = paths(ASSETS, name)
        with open(files["model"]) as file:
            model = json.load(file)
        team = files["team"] if os.path.exists(files["team"]) else None
        project = to_blockbench(name, model, files["texture"], team)
        out = os.path.join(BLOCKBENCH, name + ".bbmodel")
        with open(out, "w") as file:
            json.dump(project, file, indent=1)
            file.write("\n")
        print("%s: %d groups, %d cubes -> %s" % (name, len(model["parts"]), len(project["elements"]),
                                                 os.path.relpath(out)))


# ---------------------------------------------------------------- import


def from_blockbench(project):
    """The mod's model JSON from a Blockbench project, and its textures' PNG bytes by name."""
    width = int(project.get("resolution", {}).get("width", 64))
    height = int(project.get("resolution", {}).get("height", 64))
    cubes = {element["uuid"]: element for element in project.get("elements", [])
             if element.get("type", "cube") == "cube"}
    # Blockbench 5 keeps each group's settings in "groups" and only its place in "outliner"; older ones keep both
    # in "outliner".
    settings = {group["uuid"]: group for group in project.get("groups", [])}
    used = set()
    parts = []

    def walk(node, parent, parent_origin):
        if not isinstance(node, dict):
            raise SystemExit("a cube is not in any group; put every cube in a group")
        group = dict(settings.get(node.get("uuid"), {}))
        group.update(node)
        name = group["name"]
        glow = name.endswith(GLOW)
        if glow:
            name = name[:-len(GLOW)]
        origin = [float(c) for c in group.get("origin", [0, 0, 0])]
        if parent is None:
            pivot = [-origin[0], FEET - origin[1], origin[2]]
        else:
            pivot = [parent_origin[0] - origin[0], parent_origin[1] - origin[1], origin[2] - parent_origin[2]]
        rotation = [float(c) for c in group.get("rotation", [0, 0, 0])]
        part = {
            "name": name,
            "parent": parent,
            "pivot": [float(number(c)) for c in pivot],
            "rotation": [round(-rotation[0] / 57.29577951308232, 5) + 0.0,
                         round(-rotation[1] / 57.29577951308232, 5) + 0.0,
                         round(rotation[2] / 57.29577951308232, 5) + 0.0],
            "glow": glow,
            "boxes": [],
        }
        if any(p["name"] == name for p in parts):
            raise SystemExit("two groups are named %s; every group needs its own name" % name)
        parts.append(part)
        children = []
        for child in group.get("children", []):
            if isinstance(child, dict):
                children.append(child)
                continue
            cube = cubes.get(child)
            if cube is None:
                continue
            used.add(child)
            if any(abs(float(a)) > 1e-6 for a in cube.get("rotation", [0, 0, 0])):
                raise SystemExit("cube %s in %s is turned on its own; turn its group instead"
                                 % (cube.get("name"), name))
            start = [float(c) for c in cube["from"]]
            end = [float(c) for c in cube["to"]]
            size = [int(round(end[i] - start[i])) for i in range(3)]
            uv = cube.get("uv_offset", [0, 0])
            part["boxes"].append({
                "uv": [int(uv[0]), int(uv[1])],
                "from": [number(origin[0] - end[0]), number(origin[1] - end[1]), number(start[2] - origin[2])],
                "size": size,
                "inflate": float(cube.get("inflate", 0.0)),
                "mirror": bool(cube.get("mirror_uv", False)),
            })
        for child in children:
            walk(child, name, origin)

    for root in project.get("outliner", []):
        walk(root, None, None)
    loose = [cubes[c].get("name") for c in cubes if c not in used and cubes[c].get("export", True)]
    if loose:
        raise SystemExit("cubes not in any group: %s; put every cube in a group" % ", ".join(loose))
    textures = {}
    for texture in project.get("textures", []):
        source = texture.get("source", "")
        if source.startswith("data:image/png;base64,"):
            textures[texture.get("name", "")] = base64.b64decode(source.split(",", 1)[1])
    return {"texture": [width, height], "parts": parts}, textures


def same(a, b):
    """Whether two models' JSON say the same, whole numbers written either way."""
    if isinstance(a, (int, float)) and isinstance(b, (int, float)) and not isinstance(a, bool):
        return abs(a - b) < 1e-3
    if isinstance(a, list) and isinstance(b, list):
        return len(a) == len(b) and all(same(x, y) for x, y in zip(a, b))
    if isinstance(a, dict) and isinstance(b, dict):
        return a.keys() == b.keys() and all(same(a[k], b[k]) for k in a)
    return a == b


def edited(name):
    """Whether tools/models/blockbench/NAME.bbmodel was changed since it was written from the model, so the model
    is now made in Blockbench rather than by tools/models/generate.py."""
    path = os.path.join(BLOCKBENCH, name + ".bbmodel")
    if not os.path.exists(path):
        return False
    with open(path) as file:
        model, textures = from_blockbench(json.load(file))
    files = paths(ASSETS, name)
    with open(files["model"]) as file:
        if not same(model, json.load(file)):
            return True
    for texture, key in ((name + ".png", "texture"), (name + "_team.png", "team")):
        if texture in textures:
            with open(files[key], "rb") as file:
                if file.read() != textures[texture]:
                    return True
    return False


def model_json(model):
    return json.dumps(model, indent=1) + "\n"


def draw_icon(name, model, texture_path):
    """The inventory icon, drawn from the model as tools/models/generate.py draws it."""
    try:
        import generate  # noqa: E402  (needs Pillow and numpy)
        from boxmodel import Model
        from PIL import Image
    except ImportError:
        print("%s: Pillow or numpy is missing, so its icon is left as it was" % name)
        return None
    built = Model(name, *model["texture"])
    made = {}
    for part in model["parts"]:
        parent = made.get(part["parent"])
        add = parent.child if parent is not None else built.part
        node = add(part["name"], part["pivot"], part["rotation"], part["glow"])
        for box in part["boxes"]:
            node.box(*box["from"], *box["size"], paint=None, inflate=box["inflate"], mirror=box["mirror"])
            node.boxes[-1].u, node.boxes[-1].v = box["uv"]
        made[part["name"]] = node
    return generate.icon(built, Image.open(texture_path).convert("RGBA"))


def import_(files, into):
    sys.path.insert(0, HERE)
    for path in files:
        name = os.path.splitext(os.path.basename(path))[0]
        with open(path) as file:
            project = json.load(file)
        try:
            model, textures = from_blockbench(project)
        except SystemExit as problem:
            raise SystemExit("%s: %s" % (path, problem))
        out = paths(into, name)
        for key in ("model", "texture", "icon"):
            os.makedirs(os.path.dirname(out[key]), exist_ok=True)
        with open(out["model"], "w") as file:
            file.write(model_json(model))
        texture = textures.get(name + ".png") or next(
            (data for key, data in textures.items() if not key.endswith("_team.png")), None)
        if texture is not None:
            with open(out["texture"], "wb") as file:
                file.write(texture)
        if name + "_team.png" in textures:
            with open(out["team"], "wb") as file:
                file.write(textures[name + "_team.png"])
        if name in ICONS and os.path.exists(out["texture"]):
            icon = draw_icon(name, model, out["texture"])
            if icon is not None:
                icon.save(out["icon"])
        print("%s: %d parts -> %s" % (name, len(model["parts"]), os.path.relpath(out["model"])))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    out = commands.add_parser("export", help="write models as .bbmodel files into tools/models/blockbench/")
    out.add_argument("names", nargs="*", help="models to write; all of %s when none" % ", ".join(NAMES))
    back = commands.add_parser("import", help="turn .bbmodel files back into the mod's models")
    back.add_argument("files", nargs="*", help=".bbmodel files; all of tools/models/blockbench/ when none")
    back.add_argument("--into", help="write under this folder, laid out as assets/warofthegods/ is")
    args = parser.parse_args()
    if args.command == "export":
        export(args.names or NAMES)
    else:
        files = args.files or sorted(os.path.join(BLOCKBENCH, f) for f in os.listdir(BLOCKBENCH)
                                     if f.endswith(".bbmodel"))
        import_(files, args.into or ASSETS)


if __name__ == "__main__":
    main()
