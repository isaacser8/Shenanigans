package com.seryizhe.shenanigans.game;

public enum GameState {
    IDLE,       // no game running, players can join the lobby
    COUNTDOWN,  // enough players, about to start
    HIDING,     // hiders disguising, seekers frozen
    SEEKING,    // seekers released
    ENDING      // showing results, cleaning up
}
