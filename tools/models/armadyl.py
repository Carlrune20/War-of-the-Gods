"""Armadyl's models, drawn after Old School RuneScape's: the Armadyl crossbow, Armadyl armour (the helmet, and the
chestplate and chainskirt as one model), and the aviansie, the bird-people of his army, with Kree'arra and his three
lieutenants as looks of the same model.

The crossbow is drawn like the other artifacts (see artifacts.py): half a pixel a unit, its stock along y with its
front up (-y), held where y = 0. The armour's parts are placed as the player model's are, as armor.py's are. The
aviansie's parts are named as the player model's (head, body, right_arm, left_arm, right_leg, left_leg), so the game
can move them the way it moves a man, and its wings are left_wing and right_wing with a _tip each, as the greater
demon's are.
"""

from artifacts import fbox, flat
from armor import metal
from boxmodel import TEAM, Model, shade
from creatures import clear, flesh, plain

# ---------------------------------------------------------------- the Armadyl crossbow
#
# A stock of white wood, a loop of a trigger guard under it, gold fittings at its front and butt; across its front
# two limbs of gold curving back toward the shooter, their tips curling out, and a pale string between them.

STOCK = (226, 226, 222)
STOCK_DARK = (176, 176, 172)
GOLD = (222, 190, 40)
GOLD_LIGHT = (246, 222, 96)
GOLD_DARK = (164, 128, 20)
STRING = (210, 206, 190)


def stock(face, x, y, w, h):
    if face in ("top", "bottom"):
        return STOCK_DARK if face == "bottom" else STOCK
    return STOCK_DARK if x == w - 1 else STOCK


def gold(face, x, y, w, h):
    if face == "top":
        return GOLD_LIGHT
    if face == "bottom":
        return GOLD_DARK
    return GOLD_LIGHT if x == 0 else GOLD


def armadyl_crossbow():
    m = Model("armadyl_crossbow", 128, 128, seed=31)
    bow = m.part("crossbow")
    # The stock: a long white beam, thicker toward the butt, a gold cap on its end.
    fbox(bow, -1.25, -18, -1.5, 2.5, 22, 3, stock)
    fbox(bow, -1.5, 4, -2, 3, 6, 4, stock, inflate=0.1)
    fbox(bow, -1.5, 10, -2, 3, 2, 4, gold, inflate=0.15)
    # The trigger guard: a white loop under the stock behind the limbs.
    guard = bow.child("guard", pivot=(0, -1, 1.5))
    fbox(guard, -0.5, 0, 0, 1, 1, 3, flat(STOCK))
    fbox(guard, -0.5, 0, 3, 1, 5, 1, flat(STOCK))
    fbox(guard, -0.5, 5, 0, 1, 1, 4, flat(STOCK))
    # Gold fittings round the stock where the limbs cross it, and its gold nose.
    fbox(bow, -1.75, -17, -2, 3.5, 4, 4, gold, inflate=0.1)
    fbox(bow, -1, -21, -1, 2, 3, 2, gold)
    # The limbs: out to either side from the front, each in three segments bending back, its tip curling out.
    for side in (-1, 1):
        x, y = side * 1.75, -15.0
        part = bow
        for i, (length, angle) in enumerate(((5, 1.65), (5, 2.05), (4, 2.45))):
            seg = part.child("limb_%d_%d" % (side, i), pivot=(x, y, 0) if i == 0 else (0, -length_before, 0),
                             rotation=(0, 0, side * (angle if i == 0 else angle - previous)))
            fbox(seg, -0.9, -length, -0.9, 1.8, length, 1.8, gold)
            length_before, previous, part = length, angle, seg
        tip = part.child("tip_%d" % side, pivot=(0, -length_before, 0), rotation=(0, 0, side * -1.1))
        fbox(tip, -0.6, -3, -0.6, 1.2, 3, 1.2, gold)
    # The string, straight across between the limbs' ends, behind the fittings.
    fbox(bow, -13.5, -9.5, -0.3, 27, 0.6, 0.6, flat(STRING), inflate=-0.1)
    return m


