package com.seryizhe.shenanigans.game;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class GameManager {

    private static final int HIDE_SECONDS = 30;
    private static final int SEEK_SECONDS = 180;

    /** Mobs hiders can be disguised as. Picked at random. */
    private static final List<DisguiseType> DISGUISES = List.of(
            DisguiseType.SHEEP,
            DisguiseType.COW,
            DisguiseType.PIG,
            DisguiseType.CHICKEN,
            DisguiseType.SPIDER,
            DisguiseType.WOLF,
            DisguiseType.FOX,
            DisguiseType.RABBIT
    );

    private final Plugin plugin;
    private final Random random = new Random();

    private GameState state = GameState.IDLE;
    private final Set<UUID> lobby = new HashSet<>();
    private final Set<UUID> hiders = new HashSet<>();
    private final Set<UUID> seekers = new HashSet<>();

    private Location arenaCenter;
    private int arenaRadius = 50;

    private final List<Location> candidates = new ArrayList<>();

    private BukkitTask timerTask;
    private int secondsLeft;

    public GameManager(Plugin plugin) {
        this.plugin = plugin;
    }

    // --- state queries ---

    public GameState getState() {
        return state;
    }

    public boolean isHider(Player player) {
        return hiders.contains(player.getUniqueId());
    }

    public boolean isSeeker(Player player) {
        return seekers.contains(player.getUniqueId());
    }

    public boolean isPlaying(Player player) {
        return isHider(player) || isSeeker(player);
    }

    // --- arena ---

    public void setArena(Location center, int radius) {
        this.arenaCenter = center;
        this.arenaRadius = radius;
    }

    public Location getArenaCenter() {
        return arenaCenter;
    }

    /** True if the player has wandered outside the arena. */
    public boolean isOutside(Player player) {
        if (arenaCenter == null) return false;
        if (!player.getWorld().equals(arenaCenter.getWorld())) return true;
        return player.getLocation().distance(arenaCenter) > arenaRadius;
    }

    // --- arena candidates ---

    /**
     * Finds candidate arenas near the player and stores them for previewing.
     */
    public int findCandidates(Player player) {
        candidates.clear();

        var world = player.getWorld();
        var origin = player.getLocation();

        // Nearest village, if any — buildings make the best arenas.
        var villageTypes = List.of(
                Structure.VILLAGE_PLAINS,
                Structure.VILLAGE_TAIGA,
                Structure.VILLAGE_SAVANNA,
                Structure.VILLAGE_DESERT,
                Structure.VILLAGE_SNOWY
        );

        for (var type : villageTypes) {
            var result = world.locateNearestStructure(origin, type, 100, false);
            if (result != null && result.getLocation().distance(origin) < 2000) {
                candidates.add(world.getHighestBlockAt(result.getLocation()).getLocation().add(0, 1, 0));
            }
        }

        // Wooded biomes at increasing distances and four compass directions.
        for (int radius = 100; radius <= 500; radius += 200) {
            for (int angle = 0; angle < 360; angle += 90) {
                int x = origin.getBlockX() + (int) (radius * Math.cos(Math.toRadians(angle)));
                int z = origin.getBlockZ() + (int) (radius * Math.sin(Math.toRadians(angle)));

                if (isWooded(world.getBiome(x, 64, z))) {
                    candidates.add(world.getHighestBlockAt(x, z).getLocation().add(0, 1, 0));
                }
            }
        }

        return candidates.size();
    }

    private boolean isWooded(Biome biome) {
        String name = biome.getKey().getKey();
        return name.contains("forest")
                || name.contains("taiga")
                || name.contains("jungle")
                || name.contains("grove");
    }

    public int getCandidateCount() {
        return candidates.size();
    }

    /** Teleports the player to candidate number {@code index} (1-based). */
    public Location visitCandidate(Player player, int index) {
        if (index < 1 || index > candidates.size()) return null;

        var location = candidates.get(index - 1);
        player.teleport(location);
        return location;
    }

    public List<Location> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    // --- round lifecycle ---

    public boolean join(Player player) {
        if (state != GameState.IDLE) return false;
        return lobby.add(player.getUniqueId());
    }

    public boolean start() {
        if (state != GameState.IDLE) return false;
        if (lobby.size() < 2) return false;

        var players = new ArrayList<>(lobby);
        Collections.shuffle(players);

        if (arenaCenter != null) {
            for (UUID id : players) {
                var p = Bukkit.getPlayer(id);
                if (p != null) p.teleport(arenaCenter);
            }
        }

        int seekerCount = Math.max(1, players.size() / 4);
        seekers.addAll(players.subList(0, seekerCount));
        hiders.addAll(players.subList(seekerCount, players.size()));
        lobby.clear();

        state = GameState.HIDING;

        for (UUID id : hiders) {
            var p = Bukkit.getPlayer(id);
            if (p == null) continue;

            var type = DISGUISES.get(random.nextInt(DISGUISES.size()));
            DisguiseAPI.disguiseToAll(p, new MobDisguise(type));
            send(p, "<green>You are a HIDER, disguised as a " + type.name().toLowerCase()
                    + ". Run and hide!");
        }

        for (UUID id : seekers) {
            var p = Bukkit.getPlayer(id);
            if (p == null) continue;

            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, HIDE_SECONDS * 20, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, HIDE_SECONDS * 20, 10));
            send(p, "<red>You are a SEEKER. Wait " + HIDE_SECONDS + " seconds...");
        }

        startTimer(HIDE_SECONDS, this::beginSeeking);
        return true;
    }

    private void beginSeeking() {
        state = GameState.SEEKING;

        for (UUID id : seekers) {
            var p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }

        broadcast("Seekers are out!");
        startTimer(SEEK_SECONDS, () -> end("Hiders win — time ran out!"));
    }

    public void caught(Player hider, Player seeker) {
        if (state != GameState.SEEKING || !isHider(hider)) return;

        hiders.remove(hider.getUniqueId());
        seekers.add(hider.getUniqueId());
        undisguise(hider);

        broadcast(hider.getName() + " was found by " + seeker.getName() + "!");

        if (hiders.isEmpty()) {
            end("Seekers win — everyone was found!");
        }
    }

    public void end(String reason) {
        if (state == GameState.IDLE) return;
        state = GameState.ENDING;
        cancelTimer();

        broadcast(reason);
        cleanup();

        state = GameState.IDLE;
    }

    /** Called on quit, kick, and plugin disable. Must leave no one disguised. */
    public void remove(Player player) {
        UUID id = player.getUniqueId();
        lobby.remove(id);
        boolean wasPlaying = hiders.remove(id) | seekers.remove(id);

        if (wasPlaying) {
            undisguise(player);
        }

        if (wasPlaying && state == GameState.SEEKING && hiders.isEmpty()) {
            end("Seekers win — the last hider left!");
        }
    }

    private void cleanup() {
        for (UUID id : hiders) {
            var p = Bukkit.getPlayer(id);
            if (p != null) undisguise(p);
        }
        for (UUID id : seekers) {
            var p = Bukkit.getPlayer(id);
            if (p == null) continue;
            undisguise(p);
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }

        lobby.clear();
        hiders.clear();
        seekers.clear();
    }

    private void undisguise(Player player) {
        if (DisguiseAPI.isDisguised(player)) {
            DisguiseAPI.undisguiseToAll(player);
        }
    }

    // --- timer and display ---

    private void startTimer(int seconds, Runnable onFinish) {
        cancelTimer();
        secondsLeft = seconds;
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            secondsLeft--;
            updateActionBar();
            if (secondsLeft <= 0) {
                cancelTimer();
                onFinish.run();
            }
        }, 20L, 20L);
    }

    private void cancelTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void updateActionBar() {
        String text;
        if (state == GameState.HIDING) {
            text = "<green>Hiding — " + formatTime(secondsLeft);
        } else if (state == GameState.SEEKING) {
            text = "<red>" + hiders.size() + " hiding — " + formatTime(secondsLeft);
        } else {
            return;
        }

        var component = MiniMessage.miniMessage().deserialize(text);
        for (UUID id : hiders) {
            var p = Bukkit.getPlayer(id);
            if (p != null) p.sendActionBar(component);
        }
        for (UUID id : seekers) {
            var p = Bukkit.getPlayer(id);
            if (p != null) p.sendActionBar(component);
        }
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private void broadcast(String message) {
        for (UUID id : hiders) {
            var p = Bukkit.getPlayer(id);
            if (p != null) send(p, "<yellow>" + message);
        }
        for (UUID id : seekers) {
            var p = Bukkit.getPlayer(id);
            if (p != null) send(p, "<yellow>" + message);
        }
    }

    private void send(Player player, String miniMessage) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(miniMessage));
    }
}