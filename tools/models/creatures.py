"""Bandos's creatures as box models: the goblin, the hobgoblin, the ork and the ogre.

Each is one model, its parts named as the player model's are (head, body, right_arm, left_arm, right_leg, left_leg)
so the game can move them the way it moves a man; the hobgoblin's spear, the ork's cleaver and the ogre's club are
parts in the right hand. Each unit kind drawn with one of them has a texture of its own, painted on the same layout:
a goblin, an axe-thrower, a priest, the three spirits and the three sergeants all wear the goblin, and
General Graardor the ork. Boxes a kind does not use (a helmet, a robe) are left clear on its texture, so they do not
show. Where the team's color goes is painted white on the kind's team mask.
"""

from boxmodel import TEAM, Model, shade

CLEAR = None

# ---------------------------------------------------------------- colors

GOBLIN_SKIN = (104, 142, 56)
SPIRIT_SKIN = (150, 208, 196)
HOBGOBLIN_SKIN = (150, 112, 62)
ORK_SKIN = (84, 104, 64)
GRAARDOR_SKIN = (70, 88, 60)

EYE = (220, 40, 30)
EYE_YELLOW = (240, 210, 60)
SPIRIT_EYE = (230, 255, 250)
MOUTH = (40, 20, 16)
TOOTH = (230, 222, 190)
NAIL = (60, 50, 40)

LEATHER = (110, 72, 40)
LEATHER_DARK = (72, 46, 26)
STEEL = (150, 154, 162)
STEEL_DARK = (96, 100, 108)
STEEL_LIGHT = (196, 200, 206)
DARK_STEEL = (64, 64, 72)
DARK_STEEL_LIGHT = (100, 100, 110)
BONE = (226, 216, 186)
WOOD = (110, 76, 42)
WOOD_DARK = (78, 52, 28)
ROBE = (64, 40, 30)
ROBE_DARK = (44, 28, 22)
HOOD_GREEN = (58, 86, 40)
PURPLE = (92, 60, 120)
IRON = (122, 122, 130)


def flesh(base, dark=0.8):
    """Skin, darker toward the edges of each face and on top and underneath, as if rounded."""

    def fn(face, x, y, w, h):
        c = base
        if face in ("top", "bottom"):
            c = shade(base, 0.85 if face == "top" else 0.7)
        elif min(x, w - 1 - x) == 0 and w > 2:
            c = shade(base, dark)
        if face == "back":
            c = shade(c, 0.92)
        return c

    return fn


def plain(color, edge=0.82):
    def fn(face, x, y, w, h):
        if face in ("top", "bottom"):
            return shade(color, 0.85)
        if min(x, w - 1 - x) == 0 and w > 2:
            return shade(color, edge)
        return color

    return fn


def clear(face, x, y, w, h):
    return CLEAR


def team_cloth(face, x, y, w, h):
    if face in ("top", "bottom"):
        return (TEAM, 150)
    return (TEAM, 220 if (x + y) % 4 else 190)


def ragged(color, dark, team_belt=True, rows=2):
    """Cloth worn to rags at the hem, a team-colored belt of its top rows."""

    def fn(face, x, y, w, h):
        if face == "bottom":
            return CLEAR
        if face == "top":
            return shade(color, 0.85)
        if team_belt and y < rows:
            return (TEAM, 210 if y == 0 else 180)
        if y == h - 1 and (x * 7 + 3) % 3 == 0:
            return CLEAR
        return dark if (x * 5 + y) % 4 == 0 else color

    return fn


def tunic(color, dark, team_body=True):
    """A tunic in the team's color, or the color given, with a leather belt across its lower rows."""

    def fn(face, x, y, w, h):
        if face == "bottom":
            return CLEAR
        if y >= h - 2:
            if face == "front" and y == h - 2 and abs(x - (w - 1) / 2) < 1:
                return IRON
            return LEATHER_DARK if y == h - 1 else LEATHER
        if team_body:
            return (TEAM, 220 if (x + y) % 5 else 190)
        return dark if (x + y) % 5 == 0 else color

    return fn


def robe(color, dark, trim=True):
    """A long robe with a team-colored trim down its front and round its hem."""

    def fn(face, x, y, w, h):
        if face == "bottom":
            return CLEAR
        if trim and (face == "front" and abs(x - (w - 1) / 2) < 1 or y == h - 1):
            return (TEAM, 210)
        return dark if (x * 3 + y) % 6 == 0 else color

    return fn


def plate(light, mid, dark, team_band=True):
    """Plate armor: lighter on top of each face, rivets at the corners, a team band across its middle."""

    def fn(face, x, y, w, h):
        if face == "bottom":
            return CLEAR
        if team_band and face in ("front", "back", "left", "right") and y == h // 2:
            return (TEAM, 220)
        if face == "top" or y == 0:
            return light
        if (x in (0, w - 1)) and (y in (1, h - 1)):
            return dark
        return mid

    return fn


def helm(light, mid, dark, face_open=True):
    """A helmet over the head: open in front of the face below the brow."""

    def fn(face, x, y, w, h):
        if face == "bottom":
            return CLEAR
        if face == "front" and face_open and y >= 2:
            # Only a nose guard down the middle.
            return mid if abs(x - (w - 1) / 2) < 1 and y < h - 2 else CLEAR
        if face in ("left", "right") and y >= h - 2:
            return CLEAR
        if face == "top":
            return light if (x + y) % 3 else mid
        return dark if y == h - 1 else mid

    return fn


def hood(color, dark):
    def fn(face, x, y, w, h):
        if face == "bottom":
            return CLEAR
        if face == "front" and 1 <= y and 1 <= x <= w - 2:
            return CLEAR
        return dark if face == "top" and (x + y) % 3 == 0 else color

    return fn


