#!/usr/bin/env python3
"""Generates the buildings of War of the Gods as MCEdit .schematic files, one set per faction.

    python3 tools/structures/generate.py [--preview DIR]

Every building comes in two styles: Saradomin, plain and human (stone, timber, white plaster, blue cloth), and
Zamorak, sinister (nether brick, obsidian, black clay, red cloth, iron bars, skulls and spikes). For each style it
writes into src/main/resources/assets/warofthegods/structures/<faction>/:

    <type>_l<level>.schematic       the finished building at each level (the town center has three, the rest two)
    <type>_l<level>_d<n>.schematic  the same, damaged: n = 1, 2, 3 as its health falls below 75%, 50% and 25%
    <type>_stage<n>.schematic       a building site, n = 1, 2, 3 as it goes from a foundation and a wooden frame to
                                    nearly finished walls in scaffolding
    <type>_ruin.schematic           what is left when it is destroyed

All files of one building share the same width, length, height and ground layer, so any of them can be pasted over
any other. At rotation 0 a building's front, its z = 0 side, faces north; doors and gates are on that side.

The structures are written from code rather than drawn, so they stay consistent across levels, damage and styles;
open the files in the beta map editor or MCEdit to look at them, or pass --preview to get isometric pictures.
"""

import argparse
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import nbt  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "warofthegods", "structures")

AIR = (0, 0)

# Block ids of Minecraft 1.7.10 used below.
STONE, GRASS, DIRT, COBBLE, PLANKS, WATER = 1, 2, 3, 4, 5, 9
SAND, GRAVEL, GOLD_ORE, IRON_ORE, COAL_ORE, LOG = 12, 13, 14, 15, 16, 17
GLASS, LAPIS_ORE, LAPIS, WOOL, DEAD_BUSH = 20, 21, 22, 35, 32
GOLD_BLOCK, IRON_BLOCK, SLAB, BRICK, BOOKSHELF, MOSSY, OBSIDIAN, TORCH = 41, 42, 44, 45, 47, 48, 49, 50
OAK_STAIRS, CHEST, DIAMOND_ORE, WORKBENCH, WHEAT, FARMLAND, FURNACE = 53, 54, 56, 58, 59, 60, 61
DOOR, LADDER, RAIL, COBBLE_STAIRS, IRON_DOOR, REDSTONE_ORE, REDSTONE_TORCH = 64, 65, 66, 67, 71, 73, 76
FENCE, PUMPKIN, NETHERRACK, SOUL_SAND, GLOWSTONE, JACK = 85, 86, 87, 88, 89, 91
STAINED_GLASS, TRAPDOOR, STONE_BRICK, IRON_BARS, PANE = 95, 96, 98, 101, 102
FENCE_GATE, BRICK_STAIRS, STONE_BRICK_STAIRS, NETHER_BRICK, NETHER_FENCE, NETHER_STAIRS, NETHER_WART = (
    107, 108, 109, 112, 113, 114, 115)
CAULDRON, WOOD_SLAB, SPRUCE_STAIRS, COBBLE_WALL, FLOWER_POT, CARROTS, POTATOES = 118, 126, 134, 139, 140, 141, 142
SKULL, ANVIL, REDSTONE_BLOCK, QUARTZ, CLAY, PANE_STAINED, LOG2 = 144, 145, 152, 155, 159, 160, 162
DARK_OAK_STAIRS, HAY, CARPET, COAL_BLOCK, COBWEB, FIRE, TALL_GRASS = 164, 170, 171, 173, 30, 51, 31
LEAVES = 18

# Wool and stained clay colours.
WHITE, ORANGE, LIGHT_GRAY, GRAY, BLUE, BROWN, RED, BLACK = 0, 1, 8, 7, 11, 12, 14, 15

# Stairs metadata: which way the stairs climb.
UP_EAST, UP_WEST, UP_SOUTH, UP_NORTH = 0, 1, 2, 3
UPSIDE_DOWN = 4

# Blocks that hang on others, are thin, or are furniture: never left floating in damage and ruins.
DECOR = {TORCH, REDSTONE_TORCH, PANE, PANE_STAINED, IRON_BARS, FLOWER_POT, SKULL, CHEST, WORKBENCH, FURNACE,
         CAULDRON, ANVIL, LADDER, WHEAT, CARROTS, POTATOES, NETHER_WART, DEAD_BUSH, RAIL, CARPET, TRAPDOOR,
         FENCE_GATE, BOOKSHELF, HAY, JACK, PUMPKIN, FIRE, TALL_GRASS}
DOORS = {DOOR, IRON_DOOR}


class Palette:
    def __init__(self, name, **blocks):
        self.name = name
        for key, value in blocks.items():
            setattr(self, key, value)


SARADOMIN = Palette(
    "saradomin",
    found=(STONE_BRICK, 0),          # foundations and stone walls
    base=(COBBLE, 0),                # plinths, chimneys, tower corners
    wall=(CLAY, WHITE),              # white plaster
    frame=(LOG, 0),                  # oak timber frame, upright
    frame_x=(LOG, 4),
    frame_z=(LOG, 8),
    planks=(PLANKS, 0),
    floor=(PLANKS, 0),
    plaza=(STONE_BRICK, 0),
    path=(GRAVEL, 0),
    roof=SPRUCE_STAIRS,
    roof_full=(PLANKS, 1),
    roof_slab=(WOOD_SLAB, 1),
    trim=(WOOL, BLUE),
    banner=(WOOL, BLUE),
    banner2=(WOOL, WHITE),
    emblem=(GOLD_BLOCK, 0),
    window=(PANE, 0),
    window2=(PANE_STAINED, BLUE),
    fence=(FENCE, 0),
    low_wall=(COBBLE_WALL, 0),
    light=TORCH,
    lamp=(GLOWSTONE, 0),
    door=DOOR,
    stairs=STONE_BRICK_STAIRS,
    tent=(WOOL, WHITE),
    tent2=(WOOL, BLUE),
    quarry_stone=(STONE, 0),
    topper=(FENCE, 0),
)

ZAMORAK = Palette(
    "zamorak",
    found=(NETHER_BRICK, 0),
    base=(OBSIDIAN, 0),
    wall=(CLAY, BLACK),
    frame=(LOG2, 1),                 # dark oak
    frame_x=(LOG2, 5),
    frame_z=(LOG2, 9),
    planks=(PLANKS, 5),
    floor=(PLANKS, 5),
    plaza=(NETHER_BRICK, 0),
    path=(SOUL_SAND, 0),
    roof=NETHER_STAIRS,
    roof_full=(NETHER_BRICK, 0),
    roof_slab=(SLAB, 6),
    trim=(WOOL, RED),
    banner=(WOOL, RED),
    banner2=(WOOL, BLACK),
    emblem=(REDSTONE_BLOCK, 0),
    window=(IRON_BARS, 0),
    window2=(PANE_STAINED, RED),
    fence=(NETHER_FENCE, 0),
    low_wall=(NETHER_FENCE, 0),
    light=REDSTONE_TORCH,
    lamp=(GLOWSTONE, 0),
    door=IRON_DOOR,
    stairs=NETHER_STAIRS,
    tent=(WOOL, BLACK),
    tent2=(WOOL, RED),
    quarry_stone=(STONE, 0),
    topper=(NETHER_FENCE, 0),
)

FACTIONS = [SARADOMIN, ZAMORAK]


def zam(p):
    return p is ZAMORAK


