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
# claws, the blade the same for all three; the hilt below it is the god's: Zamorak's a red grip over spread black
# claws, Saradomin's a pale grip over a gold four-pointed star, Bandos's a dark green grip over a spiked green-black
# pommel.

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
}


def claw(part, name, pivot, angle, length, paint, curl=-0.9):
    """A curved claw: a thick root and a thinner hooked tip, turned angle from straight up."""
    root = part.child(name, pivot=pivot, rotation=(0, 0, angle))
    fbox(root, -0.75, -length, -0.75, 1.5, length + 0.5, 1.5, paint)
    side = 1 if angle >= 0 else -1
    tip = root.child(name + "_tip", pivot=(0, -length + 0.3, 0), rotation=(0, 0, side * curl))
    fbox(tip, -0.5, -length * 0.7, -0.5, 1, length * 0.7 + 0.3, 1, paint, inflate=-0.05)
    return root


def godsword(god):
    grip_color, grip_dark, pommel, pommel_dark = HILTS[god]
    m = Model(god + "_godsword", 128, 128, seed=17)
    sword = m.part("sword")
    fbox(sword, -1.25, -7, -1.25, 2.5, 16, 2.5, wrapped(grip_color, grip_dark), inflate=0.1)
    pom = flat(pommel)
    pom_dark = flat(pommel_dark)
    hilt = sword.child("pommel", pivot=(0, 10, 0))
    if god == "zamorak":
        # Black claws spread out and down below a black knob.
        fbox(hilt, -1.75, -1.5, -1.75, 3.5, 3, 3.5, pom)
        for i, angle in enumerate((-2.3, -2.9, 2.9, 2.3)):
            claw(hilt, "claw%d" % i, (0, 0.5, 0), angle, 5 if i in (1, 2) else 6, pom_dark, curl=0.8)
    elif god == "saradomin":
        # A gold star of four points across the end of the grip, a short spike below it.
        fbox(hilt, -1.5, -1.5, -1.5, 3, 3, 3, pom, inflate=0.2)
        for i in range(4):
            ray = hilt.child("ray%d" % i, pivot=(0, 0, 0), rotation=(0, 0, math.pi / 4 + math.pi / 2 * i))
            fbox(ray, -1, -6, -0.75, 2, 5, 1.5, pom, inflate=-0.1)
            fbox(ray, -0.5, -8, -0.5, 1, 2, 1, pom_dark)
        fbox(hilt, -0.5, 1.5, -0.5, 1, 3, 1, pom_dark)
    else:
        # A heavy spiked knob of green-black iron.
        fbox(hilt, -2, -1.5, -2, 4, 4, 4, pom)
        for i, angle in enumerate((-1.9, -2.6, 2.6, 1.9, math.pi)):
            spike = hilt.child("spike%d" % i, pivot=(0, 0.5, 0), rotation=(0, 0, angle))
            fbox(spike, -0.75, -5, -0.75, 1.5, 3.5, 1.5, pom_dark)
            fbox(spike, -0.4, -6.5, -0.4, 0.8, 1.5, 0.8, pom_dark)
    # The guard: a heavy steel block with claws curling up from either end and hooks hanging below.
    guard = sword.child("guard", pivot=(0, -9, 0))
    guard_paint = flat(GUARD)
    fbox(guard, -4, -2, -1.75, 8, 4, 3.5, guard_paint)
    for side in (-1, 1):
        claw(guard, "claw_up_%d" % side, (side * 3.5, -1.5, 0), side * 0.7, 4, guard_paint, curl=-1.1)
        claw(guard, "claw_down_%d" % side, (side * 3.5, 1.5, 0), side * 2.4, 3, guard_paint, curl=0.9)
    # The blade: broad and long, a point at its end and saw teeth at its foot.
    blade = sword.child("blade", pivot=(0, -11, 0))
    fbox(blade, -3, -60, -0.6, 6, 60, 1.2, gs_blade)
    fbox(blade, -2, -63, -0.6, 4, 3, 1.2, gs_tip, inflate=-0.05)
    fbox(blade, -1, -65, -0.6, 2, 2, 1.2, gs_tip, inflate=-0.1)
    for side in (-1, 1):
        for i in range(3):
            tooth = blade.child("tooth_%d_%d" % (side, i), pivot=(side * 3, -3 - i * 3.5, 0),
                                rotation=(0, 0, side * 0.8))
            fbox(tooth, -0.6, -3, -0.6, 1.2, 3, 1.2, gs_tip)
    return m


# ---------------------------------------------------------------- the dark bow
#
# A recurved bow of dark red wood, its two limbs in knotted segments, pale bone spikes standing out of the knots and
# black knobs at the ends; a gold grip between them and a pale string. The limbs bow out along +x, the string runs
# straight between their tips.

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