def goblin_face(skin, eye=EYE):
    """A broad face with a long chin, beady eyes under a heavy brow and a wide mouth with two teeth sticking up."""
    base = flesh(skin)

    def fn(face, x, y, w, h):
        if face == "front":
            if y == 2 and 1 <= x <= w - 2:
                return shade(skin, 0.7)
            if y == 3 and x in (2, w - 3):
                return eye
            if y == 3 and x in (1, 3, w - 4, w - 2):
                return shade(skin, 0.6)
            if y == h - 2 and 2 <= x <= w - 3:
                return MOUTH if x not in (3, w - 4) else TOOTH
        return base(face, x, y, w, h)

    return fn


def tusked_face(skin, eye=EYE_YELLOW, tusks=True):
    """A flat brutish face: small eyes, a wide nose and, low at each corner of the mouth, a tusk."""
    base = flesh(skin)

    def fn(face, x, y, w, h):
        if face == "front":
            if y == 2 and 1 <= x <= w - 2:
                return shade(skin, 0.68)
            if y == 3 and x in (2, w - 3):
                return eye
            if y == h - 2 and 1 <= x <= w - 2:
                if tusks and x in (1, w - 2):
                    return TOOTH
                return MOUTH
        return base(face, x, y, w, h)

    return fn


def nose(skin):
    def fn(face, x, y, w, h):
        if face == "front" and y == h - 1:
            return shade(skin, 0.55)
        return flesh(skin, 0.75)(face, x, y, w, h)

    return fn


def tooth(face, x, y, w, h):
    return TOOTH if face != "top" else shade(TOOTH, 0.85)


def wood(face, x, y, w, h):
    return WOOD_DARK if (y + x) % 4 == 0 else WOOD


def iron(face, x, y, w, h):
    if face in ("front", "back") and y == 0:
        return STEEL_LIGHT
    return STEEL if (x + y) % 5 else STEEL_DARK


def spiked(face, x, y, w, h):
    if face == "top":
        return WOOD_DARK
    if (x + y) % 3 == 0:
        return IRON
    return WOOD


def team_band(face, x, y, w, h):
    return (TEAM, 220 if y == 0 else 190)


def feet(skin):
    def fn(face, x, y, w, h):
        if face == "front" and y == h - 1 and x % 2 == 0:
            return NAIL
        return flesh(skin)(face, x, y, w, h)

    return fn


# ---------------------------------------------------------------- the goblin
#
# A goblin as in Old School RuneScape: lanky and stooped, its small head thrust forward on a hunched back, a long nose
# and jutting jaw, long pointed ears sweeping up and back and a tuft of hair down its crown; long thin arms with iron
# pauldrons on the shoulders, a sleeveless tunic hanging to its knees in front and behind, a loincloth, and big bare
# feet. Over the head a box is left clear unless the kind wears a helmet or hood, and over the shins wraps or greaves.

CLOTH = (204, 194, 168)
CLOTH_DARK = (168, 158, 134)
HAIR = (44, 36, 30)
LOIN = (110, 76, 44)
# Plain eyes: a dark pupil beside the white.
GOBLIN_EYE = (52, 40, 30)
EYE_WHITE = (232, 230, 220)


def cloth(color, dark):
    """A sleeveless tunic, its hem a little darker."""

    def fn(face, x, y, w, h):
        if face == "bottom":
            return CLEAR
        if y == h - 1 or (x * 3 + y * 5) % 11 == 0:
            return dark
        return color

    return fn


def skirt(color, dark):
    """The tunic's flap hanging in front or behind, its bottom edge torn."""

    def fn(face, x, y, w, h):
        if face in ("top", "bottom"):
            return CLEAR
        if y == h - 1 and x % 2 == 1:
            return CLEAR
        return dark if y == h - 1 or x in (0, w - 1) else color

    return fn


def loin(face, x, y, w, h):
    if face == "bottom":
        return CLEAR
    if y == 0:
        return (TEAM, 210)                                               # a belt in the team's color
    return LOIN if (x + y) % 3 else shade(LOIN, 0.8)


def mohawk(color):
    def fn(face, x, y, w, h):
        return shade(color, 1.2) if face == "top" else color

    return fn


def goblin_head(skin, eye):
    """A small head: plain eyes, white with a dark pupil each, under a heavy brow, and a mouth with a fang poking up
    at each corner."""
    base = flesh(skin)

    def fn(face, x, y, w, h):
        if face == "front":
            if y == 1:
                return shade(skin, 0.7)
            if y == 2 and x in (0, w - 1):
                return EYE_WHITE
            if y == 2 and x in (1, w - 2):
                return eye
            if y == h - 1 and 1 <= x <= w - 2:
                return TOOTH if x in (1, w - 2) else MOUTH
        return base(face, x, y, w, h)

    return fn


# By kind: skin, eyes, then the paint for the helmet or hood, the tunic (and its flaps), the pauldrons and the shin
# wraps.
GOBLIN_LOOKS = {
    "goblin": (GOBLIN_SKIN, GOBLIN_EYE, clear, (CLOTH, CLOTH_DARK), iron, clear),
    "goblin_thrower": ((126, 146, 52), GOBLIN_EYE, clear, (LEATHER, LEATHER_DARK), plain(LEATHER), team_band),
    "goblin_priest": ((96, 134, 70), GOBLIN_EYE, hood(ROBE, ROBE_DARK), (ROBE, ROBE_DARK), clear, clear),
    "spiritual_warrior": (SPIRIT_SKIN, SPIRIT_EYE, helm(STEEL_LIGHT, STEEL, STEEL_DARK), (STEEL, STEEL_DARK), iron,
                          plain(STEEL)),
    "spiritual_ranger": (SPIRIT_SKIN, SPIRIT_EYE, hood(HOOD_GREEN, shade(HOOD_GREEN, 0.75)),
                         (HOOD_GREEN, shade(HOOD_GREEN, 0.75)), plain(LEATHER), team_band),
    "spiritual_mage": (SPIRIT_SKIN, SPIRIT_EYE, hood(PURPLE, shade(PURPLE, 0.75)), (PURPLE, shade(PURPLE, 0.75)),
                       clear, clear),
}

