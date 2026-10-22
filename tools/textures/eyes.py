"""Draws the glowing eyes of the units whose eyes glow: the lesser, greater and black demons, the centaur, and Sagittare,
whose eyes glow orange under his hood.

    python3 tools/textures/eyes.py [--preview DIR]

Each is a texture laid out as the unit's own, see-through but for its eyes, written beside it with _eyes after its
name (textures/models/lesser_demon_eyes.png, textures/entity/unit/saradomin_sagittare_eyes.png and on). The game draws
the model again with it over itself at full brightness, as Minecraft draws a spider's eyes, and through the shader
pack's spider eyes program when OptiFine's shaders are on, so a pack can make them glow and bloom. The eyes are found
in the textures by their color, so run this again after painting a face. With --preview it writes each eye texture
over its texture, eight times the size, into DIR.

The ghosts', hellhounds', gargoyles' and dragons' eyes are parts of their models of their own, which glow as they are
(see tools/models/blockbench.py), so they need nothing here.
"""

import argparse
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "warofthegods", "textures")
MODELS = os.path.join(ROOT, "models")
SKINS = os.path.join(ROOT, "entity", "unit")
FACTIONS = ("saradomin", "zamorak", "bandos", "armadyl")

# A centaur's eyes glow a warm white, their pupils gold; Sagittare's orange, a little paler at the edge of each eye.
CENTAUR_WHITE = (255, 240, 205)
CENTAUR_PUPIL = (245, 170, 60)
SAGITTARE_ORANGE = (255, 130, 20)
SAGITTARE_EDGE = (255, 175, 70)


def red(pixel):
    r, g, b, a = pixel
    return a > 0 and r >= 0xE0 and g < 0x40 and b < 0x40


def white(pixel):
    r, g, b, a = pixel
    return a > 0 and min(r, g, b) >= 0xE0


def orange(pixel):
    r, g, b, a = pixel
    return a > 0 and r >= 0xD0 and 0x50 <= g <= 0xA0 and b < 0x50


def demon(texture):
    """A demon's eyes: the red ones in the front of its head (the box at 40, 0, 8 by 7 by 7), in their own red."""
    eyes = Image.new("RGBA", texture.size, (0, 0, 0, 0))
    for v in range(7, 14):
        for u in range(47, 55):
            if red(texture.getpixel((u, v))):
                eyes.putpixel((u, v), (255, 24, 16, 255))
    return eyes


def centaur(texture):
    """A centaur's eyes: the whites in the front of his head (the box at 0, 28, 8 by 8 by 8) and the pupils over them."""
    eyes = Image.new("RGBA", texture.size, (0, 0, 0, 0))
    for v in range(36, 44):
        for u in range(8, 16):
            if white(texture.getpixel((u, v))):
                eyes.putpixel((u, v), CENTAUR_WHITE + (255,))
                eyes.putpixel((u, v - 1), CENTAUR_PUPIL + (255,))
    return eyes


def sagittare(skin):
    """Sagittare's eyes: the orange ones in the front of his hood, the skin's hat layer (40, 8 to 48, 16)."""
    eyes = Image.new("RGBA", skin.size, (0, 0, 0, 0))
    for v in range(8, 16):
        for u in range(40, 48):
            if orange(skin.getpixel((u, v))):
                edge = not orange(skin.getpixel((u - 1, v))) or not orange(skin.getpixel((u + 1, v)))
                eyes.putpixel((u, v), (SAGITTARE_EDGE if edge else SAGITTARE_ORANGE) + (255,))
    return eyes


def jobs():
    for name in ("lesser_demon", "greater_demon", "black_demon"):
        yield os.path.join(MODELS, name + ".png"), demon
    yield os.path.join(MODELS, "centaur.png"), centaur
    for faction in FACTIONS:
        yield os.path.join(SKINS, faction + "_sagittare.png"), sagittare


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--preview", help="a directory to draw each eye texture over its texture into")
    args = parser.parse_args()
    for path, find in jobs():
        texture = Image.open(path).convert("RGBA")
        eyes = find(texture)
        if eyes.getbbox() is None:
            raise SystemExit("No eyes found in " + path)
        out = path[:-len(".png")] + "_eyes.png"
        eyes.save(out)
        print("wrote", os.path.relpath(out))
        if args.preview:
            os.makedirs(args.preview, exist_ok=True)
            over = Image.alpha_composite(texture, eyes)
            over = over.resize((over.width * 8, over.height * 8), Image.NEAREST)
            over.save(os.path.join(args.preview, os.path.basename(out)))


if __name__ == "__main__":
    main()
