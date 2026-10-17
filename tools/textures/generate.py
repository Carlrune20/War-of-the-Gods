"""Draws the unit skins and the building and unit icons of War of the Gods.

    python3 tools/textures/generate.py [--preview DIR] [--minecraft-jar JAR]

Like the structures, the textures are drawn from code so they stay consistent and are easy to change. Skins use the
64x32 player layout. Saradomin's people wear bright steel, white and blue; Zamorak's wear blackened iron, bone and
red. A unit's team shows as a band of its team's color drawn over the skin from team_overlay.png. The resource and
tool icons are Minecraft's own, taken from the client jar.

Writes into src/main/resources/assets/warofthegods/textures/entity/unit/ and
src/main/resources/assets/warofthegods/textures/gui/icons.png, which the web map also serves.
"""

import argparse
import io
import os
import random
import zipfile

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "warofthegods")
SKINS = os.path.join(ROOT, "textures", "entity", "unit")
ICONS = os.path.join(ROOT, "textures", "gui", "icons.png")

# ---------------------------------------------------------------- skins

# The parts of the player model in the skin: u, v and the box's width, height and depth.
HEAD = (0, 0, 8, 8, 8)
HAT = (32, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
ARM = (40, 16, 4, 12, 4)
LEG = (0, 16, 4, 12, 4)

SKIN = (196, 146, 108)
SKIN_DARK = (168, 120, 86)


def faces(part):
    """Each face of a box as (name, u, v, width, height)."""
    u, v, w, h, d = part
    return [
        ("top", u + d, v, w, d),
        ("bottom", u + d + w, v, w, d),
        ("right", u, v + d, d, h),
        ("front", u + d, v + d, w, h),
        ("left", u + d + w, v + d, d, h),
        ("back", u + d + w + d, v + d, w, h),
    ]


def paint(image, part, fn, rng, noise=8):
    """Fills each face of a part pixel by pixel with fn(face, x, y, width, height), a color or None."""
    for face, fu, fv, fw, fh in faces(part):
        for y in range(fh):
            for x in range(fw):
                color = fn(face, x, y, fw, fh)
                if color is None:
                    continue
                n = rng.randint(-noise, noise)
                shade = 0.88 if face in ("left", "right") else 0.8 if face == "bottom" else 1.0
                image.putpixel((fu + x, fv + y), tuple(max(0, min(255, int(c * shade) + n)) for c in color) + (255,))


def face_fn(hair, eyes=(60, 80, 150), mouth=(140, 80, 70), beard=None):
    def fn(face, x, y, w, h):
        if face == "top":
            return hair
        if face == "bottom":
            return SKIN_DARK
        if face == "back":
            return hair if y < 6 else SKIN_DARK
        if face in ("left", "right"):
            if y < 3 or (face == "right" and x < 3 and y < 5) or (face == "left" and x > 4 and y < 5):
                return hair
            return SKIN
        # front
        if y < 2:
            return hair
        if y == 4 and x in (1, 2, 5, 6):
            return (245, 245, 245) if x in (1, 6) else eyes
        if beard and y >= 6:
            return beard
        if y == 6 and x in (3, 4):
            return mouth
        if y == 5 and x in (3, 4):
            return SKIN_DARK
        return SKIN
    return fn


def worker(faction):
    def tunic_color():
        return (196, 176, 136) if faction == "saradomin" else (88, 74, 64)
    tunic = tunic_color()
    apron = (122, 88, 56) if faction == "saradomin" else (52, 40, 34)
    trim = (52, 84, 168) if faction == "saradomin" else (150, 26, 24)
    trousers = (110, 84, 58) if faction == "saradomin" else (40, 36, 36)
    boots = (70, 48, 30) if faction == "saradomin" else (26, 22, 22)

    def body(face, x, y, w, h):
        if face == "top":
            return tunic
        if face == "bottom":
            return trousers
        if y == 7:
            return (78, 54, 32)
        if face == "front" and 2 <= x <= 5 and y >= 3:
            return apron
        if face == "front" and y == 0 and x in (2, 3, 4, 5):
            return SKIN
        if y == 11 or (face == "front" and x == 0 and False):
            return trim
        return tunic

    def arm(face, x, y, w, h):
        if face == "top":
            return tunic
        if face == "bottom" or y >= 9:
            return SKIN
        if y == 8:
            return trim
        return tunic

    def leg(face, x, y, w, h):
        if face == "bottom" or y >= 9:
            return boots
        return trousers

    def hat(face, x, y, w, h):
        if faction == "saradomin":
            # A straw cap.
            if face == "top":
                return (214, 190, 112)
            if face != "bottom" and y < 2:
                return (190, 166, 92) if y == 1 else (214, 190, 112)
            return None
        # A hood that frames the face.
        hood = (66, 56, 50)
        if face == "bottom":
            return None
        if face == "front":
            if y < 2 or x == 0 or x == 7:
                return trim if y == 1 or x in (0, 7) and y == 1 else hood
            return None
        if face in ("left", "right") and y > 6:
            return None
        return hood

    hair = (92, 62, 36) if faction == "saradomin" else (34, 28, 26)
    return {"head": face_fn(hair, beard=(92, 62, 36) if faction == "saradomin" else None), "hat": hat,
            "body": body, "arm": arm, "leg": leg}


def warrior(faction):
    # A plain fighting man, not a knight: bare-headed in a shirt and trousers of his god's color, with his team's belt
    # and leather boots. Saradomin's wears blue, Zamorak's a red shirt and black trousers.
    if faction == "saradomin":
        shirt, shirt_dark = (52, 86, 176), (36, 60, 132)
        trousers, trousers_dark = (34, 50, 104), (24, 36, 78)
        hair = (104, 70, 40)
    else:
        shirt, shirt_dark = (156, 30, 28), (112, 20, 20)
        trousers, trousers_dark = (34, 32, 36), (22, 20, 24)
        hair = (40, 30, 26)
    belt = (78, 54, 32)
    boots = (58, 40, 26)

    def body(face, x, y, w, h):
        if face == "top":
            return shirt
        if face == "bottom":
            return trousers
        if y >= 10:
            return trousers
        if y in (8, 9):
            # Under the belt in the team's color, which team_overlay.png draws over it.
            return belt
        if face == "front" and y == 0 and 2 <= x <= 5:
            # The open neck of the shirt.
            return SKIN if 3 <= x <= 4 else shirt_dark
        if face == "front" and x in (3, 4) and y in (1, 2):
            return SKIN if y == 1 else shirt_dark
        return shirt

    def arm(face, x, y, w, h):
        if face == "top":
            return shirt
        if face == "bottom" or y >= 10:
            return SKIN
        if y == 9:
            return shirt_dark
        return shirt

    def leg(face, x, y, w, h):
        if face == "bottom" or y >= 9:
            return boots if y != 9 else (44, 30, 20)
        if face == "top":
            return trousers
        return trousers_dark if (face == "front" and x == 3) or y == 8 else trousers

    def hat(face, x, y, w, h):
        return None

    return {"head": face_fn(hair), "hat": hat, "body": body, "arm": arm, "leg": leg}


def archer(faction):
    if faction == "saradomin":
        hood, hood_trim = (40, 70, 150), (220, 220, 214)
        jerkin, leather = (112, 82, 50), (84, 58, 34)
        shirt, trousers, boots = (220, 214, 196), (78, 92, 56), (66, 46, 30)
        mask = None
    else:
        hood, hood_trim = (30, 26, 28), (140, 22, 22)
        jerkin, leather = (46, 38, 36), (96, 22, 20)
        shirt, trousers, boots = (70, 30, 28), (32, 30, 30), (20, 18, 18)
        mask = (24, 20, 20)
    strap = (70, 46, 26) if faction == "saradomin" else (130, 24, 22)

    def body(face, x, y, w, h):
        if face in ("top", "bottom"):
            return jerkin
        # The quiver's strap, from the right shoulder down to the left hip, and the quiver on the back.
        if face == "front" and x == y - 1 and y <= 8:
            return strap
        if face == "back" and 4 <= x <= 6 and y <= 9:
            if y == 0:
                return (236, 236, 230)
            return leather
        if y == 8:
            return leather
        return jerkin

    def arm(face, x, y, w, h):
        if face == "top":
            return jerkin
        if y < 3:
            return jerkin
        if face == "bottom" or y >= 9:
            return leather if y < 11 else SKIN
        return shirt

    def leg(face, x, y, w, h):
        if face == "bottom" or y >= 8:
            return boots
        return trousers

    def hat(face, x, y, w, h):
        if face == "bottom":
            return None
        if face == "front":
            if y < 2 or x in (0, 7):
                return hood_trim if y == 1 and 0 < x < 7 else hood
            if mask and y >= 5:
                return mask
            return None
        if face in ("left", "right") and y > 6:
            return None
        if face == "back" and y >= 7:
            return hood
        return hood

    return {"head": face_fn((120, 84, 44) if faction == "saradomin" else (28, 24, 24)), "hat": hat,
            "body": body, "arm": arm, "leg": leg}


def cavalry(faction):
    """A rider: a warrior's plate and tabard, with a plume on the helm in the god's color and riding boots."""
    parts = warrior(faction)
    plume = (44, 72, 164) if faction == "saradomin" else (150, 26, 24)
    boots, cuff = ((92, 62, 38), (122, 88, 56)) if faction == "saradomin" else ((36, 28, 26), (70, 52, 44))
    helm, legs = parts["hat"], parts["leg"]

    def hat(face, x, y, w, h):
        if face == "top" and x in (3, 4):
            return plume
        if face in ("front", "back") and y == 0 and x in (3, 4):
            return plume
        return helm(face, x, y, w, h)

    def leg(face, x, y, w, h):
        if face != "top" and y >= 6:
            return cuff if y == 6 else boots
        return legs(face, x, y, w, h)

    parts.update(hat=hat, leg=leg)
    return parts


def robed(robe, trim, hood=None, hair=(60, 44, 32), beard=None, belt=None, emblem=None, pointed=False, bald=False):
    """A figure in a long robe to the ground, with trim at its hem and cuffs: priests, monks, wizards and mages. Over
    the head a hood, a wizard's pointed hat, or with neither and bald, a monk's shaved crown."""
    def body(face, x, y, w, h):
        if face == "top":
            return robe
        if face == "bottom":
            return trim
        if belt and y == 7:
            return belt
        if face == "front" and emblem and 2 <= y <= 5 and (x in (3, 4) or y == 3 and 2 <= x <= 5):
            return emblem
        if face == "front" and y == 0 and x in (3, 4):
            return SKIN_DARK if hood is None else hood
        if face == "front" and x in (3, 4) and y > 0:
            return trim
        if y == 11:
            return trim
        return robe

    def arm(face, x, y, w, h):
        if face == "bottom" or y >= 10:
            return SKIN
        if y == 9:
            return trim
        return robe

    def leg(face, x, y, w, h):
        # The skirt of the robe, down to the feet.
        if face == "bottom":
            return (40, 32, 28)
        if y >= 10:
            return trim
        return robe

    def hat(face, x, y, w, h):
        if hood is not None:
            if face == "bottom":
                return None
            if face == "front":
                return hood if y < 1 or x == 0 or x == 7 else None
            if face in ("left", "right") and y > 6:
                return None
            return hood
        if pointed:
            # A brim round the head and a cone, drawn as its lower part.
            if face == "top":
                return robe if 2 <= x <= 5 and 2 <= y <= 5 else trim
            if face != "bottom" and y < 2:
                return trim if y == 1 else robe
            return None
        return None

    head = face_fn(hair, beard=beard)
    if bald:
        plain = head

        def head(face, x, y, w, h):
            if face == "top":
                return SKIN if 2 <= x <= 5 and 2 <= y <= 5 else hair
            if face == "front" and y < 2:
                return SKIN
            return plain(face, x, y, w, h)
    return {"head": head, "hat": hat, "body": body, "arm": arm, "leg": leg}


def chaos_priest(faction):
    """Zamorak's priest: a black robe with his red emblem on the breast and a red hood."""
    return robed((40, 34, 36), (150, 26, 24), hood=(126, 22, 24), emblem=(196, 40, 34), hair=(28, 24, 24))


def monk(faction):
    """Saradomin's monk: a plain brown habit tied with a rope, the crown of his head shaved."""
    return robed((106, 76, 48), (78, 54, 34), belt=(214, 196, 140), hair=(92, 62, 36), bald=True)


def dark_wizard(faction):
    """A dark wizard: black robes trimmed in purple, under a dark hood."""
    return robed((32, 30, 40), (100, 40, 130), hood=(26, 24, 32), hair=(30, 26, 28), beard=(70, 66, 70))


def zamorak_mage(faction):
    """A Zamorak mage: Zamorak's red robes with black and gold trim, hooded."""
    return robed((170, 30, 28), (30, 24, 24), hood=(140, 22, 22), emblem=(226, 186, 60), hair=(28, 24, 24))


def blue_wizard(faction):
    """A blue wizard of Saradomin's tower: blue robes and a pointed hat, and a long white beard."""
    return robed((44, 72, 170), (220, 200, 90), hair=(222, 222, 216), beard=(232, 232, 226), pointed=True)


# The demon's horns and tail, in the corner of the skin right of the arm, as ModelDemon draws them.
HORN = (56, 16, 1, 3, 1)
TAIL = (56, 20, 1, 8, 1)


def lesser_demon(faction):
    """A lesser demon: red hide all over, darker at the joints, black horns, yellow eyes and claws."""
    hide, dark, claw, horn = (168, 34, 26), (110, 20, 18), (40, 30, 26), (36, 30, 30)

    def head(face, x, y, w, h):
        if face == "front":
            if y == 3 and x in (1, 2, 5, 6):
                return (40, 10, 8)
            if y == 4 and x in (1, 2, 5, 6):
                return (250, 210, 60)
            if y == 6 and 2 <= x <= 5:
                return (40, 10, 8) if x in (3, 4) else (236, 230, 214)
        return dark if face in ("top", "back") and y < 2 else hide

    def body(face, x, y, w, h):
        if face == "front" and y in (2, 3, 5, 6, 8) and x in (2, 3, 4, 5):
            return dark
        return hide

    def arm(face, x, y, w, h):
        if face == "bottom" or y == 11:
            return claw
        return dark if y in (4, 5) else hide

    def leg(face, x, y, w, h):
        if face == "bottom" or y == 11:
            return claw
        return dark if y in (5, 6) else hide

    return {"head": head, "hat": lambda face, x, y, w, h: None, "body": body, "arm": arm, "leg": leg,
            "extra": [(HORN, lambda face, x, y, w, h: horn if y < 2 else (60, 50, 46)),
                      (TAIL, lambda face, x, y, w, h: claw if y >= 7 else hide)]}


def centaur(faction):
    """A centaur: only its face is seen, on its buttons; the game draws it with a model of its own. A bearded man with
    long brown hair under a gold circlet set with a blue gem, bare-chested over a chestnut coat."""
    skin, hair, coat = (222, 170, 128), (96, 62, 36), (138, 84, 46)

    def head(face, x, y, w, h):
        if face == "front":
            if y == 2:
                return (70, 130, 230) if x in (3, 4) else (226, 186, 70)
            if y == 3 and x in (1, 2, 5, 6):
                return (70, 44, 26)
            if y == 4 and x in (1, 2, 5, 6):
                return (52, 92, 170) if x in (2, 5) else (240, 240, 236)
            if y == 5 and x in (3, 4):
                return (190, 136, 98)
            if y >= 6 and 1 <= x <= 6:
                return (120, 50, 40) if y == 6 and 3 <= x <= 4 else hair
            if y < 2:
                return hair
            return skin
        if face in ("left", "right") and y == 2:
            return (226, 186, 70)
        return hair if face in ("top", "back") or y < 2 else skin

    def body(face, x, y, w, h):
        return skin if y < 10 else coat

    return {"head": head, "hat": lambda face, x, y, w, h: None, "body": body,
            "arm": lambda face, x, y, w, h: skin, "leg": lambda face, x, y, w, h: coat}


# The knights' extra parts, in the lower half of their 64x64 skin, as ModelKnight draws them.
VISOR = (0, 32, 7, 4, 1)
PLUME = (16, 32, 2, 3, 3)
SPIKE = (28, 32, 1, 4, 1)
PAULDRON = (0, 37, 6, 4, 6)
PAULDRON_CAP = (20, 56, 5, 1, 5)
CUFF = (0, 56, 5, 3, 5)
KNEE = (24, 43, 5, 3, 2)
BOOT = (0, 47, 5, 3, 6)
SKIRT = (22, 48, 8, 7, 1)
CAPE = (42, 37, 10, 20, 1)

# Zamorak's sign, as on his cape: a pair of horns curving up from a joined base, on an 8x6 grid.
ZAMORAK_SIGN = [
    "#......#",
    "##....##",
    "##....##",
    ".##..##.",
    ".######.",
    "..####..",
]

KNIGHTS = {
    # Blackened plate, near black on the legs, as the Black Knights of Falador wear it.
    "black": dict(plate=(46, 46, 52), dark=(24, 24, 28), light=(78, 78, 88), trim=(62, 62, 70), slit=(8, 8, 10),
                  legs=(30, 30, 34), boots=(22, 22, 24), strap=None),
    # The same, trimmed in gold, with black boots.
    "black_elite": dict(plate=(40, 40, 46), dark=(20, 20, 24), light=(70, 70, 80), trim=(232, 178, 36),
                        slit=(8, 8, 10), legs=(28, 28, 32), boots=(22, 22, 24), strap=None),
    # Bright white plate, the scabbard's strap across the chest.
    "white": dict(plate=(228, 228, 232), dark=(176, 178, 186), light=(250, 250, 252), trim=(204, 204, 212),
                  slit=(26, 26, 30), legs=(220, 220, 226), boots=(196, 196, 204), strap=(46, 40, 36)),
    # A justiciar: pale silver plate with gold edges over a blue tabard, olive boots.
    "justiciar": dict(plate=(206, 202, 218), dark=(150, 146, 168), light=(234, 232, 242), trim=(224, 184, 86),
                      slit=(30, 26, 34), legs=(58, 54, 58), boots=(98, 96, 40), strap=None, cloth=(96, 102, 172),
                      cloth_dark=(70, 74, 136)),
}


def knight(style):
    """A knight in full plate as ModelKnight draws it: a closed helm with a visor, broad pauldrons, gauntlet cuffs,
    knee guards and sabatons. The plume is painted white, for the renderer to tint in the team's color. Knights look
    the same whichever team trains them; an elite style is what their fortress's upgrade turns them into."""
    c = KNIGHTS[style]
    plate, dark, light, trim, slit = c["plate"], c["dark"], c["light"], c["trim"], c["slit"]
    justiciar = style == "justiciar"
    elite = style == "black_elite"
    gilded = elite or justiciar

    def body(face, x, y, w, h):
        if face in ("top", "bottom"):
            return plate
        if justiciar:
            if face == "front":
                # A gold star of Saradomin on the breastplate, the blue tabard showing at the collar and below the
                # belt.
                if y == 0:
                    return c["cloth"]
                if y == 8:
                    return trim
                if y > 8:
                    return c["cloth"] if 2 <= x <= 5 else plate
                if (x in (3, 4) and 1 <= y <= 6) or (y in (3, 4) and 1 <= x <= 6) or (x in (2, 5) and y in (2, 5)):
                    return trim if not (x in (3, 4) and y in (3, 4)) else (250, 226, 140)
                return light if x in (3, 4) else plate
            if face == "back":
                return c["cloth"] if y >= 8 or y == 0 else plate
            return c["cloth"] if y >= 8 else plate
        if face == "front":
            if c["strap"] and x == 7 - (y * 7) // 11:
                return c["strap"]
            # A breastplate with a ridge down the middle, edged below, and plate faulds under the belt.
            if y == 7:
                return trim if gilded else dark
            if gilded and (y == 0 or x in (0, 7)) and y < 7:
                return trim
            if x in (3, 4) and y < 7:
                return light
            if y >= 8:
                return dark if y % 2 else plate
            return plate
        if face == "back":
            if c["strap"] and x == (y * 7) // 11:
                return c["strap"]
            if y < 7 and x in (3, 4):
                return dark
            return dark if y >= 8 and y % 2 else plate
        return plate if y < 8 else (dark if y % 2 else plate)

    def arm(face, x, y, w, h):
        if face == "top":
            return light
        if face == "bottom":
            return dark
        if justiciar and y >= 6:
            # Grey gauntlets and bare hands showing below the cuffs.
            return SKIN if y >= 10 else (120, 120, 132)
        if y in (5, 6):
            # The elbow cop.
            return light if face == "front" else plate
        return plate if y < 10 else dark

    def leg(face, x, y, w, h):
        if face == "bottom":
            return c["boots"]
        if justiciar:
            return c["legs"] if y < 6 else plate if y < 9 else c["boots"]
        if y >= 9:
            return c["boots"]
        return c["legs"] if face in ("back", "left", "right") or y < 4 else plate

    def hat(face, x, y, w, h):
        if face == "bottom":
            return None
        if face == "top":
            if justiciar:
                return trim if x in (3, 4) or y in (3, 4) else plate
            return (trim if gilded else light) if x in (3, 4) else plate
        if face == "front":
            if justiciar:
                # A pointed great helm: eye slits either side of a gold nasal, gold at the brow.
                if y == 3 and x in (1, 2, 5, 6):
                    return slit
                if x in (3, 4) and y >= 1:
                    return trim
                if y == 0:
                    return trim
                return light if y == 1 else plate
            if gilded and y == 0:
                return trim
            if x in (3, 4):
                return trim if gilded else light
            return plate
        if face == "back":
            return dark if y == 7 else (trim if gilded and x in (3, 4) else plate)
        if justiciar and y == 2:
            return trim
        return plate if y != 7 else dark

    def visor(face, x, y, w, h):
        # The visor: a grille of slits for the eyes, as on the Falador knights' helms.
        if face == "front":
            if justiciar:
                return trim if y in (0, 3) else plate
            if gilded and (y == 0 or x in (0, 6)):
                return trim
            if y in (1, 2) and x in (1, 3, 5):
                return slit
            return light if y == 0 else plate
        return dark if face == "bottom" else plate

    def plume(face, x, y, w, h):
        # White, for the team's color to tint; lighter on top so the curl shows.
        if face == "top":
            return (255, 255, 255)
        return (236, 236, 236) if (x + y) % 2 else (212, 212, 212)

    def spike(face, x, y, w, h):
        return (250, 226, 140) if y == 0 or face == "top" else trim

    def pauldron(face, x, y, w, h):
        if face == "top":
            return light if 1 <= x <= 4 and 1 <= y <= 4 else plate
        if face == "bottom":
            return dark
        if y == h - 1:
            return trim if gilded else dark
        if gilded and justiciar and x in (0, w - 1):
            return trim
        return light if y == 0 else plate

    def cap(face, x, y, w, h):
        if face == "top":
            return trim if gilded and (x in (0, w - 1) or y in (0, h - 1)) else light
        return trim if gilded else light

    def cuff(face, x, y, w, h):
        if face in ("top", "bottom"):
            return dark
        if gilded and y in (0, h - 1):
            return trim
        return light if y == 0 else plate

    def knee(face, x, y, w, h):
        if gilded:
            return (250, 226, 140) if face == "front" and y == 1 and x == 2 else trim
        return light if face == "front" and y == 1 else plate

    def boot(face, x, y, w, h):
        if face == "top":
            return c["boots"]
        if justiciar and y == 0:
            return trim
        return c["boots"] if y > 0 else plate

    def skirt(face, x, y, w, h):
        # Plates either side of a blue panel, edged in gold.
        if face in ("front", "back"):
            if 2 <= x <= 5:
                return c["cloth"] if y < h - 1 else c["cloth_dark"]
            if y == h - 1 or x in (1, 6):
                return trim
            return light if y % 3 == 0 else plate
        return plate

    def cape(face, x, y, w, h):
        red, red_dark, black = (164, 22, 20), (118, 14, 14), (14, 12, 12)
        if face == "back":
            # The outside, seen from behind: Zamorak's sign in black and a ragged black hem.
            if 1 <= x <= 8 and 4 <= y <= 9 and ZAMORAK_SIGN[y - 4][x - 1] == "#":
                return black
            if y == h - 1:
                return black if x % 3 else None
            if y == h - 2 and x % 3 == 0:
                return black
            return red if (x + y) % 5 else red_dark
        if face == "front":
            # The lining, against his back.
            return red_dark
        return black if face in ("left", "right") else red

    parts = {"head": face_fn((60, 44, 30)), "hat": hat, "body": body, "arm": arm, "leg": leg, "height": 64,
             "extra": [(VISOR, visor), (PLUME, plume), (PAULDRON, pauldron), (PAULDRON_CAP, cap), (CUFF, cuff),
                       (KNEE, knee), (BOOT, boot)]}
    if justiciar:
        parts["extra"] += [(SPIKE, spike), (SKIRT, skirt)]
    if elite:
        parts["extra"].append((CAPE, cape))
    return parts


def white_knight(faction):
    return knight("white")


def black_knight(faction):
    return knight("black")


def justiciar(faction):
    """What the Justiciars upgrade turns White Knights into."""
    return knight("justiciar")


def elite_black_knight(faction):
    """What the Elite Black Knights upgrade turns Black Knights into."""
    return knight("black_elite")


def tradigee(faction):
    """Sir Tradigee, a justiciar: white and gold plate, a gold faceguard with a cross of light, and a white cloak."""
    white, white_dark = (236, 234, 226), (186, 182, 168)
    gold, gold_dark, gold_light = (214, 172, 58), (156, 116, 30), (250, 222, 120)
    visor = (40, 34, 26)

    def body(face, x, y, w, h):
        if face in ("top", "bottom"):
            return gold
        if face == "back":
            # A white cloak, edged in gold.
            return gold if x in (0, 7) or y == 11 else white
        if face == "front":
            if x in (0, 7) or y in (0, 7):
                return gold
            # The star of Saradomin on the breast.
            if (x in (3, 4) and 1 <= y <= 5) or (y == 3 and 2 <= x <= 5):
                return gold_light
            return white if y < 7 else gold_dark if y == 8 else white_dark
        return white if y < 7 else gold

    def arm(face, x, y, w, h):
        if face == "top":
            return gold_light
        if y < 4:
            # Great winged pauldrons.
            return gold_light if y == 0 else gold if y < 3 else gold_dark
        if y >= 9:
            return gold if y == 9 else white
        return white_dark if y == 4 else white

    def leg(face, x, y, w, h):
        if face == "bottom" or y >= 10:
            return gold_dark
        if y == 5:
            return gold
        return white

    def hat(face, x, y, w, h):
        if face == "bottom":
            return None
        if face == "top":
            return gold_light if x in (3, 4) else gold
        if face == "front":
            # The faceguard: gold, with a cross-shaped opening.
            if (y == 3 and 1 <= x <= 6) or (x in (3, 4) and 3 <= y <= 6):
                return visor
            return gold_light if y == 0 else gold
        if face in ("left", "right") and y < 3:
            # Wings on either side of the helm.
            return white
        return gold if y != 7 else gold_dark

    return {"head": face_fn((200, 190, 160)), "hat": hat, "body": body, "arm": arm, "leg": leg}


def sagittare(faction):
    """Sagittare, the ranger of Daemonheim: a dark hooded ranger in black and green leather, with gold-edged
    vambraces, a quiver on his back and burning eyes under the hood."""
    hood, hood_edge = (30, 34, 28), (176, 140, 52)
    leather, leather_dark = (52, 74, 40), (34, 48, 28)
    black, gold = (24, 22, 22), (196, 156, 60)
    eyes = (240, 120, 30)

    def body(face, x, y, w, h):
        if face in ("top", "bottom"):
            return leather
        if face == "front" and x == y - 1 and y <= 8:
            return gold
        if face == "back" and 3 <= x <= 6 and y <= 10:
            return (236, 236, 230) if y == 0 else (92, 54, 30)
        if y == 8:
            return black
        if face == "front" and x in (0, 7):
            return black
        return leather if y < 8 else leather_dark

    def arm(face, x, y, w, h):
        if face == "top":
            return black
        if y < 3:
            return black
        if 7 <= y <= 10:
            # Vambraces.
            return gold if y in (7, 10) else black
        if y == 11:
            return black
        return leather_dark

    def leg(face, x, y, w, h):
        if face == "bottom" or y >= 8:
            return black
        return leather_dark if y % 3 else black

    def hat(face, x, y, w, h):
        if face == "bottom":
            return None
        if face == "front":
            if y < 2 or x in (0, 7):
                return hood_edge if y == 1 and 0 < x < 7 else hood
            if y == 4 and x in (1, 2, 5, 6):
                return eyes
            if y >= 5:
                # A mask over the mouth.
                return black
            return (16, 14, 14)
        if face in ("left", "right") and y > 6:
            return None
        return hood

    return {"head": face_fn((24, 20, 20)), "hat": hat, "body": body, "arm": arm, "leg": leg}


def overlay():
    """White bands the team's color is drawn in: round the upper arms and a belt."""
    image = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    rng = random.Random(1)

    def body(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        return (255, 255, 255) if y in (8, 9) else None

    def arm(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        return (255, 255, 255) if y in (4, 5) else None

    def leg(face, x, y, w, h):
        return None

    paint(image, BODY, body, rng, noise=0)
    paint(image, ARM, arm, rng, noise=0)
    return image


def skin(kind, faction):
    parts = kind(faction)
    image = Image.new("RGBA", (64, parts.get("height", 32)), (0, 0, 0, 0))
    rng = random.Random(sum(map(ord, kind.__name__ + faction)))
    paint(image, HEAD, parts["head"], rng, noise=4)
    paint(image, HAT, parts["hat"], rng, noise=5)
    paint(image, BODY, parts["body"], rng)
    paint(image, ARM, parts["arm"], rng)
    paint(image, LEG, parts["leg"], rng)
    for part, fn in parts.get("extra", []):
        paint(image, part, fn, rng)
    return image


# ---------------------------------------------------------------- icons

PALETTE = {
    "k": (34, 28, 24), "w": (156, 110, 64), "W": (104, 72, 42), "s": (160, 160, 160), "S": (106, 106, 112),
    "r": (178, 64, 48), "R": (120, 38, 32), "b": (64, 96, 196), "B": (38, 58, 132), "y": (234, 194, 64),
    "g": (84, 156, 64), "G": (46, 104, 42), "l": (236, 230, 214), "f": (226, 172, 132), "i": (206, 210, 220),
    "I": (132, 138, 150), "h": (226, 196, 92), "o": (224, 134, 62), "c": (96, 204, 214), "d": (122, 86, 56),
    "x": (44, 50, 66), "e": (60, 40, 30), "m": (196, 52, 50), "M": (138, 30, 34), "t": (190, 154, 100),
    "u": (255, 240, 150), "Y": (176, 124, 28), "n": (60, 58, 66), "N": (30, 28, 32), "v": (248, 248, 246),
    "a": (242, 110, 30), "j": (60, 84, 44), "p": (150, 70, 190), "P": (96, 36, 130),
}

ICON_ORDER = ["town_hall", "house", "barracks", "storehouse", "quarry", "farm", "armory", "tower",
              "worker", "soldier", "archer", "tree", "upgrade", "level",
              "food", "wood", "gold", "sword", "axe", "pickaxe", "hoe", "wall", "gate",
              "white_knights_fortress", "black_knights_fortress", "white_knight", "black_knight", "tradigee",
              "sagittare", "archery_range", "stables", "cavalry", "horse", "favor", "chaos_temple", "monastery",
              "wizards_tower", "stone_circle", "chaos_priest", "monk", "dark_wizard", "zamorak_mage", "blue_wizard",
              "lesser_demon", "centaur"]

# Icons that are Minecraft's own item pictures, taken from the game's jar: the resources and the tools of the actions
# in tooltips. An oak log is drawn as a little block, the way the inventory shows it.
VANILLA = {
    "food": "items/beef_raw.png", "gold": "items/gold_ingot.png", "sword": "items/iron_sword.png",
    "axe": "items/iron_axe.png", "pickaxe": "items/iron_pickaxe.png", "hoe": "items/iron_hoe.png",
    "favor": "items/nether_star.png",
}
MINECRAFT_JAR = os.path.expanduser("~/.gradle/caches/retro_futura_gradle/mc-vanilla/1.7.10/client.jar")

ICON_ART = {
    "town_hall": """
        .......y........
        .......yb.......
        .......k........
        ......krk.......
        .....krrrk......
        ....kRRRRRk.....
        ..kk.ksssk.kk...
        .krrkksxskkrrk..
        krrrrksxskrrrrk.
        kkkkkkkkkkkkkkk.
        ksssssssssssssk.
        ksxxsssssssxxsk.
        ksxxssskkssxxsk.
        kSSSSSkWWkSSSSk.
        kSSSSSkWWkSSSSk.
        kkkkkkkkkkkkkkk.""",
    "house": """
        ................
        ........kk......
        .......krrk.....
        ......krrrrk....
        .....krrrrrrk...
        ....krrrrrrrrk..
        ...krrrrrrrrrrk.
        ..kkkkkkkkkkkkkk
        ...kllllllllllk.
        ...klxxlllxxllk.
        ...klxxlllxxllk.
        ...kllllkkllllk.
        ...kWWWWkWkWWWk.
        ...kllllkWkllllk
        ...kkkkkkkkkkkk.
        ................""",
    "barracks": """
        ................
        .kk..........kk.
        kiik........kiik
        .kiik......kiik.
        ..kiik....kiik..
        ...kiik..kiik...
        ....kiikkiik....
        .....kiiiik.....
        .....kiiiik.....
        ....kiikkiik....
        ...kyykk.kyyk...
        ..kyyk....kyyk..
        .kWWk......kWWk.
        kWWk........kWWk
        .kk..........kk.
        ................""",
    "storehouse": """
        ................
        ................
        ..kkkkkkkkkkkk..
        .kwwwwwwwwwwwwk.
        .kWWWWWWWWWWWWk.
        .kkkkkkkkkkkkkk.
        .kwwwwwkkwwwwwk.
        .kwwwwkyykwwwwk.
        .kWWWWkyykWWWWk.
        .kwwwwwkkwwwwwk.
        .kwwwwwwwwwwwwk.
        .kWWWWWWWWWWWWk.
        .kwwwwwwwwwwwwk.
        .kkkkkkkkkkkkkk.
        ................
        ................""",
    "wall": """
        ................
        ................
        ..ss..ss..ss....
        ..sS..sS..sS....
        .ssssssssssss...
        .sSssSssSssSs...
        .ssssssssssss...
        .SssSssSssSss...
        .ssssssssssss...
        .sSssSssSssSs...
        .ssssssssssss...
        .SssSssSssSss...
        .ssssssssssss...
        .SSSSSSSSSSSS...
        ................
        ................""",
    "gate": """
        ................
        ..ss.ss..ss.ss..
        ..sS.sS..sS.sS..
        .ssssss..ssssss.
        .sSssSs..sSssSs.
        .ssssskkkkssssS.
        .SssskwWwWkssss.
        .sssskWwWwkSsss.
        .SssskwWwWkssss.
        .sssskWwWwkSsss.
        .SssskwWwWkssss.
        .sssskWwWwkSsss.
        .SssskwWwWkssss.
        .SSSSkkkkkkSSSS.
        ................
        ................""",
    "quarry": """
        ................
        .........kk.....
        ........kiik....
        .......kiIk.....
        ......kiIk......
        ..kkkkWkk.......
        .kiiikWk........
        ..kkkWk.........
        ....kWk.........
        ...kWk..........
        ..kWk..sssss....
        ..kk.ssosssss...
        ...sssssssocss..
        ..sSssossSssss..
        .SSSSSSSSSSSSSS.
        ................""",
    "farm": """
        ................
        ....h..h..h..h..
        ...hhh.hh.hh.hh.
        ...hh.hhh.hhhh..
        ....h..h..h..h..
        ....g..g..g..g..
        ....g..g..g..g..
        .dddddddddddddd.
        .dGdGdGdGdGdGdd.
        .dddddddddddddd.
        .ddhdhdhddhdhdd.
        .dddddddddddddd.
        .dGdGdGdGdGdGdd.
        .dddddddddddddd.
        ................
        ................""",
    "armory": """
        ................
        ................
        ....kkkkkkkk....
        ...kiiiiiiiiik..
        ..kiiiiiiiiiiik.
        ..kIIIIIIIIIIIk.
        ...kkkkiiikkkk..
        ......kiiik.....
        ......kIIIk.....
        .....kiiiiik....
        ....kiiiiiiik...
        ....kIIIIIIIk...
        ....kkkkkkkkk...
        .....kWWWWWk....
        .....kkkkkkk....
        ................""",
    "tower": """
        ....k.k..k.k....
        ....kskkkksk....
        ....kssssssk....
        ....kkkkkkkk....
        .....kssssk.....
        .....ksxxsk.....
        .....kssssk.....
        .....ksSssk.....
        .....kssssk.....
        .....ksxxsk.....
        .....kssssk.....
        .....kSsssk.....
        .....ksssSk.....
        ....kssWWssk....
        ....kSSWWSSk....
        ....kkkkkkkk....""",
    "worker": """
        ................
        .....kkkk.......
        ....khhhhk......
        ...khhhhhhk.....
        ....kffffk......
        ....kfkfkk...kk.
        ....kffffk..kiik
        .....kffk..kiIk.
        ...kkllllkkkWk..
        ..klllllllkWk...
        ..kllkllkllWk...
        ..kfkwwwwkkk....
        ....kddddk......
        ....kdkkdk......
        ....kekkek......
        ....kkk.kkk.....""",
    "soldier": """
        .........kk.....
        ....kkkkkik.....
        ...kiiiikik.....
        ...kixxiikik....
        ...kiiiik.kik...
        ....kiik...kik..
        ..kkbbbbkk..kik.
        .kibbyybbik..kik
        .kibyyyybikkyk..
        .kibbyybbikyyk..
        .kfbbbbbbfkk....
        ..kkIIIIkk......
        ...kiIIik.......
        ...kik.kik......
        ...kIk.kIk......
        ...kkk.kkk......""",
    "archer": """
        ................
        .....kkkk..kk...
        ....kbbbbk.kWk..
        ...kbbbbbbk.kWk.
        ....kffffk..kWk.
        ....kfkfkk..klWk
        ....kffffk..kl.k
        .....kbbk...kl.k
        ...kkwwwwkk.kl.k
        ..kwwwwwwwwkkl.k
        ..kfwwwwwwkfkWk.
        ....kwwwwk..kWk.
        ....kGGGGk.kWk..
        ....kGkkGk..kk..
        ....kekkek......
        ....kkk.kkk.....""",
    "tree": """
        ................
        ......kkkk......
        ....kkggggkk....
        ...kggGggggGk...
        ..kgggggGggggk..
        ..kgGggggggGgk..
        .kgggggggGggggk.
        .kggGgggggggGgk.
        ..kgggggGggggk..
        ...kkggggggkk...
        .....kkWWkk.....
        ......kWWk......
        ......kWwk......
        ......kWWk......
        .....kWWWWk.....
        .....kkkkkk.....""",
    "upgrade": """
        ................
        .......kk.......
        ......kyyk......
        .....kyyyyk.....
        ....kyyyyyyk....
        ...kyyyyyyyyk...
        ..kkkkyyyykkkk..
        .....kyyyyk.....
        .....kyyyyk.....
        .....kyyyyk.....
        .....kyyyyk.....
        .....kkkkkk.....
        ................
        ..kkkkkkkkkkkk..
        ..kWWWWWWWWWWk..
        ..kkkkkkkkkkkk..""",
    "level": """
        ................
        ..kk..........k.
        .kyyk........kyk
        .kyyyk......kyyk
        ..kyyyk....kyyk.
        ...kyyykkkkyyk..
        ....kyyyyyyyk...
        .....kyyyyyk....
        ......kkkkk.....
        ...kkkkkkkkkk...
        ..kssssssssssk..
        ..ksSssSssSssk..
        ..kssssssssssk..
        ..kSSSSSSSSSSk..
        ..kkkkkkkkkkkk..
        ................""",
    "white_knights_fortress": """
        ..b.........b...
        ..bb........bb..
        ..k..........k..
        .kvk.k.kk.k.kvk.
        .kvvkvkvvkvkvvk.
        .kvikvvvvvvkvik.
        .kvikvviivvkvik.
        .kvvkvxvvxvkvvk.
        .kvikvxvvxvkvik.
        .kvvkvvvvvvkvvk.
        .kvikvvkkvvkvik.
        .kvvkvkWWkvkvvk.
        .kvikvkWWkvkvik.
        .kiikikWWkikiik.
        .kkkkkkkkkkkkkk.
        ................""",
    "black_knights_fortress": """
        ..r.........r...
        ..rr........rr..
        ..k..........k..
        .knk.k.kk.k.knk.
        .knnknknnknknnk.
        .knNknnnnnnknNk.
        .knNknnNNnnknNk.
        .knnknxnnxnknnk.
        .knNknxnnxnknNk.
        .knnknnnnnnknnk.
        .knNknnkknnknNk.
        .knnknkRRknknnk.
        .knNknkRRknknNk.
        .kNNkNkRRkNkNNk.
        .kkkkkkkkkkkkkk.
        ................""",
    "white_knight": """
        ................
        .....kkkk.......
        ....kvvvvk......
        ...kvvvvvvk.....
        ...kvxxxxvk...k.
        ...kvvvvvvk..kik
        ....kvvvvk..kik.
        ..kkvvvvvvkkik..
        .kivvvvvvvvkk...
        .kiyvvvvvvykk...
        .kfkvvvvvvkfk...
        ...kiiiiiik.....
        ...kvvkkvvk.....
        ...kvk..kvk.....
        ...kik..kik.....
        ...kkk..kkk.....""",
    "black_knight": """
        ................
        .....kkkk.......
        ....knnnnk......
        ...knnnnnnk.....
        ...knrrrrnk...k.
        ...knnnnnnk..kik
        ....knnnnk..kik.
        ..kknnnnnnkkik..
        .knnnnnnnnnkk...
        .knNnnnnnnNkk...
        .kfknnnnnnkfk...
        ...kNNNNNNk.....
        ...knnkknnk.....
        ...knk..knk.....
        ...kNk..kNk.....
        ...kkk..kkk.....""",
    "tradigee": """
        ..k........k....
        .kvk.kkkk.kvk...
        .kvkkyyyykkvk.k.
        ..kkyyyyyykk.kck
        ...kyxxxxyk..kck
        ...kyyxxyyk.kck.
        ....kyxxyk..kck.
        ..kkvvvvvvkkck..
        .kyvvvyyvvvyk...
        .kyvvyyyyvvyk...
        .kfkvvyyvvkfk...
        ...kyyyyyyk.....
        ...kvvkkvvk.....
        ...kvk..kvk.....
        ...kyk..kyk.....
        ...kkk..kkk.....""",
    "archery_range": """
        ................
        ....kkkkkkkk....
        ...kllllllllk...
        ..kllrrrrrrllk..
        .kllrrllllrrllk.
        .klrrllrrllrrlk.
        .klrllrrrrllrlk.
        .klrlrryyrrlrlk.
        .klrlrryyrrlrlk.
        .klrllrrrrllrlk.
        .klrrllrrllrrlk.
        .kllrrllllrrllk.
        ..kllrrrrrrllk..
        ...kllllllllk...
        ....kkkkkkkk....
        ................""",
    "stables": """
        ................
        ....kkkkkkkk....
        ...kSssssssSk...
        ..kSskkkkkksSk..
        ..kskk....kksk..
        .kSsk......ksSk.
        .ksk........ksk.
        .kSk........kSk.
        .ksk........ksk.
        .kSk........kSk.
        .ksk........ksk.
        .kSkk......kkSk.
        .kssk......kssk.
        .kkkk......kkkk.
        ................
        ................""",
    "cavalry": """
        ................
        .........kk.....
        ........kmk.....
        .......kmmkkk...
        ......kssssssk..
        .....kssksssSsk.
        ....ksssssssSsk.
        ...kssssssssSmk.
        ..ksssssskkSmk..
        .kssssssk..kmk..
        .kkssssk..kSmk..
        ..kkkkk...kSmk..
        ..........kSmk..
        .........kssmk..
        .........ksssSk.
        .........kkkkkk.""",
    "chaos_temple": """
        .......kk.......
        ......kmmk......
        .......kk.......
        ......kNNk......
        .....kNnnNk.....
        ....kNnnnnNk....
        ...kNnnnnnnNk...
        ..kkkkkkkkkkkk..
        ..knnnnnnnnnnk..
        ..knMnnnnnnMnk..
        ..knMnnkknnMnk..
        ..knMnkmmknMnk..
        ..knMnkmmknMnk..
        ..knMnkmmknMnk..
        .kNNNNNNNNNNNNk.
        kkkkkkkkkkkkkkkk""",
    "monastery": """
        ................
        ..........k.....
        .........kyk....
        ..........k.....
        .........kwk....
        ........kwwwk...
        ..kkkkkkkssskk..
        .kwwwwwwkssssk..
        kwwwwwwwwkskssk.
        kkkkkkkkkksksksk
        .kssssssskssssk.
        .ksxsxsxsksxxsk.
        .ksxsxsxsksxxsk.
        .ksssssssksssskk
        .kSSSSSSSkSWWSk.
        kkkkkkkkkkkkkkkk""",
    "wizards_tower": """
        .......kk.......
        ......kbbk......
        .....kbbbbk.....
        ....kbbbbbbk....
        ...kkkkkkkkkk...
        ....kssssssk....
        ....kssxxssk....
        ....kssxxssk....
        ....kssssssk....
        ....ksSssssk....
        ....kssssSsk....
        ....kssxxssk....
        ....kssxxssk....
        ....kssssssk....
        ...kssskkksssk..
        ..kkkkkWWkkkkkk.""",
    "stone_circle": """
        ................
        ................
        ......kk.kk.....
        .....ksSkSsk....
        ..kk.ksSkSsk.kk.
        .ksSkksSkSskksSk
        .ksSk.kk.kk.ksSk
        .ksSk..pPp..ksSk
        .ksSk.pPpPp.ksSk
        .kkkk..pPp..kkkk
        ..kk..........kk
        .ksSk..kk..ksSk.
        .ksSk.ksSk.ksSk.
        .ksSk.ksSk.ksSk.
        jjjjjjjjjjjjjjjj
        ................""",
    "chaos_priest": """
        ................
        .....kkkkkk.....
        ....kmmmmmmk....
        ...kmMMMMMMmk...
        ...kmMffffMmk...
        ...kmMfkfkMmk...
        ...kmMffffMmk...
        ....kkNmmNkk....
        ...kNNNmmNNNk...
        ..kNNNmmmmNNNk..
        ..kfNNNmmNNNfk..
        ...kNNNmmNNNk...
        ...kNNNNNNNNk...
        ...kNNNNNNNNk...
        ...kmmmmmmmmk...
        ...kkkkkkkkkk...""",
    "monk": """
        ................
        .....kkkkkk.....
        ....kffffffk....
        ....kWffffWk....
        ....kWfkfkWk....
        ....kWffffWk....
        .....kffffk.....
        ....kkddddkk....
        ...kddddddddk...
        ..kddddddddddk..
        ..kftttttttdfk..
        ...kddddddddk...
        ...kddddddddk...
        ...kddddddddk...
        ...kWWWWWWWWk...
        ...kkkkkkkkkk...""",
    "dark_wizard": """
        ................
        .....kkkkkk.....
        ....kNNNNNNk....
        ...kNNNNNNNNk...
        ...kNNffffNNk...
        ...kNNfkfkNNk...
        ...kNNffffNNk...
        ....kkSSSSkk..k.
        ...kNNNSSNNNkkyk
        ..kNNNNppNNNNkWk
        ..kfNNNppNNNfWk.
        ...kNNNppNNNkWk.
        ...kNNNppNNNkWk.
        ...kNNNNNNNNkk..
        ...kppppppppk...
        ...kkkkkkkkkk...""",
    "zamorak_mage": """
        ................
        .....kkkkkk.....
        ....kMMMMMMk....
        ...kMMMMMMMMk...
        ...kMMffffMMk...
        ...kMMfkfkMMk...
        ...kMMffffMMk.km
        ....kkmmmmkk.kmk
        ...kmmmNNmmmkyk.
        ..kmmmmyymmmmIk.
        ..kfmmmNNmmmIfk.
        ...kmmmNNmmkIk..
        ...kmmmNNmmkIk..
        ...kmmmmmmmkk...
        ...kNNNNNNNNk...
        ...kkkkkkkkkk...""",
    "blue_wizard": """
        .......kk.......
        ......kbbk......
        .....kbbbbk.....
        ....kbbbbbbk....
        ..kkkkkkkkkkkk..
        ....kffffffk....
        ....kfkffkfk....
        ....kvvvvvvk..k.
        ...kbvvvvvvbkkck
        ..kbbbvvvvbbbkWk
        ..kfbbbvvbbbfWk.
        ...kbbbbbbbbkWk.
        ...kbbbbbbbbkWk.
        ...kbbbbbbbbkk..
        ...kyyyyyyyyk...
        ...kkkkkkkkkk...""",
    "centaur": """
        ....kkkk........
        ...keeeek.......
        ...kyybyk.......
        ...kfkfkk.......
        ...keeeek.......
        .k..keek........
        kwkkffffk.......
        kwfffffffk......
        kw.kffffkkkkkkk.
        kw.kffffddddddek
        kw.kddddddddddek
        .k.kddddddddddke
        ...kddkkkkkkddk.
        ...kdk.....kdk..
        ...klk.....klk..
        ...kkk.....kkk..""",
    "lesser_demon": """
        ..kk........kk..
        ..kNk......kNk..
        ...kNkkkkkkNk...
        ....kmmmmmmk....
        ....kmymmymk....
        ....kmmmmmmk....
        ....kmvkkvmk....
        ..kkkkmmmmkkkk..
        .kmmmmMmmMmmmmk.
        kmmkkmMmmMmkkmmk
        kmk.kmmmmmmk.kmk
        kNk.kmMmmMmk.kNk
        ....kmmkkmmk..k.
        ....kmmk.kmmkkmk
        ...kNNk...kNNkk.
        ...kkk.....kkk..""",
    "horse": """
        ................
        .........kk.....
        ........kdk.....
        .......kddkkk...
        ......kddddddk..
        .....kddkdddddk.
        ....kddddddddek.
        ...kdddddddddek.
        ..kddddddkkdek..
        .kdddddddk.kek..
        .kkddddddk.kdek.
        ..kkkkkkk..kdek.
        ...........kdek.
        ..........kddek.
        ..........kdddek
        ..........kkkkkk""",
    "sagittare": """
        ................
        .....kkkk..kk...
        ....kNNNNk.kWk..
        ...kNyyyyNk.kWk.
        ....kNNNNk..kWk.
        ....kaNNak..klWk
        ....kNNNNk..kl.k
        .....kNNk...kl.k
        ...kkjjjjkk.kl.k
        ..kjjyjjjjjkkl.k
        ..kNjjyjjjNkkWk.
        ....kjjjjk..kWk.
        ....kNNNNk.kWk..
        ....kNkkNk..kk..
        ....kykkyk......
        ....kkk.kkk.....""",
}


# Items of the mod's own, drawn as the icons are and held diagonally the way Minecraft's tools are.
ITEMS = os.path.join(ROOT, "textures", "items")
# Zamorak's staff and Silverlight are models, with icons drawn from them by tools/models/generate.py.
ITEM_ART = {}


def texture(jar, path):
    with zipfile.ZipFile(jar) as archive:
        with archive.open("assets/minecraft/textures/" + path) as file:
            return Image.open(io.BytesIO(file.read())).convert("RGBA")


def block_icon(top, side):
    """A block seen from above at an angle, as in the inventory: the top and two shaded sides."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    t, l, r, b = (8, 0.5), (0.5, 4.25), (15.5, 4.25), (8, 8)
    drop = 7.5

    def solve(p, o, a, c):
        # p = o + u * a + v * c for u, v.
        det = a[0] * c[1] - a[1] * c[0]
        dx, dy = p[0] - o[0], p[1] - o[1]
        return (dx * c[1] - dy * c[0]) / det, (a[0] * dy - a[1] * dx) / det

    def sample(image, u, v, shade):
        x = min(15, max(0, int(u * 16)))
        y = min(15, max(0, int(v * 16)))
        pr, pg, pb, pa = image.getpixel((x, y))
        return int(pr * shade), int(pg * shade), int(pb * shade), pa

    for y in range(16):
        for x in range(16):
            p = (x + 0.5, y + 0.5)
            u, v = solve(p, t, (r[0] - t[0], r[1] - t[1]), (l[0] - t[0], l[1] - t[1]))
            if 0 <= u <= 1 and 0 <= v <= 1:
                image.putpixel((x, y), sample(top, u, v, 1.0))
                continue
            u, v = solve(p, l, (b[0] - l[0], b[1] - l[1]), (0, drop))
            if 0 <= u <= 1 and 0 <= v <= 1:
                image.putpixel((x, y), sample(side, u, v, 0.8))
                continue
            u, v = solve(p, b, (r[0] - b[0], r[1] - b[1]), (0, drop))
            if 0 <= u <= 1 and 0 <= v <= 1:
                image.putpixel((x, y), sample(side, u, v, 0.6))
    return image


def icon(name, jar):
    if name in VANILLA:
        return texture(jar, VANILLA[name])
    if name == "wood":
        return block_icon(texture(jar, "blocks/log_oak_top.png"), texture(jar, "blocks/log_oak.png"))
    return pixel_art(ICON_ART[name])


def pixel_art(art):
    rows = [row.strip() for row in art.strip("\n").split("\n")]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows[:16]):
        for x, char in enumerate(row[:16].ljust(16, ".")):
            if char != ".":
                image.putpixel((x, y), PALETTE[char] + (255,))
    return image


def icons(jar):
    sheet = Image.new("RGBA", (16 * len(ICON_ORDER), 16), (0, 0, 0, 0))
    for i, name in enumerate(ICON_ORDER):
        sheet.paste(icon(name, jar), (16 * i, 0))
    return sheet


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", help="also write enlarged pictures into this folder")
    parser.add_argument("--minecraft-jar", default=MINECRAFT_JAR,
                        help="the Minecraft 1.7.10 client jar to take item pictures from (the build downloads it)")
    args = parser.parse_args()
    os.makedirs(SKINS, exist_ok=True)
    os.makedirs(os.path.dirname(ICONS), exist_ok=True)
    written = []
    for faction in ("saradomin", "zamorak"):
        # Knights and heroes look the same whichever team trains them; both factions get a copy.
        for kind, name in ((worker, "worker"), (warrior, "soldier"), (archer, "archer"),
                           (white_knight, "white_knight"), (black_knight, "black_knight"), (tradigee, "tradigee"),
                           (sagittare, "sagittare"), (cavalry, "cavalry"), (chaos_priest, "chaos_priest"),
                           (monk, "monk"), (dark_wizard, "dark_wizard"), (zamorak_mage, "zamorak_mage"),
                           (blue_wizard, "blue_wizard"), (lesser_demon, "lesser_demon"), (centaur, "centaur"),
                           (elite_black_knight, "black_knight_elite"), (justiciar, "white_knight_elite")):
            path = os.path.join(SKINS, "%s_%s.png" % (faction, name))
            skin(kind, faction).save(path)
            written.append(path)
    overlay().save(os.path.join(SKINS, "team_overlay.png"))
    icons(args.minecraft_jar).save(ICONS)
    os.makedirs(ITEMS, exist_ok=True)
    for name, art in ITEM_ART.items():
        path = os.path.join(ITEMS, name + ".png")
        pixel_art(art).save(path)
        written.append(path)
    if args.preview:
        os.makedirs(args.preview, exist_ok=True)
        for path in written + [ICONS]:
            image = Image.open(path)
            image.resize((image.width * 8, image.height * 8), Image.NEAREST).save(
                os.path.join(args.preview, os.path.basename(path)))
    print("Wrote %d skins and items and %d icons" % (len(written), len(ICON_ORDER)))


if __name__ == "__main__":
    main()
