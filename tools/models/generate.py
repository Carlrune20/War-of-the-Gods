#!/usr/bin/env python3
"""Builds War of the Gods' own box models: the lesser demon, the centaur, Zamorak's staff and Silverlight.

    python3 tools/models/generate.py [--preview DIR]

For each model it writes its parts as JSON into src/main/resources/assets/warofthegods/models/ and its texture
(and for the demon and the centaur, its team mask: where the team's color goes) into textures/models/. Items also get their
inventory icon, drawn from the model, in textures/items/. With --preview it draws each model the way the game does.
"""

import argparse
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import boxmodel  # noqa: E402
from boxmodel import TEAM, Model, mix, shade  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets",
                    "warofthegods")
MODELS = os.path.join(ROOT, "models")
TEXTURES = os.path.join(ROOT, "textures", "models")
ITEMS = os.path.join(ROOT, "textures", "items")

# ---------------------------------------------------------------- the lesser demon
#
# Zamorak's lesser demon as in Old School RuneScape: a hulking, hunched red brute with a heavy brow, a jaw full of
# fangs, long horns sweeping up and back from its skull, long arms ending in black claws, legs bent like a beast's
# and a long whip of a tail with a spade at its end. A belt and loincloth, and bands round its arms, are in its team's
# color.

HIDE = (176, 38, 30)
HIDE_DARK = (122, 22, 20)
HIDE_DEEP = (84, 14, 14)
HIDE_LIGHT = (206, 70, 52)
BELLY = (190, 64, 48)
HORN = (58, 46, 40)
HORN_TIP = (24, 20, 20)
HORN_RING = (88, 72, 60)
CLAW = (30, 24, 24)
FANG = (232, 224, 196)
EYE = (255, 214, 64)
EYE_CORE = (255, 250, 200)
MOUTH = (48, 8, 8)


def hide(dark_top=True, back_dark=True, belly=False, ridges=None):
    """Red hide, darker on the back and toward the top, lighter in the middle of the front, with muscle lines."""

    def fn(face, x, y, w, h):
        base = HIDE
        if face == "back" and back_dark:
            base = HIDE_DARK
        elif face == "top":
            base = HIDE_DARK if dark_top else HIDE
        elif face == "bottom":
            base = HIDE_DEEP
        elif face == "front" and belly:
            base = BELLY
        # Toward the edges of a face it turns darker, as if rounded.
        edge = min(x, w - 1 - x) if face in ("front", "back", "left", "right") else 2
        if edge == 0 and w > 2:
            base = shade(base, 0.82)
        if ridges and face == ridges[0] and y in ridges[1]:
            base = shade(base, 0.7)
        return base

    return fn


