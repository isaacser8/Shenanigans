package com.seryizhe.shenanigans.game;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Runs whichever minigame is selected: lobby, timers, arena, cleanup.
 *
 * <p>Config rule: save plain values (strings, numbers) to config.yml, never Bukkit objects
 * like Location. Bukkit deserializes those at load time, and a Location whose world isn't
 * loaded yet throws "unknown world" and is silently dropped — then the next saveConfig()
 * erases it from the file. Use {@link #writeLocation}/{@link #readLocation}.
 */
public class GameHost {

    private final Plugin plugin;

    private Minigame game;
    private GameState state = GameState.IDLE;

    private final Set<UUID> lobby = new HashSet<>();
    private final Set<UUID> players = new HashSet<>();

    private Location arenaCenter;
    private int arenaRadius = 50;
    // False when the arena was picked from the saved list, so the next round picks again.
    private boolean manualArena;


    private final Random random = new Random();

    private BukkitTask timerTask;
    private int secondsLeft;

    public GameHost(Plugin plugin) {
        this.plugin = plugin;
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
        writeLocation("lobby", location);
        plugin.saveConfig();
    }

    public void sendToLobby(Player player) {
        // Read on use, so a lobby world that loads after startup still works.
        var lobby = readLocation(plugin.getConfig().getConfigurationSection("lobby"));
        if (lobby != null) player.teleport(lobby);
    }

    // --- arena ---

    public void setArena(Location center, int radius) {
        this.arenaCenter = center;
        this.arenaRadius = radius;
        this.manualArena = true;
    }

    public Location getArenaCenter() {
        return arenaCenter;
    }

    public int getArenaRadius() {
        return arenaRadius;
    }

    /** Drops the /mg setarena override so the next round picks a saved arena. False if there was none. */
    public boolean clearArena() {
        boolean had = manualArena;
        manualArena = false;
        return had;
    }

    // --- saved arenas (config.yml: arenas.<game>.<name>.world / x / y / z / yaw / pitch / radius) ---

    /** {@code center} is null when {@code world} isn't loaded. */
    public record SavedArena(String name, String world, Location center, int radius) {}

    /** Saves the current arena. False if no arena is set. */
    public boolean saveArena(String gameId, String name) {
        if (arenaCenter == null) return false;
        var section = writeLocation("arenas." + gameId + "." + name, arenaCenter);
        section.set("radius", arenaRadius);
        plugin.saveConfig();
        return true;
    }

    /** False if there was no such arena. */
    public boolean deleteArena(String gameId, String name) {
        var path = "arenas." + gameId + "." + name;
        if (!plugin.getConfig().contains(path)) return false;
        plugin.getConfig().set(path, null);
        plugin.saveConfig();
        return true;
    }

    /** Saved arenas for a game, including ones whose world isn't loaded. */
    public List<SavedArena> getSavedArenas(String gameId) {
        var list = new ArrayList<SavedArena>();
        var section = plugin.getConfig().getConfigurationSection("arenas." + gameId);
        if (section == null) return list;

        for (var name : section.getKeys(false)) {
            var arena = section.getConfigurationSection(name);
            if (arena == null) continue;
            list.add(new SavedArena(name, arena.getString("world", "?"),
                    readLocation(arena), arena.getInt("radius", 50)));
        }
        return list;
    }

    public boolean isOutside(Player player) {
        if (arenaCenter == null) return false;
        if (!player.getWorld().equals(arenaCenter.getWorld())) return true;
        return player.getLocation().distance(arenaCenter) > arenaRadius;
    }

    // --- config locations (see class comment) ---

    /** Replaces {@code path} with a section of plain world/x/y/z/yaw/pitch values. Caller saves. */
    private ConfigurationSection writeLocation(String path, Location location) {
        var section = plugin.getConfig().createSection(path);
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
        return section;
    }

    /** Null if the section is missing or its world isn't loaded. */
    private static Location readLocation(ConfigurationSection section) {
        if (section == null) return null;
        var world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    // --- round lifecycle ---

    public boolean start() {
        if (state != GameState.IDLE) return false;
        if (game == null) return false;
        if (lobby.size() < game.getMinPlayers()) return false;

        players.addAll(lobby);
        lobby.clear();

        if (!manualArena) {
            var saved = getSavedArenas(game.getId());
            saved.removeIf(a -> a.center() == null);
            if (saved.isEmpty()) {
                arenaCenter = null;
                broadcast("No arena set — playing without boundaries");
            } else {
                var pick = saved.get(random.nextInt(saved.size()));
                arenaCenter = pick.center();
                arenaRadius = pick.radius();
                broadcast("Arena: " + pick.name());
            }
        }

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
