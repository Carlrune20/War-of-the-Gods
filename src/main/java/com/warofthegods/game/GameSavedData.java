package com.warofthegods.game;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

/** Stores the current game in the overworld save, as data/warofthegods.dat. */
public class GameSavedData extends WorldSavedData {

    public static final String KEY = "warofthegods";

    private Game game;
    /** Number of the last game started on this world, so game numbers keep counting up. */
    private int lastGameNumber;

    public GameSavedData(String key) {
        super(key);
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
        if (game != null) {
            lastGameNumber = Math.max(lastGameNumber, game.getNumber());
            game.setChangeListener(this::markDirty);
        }
        markDirty();
    }

    public int getLastGameNumber() {
        return lastGameNumber;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        lastGameNumber = tag.getInteger("lastGameNumber");
        game = tag.hasKey("game") ? Game.readFromNBT(tag.getCompoundTag("game")) : null;
        if (game != null) {
            game.setChangeListener(this::markDirty);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("lastGameNumber", lastGameNumber);
        if (game != null) {
            tag.setTag("game", game.writeToNBT());
        }
    }
}
