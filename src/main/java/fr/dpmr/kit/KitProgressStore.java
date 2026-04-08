package fr.dpmr.kit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent playtime (minutes) and per-kit claim timestamps for {@link EvolvingKitManager}.
 */
public final class KitProgressStore {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public KitProgressStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kit-progress.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        load();
    }

    static final class Entry {
        int minutes;
        long lastPlayerKitMs;
        long lastGradeKitMs;
    }

    private Entry entry(UUID uuid) {
        return entries.computeIfAbsent(uuid, k -> new Entry());
    }

    public int getMinutes(UUID uuid) {
        return entry(uuid).minutes;
    }

    public void addMinutes(UUID uuid, int delta) {
        if (delta <= 0) {
            return;
        }
        Entry e = entry(uuid);
        e.minutes = Math.min(Integer.MAX_VALUE - delta, e.minutes) + delta;
    }

    public long getLastPlayerKitMs(UUID uuid) {
        return entry(uuid).lastPlayerKitMs;
    }

    public void setLastPlayerKitMs(UUID uuid, long ms) {
        entry(uuid).lastPlayerKitMs = ms;
    }

    public long getLastGradeKitMs(UUID uuid) {
        return entry(uuid).lastGradeKitMs;
    }

    public void setLastGradeKitMs(UUID uuid, long ms) {
        entry(uuid).lastGradeKitMs = ms;
    }

    private void load() {
        entries.clear();
        yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.isConfigurationSection("players")) {
            return;
        }
        var sec = yaml.getConfigurationSection("players");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Entry e = new Entry();
                e.minutes = Math.max(0, yaml.getInt("players." + key + ".minutes", 0));
                e.lastPlayerKitMs = yaml.getLong("players." + key + ".last-player-kit", 0L);
                e.lastGradeKitMs = yaml.getLong("players." + key + ".last-grade-kit", 0L);
                entries.put(id, e);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid UUID in kit-progress.yml: " + key);
            }
        }
    }

    public void save() {
        yaml.set("players", null);
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            String base = "players." + e.getKey();
            Entry v = e.getValue();
            yaml.set(base + ".minutes", v.minutes);
            yaml.set(base + ".last-player-kit", v.lastPlayerKitMs);
            yaml.set(base + ".last-grade-kit", v.lastGradeKitMs);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save kit-progress.yml: " + ex.getMessage());
        }
    }
}
