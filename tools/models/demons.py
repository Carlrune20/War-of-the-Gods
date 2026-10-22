#!/usr/bin/env python3
"""Draws the lesser, greater and black demon's four looks each, as Old School RuneScape has them, from the three demon
models (tools/models/blockbench/lesser_demon.bbmodel and the rest, as built into the mod's assets).

    python3 tools/models/demons.py [--preview FILE]

Every demon takes one of its four looks at random when it is trained. Across the three demons the looks go alike:

1. plain, hornless but for stubs, with a green beard of drool and a bone-white tail spike;
2. dark and spiked, white spikes on its head, shoulders, elbows and knees, with yellow eyes;
3. bright, with great curving ivory horns, a pink maw and iron bands at its wrists and ankles;
4. dark and armoured, iron bands at its elbows and knees, white spikes on its shoulders and a black tail spade.

Each look keeps its demon's parts, so the game moves it the same, and its team mask. It is written as the model's name
and the look's number (lesser_demon_1 to black_demon_4): its JSON into models/, its texture and team mask into
textures/models/, and its Blockbench file into tools/models/blockbench/, where it can be edited by hand like the
others. Running this again draws them anew from the three demons, over any such edits. With --preview it also draws
every look side by side into FILE.
"""

import argparse
import copy
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import blockbench  # noqa: E402
from boxmodel import render  # noqa: E402

ASSETS = blockbench.ASSETS
DEMONS = ("lesser_demon", "greater_demon", "black_demon")
SIZE = 128

BONE = (226, 219, 196)
IVORY = (232, 222, 186)
IRON = (128, 130, 136)
BLACK = (26, 22, 22)
GREEN = (96, 168, 40)
YELLOW = (250, 216, 40)

# By demon, its four looks: the colors of its hide, wings, horns, claws, eyes, maw, spine and tail spade, the shape of
# its horns (long, as the demon has them, or stubs), and what it has more: drool, spikes and iron bands.
LOOKS = {
    "lesser_demon": [
        dict(hide=(148, 56, 44), horns="stubs", horn=(70, 30, 26), claw=BONE, eyes=(120, 230, 60),
             maw=(60, 14, 12), spine=(70, 30, 26), tip=BONE, drool=GREEN),
        dict(hide=(128, 92, 96), horns="stubs", horn=BONE, claw=BONE, eyes=YELLOW, maw=(40, 10, 12), spine=BONE,
             tip=BONE, spikes=("shoulders", "elbows", "knees", "head")),
        dict(hide=(196, 42, 40), horn=IVORY, claw=(40, 30, 30), eyes=(255, 230, 70), maw=(200, 60, 150),
             spine=(60, 20, 20), tip=BLACK, bands=("wrists", "ankles")),
        dict(hide=(116, 22, 20), horns="stubs", horn=BONE, claw=BONE, eyes=YELLOW, maw=(50, 10, 10), spine=BONE,
             tip=BLACK, spikes=("shoulders",), bands=("elbows", "knees")),
    ],
    "greater_demon": [
        dict(hide=(152, 56, 44), wing=(138, 46, 38), horns="stubs", horn=(70, 30, 26), claw=BONE,
             eyes=(120, 230, 60), maw=(60, 14, 12), spine=(70, 30, 26), tip=BONE, drool=GREEN),
        dict(hide=(122, 86, 90), wing=(104, 80, 84), horns="stubs", horn=BONE, claw=BONE, eyes=YELLOW,
             maw=(40, 10, 12), spine=BONE, tip=BONE, spikes=("shoulders", "elbows", "knees", "head")),
        dict(hide=(200, 44, 42), wing=(176, 40, 40), horn=IVORY, claw=(40, 30, 30), eyes=(255, 230, 70),
             maw=(200, 60, 150), spine=(60, 20, 20), tip=BLACK, bands=("wrists", "ankles")),
        dict(hide=(120, 24, 22), wing=(100, 22, 26), horns="stubs", horn=BONE, claw=BONE, eyes=YELLOW,
             maw=(50, 10, 10), spine=BONE, tip=BONE, drool=GREEN, spikes=("shoulders",),
             bands=("elbows", "knees")),
    ],
    "black_demon": [
        dict(hide=(46, 44, 50), wing=(40, 38, 44), horns="stubs", horn=(90, 90, 96), claw=(150, 30, 40),
             eyes=YELLOW, maw=(30, 70, 20), spine=(80, 80, 86), tip=BONE, drool=(70, 120, 30)),
        dict(hide=(40, 40, 44), wing=(52, 50, 56), horns="stubs", horn=BONE, claw=(150, 30, 40),
             eyes=(140, 220, 60), maw=(30, 70, 20), spine=BONE, tip=BONE, drool=(70, 120, 30),
             spikes=("shoulders", "elbows", "knees", "head")),
        dict(hide=(78, 62, 84), wing=(62, 52, 68), horn=IVORY, claw=BONE, eyes=(230, 40, 40),
             maw=(170, 50, 130), spine=BONE, tip=BLACK, spikes=("elbows",), bands=("wrists", "ankles")),
        dict(hide=(34, 32, 36), wing=(90, 20, 26), horns="stubs", horn=BONE, claw=BONE, eyes=(230, 30, 30),
             maw=(120, 10, 16), spine=BONE, tip=BONE, spikes=("shoulders", "knees", "head"),
             bands=("elbows", "knees")),
    ],
}