class Struct:
    """Blocks of a structure. y is relative to the ground layer: 0 replaces the terrain's top block, below are
    foundations. Everything not set is air, which clears what was in the world there."""

    def __init__(self, width, length, top, ground):
        self.w = width
        self.l = length
        self.top = top
        self.g = ground
        self.blocks = {}

    def inside(self, x, y, z):
        return 0 <= x < self.w and 0 <= z < self.l and -self.g <= y <= self.top

    def set(self, x, y, z, block):
        if not self.inside(x, y, z):
            raise ValueError("(%d, %d, %d) is outside %dx%d, -%d..%d" % (x, y, z, self.w, self.l, self.g, self.top))
        if block is None or block[0] == 0:
            self.blocks.pop((x, y, z), None)
        else:
            self.blocks[(x, y, z)] = block

    def get(self, x, y, z):
        return self.blocks.get((x, y, z), AIR)

    def fill(self, x0, y0, z0, x1, y1, z1, block):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, block)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, None)

    def walls(self, x0, z0, x1, z1, y0, y1, block):
        """The four walls of a box, without its inside."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, z0, block)
                self.set(x, y, z1, block)
            for z in range(z0, z1 + 1):
                self.set(x0, y, z, block)
                self.set(x1, y, z, block)

    def pillars(self, x0, z0, x1, z1, y0, y1, block):
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            self.fill(x, y0, z, x, y1, z, block)

    def copy(self):
        other = Struct(self.w, self.l, self.top, self.g)
        other.blocks = dict(self.blocks)
        return other

    def height_above_ground(self):
        return max([y for (_, y, _) in self.blocks if y > 0] or [0])

    def save(self, path, name):
        width, length, height = self.w, self.l, self.g + self.top + 1
        ids = bytearray(width * length * height)
        data = bytearray(width * length * height)
        for (x, y, z), (block, meta) in self.blocks.items():
            index = ((y + self.g) * length + z) * width + x
            ids[index] = block
            data[index] = meta
        nbt.write_compressed(path, {
            "Width": nbt.Tagged(nbt.SHORT, width),
            "Height": nbt.Tagged(nbt.SHORT, height),
            "Length": nbt.Tagged(nbt.SHORT, length),
            "Materials": "Alpha",
            "Blocks": bytes(ids),
            "Data": bytes(data),
            "Entities": [],
            "TileEntities": [],
            "EditorGround": self.g,
            "EditorName": name,
        })


def stairs(block_id, climbs, upside_down=False):
    return (block_id, climbs + (UPSIDE_DOWN if upside_down else 0))


def gable_x(s, x0, x1, z0, z1, y, roof, ridge, end=None):
    """A roof whose ridge runs along x over z0..z1, starting at y; end fills the gables at x0 and x1. Returns the y
    of its ridge."""
    i = 0
    while True:
        north, south = z0 + i, z1 - i
        level = y + i
        if north > south:
            return level - 1
        if north == south:
            s.fill(x0, level, north, x1, level, north, ridge)
            return level
        for x in range(x0, x1 + 1):
            s.set(x, level, north, stairs(roof, UP_SOUTH))
            s.set(x, level, south, stairs(roof, UP_NORTH))
        if end is not None:
            for z in range(north + 1, south):
                s.set(x0, level, z, end)
                s.set(x1, level, z, end)
        i += 1


def gable_z(s, z0, z1, x0, x1, y, roof, ridge, end=None):
    """A roof whose ridge runs along z over x0..x1, starting at y; end fills the gables at z0 and z1."""
    i = 0
    while True:
        west, east = x0 + i, x1 - i
        level = y + i
        if west > east:
            return level - 1
        if west == east:
            s.fill(west, level, z0, west, level, z1, ridge)
            return level
        for z in range(z0, z1 + 1):
            s.set(west, level, z, stairs(roof, UP_EAST))
            s.set(east, level, z, stairs(roof, UP_WEST))
        if end is not None:
            for x in range(west + 1, east):
                s.set(x, level, z0, end)
                s.set(x, level, z1, end)
        i += 1


def hip(s, x0, z0, x1, z1, y, roof, cap):
    """A pyramid roof over x0..x1, z0..z1 starting at y, closed with cap. Returns the y of the cap."""
    i = 0
    while True:
        a, b, c, d = x0 + i, x1 - i, z0 + i, z1 - i
        level = y + i
        if a > b or c > d:
            return level - 1
        if a == b or c == d:
            s.fill(a, level, c, b, level, d, cap)
            return level
        for x in range(a, b + 1):
            s.set(x, level, c, stairs(roof, UP_SOUTH))
            s.set(x, level, d, stairs(roof, UP_NORTH))
        for z in range(c + 1, d):
            s.set(a, level, z, stairs(roof, UP_EAST))
            s.set(b, level, z, stairs(roof, UP_WEST))
        i += 1


def door(s, p, x, y, z):
    """A door in a wall that runs along x, opening to the north."""
    s.set(x, y, z, (p.door, 1))
    s.set(x, y + 1, z, (p.door, 8))


def torch(s, p, x, y, z, facing):
    """A torch (Saradomin) or redstone torch (Zamorak): facing 'up', or away from the wall it hangs on."""
    meta = {"east": 1, "west": 2, "south": 3, "north": 4, "up": 5}[facing]
    s.set(x, y, z, (p.light, meta))


def spikes(s, p, points):
    """Fence posts sticking up as spikes, Zamorak only."""
    if zam(p):
        for x, y, z in points:
            s.set(x, y, z, (NETHER_FENCE, 0))


def skull(s, x, y, z):
    s.set(x, y, z, (SKULL, 1))


def timber_walls(s, p, x0, z0, x1, z1, y0, y1, plinth=True):
    """Walls of plaster in a timber frame: posts at the corners, a beam along the top and a stone plinth."""
    s.walls(x0, z0, x1, z1, y0, y1, p.wall)
    if plinth:
        s.walls(x0, z0, x1, z1, y0, y0, p.base if not zam(p) else (CLAY, RED))
    for x in range(x0, x1 + 1):
        s.set(x, y1, z0, p.frame_x)
        s.set(x, y1, z1, p.frame_x)
    for z in range(z0, z1 + 1):
        s.set(x0, y1, z, p.frame_z)
        s.set(x1, y1, z, p.frame_z)
    s.pillars(x0, z0, x1, z1, y0, y1, p.frame if not zam(p) else p.base)


def foundation(s, p, x0, z0, x1, z1, top=0):
    s.fill(x0, -s.g, z0, x1, top, z1, p.found)


# ---------------------------------------------------------------- buildings


def house(p, level):
    s = Struct(7, 7, 12, 1)
    foundation(s, p, 0, 0, 6, 6)
    s.fill(1, 0, 1, 5, 0, 5, p.floor)
    top = 3 if level == 1 else 6
    timber_walls(s, p, 0, 0, 6, 6, 1, top)
    if level == 2:
        # A floor between the storeys, its beam showing outside.
        for x in range(1, 6):
            s.set(x, 3, 0, p.frame_x)
            s.set(x, 3, 6, p.frame_x)
        for z in range(1, 6):
            s.set(0, 3, z, p.frame_z)
            s.set(6, 3, z, p.frame_z)
        s.fill(1, 3, 1, 5, 3, 5, p.floor)
        s.clear(4, 3, 4, 5, 3, 5)
    door(s, p, 3, 1, 0)
    rows = [2] if level == 1 else [2, 5]
    for y in rows:
        for x, z in ((1, 0), (5, 0), (3, 6), (1, 6), (5, 6)):
            s.set(x, y, z, p.window)
        for z in (2, 4):
            s.set(0, y, z, p.window)
            s.set(6, y, z, p.window)
    ridge = gable_x(s, 0, 6, 0, 6, top + 1, p.roof, p.roof_full, end=p.wall if not zam(p) else p.planks)
    # An attic window in each gable.
    s.set(0, top + 2, 3, p.window)
    s.set(6, top + 2, 3, p.window)
    # A chimney at the back.
    s.fill(1, 1, 5, 1, ridge + 1, 5, p.base)
    if zam(p):
        s.set(1, ridge + 2, 5, (NETHER_FENCE, 0))
        spikes(s, p, [(0, ridge + 1, 3), (6, ridge + 1, 3)])
        # Red cloth over the door.
        s.set(2, 3 if level == 1 else 6, 0, p.trim)
        s.set(4, 3 if level == 1 else 6, 0, p.trim)
    else:
        # Blue shutters beside the front windows.
        s.set(2, 2, 0, p.trim)
        s.set(4, 2, 0, p.trim)
        if level == 2:
            s.set(2, 5, 0, p.trim)
            s.set(4, 5, 0, p.trim)
    # Inside: a bench, a chest and a light.
    s.set(5, 1, 5, (WORKBENCH, 0))
    s.set(4, 1, 5, (CHEST, 2))
    torch(s, p, 3, 2, 5, "north")
    if level == 2:
        torch(s, p, 3, 5, 5, "north")
        s.set(1, 4, 1, (BOOKSHELF, 0))
    return s


def thatch_x(s, x0, x1, z0, z1, y, block, end):
    """A steep roof of full blocks, such as hay for thatch or wool for a tent, its ridge along x over z0..z1, from y;
    end closes the gables. Returns the y of the ridge."""
    i = 0
    while True:
        north, south = z0 + i, z1 - i
        level = y + i
        if north > south:
            return level - 1
        s.fill(x0, level, north, x1, level, north, block)
        s.fill(x0, level, south, x1, level, south, block)
        if north + 1 <= south - 1:
            for z in range(north + 1, south):
                s.set(x0, level, z, end)
                s.set(x1, level, z, end)
        if north >= south - 1:
            return level
        i += 1


def hut(p, level, x0, z0, x1, z1, door_x, door_z):
    """One hut of the town center's camp: a log cabin with a thatched roof in Age I (a tent of cloth for Zamorak), a
    timber and plaster house under a timber roof in Age II, and a taller house on a stone plinth in Age III. Its ridge
    runs along x; door_x, door_z is its door in the south or north wall."""
    s = p._s
    top = {1: 3, 2: 4, 3: 5}[level]
    s.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, p.floor)
    if level == 1:
        if zam(p):
            # A war tent: black cloth over dark posts.
            s.walls(x0, z0, x1, z1, 1, top - 1, p.tent)
            s.pillars(x0, z0, x1, z1, 1, top, p.frame)
            ridge = thatch_x(s, x0, x1, z0, z1, top, p.tent, p.tent)
            s.fill(x0, ridge, (z0 + z1) // 2, x1, ridge, (z0 + z1) // 2, p.tent2)
        else:
            # A log cabin under thatch.
            s.walls(x0, z0, x1, z1, 1, top, p.planks)
            s.pillars(x0, z0, x1, z1, 1, top, p.frame)
            for x in range(x0, x1 + 1):
                s.set(x, top, z0, p.frame_x)
                s.set(x, top, z1, p.frame_x)
            ridge = thatch_x(s, x0, x1, z0, z1, top + 1, (HAY, 0), p.planks)
    else:
        timber_walls(s, p, x0, z0, x1, z1, 1, top, plinth=True)
        if level == 3:
            s.walls(x0, z0, x1, z1, 1, 1, p.found)
            s.walls(x0, z0, x1, z1, 2, 2, p.found if zam(p) else p.base)
        ridge = gable_x(s, x0, x1, z0, z1, top + 1, p.roof, p.roof_full, end=p.planks)
    # Windows along the long walls.
    for x in range(x0 + 2, x1 - 1, 2):
        for z in (z0, z1):
            if (x, z) != (door_x, door_z) and abs(x - door_x) > 1:
                s.set(x, 2, z, p.window if level > 1 or not zam(p) else p.tent2)
                if level == 3:
                    s.set(x, 4, z, p.window)
    s.set(door_x, 1, door_z, AIR)
    s.set(door_x, 2, door_z, AIR)
    if level > 1:
        s.set(door_x, 1, door_z, (p.door, 1 if door_z == z0 else 3))
        s.set(door_x, 2, door_z, (p.door, 8))
    # Inside: a bed of straw, a chest and a light.
    s.set(x0 + 1, 1, z0 + 1 if door_z == z1 else z1 - 1, (HAY, 0))
    s.set(x1 - 1, 1, z0 + 1 if door_z == z1 else z1 - 1, (CHEST, 2))
    torch(s, p, (x0 + x1) // 2, 2, z0 + 1 if door_z == z1 else z1 - 1, "south" if door_z == z1 else "north")
    return ridge


def granary(p, level, cx, cz):
    """A round granary: a domed haystack on a ring of stakes in Age I, a round store of planks with a cone roof in
    Age II, and the same on a stone base, taller, in Age III."""
    s = p._s

    def ring(radius, y, block, fill=False):
        for x in range(cx - radius, cx + radius + 1):
            for z in range(cz - radius, cz + radius + 1):
                d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
                if d <= radius + 0.3 and (fill or d > radius - 0.8):
                    s.set(x, y, z, block)

    if level == 1:
        for y in (1, 2):
            ring(2, y, (HAY, 0), fill=True)
        ring(1, 3, (HAY, 0), fill=True)
        s.set(cx, 4, cz, (HAY, 0))
        for x, z in ((cx - 2, cz - 2), (cx + 2, cz - 2), (cx - 2, cz + 2), (cx + 2, cz + 2)):
            s.set(x, 1, z, p.fence)
        return
    base = 2 if level == 3 else 1
    if level == 3:
        ring(2, 1, p.found)
    wall_top = base + 3
    for y in range(base, wall_top + 1):
        ring(2, y, p.planks if not zam(p) else (PLANKS, 5))
    s.fill(cx - 1, 1, cz - 1, cx + 1, 1, cz + 1, (HAY, 0))
    ring(2, wall_top + 1, p.roof_slab, fill=True)
    ring(1, wall_top + 2, p.roof_full, fill=True)
    s.set(cx, wall_top + 3, cz, p.topper)
    s.set(cx, wall_top + 4, cz, p.banner if level == 3 else p.topper)
    # A hatch facing the fire.
    s.set(cx - 2, base + 1, cz, AIR)
    s.set(cx - 2, base, cz, AIR)


def town_center(p, level):
    """The town center as a camp: a fenced yard round a campfire, with a longhouse, a lodge, a granary and a woodpile.
    It grows with the ages, from log cabins under thatch to timber and plaster houses behind a palisade, and to stone
    footed halls behind a low wall."""
    s = Struct(17, 17, 16, 2)
    p._s = s
    s.fill(0, -2, 0, 16, -1, 16, (DIRT, 0))
    s.fill(0, 0, 0, 16, 0, 16, (GRASS, 0))
    # Trodden ground round the fire and a path in from the gate.
    for x in range(4, 13):
        for z in range(4, 13):
            if (x - 8) ** 2 + (z - 8) ** 2 <= 12:
                s.set(x, 0, z, p.path if level > 1 or zam(p) else (DIRT, 0))
    s.fill(7, 0, 0, 9, 0, 5, p.path)

    # The fence round the yard, open at the gate in front and at a gap in each side.
    for i in range(17):
        for x, z in ((i, 0), (i, 16), (0, i), (16, i)):
            gap = (z == 0 and 7 <= x <= 9) or (z == 16 and 7 <= x <= 9) or (x in (0, 16) and 7 <= z <= 9)
            if gap:
                continue
            if level == 3:
                s.set(x, 1, z, p.low_wall)
                s.set(x, 2, z, p.fence)
            else:
                s.set(x, 1, z, p.fence)
                if level == 2:
                    s.set(x, 2, z, p.fence)
    # Posts by the gaps, lit; at the gate taller, with the god's colors.
    posts = [(6, 0), (10, 0), (6, 16), (10, 16), (0, 6), (0, 10), (16, 6), (16, 10)]
    if level > 1:
        posts += [(0, 0), (16, 0), (0, 16), (16, 16)]
    for x, z in posts:
        height = 3 if level == 1 else 4
        s.fill(x, 1, z, x, height, z, p.frame if level < 3 else p.base)
        if zam(p):
            skull(s, x, height + 1, z)
        else:
            s.set(x, height + 1, z, (p.light, 5))
    for x in (6, 10):
        s.set(x, 3 if level == 1 else 4, 1, p.banner)
        if level > 1:
            s.set(x, 2, 1, p.banner)

    # The campfire in the middle, in a ring of stones, with a spit across it.
    s.fill(7, 0, 7, 9, 0, 9, (COBBLE, 0) if not zam(p) else (NETHER_BRICK, 0))
    s.set(8, 0, 8, (NETHERRACK, 0))
    s.set(8, 1, 8, (FIRE, 0))
    for x in (6, 10):
        s.set(x, 1, 8, p.low_wall if not zam(p) else (NETHER_FENCE, 0))
        s.set(x, 2, 8, p.low_wall if not zam(p) else (NETHER_FENCE, 0))
    for x in (7, 8, 9):
        s.set(x, 3, 8, (IRON_BARS, 0))
    for x, z in ((6, 6), (10, 6), (6, 10), (10, 10)):
        s.set(x, 1, z, (SLAB, 3) if not zam(p) else (SLAB, 6))

    # The longhouse at the back, its door facing the fire.
    hut(p, level, 1, 11, 9, 15, 5, 11)
    # The lodge at the front left.
    hut(p, level, 1, 2, 5, 6, 3, 6)
    # The granary at the back right.
    granary(p, level, 13, 13)

    # The woodpile and stores at the front right.
    for x in range(11, 15):
        s.set(x, 1, 2, (LOG, 4) if not zam(p) else (LOG2, 5))
        s.set(x, 1, 3, (LOG, 4) if not zam(p) else (LOG2, 5))
        if x < 14:
            s.set(x, 2, 2, (LOG, 4) if not zam(p) else (LOG2, 5))
    s.set(14, 1, 5, (CHEST, 2))
    s.set(13, 1, 5, (WORKBENCH, 0))
    if level > 1:
        # A lean-to over the woodpile.
        for x in (11, 14):
            s.fill(x, 1, 4, x, 3, 4, p.fence)
        s.fill(11, 4, 1, 14, 4, 4, p.roof_slab)
        s.set(12, 1, 5, (CAULDRON, 3))
    if level == 3:
        s.set(11, 1, 5, (ANVIL, 0))
        # A flag over the gate.
        s.fill(8, 1, 14, 8, 1, 14, p.found)
        s.fill(15, 1, 8, 15, 7, 8, p.fence if not zam(p) else (NETHER_FENCE, 0))
        s.fill(14, 6, 8, 14, 7, 8, p.banner)
        s.set(14, 5, 8, p.banner2 if not zam(p) else p.emblem)
    if zam(p):
        # Skulls on stakes round the fire.
        for x, z in ((5, 5), (11, 5), (5, 11), (11, 11)):
            if s.get(x, 1, z) == AIR:
                s.set(x, 1, z, (NETHER_FENCE, 0))
                skull(s, x, 2, z)
    else:
        # A barrel and a cart by the lodge.
        s.set(6, 1, 3, (CAULDRON, 0))
        s.set(2, 1, 8, (HAY, 0))
    del p._s
    return s


# ---------------------------------------------------------------- neutral map features


def settlement():
    """A settlement no team holds: the town center's camp of Age I left to rot, where a town center can go. Its shacks
    stand roofless and holed, the fence has fallen in places, the fire is long cold and the stores are gone, and a
    white flag on a post marks free land. It has the town center's size, so either can be pasted over the other."""
    rng = random.Random(77)
    s = damaged(town_center(SARADOMIN, 1), 3, 7701)
    for (x, y, z), block in list(s.blocks.items()):
        if y <= 0:
            continue
        if block[0] in (FIRE, WOOL, CHEST, WORKBENCH, TORCH, CAULDRON, IRON_BARS):
            s.set(x, y, z, None)
        elif block[0] == FENCE and rng.random() < 0.45:
            s.set(x, y, z, None)
        elif block[0] == HAY and rng.random() < 0.6:
            s.set(x, y, z, None)
        elif block[0] == PLANKS and rng.random() < 0.12:
            s.set(x, y, z, (LOG, 0))
    # Grass grows back over the trodden ground, and the hearth is cold ash and stone.
    for x in range(17):
        for z in range(17):
            if s.get(x, 0, z) == (DIRT, 0) and rng.random() < 0.5:
                s.set(x, 0, z, (GRASS, 0))
    s.set(8, 0, 8, (GRAVEL, 0))
    s.set(8, 1, 8, None)
    for (x, y, z), block in list(s.blocks.items()):
        if block[0] == COBBLE and rng.random() < 0.4:
            s.set(x, y, z, (MOSSY, 0))
    # Weeds and tall grass in the yard.
    for _ in range(40):
        x, z = rng.randrange(1, 16), rng.randrange(1, 16)
        if s.get(x, 1, z) == AIR and s.get(x, 0, z) == (GRASS, 0):
            s.set(x, 1, z, (TALL_GRASS, 1))
    # A marker post with a white flag at the gate: free land.
    s.fill(8, 1, 1, 8, 5, 1, (FENCE, 0))
    s.set(9, 5, 1, (WOOL, WHITE))
    s.set(9, 4, 1, (WOOL, WHITE))
    s.set(8, 0, 1, (COBBLE, 0))
    return s