# Goblins and axe-throwers in their team's armor upgrades: a leather cap and a quilted jerkin with leather pauldrons
# and shin wraps for padded armor, an iron helm, a steel shirt with iron pauldrons and greaves for plate.
PADDED = (150, 118, 78)
PADDED_DARK = (112, 86, 56)
for name in ("goblin", "goblin_thrower"):
    skin, eye = GOBLIN_LOOKS[name][:2]
    GOBLIN_LOOKS[name + "_padded"] = (skin, eye, helm(LEATHER, LEATHER, LEATHER_DARK), (PADDED, PADDED_DARK),
                                      plain(LEATHER), plain(LEATHER_DARK))
    GOBLIN_LOOKS[name + "_plate"] = (skin, eye, helm(STEEL_LIGHT, STEEL, STEEL_DARK), (STEEL, STEEL_DARK), iron,
                                     plain(STEEL))


def goblin(kind="goblin"):
    skin, eye, helmet, (tunic_color, tunic_dark), pauldron, wraps = GOBLIN_LOOKS[kind]
    m = Model("goblin", 64, 64, seed=21)
    # The head, thrust forward from the hunched shoulders.
    head = m.part("head", pivot=(0, 8, -2.5))
    head.box(-3, -6, -3, 6, 6, 6, goblin_head(skin, eye))
    head.box(-2.5, -1.5, -4, 5, 2, 1, flesh(skin))                    # the jutting jaw
    head.child("nose", pivot=(0, -3.5, -3), rotation=(0.35, 0, 0)).box(-1, -0.5, -3, 2, 2, 3, nose(skin))
    head.box(-0.5, -8, -2, 1, 2, 5, mohawk(HAIR) if helmet is clear else clear)   # hidden under a helmet or hood
    head.box(-3, -6, -3, 6, 3, 6, helmet, inflate=0.5)
    for side in (-1, 1):
        ear = head.child("%s_ear" % ("left" if side > 0 else "right"), pivot=(side * 3, -4, 0.5),
                         rotation=(0, side * -0.45, side * -0.5))
        ear.box(0 if side > 0 else -5, -1, -0.5, 5, 2, 1, flesh(skin), mirror=side > 0)
        ear.box(5 if side > 0 else -7, -1.5, -0.5, 2, 1, 1, flesh(skin), mirror=side > 0)

    # A thin body, stooped forward, with the tunic's flaps hanging from it and a loincloth under them.
    body = m.part("body", pivot=(0, 7, 0))
    torso = body.child("torso", pivot=(0, 0, -0.5), rotation=(0.3, 0, 0))
    torso.box(-3, 0, -1.5, 6, 8, 3, flesh(skin))
    torso.box(-3, 0, -1.5, 6, 8, 3, cloth(tunic_color, tunic_dark), inflate=0.3)
    torso.box(-3, 7, -1.5, 6, 2, 3, loin, inflate=0.45)
    torso.box(-2.5, 8, -2.3, 5, 4, 1, skirt(tunic_color, tunic_dark))
    torso.box(-2.5, 8, 1.3, 5, 4, 1, skirt(tunic_color, tunic_dark))

    # Long thin arms, nearly to the knees, with pauldrons on the shoulders and bands round the wrists.
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 4, 8, -1))
        arm.box(-1, -1, -1, 2, 11, 2, flesh(skin), mirror=side > 0)
        arm.box(-1.5, -2, -1.5, 3, 2, 3, pauldron, inflate=0.3)
        arm.box(-1, 7, -1, 2, 2, 2, team_band, inflate=0.2)
        leg = m.part("%s_leg" % name, pivot=(side * 1.6, 15, 0))
        leg.box(-1, 0, -1, 2, 8, 2, flesh(skin), mirror=side > 0)
        leg.box(-1, 4, -1, 2, 3, 2, wraps, inflate=0.2)
        leg.box(-1.5, 8, -3, 3, 1, 4, feet(skin))                     # big bare feet
    return m


# ---------------------------------------------------------------- the hobgoblin
#
# A hobgoblin as in Old School RuneScape: as tall as a man, orange-skinned, its head long in the snout like a dog's,
# with pointed ears and slit eyes; a white shirt under an open olive vest, a dark belt with an iron buckle, olive
# breeches, great grey pauldrons and big bare feet. It holds a long spear with an iron head.

HOB_SKIN = (198, 122, 34)
VEST = (112, 104, 30)
VEST_DARK = (84, 78, 22)
SHIRT = (214, 208, 200)
BELT = (58, 44, 34)
PAULDRON = (104, 102, 108)


def hob_head(face, x, y, w, h):
    if face == "front":
        if y == 2 and x in (1, 2, w - 3, w - 2):
            return (40, 24, 12)                                          # slit eyes under the brow
        if y == 1:
            return shade(HOB_SKIN, 0.8)
    return flesh(HOB_SKIN)(face, x, y, w, h)


def snout(face, x, y, w, h):
    if face == "front" and y == 0 and x in (0, w - 1):
        return (60, 34, 16)                                              # nostrils
    if face == "front" and y == h - 1:
        return (70, 36, 20)                                              # the mouth
    return flesh(HOB_SKIN, 0.85)(face, x, y, w, h)


def vest(face, x, y, w, h):
    if face == "bottom":
        return CLEAR
    if y == 8:
        if face == "front" and abs(x - (w - 1) / 2) < 1:
            return IRON                                                  # the buckle
        return BELT
    if y > 8:
        return SHIRT if face == "front" and 3 <= x <= w - 4 else VEST_DARK
    if face == "front" and 2 <= x <= w - 3:
        return SHIRT                                                     # the shirt, where the vest hangs open
    if face == "top" and 2 <= x <= w - 3:
        return SHIRT
    return VEST if (x + y) % 6 else VEST_DARK


def breeches(face, x, y, w, h):
    if face == "bottom" or y >= h - 2:
        return flesh(HOB_SKIN)(face, x, y, w, h)
    return VEST_DARK if (x + y) % 5 == 0 else shade(VEST, 0.85)


