package fr.dpmr.quest;

import fr.dpmr.data.PointsManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-player calendar daily quests: three parallel tracks by default (Combat, Gathering, Challenge).
 * Progress is tracked by {@link DailyQuestGui} events.
 */
public final class DailyQuestManager {

    public static final int DEFAULT_SLOT_COUNT = 3;

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final File file;
    private final YamlConfiguration yaml;
    private final ZoneId zoneId;
    private boolean saveTaskQueued;

    public DailyQuestManager(JavaPlugin plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.file = new File(plugin.getDataFolder(), "daily-quests.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        String zid = plugin.getConfig().getString("daily-quests.timezone", "");
        this.zoneId = zid != null && !zid.isBlank() ? ZoneId.of(zid) : ZoneId.systemDefault();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("daily-quests.enabled", true);
    }

    public int slotCount() {
        int n = plugin.getConfig().getInt("daily-quests.slot-count", DEFAULT_SLOT_COUNT);
        return Math.max(1, Math.min(3, n));
    }

    public LocalDate today() {
        return LocalDate.now(zoneId);
    }

    public String todayKey() {
        return today().toString();
    }

    public void ensureQuestForToday(Player player) {
        if (!isEnabled()) {
            return;
        }
        UUID id = player.getUniqueId();
        String path = "players." + id;
        String key = todayKey();
        String storedDay = yaml.getString(path + ".day", "");
        boolean dirty = false;
        if (!key.equals(storedDay)) {
            startNewDay(path, key);
            dirty = true;
        } else if (yaml.contains(path + ".type") && !yaml.contains(path + ".quests.0.type")) {
            migrateLegacySingleQuest(path, key);
            dirty = true;
        } else {
            for (int s = 0; s < slotCount(); s++) {
                if (parseType(yaml.getString(path + ".quests." + s + ".type", "")) == null) {
                    rollNewQuest(path, key, s);
                    dirty = true;
                }
            }
        }
        if (dirty) {
            saveQuiet();
        }
    }

    private void startNewDay(String playerPath, String dayKey) {
        yaml.set(playerPath + ".day", dayKey);
        yaml.set(playerPath + ".type", null);
        yaml.set(playerPath + ".target", null);
        yaml.set(playerPath + ".progress", null);
        yaml.set(playerPath + ".claimed", null);
        yaml.set(playerPath + ".quests", null);
        yaml.set(playerPath + ".day-complete-bonus-claimed", null);
        for (int s = 0; s < slotCount(); s++) {
            rollNewQuest(playerPath, dayKey, s);
        }
    }

    private void migrateLegacySingleQuest(String playerPath, String dayKey) {
        String oldType = yaml.getString(playerPath + ".type");
        if (oldType != null && !oldType.isEmpty()) {
            yaml.set(playerPath + ".quests.0.type", oldType);
            yaml.set(playerPath + ".quests.0.target", yaml.getInt(playerPath + ".target", 1));
            yaml.set(playerPath + ".quests.0.progress", yaml.getInt(playerPath + ".progress", 0));
            yaml.set(playerPath + ".quests.0.claimed", yaml.getBoolean(playerPath + ".claimed", false));
        }
        yaml.set(playerPath + ".type", null);
        yaml.set(playerPath + ".target", null);
        yaml.set(playerPath + ".progress", null);
        yaml.set(playerPath + ".claimed", null);
        for (int s = 0; s < slotCount(); s++) {
            if (parseType(yaml.getString(playerPath + ".quests." + s + ".type", "")) == null) {
                rollNewQuest(playerPath, dayKey, s);
            }
        }
    }

    private void rollNewQuest(String playerPath, String dayKey, int slot) {
        DailyQuestType type = pickTypeForTrack(slot);
        int target = targetForType(type);
        String qp = playerPath + ".quests." + slot;
        yaml.set(qp + ".type", type.name());
        yaml.set(qp + ".target", target);
        yaml.set(qp + ".progress", 0);
        yaml.set(qp + ".claimed", false);
        yaml.set(playerPath + ".day", dayKey);
    }

    /**
     * Three systems: combat PvP/mobs, gathering fish/logs, challenge mobs/damage dealt.
     */
    private DailyQuestType pickTypeForTrack(int slot) {
        DailyQuestType[] pool = switch (slot) {
            case 0 -> new DailyQuestType[]{DailyQuestType.KILL_PLAYERS, DailyQuestType.KILL_HOSTILE_MOBS};
            case 1 -> new DailyQuestType[]{DailyQuestType.CATCH_FISH, DailyQuestType.CHOP_LOGS};
            case 2 -> new DailyQuestType[]{DailyQuestType.KILL_HOSTILE_MOBS, DailyQuestType.DEAL_PLAYER_DAMAGE};
            default -> DailyQuestType.values();
        };
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    private int targetForType(DailyQuestType type) {
        return switch (type) {
            case KILL_PLAYERS -> randomBetween(
                    "daily-quests.kill-players.min",
                    "daily-quests.kill-players.max",
                    5, 12);
            case KILL_HOSTILE_MOBS -> randomBetween(
                    "daily-quests.kill-mobs.min",
                    "daily-quests.kill-mobs.max",
                    20, 45);
            case CATCH_FISH -> randomBetween(
                    "daily-quests.catch-fish.min",
                    "daily-quests.catch-fish.max",
                    3, 8);
            case CHOP_LOGS -> randomBetween(
                    "daily-quests.chop-logs.min",
                    "daily-quests.chop-logs.max",
                    8, 22);
            case DEAL_PLAYER_DAMAGE -> randomBetween(
                    "daily-quests.deal-player-damage.min",
                    "daily-quests.deal-player-damage.max",
                    45, 120);
        };
    }

    private int randomBetween(String minKey, String maxKey, int defMin, int defMax) {
        int lo = plugin.getConfig().getInt(minKey, defMin);
        int hi = plugin.getConfig().getInt(maxKey, defMax);
        if (hi < lo) {
            int t = lo;
            lo = hi;
            hi = t;
        }
        return ThreadLocalRandom.current().nextInt(lo, hi + 1);
    }

    public List<QuestSnapshot> snapshots(UUID playerId) {
        List<QuestSnapshot> list = new ArrayList<>(slotCount());
        String path = "players." + playerId;
        String day = yaml.getString(path + ".day", "");
        for (int s = 0; s < slotCount(); s++) {
            String qp = path + ".quests." + s;
            DailyQuestType type = parseType(yaml.getString(qp + ".type", ""));
            if (type == null) {
                list.add(null);
                continue;
            }
            int target = Math.max(1, yaml.getInt(qp + ".target", 1));
            int progress = Math.max(0, yaml.getInt(qp + ".progress", 0));
            boolean claimed = yaml.getBoolean(qp + ".claimed", false);
            list.add(new QuestSnapshot(s, type, target, progress, claimed, day));
        }
        return list;
    }

    private static DailyQuestType parseType(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return DailyQuestType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @return numeros de pistes (0-based) dont l'objectif vient d'etre atteint (progression avant &lt; cible, apres &gt;=)
     */
    public List<Integer> addProgress(Player player, DailyQuestType type, int delta) {
        if (!isEnabled() || delta <= 0) {
            return List.of();
        }
        ensureQuestForToday(player);
        UUID id = player.getUniqueId();
        String path = "players." + id;
        List<Integer> justCompleted = new ArrayList<>();
        for (int s = 0; s < slotCount(); s++) {
            String qp = path + ".quests." + s;
            DailyQuestType current = parseType(yaml.getString(qp + ".type", ""));
            if (current != type) {
                continue;
            }
            if (yaml.getBoolean(qp + ".claimed", false)) {
                continue;
            }
            int target = Math.max(1, yaml.getInt(qp + ".target", 1));
            int p = Math.max(0, yaml.getInt(qp + ".progress", 0));
            int next = Math.min(target, p + delta);
            if (next >= target && p < target) {
                justCompleted.add(s);
            }
            yaml.set(qp + ".progress", next);
        }
        saveQuiet();
        return Collections.unmodifiableList(justCompleted);
    }

    public int rewardPointsPerQuest() {
        return Math.max(0, plugin.getConfig().getInt("daily-quests.reward-points", 100));
    }

    public int rewardPointsForType(DailyQuestType type) {
        int def = rewardPointsPerQuest();
        return Math.max(0, plugin.getConfig().getInt("daily-quests.reward-points-by-type." + type.name(), def));
    }

    /**
     * @return points granted, or -1 if claim failed
     */
    public int tryClaim(Player player, int slot) {
        if (!isEnabled() || slot < 0 || slot >= slotCount()) {
            return -1;
        }
        ensureQuestForToday(player);
        UUID id = player.getUniqueId();
        String path = "players." + id;
        String qp = path + ".quests." + slot;
        if (yaml.getBoolean(qp + ".claimed", false)) {
            return -1;
        }
        int target = Math.max(1, yaml.getInt(qp + ".target", 1));
        int progress = Math.max(0, yaml.getInt(qp + ".progress", 0));
        if (progress < target) {
            return -1;
        }
        DailyQuestType qType = parseType(yaml.getString(qp + ".type", ""));
        int reward = qType != null ? rewardPointsForType(qType) : rewardPointsPerQuest();
        yaml.set(qp + ".claimed", true);
        if (reward > 0) {
            pointsManager.addPoints(id, reward);
        }
        int bonus = Math.max(0, plugin.getConfig().getInt("daily-quests.bonus-all-complete", 0));
        if (bonus > 0 && allQuestSlotsClaimed(path)) {
            if (!yaml.getBoolean(path + ".day-complete-bonus-claimed", false)) {
                yaml.set(path + ".day-complete-bonus-claimed", true);
                pointsManager.addPoints(id, bonus);
                reward += bonus;
            }
        }
        saveQuiet();
        if (reward > 0) {
            pointsManager.saveAsync();
        }
        return reward;
    }

    private boolean allQuestSlotsClaimed(String playerPath) {
        for (int s = 0; s < slotCount(); s++) {
            if (!yaml.getBoolean(playerPath + ".quests." + s + ".claimed", false)) {
                return false;
            }
        }
        return true;
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save daily-quests.yml: " + e.getMessage());
        }
    }

    private void saveQuiet() {
        if (saveTaskQueued) {
            return;
        }
        saveTaskQueued = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            saveTaskQueued = false;
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save daily-quests.yml: " + e.getMessage());
            }
        });
    }

    public record QuestSnapshot(int slot, DailyQuestType type, int target, int progress, boolean claimed, String dayKey) {

        public boolean complete() {
            return progress >= target;
        }
    }
}
