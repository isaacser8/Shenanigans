package com.seryizhe.shenanigans.listeners;

import com.seryizhe.shenanigans.game.GameManager;
import com.seryizhe.shenanigans.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GameListener implements Listener {

    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        if (!gameManager.isPlaying(victim) || !gameManager.isPlaying(attacker)) return;

        // No damage between players during a game — a hit is a tag, not a fight
        event.setCancelled(true);

        if (gameManager.getState() == GameState.SEEKING
                && gameManager.isSeeker(attacker)
                && gameManager.isHider(victim)) {
            gameManager.caught(victim, attacker);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gameManager.remove(event.getPlayer());
    }
}
