package com.warofthegods.game;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.DimensionManager;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.building.Buildings;
import com.warofthegods.command.CommandRts;
import com.warofthegods.unit.Units;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Decides who loses and who wins. A team that loses its last town center has a while to raise a new one, counted down
 * for everyone to see; if it has no workers left either, it is defeated at once. A defeated team's units die and its
 * buildings fall to ruins. Once every team still standing is allied with the rest, they have won and the game is
 * over.
 *
 * <p>
 * A team that has never had a finished town center, such as one whose base is still being chosen, cannot lose.
 */
public final class WinConditions {

    /** Ticks between looking over the teams. */
    private static final int INTERVAL = 20;
    /** Seconds left at which everyone is told again how long a team has. */
    private static final int[] WARNINGS = { 60, 30, 10 };

    private int ticks;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % INTERVAL != 0) {
            return;
        }
        Game game = DimensionManager.getWorld(0) == null ? null : GameManager.getGame();
        if (game == null || game.isOver()) {
            return;
        }
        for (Team team : new ArrayList<>(game.getTeams())) {
            if (!team.isDefeated()) {
                check(game, team);
            }
        }
    }

    private static void check(Game game, Team team) {
        if (game.countTownCenters(team) > 0) {
            team.setHadTownCenter();
            if (team.getDefeatTicks() >= 0) {
                team.setDefeatTicks(-1);
                CommandRts.broadcast("Team " + team.getColoredName() + gold() + " has a town center again.");
            }
            return;
        }
        if (!team.hasHadTownCenter() || team.isTownHallDue()) {
            return;
        }
        if (game.countWorkers(team) == 0) {
            defeat(game, team, "has no town center and no workers left");
            return;
        }
        int left = team.getDefeatTicks();
        if (left < 0) {
            int seconds = Config.defeatCountdownSeconds;
            team.setDefeatTicks(seconds * 20);
            CommandRts.broadcast(
                "Team " + team.getColoredName()
                    + gold()
                    + " has lost its last town center! It has "
                    + clock(seconds)
                    + " to raise a new one or it is defeated.");
            return;
        }
        int before = (left + 19) / 20;
        left = Math.max(0, left - INTERVAL);
        team.setDefeatTicks(left);
        int now = (left + 19) / 20;
        if (left == 0) {
            defeat(game, team, "did not raise a new town center in time");
            return;
        }
        for (int warning : WARNINGS) {
            if (before > warning && now <= warning) {
                CommandRts.broadcast(
                    "Team " + team.getColoredName()
                        + gold()
                        + " has "
                        + clock(now)
                        + " left to raise a new town center.");
            }
        }
    }

    /** Seconds as m:ss, such as 2:00. */
    public static String clock(int seconds) {
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    private static String gold() {
        return EnumChatFormatting.GOLD.toString();
    }

    /**
     * Defeats the team: its units die and its buildings fall to ruins. If that leaves only allies standing, they win.
     */
    public static void defeat(Game game, Team team, String why) {
        if (team.isDefeated()) {
            return;
        }
        team.setDefeated();
        Units.get()
            .kill(
                Units.get()
                    .getUnits(team));
        for (UnitRecord record : new ArrayList<>(game.getUnitRecords())) {
            if (record.team.equals(team.getId())) {
                // Units in chunks that are not loaded.
                game.removeUnitRecord(record.id);
            }
        }
        for (Building building : game.getBuildings(team)) {
            Buildings.get()
                .damage(building, Integer.MAX_VALUE, "its team was defeated");
        }
        CommandRts.broadcast("Team " + team.getColoredName() + gold() + " " + why + " and has been defeated!");
        WarOfTheGods.LOG.info("Team {} was defeated: {}", team.getName(), why);
        checkVictory(game);
    }

    /** Ends the game once every team still standing is allied with all the others. */
    static void checkVictory(Game game) {
        if (game.isOver()) {
            return;
        }
        List<Team> standing = new ArrayList<>();
        for (Team team : game.getTeams()) {
            if (!team.isDefeated()) {
                standing.add(team);
            }
        }
        for (Team a : standing) {
            for (Team b : standing) {
                if (game.areEnemies(a, b)) {
                    return;
                }
            }
        }
        game.setOver(standing);
        if (standing.isEmpty()) {
            CommandRts.broadcast("Game #" + game.getNumber() + " is over. Nobody won.");
            WarOfTheGods.LOG.info("Game #{} is over with no winner", game.getNumber());
            return;
        }
        List<String> names = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        for (Team team : standing) {
            names.add(team.getColoredName() + gold());
            plain.add(team.getName());
        }
        CommandRts.broadcast(
            "Game #" + game.getNumber()
                + " is over! "
                + join(names)
                + (standing.size() == 1 ? " wins!" : " win!"));
        WarOfTheGods.LOG.info("Game #{} is over: {} won", game.getNumber(), join(plain));
    }

    /** Red, Blue and Green. */
    static String join(List<String> names) {
        if (names.size() <= 1) {
            return names.isEmpty() ? "" : names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}