# ---------------------------------------------------------------- Armadyl armour
#
# The helmet: a white steel shell round the whole head, its face guard drawn out into a beak, with dark slits for the
# eyes, a gold crest sweeping up and back from the brow and gold trim round its lower edge. The chestplate: white
# steel edged in gold, a gold diamond on the breast, gold-trimmed pauldrons and white bracers. The chainskirt: a
# white skirt to the knee, gold at its belt and hem, over grey plated legs and boots.

WHITE = (222, 222, 226)
WHITE_DARK = (172, 172, 182)
GREY = (150, 150, 160)
GREY_DARK = (110, 110, 120)
SLIT = (40, 36, 40)


def trimmed(base, top=False, bottom=True, sides=False, diamond=False):
    """White steel with gold edges: along the bottom of each side, and the top or the sides if asked; a gold diamond
    in the middle of the front."""
    plain_metal = metal(base)

    def fn(face, x, y, w, h):
        if face in ("top", "bottom"):
            return plain_metal(face, x, y, w, h)
        if diamond and face == "front":
            middle_x, middle_y = (w - 1) / 2, h * 0.42
            d = abs(x - middle_x) / 1.0 + abs(y - middle_y) / 1.3
            if d <= 2.2:
                return GOLD_LIGHT if d <= 1 else GOLD
        if (bottom and y == h - 1) or (top and y == 0) or (sides and x in (0, w - 1)):
            return GOLD
        return plain_metal(face, x, y, w, h)

    return fn


def helmet_shell(face, x, y, w, h):
    if face == "front":
        # Two slits for the eyes either side of the beak.
        if y == 4 and (1 <= x <= 3 or w - 4 <= x <= w - 2):
            return SLIT
        if y == h - 1:
            return GOLD
    if face in ("left", "right", "back") and y == h - 1:
        return GOLD
    return metal(WHITE)(face, x, y, w, h)


def armadyl_helmet():
    m = Model("armadyl_helmet", 64, 64, seed=33)
    head = m.part("head")
    head.box(-5, -9, -5, 10, 10, 10, helmet_shell)
    head.box(-4, -10, -4, 8, 1, 8, metal(WHITE))
    # The beak: a ridge down the middle of the face guard, standing out to a hooked point at the chin.
    head.box(-1, -6, -6, 2, 5, 1, metal(WHITE))
    head.box(-1.5, -6, -5.6, 3, 2, 1, metal(WHITE))
    head.box(-0.5, -1, -6.5, 1, 2, 1, metal(WHITE_DARK))
    # The crest: a gold fin rising from the brow, sweeping up and back over the crown.
    crest = head.child("crest", pivot=(0, -9, -3), rotation=(0.55, 0, 0))
    crest.box(-0.5, -6, -1, 1, 6, 3, gold)
    return m


def chainskirt(face, x, y, w, h):
    if face == "bottom":
        return None
    if face == "top":
        return GOLD
    if y <= 1:
        return GOLD if y == 0 else GOLD_DARK
    if y == h - 1:
        return GOLD
    return shade(WHITE, 0.94 if (x + y) % 2 else 1.0)


def armadyl_armor():
    m = Model("armadyl_armor", 128, 64, seed=35)
    body = m.part("body")
    body.box(-5, -1, -3, 10, 12, 6, trimmed(WHITE, top=True, diamond=True))
    skirt = m.part("skirt")
    skirt.box(-5, 10, -3.5, 10, 9, 7, chainskirt, inflate=0.6)
    for side, name in ((-1, "right"), (1, "left")):
        arm = m.part(name + "_arm", pivot=(side * 5, 2, 0))
        x = -3 if side < 0 else -1
        # Gold-edged pauldrons, and white bracers edged in gold over the forearms.
        arm.box(x - 1 if side < 0 else x, -3, -3, 6, 4, 6, trimmed(WHITE, sides=True), inflate=0.2, mirror=side > 0)
        arm.box(x, 4, -2, 4, 5, 4, trimmed(WHITE, top=True), inflate=0.5, mirror=side > 0)
        leg = m.part(name + "_leg", pivot=(side * 1.9, 12, 0))
        leg.box(-2, 3, -2, 4, 7, 4, metal(GREY), inflate=0.5, mirror=side > 0)
        leg.box(-2.5, 4, -2.5, 5, 2, 5, metal(shade(GREY, 1.1)), inflate=0.2, mirror=side > 0)
        boot = m.part(name + "_boot", pivot=(side * 1.9, 12, 0))
        boot.box(-2, 9, -3, 4, 3, 5, metal(GREY_DARK), inflate=0.5, mirror=side > 0)
    return m


