package com.warofthegods.client.rts;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * The RTS view's camera: an entity of the client only, never added to the world, that Minecraft renders from in
 * place of the player. It looks down at a focus point from a distance, turned to one of four diagonals like the web
 * map, and is moved every frame.
 *
 * <p>
 * Screen points are in pixels from the top left of the window.
 */
public final class RtsCamera extends EntityLivingBase {

    public double focusX;
    public double focusY;
    public double focusZ;
    /** Minecraft yaw, in degrees; at 135 it looks north-west, like the web map at first. */
    public float yaw = 135;
    public float yawGoal = 135;
    /** Degrees down from level. */
    public float pitch = 50;
    /** Blocks from the camera to the focus point. */
    public double distance = 40;
    public float fov = 40;

    public RtsCamera(World world) {
        super(world);
        setSize(0.1F, 0.1F);
        // Minecraft puts the eye at posY - (yOffset - 1.62); at 1.62 the eye is at posY itself.
        yOffset = 1.62F;
    }

    /** Puts the camera where it should be for its focus, turn and zoom, with nothing to interpolate. */
    public void place() {
        double[] forward = forward();
        double x = focusX - forward[0] * distance;
        double y = focusY - forward[1] * distance;
        double z = focusZ - forward[2] * distance;
        posX = prevPosX = lastTickPosX = x;
        posY = prevPosY = lastTickPosY = y;
        posZ = prevPosZ = lastTickPosZ = z;
        boundingBox.setBounds(x - 0.05, y - 0.05, z - 0.05, x + 0.05, y + 0.05, z + 0.05);
        rotationYaw = prevRotationYaw = rotationYawHead = prevRotationYawHead = yaw;
        rotationPitch = prevRotationPitch = pitch;
        renderYawOffset = prevRenderYawOffset = yaw;
    }

    /** Turns smoothly toward the yaw it was turned to; dt in seconds. */
    public void animate(double dt) {
        float d = yawGoal - yaw;
        if (Math.abs(d) < 0.05F) {
            yaw = yawGoal;
        } else {
            yaw += d * (float) Math.min(1, dt * 12);
        }
    }

    /** Turns a quarter round to the next diagonal: -1 to the left, 1 to the right. */
    public void turn(int direction) {
        float snapped = Math.round((yawGoal - 45) / 90) * 90 + 45;
        yawGoal = snapped + direction * 90;
    }

    /** The unit vector the camera looks along. */
    public double[] forward() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new double[] { -Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad) };
    }

    /** The unit vector to the right of the screen, level. */
    public double[] right() {
        double yawRad = Math.toRadians(yaw);
        return new double[] { -Math.cos(yawRad), 0, -Math.sin(yawRad) };
    }

    /** Level direction toward the top of the screen. */
    public double[] ahead() {
        double yawRad = Math.toRadians(yaw);
        return new double[] { -Math.sin(yawRad), 0, Math.cos(yawRad) };
    }

    /** The unit vector toward the top of the screen. */
    public double[] up() {
        double[] f = forward();
        double[] r = right();
        // up = right x forward
        return new double[] { r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0] };
    }

    /** Moves the focus along the ground: right and ahead in blocks, as the screen shows them. */
    public void pan(double rightBlocks, double aheadBlocks) {
        double[] r = right();
        double[] a = ahead();
        focusX += r[0] * rightBlocks + a[0] * aheadBlocks;
        focusZ += r[2] * rightBlocks + a[2] * aheadBlocks;
    }

    /** Where the camera eye is. */
    public double[] eye() {
        return new double[] { posX, posY, posZ };
    }

    /** The ray from the eye through a screen point, as {x, y, z, dx, dy, dz} with a unit direction. */
    public double[] ray(double px, double py, int width, int height) {
        double tan = Math.tan(Math.toRadians(fov) / 2);
        double aspect = width / (double) Math.max(1, height);
        double nx = (2 * px / width - 1) * tan * aspect;
        double ny = (1 - 2 * py / height) * tan;
        double[] f = forward();
        double[] r = right();
        double[] u = up();
        double dx = f[0] + r[0] * nx + u[0] * ny;
        double dy = f[1] + r[1] * nx + u[1] * ny;
        double dz = f[2] + r[2] * nx + u[2] * ny;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new double[] { posX, posY, posZ, dx / length, dy / length, dz / length };
    }

    /** The screen point a world point is drawn at, as {px, py}, or null when it is behind the camera. */
    public double[] project(double x, double y, double z, int width, int height) {
        double rx = x - posX;
        double ry = y - posY;
        double rz = z - posZ;
        double[] f = forward();
        double[] r = right();
        double[] u = up();
        double depth = rx * f[0] + ry * f[1] + rz * f[2];
        if (depth <= 0.1) {
            return null;
        }
        double tan = Math.tan(Math.toRadians(fov) / 2);
        double aspect = width / (double) Math.max(1, height);
        double sx = (rx * r[0] + ry * r[1] + rz * r[2]) / (depth * tan * aspect);
        double sy = (rx * u[0] + ry * u[1] + rz * u[2]) / (depth * tan);
        return new double[] { (sx + 1) / 2 * width, (1 - sy) / 2 * height };
    }

    // ---------------------------------------------------------------- EntityLivingBase

    @Override
    public ItemStack getHeldItem() {
        return null;
    }

    @Override
    public ItemStack getEquipmentInSlot(int slot) {
        return null;
    }

    @Override
    public void setCurrentItemOrArmor(int slot, ItemStack stack) {}

    @Override
    public ItemStack[] getLastActiveItems() {
        return new ItemStack[5];
    }

    @Override
    public boolean isEntityInvulnerable() {
        return true;
    }

    @Override
    public void onUpdate() {}
}
