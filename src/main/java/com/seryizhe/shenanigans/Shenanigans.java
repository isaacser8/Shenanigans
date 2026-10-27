package com.seryizhe.shenanigans;

import com.seryizhe.shenanigans.commands.GameCommand;
import com.seryizhe.shenanigans.commands.TrollCommands;
import com.seryizhe.shenanigans.game.GameManager;
import com.seryizhe.shenanigans.listeners.GameListener;
import com.seryizhe.shenanigans.listeners.JoinListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Shenanigans extends JavaPlugin {

    private GameManager gameManager;

    @Override
    public void onEnable() {
        gameManager = new GameManager(this);

        var trolls = new TrollCommands();
        var gameCommand = new GameCommand(gameManager);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new JoinListener(), this);
        pm.registerEvents(new GameListener(gameManager), this);

        registerCommand("lightning", trolls::lightning);
        registerCommand("creepyspawn", trolls::creepySpawn);
        registerCommand("mobdisguise", trolls::disguise);
        registerCommand("mobundisguise", trolls::undisguise);
        registerCommand("hs", gameCommand::execute);
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.end("Server shutting down");
        }
    }
}