"""Armor as Old School RuneScape has it, drawn in place of Minecraft's own: the full helm with a red band, and the
platebody, platelegs and boots, each in four looks (silver iron, dark iron, blue-green runite and red dragon) that share one
model, so only their textures differ; and the leather cowl, a hood with a mantle over the shoulders, tinted with the
leather's dye as Minecraft's leather is.

Each part is placed as the player model's part of the same name is (head, body, arms and legs, about the same
pivots), and turns and moves with it. The inventory icons are made from RuneScape's own pictures in icons/.
"""

import os

from PIL import Image

from boxmodel import Model, shade

# ---------------------------------------------------------------- the full helm
#
# A steel shell round the whole head, rounded on top, with a red band round the brow and
# a T-shaped opening in front: a slit across the eyes and one down to the chin, a nose guard between the eyes.

SLIT = (34, 28, 28)
SLIT_DEEP = (20, 16, 16)
BAND = (160, 30, 26)
BAND_DARK = (116, 20, 18)
BAND_LIGHT = (196, 52, 40)

# Metal by look: the plates' color and the darker one of the platebody's shoulders.
METALS = {
    "": ((184, 180, 178), (122, 118, 118)),
    "_dark": ((70, 68, 70), (44, 42, 44)),
    "_runite": ((86, 128, 142), (50, 84, 100)),
    "_dragon": ((150, 30, 26), (92, 16, 14)),
}
LOOKS = tuple(METALS)
BELT = (92, 20, 16)
BOOT_LEATHER = (104, 76, 46)


def metal(base):
    """Flat facets as RuneScape draws them: lighter toward the top and the front edges, darker underneath."""

    def fn(face, x, y, w, h):
        if face == "top":
            return shade(base, 1.12)
        if face == "bottom":
            return shade(base, 0.6)
        # A faint edge where the facets meet.
        if x == 0 or x == w - 1:
            return shade(base, 0.86)
        return shade(base, 1.04 - 0.16 * y / max(1, h - 1))

    return fn


