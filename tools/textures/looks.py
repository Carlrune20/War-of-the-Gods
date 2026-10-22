"""Draws the looks units of a kind are told apart by: workers of every god, Saradomin's and Zamorak's warriors and
archers, and the blue and dark wizards. Each look is the kind's own skin (see generate.py) with another face, hair
and beard, a hat or hood or none, and its clothes in other shades; a blue wizard's look also has a pointed hat, a model
of its own drawn on its head.

    python3 tools/textures/looks.py [--preview FILE]

A unit takes one of its kind's looks at random when it is trained. The looks are written next to the kind's skin, as
its name and the look's number (saradomin_worker_1.png and on); the blue wizard's hat as models/wizard_hat.json with a
texture for each look (textures/models/wizard_hat_1.png and on). LOOKS in com.warofthegods.game.UnitLooks says how
many looks each kind has, and must say what this draws. generate.py runs this too. With --preview it draws every look
side by side into FILE.
"""

import argparse
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.append(os.path.join(HERE, "..", "models"))

import generate  # noqa: E402
from boxmodel import Model, render, shade  # noqa: E402

MODELS = os.path.join(generate.ROOT, "models")
TEXTURES = os.path.join(generate.ROOT, "textures", "models")

# ---------------------------------------------------------------- faces

# Skin tones, each with the darker one for under the chin, the nose and the like.
TONES = [((196, 146, 108), (168, 120, 86)), ((224, 180, 146), (194, 150, 118)), ((172, 122, 84), (142, 98, 66)),
         ((140, 96, 64), (112, 74, 48)), ((104, 70, 48), (80, 52, 36))]
HAIR = {"black": (32, 28, 26), "dark": (60, 42, 28), "brown": (96, 64, 38), "auburn": (120, 58, 34),
        "ginger": (184, 92, 42), "blond": (214, 182, 108), "grey": (146, 144, 140), "white": (228, 226, 220)}
EYES = {"blue": (60, 80, 150), "brown": (92, 58, 34), "green": (64, 120, 62), "grey": (110, 116, 124)}
MOUTH = (140, 80, 70)
WHITES = (245, 245, 245)


def mix(a, b, t):
    return tuple(int(round(x + (y - x) * t)) for x, y in zip(a, b))


def depth(face, x):
    """How far back a column of a side of the head is: 0 at its front."""
    return 7 - x if face == "right" else x


