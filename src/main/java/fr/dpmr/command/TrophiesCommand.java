package fr.dpmr.command;

import fr.dpmr.trophy.Trophy;
import fr.dpmr.trophy.TrophyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TrophiesCommand implements CommandExecutor {

    private final TrophyManager trophyManager;

    public TrophiesCommand(TrophyManager trophyManager) {
        this.trophyManager = trophyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        UUID id = player.getUniqueId();
        int have = trophyManager.unlockedCount(id);
        int total = trophyManager.totalDefined();
        player.sendMessage(Component.text("Your trophies: " + have + " / " + total, NamedTextColor.GOLD));
        for (Trophy t : Trophy.values()) {
            boolean ok = trophyManager.has(id, t);
            NamedTextColor nameColor = ok ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;
            NamedTextColor hintColor = ok ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY;
            String prefix = ok ? "[✓] " : "[ ] ";
            player.sendMessage(
                    Component.text(prefix, ok ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY)
                            .append(Component.text(t.title(), nameColor))
                            .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(t.subtitle(), hintColor))
            );
        }
        return true;
    }
}
