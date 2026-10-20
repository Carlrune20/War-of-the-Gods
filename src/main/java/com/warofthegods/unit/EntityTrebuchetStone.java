package com.warofthegods.unit;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.warofthegods.Config;
import com.warofthegods.building.Buildings;
import com.warofthegods.building.Schematic;
import com.warofthegods.game.Building;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Team;

/**
 * A stone a trebuchet throws: a falling cobblestone block, lobbed high, that blows up where it comes down as TNT
 * does. The blast digs a crater, though it breaks no building, nothing close round one and no map feature; it smashes
 * the enemy buildings it lands on or beside and hurts enemies round it, less toward its edge, but never harms its own
 * team or allies.
 *
 * <p>
 * It is left out of the mod's entity list on purpose: Minecraft then sends it to clients as the falling block it is
 * made from, and they draw it and work out its flight as they do for falling sand, with the same pull and drag used
 * here. Nor is it saved; a stone in the air when the world stops is gone.
 */
public class EntityTrebuchetStone extends EntityFallingBlock {

    /** How far above a trebuchet's feet the stone leaves its arm, at the top of the swing. */
    public static final double RELEASE_HEIGHT = 12.0;
    /** How big the blast is, for the blocks it breaks: as big as TNT's. */
    private static final float BLAST_POWER = 4.0F;
    /** How hard the world pulls the stone down, and how much speed it keeps each tick, as for falling sand. */
    private static final double GRAVITY = 0.04;
    private static final double DRAG = 0.98;
    /** Ticks the stone is in the air at least, and how many more for each block it has to cover. */
    private static final int MIN_FLIGHT = 24;
    private static final double FLIGHT_PER_BLOCK = 0.7;
    /** Ticks after which a stone that has hit nothing bursts anyway. */
    private static final int MAX_TICKS = 200;

    private EntityUnit thrower;
    private String teamId = "";
    private int flightTicks;

    public EntityTrebuchetStone(World world) {
        super(world);
    }

    /** A stone leaving a trebuchet's arm at one spot, thrown to come down on another. */
    public EntityTrebuchetStone(World world, EntityUnit thrower, String teamId, double x, double y, double z,
        double targetX, double targetY, double targetZ) {
        super(world, x, y, z, Blocks.cobblestone, 0);
        this.thrower = thrower;
        this.teamId = teamId;
        double dx = targetX - x;
        double dz = targetZ - z;
        int ticks = (int) Math.round(MIN_FLIGHT + Math.hypot(dx, dz) * FLIGHT_PER_BLOCK);
        double[] velocity = launch(dx, targetY - y, dz, ticks);
        motionX = velocity[0];
        motionY = velocity[1];
        motionZ = velocity[2];
    }

    /**
     * The speed a stone has to leave at to cover dx, dy, dz in the given ticks, as it flies: each tick it is pulled
     * down, moves, and then keeps DRAG of its speed.
     */
    static double[] launch(double dx, double dy, double dz, int ticks) {
        // Over the ticks a speed v carries it v * (1 + DRAG + DRAG^2 + ...), and the pull takes it down a set amount.
        double carried = 0;
        double fallen = 0;
        double fall = 0;
        double keep = 1;
        for (int i = 0; i < ticks; i++) {
            carried += keep;
            fall = fall * DRAG - GRAVITY;
            fallen += fall;
            keep *= DRAG;
        }
        return new double[] { dx / carried, (dy - fallen) / carried, dz / carried };
    }

    @Override
    public void onUpdate() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        flightTicks++;
        motionY -= GRAVITY;
        moveEntity(motionX, motionY, motionZ);
        motionX *= DRAG;
        motionY *= DRAG;
        motionZ *= DRAG;
        if (worldObj.isRemote) {
            return;
        }
        if (onGround || isCollidedHorizontally || isCollidedVertically || flightTicks > MAX_TICKS || hitsEnemy()) {
            burst();
            setDead();
        }
    }

    /** Whether it has come down on an enemy, as on a rider, before reaching the ground. */
    private boolean hitsEnemy() {
        Game game = GameManager.getGame();
        Team team = game == null ? null : game.getTeam(teamId);
        if (team == null || motionY > 0) {
            return false;
        }
        for (Object object : worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox.expand(0.3, 0.3, 0.3))) {
            if (object instanceof EntityLivingBase && Units.get()
                .isEnemyOf(team, (EntityLivingBase) object)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bursts where it is: a blast that breaks blocks as TNT does and a cloud of dust, enemies round it hurt and enemy
     * buildings smashed. Buildings see to it that the blast breaks none of theirs and leaves the hurting to this.
     */
    private void burst() {
        worldObj.newExplosion(this, posX, posY, posZ, BLAST_POWER, false, true);
        ((WorldServer) worldObj).func_147487_a(
            "blockcrack_" + Block.getIdFromBlock(Blocks.cobblestone) + "_0",
            posX,
            posY + 0.5,
            posZ,
            40,
            1.0,
            0.5,
            1.0,
            0.15);
        Game game = GameManager.getGame();
        Team team = game == null ? null : game.getTeam(teamId);
        if (team == null) {
            return;
        }
        double blast = Config.trebuchetBlast;
        DamageSource source = DamageSource.causeThrownDamage(this, thrower)
            .setExplosion();
        for (EntityLivingBase enemy : Units.get()
            .findEnemiesNear(worldObj, team, posX, posY, posZ, blast + 1, Integer.MAX_VALUE)) {
            if (Math.abs(enemy.posY - posY) > blast + 1) {
                continue;
            }
            double distance = enemy.getDistance(posX, posY, posZ);
            float damage = (float) (Config.trebuchetUnitDamage * Math.max(0, 1 - distance / (blast + 1)));
            if (damage > 0) {
                enemy.attackEntityFrom(source, damage);
            }
        }
        String cause = thrower != null ? thrower.getLabel() : "a trebuchet";
        for (Building building : new ArrayList<>(game.getBuildings())) {
            Team owner = game.getTeam(building.team);
            if (owner == null || !game.areEnemies(team, owner) || building.getShown() == null) {
                continue;
            }
            Schematic schematic = Buildings.get()
                .schematic(building.type);
            int top = building.groundY + (schematic == null ? 0 : schematic.height - schematic.ground);
            double dx = Math.max(0, Math.max(building.minX - posX, posX - (building.getMaxX() + 1)));
            double dz = Math.max(0, Math.max(building.minZ - posZ, posZ - (building.getMaxZ() + 1)));
            double dy = Math.max(0, Math.max(building.groundY - posY, posY - top));
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance >= blast) {
                continue;
            }
            int amount = (int) Math.round(Config.trebuchetBuildingDamage * (1 - distance / (blast + 1)));
            Buildings.get()
                .damage(building, amount, cause);
            if (thrower != null && game.getBuilding(building.id) == null) {
                WarFavor.razed(thrower);
            }
        }
    }

    /** Never saved, as it is in no entity list; nothing to write or read. */
    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {}

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        setDead();
    }
}
