"""Forinthry's wild creatures as box models, drawn after the Wilderness's own creatures as Old School RuneScape
shows them: the ghost, the hill giant, the hellhound, the deadly red spider, the gargoyle, the chromatic dragons
(one model, green, red, blue and black) and the bandit. They belong to no team, so none has a team mask.

Their textures are painted at RES texels to the model's unit, four times as fine as a Minecraft mob's, in soft
shaded colour as those models are: each face lighter toward the top, broken up by noise, with the muscles, scales,
folds and markings drawn in. A face's paint can leave a texel clear (None), which cuts the box's outline: tattered
robes and hems, bat wings scalloped between their fingers, the open front of a hood.

Their parts are named for EntityWildMob's renderer (com.warofthegods.client.ModelWild) to move them: a head, a body,
arms and legs as the player model's are named for the ghost, the giant, the gargoyle and the bandit; front and back
legs, a jaw, a neck and a tail in links for the hound and the dragons; eight legs for the spider; and wings for the
gargoyle and the dragons. The dragons do not fly: their wings are raised and folded over their backs.
"""

import math

from boxmodel import Model, clamp

RES = 1
FACES = {"top": 1, "bottom": 2, "right": 3, "front": 4, "left": 5, "back": 6}
SIDES = ("right", "front", "left", "back")

# ---------------------------------------------------------------- painting


def _hash(ix, iy, seed):
    h = (ix * 374761393 + iy * 668265263 + seed * 982451653) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def noise(x, y, seed=0):
    """Smooth value noise, 0 to 1."""
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = _hash(ix, iy, seed), _hash(ix + 1, iy, seed)
    c, d = _hash(ix, iy + 1, seed), _hash(ix + 1, iy + 1, seed)
    return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy


def fbm(x, y, seed=0, octaves=3):
    total, amp, norm = 0.0, 0.5, 0.0
    for i in range(octaves):
        total += amp * noise(x, y, seed + i * 17)
        norm += amp
        x, y, amp = x * 2.03, y * 2.03, amp * 0.5
    return total / norm


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(p + (q - p) * t for p, q in zip(a, b))


def shade(color, factor):
    return tuple(clamp(c * factor) for c in color)


def uv(x, y, w, h):
    """A texel's place on its face, 0 to 1 across and down."""
    return (x + 0.5) / w, (y + 0.5) / h


def lit(face, y, h, color, gradient=0.24, top=1.1, bottom=0.66):
    """Soft light from above: a face lighter at its top than its bottom, a top face lit, a bottom face in shadow."""
    if face == "top":
        return shade(color, top)
    if face == "bottom":
        return shade(color, bottom)
    t = (y + 0.5) / h
    return shade(color, 1.0 + gradient * 0.5 - gradient * t)


def surface(base, dark, light, grain=2.0, seed=0, gradient=0.24, rim=0.9, contrast=1.0):
    """A material: the base colour mottled toward dark and light by noise grain units across, softly lit."""

    def fn(face, x, y, w, h):
        g = grain * RES
        n = fbm(x / g + FACES[face] * 31.7, y / g + FACES[face] * 7.3, seed)
        n = 0.5 + (n - 0.5) * contrast
        color = mix(dark, base, n * 2) if n < 0.5 else mix(base, light, (n - 0.5) * 2)
        color = lit(face, y, h, color, gradient)
        if rim and w > 3 and h > 3 and (x == 0 or y == 0 or x == w - 1 or y == h - 1):
            color = shade(color, rim)
        return color

    return fn


def over(base, *layers):
    """The base paint with layers on it: each gives a colour for a texel, CUT to leave it clear, or None."""

    def fn(face, x, y, w, h):
        for layer in layers:
            c = layer(face, x, y, w, h)
            if c is CUT:
                return None
            if c is not None:
                return c
        return base(face, x, y, w, h)

    return fn


CUT = object()


def on(faces, test, color):
    """A layer: color where test(u, v) holds on the given faces. color may be a paint to take the colour from."""

    def fn(face, x, y, w, h):
        if face not in faces:
            return None
        u, v = uv(x, y, w, h)
        if not test(u, v):
            return None
        return color(face, x, y, w, h) if callable(color) else color

    return fn


def ellipse(cx, cy, rx, ry):
    return lambda u, v: ((u - cx) / rx) ** 2 + ((v - cy) / ry) ** 2 <= 1


def ragged(faces, start, depth, seed, every=0.9):
    """A layer cutting a hem: tatters hanging below start (0 to 1 down the face), as deep as depth."""

    def fn(face, x, y, w, h):
        if face not in faces:
            return None
        u, v = uv(x, y, w, h)
        if v < start:
            return None
        n = noise(x / (every * RES) + FACES[face] * 11, 0.5, seed)
        return CUT if (v - start) / depth > n else None

    return fn


def darken(faces, test, factor):
    """A layer darkening (or lightening) what is under it, so it keeps its mottle."""

    def make(base):
        def fn(face, x, y, w, h):
            c = base(face, x, y, w, h)
            if c is None or face not in faces:
                return c
            u, v = uv(x, y, w, h)
            return shade(c, factor(u, v) if callable(factor) else factor) if test(u, v) else c

        return fn

    return make


def glow(color):
    return lambda face, x, y, w, h: color


def polygon(points):
    """Whether a point is inside a polygon."""

    def inside(px, py):
        hit = False
        j = len(points) - 1
        for i in range(len(points)):
            xi, yi = points[i]
            xj, yj = points[j]
            if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                hit = not hit
            j = i
        return hit

    return inside


def near_segment(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / float(dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


# ---------------------------------------------------------------- the ghost
#
# A ghost: the shape of a man, pale white all over with a little grey in the folds of its clothes and the shadows
# of its face, and two white eyes. The game draws it half see-through, its eyes glowing.

GHOST = (232, 234, 242)
GHOST_SHADOW = (186, 190, 210)
GHOST_LIGHT = (252, 252, 255)
GHOST_SOCKET = (140, 144, 170)
GHOST_EYE = (255, 255, 255)


def ghost():
    m = Model("ghost", 64, 64, seed=41, res=RES)
    pale = surface(GHOST, GHOST_SHADOW, GHOST_LIGHT, grain=3.0, seed=3, gradient=0.3, contrast=0.6)
    head = m.part("head", pivot=(0, 0, 0))
    face = over(pale, on(("front",), lambda u, v: 0.4 < v < 0.6 and (0.1 < u < 0.45 or 0.55 < u < 0.9),
                         GHOST_SOCKET),
                on(("front",), lambda u, v: 0.75 < v < 0.85 and 0.3 < u < 0.7, GHOST_SHADOW))
    head.box(-4, -8, -4, 8, 8, 8, face)
    eyes = head.child("eyes", pivot=(0, 0, 0), glow=True)
    eyes.box(-3, -4, -4.1, 2, 1, 1, glow(GHOST_EYE), inflate=-0.05)
    eyes.box(1, -4, -4.1, 2, 1, 1, glow(GHOST_EYE), inflate=-0.05)
    body = m.part("body", pivot=(0, 0, 0))
    body.box(-4, 0, -2, 8, 12, 4, over(pale, on(("front",), lambda u, v: abs(u - 0.5) < 0.07,
                                                lambda f, x, y, w, h: shade(pale(f, x, y, w, h), 0.86))))
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 5, 2, 0), rotation=(-0.25, 0, side * -0.08))
        arm.box(-1 if side > 0 else -3, -2, -2, 4, 12, 4, pale, mirror=side > 0)
        leg = m.part("%s_leg" % name, pivot=(side * 1.9, 12, 0))
        # Its legs fade away toward the feet.
        leg.box(-2, 0, -2, 4, 12, 4, over(pale, on(SIDES, lambda u, v: v > 0.75 and (u * 4 + v * 7) % 2 < 1, CUT),
                                          on(("bottom",), lambda u, v: True, CUT)), mirror=side > 0)
    return m