def gold_mine(depleted):
    """A rocky outcrop with gold showing in it, which workers mine for gold; once mined out, only stone and rubble
    are left. Workers stand round it, so it has nothing to climb."""
    rng = random.Random(4242)
    s = Struct(7, 7, 6, 1)
    s.fill(0, -1, 0, 6, -1, 6, (STONE, 0))
    heights = {}
    for x in range(7):
        for z in range(7):
            d = ((x - 3) ** 2 + (z - 3) ** 2) ** 0.5
            h = max(0, round(4 - d * 1.15 + rng.uniform(-0.4, 0.4)))
            heights[(x, z)] = h
            s.set(x, 0, z, (STONE, 0) if d < 3.3 else (GRAVEL, 0) if rng.random() < 0.5 else (GRASS, 0))
            for y in range(1, h + 1):
                s.set(x, y, z, rng.choice([(STONE, 0), (STONE, 0), (COBBLE, 0), (MOSSY, 0)]))
    # Gold on the faces of the rock.
    for (x, z), h in heights.items():
        for y in range(1, h + 1):
            exposed = any(heights.get((x + dx, z + dz), 0) < y for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) \
                or y == h
            if exposed and rng.random() < 0.45:
                if depleted:
                    if rng.random() < 0.5:
                        s.set(x, y, z, (COBBLE, 0))
                else:
                    s.set(x, y, z, (GOLD_ORE, 0))
    if depleted:
        # Rubble round the foot.
        for x, z in ((0, 3), (6, 2), (3, 6), (1, 1)):
            s.set(x, 1, z, (GRAVEL, 0))
    else:
        s.set(3, max(heights.values()) + 1, 3, (GOLD_ORE, 0))
    return s


def bridge():
    """A stone bridge over the river, 17 long and 7 wide with its parapets. The deck lies level with the banks, so
    units walk straight across; two arches on a middle pier carry it over the water, which runs 7 wide and 2 deep
    under it. It cannot be broken."""
    s = Struct(7, 17, 3, 3)
    deck = (STONE_BRICK, 0)
    mossy = (STONE_BRICK, 1)
    cracked = (STONE_BRICK, 2)
    rng = random.Random(77)
    for z in range(17):
        water = 5 <= z <= 11
        for x in range(7):
            # Below the deck: the river bed, water in the channel, and the arches and pier.
            s.set(x, -3, z, (STONE_BRICK, 0) if not water or z == 8 else (GRAVEL, 0))
            for y in (-2, -1):
                if not water or z == 8:
                    s.set(x, y, z, rng.choice([deck, deck, mossy]) if water or z in (4, 12) else (DIRT, 0))
                else:
                    s.set(x, y, z, (WATER, 0))
            s.set(x, 0, z, rng.choice([deck, deck, deck, cracked]) if 1 <= x <= 5 else deck)
    # The arches: upside down steps against the abutments and the pier.
    for x in range(7):
        s.set(x, -1, 5, (STONE_BRICK_STAIRS, UP_NORTH | UPSIDE_DOWN))
        s.set(x, -1, 7, (STONE_BRICK_STAIRS, UP_SOUTH | UPSIDE_DOWN))
        s.set(x, -1, 9, (STONE_BRICK_STAIRS, UP_NORTH | UPSIDE_DOWN))
        s.set(x, -1, 11, (STONE_BRICK_STAIRS, UP_SOUTH | UPSIDE_DOWN))
    # Parapets along both sides, with posts and lamps at the ends and over the pier.
    for x in (0, 6):
        for z in range(17):
            s.set(x, 1, z, deck)
            s.set(x, 2, z, (SLAB, 5))
        for z in (0, 8, 16):
            s.set(x, 2, z, deck)
            s.set(x, 3, z, (TORCH, 5))
    return s