def chest(face, x, y, w, h):
    if face == "front":
        # Two great slabs of muscle, split down the middle, and a line under them.
        if x in (w // 2 - 1, w // 2) and y < h - 2:
            return shade(HIDE, 0.72)
        if y == h - 3 or (y == h - 4 and x in (1, 2, w - 3, w - 2)):
            return shade(HIDE, 0.75)
        if 1 <= y <= 3 and (2 <= x <= 3 or w - 4 <= x <= w - 3):
            return HIDE_LIGHT
        return mix(HIDE, BELLY, 0.3)
    if face == "back":
        # The spine down the middle, and the shoulder blades.
        if x in (w // 2 - 1, w // 2):
            return HIDE_DEEP
        if y in (2, 3) and x in (2, 3, w - 4, w - 3):
            return shade(HIDE_DARK, 0.8)
        return HIDE_DARK
    return hide()(face, x, y, w, h)


def belly(face, x, y, w, h):
    if face == "front":
        # A six pack: three rows of two, split down the middle.
        if x == w // 2:
            return shade(BELLY, 0.72)
        if y % 2 == 1:
            return shade(BELLY, 0.8)
        return BELLY
    return hide()(face, x, y, w, h)


def belt(face, x, y, w, h):
    if face in ("top", "bottom"):
        return (TEAM, 120)
    if y == 0:
        return (TEAM, 150)
    # A dark iron buckle at the front.
    if face == "front" and abs(x - (w - 1) / 2) < 1.1:
        return (52, 48, 46)
    return (TEAM, 210)


def loincloth(face, x, y, w, h):
    if face == "front" or face == "back":
        # Frayed at the bottom, folds down it.
        if y == h - 1 and x % 2 == 1:
            return None
        return (TEAM, 230 if x % 3 else 185)
    return (TEAM, 170)


def band(face, x, y, w, h):
    return (TEAM, 220 if y == 0 else 190)


def skull(face, x, y, w, h):
    if face == "front":
        # Deep eye sockets under the brow, glowing.
        if y in (1, 2) and x in (1, 2, w - 3, w - 2):
            if y == 2 and x in (2, w - 3):
                return EYE_CORE
            return EYE if y == 2 else shade(HIDE_DEEP, 0.8)
        if y == 0:
            return HIDE_DARK
        if x == w // 2 or x == w // 2 - 1:
            return shade(HIDE, 0.9)
        return HIDE
    if face == "top":
        # Ridges running back over the skull.
        return HIDE_DARK if x in (1, w - 2, w // 2) else HIDE
    if face in ("left", "right") and y >= h - 2:
        return HIDE_DARK
    return hide()(face, x, y, w, h)


def brow(face, x, y, w, h):
    if face == "bottom":
        return HIDE_DEEP
    return shade(HIDE_DARK, 0.9) if y == h - 1 else HIDE_DARK


def snout(face, x, y, w, h):
    if face == "front":
        if y == h - 1:
            return MOUTH
        if y == 0 and x in (1, w - 2):
            return (20, 6, 6)
        return HIDE
    if face == "bottom":
        return MOUTH
    if face in ("left", "right") and y == h - 1:
        return MOUTH
    return hide()(face, x, y, w, h)


def jaw(face, x, y, w, h):
    if face == "top":
        return MOUTH if 0 < x < w - 1 else HIDE
    if face == "front" and y == 0:
        return MOUTH
    return hide(dark_top=False)(face, x, y, w, h)


def bone(face, x, y, w, h):
    return FANG if face != "top" or y % 2 else shade(FANG, 0.92)


def horn(t0, t1):
    """A stretch of horn, t0 to t1 of the way from its root to its tip: dull brown at the root, ringed, going black."""

    def fn(face, x, y, w, h):
        t = t0 + (t1 - t0) * (y / max(1, h - 1) if face not in ("top", "bottom") else (0 if face == "bottom" else 1))
        # The base runs from the root (y = h, the box's bottom) toward its tip (y = 0).
        t = t0 + t1 - t
        color = mix(HORN, HORN_TIP, t)
        if face not in ("top", "bottom") and y % 3 == 1:
            color = mix(color, HORN_RING, 0.45)
        if face in ("left", "right") and x == 0:
            color = shade(color, 0.85)
        return color

    return fn


def ear(face, x, y, w, h):
    if face in ("left", "right") and 0 < y < h - 1:
        return HIDE_DEEP
    return HIDE_DARK


def claw(face, x, y, w, h):
    return CLAW if y < h - 1 else (14, 10, 10)


def toe_claw(face, x, y, w, h):
    return CLAW if face != "front" or x else (60, 52, 50)


def forearm(face, x, y, w, h):
    if face == "back" and y in (1, 3, 5) and x == w // 2:
        return HIDE_DEEP
    if face == "front" and y >= h - 2:
        return HIDE_DARK
    return hide()(face, x, y, w, h)


def hand(face, x, y, w, h):
    if face == "bottom":
        return HIDE_DEEP
    return hide()(face, x, y, w, h) if y < h - 1 else HIDE_DARK


def thigh(face, x, y, w, h):
    if face == "front" and y in (2, 3) and 0 < x < w - 1:
        return HIDE_LIGHT if y == 2 else HIDE
    return hide()(face, x, y, w, h)


def hoof(face, x, y, w, h):
    if face == "top":
        return HIDE_DARK
    return CLAW if y == h - 1 else (46, 36, 34)


def tail(i, n):
    def fn(face, x, y, w, h):
        color = mix(HIDE_DARK, HIDE_DEEP, i / n) if face in ("top", "back") else mix(HIDE, HIDE_DARK, i / n)
        if face in ("left", "right") and y == h - 1:
            color = shade(color, 0.85)
        return color

    return fn


def spade(face, x, y, w, h):
    return HIDE_DEEP if face in ("top", "bottom") and (x + y) % 3 == 0 else HIDE_DARK


def spine(face, x, y, w, h):
    return HORN if y else HORN_TIP


def lesser_demon():
    m = Model("lesser_demon", 128, 128, seed=7)
    # Hunched over: the trunk leans forward from the hips.
    body = m.part("body", pivot=(0, 8, 1), rotation=(0.42, 0, 0))
    body.box(-6, -12, -4, 12, 10, 8, chest)
    body.box(-5, -13.5, -2, 10, 2, 6, hide())                      # the heavy neck and shoulders
    body.box(-4, -3, -3, 8, 5, 6, belly)                           # the belly, narrower
    body.box(-4.5, 1.5, -3.5, 9, 2, 7, belt, inflate=0.1)
    body.box(-2.5, 3, -4, 5, 6, 1, loincloth)
    body.box(-2.5, 3, 3, 5, 5, 1, loincloth)
    for i, y in enumerate((-12, -9, -6)):
        body.child("spine%d" % i, pivot=(0, y, 4), rotation=(-0.6, 0, 0)).box(-0.5, -2.5, -0.5, 1, 3, 2, spine)

    head = body.child("head", pivot=(0, -12, -4), rotation=(-0.42, 0, 0))
    head.box(-4, -7, -6, 8, 7, 7, skull)
    head.box(-4.5, -6, -6.8, 9, 2, 2, brow)
    head.box(-3, -4, -9.5, 6, 3, 4, snout)
    head.box(-1.5, -4.6, -9.9, 3, 1, 2, brow)                      # the ridge of the nose
    for side in (-1, 1):
        head.box(side * 2 - 0.5, -1.2, -9.3, 1, 2, 1, bone, inflate=-0.1)  # upper fangs
        head.child("ear_%s" % ("left" if side > 0 else "right"), pivot=(side * 4, -5, -2),
                   rotation=(0.2, side * -0.5, side * 0.6)).box(-0.5, -1, 0, 1, 2, 4, ear)
    jaw_part = head.child("jaw", pivot=(0, -1, -3), rotation=(0.18, 0, 0))
    jaw_part.box(-3, 0, -6, 6, 2, 6, jaw)
    for side in (-1, 1):
        jaw_part.box(side * 1.5 - 0.5, -1.5, -5.6, 1, 2, 1, bone, inflate=-0.15)  # lower tusks
    # The horns: from either side of the crown, out and up, then sweeping back and up into a long point.
    for side in (-1, 1):
        name = "horn_%s" % ("left" if side > 0 else "right")
        h1 = head.child(name, pivot=(side * 3, -6.5, -3.5), rotation=(-0.25, 0, side * 0.95))
        h1.box(-1.5, -4, -1.5, 3, 5, 3, horn(0.0, 0.25))
        h2 = h1.child(name + "_2", pivot=(0, -3.6, 0), rotation=(-0.25, 0, side * -0.55))
        h2.box(-1, -4.5, -1, 2, 5, 2, horn(0.25, 0.5), inflate=0.2)
        h3 = h2.child(name + "_3", pivot=(0, -4.2, 0), rotation=(-0.45, 0, side * -0.35))
        h3.box(-1, -4.5, -1, 2, 5, 2, horn(0.5, 0.75), inflate=-0.1)
        h4 = h3.child(name + "_4", pivot=(0, -4.2, 0), rotation=(-0.4, 0, side * -0.15))
        h4.box(-0.5, -4.5, -0.5, 1, 5, 1, horn(0.75, 1.0))

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        # Hung from the shoulders, hanging straight down whatever the trunk's lean.
        arm = body.child("%s_arm" % name, pivot=(side * 7.5, -10.5, 0), rotation=(-0.42, 0, side * -0.12))
        arm.box(-3, -3, -3, 6, 5, 6, hide(), mirror=side > 0)                # the shoulder
        arm.box(-2.5, 1, -2.5, 5, 7, 5, thigh, mirror=side > 0)              # the upper arm
        arm.box(-2.5, 4, -2.5, 5, 2, 5, band, inflate=0.25)
        fore = arm.child("%s_forearm" % name, pivot=(0, 7.5, 0), rotation=(-0.35, 0, 0))
        fore.box(-2, 0, -2, 4, 8, 4, forearm, mirror=side > 0)
        fore.box(-0.5, 1, 2, 1, 3, 1, spine)                                 # a spur on the back of the arm
        paw = fore.child("%s_hand" % name, pivot=(0, 7.5, 0), rotation=(0.1, 0, 0))
        paw.box(-2.5, 0, -2.5, 5, 3, 5, hand)
        for i in range(3):
            paw.child("%s_claw%d" % (name, i), pivot=(-1.5 + i * 1.5, 3, -2), rotation=(-0.5, 0, 0)).box(
                -0.5, 0, -0.5, 1, 3, 1, claw)
        paw.child("%s_thumb" % name, pivot=(-side * 2.5, 1.5, -1.5), rotation=(-0.3, 0, -side * 0.6)).box(
            -0.5, 0, -0.5, 1, 3, 1, claw)

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        # Bent like a beast's: the thigh forward, the shin back, the foot down to the ground.
        leg = m.part("%s_leg" % name, pivot=(side * 3.2, 9, 1), rotation=(-0.55, 0, side * 0.05))
        leg.box(-2.5, -1, -3, 5, 7, 6, thigh, mirror=side > 0)
        shin = leg.child("%s_shin" % name, pivot=(0, 5.5, 0), rotation=(1.25, 0, 0))
        shin.box(-2, -0.5, -2, 4, 7, 4, forearm, mirror=side > 0)
        foot = shin.child("%s_foot" % name, pivot=(0, 6, 0), rotation=(-0.7, 0, 0))
        foot.box(-1.5, -0.5, -1.5, 3, 5, 3, hide(), mirror=side > 0)
        sole = foot.child("%s_sole" % name, pivot=(0, 4.3, 0), rotation=(0, 0, 0))
        sole.box(-2, -0.5, -3, 4, 2, 4, hoof)
        for i in range(3):
            sole.child("%s_toe%d" % (name, i), pivot=(-1.4 + i * 1.4, 0.5, -3), rotation=(0.3, 0, 0)).box(
                -0.5, -0.5, -2, 1, 1, 2, toe_claw)

    # The tail: a long whip in eight segments, each thinner, ending in a spade.
    link = body.child("tail0", pivot=(0, 1, 3), rotation=(-1.25, 0, 0))
    segments = 8
    for i in range(segments):
        size = 3 if i < 2 else 2 if i < 6 else 1
        link.box(-size / 2, -size / 2, -0.2, size, size, 4, tail(i, segments), inflate=-0.05 * (i % 2))
        nxt = link.child("tail%d" % (i + 1), pivot=(0, 0, 3.8), rotation=(0.2 if i < 5 else 0.05, 0, 0))
        link = nxt
    link.box(-2, -0.5, 0, 4, 1, 3, spade)
    link.child("tail_spade", pivot=(0, 0, 1), rotation=(0, math.pi / 4, 0)).box(-1.5, -0.4, -1.5, 3, 1, 3, spade,
                                                                              inflate=-0.1)
    return m


DEMON_POSES = {
    "front": {},
    "walking": {"left_leg": (0.5, 0, 0), "right_leg": (-0.45, 0, 0), "left_arm": (-0.4, 0, 0),
                "right_arm": (0.45, 0, 0), "tail2": (0, 0.25, 0), "tail4": (0, 0.3, 0), "tail6": (0, 0.3, 0)},
    "attacking": {"right_arm": (-2.0, 0.3, 0), "right_forearm": (-0.4, 0, 0), "jaw": (0.4, 0, 0),
                  "left_arm": (0.3, 0, 0)},
}

# ---------------------------------------------------------------- the centaur
#
# Saradomin's centaur, as in RuneScape: a man from the waist up, bearded, with long brown hair held back by a gold
# circlet, on a chestnut horse's body with white socks, a long dark tail and a blanket over its back. It carries a
# longbow in its left hand and a quiver of arrows on its back. Its blanket, sash, quiver strap and arm bands are in
# its team's color.

COAT = (138, 84, 46)
COAT_DARK = (100, 58, 32)
COAT_DEEP = (70, 40, 24)
COAT_LIGHT = (164, 106, 62)
SOCK = (232, 226, 214)
HOOF = (58, 50, 46)
MANE = (52, 34, 24)
MANE_LIGHT = (78, 52, 34)
SKIN = (222, 170, 128)
SKIN_DARK = (190, 136, 98)
SKIN_LIGHT = (238, 192, 152)
HAIR = (96, 62, 36)
HAIR_DARK = (70, 44, 26)
EYE_WHITE = (240, 240, 236)
IRIS = (52, 92, 170)
LEATHER = (110, 72, 40)
LEATHER_DARK = (74, 46, 26)
BOWWOOD = (150, 104, 56)
BOWWOOD_DARK = (104, 70, 36)
STRING = (236, 232, 220)
FLETCH = (240, 240, 240)
TRIM = (226, 186, 70)


def coat(dark_back=True):
    """A chestnut coat: darker along the top and the edges, as if rounded, and deep underneath."""

    def fn(face, x, y, w, h):
        if face == "bottom":
            return COAT_DEEP
        if face == "top":
            return COAT_DARK if dark_back else COAT
        base = COAT
        if face in ("left", "right", "front", "back"):
            if y == 0 and dark_back:
                base = COAT_DARK
            elif y >= h - 2:
                base = shade(COAT, 0.86)
            elif 1 <= y <= 2 and face in ("left", "right"):
                base = COAT_LIGHT
            edge = min(x, w - 1 - x)
            if edge == 0 and w > 2:
                base = shade(base, 0.85)
        return base

    return fn


def horse_chest(face, x, y, w, h):
    if face == "front":
        # The breast, split down the middle where the man's waist joins it.
        if x in (w // 2 - 1, w // 2) and y > 1:
            return shade(COAT, 0.85)
        return COAT_LIGHT if y < 3 else COAT
    return coat()(face, x, y, w, h)


def blanket(face, x, y, w, h):
    if face in ("left", "right", "front", "back") and y == h - 1:
        return TRIM
    if face in ("left", "right") and y == h - 2 and x % 2 == 0:
        return (TEAM, 150)
    if face == "top" and (x in (0, w - 1) or y in (0, h - 1)):
        return TRIM
    return (TEAM, 215)


def leg_upper(face, x, y, w, h):
    if face == "top":
        return COAT_DARK
    return coat(dark_back=False)(face, x, y, w, h)


def shin(face, x, y, w, h):
    if face == "top":
        return COAT
    # A white sock, ragged at its top.
    if y >= 1 or (y == 0 and x % 2):
        return SOCK if y < h - 1 else shade(SOCK, 0.9)
    return COAT


def horse_hoof(face, x, y, w, h):
    return HOOF if face != "top" else shade(SOCK, 0.9)


def tail_hair(i):
    def fn(face, x, y, w, h):
        if face in ("top", "bottom"):
            return MANE
        # Strands down its length, lighter and darker.
        return MANE_LIGHT if (x + i) % 3 == 0 else MANE if y < h - 1 else shade(MANE, 0.8)

    return fn


def torso(face, x, y, w, h):
    if face == "front":
        # Chest and belly: two slabs of muscle over a lean stomach.
        if y == 4 and 0 < x < w - 1:
            return SKIN_DARK
        if x in (w // 2 - 1, w // 2) and 1 < y < h - 1:
            return shade(SKIN, 0.9)
        if y in (7, 9) and 1 < x < w - 2:
            return shade(SKIN, 0.92)
        if 1 <= y <= 3 and (1 <= x <= 2 or w - 3 <= x <= w - 2):
            return SKIN_LIGHT
        return SKIN
    if face == "back":
        if x in (w // 2 - 1, w // 2):
            return SKIN_DARK
        return shade(SKIN, 0.94)
    if face in ("left", "right") and (x == 0 or x == w - 1):
        return shade(SKIN, 0.9)
    return SKIN


def sash(face, x, y, w, h):
    if face == "front" and abs(x - (w - 1) / 2) < 1.1:
        return TRIM
    if face in ("top", "bottom"):
        return (TEAM, 130)
    return (TEAM, 200 if y else 160)


def strap(face, x, y, w, h):
    if face in ("front", "back") and y % 4 == 2 and x == 0:
        return (TEAM, 150)
    return (TEAM, 205)


def face_paint(face, x, y, w, h):
    if face == "front":
        if y == 3 and x in (1, 2, 5, 6):
            return HAIR_DARK
        if y == 4 and x in (1, 2, 5, 6):
            return IRIS if x in (2, 5) else EYE_WHITE
        if y == 5 and x in (3, 4):
            return SKIN_DARK
        if y == 6 and 2 <= x <= 5:
            return shade(SKIN, 0.8)
        return SKIN
    if face == "top":
        return HAIR
    if face in ("left", "right") and y in (4, 5) and x in (3, 4):
        return SKIN_DARK
    return SKIN


def hair(face, x, y, w, h):
    if face == "bottom":
        return HAIR_DARK
    if face == "front":
        # The fringe over the brow is a little lighter.
        return HAIR if y > 0 or x % 3 else shade(HAIR, 1.12)
    return HAIR_DARK if (x + y) % 4 == 0 else HAIR


def long_hair(face, x, y, w, h):
    if face == "front":
        return None
    if face in ("back", "left", "right"):
        if y == h - 1 and x % 2:
            return None
        return HAIR_DARK if x % 3 == 0 else HAIR
    return HAIR


def beard(face, x, y, w, h):
    if face == "front" and y == 0 and 1 < x < w - 2:
        return (120, 50, 40)
    if face == "front" and y == h - 1 and x in (0, w - 1):
        return None
    return HAIR if (x + y) % 3 else HAIR_DARK


def circlet(face, x, y, w, h):
    # A gold band with Saradomin's blue gem at the front.
    if face == "front" and x in (w // 2 - 1, w // 2):
        return (70, 130, 230)
    if face in ("top", "bottom"):
        return None
    return TRIM if x % 3 else shade(TRIM, 0.85)


def arm(face, x, y, w, h):
    if face == "bottom":
        return SKIN_DARK
    if face in ("left", "right", "front", "back") and (x == 0 or x == w - 1):
        return shade(SKIN, 0.92)
    return SKIN


def arm_band(face, x, y, w, h):
    return (TEAM, 215 if y == 0 else 180)


def bracer(face, x, y, w, h):
    if y == 0 or y == h - 1:
        return LEATHER_DARK
    return LEATHER


def quiver(face, x, y, w, h):
    if face in ("top",):
        return LEATHER_DARK
    if y in (1, h - 2):
        return TRIM
    return LEATHER if x % 2 else shade(LEATHER, 0.9)


def fletching(face, x, y, w, h):
    if face == "top":
        return (40, 30, 24) if (x + y) % 2 else FLETCH
    return FLETCH if y < h - 1 else (196, 40, 40)


def bowwood(face, x, y, w, h):
    return BOWWOOD if (y + x) % 3 else BOWWOOD_DARK


def bow_grip(face, x, y, w, h):
    return LEATHER_DARK if y % 2 else LEATHER


def bowstring(face, x, y, w, h):
    return STRING


def centaur():
    m = Model("centaur", 128, 128, seed=11)
    # The horse's body, level, its front toward -z.
    body = m.part("body", pivot=(0, 9, 1))
    body.box(-5, -4, -10, 10, 8, 20, coat())
    body.box(-4.5, -3.5, -12, 9, 7, 3, horse_chest)                   # the breast
    body.box(-5.5, -4.5, 5, 11, 8, 7, coat())                          # the rump, broader
    body.box(-5.5, -4.6, -4, 11, 5, 9, blanket, inflate=0.15)
    tail = body.child("tail", pivot=(0, -3.5, 11.5), rotation=(0.3, 0, 0))
    tail.box(-1.5, -0.5, -1, 3, 5, 2, tail_hair(0))
    tail_end = tail.child("tail_end", pivot=(0, 4.5, 0), rotation=(0.1, 0, 0))
    tail_end.box(-1.5, 0, -1, 3, 9, 3, tail_hair(1))

    # The man, from the waist up, standing out of the front of the horse.
    man = body.child("torso", pivot=(0, -3, -8))
    man.box(-4, -12, -2.5, 8, 12, 5, torso)
    man.box(-4.5, -2, -3, 9, 2, 6, sash, inflate=0.1)
    man.child("strap", pivot=(0, -6.5, 0), rotation=(0, 0, 0.75)).box(-0.5, -6, -3, 1, 12, 6, strap)
    quiver_part = man.child("quiver", pivot=(1, -6, 3), rotation=(0.15, 0, -0.55))
    quiver_part.box(-1.5, -5, 0, 3, 10, 2, quiver)
    quiver_part.box(-1, -7, 0.5, 2, 2, 1, fletching)

    head = man.child("head", pivot=(0, -12, 0))
    head.box(-4, -8, -4, 8, 8, 8, face_paint)
    head.box(-4, -8, -4, 8, 2, 8, hair, inflate=0.3)                  # hair over the crown and brow
    head.box(-4, -6, 2, 8, 8, 3, long_hair, inflate=0.3)             # long hair down the back of the neck
    head.box(-3, -2, -5, 6, 3, 1, beard, inflate=0.05)
    head.box(-4, -6.6, -4, 8, 1, 8, circlet, inflate=0.45)

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm_part = man.child("%s_arm" % name, pivot=(side * 5.5, -10.5, 0),
                             rotation=(-0.35 if side > 0 else -0.12, 0, side * -0.1))
        arm_part.box(-1.5, -1.5, -1.5, 3, 11, 3, arm, mirror=side > 0)
        arm_part.box(-1.5, 1, -1.5, 3, 2, 3, arm_band, inflate=0.2)
        arm_part.box(-1.5, 6, -1.5, 3, 3, 3, bracer, inflate=0.15)
        if side > 0:
            # The longbow in the left hand, upright whether the arm hangs or is held out to shoot.
            bow = arm_part.child("bow", pivot=(0, 8.5, -0.5), rotation=(0.35, 0, 0))
            bow.box(-0.5, -1.5, -0.5, 1, 3, 1, bow_grip, inflate=0.1)
            upper = bow.child("bow_upper", pivot=(0, -1.5, 0), rotation=(-0.3, 0, 0))
            upper.box(-0.5, -6, -0.5, 1, 6, 1, bowwood)
            upper.child("bow_upper_tip", pivot=(0, -6, 0), rotation=(-0.35, 0, 0)).box(-0.5, -2, -0.5, 1, 2, 1,
                                                                                         bowwood, inflate=-0.1)
            lower = bow.child("bow_lower", pivot=(0, 1.5, 0), rotation=(0.3, 0, 0))
            lower.box(-0.5, 0, -0.5, 1, 6, 1, bowwood)
            lower.child("bow_lower_tip", pivot=(0, 6, 0), rotation=(0.35, 0, 0)).box(-0.5, 0, -0.5, 1, 2, 1,
                                                                                       bowwood, inflate=-0.1)
            bow.box(-0.5, -8.9, 2.6, 1, 18, 1, bowstring, inflate=-0.38)

    # Four legs: the forelegs slim, the hind legs with heavy thighs; white socks and dark hooves.
    for front in (True, False):
        for side in (-1, 1):
            name = "%s_%s_leg" % ("front" if front else "back", "left" if side > 0 else "right")
            leg = m.part(name, pivot=(side * 3, 12 if front else 11, -8 if front else 8))
            if front:
                leg.box(-1.5, -1, -1.5, 3, 7, 3, leg_upper, mirror=side > 0)
            else:
                leg.box(-2, -2, -2, 4, 8, 4, leg_upper, mirror=side > 0)
            low = leg.child(name.replace("_leg", "_shin"), pivot=(0, 6, 0))
            low.box(-1, 0, -1, 2, 5 if front else 6, 2, shin, mirror=side > 0)
            low.box(-1.5, 5 if front else 6, -1.5, 3, 1, 3, horse_hoof)
    return m


CENTAUR_POSES = {
    "front": {},
    "walking": {"front_left_leg": (-0.6, 0, 0), "back_right_leg": (-0.6, 0, 0), "front_right_leg": (0.6, 0, 0),
                "back_left_leg": (0.6, 0, 0), "front_left_shin": (0.5, 0, 0), "back_right_shin": (0.3, 0, 0),
                "tail": (0.2, 0.3, 0)},
    "shooting": {"left_arm": (-math.pi / 2 + 0.35, 0.15, 0.1), "bow": (math.pi / 2 - 0.35, 0, 0),
                 "right_arm": (-math.pi / 2 + 0.12, -0.55, -0.1), "head": (0, 0.15, 0)},
}

# ---------------------------------------------------------------- Zamorak's staff
#
# Drawn at twice the detail of a block's pixels: one unit is half a pixel. The staff runs along y, its top up (-y),
# held where y = 0. A shaft of blackened wood bound in a spiral of gold, a leather grip and gold bands, ending below
# in an iron spike; above, a gold collar and Zamorak's sign: two thick blood-red horns sweeping out and up.

BLACKWOOD = (44, 30, 30)
BLACKWOOD_LIGHT = (66, 46, 42)
GOLD = (226, 178, 52)
GOLD_LIGHT = (252, 222, 120)
GOLD_DARK = (150, 104, 26)
LEATHER = (92, 52, 30)
LEATHER_DARK = (60, 32, 20)
IRON = (118, 118, 126)
IRON_DARK = (70, 70, 78)
BLOOD = (170, 20, 22)
BLOOD_LIGHT = (214, 52, 44)
BLOOD_DARK = (96, 10, 14)


def blackwood(face, x, y, w, h):
    # A gold wire wound round in a spiral: where x + y comes round on each face.
    turn = {"front": 0, "left": 1, "back": 2, "right": 3}.get(face, 0)
    if face not in ("top", "bottom") and (y + x + turn * w) % 8 == 0:
        return GOLD
    if face not in ("top", "bottom") and (y + x + turn * w) % 8 == 1:
        return GOLD_DARK
    return BLACKWOOD_LIGHT if (x == 0 and face in ("front", "left")) else BLACKWOOD


def gold(face, x, y, w, h):
    if face == "top":
        return GOLD_LIGHT
    if face == "bottom":
        return GOLD_DARK
    return GOLD_LIGHT if y == 0 else GOLD_DARK if y == h - 1 else GOLD


def gold_engraved(face, x, y, w, h):
    if face in ("top", "bottom"):
        return gold(face, x, y, w, h)
    if (x + y) % 3 == 0:
        return GOLD_DARK
    return gold(face, x, y, w, h)


def leather(face, x, y, w, h):
    # Strips wound round and round, overlapping.
    if face in ("top", "bottom"):
        return LEATHER_DARK
    return LEATHER_DARK if (y + (x // 2)) % 3 == 0 else LEATHER


def iron(face, x, y, w, h):
    return IRON if (x + y) % 4 else IRON_DARK


def blood(face, x, y, w, h):
    if face in ("front", "back"):
        if x == 0 or x == w - 1:
            return BLOOD_DARK
        return BLOOD_LIGHT if y == 0 else BLOOD
    return BLOOD_DARK if face in ("left", "right") else BLOOD


def blood_dark(face, x, y, w, h):
    return BLOOD_DARK


def horn_red(t0, t1):
    """A stretch of Zamorak's horn, t0 to t1 of the way from its root to its tip: red, darkening to the point."""

    def paint(face, x, y, w, h):
        t = t0 + (t1 - t0) * (1 - y / max(1, h - 1))
        if face in ("top", "bottom"):
            return mix(BLOOD, (40, 4, 6), t * 0.6)
        edge = face in ("left", "right") or (w > 2 and x in (0, w - 1))
        base = BLOOD_DARK if edge else (BLOOD_LIGHT if y % 4 == 0 and t < 0.7 else BLOOD)
        return mix(base, (40, 4, 6), max(0, t - 0.75) * 1.6)

    return paint


def zamorak_staff():
    m = Model("zamorak_staff", 128, 64, seed=11)
    staff = m.part("staff")
    # Below the hand: the shaft, a gold band and an iron spike.
    staff.box(-1, 0, -1, 2, 18, 2, blackwood)
    staff.box(-1.5, 18, -1.5, 3, 2, 3, gold_engraved)
    staff.box(-1, 20, -1, 2, 2, 2, iron)
    staff.box(-0.5, 22, -0.5, 1, 3, 1, iron, inflate=-0.1)
    # The grip, wound in leather between two gold bands.
    staff.box(-1, -6, -1, 2, 12, 2, leather, inflate=0.35)
    staff.box(-1.5, -7.5, -1.5, 3, 1, 3, gold, inflate=0.2)
    staff.box(-1.5, 6, -1.5, 3, 1, 3, gold, inflate=0.2)
    # Above it, the long shaft, banded in gold every so often.
    staff.box(-1, -40, -1, 2, 33, 2, blackwood)
    for y in (-18, -29):
        staff.box(-1.5, y, -1.5, 3, 1, 3, gold, inflate=0.1)
    # The collar: a flared gold cup the head sits in.
    staff.box(-1.5, -42, -1.5, 3, 2, 3, gold_engraved, inflate=0.1)
    staff.box(-2, -44, -2, 4, 2, 4, gold, inflate=0.1)
    staff.box(-2.5, -45, -2.5, 5, 1, 5, gold_engraved)
    # Zamorak's sign on top: two thick red horns from a common root, sweeping out to either side and curving up into
    # points that flare outward at their tips.
    head = staff.child("head", pivot=(0, -45, 0))
    head.box(-3, -3, -2, 6, 3, 4, blood, inflate=0.3)
    head.box(-1.5, -1, -1.5, 3, 1, 3, blood_dark, inflate=0.2)
    # Each link: length, thickness and how much thicker or thinner it is drawn, and its turn from the one before
    # (outward first, then back round to upward, flaring out again at the point).
    links = ((3, 4, 0.0, 1.35), (3, 4, -0.3, -0.15), (3, 3, 0.0, -0.35), (3, 3, -0.3, -0.45), (3, 2, 0.1, -0.4),
             (3, 2, -0.2, -0.15), (2, 1, 0.2, 0.2), (2, 1, -0.1, 0.35))
    for side in (-1, 1):
        link = head.child("horn_%d" % side, pivot=(side * 1.6, -1.6, 0), rotation=(0, 0, side * links[0][3]))
        for i, (length, thick, swell, turn) in enumerate(links):
            if i:
                link = link.child("horn_%d_%d" % (side, i), pivot=(0, -prev, 0), rotation=(0, 0, side * turn))
            # Each overlaps the one before a little, so the horn bends without a gap.
            link.box(-thick / 2, -length, -thick / 2, thick, length + 1, thick,
                     horn_red(i / len(links), (i + 1) / len(links)), inflate=swell)
            prev = length
    return m


# ---------------------------------------------------------------- Silverlight
#
# The demon-slaying sword, also drawn at half a pixel a unit and held where y = 0. A long silver blade with a line
# of glowing light down it ending in an arrowhead, saw-toothed flanges at its base, a gilded hilt of curling horns
# and rays round a sky-blue gem, a brown leather grip, and a gilded pommel of rays round a silver spike.

SILVER = (206, 210, 214)
SILVER_LIGHT = (240, 242, 244)
SILVER_DARK = (140, 146, 152)
LIGHT = (70, 236, 240)
LIGHT_CORE = (196, 255, 255)
GILT = (214, 196, 136)
GILT_LIGHT = (242, 230, 180)
GILT_DARK = (150, 130, 84)


def blade(face, x, y, w, h):
    if face in ("front", "back"):
        middle = (w - 1) / 2
        # The fuller of light down the middle, ending in an arrowhead near the tip.
        if abs(x - middle) < 0.6 and y > 4:
            return LIGHT_CORE if y % 5 else LIGHT
        if y in (4, 5) and abs(x - middle) < 1.6 - (5 - y):
            return LIGHT
        if x == 0 or x == w - 1:
            return SILVER_LIGHT
        return SILVER if abs(x - middle) > 1.2 else SILVER_DARK
    if face in ("left", "right"):
        return SILVER_LIGHT
    return SILVER


def blade_tip(face, x, y, w, h):
    return SILVER_LIGHT if face in ("left", "right") or x in (0, w - 1) else SILVER


def flange(face, x, y, w, h):
    return SILVER_DARK if (x + y) % 2 else SILVER


def gilt(face, x, y, w, h):
    if face == "top":
        return GILT_LIGHT
    if face == "bottom":
        return GILT_DARK
    return GILT_LIGHT if y == 0 else GILT_DARK if y == h - 1 else GILT


def gem(face, x, y, w, h):
    return LIGHT_CORE if 0 < x < w - 1 and 0 < y < h - 1 else LIGHT


def grip(face, x, y, w, h):
    if face in ("top", "bottom"):
        return LEATHER_DARK
    return LEATHER_DARK if (y + x) % 3 == 0 else (120, 82, 52)


def silverlight():
    m = Model("silverlight", 64, 64, seed=3)
    sword = m.part("sword")
    # The grip and pommel below the hand.
    sword.box(-1, -6, -1, 2, 14, 2, grip, inflate=0.2)
    sword.box(-1.5, 8, -1.5, 3, 2, 3, gilt, inflate=0.1)
    pommel = sword.child("pommel", pivot=(0, 11, 0))
    pommel.box(-1, -1, -1, 2, 2, 2, gilt, inflate=0.4)
    pommel.box(-0.5, 1, -0.5, 1, 4, 1, flange)
    for i in range(6):
        ray = pommel.child("pommel_ray%d" % i, pivot=(0, 0, 0), rotation=(0, 0, math.pi / 3 * i + math.pi / 6))
        ray.box(-0.5, -3.5, -0.5, 1, 3, 1, gilt)
    # The hilt: a gilded boss round a blue gem, rays all round it, and two great horns curling up and out.
    hilt = sword.child("hilt", pivot=(0, -9, 0))
    hilt.box(-2.5, -2.5, -1.5, 5, 5, 3, gilt, inflate=0.2)
    hilt.child("gem", pivot=(0, 0, -1.8), glow=True).box(-1, -1, -0.5, 2, 2, 1, gem, inflate=0.1)
    hilt.child("gem_back", pivot=(0, 0, 1.8), glow=True).box(-1, -1, -0.5, 2, 2, 1, gem, inflate=0.1)
    for i in range(10):
        angle = math.pi * 2 * i / 10
        length = 4 if i % 2 else 6
        ray = hilt.child("ray%d" % i, pivot=(0, 0, 0), rotation=(0, 0, angle))
        ray.box(-0.5, -2.5 - length, -0.5, 1, length, 1, gilt, inflate=-0.05)
    for side in (-1, 1):
        horn_part = hilt.child("horn_%d" % side, pivot=(side * 2.5, 0, 0), rotation=(0, 0, side * 1.2))
        horn_part.box(-1, -5, -1, 2, 5, 2, gilt)
        curl = horn_part.child("horn_%d_curl" % side, pivot=(0, -4.6, 0), rotation=(0, 0, side * -1.0))
        curl.box(-0.5, -4, -0.5, 1, 4, 1, gilt, inflate=0.15)
        hook = curl.child("horn_%d_hook" % side, pivot=(0, -3.8, 0), rotation=(0, 0, side * -1.1))
        hook.box(-0.5, -3, -0.5, 1, 3, 1, gilt)
        # Lesser hooks below, curling down.
        low = hilt.child("hook_%d" % side, pivot=(side * 2.2, 2.0, 0), rotation=(0, 0, side * 2.2))
        low.box(-0.5, -4, -0.5, 1, 4, 1, gilt)
        low.child("hook_%d_end" % side, pivot=(0, -3.8, 0), rotation=(0, 0, side * 0.9)).box(-0.5, -2, -0.5, 1, 2, 1,
                                                                                             gilt)
    # The blade: long, with saw-toothed flanges at its base and a point.
    bl = sword.child("blade", pivot=(0, -11.5, 0))
    bl.box(-2.5, -44, -0.5, 5, 44, 1, blade)
    bl.box(-1.5, -47, -0.5, 3, 3, 1, blade_tip, inflate=-0.05)
    bl.box(-0.5, -49, -0.5, 1, 2, 1, blade_tip, inflate=-0.1)
    for side in (-1, 1):
        for i in range(3):
            tooth = bl.child("tooth_%d_%d" % (side, i), pivot=(side * 2.5, -2.5 - i * 3.2, 0),
                             rotation=(0, 0, side * (0.9 - i * 0.12)))
            tooth.box(-0.5, -3.5 + i * 0.5, -0.5, 1, 4 - i, 1, flange, inflate=0.1)
    return m


# ---------------------------------------------------------------- output

MODELS_BUILT = {"lesser_demon": lesser_demon, "centaur": centaur, "zamorak_staff": zamorak_staff, "silverlight": silverlight}
ICONS = {"zamorak_staff", "silverlight"}
ICON_SIZE = 32


def icon(model, texture):
    """The model laid diagonally across a 32 pixel icon, its top to the top right, as Minecraft draws its swords."""
    big = boxmodel.render(model, texture, size=128, yaw=0, pitch=0, roll=45, margin=0.01,
                          up=boxmodel.np.diag([1.0, -1.0, 1.0]))
    small = big.resize((ICON_SIZE, ICON_SIZE), Image.BOX)
    pixels = small.load()
    for y in range(ICON_SIZE):
        for x in range(ICON_SIZE):
            r, g, b, a = pixels[x, y]
            if a < 80:
                pixels[x, y] = (0, 0, 0, 0)
            else:
                # Undo the darkening of pixels the model only partly covered.
                pixels[x, y] = tuple(min(255, int(c * 255 / a)) for c in (r, g, b)) + (255,)
    return small


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", help="also draw each model into this folder")
    args = parser.parse_args()
    for folder in (MODELS, TEXTURES, ITEMS):
        os.makedirs(folder, exist_ok=True)
    for name, build in MODELS_BUILT.items():
        model = build()
        texture, mask = model.paint()
        with open(os.path.join(MODELS, name + ".json"), "w") as file:
            file.write(model.to_json() + "\n")
        texture.save(os.path.join(TEXTURES, name + ".png"))
        if model.has_team(mask):
            mask.save(os.path.join(TEXTURES, name + "_team.png"))
        if name in ICONS:
            icon(model, texture).save(os.path.join(ITEMS, name + ".png"))
        lo, hi = boxmodel.bounds(model)
        print("%s: %d parts, from %s to %s" % (name, len(list(model.parts())), lo.round(1), hi.round(1)))
        if args.preview:
            os.makedirs(args.preview, exist_ok=True)
            background = (54, 62, 74)
            if name in ("lesser_demon", "centaur"):
                poses = DEMON_POSES if name == "lesser_demon" else CENTAUR_POSES
                for pose, extra in poses.items():
                    for yaw in (25, 145):
                        image = boxmodel.render(model, texture, size=640, yaw=yaw, pitch=12, pose=extra, mask=mask,
                                                team=(200, 40, 40) if yaw < 90 else (50, 90, 220),
                                                background=background)
                        image.save(os.path.join(args.preview, "%s_%s_%d.png" % (name, pose, yaw)))
                texture.resize((texture.width * 4, texture.height * 4), Image.NEAREST).save(
                    os.path.join(args.preview, name + "_texture.png"))
            else:
                for yaw in (20, 70):
                    image = boxmodel.render(model, texture, size=640, yaw=yaw, pitch=8, background=background,
                                            roll=0)
                    image.save(os.path.join(args.preview, "%s_%d.png" % (name, yaw)))
                icon(model, texture).resize((256, 256), Image.NEAREST).save(
                    os.path.join(args.preview, name + "_icon.png"))


if __name__ == "__main__":
    main()
