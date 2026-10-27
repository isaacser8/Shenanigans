package com.seryizhe.shenanigans.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public class TrollCommands {

    public void lightning(CommandSourceStack source, String[] args) {
        if (args.length != 1) {
            fail(source, "Usage: /lightning <player>");
            return;
        }

        var player = resolve(source, args[0]);
        if (player == null) return;

        player.getWorld().strikeLightning(player.getLocation());
        ok(source, "Struck <gold>" + player.getName() + "</gold> with lightning!");
    }

    public void creepySpawn(CommandSourceStack source, String[] args) {
        if (args.length != 1) {
            fail(source, "Usage: /creepyspawn <player>");
            return;
        }

        var player = resolve(source, args[0]);
        if (player == null) return;

        var location = player.getLocation();
        var behind = location.clone().add(location.getDirection().normalize().multiply(-3));
        player.getWorld().spawnEntity(behind, EntityType.CREEPER);

        ok(source, "Spawned a creeper behind <gold>" + player.getName() + "</gold>!");
    }

    public void disguise(CommandSourceStack source, String[] args) {
        if (args.length != 2) {
            fail(source, "Usage: /mobdisguise <player> <disguiseType>");
            return;
        }

        var player = resolve(source, args[0]);
        if (player == null) return;

        DisguiseType type;
        try {
            type = DisguiseType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            fail(source, "Unknown disguise type: " + args[1]);
            return;
        }

        DisguiseAPI.disguiseToAll(player, new MobDisguise(type));
        ok(source, "Disguised <gold>" + player.getName() + "</gold> as <gold>" + args[1] + "</gold>!");
    }

    public void undisguise(CommandSourceStack source, String[] args) {
        if (args.length != 1) {
            fail(source, "Usage: /mobundisguise <player>");
            return;
        }

        var player = resolve(source, args[0]);
        if (player == null) return;

        if (!DisguiseAPI.isDisguised(player)) {
            fail(source, "They are not disguised!");
            return;
        }

        DisguiseAPI.undisguiseToAll(player);
        ok(source, "Removed disguise from <gold>" + player.getName() + "</gold>!");
    }

    // --- shared helpers ---

    /** Looks up a player, reporting failure to the sender. Returns null if not found. */
    static Player resolve(CommandSourceStack source, String name) {
        var player = Bukkit.getPlayer(name);
        if (player == null) {
            fail(source, "Player not found: " + name);
        }
        return player;
    }

    static void ok(CommandSourceStack source, String message) {
        source.sendSuccess(MiniMessage.miniMessage().deserialize("<green>" + message), false);
    }

    static void fail(CommandSourceStack source, String message) {
        source.sendFailure(MiniMessage.miniMessage().deserialize("<red>" + message));
    }
}