NEUTRAL = {
    "bridge": bridge,
    "settlement": settlement,
    "gold_mine": lambda: gold_mine(False),
    "gold_mine_depleted": lambda: gold_mine(True),
}


def barracks(p, level):
    s = Struct(13, 11, 12, 1)
    foundation(s, p, 0, 0, 12, 10)
    s.fill(0, 0, 0, 12, 0, 4, p.path)
    top = 4 if level == 1 else 7
    # The hall at the back.
    s.fill(0, 0, 5, 12, 0, 10, p.floor)
    timber_walls(s, p, 0, 5, 12, 10, 1, top)
    for x in (4, 8):
        s.fill(x, 1, 5, x, top, 5, p.frame if not zam(p) else p.base)
    door(s, p, 6, 1, 5)
    for x in (2, 10):
        s.fill(x, 2, 5, x, 3, 5, p.window)
    for z in (7, 8):
        s.set(0, 2, z, p.window)
        s.set(12, 2, z, p.window)
    for x in (2, 6, 10):
        s.set(x, 2, 10, p.window)
    if level == 2:
        for x in (2, 6, 10):
            s.set(x, 5, 5, p.window)
            s.set(x, 5, 10, p.window)
        s.fill(1, 4, 6, 11, 4, 9, p.floor)
        s.clear(10, 4, 8, 11, 4, 9)
    ridge = gable_x(s, 0, 12, 5, 10, top + 1, p.roof, p.roof_full, end=p.planks)
    for x in range(0, 13):
        s.set(x, ridge, 7, p.roof_full)
        s.set(x, ridge, 8, p.roof_full)
    if zam(p):
        spikes(s, p, [(x, ridge + 1, 7) for x in (0, 4, 8, 12)])
    # Banners on the front.
    s.set(5, top - 1, 5, p.banner)
    s.set(7, top - 1, 5, p.banner)
    # Inside: weapon racks and beds of straw.
    for x in (1, 11):
        s.set(x, 1, 9, (HAY, 0))
    s.set(3, 1, 9, (CHEST, 2))
    s.set(9, 1, 9, (ANVIL, 0))
    torch(s, p, 6, 3, 9, "north")
    torch(s, p, 6, 3, 6, "south")

    # The training yard in front, fenced, with a gate in the middle.
    for z in range(0, 5):
        s.set(0, 1, z, p.fence)
        s.set(12, 1, z, p.fence)
    for x in range(0, 13):
        if not 5 <= x <= 7:
            s.set(x, 1, 0, p.fence)
    s.set(5, 1, 0, p.fence)
    s.set(7, 1, 0, p.fence)
    s.set(5, 2, 0, (p.light, 5))
    s.set(7, 2, 0, (p.light, 5))
    # Two dummies to swing at.
    for x in (2, 4):
        s.set(x, 1, 2, p.fence)
        s.set(x, 2, 2, (HAY, 0))
        if zam(p):
            skull(s, x, 3, 2)
        else:
            s.set(x, 3, 2, (PUMPKIN, 2))
    # A target to shoot at.
    s.fill(9, 1, 3, 11, 3, 3, (WOOL, WHITE) if not zam(p) else (WOOL, BLACK))
    s.set(10, 2, 3, (WOOL, RED))
    s.set(10, 1, 3, p.planks)
    if zam(p):
        s.set(0, 2, 0, (SKULL, 1))
        s.set(12, 2, 0, (SKULL, 1))
    if level == 2:
        # A flag over the yard.
        s.fill(12, 2, 4, 12, 7, 4, p.fence)
        s.fill(11, 6, 4, 11, 7, 4, p.banner)
        s.set(11, 1, 1, (HAY, 0))
        s.set(1, 1, 1, (CHEST, 3))
    return s


def archery_range(p, level):
    """Trains archers: a shooting shed at the back, open to the front, and lanes of targets to shoot at in front."""
    s = Struct(13, 11, 9, 1)
    foundation(s, p, 0, 0, 12, 10)
    s.fill(0, 0, 0, 12, 0, 10, p.path)
    top = 3 if level == 1 else 4
    # The shed: walls at the back and sides, open to the lanes, posts holding up a lean-to roof.
    s.fill(0, 0, 7, 12, 0, 10, p.floor)
    s.fill(0, 1, 10, 12, top, 10, p.wall)
    s.fill(0, 1, 7, 0, top, 10, p.wall)
    s.fill(12, 1, 7, 12, top, 10, p.wall)
    for x in (0, 4, 8, 12):
        s.fill(x, 1, 7, x, top, 7, p.frame if not zam(p) else p.base)
        s.fill(x, 1, 10, x, top, 10, p.frame if not zam(p) else p.base)
    for x in range(0, 13):
        s.set(x, top + 1, 7, stairs(p.roof, UP_SOUTH))
        s.set(x, top + 1, 8, p.roof_full)
        s.set(x, top + 2, 9, stairs(p.roof, UP_SOUTH))
        s.set(x, top + 2, 10, p.roof_full)
        s.set(x, top + 1, 9, p.planks)
        s.set(x, top + 1, 10, p.planks)
    s.set(4, 2, 10, p.window)
    s.set(8, 2, 10, p.window)
    # Racks of bows and quivers: chests, hay to sit on, a fletcher's bench.
    s.set(2, 1, 9, (CHEST, 2))
    s.set(10, 1, 9, (WORKBENCH, 0))
    s.set(6, 1, 9, (HAY, 0))
    torch(s, p, 6, 3, 9, "north")
    # A low rail the archers stand behind.
    for x in range(1, 12):
        if x not in (5, 6, 7):
            s.set(x, 1, 6, p.fence)
    # Three lanes of targets: straw butts with painted faces.
    face = (WOOL, WHITE) if not zam(p) else (WOOL, BLACK)
    for x in (2, 6, 10):
        s.set(x, 1, 1, (HAY, 0))
        s.set(x, 2, 1, face)
        s.set(x, 3, 1, (WOOL, RED) if level == 2 else face)
        if zam(p):
            skull(s, x, 4, 1)
    # Banners on the corner posts.
    s.set(0, top, 7, p.banner)
    s.set(12, top, 7, p.banner)
    if zam(p):
        spikes(s, p, [(0, top + 2, 7), (12, top + 2, 7)])
    if level == 2:
        # More targets, and lamps along the lanes.
        for x in (4, 8):
            s.set(x, 1, 2, (HAY, 0))
            s.set(x, 2, 2, face)
        for x in (0, 12):
            s.set(x, 1, 3, p.base)
            s.set(x, 2, 3, p.fence)
            s.set(x, 3, 3, p.lamp)
    return s


def stables(p, level):
    """Trains cavalry and horses: a long stable of stalls at the back, a fenced paddock in front."""
    s = Struct(13, 11, 10, 1)
    foundation(s, p, 0, 0, 12, 10)
    s.fill(0, 0, 0, 12, 0, 4, (GRASS, 0) if not zam(p) else (SOUL_SAND, 0))
    top = 4 if level == 1 else 5
    s.fill(0, 0, 5, 12, 0, 10, p.floor)
    timber_walls(s, p, 0, 5, 12, 10, 1, top)
    # Wide stall doors in front, three across, with half doors of fence gates.
    for x0 in (1, 5, 9):
        s.clear(x0, 1, 5, x0 + 2, 3, 5)
        s.set(x0 + 1, 1, 5, (FENCE_GATE, 0))
        s.set(x0, 1, 5, p.fence)
        s.set(x0 + 2, 1, 5, p.fence)
    # Stalls inside: fences between, hay to eat, a trough of water.
    for x in (4, 8):
        s.fill(x, 1, 6, x, 2, 9, p.fence)
    for x in (2, 6, 10):
        s.set(x, 1, 9, (HAY, 0))
        s.set(x - 1, 1, 9, (CAULDRON, 3))
    s.set(11, 1, 9, (CHEST, 2))
    for z in (7, 8):
        s.set(0, 2, z, p.window)
        s.set(12, 2, z, p.window)
    ridge = gable_x(s, 0, 12, 5, 10, top + 1, p.roof, p.roof_full, end=p.planks)
    for x in range(0, 13):
        s.set(x, ridge, 7, p.roof_full)
        s.set(x, ridge, 8, p.roof_full)
    # A hayloft door over the middle stall.
    s.set(6, top, 5, (HAY, 0))
    torch(s, p, 2, 3, 9, "north")
    torch(s, p, 10, 3, 9, "north")
    # The paddock: a fence round it, a gate in the middle, a trough and a hay bale.
    for z in range(0, 5):
        s.set(0, 1, z, p.fence)
        s.set(12, 1, z, p.fence)
    for x in range(0, 13):
        if x != 6:
            s.set(x, 1, 0, p.fence)
    s.set(6, 1, 0, (FENCE_GATE, 0))
    s.set(2, 1, 2, (CAULDRON, 3))
    s.set(10, 1, 3, (HAY, 0))
    if zam(p):
        spikes(s, p, [(x, ridge + 1, 7) for x in (0, 6, 12)])
        s.set(0, 2, 0, (SKULL, 1))
        s.set(12, 2, 0, (SKULL, 1))
    else:
        s.set(6, ridge + 1, 7, p.topper)
    s.set(5, top - 1, 5, p.banner)
    s.set(7, top - 1, 5, p.banner)
    if level == 2:
        # A weathervane and lamps at the paddock gate.
        s.fill(0, ridge - 1, 7, 0, ridge + 1, 7, p.fence)
        for x in (5, 7):
            s.set(x, 1, 0, p.base)
            s.set(x, 2, 0, p.fence)
            s.set(x, 3, 0, p.lamp)
        for x in (2, 6, 10):
            s.set(x, 5, 5, p.window)
    return s