# ---------------------------------------------------------------- the aviansie
#
# A bird-man as tall as a man, broad in the chest: brown feathered limbs and body, a hawk's head with a hooked yellow
# beak and a red tongue, great brown wings rising from its shoulders and falling to its knees, and taloned yellow
# feet. Over the feathers it wears Armadyl's armour: a white vest edged in gold with a gold diamond on it, a white
# skirt, grey bracers, and heavy grey plates round its thighs and shins. A belt in its team's color.
#
# Looks: the plain aviansie (a ranger, throwing spears), the warrior (its team's sword, held as a man's is), the mage
# (a staff), Flight Kilisa (melee, a helmet and a jagged sword), Wingman Skree (a mage, big pauldrons and a dark
# skirt), Flockleader Geerin (ranged, a helmet and a spear), and Kree'arra, Armadyl's general: grey-headed, his wings
# sheathed in grey steel.

FEATHER = (124, 96, 58)
FEATHER_DARK = (92, 70, 42)
FEATHER_LIGHT = (150, 120, 76)
GREY_FEATHER = (120, 118, 122)
GREY_FEATHER_DARK = (88, 86, 92)
BEAK = (232, 196, 104)
BEAK_DARK = (184, 146, 70)
TONGUE = (190, 50, 50)
TALON = (226, 190, 128)
EYE = (36, 30, 26)
EYE_GOLD = (240, 200, 60)
BLUE_HAIR = (40, 44, 104)
SKIRT_DARK = (34, 34, 46)
WOOD = (110, 86, 56)
STEEL = (170, 172, 180)
STEEL_LIGHT = (214, 216, 222)
STAFF_GEM = (90, 180, 230)

TEAL = (70, 150, 156)

# By look: feathers (light, mid, dark), eye, helmet, skirt, pauldron paint, weapon, wing steel, leg plates, and
# whether it wears Armadyl armour: a white helm, white plates and teal-edged pauldrons, as aviansie do once their
# team has researched it. The first look, aviansie, is the model's own; aviansie_ranger is the ranger's as the game
# names it, and an _elite look is a kind's in Armadyl armour.
LOOKS = {
    "aviansie": ((FEATHER_LIGHT, FEATHER, FEATHER_DARK), EYE, False, WHITE, False, "spear", False, GREY),
    "aviansie_ranger": ((FEATHER_LIGHT, FEATHER, FEATHER_DARK), EYE, False, WHITE, False, "spear", False, GREY),
    "aviansie_ranger_elite": ((FEATHER_LIGHT, FEATHER, FEATHER_DARK), EYE, True, WHITE, True, "spear", False, WHITE,
                              True),
    "aviansie_warrior_elite": ((FEATHER_LIGHT, FEATHER, FEATHER_DARK), EYE, True, WHITE, True, None, False, WHITE,
                               True),
    "aviansie_mage_elite": ((FEATHER_LIGHT, FEATHER, FEATHER_DARK), EYE, True, (96, 110, 170), True, "staff", False,
                            WHITE, True),
    "aviansie_warrior": ((FEATHER_LIGHT, FEATHER, FEATHER_DARK), EYE, False, WHITE, True, None, False, GREY),
    "aviansie_mage": ((FEATHER_LIGHT, FEATHER, FEATHER_DARK), EYE, False, (96, 110, 170), False, "staff", False, GREY),
    "kilisa": ((FEATHER_LIGHT, FEATHER, FEATHER_DARK), EYE, True, WHITE, False, "jagged", False, GREY_DARK),
    "skree": ((FEATHER_LIGHT, FEATHER, FEATHER_DARK), EYE, True, SKIRT_DARK, True, "staff", False, GREY),
    "geerin": ((FEATHER_LIGHT, FEATHER, FEATHER_DARK), EYE, True, WHITE, False, "spear", False, GREY_DARK),
    "kreearra": ((FEATHER_LIGHT, FEATHER, FEATHER_DARK), EYE_GOLD, False, GREY, True, None, True, GREY),
}
# Kree'arra's head is grey-feathered, the rest of him brown as his aviansie are.
GREY_HEAD = (shade(GREY_FEATHER, 1.15), GREY_FEATHER, GREY_FEATHER_DARK)


