package com.seryizhe.shenanigans.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds candidate arena locations near villages and on a ring around the player; the game judges them. */
public class ArenaFinder {

    private static final List<Structure> VILLAGES = List.of(
            Structure.VILLAGE_PLAINS,
            Structure.VILLAGE_TAIGA,
            Structure.VILLAGE_SAVANNA,
            Structure.VILLAGE_DESERT,
            Structure.VILLAGE_SNOWY
    );

    private final List<Location> candidates = new ArrayList<>();
    private int rawCount;
    private int current;

    public int find(Player player, Minigame game) {
        candidates.clear();
        rawCount = 0;
        current = 0;

        var world = player.getWorld();
        var origin = player.getLocation();

        for (var type : VILLAGES) {
            var result = world.locateNearestStructure(origin, type, 100, false);
            if (result != null && result.getLocation().distance(origin) < 2000) {
                consider(game, world.getHighestBlockAt(result.getLocation()).getLocation().add(0, 1, 0));
            }
        }

        for (int radius = 100; radius <= 500; radius += 200) {
            for (int angle = 0; angle < 360; angle += 90) {
                int x = origin.getBlockX() + (int) (radius * Math.cos(Math.toRadians(angle)));
                int z = origin.getBlockZ() + (int) (radius * Math.sin(Math.toRadians(angle)));

                consider(game, world.getHighestBlockAt(x, z).getLocation().add(0, 1, 0));
            }
        }

        return candidates.size();
    }

    private void consider(Minigame game, Location location) {
        rawCount++;
        if (game.isGoodArena(location)) candidates.add(location);
    }

    public List<Location> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    /** Spots found by the last search, before the game's isGoodArena check. */
    public int getRawCount() {
        return rawCount;
    }

    /** Spots that passed the game's isGoodArena check. */
    public int size() {
        return candidates.size();
    }

    /** Teleports the player to candidate number {@code index} (1-based). Null if out of range. */
    public Location visit(Player player, int index) {
        if (index < 1 || index > candidates.size()) return null;
        var location = candidates.get(index - 1);
        player.teleport(location);
        current = index;
        return location;
    }

    /** 1-based index of the candidate after the last one visited, wrapping to 1. */
    public int nextIndex() {
        return current % Math.max(1, candidates.size()) + 1;
    }
}
