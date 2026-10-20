"""Draws the square icons on the RTS view's build, train and upgrade buttons, and the web map's.

    python3 tools/textures/actions.py [--preview DIR] [--minecraft-jar JAR]

Buildings are their own level 1 structure, read from the .schematic files and drawn isometrically with Minecraft's
block textures on black, so the icons look like what goes up; run this again after changing a structure. Units are
their face, taken from their skin. Upgrades and the other actions are a Minecraft item or block that fits.

Writes src/main/resources/assets/warofthegods/textures/gui/actions.png, one 64 pixel square per icon, and
actions.json beside it with the icons' names in the order they are in the picture. The RTS view and the web map both
read the two files.
"""

import argparse
import io
import json
import os
import sys
import zipfile

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "structures"))
sys.path.insert(0, os.path.join(HERE, "..", "models"))
import nbt  # noqa: E402

ROOT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "warofthegods")
STRUCTURES = os.path.join(ROOT, "structures")
SKINS = os.path.join(ROOT, "textures", "entity", "unit")
MOD_BLOCKS = os.path.join(ROOT, "textures", "blocks")
MODELS = os.path.join(ROOT, "models")
MODEL_TEXTURES = os.path.join(ROOT, "textures", "models")
OUT = os.path.join(ROOT, "textures", "gui", "actions.png")
OUT_NAMES = os.path.join(ROOT, "textures", "gui", "actions.json")
MINECRAFT_JAR = os.path.expanduser("~/.gradle/caches/retro_futura_gradle/mc-vanilla/1.7.10/client.jar")

SIZE = 64
COLUMNS = 16
FACTIONS = ("saradomin", "zamorak", "bandos")
BUILDINGS = ("town_hall", "house", "farm", "quarry", "barracks", "storehouse", "armory", "tower", "wall", "gate",
             "white_knights_fortress", "black_knights_fortress", "archery_range", "stables", "chaos_temple",
             "monastery", "wizards_tower", "stone_circle", "goblin_cave", "war_pit", "bandos_temple",
             "bandos_bastion")
UNITS = ("worker", "soldier", "archer", "white_knight", "black_knight", "tradigee", "sagittare", "cavalry",
         "chaos_priest", "monk", "dark_wizard", "zamorak_mage", "blue_wizard", "lesser_demon",
         "centaur")
# Bandos's units, which only his teams train, so only his icons have them.
BANDOS_UNITS = ("slave", "goblin", "goblin_thrower", "hobgoblin", "ork", "ogre", "goblin_priest", "spiritual_warrior",
                "spiritual_ranger", "spiritual_mage", "strongstack", "steelwill", "grimspike", "graardor")
# The model each of them wears.
CREATURES = {"hobgoblin": "hobgoblin", "ork": "ork", "graardor": "ork", "ogre": "ogre", "strongstack": "strongstack",
             "steelwill": "strongstack", "grimspike": "strongstack"}

# Upgrades and other actions: an item picture, ("block", top, side) for a block drawn as the inventory draws it, or
# ("face", skin) for the face of a skin, such as the knights the knights' upgrades make, or ("head", model) for the
# head of one of the mod's own models, drawn in 3D.
ITEMS = {
    "upgrade/forged_blades": "items/iron_sword.png",
    "upgrade/tempered_steel": "items/diamond_sword.png",
    "upgrade/fletching": "items/arrow.png",
    "upgrade/fire_arrows": "items/fireball.png",
    "upgrade/padded_armor": ("tint", "items/leather_chestplate.png", (160, 101, 64)),
    "upgrade/plate_armor": "items/iron_chestplate.png",
    "upgrade/baskets": "items/minecart_chest.png",
    "upgrade/sharp_axes": "items/iron_axe.png",
    "upgrade/pickaxes": "items/iron_pickaxe.png",
    "upgrade/ploughs": "items/iron_hoe.png",
    "upgrade/diamond_axes": "items/diamond_axe.png",
    "upgrade/diamond_pickaxes": "items/diamond_pickaxe.png",
    "upgrade/diamond_hoes": "items/diamond_hoe.png",
    "upgrade/tunnel_mining": "items/minecart_normal.png",
    "upgrade/masonry": ("block", "blocks/stonebrick.png", "blocks/stonebrick.png"),
    "upgrade/fortified_walls": ("block", "blocks/iron_block.png", "blocks/iron_block.png"),
    "upgrade/watch_tower": "items/bow_standby.png",
    "upgrade/guard_tower": "items/ender_eye.png",
    "upgrade/stone_walls": ("block", "blocks/cobblestone.png", "blocks/cobblestone.png"),
    "upgrade/citadel_walls": ("block", "blocks/obsidian.png", "blocks/obsidian.png"),
    "upgrade/elite_black_knights": ("face", "zamorak_black_knight_elite"),
    "upgrade/justiciars": ("face", "saradomin_white_knight_elite"),
    "upgrade/greater_demons": ("head", "greater_demon"),
    "upgrade/black_demons": ("head", "black_demon"),
    # Taking a building up a level, or the town into the next age.
    "level": "items/experience_bottle.png",
    "gate": "items/door_wood.png",
    "torches": "blocks/torch_on.png",
    # A horse trained at the stables has no face of its own: its saddle stands for it.
    "face/saradomin/horse": "items/saddle.png",
    "face/zamorak/horse": "items/saddle.png",
}

