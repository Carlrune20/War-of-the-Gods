package com.warofthegods.unit;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.warofthegods.Config;

/**
 * One of Forinthry's wild creatures (see {@link WildKind}): a ghost, a hill giant, a hellhound, a deadly red spider, a
 * gargoyle, a chromatic dragon or a bandit. They are hostile mobs as zombies are, spawned in the Wilderness and round
 * its ruins by {@link Mobs}, and they go after players and units alike. Dragons neither fly nor breathe fire: they
 * wander, and bite and claw. A red spider's bite poisons, a giant's cleaver and a dragon's blow throw their victim
 * back,
 * arrows and spells mostly pass through ghosts, and a gargoyle's stone turns some of every blow.
 */
public class EntityWildMob extends EntityMob {

    /** The data watcher slot of its kind's ordinal, which clients draw it by. */
    private static final int KIND = 16;
    private WildKind kind = WildKind.GHOST;

    public EntityWildMob(World world) {
        super(world);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.0D, false));
        tasks.addTask(3, new EntityAIAttackOnCollide(this, EntityUnit.class, 1.0D, true));
        tasks.addTask(6, new EntityAIWander(this, 0.8D));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityUnit.class, 0, true));
        applyKind(WildKind.GHOST);
    }

    public EntityWildMob(World world, WildKind kind) {
        this(world);
        setKind(kind);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(KIND, (byte) 0);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    public WildKind getKind() {
        return kind;
    }

    /** Makes it the kind given, at full health. */
    public void setKind(WildKind kind) {
        dataWatcher.updateObject(KIND, (byte) kind.ordinal());
        applyKind(kind);
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(kind.health);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(kind.damage);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(kind.speed);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(kind.knockbackResistance());
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(Config.mobDetectRange);
        setHealth(getMaxHealth());
    }

    private void applyKind(WildKind kind) {
        this.kind = kind;
        setSize(kind.width, kind.height);
        isImmuneToFire = kind.fireproof;
        experienceValue = kind.isDragon() ? 40 : kind == WildKind.HILL_GIANT || kind == WildKind.GARGOYLE ? 15 : 6;
    }

    @Override
    public void onUpdate() {
        if (worldObj.isRemote) {
            WildKind watched = WildKind.byOrdinal(dataWatcher.getWatchableObjectByte(KIND));
            if (watched != kind) {
                applyKind(watched);
            }
        }
        super.onUpdate();
    }

    @Override
    public String getCommandSenderName() {
        return hasCustomNameTag() ? getCustomNameTag() : kind.displayName;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (source.isProjectile() || source.isMagicDamage()) {
            amount *= kind.projectileFactor();
        } else if (source.getEntity() != null) {
            amount *= kind.meleeFactor();
        }
        return super.attackEntityFrom(source, amount);
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        boolean hit = super.attackEntityAsMob(target);
        if (!hit || !(target instanceof EntityLivingBase)) {
            return hit;
        }
        if (kind == WildKind.RED_SPIDER) {
            ((EntityLivingBase) target).addPotionEffect(new PotionEffect(Potion.poison.id, 100, 0));
        }
        if (kind == WildKind.HILL_GIANT || kind.isDragon()) {
            // A heavy blow throws its victim back and up.
            double push = kind.isDragon() ? 0.8 : 0.6;
            float yaw = rotationYaw * (float) Math.PI / 180;
            target.addVelocity(-MathHelper.sin(yaw) * push, 0.3, MathHelper.cos(yaw) * push);
        }
        return hit;
    }

    /** Ghosts float down without harm, and nothing big falls far in the Wilderness. */
    @Override
    protected void fall(float distance) {
        if (kind != WildKind.GHOST) {
            super.fall(distance);
        }
    }

    @Override
    protected String getLivingSound() {
        switch (kind) {
            case GHOST:
                return "mob.endermen.idle";
            case HILL_GIANT:
                return "mob.zombie.say";
            case HELLHOUND:
                return "mob.wolf.growl";
            case RED_SPIDER:
                return "mob.spider.say";
            case GARGOYLE:
                return "mob.silverfish.say";
            case BANDIT:
                return null;
            default:
                return "mob.enderdragon.growl";
        }
    }

    @Override
    protected String getHurtSound() {
        switch (kind) {
            case GHOST:
                return "mob.endermen.hit";
            case HILL_GIANT:
                return "mob.zombie.hurt";
            case HELLHOUND:
                return "mob.wolf.hurt";
            case RED_SPIDER:
                return "mob.spider.say";
            case GARGOYLE:
                return "mob.irongolem.hit";
            case BANDIT:
                return "game.player.hurt";
            default:
                return "mob.enderdragon.hit";
        }
    }

    @Override
    protected String getDeathSound() {
        switch (kind) {
            case GHOST:
                return "mob.endermen.death";
            case HILL_GIANT:
                return "mob.zombie.death";
            case HELLHOUND:
                return "mob.wolf.death";
            case RED_SPIDER:
                return "mob.spider.death";
            case GARGOYLE:
                return "mob.irongolem.death";
            case BANDIT:
                return "game.player.die";
            default:
                return "mob.enderdragon.growl";
        }
    }

    @Override
    protected float getSoundVolume() {
        return kind.isDragon() ? 0.6F : 1.0F;
    }

    /** Big things sound deep, a ghost hollow. */
    @Override
    protected float getSoundPitch() {
        float pitch = kind == WildKind.HILL_GIANT || kind == WildKind.GARGOYLE ? 0.55F
            : kind == WildKind.GHOST ? 0.6F : kind.isDragon() ? 1.3F : kind == WildKind.HELLHOUND ? 0.75F : 1.0F;
        return pitch + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        if (kind == WildKind.GHOST) {
            return;
        }
        if (kind == WildKind.RED_SPIDER) {
            playSound("mob.spider.step", 0.15F, 1.0F);
        } else if (kind == WildKind.HILL_GIANT || kind.isDragon() || kind == WildKind.GARGOYLE) {
            playSound("mob.irongolem.walk", 0.6F, 0.8F);
        } else {
            super.func_145780_a(x, y, z, block);
        }
    }

    @Override
    protected Item getDropItem() {
        switch (kind) {
            case RED_SPIDER:
                return Items.string;
            case HILL_GIANT:
            case HELLHOUND:
                return Items.bone;
            case GARGOYLE:
                return Items.flint;
            case BANDIT:
                return Items.gold_nugget;
            case GHOST:
                return null;
            default:
                return Items.leather;
        }
    }

    @Override
    protected void dropFewItems(boolean hitByPlayer, int looting) {
        super.dropFewItems(hitByPlayer, looting);
        if (kind.isDragon()) {
            // Dragon bones, and now and then a little gold from its hoard.
            dropItem(Items.bone, 2 + rand.nextInt(3));
            if (rand.nextInt(3) == 0) {
                dropItem(Items.gold_ingot, 1 + rand.nextInt(2));
            }
        } else if (kind == WildKind.RED_SPIDER && rand.nextInt(3) == 0) {
            dropItem(Items.spider_eye, 1);
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setString("WildKind", kind.id);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        WildKind saved = WildKind.byId(tag.getString("WildKind"));
        if (saved != null) {
            dataWatcher.updateObject(KIND, (byte) saved.ordinal());
            applyKind(saved);
        }
        super.readEntityFromNBT(tag);
    }
}
