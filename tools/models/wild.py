"""Forinthry's wild creatures as box models: the ghost, the hill giant, the hellhound, the deadly red spider, the
gargoyle, the chromatic dragons (one model, red, blue, green and black) and the bandit. They belong to no team, so
none has a team mask.

Their parts are named for EntityWildMob's renderer (com.warofthegods.client.ModelWild) to move them: a head, a body,
arms and legs as the player model's are named for the ghost, the giant, the gargoyle and the bandit; front and back
legs, a jaw, a neck and a tail in links for the hound and the dragons; eight legs for the spider; and wings for the
gargoyle and the dragons, folded, since they do not fly.
"""

from boxmodel import Model, shade

# ---------------------------------------------------------------- painting


def plain(color, edge=0.82):
    def fn(face, x, y, w, h):
        if face in ("top", "bottom"):
            return shade(color, 0.88 if face == "top" else 0.7)
        if min(x, w - 1 - x) == 0 and w > 2:
            return shade(color, edge)
        return color

    return fn


def mottled(color, dark, light=None, every=5):
    """Hide, stone or scales: the color broken up by darker (and lighter) flecks in a pattern."""

    def fn(face, x, y, w, h):
        c = color
        if (x * 3 + y * 7) % every == 0:
            c = dark
        elif light is not None and (x * 5 + y * 3) % (every + 2) == 0:
            c = light
        if face == "bottom":
            c = shade(c, 0.7)
        elif face == "top":
            c = shade(c, 1.08)
        return c

    return fn