def head(look):
    """A face with the look's hair, beard and eyes, painted on the head."""
    tone, tone_dark = TONES[look.get("tone", 0)]
    hair = HAIR[look.get("hair", "brown")]
    beard = HAIR[look.get("beard_color", look.get("hair", "brown"))]
    style = look.get("style", "short")
    whiskers = look.get("beard")
    eyes = EYES[look.get("eyes", "blue")]
    stubble = mix(tone, beard, 0.45)
    paint = look.get("paint")

    def bearded(face, x, y):
        """The beard's color for a texel of the lower face, or None."""
        if not whiskers:
            return None
        if face == "front":
            mouth = y == 6 and x in (3, 4)
            if whiskers == "stubble":
                return stubble if y >= 6 and not mouth or y == 5 and x in (1, 2, 5, 6) else None
            if whiskers == "moustache":
                return beard if y == 5 and x in (1, 2, 5, 6) or y == 6 and x in (2, 5) else None
            if whiskers == "goatee":
                return beard if y == 5 and x in (2, 5) or y == 6 and x in (2, 5) or y == 7 and 2 <= x <= 5 else None
            if whiskers in ("full", "long"):
                return beard if (y >= 6 and not mouth) or y == 5 and x in (0, 1, 2, 5, 6, 7) else None
        if face in ("left", "right") and whiskers in ("full", "long", "stubble") and y >= 5 and depth(face, x) <= 3:
            return stubble if whiskers == "stubble" else beard
        if face == "bottom" and whiskers in ("full", "long"):
            return beard
        return None

    def fn(face, x, y, w, h):
        b = bearded(face, x, y)
        if b is not None:
            return b
        if face == "bottom":
            return tone_dark
        if face == "top":
            # The top face's last row meets the brow.
            if style == "bald":
                return tone if 1 <= x <= 6 and 1 <= y <= 6 else hair
            if style == "receding":
                return tone if y >= 5 and 1 <= x <= 6 else hair
            if style == "shaved":
                return mix(tone, hair, 0.5)
            if style == "mohawk":
                return hair if 3 <= x <= 4 else mix(tone, hair, 0.25)
            return hair
        if face == "back":
            if style == "long":
                return hair
            if style == "bald":
                return hair if 2 <= y <= 5 else tone
            if style == "shaved":
                return mix(tone, hair, 0.5) if y < 6 else tone_dark
            if style == "mohawk":
                return hair if 3 <= x <= 4 and y < 6 else mix(tone, hair, 0.25) if y < 6 else tone_dark
            if style == "cropped":
                return hair if y < 5 else tone_dark
            return hair if y < 6 else tone_dark
        if face in ("left", "right"):
            d = depth(face, x)
            if style == "long":
                return hair if y < 3 or d >= 3 else tone
            if style == "bald":
                return hair if 2 <= y <= 4 and d >= 4 else tone
            if style in ("shaved", "mohawk"):
                return mix(tone, hair, 0.5 if style == "shaved" else 0.25) if y < 4 and d >= 2 else tone
            if style == "receding":
                return hair if y < 4 and d >= 3 else tone
            if style == "cropped":
                return hair if y < 2 or y < 4 and d >= 5 else tone
            return hair if y < 3 or y < 5 and d >= 5 else tone
        # The front.
        if style in ("short", "long") and (y < 2 or y == 2 and x in (0, 7)):
            return hair
        if style == "cropped" and y < 1:
            return hair
        if style == "mohawk" and y < 1 and 3 <= x <= 4:
            return hair
        if y == 3 and x in (1, 2, 5, 6) and style not in ("bald", "receding", "shaved"):
            # Brows, a shade of the hair.
            return mix(hair, tone, 0.35) if (x in (1, 6)) == (look.get("brows") == "low") else tone
        if y == 4 and x in (1, 2, 5, 6):
            return WHITES if x in (1, 6) else eyes
        if paint and y in (4, 5) and x in (0, 7) or paint and y == 5 and x in (1, 6):
            return paint
        if y == 5 and x in (3, 4):
            return tone_dark
        if y == 6 and x in (3, 4):
            return MOUTH
        return tone

    return fn


def hair_over(look, below=None):
    """The hat layer: what the kind wears over its head, and long hair falling round the head from under it, or a
    mohawk's crest."""
    hair = HAIR[look.get("hair", "brown")]
    style = look.get("style", "short")

    def fn(face, x, y, w, h):
        worn = below(face, x, y, w, h) if below else None
        if worn is not None:
            return worn
        if style == "long":
            if face in ("back", "top") or face in ("left", "right") and depth(face, x) >= 4:
                return hair
        if style == "mohawk" and face == "top" and 3 <= x <= 4:
            return hair
        return None

    return fn


def headband(color):
    def fn(face, x, y, w, h):
        return color if face not in ("top", "bottom") and y == 2 else None
    return fn


def hood(color, trim=None):
    """A hood framing the face, as the workers' and archers' are."""
    def fn(face, x, y, w, h):
        if face == "bottom":
            return None
        if face == "front":
            if y < 2 or x in (0, 7):
                return trim if trim and y == 1 and 0 < x < 7 else color
            return None
        if face in ("left", "right") and y > 6:
            return None
        return color
    return fn


def long_beard(look, body):
    """A long beard hanging over the breast, drawn on the body's front."""
    beard = HAIR[look.get("beard_color", look.get("hair", "brown"))]

    def fn(face, x, y, w, h):
        if face == "front" and (y <= 2 and 2 <= x <= 5 or y in (3, 4) and 3 <= x <= 4):
            return beard
        return body(face, x, y, w, h)

    return fn


