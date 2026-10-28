package com.seryizhe.shenanigans;

import com.seryizhe.shenanigans.commands.GameCommand;
import com.seryizhe.shenanigans.commands.TrollCommands;
import com.seryizhe.shenanigans.game.GameHost;
import com.seryizhe.shenanigans.game.MinigameRegistry;
import com.seryizhe.shenanigans.game.games.HideAndSeek;
import com.seryizhe.shenanigans.listeners.ButtonListener;
import com.seryizhe.shenanigans.listeners.GameListener;
import com.seryizhe.shenanigans.listeners.JoinListener;
import com.seryizhe.shenanigans.listeners.LobbySignListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Shenanigans extends JavaPlugin {

    private GameHost host;

    @Override
    public void onEnable() {
        host = new GameHost(this);

        var registry = new MinigameRegistry();
        registry.register(new HideAndSeek());

        var trolls = new TrollCommands();
        var gameCommand = new GameCommand(host, registry);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new JoinListener(host), this);
        pm.registerEvents(new LobbySignListener(host, registry), this);
        pm.registerEvents(new ButtonListener(gameCommand), this);
        pm.registerEvents(new GameListener(host), this);

        registerCommand("lightning", trolls::lightning);
        registerCommand("creepyspawn", trolls::creepySpawn);
        registerCommand("mobdisguise", trolls::disguise);
        registerCommand("mobundisguise", trolls::undisguise);
        registerCommand("mg", gameCommand::execute);
    }

    @Override
    public void onDisable() {
        if (host != null) {
            host.end("Server shutting down");
        }
    }
}