def scales(color, dark, belly=None):
    """A dragon's scales: rows of overlapping scales, each lit at its top, with the belly plates paler underneath."""

    def fn(face, x, y, w, h):
        if face == "bottom" and belly is not None:
            return belly if y % 2 else shade(belly, 0.85)
        row = y % 3
        offset = (y // 3) % 2
        c = color if row else shade(color, 1.15)
        if (x + offset) % 3 == 0:
            c = dark
        if face == "top":
            c = shade(c, 1.05)
        return c

    return fn


def face_with(base, eye, eye_rows, eyes, mouth=None, mouth_row=None, glow_eyes=False):
    """A front face with eyes at the given x positions on the given rows, and maybe a mouth row."""

    def fn(face, x, y, w, h):
        if face != "front":
            return base(face, x, y, w, h)
        if y in eye_rows and x in eyes:
            return eye
        if mouth is not None and y == mouth_row and 0 < x < w - 1:
            return mouth
        return base(face, x, y, w, h)

    return fn


def glowing(color):
    def fn(face, x, y, w, h):
        return color

    return fn


# ---------------------------------------------------------------- the ghost
#
# A ghost of the Wilderness: a hooded, robed shape, pale and grey-green, that floats a little off the ground with no
# legs under its tattered hem, reaching out with long thin arms. Its face is darkness with two cold lights for eyes.
# The game draws it see-through.

GHOST = (186, 206, 196)
GHOST_DARK = (128, 150, 142)
GHOST_DEEP = (70, 84, 80)
GHOST_EYE = (220, 255, 240)


def robe(face, x, y, w, h):
    if face == "bottom":
        return None
    c = GHOST if (x + y) % 4 else GHOST_DARK
    if y == h - 1 and x % 3 == 1:
        return None
    if y >= h - 2 and x % 2 == 0:
        return GHOST_DARK
    return c


def hood(face, x, y, w, h):
    if face == "front":
        if 1 <= x <= w - 2 and 2 <= y <= h - 1:
            return GHOST_DEEP
        return GHOST_DARK
    return plain(GHOST_DARK)(face, x, y, w, h)


def ghost():
    m = Model("ghost", 64, 64, seed=41)
    # It floats: everything hangs from the body, its hem a hand's breadth off the ground.
    body = m.part("body", pivot=(0, 0, 0))
    body.box(-4, 0, -2.5, 8, 10, 5, robe)
    body.box(-5, 9, -3.5, 10, 6, 7, robe)                        # the robe widening to its hem
    body.box(-4, 14, -3, 8, 6, 6, robe, inflate=-0.2)            # the tattered tail of it, trailing off
    head = m.part("head", pivot=(0, 0, 0))
    head.box(-4, -8, -4, 8, 8, 8, hood)
    head.box(-4, -8.5, -4, 8, 3, 9, plain(GHOST_DARK), inflate=0.3)   # the hood's peak, falling behind
    eyes = head.child("eyes", pivot=(0, 0, 0), glow=True)
    eyes.box(-3, -5, -4.2, 2, 1, 1, glowing(GHOST_EYE))
    eyes.box(1, -5, -4.2, 2, 1, 1, glowing(GHOST_EYE))
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        # Reaching forward.
        arm = m.part("%s_arm" % name, pivot=(side * 5, 2, 0), rotation=(-1.35, 0, side * -0.08))
        arm.box(-1.5, -2, -1.5, 3, 12, 3, robe, mirror=side > 0)
        arm.box(-1, 9, -1, 2, 4, 2, plain(GHOST), mirror=side > 0)       # long thin fingers
    return m


# ---------------------------------------------------------------- the hill giant
#
# A hill giant of the Wilderness, nearly twice as tall as a man: bald and heavy-browed, a broad nose and jutting jaw,
# a great belly over a hide loincloth held up by a rope belt, bare feet, and a huge wooden club in its right hand.

GIANT_SKIN = (196, 150, 112)
GIANT_DARK = (160, 116, 84)
GIANT_LIGHT = (214, 172, 136)
HIDE = (112, 82, 52)
HIDE_DARK = (82, 58, 36)
ROPE = (150, 126, 82)
CLUB = (108, 74, 42)
CLUB_DARK = (74, 50, 28)
GIANT_EYE = (40, 30, 24)


def giant_head(face, x, y, w, h):
    if face == "front":
        if y == 2 and 0 < x < w - 1:
            return GIANT_DARK                                    # the heavy brow
        if y == 3 and x in (2, w - 3):
            return GIANT_EYE
        if y == 6 and 2 <= x <= w - 3:
            return (90, 40, 34)                                  # the mouth
    if face == "top":
        return GIANT_LIGHT
    return plain(GIANT_SKIN)(face, x, y, w, h)


def belly(face, x, y, w, h):
    if face == "front" and y > h // 2:
        return GIANT_LIGHT if (x - w / 2) ** 2 + (y - h * 0.75) ** 2 < 6 else GIANT_SKIN
    return plain(GIANT_SKIN)(face, x, y, w, h)


def club(face, x, y, w, h):
    c = CLUB if (x + y * 2) % 5 else CLUB_DARK
    if face in ("top", "bottom"):
        return CLUB_DARK
    return c


def hill_giant():
    m = Model("hill_giant", 128, 64, seed=43)
    head = m.part("head", pivot=(0, 0, -1))
    head.box(-4, -8, -4, 8, 8, 8, giant_head)
    head.box(-1, -4, -5, 2, 3, 1, plain(GIANT_DARK))             # the broad nose
    head.box(-3, -1, -4.5, 6, 2, 2, plain(GIANT_SKIN))           # the jutting jaw
    for side in (-1, 1):
        head.box(side * 4 - (1 if side > 0 else 0), -5, -1, 1, 3, 2, plain(GIANT_DARK))   # ears
    body = m.part("body", pivot=(0, 0, 0))
    body.box(-5, 0, -3, 10, 7, 6, plain(GIANT_SKIN))             # the chest and shoulders
    body.box(-5, 6, -3.5, 10, 6, 7, belly)                       # the belly
    body.box(-5, 10, -3, 10, 2, 6, plain(ROPE), inflate=0.3)     # the rope belt
    body.box(-4, 11, -3.6, 8, 5, 1, mottled(HIDE, HIDE_DARK))    # the loincloth, front and back
    body.box(-4, 11, 2.6, 8, 5, 1, mottled(HIDE, HIDE_DARK))
    arms = {}
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = arms[name] = m.part("%s_arm" % name, pivot=(side * 6.5, 2, 0))
        arm.box(-2 if side > 0 else -3, -2, -2.5, 5, 13, 5, plain(GIANT_SKIN), mirror=side > 0)
        leg = m.part("%s_leg" % name, pivot=(side * 2.5, 12, 0))
        leg.box(-2.5, 0, -2.5, 5, 12, 5, plain(GIANT_SKIN), mirror=side > 0)
        leg.box(-2.5, 11, -4, 5, 1, 2, plain(GIANT_DARK))           # the toes
    # The club in its right hand, head down past the knee when it hangs.
    weapon = arms["right"].child("club", pivot=(-0.5, 10, 0), rotation=(-1.2, 0, 0))
    weapon.box(-1, -2, -1, 2, 8, 2, club)
    weapon.box(-2, 6, -2, 4, 9, 4, club)
    return m


# ---------------------------------------------------------------- the hellhound
#
# A hellhound of Zamorak: a great black hound with red-raw skin showing through, a ridge of bony spikes down its
# back, a long snout full of fangs and eyes like coals, its tail a whip.

HOUND = (44, 34, 36)
HOUND_DARK = (26, 20, 22)
HOUND_RED = (138, 34, 26)
HOUND_EYE = (255, 120, 30)
FANG = (232, 222, 196)
SPIKE = (70, 58, 54)


def hound_hide(face, x, y, w, h):
    c = HOUND if (x * 3 + y * 5) % 7 else HOUND_RED
    if (x + y) % 5 == 0:
        c = HOUND_DARK
    if face == "bottom":
        return shade(HOUND_RED, 0.8)
    return c


def hound_head(face, x, y, w, h):
    if face == "front" and y == 1 and x in (1, w - 2):
        return HOUND_EYE
    return hound_hide(face, x, y, w, h)


def snout(face, x, y, w, h):
    if face == "front" and y == h - 1:
        return FANG if x % 2 == 0 else HOUND_DARK
    if face in ("left", "right") and y == h - 1 and x % 2 == 0:
        return FANG
    if face == "front" and y == 0:
        return (20, 16, 16)                                      # the nose
    return hound_hide(face, x, y, w, h)


def quad_legs(m, length, thick, paint, foot_paint, spread_x, front_z, back_z, top_y):
    """Four legs on a quadruped's body, each a thigh and a shin, named front/back and left/right."""
    for end, z in (("front", front_z), ("back", back_z)):
        for side in (-1, 1):
            name = "%s_%s_leg" % (end, "left" if side > 0 else "right")
            leg = m.part(name, pivot=(side * spread_x, top_y, z))
            leg.box(-thick / 2, 0, -thick / 2, thick, length - 1, thick, paint, mirror=side > 0)
            leg.box(-thick / 2 - 0.5, length - 1, -thick / 2 - 1, thick + 1, 1, thick + 1, foot_paint)


def hellhound():
    m = Model("hellhound", 64, 64, seed=47)
    body = m.part("body", pivot=(0, 12, 0))
    body.box(-4, -4, -8, 8, 7, 16, hound_hide)
    body.box(-4.5, -4.5, -8, 9, 5, 7, hound_hide, inflate=0.2)   # the heavy shoulders
    for i in range(5):
        body.child("spike%d" % i, pivot=(0, -4, -6 + i * 3), rotation=(-0.5, 0, 0)).box(
            -0.5, -3, -0.5, 1, 3, 1, plain(SPIKE))
    head = m.part("head", pivot=(0, 10, -8))
    head.box(-3, -4, -5, 6, 6, 5, hound_head)
    head.child("snout", pivot=(0, -1, -5)).box(-2, -1, -5, 4, 2, 5, snout)
    head.child("jaw", pivot=(0, 1, -5), rotation=(0.1, 0, 0)).box(-1.5, 0, -4.5, 3, 1, 5, snout)
    for side in (-1, 1):
        head.child("%s_ear" % ("left" if side > 0 else "right"), pivot=(side * 2, -4, -2),
                   rotation=(-0.3, 0, side * 0.3)).box(-1, -3, -0.5, 2, 3, 1, hound_hide)
    quad_legs(m, 9, 3, hound_hide, plain(HOUND_DARK), 2.5, -5.5, 6, 15)
    link = m.part("tail0", pivot=(0, 9, 8), rotation=(0.9, 0, 0))
    for i in range(3):
        link.box(-0.5, 0, -0.5, 1, 4, 1, hound_hide)
        link = link.child("tail%d" % (i + 1), pivot=(0, 4, 0), rotation=(-0.25, 0, 0))
    return m


# ---------------------------------------------------------------- the deadly red spider
#
# A deadly red spider of the Wilderness, as big as a hound: a blood red body, its abdomen striped with black, eight
# long jointed legs, a cluster of black eyes and fangs dripping poison.

SPIDER = (168, 30, 28)
SPIDER_DARK = (96, 14, 16)
SPIDER_BLACK = (30, 18, 18)
SPIDER_EYE = (14, 10, 10)
VENOM = (140, 220, 80)


def abdomen(face, x, y, w, h):
    if face in ("top", "back") and (y % 3 == 0):
        return SPIDER_BLACK
    if face == "bottom":
        return SPIDER_DARK
    return SPIDER if (x + y) % 4 else SPIDER_DARK


def spider_head(face, x, y, w, h):
    if face == "front" and y in (1, 2) and x in (1, 2, w - 3, w - 2):
        return SPIDER_EYE
    if face == "front" and y == h - 1 and x in (2, w - 3):
        return VENOM
    return plain(SPIDER)(face, x, y, w, h)


def leg_paint(face, x, y, w, h):
    return SPIDER_DARK if y % 4 == 0 else SPIDER


def red_spider():
    m = Model("red_spider", 64, 64, seed=53)
    body = m.part("body", pivot=(0, 19, 0))
    body.box(-3.5, -3, -4, 7, 6, 7, plain(SPIDER))               # the thorax
    belly_part = m.part("abdomen", pivot=(0, 18, 3), rotation=(0.15, 0, 0))
    belly_part.box(-5, -5, 0, 10, 8, 11, abdomen)
    head = m.part("head", pivot=(0, 19, -4))
    head.box(-3, -3, -5, 6, 5, 5, spider_head)
    for side in (-1, 1):
        head.child("%s_fang" % ("left" if side > 0 else "right"), pivot=(side * 1.5, 2, -4.5),
                   rotation=(0.3, 0, 0)).box(-0.5, 0, -0.5, 1, 3, 1, plain(SPIDER_BLACK))
    # Eight legs, four each side, splayed out and bent down to the ground at the knee.
    for i in range(8):
        side = 1 if i % 2 else -1
        row = i // 2
        z = -3 + row * 2
        spread = (-0.5 + row * 0.33) * side
        leg = m.part("leg%d" % i, pivot=(side * 3.5, 18, z), rotation=(0, spread, side * -0.6))
        leg.box(0 if side > 0 else -9, -1, -1, 9, 2, 2, leg_paint, mirror=side > 0)
        shin = leg.child("leg%d_shin" % i, pivot=(side * 9, 0, 0), rotation=(0, 0, side * 1.45))
        shin.box(0 if side > 0 else -11, -0.5, -0.5, 11, 1, 1, leg_paint, mirror=side > 0)
    return m


# ---------------------------------------------------------------- the gargoyle
#
# A gargoyle: a hunched figure of grey stone, horned, with a beaked face and a heavy jaw, clawed hands and feet, a
# stub of a tail, and stone bat wings folded over its back. It is stone through and through, so arrows and blows do
# less to it.

STONE = (122, 122, 126)
STONE_DARK = (88, 88, 94)
STONE_LIGHT = (150, 150, 154)
GARGOYLE_EYE = (255, 196, 60)


def stone(face, x, y, w, h):
    return mottled(STONE, STONE_DARK, STONE_LIGHT, 6)(face, x, y, w, h)


def gargoyle_head(face, x, y, w, h):
    if face == "front" and y == 3 and x in (1, w - 2):
        return GARGOYLE_EYE
    if face == "front" and y == 6 and 1 <= x <= w - 2:
        return (40, 40, 44)
    return stone(face, x, y, w, h)


def wing(face, x, y, w, h):
    if face in ("front", "back") and x % 4 == 0:
        return STONE_DARK                                        # the ribs of the wing
    return shade(STONE, 0.9)


def gargoyle():
    m = Model("gargoyle", 128, 64, seed=59)
    body = m.part("body", pivot=(0, 2, 0), rotation=(0.35, 0, 0))
    body.box(-4.5, -2, -2.5, 9, 12, 5, stone)
    head = m.part("head", pivot=(0, 1, -2))
    head.box(-3.5, -7, -4, 7, 7, 7, gargoyle_head)
    head.box(-1, -4, -6, 2, 3, 2, stone)                         # the beak of a nose
    for side in (-1, 1):
        horn = head.child("%s_horn" % ("left" if side > 0 else "right"), pivot=(side * 2.5, -7, -1),
                          rotation=(-0.5, 0, side * 0.4))
        horn.box(-0.5, -4, -0.5, 1, 4, 1, plain(STONE_DARK))
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 5.5, 3, -1))
        arm.box(-1.5, -2, -1.5, 3, 12, 3, stone, mirror=side > 0)
        arm.box(-2, 9, -2, 4, 2, 4, plain(STONE_DARK))           # the clawed hand
        leg = m.part("%s_leg" % name, pivot=(side * 2.2, 12, 1))
        leg.box(-2, 0, -2, 4, 12, 4, stone, mirror=side > 0)
        leg.box(-2, 11, -4, 4, 1, 2, plain(STONE_DARK))          # claws
        # The wings, folded over its back, their tips down by its knees.
        w = body.child("%s_wing" % name, pivot=(side * 2.5, 0, 2.5), rotation=(0.2, side * 0.5, side * 0.15))
        w.box(0 if side > 0 else -1, -4, 0, 1, 14, 9, wing, mirror=side > 0)
    body.child("tail", pivot=(0, 9, 2.5), rotation=(0.6, 0, 0)).box(-0.5, 0, 0, 1, 1, 6, plain(STONE_DARK))
    return m


