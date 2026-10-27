package com.seryizhe.shenanigans;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class Shenanigans extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCommand("lightning", this::lightningCommand);
        registerCommand("creepyspawn", this::creepyspawnCommand);
        registerCommand("mobdisguise", this::mobdisguiseCommand);
        registerCommand("mobundisguise", this::mobundisguiseCommand);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var message = MiniMessage.miniMessage().deserialize(
                "<red>Welcome, <obf>" + event.getPlayer().getName() + "</obf>!");
        event.joinMessage(message);
    }

    private void lightningCommand(CommandSourceStack source, String[] args) {
        if (args.length != 1) {
            source.sendFailure(MiniMessage.miniMessage()
                    .deserialize("<red>Usage: /lightning <player>"));
            return;
        }

        var playerName = args[0];
        var player = Bukkit.getPlayer(playerName);

        if (player == null) {
            source.sendFailure(MiniMessage.miniMessage()
                    .deserialize("<red>Player not found: " + playerName));
            return;
        }

        player.getWorld().strikeLightning(player.getLocation());
        source.sendSuccess(MiniMessage.miniMessage()
                .deserialize("<green>Struck <gold>" + playerName + "</gold> with lightning!"), false);
    }

    private void creepyspawnCommand(CommandSourceStack source, String[] args) {
        if (args.length != 1) {
            source.sendFailure(MiniMessage.miniMessage()
                    .deserialize("<red>Usage: /creepyspawn <player>"));
            return;
        }

        var playerName = args[0];
        var player = Bukkit.getPlayer(playerName);

        if (player == null) {
            source.sendFailure(MiniMessage.miniMessage()
                    .deserialize("<red>Player not found: " + playerName));
            return;
        }

        var location = player.getLocation();
        var behind = location.clone().add(location.getDirection().normalize().multiply(-3));
        player.getWorld().spawnEntity(behind, EntityType.CREEPER);

        source.sendSuccess(MiniMessage.miniMessage()
                .deserialize("<green>Spawned a creeper behind <gold>" + playerName + "</gold>!"), false);
    }

    private void mobdisguiseCommand(CommandSourceStack source, String[] args) {
        if (args.length != 2) {
            source.sendFailure(MiniMessage.miniMessage()
                    .deserialize("<red>Usage: /mobdisguise <player> <disguiseType>"));
            return;
        }

        var player = Bukkit.getPlayer(args[0]);
        if (player == null) {
            source.sendFailure(MiniMessage.miniMessage()
                    .deserialize("<red>Player not found: " + args[0]));
            return;
        }

        DisguiseType disguiseType;
        try {
            disguiseType = DisguiseType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(MiniMessage.miniMessage()
                    .deserialize("<red>Unknown disguise type: " + args[1]));
            return;
        }

        DisguiseAPI.disguiseToAll(player, new MobDisguise(disguiseType));
        source.sendSuccess(MiniMessage.miniMessage()
                .deserialize("<green>Disguised <gold>" + player.getName()
                        + "</gold> as <gold>" + args[1] + "</gold>!"), false);
    }

    private void mobundisguiseCommand(CommandSourceStack source, String[] args) {
        if (args.length != 1) {
            source.sendFailure(MiniMessage.miniMessage()
                    .deserialize("<red>Usage: /mobundisguise <player>"));
            return;
        }

        var player = Bukkit.getPlayer(args[0]);
        if (player == null) {
            source.sendFailure(MiniMessage.miniMessage()
                    .deserialize("<red>Player not found: " + args[0]));
            return;
        }

        if (!DisguiseAPI.isDisguised(player)) {
            source.sendFailure(MiniMessage.miniMessage().deserialize("<red>They are not disguised!"));
            return;
        }

        DisguiseAPI.undisguiseToAll(player);
        source.sendSuccess(MiniMessage.miniMessage()
                .deserialize("<green>Removed disguise from <gold>" + player.getName() + "</gold>!"), false);
    }
}
