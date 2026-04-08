package fr.dpmr.trophy;

import fr.dpmr.data.ClanManager;
import fr.dpmr.data.PointsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists unlocked trophies and evaluates milestones when kills/points change.
 */
public class TrophyManager implements Listener {

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final ClanManager clanManager;
    private final File file;
    private final Map<UUID, Set<String>> unlocked = new ConcurrentHashMap<>();

    public TrophyManager(JavaPlugin plugin, PointsManager pointsManager, ClanManager clanManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.clanManager = clanManager;
        this.file = new File(plugin.getDataFolder(), "trophies.yml");
        load();
        pointsManager.addAfterKillTotalListener(this::onKillTotal);
        pointsManager.addAfterPointsTotalListener(this::onPointsTotal);
    }

    private void load() {
        unlocked.clear();
        if (!file.exists()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        if (!yaml.isConfigurationSection("players")) {
            return;
        }
        var sec = yaml.getConfigurationSection("players");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                List<String> list = sec.getStringList(key);
                unlocked.put(uuid, ConcurrentHashMap.newKeySet());
                unlocked.get(uuid).addAll(list);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in trophies.yml: " + key);
            }
        }
    }

    public void save() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> e : unlocked.entrySet()) {
            yaml.set("players." + e.getKey().toString(), new ArrayList<>(e.getValue()));
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save trophies.yml: " + ex.getMessage());
        }
    }

    public int unlockedCount(UUID uuid) {
        return unlocked.getOrDefault(uuid, Collections.emptySet()).size();
    }

    public int totalDefined() {
        return Trophy.values().length;
    }

    public boolean has(UUID uuid, Trophy trophy) {
        return unlocked.getOrDefault(uuid, Collections.emptySet()).contains(trophy.storageId());
    }

    public Set<String> unlockedIds(UUID uuid) {
        return Collections.unmodifiableSet(new HashSet<>(unlocked.getOrDefault(uuid, Collections.emptySet())));
    }

    private Trophy.UnlockContext context(UUID uuid) {
        return Trophy.UnlockContext.forPlayer(
                uuid,
                pointsManager::getKills,
                pointsManager::getPoints,
                clanManager::getPlayerClan
        );
    }

    private void onKillTotal(UUID uuid, int newKillTotal) {
        tryUnlock(uuid, context(uuid));
    }

    private void onPointsTotal(UUID uuid, int newPointsTotal) {
        tryUnlock(uuid, context(uuid));
    }

    private void tryUnlock(UUID uuid, Trophy.UnlockContext ctx) {
        Set<String> set = unlocked.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet());
        for (Trophy trophy : Trophy.values()) {
            if (set.contains(trophy.storageId())) {
                continue;
            }
            if (!trophy.qualifies(ctx)) {
                continue;
            }
            set.add(trophy.storageId());
            notifyUnlock(uuid, trophy);
        }
    }

    private void notifyUnlock(UUID uuid, Trophy trophy) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        Title title = Title.title(
                Component.text(trophy.title(), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(trophy.subtitle(), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(400))
        );
        player.showTitle(title);
        player.sendMessage(
                Component.text("Trophy unlocked: ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text(trophy.title(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                        .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(trophy.subtitle(), NamedTextColor.GRAY))
        );
    }

    /** Scan all trophies (e.g. after migration or manual YAML edit). */
    public void recheckPlayer(Player player) {
        tryUnlock(player.getUniqueId(), context(player.getUniqueId()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // Evite d'empiler logique + titres sur le meme tick que spawn/chunks (watchdog sous charge).
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                recheckPlayer(p);
            }
        }, 3L);
    }
}