def dressed(kind, faction, look, skin_colors=((generate.SKIN, generate.SKIN_DARK),)):
    """The kind's skin as the look has it: its face, hair and beard, its hat, and its clothes recolored."""
    parts = kind(faction)
    tone, tone_dark = TONES[look.get("tone", 0)]
    colors = {}
    for light, dark in skin_colors:
        colors[light], colors[dark] = tone, tone_dark
    colors.update(look.get("colors", {}))

    def recolored(fn):
        def wrapped(face, x, y, w, h):
            color = fn(face, x, y, w, h)
            return colors.get(color, color)
        return wrapped

    for name in ("body", "arm", "leg"):
        parts[name] = recolored(parts[name])
    parts["head"] = head(look)
    over = look.get("hat", "kind")
    parts["hat"] = hair_over(look, recolored(parts["hat"]) if over == "kind" else over)
    if look.get("beard") == "long":
        parts["body"] = long_beard(look, parts["body"])
    return parts


# ---------------------------------------------------------------- the looks

SARADOMIN_BLUE = (52, 84, 168)
ZAMORAK_RED = (150, 26, 24)
WORKERS = {
    "saradomin": [
        dict(hair="brown", beard="full", tone=0),
        dict(hair="blond", style="long", tone=1, hat=None, colors={(196, 176, 136): (150, 156, 104)}),
        dict(hair="grey", style="bald", beard="full", tone=2, hat=None, eyes="brown"),
        dict(hair="ginger", beard="moustache", tone=1, eyes="green", colors={(196, 176, 136): (210, 192, 150)}),
        dict(hair="black", style="cropped", beard="stubble", tone=3, hat=None, eyes="brown",
             colors={(196, 176, 136): (164, 156, 140)}),
        dict(hair="brown", style="receding", beard="goatee", tone=0, eyes="grey"),
        dict(hair="white", style="bald", beard="long", tone=0, hat=None, colors={(122, 88, 56): (96, 70, 46)}),
        dict(hair="dark", style="long", tone=4, hat=headband(SARADOMIN_BLUE), eyes="brown",
             colors={(196, 176, 136): (176, 150, 112)}),
    ],
    "zamorak": [
        dict(hair="black", tone=0),
        dict(hair="dark", beard="full", tone=2, hat=None, eyes="brown", colors={(88, 74, 64): (96, 64, 56)}),
        dict(hair="grey", style="bald", beard="stubble", tone=0, hat=None, colors={(88, 74, 64): (66, 62, 64)}),
        dict(hair="auburn", style="long", tone=1, hat=None, eyes="green"),
        dict(hair="black", style="shaved", beard="goatee", tone=3, hat=None, eyes="brown",
             colors={(88, 74, 64): (74, 58, 50)}),
        dict(hair="brown", beard="moustache", tone=1, colors={(88, 74, 64): (104, 86, 70)}),
        dict(hair="white", style="receding", beard="full", tone=2, hat=headband(ZAMORAK_RED), eyes="grey"),
        dict(hair="black", style="cropped", tone=4, hat=None, eyes="brown", colors={(88, 74, 64): (60, 50, 46)}),
    ],
    "armadyl": [
        dict(hair="brown", tone=0),
        dict(hair="blond", style="long", tone=1, eyes="blue"),
        dict(hair="grey", style="receding", beard="full", tone=0, hat=None, eyes="grey"),
        dict(hair="black", beard="goatee", tone=3, eyes="brown"),
        dict(hair="ginger", style="cropped", beard="stubble", tone=1, hat=None, eyes="green"),
        dict(hair="white", style="bald", beard="long", tone=2, hat=None),
        dict(hair="dark", style="long", tone=4, hat=None, eyes="brown"),
        dict(hair="auburn", beard="moustache", tone=0),
    ],
}
SLAVES = [
    dict(hair="dark", style="shaggy", tone=0),
    dict(hair="black", style="shaved", beard="stubble", tone=3, eyes="brown"),
    dict(hair="brown", style="long", beard="full", tone=2),
    dict(hair="grey", style="bald", beard="full", tone=1, eyes="grey"),
    dict(hair="ginger", style="shaggy", beard="stubble", tone=1, eyes="green"),
    dict(hair="black", style="long", tone=4, eyes="brown"),
    dict(hair="blond", style="cropped", beard="goatee", tone=0),
    dict(hair="white", style="receding", beard="long", tone=2, eyes="grey"),
]
SARADOMIN_SHIRT = (52, 86, 176)
ZAMORAK_SHIRT = (156, 30, 28)
WARRIORS = {
    "saradomin": [
        dict(hair="brown", tone=0),
        dict(hair="blond", beard="full", tone=1, eyes="blue"),
        dict(hair="black", style="cropped", beard="stubble", tone=3, eyes="brown",
             colors={SARADOMIN_SHIRT: (40, 68, 150), (36, 60, 132): (28, 48, 108)}),
        dict(hair="ginger", style="long", tone=1, eyes="green", hat=headband((230, 230, 224))),
        dict(hair="grey", style="receding", beard="moustache", tone=0, eyes="grey",
             colors={SARADOMIN_SHIRT: (74, 108, 192), (36, 60, 132): (50, 80, 156)}),
        dict(hair="dark", beard="goatee", tone=2, eyes="brown"),
        dict(hair="brown", style="bald", beard="full", tone=4, eyes="brown",
             colors={SARADOMIN_SHIRT: (40, 68, 150), (36, 60, 132): (28, 48, 108)}),
        dict(hair="auburn", style="short", beard="stubble", tone=1, eyes="blue"),
    ],
    "zamorak": [
        dict(hair="black", tone=0),
        dict(hair="black", style="mohawk", tone=0, eyes="brown", paint=(30, 26, 26)),
        dict(hair="dark", style="shaved", beard="full", tone=2, eyes="brown",
             colors={ZAMORAK_SHIRT: (126, 22, 22), (112, 20, 20): (90, 14, 14)}),
        dict(hair="grey", style="bald", beard="goatee", tone=1, eyes="grey", paint=(130, 20, 20)),
        dict(hair="auburn", style="long", beard="stubble", tone=1, eyes="green", hat=headband((30, 26, 26))),
        dict(hair="black", style="cropped", beard="moustache", tone=3, eyes="brown",
             colors={ZAMORAK_SHIRT: (172, 48, 30), (112, 20, 20): (124, 30, 20)}),
        dict(hair="white", style="mohawk", tone=4, eyes="brown"),
        dict(hair="brown", style="receding", beard="full", tone=0, eyes="blue",
             colors={ZAMORAK_SHIRT: (126, 22, 22), (112, 20, 20): (90, 14, 14)}),
    ],
}
ARCHERS = {
    "saradomin": [
        dict(hair="brown", tone=0),
        dict(hair="blond", style="long", tone=1, hat=None),
        dict(hair="dark", beard="goatee", tone=2, eyes="brown",
             colors={(112, 82, 50): (84, 104, 62), (84, 58, 34): (60, 76, 42)}),
        dict(hair="ginger", style="short", beard="full", tone=1, hat=None, eyes="green"),
        dict(hair="black", beard="stubble", tone=3, eyes="brown", hat=hood((30, 54, 120), (220, 220, 214))),
        dict(hair="grey", style="receding", beard="moustache", tone=0, hat=None, eyes="grey",
             colors={(112, 82, 50): (128, 98, 64)}),
        dict(hair="brown", style="cropped", tone=4, eyes="brown",
             colors={(112, 82, 50): (84, 104, 62), (84, 58, 34): (60, 76, 42)}),
        dict(hair="auburn", style="long", beard="stubble", tone=1, hat=hood((54, 88, 168), (220, 220, 214))),
    ],
    "zamorak": [
        dict(hair="black", tone=0),
        dict(hair="black", style="shaved", beard="goatee", tone=2, hat=None, eyes="brown"),
        dict(hair="dark", tone=3, hat=hood((30, 26, 28), (140, 22, 22)), eyes="brown"),
        dict(hair="white", style="long", tone=1, hat=None, eyes="grey", paint=(130, 20, 20)),
        dict(hair="auburn", beard="full", tone=0, hat=hood((54, 22, 22)), eyes="green"),
        dict(hair="black", style="mohawk", beard="stubble", tone=4, hat=None, eyes="brown", paint=(30, 26, 26)),
        dict(hair="brown", tone=1, colors={(46, 38, 36): (60, 46, 40), (96, 22, 20): (70, 30, 26)}),
        dict(hair="grey", style="bald", beard="moustache", tone=2, hat=None, eyes="grey"),
    ],
}
DARK_ROBE = (32, 30, 36)
DARK_TRIM = (58, 56, 62)
DARK_HOOD = (26, 24, 30)
DARK_WIZARDS = [
    dict(hair="black", beard="full", beard_color="grey", tone=0),
    dict(hair="grey", style="long", beard="long", tone=0, hat=None, eyes="grey"),
    dict(hair="black", style="bald", beard="goatee", tone=2, hat=None, eyes="brown"),
    dict(hair="white", style="receding", beard="long", tone=1, hat=None, eyes="grey",
         colors={DARK_ROBE: (24, 24, 26), DARK_TRIM: (46, 44, 48)}),
    dict(hair="dark", beard="moustache", tone=3, eyes="brown", colors={DARK_ROBE: (38, 30, 30), DARK_HOOD: (32, 24, 24)}),
    dict(hair="black", style="long", tone=1, hat=None, eyes="green", colors={DARK_ROBE: (24, 24, 26)}),
    dict(hair="grey", beard="stubble", tone=2, eyes="grey", colors={DARK_HOOD: (20, 18, 22)}),
    dict(hair="white", style="bald", beard="full", tone=4, hat=None, eyes="brown",
         colors={DARK_ROBE: (38, 30, 30), DARK_TRIM: (66, 58, 58)}),
]
BLUE_ROBE = (44, 72, 170)
BLUE_GOLD = (220, 200, 90)
# A blue wizard's robe and its trim, gold or none; its hat is made to match.
BLUE_WIZARDS = [
    dict(hair="white", beard="long", tone=0, robe=BLUE_ROBE, gold=True),
    dict(hair="grey", style="receding", beard="full", tone=1, robe=(36, 58, 140), gold=False, eyes="grey"),
    dict(hair="brown", style="long", beard="long", tone=0, robe=(58, 92, 190), gold=True),
    dict(hair="black", style="short", beard="goatee", tone=3, robe=BLUE_ROBE, gold=False, eyes="brown"),
    dict(hair="ginger", style="short", beard="full", tone=1, robe=(30, 48, 120), gold=True, eyes="green"),
    dict(hair="white", style="bald", beard="long", tone=2, robe=(58, 92, 190), gold=False),
    dict(hair="blond", style="long", beard="moustache", tone=1, robe=(36, 58, 140), gold=True),
    dict(hair="grey", style="short", beard="long", beard_color="white", tone=4, robe=(30, 48, 120), gold=False,
         eyes="brown"),
]
# What each kind's looks are, by the name of its skins, and for the workers by faction. The slaves are Bandos's
# workers.
KINDS = {
    "worker": (generate.worker, WORKERS),
    "slave": (generate.slave, {"bandos": SLAVES}),
    "soldier": (generate.warrior, WARRIORS),
    "archer": (generate.archer, ARCHERS),
    "dark_wizard": (generate.dark_wizard, {f: DARK_WIZARDS for f in generate.FACTIONS}),
    "blue_wizard": (None, {f: BLUE_WIZARDS for f in generate.FACTIONS}),
}


