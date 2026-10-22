"""The ruins of Forinthry, the Wilderness burnt black in the God Wars, as schematics for the map's ruins
(ForinthryRuins.java), more threatening than Misthalin's:

    graveyard_of_shadows_<n>  a great walled graveyard round a mausoleum, haunted by ghosts and skeletons
    old_fortress              the old fortress, 55 across: a curtain wall with towers and a gatehouse round the
                              burnt-out keep, barracks, chapel, smithy, prison, stable and storehouse, hung with
                              cobwebs; one or two to a map, haunted by ghosts, the undead and red spiders
    graveyard_<n>             a small walled graveyard
    chapel_<n>                a burnt chapel
    house_<n>                 a burnt-out house of some outpost
    watchtower_<n>            a broken watchtower
    chaos_altar_<n>           a ring of black stone round an altar of Zamorak
    camp_<piece>_<n>          the pieces of a bandits' camp, which the game lays out together round the fire:
                              camp_fire, camp_tent (six kinds), camp_lookout, camp_cage and camp_stash

Each comes in a few variants, n = 1, 2, ..., drawn with different seeds; the small ones also come with cobwebs, as
_webs, for when red spiders lurk in them. They stand on Forinthry's charred earth and ash, so they sit in the map as
if they had always been there. The game adds the chests (RuinLoot.java) and the mobs that haunt them.
"""

import random

from generate import (AIR, ANVIL, BLACK, BOOKSHELF, BROWN, CARPET, CAULDRON, CHEST, COBBLE, COBBLE_WALL, COBWEB,
                      DEAD_BUSH, DIRT, FENCE, NETHER_FENCE, FIRE, FURNACE, GRAVEL, GRAY, GRAYSTONE, GRAYSTONE_SLAB,
                      GRAYSTONE_STAIRS, HAY, IRON_BARS, LADDER, LOG, LOG2, MOSSY, NETHERRACK, NETHER_BRICK,
                      NETHER_STAIRS, OBSIDIAN, PLANKS, RED, SKULL, SLAB, SOUL_SAND, STONE_BRICK, STONE_BRICK_STAIRS,
                      TORCH, UPSIDE_DOWN, UP_EAST, UP_NORTH, UP_SOUTH, UP_WEST, WOOD_SLAB, WOOL, Struct, tidy)

# Forinthry's own blocks, by the ids ModBlocks.java gives them in schematics.
CHARRED_EARTH, ASH, SCORCHED, EMBER = 214, 215, 216, 217

BRICK, MOSSY_BRICK, CRACKED_BRICK = (STONE_BRICK, 0), (STONE_BRICK, 1), (STONE_BRICK, 2)


# ---------------------------------------------------------------- materials


def ground(s, rng, ash_share=0.35, extra=()):
    """The ground layer and a foundation under it: charred earth, scorched stone and a little ash, with what else is
    given mixed in."""
    for x in range(s.w):
        for z in range(s.l):
            s.set(x, -1, z, (SCORCHED, 0))
            # Mostly charred earth and scorched stone; ash only here and there.
            roll = rng.random()
            block = (ASH, 0) if roll < ash_share / 3 else (SCORCHED, 0) if roll < 0.2 else (CHARRED_EARTH, 0)
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