def hobgoblin(kind="hobgoblin"):
    m = Model("hobgoblin", 128, 64, seed=22)
    head = m.part("head", pivot=(0, 1, -1))
    head.box(-3.5, -7, -3.5, 7, 7, 7, hob_head)
    head.box(-2, -4, -7, 4, 3, 4, snout)
    for side in (-1, 1):
        ear = head.child("%s_ear" % ("left" if side > 0 else "right"), pivot=(side * 3, -6, 0),
                         rotation=(-0.25, 0, side * 0.4))
        ear.box(-1 if side < 0 else 0, -4, -0.5, 1, 4, 2, flesh(HOB_SKIN), mirror=side > 0)

    body = m.part("body", pivot=(0, 1, 0))
    body.box(-4.5, 0, -2.5, 9, 12, 5, flesh(HOB_SKIN))
    body.box(-4.5, 0, -2.5, 9, 12, 5, vest, inflate=0.3)

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 6.5, 3, 0))
        arm.box(-2, -1.5, -2, 4, 12, 4, flesh(HOB_SKIN), mirror=side > 0)
        arm.box(-3, -3, -3, 6, 4, 6, plate(shade(PAULDRON, 1.25), PAULDRON, shade(PAULDRON, 0.7), False))
        arm.box(-2, 8, -2, 4, 1, 4, team_band, inflate=0.2)
        if side < 0:
            # The spear, upright in the right hand and leaning a little forward.
            spear = arm.child("spear", pivot=(0, 9.5, -0.5), rotation=(0.45, 0, 0))
            spear.box(-0.5, -16, -0.5, 1, 24, 1, wood)
            spear.box(-1, -20, -0.5, 2, 4, 1, iron)
        leg = m.part("%s_leg" % name, pivot=(side * 2.2, 13, 0))
        leg.box(-2, 0, -2, 4, 11, 4, breeches, mirror=side > 0)
        leg.box(-2, 10, -4, 4, 1, 2, feet(HOB_SKIN))                    # big bare feet
    return m


# ---------------------------------------------------------------- the ork
#
# An ork as in Old School RuneScape: a hulking, hunched olive brute, a head taller than a man and twice as broad, its
# small head sunk low and forward between huge shoulders under a battered cooking pot for a helmet, with a jutting
# jaw and tusks; plates of bone on its shoulders and chest held on with leather straps, a belt in its team's color,
# and a great double-bladed axe held across its front in both fists. General Graardor is an ork in dark armor, grown
# huge, who has no pot.

BONE_PAD = (214, 206, 182)
BONE_DARK = (170, 160, 136)
POT = (118, 120, 126)
STRAP = (96, 62, 36)

# By kind: skin, eyes, the helmet, the pads' light, middle and dark colors.
ORK_LOOKS = {
    "ork": ((94, 106, 54), GOBLIN_EYE, "pot", (BONE_PAD, BONE_PAD, BONE_DARK)),
    "graardor": (GRAARDOR_SKIN, GOBLIN_EYE, None, (DARK_STEEL_LIGHT, DARK_STEEL, (40, 40, 46))),
}


def pot(face, x, y, w, h):
    if face == "bottom":
        return CLEAR
    if face == "top":
        return shade(POT, 0.8) if 1 <= x <= w - 2 and 1 <= y <= h - 2 else POT
    if y == h - 1:
        return shade(POT, 0.7)                                           # its rim
    return shade(POT, 1.15) if (x * 3 + y) % 7 == 0 else POT


def ork_chest(pads):
    light, mid, dark = pads

    def fn(face, x, y, w, h):
        if face == "bottom":
            return CLEAR
        if y == h - 2:
            if face == "front" and abs(x - (w - 1) / 2) < 1:
                return IRON
            return (TEAM, 210)                                           # the belt
        if y == h - 1:
            return CLEAR
        # A strap from each shoulder crossing the chest and back, a plate of bone where they cross.
        if face in ("front", "back"):
            if 2 <= y <= 5 and abs(x - (w - 1) / 2) < 3:
                return light if y > 2 else mid
            if abs((x - y) % w) < 1 or abs((x + y - (w - 1)) % w) < 1:
                return STRAP
        return CLEAR

    return fn


def ork(kind="ork"):
    skin, eye, helmet, pads = ORK_LOOKS[kind]
    light, mid, dark = pads
    m = Model("ork", 128, 64, seed=23)
    head = m.part("head", pivot=(0, 5, -4))
    head.box(-3, -6, -3, 6, 6, 6, goblin_head(skin, eye))
    head.box(-3, -2, -4.2, 6, 2, 1, flesh(skin))                       # the jutting jaw
    for side in (-1, 1):
        head.box(side * 2 - 0.5, -3.5, -4.6, 1, 2, 1, tooth)
    pot_paint = pot if helmet == "pot" else clear
    head.box(-3.5, -7, -3.5, 7, 3, 7, pot_paint, inflate=0.2)
    head.box(3.7, -6, -0.5, 2, 1, 1, pot_paint)                         # the pot's handle

    body = m.part("body", pivot=(0, 3, 0))
    torso = body.child("torso", pivot=(0, 1, 0), rotation=(0.35, 0, 0))
    torso.box(-6, 0, -3.5, 12, 11, 7, flesh(skin))
    torso.box(-6, 0, -3.5, 12, 11, 7, ork_chest(pads), inflate=0.3)
    torso.box(-5.5, 10, -3, 11, 3, 6, plain(LEATHER), inflate=0.3)    # a leather kilt over the hips

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        # The arms brought forward and in, the fists together in front holding the axe.
        arm = m.part("%s_arm" % name, pivot=(side * 7.5, 5, -2), rotation=(-0.75, 0, side * 0.35))
        arm.box(-2.5, -2, -2.5, 5, 13, 5, flesh(skin), mirror=side > 0)
        arm.box(-3.5, -3.5, -3.5, 7, 4, 7, plate(light, mid, dark, False), inflate=0.2)
        arm.box(-2.5, 8, -2.5, 5, 2, 5, plain(STRAP), inflate=0.2)
        if side < 0:
            axe = arm.child("weapon", pivot=(1.5, 10.5, 0), rotation=(0.6, 0, -1.2))
            axe.box(-0.5, -12, -0.5, 1, 18, 1, wood)
            axe.box(-0.5, -14, -6, 1, 8, 5, iron)
            axe.box(-0.5, -14, 1, 1, 8, 5, iron)
        leg = m.part("%s_leg" % name, pivot=(side * 3, 15, 0))
        leg.box(-2.5, 0, -2.5, 5, 9, 5, feet(skin), mirror=side > 0)
        leg.box(-2.5, 3, -2.5, 5, 2, 5, plain(STRAP), inflate=0.2)
    return m


