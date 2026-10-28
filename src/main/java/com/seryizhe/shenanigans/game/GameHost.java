package com.seryizhe.shenanigans.game;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Runs whichever minigame is selected: lobby, timers, arena, cleanup. */
public class GameHost {

    private final Plugin plugin;

    private Minigame game;
    private GameState state = GameState.IDLE;

    private final Set<UUID> lobby = new HashSet<>();
    private final Set<UUID> players = new HashSet<>();

    private Location arenaCenter;
    private int arenaRadius = 50;

    private Location lobbySpawn;

    private BukkitTask timerTask;
    private int secondsLeft;

    public GameHost(Plugin plugin) {
        this.plugin = plugin;
        this.lobbySpawn = plugin.getConfig().getLocation("lobby");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    // --- selection ---

    /** False while a round is running — swapping mid-round would end the wrong game. */
    public boolean select(Minigame game) {
        if (state != GameState.IDLE) return false;
        this.game = game;
        return true;
    }

    public Minigame getGame() {
        return game;
    }

    public GameState getState() {
        return state;
    }

    // --- players ---

    public boolean isPlaying(Player player) {
        return players.contains(player.getUniqueId());
    }

    public Set<UUID> getPlayerIds() {
        return players;
    }

    /** Online players currently in the round. */
    public List<Player> getPlayers() {
        var list = new ArrayList<Player>();
        for (UUID id : players) {
            var p = Bukkit.getPlayer(id);
            if (p != null) list.add(p);
        }
        return list;
    }

    public boolean join(Player player) {
        if (state != GameState.IDLE) return false;
        return lobby.add(player.getUniqueId());
    }

    public int getLobbySize() {
        return lobby.size();
    }

    /** Removes a player from the round. Called on quit and on elimination. */
    public void remove(Player player) {
        UUID id = player.getUniqueId();
        lobby.remove(id);
        boolean wasPlaying = players.remove(id);

        if (wasPlaying && game != null && game.isOver()) {
            end("Game over");
        }
    }

    // --- lobby ---

    public void setLobbySpawn(Location location) {
        this.lobbySpawn = location;
        plugin.getConfig().set("lobby", location);
        plugin.saveConfig();
    }

    public void sendToLobby(Player player) {
        if (lobbySpawn != null) player.teleport(lobbySpawn);
    }

    // --- arena ---

    public void setArena(Location center, int radius) {
        this.arenaCenter = center;
        this.arenaRadius = radius;
    }

    public Location getArenaCenter() {
        return arenaCenter;
    }

    public boolean isOutside(Player player) {
        if (arenaCenter == null) return false;
        if (!player.getWorld().equals(arenaCenter.getWorld())) return true;
        return player.getLocation().distance(arenaCenter) > arenaRadius;
    }

    // --- round lifecycle ---

    public boolean start() {
        if (state != GameState.IDLE) return false;
        if (game == null) return false;
        if (lobby.size() < game.getMinPlayers()) return false;

        players.addAll(lobby);
        lobby.clear();

        var online = getPlayers();
        if (arenaCenter != null) {
            for (var p : online) p.teleport(arenaCenter);
        }

        state = GameState.RUNNING;
        game.onStart(this, online);

        startTicking();
        return true;
    }

    public void end(String reason) {
        if (state == GameState.IDLE) return;
        state = GameState.ENDING;
        stopTicking();

        broadcast(reason);
        if (game != null) game.onEnd(this);
        for (var p : getPlayers()) sendToLobby(p);

        lobby.clear();
        players.clear();
        state = GameState.IDLE;
    }

    // --- ticking ---

    private void startTicking() {
        stopTicking();
        secondsLeft = 0;
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            secondsLeft++;
            updateActionBar();

            if (game != null && game.isOver()) {
                end("Game over");
            }
        }, 20L, 20L);
    }

    private void stopTicking() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    public int getElapsedSeconds() {
        return secondsLeft;
    }

    private void updateActionBar() {
        if (game == null) return;

        var component = MiniMessage.miniMessage().deserialize(game.getStatusText());
        for (var p : getPlayers()) {
            p.sendActionBar(component);
        }
    }

    // --- messaging ---

    public void broadcast(String message) {
        var component = MiniMessage.miniMessage().deserialize("<yellow>" + message);
        for (var p : getPlayers()) {
            p.sendMessage(component);
        }
    }

    public void send(Player player, String miniMessage) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(miniMessage));
    }
}