def graveyard(seed, w, l, grand, with_webs=False):
    """A graveyard laid out at random, so no two are alike: a boundary of railings, a low wall or a broken fence with
    a gate somewhere along its front and gaps knocked through it; a gravel walk wandering in from the gate; graves in
    clusters and rows facing this way and that, and alone, some dug open; dead trees, obelisks and statues; and in a
    grand one a mausoleum somewhere at the back and perhaps the way down into a crypt."""
    rng = random.Random(seed)
    s = Struct(w, l, 10 if grand else 6, 1)
    ground(s, rng, 0.3, [(0.2, (SOUL_SAND, 0)), (0.06, (GRAVEL, 0)), (0.05, (DIRT, 1))])
    x0, z0, x1, z1 = 0, 0, w - 1, l - 1
    taken = set()

    def take(ax, az, bx, bz, margin=0):
        for x in range(ax - margin, bx + margin + 1):
            for z in range(az - margin, bz + margin + 1):
                taken.add((x, z))

    def free(ax, az, bx, bz):
        return all(1 <= x <= w - 2 and 1 <= z <= l - 2 and (x, z) not in taken
                   for x in range(ax, bx + 1) for z in range(az, bz + 1))

    # The boundary.
    style = rng.choice(("railing", "wall", "fence") if grand else ("wall", "fence", "wall"))
    gate = rng.randint(3, w - 5)
    for x, z in ring(x0, z0, x1, z1):
        if z == z0 and gate <= x <= gate + 2:
            continue
        if style == "railing":
            pillar = (x - x0) % 4 == 0 and z in (z0, z1) or (z - z0) % 4 == 0 and x in (x0, x1)
            if pillar:
                column(s, x, z, rng.randint(2, 3), old_stone, rng)
                if rng.random() < 0.3 and s.get(x, 3, z) == AIR and s.get(x, 2, z)[0] != SLAB:
                    s.set(x, 3, z, (SKULL, 1))
            elif rng.random() < 0.8:
                s.set(x, 1, z, (IRON_BARS, 0))
                if rng.random() < 0.5:
                    s.set(x, 2, z, (IRON_BARS, 0))
        elif style == "wall":
            if rng.random() < 0.85:
                s.set(x, 1, z, old_stone(rng) if rng.random() < 0.6 else (COBBLE_WALL, 1))
                if rng.random() < 0.25:
                    s.set(x, 2, z, (SLAB, 5))
        elif rng.random() < 0.7:
            s.set(x, 1, z, (NETHER_FENCE, 0) if rng.random() < 0.7 else (COBBLE_WALL, 1))
    for _ in range(rng.randint(1, 3)):
        x, z = rng.choice(ring(x0, z0, x1, z1))
        for d in range(rng.randint(1, 3)):
            for y in (1, 2, 3):
                xx, zz = (x + d, z) if z in (z0, z1) else (x, z + d)
                if s.inside(xx, y, zz):
                    s.set(xx, y, zz, AIR)
    if grand and rng.random() < 0.7:
        brazier(s, gate - 1, z0 + 1)
        brazier(s, gate + 3, z0 + 1)
        take(gate - 1, z0 + 1, gate + 3, z0 + 1)
    take(gate, z0, gate + 2, z0 + 2)

    # The mausoleum and crypt of a grand graveyard, somewhere toward the back.
    goal = (rng.randint(3, w - 4), l - 3)
    if grand:
        mw, ml = rng.choice((5, 7)), rng.choice((5, 7))
        mx0 = rng.choice((2, (w - mw) // 2, w - 2 - mw))
        mz0 = l - 2 - ml - rng.randint(0, 2)
        mausoleum(s, rng, mx0, mz0, mx0 + mw - 1, mz0 + ml - 1)
        take(mx0, mz0, mx0 + mw - 1, mz0 + ml - 1, 1)
        goal = (mx0 + mw // 2, mz0 - 2)
        if rng.random() < 0.6:
            for _ in range(30):
                cx, cz = rng.randint(2, w - 5), rng.randint(4, l - 5)
                if free(cx - 1, cz - 1, cx + 3, cz + 3):
                    crypt(s, rng, cx, cz)
                    take(cx, cz, cx + 2, cz + 2, 1)
                    break

    # A gravel walk wandering in from the gate.
    x, z = gate + 1, z0 + 1
    while z < goal[1] and (x, z) not in taken - {(gate + 1, z0 + 1), (gate, z0 + 1), (gate + 2, z0 + 1)}:
        for dx in (0, 1) if rng.random() < 0.6 else (0,):
            if s.inside(x + dx, 0, z) and 0 < x + dx < w - 1:
                s.set(x + dx, 0, z, (GRAVEL, 0) if rng.random() < 0.75 else (COBBLE, 0))
                taken.add((x + dx, z))
        z += 1
        if rng.random() < 0.3:
            x = max(2, min(w - 3, x + (1 if goal[0] > x else -1 if goal[0] < x else rng.choice((-1, 1)))))

    # Features: dead trees, obelisks, statues.
    for _ in range(rng.randint(1, 3 if grand else 2)):
        for _ in range(30):
            fx, fz = rng.randint(2, w - 3), rng.randint(2, l - 3)
            if free(fx - 1, fz - 1, fx + 1, fz + 1):
                kind = rng.random()
                if kind < 0.45:
                    for y in range(1, rng.randint(3, 6)):
                        s.set(fx, y, fz, (LOG2, 1))
                    top = max(y for y in range(1, 7) if s.get(fx, y, fz)[0] == LOG2)
                    for dx, dz, axis in ((1, 0, 4), (-1, 0, 4), (0, 1, 8), (0, -1, 8)):
                        if rng.random() < 0.35:
                            s.set(fx + dx, top - rng.randint(0, 1), fz + dz, (LOG2, 1 | axis))
                elif kind < 0.75:
                    s.set(fx, 1, fz, (GRAYSTONE, 0))
                    s.set(fx, 2, fz, (GRAYSTONE, 2))
                    if rng.random() < 0.6:
                        s.set(fx, 3, fz, (COBBLE_WALL, 0))
                else:
                    s.set(fx, 1, fz, (STONE_BRICK, 3))
                    s.set(fx, 2, fz, (COBBLE_WALL, 1))
                    s.set(fx, 3, fz, (SKULL, 1))
                take(fx, fz, fx, fz, 1)
                break

    # Graves: clusters in rows, each facing its own way, and some alone.
    def grave(gx, gz, facing_z, open_grave=False):
        foot = (gx, gz + 2) if facing_z else (gx + 2, gz)
        if not free(gx, gz, foot[0], foot[1]):
            return False
        if open_grave:
            s.set(gx, 1, gz, (COBBLE_WALL, 1) if rng.random() < 0.5 else (SLAB, 5))
            for px, pz in ((gx, gz + 1), (gx, gz + 2)) if facing_z else ((gx + 1, gz), (gx + 2, gz)):
                s.set(px, 0, pz, AIR)
                s.set(px, -1, pz, (SOUL_SAND, 0) if rng.random() < 0.5 else (WOOD_SLAB, 5))
            side = (gx + 1, gz + 1) if facing_z else (gx + 1, gz + 1)
            if free(*side, *side):
                s.set(side[0], 1, side[1], (DIRT, 1))
        else:
            gravestone(s, rng, gx, gz, facing_z)
        take(gx, gz, foot[0], foot[1])
        return True

    area = (w - 2) * (l - 2)
    clusters = max(2, area // (45 if grand else 40))
    for _ in range(clusters):
        cx, cz = rng.randint(2, w - 4), rng.randint(2, l - 4)
        facing_z = rng.random() < 0.6
        rows, cols = rng.randint(1, 3), rng.randint(2, 4)
        gap = rng.choice((2, 2, 3))
        for r in range(rows):
            for c in range(cols):
                if rng.random() < 0.15:
                    continue
                if facing_z:
                    gx, gz = cx + c * gap, cz + r * 4
                else:
                    gx, gz = cx + r * 4, cz + c * gap
                grave(gx, gz, facing_z, rng.random() < 0.08)
    for _ in range(area // 30):
        grave(rng.randint(2, w - 4), rng.randint(2, l - 4), rng.random() < 0.5, rng.random() < 0.15)
    # Skulls and bones lying about.
    for _ in range(rng.randint(1, 4)):
        x, z = rng.randint(1, w - 2), rng.randint(1, l - 2)
        if (x, z) not in taken and s.get(x, 1, z) == AIR:
            s.set(x, 1, z, (SKULL, 1))
    if with_webs or grand:
        webs(s, rng, (8 if grand else 5) if with_webs else 3)
    tidy(s)
    return s


def mausoleum(s, rng, mx0, mz0, mx1, mz1):
    """A mausoleum of dark graystone with a stepped roof, its door open on a sarcophagus."""
    mid = (mx0 + mx1) // 2
    for x in range(mx0, mx1 + 1):
        for z in range(mz0, mz1 + 1):
            s.set(x, 0, z, (GRAYSTONE, 0))
            if x in (mx0, mx1) or z in (mz0, mz1):
                top = 5 if rng.random() < 0.85 else rng.randint(3, 4)
                for y in range(1, top + 1):
                    corner = x in (mx0, mx1) and z in (mz0, mz1)
                    s.set(x, y, z, (STONE_BRICK, 3) if corner and y in (1, 5) else dark_stone(rng))
    for y in (1, 2, 3):
        s.set(mid, y, mz0, AIR if y < 3 else (GRAYSTONE_STAIRS, UP_SOUTH | UPSIDE_DOWN))
    s.set(mid, 1, mz0 - 1, (GRAYSTONE_STAIRS, UP_SOUTH))
    for x in range(mx0, mx1 + 1):
        for z in range(mz0, mz1 + 1):
            if rng.random() < 0.08:
                continue
            if x in (mx0, mx1) or z in (mz0, mz1):
                s.set(x, 6, z, (GRAYSTONE_SLAB, 0))
            else:
                s.set(x, 6, z, (GRAYSTONE, 0))
                if mx0 + 1 < x < mx1 - 1 and mz0 + 1 < z < mz1 - 1:
                    s.set(x, 7, z, (GRAYSTONE, 0))
    s.set(mid, 1, mz1 - 2, (GRAYSTONE, 1))
    s.set(mid, 2, mz1 - 2, (SKULL, 1))
    if mz1 - mz0 >= 6:
        s.set(mid, 1, mz1 - 3, (GRAYSTONE, 1))


def crypt(s, rng, x, z):
    """The way down into a crypt: a stone frame round a stair sunk into the ground, blocked below."""
    for dx in range(3):
        for dz in range(3):
            s.set(x + dx, 0, z + dz, (GRAYSTONE, 0))
    for dz in (0, 1, 2):
        s.set(x, 1, z + dz, dark_stone(rng))
        s.set(x + 2, 1, z + dz, dark_stone(rng))
    s.set(x + 1, 1, z + 2, dark_stone(rng))
    s.set(x + 1, 0, z, (GRAYSTONE_STAIRS, UP_SOUTH))
    s.set(x + 1, 0, z + 1, AIR)
    s.set(x + 1, -1, z + 1, (GRAYSTONE_STAIRS, UP_SOUTH))
    if rng.random() < 0.5:
        s.set(x + 1, 2, z + 2, (SKULL, 1))


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


def webs_in(s, rng, count, x0, z0, x1, z1, y0=2, y1=5):
    """Cobwebs in a box of a structure, in its corners and under what roof is left."""
    placed = 0
    for _ in range(count * 40):
        if placed >= count:
            break
        x, z, y = rng.randint(x0, x1), rng.randint(z0, z1), rng.randint(y0, y1)
        if not s.inside(x, y, z) or s.get(x, y, z) != AIR:
            continue
        around = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                     if s.inside(x + dx, y, z + dz) and s.get(x + dx, y, z + dz)[0] not in (0, COBWEB, TORCH))
        above = s.inside(x, y + 1, z) and s.get(x, y + 1, z)[0] not in (0, COBWEB)
        if around >= 2 or around >= 1 and above or above and rng.random() < 0.3:
            s.set(x, y, z, (COBWEB, 0))
            placed += 1


def big_tower(s, rng, cx, cz, height, stone):
    """A round tower seven across, hollow, with arrow slits, the stubs of its floors and its top broken off."""
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if abs(dx) + abs(dz) >= 5:
                continue
            x, z = cx + dx, cz + dz
            if not (0 <= x < s.w and 0 <= z < s.l):
                continue
            edge = abs(dx) == 3 or abs(dz) == 3 or abs(dx) + abs(dz) == 4
            if not edge:
                for y in range(1, height + 2):
                    if s.inside(x, y, z):
                        s.set(x, y, z, AIR)
                s.set(x, 0, z, rng.choice([BRICK, CRACKED_BRICK, (SCORCHED, 0)]))
                # What is left of the floors, burnt through.
                for y in (5, 10):
                    if y < height - 1 and rng.random() < 0.45:
                        s.set(x, y, z, (WOOD_SLAB, 5))
                continue
            top = height - rng.randint(0, 4)
            column(s, x, z, max(2, top), stone, rng)
            if (dx == 0 or dz == 0) and top > 4:
                for y in (3, 8):
                    if y < top - 1:
                        s.set(x, y, z, AIR)


def hall(s, rng, x0, z0, x1, z1, low, high, stone, doors, roof=0.5, floor=None):
    """A building of the old fortress, burnt out: walls one thick broken to between low and high, doorways and
    window gaps through them, the charred beams of its roof where the walls still hold them and patches of the roof
    on them. Gives back the height of its roof beams."""
    walls = ring(x0, z0, x1, z1)
    for (x, z), h in zip(walls, crumbling(len(walls), low, high, rng)):
        column(s, x, z, h, stone, rng)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            s.set(x, 0, z, floor(rng) if floor else rng.choice([BRICK, CRACKED_BRICK, (COBBLE, 0), (SCORCHED, 0)]))
    # Window gaps, every few blocks along each wall, where the wall is still high enough to have them.
    for i, (x, z) in enumerate(walls):
        corner = x in (x0, x1) and z in (z0, z1)
        if not corner and i % 4 == 2 and s.get(x, 4, z) != AIR:
            s.set(x, 3, z, AIR)
            if rng.random() < 0.3:
                s.set(x, 3, z, (IRON_BARS, 0))
    for dx, dz in doors:
        for y in (1, 2, 3):
            s.set(dx, y, dz, AIR)
        s.set(dx, 0, dz, BRICK)
    # Roof beams across the shorter span, where both ends still stand that high.
    beam_y = low + 1
    across_x = x1 - x0 <= z1 - z0
    span = range(z0 + 2, z1 - 1, 3) if across_x else range(x0 + 2, x1 - 1, 3)
    for at in span:
        a = (x0, at) if across_x else (at, z0)
        b = (x1, at) if across_x else (at, z1)
        if s.get(a[0], beam_y - 1, a[1]) == AIR or s.get(b[0], beam_y - 1, b[1]) == AIR:
            # Fallen in: the beam's burnt pieces on the floor.
            for i in range(rng.randint(1, 3)):
                x = rng.randint(x0 + 1, x1 - 1) if across_x else at
                z = at if across_x else rng.randint(z0 + 1, z1 - 1)
                s.set(x, 1, z, (WOOD_SLAB, 5))
            continue
        for i in range(1, (x1 - x0 if across_x else z1 - z0)):
            x = x0 + i if across_x else at
            z = at if across_x else z0 + i
            s.set(x, beam_y, z, (LOG2, 1 | (4 if across_x else 8)))
            # What is left of the roof boards on it.
            if rng.random() < roof:
                for d in (-1, 1):
                    rx, rz = (x, z + d) if across_x else (x + d, z)
                    if x0 < rx < x1 and z0 < rz < z1 and rng.random() < roof:
                        s.set(rx, beam_y, rz, (WOOD_SLAB, 5 | 8))
    return beam_y


def chest(s, x, y, z, facing):
    """A chest, which the game fills with the ruin's loot."""
    s.set(x, y, z, (CHEST, facing))


def garrison_barracks(s, rng, x0, z0, x1, z1, door_x, door_z, west):
    """The barracks: burnt bunks down both long walls, a chest at the end."""
    hall(s, rng, x0, z0, x1, z1, 4, 6, old_stone, [(door_x, door_z), (door_x, door_z + 1)], 0.5)
    for z in range(z0 + 2, z1 - 1, 2):
        for x in (x0 + 1, x1 - 1):
            if s.get(x, 1, z) == AIR and rng.random() < 0.8:
                s.set(x, 1, z, (WOOD_SLAB, 5) if rng.random() < 0.7 else (WOOL, rng.choice([RED, BROWN])))
    chest(s, x0 + 1 if west else x1 - 1, 1, z0 + 1, 5 if west else 4)
    webs_in(s, rng, 14, x0 + 1, z0 + 1, x1 - 1, z1 - 1)


def garrison_chapel(s, rng, x0, z0, x1, z1, door_x, door_z, west):
    """The garrison's chapel: a broken altar at its north end, pews burnt to their legs."""
    hall(s, rng, x0, z0, x1, z1, 4, 8, old_stone, [(door_x, door_z), (door_x, door_z + 1)], 0.35)
    mid = (x0 + x1) // 2
    s.set(mid - 1, 1, z0 + 1, (STONE_BRICK, 3))
    s.set(mid, 1, z0 + 1, (STONE_BRICK, 3))
    s.set(mid - 1, 2, z0 + 1, (SKULL, 1))
    s.set(mid + 1, 1, z0 + 1, (SLAB, 5))
    for z in range(z0 + 4, z1 - 1, 2):
        for x in (x0 + 2, x0 + 3, x1 - 3, x1 - 2):
            if rng.random() < 0.6 and s.get(x, 1, z) == AIR:
                s.set(x, 1, z, (WOOD_SLAB, 5) if rng.random() < 0.6 else (FENCE, 0))
    chest(s, x1 - 1 if west else x0 + 1, 1, z0 + 1, 4 if west else 5)
    webs_in(s, rng, 14, x0 + 1, z0 + 1, x1 - 1, z1 - 1)


def garrison_smithy(s, rng, x0, z0, x1, z1, door_x, door_z, west):
    """The smithy: an anvil, a cauldron, a furnace gone cold, weapon racks."""
    hall(s, rng, x0, z0, x1, z1, 3, 6, old_stone, [(door_x, door_z), (door_x, door_z + 1)], 0.4)
    back = x0 + 1 if west else x1 - 1
    step = 1 if west else -1
    s.set(back + step, 1, z0 + 2, (ANVIL, 0))
    s.set(back, 1, z0 + 1, (FURNACE, 5 if west else 4))
    s.set(back, 1, z0 + 3, (CAULDRON, 0))
    for z in range(z0 + 5, z1):
        if rng.random() < 0.8:
            s.set(back, 1, z, (FENCE, 0))
    chest(s, back + 2 * step, 1, z1 - 1, 2)
    webs_in(s, rng, 8, x0 + 1, z0 + 1, x1 - 1, z1 - 1)


def garrison_prison(s, rng, x0, z0, x1, z1, door_x, door_z, west):
    """The prison: cells behind iron bars down its back wall, a skeleton or two left in them."""
    hall(s, rng, x0, z0, x1, z1, 4, 6, dark_stone, [(door_x, door_z), (door_x, door_z + 1)], 0.6)
    back = x0 + 1 if west else x1 - 1
    step = 1 if west else -1
    bars = back + 3 * step
    for z in range(z0 + 1, z1):
        if (z - z0) % 4 == 0:
            for i in range(3):
                s.set(back + i * step, 1, z, dark_stone(rng))
                s.set(back + i * step, 2, z, dark_stone(rng))
        else:
            for y in (1, 2):
                if rng.random() < 0.85:
                    s.set(bars, y, z, (IRON_BARS, 0))
    for z in range(z0 + 2, z1 - 1, 4):
        if rng.random() < 0.7:
            s.set(back, 1, z, (SKULL, 1))
    chest(s, x1 - 1 if west else x0 + 1, 1, z1 - 1, 2)
    webs_in(s, rng, 12, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 2, 4)


def garrison_stable(s, rng, x0, z0, x1, z1, door_x, door_z, west):
    """The stable: low walls, stalls of fence and rotten hay."""
    hall(s, rng, x0, z0, x1, z1, 2, 4, old_stone, [(door_x, door_z), (door_x, door_z + 1)], 0.3)
    back = x0 + 1 if west else x1 - 1
    step = 1 if west else -1
    for z in range(z0 + 3, z1 - 1, 3):
        for i in range(3):
            s.set(back + i * step, 1, z, (FENCE, 0))
    for _ in range(5):
        x, z = rng.randint(x0 + 1, x1 - 1), rng.randint(z0 + 1, z1 - 1)
        if s.get(x, 1, z) == AIR:
            s.set(x, 1, z, (HAY, 0))
    webs_in(s, rng, 6, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 2, 3)


def garrison_storehouse(s, rng, x0, z0, x1, z1, door_x, door_z, west):
    """The storehouse: barrels and crates, mostly burnt, and the garrison's stores."""
    hall(s, rng, x0, z0, x1, z1, 4, 6, old_stone, [(door_x, door_z), (door_x, door_z + 1)], 0.5)
    back = x0 + 1 if west else x1 - 1
    for _ in range(10):
        x, z = rng.randint(x0 + 1, x1 - 1), rng.randint(z0 + 1, z1 - 1)
        if s.get(x, 1, z) == AIR and abs(x - door_x) > 1:
            s.set(x, 1, z, rng.choice([(PLANKS, 5), (LOG2, 1), (WOOD_SLAB, 5), (HAY, 0)]))
    chest(s, back, 1, z0 + 1, 5 if west else 4)
    chest(s, back, 1, z1 - 1, 5 if west else 4)
    webs_in(s, rng, 12, x0 + 1, z0 + 1, x1 - 1, z1 - 1)


def garrison_fallen(s, rng, x0, z0, x1, z1, door_x, door_z, west):
    """A building fallen in: the stumps of its walls and a heap of stone, a chest somewhere under it."""
    walls = ring(x0, z0, x1, z1)
    for (x, z), h in zip(walls, crumbling(len(walls), 0, 3, rng)):
        if h:
            column(s, x, z, h, old_stone, rng)
    rubble(s, rng, (x1 - x0) * (z1 - z0) // 3, x0 + 1, z0 + 1, x1 - 1, z1 - 1)
    for _ in range(6):
        x, z = rng.randint(x0 + 1, x1 - 1), rng.randint(z0 + 1, z1 - 1)
        if s.get(x, 1, z) != AIR and s.get(x, 2, z) == AIR:
            s.set(x, 2, z, (SLAB, 3) if rng.random() < 0.5 else old_stone(rng))
    chest(s, rng.randint(x0 + 1, x1 - 1), 1, rng.randint(z0 + 1, z1 - 1), 2)
    webs_in(s, rng, 4, x0, z0, x1, z1, 1, 3)


def old_fortress(seed):
    """The old fortress, the greatest ruin in Forinthry and one of few: a whole walled stronghold of the God Wars,
    fifty-five blocks across. A curtain wall two thick runs round it, crumbling, with great round towers at its
    corners and the middle of its sides, a gatehouse with its portcullis rusted half down, and breaches where the
    wall has fallen. Inside stand the buildings of the garrison, burnt out and hung with cobwebs: the keep with its
    great hall and the stubs of its upper floor, the barracks, the chapel and its graves, the smithy, the prison
    with its cells, a stable and a storehouse, round a yard with a dry well. Ghosts walk it, with skeletons and
    zombies, and deadly red spiders nest in its webs."""
    rng = random.Random(seed)
    s = Struct(55, 55, 18, 1)
    ground(s, rng, 0.1, [(0.1, (GRAVEL, 0)), (0.06, BRICK), (0.04, CRACKED_BRICK)])
    mid = s.w // 2
    x0, z0, x1, z1 = 2, 2, s.w - 3, s.l - 3
    # The road in from the gate to the keep, and across the yard.
    for z in range(0, 31):
        for x in range(mid - 2, mid + 3):
            if rng.random() < 0.8:
                s.set(x, 0, z, (GRAVEL, 0) if rng.random() < 0.6 else rng.choice([BRICK, CRACKED_BRICK, (COBBLE, 0)]))
    for x in range(6, 49):
        for z in (25, 26):
            if rng.random() < 0.6:
                s.set(x, 0, z, (GRAVEL, 0) if rng.random() < 0.6 else CRACKED_BRICK)

    # The curtain wall.
    outer = ring(x0, z0, x1, z1)
    for (x, z), h in zip(outer, crumbling(len(outer), 3, 9, rng)):
        column(s, x, z, h, old_stone, rng)
    inner = ring(x0 + 1, z0 + 1, x1 - 1, z1 - 1)
    for (x, z), h in zip(inner, crumbling(len(inner), 2, 8, rng)):
        column(s, x, z, h, old_stone, rng)
    # Breaches.
    for bx, bz, along_x in ((rng.randint(10, 20), z1, True), (x0, rng.randint(10, 18), False),
                            (x1, rng.randint(36, 44), False)):
        width = rng.randint(3, 5)
        for i in range(width):
            for y in range(1, 19):
                for t in (0, -1 if bz == z1 or bx == x1 else 1):
                    x = bx + (i if along_x else t)
                    z = bz + (t if along_x else i)
                    if s.inside(x, y, z):
                        s.set(x, y, z, AIR)
        rubble(s, rng, 10, max(0, bx - 4), max(0, bz - 4), min(s.w - 1, bx + 7), min(s.l - 1, bz + 7))

    # Towers: the corners, the middle of the sides and back, and the gatehouse.
    for cx, cz in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        big_tower(s, rng, cx, cz, rng.randint(11, 17), old_stone)
    for cx, cz in ((x0 + 1, mid), (x1 - 1, mid)):
        big_tower(s, rng, cx, cz, rng.randint(9, 13), old_stone)
    for cx in (mid - 5, mid + 5):
        big_tower(s, rng, cx, z0 + 1, rng.randint(12, 15), old_stone)
    # The gateway through them, an arch over it, the portcullis rusted half down, chains hanging by it.
    for y in range(1, 19):
        for x in (mid - 1, mid, mid + 1):
            for z in range(0, z0 + 5):
                if s.inside(x, y, z):
                    s.set(x, y, z, AIR)
    for x in (mid - 2, mid - 1, mid, mid + 1, mid + 2):
        for z in (z0, z0 + 1, z0 + 2):
            for y in (5, 6, 7):
                s.set(x, y, z, old_stone(rng))
        s.set(x, 8, z0, (SLAB, 5) if rng.random() < 0.6 else AIR)
    for x in (mid - 1, mid + 1):
        s.set(x, 4, z0, (STONE_BRICK_STAIRS, (UP_WEST if x < mid else UP_EAST) | UPSIDE_DOWN))
    drop = rng.randint(1, 2)
    for x in (mid - 1, mid, mid + 1):
        for y in range(5 - drop, 5):
            if rng.random() < 0.85:
                s.set(x, y, z0 + 1, (IRON_BARS, 0))
    for x in (mid - 2, mid + 2):
        if rng.random() < 0.7:
            s.set(x, 4, z0 - 1, (IRON_BARS, 0))
    for x in (mid - 9, mid + 9):
        brazier(s, x, z0 - 2)

    # The keep: the great hall in front, the lord's rooms behind, and the stubs of the floor above both. Its size
    # and the depth of its hall differ from one fortress to the next.
    half = rng.randint(7, 9)
    kx0, kz0, kx1, kz1 = mid - half, rng.randint(28, 32), mid + half, 47
    split = kz1 - rng.randint(5, 7)
    beam = hall(s, rng, kx0, kz0, kx1, kz1, 7, 13, dark_stone, [(mid, kz0), (mid - 1, kz0), (mid + 1, kz0)], 0.2,
                floor=lambda r: r.choice([BRICK, BRICK, CRACKED_BRICK, (GRAYSTONE, 0)]))
    for x in range(kx0 + 1, kx1):
        for z in range(kz0 + 1, kz1):
            if s.get(x, beam, z) == AIR and rng.random() < 0.45:
                s.set(x, beam, z, (PLANKS, 5) if rng.random() < 0.5 else (WOOD_SLAB, 5 | 8))
    # Buttresses down the hall, and the wall between the hall and the rooms behind with its doorway.
    for z in range(kz0, kz1 + 1, 4):
        for x in (kx0 - 1, kx1 + 1):
            column(s, x, z, rng.randint(3, 8), dark_stone, rng)
    inner_wall = [(x, split) for x in range(kx0 + 1, kx1)]
    for (x, z), h in zip(inner_wall, crumbling(len(inner_wall), 3, beam - 1, rng)):
        column(s, x, z, h, dark_stone, rng)
    door = mid + rng.choice((-3, 0, 3))
    for y in (1, 2, 3):
        s.set(door, y, split, AIR)
    # The throne on its dais at the back of the hall, braziers either side, and the hall's long table, burnt.
    dais = split - 2
    for x in range(mid - 2, mid + 3):
        s.set(x, 1, dais, (GRAYSTONE_SLAB, 0))
    if rng.random() < 0.75:
        s.set(mid, 2, dais, (GRAYSTONE_STAIRS, UP_SOUTH))
        s.set(mid - 1, 2, dais, (COBBLE_WALL, 0))
        s.set(mid + 1, 2, dais, (COBBLE_WALL, 0))
    else:
        s.set(mid + 1, 1, dais - 1, (GRAYSTONE_STAIRS, UP_EAST))       # the throne thrown down
    for x in (mid - 4, mid + 4):
        if rng.random() < 0.8:
            brazier(s, x, dais)
    for z in range(kz0 + 3, dais - 1):
        for x in (mid - 4, mid + 4) if rng.random() < 0.5 else (mid,):
            if rng.random() < 0.7:
                s.set(x, 1, z, (WOOD_SLAB, 5 | 8) if rng.random() < 0.7 else (FENCE, 0))
    # A stair up one side to the broken floor.
    side = rng.choice((kx0 + 1, kx1 - 1))
    for i in range(min(beam - 1, split - kz0 - 3)):
        s.set(side, 1 + i, kz0 + 2 + i, (STONE_BRICK_STAIRS, UP_SOUTH))
        for y in range(1, 1 + i):
            s.set(side, y, kz0 + 2 + i, BRICK)
    # The rooms behind: bookshelves fallen, a bed burnt, and the lord's chests.
    for x in range(kx0 + 1, kx0 + 5):
        if rng.random() < 0.6:
            s.set(x, 1, kz1 - 1, (BOOKSHELF, 0))
    s.set(kx1 - 2, 1, kz1 - 1, (WOOL, RED))
    s.set(kx1 - 3, 1, kz1 - 1, (WOOL, RED))
    chest(s, mid + 2, 1, kz1 - 1, 2)
    chest(s, kx0 + 1, 1, split + 1, 5)
    for x, z in ((kx0 + 1, kz0 + 1), (kx1 - 1, kz1 - 1)):
        s.set(x, 1, z, (SKULL, 1))
    webs_in(s, rng, 30, kx0 + 1, kz0 + 1, kx1 - 1, kz1 - 1, 2, beam + 2)

    # The garrison's buildings down both sides of the yard, a different lot in each place every time, their doors
    # on the yard; now and then one has fallen to a heap of stone.
    slots = [(6, 7, 15, 22), (6, 27, 15, 39), (6, 42, 16, 49), (39, 6, 48, 21), (39, 27, 47, 37), (38, 41, 48, 49)]
    kinds = [garrison_barracks, garrison_chapel, garrison_smithy, garrison_prison, garrison_stable,
             garrison_storehouse]
    rng.shuffle(kinds)
    if rng.random() < 0.5:
        kinds[rng.randrange(len(kinds))] = garrison_fallen
    for (bx0, bz0, bx1, bz1), build in zip(slots, kinds):
        west = bx0 < mid
        door_x = bx1 if west else bx0
        door_z = (bz0 + bz1) // 2
        build(s, rng, bx0, bz0, bx1, bz1, door_x, door_z, west)
        if build is garrison_chapel:
            # Its graves in the yard before it.
            gx0 = bx1 + 3 if west else bx0 - 8
            for gx in range(gx0, gx0 + 6, 2):
                for gz in range(bz0 + 1, bz1 - 2, 4):
                    if rng.random() < 0.75:
                        gravestone(s, rng, gx, gz)

    # The yard: a dry well, a burnt cart, a gibbet, here or there.
    wx, wz = rng.choice(((19, 14), (32, 14), (19, 20), (32, 20)))
    for dx in range(3):
        for dz in range(3):
            if (dx, dz) != (1, 1):
                s.set(wx + dx, 1, wz + dz, (COBBLE, 0) if rng.random() < 0.6 else (MOSSY, 0))
    s.set(wx + 1, 0, wz + 1, (OBSIDIAN, 0))
    for dx, dz in ((0, 0), (2, 0), (0, 2), (2, 2)):
        if rng.random() < 0.6:
            s.set(wx + dx, 2, wz + dz, (FENCE, 0))
    gx, gz = (33 if wx < mid else 21), rng.randint(9, 23)
    if rng.random() < 0.7:
        for y in range(1, 5):
            s.set(gx, y, gz, (FENCE, 0))
        s.set(gx + 1, 4, gz, (FENCE, 0))
    cx, cz = (wx, wz + 6) if wz < 18 else (wx, wz - 5)
    for x in (cx, cx + 1):
        s.set(x, 1, cz, (WOOD_SLAB, 5))
    s.set(cx - 1, 1, cz, (LOG2, 5))
    rubble(s, rng, 40, 5, 5, s.w - 6, s.l - 6)
    for _ in range(12):
        x, z = rng.randint(4, s.w - 5), rng.randint(4, s.l - 5)
        if s.get(x, 1, z) == AIR and s.get(x, 0, z)[0] == CHARRED_EARTH:
            s.set(x, 0, z, (DIRT, 1))
            s.set(x, 1, z, (DEAD_BUSH, 0))
    # Cobwebs in the towers and the angles of the walls too.
    webs(s, rng, 30)
    for _ in range(14):
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


def trodden(s, rng):
    """Ground worn bare round a camp: charred earth, gravel and ash."""
    ground(s, rng, 0.45, [(0.25, (GRAVEL, 0))])


def camp_fire(seed):
    """The middle of a bandits' camp: a fire in a ring of stones on trodden ground, logs to sit on round it, and in one
    variant a spit over it."""
    rng = random.Random(seed)
    s = Struct(7, 7, 4, 1)
    trodden(s, rng)
    cx, cz = 3, 3
    s.set(cx, 0, cz, (NETHERRACK, 0))
    s.set(cx, 1, cz, (FIRE, 0))
    for dx, dz in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)):
        if rng.random() < 0.85:
            s.set(cx + dx, 1, cz + dz, (SLAB, 3))
    seats = [((0, 2), (0, 3), 8), ((6, 3), (6, 4), 8), ((2, 6), (3, 6), 4), ((3, 0), (4, 0), 4)]
    rng.shuffle(seats)
    for a, b, meta in seats[:rng.randint(2, 3)]:
        s.set(a[0], 1, a[1], (LOG, meta))
        s.set(b[0], 1, b[1], (LOG, meta))
    if seed % 2 == 0:
        s.set(cx - 2, 1, cz, AIR)
        s.set(cx + 2, 1, cz, AIR)
        for x in (cx - 2, cx + 2):
            s.fill(x, 1, cz, x, 2, cz, (FENCE, 0))
        for x in range(cx - 2, cx + 3):
            s.set(x, 3, cz, (WOOD_SLAB, 0))
    tidy(s)
    return s


def camp_tent(seed, kind, color):
    """A bandits' tent, open at the front (z = 0) toward the fire: a small ridge tent, a long one, a great tent with
    a pole and a bedroll or two, or a lean-to of hides on posts."""
    rng = random.Random(seed)
    wool = (WOOL, color)
    if kind == "great":
        s = Struct(7, 8, 6, 1)
        trodden(s, rng)
        for z in range(s.l):
            for x, y in ((0, 1), (1, 2), (2, 3), (3, 4), (4, 3), (5, 2), (6, 1)):
                s.set(x, y, z, wool)
        for x in range(1, 6):
            s.set(x, 1, s.l - 1, wool)
        s.fill(2, 2, s.l - 1, 4, 2, s.l - 1, wool)
        s.set(3, 3, s.l - 1, wool)
        s.fill(3, 1, 0, 3, 3, 0, (FENCE, 0))
        s.fill(3, 1, s.l - 2, 3, 3, s.l - 2, (FENCE, 0))
        for x in (1, 5):
            for z in range(2, 5):
                s.set(x, 1, z, (CARPET, rng.choice((BROWN, RED, GRAY))))
        s.set(4, 1, s.l - 2, (PLANKS, 1))
        s.set(4, 2, s.l - 2, (WOOD_SLAB, 1))
        tidy(s)
        return s
    if kind == "lean_to":
        s = Struct(5, 4, 4, 1)
        trodden(s, rng)
        for x in range(5):
            s.set(x, 3, 0, (FENCE, 0) if x in (0, 4) else wool)
            s.set(x, 2, 1, wool)
            s.set(x, 1, 2, wool)
        for x in (0, 4):
            s.fill(x, 1, 0, x, 2, 0, (FENCE, 0))
        s.set(rng.randint(1, 3), 1, 1, (CARPET, BROWN))
        s.set(rng.choice((0, 4)), 1, 3, (HAY, 0))
        tidy(s)
        return s
    length = 5 if kind == "small" else 7
    s = Struct(5, length, 4, 1)
    trodden(s, rng)
    for z in range(length):
        s.set(2, 3, z, wool)
        for x in (1, 3):
            s.set(x, 2, z, wool)
        for x in (0, 4):
            s.set(x, 1, z, wool)
    s.fill(2, 1, 0, 2, 2, 0, (FENCE, 0))
    for x in (1, 2, 3):
        s.set(x, 1, length - 1, wool)
    s.set(2, 2, length - 1, wool)
    s.set(2, 1, 1, (CARPET, RED if color != RED else BROWN))
    if length > 5:
        s.set(1, 1, length - 2, (PLANKS, 0))
        # A flap torn open along one side.
        side = rng.choice((0, 4))
        s.set(side, 1, rng.randint(2, length - 3), AIR)
    tidy(s)
    return s


def camp_lookout(seed):
    """A bandits' lookout: a plank platform on log posts with a ladder up and a rail round it; the taller one has a
    roof of slabs over it."""
    rng = random.Random(seed)
    tall = seed % 2 == 0
    s = Struct(5, 5, 10 if tall else 7, 1)
    trodden(s, rng)
    deck = 5 if tall else 4
    for x, z in ((0, 0), (4, 0), (0, 4), (4, 4)):
        s.fill(x, 1, z, x, deck, z, (LOG, 0))
    for x in range(5):
        for z in range(5):
            if rng.random() < 0.92:
                s.set(x, deck, z, (PLANKS, 1))
    for x, z in ring(0, 0, 4, 4):
        if (x, z) != (2, 0) and rng.random() < 0.85:
            s.set(x, deck + 1, z, (FENCE, 0))
    # Cross braces between the posts.
    for x in (1, 2, 3):
        s.set(x, deck - 2, 4, (FENCE, 0))
    s.set(2, deck, 0, AIR)
    for y in range(1, deck + 1):
        s.set(2, y, 0, (LADDER, 2))
    if tall:
        for x, z in ((0, 0), (4, 0), (0, 4), (4, 4)):
            s.fill(x, deck + 1, z, x, deck + 2, z, (FENCE, 0))
        for x in range(5):
            for z in range(5):
                if rng.random() < 0.8:
                    s.set(x, deck + 3, z, (WOOD_SLAB, 1))
    if not tall:
        s.set(4, deck + 2, 4, (TORCH, 5))
    tidy(s)
    return s


def camp_cage(seed):
    """Where the bandits keep whoever they caught: a cage of iron bars, its door hanging open, or stocks and a gibbet
    with a skull."""
    rng = random.Random(seed)
    s = Struct(4, 4, 5, 1)
    trodden(s, rng)
    if seed % 2:
        for x in range(3):
            for z in range(3):
                if x in (0, 2) or z in (0, 2):
                    if (x, z) != (1, 0):
                        s.fill(x, 1, z, x, 2, z, (IRON_BARS, 0))
                s.set(x, 3, z, (PLANKS, 1))
        if rng.random() < 0.5:
            s.set(1, 1, 1, (SKULL, 1))
    else:
        s.fill(0, 1, 1, 0, 3, 1, (LOG, 0))
        s.set(1, 3, 1, (FENCE, 0))
        s.set(2, 3, 1, (FENCE, 0))
        s.set(2, 2, 1, (IRON_BARS, 0))
        s.set(2, 1, 1, (SKULL, 1))
        s.set(1, 1, 3, (WOOD_SLAB, 0))
        s.set(2, 1, 3, (WOOD_SLAB, 0))
    tidy(s)
    return s


def camp_stash(seed):
    """The bandits' loot: crates and bales round a chest, and a rack of weapons."""
    rng = random.Random(seed)
    s = Struct(4, 4, 4, 1)
    trodden(s, rng)
    spots = [(x, z) for x in range(4) for z in range(4) if (x, z) != (1, 1)]
    rng.shuffle(spots)
    for x, z in spots[:rng.randint(5, 8)]:
        s.set(x, 1, z, rng.choice(((PLANKS, 0), (PLANKS, 1), (HAY, 0), (LOG, 4))))
        if rng.random() < 0.3:
            s.set(x, 2, z, (WOOD_SLAB, 0))
    s.set(1, 1, 1, AIR)
    chest(s, 1, 1, 1, 3)
    s.set(1, 2, 1, AIR)
    tidy(s)
    return s


# ---------------------------------------------------------------- all of them

VARIANTS = 2
# Graveyards, laid out at random, come in more.
GRAVEYARDS = 6


def structures():
    """Every Forinthry ruin by its file name, as Structs."""
    out = {"old_fortress_%d" % n: old_fortress(700 + n * 7) for n in range(1, 4)}
    rng = random.Random(611)
    for n in range(1, GRAVEYARDS + 1):
        size = rng.randint(25, 29)
        out["graveyard_of_shadows_%d" % n] = graveyard(600 + n, size, size - rng.randint(0, 3), True)
        w, l = rng.randint(14, 19), rng.randint(13, 17)
        out["graveyard_%d" % n] = graveyard(650 + n, w, l, False)
        out["graveyard_%d_webs" % n] = graveyard(650 + n, w, l, False, True)
    for n in range(1, VARIANTS + 1):
        out["camp_fire_%d" % n] = camp_fire(800 + n)
        out["camp_lookout_%d" % n] = camp_lookout(810 + n)
        out["camp_cage_%d" % n] = camp_cage(820 + n)
        out["camp_stash_%d" % n] = camp_stash(830 + n)
        for name, build in (("chapel", chapel), ("house", house),
                            ("watchtower", watchtower), ("chaos_altar", chaos_altar)):
            seed = 900 + n * 10 + sum(map(ord, name))
            out["%s_%d" % (name, n)] = build(seed, False)
            out["%s_%d_webs" % (name, n)] = build(seed, True)
    tents = (("small", BROWN), ("small", GRAY), ("long", RED), ("great", BROWN), ("lean_to", BLACK), ("long", GRAY))
    for n, (kind, color) in enumerate(tents, 1):
        out["camp_tent_%d" % n] = camp_tent(840 + n, kind, color)
    return out
