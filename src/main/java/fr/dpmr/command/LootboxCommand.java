package fr.dpmr.command;

import fr.dpmr.crate.LootboxManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LootboxCommand implements CommandExecutor, TabCompleter {

    private final LootboxManager lootboxManager;

    public LootboxCommand(LootboxManager lootboxManager) {
        this.lootboxManager = lootboxManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("daily")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can open the daily loot box.", NamedTextColor.RED));
                return true;
            }
            lootboxManager.tryOpenDaily(player);
            return true;
        }
        if (sub.equals("give")) {
            if (!sender.hasPermission("dpmr.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3 || !args[1].equalsIgnoreCase("ultimate")) {
                sender.sendMessage(Component.text("Usage: /lootbox give ultimate <player> [amount]", NamedTextColor.YELLOW));
                return true;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED));
                    return true;
                }
            }
            target.getInventory().addItem(lootboxManager.createUltimateLootboxItem(amount));
            sender.sendMessage(Component.text("Gave " + amount + " Ultimate Loot Box(es) to " + target.getName(), NamedTextColor.GREEN));
            target.sendMessage(Component.text("You received an Ultimate Loot Box! Right-click to open (5 rewards).", NamedTextColor.LIGHT_PURPLE));
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("--- Loot boxes ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/lootbox daily", NamedTextColor.YELLOW)
                .append(Component.text(" — Free daily box (1 reward, cooldown)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("Ultimate: right-click the Ultimate Loot Box item for 5 rewards.", NamedTextColor.GRAY));
        if (sender.hasPermission("dpmr.admin")) {
            sender.sendMessage(Component.text("/lootbox give ultimate <player> [amount]", NamedTextColor.YELLOW)
                    .append(Component.text(" — Admin", NamedTextColor.DARK_GRAY)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("daily");
            out.add("help");
            if (sender.hasPermission("dpmr.admin")) {
                out.add("give");
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("dpmr.admin")) {
            out.add("ultimate");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("ultimate")
                && sender.hasPermission("dpmr.admin")) {
            return null;
        }
        return out;
    }
}