# ---------------------------------------------------------------- the chromatic dragons
#
# A chromatic dragon of the Wilderness, red, blue, green or black: a great beast on four thick legs, its long neck
# raised and its horned head low and fanged, a crest of spines from its brow to the tip of its long tail, and its
# wings folded along its sides, for it does not fly. Its belly plates are a paler shade of its scales.

# By texture: the red dragon's is the model's own, so an edit in Blockbench paints the red dragon.
DRAGONS = {
    "dragon": ((168, 40, 30), (110, 20, 18), (214, 150, 90)),
    "dragon_blue": ((48, 84, 168), (26, 48, 108), (150, 176, 206)),
    "dragon_green": ((58, 122, 50), (32, 76, 30), (176, 196, 110)),
    "dragon_black": ((40, 38, 44), (18, 16, 20), (98, 88, 84)),
}
HORN = (214, 204, 176)
CLAW = (40, 36, 34)
DRAGON_EYE = (255, 210, 60)


def dragon(kind="dragon"):
    color, dark, belly_color = DRAGONS[kind]
    hide = scales(color, dark, belly_color)
    membrane = plain(shade(color, 0.75), 0.6)
    m = Model("dragon", 128, 128, seed=61)
    body = m.part("body", pivot=(0, 9, 0))
    body.box(-6, -5, -11, 12, 11, 22, hide)
    for i in range(6):
        body.child("crest%d" % i, pivot=(0, -5, -9 + i * 4), rotation=(-0.4, 0, 0)).box(
            -0.5, -3, -0.5, 1, 3, 2, plain(HORN))
    # The neck, in three links rising forward, and the head on the end of it.
    neck = m.part("neck", pivot=(0, 7, -11), rotation=(-0.75, 0, 0))
    link = neck
    for i in range(3):
        link.box(-2.5, -2.5, -5, 5, 5, 5, hide)
        link.box(-0.5, -4, -4, 1, 2, 2, plain(HORN))
        link = link.child("neck%d" % (i + 1), pivot=(0, 0, -5), rotation=(0.15, 0, 0))
    head = link.child("head", pivot=(0, 0, -3), rotation=(0.6, 0, 0))
    head.box(-3.5, -3.5, -6, 7, 6, 7, face_with(hide, DRAGON_EYE, (1,), (1, 5)))
    head.box(-2.5, -1.5, -13, 5, 3, 7, hide)                     # the snout
    jaw = head.child("jaw", pivot=(0, 1.5, -6), rotation=(0.08, 0, 0))
    jaw.box(-2.5, 0, -7, 5, 2, 7, hide)
    for side in (-1, 1):
        head.box(side * 1.5 - 0.5, 1.5, -12.5, 1, 1, 1, plain(HORN))           # fangs
        horn = head.child("%s_horn" % ("left" if side > 0 else "right"), pivot=(side * 2.5, -3, 0),
                          rotation=(0.6, side * 0.2, 0))
        horn.box(-1, -1, 0, 2, 2, 6, plain(HORN))
    quad_legs(m, 11, 5, hide, plain(CLAW), 4.5, -7, 7, 13)
    # The tail: eight links, thinner as they go, ending in a spade of spines.
    link = m.part("tail0", pivot=(0, 7, 10), rotation=(-0.25, 0, 0))
    for i in range(8):
        size = 5 if i < 2 else 4 if i < 4 else 3 if i < 6 else 2
        link.box(-size / 2, -size / 2, 0, size, size, 5, hide)
        link = link.child("tail%d" % (i + 1), pivot=(0, 0, 4.6), rotation=(0.12 if i < 4 else 0.05, 0, 0))
    link.box(-2, -0.5, 0, 4, 1, 4, plain(HORN))
    # Wings folded along its flanks: a bony arm and a membrane hanging from it.
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        w = body.child("%s_wing" % name, pivot=(side * 6, -4, -6), rotation=(-0.15, side * -0.15, side * -0.35))
        w.box(-1, -1, 0, 2, 2, 16, plain(dark))
        w.box(0 if side > 0 else -1, 0, 1, 1, 9, 15, membrane, mirror=side > 0)
    return m


