#!/usr/bin/env python3
"""Draws the lesser, greater and black demon's four looks each, after Old School RuneScape's twelve, from the three
demon models (tools/models/blockbench/lesser_demon.bbmodel and the rest, as built into the mod's assets).

    python3 tools/models/demons.py [--preview FILE]

Every demon takes one of its four looks at random when it is trained. A look (see LOOKS) says:

- its hide's color, and its wings', whole or torn through with holes;
- its horns: stubs, short straight ones, great curving ones, or none, with spikes out of the sides of its head;
- its eyes, which glow, and its mouth: shut over a beard of drool, gaping, grinning, or open on long fangs;
- a mane of dark hair on its head and chin and in streaks down its limbs, and its hands and feet darker;
- spikes on its shoulders, elbows and knees, iron bands at its wrists and ankles, and its claws', hooves' and tail
  spade's colors.

Each look keeps its demon's parts, so the game moves it the same, and its team mask. It is written as the model's name
and the look's number (lesser_demon_1 to black_demon_4): its JSON into models/, its texture and team mask into
textures/models/, and its Blockbench file into tools/models/blockbench/, where it can be edited by hand like the
others. Running this again draws them anew from the three demons, over any such edits. With --preview it also draws
every look side by side into FILE.
"""

import argparse
import copy
import json
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import blockbench  # noqa: E402
from boxmodel import faces, render  # noqa: E402

ASSETS = blockbench.ASSETS
DEMONS = ("lesser_demon", "greater_demon", "black_demon")
SIZE = 128

BONE = (224, 216, 192)
WHITE = (236, 234, 228)
IVORY = (232, 222, 182)
IRON = (150, 152, 158)
BLACK = (24, 20, 20)
HAIR = (38, 50, 30)
DRIP = (84, 140, 34)
GREEN = (90, 235, 60)
YELLOW = (255, 226, 40)
MAGENTA = (225, 70, 235)
RED = (240, 30, 24)
GORE = (104, 20, 24)

# By demon, its four looks, in the order of Old School RuneScape's.
LOOKS = {
    "lesser_demon": [
        dict(hide=(158, 72, 62), horns="stubs", horn=WHITE, eyes=GREEN, mouth="shut", drool=DRIP, claw=BONE,
             hoof=BONE, tip=BONE, spikes=("elbows",)),
        dict(hide=(134, 104, 106), horns="straight", horn=WHITE, eyes=YELLOW, mouth="gaping", hair=HAIR,
             streaks=HAIR, claw=WHITE, hoof=WHITE, tip=WHITE, spikes=("shoulders", "elbows")),
        dict(hide=(196, 58, 52), horns="curved", horn=IVORY, head_spikes=True, eyes=MAGENTA, mouth="grinning",
             claw=BLACK, hoof=None, toe=BLACK, tip=BLACK, spikes=("shoulders",), bands=("wrists",)),
        dict(hide=(150, 40, 30), horns="straight", horn=WHITE, head_spikes=True, eyes=YELLOW, mouth="fanged",
             hair=HAIR, claw=BLACK, hoof=None, toe=BLACK, tip=BLACK, spikes=("shoulders",), bands=("wrists",)),
    ],
    "greater_demon": [
        dict(hide=(160, 74, 62), wing=(146, 64, 56), holes=True, horns="stubs", horn=WHITE, eyes=GREEN,
             mouth="shut", drool=DRIP, claw=BONE, hoof=None, toe=BONE, tip=BONE, spikes=("elbows",)),
        dict(hide=(134, 104, 106), wing=(124, 96, 98), horns="straight", horn=WHITE, eyes=YELLOW,
             mouth="gaping", hair=HAIR, streaks=HAIR, claw=WHITE, hoof=WHITE, tip=WHITE,
             spikes=("shoulders", "elbows")),
        dict(hide=(196, 60, 54), wing=(184, 58, 52), horns="curved", horn=IVORY, head_spikes=True, eyes=MAGENTA,
             mouth="grinning", claw=BLACK, hoof=None, toe=BLACK, tip=BLACK, spikes=("shoulders",),
             bands=("wrists", "ankles")),
        dict(hide=(148, 44, 32), wing=(128, 40, 30), holes=True, horns="stubs", horn=WHITE, eyes=GREEN,
             mouth="shut", drool=DRIP, streaks=HAIR, claw=WHITE, hoof=WHITE, tip=WHITE),
    ],
    "black_demon": [
        dict(hide=(46, 44, 48), wing=(36, 34, 38), holes=True, horns="straight", horn=WHITE, eyes=YELLOW,
             mouth="gaping", hands=GORE, claw=WHITE, hoof=None, toe=WHITE, tip=WHITE),
        dict(hide=(48, 46, 50), wing=(40, 38, 42), horns="stubs", horn=WHITE, eyes=GREEN, mouth="shut",
             drool=DRIP, hands=GORE, streaks=GORE, claw=WHITE, hoof=None, toe=WHITE, tip=WHITE,
             spikes=("shoulders",), bands=("ankles",)),
        dict(hide=(50, 48, 52), wing=(40, 38, 42), holes=True, horns="curved", horn=IVORY, eyes=MAGENTA,
             mouth="grinning", hands=GORE, streaks=GORE, claw=WHITE, hoof=WHITE, tip=WHITE),
        dict(hide=(44, 42, 46), wing=(36, 34, 38), horns="none", horn=WHITE, head_spikes=True, eyes=RED,
             mouth="fanged", maw=(150, 16, 20), hands=GORE, claw=WHITE, hoof=WHITE, tip=WHITE,
             spikes=("shoulders",), bands=("wrists",)),
    ],
}

