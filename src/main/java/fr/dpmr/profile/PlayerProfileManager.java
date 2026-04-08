package fr.dpmr.profile;

import fr.dpmr.data.PointsManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProfileManager implements Listener {

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, EnumMap<ProfileUpgradeType, Integer>> levels = new HashMap<>();
    private BukkitTask regenTask;

    public PlayerProfileManager(JavaPlugin plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.file = new File(plugin.getDataFolder(), "profiles.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        levels.clear();
        if (!yaml.isConfigurationSection("players")) {
            return;
        }
        for (String raw : yaml.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(raw);
                EnumMap<ProfileUpgradeType, Integer> map = new EnumMap<>(ProfileUpgradeType.class);
                for (ProfileUpgradeType t : ProfileUpgradeType.values()) {
                    int lv = yaml.getInt("players." + raw + "." + t.name(), 0);
                    map.put(t, Math.max(0, Math.min(lv, t.maxLevel())));
                }
                levels.put(uuid, map);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("UUID invalide dans profiles.yml: " + raw);
            }
        }
    }

    public void save() {
        yaml.set("players", null);
        for (Map.Entry<UUID, EnumMap<ProfileUpgradeType, Integer>> e : levels.entrySet()) {
            String base = "players." + e.getKey() + ".";
            for (ProfileUpgradeType t : ProfileUpgradeType.values()) {
                yaml.set(base + t.name(), e.getValue().getOrDefault(t, 0));
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder profiles.yml: " + e.getMessage());
        }
    }

    private EnumMap<ProfileUpgradeType, Integer> profile(UUID uuid) {
        return levels.computeIfAbsent(uuid, k -> new EnumMap<>(ProfileUpgradeType.class));
    }

    public int level(UUID uuid, ProfileUpgradeType type) {
        return profile(uuid).getOrDefault(type, 0);
    }

    public boolean upgrade(Player player, ProfileUpgradeType type) {
        int lv = level(player.getUniqueId(), type);
        if (lv >= type.maxLevel()) {
            return false;
        }
        int cost = type.nextCost(lv);
        int points = pointsManager.getPoints(player.getUniqueId());
        if (points < cost) {
            return false;
        }
        pointsManager.addPoints(player.getUniqueId(), -cost);
        profile(player.getUniqueId()).put(type, lv + 1);
        pointsManager.saveAsync();
        save();
        applyAllPassives(player);
        return true;
    }

    public int totalSpent(UUID uuid) {
        int sum = 0;
        for (ProfileUpgradeType t : ProfileUpgradeType.values()) {
            int lv = 0;
            for (int i = 0; i < level(uuid, t); i++) {
                lv++;
                sum += t.nextCost(i);
            }
        }
        return sum;
    }

    public double damageMultiplier(Player player) {
        int lv = level(player.getUniqueId(), ProfileUpgradeType.DAMAGE);
        return 1.0 + lv * 0.04;
    }

    public double pointsMultiplier(Player player) {
        int lv = level(player.getUniqueId(), ProfileUpgradeType.ECONOMY);
        return 1.0 + lv * 0.06;
    }

    public double pointsMultiplier(UUID uuid) {
        int lv = level(uuid, ProfileUpgradeType.ECONOMY);
        return 1.0 + lv * 0.06;
    }

    public void start() {
        if (regenTask != null) {
            return;
        }
        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                int lv = level(player.getUniqueId(), ProfileUpgradeType.REGEN);
                if (lv <= 0 || player.isDead()) {
                    continue;
                }
                double max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                double heal = 0.08 * lv;
                player.setHealth(Math.min(max, player.getHealth() + heal));
            }
        }, 40L, 40L);
    }

    public void stop() {
        if (regenTask != null) {
            regenTask.cancel();
            regenTask = null;
        }
    }

    public void applyAllPassives(Player player) {
        int vit = level(player.getUniqueId(), ProfileUpgradeType.VITALITY);
        double maxHealth = Math.min(40.0, 20.0 + vit * 2.0);
        var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(maxHealth);
            if (player.getHealth() > maxHealth) {
                player.setHealth(maxHealth);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyAllPassives(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> applyAllPassives(event.getPlayer()));
    }

    @EventHandler
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        int lv = level(player.getUniqueId(), ProfileUpgradeType.ARMOR);
        if (lv <= 0) {
            return;
        }
        double factor = Math.max(0.72, 1.0 - lv * 0.05);
        event.setDamage(event.getDamage() * factor);
    }

    @EventHandler
    public void onDamageDeal(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        event.setDamage(event.getDamage() * damageMultiplier(player));
    }
}

