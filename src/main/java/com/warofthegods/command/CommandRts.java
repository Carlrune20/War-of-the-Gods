package com.warofthegods.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

import com.warofthegods.Config;
import com.warofthegods.building.Buildings;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Faction;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Settlement;
import com.warofthegods.game.Team;
import com.warofthegods.game.Tree;
import com.warofthegods.game.UnitKind;
import com.warofthegods.game.Upgrade;
import com.warofthegods.game.WallLine;
import com.warofthegods.game.WinConditions;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Routes;
import com.warofthegods.unit.Torches;
import com.warofthegods.unit.Trees;
import com.warofthegods.unit.Units;
import com.warofthegods.web.Viewer;
import com.warofthegods.web.WebServer;
import com.warofthegods.world.GameWorld;

/** The /rts command: game setup, teams, alliances, resources and units. */
public class CommandRts extends CommandBase {

    /** Section sign color codes; the vanilla stripping helper only exists on the client. */
    private static final Pattern FORMATTING_CODE = Pattern.compile("\u00a7.");
    private static final Pattern TEAM_NAME = Pattern.compile("[A-Za-z0-9_-]{1,16}");
    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "help",
        "info",
        "newgame",
        "quickgame",
        "endgame",
        "spawn",
        "surrender",
        "team",
        "ally",
        "unally",
        "resources",
        "units",
        "buildings",
        "build",
        "wall",
        "gate",
        "train",
        "damage",
        "upgrade",
        "research",
        "chop",
        "mine",
        "light",
        "route",
        "explode",
        "finish",
        "ai",
        "web");
    private static final List<String> TEAM_SUBCOMMANDS = Arrays
        .asList("list", "create", "remove", "color", "faction", "join", "leave", "auto", "base");
    private static final List<String> UNIT_ACTIONS = Arrays
        .asList("spawn", "move", "attackmove", "attack", "repair", "stop", "stance", "kill");
    /** Units /rts units spawn makes at most at once. */
    private static final int MAX_SPAWN = 50;
    private static final List<String> RESOURCE_ACTIONS = Arrays.asList("give", "take", "set");

    @Override
    public String getCommandName() {
        return "rts";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/rts <help|info|newgame|quickgame|endgame|spawn|surrender|team|ally|unally|resources|units|buildings|build|wall|gate|train|damage"
            + "|upgrade|research|chop|mine|finish|ai|web>";
    }

    /** Everyone may run /rts; subcommands that change the game check for op themselves. */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            help(sender);
            return;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase()) {
            case "help":
                help(sender);
                break;
            case "info":
                info(sender);
                break;
            case "newgame":
            case "lobby":
                lobby(sender, rest);
                break;
            case "quickgame":
                quickGame(sender, rest);
                break;
            case "endgame":
                endGame(sender);
                break;
            case "spawn":
                spawn(sender, rest);
                break;
            case "surrender":
                surrender(sender, rest);
                break;
            case "team":
                team(sender, rest);
                break;
            case "ally":
                ally(sender, rest, true);
                break;
            case "unally":
                ally(sender, rest, false);
                break;
            case "resources":
            case "res":
                resources(sender, rest);
                break;
            case "units":
                units(sender, rest);
                break;
            case "buildings":
                buildings(sender, rest);
                break;
            case "build":
                build(sender, rest);
                break;
            case "train":
                train(sender, rest);
                break;
            case "damage":
                damage(sender, rest);
                break;
            case "upgrade":
                upgrade(sender, rest);
                break;
            case "research":
                research(sender, rest);
                break;
            case "chop":
                chop(sender, rest);
                break;
            case "mine":
                mine(sender, rest);
                break;
            case "light":
                light(sender, rest);
                break;
            case "route":
                route(sender, rest);
                break;
            case "explode":
                explode(sender, rest);
                break;
            case "finish":
                finish(sender, rest);
                break;
            case "wall":
                wall(sender, rest);
                break;
            case "gate":
                gate(sender, rest);
                break;
            case "ai":
                ai(sender, rest);
                break;
            case "web":
                web(sender, rest);
                break;
            default:
                throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void help(ICommandSender sender) {
        send(sender, EnumChatFormatting.GOLD + "War of the Gods commands:");
        send(sender, "/rts info - show the current game");
        send(sender, "/rts newgame [close|start] - open the new game lobby, close it, or start without waiting (op)");
        send(sender, "/rts quickgame [teams] [mapSize] [seed] - start a new game at once, players shuffled (op)");
        send(sender, "/rts endgame - end the current game (op)");
        send(sender, "/rts spawn [player] - go to the game map");
        send(sender, "/rts surrender - give up: your team is defeated");
        send(sender, "/rts surrender <team> - make a team give up (op)");
        send(sender, "/rts team list");
        send(sender, "/rts team create <name> [color] (op)");
        send(sender, "/rts team remove <name> (op)");
        send(sender, "/rts team color <name> <color> (op)");
        send(sender, "/rts team faction <name> <saradomin|zamorak> - how the team's buildings and units look (op)");
        send(sender, "/rts team join <team> [player]");
        send(sender, "/rts team leave [player]");
        send(sender, "/rts team base <team> [x z] - move a team's base and raise a new town center there (op)");
        send(sender, "/rts ally <team> <team> (op)");
        send(sender, "/rts unally <team> <team> (op)");
        send(sender, "/rts resources [team]");
        send(sender, "/rts resources <give|take|set> <team> <resource> <amount> (op)");
        send(sender, "/rts units [team] - list units");
        send(
            sender,
            "/rts units spawn <team> [count] [warrior|archer|worker] - spawn units for free at the town center (op)");
        send(
            sender,
            "/rts units move <team> <x> <z> [box|line|wedge] - send all of a team's units somewhere in formation (op)");
        send(
            sender,
            "/rts units stance <team> <aggressive|defensive|hold|passive> - set how a team's units fight (op)");
        send(sender, "/rts route <x1> <z1> <x2> <z2> - plan the way a unit would walk between two points (op)");
        send(sender, "/rts units stop <team> - make a team's units stop and guard (op)");
        send(sender, "/rts units kill <team|all> - remove units (op)");
        send(sender, "/rts buildings [team] - list buildings and building sites");
        send(
            sender,
            "/rts build <team> <farm|house|barracks|storehouse|armory|tower|town_center|white_knights_fortress"
                + "|black_knights_fortress> [x z] [rotation] - lay out a site for the team's workers (op)");
        send(
            sender,
            "/rts train <team> <worker|warrior|archer|white_knight|black_knight|tragidee|sagittare> [count] - queue"
                + " units for free at a building (op)");
        send(sender, "/rts upgrade <building id> - take a town center, and its town, into the next age at once (op)");
        send(sender, "/rts research <team> <upgrade> - give a team an upgrade at once (op)");
        send(sender, "/rts chop <team> - send a team's idle workers to chop the tree nearest its town center (op)");
        send(
            sender,
            "/rts light <team> [x1 z1 x2 z2] - send a team's idle workers to light up an area, or around their town"
                + " center (op)");
        send(
            sender,
            "/rts explode <building id> - set off a creeper-sized explosion beside a building that hurts no one (op)");
        send(sender, "/rts finish [team] - finish every building site at once (op)");
        send(
            sender,
            "/rts wall <team> <x1> <z1> <x2> <z2> - lay out a wall for free and send the team's idle workers to build"
                + " it (op)");
        send(sender, "/rts gate <building id> - make a finished wall segment a gate, for free (op)");
        send(sender, "/rts mine <team> - send a team's idle workers to the nearest gold mine (op)");
        send(sender, "/rts damage <building id> <amount> - take health off a building; at 0 it is gone (op)");
        send(sender, "/rts ai - list the teams the computer plays");
        send(sender, "/rts ai <team> [on|off] - have the computer play a team, or stop it (op)");
        send(sender, "/rts web - get a link to the live web map");
        send(sender, "/rts web <player> - send a player a web map link (op)");
        send(sender, "/rts web observer - get a link that views the map without a team (op)");
    }

    private void info(ICommandSender sender) {
        Game game = requireGame();
        send(sender, EnumChatFormatting.GOLD + "Game #" + game.getNumber());
        send(
            sender,
            "Map: " + game.getSize()
                + "x"
                + game.getSize()
                + " from "
                + game.getMinX()
                + ","
                + game.getMinZ()
                + " to "
                + game.getMaxX()
                + ","
                + game.getMaxZ());
        send(sender, "World: dimension " + GameWorld.dimensionId() + ", seed " + game.getSeed());
        int[] spawn = GameWorld.findSpawn(GameWorld.getWorld(), game);
        send(sender, "Spawn: " + spawn[0] + "," + spawn[1] + "," + spawn[2]);
        teamList(sender);
        for (Team team : game.getTeams()) {
            if (team.isDefeated()) {
                send(sender, "Team " + team.getColoredName() + " has been defeated");
            } else if (team.getDefeatTicks() >= 0) {
                send(
                    sender,
                    "Team " + team.getColoredName()
                        + " has "
                        + WinConditions.clock((team.getDefeatTicks() + 19) / 20)
                        + " left to raise a new town center");
            }
        }
        int free = 0;
        for (Settlement settlement : game.getSettlements()) {
            free += game.townCenterOn(settlement) == null ? 1 : 0;
        }
        long gold = 0;
        for (GoldMine mine : game.getMines()) {
            gold += mine.getGold();
        }
        send(
            sender,
            "Settlements: " + game.getSettlements()
                .size()
                + ", "
                + free
                + " unclaimed; gold mines: "
                + game.getMines()
                    .size()
                + " with "
                + gold
                + " gold; ruins found so far: "
                + game.getRuins()
                    .size());
        if (game.isOver()) {
            List<String> winners = new ArrayList<>();
            for (Team team : game.getWinners()) {
                winners.add(team.getColoredName());
            }
            send(
                sender,
                "The game is over. " + (winners.isEmpty() ? "Nobody won" : "Won: " + String.join(", ", winners)));
        }
        unitSummary(sender, game);
        List<Team[]> alliances = game.getAlliances();
        if (alliances.isEmpty()) {
            send(sender, "Alliances: none");
        } else {
            for (Team[] pair : alliances) {
                send(sender, "Alliance: " + pair[0].getColoredName() + " + " + pair[1].getColoredName());
            }
        }
    }

    /**
     * Starts a new game at once, skipping the lobby: the gods are shared out evenly over the teams and everyone online
     * is shuffled onto them.
     */
    private void quickGame(ICommandSender sender, String[] args) {
        requireOp(sender);
        int teamCount = args.length > 0 ? parseIntBounded(sender, args[0], 0, Team.COLORS.length)
            : Config.defaultTeamCount;
        int size = args.length > 1 ? parseIntBounded(sender, args[1], 64, 100000) : Config.defaultMapSize;
        long seed = args.length > 2 ? parseSeed(args[2]) : new Random().nextLong();

        Lobby.close();
        Game previous = GameManager.getGame();
        if (previous != null) {
            broadcast("Game #" + previous.getNumber() + " has ended. Building a new map...");
        }
        Game game = GameManager.newGame(size, teamCount, seed);
        broadcast(
            "Game #" + game
                .getNumber() + " has started on a " + size + "x" + size + " map with " + teamCount + " teams.");
        GameManager.autoAssign(game);
        announceTeams(game);
        GameWorld.sendAllToGame(game);
    }

    /**
     * Opens the new game lobby, ending the running game; with close, closes it without a game; with start, starts the
     * game at once without waiting for everyone to be ready.
     */
    private void lobby(ICommandSender sender, String[] args) {
        requireOp(sender);
        String action = args.length > 0 ? args[0].toLowerCase() : "open";
        switch (action) {
            case "open":
                if (Lobby.isOpen()) {
                    throw new CommandException("The lobby is open already");
                }
                Lobby.open(sender.getCommandSenderName());
                break;
            case "close":
                if (!Lobby.isOpen()) {
                    throw new CommandException("The lobby is not open");
                }
                Lobby.close();
                broadcast(sender.getCommandSenderName() + " closed the lobby. No game is running.");
                break;
            case "start":
                if (Lobby.startNow() == null) {
                    throw new CommandException("The lobby is not open");
                }
                break;
            default:
                throw new WrongUsageException("/rts newgame [open|close|start]");
        }
    }

    /** Tells everyone who is on which team. */
    static void announceTeams(Game game) {
        for (Team team : game.getTeams()) {
            if (!team.getMembers()
                .isEmpty()) {
                broadcast(
                    "Team " + team.getColoredName()
                        + EnumChatFormatting.GOLD
                        + ": "
                        + String.join(
                            ", ",
                            team.getMembers()
                                .values()));
            }
        }
    }

    /** A number is used as is; any other text is turned into a seed the way vanilla does for world seeds. */
    static long parseSeed(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return text.hashCode();
        }
    }

    private void endGame(ICommandSender sender) {
        requireOp(sender);
        Game game = requireGame();
        GameManager.endGame();
        broadcast("Game #" + game.getNumber() + " has ended.");
    }

    /**
     * Your team gives up and is defeated, after you confirm; an op can make any team give up at once. Its units die
     * and its buildings fall to ruins.
     */
    private void surrender(ICommandSender sender, String[] args) {
        Game game = requireGame();
        if (game.isOver()) {
            throw new CommandException("Game #%d is already over", game.getNumber());
        }
        Team team;
        if (args.length > 0 && !args[0].equalsIgnoreCase("confirm")) {
            requireOp(sender);
            team = requireTeam(game, args[0]);
        } else {
            team = sender instanceof EntityPlayer ? game.getTeamOf(((EntityPlayer) sender).getUniqueID()) : null;
            if (team == null) {
                throw new CommandException("You are on no team");
            }
            if (args.length == 0) {
                send(
                    sender,
                    EnumChatFormatting.RED + "This defeats team "
                        + team.getColoredName()
                        + EnumChatFormatting.RED
                        + " for everyone on it. Type /rts surrender confirm to give up.");
                return;
            }
        }
        if (team.isDefeated()) {
            throw new CommandException("Team %s has already been defeated", team.getName());
        }
        WinConditions.defeat(game, team, "surrendered");
    }

    private void spawn(ICommandSender sender, String[] args) {
        Game game = requireGame();
        EntityPlayerMP player;
        if (args.length > 0) {
            player = getPlayer(sender, args[0]);
            if (player != sender) {
                requireOp(sender);
            }
        } else {
            player = getCommandSenderAsPlayer(sender);
        }
        GameWorld.sendToGame(player, game);
        send(sender, "Sent " + player.getCommandSenderName() + " to the game map");
    }

    private void team(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException("/rts team <list|create|remove|color|faction|join|leave|auto|base>");
        }
        Game game = requireGame();
        switch (args[0].toLowerCase()) {
            case "list":
                teamList(sender);
                break;
            case "create": {
                requireOp(sender);
                requireArgs(args, 2, "/rts team create <name> [color]");
                String name = args[1];
                if (!TEAM_NAME.matcher(name)
                    .matches()) {
                    throw new CommandException(
                        "Team names are 1 to 16 letters, digits, dashes or underscores: '%s'",
                        name);
                }
                EnumChatFormatting color = args.length > 2 ? parseColor(args[2]) : game.nextFreeColor();
                Team team = game.addTeam(name, color, Config.startingResources);
                if (team == null) {
                    throw new CommandException("Team '%s' already exists", name);
                }
                send(sender, "Created team " + team.getColoredName());
                break;
            }
            case "remove": {
                requireOp(sender);
                requireArgs(args, 2, "/rts team remove <name>");
                Team team = requireTeam(game, args[1]);
                game.removeTeam(team.getName());
                send(sender, "Removed team " + team.getColoredName());
                break;
            }
            case "faction": {
                requireOp(sender);
                requireArgs(args, 3, "/rts team faction <name> <saradomin|zamorak>");
                Team team = requireTeam(game, args[1]);
                Faction faction = Faction.byId(args[2]);
                if (faction == null) {
                    throw new CommandException("Factions are saradomin and zamorak");
                }
                team.setFaction(faction);
                // Buildings take on the new look at once.
                for (Building building : game.getBuildings(team)) {
                    building.setShown("");
                }
                send(sender, "Team " + team.getColoredName() + " now follows " + faction.displayName);
                break;
            }
            case "color": {
                requireOp(sender);
                requireArgs(args, 3, "/rts team color <name> <color>");
                Team team = requireTeam(game, args[1]);
                team.setColor(parseColor(args[2]));
                send(sender, "Team " + team.getColoredName() + " has a new color");
                break;
            }
            case "join": {
                requireArgs(args, 2, "/rts team join <team> [player]");
                Team team = requireTeam(game, args[1]);
                EntityPlayerMP player = targetPlayer(sender, args, 2);
                game.setTeamOf(player.getUniqueID(), player.getCommandSenderName(), team);
                GameWorld.setTeamSpawn(player, game);
                broadcast(player.getCommandSenderName() + " joined team " + team.getColoredName());
                break;
            }
            case "leave": {
                EntityPlayerMP player = targetPlayer(sender, args, 1);
                Team team = game.removePlayer(player.getUniqueID());
                if (team == null) {
                    throw new CommandException("%s is not on a team", player.getCommandSenderName());
                }
                GameWorld.setTeamSpawn(player, game);
                broadcast(player.getCommandSenderName() + " left team " + team.getColoredName());
                break;
            }
            case "auto": {
                requireOp(sender);
                if (args.length > 1 && "all".equalsIgnoreCase(args[1])) {
                    // Everyone online is dealt out afresh.
                    for (Object object : MinecraftServer.getServer()
                        .getConfigurationManager().playerEntityList) {
                        game.removePlayer(((EntityPlayer) object).getUniqueID());
                    }
                }
                int placed = GameManager.autoAssign(game);
                send(
                    sender,
                    placed == 0 ? "Everyone online is on a team already" : "Put " + placed + " players on teams");
                if (placed > 0) {
                    announceTeams(game);
                }
                break;
            }
            case "base": {
                requireOp(sender);
                requireArgs(args, 2, "/rts team base <team> [x z]");
                Team team = requireTeam(game, args[1]);
                int x;
                int z;
                if (args.length >= 4) {
                    x = MathHelper.floor_double(coordinate(sender, args[2], true));
                    z = MathHelper.floor_double(coordinate(sender, args[3], false));
                } else {
                    EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                    x = MathHelper.floor_double(player.posX);
                    z = MathHelper.floor_double(player.posZ);
                }
                if (!game.isInside(x, z)) {
                    throw new CommandException("%d,%d is outside the map", x, z);
                }
                team.setBase(x, z);
                // A new town hall goes up there; the old one's blocks stay where they are.
                Building hall = game.getTownHall(team);
                if (hall != null) {
                    game.removeBuilding(hall);
                }
                send(sender, "Team " + team.getColoredName() + " base is now at " + x + "," + z);
                break;
            }
            default:
                throw new WrongUsageException("/rts team <list|create|remove|color|faction|join|leave|auto|base>");
        }
    }

    private void units(ICommandSender sender, String[] args) {
        Game game = requireGame();
        String action = args.length > 0 ? args[0].toLowerCase() : "";
        if (!UNIT_ACTIONS.contains(action)) {
            Team own = sender instanceof EntityPlayer ? game.getTeamOf(((EntityPlayer) sender).getUniqueID()) : null;
            if (args.length > 0) {
                unitList(sender, game, requireTeam(game, args[0]));
            } else if (own != null) {
                unitList(sender, game, own);
            } else {
                unitSummary(sender, game);
            }
            return;
        }
        requireOp(sender);
        switch (action) {
            case "spawn": {
                requireArgs(args, 2, "/rts units spawn <team> [count] [warrior|archer|worker]");
                Team team = requireTeam(game, args[1]);
                int count = args.length > 2 ? parseIntBounded(sender, args[2], 1, MAX_SPAWN) : 1;
                UnitKind kind = args.length > 3 ? requireKind(args[3]) : UnitKind.SOLDIER;
                List<EntityUnit> spawned = Units.get()
                    .spawn(game, team, kind, count);
                if (spawned.isEmpty()) {
                    throw new CommandException(
                        "Team %s has the most units it can have (%d)",
                        team.getName(),
                        Config.unitsPerTeam);
                }
                EntityUnit first = spawned.get(0);
                send(
                    sender,
                    "Spawned " + spawned.size()
                        + " units for "
                        + team.getColoredName()
                        + " at "
                        + MathHelper.floor_double(first.posX)
                        + ","
                        + MathHelper.floor_double(first.posZ));
                break;
            }
            case "move": {
                requireArgs(args, 4, "/rts units move <team> <x> <z> [box|line|wedge]");
                Team team = requireTeam(game, args[1]);
                double x = coordinate(sender, args[2], true);
                double z = coordinate(sender, args[3], false);
                Units.Formation formation = args.length > 4 ? Units.Formation.byId(args[4]) : Units.Formation.BOX;
                if (formation == null) {
                    throw new CommandException("Unknown formation '%s': box, line or wedge", args[4]);
                }
                List<EntityUnit> units = requireUnits(team);
                Units.get()
                    .orderMove(units, x, z, formation);
                send(
                    sender,
                    "Sent " + units.size()
                        + " units of "
                        + team.getColoredName()
                        + " to "
                        + MathHelper.floor_double(x)
                        + ","
                        + MathHelper.floor_double(z));
                break;
            }
            case "attackmove": {
                requireArgs(args, 4, "/rts units attackmove <team> <x> <z> [box|line|wedge]");
                Team team = requireTeam(game, args[1]);
                double x = coordinate(sender, args[2], true);
                double z = coordinate(sender, args[3], false);
                Units.Formation formation = args.length > 4 ? Units.Formation.byId(args[4]) : Units.Formation.BOX;
                if (formation == null) {
                    throw new CommandException("Unknown formation '%s': box, line or wedge", args[4]);
                }
                List<EntityUnit> units = requireUnits(team);
                Units.get()
                    .orderAttackMove(units, x, z, formation);
                send(
                    sender,
                    "Sent " + units.size()
                        + " units of "
                        + team.getColoredName()
                        + " on an attack move to "
                        + MathHelper.floor_double(x)
                        + ","
                        + MathHelper.floor_double(z));
                break;
            }
            case "attack": {
                requireArgs(args, 3, "/rts units attack <team> <enemy team>");
                Team team = requireTeam(game, args[1]);
                Team enemy = requireTeam(game, args[2]);
                List<EntityUnit> fighters = new ArrayList<>(requireUnits(team));
                fighters.removeIf(EntityUnit::isWorker);
                List<EntityLivingBase> targets = new ArrayList<>(
                    Units.get()
                        .getUnits(enemy));
                if (fighters.isEmpty() || targets.isEmpty()) {
                    throw new CommandException("There is no one to fight");
                }
                Units.get()
                    .orderAttackAll(fighters, targets, false);
                send(
                    sender,
                    "Sent " + fighters.size()
                        + " units of "
                        + team.getColoredName()
                        + " at "
                        + targets.size()
                        + " units of "
                        + enemy.getColoredName());
                break;
            }
            case "repair": {
                requireArgs(args, 3, "/rts units repair <team> <building id>");
                Team team = requireTeam(game, args[1]);
                Building building = game.getBuilding(parseInt(sender, args[2]));
                if (building == null || !building.team.equals(team.getId()) || !building.isDamaged()) {
                    throw new CommandException("Team %s has no damaged building #%s", team.getName(), args[2]);
                }
                List<EntityUnit> workers = new ArrayList<>();
                for (EntityUnit unit : requireUnits(team)) {
                    if (unit.isWorker() && unit.getOrder() == EntityUnit.Order.IDLE) {
                        workers.add(unit);
                    }
                }
                if (workers.isEmpty()) {
                    throw new CommandException("Team %s has no idle workers", team.getName());
                }
                Units.get()
                    .orderBuild(workers, building);
                send(
                    sender,
                    "Sent " + workers
                        .size() + " workers of " + team.getColoredName() + " to repair " + building.describe(game));
                break;
            }
            case "stance": {
                requireArgs(args, 3, "/rts units stance <team> <aggressive|defensive|hold|passive>");
                Team team = requireTeam(game, args[1]);
                EntityUnit.Stance stance = EntityUnit.Stance.byId(args[2]);
                if (stance == null) {
                    throw new CommandException("Unknown stance '%s': aggressive, defensive, hold or passive", args[2]);
                }
                List<EntityUnit> units = requireUnits(team);
                Units.get()
                    .orderStance(units, stance);
                send(
                    sender,
                    "Set " + units
                        .size() + " units of " + team.getColoredName() + " to " + stance.displayName.toLowerCase());
                break;
            }
            case "stop": {
                requireArgs(args, 2, "/rts units stop <team>");
                Team team = requireTeam(game, args[1]);
                List<EntityUnit> units = requireUnits(team);
                Units.get()
                    .orderStop(units);
                send(sender, "Stopped " + units.size() + " units of " + team.getColoredName());
                break;
            }
            default: {
                requireArgs(args, 2, "/rts units kill <team|all>");
                List<EntityUnit> units;
                if (args[1].equalsIgnoreCase("all")) {
                    units = new ArrayList<>(
                        Units.get()
                            .getLoaded());
                } else {
                    units = Units.get()
                        .getUnits(requireTeam(game, args[1]));
                }
                Units.get()
                    .kill(units);
                send(sender, "Removed " + units.size() + " units");
                break;
            }
        }
    }

    private static UnitKind requireKind(String text) {
        UnitKind kind = UnitKind.byId(text);
        if (kind == null) {
            throw new CommandException(
                "Unknown unit kind %s; use warrior, archer, worker, white_knight, black_knight, tragidee or sagittare",
                text);
        }
        return kind;
    }

    private void buildings(ICommandSender sender, String[] args) {
        Game game = requireGame();
        Team only = args.length > 0 ? requireTeam(game, args[0]) : null;
        int count = 0;
        for (Building building : game.getBuildings()) {
            if (only != null && !building.team.equals(only.getId())) {
                continue;
            }
            count++;
            StringBuilder line = new StringBuilder(building.describe(game));
            if (!building.getQueue()
                .isEmpty()) {
                line.append(", training ")
                    .append(
                        building.getQueue()
                            .size());
            }
            line.append(", health ")
                .append(building.getHealth())
                .append("/")
                .append(building.getMaxHealth());
            if (!building.isComplete()) {
                line.append(", ")
                    .append((int) Math.floor(building.getBuildProgress() * 100))
                    .append("% built by ")
                    .append(Buildings.countBuilders(building))
                    .append(" workers");
            }
            if (building.type.isWorkplace() && building.isComplete()) {
                line.append(", ")
                    .append(Buildings.countWorkers(building))
                    .append(" working");
            }
            send(sender, line.toString());
        }
        if (count == 0) {
            send(sender, "Buildings: none");
        }
    }

    /** Lays out a wall for a team, for free, and sends its workers that have nothing to do to build it. */
    private void wall(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 5, "/rts wall <team> <x1> <z1> <x2> <z2>");
        Team team = requireTeam(game, args[0]);
        int x1 = MathHelper.floor_double(coordinate(sender, args[1], true));
        int z1 = MathHelper.floor_double(coordinate(sender, args[2], false));
        int x2 = MathHelper.floor_double(coordinate(sender, args[3], true));
        int z2 = MathHelper.floor_double(coordinate(sender, args[4], false));
        Buildings.WallPlan plan = Buildings.get()
            .planWall(game, GameWorld.getWorld(), team, x1, z1, x2, z2);
        if (plan.problem != null) {
            throw new CommandException(plan.problem);
        }
        List<Building> sites = Buildings.get()
            .startWall(game, team, plan);
        List<EntityUnit> idle = new ArrayList<>();
        for (EntityUnit unit : Units.get()
            .getUnits(team)) {
            if (unit.isWorker() && unit.getOrder() == EntityUnit.Order.IDLE) {
                idle.add(unit);
            }
        }
        // Sent to the first piece, they go on along the rest of the wall.
        Units.get()
            .orderBuild(idle, sites.get(0), false);
        int posts = 0;
        int shortOnes = 0;
        for (Building site : sites) {
            if (site.type == BuildingType.WALL_POST) {
                posts++;
            }
            if (site.sizeX < WallLine.SIZE || site.sizeZ < WallLine.SIZE) {
                shortOnes++;
            }
        }
        send(
            sender,
            "Laid out a wall of " + sites.size()
                + " pieces for "
                + team.getColoredName()
                + ", "
                + posts
                + " of them posts"
                + (shortOnes > 0 ? " and " + shortOnes + " short" : "")
                + ", from #"
                + sites.get(0).id
                + " to #"
                + sites.get(sites.size() - 1).id
                + ", "
                + idle.size()
                + " workers on the way");
    }

    /** Makes a finished wall segment a gate, for free. */
    private void gate(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 1, "/rts gate <building id>");
        Building building = game.getBuilding(parseInt(sender, args[0]));
        if (building == null) {
            throw new CommandException("There is no building #%s", args[0]);
        }
        String problem = Buildings.gateProblem(building);
        if (problem != null) {
            throw new CommandException(problem);
        }
        Building gate = Buildings.get()
            .makeGate(game, GameWorld.getWorld(), building);
        send(sender, "Made wall #" + building.id + " " + gate.describe(game));
    }

    /**
     * Lays out a building site for a team, for free, and sends its workers that have nothing to do. Without x and z
     * it finds a spot near the team's town hall.
     */
    private void build(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 2, "/rts build <team> <type> [x z] [rotation]");
        Team team = requireTeam(game, args[0]);
        BuildingType type = BuildingType.byId(args[1]);
        if (type == null || !type.buildable) {
            throw new CommandException(
                "Workers can build a farm, house, barracks, storehouse, armory, tower, town center or knights' fortress");
        }
        if (!type.buildableBy(team.getFaction())) {
            throw new CommandException("Only %s's teams can build a %s", type.faction().displayName, type.displayName);
        }
        if (type == BuildingType.WALL) {
            throw new CommandException("Lay a wall out with /rts wall <team> <x1> <z1> <x2> <z2>");
        }
        WorldServer world = GameWorld.getWorld();
        Buildings.Plan plan;
        if (args.length >= 4) {
            int x = MathHelper.floor_double(coordinate(sender, args[2], true));
            int z = MathHelper.floor_double(coordinate(sender, args[3], false));
            int rotation = args.length > 4 ? parseIntBounded(sender, args[4], 0, 3) : 0;
            plan = Buildings.get()
                .plan(game, world, type, x, z, rotation);
            if (plan.problem != null) {
                throw new CommandException(plan.problem);
            }
        } else {
            plan = Buildings.get()
                .findSpot(game, world, team, type);
            if (plan == null) {
                throw new CommandException(
                    type == BuildingType.TOWN_HALL ? "There is no settlement left to build on"
                        : "Found no room for a " + type.displayName.toLowerCase() + " near the town hall");
            }
        }
        Building site = Buildings.get()
            .startSite(game, team, plan);
        List<EntityUnit> idle = new ArrayList<>();
        for (EntityUnit unit : Units.get()
            .getUnits(team)) {
            if (unit.isWorker() && unit.getOrder() == EntityUnit.Order.IDLE) {
                idle.add(unit);
            }
        }
        int sent = Units.get()
            .orderBuild(idle, site);
        send(sender, "Laid out " + site.describe(game) + ", " + sent + " workers on the way");
    }

    private static String unitActivity(EntityUnit unit) {
        if (!unit.isWorker()) {
            int queued = unit.getQueuedAttacks();
            return (unit.isAttackMoving() ? " (attack move)" : "")
                + (queued > 0 ? " (" + queued + " more to attack)" : "");
        }
        String queued = unit.getQueuedBuilds() > 0 ? " (" + unit.getQueuedBuilds() + " more queued)" : "";
        String carrying = unit.getCarried() > 0 ? " carrying " + unit.getCarried() : "";
        if (unit.isDelivering()) {
            return " (delivering" + carrying + ")" + queued;
        }
        if (unit.isBuildingAt(unit.getBuildingId())) {
            return " (building)" + queued;
        }
        if (unit.isWorkingAt(unit.getBuildingId())) {
            return " (working" + carrying + ")" + queued;
        }
        return (carrying.isEmpty() ? "" : " (" + carrying.trim() + ")") + queued;
    }

    private void damage(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 2, "/rts damage <building id> <amount>");
        Building building = game.getBuilding(parseIntBounded(sender, args[0], 1, Integer.MAX_VALUE));
        if (building == null) {
            throw new CommandException("There is no building #%s", args[0]);
        }
        int amount = parseIntBounded(sender, args[1], 1, Integer.MAX_VALUE);
        String name = building.describe(game);
        Buildings.get()
            .damage(building, amount, sender.getCommandSenderName() + " used /rts damage");
        send(
            sender,
            game.getBuilding(building.id) == null ? "Destroyed " + name
                : name + " has " + building.getHealth() + "/" + building.getMaxHealth() + " health");
    }

    private void train(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 2, "/rts train <team> <unit> [count]");
        Team team = requireTeam(game, args[0]);
        UnitKind kind = requireKind(args[1]);
        int count = args.length > 2 ? parseIntBounded(sender, args[2], 1, Buildings.MAX_QUEUE) : 1;
        if (game.heroRoom(team, kind) <= 0) {
            throw new CommandException("Team %s already has %s", team.getName(), kind.displayName);
        }
        count = Math.min(count, game.heroRoom(team, kind));
        for (Building building : game.getBuildings(team)) {
            if (building.type.trains.contains(kind) && building.isComplete()) {
                count = Math.min(count, Buildings.room(game, team));
                for (int i = 0; i < count; i++) {
                    building.enqueue(kind);
                }
                send(
                    sender,
                    "Queued " + count
                        + " "
                        + (kind.hero ? kind.displayName : kind.id + "s")
                        + " at "
                        + building.describe(game));
                return;
            }
        }
        throw new CommandException(
            "Team %s has no building that trains %ss",
            team.getName(),
            kind.displayName.toLowerCase());
    }

    private void upgrade(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 1, "/rts upgrade <building id>");
        Building building = game.getBuilding(parseIntBounded(sender, args[0], 1, Integer.MAX_VALUE));
        if (building == null) {
            throw new CommandException("There is no building #%s", args[0]);
        }
        if (building.type != BuildingType.TOWN_HALL) {
            throw new CommandException(
                "Only town centers go up a level; the other buildings follow their town into each new age");
        }
        Team owner = game.getTeam(building.team);
        if (!building.isComplete() || owner == null || game.getAge(owner) >= building.type.levels) {
            throw new CommandException("%s cannot go up a level", building.describe(game));
        }
        building.cancelResearch();
        Buildings.advanceAge(game, building);
        send(sender, "Upgraded " + building.describe(game));
    }

    private void research(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 2, "/rts research <team> <upgrade>");
        Team team = requireTeam(game, args[0]);
        Upgrade upgrade = Upgrade.byId(args[1]);
        if (upgrade == null) {
            StringBuilder ids = new StringBuilder();
            for (Upgrade each : Upgrade.values()) {
                ids.append(ids.length() > 0 ? ", " : "")
                    .append(each.id);
            }
            throw new CommandException("Upgrades are %s", ids.toString());
        }
        Map<Building, Integer> before = new HashMap<>();
        for (Building building : game.getBuildings(team)) {
            before.put(building, building.getMaxHealth());
        }
        team.addUpgrade(upgrade);
        for (Map.Entry<Building, Integer> entry : before.entrySet()) {
            entry.getKey()
                .addMaxHealth(entry.getValue());
        }
        for (EntityUnit unit : Units.get()
            .getUnits(team)) {
            unit.applyUpgrades(team);
        }
        send(sender, "Team " + team.getColoredName() + " has " + upgrade.displayName.toLowerCase());
    }

    private void finish(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        Team only = args.length > 0 ? requireTeam(game, args[0]) : null;
        int count = 0;
        for (Building building : new ArrayList<>(game.getBuildings())) {
            if (!building.isComplete() && (only == null || building.team.equals(only.getId()))) {
                Buildings.get()
                    .raise(GameWorld.getWorld(), building);
                count++;
            }
        }
        send(sender, "Finished " + count + " building sites");
    }

    private void chop(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 1, "/rts chop <team>");
        Team team = requireTeam(game, args[0]);
        Building hall = game.getTownHall(team);
        if (hall == null) {
            throw new CommandException("Team %s has no town center", team.getName());
        }
        WorldServer world = GameWorld.getWorld();
        int[] tree = Trees.nearest(world, game, hall.getCenterX(), hall.groundY, hall.getCenterZ(), 48);
        if (tree == null) {
            throw new CommandException("Found no tree near the town center of %s", team.getName());
        }
        List<EntityUnit> idle = new ArrayList<>();
        for (EntityUnit unit : Units.get()
            .getUnits(team)) {
            if (unit.isWorker() && unit.getOrder() == EntityUnit.Order.IDLE) {
                idle.add(unit);
            }
        }
        int sent = Units.get()
            .orderChop(idle, tree[0], tree[1], tree[2]);
        Tree tracked = Trees.track(world, game, tree[0], tree[1], tree[2]);
        send(
            sender,
            "Sent " + sent
                + " workers of "
                + team.getColoredName()
                + " to chop the tree at "
                + tree[0]
                + ","
                + tree[1]
                + ","
                + tree[2]
                + " with "
                + tracked.getWood()
                + " wood");
    }

    /** Sends a team's idle workers to the gold mine nearest its town center that still has gold. */
    private void mine(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 1, "/rts mine <team>");
        Team team = requireTeam(game, args[0]);
        Building hall = game.getTownHall(team);
        if (hall == null) {
            throw new CommandException("Team %s has no town center", team.getName());
        }
        GoldMine mine = Units.nearestMine(game, hall.getCenterX(), hall.getCenterZ(), Double.MAX_VALUE);
        if (mine == null) {
            throw new CommandException("There is no gold mine with gold left");
        }
        List<EntityUnit> idle = new ArrayList<>();
        for (EntityUnit unit : Units.get()
            .getUnits(team)) {
            if (unit.isWorker() && unit.getOrder() == EntityUnit.Order.IDLE) {
                idle.add(unit);
            }
        }
        int sent = Units.get()
            .orderMine(idle, mine);
        send(
            sender,
            "Sent " + sent
                + " workers of "
                + team.getColoredName()
                + " to the gold mine at "
                + (int) mine.getCenterX()
                + ","
                + (int) mine.getCenterZ()
                + " with "
                + mine.getGold()
                + " gold");
    }

    private void light(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 1, "/rts light <team> [x1 z1 x2 z2]");
        Team team = requireTeam(game, args[0]);
        int x1;
        int z1;
        int x2;
        int z2;
        if (args.length >= 5) {
            x1 = MathHelper.floor_double(coordinate(sender, args[1], true));
            z1 = MathHelper.floor_double(coordinate(sender, args[2], false));
            x2 = MathHelper.floor_double(coordinate(sender, args[3], true));
            z2 = MathHelper.floor_double(coordinate(sender, args[4], false));
        } else {
            Building hall = game.getTownHall(team);
            if (hall == null) {
                throw new CommandException("Team %s has no town center", team.getName());
            }
            x1 = hall.minX - 12;
            z1 = hall.minZ - 12;
            x2 = hall.getMaxX() + 12;
            z2 = hall.getMaxZ() + 12;
        }
        List<int[]> spots = Torches.plan(GameWorld.getWorld(), game, x1, z1, x2, z2);
        List<EntityUnit> idle = new ArrayList<>();
        for (EntityUnit unit : Units.get()
            .getUnits(team)) {
            if (unit.isWorker() && unit.getOrder() == EntityUnit.Order.IDLE) {
                idle.add(unit);
            }
        }
        int sent = Units.get()
            .orderLight(idle, spots);
        send(
            sender,
            "Sent " + sent + " workers of " + team.getColoredName() + " to put up " + spots.size() + " torches");
    }

    private void route(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 4, "/rts route <x1> <z1> <x2> <z2>");
        int x1 = MathHelper.floor_double(coordinate(sender, args[0], true));
        int z1 = MathHelper.floor_double(coordinate(sender, args[1], false));
        int x2 = MathHelper.floor_double(coordinate(sender, args[2], true));
        int z2 = MathHelper.floor_double(coordinate(sender, args[3], false));
        List<double[]> route = Routes.find(GameWorld.getWorld(), game, x1 + 0.5, z1 + 0.5, x2 + 0.5, z2 + 0.5);
        if (route == null) {
            send(sender, "Found no way from " + x1 + "," + z1 + " to " + x2 + "," + z2);
            return;
        }
        StringBuilder points = new StringBuilder();
        for (double[] point : route) {
            points.append(' ')
                .append((int) Math.floor(point[0]))
                .append(',')
                .append((int) Math.floor(point[1]));
        }
        send(
            sender,
            "Route from " + x1 + "," + z1 + " to " + x2 + "," + z2 + ": " + route.size() + " waypoints:" + points);
    }

    /**
     * Sets off an explosion as big as a creeper's two blocks off a building's side, then tells how much health it
     * lost and how many of its blocks broke, which should be none.
     */
    private void explode(ICommandSender sender, String[] args) {
        requireOp(sender);
        Game game = requireGame();
        requireArgs(args, 1, "/rts explode <building id>");
        Building building = game.getBuilding(parseIntBounded(sender, args[0], 1, Integer.MAX_VALUE));
        if (building == null) {
            throw new CommandException("There is no building #%s", args[0]);
        }
        WorldServer world = GameWorld.getWorld();
        int before = solidBlocks(world, building);
        int health = building.getHealth();
        String name = building.describe(game);
        Buildings.get()
            .explodeHarmlessly(world, building.minX - 2.5, building.groundY + 1.5, building.getCenterZ(), 3);
        int broken = before - solidBlocks(world, building);
        send(
            sender,
            game.getBuilding(building.id) == null ? "The explosion destroyed " + name
                : "The explosion took " + (health - building.getHealth())
                    + " health off "
                    + name
                    + " and broke "
                    + broken
                    + " of its blocks");
    }

    /** Blocks that are not air on and around a building's footprint, above its ground. */
    private static int solidBlocks(WorldServer world, Building building) {
        int count = 0;
        for (int x = building.minX - 1; x <= building.getMaxX() + 1; x++) {
            for (int z = building.minZ - 1; z <= building.getMaxZ() + 1; z++) {
                for (int y = building.groundY; y < building.groundY + 30; y++) {
                    if (!world.isAirBlock(x, y, z)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** An x or z coordinate; ~ means relative to the sender's position. */
    private static double coordinate(ICommandSender sender, String text, boolean isX) {
        double base = 0;
        if (sender instanceof EntityPlayer) {
            base = isX ? ((EntityPlayer) sender).posX : ((EntityPlayer) sender).posZ;
        }
        return func_110666_a(sender, base, text);
    }

    private static List<EntityUnit> requireUnits(Team team) {
        List<EntityUnit> units = Units.get()
            .getUnits(team);
        if (units.isEmpty()) {
            throw new CommandException("Team %s has no units on loaded ground", team.getName());
        }
        return units;
    }

    /** One line with every team's unit count. */
    private void unitSummary(ICommandSender sender, Game game) {
        if (game.getTeams()
            .isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder("Units:");
        boolean first = true;
        for (Team team : game.getTeams()) {
            line.append(first ? " " : ", ")
                .append(team.getColoredName())
                .append(" ")
                .append(game.countUnits(team));
            first = false;
        }
        send(sender, line.toString());
    }

    private void unitList(ICommandSender sender, Game game, Team team) {
        List<EntityUnit> units = Units.get()
            .getUnits(team);
        units.sort((a, b) -> Integer.compare(a.getNumber(), b.getNumber()));
        send(
            sender,
            "Team " + team.getColoredName()
                + " has "
                + game.countUnits(team)
                + " units, room for "
                + game.getPopulationCap(team));
        for (EntityUnit unit : units) {
            send(
                sender,
                unit.getCommandSenderName() + " at "
                    + MathHelper.floor_double(unit.posX)
                    + ","
                    + MathHelper.floor_double(unit.posY)
                    + ","
                    + MathHelper.floor_double(unit.posZ)
                    + " "
                    + unit.getOrder()
                        .name()
                        .toLowerCase()
                    + unitActivity(unit)
                    + (unit.isWorker() ? "" : ", " + unit.getStance().id));
        }
    }

    /**
     * Makes a one-time login link to the web map. A player gets one for themselves; an op can send one to another
     * player, or get an observer link that belongs to no team. The server console gets an observer link by default.
     */
    private void web(ICommandSender sender, String[] args) {
        WebServer server = WebServer.get();
        if (server == null) {
            throw new CommandException("The web map is not running. Check the web section of config/warofthegods.cfg");
        }
        boolean console = !(sender instanceof EntityPlayer);
        boolean observer = args.length == 0 ? console : args[0].equalsIgnoreCase("observer");
        if (observer) {
            requireOp(sender);
            String url = webLink(server, Viewer.observer());
            String message = "Web map observer link, valid once for " + Config.webLinkMinutes + " minutes:";
            if (console) {
                send(sender, message + " " + url);
            } else {
                send(sender, EnumChatFormatting.GOLD + message);
                sendLink((EntityPlayer) sender, url);
            }
            return;
        }
        EntityPlayerMP player = args.length > 0 ? getPlayer(sender, args[0]) : getCommandSenderAsPlayer(sender);
        if (player != sender) {
            requireOp(sender);
        }
        String url = webLink(server, Viewer.player(player.getUniqueID(), player.getCommandSenderName()));
        send(
            player,
            EnumChatFormatting.GOLD + "Your web map link works once and for "
                + Config.webLinkMinutes
                + " minutes. Do not share it:");
        sendLink(player, url);
        if (player != sender) {
            send(sender, "Sent a web map link to " + player.getCommandSenderName());
        }
    }

    private static String webLink(WebServer server, Viewer viewer) {
        return WebServer.publicUrl() + "/?token="
            + server.getSessions()
                .createLinkToken(viewer);
    }

    private static void sendLink(EntityPlayer player, String url) {
        ChatComponentText link = new ChatComponentText(url);
        link.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setUnderlined(true)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        player.addChatMessage(link);
    }

    private void teamList(ICommandSender sender) {
        Game game = requireGame();
        if (game.getTeams()
            .isEmpty()) {
            send(sender, "Teams: none");
            return;
        }
        for (Team team : game.getTeams()) {
            List<String> names = new ArrayList<>(
                team.getMembers()
                    .values());
            Collections.sort(names);
            send(
                sender,
                "Team " + team.getColoredName()
                    + (team.isAi() ? " (AI)" : "")
                    + " ["
                    + resourceSummary(team)
                    + "] members: "
                    + (names.isEmpty() ? "none" : String.join(", ", names)));
        }
    }

    /** Lists the teams the computer plays, or hands a team to the computer or takes it back. */
    private void ai(ICommandSender sender, String[] args) {
        Game game = requireGame();
        if (args.length == 0) {
            List<String> names = new ArrayList<>();
            for (Team team : game.getTeams()) {
                if (team.isAi()) {
                    names.add(team.getColoredName());
                }
            }
            send(sender, "AI teams: " + (names.isEmpty() ? "none" : String.join(", ", names)));
            return;
        }
        requireOp(sender);
        Team team = requireTeam(game, args[0]);
        boolean on;
        if (args.length < 2) {
            on = !team.isAi();
        } else if ("on".equalsIgnoreCase(args[1])) {
            on = true;
        } else if ("off".equalsIgnoreCase(args[1])) {
            on = false;
        } else {
            throw new WrongUsageException("/rts ai <team> [on|off]");
        }
        team.setAi(on);
        broadcast(
            "Team " + team.getColoredName()
                + EnumChatFormatting.GOLD
                + (on ? " is now played by the computer" : " is no longer played by the computer"));
    }

    private void ally(ICommandSender sender, String[] args, boolean allied) {
        requireOp(sender);
        requireArgs(args, 2, allied ? "/rts ally <team> <team>" : "/rts unally <team> <team>");
        Game game = requireGame();
        Team a = requireTeam(game, args[0]);
        Team b = requireTeam(game, args[1]);
        if (a == b) {
            throw new CommandException("A team cannot ally with itself");
        }
        if (!game.setAllied(a, b, allied)) {
            throw new CommandException(
                allied ? "%s and %s are already allied" : "%s and %s are not allied",
                a.getName(),
                b.getName());
        }
        broadcast(
            a.getColoredName() + EnumChatFormatting.GOLD
                + " and "
                + b.getColoredName()
                + EnumChatFormatting.GOLD
                + (allied ? " are now allies." : " are no longer allies."));
    }

    private void resources(ICommandSender sender, String[] args) {
        Game game = requireGame();
        if (args.length >= 1 && RESOURCE_ACTIONS.contains(args[0].toLowerCase())) {
            requireOp(sender);
            requireArgs(args, 4, "/rts resources <give|take|set> <team> <resource> <amount>");
            Team team = requireTeam(game, args[1]);
            Resource resource = Resource.byId(args[2]);
            if (resource == null) {
                throw new CommandException("Unknown resource '%s', use food, wood or gold", args[2]);
            }
            long amount = parseAmount(args[3]);
            switch (args[0].toLowerCase()) {
                case "give":
                    team.addResource(resource, amount);
                    break;
                case "take":
                    team.addResource(resource, -amount);
                    break;
                default:
                    team.setResource(resource, amount);
                    break;
            }
            send(sender, "Team " + team.getColoredName() + " resources: " + resourceSummary(team));
            return;
        }

        Team team;
        if (args.length >= 1) {
            team = requireTeam(game, args[0]);
        } else if (sender instanceof EntityPlayer) {
            team = game.getTeamOf(((EntityPlayer) sender).getUniqueID());
            if (team == null) {
                throw new CommandException("You are not on a team");
            }
        } else {
            throw new WrongUsageException("/rts resources <team>");
        }
        send(sender, "Team " + team.getColoredName() + " resources: " + resourceSummary(team));
    }

    private static String resourceSummary(Team team) {
        StringBuilder summary = new StringBuilder();
        for (Resource resource : Resource.values()) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(resource.color)
                .append(resource.displayName)
                .append(" ")
                .append(team.getResource(resource))
                .append(EnumChatFormatting.RESET);
        }
        return summary.toString();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsFromIterableMatchingLastWord(args, SUBCOMMANDS);
        }
        Game game = GameManager.getGame();
        List<String> teams = new ArrayList<>();
        if (game != null) {
            for (Team team : game.getTeams()) {
                teams.add(team.getName());
            }
        }
        List<String> colors = new ArrayList<>();
        for (EnumChatFormatting color : Team.COLORS) {
            colors.add(
                color.name()
                    .toLowerCase());
        }
        List<String> resources = new ArrayList<>();
        for (Resource resource : Resource.values()) {
            resources.add(resource.id);
        }
        String[] players = MinecraftServer.getServer()
            .getAllUsernames();

        switch (args[0].toLowerCase()) {
            case "spawn":
                return args.length == 2 ? getListOfStringsMatchingLastWord(args, players) : null;
            case "web": {
                if (args.length != 2) {
                    return null;
                }
                List<String> options = new ArrayList<>(Arrays.asList(players));
                options.add("observer");
                return getListOfStringsFromIterableMatchingLastWord(args, options);
            }
            case "team":
                if (args.length == 2) {
                    return getListOfStringsFromIterableMatchingLastWord(args, TEAM_SUBCOMMANDS);
                }
                String action = args[1].toLowerCase();
                if (args.length == 3 && !action.equals("create") && !action.equals("list")) {
                    return action.equals("leave") ? getListOfStringsMatchingLastWord(args, players)
                        : getListOfStringsFromIterableMatchingLastWord(args, teams);
                }
                if (args.length == 4 && (action.equals("create") || action.equals("color"))) {
                    return getListOfStringsFromIterableMatchingLastWord(args, colors);
                }
                if (args.length == 4 && action.equals("join")) {
                    return getListOfStringsMatchingLastWord(args, players);
                }
                return null;
            case "units":
                if (args.length == 2) {
                    List<String> options = new ArrayList<>(UNIT_ACTIONS);
                    options.addAll(teams);
                    return getListOfStringsFromIterableMatchingLastWord(args, options);
                }
                if (args.length == 3 && UNIT_ACTIONS.contains(args[1].toLowerCase())) {
                    if (args[1].equalsIgnoreCase("kill")) {
                        teams.add("all");
                    }
                    return getListOfStringsFromIterableMatchingLastWord(args, teams);
                }
                return null;
            case "newgame":
            case "lobby":
                return args.length == 2 ? getListOfStringsMatchingLastWord(args, "open", "close", "start") : null;
            case "ai":
                if (args.length == 2) {
                    return getListOfStringsFromIterableMatchingLastWord(args, teams);
                }
                return args.length == 3 ? getListOfStringsMatchingLastWord(args, "on", "off") : null;
            case "ally":
            case "unally":
                return args.length <= 3 ? getListOfStringsFromIterableMatchingLastWord(args, teams) : null;
            case "resources":
            case "res":
                if (args.length == 2) {
                    List<String> options = new ArrayList<>(RESOURCE_ACTIONS);
                    options.addAll(teams);
                    return getListOfStringsFromIterableMatchingLastWord(args, options);
                }
                if (args.length == 3) {
                    return getListOfStringsFromIterableMatchingLastWord(args, teams);
                }
                if (args.length == 4) {
                    return getListOfStringsFromIterableMatchingLastWord(args, resources);
                }
                return null;
            default:
                return null;
        }
    }

    private static Game requireGame() {
        Game game = GameManager.getGame();
        if (game == null) {
            throw new CommandException("No game is running. An op can start one with /rts newgame or /rts quickgame");
        }
        return game;
    }

    private static Team requireTeam(Game game, String name) {
        Team team = game.getTeam(name);
        if (team == null) {
            throw new CommandException("Unknown team '%s'", name);
        }
        return team;
    }

    private static void requireOp(ICommandSender sender) {
        if (!isOp(sender)) {
            throw new CommandException("Only ops can do that");
        }
    }

    private static boolean isOp(ICommandSender sender) {
        return sender.canCommandSenderUseCommand(2, "rts");
    }

    private static void requireArgs(String[] args, int count, String usage) {
        if (args.length < count) {
            throw new WrongUsageException(usage);
        }
    }

    /** The player named at args[index], or the sender if there is none. Moving someone else needs op. */
    private static EntityPlayerMP targetPlayer(ICommandSender sender, String[] args, int index) {
        if (args.length > index) {
            EntityPlayerMP player = getPlayer(sender, args[index]);
            if (player != sender) {
                requireOp(sender);
            }
            if (!Config.allowSelfJoin) {
                requireOp(sender);
            }
            return player;
        }
        if (!Config.allowSelfJoin) {
            requireOp(sender);
        }
        return getCommandSenderAsPlayer(sender);
    }

    private static EnumChatFormatting parseColor(String name) {
        EnumChatFormatting color = Team.colorByName(name);
        if (color == null) {
            throw new CommandException("Unknown color '%s'", name);
        }
        return color;
    }

    private static long parseAmount(String value) {
        try {
            long amount = Long.parseLong(value);
            if (amount < 0) {
                throw new NumberInvalidException("commands.generic.num.tooSmall", amount, 0);
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new NumberInvalidException("commands.generic.num.invalid", value);
        }
    }

    /** Sends a message, without color codes for the server console so its log stays readable. */
    private static void send(ICommandSender sender, String message) {
        String text = sender instanceof EntityPlayer ? message : stripFormatting(message);
        sender.addChatMessage(new ChatComponentText(text));
    }

    private static String stripFormatting(String message) {
        return FORMATTING_CODE.matcher(message)
            .replaceAll("");
    }

    /** Tells every player and the server console. */
    public static void broadcast(String message) {
        MinecraftServer server = MinecraftServer.getServer();
        String text = EnumChatFormatting.GOLD + message;
        for (Object player : server.getConfigurationManager().playerEntityList) {
            ((EntityPlayer) player).addChatMessage(new ChatComponentText(text));
        }
        send(server, text);
    }
}
