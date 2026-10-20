"""Draws the textures of War of the Gods' own blocks: graystone bricks, the wooden door and the gods' banners.

    python3 tools/textures/blocks.py [--preview DIR]

Like the skins, they are drawn from code at Minecraft's 16x16 so they sit well beside its own blocks. Writes into
src/main/resources/assets/warofthegods/textures/blocks/ and textures/items/.
"""

import argparse
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "warofthegods")
BLOCKS = os.path.join(ROOT, "textures", "blocks")
ITEMS = os.path.join(ROOT, "textures", "items")

CLEAR = (0, 0, 0, 0)


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def vary(c, rng, amount):
    n = rng.randint(-amount, amount)
    return clamp((c[0] + n, c[1] + n, c[2] + n))


def scale(c, f):
    return clamp(tuple(v * f for v in c))


def new():
    return Image.new("RGBA", (16, 16), CLEAR)


def put(image, x, y, c):
    image.putpixel((x, y), tuple(c[:3]) + ((c[3],) if len(c) > 3 else (255,)))


# ---------------------------------------------------------------- graystone

GRAYSTONE = (82, 82, 86)
MORTAR = (46, 46, 50)


def graystone(kind, seed=7):
    """Two courses of long dressed blocks, dark grey, with deep joints: like the walls of Varrock. Mossy has green in
    its joints and on its tops, cracked has fissures through the blocks."""
    rng = random.Random(seed)
    image = new()
    # Course 1: y 0..7, joints at x 0 and 8; course 2: y 8..15, joints at x 4 and 12.
    for y in range(16):
        course = y // 8
        cy = y % 8
        for x in range(16):
            joints = (0, 8) if course == 0 else (4, 12)
            jx = (x - joints[0]) % 8
            if cy == 7 or jx == 7:
                c = vary(MORTAR, rng, 4)
            else:
                # Each block a little lighter or darker than its neighbours.
                block = (x + (4 if course else 0)) // 8 + course * 3
                base = scale(GRAYSTONE, 0.92 + 0.08 * ((block * 37) % 3))
                c = vary(base, rng, 7)
                if cy == 0 or jx == 0:
                    c = scale(c, 1.18)
                elif cy == 6 or jx == 6:
                    c = scale(c, 0.82)
            put(image, x, y, c)
    if kind == "mossy":
        moss = [(64, 84, 46), (78, 100, 52), (56, 74, 40)]
        for y in range(16):
            for x in range(16):
                r, g, b, _ = image.getpixel((x, y))
                joint = r < 56
                if (joint and rng.random() < 0.6) or (y % 8 in (0, 1) and rng.random() < 0.3) \
                        or rng.random() < 0.06:
                    put(image, x, y, vary(rng.choice(moss), rng, 6))
    elif kind == "cracked":
        for path in ([(2, 1), (3, 2), (3, 3), (4, 4), (4, 5)], [(10, 9), (11, 10), (11, 11), (10, 12), (11, 13)],
                     [(13, 2), (12, 3), (13, 4)], [(6, 10), (7, 11), (6, 12)]):
            for x, y in path:
                put(image, x, y, vary(MORTAR, rng, 3))
                if x + 1 < 16:
                    r, g, b, _ = image.getpixel((x + 1, y))
                    put(image, x + 1, y, scale((r, g, b), 1.12))
    return image


# ---------------------------------------------------------------- the wooden door

OAK = (104, 70, 40)
OAK_DARK = (70, 46, 26)
BRACE = (82, 54, 30)
IRON = (44, 44, 48)
IRON_LIGHT = (96, 96, 102)