def shell(base):
    """The helm's shell: on its front the T-shaped opening, the band painted where it runs over the face."""
    plain = metal(base)

    def fn(face, x, y, w, h):
        if face == "front":
            middle = (w // 2 - 1, w // 2)
            # The slit across the eyes, the nose guard hanging into it, and the slit down to the chin.
            if 4 <= y <= 5 and 1 <= x <= w - 2 and x not in middle:
                return SLIT_DEEP if y == 4 else SLIT
            if y >= 6 and x in middle:
                return SLIT_DEEP if y == 6 else SLIT
        return plain(face, x, y, w, h)

    return fn


def band(face, x, y, w, h):
    if face == "top":
        return BAND_LIGHT
    if face == "bottom":
        return BAND_DARK
    return BAND_LIGHT if y == 0 and face == "front" else BAND


def full_helm(look):
    base = METALS[look][0]
    m = Model("full_helm" + look, 64, 64, seed=11)
    head = m.part("head")
    # The shell, a little bigger than Minecraft's helmet, from the crown to the chin.
    head.box(-5, -9, -5, 10, 10, 10, shell(base))
    # The rounded crown.
    head.box(-4, -10, -4, 8, 1, 8, metal(base))
    # The red band round the brow, standing a little proud of the shell.
    head.box(-5, -7, -5, 10, 1, 10, band, inflate=0.25)
    # The nose guard, standing out of the opening between the eyes.
    head.box(-1, -6, -5.6, 2, 3, 1, metal(shade(base, 1.08)))
    return m


# ---------------------------------------------------------------- the platebody, platelegs and boots
#
# A breastplate with a ridge down the middle and a lip at its foot, great dark pauldrons over the shoulders and
# plated sleeves; plated legs with knee guards under a dark red belt; and boots with a leather cuff over a steel foot.


def leather(base):
    def fn(face, x, y, w, h):
        if face == "top":
            return shade(base, 1.15)
        if face == "bottom":
            return shade(base, 0.7)
        return shade(base, 0.85) if y == h - 1 else base

    return fn


def breastplate(base):
    plain = metal(base)

    def fn(face, x, y, w, h):
        if face == "front" and x in (w // 2 - 1, w // 2):
            # The ridge down the middle, lit on one side.
            return shade(base, 1.14 if x == w // 2 - 1 else 0.94)
        return plain(face, x, y, w, h)

    return fn


def plate(look):
    base, dark = METALS[look]
    legs = shade(base, 0.94)
    m = Model("plate" + look, 128, 64, seed=13)
    body = m.part("body")
    body.box(-5, -1, -3, 10, 12, 6, breastplate(base))
    body.box(-5, 10, -3, 10, 2, 6, metal(shade(base, 1.06)), inflate=0.35)
    belt = m.part("belt")
    belt.box(-4, 9, -2, 8, 3, 4, leather(BELT), inflate=0.75)
    for side, name in ((-1, "right"), (1, "left")):
        arm = m.part(name + "_arm", pivot=(side * 5, 2, 0))
        x = -3 if side < 0 else -1
        arm.box(x, -2, -2, 4, 10, 4, metal(base), inflate=0.6, mirror=side > 0)
        arm.box(x - 1 if side < 0 else x, -3, -3, 6, 4, 6, metal(dark), inflate=0.2, mirror=side > 0)
        leg = m.part(name + "_leg", pivot=(side * 1.9, 12, 0))
        leg.box(-2, 0, -2, 4, 9, 4, metal(legs), inflate=0.5, mirror=side > 0)
        leg.box(-2.5, 4, -2.5, 5, 2, 5, metal(shade(base, 1.04)), inflate=0.2, mirror=side > 0)
        boot = m.part(name + "_boot", pivot=(side * 1.9, 12, 0))
        boot.box(-2, 7, -2, 4, 2, 4, leather(BOOT_LEATHER), inflate=0.8, mirror=side > 0)
        boot.box(-2, 9, -3, 4, 3, 5, metal(legs), inflate=0.5, mirror=side > 0)
    return m


# ---------------------------------------------------------------- the leather cowl
#
# A hood drawn up over the head, peaked at the crown, open round the face, with stitched seams, and a mantle over the
# shoulders. Light leather, as Minecraft's is, so the dye tints it.

HIDE = (222, 214, 204)
HIDE_DARK = (176, 166, 154)
HIDE_LIGHT = (240, 234, 226)
STITCH = (120, 110, 100)


def hood(face, x, y, w, h):
    if face == "front":
        # Open round the face, a hem of darker leather round the opening.
        corner = x in (2, w - 3) and y == 2
        if 2 <= x <= w - 3 and 2 <= y <= h - 2 and not corner:
            return None
        if 1 <= x <= w - 2 and 1 <= y <= h - 1:
            return HIDE_DARK
    if face == "top":
        # A seam down the middle, front to back.
        return STITCH if x == w // 2 and y % 2 == 0 else HIDE_LIGHT
    if face == "bottom":
        return HIDE_DARK
    if face == "back" and x == w // 2 and y % 2 == 0:
        return STITCH
    return HIDE if y < h - 2 else HIDE_DARK


def peak(face, x, y, w, h):
    if face == "top":
        return STITCH if x == w // 2 and y % 2 == 0 else HIDE_LIGHT
    return HIDE


def mantle(face, x, y, w, h):
    if face == "top":
        return HIDE_LIGHT
    if face == "bottom":
        return HIDE_DARK
    # A stitched hem along its lower edge, and a notch down the front.
    if face == "front" and w // 2 - 1 <= x <= w // 2 and y >= 1:
        return None
    return HIDE_DARK if y == h - 1 else HIDE


def leather_cowl():
    m = Model("leather_cowl", 64, 64, seed=12)
    head = m.part("head")
    head.box(-5, -9, -5, 10, 10, 10, hood)
    head.box(-4, -10, -4, 8, 1, 8, peak)
    # The tip of the hood, falling back from the crown.
    head.box(-2, -10.5, 1, 4, 2, 4, peak, inflate=0.1)
    body = m.part("mantle")
    body.box(-5, 0, -3, 10, 3, 6, mantle, inflate=0.7)
    return m


# Each look by texture name, and the model it goes on.
ARMOR = {"leather_cowl": (leather_cowl, "leather_cowl")}
for _look in LOOKS:
    ARMOR["full_helm" + _look] = (lambda look=_look: full_helm(look), "full_helm")
    ARMOR["plate" + _look] = (lambda look=_look: plate(look), "plate")


# ---------------------------------------------------------------- icons
#
# RuneScape's own pictures of each piece, from icons/, shrunk to an inventory icon.

ICONS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "icons")
ICON_SIZE = 32
PIECES = ("full_helm", "platebody", "platelegs", "boots")


def picture(metal_name, piece):
    """RuneScape's picture of a piece in iron, black (the mod's dark iron) or rune."""
    return Image.open(os.path.join(ICONS, "%s_%s.png" % (metal_name, piece))).convert("RGBA")


def icon(image):
    """The picture shrunk to fit the icon, kept in proportion and centred, its edge left hard as Minecraft's are."""
    image = image.crop(image.getbbox())
    scale = ICON_SIZE / max(image.width, image.height)
    size = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
    small = image.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")
    out = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
    out.paste(small, ((ICON_SIZE - size[0]) // 2, (ICON_SIZE - size[1]) // 2))
    pixels = out.load()
    for y in range(ICON_SIZE):
        for x in range(ICON_SIZE):
            r, g, b, a = pixels[x, y]
            pixels[x, y] = (r, g, b, 255) if a >= 128 else (0, 0, 0, 0)
    return out
