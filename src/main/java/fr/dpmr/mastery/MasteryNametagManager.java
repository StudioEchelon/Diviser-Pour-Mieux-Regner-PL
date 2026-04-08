package fr.dpmr.mastery;

import fr.dpmr.data.PointsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Shows the mastery title above the player's head (entity custom name).
 */
public class MasteryNametagManager implements Listener {

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;

    public MasteryNametagManager(JavaPlugin plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        pointsManager.addAfterKillTotalListener((uuid, total) -> scheduleApply(uuid));
        pointsManager.addAfterPointsTotalListener((uuid, total) -> scheduleApply(uuid));
    }

    private void scheduleApply(UUID uuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                apply(p);
            }
        });
    }

    /** Sets nametag to «Title» Name */
    public void apply(Player player) {
        int kills = pointsManager.getKills(player.getUniqueId());
        int pts = pointsManager.getPoints(player.getUniqueId());
        MasteryTier m = MasteryTier.fromProgress(kills, pts);
        Component tag = Component.text("\u00ab", m.accent())
                .append(Component.text(m.chatTitle(), m.accent(), TextDecoration.BOLD))
                .append(Component.text("\u00bb ", m.accent()))
                .append(Component.text(player.getName(), NamedTextColor.WHITE));
        player.customName(tag);
        player.setCustomNameVisible(true);
    }

    public void applyAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            apply(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> apply(event.getPlayer()));
    }
}
