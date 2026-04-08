package fr.dpmr.game;

import fr.dpmr.data.ClanManager;
import fr.dpmr.data.PointsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Objectifs dynamiques pendant l'apocalypse : rotation, recompenses, boss bar.
 */
public class DynamicObjectiveManager implements Listener {

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final ClanManager clanManager;

    private GameScoreboard gameScoreboard;

    private boolean apocalypseRunning;
    private BukkitTask secondTick;
    private BukkitTask pendingNextTask;
    private BossBar bossBar;
    private ActiveObjective active;

    public DynamicObjectiveManager(JavaPlugin plugin, PointsManager pointsManager, ClanManager clanManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.clanManager = clanManager;
    }

    public void setGameScoreboard(GameScoreboard gameScoreboard) {
        this.gameScoreboard = gameScoreboard;
    }

    public void onGameStart() {
        apocalypseRunning = true;
        cancelPendingNext();
        if (!plugin.getConfig().getBoolean("objectives.enabled", true)) {
            return;
        }
        int delayTicks = Math.max(20, plugin.getConfig().getInt("objectives.first-delay-seconds", 75) * 20);
        pendingNextTask = Bukkit.getScheduler().runTaskLater(plugin, this::tryStartRandomObjective, delayTicks);
        if (secondTick == null) {
            secondTick = Bukkit.getScheduler().runTaskTimer(plugin, this::tickEverySecond, 20L, 20L);
        }
    }

    public void onGameStop() {
        apocalypseRunning = false;
        cancelPendingNext();
        clearBossBar();
        active = null;
        if (secondTick != null) {
            secondTick.cancel();
            secondTick = null;
        }
    }

    private void cancelPendingNext() {
        if (pendingNextTask != null) {
            pendingNextTask.cancel();
            pendingNextTask = null;
        }
    }

    private void scheduleNextObjective() {
        cancelPendingNext();
        int gapTicks = Math.max(20, plugin.getConfig().getInt("objectives.min-gap-seconds", 50) * 20);
        pendingNextTask = Bukkit.getScheduler().runTaskLater(plugin, this::tryStartRandomObjective, gapTicks);
    }

