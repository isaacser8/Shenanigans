package com.seryizhe.shenanigans.commands;

import com.seryizhe.shenanigans.game.ArenaFinder;
import com.seryizhe.shenanigans.game.GameHost;
import com.seryizhe.shenanigans.game.Minigame;
import com.seryizhe.shenanigans.game.MinigameRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

import static com.seryizhe.shenanigans.commands.TrollCommands.fail;
import static com.seryizhe.shenanigans.commands.TrollCommands.ok;
import static com.seryizhe.shenanigans.commands.TrollCommands.resolve;

public class GameCommand {

    private final GameHost host;
    private final MinigameRegistry registry;
    private final ArenaFinder finder = new ArenaFinder();

    public GameCommand(GameHost host, MinigameRegistry registry) {
        this.host = host;
        this.registry = registry;
    }

    public void execute(CommandSourceStack source, String[] args) {
        if (args.length < 1) {
            fail(source, "Usage: /mg <games|select|join|start|stop|findarena|list|visit|setarena|savearena|cleararena|arenas|deletearena|setlobby>");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "games" -> games(source);
            case "select" -> select(source, args);
            case "join" -> join(source, args);
            case "start" -> start(source.getSender());
            case "stop" -> stop(source);
            case "findarena" -> findArena(source);
            case "list" -> listCandidates(source);
            case "visit" -> visit(source, args);
            case "setarena" -> setArena(source, args);
            case "savearena" -> saveArena(source, args);
            case "cleararena" -> clearArena(source);
            case "arenas" -> listArenas(source, args);
            case "deletearena" -> deleteArena(source, args);
            case "setlobby" -> setLobby(source);
            default -> fail(source, "Unknown subcommand: " + args[0]);
        }
    }

    private void games(CommandSourceStack source) {
        var sb = new StringBuilder("<yellow>Available games:");
        for (var entry : registry.getAll().entrySet()) {
            sb.append("\n<white>").append(entry.getKey())
                    .append(" <gray>— ").append(entry.getValue().getName());
        }
        source.sendSuccess(MiniMessage.miniMessage().deserialize(sb.toString()), false);
    }

    private void select(CommandSourceStack source, String[] args) {
        if (args.length != 2) {
            fail(source, "Usage: /mg select <game>");
            return;
        }

        select(source.getSender(), args[1]);
    }

    public boolean select(CommandSender sender, String id) {
        var game = registry.get(id);
        if (game == null) {
            fail(sender, "No such game: " + id + " — try /mg games");
            return false;
        }

        if (!host.select(game)) {
            fail(sender, "Can't switch games mid-round");
            return false;
        }
        ok(sender, "Selected " + game.getName());
        return true;
    }

    private void join(CommandSourceStack source, String[] args) {
        Player player;
        if (args.length == 2) {
            player = resolve(source, args[1]);
            if (player == null) return;
        } else {
            player = asPlayer(source, "Usage from console: /mg join <player>");
            if (player == null) return;
        }

        join(source.getSender(), player);
    }

    public void join(CommandSender sender, Player player) {
        if (host.join(player)) {
            ok(sender, player.getName() + " joined the lobby (" + host.getLobbySize() + ")");
        } else {
            fail(sender, "Could not join — a game may already be running");
        }
    }

    public void start(CommandSender sender) {
        if (host.getGame() == null) {
            fail(sender, "No game selected — use /mg select <game>");
            return;
        }

        if (host.start()) {
            ok(sender, host.getGame().getName() + " started!");
        } else {
            fail(sender, "Could not start — need " + host.getGame().getMinPlayers()
                    + "+ players and no game running");
        }
    }

    private void stop(CommandSourceStack source) {
        host.end("Game stopped");
        ok(source, "Game stopped");
    }

    private void findArena(CommandSourceStack source) {
        var player = asPlayer(source, "Run this in game");
        if (player != null) findArena(player);
    }

    /** Returns how many suitable candidates were found. */
    public int findArena(Player player) {
        if (host.getGame() == null) {
            fail(player, "No game selected — each game judges arenas differently");
            return 0;
        }

        var name = host.getGame().getName();
        int suitable = finder.find(player, host.getGame());
        int raw = finder.getRawCount();
        if (raw == 0) {
            fail(player, "No spots found nearby — use /mg setarena here instead");
            return 0;
        }
        if (suitable == 0) {
            fail(player, "Found " + raw + " spots, but none suitable for " + name
                    + " — use /mg setarena here instead");
            return 0;
        }

        ok(player, "Found " + raw + " spots, " + suitable + " suitable for " + name
                + ". /mg list to see them, /mg visit <n> to tour.");
        return suitable;
    }

    private void listCandidates(CommandSourceStack source) {
        var candidates = finder.getCandidates();
        if (candidates.isEmpty()) {
            fail(source, "No candidates — run /mg findarena first");
            return;
        }

        var sb = new StringBuilder("<yellow>Arena candidates:");
        for (int i = 0; i < candidates.size(); i++) {
            var loc = candidates.get(i);
            sb.append("\n<white>").append(i + 1).append(". ")
                    .append(loc.getBlockX()).append(", ").append(loc.getBlockZ())
                    .append(" <gray>(").append(loc.getBlock().getBiome().getKey().getKey())
                    .append(")");
        }
        source.sendSuccess(MiniMessage.miniMessage().deserialize(sb.toString()), false);
    }