# ---------------------------------------------------------------- the bandit
#
# A Wilderness bandit: a hard-faced man with a scarf over his head and a stubbled chin, a patched brown jerkin over a
# dirty shirt, a belt hung with a pouch, dark breeches and boots, and a short sword in his right hand.

SKIN = (198, 150, 116)
SKIN_DARK = (164, 120, 92)
SCARF = (120, 34, 30)
JERKIN = (104, 74, 46)
JERKIN_DARK = (76, 52, 32)
SHIRT = (170, 160, 140)
BREECHES = (56, 52, 50)
BOOT = (46, 34, 26)
STEEL = (180, 184, 192)
STEEL_DARK = (110, 114, 122)


def bandit_head(face, x, y, w, h):
    if y < 3:
        return SCARF if (x + y) % 5 else shade(SCARF, 0.8)
    if face == "front":
        if y == 4 and x in (2, 5):
            return (40, 30, 24)
        if y >= 6:
            return SKIN_DARK if (x + y) % 2 else SKIN              # stubble
        return SKIN
    if face == "back":
        return SCARF
    return SKIN_DARK if y >= 6 else SKIN


def jerkin(face, x, y, w, h):
    if face == "front" and 2 <= x <= w - 3 and y < 5:
        return SHIRT
    if y in (h - 3, h - 2):
        return (40, 30, 22)                                      # the belt
    return JERKIN if (x * 3 + y) % 7 else JERKIN_DARK


