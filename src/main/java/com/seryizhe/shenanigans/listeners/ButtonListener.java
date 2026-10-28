package com.seryizhe.shenanigans.listeners;

import com.seryizhe.shenanigans.commands.GameCommand;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Set;

/**
 * Pressing a button runs the action on a sign within 2 blocks:
 * [join] game, [start], [find], [next], [use]. Same checks and messages as /mg.
 */
public class ButtonListener implements Listener {

    private static final int REACH = 2;
    private static final int DEFAULT_RADIUS = 50;
    private static final Set<String> ACTIONS = Set.of("[join]", "[start]", "[find]", "[next]", "[use]");

    private final GameCommand actions;

    public ButtonListener(GameCommand actions) {
        this.actions = actions;
    }

    // ignoreCancelled: if another plugin blocks the press, don't act on it either.
    @EventHandler(ignoreCancelled = true)
    public void onPress(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        var button = event.getClickedBlock();
        if (button == null || !Tag.BUTTONS.isTagged(button.getType())) return;

        var lines = findSign(button);
        if (lines == null) return;

        // Never cancel: the button should still press and animate.
        var player = event.getPlayer();
        switch (lines[0]) {
            case "[join]" -> {
                if (actions.select(player, lines[1])) actions.join(player, player);
            }
            case "[start]" -> actions.start(player);
            case "[find]" -> {
                if (actions.findArena(player) > 0) actions.visit(player, 1);
            }
            case "[next]" -> actions.visitNext(player);
            case "[use]" -> actions.setArena(player, DEFAULT_RADIUS);
            default -> throw new IllegalStateException(lines[0]);
        }
    }

    /** Front lines 1 and 2 of the nearest sign whose first line is a known action, or null. */
    private String[] findSign(Block button) {
        var plain = PlainTextComponentSerializer.plainText();
        String[] best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dy = -REACH; dy <= REACH; dy++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    var block = button.getRelative(dx, dy, dz);
                    if (!Tag.ALL_SIGNS.isTagged(block.getType())) continue;
                    if (!(block.getState() instanceof Sign sign)) continue;

                    var side = sign.getSide(Side.FRONT);
                    String action = plain.serialize(side.line(0)).trim().toLowerCase();
                    if (!ACTIONS.contains(action)) continue;

                    int distance = dx * dx + dy * dy + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new String[] {action, plain.serialize(side.line(1)).trim()};
                    }
                }
            }
        }
        return best;
    }
}
