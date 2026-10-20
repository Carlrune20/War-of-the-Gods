"""Helmets as Old School RuneScape has them, drawn over the head in place of Minecraft's own box helmets: the full
helm, silver iron with a red band for Saradomin's teams (and anyone else), black for Zamorak's and blue-green for
runite, and the leather cowl, a hood with a mantle over the shoulders, tinted with the leather's dye as Minecraft's
leather is.

Each is placed as the player model's head is: its "head" part turns with the head, about the neck at y = 0, and the
cowl's "mantle" moves with the body. The full helm's three looks share one model, so only their textures differ.
"""

from boxmodel import Model, shade

# ---------------------------------------------------------------- the full helm
#
# A steel shell round the whole head, rounded on top, flared over the neck behind, with a red band round the brow and
# a T-shaped opening in front: a slit across the eyes and one down to the chin, a nose guard between the eyes.

SLIT = (34, 28, 28)
SLIT_DEEP = (20, 16, 16)
BAND = (160, 30, 26)
BAND_DARK = (116, 20, 18)
BAND_LIGHT = (196, 52, 40)

# Metal by look: the base color, lit by Minecraft's light on the top and sides as the shell turns.
METALS = {
    "full_helm": (184, 180, 178),
    "full_helm_zamorak": (66, 64, 66),
    "full_helm_runite": (82, 124, 138),
}


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


def full_helm(name):
    base = METALS[name]
    m = Model(name, 64, 64, seed=11)
    head = m.part("head")
    # The shell, a little bigger than Minecraft's helmet, from the crown to the chin.
    head.box(-5, -9, -5, 10, 10, 10, shell(base))
    # The rounded crown.
    head.box(-4, -10, -4, 8, 1, 8, metal(base))
    # The red band round the brow, standing a little proud of the shell.
    head.box(-5, -7, -5, 10, 1, 10, band, inflate=0.25)
    # The nose guard, standing out of the opening between the eyes.
    head.box(-1, -6, -5.6, 2, 3, 1, metal(shade(base, 1.08)))
    # The flare over the back of the neck and the sides of the jaw.
    head.box(-5, -2, -2, 10, 3, 7, metal(shade(base, 0.92)), inflate=0.45)
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


HELMETS = {
    "full_helm": lambda: full_helm("full_helm"),
    "full_helm_zamorak": lambda: full_helm("full_helm_zamorak"),
    "full_helm_runite": lambda: full_helm("full_helm_runite"),
    "leather_cowl": leather_cowl,
}

# The model each look's texture goes on.
MODEL_OF = {"full_helm": "full_helm", "full_helm_zamorak": "full_helm", "full_helm_runite": "full_helm",
            "leather_cowl": "leather_cowl"}