# ---------------------------------------------------------------- the hill giant
#
# A hill giant: a towering, heavily muscled man, tan and bald, a small head with a heavy brow, a broad nose and a
# beard, a great chest and shoulders, a ragged olive loincloth on a leather belt, bare feet, and a big butcher's
# cleaver of a hatchet in its right hand.

GIANT_SKIN = (226, 186, 132)
GIANT_SHADOW = (172, 126, 82)
GIANT_LIGHT = (244, 212, 164)
GIANT_LINE = (150, 104, 66)
BEARD = (154, 108, 64)
BEARD_DARK = (112, 76, 42)
LOIN = (104, 110, 58)
LOIN_DARK = (70, 74, 36)
LEATHER = (96, 66, 38)
WOOD = (118, 82, 46)
WOOD_DARK = (78, 52, 28)
STEEL = (176, 178, 186)
STEEL_DARK = (108, 110, 120)
STEEL_LIGHT = (222, 224, 230)


def giant_skin(seed):
    return surface(GIANT_SKIN, GIANT_SHADOW, GIANT_LIGHT, grain=3.0, seed=seed, gradient=0.3, contrast=0.7)


def muscle(cx, cy, rx, ry, depth=0.86):
    """A muscle: an ellipse lit softly on its upper half and shaded toward its lower edge."""

    def factor(u, v):
        d = math.sqrt(((u - cx) / rx) ** 2 + ((v - cy) / ry) ** 2)
        if v < cy:
            return 1.0 + 0.07 * (1 - d)
        return 1.0 - (1 - depth) * d * min(1.0, (v - cy) / ry * 1.5)

    return ellipse(cx, cy, rx, ry), factor


def shaded(base, faces, *shapes):
    paint = base
    for test, factor in shapes:
        paint = darken(faces, test, factor)(paint)
    return paint


def hill_giant():
    m = Model("hill_giant", 128, 64, seed=43, res=RES)
    skin = giant_skin(3)
    # The head: small for its body, bald, heavy-browed, with a broad nose and a short beard.
    head = m.part("head", pivot=(0, -4, -1))
    face = over(skin,
                on(("front",), lambda u, v: 0.38 < v < 0.46 and 0.12 < u < 0.88, GIANT_LINE),      # the brow
                on(("front",), lambda u, v: 0.46 < v < 0.54 and (0.2 < u < 0.38 or 0.62 < u < 0.8), (60, 40, 30)),
                on(("front",), lambda u, v: v > 0.72 and 0.3 < u < 0.7 and v < 0.78, (120, 70, 56)))  # mouth
    head.box(-3.5, -7, -3.5, 7, 7, 7, face)
    head.box(-3.5, -4.6, -4.1, 7, 1, 1, skin, inflate=0.2)                   # the brow ridge
    head.box(-1, -4, -4.6, 2, 3, 1, skin, inflate=0.15)                      # the nose
    beard = surface(BEARD, BEARD_DARK, (180, 132, 84), grain=0.6, seed=12)
    head.box(-3.5, -2.2, -3.9, 7, 3, 4, over(beard, on(("front",), lambda u, v: v < 0.25 and 0.3 < u < 0.7,
                                                         CUT)), inflate=0.1)
    head.box(-2, 0.5, -4, 4, 2, 2, beard)                                    # the beard's point
    for side in (-1, 1):
        head.box(side * 3.5 - (0 if side > 0 else 1), -4.5, -1, 1, 2, 2, skin)    # ears
    # The body: a broad chest and shoulders, a muscled belly, the belt and loincloth.
    body = m.part("body", pivot=(0, 0, 0))
    chest = shaded(skin, ("front",), muscle(0.28, 0.42, 0.24, 0.32), muscle(0.72, 0.42, 0.24, 0.32))
    chest = darken(("front",), lambda u, v: abs(u - 0.5) < 0.02 and v > 0.2, 0.85)(chest)
    body.box(-6.5, -3, -3.5, 13, 7, 7, chest)
    body.box(-4, -5, -2.5, 8, 2, 5, skin, inflate=0.3)                       # the neck and shoulders' swell
    belly = shaded(skin, ("front",), muscle(0.35, 0.2, 0.16, 0.14, 0.88), muscle(0.65, 0.2, 0.16, 0.14, 0.88),
                   muscle(0.35, 0.48, 0.16, 0.14, 0.88), muscle(0.65, 0.48, 0.16, 0.14, 0.88))
    belly = darken(("front",), lambda u, v: abs(u - 0.5) < 0.025 and v < 0.7, 0.86)(belly)
    body.box(-5, 4, -3, 10, 7, 6, belly)
    belt = surface(LEATHER, (66, 44, 24), (124, 90, 56), grain=0.8, seed=14)
    body.box(-5, 9, -3, 10, 2, 6, belt, inflate=0.35)
    loin = surface(LOIN, LOIN_DARK, (132, 138, 78), grain=1.4, seed=15, gradient=0.35)
    body.box(-3.5, 10.5, -3.6, 7, 7, 1, over(loin, ragged(SIDES, 0.7, 0.3, 16, 0.7)), inflate=-0.1)
    body.box(-4.5, 10.5, 2.6, 9, 6, 1, over(loin, ragged(SIDES, 0.65, 0.35, 18, 0.7)), inflate=-0.1)
    body.box(-5, 10.5, -3, 10, 3, 6, over(loin, ragged(SIDES, 0.4, 0.6, 19, 0.6)), inflate=0.25)
    hands = {}
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 8.5, -1.5, 0), rotation=(0, 0, side * -0.08))
        arm.box(-2.5, -2.5, -2.5, 5, 5, 5, shaded(skin, SIDES, muscle(0.5, 0.45, 0.48, 0.45)), inflate=0.3)
        arm.box(-2, 1, -2, 4, 7, 4, shaded(skin, SIDES, muscle(0.5, 0.35, 0.45, 0.4)), inflate=0.3)
        fore = arm.child("%s_forearm" % name, pivot=(0, 8, 0), rotation=(-0.22, 0, 0))
        fore.box(-2, 0, -2, 4, 7, 4, shaded(skin, SIDES, muscle(0.5, 0.25, 0.45, 0.3)), inflate=0.15)
        hand = hands[name] = fore.child("%s_hand" % name, pivot=(0, 7, 0))
        hand.box(-2, 0, -2, 4, 4, 4, over(skin, on(("front", "back"), lambda u, v: v > 0.55 and int(u * 4) % 2 == 0,
                                                     lambda f, x, y, w, h: shade(skin(f, x, y, w, h), 0.86))),
                 inflate=0.15)
        leg = m.part("%s_leg" % name, pivot=(side * 3, 11, 0))
        leg.box(-2.5, 0, -2.5, 5, 7, 5, shaded(skin, ("front",), muscle(0.5, 0.4, 0.45, 0.45)), inflate=0.2)
        shin = leg.child("%s_shin" % name, pivot=(0, 7, 0))
        shin.box(-2, 0, -2, 4, 4, 4, shaded(skin, ("back",), muscle(0.5, 0.3, 0.45, 0.35)), inflate=0.3)
        foot = over(skin, on(("top",), lambda u, v: v < 0.3 and int(u * 5 + 0.5) % 1 == 0 and (u * 5) % 1 < 0.15,
                             GIANT_LINE),
                    on(("front",), lambda u, v: (u * 5) % 1 < 0.14, GIANT_LINE))
        shin.box(-2.5, 4, -4.5, 5, 2, 7, foot)
    # The cleaver in its right hand, held out before it.
    cleaver = hands["right"].child("cleaver", pivot=(0, 2, 0), rotation=(-0.25, 0, 0))
    handle = surface(WOOD, WOOD_DARK, (150, 108, 64), grain=0.5, seed=21)
    cleaver.box(-0.5, -3, -0.5, 1, 11, 1, handle, inflate=0.15)
    blade = over(surface(STEEL, STEEL_DARK, STEEL_LIGHT, grain=1.0, seed=22, gradient=0.15),
                 on(("left",), lambda u, v: u < 0.12, STEEL_LIGHT),
                 on(("right",), lambda u, v: u > 0.88, STEEL_LIGHT),
                 on(("left",), lambda u, v: u > 0.9, STEEL_DARK),
                 on(("right",), lambda u, v: u < 0.1, STEEL_DARK),
                 on(("left", "right"), lambda u, v: 0.3 < u < 0.5 and 0.3 < v < 0.5, (110, 70, 60)))
    cleaver.box(-0.5, 3, -6.4, 1, 6, 6, blade, inflate=-0.1)
    return m