# How far each mouth hangs open at rest, the jaw's turn; what shows inside it, and the teeth's color.
MOUTHS = {
    "shut": (0.0, (60, 14, 14), BONE),
    "gaping": (0.55, (206, 184, 170), (240, 236, 222)),
    "grinning": (0.22, (44, 10, 10), (236, 220, 150)),
    "fanged": (0.4, (60, 12, 12), WHITE),
}
# Where spikes go, on the right side (the left is mirrored): the part, the spike's root, its turn (x, y, z) and its
# length. A spike stands up (-y) from its root before it is turned.
SPIKES = {
    "shoulders": [("right_arm", (-1.5, -3.0, 0.0), (0.0, 0.0, -0.5), 5), ("right_arm", (-3.0, -0.5, 1.0),
                                                                           (-0.3, 0.0, -1.3), 4)],
    "elbows": [("right_forearm", (0.0, 1.0, 2.0), (-1.9, 0.0, 0.0), 4)],
    "knees": [("right_shin", (0.0, 2.0, -2.0), (1.4, 0.0, 0.0), 4)],
}
# Horns grown as spikes from the top of its head: short straight ones, or stubs.
HORNS = {"straight": [("head", (-2.5, -6.5, -3.0), (-0.25, 0.0, -0.3), 6)],
         "stubs": [("head", (-2.5, -6.5, -3.0), (-0.2, 0.0, -0.6), 2)]}
# Spikes out of the sides of its head, beside its ears: as a look's horns, or with them.
HEAD_SPIKES = [("head", (-4.0, -5.5, -3.0), (0.0, 0.3, -1.25), 4), ("head", (-4.0, -3.5, -2.0), (0.0, 0.2, -1.5), 3)]
# Where iron bands go, round the part: the part, the band's corner and size.
BANDS = {
    "wrists": [("right_forearm", (-2, 5.5, -2), (4, 2, 4))],
    "ankles": [("right_foot", (-1.5, 2.5, -1.5), (3, 2, 3))],
}
# Its glowing eyes, on the front of its head where its texture paints them: corner and size, right and left.
EYES = [((-3, -5, -6.05), (2, 1, 0)), ((1, -5, -6.05), (2, 1, 0))]
# Long fangs from its upper jaw.
FANGS = [((-2.5, -1.4, -8.6), (1, 3, 1)), ((1.5, -1.4, -8.6), (1, 3, 1))]
LIMBS = ("_arm", "_forearm", "_leg", "_shin")