def blue_wizard(look):
    """A blue wizard's robe as the look has it, gold-trimmed or plain, with no hat on the skin: his hat is a model."""
    robe = look["robe"]
    trim = BLUE_GOLD if look["gold"] else shade(robe, 0.72)

    def kind(faction):
        parts = generate.robed(robe, trim, hair=(0, 0, 0))
        parts["hat"] = lambda face, x, y, w, h: None
        return parts

    return kind


def skin_of(name, faction, look):
    """The look's skin, as an image."""
    kind, _ = KINDS[name]
    if name == "worker" and faction == "armadyl":
        # Armadyl's workers are his followers.
        kind = generate.follower
    if name == "blue_wizard":
        kind = blue_wizard(look)
    tones = ((generate.SKIN, generate.SKIN_DARK),)
    if name == "slave":
        tones += (((186, 142, 108), (150, 110, 80)),)
    parts = dressed(kind, faction, look, tones)
    if name == "slave" and look.get("style") == "shaggy":
        # The slave's own messy hair and tired eyes, in the look's colors.
        own = generate.slave(faction)["head"]
        hair = HAIR[look["hair"]]
        tone, tone_dark = TONES[look.get("tone", 0)]
        swap = {(64, 50, 38): hair, (82, 64, 48): shade(hair, 1.2), (186, 142, 108): tone, (150, 110, 80): tone_dark}
        beard = head(look)

        def shaggy(face, x, y, w, h):
            if look.get("beard") and face == "front" and y >= 5:
                return beard(face, x, y, w, h)
            color = own(face, x, y, w, h)
            return swap.get(color, color)

        parts["head"] = shaggy
    image = Image.new("RGBA", (64, parts.get("height", 32)), (0, 0, 0, 0))
    rng = random.Random("%s_%s_%s" % (faction, name, sorted(look.items(), key=str)))
    generate.paint(image, generate.HEAD, parts["head"], rng, noise=4)
    generate.paint(image, generate.HAT, parts["hat"], rng, noise=5)
    generate.paint(image, generate.BODY, parts["body"], rng)
    generate.paint(image, generate.ARM, parts["arm"], rng)
    generate.paint(image, generate.LEG, parts["leg"], rng)
    return image