def door_half(upper, seed):
    """Half of the plain plank door of Gielinor's towns: four upright boards with dark seams, a cross brace with
    nail heads, and on the lower half an iron ring for a handle."""
    rng = random.Random(seed)
    image = new()
    for y in range(16):
        for x in range(16):
            board = x // 4
            base = scale(OAK, (0.94, 1.04, 0.98, 1.06)[board])
            c = vary(base, rng, 6)
            # Grain: darker streaks running down each board.
            if (x * 7 + board * 3) % 4 == 1 and rng.random() < 0.7:
                c = scale(c, 0.86)
            if x % 4 == 3:
                c = vary(OAK_DARK, rng, 4)
            put(image, x, y, c)
    # A frame of darker wood round the edge.
    for i in range(16):
        put(image, 0, i, vary(OAK_DARK, rng, 4))
        put(image, 15, i, vary(OAK_DARK, rng, 4))
    if upper:
        for x in range(16):
            put(image, x, 0, vary(OAK_DARK, rng, 4))
    else:
        for x in range(16):
            put(image, x, 15, vary(OAK_DARK, rng, 4))
    # The cross brace, three high, with nails at each board.
    brace_y = 4 if upper else 9
    for y in range(brace_y, brace_y + 3):
        for x in range(1, 15):
            c = vary(BRACE, rng, 5)
            if y == brace_y:
                c = scale(c, 1.15)
            elif y == brace_y + 2:
                c = scale(c, 0.75)
            put(image, x, y, c)
    for x in (2, 6, 10, 13):
        put(image, x, brace_y + 1, IRON_LIGHT)
    if not upper:
        # The ring handle on its plate, near the top of the lower half.
        for x, y in ((11, 2), (12, 2), (13, 2), (11, 3), (13, 3)):
            put(image, x, y, IRON)
        for x, y in ((11, 4), (13, 4), (11, 5), (13, 5), (12, 6)):
            put(image, x, y, IRON)
        put(image, 12, 3, IRON_LIGHT)
        put(image, 11, 4, IRON_LIGHT)
    return image


def door_item():
    """The door in the hand, as Minecraft draws its own doors: the door narrowed to half its width."""
    upper, lower = door_half(True, 31), door_half(False, 32)
    image = new()
    for y in range(16):
        for x in range(8):
            src = upper if y < 8 else lower
            sx = min(15, x * 2 + 1 if x else 0)
            sy = (y % 8) * 2
            put(image, x + 4, y, src.getpixel((sx, sy)))
    return image


# ---------------------------------------------------------------- banners

STAR = [
    ".....##.....",
    ".....##.....",
    "....####....",
    "....####....",
    "..########..",
    "############",
    "############",
    "..########..",
    "....####....",
    "....####....",
    ".....##.....",
    ".....##.....",
]
STAR_SMALL = [
    "...##...",
    "...##...",
    "..####..",
    "########",
    "########",
    "..####..",
    "...##...",
    "...##...",
]
ZAMORAK = [
    "#..........#",
    "##........##",
    ".##..##..##.",
    "..########..",
    "...######...",
    "....####....",
    ".....##.....",
    "...######...",
    ".....##.....",
    ".....##.....",
    "....####....",
    ".....##.....",
]
ZAMORAK_SMALL = [
    "#......#",
    "##.##.##",
    ".######.",
    "..####..",
    "...##...",
    "..####..",
    "...##...",
    "...##...",
]

# Bandos's rune: a bar with its ends turned down, a diamond hung from it, and under that a staff crossed by a bar,
# its left end turned up and its right down, the staff's foot curling to the left.
BANDOS = [
    ".##########.",
    "#....##....#",
    "....#..#....",
    "...#....#...",
    "....#..#....",
    ".....##.....",
    ".....##.....",
    "#....##.....",
    "##########..",
    ".....##...#.",
    ".....##.....",
    "...###......",
]
BANDOS_SMALL = [
    "########",
    "#..##..#",
    "..#..#..",
    "...##...",
    "#..##...",
    "#######.",
    "...##..#",
    "..##....",
]

BLUE = (40, 66, 168)
WHITE = (236, 236, 230)
GOLD = (222, 180, 52)
BLACK = (28, 24, 26)
RED = (168, 28, 26)
DARK_RED = (104, 18, 18)
GREEN = (74, 106, 42)
BROWN = (106, 74, 42)
DARK_BROWN = (62, 42, 24)
ROD = (92, 64, 36)
ROD_END = (200, 164, 60)

# The banners: field, emblem, emblem outline, border, emblem shapes.
BANNERS = {
    "saradomin": (BLUE, WHITE, GOLD, GOLD, STAR, STAR_SMALL),
    "saradomin_inverted": (WHITE, BLUE, GOLD, GOLD, STAR, STAR_SMALL),
    "zamorak": (BLACK, RED, DARK_RED, RED, ZAMORAK, ZAMORAK_SMALL),
    "zamorak_inverted": (RED, BLACK, (60, 10, 10), BLACK, ZAMORAK, ZAMORAK_SMALL),
    "bandos": (GREEN, BROWN, DARK_BROWN, BROWN, BANDOS, BANDOS_SMALL),
    "bandos_inverted": (BROWN, GREEN, (36, 52, 20), GREEN, BANDOS, BANDOS_SMALL),
}