def storehouse(p, level):
    s = Struct(9, 9, 11, 1)
    foundation(s, p, 0, 0, 8, 8)
    s.fill(1, 0, 1, 7, 0, 7, p.floor)
    top = 4 if level == 1 else 5
    timber_walls(s, p, 0, 0, 8, 8, 1, top)
    for z in (0, 8):
        s.fill(4, 1, z, 4, top, z, p.frame if not zam(p) else p.base)
    # A barn door three wide.
    s.clear(3, 1, 0, 5, 3, 0)
    s.fill(3, 4, 0, 5, 4, 0, p.frame_x)
    for x in (1, 7):
        s.set(x, 2, 8, p.window)
    for z in (3, 5):
        s.set(0, 2, z, p.window)
        s.set(8, 2, z, p.window)
    ridge = gable_z(s, 0, 8, 0, 8, top + 1, p.roof, p.roof_full, end=p.planks)
    # A loft hatch over the door, with hay inside.
    s.set(4, top + 2, 0, AIR)
    s.set(4, top + 2, 1, (HAY, 0))
    s.set(4, top + 3, 0, p.trim)
    if zam(p):
        spikes(s, p, [(4, ridge + 1, 0), (4, ridge + 1, 8)])
    else:
        s.set(4, ridge + 1, 0, (FENCE, 0))
    # Goods: chests, stacked logs and hay.
    for x in (1, 2, 3):
        s.set(x, 1, 7, (CHEST, 2))
    for x in (5, 6, 7):
        s.fill(x, 1, 6, x, 2, 7, (LOG, 4) if not zam(p) else (LOG2, 5))
    s.fill(1, 1, 1, 1, 2, 2, (HAY, 0))
    s.set(7, 1, 1, (CAULDRON, 0))
    torch(s, p, 4, 3, 7, "north")
    if level == 2:
        for x in (1, 2, 3):
            s.set(x, 2, 7, (CHEST, 2))
        s.fill(1, 3, 6, 7, 3, 7, p.planks)
        s.set(7, 1, 2, (CAULDRON, 0))
        s.set(7, 4, 4, p.lamp)
        for z in (3, 5):
            s.set(0, 4, z, p.window)
            s.set(8, 4, z, p.window)
    return s


def farm(p, level):
    s = Struct(11, 11, 4, 1)
    s.fill(0, -1, 0, 10, -1, 10, (DIRT, 0) if not zam(p) else (SOUL_SAND, 0))
    s.walls(0, 0, 10, 10, 0, 0, p.path if not zam(p) else (NETHER_BRICK, 0))
    if zam(p):
        # Soul sand rows of nether wart, with paths of nether brick between and dead bushes about.
        s.fill(1, 0, 1, 9, 0, 9, (SOUL_SAND, 0))
        for z in range(1, 10):
            s.set(5, 0, z, (NETHER_BRICK, 0))
            for x in list(range(1, 5)) + list(range(6, 10)):
                if z % 4 != 0 or level == 2:
                    s.set(x, 1, z, (NETHER_WART, 3 if level == 2 or (x + z) % 3 else 2))
                else:
                    s.set(x, 0, z, (NETHER_BRICK, 0))
        for x, z in ((1, 4), (9, 8)):
            s.set(x, 1, z, (DEAD_BUSH, 0))
    else:
        s.fill(1, 0, 1, 9, 0, 9, (FARMLAND, 7))
        for z in range(1, 10):
            s.set(5, 0, z, (WATER, 0))
            for x in list(range(1, 5)) + list(range(6, 10)):
                crop = WHEAT if z <= 3 else CARROTS if z <= 6 else POTATOES
                s.set(x, 1, z, (crop, 7 if level == 2 or (x + z) % 4 else 5))
    # A fence round it, with a gate in front.
    for x in range(11):
        s.set(x, 1, 0, p.fence)
        s.set(x, 1, 10, p.fence)
    for z in range(11):
        s.set(0, 1, z, p.fence)
        s.set(10, 1, z, p.fence)
    s.set(5, 1, 0, (FENCE_GATE, 0))
    s.set(5, 0, 0, p.planks)
    # A scarecrow in the corner.
    s.set(9, 1, 9, p.fence)
    s.set(9, 2, 9, (HAY, 0) if not zam(p) else (WOOL, BLACK))
    if zam(p):
        skull(s, 9, 3, 9)
    else:
        s.set(9, 3, 9, (JACK, 2))
    if level == 2:
        # Lamp posts at the corners and a second scarecrow.
        for x, z in ((0, 0), (10, 0), (0, 10), (10, 10)):
            s.set(x, 1, z, p.base)
            s.set(x, 2, z, p.fence)
            s.set(x, 3, z, p.lamp)
        s.set(1, 1, 9, p.fence)
        s.set(1, 2, 9, (HAY, 0) if not zam(p) else (WOOL, RED))
        if zam(p):
            skull(s, 1, 3, 9)
        else:
            s.set(1, 3, 9, (PUMPKIN, 2))
    return s


def armory(p, level):
    s = Struct(9, 9, 10, 1)
    foundation(s, p, 0, 0, 8, 8)
    top = 4 if level == 1 else 5
    # The workshop at the back.
    if zam(p):
        s.walls(0, 4, 8, 8, 1, top, p.found)
        s.pillars(0, 4, 8, 8, 1, top, p.base)
    else:
        s.walls(0, 4, 8, 8, 1, top, p.found)
        s.walls(0, 4, 8, 8, 1, 1, p.base)
        s.pillars(0, 4, 8, 8, 1, top, p.frame)
    door(s, p, 4, 1, 4)
    for x in (2, 6):
        s.set(x, 2, 4, p.window)
        s.set(x, 2, 8, p.window)
    s.set(0, 2, 6, p.window)
    s.set(8, 2, 6, p.window)
    ridge = gable_x(s, 0, 8, 4, 8, top + 1, p.roof, p.roof_full, end=p.found)
    # The forge porch in front, roofed on posts.
    for x in (0, 8):
        s.fill(x, 1, 0, x, top, 0, p.frame if not zam(p) else p.base)
    for x in range(0, 9):
        s.set(x, top + 1, 0, stairs(p.roof, UP_SOUTH))
        s.fill(x, top + 1, 1, x, top + 1, 3, p.roof_slab)
    # Hearth, furnaces, anvil and quench.
    s.fill(1, 1, 3, 2, 1, 3, (FURNACE, 2))
    s.set(1, 1, 2, p.base)
    s.set(2, 1, 2, p.lamp if zam(p) else (NETHERRACK, 0))
    s.set(1, 2, 2, p.base)
    s.set(4, 1, 1, (ANVIL, 0))
    s.set(6, 1, 1, (CAULDRON, 3))
    s.set(7, 1, 3, (WORKBENCH, 0))
    # The chimney.
    s.fill(1, 1, 4, 1, ridge + 1, 4, p.base)
    # Arms along the back wall inside.
    for x in (2, 3, 5, 6):
        s.set(x, 1, 7, p.fence)
    s.set(7, 1, 7, (CHEST, 2))
    torch(s, p, 4, 3, 7, "north")
    if zam(p):
        skull(s, 0, top + 2, 0)
        skull(s, 8, top + 2, 0)
        spikes(s, p, [(4, ridge + 1, 6)])
    else:
        s.set(0, top - 1, 1, p.trim)
        s.set(8, top - 1, 1, p.trim)
    if level == 2:
        s.set(3, 1, 1, (ANVIL, 1))
        s.set(7, 1, 1, (IRON_BLOCK, 0) if not zam(p) else (REDSTONE_BLOCK, 0))
        s.fill(7, 1, 4, 7, ridge + 1, 4, p.base)
        s.set(2, 1, 6, (FURNACE, 3))
        for x in (2, 6):
            s.set(x, 4, 8, p.window)
    return s


def tower(p, level):
    s = Struct(5, 5, 20, 1)
    foundation(s, p, 0, 0, 4, 4)
    deck = 10 if level == 1 else 13
    if zam(p):
        s.walls(0, 0, 4, 4, 1, deck, p.found)
        s.pillars(0, 0, 4, 4, 1, deck + 1, p.base)
    else:
        s.walls(0, 0, 4, 4, 1, deck, p.found)
        s.walls(0, 0, 4, 4, 1, 1, p.base)
        s.pillars(0, 0, 4, 4, 1, deck, p.base)
    s.fill(1, 0, 1, 3, 0, 3, p.floor)
    door(s, p, 2, 1, 0)
    # Arrow slits.
    for y in range(4, deck - 1, 3):
        s.set(2, y, 4, p.window)
        s.set(0, y, 2, p.window)
        s.set(4, y, 2, p.window)
        if y > 4:
            s.set(2, y, 0, p.window)
    # Ladder up the inside, and the deck at the top.
    for y in range(1, deck):
        s.set(2, y, 3, (LADDER, 2))
    s.fill(0, deck, 0, 4, deck, 4, p.found)
    s.set(2, deck, 3, (LADDER, 2))
    s.set(2, deck, 2, p.lamp)
    # Battlements.
    for x, z in ((0, 0), (2, 0), (4, 0), (0, 2), (4, 2), (0, 4), (2, 4), (4, 4)):
        s.set(x, deck + 1, z, p.found if (x, z) != (2, 0) or not zam(p) else p.base)
    if zam(p):
        spikes(s, p, [(0, deck + 2, 0), (4, deck + 2, 0), (0, deck + 2, 4), (4, deck + 2, 4)])
        skull(s, 2, deck + 2, 0)
        s.set(1, deck - 2, 0, p.trim)
        s.set(3, deck - 2, 0, p.trim)
    else:
        # A banner down the front and a flag on top.
        s.fill(1, deck - 3, 0, 1, deck - 1, 0, p.banner)
        s.fill(3, deck - 3, 0, 3, deck - 1, 0, p.banner)
        s.fill(4, deck + 2, 4, 4, deck + 3, 4, p.fence)
        s.set(3, deck + 3, 4, p.banner)
    if level == 2:
        # A roof over the deck on the corner posts.
        for x, z in ((0, 0), (4, 0), (0, 4), (4, 4)):
            s.set(x, deck + 2, z, p.found if not zam(p) else p.base)
        cap = hip(s, 0, 0, 4, 4, deck + 3, p.roof, p.roof_full)
        s.set(2, cap + 1, 2, p.emblem if not zam(p) else (NETHER_FENCE, 0))
        if not zam(p):
            s.clear(4, deck + 2, 4, 4, deck + 3, 4)
            s.set(3, deck + 3, 4, None)
            s.set(4, deck + 2, 4, p.found)
    return s


