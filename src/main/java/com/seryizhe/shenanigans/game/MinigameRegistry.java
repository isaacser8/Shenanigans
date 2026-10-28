package com.seryizhe.shenanigans.game;

import java.util.LinkedHashMap;
import java.util.Map;

public class MinigameRegistry {

    private final Map<String, Minigame> games = new LinkedHashMap<>();

    public void register(Minigame game) {
        games.put(game.getId(), game);
    }

    public Minigame get(String id) {
        return games.get(id.toLowerCase());
    }

    public Map<String, Minigame> getAll() {
        return games;
    }
}
