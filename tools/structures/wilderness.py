"""The ruins of Forinthry, the Wilderness burnt black in the God Wars, as schematics for the map's ruins
(ForinthryRuins.java), more threatening than Misthalin's:

    graveyard_of_shadows_<n>  a great walled graveyard round a mausoleum, haunted by ghosts and skeletons
    fortress_<n>              the ruin of a big fortress: a curtain wall with corner towers, a gatehouse and a keep,
                              where hill giants and gargoyles lair
    graveyard_<n>             a small walled graveyard
    chapel_<n>                a burnt chapel
    house_<n>                 a burnt-out house of some outpost
    watchtower_<n>            a broken watchtower
    chaos_altar_<n>           a ring of black stone round an altar of Zamorak
    bandit_camp_<n>           a bandits' camp: a palisade round tents, a fire, a lookout and a cage

Each comes in a few variants, n = 1, 2, ..., drawn with different seeds; the small ones also come with cobwebs, as
_webs, for when red spiders lurk in them. They stand on Forinthry's charred earth and ash, so they sit in the map as
if they had always been there. The game adds the chests (RuinLoot.java) and the mobs that haunt them.
"""

import random

from generate import (AIR, BANNER_EMBLEM, BANNER_NORTH, BANNER_TOP, BANNER_ZAMORAK, BROWN, CARPET, COBBLE,
                      COBBLE_WALL, COBWEB, FENCE, FIRE, GRAVEL, GRAY, GRAYSTONE, GRAYSTONE_SLAB, GRAYSTONE_STAIRS, HAY,
                      IRON_BARS, LADDER, LOG, LOG2, MOSSY, NETHER_BRICK, NETHER_STAIRS, NETHERRACK, OBSIDIAN, PLANKS,
                      RED, SKULL, SLAB, SOUL_SAND, STONE_BRICK, STONE_BRICK_STAIRS, TORCH, UP_EAST, UP_NORTH, UP_SOUTH,
                      UP_WEST, UPSIDE_DOWN, WOOD_SLAB, WOOL, Struct, tidy)

# Forinthry's own blocks, by the ids ModBlocks.java gives them in schematics.
CHARRED_EARTH, ASH, SCORCHED, EMBER, CHARRED_LOG = 212, 213, 214, 215, 216

BRICK, MOSSY_BRICK, CRACKED_BRICK = (STONE_BRICK, 0), (STONE_BRICK, 1), (STONE_BRICK, 2)


# ---------------------------------------------------------------- materials


def ground(s, rng, ash_share=0.35, extra=()):
    """The ground layer and a foundation under it: charred earth and ash, with what else is given mixed in."""
    for x in range(s.w):
        for z in range(s.l):
            s.set(x, -1, z, (SCORCHED, 0))
            roll = rng.random()
            block = (ASH, 0) if roll < ash_share else (CHARRED_EARTH, 0)
            for share, other in extra:
                if rng.random() < share:
                    block = other
            s.set(x, 0, z, block)


def old_stone(rng, black=0.3):
    """A block of an old wall burnt in the God Wars: stone bricks, mossy and cracked, cobble, and scorched stone."""
    roll = rng.random()
    if roll < black:
        return (SCORCHED, 0) if rng.random() < 0.85 else (EMBER, 0)
    roll = rng.random()
    if roll < 0.4:
        return BRICK
    if roll < 0.6:
        return CRACKED_BRICK
    if roll < 0.72:
        return MOSSY_BRICK
    if roll < 0.9:
        return (COBBLE, 0)
    return (MOSSY, 0)


def dark_stone(rng):
    """Graystone, the Kinshra's dark stone, cracked and mossy, with scorched stone in it."""
    roll = rng.random()
    if roll < 0.55:
        return (GRAYSTONE, 0)
    if roll < 0.75:
        return (GRAYSTONE, 2)
    if roll < 0.85:
        return (GRAYSTONE, 1)
    return (SCORCHED, 0)


