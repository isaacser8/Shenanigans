package com.seryizhe.shenanigans.game;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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

    private BukkitTask timerTask;
    private int secondsLeft;

    public GameManager(Plugin plugin) {
        this.plugin = plugin;
    }

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

    public boolean join(Player player) {
        if (state != GameState.IDLE) return false;
        return lobby.add(player.getUniqueId());
    }

    public boolean start() {
        if (state != GameState.IDLE) return false;
        if (lobby.size() < 1) return false;

        // Split into roles: roughly one seeker per four players, at least one.
        var players = new ArrayList<>(lobby);
        Collections.shuffle(players);

        int seekerCount = Math.max(1, players.size() / 4);
        seekers.addAll(players.subList(0, seekerCount));
        hiders.addAll(players.subList(seekerCount, players.size()));
        lobby.clear();

        state = GameState.HIDING;

        // Hiders get a random disguise and are told to run.
        for (UUID id : hiders) {
            var p = Bukkit.getPlayer(id);
            if (p == null) continue;

            var type = DISGUISES.get(random.nextInt(DISGUISES.size()));
            DisguiseAPI.disguiseToAll(p, new MobDisguise(type));
            send(p, "<green>You are a HIDER, disguised as a " + type.name().toLowerCase()
                    + ". Run and hide!");
        }

        // Seekers are blinded and rooted for the hiding phase.
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

    private void startTimer(int seconds, Runnable onFinish) {
        cancelTimer();
        secondsLeft = seconds;
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            secondsLeft--;
            if (secondsLeft <= 0) {
                cancelTimer();
                onFinish.run();
            } else if (secondsLeft % 15 == 0 || secondsLeft <= 5) {
                broadcast(secondsLeft + "s");
            }
        }, 20L, 20L);
    }

    private void cancelTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
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
