package fr.dpmr.command;

import fr.dpmr.clan.ClanMenus;
import fr.dpmr.clan.ClanMenuListener;
import fr.dpmr.data.ClanManager;
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
import java.util.stream.Collectors;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final ClanManager clanManager;

    public ClanCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command is for players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            ClanMenus.openMain(player, clanManager);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "gui" -> ClanMenus.openMain(player, clanManager);
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /clan create <name>", NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("Or open ", NamedTextColor.GRAY)
                            .append(Component.text("/clan", NamedTextColor.GOLD))
                            .append(Component.text(" and use Create in the menu.", NamedTextColor.GRAY)));
                    return true;
                }
                String name = args[1];
                if (clanManager.createClan(name, player.getUniqueId())) {
                    clanManager.save();
                    player.sendMessage(Component.text("Clan ", NamedTextColor.GREEN)
                            .append(Component.text(name, NamedTextColor.WHITE))
                            .append(Component.text(" created! You are the leader.", NamedTextColor.GREEN)));
                } else {
                    player.sendMessage(Component.text("Could not create that clan (invalid name, taken, or you are already in a clan).", NamedTextColor.RED));
                }
            }
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /clan join <clan>", NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("You need an invite to that clan (check chat for [ACCEPT]).", NamedTextColor.GRAY));
                    return true;
                }
                String wanted = args[1];
                String pending = clanManager.getPendingInvite(player.getUniqueId());
                if (pending == null || !pending.equalsIgnoreCase(wanted)) {
                    player.sendMessage(Component.text("You have no pending invite for that clan.", NamedTextColor.RED));
                    return true;
                }
                if (clanManager.acceptInvite(player.getUniqueId())) {
                    clanManager.save();
                    player.sendMessage(Component.text("You joined ", NamedTextColor.GREEN)
                            .append(Component.text(pending, NamedTextColor.WHITE))
                            .append(Component.text("!", NamedTextColor.GREEN)));
                } else {
                    player.sendMessage(Component.text("Could not join (clan may no longer exist).", NamedTextColor.RED));
                }
            }
            case "leave" -> {
                if (clanManager.leaveClan(player.getUniqueId())) {
                    clanManager.save();
                    player.sendMessage(Component.text("You left your clan.", NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("You are not in a clan.", NamedTextColor.RED));
                }
            }
            case "info" -> ClanMenus.printClanInfo(player, clanManager);
            case "top" -> ClanMenus.printTopClans(player, clanManager);
            case "accept" -> {
                if (clanManager.acceptInvite(player.getUniqueId())) {
                    clanManager.save();
                    player.sendMessage(Component.text("You joined the clan!", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("No pending invite.", NamedTextColor.RED));
                }
            }
            case "deny", "decline" -> {
                if (clanManager.denyInvite(player.getUniqueId())) {
                    player.sendMessage(Component.text("Invite declined.", NamedTextColor.GRAY));
                } else {
                    player.sendMessage(Component.text("No pending invite.", NamedTextColor.RED));
                }
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /clan invite <player>", NamedTextColor.YELLOW));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED));
                    return true;
                }
                String clan = clanManager.getPlayerClan(player.getUniqueId());
                if (clan == null || !clanManager.isLeader(player.getUniqueId())) {
                    player.sendMessage(Component.text("Only the clan leader can invite.", NamedTextColor.RED));
                    return true;
                }
                if (clanManager.invitePlayer(player.getUniqueId(), target.getUniqueId())) {
                    clanManager.save();
                    player.sendMessage(Component.text("Invite sent to ", NamedTextColor.GREEN)
                            .append(Component.text(target.getName(), NamedTextColor.WHITE))
                            .append(Component.text(".", NamedTextColor.GREEN)));
                    ClanMenuListener.sendInviteMessage(target, clan);
                } else {
                    player.sendMessage(Component.text("Could not invite that player.", NamedTextColor.RED));
                }
            }
            case "inviteall" -> {
                if (clanManager.getPlayerClan(player.getUniqueId()) == null || !clanManager.isLeader(player.getUniqueId())) {
                    player.sendMessage(Component.text("Only the clan leader can mass-invite.", NamedTextColor.RED));
                    return true;
                }
                String clan = clanManager.getPlayerClan(player.getUniqueId());
                List<java.util.UUID> invited = clanManager.inviteAllOnline(player.getUniqueId());
                for (java.util.UUID id : invited) {
                    Player t = Bukkit.getPlayer(id);
                    if (t != null && t.isOnline() && clan != null) {
                        ClanMenuListener.sendInviteMessage(t, clan);
                    }
                }
                clanManager.save();
                player.sendMessage(Component.text("Sent " + invited.size() + " invite(s).", NamedTextColor.GREEN));
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /clan kick <player>", NamedTextColor.YELLOW));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED));
                    return true;
                }
                if (clanManager.kickMember(player.getUniqueId(), target.getUniqueId())) {
                    clanManager.save();
                    player.sendMessage(Component.text("Removed ", NamedTextColor.YELLOW)
                            .append(Component.text(target.getName(), NamedTextColor.WHITE))
                            .append(Component.text(" from the clan.", NamedTextColor.YELLOW)));
                    target.sendMessage(Component.text("You were removed from your clan.", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("You cannot kick that player.", NamedTextColor.RED));
                }
            }
            default -> player.sendMessage(Component.text("Unknown subcommand. Try /clan", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return List.of("create", "join", "leave", "info", "top", "accept", "deny", "invite", "inviteall", "kick", "menu")
                    .stream()
                    .filter(s -> s.startsWith(p))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("invite") || sub.equals("kick")) {
                String frag = args[1].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    if (pl.getName().toLowerCase(Locale.ROOT).startsWith(frag)) {
                        out.add(pl.getName());
                    }
                }
                return out;
            }
            if (sub.equals("join") && sender instanceof Player pl) {
                String pending = clanManager.getPendingInvite(pl.getUniqueId());
                if (pending != null && pending.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    return List.of(pending);
                }
            }
        }
        return List.of();
    }
}
