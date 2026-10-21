"""Draws structures as textured isometric pictures, with Minecraft's own block textures and the mod's, to see them as
they will look in the game without starting it.

    python3 tools/structures/render.py --minecraft-jar client.jar OUT_DIR NAME [NAME...]

NAME is a structure under src/main/resources/assets/warofthegods/structures, such as zamorak/house_l2. The client jar
of Minecraft 1.7.10 has the textures; Gradle keeps one in ~/.gradle/caches/retro_futura_gradle/mc-vanilla/1.7.10/.
Shapes are close, not exact: stairs, slabs, fences, panes, doors, torches, banners and plants are drawn as boxes and
upright pictures, and chests and the like as small boxes.
"""

import argparse
import io
import os
import sys
import zipfile

import numpy
from PIL import Image, ImageDraw, ImageEnhance

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import nbt  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
STRUCTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "warofthegods", "structures")
MOD_TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "warofthegods", "textures", "blocks")

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "silver", "cyan", "purple",
          "blue", "brown", "green", "red", "black"]
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "big_oak"]
GRASS_TINT = (124, 189, 84)
LEAF_TINT = (96, 160, 60)

STAIRS = {53: "planks_oak", 67: "cobblestone", 108: "brick", 109: "stonebrick", 114: "nether_brick",
          128: "sandstone_normal", 134: "planks_spruce", 135: "planks_birch", 136: "planks_jungle",
          156: "quartz_block_side", 163: "planks_acacia", 164: "planks_big_oak", 203: "mod:graystone_bricks"}
SLAB_TEXTURES = ["stone_slab_top", "sandstone_normal", "planks_oak", "cobblestone", "brick", "stonebrick",
                 "nether_brick", "quartz_block_side"]
CROSS = {6: "sapling_oak", 30: "web", 31: "tallgrass", 32: "deadbush", 37: "flower_dandelion", 38: "flower_rose",
         59: "wheat_stage_7", 115: "nether_wart_stage_2", 141: "carrots_stage_3", 142: "potatoes_stage_3",
         51: "fire_layer_0", 209: "fire_layer_0"}
# Forinthry's blocks, by the ids ModBlocks.java gives them.
WILDERNESS = {212: "charred_earth", 213: "ash", 214: "scorched_stone", 215: "ember_rock", 216: "charred_log",
              217: "mushroom_stem", 218: "mushroom_cap"}
BANNER_KINDS = {205: "saradomin", 206: "saradomin_inverted", 207: "zamorak", 208: "zamorak_inverted", 210: "bandos",
                211: "bandos_inverted"}


