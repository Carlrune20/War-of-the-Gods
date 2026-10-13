package com.warofthegods.unit;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * What is left of an animal units killed, lying on its side where it fell, such as a dead pig. It is only the look of
 * it: {@link Hunting} keeps the food workers carve off it, and it goes once that is eaten up. It is not saved, as
 * carcasses do not outlast the server.
 */
public class EntityCarcass extends Entity {

    /** The animals it can be, by the kind number it is drawn as. */
    public static final String[] KINDS = { "cow", "pig", "sheep", "chicken" };

    private static final int KIND = 16;

    private int carcassId;

    public EntityCarcass(World world) {
        super(world);
        setSize(0.9F, 0.5F);
        noClip = true;
    }

    EntityCarcass(World world, int carcassId, int kind, double x, double y, double z, float yaw) {
        this(world);
        this.carcassId = carcassId;
        dataWatcher.updateObject(KIND, (byte) kind);
        setLocationAndAngles(x, y, z, yaw, 0);
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(KIND, (byte) 0);
    }

    /** Which of {@link #KINDS} it is. */
    public int getKind() {
        int kind = dataWatcher.getWatchableObjectByte(KIND);
        return kind >= 0 && kind < KINDS.length ? kind : 0;
    }

    @Override
    public String getCommandSenderName() {
        return "Dead " + KINDS[getKind()];
    }

    @Override
    public void onUpdate() {
        // It lies still; it only goes once its food is eaten up, or with the game it was killed in.
        if (!worldObj.isRemote && Hunting.get(carcassId) == null) {
            setDead();
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound tag) {
        return false;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {}

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {}
}
