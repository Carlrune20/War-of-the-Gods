package com.warofthegods.client;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.item.ItemPrayerBook;
import com.warofthegods.rts.PrayerMessage;
import com.warofthegods.unit.PlayerPrayers;
import com.warofthegods.unit.Prayer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * The prayer bar, above the hunger bar while the player carries a prayer book: ten stars of two points each, as
 * hearts are of health, with the chosen protection prayer's icon beside them, bright while it is up. While the book
 * is held down to switch prayers, the prayer's name shows above the bar.
 */
public final class PrayerHud {

    private static final ResourceLocation POINTS = new ResourceLocation(
        WarOfTheGods.MODID,
        "textures/gui/prayer/points.png");
    private static final Map<Prayer, ResourceLocation> ICONS = new EnumMap<>(Prayer.class);

    static {
        for (Prayer prayer : Prayer.values()) {
            if (prayer != Prayer.NONE) {
                ICONS
                    .put(prayer, new ResourceLocation(WarOfTheGods.MODID, "textures/gui/prayer/" + prayer.id + ".png"));
            }
        }
    }

    /** As the server last told: written on a Netty thread, read on the client's. */
    private static volatile int points = PlayerPrayers.MAX_POINTS;
    private static volatile Prayer prayer = Prayer.MELEE;
    private static volatile boolean up;

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new PrayerHud());
    }

    public static void receive(PrayerMessage message) {
        points = message.points;
        prayer = Prayer.byOrdinal(message.prayer);
        up = message.up;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.FOOD || mc.thePlayer == null
            || !ItemPrayerBook.carriedBy(mc.thePlayer)) {
            return;
        }
        int right = event.resolution.getScaledWidth() / 2 + 91;
        int top = event.resolution.getScaledHeight() - GuiIngameForge.right_height;
        GuiIngameForge.right_height += 10;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(POINTS);
        int shown = points;
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            // Full, half or empty, as the sheet has them from left to right.
            int u = i * 2 + 1 < shown ? 0 : i * 2 + 1 == shown ? 9 : 18;
            Gui.func_146110_a(x, top, u, 0, 9, 9, 32, 16);
        }
        Prayer chosen = prayer;
        ResourceLocation icon = ICONS.get(chosen);
        if (icon != null) {
            mc.getTextureManager()
                .bindTexture(icon);
            float shade = up ? 1 : 0.35F;
            GL11.glColor4f(shade, shade, shade, up ? 1 : 0.8F);
            quad(right - 80 - 11, top - 1, 11);
            GL11.glColor4f(1, 1, 1, 1);
        }
        ItemStack using = mc.thePlayer.getItemInUse();
        if (using != null && using.getItem() instanceof ItemPrayerBook
            && mc.thePlayer.getItemInUseDuration() >= ItemPrayerBook.HOLD_TICKS) {
            String name = chosen.displayName;
            mc.fontRenderer
                .drawStringWithShadow(name, right - mc.fontRenderer.getStringWidth(name), top - 10, 0xA8F0E0);
        }
        GL11.glDisable(GL11.GL_BLEND);
        mc.getTextureManager()
            .bindTexture(Gui.icons);
    }

    /** The whole of the bound texture, size pixels square at x, y. */
    private static void quad(int x, int y, int size) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + size, 0, 0, 1);
        tessellator.addVertexWithUV(x + size, y + size, 0, 1, 1);
        tessellator.addVertexWithUV(x + size, y, 0, 1, 0);
        tessellator.addVertexWithUV(x, y, 0, 0, 0);
        tessellator.draw();
    }
}
