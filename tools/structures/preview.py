"""Draws a structure as an isometric picture with flat block colours, to look at without starting the game."""

from PIL import Image, ImageDraw

COLORS = {
    1: (125, 125, 125), 2: (95, 160, 60), 3: (134, 96, 67), 4: (110, 110, 110), 5: (160, 130, 80),
    9: (50, 90, 200), 12: (220, 210, 160), 13: (135, 128, 125), 14: (220, 190, 70), 15: (190, 150, 120),
    16: (60, 60, 60), 17: (100, 80, 50), 20: (200, 230, 240), 21: (40, 60, 160), 22: (30, 60, 170),
    30: (230, 230, 230), 31: (90, 150, 60), 32: (140, 100, 50), 41: (250, 220, 60), 42: (220, 220, 220), 44: (160, 160, 160),
    47: (140, 100, 60), 48: (90, 110, 80), 49: (25, 18, 40), 50: (255, 220, 120), 51: (255, 140, 30), 54: (150, 110, 50),
    56: (100, 220, 220), 58: (130, 90, 50), 59: (200, 180, 70), 60: (110, 70, 40), 61: (100, 100, 100),
    64: (130, 100, 60), 65: (150, 120, 70), 66: (150, 140, 120), 67: (110, 110, 110), 71: (190, 190, 190),
    73: (160, 60, 60), 76: (220, 40, 30), 85: (150, 120, 70), 86: (220, 130, 30), 87: (110, 40, 40),
    88: (85, 65, 50), 89: (250, 220, 130), 91: (240, 160, 40), 98: (120, 120, 120), 101: (90, 90, 95),
    102: (210, 230, 240), 107: (150, 120, 70), 109: (120, 120, 120), 112: (50, 25, 30), 113: (50, 25, 30),
    114: (50, 25, 30), 115: (160, 30, 30), 118: (60, 60, 60), 126: (160, 130, 80), 134: (100, 75, 45),
    139: (110, 110, 110), 141: (230, 130, 40), 142: (200, 170, 70), 144: (220, 220, 210), 145: (60, 60, 60),
    152: (180, 20, 10), 155: (236, 232, 224), 156: (236, 232, 224), 162: (60, 45, 30), 170: (200, 170, 40),
    173: (20, 20, 20), 108: (150, 70, 55), 45: (150, 70, 55), 164: (65, 45, 25), 156: (236, 232, 224),
    # The mod's own blocks: graystone and its slabs and stairs, the wooden door, the banners, and fire.
    200: (78, 78, 82), 201: (78, 78, 82), 202: (78, 78, 82), 203: (78, 78, 82), 204: (104, 70, 40),
    205: (42, 66, 168), 206: (230, 230, 224), 207: (28, 24, 26), 208: (168, 28, 26), 209: (255, 140, 30),
    210: (74, 106, 42), 211: (106, 74, 42), 212: (230, 230, 224), 213: (62, 116, 206),
    # Forinthry's: charred earth, ash, scorched stone and ember rock, and lava.
    214: (46, 38, 34), 215: (132, 128, 124), 216: (58, 54, 56), 217: (120, 60, 30), 10: (230, 100, 20), 11: (230, 100, 20),
}
WOOL = {0: (235, 235, 235), 11: (50, 70, 170), 14: (170, 40, 40), 15: (25, 25, 30), 8: (150, 150, 150),
        7: (70, 70, 70), 12: (100, 70, 40), 13: (70, 100, 40), 1: (230, 130, 40)}
CLAY = {0: (210, 180, 165), 15: (37, 23, 17), 14: (145, 60, 47), 8: (135, 107, 98), 7: (58, 42, 36),
        11: (75, 60, 92)}
PLANKS = {0: (160, 130, 80), 1: (110, 80, 50), 5: (65, 45, 25)}


POSTS = {85, 113, 101, 102, 160, 139, 107}
LOW = {44, 126, 201, 59, 141, 142, 115, 60, 66, 171}
TINY = {50, 76, 32}


def color(block):
    kind, meta = block
    if kind == 35:
        return WOOL.get(meta, (200, 200, 200))
    if kind == 159:
        return CLAY.get(meta, (150, 120, 110))
    if kind in (95, 160):
        return WOOL.get(meta, (200, 200, 200))
    if kind in (5, 125, 126):
        return PLANKS.get(meta & 7, (160, 130, 80))
    if kind == 98 and meta == 1:
        return (100, 120, 90)
    if kind == 98 and meta == 2:
        return (105, 105, 105)
    return COLORS.get(kind, (255, 0, 255))


def shade(c, factor):
    return tuple(max(0, min(255, int(v * factor))) for v in c)


def render(s, path, size=10):
    """Isometric view from the north-east, so the front (the z = 0 side) faces the viewer on the left."""
    w, l, g, top = s.w, s.l, s.g, s.top
    width = (w + l) * size + 4
    height = (w + l) * size // 2 + (g + top + 2) * size + 4
    image = Image.new("RGB", (width, height), (32, 40, 52))
    draw = ImageDraw.Draw(image)
    ox = l * size + 2
    oy = (top + 1) * size + 2

    def point(x, y, z):
        return (ox + (x - z) * size, oy + (x + z) * size / 2 - y * size)

    turned = {(x, y, l - 1 - z): block for (x, y, z), block in s.blocks.items()}
    cubes = sorted(turned.items(), key=lambda item: (item[0][0] + item[0][2], item[0][1]))
    for (bx, by, bz), block in cubes:
        c = color(block)
        # Thin things as posts, crops and slabs lower, so the picture reads like the game.
        x, y, z, sx, sy, sz = bx, by, bz, 1, 1, 1
        if block[0] in POSTS:
            x, z, sx, sz = bx + 0.35, bz + 0.35, 0.3, 0.3
        elif block[0] in LOW:
            sy = 0.5
        elif block[0] in TINY:
            x, z, sx, sz, sy = bx + 0.4, bz + 0.4, 0.2, 0.2, 0.6
        top_face = [point(x, y + sy, z), point(x + sx, y + sy, z), point(x + sx, y + sy, z + sz),
                    point(x, y + sy, z + sz)]
        left = [point(x, y + sy, z + sz), point(x + sx, y + sy, z + sz), point(x + sx, y, z + sz), point(x, y, z + sz)]
        right = [point(x + sx, y + sy, z), point(x + sx, y + sy, z + sz), point(x + sx, y, z + sz), point(x + sx, y, z)]
        draw.polygon(left, fill=shade(c, 0.75), outline=shade(c, 0.55))
        draw.polygon(right, fill=shade(c, 0.6), outline=shade(c, 0.45))
        draw.polygon(top_face, fill=c, outline=shade(c, 0.8))
    image.save(path)
