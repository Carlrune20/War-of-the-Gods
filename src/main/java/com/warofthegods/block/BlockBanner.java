package com.warofthegods.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A god's banner of cloth hanging flat against a wall or post: Saradomin's star or Zamorak's mark, each in its colors
 * and inverted. A full banner hangs three blocks tall, a top piece on its rod, the emblem and a forked end; a pennant
 * is all of that in one block.
 *
 * <p>
 * The metadata holds which way its face looks in the low two bits, {@link #NORTH} to {@link #WEST}, and which piece it
 * is in the high two, {@link #TOP} to {@link #PENNANT}. It hangs on the block behind its face.
 */
public class BlockBanner extends Block {

    public static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
    public static final int TOP = 0, EMBLEM = 1, BOTTOM = 2, PENNANT = 3;
    private static final String[] PIECES = { "top", "emblem", "bottom", "pennant" };
    private static final float THICK = 1 / 16F;

    /** Such as saradomin or zamorak_inverted. */
    public final String kind;

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public BlockBanner(String kind) {
        super(Material.cloth);
        this.kind = kind;
        setHardness(0.8F);
        setStepSound(soundTypeCloth);
        setBlockName(WarOfTheGods.MODID + ".banner_" + kind);
        setBlockTextureName(WarOfTheGods.MODID + ":banner_" + kind);
        setCreativeTab(CreativeTabs.tabDecorations);
    }

    public static int facing(int meta) {
        return meta & 3;
    }

    public static int piece(int meta) {
        return meta >> 2 & 3;
    }

    /** The metadata turned a quarter clockwise seen from above. */
    public static int turned(int meta) {
        return (meta & 12) | ((meta + 1) & 3);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    public boolean getBlocksMovement(IBlockAccess world, int x, int y, int z) {
        return true;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        setBoundsFor(world.getBlockMetadata(x, y, z));
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0, 0, 0.5F - THICK / 2, 1, 1, 0.5F + THICK / 2);
    }

    private void setBoundsFor(int meta) {
        // Flat against the block behind the face.
        switch (facing(meta)) {
            case NORTH:
                setBlockBounds(0, 0, 1 - THICK, 1, 1, 1);
                break;
            case SOUTH:
                setBlockBounds(0, 0, 0, 1, 1, THICK);
                break;
            case EAST:
                setBlockBounds(0, 0, 0, THICK, 1, 1);
                break;
            default:
                setBlockBounds(1 - THICK, 0, 0, 1, 1, 1);
                break;
        }
    }

    @Override
    public int damageDropped(int meta) {
        return PENNANT << 2;
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int meta) {
        // Hung on the face of the block clicked. On top of or under a block it faces the player instead, which the
        // piece TOP marks for onBlockPlacedBy: the item itself is always a pennant.
        int facing = side == 2 ? NORTH : side == 3 ? SOUTH : side == 4 ? WEST : side == 5 ? EAST : -1;
        return facing < 0 ? TOP << 2 : PENNANT << 2 | facing;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int meta = world.getBlockMetadata(x, y, z);
        int facing = facing(meta);
        if (world.getBlock(x, y, z) != this) {
            return;
        }
        if (piece(meta) == TOP) {
            int look = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
            // Looking south (0) the banner should face north, back at the player.
            facing = new int[] { NORTH, EAST, SOUTH, WEST }[look];
        }
        // A whole banner when there is room below, otherwise a pennant.
        if (world.isAirBlock(x, y - 1, z) && world.isAirBlock(x, y - 2, z) && y > 2) {
            world.setBlock(x, y, z, this, TOP << 2 | facing, 3);
            world.setBlock(x, y - 1, z, this, EMBLEM << 2 | facing, 3);
            world.setBlock(x, y - 2, z, this, BOTTOM << 2 | facing, 3);
        } else {
            world.setBlockMetadataWithNotify(x, y, z, PENNANT << 2 | facing, 3);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons[piece(meta)];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[PIECES.length];
        for (int i = 0; i < PIECES.length; i++) {
            icons[i] = register.registerIcon(getTextureName() + "_" + PIECES[i]);
        }
    }

    /** In the inventory a banner shows flat, as a picture of a pennant. */
    @Override
    @SideOnly(Side.CLIENT)
    public String getItemIconName() {
        return getTextureName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, PENNANT << 2));
    }
}
