package com.seryizhe.shenanigans.commands;

import com.seryizhe.shenanigans.game.GameManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import static com.seryizhe.shenanigans.commands.TrollCommands.fail;
import static com.seryizhe.shenanigans.commands.TrollCommands.ok;
import static com.seryizhe.shenanigans.commands.TrollCommands.resolve;

public class GameCommand {

    private final GameManager game;

    public GameCommand(GameManager game) {
        this.game = game;
    }

    public void execute(CommandSourceStack source, String[] args) {
        if (args.length < 1) {
            fail(source, "Usage: /hs <join|start|stop|findarena|list|visit|setarena>");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "join" -> join(source, args);
            case "start" -> start(source);
            case "stop" -> stop(source);
            case "findarena" -> findArena(source);
            case "list" -> list(source);
            case "visit" -> visit(source, args);
            case "setarena" -> setArena(source, args);
            default -> fail(source, "Unknown subcommand: " + args[0]);
        }
    }

    private void join(CommandSourceStack source, String[] args) {
        Player player;
        if (args.length == 2) {
            player = resolve(source, args[1]);
            if (player == null) return;
        } else {
            player = asPlayer(source, "Usage from console: /hs join <player>");
            if (player == null) return;
        }

        if (game.join(player)) {
            ok(source, player.getName() + " joined the lobby");
        } else {
            fail(source, "Could not join — game may already be running");
        }
    }

    private void start(CommandSourceStack source) {
        if (game.start()) {
            ok(source, "Game started!");
        } else {
            fail(source, "Could not start — need 2+ players and no game running");
        }
    }

    private void stop(CommandSourceStack source) {
        game.end("Game stopped");
        ok(source, "Game stopped");
    }

    private void findArena(CommandSourceStack source) {
        var player = asPlayer(source, "Run this in game");
        if (player == null) return;

        int found = game.findCandidates(player);
        if (found == 0) {
            fail(source, "No candidates found — use /hs setarena here instead");
            return;
        }

        ok(source, "Found " + found + " candidates. Use /hs list to see them, /hs visit <n> to tour.");
    }

    private void list(CommandSourceStack source) {
        var candidates = game.getCandidates();
        if (candidates.isEmpty()) {
            fail(source, "No candidates — run /hs findarena first");
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

        Integer index = parseInt(source, args, 1, "Usage: /hs visit <number>");
        if (index == null) return;

        var location = game.visitCandidate(player, index);
        if (location == null) {
            fail(source, "No candidate " + index + " — there are " + game.getCandidateCount());
            return;
        }

        ok(source, "Candidate " + index + " — " + location.getBlock().getBiome().getKey().getKey());
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

        game.setArena(player.getLocation(), radius);
        ok(source, "Arena set here, radius " + radius);
    }

    // --- helpers ---

    /** Returns the sender as a Player, or null (with a message) if it's the console. */
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
