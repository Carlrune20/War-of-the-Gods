package com.warofthegods.game;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.warofthegods.Config;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public class PlayerEvents {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Game game = GameManager.getGame();
        if (game == null) {
            return;
        }
        EntityPlayer player = event.player;
        game.updatePlayerName(player.getUniqueID(), player.getCommandSenderName());

        Team team = game.getTeamOf(player.getUniqueID());
        if (team == null && Config.autoAssignTeams) {
            team = GameManager.autoAssign(game, player);
        }
        String message = team == null
            ? "War of the Gods game #" + game.getNumber() + " is running. Use /rts team list to see the teams."
            : "War of the Gods game #" + game
                .getNumber() + " is running. You are on team " + team.getColoredName() + EnumChatFormatting.GOLD + ".";
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + message));

        // Players who were elsewhere, or in an older game's world, start at their town center on the current map.
        if (player instanceof EntityPlayerMP && GameWorld.isOutsideGame((EntityPlayerMP) player, game)) {
            GameWorld.sendToGame((EntityPlayerMP) player, game);
        }
    }

    /** Players who die in the game world come back in front of their town center. */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Game game = GameManager.getGame();
        if (game != null && event.player instanceof EntityPlayerMP
            && event.player.dimension == GameWorld.dimensionId()) {
            GameWorld.sendToGame((EntityPlayerMP) event.player, game);
        }
    }
}