def dark_bow():
    m = Model("dark_bow", 128, 128, seed=23)
    bow = m.part("bow")
    fbox(bow, -1.25, -3.5, -1.25, 2.5, 7, 2.5, wrapped(GRIP, shade(GRIP, 0.7)), inflate=0.1)
    tips = []
    for up in (-1, 1):
        # Walk out along the limb, placing each segment where the one before ended.
        x, y = 0.0, up * 3.0
        for i, (length, turn) in enumerate(LIMB):
            # Turned about z: a segment running along -y (up) leans toward +x for a positive turn; the lower limb
            # mirrors it.
            angle = turn * -up
            seg = bow.child("limb_%d_%d" % (up, i), pivot=(x, y, 0), rotation=(0, 0, angle))
            thick = 2.2 - i * 0.25
            fbox(seg, -thick / 2, -length if up < 0 else 0, -thick / 2, thick, length, thick, dark_wood)
            dx, dy = math.sin(angle) * length, -math.cos(angle) * length
            x, y = x + (dx if up < 0 else -dx), y + (dy if up < 0 else -dy)
            # A knot where it meets the next, a bone spike standing out of it.
            knot = bow.child("knot_%d_%d" % (up, i), pivot=(x, y, 0))
            fbox(knot, -1.2, -1.2, -1.2, 2.4, 2.4, 2.4, flat(KNOT), inflate=-0.1 * i)
            spike = knot.child("spike_%d_%d" % (up, i), pivot=(0, 0, 0),
                               rotation=(0, 0, (1.2 + 0.3 * (i % 2)) * (1 if up < 0 else -1) + (0 if up < 0 else math.pi)))
            fbox(spike, -0.5, -4.5, -0.5, 1, 3.5, 1, flat(BONE))
        tips.append((x, y))
    # The string between the tips, standing on the inside of the curve.
    (x0, y0), (x1, y1) = tips
    sx = (x0 + x1) / 2
    fbox(bow, sx - 0.25, y0, -0.25, 0.5, y1 - y0, 0.5, flat(STRING), inflate=-0.3)
    return m


# ---------------------------------------------------------------- the dragon scimitar
#
# A curved red blade, broadening toward its end and sweeping back to a point, on a dark iron hilt with a curled guard.

DRAGON = (176, 34, 30)
DRAGON_LIGHT = (214, 66, 52)
DRAGON_DARK = (110, 16, 16)
HILT_IRON = (70, 66, 70)


def dragon_metal(face, x, y, w, h):
    if face in ("front", "back"):
        if x == w - 1:
            return DRAGON_LIGHT
        if x == 0:
            return DRAGON_DARK
        return DRAGON
    return DRAGON_LIGHT if face == "right" else DRAGON_DARK


def dragon_scimitar():
    m = Model("dragon_scimitar", 64, 64, seed=29)
    sword = m.part("sword")
    fbox(sword, -1, -5, -1, 2, 11, 2, wrapped(HILT_IRON, shade(HILT_IRON, 0.6)), inflate=0.1)
    fbox(sword, -1.5, 6, -1.5, 3, 2, 3, flat(HILT_IRON), inflate=0.1)
    # The guard: a bar across, its ends curling back toward the hand.
    guard = sword.child("guard", pivot=(0, -6, 0))
    fbox(guard, -3.5, -1, -1.25, 7, 2, 2.5, flat(HILT_IRON))
    for side in (-1, 1):
        curl = guard.child("curl_%d" % side, pivot=(side * 3.2, 0, 0), rotation=(0, 0, side * 2.6))
        fbox(curl, -0.5, -3, -0.5, 1, 3, 1, flat(HILT_IRON))
    # The blade, in segments each turned a little further, so it curves; each a little broader than the last.
    part = sword.child("blade_0", pivot=(0, -7, 0), rotation=(0, 0, 0.05))
    widths = (3, 3.2, 3.5, 3.8, 4.2, 4.6, 4.8)
    for i, width in enumerate(widths):
        if i:
            part = part.child("blade_%d" % i, pivot=(0, -6.5, 0), rotation=(0, 0, 0.09))
        fbox(part, -width / 2, -7, -0.5, width, 7, 1, dragon_metal)
    # The point sweeps back from the broad end.
    tip = part.child("tip", pivot=(0, -6.5, 0), rotation=(0, 0, 0.35))
    fbox(tip, -2, -3, -0.5, 3.5, 3, 1, dragon_metal, inflate=-0.05)
    fbox(tip, -0.5, -5, -0.5, 1.5, 2, 1, dragon_metal, inflate=-0.1)
    return m


ARTIFACTS = {"zamorak_godsword": lambda: godsword("zamorak"), "saradomin_godsword": lambda: godsword("saradomin"),
             "bandos_godsword": lambda: godsword("bandos"), "dark_bow": dark_bow, "dragon_scimitar": dragon_scimitar}