def chaos_temple(p, level):
    """Zamorak's temple, where chaos priests pray for his favor and dark wizards and his mages are trained: a dark
    nave under a steep roof, red light through its windows, an altar of redstone at the far end and a spire."""
    s = Struct(11, 13, 22, 1)
    foundation(s, p, 0, 0, 10, 12)
    s.fill(0, 0, 0, 10, 0, 1, p.plaza)
    top = 5 if level == 1 else 6
    s.fill(1, 0, 2, 9, 0, 12, p.floor)
    s.walls(1, 2, 9, 12, 1, top, p.found)
    s.pillars(1, 2, 9, 12, 1, top + 1, p.base)
    for z in (5, 9):
        s.fill(1, 1, z, 1, top, z, p.base)
        s.fill(9, 1, z, 9, top, z, p.base)
    door(s, p, 5, 1, 2)
    s.set(4, 3, 2, p.window2)
    s.set(6, 3, 2, p.window2)
    for z in (4, 7, 10):
        s.fill(1, 2, z, 1, 3, z, p.window2)
        s.fill(9, 2, z, 9, 3, z, p.window2)
    # The altar: a block of redstone between two braziers, under the god's banner.
    s.set(5, 1, 11, p.emblem)
    s.set(5, 2, 11, (NETHER_FENCE, 0))
    skull(s, 5, 3, 11)
    for x in (3, 7):
        s.set(x, 1, 11, (NETHERRACK, 0))
        s.set(x, 2, 11, (FIRE, 0))
    s.fill(4, 3, 12, 6, top - 1, 12, p.banner)
    s.fill(3, 1, 4, 3, 1, 9, (NETHER_STAIRS, UP_WEST))
    s.fill(7, 1, 4, 7, 1, 9, (NETHER_STAIRS, UP_EAST))
    s.fill(5, 0, 3, 5, 0, 10, (CARPET, RED))
    ridge = gable_z(s, 2, 12, 1, 9, top + 1, p.roof, p.roof_full, end=p.found)
    # The spire over the door, with spikes at the corners.
    spire = 4 if level == 1 else 6
    s.fill(4, ridge, 2, 6, ridge + spire, 4, p.found)
    s.fill(5, ridge + spire + 1, 3, 5, ridge + spire + 2, 3, p.base)
    s.set(5, ridge + spire + 3, 3, (NETHER_FENCE, 0))
    s.set(5, ridge + spire - 1, 2, p.emblem)
    spikes(s, p, [(1, top + 2, 2), (9, top + 2, 2), (1, top + 2, 12), (9, top + 2, 12)])
    if level == 2:
        # Fire pits flank the steps.
        for x in (0, 10):
            s.set(x, 0, 0, (NETHERRACK, 0))
            s.set(x, 1, 0, (FIRE, 0))
    return s


def monastery(p, level):
    """Saradomin's monastery, home of the monks: a stone chapel with a bell tower at one corner and a little cloister
    garden in front."""
    s = Struct(13, 11, 16, 1)
    foundation(s, p, 0, 0, 12, 10)
    s.fill(0, 0, 0, 12, 0, 3, (GRASS, 0))
    top = 4 if level == 1 else 5
    s.fill(1, 0, 4, 8, 0, 10, p.floor)
    s.walls(0, 4, 8, 10, 1, top, p.found)
    s.walls(0, 4, 8, 10, 1, 1, p.base)
    door(s, p, 4, 1, 4)
    for x in (2, 6):
        s.fill(x, 2, 4, x, 3, 4, p.window2)
    for z in (6, 8):
        s.fill(0, 2, z, 0, 3, z, p.window2)
    # Pews, a lectern and an altar of gold.
    for z in (6, 8):
        s.fill(2, 1, z, 3, 1, z, stairs(OAK_STAIRS, UP_NORTH))
        s.fill(5, 1, z, 6, 1, z, stairs(OAK_STAIRS, UP_NORTH))
    s.set(4, 1, 9, p.emblem)
    s.set(2, 1, 9, (BOOKSHELF, 0))
    s.set(6, 1, 9, (BOOKSHELF, 0))
    torch(s, p, 1, 3, 9, "north")
    torch(s, p, 7, 3, 9, "north")
    gable_x(s, 0, 8, 4, 10, top + 1, p.roof, p.roof_full, end=p.found)
    # The bell tower.
    bell = 9 if level == 1 else 11
    s.walls(9, 6, 12, 10, 1, bell, p.found)
    s.pillars(9, 6, 12, 10, 1, bell + 1, p.base)
    s.fill(10, 0, 7, 11, 0, 9, p.floor)
    s.set(9, 1, 8, None)
    s.set(9, 2, 8, None)
    for y in (bell - 2, bell - 1):
        s.set(10, y, 6, None)
        s.set(11, y, 6, None)
        s.set(12, y, 8, None)
    s.set(10, bell - 1, 8, (ANVIL, 0))
    s.fill(9, bell + 1, 6, 12, bell + 1, 10, p.found)
    hip(s, 9, 6, 12, 10, bell + 2, p.roof, p.roof_full)
    s.set(10, bell, 6, p.banner)
    # The cloister garden: a low hedge, a well and flowers.
    for x in range(0, 9):
        if x != 4:
            s.set(x, 1, 0, (LEAVES, 4))
    s.set(2, 1, 2, (CAULDRON, 3))
    s.set(6, 1, 2, (FLOWER_POT, 0))
    s.fill(4, 0, 0, 4, 0, 3, p.path)
    if level == 2:
        # A second bell and lamps by the door.
        s.set(11, bell - 1, 8, (ANVIL, 0))
        for x in (3, 5):
            s.set(x, 1, 3, p.base)
            s.set(x, 2, 3, p.lamp)
    return s


def wizards_tower(p, level):
    """Saradomin's wizards' tower, where blue wizards are trained: a tall round tower of stone under a pointed blue
    roof, its windows of blue glass."""
    s = Struct(9, 9, 30, 1)
    foundation(s, p, 0, 0, 8, 8)
    top = 12 if level == 1 else 16
    ring = [(x, z) for x in range(9) for z in range(9)
            if 9 <= (x - 4) ** 2 + (z - 4) ** 2 <= 17]
    inside = [(x, z) for x in range(9) for z in range(9) if (x - 4) ** 2 + (z - 4) ** 2 < 9]
    for x, z in ring:
        s.fill(x, 1, z, x, top, z, p.found)
        s.set(x, 1, z, p.base)
    for x, z in inside:
        s.set(x, 0, z, p.floor)
    # Floors every few blocks, with a ladder up.
    for y in range(5, top, 4):
        for x, z in inside:
            s.set(x, y, z, p.planks)
    for y in range(1, top):
        s.set(4, y, 6, (LADDER, 2))
    door(s, p, 4, 1, 0)
    for y in range(3, top - 1, 4):
        for x, z in ((4, 8), (0, 4), (8, 4)):
            s.set(x, y, z, p.window2)
            s.set(x, y + 1, z, p.window2)
    # A ring of blue round the top, then the pointed roof.
    for x, z in ring:
        s.set(x, top, z, p.trim)
    y = top + 1
    for r2 in (17, 12, 8, 4, 2, 0):
        for x in range(9):
            for z in range(9):
                if (x - 4) ** 2 + (z - 4) ** 2 <= r2:
                    s.set(x, y, z, (WOOL, BLUE))
        y += 1 if r2 > 4 else 2
        if r2 <= 2:
            s.fill(4, y - 2, 4, 4, y, 4, (WOOL, BLUE))
    s.set(4, y + 1, 4, p.emblem)
    s.set(4, 2, 4, (BOOKSHELF, 0))
    s.set(3, 2, 4, (CAULDRON, 0))
    torch(s, p, 4, 3, 1, "south")
    if level == 2:
        # Lamps over the door and on the far side.
        s.set(4, top - 3, 0, p.lamp)
        s.set(4, top - 3, 8, p.lamp)
    return s


def stone_circle(p, level):
    """A circle of standing stones raised by dark wizards. When four of them gather inside it they can summon a
    lesser demon. Its middle is open ground, so the wizards can stand there."""
    s = Struct(9, 9, 6, 1)
    s.fill(0, -1, 0, 8, -1, 8, (STONE, 0))
    s.fill(0, 0, 0, 8, 0, 8, (GRASS, 0))
    for x in range(2, 7):
        for z in range(2, 7):
            if (x - 4) ** 2 + (z - 4) ** 2 <= 5:
                s.set(x, 0, z, (NETHERRACK, 0) if (x + z) % 2 else (SOUL_SAND, 0))
    s.set(4, 0, 4, p.emblem)
    stones = [(4, 0), (8, 4), (4, 8), (0, 4), (1, 1), (7, 1), (1, 7), (7, 7)]
    for i, (x, z) in enumerate(stones):
        height = 3 if i < 4 else 2
        s.fill(x, 1, z, x, height, z, (OBSIDIAN, 0) if i < 4 else (STONE, 0))
        if i < 4:
            s.set(x, height + 1, z, (NETHERRACK, 0))
            s.set(x, height + 2, z, (FIRE, 0))
    skull(s, 4, 1, 5)
    return s


# ---------------------------------------------------------------- walls
#
# Walls are 3 by 3 pieces laid along a line: straight segments, posts where the line turns or ends, and gates made
# from segments. A segment runs along x at rotation 0, its wall in the middle row. Level 1 is a wooden palisade,
# level 2 stone and level 3 stone brick with battlements; Zamorak's are dark oak, smooth stone and nether brick.


def wall_style(p, level):
    """The body, foot and top of a wall at a level, and how tall its body stands."""
    if zam(p):
        return [((LOG2, 1), (NETHERRACK, 0), (NETHER_FENCE, 0), 5),
                ((STONE, 0), (COBBLE, 0), (SLAB, 0), 6),
                ((NETHER_BRICK, 0), (NETHER_BRICK, 0), (NETHER_FENCE, 0), 7)][level - 1]
    return [((LOG, 0), (COBBLE, 0), (FENCE, 0), 5),
            ((COBBLE, 0), (COBBLE, 0), (COBBLE_WALL, 0), 6),
            ((STONE_BRICK, 0), (STONE_BRICK, 0), (SLAB, 5), 7)][level - 1]


WALL = 5      # blocks across a piece of wall, as WallLine.SIZE
WALL_TOP = 10


def wall_ground(s):
    """Ground under and round a wall piece, so it sits in the terrain without a hole round it."""
    s.fill(0, -1, 0, WALL - 1, -1, WALL - 1, (DIRT, 0))
    s.fill(0, 0, 0, WALL - 1, 0, WALL - 1, (GRASS, 0))


def battlements(s, p, level, x0, x1, z0, z1, y):
    """The top of a stretch of wall three blocks thick, from x0 to x1 along it: a palisade of stakes in Age I, and
    merlons along both faces with a walk between them after."""
    body, foot, top, height = wall_style(p, level)
    for x in range(x0, x1 + 1):
        if level == 1:
            s.set(x, y, z0 + 1, top)
            if x % 2 == 0:
                s.set(x, y, z0, top)
                s.set(x, y, z1, top)
        elif x % 2 == 0:
            s.set(x, y, z0, body)
            s.set(x, y, z1, body)
            if level == 3:
                s.set(x, y + 1, z0, top)
                s.set(x, y + 1, z1, top)


