package fr.dpmr.command;

import fr.dpmr.data.PointsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PointsCommand implements CommandExecutor {

    private final PointsManager pointsManager;

    public PointsCommand(PointsManager pointsManager) {
        this.pointsManager = pointsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("dpmr.admin")) {
                sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /points give <player> <montant>", NamedTextColor.YELLOW));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Montant invalide.", NamedTextColor.RED));
                return true;
            }
            amount = Math.max(0, amount);
            pointsManager.addPoints(target.getUniqueId(), amount);
            pointsManager.save();
            sender.sendMessage(Component.text("+" + amount + " points -> " + target.getName(), NamedTextColor.GREEN));
            target.sendMessage(Component.text("Tu as recu +" + amount + " points.", NamedTextColor.GOLD));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("dpmr.admin")) {
                sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /points set <player> <montant>", NamedTextColor.YELLOW));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(Component.text("Joueur inconnu.", NamedTextColor.RED));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Montant invalide.", NamedTextColor.RED));
                return true;
            }
            pointsManager.setPoints(target.getUniqueId(), Math.max(0, amount));
            pointsManager.save();
            sender.sendMessage(Component.text("Points definis: " + target.getName() + " -> " + Math.max(0, amount), NamedTextColor.GREEN));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("take")) {
            if (!sender.hasPermission("dpmr.admin")) {
                sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /points take <player> <montant>", NamedTextColor.YELLOW));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(Component.text("Joueur inconnu.", NamedTextColor.RED));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Montant invalide.", NamedTextColor.RED));
                return true;
            }
            amount = Math.max(0, amount);
            if (!pointsManager.tryRemovePoints(target.getUniqueId(), amount)) {
                sender.sendMessage(Component.text("Solde insuffisant.", NamedTextColor.RED));
                return true;
            }
            pointsManager.save();
            sender.sendMessage(Component.text("-" + amount + " points sur " + target.getName(), NamedTextColor.GREEN));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            int n = 10;
            if (args.length >= 2) {
                try {
                    n = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Nombre invalide.", NamedTextColor.RED));
                    return true;
                }
            }
            n = Math.max(3, Math.min(15, n));
            List<Map.Entry<UUID, Integer>> top = pointsManager.getTop(n);
            sender.sendMessage(Component.text("--- Top " + n + " points ---", NamedTextColor.GOLD));
            int rank = 1;
            for (Map.Entry<UUID, Integer> e : top) {
                String name = pointsManager.resolveName(e.getKey());
                sender.sendMessage(Component.text(rank + ". " + name + " — " + e.getValue(), NamedTextColor.YELLOW));
                rank++;
            }
            if (top.isEmpty()) {
                sender.sendMessage(Component.text("(vide)", NamedTextColor.DARK_GRAY));
            }
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("topkills")) {
            int n = 10;
            if (args.length >= 2) {
                try {
                    n = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Nombre invalide.", NamedTextColor.RED));
                    return true;
                }
            }
            n = Math.max(3, Math.min(15, n));
            List<Map.Entry<UUID, Integer>> top = pointsManager.getTopKills(n);
            sender.sendMessage(Component.text("--- Top " + n + " kills ---", NamedTextColor.GOLD));
            int rank = 1;
            for (Map.Entry<UUID, Integer> e : top) {
                String name = pointsManager.resolveName(e.getKey());
                sender.sendMessage(Component.text(rank + ". " + name + " — " + e.getValue(), NamedTextColor.YELLOW));
                rank++;
            }
            if (top.isEmpty()) {
                sender.sendMessage(Component.text("(vide)", NamedTextColor.DARK_GRAY));
            }
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Usage: /points [player] | /points top [n] | /points topkills [n]");
                return true;
            }
            int points = pointsManager.getPoints(player.getUniqueId());
            sender.sendMessage(Component.text("Tu as " + points + " points.", NamedTextColor.GOLD));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        int points = pointsManager.getPoints(target.getUniqueId());
        sender.sendMessage(Component.text(target.getName() + " a " + points + " points.", NamedTextColor.YELLOW));
        return true;
    }
}
