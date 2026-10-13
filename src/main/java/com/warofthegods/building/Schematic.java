package com.warofthegods.building;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.world.World;

/**
 * A structure from an MCEdit .schematic file, as the beta map editor saves them: blocks and metadata in y, z, x
 * order, with the editor's ground layer (EditorGround) marking which layer stands level with the terrain. Layers
 * below it are foundations that go into the ground.
 *
 * <p>
 * Structures are pasted in one of four rotations, quarter turns clockwise seen from above. At rotation 0 the
 * structure's front, its z = 0 side, faces north.
 */
public final class Schematic {

    public final int width;
    public final int height;
    public final int length;
    /** The layer that replaces the terrain's top block. */
    public final int ground;
    private final byte[] blocks;
    private final byte[] data;
    private final List<int[]> signPositions = new ArrayList<>();
    private final List<String[]> signTexts = new ArrayList<>();

    private Schematic(NBTTagCompound tag) {
        width = tag.getShort("Width");
        height = tag.getShort("Height");
        length = tag.getShort("Length");
        ground = Math.max(0, Math.min(height - 1, tag.getInteger("EditorGround")));
        blocks = tag.getByteArray("Blocks");
        data = tag.getByteArray("Data");
        if (width <= 0 || height <= 0
            || length <= 0
            || blocks.length != width * height * length
            || data.length != blocks.length) {
            throw new IllegalArgumentException("not a valid schematic");
        }
        NBTTagList tiles = tag.getTagList("TileEntities", 10);
        for (int i = 0; i < tiles.tagCount(); i++) {
            NBTTagCompound tile = tiles.getCompoundTagAt(i);
            if ("Sign".equals(tile.getString("id"))) {
                signPositions.add(new int[] { tile.getInteger("x"), tile.getInteger("y"), tile.getInteger("z") });
                signTexts.add(
                    new String[] { tile.getString("Text1"), tile.getString("Text2"), tile.getString("Text3"),
                        tile.getString("Text4") });
            }
        }
    }

    /** Reads assets/warofthegods/structures/name.schematic from the mod jar. */
    public static Schematic load(String name) {
        String path = "/assets/warofthegods/structures/" + name + ".schematic";
        try (InputStream in = Schematic.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + path);
            }
            return new Schematic(CompressedStreamTools.readCompressed(in));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private int index(int x, int y, int z) {
        return (y * length + z) * width + x;
    }

    public int blockId(int x, int y, int z) {
        return blocks[index(x, y, z)] & 0xFF;
    }

    public int meta(int x, int y, int z) {
        return data[index(x, y, z)] & 0xF;
    }

    /** Footprint size along x for a rotation. */
    public int sizeX(int rotation) {
        return (rotation & 1) == 0 ? width : length;
    }

    public int sizeZ(int rotation) {
        return (rotation & 1) == 0 ? length : width;
    }

