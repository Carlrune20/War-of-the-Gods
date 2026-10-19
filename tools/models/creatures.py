"""Bandos's creatures as box models: the goblin, the hobgoblin, the ork and the ogre.

Each is one model, its parts named as the player model's are (head, body, right_arm, left_arm, right_leg, left_leg)
so the game can move them the way it moves a man; the hobgoblin's spear, the ork's cleaver and the ogre's club are
parts in the right hand. Each unit kind drawn with one of them has a texture of its own, painted on the same layout:
a goblin, a slave, an axe-thrower, a priest, the three spirits and the three sergeants all wear the goblin, and
General Graardor the ork. Boxes a kind does not use (a helmet, a robe) are left clear on its texture, so they do not
show. Where the team's color goes is painted white on the kind's team mask.
"""

from boxmodel import TEAM, Model, shade

CLEAR = None

# ---------------------------------------------------------------- colors

GOBLIN_SKIN = (104, 142, 56)
SLAVE_SKIN = (112, 126, 72)
SPIRIT_SKIN = (150, 208, 196)
HOBGOBLIN_SKIN = (150, 112, 62)
ORK_SKIN = (84, 104, 64)
GRAARDOR_SKIN = (70, 88, 60)
OGRE_SKIN = (154, 136, 94)

EYE = (220, 40, 30)
EYE_YELLOW = (240, 210, 60)
SPIRIT_EYE = (230, 255, 250)
MOUTH = (40, 20, 16)
TOOTH = (230, 222, 190)
NAIL = (60, 50, 40)

LEATHER = (110, 72, 40)
LEATHER_DARK = (72, 46, 26)
RAG = (120, 104, 80)
RAG_DARK = (88, 74, 56)
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
# A small, hunched goblin: a head as wide as its shoulders, long ears sticking out sideways and a long nose, a pot
# belly on short bowed legs, and long arms. Over it, boxes left clear unless the kind wears them: a helmet or hood
# over the head, a tunic, robe or plate over the body, and a robe's skirt or greaves over the legs.

# By kind: skin, eyes, then the paint for the helmet, the body's covering, the arms', and the legs'.
GOBLIN_LOOKS = {
    "goblin": (GOBLIN_SKIN, EYE, clear, tunic(None, None), clear, clear),
    "slave": (SLAVE_SKIN, EYE, clear, ragged(RAG, RAG_DARK), clear, clear),
    "goblin_thrower": (GOBLIN_SKIN, EYE, helm(LEATHER, LEATHER, LEATHER_DARK), tunic(LEATHER, LEATHER_DARK, False),
                       team_band, clear),
    "goblin_priest": (GOBLIN_SKIN, EYE_YELLOW, hood(ROBE, ROBE_DARK), robe(ROBE, ROBE_DARK), clear,
                      robe(ROBE, ROBE_DARK)),
    "spiritual_warrior": (SPIRIT_SKIN, SPIRIT_EYE, helm(STEEL_LIGHT, STEEL, STEEL_DARK),
                          plate(STEEL_LIGHT, STEEL, STEEL_DARK), clear, clear),
    "spiritual_ranger": (SPIRIT_SKIN, SPIRIT_EYE, hood(HOOD_GREEN, shade(HOOD_GREEN, 0.75)),
                         tunic(HOOD_GREEN, shade(HOOD_GREEN, 0.75), False), team_band, clear),
    "spiritual_mage": (SPIRIT_SKIN, SPIRIT_EYE, hood(PURPLE, shade(PURPLE, 0.75)), robe(PURPLE, shade(PURPLE, 0.75)),
                       clear, robe(PURPLE, shade(PURPLE, 0.75))),
    "strongstack": (GOBLIN_SKIN, EYE, helm(STEEL_LIGHT, STEEL, STEEL_DARK), plate(STEEL_LIGHT, STEEL, STEEL_DARK),
                    plate(STEEL_LIGHT, STEEL, STEEL_DARK, False), plate(STEEL_LIGHT, STEEL, STEEL_DARK, False)),
    "steelwill": (shade(GOBLIN_SKIN, 0.9), EYE_YELLOW, hood(ROBE_DARK, (30, 20, 16)),
                  robe(ROBE_DARK, (30, 20, 16)), team_band, robe(ROBE_DARK, (30, 20, 16))),
    "grimspike": (GOBLIN_SKIN, EYE, helm(LEATHER, LEATHER, LEATHER_DARK), tunic(None, None),
                  plate(LEATHER, LEATHER, LEATHER_DARK, False), plate(LEATHER, LEATHER, LEATHER_DARK, False)),
}


