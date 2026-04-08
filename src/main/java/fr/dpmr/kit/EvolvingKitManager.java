package fr.dpmr.kit;

import fr.dpmr.data.PointsManager;
import fr.dpmr.game.BandageManager;
import fr.dpmr.game.DpmrConsumable;
import fr.dpmr.game.WeaponManager;
import fr.dpmr.game.WeaponProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Playtime-based {@link KitKind#PLAYER} kit and permission-based {@link KitKind#GRADE} kit — English player feedback.
 */
public final class EvolvingKitManager {

    public enum KitKind {
        PLAYER,
        GRADE
    }

    private final JavaPlugin plugin;
    private final KitProgressStore store;
    private final BandageManager bandageManager;
    private final WeaponManager weaponManager;
    private final PointsManager pointsManager;

    private boolean enabled;
    private long playerCooldownMs;
    private long gradeCooldownMs;
    private int trackIntervalTicks;
    private final List<PlayerTier> playerTiers = new ArrayList<>();
    private final List<GradeRank> gradeRanks = new ArrayList<>();
    private BukkitTask trackTask;

    public EvolvingKitManager(JavaPlugin plugin, KitProgressStore store, BandageManager bandageManager,
                              WeaponManager weaponManager, PointsManager pointsManager) {
        this.plugin = plugin;
        this.store = store;
        this.bandageManager = bandageManager;
        this.weaponManager = weaponManager;
        this.pointsManager = pointsManager;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("evolving-kits");
        enabled = root != null && root.getBoolean("enabled", true);
        playerCooldownMs = Math.max(0L, (long) (root != null ? root.getDouble("player-kit.cooldown-hours", 6) : 6) * 3600_000L);
        gradeCooldownMs = Math.max(0L, (long) (root != null ? root.getDouble("grade-kit.cooldown-hours", 12) : 12) * 3600_000L);
        int sec = root != null ? root.getInt("track-interval-seconds", 60) : 60;
        trackIntervalTicks = Math.max(20, sec * 20);

        playerTiers.clear();
        if (root != null) {
            loadPlayerTiers(root);
            loadGradeRanks(root);
        }
        playerTiers.sort(Comparator.comparingInt(t -> t.minMinutes));
    }

    private void loadPlayerTiers(ConfigurationSection root) {
        List<?> raw = root.getList("player-kit.tiers");
        if (raw == null) {
            return;
        }
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> map)) {
                continue;
            }
            int min = num(map.get("min-minutes"), 0);
            String label = str(map.get("label"), "Tier");
            List<KitReward> rewards = parseRewards(map.get("items"));
            if (!rewards.isEmpty()) {
                playerTiers.add(new PlayerTier(min, label, rewards));
            }
        }
    }

    private void loadGradeRanks(ConfigurationSection root) {
        List<?> raw = root.getList("grade-kit.ranks");
        if (raw == null) {
            return;
        }
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> map)) {
                continue;
            }
            boolean fallback = bool(map.get("fallback"), false);
            String permission = str(map.get("permission"), "");
            String label = str(map.get("label"), "Rank");
            List<KitReward> rewards = parseRewards(map.get("items"));
            if (!rewards.isEmpty()) {
                gradeRanks.add(new GradeRank(permission, fallback, label, rewards));
            }
        }
    }

    private List<KitReward> parseRewards(Object listObj) {
        List<KitReward> out = new ArrayList<>();
        if (!(listObj instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> map)) {
                continue;
            }
            String type = str(map.get("type"), "").toLowerCase(Locale.ROOT);
            int amount = Math.max(1, num(map.get("amount"), 1));
            switch (type) {
                case "consumable" -> {
                    String id = str(map.get("id"), "");
                    DpmrConsumable c = DpmrConsumable.fromConfigKey(id);
                    if (c == null) {
                        try {
                            c = DpmrConsumable.valueOf(id.toUpperCase(Locale.ROOT).replace('-', '_'));
                        } catch (IllegalArgumentException ignored) {
                            c = null;
                        }
                    }
                    if (c != null) {
                        out.add(new KitReward.ConsumableReward(c, amount));
                    } else {
                        plugin.getLogger().warning("[evolving-kits] Unknown consumable id: " + id);
                    }
                }
                case "weapon" -> {
                    String id = str(map.get("id"), "").trim();
                    if (!id.isEmpty()) {
                        try {
                            WeaponProfile.valueOf(id);
                            out.add(new KitReward.WeaponReward(id, amount));
                        } catch (IllegalArgumentException ex) {
                            plugin.getLogger().warning("[evolving-kits] Unknown weapon id: " + id);
                        }
                    }
                }
                case "points" -> out.add(new KitReward.PointsReward(amount));
                case "material" -> {
                    String matName = str(map.get("material"), "AIR").toUpperCase(Locale.ROOT);
                    try {
                        Material m = Material.valueOf(matName);
                        if (!m.isAir()) {
                            out.add(new KitReward.MaterialReward(m, amount));
                        }
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("[evolving-kits] Unknown material: " + matName);
                    }
                }
                default -> plugin.getLogger().warning("[evolving-kits] Unknown item type: " + type);
            }
        }
        return out;
    }

    private static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static int num(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public void start() {
        if (trackTask != null) {
            trackTask.cancel();
            trackTask = null;
        }
        if (!enabled) {
            return;
        }
        trackTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled) {
                return;
            }
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.hasMetadata("NPC")) {
                    continue;
                }
                store.addMinutes(p.getUniqueId(), 1);
            }
            store.save();
        }, trackIntervalTicks, trackIntervalTicks);
    }

    public void stop() {
        if (trackTask != null) {
            trackTask.cancel();
            trackTask = null;
        }
        store.save();
    }

    public boolean kitsEnabled() {
        return enabled;
    }

    public int getPlaytimeMinutes(UUID uuid) {
        return store.getMinutes(uuid);
    }

    public PlayerTier resolvePlayerTier(UUID uuid) {
        int m = store.getMinutes(uuid);
        PlayerTier best = null;
        for (PlayerTier t : playerTiers) {
            if (m >= t.minMinutes) {
                best = t;
            }
        }
        return best;
    }

    public GradeRank resolveGradeRank(Player player) {
        GradeRank fallback = null;
        for (GradeRank r : gradeRanks) {
            if (r.fallback) {
                fallback = r;
                continue;
            }
            if (!r.permission.isEmpty() && player.hasPermission(r.permission)) {
                return r;
            }
        }
        return fallback;
    }

    public long cooldownRemainingMs(Player player, KitKind kind) {
        long now = System.currentTimeMillis();
        long last = kind == KitKind.PLAYER
                ? store.getLastPlayerKitMs(player.getUniqueId())
                : store.getLastGradeKitMs(player.getUniqueId());
        long cd = kind == KitKind.PLAYER ? playerCooldownMs : gradeCooldownMs;
        long remain = last + cd - now;
        return Math.max(0L, remain);
    }

    /**
     * @return true if kit was granted
     */
    public boolean tryClaim(Player player, KitKind kind) {
        if (!enabled) {
            player.sendMessage(Component.text("Evolving kits are disabled.", NamedTextColor.RED));
            return false;
        }
        if (!player.hasPermission("dpmr.kit.use")) {
            player.sendMessage(Component.text("You don't have permission to use /kit.", NamedTextColor.RED));
            return false;
        }
        long remain = cooldownRemainingMs(player, kind);
        if (remain > 0) {
            player.sendMessage(Component.text(
                    "Cooldown: wait " + formatDuration(remain) + " before claiming this kit again.",
                    NamedTextColor.RED));
            return false;
        }

        if (kind == KitKind.PLAYER) {
            PlayerTier tier = resolvePlayerTier(player.getUniqueId());
            if (tier == null) {
                player.sendMessage(Component.text("No playtime kit tiers are configured.", NamedTextColor.RED));
                return false;
            }
            grant(player, tier.rewards);
            store.setLastPlayerKitMs(player.getUniqueId(), System.currentTimeMillis());
            player.sendMessage(Component.text(
                    "Player kit ("
                            + tier.label
                            + " — "
                            + store.getMinutes(player.getUniqueId())
                            + " min playtime): items added to your inventory.",
                    NamedTextColor.GREEN));
        } else {
            GradeRank rank = resolveGradeRank(player);
            if (rank == null) {
                player.sendMessage(Component.text("No grade kit ranks are configured.", NamedTextColor.RED));
                return false;
            }
            grant(player, rank.rewards);
            store.setLastGradeKitMs(player.getUniqueId(), System.currentTimeMillis());
            player.sendMessage(Component.text(
                    "Grade kit (" + rank.label + "): items added to your inventory.",
                    NamedTextColor.GREEN));
        }
        store.save();
        pointsManager.saveAsync();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        return true;
    }

    private void grant(Player player, List<KitReward> rewards) {
        PlayerInventory inv = player.getInventory();
        for (KitReward r : rewards) {
            r.grant(player, inv, bandageManager, weaponManager, pointsManager);
        }
    }

    public void sendInfo(Player player) {
        if (!enabled) {
            player.sendMessage(Component.text("Evolving kits are disabled.", NamedTextColor.GRAY));
            return;
        }
        int min = store.getMinutes(player.getUniqueId());
        PlayerTier pt = resolvePlayerTier(player.getUniqueId());
        GradeRank gr = resolveGradeRank(player);
        player.sendMessage(Component.text("--- Evolving kits ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Playtime: ", NamedTextColor.GRAY).append(Component.text(min + " minutes", NamedTextColor.WHITE)));
        if (pt != null) {
            player.sendMessage(Component.text("Player kit tier: ", NamedTextColor.GRAY)
                    .append(Component.text(pt.label, NamedTextColor.AQUA)));
        }
        long pr = cooldownRemainingMs(player, KitKind.PLAYER);
        player.sendMessage(Component.text("Player kit cooldown: ", NamedTextColor.GRAY)
                .append(Component.text(pr > 0 ? formatDuration(pr) : "ready", pr > 0 ? NamedTextColor.RED : NamedTextColor.GREEN)));
        if (gr != null) {
            player.sendMessage(Component.text("Grade kit rank: ", NamedTextColor.GRAY)
                    .append(Component.text(gr.label, NamedTextColor.LIGHT_PURPLE)));
        }
        long grCd = cooldownRemainingMs(player, KitKind.GRADE);
        player.sendMessage(Component.text("Grade kit cooldown: ", NamedTextColor.GRAY)
                .append(Component.text(grCd > 0 ? formatDuration(grCd) : "ready", grCd > 0 ? NamedTextColor.RED : NamedTextColor.GREEN)));
    }

    private static String formatDuration(long ms) {
        long s = (ms + 999) / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return h + "h " + m + "m";
        }
        if (m > 0) {
            return m + "m " + sec + "s";
        }
        return sec + "s";
    }

    public record PlayerTier(int minMinutes, String label, List<KitReward> rewards) {
    }

    public record GradeRank(String permission, boolean fallback, String label, List<KitReward> rewards) {
    }

    public sealed interface KitReward permits KitReward.ConsumableReward, KitReward.WeaponReward, KitReward.PointsReward, KitReward.MaterialReward {

        void grant(Player player, PlayerInventory inv, BandageManager bandageManager, WeaponManager weaponManager, PointsManager pointsManager);

        record ConsumableReward(DpmrConsumable type, int amount) implements KitReward {
            @Override
            public void grant(Player player, PlayerInventory inv, BandageManager bandageManager, WeaponManager wm, PointsManager pm) {
                addOrDrop(inv, player, bandageManager.createConsumable(type, amount));
            }
        }

        record WeaponReward(String weaponId, int amount) implements KitReward {
            @Override
            public void grant(Player player, PlayerInventory inv, BandageManager bandageManager, WeaponManager wm, PointsManager pm) {
                int n = Math.min(64, Math.max(1, amount));
                for (int i = 0; i < n; i++) {
                    ItemStack it = wm.createWeaponItem(weaponId, player);
                    if (it != null) {
                        addOrDrop(inv, player, it);
                    }
                }
            }
        }

        record PointsReward(int amount) implements KitReward {
            @Override
            public void grant(Player player, PlayerInventory inv, BandageManager bandageManager, WeaponManager wm, PointsManager pm) {
                pm.addPoints(player.getUniqueId(), Math.max(0, amount));
            }
        }

        record MaterialReward(Material material, int amount) implements KitReward {
            @Override
            public void grant(Player player, PlayerInventory inv, BandageManager bandageManager, WeaponManager wm, PointsManager pm) {
                addOrDrop(inv, player, new ItemStack(material, Math.min(64, Math.max(1, amount))));
            }
        }

        private static void addOrDrop(PlayerInventory inv, Player player, ItemStack stack) {
            if (stack == null || stack.getType().isAir()) {
                return;
            }
            var left = inv.addItem(stack);
            for (ItemStack extra : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
    }
}
