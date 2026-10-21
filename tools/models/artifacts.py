"""The artifacts found in old ruins' chests, as held models: the three godswords, the dark bow and the dragon scimitar,
drawn after Old School RuneScape's. Like Silverlight they are drawn at half a pixel a unit, run along y with their top
up (-y) and are held where y = 0. Their inventory icons are RuneScape's own pictures, not drawn from these.
"""

import math

from boxmodel import Model, mix, shade

# ---------------------------------------------------------------- shared paints


def fbox(part, x, y, z, w, h, d, paint, inflate=0.0):
    """A box of about the size given: textures want whole sizes, so each is rounded, about the same middle."""
    iw, ih, idp = (max(1, round(v)) for v in (w, h, d))
    part.box(x + (w - iw) / 2, y + (h - ih) / 2, z + (d - idp) / 2, iw, ih, idp, paint, inflate=inflate)


def flat(color, light=1.12, dark=0.62):
    """A plain colour, lighter on top and darker underneath, a faint edge down each side."""

    def fn(face, x, y, w, h):
        if face == "top":
            return shade(color, light)
        if face == "bottom":
            return shade(color, dark)
        if w > 2 and (x == 0 or x == w - 1):
            return shade(color, 0.86)
        return color

    return fn


def wrapped(color, dark):
    """A grip wound round with strips."""

    def fn(face, x, y, w, h):
        if face in ("top", "bottom"):
            return dark
        return dark if (y + x // 2) % 3 == 0 else color

    return fn


# ---------------------------------------------------------------- the godswords
#
# A great broad blade of pale steel with a ridge down its middle, saw teeth at its foot and a heavy guard of curling
# claws, the blade the same for all four; the hilt below it is the god's: Zamorak's a red grip over spread black
# claws, Saradomin's a pale grip over a gold four-pointed star, Bandos's a dark green grip over a spiked green-black
# pommel, Armadyl's a lavender grip over a grey knob and a fan of blue feathers.

STEEL = (182, 184, 192)
STEEL_LIGHT = (222, 224, 230)
STEEL_DARK = (118, 120, 130)
GUARD = (132, 132, 140)


def gs_blade(face, x, y, w, h):
    if face in ("front", "back"):
        middle = (w - 1) / 2
        if abs(x - middle) < 0.6:
            return STEEL_LIGHT
        if abs(x - middle) < 1.6:
            return STEEL_DARK
        if x == 0 or x == w - 1:
            return STEEL_LIGHT
        return STEEL
    return STEEL_LIGHT if face in ("left", "right") else STEEL


def gs_tip(face, x, y, w, h):
    return STEEL_LIGHT if face in ("left", "right") or x in (0, w - 1) else STEEL


HILTS = {
    # grip, grip's dark strips, pommel, pommel's dark
    "zamorak": ((150, 26, 24), (88, 12, 14), (62, 56, 60), (34, 30, 34)),
    "saradomin": ((224, 214, 180), (176, 164, 128), (214, 184, 90), (156, 128, 54)),
    "bandos": ((92, 104, 88), (58, 68, 56), (60, 72, 60), (34, 42, 34)),
    "armadyl": ((118, 118, 192), (80, 80, 146), (140, 142, 152), (80, 92, 168)),
}


def claw(part, name, pivot, angle, length, paint, curl=-0.9):
    """A curved claw: a thick root and a thinner hooked tip, turned angle from straight up."""
    root = part.child(name, pivot=pivot, rotation=(0, 0, angle))
    fbox(root, -0.75, -length, -0.75, 1.5, length + 0.5, 1.5, paint)
    side = 1 if angle >= 0 else -1
    tip = root.child(name + "_tip", pivot=(0, -length + 0.3, 0), rotation=(0, 0, side * curl))
    fbox(tip, -0.5, -length * 0.7, -0.5, 1, length * 0.7 + 0.3, 1, paint, inflate=-0.05)
    return root


def bandos_mark(face, x, y, w, h):
    """Bandos's sign on the end of his godsword's pommel: a spearhead pointing down, a bar across its shaft."""
    if face in ("front", "back"):
        middle = (w - 1) / 2
        head = y >= h - 3 and abs(x - middle) <= (h - 1 - y) + 0.5
        shaft = abs(x - middle) < 0.6 and y >= 1
        bar = y == 2 and 0 < x < w - 1
        if head or shaft or bar:
            return BANDOS_MARK
    return shade(HILTS["bandos"][2], 0.85)


BANDOS_MARK = (176, 150, 92)


def godsword(god):
    grip_color, grip_dark, pommel, pommel_dark = HILTS[god]
    m = Model(god + "_godsword", 128, 128, seed=17)
    sword = m.part("sword")
    fbox(sword, -1.25, -7, -1.25, 2.5, 16, 2.5, wrapped(grip_color, grip_dark), inflate=0.1)
    pom = flat(pommel)
    pom_dark = flat(pommel_dark)
    hilt = sword.child("pommel", pivot=(0, 10, 0))
    if god == "zamorak":
        # Great black claws spread out and down below a black knob.
        fbox(hilt, -1.75, -1.5, -1.75, 3.5, 3, 3.5, pom)
        for i, angle in enumerate((-2.1, -2.8, 2.8, 2.1)):
            claw(hilt, "claw%d" % i, (0, 0.5, 0), angle, 6 if i in (1, 2) else 8, pom_dark, curl=0.9)
    elif god == "saradomin":
        # A gold star of four points across the end of the grip: up, down and to either side.
        fbox(hilt, -1.5, -1.5, -1.5, 3, 3, 3, pom, inflate=0.2)
        for i in range(4):
            ray = hilt.child("ray%d" % i, pivot=(0, 0, 0), rotation=(0, 0, math.pi / 2 * i))
            fbox(ray, -1, -6, -0.75, 2, 5, 1.5, pom, inflate=-0.1)
            fbox(ray, -0.5, -8, -0.5, 1, 2, 1, pom_dark)
    elif god == "armadyl":
        # A grey knob, and out of it a great fan of blue feathers spreading down and out, longest in the middle,
        # each with a barb near its tip.
        fbox(hilt, -2.25, -2, -2.25, 4.5, 4.5, 4.5, pom, inflate=0.2)
        for i, angle in enumerate((-1.7, -2.15, -2.6, 3.14, 2.6, 2.15, 1.7)):
            length = 11 - abs(i - 3) * 1.2
            feather = hilt.child("feather%d" % i, pivot=(0, 1.5, 0), rotation=(0, 0, angle))
            fbox(feather, -1, -length, -0.6, 2, length, 1.2, pom_dark)
            fbox(feather, -0.5, -length - 2, -0.5, 1, 2, 1, pom_dark, inflate=-0.05)
            barb = feather.child("barb%d" % i, pivot=(0, -length + 3, 0), rotation=(0, 0, 0.8 if i < 3 else -0.8))
            fbox(barb, -0.5, -3, -0.5, 1, 3, 1, pom_dark)
    else:
        # A heavy spiked knob of green-black iron, Bandos's sign on its end.
        fbox(hilt, -2, -1.5, -2, 4, 4, 4, pom)
        for i, angle in enumerate((-1.9, -2.5, 2.5, 1.9)):
            spike = hilt.child("spike%d" % i, pivot=(0, 0.5, 0), rotation=(0, 0, angle))
            fbox(spike, -0.75, -5, -0.75, 1.5, 3.5, 1.5, pom_dark)
            fbox(spike, -0.4, -6.5, -0.4, 0.8, 1.5, 0.8, pom_dark)
        mark = hilt.child("mark", pivot=(0, 3, 0))
        fbox(mark, -2.5, 0, -1, 5, 6, 2, bandos_mark)
    # The guard: a heavy steel block with great claws curling up and out from either end, and hooks hanging below.
    guard = sword.child("guard", pivot=(0, -9, 0))
    guard_paint = flat(GUARD)
    fbox(guard, -4, -2, -1.75, 8, 4, 3.5, guard_paint)
    for side in (-1, 1):
        claw(guard, "claw_out_%d" % side, (side * 3.5, -1, 0), side * 1.25, 9, guard_paint, curl=-1.2)
        claw(guard, "claw_up_%d" % side, (side * 3, -2, 0), side * 0.55, 7, guard_paint, curl=-1.0)
        claw(guard, "claw_down_%d" % side, (side * 3.5, 1.5, 0), side * 2.2, 6, guard_paint, curl=1.0)
    # The blade: broad and long, a point at its end, great saw teeth up its foot and a hook out of either side.
    blade = sword.child("blade", pivot=(0, -11, 0))
    fbox(blade, -3, -60, -0.6, 6, 60, 1.2, gs_blade)
    fbox(blade, -2, -63, -0.6, 4, 3, 1.2, gs_tip, inflate=-0.05)
    fbox(blade, -1, -65, -0.6, 2, 2, 1.2, gs_tip, inflate=-0.1)
    for side in (-1, 1):
        for i in range(4):
            tooth = blade.child("tooth_%d_%d" % (side, i), pivot=(side * 3, -2 - i * 4, 0),
                                rotation=(0, 0, side * 0.75))
            fbox(tooth, -0.75, -5 + i * 0.5, -0.6, 1.5, 5 - i * 0.5, 1.2, gs_tip)
        claw(blade, "hook_%d" % side, (side * 3, -26, 0), side * 1.0, 6, flat(STEEL), curl=-1.1)
    return m


# ---------------------------------------------------------------- the dark bow
#
# A recurved bow of dark red wood, its two limbs in knotted segments, short pale bone spikes standing out of some of the knots and
# black knobs at the ends; a gold grip between them and a pale string. The limbs bow out along +x, the string runs
# straight between their tips; drawn back, in three stages as Minecraft's bow is, two arrows lie on it.

WOOD = (138, 48, 40)
WOOD_DARK = (88, 26, 24)
KNOT = (52, 20, 16)
BONE = (214, 210, 186)
GRIP = (156, 124, 52)
STRING = (176, 170, 150)

# Each segment of a limb out from the grip: its length and its turn from the grip's line.
LIMB = ((7, 0.38), (7, 0.18), (7, -0.05), (6, -0.3), (5, -0.55))


def dark_wood(face, x, y, w, h):
    if face in ("top", "bottom"):
        return WOOD_DARK
    return WOOD_DARK if x == 0 or (y + x) % 5 == 0 else WOOD


ARROW_WOOD = (150, 118, 72)
ARROW_HEAD = (96, 96, 104)
FLETCH = (226, 222, 214)

# How far back the string is drawn at each of the three stages of pulling, in units, and how much more the limbs bend.
DRAWS = (5, 9, 13)
BEND = 0.06


def segment(part, name, start, end, paint, inflate=-0.3):
    """A thin box from start to end in x, y: a stretch of string."""
    dx, dy = end[0] - start[0], end[1] - start[1]
    length = math.hypot(dx, dy)
    # Turned about z, the box's +y runs to (-sin a, cos a).
    angle = math.atan2(-dx, dy)
    child = part.child(name, pivot=(start[0], start[1], 0), rotation=(0, 0, angle))
    fbox(child, -0.5, 0, -0.5, 1, length, 1, paint, inflate=inflate)


def arrow(part, name, nock_x, y, z):
    """An arrow lying along -x from its nock on the string, past the grip: a shaft, a grey head, white fletching."""
    shaft = 32
    fbox(part, nock_x - shaft, y - 0.5, z - 0.5, shaft, 1, 1, flat(ARROW_WOOD), inflate=-0.2)
    fbox(part, nock_x - shaft - 3, y - 1, z - 0.5, 3, 2, 1, flat(ARROW_HEAD), inflate=-0.15)
    fbox(part, nock_x - 6, y - 1.5, z - 0.5, 5, 3, 1, flat(FLETCH), inflate=-0.35)


def dark_bow(stage=None):
    """The dark bow at rest, or drawn back to a stage of pulling (0 to 2) with two arrows on the string."""
    name = "dark_bow" if stage is None else "dark_bow_pulling_%d" % stage
    m = Model(name, 128, 128, seed=23)
    bow = m.part("bow")
    fbox(bow, -1.25, -3.5, -1.25, 2.5, 7, 2.5, wrapped(GRIP, shade(GRIP, 0.7)), inflate=0.1)
    bend = 0 if stage is None else BEND * (stage + 1)
    tips = []
    for up in (-1, 1):
        # Walk out along the limb, placing each segment where the one before ended.
        x, y = 0.0, up * 3.0
        for i, (length, turn) in enumerate(LIMB):
            # Turned about z: a segment running along -y (up) leans toward +x for a positive turn; the lower limb
            # mirrors it. Drawn back, each bends a little further toward the string.
            angle = (turn + bend) * -up
            seg = bow.child("limb_%d_%d" % (up, i), pivot=(x, y, 0), rotation=(0, 0, angle))
            thick = 2.2 - i * 0.25
            fbox(seg, -thick / 2, -length if up < 0 else 0, -thick / 2, thick, length, thick, dark_wood)
            dx, dy = math.sin(angle) * length, -math.cos(angle) * length
            x, y = x + (dx if up < 0 else -dx), y + (dy if up < 0 else -dy)
            # A knot where it meets the next, a bone spike standing out of it.
            knot = bow.child("knot_%d_%d" % (up, i), pivot=(x, y, 0))
            fbox(knot, -1.2, -1.2, -1.2, 2.4, 2.4, 2.4, flat(KNOT), inflate=-0.1 * i)
            # A short bone spike out of every other knot, and the last.
            if i % 2 == 1 or i == len(LIMB) - 1:
                spike = knot.child("spike_%d_%d" % (up, i), pivot=(0, 0, 0),
                                   rotation=(0, 0, 1.3 * (1 if up < 0 else -1) + (0 if up < 0 else math.pi)))
                fbox(spike, -0.5, -3, -0.5, 1, 2, 1, flat(BONE))
        tips.append((x, y))
    (x0, y0), (x1, y1) = tips
    if stage is None:
        # The string between the tips, standing on the inside of the curve.
        sx = (x0 + x1) / 2
        fbox(bow, sx - 0.25, y0, -0.25, 0.5, y1 - y0, 0.5, flat(STRING), inflate=-0.3)
        return m
    # Drawn back to a point behind the grip, two arrows nocked there one above the other, lying beside the grip.
    nock = ((x0 + x1) / 2 + DRAWS[stage], (y0 + y1) / 2)
    segment(bow, "string_top", (x0, y0), nock, flat(STRING))
    segment(bow, "string_bottom", (x1, y1), nock, flat(STRING))
    arrow(bow, "arrow_high", nock[0], nock[1] - 1.2, -1.8)
    arrow(bow, "arrow_low", nock[0], nock[1] + 1.2, -1.8)
    return m


# ---------------------------------------------------------------- the dragon scimitar
#
# A short curved red blade, broadening greatly toward its end, its back jagged, sweeping up to a point; on a dark
# iron hilt whose pommel and guard curl round the hand in an S.

DRAGON = (176, 34, 30)
DRAGON_LIGHT = (214, 66, 52)
DRAGON_DARK = (110, 16, 16)
HILT_IRON = (70, 66, 70)


def dragon_metal(face, x, y, w, h):
    if face in ("front", "back"):
        if x == 0:
            return DRAGON_LIGHT
        if x == w - 1:
            return DRAGON_DARK
        return DRAGON
    return DRAGON_LIGHT if face == "left" else DRAGON_DARK


def dragon_scimitar():
    m = Model("dragon_scimitar", 64, 64, seed=29)
    sword = m.part("sword")
    iron = flat(HILT_IRON)
    fbox(sword, -1, -5, -1, 2, 10, 2, wrapped(HILT_IRON, shade(HILT_IRON, 0.6)), inflate=0.1)
    # The pommel hooks back the way the blade curves, the guard's ends curl: an S of dark iron round the grip.
    pommel = sword.child("pommel", pivot=(0, 5, 0), rotation=(0, 0, -0.9))
    fbox(pommel, -0.75, 0, -0.75, 1.5, 4, 1.5, iron)
    fbox(pommel, -2.5, 3, -0.5, 3, 1, 1, iron)
    guard = sword.child("guard", pivot=(0, -6, 0))
    fbox(guard, -3, -1, -1.25, 6, 2, 2.5, iron)
    curl = guard.child("curl", pivot=(3, 0, 0), rotation=(0, 0, -2.3))
    fbox(curl, -0.5, -3, -0.5, 1, 3, 1, iron)
    fbox(curl, -1.5, -3.5, -0.5, 2, 1, 1, iron)
    hook = guard.child("hook", pivot=(-3, 0, 0), rotation=(0, 0, 0.4))
    fbox(hook, -0.5, -3, -0.5, 1, 3, 1, iron)
    # The blade: short, curving back toward -x, broadening greatly toward its end. Each step is set to the edge (+x),
    # so its back (-x) rises in jagged steps, as RuneScape's does; then a long point sweeps up from the broad end.
    part = sword.child("blade_0", pivot=(0, -7, 0), rotation=(0, 0, -0.1))
    widths = (3, 4, 5, 6, 7, 8)
    for i, width in enumerate(widths):
        if i:
            part = part.child("blade_%d" % i, pivot=(0, -5.5, 0), rotation=(0, 0, -0.07))
        fbox(part, 1.5 - width, -6, -0.5, width, 6, 1, dragon_metal)
    tip = part.child("tip", pivot=(1.5, -5.5, 0), rotation=(0, 0, -0.25))
    fbox(tip, -6, -5, -0.5, 6, 5, 1, dragon_metal, inflate=-0.05)
    fbox(tip, -4.5, -9, -0.5, 4, 4, 1, dragon_metal, inflate=-0.05)
    fbox(tip, -3, -12, -0.5, 2, 3, 1, dragon_metal, inflate=-0.1)
    return m


ARTIFACTS = {"zamorak_godsword": lambda: godsword("zamorak"), "saradomin_godsword": lambda: godsword("saradomin"),
             "bandos_godsword": lambda: godsword("bandos"), "armadyl_godsword": lambda: godsword("armadyl"),
             "dark_bow": dark_bow,
             "dark_bow_pulling_0": lambda: dark_bow(0), "dark_bow_pulling_1": lambda: dark_bow(1),
             "dark_bow_pulling_2": lambda: dark_bow(2), "dragon_scimitar": dragon_scimitar}