def goblin(kind="goblin"):
    skin, eye, helmet, body_cover, arm_cover, leg_cover = GOBLIN_LOOKS[kind]
    m = Model("goblin", 64, 64, seed=21)
    head = m.part("head", pivot=(0, 10, -1))
    head.box(-4.5, -7, -4.5, 9, 7, 8, goblin_face(skin, eye))
    head.box(-1, -4, -6.5, 2, 3, 2, nose(skin))
    head.box(-4.5, -7, -4.5, 9, 4, 8, helmet, inflate=0.5)
    for side in (-1, 1):
        ear = head.child("%s_ear" % ("left" if side > 0 else "right"), pivot=(side * 4.5, -5, -0.5),
                         rotation=(0, 0, side * -0.25))
        ear.box(0 if side > 0 else -4, -1, -0.5, 4, 2, 1, flesh(skin), mirror=side > 0)
        ear.box(4 if side > 0 else -6, -1.5, -0.5, 2, 1, 1, flesh(skin), mirror=side > 0)

    body = m.part("body", pivot=(0, 10, 0))
    body.box(-4, 0, -2, 8, 7, 4, flesh(skin))
    body.box(-3.5, 3, -3, 7, 4, 1, flesh(skin))                      # the pot belly
    body.box(-4, 0, -2, 8, 7, 4, body_cover, inflate=0.3)
    body.box(-4, 7, -2, 8, 2, 4, body_cover, inflate=0.3)             # the hem, down over the hips

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 5.5, 11, 0))
        arm.box(-1.5, -1, -1.5, 3, 9, 3, flesh(skin), mirror=side > 0)
        arm.box(-1.5, -1, -1.5, 3, 4, 3, arm_cover, inflate=0.25)
        leg = m.part("%s_leg" % name, pivot=(side * 2, 17, 0.5))
        leg.box(-1.5, 0, -1.5, 3, 7, 3, feet(skin), mirror=side > 0)
        leg.box(-1.5, 0, -1.5, 3, 5, 3, leg_cover, inflate=0.3)
    return m


# ---------------------------------------------------------------- the hobgoblin
#
# A hobgoblin stands as tall as a man and broader, with a short snout, small tusks and pointed ears that stand up,
# in a leather jerkin over a team-colored tunic, holding a long spear with an iron head.


def jerkin(face, x, y, w, h):
    if face == "bottom":
        return CLEAR
    if face == "front" and 2 <= x <= w - 3 and y < h - 3:
        return (TEAM, 215 if (x + y) % 4 else 185)
    if y >= h - 2:
        return LEATHER_DARK
    return LEATHER if (x + y) % 5 else LEATHER_DARK


def hobgoblin(kind="hobgoblin"):
    skin = HOBGOBLIN_SKIN
    m = Model("hobgoblin", 128, 64, seed=22)
    head = m.part("head", pivot=(0, 1, -0.5))
    head.box(-4, -8, -4, 8, 8, 8, tusked_face(skin, EYE_YELLOW, tusks=False))
    head.box(-2, -4, -5.5, 4, 3, 2, nose(skin))
    for side in (-1, 1):
        head.box(side * 1.5 - 0.5, -2, -5, 1, 2, 1, tooth)
        ear = head.child("%s_ear" % ("left" if side > 0 else "right"), pivot=(side * 4, -6, 0),
                         rotation=(0, 0, side * 0.35))
        ear.box(-1 if side < 0 else 0, -4, -0.5, 1, 4, 1, flesh(skin), mirror=side > 0)

    body = m.part("body", pivot=(0, 1, 0))
    body.box(-4.5, 0, -2.5, 9, 12, 5, flesh(skin))
    body.box(-4.5, 0, -2.5, 9, 12, 5, jerkin, inflate=0.3)

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 6.5, 3, 0))
        arm.box(-2, -1.5, -2, 4, 12, 4, flesh(skin), mirror=side > 0)
        arm.box(-2, -1.5, -2, 4, 3, 4, team_band, inflate=0.25)
        if side < 0:
            # The spear, upright in the right hand and leaning a little forward.
            spear = arm.child("spear", pivot=(0, 9.5, -0.5), rotation=(0.45, 0, 0))
            spear.box(-0.5, -16, -0.5, 1, 24, 1, wood)
            spear.box(-1, -20, -0.5, 2, 4, 1, iron)
        leg = m.part("%s_leg" % name, pivot=(side * 2.2, 13, 0))
        leg.box(-2, 0, -2, 4, 11, 4, feet(skin), mirror=side > 0)
        leg.box(-2, 0, -2, 4, 5, 4, plain(LEATHER_DARK), inflate=0.25)
    return m


# ---------------------------------------------------------------- the ork
#
# An ork is a head taller than a man and twice as broad: a small head sunk between its shoulders, a jutting jaw with
# tusks, iron shoulder plates with a team-colored rim, and a heavy cleaver in its right hand. General Graardor is an
# ork in dark armor, grown huge.

