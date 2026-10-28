package com.seryizhe.shenanigans.listeners;

import com.seryizhe.shenanigans.game.GameHost;
import com.seryizhe.shenanigans.game.games.HideAndSeek;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GameListener implements Listener {

    private final GameHost host;

    public GameListener(GameHost host) {
        this.host = host;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!host.isPlaying(victim) || !host.isPlaying(attacker)) return;

        // A hit is a tag, not a fight.
        event.setCancelled(true);

        // Only hide and seek cares about tagging, for now.
        if (host.getGame() instanceof HideAndSeek game
                && game.isSeeker(attacker) && game.isHider(victim)) {
            game.caught(victim, attacker);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        var player = event.getPlayer();
        if (!host.isPlaying(player)) return;
        if (!host.isOutside(player)) return;

        player.teleport(host.getArenaCenter());
        player.sendMessage(MiniMessage.miniMessage()
                .deserialize("<red>Stay inside the arena!"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        host.remove(event.getPlayer());
    }
}