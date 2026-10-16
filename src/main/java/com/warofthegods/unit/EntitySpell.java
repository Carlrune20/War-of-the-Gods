package com.warofthegods.unit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Spell;
import com.warofthegods.game.Team;

/**
 * A spell cast by a wizard, a mage or a player with a Zamorak staff: a bolt of air, water, earth or fire, or a curse,
 * flying straight and trailing the particles of its element. Like a team's arrows it flies on past its own team and
 * its allies; it is gone a few seconds after it is cast, or when it hits something.
 */
public class EntitySpell extends EntityThrowable {

    private static final float SPEED = 1.6F;
    /** Ticks it flies before it fades. */
    private static final int LIFE = 60;
    /** Seconds a fire spell sets what it hits alight for. */
    private static final int FIRE_SECONDS = 3;
    /** Which spell it is, kept where the client sees it to draw its particles. */
    private static final int WATCH_SPELL = 20;

    private String teamId = "";
    private float damage;

    public EntitySpell(World world) {
        super(world);
        setSize(0.3F, 0.3F);
    }

    /** Cast by a unit or a player, from its eyes; then aimed at what it is cast at. */
    public EntitySpell(World world, EntityLivingBase caster, String teamId, Spell spell, float damage) {
        super(world, caster);
        setSize(0.3F, 0.3F);
        this.teamId = teamId == null ? "" : teamId;
        this.damage = damage;
        dataWatcher.updateObject(WATCH_SPELL, (byte) spell.ordinal());
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(WATCH_SPELL, (byte) 0);
    }

    public Spell getSpell() {
        return Spell.byOrdinal(dataWatcher.getWatchableObjectByte(WATCH_SPELL));
    }

    /** Spells fly straight, not falling as a thrown thing does. */
    @Override
    protected float getGravityVelocity() {
        return 0;
    }

    @Override
    protected float func_70182_d() {
        return SPEED;
    }

    /** Sends it at the middle of the target. */
    public void aim(Entity target) {
        double height = target instanceof EntityLivingBase ? target.height * 0.6 : 0.5;
        aimAt(target.posX, target.boundingBox.minY + height, target.posZ);
    }

    public void aimAt(double x, double y, double z) {
        setThrowableHeading(x - posX, y - posY, z - posZ, SPEED, 1);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) {
            Spell spell = getSpell();
            float red = (spell.color >> 16 & 255) / 255F;
            float green = (spell.color >> 8 & 255) / 255F;
            float blue = (spell.color & 255) / 255F;
            for (int i = 0; i < 3; i++) {
                double x = posX - motionX * i / 3;
                double y = posY - motionY * i / 3;
                double z = posZ - motionZ * i / 3;
                // Redstone dust takes its color from what would be its motion; 0 red would make it plain red.
                worldObj.spawnParticle("reddust", x, y, z, Math.max(0.001, red), green, blue);
                worldObj.spawnParticle(spell.particle, x, y, z, 0, 0, 0);
            }
        } else if (ticksExisted > LIFE) {
            setDead();
        }
    }

    @Override
    protected void onImpact(MovingObjectPosition hit) {
        if (worldObj.isRemote) {
            return;
        }
        Spell spell = getSpell();
        if (hit.entityHit != null) {
            // Past its caster, and its caster's team and allies, it flies on.
            if (hit.entityHit == getThrower() || isFriend(hit.entityHit)) {
                return;
            }
            if (spell.isCurse()) {
                if (hit.entityHit instanceof EntityLivingBase) {
                    ((EntityLivingBase) hit.entityHit).addPotionEffect(new PotionEffect(spell.potion, spell.ticks, 0));
                }
            } else if (hit.entityHit.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, getThrower()), damage)
                && spell == Spell.FIRE) {
                    hit.entityHit.setFire(FIRE_SECONDS);
                }
        }
        burst(spell);
        setDead();
    }

    /** Whether the entity is of the caster's team or an ally's. */
    private boolean isFriend(Entity entity) {
        Game game = GameManager.getGame();
        Team caster = game == null ? null : game.getTeam(teamId);
        Team other = caster == null ? null : Units.teamOf(game, entity);
        return other != null && game.areAllied(caster, other);
    }

    /** A puff of its element where it lands. */
    private void burst(Spell spell) {
        ((WorldServer) worldObj).func_147487_a(spell.particle, posX, posY, posZ, 12, 0.3, 0.3, 0.3, 0.05);
        worldObj.playSoundAtEntity(this, spell == Spell.FIRE ? "mob.ghast.fireball" : "random.fizz", 0.4F, 1.4F);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setString("Team", teamId);
        tag.setFloat("Damage", damage);
        tag.setByte("Spell", (byte) getSpell().ordinal());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        teamId = tag.getString("Team");
        damage = tag.getFloat("Damage");
        dataWatcher.updateObject(WATCH_SPELL, tag.getByte("Spell"));
    }
}
