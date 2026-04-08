package fr.dpmr.game;

import fr.dpmr.data.ClanManager;
import fr.dpmr.data.PointsManager;
import fr.dpmr.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
/**
 * Cible une prime PvP sur un joueur en ligne, tirage aleatoire a intervalle fixe.
 */
public final class BountyManager implements Listener {

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final ClanManager clanManager;
    private BukkitTask task;
    private UUID bountyUuid;
    private int rewardPoints;

    public BountyManager(JavaPlugin plugin, PointsManager pointsManager, ClanManager clanManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.clanManager = clanManager;
    }

    public void start() {
        if (task != null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("bounty.enabled", true)) {
            return;
        }
        long intervalTicks = Math.max(600L, plugin.getConfig().getLong("bounty.interval-minutes", 15) * 60 * 20);
        long firstDelayTicks = Math.max(1L, plugin.getConfig().getLong("bounty.first-delay-seconds", 5) * 20);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::pickNewBounty, firstDelayTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        bountyUuid = null;
    }

    /** Ligne scoreboard courte, vide si pas de prime. */
    public String getSidebarLine() {
        if (bountyUuid == null) {
            return "";
        }
        Player p = Bukkit.getPlayer(bountyUuid);
        if (p == null) {
            return "";
        }
        int r = Math.max(1, rewardPoints);
        return p.getName() + " +" + r;
    }

    private void pickNewBounty() {
        if (!plugin.getConfig().getBoolean("bounty.enabled", true)) {
            return;
        }
        int minPlayers = Math.max(1, plugin.getConfig().getInt("bounty.min-players-online", 1));
        List<Player> online = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (!pl.getPersistentDataContainer().has(new NamespacedKey(plugin, "dpmr_fake_npc"), PersistentDataType.BYTE)) {
                online.add(pl);
            }
        }
        if (online.size() < minPlayers) {
            bountyUuid = null;
            return;
        }
        rewardPoints = Math.max(1, plugin.getConfig().getInt("bounty.reward-points", 50));
        Player pick = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        bountyUuid = pick.getUniqueId();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.sendMessage(Component.text(I18n.string(pl, "bounty.new_target", pick.getName(), rewardPoints),
                    NamedTextColor.GOLD, TextDecoration.BOLD));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (bountyUuid != null && event.getPlayer().getUniqueId().equals(bountyUuid)) {
            bountyUuid = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (bountyUuid == null) {
            return;
        }
        Player victim = event.getPlayer();
        if (victim.getPersistentDataContainer().has(new NamespacedKey(plugin, "dpmr_fake_npc"), PersistentDataType.BYTE)) {
            return;
        }
        if (!victim.getUniqueId().equals(bountyUuid)) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        String killerClan = clanManager.getPlayerClan(killer.getUniqueId());
        String victimClan = clanManager.getPlayerClan(victim.getUniqueId());
        if (killerClan != null && killerClan.equalsIgnoreCase(victimClan)) {
            return;
        }
        int bonus = Math.max(1, rewardPoints);
        bountyUuid = null;
        int gained = pointsManager.addKillRewardPoints(killer.getUniqueId(), bonus);
        pointsManager.saveAsync();
        killer.sendMessage(Component.text(I18n.string(killer, "bounty.reward_kill", gained, victim.getName()),
                NamedTextColor.GOLD));
        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.sendMessage(Component.text(I18n.string(pl, "bounty.claimed_broadcast", killer.getName(), victim.getName(), gained),
                    NamedTextColor.YELLOW));
        }
    }
}