def wall(p, level, gate=False):
    """A stretch of wall five blocks long and three thick, along x. A gate has an opening three wide and three high
    through it, closed by fence gates that open for the team's own people and its allies."""
    body, foot, top, height = wall_style(p, level)
    s = Struct(WALL, WALL, WALL_TOP, 1)
    wall_ground(s)
    s.fill(0, 0, 1, WALL - 1, 0, 3, foot)
    s.fill(0, 1, 1, WALL - 1, height, 3, body)
    if level > 1:
        # A plinth along both faces.
        s.fill(0, 1, 1, WALL - 1, 1, 1, foot)
        s.fill(0, 1, 3, WALL - 1, 1, 3, foot)
    battlements(s, p, level, 0, WALL - 1, 1, 3, height + 1)
    if gate:
        s.clear(1, 1, 1, 3, 3, 3)
        s.fill(1, 0, 1, 3, 0, 3, p.path if level > 1 else foot)
        s.fill(1, 1, 2, 3, 3, 2, (FENCE_GATE, 0))
        # Posts either side, lit, and the team's colors over the opening.
        for x in (0, 4):
            s.set(x, height + 1, 1, (p.light, 5) if level > 1 or not zam(p) else top)
            s.set(x, height + 1, 3, (p.light, 5) if level > 1 or not zam(p) else top)
        for z in (0, 4):
            s.set(2, 4, z, p.banner)
            if height > 5:
                s.set(2, 5, z, p.banner)
    return s


def wall_post(p, level):
    """Where a wall turns or ends: a square pillar a block taller than the wall, with stubs of wall all four ways so
    it meets the wall, or a tower, whichever way it comes in."""
    body, foot, top, height = wall_style(p, level)
    s = Struct(WALL, WALL, WALL_TOP, 1)
    wall_ground(s)
    s.fill(1, 0, 0, 3, 0, 4, foot)
    s.fill(0, 0, 1, 4, 0, 3, foot)
    s.fill(1, 1, 0, 3, height, 4, body)
    s.fill(0, 1, 1, 4, height, 3, body)
    s.fill(1, height + 1, 1, 3, height + 1, 3, body if level == 1 else foot)
    for x, z in ((1, 1), (3, 1), (1, 3), (3, 3)):
        s.set(x, height + 2, z, top if level == 1 else body)
    s.set(2, height + 2, 2, (p.light, 5) if level > 1 or not zam(p) else top)
    return s


# ---------------------------------------------------------------- the knights' fortresses
#
# Each god's knights have their own fortress, built in Age III by that god's teams only: the White Knights' in white
# quartz with blue banners and gold, the Black Knights' in black stone with red banners, spikes and skulls. Either
# fortress looks the same whichever folder it is written into. Four corner towers and curtain walls with battlements
# round a courtyard, a gate in the middle of the front and a tall keep at the back.

FORTRESS = 15
FORTRESS_TOP = 18
QUARTZ_STAIRS = 156


def fortress_style(black):
    if black:
        return dict(wall=(CLAY, BLACK), pillar=(OBSIDIAN, 0), base=(NETHER_BRICK, 0), floor=(NETHER_BRICK, 0),
                    accent=(COAL_BLOCK, 0), banner=(WOOL, RED), banner2=(WOOL, BLACK), emblem=(REDSTONE_BLOCK, 0),
                    slit=(IRON_BARS, 0), light=REDSTONE_TORCH, lamp=(GLOWSTONE, 0), door=IRON_DOOR,
                    stairs=NETHER_STAIRS, slab=(SLAB, 6), fence=(NETHER_FENCE, 0))
    return dict(wall=(QUARTZ, 0), pillar=(QUARTZ, 2), base=(STONE_BRICK, 0), floor=(STONE_BRICK, 0),
                accent=(QUARTZ, 1), banner=(WOOL, BLUE), banner2=(WOOL, WHITE), emblem=(GOLD_BLOCK, 0),
                slit=(PANE, 0), light=TORCH, lamp=(GLOWSTONE, 0), door=DOOR, stairs=QUARTZ_STAIRS,
                slab=(SLAB, 7), fence=(FENCE, 0))


def fortress(black):
    f = fortress_style(black)
    s = Struct(FORTRESS, FORTRESS, FORTRESS_TOP, 1)
    last = FORTRESS - 1
    s.fill(0, -1, 0, last, 0, last, f["base"])
    s.fill(1, 0, 1, last - 1, 0, last - 1, f["floor"])
    wall_top = 8
    tower_top = 11

    # Curtain walls, two thick, with battlements along the outer edge.
    for a in range(4, last - 3):
        for outer, inner in (((a, 0), (a, 1)), ((a, last), (a, last - 1)), ((0, a), (1, a)), ((last, a), (last - 1, a))):
            s.fill(outer[0], 1, outer[1], outer[0], wall_top, outer[1], f["wall"])
            s.fill(inner[0], 1, inner[1], inner[0], wall_top, inner[1], f["wall"])
            if a % 2 == 0:
                s.set(outer[0], wall_top + 1, outer[1], f["wall"])
        # A band of darker stone near the top.
        for x, z in ((a, 0), (a, last), (0, a), (last, a)):
            s.set(x, wall_top - 1, z, f["accent"])

    # The corner towers, hollow, with arrow slits and battlements, a lamp on top.
    for x0, z0 in ((0, 0), (last - 3, 0), (0, last - 3), (last - 3, last - 3)):
        x1, z1 = x0 + 3, z0 + 3
        s.walls(x0, z0, x1, z1, 1, tower_top, f["wall"])
        s.pillars(x0, z0, x1, z1, 1, tower_top, f["pillar"])
        s.fill(x0, tower_top, z0, x1, tower_top, z1, f["base"])
        s.walls(x0, z0, x1, z1, tower_top - 1, tower_top - 1, f["accent"])
        for y in (4, 7):
            for x, z in ((x0, z0 + 1), (x0, z0 + 2), (x1, z0 + 1), (x1, z0 + 2), (x0 + 1, z0), (x0 + 2, z0),
                         (x0 + 1, z1), (x0 + 2, z1)):
                if (x in (0, last) or z in (0, last)) and (x + z) % 2 == 0:
                    s.set(x, y, z, f["slit"])
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            s.set(x, tower_top + 1, z, f["pillar"])
            if black:
                s.set(x, tower_top + 2, z, f["fence"])
        s.set(x0 + 1, tower_top + 1, z0 + 1, f["lamp"])

    # The gate: an arch three wide through the front wall, with the god's emblem above it and banners either side.
    gate = FORTRESS // 2
    s.clear(gate - 1, 1, 0, gate + 1, 3, 1)
    s.set(gate - 1, 4, 0, stairs(f["stairs"], UP_EAST, upside_down=True))
    s.set(gate + 1, 4, 0, stairs(f["stairs"], UP_WEST, upside_down=True))
    s.set(gate, 4, 0, f["accent"])
    s.set(gate, 6, 0, f["emblem"])
    for x in (gate - 3, gate + 3):
        s.fill(x, 3, 0, x, 6, 0, f["banner"])
        s.set(x, 3, 0, f["banner2"])

    # The keep at the back of the courtyard: taller than the towers, with a door, windows and a flag on top.
    kx0, kx1, kz0, kz1 = gate - 3, gate + 3, 7, last - 2
    keep_top = 14
    s.walls(kx0, kz0, kx1, kz1, 1, keep_top, f["wall"])
    s.pillars(kx0, kz0, kx1, kz1, 1, keep_top + 1, f["pillar"])
    s.fill(kx0 + 1, 0, kz0 + 1, kx1 - 1, 0, kz1 - 1, f["floor"])
    s.fill(kx0, keep_top, kz0, kx1, keep_top, kz1, f["base"])
    s.walls(kx0, kz0, kx1, kz1, keep_top - 1, keep_top - 1, f["accent"])
    s.fill(kx0 + 1, 6, kz0 + 1, kx1 - 1, 6, kz1 - 1, f["floor"])
    s.clear(kx1 - 2, 6, kz1 - 2, kx1 - 1, 6, kz1 - 1)
    s.set(gate, 1, kz0, (f["door"], 1))
    s.set(gate, 2, kz0, (f["door"], 8))
    for y in (3, 9, 11):
        for x in (gate - 2, gate + 2):
            s.set(x, y, kz0, f["slit"])
    s.set(kx0, 9, kz0 + 3, f["slit"])
    s.set(kx1, 9, kz0 + 3, f["slit"])
    s.set(gate, 5, kz0, f["emblem"])
    for x in range(kx0, kx1 + 1, 2):
        s.set(x, keep_top + 1, kz0, f["wall"])
        s.set(x, keep_top + 1, kz1, f["wall"])
    for z in range(kz0, kz1 + 1, 2):
        s.set(kx0, keep_top + 1, z, f["wall"])
        s.set(kx1, keep_top + 1, z, f["wall"])
    # The flag over the keep.
    s.fill(gate, keep_top + 1, kz0 + 3, gate, FORTRESS_TOP, kz0 + 3, f["fence"])
    s.fill(gate + 1, FORTRESS_TOP - 2, kz0 + 3, gate + 2, FORTRESS_TOP - 1, kz0 + 3, f["banner"])
    s.set(gate + 1, FORTRESS_TOP - 1, kz0 + 3, f["banner2"])
    # Banners hanging down the front of the keep.
    for x in (gate - 1, gate + 1):
        s.fill(x, 8, kz0, x, 11, kz0, f["banner"])

    # The courtyard: weapon racks, training dummies, lights.
    for x in (3, last - 3):
        s.set(x, 1, 5, f["fence"])
        s.set(x, 2, 5, (HAY, 0))
        if black:
            skull(s, x, 3, 5)
        else:
            s.set(x, 3, 5, (PUMPKIN, 2))
    s.set(3, 1, 3, (ANVIL, 0))
    s.set(last - 3, 1, 3, (CHEST, 3))
    for x in (gate - 2, gate + 2):
        s.set(x, 1, 3, f["fence"])
        s.set(x, 2, 3, f["lamp"])
    if black:
        # Skulls along the walls and spikes on the keep.
        for x in range(4, last - 3, 2):
            skull(s, x, wall_top + 2, 0)
        for x, z in ((kx0, kz0), (kx1, kz0), (kx0, kz1), (kx1, kz1)):
            s.set(x, keep_top + 2, z, f["fence"])
    return s


FIXED_STYLE_BUILDINGS = {
    "white_knights_fortress": lambda p, level: fortress(False),
    "black_knights_fortress": lambda p, level: fortress(True),
    "chaos_temple": lambda p, level: chaos_temple(ZAMORAK, level),
    "stone_circle": lambda p, level: stone_circle(ZAMORAK, level),
    "monastery": lambda p, level: monastery(SARADOMIN, level),
    "wizards_tower": lambda p, level: wizards_tower(SARADOMIN, level),
}