# Where spikes go, on the right side (the left is mirrored): the part, the spike's root, its turn (x, y, z) and its
# length.
SPIKES = {
    "shoulders": [("right_arm", (-1.5, -3.0, 0.0), (0.0, 0.0, -0.5), 5), ("right_arm", (-3.0, -0.5, 1.0),
                                                                           (-0.3, 0.0, -1.3), 4)],
    "elbows": [("right_forearm", (0.0, 1.0, 2.0), (-1.9, 0.0, 0.0), 4)],
    "knees": [("right_shin", (0.0, 2.0, -2.0), (1.4, 0.0, 0.0), 4)],
    "head": [("head", (-2.5, -7.0, -3.0), (-0.5, 0.0, -0.35), 4)],
}
# Where iron bands go, round the part: the part, the band's corner and size, by the part's own box.
BANDS = {
    "wrists": [("right_forearm", (-2, 5.5, -2), (4, 2, 4))],
    "ankles": [("right_foot", (-1.5, 2.5, -1.5), (3, 2, 3))],
    "elbows": [("right_forearm", (-2, 0, -2), (4, 2, 4))],
    "knees": [("right_shin", (-2, 0, -2), (4, 2, 4))],
}


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


def kind_of(part, index, box):
    """What a box of the demon's is: the parts a look paints its own color."""
    name = part["name"]
    if name.startswith("horn_"):
        return "horn"
    if "claw" in name or "thumb" in name or "toe" in name:
        return "claw"
    if name.endswith("_sole"):
        return "sole"
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


def kinds(model):
    """By texel, what it is the texture of."""
    out = {}
    for part in model["parts"]:
        for index, box in enumerate(part["boxes"]):
            kind = kind_of(part, index, box)
            for texel in box_footprint(box):
                out.setdefault(texel, kind)
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
        raise SystemExit("no room left in the texture for a %dx%dx%d box" % size)


# ---------------------------------------------------------------- painting


def luminance(pixels):
    return pixels[..., 0] * 0.3 + pixels[..., 1] * 0.59 + pixels[..., 2] * 0.11


def tint(color, factor):
    return np.clip(np.array(color, dtype=float)[None, :] * factor[:, None], 0, 255)


def repaint(look, base, shading, wing_shading, black, team, texels):
    """The demon's texture in the look's colors, shaded as the demon's own is. Eyes and maw are found on the black
    demon's texture, where they alone are red."""
    out = base.copy()
    groups = {}
    for (x, y), kind in texels.items():
        if team[y, x, 3] > 0 or base[y, x, 3] == 0:
            continue
        r, g, b = black[y, x, :3]
        if kind in ("hide", "teeth") and r > 200 and g < 30:
            kind = "eyes"
        elif kind == "hide" and r > g + 20:
            kind = "maw"
        groups.setdefault(kind, []).append((x, y))
    colors = {"hide": look["hide"], "horn": look["horn"], "claw": look["claw"], "sole": shade(look["hide"], 0.35),
              "spine": look["spine"], "tip": look["tip"], "eyes": look["eyes"], "maw": look["maw"],
              "membrane": look.get("wing", look["hide"]), "wingbone": shade(look.get("wing", look["hide"]), 0.7)}
    for kind, spots in groups.items():
        if kind == "teeth":
            continue
        xs = np.array([s[0] for s in spots])
        ys = np.array([s[1] for s in spots])
        source = wing_shading if kind in ("membrane", "wingbone") else shading
        lum = luminance(source[ys, xs, :3].astype(float))
        middle = np.median(lum) or 1.0
        spread = 0.15 if kind in ("eyes", "tip") else 0.45
        factor = 1 + np.clip(lum / middle - 1, -spread, spread)
        out[ys, xs, :3] = tint(colors[kind], factor).astype(np.uint8)
    return out