def crumbling(length, low, high, rng):
    """A wall's height along its length: a walk up and down between low and high."""
    heights = []
    h = rng.randint(low, high)
    for _ in range(length):
        h = max(low, min(high, h + rng.randint(-1, 1)))
        heights.append(h)
    return heights


def column(s, x, z, height, stone, rng, y0=1):
    for y in range(y0, y0 + height):
        if not s.inside(x, y, z):
            break
        if y == y0 + height - 1 and height > 1 and rng.random() < 0.25:
            s.set(x, y, z, (SLAB, 5))
        else:
            s.set(x, y, z, stone(rng))


def rubble(s, rng, count, x0=0, z0=0, x1=None, z1=None, stone=old_stone):
    """Stones fallen on the ground, one high so units step over them."""
    x1 = s.w - 1 if x1 is None else x1
    z1 = s.l - 1 if z1 is None else z1
    for _ in range(count):
        x, z = rng.randint(x0, x1), rng.randint(z0, z1)
        if s.get(x, 1, z) == AIR:
            s.set(x, 1, z, (SLAB, 3) if rng.random() < 0.3 else stone(rng))


def ring(x0, z0, x1, z1):
    """The places round the edge of a rectangle, clockwise from its north-west corner."""
    out = [(x, z0) for x in range(x0, x1)]
    out += [(x1, z) for z in range(z0, z1)]
    out += [(x, z1) for x in range(x1, x0, -1)]
    out += [(x0, z) for z in range(z1, z0, -1)]
    return out


def brazier(s, x, z, y=1):
    """A brazier of black stone with a fire in it that burns for show."""
    s.set(x, y, z, (NETHER_BRICK, 0))
    s.set(x, y + 1, z, (NETHERRACK, 0))
    s.set(x, y + 2, z, (FIRE, 0))


def webs(s, rng, count):
    """Cobwebs up in the corners, out of the way of units walking through."""
    placed = 0
    for _ in range(400):
        if placed >= count:
            break
        x, z, y = rng.randrange(s.w), rng.randrange(s.l), rng.randint(2, 5)
        if not s.inside(x, y, z) or s.get(x, y, z) != AIR:
            continue
        around = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                     if s.inside(x + dx, y, z + dz) and s.get(x + dx, y, z + dz)[0] not in (0, COBWEB, TORCH))
        above = s.inside(x, y + 1, z) and s.get(x, y + 1, z)[0] not in (0, COBWEB)
        if around >= 2 or around >= 1 and above:
            s.set(x, y, z, (COBWEB, 0))
            placed += 1


def gravestone(s, rng, x, z, facing_z=True):
    """A grave: a headstone at its head, upright, leaning or fallen, and the plot of earth or soul sand before it."""
    kind = rng.random()
    if kind < 0.35:
        s.set(x, 1, z, (COBBLE_WALL, 1 if rng.random() < 0.5 else 0))
    elif kind < 0.6:
        s.set(x, 1, z, rng.choice([BRICK, MOSSY_BRICK, CRACKED_BRICK]))
        if rng.random() < 0.3:
            s.set(x, 2, z, (SLAB, 5))
    elif kind < 0.8:
        # Leaning: a step tipped forward.
        s.set(x, 1, z, (STONE_BRICK_STAIRS, UP_NORTH if facing_z else UP_WEST))
    else:
        # Fallen flat.
        s.set(x, 1, z, (SLAB, 5))
    plot = [(x, z + 1), (x, z + 2)] if facing_z else [(x + 1, z), (x + 2, z)]
    for px, pz in plot:
        if s.inside(px, 0, pz):
            s.set(px, 0, pz, (SOUL_SAND, 0) if rng.random() < 0.6 else (GRAVEL, 0))
    if rng.random() < 0.15 and s.get(x, 2, z) == AIR and s.get(x, 1, z)[0] in (STONE_BRICK, COBBLE_WALL):
        s.set(x, 2, z, (SKULL, 1))


# ---------------------------------------------------------------- the graveyard of shadows


