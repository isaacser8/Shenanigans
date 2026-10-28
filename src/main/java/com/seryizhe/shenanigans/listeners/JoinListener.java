package com.seryizhe.shenanigans.listeners;

import com.seryizhe.shenanigans.game.GameHost;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final GameHost host;

    public JoinListener(GameHost host) {
        this.host = host;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var message = MiniMessage.miniMessage().deserialize(
                "<red>Welcome, <obf>" + event.getPlayer().getName() + "</obf>!");
        event.joinMessage(message);
        host.sendToLobby(event.getPlayer());
    }
}