# ---------------------------------------------------------------- the ogre
#
# An ogre as in Old School RuneScape: huge, fat and tan, with a small bald head under a heavy brow, small pale eyes
# and a wide mouth; a great round belly, broad shoulders and long bare arms ending in fists; an olive loincloth
# wrapped round the hips on a belt in its team's color, olive bracers on its forearms and olive boots. It fights with
# its fists.

OGRE_TAN = (206, 180, 136)
OLIVE = (112, 112, 56)
OLIVE_DARK = (84, 84, 42)


def ogre_head(face, x, y, w, h):
    if face == "front":
        if y == 2 and x in (1, w - 2):
            return EYE_WHITE                                             # small pale eyes
        if y == h - 2 and 1 <= x <= w - 2:
            return MOUTH                                                 # the wide mouth
    return flesh(OGRE_TAN)(face, x, y, w, h)


def ogre_belly(face, x, y, w, h):
    if face == "front" and y == h // 2 and x == w // 2:
        return shade(OGRE_TAN, 0.6)                                      # the navel
    return flesh(OGRE_TAN, 0.88)(face, x, y, w, h)


def wrap(color, dark, team_belt=False):
    """Cloth wrapped round, a little darker in bands, its top row a belt in the team's color if asked."""

    def fn(face, x, y, w, h):
        if face == "bottom":
            return CLEAR
        if face == "top":
            return shade(color, 0.85)
        if team_belt and y == 0:
            return (TEAM, 210)
        return dark if (x * 3 + y * 2) % 7 == 0 or y == h - 1 else color

    return fn


def booted(skin, boot, boot_dark, rows):
    """A bare leg in a boot covering its bottom rows."""

    def fn(face, x, y, w, h):
        if face == "bottom" or y >= h - rows:
            if face != "top" and y == h - rows:
                return boot_dark
            return boot if face != "bottom" else boot_dark
        return flesh(skin)(face, x, y, w, h)

    return fn


def ogre(kind="ogre"):
    skin = OGRE_TAN
    m = Model("ogre", 128, 64, seed=24)
    head = m.part("head", pivot=(0, 2, -2))
    head.box(-3, -6, -3, 6, 6, 6, ogre_head)
    head.box(-3, -5, -3.6, 6, 1, 1, flesh(skin, 0.6))                  # the heavy brow
    head.box(-1, -4, -3.8, 2, 1, 1, nose(skin))

    body = m.part("body", pivot=(0, 0, 0))
    body.box(-7, 1, -3.5, 14, 11, 7, flesh(skin))
    body.box(-6, 5, -6, 12, 9, 3, ogre_belly)                          # the great belly
    body.box(-6.5, 11, -3.5, 13, 4, 7, wrap(OLIVE, OLIVE_DARK, team_belt=True), inflate=0.4)
    body.box(-3, 14, -4.2, 6, 4, 1, wrap(OLIVE, OLIVE_DARK))           # the loincloth's flaps
    body.box(-3, 14, 3.2, 6, 4, 1, wrap(OLIVE, OLIVE_DARK))

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 8.5, 3, 0))
        arm.box(-2.5, -1, -2.5, 5, 13, 5, flesh(skin), mirror=side > 0)
        arm.box(-2.5, 6, -2.5, 5, 5, 5, wrap(OLIVE, OLIVE_DARK), inflate=0.3)   # the bracer
        arm.box(-3, 12, -3, 6, 3, 6, flesh(skin))                       # the fist
        leg = m.part("%s_leg" % name, pivot=(side * 3.5, 14, 0))
        leg.box(-3, 0, -3, 6, 10, 6, booted(skin, OLIVE, OLIVE_DARK, 6), mirror=side > 0)
        leg.box(-3, 4, -3, 6, 1, 6, plain(OLIVE_DARK), inflate=0.3)    # the boot's turned-down top
    return m


# ---------------------------------------------------------------- the sergeants
#
# General Graardor's three sergeants as in Old School RuneScape: big, muscled goblins, a head taller than a goblin and
# broad as an ork, yellow-green, with pointed ears and a belt in their team's color. Strongstack is bald with a grey
# beard, grey pauldrons and bracers, brown breeches, bare feet and a spiked flail; Steelwill has a purple crest and
# beard, grey pauldrons and bracers, grey breeches, dark spiked boots and a clawed staff; Grimspike a red bandana and
# an eyepatch, one dull pauldron on his left shoulder, black breeches, bare feet and an axe. Each kind's weapon is a
# part in the right hand, left clear on the others' textures. The model is named for Strongstack, as the ork's is
# for the ork that Graardor wears too.

PAULDRON_GREY = (184, 184, 196)
CREST = (106, 30, 112)
BANDANA = (134, 50, 48)
PATCH = (34, 30, 30)
BOOT = (64, 70, 58)