    /**
     * How tall each column stands above the ground layer, unrotated, x + z * width: 0 where there is nothing above
     * ground, otherwise the highest block's height above ground plus one. The web map draws building sites with it.
     */
    public int[] columnHeights() {
        int[] heights = new int[width * length];
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                for (int y = height - 1; y >= ground; y--) {
                    if (blockId(x, y, z) != 0) {
                        heights[x + z * width] = y - ground + 1;
                        break;
                    }
                }
            }
        }
        return heights;
    }

    /** Where a structure cell lands turned rotation quarter turns, as x, z offsets from the footprint's corner. */
    public int[] turned(int x, int z, int rotation) {
        return rotate(x, z, rotation);
    }

    /** The metadata of the block at x, y, z as it is placed turned rotation quarter turns. */
    public int turnedMeta(int x, int y, int z, int rotation) {
        return rotateMeta(Block.getBlockById(blockId(x, y, z)), meta(x, y, z), rotation);
    }

    /** Where a structure cell lands, as x, z offsets from the footprint's north-west corner. */
    private int[] rotate(int x, int z, int rotation) {
        int w = width;
        int l = length;
        for (int i = 0; i < (rotation & 3); i++) {
            int nx = l - 1 - z;
            z = x;
            x = nx;
            int t = w;
            w = l;
            l = t;
        }
        return new int[] { x, z };
    }

    /**
     * Pastes the structure with its footprint's north-west corner at minX, minZ and its ground layer at groundY. The
     * structure's own air clears what was there; foundations are filled down to solid ground and anything above the
     * structure on its footprint is cleared, so it does not end up buried in a hill or floating over a dip.
     */
    public void paste(World world, int minX, int minZ, int groundY, int rotation) {
        int baseY = groundY - ground;
        // Solid blocks and air first, then what hangs on them: torches, doors, ladders, signs, crops.
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < height; y++) {
                int wy = baseY + y;
                if (wy < 1 || wy > 255) {
                    continue;
                }
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        Block block = Block.getBlockById(blockId(x, y, z));
                        boolean attached = block != Blocks.air && !block.isOpaqueCube()
                            && !block.getMaterial()
                                .isLiquid();
                        if (attached != (pass == 1)) {
                            continue;
                        }
                        int[] at = rotate(x, z, rotation);
                        int wx = minX + at[0];
                        int wz = minZ + at[1];
                        if (block == Blocks.air && world.isAirBlock(wx, wy, wz)) {
                            continue;
                        }
                        world.setBlock(wx, wy, wz, block, rotateMeta(block, meta(x, y, z), rotation), 2);
                    }
                }
            }
        }
        fillAndClear(world, minX, minZ, baseY, rotation);
        for (int i = 0; i < signPositions.size(); i++) {
            int[] p = signPositions.get(i);
            int[] at = rotate(p[0], p[2], rotation);
            TileEntity tile = world.getTileEntity(minX + at[0], baseY + p[1], minZ + at[1]);
            if (tile instanceof TileEntitySign) {
                String[] text = signTexts.get(i);
                for (int line = 0; line < 4; line++) {
                    String value = text[line];
                    ((TileEntitySign) tile).signText[line] = value.length() > 15 ? value.substring(0, 15) : value;
                }
                world.markBlockForUpdate(minX + at[0], baseY + p[1], minZ + at[1]);
            }
        }
    }

    private void fillAndClear(World world, int minX, int minZ, int baseY, int rotation) {
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int[] at = rotate(x, z, rotation);
                int wx = minX + at[0];
                int wz = minZ + at[1];
                int lowest = -1;
                for (int y = 0; y < height; y++) {
                    if (blockId(x, y, z) != 0) {
                        lowest = y;
                        break;
                    }
                }
                if (lowest >= 0 && lowest <= ground) {
                    Block bottom = Block.getBlockById(blockId(x, lowest, z));
                    Block fill = bottom == Blocks.grass || !bottom.isOpaqueCube() ? Blocks.dirt : bottom;
                    int fillMeta = fill == bottom ? meta(x, lowest, z) : 0;
                    for (int wy = baseY + lowest - 1, depth = 0; wy > 0 && depth < 24; wy--, depth++) {
                        if (isGround(world.getBlock(wx, wy, wz))) {
                            break;
                        }
                        world.setBlock(wx, wy, wz, fill, fillMeta, 2);
                    }
                }
                for (int wy = baseY + height; wy <= Math.min(255, baseY + height + 32); wy++) {
                    if (!world.isAirBlock(wx, wy, wz)) {
                        world.setBlock(wx, wy, wz, Blocks.air, 0, 2);
                    }
                }
            }
        }
    }

    /** Ground a foundation can rest on: anything solid that is not part of a tree. */
    static boolean isGround(Block block) {
        Material material = block.getMaterial();
        return material.isSolid() && material != Material.leaves
            && material != Material.wood
            && material != Material.plants
            && material != Material.vine;
    }

    /** Metadata of a block turned a number of quarter turns clockwise, for blocks that face a way. */
    static int rotateMeta(Block block, int meta, int rotation) {
        for (int i = 0; i < (rotation & 3); i++) {
            meta = rotateOnce(block, meta);
        }
        return meta;
    }

    private static final int[] FACING_CW = { 0, 1, 5, 4, 2, 3 }; // 2 N -> 5 E -> 3 S -> 4 W -> 2 N
    private static final int[] TORCH_CW = { 0, 3, 4, 2, 1, 5 }; // 1 E -> 3 S -> 2 W -> 4 N -> 1 E
    private static final int[] STAIRS_CW = { 2, 3, 1, 0 }; // 0 E -> 2 S -> 1 W -> 3 N -> 0 E
    private static final int[] TRAPDOOR_CW = { 3, 2, 0, 1 }; // 0 S -> 3 W -> 1 N -> 2 E -> 0 S

    private static int rotateOnce(Block block, int meta) {
        int id = Block.getIdFromBlock(block);
        switch (id) {
            case 53: // stairs
            case 67:
            case 108:
            case 109:
            case 114:
            case 128:
            case 134:
            case 135:
            case 136:
            case 156:
            case 163:
            case 164:
                return (meta & 12) | STAIRS_CW[meta & 3];
            case 50: // torches
            case 75:
            case 76:
                return meta >= 1 && meta <= 4 ? TORCH_CW[meta] : meta;
            case 23: // things facing one of four sides by 2 to 5
            case 54:
            case 61:
            case 62:
            case 65:
            case 68:
            case 130:
            case 146:
            case 158:
                return (meta & 7) < 6 ? (meta & 8) | FACING_CW[meta & 7] : meta;
            case 63: // standing sign
                return (meta + 4) & 15;
            case 64: // doors: the top half only holds the hinge side
            case 71:
                return (meta & 8) != 0 ? 8 : (meta & 12) | ((meta + 1) & 3);
            case 26: // bed, pumpkins, fence gate
            case 86:
            case 91:
            case 107:
                return (meta & 12) | ((meta + 1) & 3);
            case 96: // trapdoor
                return (meta & 12) | TRAPDOOR_CW[meta & 3];
            case 17: // logs: swap the x and z axis bits
            case 162:
                int axis = meta & 12;
                return (meta & 3) | (axis == 4 ? 8 : axis == 8 ? 4 : axis);
            default:
                return meta;
        }
    }
}