def graveyard_of_shadows(seed):
    """The Graveyard of Shadows: a great graveyard walled with iron railings between stone pillars, its gate on the
    front flanked by braziers, rows of graves either side of a gravel walk, and at the back a mausoleum of dark
    graystone with a stepped roof, its door open on a sarcophagus. Skulls sit on the pillars; the ground is soul sand
    and ash. Ghosts walk here."""
    rng = random.Random(seed)
    s = Struct(25, 21, 10, 1)
    ground(s, rng, 0.4, [(0.18, (SOUL_SAND, 0)), (0.06, (GRAVEL, 0))])
    x0, z0, x1, z1 = 0, 0, s.w - 1, s.l - 1
    mid = s.w // 2
    # The railing: stone pillars every four, iron bars between, broken through in places.
    for i, (x, z) in enumerate(ring(x0, z0, x1, z1)):
        gate = z == z0 and abs(x - mid) <= 1
        if gate:
            continue
        pillar = (x - x0) % 4 == 0 and z in (z0, z1) or (z - z0) % 4 == 0 and x in (x0, x1)
        if pillar:
            column(s, x, z, rng.randint(2, 3), old_stone, rng)
            if rng.random() < 0.35 and s.get(x, 3, z) == AIR and s.get(x, 2, z)[0] != SLAB:
                s.set(x, 3, z, (SKULL, 1))
        elif rng.random() < 0.8:
            s.set(x, 1, z, (IRON_BARS, 0))
            if rng.random() < 0.6:
                s.set(x, 2, z, (IRON_BARS, 0))
        elif rng.random() < 0.5:
            s.set(x, 1, z, (SLAB, 5))
    # Braziers either side of the gate.
    brazier(s, mid - 2, z0 + 1)
    brazier(s, mid + 2, z0 + 1)
    # The walk up the middle to the mausoleum.
    for z in range(z0 + 1, z1 - 7):
        for x in (mid - 1, mid, mid + 1):
            s.set(x, 0, z, (GRAVEL, 0) if rng.random() < 0.7 else (COBBLE, 0))
    # Rows of graves either side of the walk.
    for z in range(z0 + 3, z1 - 9, 4):
        for x in list(range(x0 + 2, mid - 2, 2)) + list(range(mid + 3, x1 - 1, 2)):
            if rng.random() < 0.85:
                gravestone(s, rng, x, z)
    # Graves along the sides, beside the mausoleum.
    for z in range(z1 - 8, z1 - 1, 3):
        for x in (x0 + 2, x0 + 4, x1 - 4, x1 - 2):
            if rng.random() < 0.7:
                gravestone(s, rng, x, z)
    # The mausoleum: 7 wide and 7 deep at the back, its door facing the gate.
    mx0, mx1, mz0, mz1 = mid - 3, mid + 3, z1 - 7, z1 - 1
    for x in range(mx0, mx1 + 1):
        for z in range(mz0, mz1 + 1):
            s.set(x, 0, z, (GRAYSTONE, 0))
            edge = x in (mx0, mx1) or z in (mz0, mz1)
            if edge:
                top = 5 if rng.random() < 0.85 else rng.randint(3, 4)
                for y in range(1, top + 1):
                    corner = x in (mx0, mx1) and z in (mz0, mz1)
                    s.set(x, y, z, (STONE_BRICK, 3) if corner and y in (1, 5) else dark_stone(rng))
    # Its doorway, two high, and a step up to it.
    for y in (1, 2, 3):
        s.set(mid, y, mz0, AIR if y < 3 else (GRAYSTONE_STAIRS, UP_SOUTH | UPSIDE_DOWN))
    s.set(mid, 1, mz0 - 1, (GRAYSTONE_STAIRS, UP_SOUTH))
    # The stepped roof: a rim of slabs, then a block layer, then a cap.
    for x in range(mx0, mx1 + 1):
        for z in range(mz0, mz1 + 1):
            if rng.random() < 0.08:
                continue
            if x in (mx0, mx1) or z in (mz0, mz1):
                s.set(x, 6, z, (GRAYSTONE_SLAB, 0))
            elif x in (mx0 + 1, mx1 - 1) or z in (mz0 + 1, mz1 - 1):
                s.set(x, 6, z, (GRAYSTONE, 0))
            else:
                s.set(x, 6, z, (GRAYSTONE, 0))
                s.set(x, 7, z, (GRAYSTONE, 0))
    s.set(mid, 8, mz0 + 3, (GRAYSTONE_SLAB, 0))
    # The sarcophagus inside, a skull at its head, and a torch gone cold: none lit within.
    for z in (mz0 + 3, mz0 + 4):
        s.set(mid, 1, z, (GRAYSTONE, 1))
    s.set(mid, 2, mz0 + 3, (SLAB, 5))
    s.set(mid, 2, mz0 + 4, (SKULL, 1))
    s.set(mid - 2, 1, mz1 - 1, (COBBLE_WALL, 1))
    s.set(mid + 2, 1, mz1 - 1, (COBBLE_WALL, 1))
    # Obelisks at the back corners of the yard.
    for x in (x0 + 2, x1 - 2):
        z = z1 - 2
        s.set(x, 1, z, (GRAYSTONE, 0))
        s.set(x, 2, z, (GRAYSTONE, 2))
        s.set(x, 3, z, (COBBLE_WALL, 0))
    webs(s, rng, 6)
    tidy(s)
    return s


