package fr.dpmr.command;

import fr.dpmr.kit.EvolvingKitManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KitCommand implements CommandExecutor, TabCompleter {

    private final EvolvingKitManager evolvingKitManager;

    public KitCommand(EvolvingKitManager evolvingKitManager) {
        this.evolvingKitManager = evolvingKitManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("info")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can view kit info.", NamedTextColor.RED));
                return true;
            }
            evolvingKitManager.sendInfo(player);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can claim kits.", NamedTextColor.RED));
            return true;
        }
        if (sub.equals("player") || sub.equals("playtime")) {
            evolvingKitManager.tryClaim(player, EvolvingKitManager.KitKind.PLAYER);
            return true;
        }
        if (sub.equals("grade") || sub.equals("rank")) {
            evolvingKitManager.tryClaim(player, EvolvingKitManager.KitKind.GRADE);
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("--- Kits (evolving) ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/kit player", NamedTextColor.YELLOW)
                .append(Component.text(" — Playtime kit: better rewards the more you play.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/kit grade", NamedTextColor.YELLOW)
                .append(Component.text(" — Rank kit: based on your permissions (e.g. VIP).", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/kit info", NamedTextColor.YELLOW)
                .append(Component.text(" — Playtime, tier, cooldowns.", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> all = List.of("player", "grade", "info", "help");
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String a : all) {
                if (a.startsWith(pref)) {
                    out.add(a);
                }
            }
            return out;
        }
        return List.of();
    }
}