def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color)


def paint_box(texture, box, color, rng):
    """A box of the look's own in one color, its top lighter and its bottom darker, a little mottled."""
    u, v = box["uv"]
    w, h, d = box["size"]
    for x, y in box_footprint(box):
        light = 1.12 if y < v + d and x < u + d + w else 0.8 if y < v + d else 1.0
        if y >= v + d and (x < u + d or u + d + w <= x < u + 2 * d + w):
            light *= 0.9
        texture[y, x, :3] = shade(color, light * rng.uniform(0.93, 1.05))
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


def new_part(name, parent, pivot, rotation, boxes):
    return {"name": name, "parent": parent, "pivot": [float(c) for c in pivot],
            "rotation": [float(c) for c in rotation], "glow": False,
            "boxes": [{"uv": [0, 0], "from": list(f), "size": list(s), "inflate": i, "mirror": False}
                      for f, s, i in boxes]}


def spike(name, parent, root, turn, length):
    """A spike standing up from its root before it is turned: a broad foot and a thin point."""
    foot = max(1, length // 2)
    return new_part(name, parent, root, turn, [((-1, -foot, -1), (2, foot, 2), -0.1),
                                               ((-0.5, -length, -0.5), (1, length - foot, 1), -0.05)])


def build(name, number, look):
    """The look's model JSON, texture and team mask, as arrays."""
    with open(os.path.join(ASSETS, "models", name + ".json")) as file:
        model = json.load(file)
    textures = os.path.join(ASSETS, "textures", "models")

    def read(texture):
        return np.asarray(Image.open(os.path.join(textures, texture + ".png")).convert("RGBA")).copy()

    base = read(name)
    team = read(name + "_team")
    texture = repaint(look, base, read("lesser_demon"), read("greater_demon"), read("black_demon"), team,
                      kinds(model))
    parts = model["parts"]
    if look.get("horns") == "stubs":
        # Only the horns' roots, which point up and out.
        parts = [p for p in parts if not p["name"].endswith(("_3", "_4")) or not p["name"].startswith("horn_")]
    added = []
    if look.get("drool"):
        added.append((new_part("drool", "jaw", (0, 0, 0), (0.34907, 0, 0),
                               [((-2, 1.2, -4.9), (1, 4, 1), -0.1), ((-0.5, 1.2, -4.8), (1, 6, 1), -0.05),
                                ((1, 1.2, -4.9), (1, 3, 1), -0.1), ((-1, 1.2, -4.6), (2, 2, 1), -0.05)]),
                      look["drool"]))
    for place in look.get("spikes", ()):
        for index, (parent, root, turn, length) in enumerate(SPIKES[place]):
            part = spike("right_%s_spike%d" % (place, index), parent, root, turn, length)
            if parent == "head":
                part["name"] = "head_spike%d" % index
            added.append((part, look["horn"] if place == "head" else BONE))
    for place in look.get("bands", ()):
        for index, (parent, corner, size) in enumerate(BANDS[place]):
            added.append((new_part("right_%s_band%d" % (place, index), parent, (0, 0, 0), (0, 0, 0),
                                   [(corner, size, 0.35)]), IRON))
    # Everything added on the right goes on the left as well, the head's spikes too.
    sides = []
    for part, color in added:
        sides.append((part, color))
        if part["name"].startswith("right_") or part["name"].startswith("head_spike"):
            other = mirrored(part)
            if other["name"] == part["name"]:
                other["name"] = part["name"] + "_left"
            sides.append((other, color))
    taken = base[:, :, 3] > 0
    for other in DEMONS:
        taken = taken | (read(other)[:, :, 3] > 0)
    space = Space(taken)
    rng = random.Random("%s_%d" % (name, number))
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


def preview(names, path, team=(60, 90, 220)):
    """Every look from the front and the back, a row to a demon."""
    size = 300
    sheet = Image.new("RGB", (size * 8, size * len(DEMONS)), (48, 54, 66))
    for row, demon in enumerate(DEMONS):
        for column in range(4):
            name = "%s_%d" % (demon, column + 1)
            files = blockbench.paths(ASSETS, name)
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
    names = []
    for demon in DEMONS:
        for number, look in enumerate(LOOKS[demon], 1):
            name = "%s_%d" % (demon, number)
            write(name, *build(demon, number, look))
            names.append(name)
            print(name)
    if args.preview:
        preview(names, args.preview)


if __name__ == "__main__":
    main()