GRASS = (124, 189, 82)
WATER = (63, 118, 228)
COLORS = {0: "white", 1: "orange", 7: "gray", 8: "silver", 11: "blue", 12: "brown", 14: "red", 15: "black"}


def texture(jar, path):
    with jar.open("assets/minecraft/textures/" + path) as file:
        image = Image.open(io.BytesIO(file.read())).convert("RGBA")
    # Animated textures are a strip of frames; the first will do.
    return image.crop((0, 0, image.width, image.width)) if image.height > image.width else image


def tint(image, color):
    r, g, b, a = image.split()
    r = r.point(lambda v: v * color[0] // 255)
    g = g.point(lambda v: v * color[1] // 255)
    b = b.point(lambda v: v * color[2] // 255)
    return Image.merge("RGBA", (r, g, b, a))


# How each block id is drawn: (top, side) textures, or a function of the metadata giving them. Blocks not listed are
# left out: small things like ladders, levers and skulls hardly show at icon size.
def block_faces(kind, meta):
    color = COLORS.get(meta, "white")
    planks = {0: "oak", 1: "spruce", 2: "birch", 3: "jungle", 4: "acacia", 5: "big_oak"}
    table = {
        1: "stone", 3: "dirt", 4: "cobblestone", 12: "sand", 13: "gravel", 48: "cobblestone_mossy", 41: "gold_block", 42: "iron_block", 49: "obsidian",
        87: "netherrack", 88: "soul_sand", 89: "glowstone", 112: "nether_brick", 113: "nether_brick",
        114: "nether_brick", 152: "redstone_block", 173: "coal_block", 101: "iron_bars", 102: "glass",
        139: "cobblestone", 145: "anvil_base", 118: "cauldron_side",
    }
    if kind in table:
        return table[kind], table[kind]
    if kind == 2:
        return ("grass_top", GRASS), "grass_side"
    if kind == 9 or kind == 8:
        return ("water_still", WATER), ("water_still", WATER)
    if kind in (5, 85, 107, 125):
        name = "planks_" + planks.get(meta & 7, "oak")
        return name, name
    if kind in (126, 134, 135, 136):
        name = "planks_" + {134: "spruce", 135: "birch", 136: "jungle"}.get(kind, planks.get(meta & 7, "oak"))
        return name, name
    if kind == 53:
        return "planks_oak", "planks_oak"
    if kind == 17:
        name = "log_" + ("oak", "spruce", "birch", "jungle")[meta & 3]
        return (name + "_top", name) if meta < 4 else (name, name)
    if kind == 162:
        name = "log_" + ("acacia", "big_oak")[meta & 1]
        return (name + "_top", name) if meta < 4 else (name, name)
    if kind in (35, 171):
        return "wool_colored_" + color, "wool_colored_" + color
    if kind == 159:
        return "hardened_clay_stained_" + color, "hardened_clay_stained_" + color
    if kind in (95, 160):
        return "glass_" + color, "glass_" + color
    if kind in (43, 44):
        name = {0: "stone_slab_top", 3: "cobblestone", 5: "stonebrick", 6: "nether_brick", 7: "quartz_block_top"}
        return name.get(meta & 7, "stone_slab_top"), name.get(meta & 7, "stone_slab_side")
    if kind == 47:
        return "planks_oak", "bookshelf"
    if kind == 54:
        return "planks_oak", "planks_oak"
    if kind == 58:
        return "crafting_table_top", "crafting_table_front"
    if kind == 59:
        return "wheat_stage_7", "wheat_stage_7"
    if kind == 141:
        return "carrots_stage_3", "carrots_stage_3"
    if kind == 142:
        return "potatoes_stage_3", "potatoes_stage_3"
    if kind == 115:
        return "nether_wart_stage_2", "nether_wart_stage_2"
    if kind == 60:
        return "farmland_wet", "dirt"
    if kind == 61:
        return "furnace_top", "furnace_front_off"
    if kind == 64:
        return "planks_oak", "door_wood_lower"
    if kind == 71:
        return "iron_block", "door_iron_lower"
    if kind in (86, 91):
        return "pumpkin_top", "pumpkin_side"
    if kind in (98, 109):
        name = ("stonebrick", "stonebrick_mossy", "stonebrick_cracked", "stonebrick_carved")[meta & 3]
        return (name, name) if kind == 98 else ("stonebrick", "stonebrick")
    if kind in (155, 156):
        if kind == 155 and meta == 1:
            return "quartz_block_chiseled_top", "quartz_block_chiseled"
        if kind == 155 and meta >= 2:
            return "quartz_block_lines_top", "quartz_block_lines"
        return "quartz_block_top", "quartz_block_side"
    if kind == 170:
        return "hay_block_top", "hay_block_side"
    if kind == 164:
        return "planks_big_oak", "planks_big_oak"
    if kind == 50:
        return "torch_on", "torch_on"
    if kind in (51, 209):
        return "fire_layer_0", "fire_layer_0"
    # The mod's own blocks, by the ids ModBlocks.java gives them in schematics; "mod:" names its own textures.
    if kind in (200, 201, 202, 203):
        return "mod:graystone_bricks", "mod:graystone_bricks"
    if kind == 204:
        return "planks_big_oak", ("mod:wood_door_upper" if meta & 8 else "mod:wood_door_lower")
    if kind in BANNERS:
        name = "mod:banner_" + BANNER_KINDS[kind] + "_" + ("top", "emblem", "bottom", "pennant")[meta >> 2 & 3]
        return name, name
    return None


BANNERS = {205, 206, 207, 208, 210, 211}
BANNER_KINDS = {205: "saradomin", 206: "saradomin_inverted", 207: "zamorak", 208: "zamorak_inverted", 210: "bandos",
                211: "bandos_inverted"}


# Block shapes: posts are thin, slabs and crops low, torches small; everything else is a whole cube, stairs too.
POSTS = {85, 113, 101, 102, 160, 139, 107}
LOW = {44, 126, 59, 141, 142, 115, 60, 201}
TINY = {50, 51, 76, 209}
# Blocks that hide what is behind them.
SEE_THROUGH = POSTS | LOW | TINY | BANNERS | {0, 20, 95, 50, 64, 71, 65, 32, 31, 37, 38, 204}


class Faces:
    """Block textures from the jar, loaded once each."""

    def __init__(self, jar):
        self.jar = jar
        self.cache = {}

    def get(self, spec):
        key = spec if isinstance(spec, str) else spec[0]
        if spec not in self.cache:
            if key.startswith("mod:"):
                image = Image.open(os.path.join(MOD_BLOCKS, key[4:] + ".png")).convert("RGBA")
                image = image.crop((0, 0, image.width, image.width))
            else:
                image = texture(self.jar, "blocks/" + key + ".png")
            if not isinstance(spec, str):
                image = tint(image, spec[1])
            self.cache[spec] = image
        return self.cache[spec]


def face(canvas, image, origin, a, b, shade):
    """Pastes a 16 pixel texture onto the parallelogram origin + u * a + v * b, u and v from 0 to 1."""
    xs = [origin[0], origin[0] + a[0], origin[0] + b[0], origin[0] + a[0] + b[0]]
    ys = [origin[1], origin[1] + a[1], origin[1] + b[1], origin[1] + a[1] + b[1]]
    left, top = int(min(xs)) - 1, int(min(ys)) - 1
    w, h = int(max(xs)) - left + 2, int(max(ys)) - top + 2
    det = a[0] * b[1] - a[1] * b[0]
    if abs(det) < 1e-6:
        return
    tw, th = image.size
    # Texture pixel (u * tw, v * th) for each output pixel x, y of the box at left, top.
    ia, ib = b[1] / det, -b[0] / det
    ic, id_ = -a[1] / det, a[0] / det
    ox, oy = left - origin[0], top - origin[1]
    coefficients = (ia * tw, ib * tw, (ia * ox + ib * oy) * tw, ic * th, id_ * th, (ic * ox + id_ * oy) * th)
    if shade != 1:
        bands = image.split()
        image = Image.merge("RGBA", [band.point(lambda v: int(v * shade)) for band in bands[:3]] + [bands[3]])
    patch = image.transform((w, h), Image.AFFINE, coefficients, resample=Image.NEAREST)
    # Pixels whose texture coordinates fall outside the face sample nothing: clear them.
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon(
        [(xs[0] - left, ys[0] - top), (xs[1] - left, ys[1] - top), (xs[3] - left, ys[3] - top),
         (xs[2] - left, ys[2] - top)], fill=255)
    alpha = Image.composite(patch.split()[3], Image.new("L", (w, h), 0), mask)
    patch.putalpha(alpha)
    canvas.alpha_composite(patch, (left, top))


def read_schematic(path):
    root = nbt.read_compressed(path)
    w, h, l = root["Width"], root["Height"], root["Length"]
    blocks = {}
    for i, (kind, meta) in enumerate(zip(root["Blocks"], root["Data"])):
        kind &= 0xFF
        if kind:
            x = i % w
            z = (i // w) % l
            y = i // (w * l)
            blocks[(x, y, z)] = (kind, meta)
    return w, h, l, blocks


def building(faces, path):
    """The structure seen from the south-east, its front on the left, cropped to what was drawn, on black."""
    w, h, l, blocks = read_schematic(path)
    s = 16  # half the width of a block's top diamond, in pixels
    width = (w + l) * s + 8
    height = (w + l) * s // 2 + h * s + 8
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ox, oy = l * s + 4, h * s + 4

    def point(x, y, z):
        return ox + (x - z) * s, oy + (x + z) * s / 2 - y * s

    def solid(x, y, z):
        block = blocks.get((x, y, z))
        return block is not None and block[0] not in SEE_THROUGH and block_faces(*block) is not None

    # Turned so the z = 0 side, where the door is, faces the viewer on the left; back to front.
    turned = {(x, y, l - 1 - z): block for (x, y, z), block in blocks.items()}
    blocks = turned
    for (bx, by, bz), block in sorted(blocks.items(), key=lambda item: (sum(item[0]), item[0][1])):
        spec = block_faces(*block)
        if spec is None:
            continue
        top, side = spec
        x, y, z, sx, sy, sz = bx, by, bz, 1.0, 1.0, 1.0
        if block[0] in POSTS:
            x, z, sx, sz = bx + 0.35, bz + 0.35, 0.3, 0.3
        elif block[0] in LOW:
            sy = 0.5
        elif block[0] in BANNERS:
            # A cloth hanging flat against the block behind it, thin across the way it faces.
            if block[1] & 1:
                x, sx = bx + 0.45, 0.1
            else:
                z, sz = bz + 0.45, 0.1
        elif block[0] in TINY:
            x, z, sx, sz, sy = bx + 0.4, bz + 0.4, 0.2, 0.2, 0.6
        full = sx == 1 and sz == 1
        # The +z side faces the viewer on the left, the +x side on the right.
        if not (full and sy == 1 and solid(bx, by, bz + 1)):
            face(canvas, faces.get(side), point(x, y + sy, z + sz), (sx * s, sx * s / 2), (0, sy * s), 0.78)
        if not (full and sy == 1 and solid(bx + 1, by, bz)):
            face(canvas, faces.get(side), point(x + sx, y + sy, z + sz), (sz * s, -sz * s / 2), (0, sy * s), 0.62)
        if not (full and sy == 1 and solid(bx, by + 1, bz)):
            face(canvas, faces.get(top), point(x, y + sy, z), (sx * s, sx * s / 2), (-sz * s, sz * s / 2), 1.0)
    return framed(canvas, (0, 0, 0, 255), 3)


def framed(image, background, margin):
    """The drawn part of an image scaled into an icon square, centred on the background."""
    box = image.getbbox()
    image = image.crop(box)
    room = SIZE - 2 * margin
    scale = min(room / image.width, room / image.height)
    size = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
    image = image.resize(size, Image.LANCZOS if scale < 1 else Image.NEAREST)
    icon = Image.new("RGBA", (SIZE, SIZE), background)
    icon.alpha_composite(image, ((SIZE - size[0]) // 2, (SIZE - size[1]) // 2))
    return icon


# Units drawn as their model's head rather than their skin's face.
HEADS = {"lesser_demon": "lesser_demon"}


def face_icon(faction, kind):
    """A unit's face: the front of its head with its hat layer over it, as large as the icon."""
    if kind in HEADS:
        return model_head(HEADS[kind])
    if kind in BANDOS_UNITS and kind != "slave":
        return creature_head(CREATURES.get(kind, "goblin"), kind)
    return skin_face("%s_%s" % (faction, kind))


def model_head(name):
    """The head of one of the mod's own models, seen a little from the left and above: its face and the roots of its
    horns, and behind them anything else that hangs from its body, such as a greater demon's wings, with the body
    itself left out."""
    import blockbench
    import boxmodel
    with open(os.path.join(MODELS, name + ".json")) as f:
        model = json.load(f)
    texture = Image.open(os.path.join(MODEL_TEXTURES, name + ".png")).convert("RGBA")
    parts, keep = [], set()
    for part in model["parts"]:
        if part["name"] == "body":
            part = dict(part, boxes=[], rotation=[0.0, 0.0, 0.0])
        elif part["parent"] == "body" and (part["name"] == "head" or "wing" in part["name"]):
            if part["name"] == "head":
                part = dict(part, rotation=[0.0, 0.0, 0.0])
        elif part["parent"] not in keep or part["parent"] == "body":
            continue
        parts.append(part)
        keep.add(part["name"])
    bust = blockbench.built({"texture": model["texture"], "parts": parts})
    yaw, pitch, big = -12, 18, 1024
    picture = boxmodel.render(bust, texture, size=big, yaw=yaw, pitch=pitch, margin=0.02)
    # Where the face is in the picture: the same view with only the head's own box in the texture.
    head = next(part for part in model["parts"] if part["name"] == "head")["boxes"][0]
    (u, v), (w, h, d) = head["uv"], head["size"]
    alone = Image.new("RGBA", texture.size, (0, 0, 0, 0))
    alone.paste(texture.crop((u, v, u + 2 * (d + w), v + d + h)), (u, v))
    left, top, right, bottom = boxmodel.render(bust, alone, size=big, yaw=yaw, pitch=pitch, margin=0.02).getbbox()
    middle_x, middle_y = (left + right) / 2, (top + bottom) / 2 + (bottom - top) * 0.1
    half = max(right - left, bottom - top) * 1.15
    square = picture.crop((int(middle_x - half), int(middle_y - half), int(middle_x + half), int(middle_y + half)))
    return square.resize((SIZE, SIZE), Image.LANCZOS)


def creature_head(model_name, kind):
    """The head of one of Bandos's creatures, in the kind's own texture, seen a little from the left and above."""
    import blockbench
    import boxmodel
    with open(os.path.join(MODELS, model_name + ".json")) as f:
        model = json.load(f)
    texture = Image.open(os.path.join(MODEL_TEXTURES, kind + ".png")).convert("RGBA")
    team = Image.open(os.path.join(MODEL_TEXTURES, kind + "_team.png")).convert("RGBA")
    keep = {"head"}
    parts = []
    for part in model["parts"]:
        if part["name"] in keep or part["parent"] in keep:
            parts.append(dict(part, rotation=[0.0, 0.0, 0.0]) if part["name"] == "head" else part)
            keep.add(part["name"])
    # Weapons stay out of the picture.
    parts = [part for part in parts if part["name"] not in ("spear", "weapon")]
    bust = blockbench.built({"texture": model["texture"], "parts": parts})
    picture = boxmodel.render(bust, texture, size=512, yaw=-12, pitch=18, margin=0.06, mask=team,
                              team=(150, 150, 150))
    return picture.crop(picture.getbbox() or (0, 0, 512, 512)).resize((SIZE, SIZE), Image.LANCZOS)


def skin_face(name):
    skin = Image.open(os.path.join(SKINS, name + ".png")).convert("RGBA")
    head = skin.crop((8, 8, 16, 16))
    head.alpha_composite(skin.crop((40, 8, 48, 16)))
    return head.resize((SIZE, SIZE), Image.NEAREST)


def block_icon(top, side):
    """A block seen from above at an angle, as the inventory draws it."""
    canvas = Image.new("RGBA", (40, 40), (0, 0, 0, 0))
    s = 16
    o = (4, 4 + s / 2)
    face(canvas, side, (o[0], o[1]), (s, s / 2), (0, s), 0.8)
    face(canvas, side, (o[0] + s, o[1] + s / 2), (s, -s / 2), (0, s), 0.6)
    face(canvas, top, (o[0] + s, o[1] - s / 2), (s, s / 2), (-s, s / 2), 1.0)
    return framed(canvas, (0, 0, 0, 0), 6)


def item_icon(jar, spec):
    if isinstance(spec, tuple) and spec[0] == "face":
        return skin_face(spec[1])
    if isinstance(spec, tuple) and spec[0] == "head":
        return model_head(spec[1])
    if isinstance(spec, tuple) and spec[0] == "block":
        return block_icon(texture(jar, spec[1]), texture(jar, spec[2]))
    if isinstance(spec, tuple) and spec[0] == "tint":
        return framed(tint(texture(jar, spec[1]), spec[2]), (0, 0, 0, 0), 4)
    return framed(texture(jar, spec), (0, 0, 0, 0), 4)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", help="also write each icon into this folder")
    parser.add_argument("--minecraft-jar", default=MINECRAFT_JAR,
                        help="the Minecraft 1.7.10 client jar to take block and item pictures from")
    args = parser.parse_args()
    jar = zipfile.ZipFile(args.minecraft_jar)
    faces = Faces(jar)
    icons = []
    for faction in FACTIONS:
        for name in BUILDINGS:
            path = os.path.join(STRUCTURES, faction, name + "_l1.schematic")
            if os.path.exists(path):
                icons.append(("building/%s/%s" % (faction, name), building(faces, path)))
    for faction in FACTIONS:
        for name in UNITS + (BANDOS_UNITS if faction == "bandos" else ()):
            icons.append(("face/%s/%s" % (faction, name), face_icon(faction, name)))
    for name, spec in ITEMS.items():
        icons.append((name, item_icon(jar, spec)))

    rows = (len(icons) + COLUMNS - 1) // COLUMNS
    sheet_rows = 1
    while sheet_rows < rows:
        sheet_rows *= 2
    sheet = Image.new("RGBA", (COLUMNS * SIZE, sheet_rows * SIZE), (0, 0, 0, 0))
    for i, (name, image) in enumerate(icons):
        sheet.paste(image, ((i % COLUMNS) * SIZE, (i // COLUMNS) * SIZE))
    sheet.save(OUT, optimize=True)
    with open(OUT_NAMES, "w") as f:
        json.dump({"size": SIZE, "columns": COLUMNS, "names": [name for name, _ in icons]}, f, indent=1)
        f.write("\n")
    if args.preview:
        os.makedirs(args.preview, exist_ok=True)
        for name, image in icons:
            image.resize((SIZE * 2, SIZE * 2), Image.NEAREST).save(
                os.path.join(args.preview, name.replace("/", "_") + ".png"))
    print("Wrote %d icons" % len(icons))


if __name__ == "__main__":
    main()