# By kind: skin, crest, bandana, beard, the left and right pauldrons, bracers, breeches, boots (None for bare feet),
# and the weapon in the right hand.
SERGEANT_LOOKS = {
    "strongstack": ((170, 180, 56), None, None, STEEL, PAULDRON_GREY, PAULDRON_GREY, PAULDRON_GREY,
                    (100, 88, 66), None, "flail"),
    "steelwill": ((178, 182, 60), CREST, None, CREST, PAULDRON_GREY, PAULDRON_GREY, PAULDRON_GREY,
                  (112, 106, 110), BOOT, "claw"),
    "grimspike": ((168, 182, 54), None, BANDANA, None, (114, 114, 92), None, None, (58, 56, 60), None, "axe"),
}


def chest(skin):
    """A muscled chest: a line under the pecs and down the middle of the belly."""
    base = flesh(skin)

    def fn(face, x, y, w, h):
        if face == "front" and (y == 4 and 1 <= x <= w - 2 or y > 4 and x in (w // 2 - 1, w // 2) and y % 2 == 0):
            return shade(skin, 0.8)
        return base(face, x, y, w, h)

    return fn


def breeches_leg(skin, cloth_color, boot):
    """Breeches down to the shins, then a boot, or bare feet with nails."""

    def fn(face, x, y, w, h):
        if y >= h - 3 or face == "bottom":
            if boot:
                if face == "front" and y == h - 1 and x % 2 == 1:
                    return STEEL_LIGHT                                   # spikes on the toes
                return boot
            return feet(skin)(face, x, y, w, h)
        if face == "top":
            return shade(cloth_color, 0.85)
        return shade(cloth_color, 0.85) if (x * 5 + y * 3) % 9 == 0 else cloth_color

    return fn


def color_or_clear(color, paint=plain):
    return paint(color) if color else clear


def team_belt(face, x, y, w, h):
    if face == "front" and abs(x - (w - 1) / 2) < 1:
        return STEEL_LIGHT                                               # the buckle
    return (TEAM, 210)


def sergeant(kind="strongstack"):
    skin, crest, bandana, beard, left_pad, right_pad, bracer, cloth_color, boot, weapon = SERGEANT_LOOKS[kind]
    m = Model("strongstack", 128, 64, seed=25)
    head = m.part("head", pivot=(0, 2, -1.5))
    head.box(-3, -6, -3, 6, 6, 6, goblin_head(skin, GOBLIN_EYE))
    head.box(-2.5, -1.5, -3.8, 5, 2, 1, flesh(skin))                  # the jutting jaw
    head.box(-1, -4, -3.8, 1, 2, 1, nose(skin))
    head.box(-1, -0.5, -4.2, 2, 2, 1, color_or_clear(beard))            # the beard
    head.box(-0.5, -9, -2.5, 1, 3, 5, mohawk(crest) if crest else clear)
    head.box(-3, -6, -3, 6, 2, 6, color_or_clear(bandana), inflate=0.4)
    eyepatch = plain(PATCH) if bandana else clear
    head.box(-3, -4, -3, 6, 1, 6, eyepatch, inflate=0.2)               # the eyepatch's strap
    head.box(-3, -4.5, -3.6, 2, 2, 1, eyepatch)                         # the patch, over the right eye
    for side in (-1, 1):
        ear = head.child("%s_ear" % ("left" if side > 0 else "right"), pivot=(side * 3, -4, 0.5),
                         rotation=(0, side * -0.3, side * -0.6))
        ear.box(0 if side > 0 else -3, -1, -0.5, 3, 2, 1, flesh(skin), mirror=side > 0)

    body = m.part("body", pivot=(0, 0, 0))
    body.box(-5.5, 1, -3, 11, 10, 6, chest(skin))
    body.box(-5, 10, -2.5, 10, 2, 5, team_belt, inflate=0.4)
    body.box(-5, 11, -2.5, 10, 2, 5, breeches_leg(skin, cloth_color, None))

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 7.5, 3, 0))
        arm.box(-2, -1, -2, 4, 13, 4, flesh(skin), mirror=side > 0)
        pad = left_pad if side > 0 else right_pad
        arm.box(-3, -2.5, -3, 6, 4, 6, plate(shade(pad, 1.1), pad, shade(pad, 0.75), False) if pad else clear,
                inflate=0.2)
        arm.box(-2, 6, -2, 4, 4, 4, color_or_clear(bracer), inflate=0.3)
        arm.box(-2.5, 11, -2.5, 5, 2, 5, flesh(skin))                   # the fist
        if side < 0:
            flail = arm.child("flail", pivot=(0, 12, 0))
            paint = (lambda p: p) if weapon == "flail" else (lambda p: clear)
            flail.box(-0.5, -1, -0.5, 1, 3, 1, paint(wood))
            flail.box(-0.5, 2, -0.5, 1, 3, 1, paint(plain(IRON)))       # the chain
            flail.box(-2, 5, -2, 4, 4, 4, paint(iron))                   # the ball
            flail.box(-0.5, 4.5, -2.5, 1, 5, 5, paint(plain(STEEL_LIGHT)))   # its spikes
            claw = arm.child("claw", pivot=(0, 12, 0), rotation=(0.7, 0, 0))
            paint = (lambda p: p) if weapon == "claw" else (lambda p: clear)
            claw.box(-0.5, -8, -0.5, 1, 14, 1, paint(plain(STEEL)))
            claw.box(-2.5, 6, -2.5, 5, 2, 5, paint(iron))
            for px, pz in ((-2.5, -2.5), (1.5, -2.5), (-2.5, 1.5), (1.5, 1.5)):
                claw.box(px, 8, pz, 1, 3, 1, paint(plain(STEEL_LIGHT)))  # the claws
            axe = arm.child("weapon", pivot=(1.5, 10.5, 0), rotation=(0.6, 0, -1.2))
            axe.box(-0.5, -12, -0.5, 1, 18, 1, wood)
            axe.box(-0.5, -14, -6, 1, 8, 5, iron)
            axe.box(-0.5, -14, 1, 1, 8, 5, iron)
        leg = m.part("%s_leg" % name, pivot=(side * 3, 15, 0))
        leg.box(-2.5, 0, -2.5, 5, 9, 5, feet(skin), mirror=side > 0)
        leg.box(-2.5, 3, -2.5, 5, 2, 5, plain(STRAP), inflate=0.2)
    return m


