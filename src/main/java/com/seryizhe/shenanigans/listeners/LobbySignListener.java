package com.seryizhe.shenanigans.listeners;

import com.seryizhe.shenanigans.game.GameHost;
import com.seryizhe.shenanigans.game.MinigameRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/** Right-clicking a sign with "[game]" on line 1 and a game id on line 2 selects that game. */
public class LobbySignListener implements Listener {

    private final GameHost host;
    private final MinigameRegistry registry;

    public LobbySignListener(GameHost host, MinigameRegistry registry) {
        this.host = host;
        this.registry = registry;
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) return;

        var plain = PlainTextComponentSerializer.plainText();
        var side = sign.getSide(Side.FRONT);
        if (!plain.serialize(side.line(0)).trim().equalsIgnoreCase("[game]")) return;

        // Don't open the sign editor.
        event.setCancelled(true);

        var player = event.getPlayer();
        var mm = MiniMessage.miniMessage();
        var game = registry.get(plain.serialize(side.line(1)).trim());
        if (game == null) {
            player.sendMessage(mm.deserialize("<red>This sign names an unknown game"));
        } else if (host.select(game)) {
            player.sendMessage(mm.deserialize("<green>Selected " + game.getName()));
        } else {
            player.sendMessage(mm.deserialize("<red>Can't switch games mid-round"));
        }
    }
}