# ---------------------------------------------------------------- the blue wizard's hat


def wizard_hat(look):
    """A wide-brimmed hat with a tall cone that bends back toward its tip, in the robe's blue, with a band of gold or
    a darker blue. It sits on the player model's head, which turns about the neck."""
    robe = look["robe"]
    band = BLUE_GOLD if look["gold"] else shade(robe, 0.7)

    def cloth(color):
        def fn(face, x, y, w, h):
            if face == "top":
                return shade(color, 1.1)
            if face == "bottom":
                return shade(color, 0.6)
            return shade(color, 1.0 - 0.12 * y / max(1, h - 1))
        return fn

    def banded(face, x, y, w, h):
        if face in ("top", "bottom"):
            return shade(robe, 0.9)
        return band

    model = Model("wizard_hat", 64, 64, seed=3)
    hat = model.part("head")
    hat.box(-5.5, -9, -5.5, 11, 1, 11, cloth(robe))
    hat.box(-4.5, -10.5, -4.5, 9, 2, 9, banded)
    hat.box(-4, -12.5, -4, 8, 2, 8, cloth(robe))
    tip = hat.child("hat_tip", (0, -12.5, 0), (-0.22, 0, 0))
    tip.box(-3, -3, -3, 6, 3, 6, cloth(robe))
    tip.box(-2, -6, -2, 4, 3, 4, cloth(robe))
    point = tip.child("hat_point", (0, -6, 0), (-0.4, 0, 0))
    point.box(-1.5, -2, -1.5, 3, 2, 3, cloth(shade(robe, 0.95)))
    point.box(-0.5, -4, -0.5, 1, 2, 1, cloth(shade(robe, 0.9)))
    return model