# ---------------------------------------------------------------- the ogre
#
# An ogre as in Old School RuneScape: huge, fat and tan, with a small bald head under a heavy brow, small pale eyes
# and a wide mouth; a great round belly, broad shoulders and long bare arms ending in fists; an olive loincloth
# wrapped round the hips on a belt in its team's color, olive bracers on its forearms and olive boots. It fights with
# its fists.

OGRE_TAN = (206, 180, 136)
OLIVE = (112, 112, 56)
OLIVE_DARK = (84, 84, 42)


def ogre_head(face, x, y, w, h):
    if face == "front":
        if y == 2 and x in (1, w - 2):
            return EYE_WHITE                                             # small pale eyes
        if y == h - 2 and 1 <= x <= w - 2:
            return MOUTH                                                 # the wide mouth
    return flesh(OGRE_TAN)(face, x, y, w, h)


def ogre_belly(face, x, y, w, h):
    if face == "front" and y == h // 2 and x == w // 2:
        return shade(OGRE_TAN, 0.6)                                      # the navel
    return flesh(OGRE_TAN, 0.88)(face, x, y, w, h)


def wrap(color, dark, team_belt=False):
    """Cloth wrapped round, a little darker in bands, its top row a belt in the team's color if asked."""

    def fn(face, x, y, w, h):
        if face == "bottom":
            return CLEAR
        if face == "top":
            return shade(color, 0.85)
        if team_belt and y == 0:
            return (TEAM, 210)
        return dark if (x * 3 + y * 2) % 7 == 0 or y == h - 1 else color

    return fn


def booted(skin, boot, boot_dark, rows):
    """A bare leg in a boot covering its bottom rows."""

    def fn(face, x, y, w, h):
        if face == "bottom" or y >= h - rows:
            if face != "top" and y == h - rows:
                return boot_dark
            return boot if face != "bottom" else boot_dark
        return flesh(skin)(face, x, y, w, h)

    return fn


def ogre(kind="ogre"):
    skin = OGRE_TAN
    m = Model("ogre", 128, 64, seed=24)
    head = m.part("head", pivot=(0, 2, -2))
    head.box(-3, -6, -3, 6, 6, 6, ogre_head)
    head.box(-3, -5, -3.6, 6, 1, 1, flesh(skin, 0.6))                  # the heavy brow
    head.box(-1, -4, -3.8, 2, 1, 1, nose(skin))

    body = m.part("body", pivot=(0, 0, 0))
    body.box(-7, 1, -3.5, 14, 11, 7, flesh(skin))
    body.box(-6, 5, -6, 12, 9, 3, ogre_belly)                          # the great belly
    body.box(-6.5, 11, -3.5, 13, 4, 7, wrap(OLIVE, OLIVE_DARK, team_belt=True), inflate=0.4)
    body.box(-3, 14, -4.2, 6, 4, 1, wrap(OLIVE, OLIVE_DARK))           # the loincloth's flaps
    body.box(-3, 14, 3.2, 6, 4, 1, wrap(OLIVE, OLIVE_DARK))

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 8.5, 3, 0))
        arm.box(-2.5, -1, -2.5, 5, 13, 5, flesh(skin), mirror=side > 0)
        arm.box(-2.5, 6, -2.5, 5, 5, 5, wrap(OLIVE, OLIVE_DARK), inflate=0.3)   # the bracer
        arm.box(-3, 12, -3, 6, 3, 6, flesh(skin))                       # the fist
        leg = m.part("%s_leg" % name, pivot=(side * 3.5, 14, 0))
        leg.box(-3, 0, -3, 6, 10, 6, booted(skin, OLIVE, OLIVE_DARK, 6), mirror=side > 0)
        leg.box(-3, 4, -3, 6, 1, 6, plain(OLIVE_DARK), inflate=0.3)    # the boot's turned-down top
    return m


# ---------------------------------------------------------------- the sergeants
#
# General Graardor's three sergeants as in Old School RuneScape: big, muscled goblins, a head taller than a goblin and
# broad as an ork, yellow-green, with pointed ears and a belt in their team's color. Strongstack is bald with a grey
# beard, grey pauldrons and bracers, brown breeches, bare feet and a spiked flail; Steelwill has a purple crest and
# beard, grey pauldrons and bracers, grey breeches, dark spiked boots and a clawed staff; Grimspike a red bandana and
# an eyepatch, one dull pauldron on his left shoulder, black breeches, bare feet and an axe. Each kind's weapon is a
# part in the right hand, left clear on the others' textures. The model is named for Strongstack, as the ork's is
# for the ork that Graardor wears too.

PAULDRON_GREY = (184, 184, 196)
CREST = (106, 30, 112)
BANDANA = (134, 50, 48)
PATCH = (34, 30, 30)
BOOT = (64, 70, 58)

# By kind: skin, crest, bandana, beard, the left and right pauldrons, bracers, breeches, boots (None for bare feet),
# and the weapon in the right hand.
SERGEANT_LOOKS = {
    "strongstack": ((170, 180, 56), None, None, STEEL, PAULDRON_GREY, PAULDRON_GREY, PAULDRON_GREY,
                    (100, 88, 66), None, "flail"),
    "steelwill": ((178, 182, 60), CREST, None, CREST, PAULDRON_GREY, PAULDRON_GREY, PAULDRON_GREY,
                  (112, 106, 110), BOOT, "claw"),
    "grimspike": ((168, 182, 54), None, BANDANA, None, (114, 114, 92), None, None, (58, 56, 60), None, "axe"),
}