def feathers(colors, belly=False):
    """Feathers in rows, each row's lower edge darker, as RuneScape's flat facets show them."""
    light, mid, dark = colors

    def fn(face, x, y, w, h):
        if face == "top":
            return light
        if face == "bottom":
            return dark
        if y % 3 == 2 or (x == 0 and w > 2):
            return dark
        if belly and face == "front":
            return light
        return mid

    return fn


def flight(colors):
    """The long flight feathers: lines running down their length, their ends ragged."""
    light, mid, dark = colors

    def fn(face, x, y, w, h):
        if face == "top":
            return light
        if face == "bottom":
            return dark
        if y >= h - 3 and (x + (h - 1 - y)) % 2 == 1:
            return None
        if face in ("front", "back") and x % 2 == 1:
            return dark
        return mid

    return fn


def bird_head(colors, eye):
    light, mid, dark = colors

    def fn(face, x, y, w, h):
        if face in ("left", "right"):
            # An eye on each side, toward the front, under a dark brow.
            front = 1 if face == "left" else w - 2
            if y == 2 and abs(x - front) <= 1:
                return dark
            if y == 3 and x == front:
                return eye
        if face == "top":
            return mid
        if face == "back" and y >= h - 2:
            return dark
        return light if face == "front" else mid

    return fn


def beak(face, x, y, w, h):
    if face == "top":
        return BEAK
    if face == "bottom":
        return BEAK_DARK
    return BEAK_DARK if y == h - 1 else BEAK


def tongue(face, x, y, w, h):
    return TONGUE


def talon(face, x, y, w, h):
    if face == "bottom":
        return shade(TALON, 0.7)
    return TALON


def vest(base):
    """The armour vest: white edged in gold, a gold diamond on the breast, a team-colored belt at its foot."""
    edged = trimmed(base, top=True, bottom=False, diamond=True)

    def fn(face, x, y, w, h):
        if face == "bottom":
            return None
        if y == h - 1 and face != "top":
            return (TEAM, 220)
        if face in ("front", "back") and y < 3 and 2 <= x <= w - 3 and face == "back":
            return None
        return edged(face, x, y, w, h)

    return fn


def skirt(base):
    def fn(face, x, y, w, h):
        if face == "bottom":
            return None
        if face == "top":
            return (TEAM, 200)
        if y == 0:
            return (TEAM, 220)
        if y == h - 1:
            return GOLD
        if face == "front" and abs(x - (w - 1) / 2) < 1.5 and y < h - 1:
            return GOLD if y == 1 else shade(base, 1.06)
        return shade(base, 0.92 if (x * 3 + y) % 5 == 0 else 1.0)

    return fn


def leg_plate(base):
    def fn(face, x, y, w, h):
        if face == "bottom":
            return None
        if face == "top":
            return shade(base, 1.15)
        # Overlapping plates: each lighter at its top edge.
        return shade(base, 1.12 if y % 3 == 0 else 0.96 if y % 3 == 2 else 1.0)

    return fn


def helm(face, x, y, w, h):
    if face == "bottom":
        return None
    if face == "front":
        # Open below the eyes for the beak; slits for the eyes.
        if y >= 4 and 1 <= x <= w - 2:
            return None
        if y == 3 and (1 <= x <= 2 or w - 3 <= x <= w - 2):
            return SLIT
    if face in ("left", "right") and y >= 5 and ((face == "left" and x < 3) or (face == "right" and x > w - 4)):
        return None
    return metal(STEEL)(face, x, y, w, h)


