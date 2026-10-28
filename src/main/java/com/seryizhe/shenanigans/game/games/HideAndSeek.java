package com.seryizhe.shenanigans.game.games;

import com.seryizhe.shenanigans.game.GameHost;
import com.seryizhe.shenanigans.game.Minigame;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class HideAndSeek implements Minigame {

    private static final int HIDE_SECONDS = 30;
    private static final int SEEK_SECONDS = 180;

    private static final List<DisguiseType> DISGUISES = List.of(
            DisguiseType.SHEEP, DisguiseType.COW, DisguiseType.PIG,
            DisguiseType.CHICKEN, DisguiseType.SPIDER, DisguiseType.WOLF,
            DisguiseType.FOX, DisguiseType.RABBIT
    );

    private final Random random = new Random();

    private final Set<UUID> hiders = new HashSet<>();
    private final Set<UUID> seekers = new HashSet<>();

    private GameHost host;
    private boolean seeking;
    private boolean over;

    @Override
    public String getId() {
        return "hideandseek";
    }

    @Override
    public String getName() {
        return "Hide and Seek";
    }

    @Override
    public int getMinPlayers() {
        return 2;
    }

    @Override
    public void onStart(GameHost host, List<Player> players) {
        this.host = host;
        this.over = false;
        this.seeking = false;
        hiders.clear();
        seekers.clear();

        var shuffled = new java.util.ArrayList<>(players);
        Collections.shuffle(shuffled);

        int seekerCount = Math.max(1, shuffled.size() / 4);
        for (int i = 0; i < shuffled.size(); i++) {
            var p = shuffled.get(i);
            if (i < seekerCount) {
                seekers.add(p.getUniqueId());
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, HIDE_SECONDS * 20, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, HIDE_SECONDS * 20, 10));
                host.send(p, "<red>You are a SEEKER. Wait " + HIDE_SECONDS + " seconds...");
            } else {
                hiders.add(p.getUniqueId());
                var type = DISGUISES.get(random.nextInt(DISGUISES.size()));
                DisguiseAPI.disguiseToAll(p, new MobDisguise(type));
                host.send(p, "<green>You are a HIDER, disguised as a "
                        + type.name().toLowerCase() + ". Run and hide!");
            }
        }

        // Release the seekers after the hiding phase.
        Bukkit.getScheduler().runTaskLater(host.getPlugin(), this::beginSeeking, HIDE_SECONDS * 20L);
    }

    private void beginSeeking() {
        if (over) return;
        seeking = true;

        for (UUID id : seekers) {
            var p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }

        host.broadcast("Seekers are out!");
    }

    /** Called by the listener when a seeker tags a hider. */
    public void caught(Player hider, Player seeker) {
        if (!seeking || !hiders.contains(hider.getUniqueId())) return;

        hiders.remove(hider.getUniqueId());
        seekers.add(hider.getUniqueId());
        undisguise(hider);

        host.broadcast(hider.getName() + " was found by " + seeker.getName() + "!");

        if (hiders.isEmpty()) {
            over = true;
        }
    }

    @Override
    public void onEnd(GameHost host) {
        over = true;
        for (var p : host.getPlayers()) {
            undisguise(p);
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        hiders.clear();
        seekers.clear();
    }

    @Override
    public boolean isOver() {
        return over
                || (seeking && hiders.isEmpty())
                || (host != null && host.getElapsedSeconds() > HIDE_SECONDS + SEEK_SECONDS);
    }

    @Override
    public String getStatusText() {
        if (!seeking) {
            return "<green>Hiding — " + (HIDE_SECONDS - (host == null ? 0 : host.getElapsedSeconds())) + "s";
        }
        return "<red>" + hiders.size() + " hiding";
    }

    @Override
    public boolean isGoodArena(Location location) {
        // Also check 8 blocks out each way, for spots whose centre falls just outside a village or wood.
        return hasCover(location)
                || hasCover(location.clone().add(0, 0, -8))
                || hasCover(location.clone().add(0, 0, 8))
                || hasCover(location.clone().add(8, 0, 0))
                || hasCover(location.clone().add(-8, 0, 0));
    }

    /** Villages (buildings) and woods (trees) both give cover. */
    private boolean hasCover(Location location) {
        var structures = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        for (var structure : location.getChunk().getStructures()) {
            if (structures.getKeyOrThrow(structure.getStructure()).getKey().startsWith("village")) return true;
        }
        String biome = location.getBlock().getBiome().getKey().getKey();
        return biome.contains("forest") || biome.contains("taiga")
                || biome.contains("jungle") || biome.contains("grove");
    }

    public boolean isHider(Player p) {
        return hiders.contains(p.getUniqueId());
    }

    public boolean isSeeker(Player p) {
        return seekers.contains(p.getUniqueId());
    }

    private void undisguise(Player player) {
        if (DisguiseAPI.isDisguised(player)) {
            DisguiseAPI.undisguiseToAll(player);
        }
    }
}