    private void visit(CommandSourceStack source, String[] args) {
        var player = asPlayer(source, "Run this in game");
        if (player == null) return;

        Integer index = parseInt(source, args, 1, "Usage: /mg visit <number>");
        if (index == null) return;

        visit(player, index);
    }

    public void visit(Player player, int index) {
        var location = finder.visit(player, index);
        if (location == null) {
            fail(player, "No candidate " + index + " — there are " + finder.size());
            return;
        }

        ok(player, "Candidate " + index + " — " + location.getBlock().getBiome().getKey().getKey());
    }

    /** Visits the candidate after the last one visited, wrapping at the end. */
    public void visitNext(Player player) {
        if (finder.size() == 0) {
            fail(player, "No candidates — run /mg findarena first");
            return;
        }
        visit(player, finder.nextIndex());
    }

    private void setArena(CommandSourceStack source, String[] args) {
        var player = asPlayer(source, "Run this in game, standing at the arena center");
        if (player == null) return;

        int radius = 50;
        if (args.length == 2) {
            Integer parsed = parseInt(source, args, 1, "Radius must be a number");
            if (parsed == null) return;
            radius = parsed;
        }

        setArena(player, radius);
    }

    public void setArena(Player player, int radius) {
        host.setArena(player.getLocation(), radius);
        ok(player, "Arena set here, radius " + radius);
    }

    private void saveArena(CommandSourceStack source, String[] args) {
        if (args.length != 3) {
            fail(source, "Usage: /mg savearena <game> <name>");
            return;
        }
        var game = gameArg(source, args[1]);
        var name = nameArg(source, args[2]);
        if (game == null || name == null) return;

        if (host.saveArena(game.getId(), name)) {
            host.clearArena();
            ok(source, "Saved arena " + name + " for " + game.getName()
                    + " (radius " + host.getArenaRadius() + "). Rounds now pick a saved arena at random.");
        } else {
            fail(source, "No arena set — use /mg setarena first");
        }
    }

    private void listArenas(CommandSourceStack source, String[] args) {
        if (args.length > 2) {
            fail(source, "Usage: /mg arenas [game]");
            return;
        }

        var games = new ArrayList<Minigame>();
        if (args.length == 2) {
            var game = gameArg(source, args[1]);
            if (game == null) return;
            games.add(game);
        } else {
            games.addAll(registry.getAll().values());
        }

        var sb = new StringBuilder("<yellow>Saved arenas:");
        boolean any = false;
        for (var game : games) {
            for (var arena : host.getSavedArenas(game.getId())) {
                any = true;
                var c = arena.center();
                sb.append("\n<white>").append(game.getId()).append(" ").append(arena.name())
                        .append(" <gray>— ").append(arena.world());
                if (c == null) {
                    sb.append(" <red>(unavailable: world not loaded)");
                } else {
                    sb.append(" ").append(c.getBlockX()).append(", ").append(c.getBlockZ())
                            .append(", radius ").append(arena.radius());
                }
            }
        }

        if (!any) {
            fail(source, "No saved arenas — use /mg savearena <game> <name>");
            return;
        }
        source.sendSuccess(MiniMessage.miniMessage().deserialize(sb.toString()), false);
    }

    private void clearArena(CommandSourceStack source) {
        if (host.clearArena()) {
            ok(source, "Manual arena cleared — rounds now pick a saved arena at random");
        } else {
            fail(source, "No manual arena to clear");
        }
    }

    private void deleteArena(CommandSourceStack source, String[] args) {
        if (args.length != 3) {
            fail(source, "Usage: /mg deletearena <game> <name>");
            return;
        }
        var game = gameArg(source, args[1]);
        var name = nameArg(source, args[2]);
        if (game == null || name == null) return;

        if (host.deleteArena(game.getId(), name)) {
            ok(source, "Deleted arena " + name + " for " + game.getName());
        } else {
            fail(source, "No arena " + name + " for " + game.getName() + " — try /mg arenas " + game.getId());
        }
    }

    private Minigame gameArg(CommandSourceStack source, String id) {
        var game = registry.get(id);
        if (game == null) fail(source, "No such game: " + id + " — try /mg games");
        return game;
    }

    /** Lowercase letters, digits, - and _ only: dots would split the config path. */
    private String nameArg(CommandSourceStack source, String name) {
        var lower = name.toLowerCase();
        if (lower.matches("[a-z0-9_-]+")) return lower;
        fail(source, "Arena names can only use letters, digits, - and _");
        return null;
    }

    private void setLobby(CommandSourceStack source) {
        var player = asPlayer(source, "Run this in game, standing at the lobby spawn");
        if (player == null) return;

        host.setLobbySpawn(player.getLocation());
        ok(source, "Lobby spawn set here");
    }

    private Player asPlayer(CommandSourceStack source, String message) {
        if (source.getSender() instanceof Player p) return p;
        fail(source, message);
        return null;
    }

    private Integer parseInt(CommandSourceStack source, String[] args, int index, String usage) {
        if (args.length <= index) {
            fail(source, usage);
            return null;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            fail(source, "That's not a number");
            return null;
        }
    }
}