package fr.dpmr.pass;

import fr.dpmr.data.PointsManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Progression saisonnière du Conquer Pass : XP, paliers, récompenses gratuites / premium.
 */
public final class ConquerPassManager {

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final org.bukkit.configuration.file.YamlConfiguration yaml;
    private final File file;

    public ConquerPassManager(JavaPlugin plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.file = new File(plugin.getDataFolder(), "conquer-pass.yml");
        this.yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("conquer-pass.enabled", true);
    }

    public String seasonId() {
        return plugin.getConfig().getString("conquer-pass.season-id", "1");
    }

    public int xpPerTier() {
        return Math.max(1, plugin.getConfig().getInt("conquer-pass.xp-per-tier", 350));
    }

    public int maxTier() {
        int fromConfig = plugin.getConfig().getInt("conquer-pass.max-tier", 14);
        ConfigurationSection tiers = plugin.getConfig().getConfigurationSection("conquer-pass.tiers");
        int maxKey = 0;
        if (tiers != null) {
            for (String k : tiers.getKeys(false)) {
                try {
                    maxKey = Math.max(maxKey, Integer.parseInt(k));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Math.max(1, Math.max(fromConfig, maxKey));
    }

    public int pageSize() {
        return Math.min(7, Math.max(1, plugin.getConfig().getInt("conquer-pass.page-size", 7)));
    }

    public int xpPlayerKill() {
        return Math.max(0, plugin.getConfig().getInt("conquer-pass.xp.player-kill", 40));
    }

    public int xpMobKill() {
        return Math.max(0, plugin.getConfig().getInt("conquer-pass.xp.mob-kill", 4));
    }

    public String premiumPermission() {
        return plugin.getConfig().getString("conquer-pass.premium.permission", "dpmr.conquerpass.premium");
    }

    public int premiumUnlockPoints() {
        return Math.max(0, plugin.getConfig().getInt("conquer-pass.premium.unlock-points", 3000));
    }

    public int freeRewardPoints(int tier) {
        return Math.max(0, plugin.getConfig().getInt("conquer-pass.tiers." + tier + ".free-points", 0));
    }

    public int premiumRewardPoints(int tier) {
        return Math.max(0, plugin.getConfig().getInt("conquer-pass.tiers." + tier + ".premium-points", 0));
    }

    private String basePath(UUID id) {
        return "players." + id;
    }

    public void ensureSeason(Player player) {
        if (!isEnabled()) {
            return;
        }
        UUID id = player.getUniqueId();
        String path = basePath(id);
        String current = seasonId();
        if (current.equals(yaml.getString(path + ".season", ""))) {
            return;
        }
        yaml.set(path + ".season", current);
        yaml.set(path + ".xp", 0);
        yaml.set(path + ".premium-purchased", false);
        yaml.set(path + ".claimed-free", null);
        yaml.set(path + ".claimed-premium", null);
        saveQuiet();
    }

    public int getXp(UUID id) {
        return Math.max(0, yaml.getInt(basePath(id) + ".xp", 0));
    }

    /** Palier le plus élevé déjà atteint (0 = aucun palier débloqué). */
    public int unlockedTierLevel(UUID id) {
        return Math.min(maxTier(), getXp(id) / xpPerTier());
    }

    public boolean tierReached(UUID id, int tier) {
        return tier >= 1 && tier <= maxTier() && unlockedTierLevel(id) >= tier;
    }

    public boolean hasPremiumAccess(Player player) {
        String perm = premiumPermission();
        if (perm != null && !perm.isBlank() && player.hasPermission(perm)) {
            return true;
        }
        return yaml.getBoolean(basePath(player.getUniqueId()) + ".premium-purchased", false);
    }

    public boolean isFreeClaimed(UUID id, int tier) {
        return yaml.getBoolean(basePath(id) + ".claimed-free." + tier, false);
    }

    public boolean isPremiumClaimed(UUID id, int tier) {
        return yaml.getBoolean(basePath(id) + ".claimed-premium." + tier, false);
    }

    public void addXp(Player player, int delta) {
        if (!isEnabled() || delta <= 0) {
            return;
        }
        ensureSeason(player);
        UUID id = player.getUniqueId();
        String path = basePath(id);
        int cap = maxTier() * xpPerTier();
        int next = Math.min(cap, getXp(id) + delta);
        yaml.set(path + ".xp", next);
        saveQuiet();
    }

    /**
     * @return message key for I18n on failure, or null on success
     */
    public String tryBuyPremium(Player player) {
        if (!isEnabled()) {
            return "conquerpass.disabled";
        }
        ensureSeason(player);
        if (hasPremiumAccess(player)) {
            return "conquerpass.premium_already";
        }
        int cost = premiumUnlockPoints();
        if (cost <= 0) {
            return "conquerpass.premium_no_points_price";
        }
        UUID id = player.getUniqueId();
        if (pointsManager.getPoints(id) < cost) {
            return "conquerpass.premium_not_enough_points";
        }
        pointsManager.addPoints(id, -cost);
        pointsManager.saveAsync();
        yaml.set(basePath(id) + ".premium-purchased", true);
        saveQuiet();
        return null;
    }

    /**
     * @return message key on failure, or null on success
     */
    public String tryClaimFree(Player player, int tier) {
        if (!isEnabled()) {
            return "conquerpass.disabled";
        }
        ensureSeason(player);
        UUID id = player.getUniqueId();
        if (tier < 1 || tier > maxTier()) {
            return "conquerpass.invalid_tier";
        }
        if (!tierReached(id, tier)) {
            return "conquerpass.not_unlocked";
        }
        if (isFreeClaimed(id, tier)) {
            return "conquerpass.already_claimed";
        }
        int reward = freeRewardPoints(tier);
        yaml.set(basePath(id) + ".claimed-free." + tier, true);
        saveQuiet();
        if (reward > 0) {
            pointsManager.addPoints(id, reward);
            pointsManager.saveAsync();
        }
        return null;
    }

    public String tryClaimPremium(Player player, int tier) {
        if (!isEnabled()) {
            return "conquerpass.disabled";
        }
        ensureSeason(player);
        if (!hasPremiumAccess(player)) {
            return "conquerpass.need_premium";
        }
        UUID id = player.getUniqueId();
        if (tier < 1 || tier > maxTier()) {
            return "conquerpass.invalid_tier";
        }
        if (!tierReached(id, tier)) {
            return "conquerpass.not_unlocked";
        }
        if (isPremiumClaimed(id, tier)) {
            return "conquerpass.already_claimed";
        }
        int reward = premiumRewardPoints(tier);
        yaml.set(basePath(id) + ".claimed-premium." + tier, true);
        saveQuiet();
        if (reward > 0) {
            pointsManager.addPoints(id, reward);
            pointsManager.saveAsync();
        }
        return null;
    }

    public int maxPage() {
        int ps = pageSize();
        int mt = maxTier();
        return Math.max(0, (mt + ps - 1) / ps - 1);
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save conquer-pass.yml: " + e.getMessage());
        }
    }

    private void saveQuiet() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save conquer-pass.yml: " + e.getMessage());
        }
    }
}