def wing_steel(face, x, y, w, h):
    if face == "top":
        return STEEL_LIGHT
    if face == "bottom":
        return shade(STEEL, 0.6)
    return STEEL_LIGHT if y % 4 == 0 else STEEL if x else shade(STEEL, 0.8)


def color_or_clear(on, paint):
    return paint if on else clear


def armadyl_helm(face, x, y, w, h):
    """Armadyl's helm: Kree'arra's steel helm made in white, its eye slits edged in teal."""
    if face == "bottom":
        return None
    if face == "front":
        if y >= 4 and 1 <= x <= w - 2:
            return None
        if y == 3 and (1 <= x <= 2 or w - 3 <= x <= w - 2):
            return SLIT
        if y == 2 and 1 <= x <= w - 2:
            return TEAL
    if face in ("left", "right") and y >= 5 and ((face == "left" and x < 3) or (face == "right" and x > w - 4)):
        return None
    return metal(WHITE)(face, x, y, w, h)


def aviansie(kind="aviansie"):
    colors, eye, helmet, skirt_color, pauldrons, weapon, steel_wings, plates = LOOKS[kind][:8]
    armoured = len(LOOKS[kind]) > 8 and LOOKS[kind][8]
    light, mid, dark = colors
    m = Model("aviansie", 128, 128, seed=37)

    head = m.part("head", pivot=(0, 1, -1))
    head.box(-3, -7, -3.5, 6, 7, 7, bird_head(GREY_HEAD if kind == "kreearra" else colors, eye))
    head.box(-2.5, -5, 3, 5, 5, 2, color_or_clear(helmet, plain(BLUE_HAIR)))      # feathers out from under a helm
    upper = head.child("beak", pivot=(0, -4, -3.5), rotation=(0.25, 0, 0))
    upper.box(-1.5, -1.5, -5, 3, 3, 5, beak)
    upper.box(-1, 1.5, -5, 2, 2, 1, beak)                                       # its hooked point
    upper.box(-1, 1.5, -4, 2, 1, 3, tongue)                                     # the open mouth's red tongue
    head.box(-3, -7, -3.5, 6, 5, 7, color_or_clear(helmet, armadyl_helm if armoured else helm), inflate=0.4)
    crest = head.child("crest", pivot=(0, -7.4, -2.5), rotation=(-0.45, 0, 0))
    crest.box(-0.5, -4, -1, 1, 4, 6, color_or_clear(helmet, gold))

    body = m.part("body", pivot=(0, 1, 0))
    body.box(-4.5, 0, -2.5, 9, 12, 5, feathers(colors, belly=True))
    body.box(-4.5, 0, -2.5, 9, 9, 5, vest(skirt_color if kind == "kreearra" else WHITE), inflate=0.35)
    body.box(-4.5, 9, -3, 9, 5, 6, skirt(skirt_color), inflate=0.4)

    for side in (-1, 1):
        name = "left" if side > 0 else "right"
        arm = m.part("%s_arm" % name, pivot=(side * 6, 3, 0))
        arm.box(-1.5, -1.5, -1.5, 3, 12, 3, feathers(colors), mirror=side > 0)
        arm.box(-1.5, 5, -1.5, 3, 4, 3, metal(STEEL), inflate=0.35)            # the bracer
        pauldron = TEAL if armoured else WHITE if kind != "kreearra" else STEEL
        arm.box(-2, -2.5, -2, 4, 3, 4, color_or_clear(pauldrons, trimmed(pauldron, sides=True)), inflate=0.5)
        for i in range(3):                                                      # the talons of the hand
            finger = arm.child("%s_finger%d" % (name, i), pivot=(side * 0.9 * (i - 1), 10.5, -1))
            finger.box(-0.5, 0, -0.5, 1, 2, 1, talon)
        if side < 0:
            # The weapons each look carries, in the right hand: all are in the model, those a look does not carry
            # left clear on its texture. The warrior carries none, as the game puts its team's sword in its hand.
            held = arm.child("weapon", pivot=(0, 9.5, -0.5), rotation=(0.45, 0, 0))
            spear, staff, sword = (weapon == "spear"), (weapon == "staff"), (weapon == "jagged")
            held.box(-0.5, -16, -0.5, 1, 24, 1, color_or_clear(spear, flat(WOOD)))
            held.box(-1, -20, -0.5, 2, 4, 1, color_or_clear(spear, flat(STEEL_LIGHT)))
            held.box(-0.5, -14, -0.5, 1, 22, 1, color_or_clear(staff, flat(STEEL)), inflate=0.05)
            held.box(-1, -16, -1, 2, 2, 2, color_or_clear(staff, flat(STAFF_GEM)))
            held.box(-1.5, -14, -0.5, 3, 1, 1, color_or_clear(staff, gold))
            held.box(-0.5, -1, -0.5, 1, 3, 1, color_or_clear(sword, flat(SKIRT_DARK)), inflate=0.1)
            held.box(-2, -2, -1, 4, 1, 2, color_or_clear(sword, flat(STEEL)))
            held.box(-1, -14, -0.5, 2, 12, 1, color_or_clear(sword, flat(STEEL_LIGHT)))
            for i in range(3):
                tooth = held.child("tooth%d" % i, pivot=(1, -5 - i * 3, 0), rotation=(0, 0, 0.7))
                tooth.box(-0.5, -2, -0.5, 1, 2, 1, color_or_clear(sword, flat(STEEL_LIGHT)))

        leg = m.part("%s_leg" % name, pivot=(side * 2.3, 14, 0))
        leg.box(-2, 0, -2, 4, 6, 4, feathers(colors), mirror=side > 0)
        leg.box(-2.5, 0, -2.5, 5, 5, 5, leg_plate(plates), inflate=0.3)       # the thigh plates
        leg.box(-2, 5, -2, 4, 4, 4, leg_plate(plates), inflate=0.2)           # the shin plates
        leg.box(-1.5, 9, -3, 3, 1, 4, talon)                                    # the foot
        for i in range(3):                                                      # its talons
            toe = leg.child("%s_toe%d" % (name, i), pivot=(i - 1, 9.5, -3), rotation=(-0.5, (i - 1) * 0.35, 0))
            toe.box(-0.5, -0.5, -2, 1, 1, 2, talon)
        hind = leg.child("%s_hind_toe" % name, pivot=(0, 9.5, 1))
        hind.box(-0.5, -0.5, 0, 1, 1, 2, talon)

        # The wing: an arm of feathers rising up and out from the shoulder blade, and from its end the long
        # flight feathers falling to the knee. Kree'arra's are sheathed in grey steel.
        wing_paint = wing_steel if steel_wings else feathers(colors)
        wing = m.part("%s_wing" % name, pivot=(side * 2, 2, 2.5), rotation=(0.2, side * -0.3, side * -1.05))
        wing.box(0 if side > 0 else -13, -2, 0, 13, 3, 2, wing_paint, mirror=side > 0)
        wing.box(0 if side > 0 else -13, 1, 0.5, 13, 4, 1, feathers((light, mid, dark)), mirror=side > 0)
        tip = wing.child("%s_wing_tip" % name, pivot=(side * 12.5, -0.5, 1), rotation=(0, 0, side * 0.8))
        tip.box(-1 if side > 0 else -5, -2, -1, 6, 24, 2, wing_steel if steel_wings else flight(colors),
                mirror=side > 0)
        tip.box(0 if side > 0 else -4, 0, -0.5, 4, 19, 1, flight((light, mid, dark)), mirror=side > 0)
    return m


# By model: how to build it, and the kinds that wear it.
CREATURES = {"aviansie": (aviansie, list(LOOKS))}
MODELS = {"armadyl_crossbow": armadyl_crossbow, "armadyl_helmet": armadyl_helmet, "armadyl_armor": armadyl_armor}