BUILDINGS = {
    "wall": (wall, 3),
    "wall_post": (wall_post, 3),
    "gate": (lambda p, level: wall(p, level, gate=True), 3),
    "town_hall": (town_center, 3),
    "house": (house, 2),
    "barracks": (barracks, 2),
    "storehouse": (storehouse, 2),
    "farm": (farm, 2),
    "armory": (armory, 2),
    "tower": (tower, 2),
    "archery_range": (archery_range, 2),
    "stables": (stables, 2),
    "white_knights_fortress": (FIXED_STYLE_BUILDINGS["white_knights_fortress"], 1),
    "black_knights_fortress": (FIXED_STYLE_BUILDINGS["black_knights_fortress"], 1),
    "chaos_temple": (FIXED_STYLE_BUILDINGS["chaos_temple"], 2),
    "monastery": (FIXED_STYLE_BUILDINGS["monastery"], 2),
    "wizards_tower": (FIXED_STYLE_BUILDINGS["wizards_tower"], 2),
    "stone_circle": (FIXED_STYLE_BUILDINGS["stone_circle"], 1),
}

# The palette each fortress's building site and ruin are drawn in, whichever folder it is written into.
FIXED_PALETTE = {"white_knights_fortress": "saradomin", "black_knights_fortress": "zamorak", "chaos_temple": "zamorak",
                 "stone_circle": "zamorak", "monastery": "saradomin", "wizards_tower": "saradomin"}

# ---------------------------------------------------------------- building sites, damage and ruins

SCAFFOLD_POST = (FENCE, 0)
SCAFFOLD_DECK = (WOOD_SLAB, 0)


def is_decor(block):
    return block[0] in DECOR


def supported(s, x, y, z, block):
    """Whether something that hangs on a block still has it."""
    kind, meta = block
    if kind in (TORCH, REDSTONE_TORCH):
        offset = {1: (-1, 0, 0), 2: (1, 0, 0), 3: (0, 0, -1), 4: (0, 0, 1)}.get(meta, (0, -1, 0))
    elif kind == LADDER:
        offset = {2: (0, 0, 1), 3: (0, 0, -1), 4: (1, 0, 0), 5: (-1, 0, 0)}.get(meta, (0, 0, 1))
    else:
        offset = (0, -1, 0)
    below = s.get(x + offset[0], y + offset[1], z + offset[2])
    return below != AIR and not is_decor(below)


def tidy(s):
    """Drops what would hang in the air, and doors missing a half."""
    changed = True
    while changed:
        changed = False
        for (x, y, z), block in list(s.blocks.items()):
            if block[0] in DOORS:
                other = s.get(x, y + (1 if block[1] < 8 else -1), z)
                if other[0] != block[0]:
                    s.set(x, y, z, None)
                    changed = True
            elif block[0] in (TORCH, REDSTONE_TORCH, LADDER, SKULL, FLOWER_POT, WHEAT, CARROTS, POTATOES,
                              NETHER_WART, DEAD_BUSH, RAIL, CARPET, FIRE) and not supported(s, x, y, z, block):
                s.set(x, y, z, None)
                changed = True


def building_site(finished, p, stage, seed):
    """A building site: its foundations, the lower part of its walls and a wooden frame of scaffolding, taller at
    each stage. Stage 1 is little more than the foundation and a frame of posts."""
    rng = random.Random(seed)
    s = Struct(finished.w, finished.l, finished.top, finished.g)
    height = finished.height_above_ground()
    built = {1: 0, 2: max(1, round(height * 0.35)), 3: max(2, round(height * 0.7))}[stage]
    for (x, y, z), block in finished.blocks.items():
        if y <= 0 or (y <= built and not is_decor(block) and block[0] not in DOORS):
            s.set(x, y, z, block)
    # Posts at the corners and along the sides, decks of planks around them every few blocks.
    frame_top = {1: min(3, height), 2: min(height, built + 3), 3: min(height, built + 3)}[stage]
    w, l = finished.w, finished.l
    posts = set()
    for x in range(0, w, 3):
        posts.add((x, 0))
        posts.add((x, l - 1))
    for z in range(0, l, 3):
        posts.add((0, z))
        posts.add((w - 1, z))
    for corner in ((w - 1, 0), (0, l - 1), (w - 1, l - 1)):
        posts.add(corner)
    for x, z in posts:
        for y in range(1, frame_top + 1):
            if s.get(x, y, z) == AIR:
                s.set(x, y, z, SCAFFOLD_POST)
    if stage > 1:
        for y in range(3, frame_top + 1, 3):
            for x in range(w):
                for z in range(l):
                    edge = x in (0, w - 1) or z in (0, l - 1)
                    if edge and s.get(x, y, z) == AIR and finished.get(x, y, z) == AIR:
                        s.set(x, y, z, SCAFFOLD_DECK)
    # Timber and stone waiting to go up.
    spots = [(x, z) for x in range(1, w - 1) for z in range(1, l - 1) if s.get(x, 1, z) == AIR
             and s.get(x, 0, z) != AIR and not is_decor(s.get(x, 0, z))]
    rng.shuffle(spots)
    for x, z in spots[:3 if stage == 1 else 2]:
        s.set(x, 1, z, (LOG, 4) if rng.random() < 0.6 else p.found)
    tidy(s)
    return s


CRACKED = {
    (STONE_BRICK, 0): [(STONE_BRICK, 2), (STONE_BRICK, 1)],
    (COBBLE, 0): [(MOSSY, 0)],
    (CLAY, WHITE): [(CLAY, LIGHT_GRAY)],
    (CLAY, BLACK): [(CLAY, GRAY)],
    (NETHER_BRICK, 0): [(NETHERRACK, 0)],
    (PLANKS, 0): [(WOOD_SLAB, 0)],
    (PLANKS, 5): [(WOOD_SLAB, 5)],
    (OBSIDIAN, 0): [(OBSIDIAN, 0)],
}


def damaged(finished, level, seed):
    """The building with holes in its walls and roof, cracked and mossy stone, and cobwebs inside; more of it
    gone at each level of damage, the roof first."""
    rng = random.Random(seed)
    s = finished.copy()
    height = max(1, finished.height_above_ground())
    gone, cracked = {1: (0.05, 0.15), 2: (0.16, 0.3), 3: (0.34, 0.45)}[level]
    for (x, y, z), block in list(finished.blocks.items()):
        if y <= 0 or block[0] in DOORS:
            continue
        weight = 0.4 + 1.2 * y / height
        if is_decor(block):
            if rng.random() < gone * 2.5:
                s.set(x, y, z, None)
        elif rng.random() < gone * weight:
            s.set(x, y, z, None)
        elif block in CRACKED and rng.random() < cracked:
            s.set(x, y, z, rng.choice(CRACKED[block]))
    # Cobwebs in corners inside and rubble on the floor.
    for (x, y, z), block in list(s.blocks.items()):
        if y != 0 or block == AIR:
            continue
        above = s.get(x, 1, z)
        if above == AIR and rng.random() < 0.04 * level:
            s.set(x, 1, z, (GRAVEL, 0) if rng.random() < 0.5 else (COBBLE, 0))
    if level >= 2:
        for (x, y, z) in [key for key, block in finished.blocks.items() if key[1] > 1]:
            if s.get(x, y - 1, z) == AIR and rng.random() < 0.02 * level:
                s.set(x, y - 1, z, (COBWEB, 0))
    tidy(s)
    return s


def ruin(finished, p, seed):
    """What is left of a building: its foundation, stumps of wall and rubble."""
    rng = random.Random(seed)
    s = Struct(finished.w, finished.l, finished.top, finished.g)
    for (x, y, z), block in finished.blocks.items():
        if y <= 0:
            s.set(x, y, z, block if block[0] not in (WHEAT, CARROTS, POTATOES, NETHER_WART) else None)
        elif y <= 2 and not is_decor(block) and block[0] not in DOORS and rng.random() < (0.75 if y == 1 else 0.4):
            s.set(x, y, z, rng.choice(CRACKED.get(block, [block])) if rng.random() < 0.5 else block)
    for x in range(s.w):
        for z in range(s.l):
            if s.get(x, 1, z) == AIR and s.get(x, 0, z) != AIR and rng.random() < 0.18:
                s.set(x, 1, z, (GRAVEL, 0) if rng.random() < 0.4 else (COBBLE, 0) if not zam(p) else (NETHERRACK, 0))
    # Farmland dries out under a ruin.
    for (x, y, z), block in list(s.blocks.items()):
        if block[0] == FARMLAND:
            s.set(x, y, z, (DIRT, 0))
        if block[0] == WATER:
            s.set(x, y, z, (DIRT, 0))
    tidy(s)
    return s


# ---------------------------------------------------------------- output


def check_same_size(name, structures):
    sizes = {(s.w, s.l, s.top, s.g) for s in structures}
    if len(sizes) != 1:
        raise SystemExit("%s: variants differ in size: %s" % (name, sizes))


def generate(preview_dir=None):
    written = 0
    for p in FACTIONS:
        folder = os.path.join(OUT, p.name)
        os.makedirs(folder, exist_ok=True)
        for type_id, (build, levels) in BUILDINGS.items():
            # A fortress is drawn in its own god's style, whichever faction's folder it goes into.
            style = {"saradomin": SARADOMIN, "zamorak": ZAMORAK}[FIXED_PALETTE[type_id]] \
                if type_id in FIXED_PALETTE else p
            seed = sum(map(ord, style.name + type_id))
            variants = {}
            for level in range(1, levels + 1):
                finished = build(style, level)
                tidy(finished)
                variants["%s_l%d" % (type_id, level)] = finished
                for d in (1, 2, 3):
                    variants["%s_l%d_d%d" % (type_id, level, d)] = damaged(finished, d, seed + 10 * level + d)
            first = variants["%s_l1" % type_id]
            for stage in (1, 2, 3):
                variants["%s_stage%d" % (type_id, stage)] = building_site(first, style, stage, seed + stage)
            variants["%s_ruin" % type_id] = ruin(first, style, seed + 99)
            check_same_size(type_id, variants.values())
            for name, s in variants.items():
                s.save(os.path.join(folder, name + ".schematic"), "%s %s" % (p.name, name))
                written += 1
            if preview_dir:
                import preview
                os.makedirs(preview_dir, exist_ok=True)
                for name, s in variants.items():
                    preview.render(s, os.path.join(preview_dir, "%s_%s.png" % (p.name, name)))
    folder = os.path.join(OUT, "neutral")
    os.makedirs(folder, exist_ok=True)
    for name, build in NEUTRAL.items():
        s = build()
        tidy(s)
        s.save(os.path.join(folder, name + ".schematic"), "neutral " + name)
        written += 1
        if preview_dir:
            import preview
            preview.render(s, os.path.join(preview_dir, "neutral_%s.png" % name))
    print("Wrote %d structures to %s" % (written, OUT))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", help="also draw an isometric picture of each structure into this folder")
    args = parser.parse_args()
    generate(args.preview)