def sleeve(face, x, y, w, h):
    if y >= h - 3:
        return SKIN
    return JERKIN if (x + y) % 6 else JERKIN_DARK


def breeches(face, x, y, w, h):
    if y >= h - 4:
        return BOOT if face != "top" else BREECHES
    return BREECHES


def blade(face, x, y, w, h):
    if face in ("front", "back") and x == 0:
        return STEEL_DARK
    return STEEL


def bandit():
    m = Model("bandit", 64, 64, seed=67)
    head = m.part("head", pivot=(0, 0, 0))
    head.box(-4, -8, -4, 8, 8, 8, bandit_head)
    head.box(-1.5, -7.5, 3.5, 3, 4, 2, plain(SCARF))             # the scarf's knot and tail
    body = m.part("body", pivot=(0, 0, 0))
    body.box(-4, 0, -2, 8, 12, 4, jerkin)
    body.box(2.5, 9, -3, 2, 2, 1, plain(JERKIN_DARK))            # the pouch
    arms = {}
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = arms[name] = m.part("%s_arm" % name, pivot=(side * 5, 2, 0))
        arm.box(-1 if side > 0 else -3, -2, -2, 4, 12, 4, sleeve, mirror=side > 0)
        leg = m.part("%s_leg" % name, pivot=(side * 1.9, 12, 0))
        leg.box(-2, 0, -2, 4, 12, 4, breeches, mirror=side > 0)
    sword = arms["right"].child("sword", pivot=(-1, 10, 0), rotation=(-1.57, 0, 0))
    sword.box(-0.5, -2, -0.5, 1, 3, 1, plain(JERKIN_DARK))       # the grip
    sword.box(-1.5, 1, -1, 3, 1, 2, plain(STEEL_DARK))           # the guard
    sword.box(-0.5, 2, -0.5, 1, 9, 1, blade)
    return m


# The models and the textures each is painted in: a name to the build and its looks.
WILD = {
    "ghost": (ghost, ["ghost"]),
    "hill_giant": (hill_giant, ["hill_giant"]),
    "hellhound": (hellhound, ["hellhound"]),
    "red_spider": (red_spider, ["red_spider"]),
    "gargoyle": (gargoyle, ["gargoyle"]),
    "dragon": (dragon, list(DRAGONS)),
    "bandit": (bandit, ["bandit"]),
}


def build(name, look):
    """The model of the name, painted as the look."""
    make, _ = WILD[name]
    return make(look) if name == "dragon" else make()
