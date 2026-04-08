package fr.dpmr.chat;

import fr.dpmr.data.ClanManager;
import fr.dpmr.data.PointsManager;
import fr.dpmr.mastery.MasteryTier;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Join message + chat formatting (Paper 1.20+).
 */
public class ChatFormatListener implements Listener {

    private final PointsManager pointsManager;
    private final ClanManager clanManager;

    public ChatFormatListener(PointsManager pointsManager, ClanManager clanManager) {
        this.pointsManager = pointsManager;
        this.clanManager = clanManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var p = event.getPlayer();
        String clan = clanManager.getPlayerClan(p.getUniqueId());
        int pts = pointsManager.getPoints(p.getUniqueId());
        int kills = pointsManager.getKills(p.getUniqueId());
        MasteryTier mastery = MasteryTier.fromProgress(kills, pts);

        Component join = Component.text("+ ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("[" + mastery.chatTitle() + "] ", mastery.accent(), TextDecoration.BOLD))
                .append(Component.text(p.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" joined the server", NamedTextColor.GRAY))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("[" + pts + " pts]", NamedTextColor.GOLD))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(Component.text("[" + (clan != null ? clan : "-") + "]", NamedTextColor.AQUA));
        event.joinMessage(join);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var p = event.getPlayer();
        Component finalMsg = chatPrefix(p).append(event.message().colorIfAbsent(NamedTextColor.GRAY));
        event.setCancelled(true);
        for (Audience viewer : event.viewers()) {
            viewer.sendMessage(finalMsg);
        }
        Bukkit.getConsoleSender().sendMessage(finalMsg);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        var p = event.getPlayer();
        String raw = event.getMessage();
        Component finalMsg = chatPrefix(p).append(Component.text(raw, NamedTextColor.GRAY));
        event.setCancelled(true);
        Bukkit.getOnlinePlayers().forEach(pl -> pl.sendMessage(finalMsg));
        Bukkit.getConsoleSender().sendMessage(finalMsg);
    }

    private static String pulse() {
        long t = System.currentTimeMillis() / 400L;
        return (t % 2 == 0) ? "✦" : "✧";
    }

    private Component chatPrefix(org.bukkit.entity.Player p) {
        String clan = clanManager.getPlayerClan(p.getUniqueId());
        int pts = pointsManager.getPoints(p.getUniqueId());
        int kills = pointsManager.getKills(p.getUniqueId());
        MasteryTier mastery = MasteryTier.fromProgress(kills, pts);
        String pulse = pulse();
        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(pulse + " ", NamedTextColor.GOLD))
                .append(Component.text("CHAT", NamedTextColor.GRAY))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .append(Component.space())
                .append(Component.text("«", mastery.accent()))
                .append(Component.text(mastery.chatTitle(), mastery.accent(), TextDecoration.BOLD))
                .append(Component.text("»", mastery.accent()))
                .append(Component.space())
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(clan != null ? clan : "-", NamedTextColor.AQUA))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .append(Component.space())
                .append(Component.text(pts + "✦", NamedTextColor.GOLD))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("K:" + kills, NamedTextColor.RED))
                .append(Component.space())
                .append(Component.text(p.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" » ", NamedTextColor.GRAY));
    }
}