# ---------------------------------------------------------------- writing


def written():
    """Every look's skin, as (faction, kind's skin name, number, look)."""
    for name, (_, by_faction) in KINDS.items():
        for faction, looks in by_faction.items():
            for number, look in enumerate(looks, 1):
                yield faction, name, number, look


def write():
    paths = []
    for faction, name, number, look in written():
        path = os.path.join(generate.SKINS, "%s_%s_%d.png" % (faction, name, number))
        skin_of(name, faction, look).save(path)
        paths.append(path)
    for number, look in enumerate(BLUE_WIZARDS, 1):
        model = wizard_hat(look)
        texture, _ = model.paint()
        texture.save(os.path.join(TEXTURES, "wizard_hat_%d.png" % number))
        if number == 1:
            with open(os.path.join(MODELS, "wizard_hat.json"), "w") as file:
                file.write(model.to_json() + "\n")
    return paths


def player():
    """The player model, as Minecraft's ModelBiped is, to draw the skins on."""
    model = Model("player", 64, 32)
    for name, pivot, box, uv, inflate, mirror in (
            ("head", (0, 0, 0), (-4, -8, -4, 8, 8, 8), (0, 0), 0.0, False),
            ("hat", (0, 0, 0), (-4, -8, -4, 8, 8, 8), (32, 0), 0.5, False),
            ("body", (0, 0, 0), (-4, 0, -2, 8, 12, 4), (16, 16), 0.0, False),
            ("right_arm", (-5, 2, 0), (-3, -2, -2, 4, 12, 4), (40, 16), 0.0, False),
            ("left_arm", (5, 2, 0), (-1, -2, -2, 4, 12, 4), (40, 16), 0.0, True),
            ("right_leg", (-1.9, 12, 0), (-2, 0, -2, 4, 12, 4), (0, 16), 0.0, False),
            ("left_leg", (1.9, 12, 0), (-2, 0, -2, 4, 12, 4), (0, 16), 0.0, True)):
        part = model.part(name, pivot)
        part.box(*box, paint=None, inflate=inflate, mirror=mirror)
        part.boxes[-1].u, part.boxes[-1].v = uv
    return model


