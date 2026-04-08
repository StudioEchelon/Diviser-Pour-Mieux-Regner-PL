package fr.dpmr.clan;

import fr.dpmr.data.ClanManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ClanMenus {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public static final Component TITLE_MAIN = Component.text("Clan Hub", NamedTextColor.GOLD, TextDecoration.BOLD);
    public static final Component TITLE_INVITE = Component.text("Invite a player", NamedTextColor.AQUA, TextDecoration.BOLD);

    private static final String PLAIN_MAIN = PLAIN.serialize(TITLE_MAIN);
    private static final String PLAIN_INVITE = PLAIN.serialize(TITLE_INVITE);

    /** Paper often wraps titles; {@link Component#equals(Object)} against a constant fails. Compare plain text. */
    public static boolean isClanHubTitle(Component viewTitle) {
        return viewTitle != null && PLAIN.serialize(viewTitle).equals(PLAIN_MAIN);
    }

    public static boolean isInvitePickerTitle(Component viewTitle) {
        return viewTitle != null && PLAIN.serialize(viewTitle).equals(PLAIN_INVITE);
    }

    private static final int SIZE = 45;
    private static final int SLOT_CLOSE = 40;
    private static final int SLOT_CREATE = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_LEAVE = 15;
    private static final int SLOT_INVITE_ONE = 21;
    private static final int SLOT_INVITE_ALL = 23;
    private static final int SLOT_ACCEPT = 29;
    private static final int SLOT_DENY = 31;
    private static final int SLOT_TOP = 19;

    private ClanMenus() {
    }

    public static void openMain(Player player, ClanManager clans) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE_MAIN);
        fillBorder(inv);
        String myClan = clans.getPlayerClan(player.getUniqueId());
        String pending = clans.getPendingInvite(player.getUniqueId());

        inv.setItem(4, icon(Material.NETHER_STAR,
                Component.text("Your clan", NamedTextColor.YELLOW, TextDecoration.BOLD),
                List.of(Component.text(myClan != null ? myClan : "— None —", NamedTextColor.WHITE))));

        inv.setItem(SLOT_CREATE, icon(Material.EMERALD_BLOCK,
                Component.text("Create a clan", NamedTextColor.GREEN, TextDecoration.BOLD),
                List.of(
                        Component.text("Chat: type a name (letters, numbers, _ -)", NamedTextColor.GRAY),
                        Component.text("2-18 characters. You must not be in a clan.", NamedTextColor.DARK_GRAY)
                )));

        inv.setItem(SLOT_INFO, icon(Material.BOOK,
                Component.text("Clan info", NamedTextColor.AQUA, TextDecoration.BOLD),
                List.of(Component.text("Members and details in chat.", NamedTextColor.GRAY))));

        inv.setItem(SLOT_LEAVE, icon(Material.RED_BED,
                Component.text("Leave clan", NamedTextColor.RED, TextDecoration.BOLD),
                List.of(Component.text("Leave your current clan.", NamedTextColor.GRAY))));

        inv.setItem(SLOT_INVITE_ONE, icon(Material.PLAYER_HEAD,
                Component.text("Invite someone", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                List.of(
                        Component.text("Pick an online player.", NamedTextColor.GRAY),
                        Component.text("Requires: you are in a clan.", NamedTextColor.DARK_GRAY)
                )));

        inv.setItem(SLOT_INVITE_ALL, icon(Material.BEACON,
                Component.text("Invite everyone online", NamedTextColor.GOLD, TextDecoration.BOLD),
                List.of(
                        Component.text("Sends an invite to all players", NamedTextColor.GRAY),
                        Component.text("who are not in a clan yet.", NamedTextColor.GRAY)
                )));

        List<Component> acceptLore = new ArrayList<>();
        if (pending != null) {
            acceptLore.add(Component.text("From clan: ", NamedTextColor.GRAY).append(Component.text(pending, NamedTextColor.GREEN)));
        } else {
            acceptLore.add(Component.text("No pending invite.", NamedTextColor.DARK_GRAY));
        }
        inv.setItem(SLOT_ACCEPT, icon(Material.LIME_CONCRETE,
                Component.text("Accept invite", NamedTextColor.GREEN, TextDecoration.BOLD),
                acceptLore));

        inv.setItem(SLOT_DENY, icon(Material.BARRIER,
                Component.text("Decline invite", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                List.of(Component.text("Clear pending clan invite.", NamedTextColor.GRAY))));

        inv.setItem(SLOT_TOP, icon(Material.GOLD_INGOT,
                Component.text("Top clans", NamedTextColor.GOLD, TextDecoration.BOLD),
                List.of(Component.text("Largest clans by member count.", NamedTextColor.GRAY))));

        inv.setItem(SLOT_CLOSE, icon(Material.IRON_DOOR,
                Component.text("Close", NamedTextColor.WHITE, TextDecoration.BOLD),
                List.of(Component.text("Return to the game.", NamedTextColor.DARK_GRAY))));

        player.openInventory(inv);
    }

    public static void openInvitePicker(Player player, ClanManager clans) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE_INVITE);
        fillBorder(inv);
        int[] innerSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
        int idx = 0;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            if (clans.getPlayerClan(other.getUniqueId()) != null) {
                continue;
            }
            if (idx >= innerSlots.length) {
                break;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.displayName(Component.text(other.getName(), NamedTextColor.WHITE, TextDecoration.BOLD));
            sm.lore(List.of(
                    Component.text("Click to send invite", NamedTextColor.GREEN),
                    Component.text("to your clan.", NamedTextColor.GRAY)
            ));
            sm.setOwningPlayer(other);
            head.setItemMeta(sm);
            inv.setItem(innerSlots[idx++], head);
        }
        inv.setItem(SLOT_CLOSE, icon(Material.ARROW,
                Component.text("Back", NamedTextColor.GRAY, TextDecoration.BOLD),
                List.of()));
        player.openInventory(inv);
    }

    private static void fillBorder(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.displayName(Component.text(" ", NamedTextColor.WHITE));
        pane.setItemMeta(m);
        int rows = SIZE / 9;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) {
                if (r == 0 || r == rows - 1 || c == 0 || c == 8) {
                    inv.setItem(r * 9 + c, pane.clone());
                }
            }
        }
    }

    private static ItemStack icon(Material mat, Component name, List<Component> lore) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        m.displayName(name);
        if (lore != null && !lore.isEmpty()) {
            m.lore(lore);
        }
        s.setItemMeta(m);
        return s;
    }

    public static void printClanInfo(Player player, ClanManager clans) {
        String clan = clans.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(Component.text("You are not in a clan.", NamedTextColor.RED));
            return;
        }
        Set<UUID> mem = clans.getMembers(clan);
        player.sendMessage(Component.text("Clan: ", NamedTextColor.AQUA).append(Component.text(clan, NamedTextColor.WHITE, TextDecoration.BOLD)));
        player.sendMessage(Component.text("Members (" + mem.size() + "):", NamedTextColor.GRAY));
        for (UUID id : mem) {
            Player p = Bukkit.getPlayer(id);
            String label = p != null ? p.getName() : id.toString().substring(0, 8) + "…";
            boolean lead = clans.getLeader(clan) != null && clans.getLeader(clan).equals(id);
            player.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(label + (lead ? " (leader)" : ""), NamedTextColor.WHITE)));
        }
    }

    public static void printTopClans(Player player, ClanManager clans) {
        player.sendMessage(Component.text("══════ Top clans ══════", NamedTextColor.GOLD, TextDecoration.BOLD));
        clans.getClans().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(10)
                .forEach(e -> player.sendMessage(Component.text(e.getKey(), NamedTextColor.YELLOW)
                        .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(e.getValue().size() + " members", NamedTextColor.WHITE))));
    }
}