# ---------------------------------------------------------------- the hellhound
#
# A hellhound of Zamorak: a great hound of dark red hide, deep in the chest and narrow at the waist, on long legs,
# with pale bone spikes growing from its back, its elbows and hocks and the backs of its paws, two horns sweeping
# back from its brow, a red eye burning, and a jaw full of fangs.

HOUND = (126, 36, 26)
HOUND_DARK = (78, 18, 14)
HOUND_LIGHT = (160, 58, 40)
BONE = (236, 226, 192)
BONE_DARK = (186, 172, 136)
HOUND_EYE = (255, 60, 40)


def bone(seed):
    return surface(BONE, BONE_DARK, (250, 244, 222), grain=0.6, seed=seed, gradient=0.3)


def spike(parent, name, pivot, rotation, length=3, base=1):
    """A bone spike: a thick base and a thin point."""
    s = parent.child(name, pivot=pivot, rotation=rotation)
    s.box(-base / 2.0, -length / 2.0, -base / 2.0, base, length // 2 + 1, base, bone(31), inflate=0.15)
    s.child(name + "_tip", pivot=(0, -length / 2.0, 0)).box(-0.5, -(length - length // 2), -0.5, 1,
                                                             length - length // 2, 1, bone(32), inflate=-0.15)
    return s


def hellhound():
    m = Model("hellhound", 128, 64, seed=47, res=RES)
    hide = surface(HOUND, HOUND_DARK, HOUND_LIGHT, grain=2.4, seed=3, gradient=0.34, contrast=0.9)
    body = m.part("body", pivot=(0, 9, 0))
    chest = shaded(hide, ("left", "right"), muscle(0.3, 0.45, 0.28, 0.35))
    body.box(-4.5, -4, -9, 9, 9, 8, chest)
    body.box(-3.5, -3, -1.5, 7, 6, 8, hide)
    body.box(-4, -3.5, 5.5, 8, 7, 5, shaded(hide, ("left", "right"), muscle(0.6, 0.5, 0.35, 0.4)))
    for i in range(5):
        spike(body, "spike%d" % i, (0, -4 + (0.5 if i > 1 else 0), -6 + i * 3), (-0.7, 0, 0), 4 - (i > 2), 2 - (i > 2))
    head = m.part("head", pivot=(0, 6, -8))
    head.box(-3, -3, -4, 6, 6, 5, hide)                                       # the neck
    skull = over(hide, on(("left", "right"), ellipse(0.28, 0.35, 0.1, 0.08), HOUND_EYE))
    head.box(-3.5, -4, -8, 7, 6, 5, skull)
    snout = over(hide, on(("front",), lambda u, v: v < 0.4 and 0.25 < u < 0.75, (24, 14, 14)),
                 on(("left", "right", "front"), lambda u, v: v > 0.85 and (u * 6) % 1 < 0.45, BONE))
    head.child("snout", pivot=(0, -1, -8)).box(-2, -1, -5, 4, 3, 5, snout)
    jaw = head.child("jaw", pivot=(0, 2, -8), rotation=(0.15, 0, 0))
    jaw.box(-1.5, 0, -4.5, 3, 1, 5, over(hide, on(("top", "left", "right", "front"),
                                                  lambda u, v: v < 0.3 and (u * 6) % 1 < 0.5, BONE)))
    eyes = head.child("eyes", pivot=(0, 0, 0), glow=True)
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        eyes.box(side * 3.3 - 0.5, -2.6, -7.3, 1, 1, 1, glow(HOUND_EYE), inflate=-0.2)
        ear = head.child("%s_ear" % name, pivot=(side * 2.2, -4, -5), rotation=(-0.3, 0, side * 0.35))
        ear.box(-1, -3, -0.5, 2, 3, 1, hide)
        horn = head.child("%s_horn" % name, pivot=(side * 2.5, -3.5, -6.5), rotation=(-1.0, 0, side * 0.3))
        horn.box(-0.5, -3, -0.5, 1, 3, 1, bone(33), inflate=0.2)
        horn.child("%s_horn_tip" % name, pivot=(0, -3, 0), rotation=(-0.6, 0, 0)).box(
            -0.5, -3, -0.5, 1, 3, 1, bone(34), inflate=-0.1)
    paw = surface(HOUND_DARK, (50, 12, 10), HOUND, grain=0.6, seed=5)
    for end, z, top in (("front", -6, 11), ("back", 7, 10)):
        for side in (-1, 1):
            name = "%s_%s_leg" % (end, "left" if side > 0 else "right")
            leg = m.part(name, pivot=(side * 3, top, z))
            if end == "front":
                leg.box(-1.5, -1, -1.5, 3, 7, 3, hide, inflate=0.2)
                lower = leg.child(name + "_lower", pivot=(0, 6, 0))
                lower.box(-1, 0, -1, 2, 6, 2, hide, inflate=0.2)
                spike(lower, name + "_spike", (0, 0.5, 1.2), (-1.9, 0, 0), 3)
                lower.box(-1.5, 6, -2.5, 3, 1, 4, paw)
                spike(lower, name + "_dewclaw", (0, 6.5, 1.5), (-2.0, 0, 0), 2)
            else:
                leg.box(-2, -1, -2, 4, 7, 4, shaded(hide, ("left", "right"), muscle(0.5, 0.4, 0.45, 0.45)))
                shin = leg.child(name + "_shin", pivot=(0, 6, 0.5), rotation=(0.55, 0, 0))
                shin.box(-1, 0, -1, 2, 5, 2, hide, inflate=0.25)
                spike(shin, name + "_spike", (0, 4.5, 1.2), (-2.0, 0, 0), 3)
                foot = shin.child(name + "_foot", pivot=(0, 5, 0), rotation=(-0.55, 0, 0))
                foot.box(-1, 0, -1, 2, 3, 2, hide, inflate=0.15)
                foot.box(-1.5, 3, -2.5, 3, 1, 4, paw)
                spike(foot, name + "_dewclaw", (0, 3.5, 1.5), (-2.0, 0, 0), 2)
    link = m.part("tail0", pivot=(0, 6.5, 10), rotation=(0.8, 0, 0))
    for i in range(3):
        link.box(-0.5, -0.5, 0, 1, 1, 4, hide, inflate=0.25 - i * 0.1)
        link = link.child("tail%d" % (i + 1), pivot=(0, 0, 3.8), rotation=(-0.3, 0, 0))
    spike(link, "tail_spike", (0, 0, 1), (1.57, 0, 0), 3)
    return m


# ---------------------------------------------------------------- the deadly red spider
#
# A deadly red spider: crimson, with salmon lights and dark navy markings at its joints, round its eyes and on the
# great pointed abdomen it carries raised behind it, a red diamond in a field of navy; fangs before a black maw, and
# eight long legs, each jointed twice and ending in a sharp point.

SPIDER = (176, 30, 52)
SPIDER_DARK = (112, 16, 34)
SPIDER_LIGHT = (226, 98, 86)
NAVY = (36, 36, 66)
NAVY_LIGHT = (62, 62, 104)
SPIDER_EYE = (20, 20, 30)
MAW = (16, 10, 12)


def crimson(seed, grain=1.2):
    return surface(SPIDER, SPIDER_DARK, SPIDER_LIGHT, grain=grain * 1.5, seed=seed, gradient=0.4, contrast=1.1)


def banded(paint, ends=(0.0,), width=0.12, along="v"):
    """Navy bands round a limb at the given places along it (0 to 1)."""

    def fn(face, x, y, w, h):
        u, v = uv(x, y, w, h)
        t = v if along == "v" else u
        if face in SIDES and any(abs(t - e) < width for e in ends):
            return shade(NAVY, 1.15 - 0.3 * v)
        if face in ("top", "bottom") and along == "v" and 0.0 in ends:
            return paint(face, x, y, w, h)
        return paint(face, x, y, w, h)

    return fn


def red_spider():
    m = Model("red_spider", 128, 64, seed=53, res=RES)
    body = m.part("body", pivot=(0, 16, 0))
    thorax = over(crimson(3), on(("top",), lambda u, v: abs(u - 0.5) + abs(v - 0.5) * 0.8 < 0.32, NAVY),
                  on(("top",), lambda u, v: abs(u - 0.5) + abs(v - 0.5) * 0.8 < 0.18, SPIDER_LIGHT))
    body.box(-3.5, -2.5, -4, 7, 5, 8, thorax)
    abdomen_part = m.part("abdomen", pivot=(0, 14.5, 3), rotation=(1.0, 0, 0))
    # Its back faces forward when raised: a red diamond in navy, outlined, a navy stripe down each side.
    mark = over(crimson(5, 1.6),
                on(("top",), lambda u, v: abs(u - 0.5) * 1.4 + abs(v - 0.5) < 0.12, SPIDER_LIGHT),
                on(("top",), lambda u, v: abs(u - 0.5) * 1.4 + abs(v - 0.5) < 0.3, SPIDER),
                on(("top",), lambda u, v: abs(u - 0.5) * 1.1 + abs(v - 0.5) < 0.5, NAVY),
                on(("left", "right"), lambda u, v: 0.2 < v < 0.45 and 0.15 < u < 0.85, NAVY),
                on(("bottom",), lambda u, v: True, shade(SPIDER_DARK, 0.9)))
    abdomen_part.box(-3.5, -3.5, 0, 7, 7, 7, mark, inflate=0.3)
    upper = abdomen_part.child("abdomen_upper", pivot=(0, 0, 7))
    upper.box(-3, -3, 0, 6, 6, 3, over(crimson(6), on(("top",), lambda u, v: abs(u - 0.5) < 0.25, NAVY)))
    tip = upper.child("abdomen_tip", pivot=(0, 0, 3))
    tip.box(-2, -2, 0, 4, 4, 2, crimson(7))
    tip.box(-1, -1, 2, 2, 2, 2, over(crimson(8), on(("back",), lambda u, v: True, NAVY)))
    head = m.part("head", pivot=(0, 16, -4))
    face = over(crimson(9),
                on(("front",), lambda u, v: v < 0.4 and (abs(u - 0.3) < 0.1 or abs(u - 0.7) < 0.1), SPIDER_EYE),
                on(("front",), lambda u, v: v < 0.46 and abs(u - 0.5) < 0.38, NAVY),
                on(("front",), lambda u, v: v > 0.62 and abs(u - 0.5) < 0.28 and v < 0.7, (236, 230, 220)),
                on(("front",), lambda u, v: v > 0.6 and abs(u - 0.5) < 0.3, MAW),
                on(("top",), lambda u, v: v < 0.3 and (abs(u - 0.3) < 0.12 or abs(u - 0.7) < 0.12), SPIDER_EYE))
    head.box(-2.5, -2, -4, 5, 4, 4, face)
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        fang = head.child("%s_fang" % name, pivot=(side * 1.3, 1.5, -3.6), rotation=(0.25, 0, side * -0.1))
        fang.box(-0.5, 0, -0.5, 1, 3, 1, banded(crimson(11), (0.0,), 0.3), inflate=0.2)
        fang.child("%s_fang_tip" % name, pivot=(0, 3, 0), rotation=(-0.5, 0, 0)).box(
            -0.5, 0, -0.5, 1, 2, 1, crimson(12), inflate=-0.15)
        palp = head.child("%s_palp" % name, pivot=(side * 2.2, 1, -3.2), rotation=(0.6, side * 0.3, 0))
        palp.box(-0.5, 0, -0.5, 1, 4, 1, banded(crimson(13), (0.95,), 0.1), inflate=-0.05)
    # Eight legs, four each side, front ones reaching forward and back ones back: up to a high knee, down to the
    # next joint, and on down to a point.
    for i in range(8):
        side = 1 if i % 2 else -1
        row = i // 2
        z = -2.8 + row * 1.9
        spread = (-0.75 + row * 0.5) * side
        leg = m.part("leg%d" % i, pivot=(side * 3.2, 15.3, z), rotation=(0, spread, side * -0.8))
        reach = 9 if row in (0, 3) else 8
        leg.box(0 if side > 0 else -reach, -1, -1, reach, 2, 2,
                banded(crimson(20 + i), (0.0, 1.0), 0.08, along="u"), inflate=-0.05)
        shin = leg.child("leg%d_shin" % i, pivot=(side * reach, 0, 0), rotation=(0, 0, side * 1.9))
        shin.box(0 if side > 0 else -9, -1, -1, 9, 2, 2, banded(crimson(30 + i), (0.0, 1.0), 0.07, along="u"),
                 inflate=-0.25)
        point = shin.child("leg%d_point" % i, pivot=(side * 9, 0, 0), rotation=(0, 0, side * 0.25))
        point.box(0 if side > 0 else -4, -0.5, -0.5, 4, 1, 1, crimson(40 + i, 0.6), inflate=-0.05)
        point.child("leg%d_tip" % i, pivot=(side * 4, 0, 0)).box(0 if side > 0 else -3, -0.5, -0.5, 3, 1, 1,
                                                                   surface(SPIDER_LIGHT, SPIDER, (240, 140, 120),
                                                                           seed=50 + i), inflate=-0.3)
    return m


# ---------------------------------------------------------------- the gargoyle
#
# A gargoyle: a hunched creature of grey stone, horned, with pointed ears and red eyes, a paler chest, long clawed
# arms, legs that bend back like a beast's on taloned feet, a thin tail, and great bat wings spread wide.

STONE = (150, 152, 164)
STONE_DARK = (100, 102, 116)
STONE_LIGHT = (190, 192, 202)
STONE_PALE = (198, 186, 182)
TALON = (226, 226, 232)
GARGOYLE_EYE = (230, 40, 40)


def stone(seed, grain=1.6):
    return surface(STONE, STONE_DARK, STONE_LIGHT, grain=grain, seed=seed, gradient=0.3, contrast=1.2)


def bat_wing(span, height, seed, membrane, bone_color):
    """A bat's wing seen flat, in the wing's own units from the shoulder (0, 0) outward along x and down along y:
    the outline scalloped between its finger tips, the finger bones drawn over the membrane."""
    wrist = (span * 0.42, -1.5)
    tips = [(span, -2.5), (span * 0.92, height * 0.45), (span * 0.66, height * 0.85), (span * 0.36, height)]
    outline = [(0, -1), wrist, tips[0]]
    for a, b in zip(tips, tips[1:]):
        outline.append(((a[0] + b[0]) / 2 - span * 0.07, (a[1] + b[1]) / 2 - height * 0.08))
        outline.append(b)
    outline.append((span * 0.12, height * 0.6))
    outline.append((0, height * 0.55))
    inside = polygon(outline)
    skin = surface(membrane, shade(membrane, 0.7), shade(membrane, 1.15), grain=2.0, seed=seed, gradient=0.1)

    def at(face, x, y, w, h):
        u, v = uv(x, y, w, h)
        return u * span, -2.5 + v * (height + 2.5)

    def fn(face, x, y, w, h):
        if face not in ("front", "back"):
            return None
        px, py = at(face, x, y, w, h)
        if not inside(px, py):
            return None
        for tx, ty in tips:
            if near_segment(px, py, wrist[0], wrist[1], tx, ty) < 0.35:
                return shade(bone_color, 0.95 + 0.1 * (1 - py / height))
        if near_segment(px, py, 0, -0.5, wrist[0], wrist[1]) < 0.6:
            return bone_color
        return skin(face, x, y, w, h)

    return fn


def gargoyle():
    m = Model("gargoyle", 128, 64, seed=59, res=RES)
    hide = stone(3)
    body = m.part("body", pivot=(0, 2, 0), rotation=(0.3, 0, 0))
    chest = over(hide, on(("front",), lambda u, v: abs(u - 0.5) < 0.36 and v > 0.15,
                          surface(STONE_PALE, shade(STONE_PALE, 0.82), (222, 212, 208), seed=4, gradient=0.2)))
    body.box(-4, -2, -2.5, 8, 7, 5, chest)
    body.box(-3.5, 5, -2, 7, 5, 4, over(hide, on(("front",), lambda u, v: abs(u - 0.5) < 0.3,
                                                 surface(STONE_PALE, shade(STONE_PALE, 0.82), (222, 212, 208),
                                                         seed=5, gradient=0.2))))
    head = m.part("head", pivot=(0, 0, -1.5))
    skull = over(stone(6, 1.0), on(("front",), lambda u, v: 0.42 < v < 0.56 and 0.15 < u < 0.85, STONE_DARK))
    head.box(-3, -6, -3, 6, 6, 6, skull)
    head.box(-2, -3, -5, 4, 3, 2, over(stone(7, 0.8), on(("front",), lambda u, v: v > 0.7 and 0.2 < u < 0.8,
                                                           (40, 40, 48))))
    eyes = head.child("eyes", pivot=(0, 0, 0), glow=True)
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        eyes.box(side * 1.6 - 0.5, -4, -3.3, 1, 1, 1, glow(GARGOYLE_EYE), inflate=-0.15)
        ear = head.child("%s_ear" % name, pivot=(side * 3, -4.5, -0.5), rotation=(0, 0, side * -1.1))
        ear.box(-0.5, -3, -1, 1, 3, 2, stone(8), inflate=-0.1)
        for j, (dx, tilt, length) in enumerate(((1.6, 0.3, 5), (2.6, 0.75, 3))):
            horn = head.child("%s_horn%d" % (name, j), pivot=(side * dx, -6, -0.5 + j), rotation=(-0.5, 0, side * tilt))
            horn.box(-0.5, -length / 2.0, -0.5, 1, length // 2 + 1, 1, stone(9 + j, 0.8), inflate=0.2)
            horn.child("%s_horn%d_tip" % (name, j), pivot=(0, -length / 2.0, 0), rotation=(-0.4, 0, 0)).box(
                -0.5, -(length - length // 2), -0.5, 1, length - length // 2, 1, stone(11 + j, 0.8), inflate=-0.1)
    talon = surface(TALON, (180, 180, 190), (246, 246, 250), grain=0.4, seed=13)
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 5, 1, -0.5), rotation=(-0.25, 0, side * -0.1))
        arm.box(-1.5, -1, -1.5, 3, 7, 3, stone(14))
        fore = arm.child("%s_forearm" % name, pivot=(0, 6, 0), rotation=(-0.45, 0, 0))
        fore.box(-1, 0, -1, 2, 6, 2, stone(15), inflate=0.2)
        hand = fore.child("%s_hand" % name, pivot=(0, 6, 0))
        hand.box(-1.5, 0, -1.5, 3, 2, 3, stone(16))
        for i, dx in enumerate((-1, 0, 1)):
            hand.child("%s_claw%d" % (name, i), pivot=(dx, 2, -1), rotation=(-0.4, 0, dx * 0.2)).box(
                -0.5, 0, -0.5, 1, 3, 1, talon, inflate=-0.2)
        leg = m.part("%s_leg" % name, pivot=(side * 2.5, 11.5, 0.5), rotation=(-0.55, 0, side * 0.05))
        leg.box(-2, 0, -2, 4, 6, 4, stone(17))
        shin = leg.child("%s_shin" % name, pivot=(0, 6, 0), rotation=(1.15, 0, 0))
        shin.box(-1, 0, -1, 2, 6, 2, stone(18), inflate=0.3)
        foot = shin.child("%s_foot" % name, pivot=(0, 6, 0), rotation=(-0.6, 0, 0))
        foot.box(-1.5, 0, -1.5, 3, 2, 3, stone(19))
        for i, dx in enumerate((-1, 0, 1)):
            foot.child("%s_talon%d" % (name, i), pivot=(dx, 1.5, -1.3), rotation=(1.2, 0, 0)).box(
                -0.5, 0, -0.5, 1, 3, 1, talon, inflate=-0.2)
        # The wings, spread wide and a little back from its shoulders.
        span, height = 22, 15
        wing = body.child("%s_wing" % name, pivot=(side * 2.5, -1, 2.3), rotation=(0, side * -0.35, side * -0.32))
        wing.box(0 if side > 0 else -span, -2.5, -0.5, span, height + 2, 1,
                 bat_wing(span, height, 23 + side, (126, 128, 142), STONE_LIGHT), inflate=-0.3, mirror=side < 0)
        wing.box(0 if side > 0 else -span * 0.42, -1.5, -0.5, int(span * 0.42), 1, 1, stone(25), inflate=0.15)
    tail = body.child("tail", pivot=(0, 9.5, 1.5), rotation=(0.9, 0, 0))
    tail.box(-0.5, -0.5, 0, 1, 1, 5, stone(26), inflate=0.15)
    tail.child("tail_tip", pivot=(0, 0, 5), rotation=(-0.5, 0, 0)).box(-0.5, -0.5, 0, 1, 1, 4, stone(27))
    return m


# ---------------------------------------------------------------- the chromatic dragons
#
# A chromatic dragon of the Wilderness, green, red, blue or black: a great beast crouched low on four clawed legs,
# deep in the chest, its neck reaching forward to a long low head with two white horns sweeping back and its eyes
# red; its tail curling up high behind it; and its wings raised and folded, their long finger bones fanned back
# over its back, for it does not fly. Its claws, horns and the spurs at its elbows are white, its belly paler.

# By texture: the model's own is the red dragon's, so an edit in Blockbench paints the red dragon.
DRAGONS = {
    "dragon": dict(base=(146, 38, 28), dark=(94, 20, 16), light=(186, 70, 50), belly=(176, 82, 58),
                   wing=(120, 40, 40)),
    "dragon_green": dict(base=(120, 128, 38), dark=(78, 84, 22), light=(158, 164, 62), belly=(164, 160, 84),
                         wing=(98, 104, 32)),
    "dragon_blue": dict(base=(40, 50, 146), dark=(22, 26, 96), light=(72, 86, 190), belly=(96, 104, 172),
                        wing=(40, 44, 116)),
    "dragon_black": dict(base=(44, 44, 40), dark=(22, 22, 20), light=(74, 74, 66), belly=(74, 70, 62),
                         wing=(36, 34, 34)),
}
IVORY = (234, 230, 216)
IVORY_DARK = (182, 176, 158)
DRAGON_EYE = (226, 34, 30)


def scales(colors, seed):
    """Scales in staggered rows, each lit at its top and edged darker below, the belly in pale plates."""
    hide = surface(colors["base"], colors["dark"], colors["light"], grain=3.0, seed=seed, gradient=0.32,
                   contrast=0.9)
    belly = surface(colors["belly"], shade(colors["belly"], 0.78), shade(colors["belly"], 1.12), grain=2.0,
                    seed=seed + 1, gradient=0.1)

    def fn(face, x, y, w, h):
        if face == "bottom":
            c = belly(face, x, y, w, h)
            return shade(c, 0.86) if (y / (1.5 * RES)) % 1 < 0.15 else c
        c = hide(face, x, y, w, h)
        # Each scale a little lighter or darker than the next, lit at its top.
        sy = y / (2.0 * RES)
        row = int(sy)
        sx = x / (2.0 * RES) + 0.5 * (row % 2)
        fy = sy % 1
        tone = 0.95 + 0.1 * _hash(int(sx), row, FACES[face] + seed)
        return shade(c, tone * (1.04 - 0.08 * fy))

    return fn


def ivory(seed):
    return surface(IVORY, IVORY_DARK, (250, 248, 240), grain=0.6, seed=seed, gradient=0.35)


def claws(parent, name, at_y, front_z, seed):
    for i, dx in enumerate((-1.3, 0, 1.3)):
        claw = parent.child("%s_claw%d" % (name, i), pivot=(dx, at_y, front_z), rotation=(0.5, 0, dx * 0.12))
        claw.box(-0.5, -0.5, -2.5, 1, 1, 3, ivory(seed + i), inflate=-0.15)


def folded_wing(colors, side, seed):
    """A dragon's wing raised and folded, in the wing's own plane (x thin), y up the arm and z back along the
    body: the arm up to the elbow, the forearm back to the wrist, and three long fingers fanned back from it with
    the membrane between them, scalloped at its edge."""
    elbow = (0.0, -10.0)
    wrist = (11.0, -14.5)
    tips = [(25.0, -12.5), (23.0, -6.0), (16.5, -0.5)]
    outline = [(0, 0), elbow, wrist, tips[0], (19.5, -8.5), tips[1], (16.5, -3.8), tips[2], (8.0, -1.5)]
    inside = polygon(outline)
    membrane = surface(colors["wing"], shade(colors["wing"], 0.72), shade(colors["wing"], 1.18), grain=2.2,
                       seed=seed, gradient=0.12)
    z0, z1, y0, y1 = 0.0, 26.0, -16.0, 1.0

    def fn(face, x, y, w, h):
        if face not in ("left", "right"):
            return None
        u, v = uv(x, y, w, h)
        z = z1 - u * (z1 - z0) if face == "right" else z0 + u * (z1 - z0)
        py = y0 + v * (y1 - y0)
        if not inside(z, py):
            return None
        c = membrane(face, x, y, w, h)
        for tz, ty in tips:
            if near_segment(z, py, wrist[0], wrist[1], tz, ty) < 0.45:
                return shade(colors["dark"], 1.1)
        # Ribs of shadow between the fingers.
        return shade(c, 0.92 + 0.08 * math.sin(math.atan2(py - wrist[1], z - wrist[0]) * 9))

    return fn, elbow, wrist, tips


def dragon(kind="dragon"):
    colors = DRAGONS[kind]
    hide = scales(colors, 3)
    m = Model("dragon", 256, 128, seed=61, res=RES)
    body = m.part("body", pivot=(0, 10, 0), rotation=(-0.06, 0, 0))
    body.box(-6, -6, -12, 12, 11, 11, shaded(hide, ("left", "right"), muscle(0.3, 0.45, 0.3, 0.4, 0.84)))
    body.box(-5.5, -5, -1.5, 11, 10, 11, shaded(hide, ("left", "right"), muscle(0.7, 0.45, 0.32, 0.42, 0.84)))
    body.box(-4.5, -4.5, 9, 9, 8, 3, hide)
    # The neck, rising a little from the shoulders and reaching forward and down to the head.
    neck = m.part("neck", pivot=(0, 6, -11), rotation=(-0.15, 0, 0))
    neck.box(-3.5, -3.5, -5, 7, 7, 5, hide)
    link = neck.child("neck1", pivot=(0, 0, -4.5), rotation=(0.3, 0, 0))
    link.box(-3, -3, -5, 6, 6, 5, hide)
    link = link.child("neck2", pivot=(0, 0, -4.5), rotation=(0.25, 0, 0))
    link.box(-2.5, -2.5, -4, 5, 5, 4, hide)
    head = link.child("head", pivot=(0, 0, -3.5), rotation=(0.15, 0, 0))
    skull = over(hide, on(("left", "right"), ellipse(0.22, 0.38, 0.12, 0.1), DRAGON_EYE))
    head.box(-3.5, -3, -5, 7, 6, 6, skull)
    snout = over(hide, on(("front",), lambda u, v: v < 0.45 and (abs(u - 0.25) < 0.1 or abs(u - 0.75) < 0.1),
                           shade(colors["dark"], 0.6)),
                 on(("left", "right"), lambda u, v: v > 0.8 and (u * 5) % 1 < 0.4, IVORY))
    head.box(-2.5, -2, -12, 5, 3, 7, snout)
    head.box(-3, -3.4, -6, 6, 1, 3, hide)                                    # the brow
    jaw = head.child("jaw", pivot=(0, 1, -4.5), rotation=(0.1, 0, 0))
    jaw.box(-2, 0, -7.5, 4, 2, 8, over(hide, on(("left", "right", "front"), lambda u, v: v < 0.3 and (u * 6) % 1 < 0.5,
                                                 IVORY)))
    eyes = head.child("eyes", pivot=(0, 0, 0), glow=True)
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        eyes.box(side * 3.4 - 0.5, -1.4, -4.2, 1, 1, 1, glow(DRAGON_EYE), inflate=-0.2)
        head.child("%s_fang" % name, pivot=(side * 1.6, 1, -11), rotation=(0.1, 0, 0)).box(
            -0.5, 0, -0.5, 1, 2, 1, ivory(40), inflate=-0.15)
        horn = head.child("%s_horn" % name, pivot=(side * 2.4, -2.5, -0.5), rotation=(-0.75, 0, side * 0.22))
        horn.box(-1, -4, -1, 2, 4, 2, ivory(41), inflate=0.15)
        mid = horn.child("%s_horn_mid" % name, pivot=(0, -4, 0), rotation=(-0.35, 0, 0))
        mid.box(-1, -4, -1, 2, 4, 2, ivory(42), inflate=-0.2)
        mid.child("%s_horn_tip" % name, pivot=(0, -4, 0), rotation=(-0.3, 0, 0)).box(
            -0.5, -3, -0.5, 1, 3, 1, ivory(43), inflate=-0.05)
        spur = head.child("%s_cheek" % name, pivot=(side * 3.2, 1.5, -1), rotation=(-0.3, side * 0.5, 0))
        spur.box(-0.5, -0.5, 0, 1, 1, 3, ivory(44), inflate=-0.1)
    claw_hide = scales(colors, 7)
    for end, z, top in (("front", -7.5, 9), ("back", 5.5, 8)):
        for side in (-1, 1):
            name = "%s_%s_leg" % (end, "left" if side > 0 else "right")
            leg = m.part(name, pivot=(side * 5.5, top, z))
            if end == "front":
                leg.box(-2, -1, -2.5, 4, 8, 5, shaded(claw_hide, ("left", "right"), muscle(0.5, 0.35, 0.45, 0.4)),
                        inflate=0.3)
                fore = leg.child(name + "_fore", pivot=(0, 7, 0))
                fore.box(-1.5, 0, -1.5, 3, 6, 3, claw_hide, inflate=0.3)
                spur = fore.child(name + "_spur", pivot=(0, 0.5, 1.5), rotation=(-1.9, 0, 0))
                spur.box(-0.5, -2.5, -0.5, 1, 3, 1, ivory(50), inflate=-0.05)
                fore.box(-2, 6, -4, 4, 2, 5, claw_hide)
                claws(fore, name, 7, -3.6, 51)
            else:
                leg.box(-2.5, -1, -3, 5, 8, 6, shaded(claw_hide, ("left", "right"), muscle(0.5, 0.4, 0.45, 0.45)),
                        inflate=0.3)
                shin = leg.child(name + "_shin", pivot=(0, 7, 1.5), rotation=(0.7, 0, 0))
                shin.box(-1.5, 0, -1.5, 3, 5, 3, claw_hide, inflate=0.2)
                spur = shin.child(name + "_spur", pivot=(0, 4.5, 1.3), rotation=(-2.2, 0, 0))
                spur.box(-0.5, -2.5, -0.5, 1, 3, 1, ivory(55), inflate=-0.05)
                foot = shin.child(name + "_foot", pivot=(0, 5, 0), rotation=(-0.7, 0, 0))
                foot.box(-1, 0, -1, 2, 4, 2, claw_hide, inflate=0.3)
                foot.box(-2, 4, -4, 4, 1, 5, claw_hide, inflate=0.1)
                claws(foot, name, 4.5, -3.6, 56)
    # The tail: nine links, thinner as they go, dipping and then curling up high, the tip a flattened point.
    turns = (-0.3, 0.05, 0.2, 0.3, 0.35, 0.3, 0.15, -0.1, -0.3)
    sizes = (6, 5, 5, 4, 4, 3, 3, 2, 2)
    link = m.part("tail0", pivot=(0, 7, 11), rotation=(turns[0], 0, 0))
    for i in range(9):
        s = sizes[i]
        link.box(-s / 2.0, -s / 2.0, 0, s, s, 4, hide, inflate=0.2)
        if i < 8:
            link = link.child("tail%d" % (i + 1), pivot=(0, 0, 3.4), rotation=(turns[i + 1], 0, 0))
    fin = over(hide, on(("top", "bottom"), lambda u, v: abs(u - 0.5) > 0.5 - 0.5 * v, CUT))
    link.box(-2.5, -0.5, 3.5, 5, 1, 5, fin)
    # The wings, raised from the shoulders and folded back over the body.
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        paint, elbow, wrist, tips = folded_wing(colors, side, 70 + side)
        wing = body.child("%s_wing" % name, pivot=(side * 4.5, -5.5, -6), rotation=(0.45, side * 0.12, side * -0.28))
        wing.box(-0.5, -16, 0, 1, 17, 26, paint, inflate=-0.35)
        bone_paint = surface(colors["dark"], shade(colors["dark"], 0.7), colors["base"], grain=0.8, seed=80)
        wing.box(-1, -10, -1, 2, 10, 2, bone_paint, inflate=0.1)               # the arm, up to the elbow
        fore = wing.child("%s_wing_fore" % name, pivot=(0, elbow[1], 0),
                          rotation=(math.atan2(-(wrist[1] - elbow[1]), wrist[0] - elbow[0]) - math.pi / 2, 0, 0))
        length = int(round(math.hypot(wrist[0] - elbow[0], wrist[1] - elbow[1])))
        fore.box(-1, -length, -1, 2, length, 2, bone_paint, inflate=-0.1)
        fore.child("%s_wing_claw" % name, pivot=(0, -length, 0), rotation=(0.8, 0, 0)).box(
            -0.5, -3, -0.5, 1, 3, 1, ivory(81), inflate=-0.1)
        for i, (tz, ty) in enumerate(tips):
            finger = wing.child("%s_wing_finger%d" % (name, i), pivot=(0, wrist[1], wrist[0]),
                                rotation=(-math.atan2(ty - wrist[1], tz - wrist[0]), 0, 0))
            finger.box(-0.5, -0.5, 0, 1, 1, int(round(math.hypot(tz - wrist[0], ty - wrist[1]))), bone_paint,
                       inflate=0.05)
    return m


# ---------------------------------------------------------------- the bandit
#
# A Wilderness bandit: a man with short brown hair and a beard, a black mask over his eyes, a blue shirt with a
# brown collar, a red belt, grey bracers on his wrists, olive trousers, tall brown boots, a red cape down his back
# and a dagger in his right hand.

SKIN = (214, 166, 124)
SKIN_SHADOW = (170, 120, 84)
SKIN_LIGHT = (232, 190, 150)
HAIR = (104, 68, 36)
HAIR_DARK = (70, 44, 22)
MASK = (24, 22, 26)
SHIRT = (86, 84, 168)
SHIRT_DARK = (56, 54, 120)
SHIRT_LIGHT = (116, 114, 196)
COLLAR = (140, 92, 50)
BELT = (168, 30, 30)
BRACER = (150, 150, 156)
TROUSERS = (82, 92, 50)
TROUSERS_DARK = (56, 64, 32)
BOOT = (128, 84, 46)
BOOT_DARK = (88, 56, 28)
CAPE = (176, 30, 26)
CAPE_DARK = (110, 16, 14)


def bandit():
    m = Model("bandit", 128, 64, seed=67, res=RES)
    skin = surface(SKIN, SKIN_SHADOW, SKIN_LIGHT, grain=1.4, seed=3, gradient=0.25, contrast=1.2)
    hair = surface(HAIR, HAIR_DARK, (136, 94, 54), grain=0.5, seed=4, gradient=0.2)
    head = m.part("head", pivot=(0, 0, 0))
    face = over(skin,
                on(("front",), lambda u, v: 0.38 < v < 0.56 and (abs(u - 0.3) < 0.08 or abs(u - 0.7) < 0.08) and
                   0.44 < v < 0.52, (236, 232, 226)),
                on(("front",), lambda u, v: 0.36 < v < 0.58, MASK),
                on(("left", "right"), lambda u, v: 0.36 < v < 0.5, MASK),
                on(("back",), lambda u, v: 0.36 < v < 0.5 and abs(u - 0.5) < 0.12, MASK),
                on(("front",), lambda u, v: v > 0.66 and abs(u - 0.5) < 0.18 and v < 0.72, (140, 80, 70)),
                on(("front",), lambda u, v: v > 0.62 and (v > 0.74 or abs(u - 0.5) > 0.2 or v < 0.66), hair),
                on(("left", "right"), lambda u, v: v > 0.6 and u < 0.45, hair),
                on(("front",), lambda u, v: v < 0.22, hair),
                on(("top", "back"), lambda u, v: True, hair),
                on(("left", "right"), lambda u, v: v < 0.36, hair))
    head.box(-4, -8, -4, 8, 8, 8, face)
    knot = head.child("mask_knot", pivot=(0, -4.6, 4))
    knot.box(-0.5, -0.5, 0, 1, 1, 1, surface(MASK, MASK, (48, 46, 52), seed=6), inflate=0.1)
    knot.child("mask_tails", pivot=(0, 0, 0.6), rotation=(0.3, 0, 0)).box(-1, 0, 0, 2, 3, 1,
                                                                        surface(MASK, MASK, (48, 46, 52), seed=6),
                                                                        inflate=-0.3)
    body = m.part("body", pivot=(0, 0, 0))
    shirt = surface(SHIRT, SHIRT_DARK, SHIRT_LIGHT, grain=1.2, seed=7, gradient=0.25)
    shirt_paint = over(shirt, on(SIDES, lambda u, v: 10 / 12.0 < v < 11.5 / 12.0,
                                 surface(BELT, shade(BELT, 0.7), shade(BELT, 1.2), seed=8)),
                       on(("front",), lambda u, v: v > 10 / 12.0 and abs(u - 0.5) < 0.1 and v < 11.5 / 12.0,
                          (200, 180, 90)))
    body.box(-4, 0, -2, 8, 12, 4, shirt_paint)
    collar = surface(COLLAR, shade(COLLAR, 0.72), shade(COLLAR, 1.2), grain=0.8, seed=9)
    body.box(-4.5, -0.6, -2.5, 9, 2, 5, collar, inflate=0.1)
    body.box(-2, 1, -2.7, 4, 3, 1, over(collar, on(("front",), lambda u, v: abs(u - 0.5) > 0.5 - v * 0.5, CUT)),
             inflate=-0.1)
    body.box(-4, 10, -2, 8, 2, 4, surface(BELT, shade(BELT, 0.7), shade(BELT, 1.2), seed=10), inflate=0.3)
    cape_paint = over(surface(CAPE, CAPE_DARK, (206, 52, 44), grain=1.6, seed=11, gradient=0.35),
                      ragged(SIDES, 0.92, 0.08, 12, 0.6))
    cape = body.child("cape", pivot=(0, 0.5, 2.3), rotation=(0.1, 0, 0))
    cape.box(-4.5, 0, 0, 9, 20, 1, darken(("back", "front"), lambda u, v: True,
                                          lambda u, v: 0.88 + 0.12 * math.sin(u * math.pi * 5))(cape_paint),
             inflate=-0.1)
    cape.box(-5, -0.5, -0.5, 10, 2, 2, surface(CAPE, CAPE_DARK, (206, 52, 44), seed=13), inflate=-0.1)
    bracer = surface(BRACER, shade(BRACER, 0.7), shade(BRACER, 1.2), grain=0.6, seed=14)
    trousers = surface(TROUSERS, TROUSERS_DARK, (106, 118, 70), grain=1.2, seed=15, gradient=0.3)
    boot = surface(BOOT, BOOT_DARK, (160, 112, 66), grain=0.8, seed=16, gradient=0.3)
    arms = {}
    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = arms[name] = m.part("%s_arm" % name, pivot=(side * 5, 2, 0))
        x0 = -1 if side > 0 else -3
        arm.box(x0, -2, -2, 4, 12, 4, over(skin, on(SIDES + ("top",), lambda u, v: v < 5 / 12.0, shirt)),
                mirror=side > 0)
        arm.box(x0, 5.5, -2, 4, 3, 4, bracer, inflate=0.3)
        leg = m.part("%s_leg" % name, pivot=(side * 1.9, 12, 0))
        leg.box(-2, 0, -2, 4, 12, 4, over(trousers, on(SIDES, lambda u, v: v > 0.55, boot)), mirror=side > 0)
        leg.box(-2, 6.5, -2, 4, 1, 4, boot, inflate=0.45)                     # the boot's turned-down top
        leg.box(-2, 11, -2.6, 4, 1, 1, boot, inflate=0.1)                      # the toe
    dagger = arms["right"].child("dagger", pivot=(-1, 9.5, 0), rotation=(-1.45, 0, 0))
    dagger.box(-0.5, -1.5, -0.5, 1, 3, 1, surface(WOOD, WOOD_DARK, (150, 108, 64), seed=17), inflate=-0.05)
    dagger.box(-1, 1.5, -0.5, 2, 1, 1, surface(STEEL_DARK, (80, 80, 90), STEEL, seed=18), inflate=-0.1)
    dagger.box(-0.5, 2.5, -0.5, 1, 5, 1, over(surface(STEEL, STEEL_DARK, STEEL_LIGHT, seed=19),
                                              on(SIDES, lambda u, v: u > 0.7, STEEL_LIGHT)), inflate=-0.15)
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