def small_graveyard(seed, with_webs):
    """A small graveyard: a low wall of old stone round two rows of graves, a gap for a gate, and a dead tree's
    stump."""
    rng = random.Random(seed)
    s = Struct(11, 9, 4, 1)
    ground(s, rng, 0.35, [(0.2, (SOUL_SAND, 0))])
    for x, z in ring(0, 0, s.w - 1, s.l - 1):
        if z == 0 and x in (5, 6):
            continue
        if rng.random() < 0.8:
            s.set(x, 1, z, old_stone(rng) if rng.random() < 0.6 else (COBBLE_WALL, 1))
    for z in (2, 5):
        for x in range(2, s.w - 2, 2):
            if rng.random() < 0.8:
                gravestone(s, rng, x, z)
    s.set(s.w - 2, 1, s.l - 2, (SKULL, 1))
    if with_webs:
        webs(s, rng, 4)
    tidy(s)
    return s


# ---------------------------------------------------------------- the fortress


def tower(s, rng, cx, cz, height, stone):
    """A round-cornered tower five across, hollow, its top fallen in to different heights."""
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) == 2 and abs(dz) == 2:
                continue
            edge = abs(dx) == 2 or abs(dz) == 2
            if not edge:
                s.set(cx + dx, 0, cz + dz, (STONE_BRICK, 0))
                continue
            top = height - rng.randint(0, 3)
            column(s, cx + dx, cz + dz, max(1, top), stone, rng)