# ---------------------------------------------------------------- the texture's layout


def box_footprint(box):
    """The texels a box takes in the texture, as the game lays them out, without the two empty corners."""
    u, v = box["uv"]
    w, h, d = box["size"]
    for x in range(u, u + 2 * (w + d)):
        for y in range(v, v + d + h):
            if y < v + d and (x < u + d or x >= u + d + 2 * w):
                continue
            if 0 <= x < SIZE and 0 <= y < SIZE:
                yield x, y


class Box:
    """A box's place in the texture, as boxmodel.faces reads it."""

    def __init__(self, box):
        self.u, self.v = box["uv"]
        self.w, self.h, self.d = box["size"]


def face_of(box, x, y):
    """Which face of a box a texel is on, and where on that face, from its top left."""
    for name, u, v, w, h in faces(Box(box)):
        if u <= x < u + w and v <= y < v + h:
            return name, x - u, y - v, w, h
    return None, 0, 0, 0, 0


def kind_of(part, index, box):
    """What a box of the demon's is: the parts a look paints its own color."""
    name = part["name"]
    if name.startswith("horn_"):
        return "horn"
    if "claw" in name or "thumb" in name:
        return "claw"
    if "toe" in name:
        return "toe"
    if name.endswith("_sole"):
        return "hoof"
    if name.startswith("spine"):
        return "spine"
    if name == "tail_spade":
        return "tip"
    if name.startswith("head_turned") or name == "jaw" or (name == "head" and index >= 2):
        return "teeth"
    if name.endswith("_forearm") and index == 1:
        return "spine"
    if "wing" in name:
        return "membrane" if 0 in box["size"] else "wingbone"
    return "hide"


def texels(model):
    """By texel: what it is the texture of, the part and box it is on, and which face of that box, where."""
    out = {}
    for part in model["parts"]:
        for index, box in enumerate(part["boxes"]):
            kind = kind_of(part, index, box)
            for x, y in box_footprint(box):
                if (x, y) not in out:
                    out[x, y] = (kind, part["name"], index) + face_of(box, x, y)
    return out


class Space:
    """The texture's free room, for the boxes a look adds."""

    def __init__(self, taken):
        self.taken = taken.copy()

    def place(self, size):
        w, h, d = size
        width, height = 2 * (w + d), d + h
        for v in range(SIZE - height + 1):
            for u in range(SIZE - width + 1):
                if not self.taken[v:v + height, u:u + width].any():
                    self.taken[v:v + height, u:u + width] = True
                    return [u, v]
        raise SystemExit("no room left in the texture for a %dx%dx%d box" % tuple(size))


# ---------------------------------------------------------------- painting


def luminance(pixels):
    return pixels[..., 0] * 0.3 + pixels[..., 1] * 0.59 + pixels[..., 2] * 0.11


def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color)


def streaked(rng, faces_seen):
    """Shaggy hair or dark hide over the lower half of its forearms and shins, its edge ragged, as a set of (part,
    index, face, x, y)."""
    out = set()
    for key, (w, h) in faces_seen.items():
        if not key[0].endswith(("_forearm", "_shin")) or key[2] == "top":
            continue
        for column in range(w):
            top = h // 2 + rng.randint(-2, 1)
            for row in range(max(0, top), h):
                out.add(key + (column, row))
            if rng.random() < 0.35:
                # A tuft reaching higher.
                for row in range(max(0, top - rng.randint(2, 3)), max(0, top)):
                    out.add(key + (column, row))
    return out


