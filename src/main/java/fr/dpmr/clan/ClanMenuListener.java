package fr.dpmr.clan;

import fr.dpmr.data.ClanManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanMenuListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final JavaPlugin plugin;
    private final ClanManager clans;
    private final Map<UUID, Boolean> awaitingName = new ConcurrentHashMap<>();

    public ClanMenuListener(JavaPlugin plugin, ClanManager clans) {
        this.plugin = plugin;
        this.clans = clans;
    }

    public void startCreateFlow(Player player) {
        awaitingName.put(player.getUniqueId(), true);
        player.closeInventory();
        player.sendMessage(Component.text("Type your clan name in chat (2-18 chars, letters, numbers, _ -). Type ", NamedTextColor.GRAY)
                .append(Component.text("cancel", NamedTextColor.RED))
                .append(Component.text(" to abort.", NamedTextColor.GRAY)));
    }

    public boolean isAwaitingName(UUID id) {
        return awaitingName.containsKey(id);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!awaitingName.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String msg = PLAIN.serialize(event.message()).trim();
        handleNameChat(event.getPlayer(), msg);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!awaitingName.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        handleNameChat(event.getPlayer(), event.getMessage().trim());
    }

    private void handleNameChat(Player p, String msg) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!awaitingName.containsKey(p.getUniqueId())) {
                return;
            }
            awaitingName.remove(p.getUniqueId());
            if (msg.equalsIgnoreCase("cancel")) {
                p.sendMessage(Component.text("Clan creation cancelled.", NamedTextColor.GRAY));
                return;
            }
            if (!clans.isValidClanName(msg)) {
                p.sendMessage(Component.text("Invalid name. Use 2-18 characters (letters, numbers, _ -).", NamedTextColor.RED));
                return;
            }
            if (clans.getPlayerClan(p.getUniqueId()) != null) {
                p.sendMessage(Component.text("You are already in a clan.", NamedTextColor.RED));
                return;
            }
            if (clans.createClan(msg, p.getUniqueId())) {
                clans.saveAsync();
                p.sendMessage(Component.text("Clan ", NamedTextColor.GREEN)
                        .append(Component.text(msg, NamedTextColor.WHITE))
                        .append(Component.text(" created! You are the leader.", NamedTextColor.GREEN)));
            } else {
                p.sendMessage(Component.text("That clan name is taken or invalid.", NamedTextColor.RED));
            }
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Component title = event.getView().title();
        if (ClanMenus.isClanHubTitle(title) || ClanMenus.isInvitePickerTitle(title)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Component title = event.getView().title();
        if (ClanMenus.isClanHubTitle(title)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            int slot = event.getRawSlot();
            switch (slot) {
                case 11 -> startCreateFlow(player);
                case 13 -> {
                    player.closeInventory();
                    ClanMenus.printClanInfo(player, clans);
                }
                case 15 -> {
                    player.closeInventory();
                    if (clans.leaveClan(player.getUniqueId())) {
                        clans.saveAsync();
                        player.sendMessage(Component.text("You left your clan.", NamedTextColor.YELLOW));
                    } else {
                        player.sendMessage(Component.text("You are not in a clan.", NamedTextColor.RED));
                    }
                }
                case 19 -> {
                    player.closeInventory();
                    ClanMenus.printTopClans(player, clans);
                }
                case 21 -> {
                    if (clans.getPlayerClan(player.getUniqueId()) == null) {
                        player.sendMessage(Component.text("Join or create a clan first.", NamedTextColor.RED));
                        return;
                    }
                    if (!clans.isLeader(player.getUniqueId())) {
                        player.sendMessage(Component.text("Only the clan leader can invite.", NamedTextColor.RED));
                        return;
                    }
                    ClanMenus.openInvitePicker(player, clans);
                }
                case 23 -> {
                    if (clans.getPlayerClan(player.getUniqueId()) == null) {
                        player.sendMessage(Component.text("Join or create a clan first.", NamedTextColor.RED));
                        return;
                    }
                    if (!clans.isLeader(player.getUniqueId())) {
                        player.sendMessage(Component.text("Only the clan leader can invite.", NamedTextColor.RED));
                        return;
                    }
                    List<UUID> invited = clans.inviteAllOnline(player.getUniqueId());
                    String clan = clans.getPlayerClan(player.getUniqueId());
                    for (UUID id : invited) {
                        Player t = Bukkit.getPlayer(id);
                        if (t != null && t.isOnline() && clan != null) {
                            sendInviteMessage(t, clan);
                        }
                    }
                    clans.saveAsync();
                    player.sendMessage(Component.text("Sent " + invited.size() + " invite(s) to players without a clan.", NamedTextColor.GREEN));
                }
                case 29 -> {
                    player.closeInventory();
                    if (clans.acceptInvite(player.getUniqueId())) {
                        clans.saveAsync();
                        player.sendMessage(Component.text("You joined the clan!", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("No pending invite.", NamedTextColor.RED));
                    }
                }
                case 31 -> {
                    player.closeInventory();
                    if (clans.denyInvite(player.getUniqueId())) {
                        player.sendMessage(Component.text("Invite declined.", NamedTextColor.GRAY));
                    } else {
                        player.sendMessage(Component.text("No pending invite.", NamedTextColor.RED));
                    }
                }
                case 40 -> player.closeInventory();
                default -> {
                }
            }
            return;
        }
        if (ClanMenus.isInvitePickerTitle(title)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            int slot = event.getRawSlot();
            if (slot == 40) {
                ClanMenus.openMain(player, clans);
                return;
            }
            ItemStack it = event.getCurrentItem();
            if (it == null || it.getItemMeta() == null) {
                return;
            }
            if (!(it.getItemMeta() instanceof SkullMeta sm)) {
                return;
            }
            Player target = null;
            if (sm.getOwningPlayer() != null) {
                target = Bukkit.getPlayer(sm.getOwningPlayer().getUniqueId());
            }
            if (target == null || !target.isOnline()) {
                player.sendMessage(Component.text("That player is no longer online.", NamedTextColor.RED));
                return;
            }
            if (clans.getPlayerClan(player.getUniqueId()) == null || !clans.isLeader(player.getUniqueId())) {
                player.sendMessage(Component.text("You cannot invite.", NamedTextColor.RED));
                return;
            }
            String clan = clans.getPlayerClan(player.getUniqueId());
            if (clan != null && clans.invitePlayer(player.getUniqueId(), target.getUniqueId())) {
                clans.saveAsync();
                player.sendMessage(Component.text("Invite sent to ", NamedTextColor.GREEN)
                        .append(Component.text(target.getName(), NamedTextColor.WHITE))
                        .append(Component.text(".", NamedTextColor.GREEN)));
                sendInviteMessage(target, clan);
            } else {
                player.sendMessage(Component.text("Could not send invite (already invited, in clan, or invalid).", NamedTextColor.RED));
            }
        }
    }

    public static void sendInviteMessage(Player target, String clan) {
        target.sendMessage(Component.text("You were invited to join ", NamedTextColor.AQUA)
                .append(Component.text(clan, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(". ", NamedTextColor.AQUA)));
        target.sendMessage(Component.text("[ACCEPT]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/clan accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Run /clan accept", NamedTextColor.GRAY)))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("[DECLINE]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/clan deny"))
                        .hoverEvent(HoverEvent.showText(Component.text("Run /clan deny", NamedTextColor.GRAY)))));
    }
}