def fortress(seed):
    """The ruin of a big fortress from the God Wars, of the kind that stood all over Forinthry: a curtain wall two
    thick, crumbling from seven high to two, with broken towers at the corners and a gatehouse on the front; in the
    yard the shell of a keep, its walls fallen in, a stair climbing a broken wall, and rubble everywhere. Zamorak's
    tattered banner still hangs over the gate. Hill giants and gargoyles lair in it."""
    rng = random.Random(seed)
    s = Struct(27, 27, 14, 1)
    ground(s, rng, 0.25, [(0.15, (GRAVEL, 0)), (0.12, BRICK)])
    x0, z0, x1, z1 = 2, 2, s.w - 3, s.l - 3
    mid = s.w // 2
    outer = ring(x0, z0, x1, z1)
    inner = ring(x0 + 1, z0 + 1, x1 - 1, z1 - 1)
    heights = crumbling(len(outer), 2, 7, rng)
    for (x, z), h in zip(outer, heights):
        column(s, x, z, h, old_stone, rng)
    inner_heights = crumbling(len(inner), 1, 6, rng)
    for (x, z), h in zip(inner, inner_heights):
        column(s, x, z, h, old_stone, rng)
    # Breaches through the wall on the back and the sides.
    for bx, bz, along_x in ((rng.randint(x0 + 4, x1 - 6), z1, True), (x0, rng.randint(z0 + 4, z1 - 6), False),
                            (x1, rng.randint(z0 + 4, z1 - 6), False)):
        for i in range(3):
            for y in range(1, 15):
                for t in (0, -1 if bz == z1 or bx == x1 else 1):
                    x = bx + (i if along_x else t)
                    z = bz + (t if along_x else i)
                    if s.inside(x, y, z):
                        s.set(x, y, z, AIR)
        rubble(s, rng, 6, max(0, bx - 3), max(0, bz - 3), min(s.w - 1, bx + 5), min(s.l - 1, bz + 5))
    # Corner towers.
    for cx, cz in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        tower(s, rng, cx, cz, rng.randint(7, 12), old_stone)
    # The gatehouse: two towers either side of a gateway three wide, an arch over it, the banner above.
    for y in range(1, 15):
        for x in (mid - 1, mid, mid + 1):
            for z in (z0, z0 + 1):
                s.set(x, y, z, AIR)
    tower(s, rng, mid - 4, z0, rng.randint(8, 11), old_stone)
    tower(s, rng, mid + 4, z0, rng.randint(8, 11), old_stone)
    for x in (mid - 1, mid, mid + 1):
        s.set(x, 5, z0, old_stone(rng))
        s.set(x, 4, z0, (STONE_BRICK_STAIRS, UP_SOUTH | UPSIDE_DOWN) if x == mid else old_stone(rng))
        s.set(x, 6, z0, (SLAB, 5))
    # Torn: the top and the emblem are left, the bottom burnt away.
    s.set(mid, 5, z0 - 1, (BANNER_ZAMORAK, BANNER_TOP << 2 | BANNER_NORTH))
    s.set(mid, 4, z0 - 1, (BANNER_ZAMORAK, BANNER_EMBLEM << 2 | BANNER_NORTH))
    for x in (mid - 2, mid + 2):
        brazier(s, x, z0 - 2)
    # The keep: nine across in the middle of the yard, its walls fallen to different heights, a doorway on the
    # front and a stair up its back wall to nowhere.
    kx0, kz0 = mid - 4, mid - 2
    kx1, kz1 = mid + 4, mid + 6
    keep = ring(kx0, kz0, kx1, kz1)
    keep_heights = crumbling(len(keep), 2, 10, rng)
    for (x, z), h in zip(keep, keep_heights):
        column(s, x, z, h, dark_stone, rng)
    for x in range(kx0 + 1, kx1):
        for z in range(kz0 + 1, kz1):
            s.set(x, 0, z, rng.choice([BRICK, CRACKED_BRICK, (GRAYSTONE, 0), (SCORCHED, 0)]))
    for y in range(1, 15):
        s.set(mid, y, kz0, AIR)
        s.set(mid - 1, y, kz0, AIR)
    for i in range(5):
        s.set(kx0 + 1 + i, 1 + i, kz1 - 1, (STONE_BRICK_STAIRS, UP_EAST))
        for y in range(1, 1 + i):
            s.set(kx0 + 1 + i, y, kz1 - 1, BRICK)
    # A fallen roof beam or two, and rubble inside the keep and round the yard.
    rubble(s, rng, 10, kx0 + 1, kz0 + 1, kx1 - 1, kz1 - 2, dark_stone)
    rubble(s, rng, 24, x0 + 2, z0 + 2, x1 - 2, z1 - 2)
    # A well in the yard, gone dry and choked with ash.
    wx, wz = x0 + 4, z1 - 5
    for dx, dz in ((0, 0), (1, 0), (2, 0), (0, 1), (2, 1), (0, 2), (1, 2), (2, 2)):
        s.set(wx + dx, 1, wz + dz, (COBBLE, 0) if rng.random() < 0.6 else (MOSSY, 0))
    s.set(wx + 1, 0, wz + 1, (ASH, 0))
    # Embers still glowing in the stones here and there.
    for _ in range(8):
        x, z = rng.randrange(s.w), rng.randrange(s.l)
        if s.get(x, 0, z)[0] in (CHARRED_EARTH, ASH):
            s.set(x, 0, z, (EMBER, 0))
    tidy(s)
    return s


