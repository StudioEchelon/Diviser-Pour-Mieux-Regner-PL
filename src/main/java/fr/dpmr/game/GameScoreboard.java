package fr.dpmr.game;

import fr.dpmr.data.ClanManager;
import fr.dpmr.data.PointsManager;
import fr.dpmr.util.BrandingText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class GameScoreboard {

    private static final String[] ENTRIES = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73", "\u00A74", "\u00A75",
            "\u00A76", "\u00A77", "\u00A78", "\u00A79"
    };
    private static final String HEALTH_OBJECTIVE_ID = "dpmr_health";

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final ClanManager clanManager;
    private final BooleanSupplier gameRunning;
    private final Supplier<String> objectiveSidebarLine;
    private final Supplier<String> bountySidebarLine;
    private BukkitTask task;
    private int animFrame = 0;

    public GameScoreboard(JavaPlugin plugin, PointsManager pointsManager, ClanManager clanManager, BooleanSupplier gameRunning) {
        this(plugin, pointsManager, clanManager, gameRunning, () -> "", () -> "");
    }

    public GameScoreboard(JavaPlugin plugin, PointsManager pointsManager, ClanManager clanManager,
                          BooleanSupplier gameRunning, Supplier<String> objectiveSidebarLine) {
        this(plugin, pointsManager, clanManager, gameRunning, objectiveSidebarLine, () -> "");
    }

    public GameScoreboard(JavaPlugin plugin, PointsManager pointsManager, ClanManager clanManager,
                          BooleanSupplier gameRunning, Supplier<String> objectiveSidebarLine, Supplier<String> bountySidebarLine) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.clanManager = clanManager;
        this.gameRunning = gameRunning;
        this.objectiveSidebarLine = objectiveSidebarLine;
        this.bountySidebarLine = bountySidebarLine != null ? bountySidebarLine : () -> "";
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(main);
        }
    }

    private void tick() {
        animFrame = (animFrame + 1) % 8;
        for (Player p : Bukkit.getOnlinePlayers()) {
            applySidebar(p);
            applyTab(p);
        }
    }

    public void applySidebar(Player player) {
        Scoreboard board = player.getScoreboard();
        if (board == null || board.equals(Bukkit.getScoreboardManager().getMainScoreboard())
                || board.getObjective("dpmr_sb") == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("dpmr_sb", Criteria.DUMMY,
                    Component.text("DPMR", NamedTextColor.RED, TextDecoration.BOLD));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            ensureBelowNameHealthObjective(board);
            player.setScoreboard(board);
        }
        ensureBelowNameHealthObjective(board);
        Objective obj = board.getObjective("dpmr_sb");
        if (obj == null) {
            return;
        }

        boolean running = gameRunning.getAsBoolean();
        String clan = clanManager.getPlayerClan(player.getUniqueId());
        int pts = pointsManager.getPoints(player.getUniqueId());
        int kills = pointsManager.getKills(player.getUniqueId());
        String objRaw = objectiveSidebarLine.get();
        String objLine = (objRaw == null || objRaw.isBlank()) ? "-" : shrink(objRaw, 16);
        String bountyRaw = bountySidebarLine.get();
        String bountyLine = (bountyRaw == null || bountyRaw.isBlank()) ? "" : shrink(bountyRaw, 18);
        String spinner = switch (animFrame % 4) {
            case 0 -> "◜";
            case 1 -> "◠";
            case 2 -> "◝";
            default -> "◞";
        };

        TextColor blueGrey = TextColor.fromHexString("#7B93A7");
        obj.displayName(BrandingText.serverName()
                .append(Component.text(spinner, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ", blueGrey)));
        List<Component> lineList = new ArrayList<>();
        lineList.add(Component.text("╺━━━━━━━━━━━━╸", NamedTextColor.DARK_GRAY));
        lineList.add(Component.text("Mode ", TextColor.fromHexString("#7B93A7"))
                .append(Component.text(running ? "WAR" : "IDLE", running ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, TextDecoration.BOLD)));
        lineList.add(Component.text("Pts ", TextColor.fromHexString("#F6D365")).append(Component.text(pts, NamedTextColor.WHITE))
                .append(Component.text("  K ", NamedTextColor.RED)).append(Component.text(kills, NamedTextColor.WHITE)));
        lineList.add(Component.text("Clan ", TextColor.fromHexString("#4A6A86")).append(Component.text(shrink(clan != null ? clan : "-", 10), NamedTextColor.WHITE)));
        lineList.add(Component.text("Goal ", NamedTextColor.LIGHT_PURPLE).append(Component.text(objLine, TextColor.fromHexString("#B48EAD"))));
        if (!bountyLine.isEmpty()) {
            lineList.add(Component.text("Prime ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(bountyLine, NamedTextColor.GOLD)));
        }
        lineList.add(Component.text("╺━━━━━━━━━━━━╸", NamedTextColor.DARK_GRAY));
        lineList.add(Component.text("/bout /hdv /cos", TextColor.fromHexString("#4A6A86")));

        Component[] lines = lineList.toArray(Component[]::new);

        int n = Math.min(lines.length, ENTRIES.length);
        int score = n;
        for (int i = 0; i < n; i++) {
            String teamName = "dpmr_sb_" + i;
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
                team.addEntry(ENTRIES[i]);
            }
            team.prefix(lines[i]);
            obj.getScore(ENTRIES[i]).setScore(score--);
        }
        // Clear unused score lines
        for (int i = n; i < ENTRIES.length; i++) {
            obj.getScore(ENTRIES[i]).resetScore();
        }
    }

    public void apply(Player player) {
        applySidebar(player);
    }

    private void applyTab(Player player) {
        boolean running = gameRunning.getAsBoolean();
        int pts = pointsManager.getPoints(player.getUniqueId());
        int kills = pointsManager.getKills(player.getUniqueId());
        String clan = clanManager.getPlayerClan(player.getUniqueId());
        String objRaw = objectiveSidebarLine.get();
        String obj = (objRaw == null || objRaw.isBlank()) ? "-" : shrink(objRaw, 52);
        String pulse = switch (animFrame % 6) {
            case 0 -> "✦";
            case 1 -> "✧";
            case 2 -> "✦";
            case 3 -> "✧";
            case 4 -> "✦";
            default -> "✧";
        };

        player.sendPlayerListHeader(
                BrandingText.divideAndConquerTab()
                        .append(Component.newline())
                        .append(Component.text("───────────", NamedTextColor.DARK_GRAY))
                        .append(Component.newline())
                        .append(Component.text(pulse + " ", NamedTextColor.GOLD))
                        .append(Component.text("Apocalypse", NamedTextColor.GRAY))
                        .append(Component.text(running ? " ON" : " OFF", running ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
        );

        player.sendPlayerListFooter(
                Component.text("Points: ", NamedTextColor.YELLOW).append(Component.text(pts, NamedTextColor.WHITE))
                        .append(Component.text(" | Kills: ", NamedTextColor.GRAY)).append(Component.text(kills, NamedTextColor.WHITE))
                        .append(Component.text(" | Clan: ", NamedTextColor.AQUA)).append(Component.text(clan != null ? clan : "-", NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("Objective: ", NamedTextColor.LIGHT_PURPLE)).append(Component.text(obj, NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text("  " + pulse, NamedTextColor.GOLD))
        );
    }

    private static String shrink(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, Math.max(1, max - 1)) + "…";
    }

    private void ensureBelowNameHealthObjective(Scoreboard board) {
        Objective health = board.getObjective(HEALTH_OBJECTIVE_ID);
        if (health == null) {
            health = board.registerNewObjective(
                    HEALTH_OBJECTIVE_ID,
                    Criteria.HEALTH,
                    Component.text("❤", NamedTextColor.RED),
                    RenderType.HEARTS
            );
        }
        health.setDisplaySlot(DisplaySlot.BELOW_NAME);
    }
}