def hair_on_head(part, index, face, fx, fy, w, h):
    """Whether a texel of the head is under its mane: its crown and the top of its sides and back, and its chin."""
    if part == "head" and index == 0:
        if face == "top":
            return True
        if face in ("right", "left", "back"):
            return fy <= 1 + (fx * 7 + 3) % 3
        if face == "front":
            return fy == 0 and fx in (0, 1, w - 2, w - 1)
    if part == "jaw_turned1":
        if face in ("bottom", "right", "left"):
            return True
        if face == "front":
            return fy >= 1
    return False


def repaint(look, base, shading, wing_shading, black, team, layout, rng):
    """The demon's texture in the look's colors, shaded as the demon's own is. Eyes and maw are found on the black
    demon's texture, where they alone are red."""
    out = base.copy()
    _, maw, teeth = MOUTHS[look["mouth"]]
    maw = look.get("maw", maw)
    hide = look["hide"]
    hands = look.get("hands")
    colors = {"hide": hide, "horn": look["horn"], "claw": look["claw"], "toe": look.get("toe", look["claw"]),
              "hoof": look["hoof"] or shade(hands or hide, 0.45), "spine": shade(hide, 0.55), "tip": look["tip"],
              "eyes": look["eyes"], "maw": maw, "brow": shade(hide, 0.45), "teeth": teeth, "hair": look.get("hair"),
              "streak": look.get("streaks"), "hands": hands, "membrane": look.get("wing", hide),
              "wingbone": shade(look.get("wing", hide), 0.72)}
    if look.get("hoof"):
        colors["toe"] = look["hoof"]
    limb_faces = {}
    for (x, y), (kind, part, index, face, fx, fy, w, h) in layout.items():
        if kind == "hide" and part.endswith(LIMBS) and face:
            limb_faces[(part, index, face)] = (w, h)
    streaks = streaked(rng, limb_faces) if colors["streak"] else set()
    groups = {}
    for (x, y), (kind, part, index, face, fx, fy, w, h) in layout.items():
        if team[y, x, 3] > 0 or base[y, x, 3] == 0:
            continue
        r, g, b = black[y, x, :3]
        if kind in ("hide", "teeth") and r > 200 and g < 30:
            kind = "eyes"
        elif kind == "hide" and r > g + 20:
            # Red on the black demon is its maw inside its jaws, and the shadow of its brow over its eyes.
            kind = "maw" if part == "jaw_turned1" or part == "head" and index == 1 else "brow"
        elif kind == "hide" and colors["hair"] and hair_on_head(part, index, face, fx, fy, w, h):
            kind = "hair"
        elif kind == "hide" and (part, index, face, fx, fy) in streaks:
            kind = "streak"
        elif kind == "hide" and hands and part.endswith(("_hand", "_foot")):
            kind = "hands"
        groups.setdefault(kind, []).append((x, y))
    for kind, spots in groups.items():
        xs = np.array([s[0] for s in spots])
        ys = np.array([s[1] for s in spots])
        source = wing_shading if kind in ("membrane", "wingbone") else shading
        lum = luminance(source[ys, xs, :3].astype(float))
        middle = np.median(lum) or 1.0
        spread = 0.12 if kind in ("eyes", "tip", "teeth") else 0.4
        factor = 1 + np.clip(lum / middle - 1, -spread, spread)
        factor *= np.array([rng.uniform(0.95, 1.04) for _ in spots])
        out[ys, xs, :3] = np.clip(np.array(colors[kind], dtype=float)[None, :] * factor[:, None], 0, 255)
    return out