def preview(path, yaw=20):
    """Every look, front and back, as the game draws the player model, a row to a kind."""
    rows = {}
    for faction, name, number, look in written():
        # The wizards look the same whichever god's team trains them.
        if name.endswith("wizard") and faction != "saradomin":
            continue
        rows.setdefault((faction, name), []).append(number)
    size = 200
    width = max(len(v) for v in rows.values())
    sheet = Image.new("RGB", (size * width, size * len(rows)), (48, 54, 66))
    for row, ((faction, name), numbers) in enumerate(rows.items()):
        for number in numbers:
            skin = Image.open(os.path.join(generate.SKINS, "%s_%s_%d.png" % (faction, name, number)))
            if name == "blue_wizard":
                image = render_with_hat(skin, number, size, yaw)
            else:
                image = render(player(), skin, size=size, yaw=yaw, pitch=6, background=(48, 54, 66))
            sheet.paste(image, ((number - 1) * size, row * size))
    sheet.save(path)


def render_with_hat(skin, number, size, yaw):
    """The wizard drawn with his hat on: both models in one, the hat's texture below the skin's."""
    hat = wizard_hat(BLUE_WIZARDS[number - 1])
    texture, _ = hat.paint()
    combined = Image.new("RGBA", (64, 96), (0, 0, 0, 0))
    combined.paste(skin.convert("RGBA"), (0, 0))
    combined.paste(texture, (0, 32))
    both = player()
    both.tex_h = 96
    for part in hat.parts():
        for box in part.boxes:
            box.v += 32
    both.roots.extend(hat.roots)
    return render(both, combined, size=size, yaw=yaw, pitch=6, background=(48, 54, 66))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", help="also draw every look into this PNG")
    args = parser.parse_args()
    paths = write()
    print("Wrote %d looks" % len(paths))
    if args.preview:
        preview(args.preview)


if __name__ == "__main__":
    main()
