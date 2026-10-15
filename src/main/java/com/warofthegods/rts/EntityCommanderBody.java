package com.warofthegods.rts;

import java.util.Collection;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.mojang.authlib.properties.Property;
import com.warofthegods.unit.Combat;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/**
 * Where a player commanding from the RTS view really is: their body, left standing where they were, in their skin and
 * their gear, while they look down from above. It shares the player's UUID, so units ordered against the player go
 * for it, and whatever hurts it hurts the player, through the player's own armor, team rules and difficulty. When the
 * player dies, so does it; when they stop commanding they step back into it.
 *
 * <p>
 * It is never saved: a body in a chunk that unloads is gone, and {@link Commanders} puts a new one there when the
 * chunk loads again.
 */
public class EntityCommanderBody extends EntityLiving implements IEntityAdditionalSpawnData {

    private String ownerName = "";
    /** The owner's skin, as the session server signed it, for clients to draw; empty when offline. */
    private String textures = "";
    private String texturesSignature = "";

    public EntityCommanderBody(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        experienceValue = 0;
        for (int slot = 0; slot < 5; slot++) {
            setEquipmentDropChance(slot, 0);
        }
    }

    EntityCommanderBody(World world, EntityPlayerMP owner) {
        this(world);
        entityUniqueID = owner.getUniqueID();
        ownerName = owner.getCommandSenderName();
        Collection<Property> properties = owner.getGameProfile()
            .getProperties()
            .get("textures");
        for (Property property : properties) {
            textures = property.getValue();
            texturesSignature = property.hasSignature() ? property.getSignature() : "";
        }
        setLocationAndAngles(owner.posX, owner.posY, owner.posZ, owner.rotationYaw, owner.rotationPitch);
        rotationYawHead = owner.rotationYawHead;
        renderYawOffset = owner.renderYawOffset;
        fallDistance = owner.fallDistance;
        setCustomNameTag(ownerName);
        setAlwaysRenderNameTag(true);
        follow(owner);
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getTextures() {
        return textures;
    }

    public String getTexturesSignature() {
        return texturesSignature;
    }

    /** The player this is the body of, when they are online and commanding; null on clients. */
    public EntityPlayerMP getOwner() {
        if (worldObj.isRemote) {
            return null;
        }
        EntityPlayerMP owner = Commanders.find(getUniqueID());
        return owner != null && Commanders.get()
            .isCommanding(owner.getUniqueID()) ? owner : null;
    }

    /** Takes on the owner's health and gear, so others see how they are and what they hold. */
    void follow(EntityPlayerMP owner) {
        float max = owner.getMaxHealth();
        if (getEntityAttribute(SharedMonsterAttributes.maxHealth).getBaseValue() != max) {
            getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(max);
        }
        setHealth(Math.max(0, owner.getHealth()));
        for (int slot = 0; slot < 5; slot++) {
            ItemStack theirs = owner.getEquipmentInSlot(slot);
            if (!ItemStack.areItemStacksEqual(theirs, getEquipmentInSlot(slot))) {
                setCurrentItemOrArmor(slot, theirs == null ? null : theirs.copy());
            }
        }
    }

    @Override
    public void onUpdate() {
        if (!worldObj.isRemote) {
            EntityPlayerMP owner = getOwner();
            if (owner == null) {
                setDead();
                return;
            }
            follow(owner);
        }
        super.onUpdate();
    }

    /** Hurts the owner instead, and shows it here. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (worldObj.isRemote || isEntityInvulnerable()) {
            return false;
        }
        // An arrow of the commander's team or an ally flies on past the body, not the hovering owner.
        if (Combat.passesFriend(source, this)) {
            return false;
        }
        EntityPlayerMP owner = getOwner();
        if (owner == null || !owner.isEntityAlive()) {
            return false;
        }
        if (owner.capabilities.isCreativeMode && !source.canHarmInCreative()) {
            return false;
        }
        boolean hurt;
        // Commanding makes the owner unhurtable where they hover; for this one hit they are here.
        boolean disableDamage = owner.capabilities.disableDamage;
        double motionX = owner.motionX;
        double motionY = owner.motionY;
        double motionZ = owner.motionZ;
        owner.capabilities.disableDamage = false;
        try {
            hurt = owner.attackEntityFrom(source, amount);
        } finally {
            owner.capabilities.disableDamage = disableDamage;
            // The knockback is the body's, not the hovering owner's.
            owner.motionX = motionX;
            owner.motionY = motionY;
            owner.motionZ = motionZ;
            owner.velocityChanged = false;
        }
        if (!hurt) {
            return false;
        }
        follow(owner);
        worldObj.setEntityState(this, (byte) 2);
        Entity attacker = source.getEntity();
        if (attacker != null) {
            double dx = attacker.posX - posX;
            double dz = attacker.posZ - posZ;
            while (dx * dx + dz * dz < 1.0E-4) {
                dx = (Math.random() - Math.random()) * 0.01;
                dz = (Math.random() - Math.random()) * 0.01;
            }
            knockBack(attacker, amount, dx, dz);
            velocityChanged = true;
        }
        return true;
    }

    @Override
    protected String getHurtSound() {
        return "game.player.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "game.player.die";
    }

    @Override
    protected String getLivingSound() {
        return null;
    }

    /** Stands still and looks where the owner last looked. */
    @Override
    protected void updateEntityActionState() {}

    @Override
    protected boolean isAIEnabled() {
        return false;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected boolean interact(EntityPlayer player) {
        return false;
    }

    @Override
    protected void dropFewItems(boolean recentlyHit, int looting) {}

    @Override
    protected void dropEquipment(boolean recentlyHit, int looting) {}

    @Override
    public boolean writeToNBTOptional(NBTTagCompound tag) {
        return false;
    }

    @Override
    public boolean writeMountToNBT(NBTTagCompound tag) {
        return false;
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeLong(entityUniqueID.getMostSignificantBits());
        buffer.writeLong(entityUniqueID.getLeastSignificantBits());
        ByteBufUtils.writeUTF8String(buffer, ownerName);
        ByteBufUtils.writeUTF8String(buffer, textures);
        ByteBufUtils.writeUTF8String(buffer, texturesSignature);
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        entityUniqueID = new UUID(buffer.readLong(), buffer.readLong());
        ownerName = ByteBufUtils.readUTF8String(buffer);
        textures = ByteBufUtils.readUTF8String(buffer);
        texturesSignature = ByteBufUtils.readUTF8String(buffer);
    }
}
