package fr.dpmr.command;

import fr.dpmr.quest.DailyQuestGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DailyQuestCommand implements CommandExecutor {

    private final DailyQuestGui dailyQuestGui;

    public DailyQuestCommand(DailyQuestGui dailyQuestGui) {
        this.dailyQuestGui = dailyQuestGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        dailyQuestGui.open(player);
        return true;
    }
}