def cloth(image, field, border, rng, y0, y1, x0=1, x1=14):
    """Cloth from y0 to y1, with a border down both sides; folds show as faint darker columns."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c = vary(field, rng, 5)
            if x in (5, 10):
                c = scale(c, 0.9)
            if x in (x0, x1):
                c = vary(border, rng, 4)
            put(image, x, y, c)


def rod(image):
    for x in range(16):
        put(image, x, 0, scale(ROD, 1.15))
        put(image, x, 1, ROD)
    for x in (0, 15):
        put(image, x, 0, ROD_END)
        put(image, x, 1, scale(ROD_END, 0.8))


def emblem(image, shape, color, outline, ox, oy, rng):
    h, w = len(shape), len(shape[0])
    on = {(x, y) for y in range(h) for x in range(w) if shape[y][x] == "#"}
    for x, y in on:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in on:
                px, py = ox + x + dx, oy + y + dy
                if 1 <= px <= 14 and 0 <= py <= 15:
                    put(image, px, py, vary(outline, rng, 3))
    for x, y in on:
        c = vary(color, rng, 4)
        # Light from above.
        if (x, y - 1) not in on:
            c = scale(c, 1.1)
        put(image, ox + x, oy + y, c)


def forked(image, field, border, rng, y0):
    """The forked end of a banner from y0 to the bottom: a notch cut up into the middle."""
    for y in range(y0, 16):
        depth = y - y0
        for x in range(1, 15):
            notch = abs(x - 7.5) < depth * 1.15
            if notch:
                continue
            edge = abs(x - 7.5) < depth * 1.15 + 1.2 or x in (1, 14)
            put(image, x, y, vary(border if edge else field, rng, 4))


def banner(kind, piece):
    field, mark, outline, border, shape, small = BANNERS[kind]
    rng = random.Random(sum(map(ord, kind + piece)))
    image = new()
    if piece == "top":
        rod(image)
        cloth(image, field, border, rng, 2, 15)
        for x in range(1, 15):
            put(image, x, 2, vary(border, rng, 4))
    elif piece == "emblem":
        cloth(image, field, border, rng, 0, 15)
        emblem(image, shape, mark, outline, 2, 2, rng)
    elif piece == "bottom":
        cloth(image, field, border, rng, 0, 7)
        forked(image, field, border, rng, 8)
    else:
        rod(image)
        cloth(image, field, border, rng, 2, 11)
        emblem(image, small, mark, outline, 4, 3, rng)
        forked(image, field, border, rng, 12)
    return image


def generate(preview=None):
    os.makedirs(BLOCKS, exist_ok=True)
    os.makedirs(ITEMS, exist_ok=True)
    out = {}
    out["graystone_bricks"] = graystone("plain")
    out["graystone_bricks_mossy"] = graystone("mossy")
    out["graystone_bricks_cracked"] = graystone("cracked")
    out["wood_door_upper"] = door_half(True, 31)
    out["wood_door_lower"] = door_half(False, 32)
    for kind in BANNERS:
        for piece in ("top", "emblem", "bottom", "pennant"):
            out["banner_%s_%s" % (kind, piece)] = banner(kind, piece)
    for name, image in out.items():
        image.save(os.path.join(BLOCKS, name + ".png"))
    items = {"wood_door": door_item()}
    for kind in BANNERS:
        items["banner_" + kind] = banner(kind, "pennant")
    for name, image in items.items():
        image.save(os.path.join(ITEMS, name + ".png"))
    if preview:
        os.makedirs(preview, exist_ok=True)
        sheet = Image.new("RGBA", (len(out) * 18 * 4, 18 * 4), (40, 44, 52, 255))
        for i, image in enumerate(out.values()):
            sheet.paste(image.resize((64, 64), Image.NEAREST), (i * 72 + 4, 4), image.resize((64, 64), Image.NEAREST))
        sheet.save(os.path.join(preview, "block_textures.png"))
    print("Wrote %d block and %d item textures" % (len(out), len(items)))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", help="also draw a sheet of the textures, enlarged, into this folder")
    generate(parser.parse_args().preview)