# ---------------------------------------------------------------- small ruins


def chapel(seed, with_webs):
    """A burnt chapel: stone walls with tall window gaps, the roof gone, a broken altar at the far end and pews
    burnt down to their legs."""
    rng = random.Random(seed)
    s = Struct(11, 15, 8, 1)
    ground(s, rng, 0.3)
    x0, z0, x1, z1 = 1, 1, s.w - 2, s.l - 2
    walls = ring(x0, z0, x1, z1)
    heights = crumbling(len(walls), 1, 6, rng)
    for (x, z), h in zip(walls, heights):
        column(s, x, z, h, old_stone, rng)
    for y in range(1, 9):
        s.set(5, y, z0, AIR)
        s.set(4, y, z0, AIR)
    # Window gaps down the long sides.
    for z in range(z0 + 3, z1 - 1, 3):
        for x in (x0, x1):
            for y in (3, 4):
                s.set(x, y, z, AIR)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            s.set(x, 0, z, rng.choice([BRICK, CRACKED_BRICK, (CHARRED_EARTH, 0), (ASH, 0)]))
    # The altar, broken.
    s.set(4, 1, z1 - 2, (STONE_BRICK, 3))
    s.set(5, 1, z1 - 2, (STONE_BRICK, 3))
    s.set(6, 1, z1 - 2, (SLAB, 5))
    # Burnt pews.
    for z in range(z0 + 3, z1 - 3, 2):
        for x in (x0 + 1, x0 + 2, x1 - 2, x1 - 1):
            if rng.random() < 0.6:
                s.set(x, 1, z, (WOOD_SLAB, 5) if rng.random() < 0.6 else (FENCE, 0))
    rubble(s, rng, 6)
    if with_webs:
        webs(s, rng, 6)
    tidy(s)
    return s


def house(seed, with_webs):
    """A house of some outpost, burnt out: a stone ground floor, its walls broken, the charred stubs of its upper
    floor's beams and a chimney still standing."""
    rng = random.Random(seed)
    s = Struct(9, 9, 7, 1)
    ground(s, rng, 0.3)
    x0, z0, x1, z1 = 1, 1, s.w - 2, s.l - 2
    walls = ring(x0, z0, x1, z1)
    heights = crumbling(len(walls), 1, 4, rng)
    for (x, z), h in zip(walls, heights):
        column(s, x, z, h, old_stone, rng)
    for y in (1, 2):
        s.set(4, y, z0, AIR)
    # The chimney in the back corner.
    for y in range(1, 7):
        s.set(x1, y, z1, (COBBLE, 0) if rng.random() < 0.7 else (MOSSY, 0))
    s.set(x1 - 1, 1, z1 - 1, (NETHERRACK, 0))
    # Charred beams of the fallen floor above: planks gone black, lying askew.
    for x in range(x0 + 1, x1):
        if rng.random() < 0.5:
            s.set(x, 3, rng.randint(z0 + 1, z1 - 1), (LOG2, 5 if rng.random() < 0.5 else 9))
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if rng.random() < 0.25:
                s.set(x, 1, z, (WOOD_SLAB, 5))
    rubble(s, rng, 5)
    if with_webs:
        webs(s, rng, 5)
    tidy(s)
    return s


def watchtower(seed, with_webs):
    """A broken watchtower: a square tower five across standing to eight or ten on one side and fallen to three on
    the other, a ladder up its inside to a ledge, rubble all round."""
    rng = random.Random(seed)
    s = Struct(9, 9, 11, 1)
    ground(s, rng, 0.3)
    c = 4
    high_side = rng.choice(["n", "s", "e", "w"])
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            edge = abs(dx) == 2 or abs(dz) == 2
            if not edge:
                s.set(c + dx, 0, c + dz, BRICK)
                continue
            side = {"n": dz == -2, "s": dz == 2, "e": dx == 2, "w": dx == -2}[high_side]
            h = rng.randint(7, 10) if side else rng.randint(1, 4)
            column(s, c + dx, c + dz, h, old_stone, rng)
    for y in (1, 2):
        s.set(c, y, c - 2, AIR)
    for y in range(1, 6):
        s.set(c, y, c + 1, (LADDER, 2))
    s.set(c, 6, c + 1, (WOOD_SLAB, 5))
    rubble(s, rng, 10)
    if with_webs:
        webs(s, rng, 5)
    tidy(s)
    return s


