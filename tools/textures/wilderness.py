"""Forinthry's blocks, the Wilderness burnt black in the God Wars: charred earth, ash, scorched stone and ember rock
whose cracks still glow. Drawn at Minecraft's 16x16, called from blocks.py."""

import random

from PIL import Image

CLEAR = (0, 0, 0, 0)


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def vary(c, rng, amount):
    n = rng.randint(-amount, amount)
    return clamp((c[0] + n, c[1] + n, c[2] + n))


def scale(c, f):
    return clamp(tuple(v * f for v in c))


def mix(a, b, t):
    return clamp(tuple(a[i] + (b[i] - a[i]) * t for i in range(3)))


def new(fill=None):
    return Image.new("RGBA", (16, 16), fill + (255,) if fill else CLEAR)


def put(image, x, y, c):
    image.putpixel((x % 16, y % 16), tuple(c[:3]) + (255,))


def speckled(base, specks, seed, amount=10):
    """Ground: the base color varied pixel by pixel, with clumps of the speck colors scattered over it."""
    rng = random.Random(seed)
    image = new()
    for y in range(16):
        for x in range(16):
            put(image, x, y, vary(base, rng, amount))
    for color, count in specks:
        for _ in range(count):
            x, y = rng.randrange(16), rng.randrange(16)
            put(image, x, y, vary(color, rng, 6))
            if rng.random() < 0.4:
                put(image, x + rng.choice((-1, 1)), y, vary(scale(color, 0.85), rng, 6))
    return image


def charred_earth():
    """Earth burnt black and brown, with ash, cinders and a few embers in it."""
    return speckled((46, 38, 34), [((26, 22, 22), 34), ((92, 88, 84), 14), ((70, 52, 40), 18), ((150, 60, 24), 3)],
                    211)


def ash():
    """Pale grey ash lying deep, with darker flecks of soot."""
    return speckled((132, 128, 124), [((104, 100, 98), 30), ((164, 160, 156), 20), ((60, 56, 56), 10)], 212, 7)


def cracked_stone(seed, glow):
    """Dark stone gone black in the fire, its cracks dark, or glowing like embers when glow is set."""
    rng = random.Random(seed)
    image = new()
    base = (58, 54, 56)
    for y in range(16):
        for x in range(16):
            c = vary(base, rng, 8)
            if (x * 3 + y * 5) % 11 == 0:
                c = scale(c, 1.15)
            put(image, x, y, c)
    # Cracks: a few walks across the face.
    for _ in range(3 if glow else 2):
        x, y = rng.randrange(16), rng.randrange(16)
        for _ in range(rng.randint(6, 12)):
            if glow:
                hot = rng.random()
                put(image, x, y, mix((200, 60, 10), (255, 190, 60), hot))
            else:
                put(image, x, y, (30, 28, 30))
            x += rng.choice((-1, 0, 1))
            y += rng.choice((0, 1, 1))
    return image


def textures():
    """The textures by name, as the blocks register them."""
    return {
        "charred_earth": charred_earth(),
        "ash": ash(),
        "scorched_stone": cracked_stone(213, False),
        "ember_rock": cracked_stone(214, True),
    }