ORK_LOOKS = {
    "ork": (ORK_SKIN, EYE_YELLOW, plate(STEEL_LIGHT, IRON, STEEL_DARK), tunic(LEATHER, LEATHER_DARK, False)),
    "graardor": (GRAARDOR_SKIN, EYE, plate(DARK_STEEL_LIGHT, DARK_STEEL, (40, 40, 46)),
                 plate(DARK_STEEL_LIGHT, DARK_STEEL, (40, 40, 46))),
}


def ork(kind="ork"):
    skin, eye, pads, chest_cover = ORK_LOOKS[kind]
    m = Model("ork", 128, 64, seed=23)
    head = m.part("head", pivot=(0, 2, -1.5))
    head.box(-4, -7, -4, 8, 7, 8, tusked_face(skin, eye))
    head.box(-3.5, -2, -5.5, 7, 3, 2, flesh(skin))                    # the jutting jaw
    for side in (-1, 1):
        head.box(side * 2.5 - 0.5, -4, -6, 1, 2, 1, tooth)
        head.box(side * 4 + (0 if side > 0 else -1), -6, -1, 1, 2, 2, flesh(skin))

    body = m.part("body", pivot=(0, 2, 0))
    body.box(-5.5, 0, -3, 11, 13, 6, flesh(skin))
    body.box(-5.5, 0, -3, 11, 13, 6, chest_cover, inflate=0.3)

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 7.5, 4, 0))
        arm.box(-2.5, -1.5, -2.5, 5, 12, 5, flesh(skin), mirror=side > 0)
        arm.box(-3, -2.5, -3, 6, 4, 6, pads, inflate=0.3)
        if side < 0:
            cleaver = arm.child("weapon", pivot=(0, 9.5, -0.5), rotation=(0.4, 0, 0))
            cleaver.box(-0.5, -3, -0.5, 1, 6, 1, wood)
            cleaver.box(-0.5, -13, -3.5, 1, 10, 5, iron)
        leg = m.part("%s_leg" % name, pivot=(side * 2.8, 15, 0))
        leg.box(-2.5, 0, -2.5, 5, 9, 5, feet(skin), mirror=side > 0)
        leg.box(-2.5, 0, -2.5, 5, 4, 5, ragged(LEATHER, LEATHER_DARK, team_belt=False), inflate=0.3)
    return m


# ---------------------------------------------------------------- the ogre
#
# An ogre: huge, fat and slow, with a small bald head, a heavy brow and an underbite, long arms nearly to its knees,
# a team-colored loincloth and a great spiked club.


def ogre_belly(face, x, y, w, h):
    if face == "front" and y == h // 2 and x == w // 2:
        return shade(OGRE_SKIN, 0.6)                                     # the navel
    return flesh(OGRE_SKIN)(face, x, y, w, h)


def ogre(kind="ogre"):
    skin = OGRE_SKIN
    m = Model("ogre", 128, 64, seed=24)
    head = m.part("head", pivot=(0, 1, -2))
    head.box(-3.5, -6, -3.5, 7, 6, 7, tusked_face(skin, EYE_YELLOW))
    head.box(-3.5, -5, -4.5, 7, 1, 1, flesh(skin, 0.6))               # the brow
    head.box(-1, -4, -4.5, 2, 2, 1, nose(skin))

    body = m.part("body", pivot=(0, 0, 0))
    body.box(-6, 1, -3, 12, 13, 7, flesh(skin))
    body.box(-5, 6, -5, 10, 8, 2, ogre_belly)
    body.box(-6, 12, -3, 12, 4, 7, ragged(LEATHER, LEATHER_DARK, rows=1), inflate=0.3)
    body.box(-3, 14, -3.6, 6, 4, 1, team_cloth)                        # the loincloth's front flap

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 7.5, 3, 0))
        arm.box(-2.5, -1, -2.5, 5, 15, 5, flesh(skin), mirror=side > 0)
        arm.box(-2.5, 11, -2.5, 5, 2, 5, team_band, inflate=0.2)
        if side < 0:
            club = arm.child("weapon", pivot=(0, 13, -0.5), rotation=(0.5, 0, 0))
            club.box(-1, -2, -1, 2, 6, 2, wood)
            club.box(-2, -11, -2, 4, 9, 4, spiked)
        leg = m.part("%s_leg" % name, pivot=(side * 3, 14, 0))
        leg.box(-3, 0, -3, 6, 10, 6, feet(skin), mirror=side > 0)
    return m


# ---------------------------------------------------------------- what is built

# By model: how to build it, and the kinds that wear it.
CREATURES = {
    "goblin": (goblin, list(GOBLIN_LOOKS)),
    "hobgoblin": (hobgoblin, ["hobgoblin"]),
    "ork": (ork, list(ORK_LOOKS)),
    "ogre": (ogre, ["ogre"]),
}

POSES = {
    "front": {},
    "walking": {"right_arm": (0.6, 0, 0), "left_arm": (-0.6, 0, 0), "right_leg": (-0.6, 0, 0),
                "left_leg": (0.6, 0, 0)},
}
