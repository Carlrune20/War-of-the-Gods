package com.warofthegods.game;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

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
        String message = team == null
            ? "War of the Gods game #" + game.getNumber() + " is running. Use /rts team list to see the teams."
            : "War of the Gods game #" + game.getNumber() + " is running. You are on team " + team.getColoredName()
                + EnumChatFormatting.GOLD + ".";
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + message));
    }
}