def chest(skin):
    """A muscled chest: a line under the pecs and down the middle of the belly."""
    base = flesh(skin)

    def fn(face, x, y, w, h):
        if face == "front" and (y == 4 and 1 <= x <= w - 2 or y > 4 and x in (w // 2 - 1, w // 2) and y % 2 == 0):
            return shade(skin, 0.8)
        return base(face, x, y, w, h)

    return fn


def breeches_leg(skin, cloth_color, boot):
    """Breeches down to the shins, then a boot, or bare feet with nails."""

    def fn(face, x, y, w, h):
        if y >= h - 3 or face == "bottom":
            if boot:
                if face == "front" and y == h - 1 and x % 2 == 1:
                    return STEEL_LIGHT                                   # spikes on the toes
                return boot
            return feet(skin)(face, x, y, w, h)
        if face == "top":
            return shade(cloth_color, 0.85)
        return shade(cloth_color, 0.85) if (x * 5 + y * 3) % 9 == 0 else cloth_color

    return fn


def color_or_clear(color, paint=plain):
    return paint(color) if color else clear


def team_belt(face, x, y, w, h):
    if face == "front" and abs(x - (w - 1) / 2) < 1:
        return STEEL_LIGHT                                               # the buckle
    return (TEAM, 210)


def sergeant(kind="strongstack"):
    skin, crest, bandana, beard, left_pad, right_pad, bracer, cloth_color, boot, weapon = SERGEANT_LOOKS[kind]
    m = Model("strongstack", 128, 64, seed=25)
    head = m.part("head", pivot=(0, 2, -1.5))
    head.box(-3, -6, -3, 6, 6, 6, goblin_head(skin, GOBLIN_EYE))
    head.box(-2.5, -1.5, -3.8, 5, 2, 1, flesh(skin))                  # the jutting jaw
    head.box(-1, -4, -3.8, 1, 2, 1, nose(skin))
    head.box(-1, -0.5, -4.2, 2, 2, 1, color_or_clear(beard))            # the beard
    head.box(-0.5, -9, -2.5, 1, 3, 5, mohawk(crest) if crest else clear)
    head.box(-3, -6, -3, 6, 2, 6, color_or_clear(bandana), inflate=0.4)
    eyepatch = plain(PATCH) if bandana else clear
    head.box(-3, -4, -3, 6, 1, 6, eyepatch, inflate=0.2)               # the eyepatch's strap
    head.box(-3, -4.5, -3.6, 2, 2, 1, eyepatch)                         # the patch, over the right eye
    for side in (-1, 1):
        ear = head.child("%s_ear" % ("left" if side > 0 else "right"), pivot=(side * 3, -4, 0.5),
                         rotation=(0, side * -0.3, side * -0.6))
        ear.box(0 if side > 0 else -3, -1, -0.5, 3, 2, 1, flesh(skin), mirror=side > 0)

    body = m.part("body", pivot=(0, 0, 0))
    body.box(-5.5, 1, -3, 11, 10, 6, chest(skin))
    body.box(-5, 10, -2.5, 10, 2, 5, team_belt, inflate=0.4)
    body.box(-5, 11, -2.5, 10, 2, 5, breeches_leg(skin, cloth_color, None))

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 7.5, 3, 0))
        arm.box(-2, -1, -2, 4, 13, 4, flesh(skin), mirror=side > 0)
        pad = left_pad if side > 0 else right_pad
        arm.box(-3, -2.5, -3, 6, 4, 6, plate(shade(pad, 1.1), pad, shade(pad, 0.75), False) if pad else clear,
                inflate=0.2)
        arm.box(-2, 6, -2, 4, 4, 4, color_or_clear(bracer), inflate=0.3)
        arm.box(-2.5, 11, -2.5, 5, 2, 5, flesh(skin))                   # the fist
        if side < 0:
            flail = arm.child("flail", pivot=(0, 12, 0))
            paint = (lambda p: p) if weapon == "flail" else (lambda p: clear)
            flail.box(-0.5, -1, -0.5, 1, 4, 1, paint(wood))
            flail.box(-0.5, 3, -0.5, 1, 5, 1, paint(plain(IRON)))       # the chain
            flail.box(-2, 8, -2, 4, 4, 4, paint(iron))                   # the spiked ball
            flail.box(-0.5, 7, -3, 1, 6, 6, paint(plain(STEEL_LIGHT)))   # its spikes
            claw = arm.child("claw", pivot=(0, 12, 0), rotation=(0.3, 0, 0))
            paint = (lambda p: p) if weapon == "claw" else (lambda p: clear)
            claw.box(-0.5, -6, -0.5, 1, 15, 1, paint(plain(STEEL)))
            claw.box(-2.5, 9, -2.5, 5, 2, 5, paint(iron))
            for px, pz in ((-2.5, -2.5), (1.5, -2.5), (-2.5, 1.5), (1.5, 1.5)):
                claw.box(px, 11, pz, 1, 4, 1, paint(plain(STEEL_LIGHT)))  # the claws
            axe = arm.child("axe", pivot=(0, 12, -0.5), rotation=(-1.1, 0, 0))
            paint = (lambda p: p) if weapon == "axe" else (lambda p: clear)
            axe.box(-0.5, -2, -0.5, 1, 14, 1, paint(wood))
            axe.box(-0.5, 8, -5, 1, 5, 4, paint(iron))                   # the blade
        leg = m.part("%s_leg" % name, pivot=(side * 2.5, 12, 0))
        leg.box(-2.5, 0, -2.5, 5, 12, 5, breeches_leg(skin, cloth_color, boot), mirror=side > 0)
    return m


# ---------------------------------------------------------------- what is built

# By model: how to build it, and the kinds that wear it.
CREATURES = {
    "goblin": (goblin, list(GOBLIN_LOOKS)),
    "hobgoblin": (hobgoblin, ["hobgoblin"]),
    "ork": (ork, list(ORK_LOOKS)),
    "ogre": (ogre, ["ogre"]),
    "strongstack": (sergeant, list(SERGEANT_LOOKS)),
}

POSES = {
    "front": {},
    "walking": {"right_arm": (0.6, 0, 0), "left_arm": (-0.6, 0, 0), "right_leg": (-0.6, 0, 0),
                "left_leg": (0.6, 0, 0)},
}