def chaos_altar(seed, with_webs):
    """A ring of black stone round an altar of Zamorak, where his followers made offerings in the God Wars: standing
    stones of obsidian and scorched stone, a floor of nether brick cracked through with embers, and the altar in the
    middle with a fire burning on it still."""
    rng = random.Random(seed)
    s = Struct(9, 9, 5, 1)
    ground(s, rng, 0.4)
    c = 4
    for x in range(1, 8):
        for z in range(1, 8):
            if (x - c) ** 2 + (z - c) ** 2 <= 9:
                s.set(x, 0, z, (NETHER_BRICK, 0) if rng.random() < 0.7 else (EMBER, 0))
    for x, z in ((c, 0), (c, 8), (0, c), (8, c), (1, 1), (7, 1), (1, 7), (7, 7)):
        h = rng.randint(1, 4)
        for y in range(1, h + 1):
            s.set(x, y, z, (OBSIDIAN, 0) if rng.random() < 0.5 else (SCORCHED, 0))
    s.set(c, 1, c, (NETHER_BRICK, 0))
    s.set(c, 2, c, (NETHERRACK, 0))
    s.set(c, 3, c, (FIRE, 0))
    for dx, dz, climbs in ((-1, 0, UP_EAST), (1, 0, UP_WEST), (0, -1, UP_SOUTH), (0, 1, UP_NORTH)):
        s.set(c + dx, 1, c + dz, (NETHER_STAIRS, climbs))
    s.set(c + 2, 1, c - 2, (SKULL, 1))
    if with_webs:
        webs(s, rng, 3)
    tidy(s)
    return s


# ---------------------------------------------------------------- the bandit camp


def tent(s, rng, x0, z0, length, color):
    """A tent of wool five wide: a ridge on fence posts, its sides sloping down, open at the front (z0)."""
    for z in range(z0, z0 + length):
        s.set(x0 + 2, 3, z, (WOOL, color))
        for x in (x0 + 1, x0 + 3):
            s.set(x, 2, z, (WOOL, color))
        for x in (x0, x0 + 4):
            s.set(x, 1, z, (WOOL, color))
    s.set(x0 + 2, 1, z0, (FENCE, 0))
    s.set(x0 + 2, 2, z0, (FENCE, 0))
    # The back closed, a bedroll inside.
    for x in (x0 + 1, x0 + 2, x0 + 3):
        s.set(x, 1, z0 + length - 1, (WOOL, color))
    s.set(x0 + 2, 2, z0 + length - 1, (WOOL, color))
    s.set(x0 + 2, 1, z0 + 1, (CARPET, BROWN if color != BROWN else RED))


