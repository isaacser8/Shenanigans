package com.seryizhe.shenanigans.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/** One playable game. The host handles the lobby, timers and cleanup. */
public interface Minigame {

    /** Name used in commands, lowercase, no spaces. */
    String getId();

    /** Display name. */
    String getName();

    int getMinPlayers();

    /**
     * Starts a round with these players. The host has already teleported
     * them to the arena.
     */
    void onStart(GameHost host, List<Player> players);

    /** Cleans up anything this game changed. Always called, even on crash or shutdown. */
    void onEnd(GameHost host);

    /** True once the round has been won or lost. Checked by the host each second. */
    boolean isOver();

    /** Shown in the action bar during a round. */
    String getStatusText();

    /** Whether a candidate spot from /mg findarena suits this game. */
    boolean isGoodArena(Location location);
}