class Textures:
    def __init__(self, jar):
        self.zip = zipfile.ZipFile(jar)
        self.cache = {}

    def get(self, name, tint=None):
        key = (name, tint)
        if key in self.cache:
            return self.cache[key]
        if name.startswith("mod:"):
            path = os.path.join(MOD_TEXTURES, name[4:] + ".png")
            image = Image.open(path).convert("RGBA") if os.path.exists(path) else None
        else:
            try:
                data = self.zip.read("assets/minecraft/textures/blocks/%s.png" % name)
                image = Image.open(io.BytesIO(data)).convert("RGBA")
            except KeyError:
                image = None
        if image is None:
            image = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
        if image.height > image.width:
            # Animated: the first frame.
            image = image.crop((0, 0, image.width, image.width))
        if image.width != 16:
            image = image.resize((16, 16), Image.NEAREST)
        if tint:
            r, g, b, a = image.split()
            grey = Image.merge("RGB", (r, g, b)).convert("L")
            tinted = Image.merge("RGB", [grey.point(lambda v, c=c: v * c // 255) for c in tint])
            image = Image.merge("RGBA", (*tinted.split(), a))
        self.cache[key] = image
        return image


def faces_for(t, block, meta):
    """Textures of a full block as (top, side, front), or None to skip it."""
    if block == 1:
        return ["stone"] * 3
    if block in WILDERNESS:
        name = WILDERNESS[block]
        if block == 216:
            side, end = "mod:charred_log", "mod:charred_log_top"
            return (side, end, side) if meta & 12 == 4 else (side, side, end) if meta & 12 == 8 else (end, side, side)
        if block == 217:
            return "mod:mushroom_inside", "mod:mushroom_stem", "mod:mushroom_stem"
        return ["mod:" + name] * 3
    if block == 2:
        return ("grass_top", GRASS_TINT), "grass_side", "grass_side"
    if block == 3:
        return ["dirt"] * 3
    if block == 4:
        return ["cobblestone"] * 3
    if block == 5:
        return ["planks_" + WOODS[meta % 6]] * 3
    if block in (8, 9):
        return ["water_still"] * 3
    if block == 12:
        return ["sand"] * 3
    if block == 13:
        return ["gravel"] * 3
    if block == 14:
        return ["gold_ore"] * 3
    if block in (17, 162):
        wood = WOODS[(meta & 3) + (4 if block == 162 else 0)] if block == 17 or (meta & 3) < 2 else "oak"
        side, end = "log_" + wood, "log_%s_top" % wood
        axis = meta & 12
        if axis == 4:   # along x
            return side, end, side
        if axis == 8:   # along z
            return side, side, end
        return end, side, side
    if block == 18:
        return [("leaves_oak", LEAF_TINT)] * 3
    if block == 20:
        return ["glass"] * 3
    if block == 35:
        return ["wool_colored_" + COLORS[meta]] * 3
    if block == 41:
        return ["gold_block"] * 3
    if block == 42:
        return ["iron_block"] * 3
    if block == 43:
        return [SLAB_TEXTURES[meta & 7]] * 3
    if block == 45:
        return ["brick"] * 3
    if block == 47:
        return "planks_oak", "bookshelf", "bookshelf"
    if block == 48:
        return ["cobblestone_mossy"] * 3
    if block == 49:
        return ["obsidian"] * 3
    if block == 58:
        return "crafting_table_top", "crafting_table_side", "crafting_table_front"
    if block == 61:
        return "furnace_top", "furnace_side", "furnace_front_off"
    if block in (86, 91):
        return "pumpkin_top", "pumpkin_side", "pumpkin_face_on" if block == 91 else "pumpkin_face_off"
    if block == 87:
        return ["netherrack"] * 3
    if block == 88:
        return ["soul_sand"] * 3
    if block == 89:
        return ["glowstone"] * 3
    if block == 95:
        return ["glass_" + COLORS[meta]] * 3
    if block == 98:
        return [["stonebrick", "stonebrick_mossy", "stonebrick_cracked", "stonebrick_carved"][meta & 3]] * 3
    if block == 112:
        return ["nether_brick"] * 3
    if block == 152:
        return ["redstone_block"] * 3
    if block == 155:
        if meta == 1:
            return "quartz_block_chiseled_top", "quartz_block_chiseled", "quartz_block_chiseled"
        if meta >= 2:
            return "quartz_block_lines_top", "quartz_block_lines", "quartz_block_lines"
        return "quartz_block_top", "quartz_block_side", "quartz_block_side"
    if block == 159:
        return ["hardened_clay_stained_" + COLORS[meta]] * 3
    if block == 170:
        return "hay_block_top", "hay_block_side", "hay_block_side"
    if block == 173:
        return ["coal_block"] * 3
    if block in (200, 202):
        return ["mod:" + ["graystone_bricks", "graystone_bricks_mossy", "graystone_bricks_cracked"][
            meta if block == 200 and meta < 3 else 0]] * 3
    return None


class Canvas:
    def __init__(self, struct, size):
        self.s = struct
        self.size = size
        w, l, h = struct["w"], struct["l"], struct["h"]
        self.cos = size * 0.866
        self.width = int((w + l) * self.cos) + 8
        self.height = int((w + l) * size / 2 + h * size) + 8
        self.ox = l * self.cos + 4
        self.oy = h * size + 4
        self.boxes = []

    def point(self, x, y, z):
        return (self.ox + (x - z) * self.cos, self.oy + (x + z) * self.size / 2 - y * self.size)

    def box(self, x0, y0, z0, x1, y1, z1, top, side, front, alpha=255):
        self.boxes.append(((x0 + x1 + y0 + y1 + z0 + z1) / 2, "box", (x0, y0, z0, x1, y1, z1, top, side, front,
                                                                    alpha)))

    def sprite(self, x, y, z, texture, height=1.0):
        self.boxes.append((x + y + z + 1.5, "sprite", (x, y, z, texture, height)))

    def face(self, image, texture, corners, uv, shade):
        """Draws a texture's uv rectangle (u0, v0, u1, v1 in 0..16) onto the parallelogram with corners origin, along u
        and along v."""
        o, a, b = corners
        tex = texture.crop((int(uv[0]), int(uv[1]), max(int(uv[0]) + 1, int(uv[2])), max(int(uv[1]) + 1, int(uv[3]))))
        tw, th = tex.size
        if shade != 1:
            rgb = ImageEnhance.Brightness(tex.convert("RGB")).enhance(shade)
            tex = Image.merge("RGBA", (*rgb.split(), tex.split()[3]))
        xs = [o[0], a[0], b[0], a[0] + b[0] - o[0]]
        ys = [o[1], a[1], b[1], a[1] + b[1] - o[1]]
        left, top = int(min(xs)) - 1, int(min(ys)) - 1
        width, height = int(max(xs)) - left + 2, int(max(ys)) - top + 2
        # The inverse map: from output pixel to texture pixel.
        ax, ay = (a[0] - o[0]) / tw, (a[1] - o[1]) / tw
        bx, by = (b[0] - o[0]) / th, (b[1] - o[1]) / th
        det = ax * by - ay * bx
        if abs(det) < 1e-6:
            return
        ia, ib, ic, id_ = by / det, -bx / det, -ay / det, ax / det
        ox, oy = o[0] - left, o[1] - top
        data = (ia, ib, -(ia * ox + ib * oy), ic, id_, -(ic * ox + id_ * oy))
        out = tex.transform((width, height), Image.AFFINE, data, resample=Image.NEAREST, fillcolor=(0, 0, 0, 0))
        # Clip to the parallelogram, so edges of the texture do not smear.
        mask = Image.new("L", (width, height), 0)
        ImageDraw.Draw(mask).polygon([(xs[0] - left, ys[0] - top), (xs[1] - left, ys[1] - top),
                                      (xs[3] - left, ys[3] - top), (xs[2] - left, ys[2] - top)], fill=255)
        out.putalpha(Image.fromarray(numpy.minimum(numpy.array(out.split()[3]), numpy.array(mask))))
        image.alpha_composite(out, (left, top))

    def render(self, textures, background=(34, 40, 52, 255)):
        image = Image.new("RGBA", (self.width, self.height), background)
        for _, kind, args in sorted(self.boxes, key=lambda item: item[0]):
            if kind == "box":
                x0, y0, z0, x1, y1, z1, top, side, front, alpha = args
                tt, ts, tf = (textures.get(*t) if isinstance(t, tuple) else textures.get(t) for t in (top, side, front))
                p = self.point
                # Where the box lies in its block, for the part of each texture it shows.
                bx, by, bz = (int((x0 + x1) // 2), int((y0 + y1) // 2), int((z0 + z1) // 2))
                fx0, fx1, fy0, fy1, fz0, fz1 = x0 - bx, x1 - bx, y0 - by, y1 - by, z0 - bz, z1 - bz
                self.face(image, tt, (p(x0, y1, z0), p(x1, y1, z0), p(x0, y1, z1)),
                          (fx0 * 16, fz0 * 16, fx1 * 16, fz1 * 16), 1.0)
                self.face(image, ts, (p(x1, y1, z1), p(x1, y1, z0), p(x1, y0, z1)),
                          ((1 - fz1) * 16, (1 - fy1) * 16, (1 - fz0) * 16, (1 - fy0) * 16), 0.78)
                self.face(image, tf, (p(x0, y1, z1), p(x1, y1, z1), p(x0, y0, z1)),
                          (fx0 * 16, (1 - fy1) * 16, fx1 * 16, (1 - fy0) * 16), 0.62)
            else:
                x, y, z, texture, height = args
                tex = textures.get(*texture) if isinstance(texture, tuple) else textures.get(texture)
                p = self.point
                # Two upright planes crossing, as Minecraft draws plants.
                self.face(image, tex, (p(x, y + height, z), p(x + 1, y + height, z + 1), p(x, y, z)),
                          (0, 16 * (1 - height), 16, 16), 0.85)
                self.face(image, tex, (p(x, y + height, z + 1), p(x + 1, y + height, z), p(x, y, z + 1)),
                          (0, 16 * (1 - height), 16, 16), 0.95)
        return image


def opaque(blocks, at):
    block = blocks.get(at)
    return block is not None and faces_for(None, *block) is not None and block[0] not in (18, 20, 95, 8, 9)


def draw(struct, size, textures):
    c = Canvas(struct, size)
    w, l = struct["w"], struct["l"]
    blocks = struct["blocks"]
    for (sx, sy, sz), (block, meta) in blocks.items():
        # Turned so the front, z = 0, faces the viewer on the right.
        x, y, z = sx, sy, l - 1 - sz
        if all(opaque(blocks, n) for n in ((sx + 1, sy, sz), (sx, sy + 1, sz), (sx, sy, sz - 1))) and \
                faces_for(None, block, meta) is not None:
            continue
        full = faces_for(textures, block, meta)
        if full is not None:
            c.box(x, y, z, x + 1, y + 1, z + 1, *full)
            continue
        draw_shape(c, blocks, sx, sy, sz, x, y, z, block, meta)
    return c.render(textures)


def turned_dir(dx, dz):
    """A direction in the structure, as one on the picture: z runs the other way."""
    return dx, -dz


def draw_shape(c, blocks, sx, sy, sz, x, y, z, block, meta):
    if block in STAIRS:
        tex = STAIRS[block]
        tex = tex if not tex.startswith("mod:") else tex
        upside = meta & 4
        c.box(x, y + (0.5 if upside else 0), z, x + 1, y + (1 if upside else 0.5), z + 1, tex, tex, tex)
        climb = meta & 3
        # 0 east, 1 west, 2 south, 3 north; south is toward +z in the structure, -z on the picture.
        x0, x1, z0, z1 = x, x + 1, z, z + 1
        if climb == 0:
            x0 = x + 0.5
        elif climb == 1:
            x1 = x + 0.5
        elif climb == 2:
            z1 = z + 0.5
        else:
            z0 = z + 0.5
        ya, yb = (y, y + 0.5) if upside else (y + 0.5, y + 1)
        c.box(x0, ya, z0, x1, yb, z1, tex, tex, tex)
    elif block in (44, 126, 201, 171):
        if block == 44:
            tex = SLAB_TEXTURES[meta & 7]
            top = "stone_slab_top" if meta & 7 == 0 else tex
            side = "stone_slab_side" if meta & 7 == 0 else tex
        elif block == 126:
            top = side = "planks_" + WOODS[(meta & 7) % 6]
        elif block == 201:
            top = side = "mod:graystone_bricks"
        else:
            top = side = "wool_colored_" + COLORS[meta]
        h = 1 / 16 if block == 171 else 0.5
        y0 = y + (0.5 if meta & 8 and block != 171 else 0)
        c.box(x, y0, z, x + 1, y0 + h, z + 1, top, side, side)
    elif block in (85, 113, 139, 107):
        tex = {85: "planks_oak", 113: "nether_brick", 139: "cobblestone", 107: "planks_oak"}[block]
        r = 0.25 if block == 139 else 0.375
        top = 1 if block != 107 else 0.9
        if block != 107:
            c.box(x + r, y, z + r, x + 1 - r, y + top, z + 1 - r, tex, tex, tex)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = blocks.get((sx + dx, sy, sz + dz))
            if n is None:
                continue
            if n[0] in (85, 113, 139, 107) or faces_for(None, *n) is not None:
                ddx, ddz = turned_dir(dx, dz)
                if block == 107 and ddx == 0:
                    continue
                for ry in ((0.4, 0.55), (0.75, 0.9)) if block != 139 else ((0, 0.8),):
                    bx0 = x + (0.5 if ddx > 0 else 0 if ddx < 0 else 0.44)
                    bx1 = x + (1 if ddx > 0 else 0.5 if ddx < 0 else 0.56)
                    bz0 = z + (0.5 if ddz > 0 else 0 if ddz < 0 else 0.44)
                    bz1 = z + (1 if ddz > 0 else 0.5 if ddz < 0 else 0.56)
                    if block == 139:
                        bx0, bx1 = (bx0, bx1) if ddx else (x + 0.3, x + 0.7)
                        bz0, bz1 = (bz0, bz1) if ddz else (z + 0.3, z + 0.7)
                    c.box(bx0, y + ry[0], bz0, bx1, y + ry[1], bz1, tex, tex, tex)
    elif block in (101, 102, 160):
        tex = "iron_bars" if block == 101 else "glass" if block == 102 else "glass_" + COLORS[meta]
        along_x = any(blocks.get((sx + d, sy, sz)) for d in (1, -1))
        if along_x:
            c.box(x, y, z + 0.47, x + 1, y + 1, z + 0.53, tex, tex, tex)
        else:
            c.box(x + 0.47, y, z, x + 0.53, y + 1, z + 1, tex, tex, tex)
    elif block in (64, 71, 204):
        upper = meta & 8
        if upper:
            below = blocks.get((sx, sy - 1, sz), (0, 0))
            facing = below[1] & 3
        else:
            facing = meta & 3
        name = {64: "door_wood", 71: "door_iron", 204: "mod:wood_door"}[block] + ("_upper" if upper else "_lower")
        t = 3 / 16
        if facing == 1:      # closed in a wall along x, on its north side
            c.box(x, y, z + 1 - t, x + 1, y + 1, z + 1, name, name, name)
        elif facing == 3:
            c.box(x, y, z, x + 1, y + 1, z + t, name, name, name)
        elif facing == 0:
            c.box(x, y, z, x + t, y + 1, z + 1, name, name, name)
        else:
            c.box(x + 1 - t, y, z, x + 1, y + 1, z + 1, name, name, name)
    elif block in BANNER_KINDS:
        piece = ["top", "emblem", "bottom", "pennant"][meta >> 2 & 3]
        name = "mod:banner_%s_%s" % (BANNER_KINDS[block], piece)
        t = 1 / 16
        facing = meta & 3
        if facing == 0:      # faces north, on the block south of it: -z on the picture
            c.box(x, y, z, x + 1, y + 1, z + t, name, name, name)
        elif facing == 2:
            c.box(x, y, z + 1 - t, x + 1, y + 1, z + 1, name, name, name)
        elif facing == 1:
            c.box(x, y, z, x + t, y + 1, z + 1, name, name, name)
        else:
            c.box(x + 1 - t, y, z, x + 1, y + 1, z + 1, name, name, name)
    elif block in (50, 76):
        tex = "torch_on" if block == 50 else "redstone_torch_on"
        dx, dz = {1: (-0.3, 0), 2: (0.3, 0), 3: (0, 0.3), 4: (0, -0.3)}.get(meta, (0, 0))
        dx, dz = dx, -dz
        lift = 0.2 if meta in (1, 2, 3, 4) else 0
        c.box(x + 0.44 + dx, y + lift, z + 0.44 + dz, x + 0.56 + dx, y + lift + 0.62, z + 0.56 + dz,
              tex, tex, tex)
    elif block == 65:
        dz = {2: 0.9, 3: 0.0, 4: None, 5: None}.get(meta, 0.9)
        if dz is None:
            xx = x + (0.9 if meta == 4 else 0)
            c.box(xx, y, z, xx + 0.1, y + 1, z + 1, "ladder", "ladder", "ladder")
        else:
            zz = z + (0 if meta == 2 else 0.9)
            c.box(x, y, zz, x + 1, y + 1, zz + 0.1, "ladder", "ladder", "ladder")
    elif block in CROSS:
        tex = CROSS[block]
        if block == 31:
            tex = ("tallgrass", GRASS_TINT) if meta != 2 else ("fern", GRASS_TINT)
        c.sprite(x, y, z, tex)
    elif block == 60:
        c.box(x, y, z, x + 1, y + 15 / 16, z + 1, "farmland_wet", "dirt", "dirt")
    elif block == 54:
        c.box(x + 1 / 16, y, z + 1 / 16, x + 15 / 16, y + 14 / 16, z + 15 / 16, "planks_oak", "planks_oak", "planks_oak")
        c.box(x + 0.44, y + 0.4, z + 15 / 16, x + 0.56, y + 0.65, z + 1, "iron_block", "iron_block", "iron_block")
    elif block == 118:
        c.box(x, y, z, x + 1, y + 1, z + 1, "cauldron_top", "cauldron_side", "cauldron_side")
    elif block == 145:
        c.box(x + 0.15, y, z + 0.15, x + 0.85, y + 0.25, z + 0.85, "anvil_base", "anvil_base", "anvil_base")
        c.box(x + 0.35, y + 0.25, z + 0.3, x + 0.65, y + 0.6, z + 0.7, "anvil_base", "anvil_base", "anvil_base")
        c.box(x + 0.2, y + 0.6, z + 0.05, x + 0.8, y + 1, z + 0.95, "anvil_top_damaged_0", "anvil_base", "anvil_base")
    elif block == 140:
        c.box(x + 0.31, y, z + 0.31, x + 0.69, y + 0.38, z + 0.69, "flower_pot", "flower_pot", "flower_pot")
    elif block == 144:
        c.box(x + 0.25, y, z + 0.25, x + 0.75, y + 0.5, z + 0.75, "bone_block_side" if False else "quartz_block_top",
              "quartz_block_top", "quartz_block_top")
    elif block == 96:
        c.box(x, y, z, x + 1, y + 0.19, z + 1, "trapdoor", "trapdoor", "trapdoor")


def load(name):
    tag = nbt.read_compressed(os.path.join(STRUCTURES, name + ".schematic"))
    w, h, l = tag["Width"], tag["Height"], tag["Length"]
    ids, data = tag["Blocks"], tag["Data"]
    ground = tag.get("EditorGround", 0)
    blocks = {}
    for y in range(h):
        for z in range(l):
            for x in range(w):
                i = (y * l + z) * w + x
                if ids[i]:
                    blocks[(x, y - ground, z)] = (ids[i], data[i])
    lowest = min((y for (_, y, _) in blocks), default=0)
    shifted = {(x, y - lowest, z): b for (x, y, z), b in blocks.items()}
    return {"w": w, "l": l, "h": h, "blocks": shifted}


def render(name, jar, size=20):
    return draw(load(name), size, Textures(jar))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--minecraft-jar", required=True)
    parser.add_argument("--size", type=int, default=20, help="pixels along a block's edge")
    parser.add_argument("out")
    parser.add_argument("names", nargs="+")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    textures = Textures(args.minecraft_jar)
    for name in args.names:
        draw(load(name), args.size, textures).save(os.path.join(args.out, name.replace("/", "_") + ".png"))


if __name__ == "__main__":
    main()
