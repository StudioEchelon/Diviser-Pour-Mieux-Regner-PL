package fr.dpmr.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class PointsManager {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private Function<UUID, Double> gainMultiplierProvider = uuid -> 1.0;
    /** Extra multiplier for PvP / scripted kill rewards only (e.g. yellow power-up block). */
    private Function<UUID, Double> killRewardMultiplierProvider = uuid -> 1.0;

    private final List<BiConsumer<UUID, Integer>> afterKillTotalListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, Integer>> afterPointsTotalListeners = new CopyOnWriteArrayList<>();

    public PointsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "points.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        points.clear();
        kills.clear();
        if (yaml.isConfigurationSection("players")) {
            for (String key : Objects.requireNonNull(yaml.getConfigurationSection("players")).getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int value = yaml.getInt("players." + key, 0);
                    points.put(uuid, value);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("UUID invalide dans points.yml: " + key);
                }
            }
        }
        if (yaml.isConfigurationSection("kills")) {
            for (String key : Objects.requireNonNull(yaml.getConfigurationSection("kills")).getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int value = yaml.getInt("kills." + key, 0);
                    kills.put(uuid, value);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("UUID invalide dans kills (points.yml): " + key);
                }
            }
        }
    }

    public void save() {
        prepareYaml();
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder points.yml: " + e.getMessage());
        }
    }

    public void saveAsync() {
        prepareYaml();
        String data = yaml.saveToString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.nio.file.Files.writeString(file.toPath(), data, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de sauvegarder points.yml (async): " + e.getMessage());
            }
        });
    }

    private void prepareYaml() {
        yaml.set("players", null);
        for (Map.Entry<UUID, Integer> entry : points.entrySet()) {
            yaml.set("players." + entry.getKey(), entry.getValue());
        }
        yaml.set("kills", null);
        for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
            yaml.set("kills." + entry.getKey(), entry.getValue());
        }
    }

    public int getPoints(UUID uuid) {
        return points.getOrDefault(uuid, 0);
    }

    public void addPoints(UUID uuid, int amount) {
        int adjusted = amount;
        if (amount > 0) {
            double mult = Math.max(1.0, gainMultiplierProvider.apply(uuid));
            adjusted = (int) Math.round(amount * mult);
        }
        points.put(uuid, Math.max(0, getPoints(uuid) + adjusted));
        fireAfterPoints(uuid);
    }

    /**
     * Kill-style rewards: applies profile economy multiplier and {@link #setKillRewardMultiplierProvider} (power-up).
     *
     * @return points actually added (after multipliers)
     */
    public int addKillRewardPoints(UUID uuid, int baseAmount) {
        if (baseAmount <= 0) {
            return 0;
        }
        int before = getPoints(uuid);
        double mult = Math.max(1.0, gainMultiplierProvider.apply(uuid))
                * Math.max(1.0, killRewardMultiplierProvider.apply(uuid));
        int adjusted = (int) Math.round(baseAmount * mult);
        points.put(uuid, Math.max(0, before + adjusted));
        fireAfterPoints(uuid);
        return adjusted;
    }

    public int getKills(UUID uuid) {
        return kills.getOrDefault(uuid, 0);
    }

    public void addKill(UUID uuid) {
        int total = getKills(uuid) + 1;
        kills.put(uuid, total);
        for (BiConsumer<UUID, Integer> listener : afterKillTotalListeners) {
            listener.accept(uuid, total);
        }
    }

    /** (uuid, newKillTotal) after each kill increment. */
    public void addAfterKillTotalListener(BiConsumer<UUID, Integer> listener) {
        if (listener != null) {
            afterKillTotalListeners.add(listener);
        }
    }

    /** (uuid, newPointsTotal) after points change via {@link #addPoints} or {@link #addKillRewardPoints}. */
    public void addAfterPointsTotalListener(BiConsumer<UUID, Integer> listener) {
        if (listener != null) {
            afterPointsTotalListeners.add(listener);
        }
    }

    private void fireAfterPoints(UUID uuid) {
        int total = getPoints(uuid);
        for (BiConsumer<UUID, Integer> listener : afterPointsTotalListeners) {
            listener.accept(uuid, total);
        }
    }

    public List<Map.Entry<UUID, Integer>> getTop(int limit) {
        return points.entrySet().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getValue(), a.getValue());
                    return c != 0 ? c : a.getKey().compareTo(b.getKey());
                })
                .limit(limit)
                .toList();
    }

    public List<Map.Entry<UUID, Integer>> getTopKills(int limit) {
        return kills.entrySet().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getValue(), a.getValue());
                    return c != 0 ? c : a.getKey().compareTo(b.getKey());
                })
                .limit(limit)
                .toList();
    }

    /** Fixe le solde (admin / reset). Declenche les listeners. */
    public void setPoints(UUID uuid, int value) {
        points.put(uuid, Math.max(0, value));
        fireAfterPoints(uuid);
    }

    /**
     * Retire des points si le solde suffit (achats, commandes admin).
     *
     * @return false si pas assez de points
     */
    public boolean tryRemovePoints(UUID uuid, int amount) {
        if (amount <= 0) {
            return true;
        }
        int cur = getPoints(uuid);
        if (cur < amount) {
            return false;
        }
        points.put(uuid, cur - amount);
        fireAfterPoints(uuid);
        return true;
    }

    public String resolveName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() != null ? player.getName() : uuid.toString().substring(0, 8);
    }

    public void setGainMultiplierProvider(Function<UUID, Double> gainMultiplierProvider) {
        this.gainMultiplierProvider = gainMultiplierProvider != null ? gainMultiplierProvider : (uuid -> 1.0);
    }

    public void setKillRewardMultiplierProvider(Function<UUID, Double> killRewardMultiplierProvider) {
        this.killRewardMultiplierProvider = killRewardMultiplierProvider != null ? killRewardMultiplierProvider : (uuid -> 1.0);
    }
}