def tear_holes(texture, model, rng):
    """Holes torn through the wings' skin between their fingers, the same through both faces."""
    for part in model["parts"]:
        if "wing" not in part["name"] or not part["name"].startswith("right_"):
            continue
        for box in part["boxes"]:
            w, h, d = box["size"]
            if d != 0:
                continue
            u, v = box["uv"]
            holes = np.zeros((h, w), bool)
            for _ in range(max(3, w * h // 28)):
                cx, cy = rng.uniform(1, w - 1), rng.uniform(2.5, h - 1.5)
                rx, ry = rng.uniform(1.0, 2.0), rng.uniform(1.2, 2.8)
                for y in range(h):
                    for x in range(w):
                        if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 < 1 + rng.uniform(-0.3, 0.3):
                            holes[y, x] = True
            for y in range(h):
                for x in range(w):
                    if holes[y, x]:
                        texture[v + y, u + x, 3] = 0
                        texture[v + y, u + w + (w - 1 - x), 3] = 0


def paint_box(texture, box, color, rng):
    """A box of the look's own in one color, its top lighter and its bottom darker, a little mottled."""
    u, v = box["uv"]
    w, h, d = box["size"]
    for x, y in box_footprint(box):
        light = 1.12 if y < v + d and x < u + d + w else 0.8 if y < v + d else 1.0
        if y >= v + d and (x < u + d or u + d + w <= x < u + 2 * d + w):
            light *= 0.9
        texture[y, x, :3] = shade(color, light * rng.uniform(0.94, 1.04))
        texture[y, x, 3] = 255


# ---------------------------------------------------------------- the model


def mirrored(part):
    """The left side's copy of a part added on the right."""
    other = copy.deepcopy(part)
    other["name"] = part["name"].replace("right_", "left_")
    if other["parent"]:
        other["parent"] = other["parent"].replace("right_", "left_")
    other["pivot"][0] = -other["pivot"][0]
    other["rotation"] = [other["rotation"][0], -other["rotation"][1], -other["rotation"][2]]
    for box in other["boxes"]:
        box["from"][0] = -box["from"][0] - box["size"][0]
    return other


def new_part(name, parent, pivot, rotation, boxes, glow=False):
    return {"name": name, "parent": parent, "pivot": [float(c) for c in pivot],
            "rotation": [float(c) for c in rotation], "glow": glow,
            "boxes": [{"uv": [0, 0], "from": list(f), "size": list(s), "inflate": i, "mirror": False}
                      for f, s, i in boxes]}


def spike(name, parent, root, turn, length):
    """A spike standing up from its root before it is turned: a broad foot and a thin point."""
    foot = max(1, length // 2)
    return new_part(name, parent, root, turn, [((-1, -foot, -1), (2, foot, 2), -0.1),
                                               ((-0.5, -length, -0.5), (1, length - foot, 1), -0.05)])


def horns(parts, style):
    """The demon's own horns, kept only when the look has them great and curving; other looks grow their own."""
    if style == "curved":
        return parts
    return [part for part in parts if not part["name"].startswith("horn_")]


def build(name, number, look):
    """The look's model JSON, texture and team mask, as arrays."""
    with open(os.path.join(ASSETS, "models", name + ".json")) as file:
        model = json.load(file)
    textures = os.path.join(ASSETS, "textures", "models")

    def read(texture):
        return np.asarray(Image.open(os.path.join(textures, texture + ".png")).convert("RGBA")).copy()

    rng = random.Random("%s_%d" % (name, number))
    base = read(name)
    team = read(name + "_team")
    texture = repaint(look, base, read("lesser_demon"), read("greater_demon"), read("black_demon"), team,
                      texels(model), rng)
    if look.get("holes"):
        tear_holes(texture, model, rng)
    parts = horns(model["parts"], look["horns"])
    rest, _, teeth = MOUTHS[look["mouth"]]
    for part in parts:
        if part["name"] == "jaw":
            part["rotation"][0] = rest
    added = [(new_part("eyes", "head", (0, 0, 0), (0, 0, 0), [(c, s, 0.0) for c, s in EYES], glow=True),
              look["eyes"], False)]
    if look["mouth"] == "fanged":
        added.append((new_part("fangs", "head", (0, 0, 0), (0, 0, 0), [(c, s, -0.15) for c, s in FANGS]), teeth,
                      False))
    if look.get("drool"):
        added.append((new_part("drool", "jaw", (0, 0, 0), (0.34907, 0, 0),
                               [((-2, 1.2, -4.9), (1, 4, 1), -0.1), ((-0.5, 1.2, -4.8), (1, 7, 1), -0.05),
                                ((1, 1.2, -4.9), (1, 3, 1), -0.1), ((-1, 1.2, -4.6), (2, 2, 1), -0.05)]),
                      look["drool"], False))
    for index, (parent, root, turn, length) in enumerate(HORNS.get(look["horns"], ())):
        added.append((spike("right_horn%d" % index, parent, root, turn, length), look["horn"], True))
    if look.get("head_spikes"):
        for index, (parent, root, turn, length) in enumerate(HEAD_SPIKES):
            added.append((spike("right_head_spike%d" % index, parent, root, turn, length), look["horn"], True))
    for place in look.get("spikes", ()):
        for index, (parent, root, turn, length) in enumerate(SPIKES[place]):
            added.append((spike("right_%s_spike%d" % (place, index), parent, root, turn, length), BONE, True))
    for place in look.get("bands", ()):
        for index, (parent, corner, size) in enumerate(BANDS[place]):
            added.append((new_part("right_%s_band%d" % (place, index), parent, (0, 0, 0), (0, 0, 0),
                                   [(corner, size, 0.35)]), IRON, True))
    # Everything added on the right goes on the left as well.
    sides = []
    for part, color, both in added:
        sides.append((part, color))
        if both:
            sides.append((mirrored(part), color))
    taken = base[:, :, 3] > 0
    for other in DEMONS:
        taken = taken | (read(other)[:, :, 3] > 0)
    space = Space(taken)
    for part, color in sides:
        for box in part["boxes"]:
            box["uv"] = space.place(box["size"])
            paint_box(texture, box, color, rng)
    return {"texture": model["texture"], "parts": parts + [p for p, _ in sides]}, texture, team


# ---------------------------------------------------------------- writing


def write(name, model, texture, team):
    files = blockbench.paths(ASSETS, name)
    Image.fromarray(texture, "RGBA").save(files["texture"])
    Image.fromarray(team, "RGBA").save(files["team"])
    project = blockbench.to_blockbench(name, model, files["texture"], files["team"])
    with open(os.path.join(blockbench.BLOCKBENCH, name + ".bbmodel"), "w") as file:
        json.dump(project, file, indent=1)
        file.write("\n")
    # The model as Blockbench's file turns back into it, so a build from that file changes nothing.
    saved, _ = blockbench.from_blockbench(project)
    with open(files["model"], "w") as file:
        file.write(blockbench.model_json(saved))


def preview(path, team=(60, 90, 220)):
    """Every look from the front and the back, a row to a demon."""
    size = 300
    sheet = Image.new("RGB", (size * 8, size * len(DEMONS)), (48, 54, 66))
    for row, demon in enumerate(DEMONS):
        for column in range(4):
            files = blockbench.paths(ASSETS, "%s_%d" % (demon, column + 1))
            with open(files["model"]) as file:
                model = blockbench.built(json.load(file))
            texture, mask = Image.open(files["texture"]), Image.open(files["team"])
            for side, yaw in enumerate((25, 200)):
                image = render(model, texture, size=size, yaw=yaw, pitch=8, mask=mask, team=team,
                               background=(48, 54, 66))
                sheet.paste(image, ((column * 2 + side) * size, row * size))
    sheet.save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", help="also draw every look into this PNG")
    args = parser.parse_args()
    for demon in DEMONS:
        for number, look in enumerate(LOOKS[demon], 1):
            name = "%s_%d" % (demon, number)
            write(name, *build(demon, number, look))
            print(name)
    if args.preview:
        preview(args.preview)


if __name__ == "__main__":
    main()