    private void tryStartRandomObjective() {
        pendingNextTask = null;
        if (!apocalypseRunning || !plugin.getConfig().getBoolean("objectives.enabled", true)) {
            return;
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        ObjectiveType type = pickRandomType();
        if (type == null) {
            return;
        }
        startObjective(type);
    }

    private ObjectiveType pickRandomType() {
        List<ObjectiveType> pool = new ArrayList<>();
        if (plugin.getConfig().getBoolean("objectives.types.elimination-rush.enabled", true)) {
            pool.add(ObjectiveType.ELIMINATION_RUSH);
        }
        if (plugin.getConfig().getBoolean("objectives.types.mob-cull.enabled", true)) {
            pool.add(ObjectiveType.MOB_CULL);
        }
        if (plugin.getConfig().getBoolean("objectives.types.hot-zone.enabled", true)) {
            pool.add(ObjectiveType.HOT_ZONE);
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private void startObjective(ObjectiveType type) {
        clearBossBar();
        long now = System.currentTimeMillis();
        ActiveObjective ao = new ActiveObjective();
        ao.type = type;
        ao.startedMs = now;
        switch (type) {
            case ELIMINATION_RUSH -> {
                ao.durationSec = Math.max(30, plugin.getConfig().getInt("objectives.types.elimination-rush.duration-seconds", 300));
                ao.deadlineMs = now + ao.durationSec * 1000L;
                ao.killsNeeded = Math.max(1, plugin.getConfig().getInt("objectives.types.elimination-rush.kills-to-win", 2));
                ao.winnerPoints = Math.max(1, plugin.getConfig().getInt("objectives.types.elimination-rush.winner-points", 22));
            }
            case MOB_CULL -> {
                ao.durationSec = Math.max(30, plugin.getConfig().getInt("objectives.types.mob-cull.duration-seconds", 180));
                ao.deadlineMs = now + ao.durationSec * 1000L;
                ao.mobsNeeded = Math.max(5, plugin.getConfig().getInt("objectives.types.mob-cull.mobs-needed", 35));
                ao.participationPoints = Math.max(1, plugin.getConfig().getInt("objectives.types.mob-cull.participation-points", 8));
            }
            case HOT_ZONE -> {
                ao.durationSec = Math.max(45, plugin.getConfig().getInt("objectives.types.hot-zone.duration-seconds", 240));
                ao.deadlineMs = now + ao.durationSec * 1000L;
                ao.zoneRadius = Math.max(4.0, plugin.getConfig().getDouble("objectives.types.hot-zone.radius", 14));
                ao.zoneNeeded = Math.max(10.0, plugin.getConfig().getDouble("objectives.types.hot-zone.player-seconds-needed", 90));
                ao.zonePoints = Math.max(1, plugin.getConfig().getInt("objectives.types.hot-zone.zone-points", 12));
                ao.zoneCenter = pickZoneLocation();
                if (ao.zoneCenter == null) {
                    scheduleNextObjective();
                    return;
                }
            }
        }
        active = ao;
        ensureBossBar();
        refreshBossBar();
        broadcastStart(type);
        refreshAllSidebars();
    }

    private Location pickZoneLocation() {
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return null;
        }
        Player anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        World w = anchor.getWorld();
        Location L = anchor.getLocation().clone();
        L.add(ThreadLocalRandom.current().nextDouble(-55, 55), 0, ThreadLocalRandom.current().nextDouble(-55, 55));
        int hx = w.getHighestBlockYAt(L);
        L.setY(hx + 1.0);
        return L;
    }

    private void tickEverySecond() {
        if (!apocalypseRunning || active == null || active.finished) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= active.deadlineMs) {
            failObjective();
            return;
        }
        if (active.type == ObjectiveType.HOT_ZONE && active.zoneCenter != null) {
            int inZone = 0;
            double r = active.zoneRadius;
            double r2 = r * r;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(active.zoneCenter.getWorld())
                        && p.getLocation().distanceSquared(active.zoneCenter) <= r2) {
                    inZone++;
                }
            }
            active.zoneCumulative += inZone;
            if (active.zoneCumulative >= active.zoneNeeded) {
                succeedObjective(null);
                return;
            }
            World zw = active.zoneCenter.getWorld();
            if (zw != null) {
                zw.spawnParticle(Particle.END_ROD, active.zoneCenter.clone().add(0, 1.0, 0), 14,
                        active.zoneRadius * 0.4, 0.35, active.zoneRadius * 0.4, 0.01);
            }
        }
        refreshBossBar();
        refreshAllSidebars();
    }

    private void ensureBossBar() {
        if (bossBar != null) {
            return;
        }
        bossBar = Bukkit.createBossBar("...", BarColor.PURPLE, BarStyle.SOLID);
        bossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }
    }

    private void clearBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    private void refreshBossBar() {
        if (bossBar == null || active == null || active.finished) {
            return;
        }
        long left = Math.max(0, active.deadlineMs - System.currentTimeMillis());
        int secLeft = (int) (left / 1000);
        double prog = progressRatio();
        bossBar.setProgress(Math.max(0.02, Math.min(1.0, prog)));
        bossBar.setTitle(buildBossTitle(secLeft));
    }

    private double progressRatio() {
        if (active == null) {
            return 0;
        }
        return switch (active.type) {
            case ELIMINATION_RUSH -> {
                int best = 0;
                for (int v : active.rushKills.values()) {
                    best = Math.max(best, v);
                }
                yield Math.min(1.0, best / (double) Math.max(1, active.killsNeeded));
            }
            case MOB_CULL -> Math.min(1.0, active.mobProgress / (double) Math.max(1, active.mobsNeeded));
            case HOT_ZONE -> Math.min(1.0, active.zoneCumulative / Math.max(1.0, active.zoneNeeded));
        };
    }

    private String buildBossTitle(int secLeft) {
        if (active == null) {
            return "DPMR";
        }
        String time = secLeft / 60 + ":" + String.format(Locale.ROOT, "%02d", secLeft % 60);
        return switch (active.type) {
            case ELIMINATION_RUSH -> "\u00A7c\u00A7lRUSH PvP \u00A7r\u00A77" + time
                    + " \u00A78| \u00A7f" + maxRushKills() + "/" + active.killsNeeded + " \u00A78elim.";
            case MOB_CULL -> "\u00A7a\u00A7lRAFLE MOBS \u00A7r\u00A77" + time
                    + " \u00A78| \u00A7f" + active.mobProgress + "/" + active.mobsNeeded;
            case HOT_ZONE -> "\u00A7e\u00A7lZONE CHAUDE \u00A7r\u00A77" + time
                    + " \u00A78| \u00A7f" + (int) active.zoneCumulative + "/" + (int) active.zoneNeeded + " \u00A78s-j";
        };
    }

    private int maxRushKills() {
        if (active == null) {
            return 0;
        }
        int best = 0;
        for (int v : active.rushKills.values()) {
            best = Math.max(best, v);
        }
        return best;
    }

    private void broadcastStart(ObjectiveType type) {
        Component msg = switch (type) {
            case ELIMINATION_RUSH -> Component.text(
                    "[DPMR] Objectif : premier a " + active.killsNeeded + " eliminations PvP (hors clan) gagne " + active.winnerPoints + " pts !",
                    NamedTextColor.RED);
            case MOB_CULL -> Component.text(
                    "[DPMR] Objectif : tuer " + active.mobsNeeded + " monstres (arme) avant la fin — tous les survivants gagnent " + active.participationPoints + " pts.",
                    NamedTextColor.GREEN);
            case HOT_ZONE -> Component.text(
                    "[DPMR] Zone chaude ~ X " + (int) active.zoneCenter.getX()
                            + " Z " + (int) active.zoneCenter.getZ() + " (r " + (int) active.zoneRadius
                            + ") — " + (int) active.zoneNeeded + " s-j cumules, +" + active.zonePoints + " pts.",
                    NamedTextColor.GOLD);
        };
        Bukkit.broadcast(msg);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!apocalypseRunning || active == null || active.finished || active.type != ObjectiveType.ELIMINATION_RUSH) {
            return;
        }
        Player victim = event.getEntity();
        if (victim.getPersistentDataContainer().has(new NamespacedKey(plugin, "dpmr_fake_npc"), PersistentDataType.BYTE)) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        String kc = clanManager.getPlayerClan(killer.getUniqueId());
        String vc = clanManager.getPlayerClan(victim.getUniqueId());
        if (kc != null && kc.equalsIgnoreCase(vc)) {
            return;
        }
        int n = active.rushKills.merge(killer.getUniqueId(), 1, Integer::sum);
        if (n >= active.killsNeeded) {
            succeedObjective(killer);
        } else {
            refreshBossBar();
            refreshAllSidebars();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (!apocalypseRunning || active == null || active.finished || active.type != ObjectiveType.MOB_CULL) {
            return;
        }
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        if (!(event.getEntity().getKiller() instanceof Player)) {
            return;
        }
        active.mobProgress++;
        if (active.mobProgress >= active.mobsNeeded) {
            succeedObjective(null);
        } else {
            refreshBossBar();
            refreshAllSidebars();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (bossBar != null) {
            bossBar.addPlayer(event.getPlayer());
        }
    }

    private void succeedObjective(Player winner) {
        if (active == null || active.finished) {
            return;
        }
        active.finished = true;
        switch (active.type) {
            case ELIMINATION_RUSH -> {
                if (winner != null) {
                    pointsManager.addPoints(winner.getUniqueId(), active.winnerPoints);
                    pointsManager.saveAsync();
                    Bukkit.broadcast(Component.text(
                            "[DPMR] " + winner.getName() + " remporte l'objectif Rush PvP (+" + active.winnerPoints + " pts) !",
                            NamedTextColor.GOLD));
                }
            }
            case MOB_CULL -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    pointsManager.addPoints(p.getUniqueId(), active.participationPoints);
                }
                pointsManager.saveAsync();
                Bukkit.broadcast(Component.text(
                        "[DPMR] Rafle de monstres reussie ! +" + active.participationPoints + " pts pour chaque survivant en ligne.",
                        NamedTextColor.GREEN));
            }
            case HOT_ZONE -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    pointsManager.addPoints(p.getUniqueId(), active.zonePoints);
                }
                pointsManager.saveAsync();
                Bukkit.broadcast(Component.text(
                        "[DPMR] Zone tenue ! +" + active.zonePoints + " pts pour tout le monde.",
                        NamedTextColor.YELLOW));
            }
        }
        clearBossBar();
        active = null;
        refreshAllSidebars();
        scheduleNextObjective();
    }

    private void failObjective() {
        if (active == null || active.finished) {
            return;
        }
        active.finished = true;
        Bukkit.broadcast(Component.text(
                "[DPMR] Objectif echoue — temps ecoule.",
                NamedTextColor.DARK_GRAY));
        clearBossBar();
        active = null;
        refreshAllSidebars();
        scheduleNextObjective();
    }

    private void refreshAllSidebars() {
        if (gameScoreboard == null) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            gameScoreboard.applySidebar(p);
        }
    }

    public boolean hasActiveObjective() {
        return active != null && !active.finished;
    }

    /** Ligne courte pour le sidebar (vide si rien). */
    public String getSidebarLineText() {
        if (!hasActiveObjective()) {
            return "";
        }
        long left = Math.max(0, active.deadlineMs - System.currentTimeMillis());
        int s = (int) (left / 1000);
        return switch (active.type) {
            case ELIMINATION_RUSH -> "Obj: Rush " + maxRushKills() + "/" + active.killsNeeded + " (" + s + "s)";
            case MOB_CULL -> "Obj: Mobs " + active.mobProgress + "/" + active.mobsNeeded + " (" + s + "s)";
            case HOT_ZONE -> "Obj: Zone " + (int) active.zoneCumulative + "/" + (int) active.zoneNeeded + " (" + s + "s)";
        };
    }

    public String getStatusSummary() {
        if (!apocalypseRunning) {
            return "Objectifs: apocalypse arretee.";
        }
        if (!plugin.getConfig().getBoolean("objectives.enabled", true)) {
            return "Objectives: disabled (config).";
        }
        if (!hasActiveObjective()) {
            return "Objectifs: en attente du prochain.";
        }
        return "Objectif actif: " + switch (active.type) {
            case ELIMINATION_RUSH -> "Rush PvP " + maxRushKills() + "/" + active.killsNeeded;
            case MOB_CULL -> "Rafle mobs " + active.mobProgress + "/" + active.mobsNeeded;
            case HOT_ZONE -> "Zone chaude " + (int) active.zoneCumulative + "/" + (int) active.zoneNeeded;
        };
    }

    private enum ObjectiveType {
        ELIMINATION_RUSH,
        MOB_CULL,
        HOT_ZONE
    }

    private static final class ActiveObjective {
        ObjectiveType type;
        long startedMs;
        long deadlineMs;
        int durationSec;
        boolean finished;

        int killsNeeded;
        int winnerPoints;
        final Map<UUID, Integer> rushKills = new HashMap<>();

        int mobsNeeded;
        int mobProgress;
        int participationPoints;

        Location zoneCenter;
        double zoneRadius;
        double zoneNeeded;
        double zoneCumulative;
        int zonePoints;
    }
}