def bandit_camp(seed):
    """A bandits' camp in the Wilderness: a palisade of sharpened stakes, gapped and leaning, round three tents, a
    fire in a ring of stones, a lookout platform on posts, a cage of iron bars for whoever they caught, and their
    loot in crates and bales. Bandits keep it."""
    rng = random.Random(seed)
    s = Struct(21, 19, 6, 1)
    ground(s, rng, 0.3, [(0.15, (GRAVEL, 0))])
    x0, z0, x1, z1 = 0, 0, s.w - 1, s.l - 1
    mid = s.w // 2
    for x, z in ring(x0, z0, x1, z1):
        if z == z0 and abs(x - mid) <= 1:
            continue
        if rng.random() < 0.82:
            s.set(x, 1, z, (FENCE, 0))
            if rng.random() < 0.7:
                s.set(x, 2, z, (FENCE, 0))
    # Posts either side of the way in, a skull on one.
    for x in (mid - 2, mid + 2):
        s.set(x, 1, z0, (LOG, 0))
        s.set(x, 2, z0, (LOG, 0))
        s.set(x, 3, z0, (FENCE, 0))
    s.set(mid - 2, 4, z0, (SKULL, 1))
    # Trodden ground in the middle, the fire in a ring of stones.
    cx, cz = mid, s.l // 2
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            if rng.random() < 0.6:
                s.set(x, 0, z, (GRAVEL, 0) if rng.random() < 0.5 else (ASH, 0))
    s.set(cx, 0, cz, (NETHERRACK, 0))
    s.set(cx, 1, cz, (FIRE, 0))
    for dx, dz in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)):
        s.set(cx + dx, 1, cz + dz, (SLAB, 3))
    # Logs to sit on round the fire.
    s.set(cx - 3, 1, cz, (LOG, 8))
    s.set(cx - 3, 1, cz + 1, (LOG, 8))
    s.set(cx + 3, 1, cz - 1, (LOG, 8))
    # Three tents, their fronts toward the fire.
    tent(s, rng, x0 + 2, z1 - 6, 5, BROWN)
    tent(s, rng, x1 - 6, z1 - 6, 5, RED)
    tent(s, rng, x0 + 2, z0 + 2, 5, GRAY)
    # The lookout: a platform on four posts by the gate, with a ladder.
    lx, lz = x1 - 4, z0 + 2
    for dx, dz in ((0, 0), (2, 0), (0, 2), (2, 2)):
        s.fill(lx + dx, 1, lz + dz, lx + dx, 3, lz + dz, (LOG, 0))
    for dx in range(3):
        for dz in range(3):
            s.set(lx + dx, 4, lz + dz, (PLANKS, 1))
    for dx, dz in ((0, 0), (1, 0), (2, 0), (0, 1), (0, 2), (1, 2), (2, 2), (2, 1)):
        s.set(lx + dx, 5, lz + dz, (FENCE, 0))
    for y in range(1, 5):
        s.set(lx + 1, y, lz - 1 + 0, AIR)
    for y in range(1, 4):
        s.set(lx - 1, y, lz + 1, (LADDER, 4))
    # The cage, its door hanging open.
    gx, gz = x0 + 8, z1 - 4
    for dx in range(3):
        for dz in range(3):
            if dx in (0, 2) or dz in (0, 2):
                if not (dx == 1 and dz == 0):
                    s.fill(gx + dx, 1, gz + dz, gx + dx, 2, gz + dz, (IRON_BARS, 0))
            s.set(gx + dx, 3, gz + dz, (PLANKS, 1))
    # Loot in crates and bales, and a weapon rack.
    for x, z, block in ((x1 - 2, cz, (PLANKS, 0)), (x1 - 2, cz + 1, (HAY, 0)), (x1 - 3, cz + 1, (PLANKS, 0)),
                        (x1 - 2, cz + 2, (PLANKS, 1))):
        s.set(x, 1, z, block)
    s.set(x1 - 2, 2, cz + 1, (WOOD_SLAB, 0))
    for x in (x0 + 2, x0 + 3, x0 + 4):
        s.set(x, 1, cz, (FENCE, 0))
    s.set(x0 + 3, 2, cz, (WOOD_SLAB, 0))
    # Torches on the gate posts.
    s.set(mid + 2, 4, z0, (TORCH, 5))
    tidy(s)
    return s


# ---------------------------------------------------------------- all of them

VARIANTS = 2


def structures():
    """Every Forinthry ruin by its file name, as Structs."""
    out = {}
    for n in range(1, VARIANTS + 1):
        out["graveyard_of_shadows_%d" % n] = graveyard_of_shadows(600 + n)
        out["fortress_%d" % n] = fortress(700 + n)
        out["bandit_camp_%d" % n] = bandit_camp(800 + n)
        for name, build in (("graveyard", small_graveyard), ("chapel", chapel), ("house", house),
                            ("watchtower", watchtower), ("chaos_altar", chaos_altar)):
            seed = 900 + n * 10 + sum(map(ord, name))
            out["%s_%d" % (name, n)] = build(seed, False)
            out["%s_%d_webs" % (name, n)] = build(seed, True)
    return out
