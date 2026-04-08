package fr.dpmr.hdv;

import fr.dpmr.data.PointsManager;
import fr.dpmr.game.WeaponManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class HdvGui implements Listener {

    private static final Component TITLE = Component.text("HDV — Annonces", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final Component TITLE_SELL = Component.text("HDV — Vendre", NamedTextColor.GREEN, TextDecoration.BOLD);

    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_SELL = 47;

    private static final int SELL_DEPOSIT = 22;
    private static final int SELL_PRICE_1 = 20;
    private static final int SELL_PRICE_10 = 21;
    private static final int SELL_PRICE_100 = 23;
    private static final int SELL_PRICE_1000 = 24;
    private static final int SELL_CONFIRM = 31;
    private static final int SELL_INFO = 13;

    private final JavaPlugin plugin;
    private final HdvManager hdvManager;
    private final PointsManager pointsManager;
    private final WeaponManager weaponManager;

    private final Map<UUID, Integer> page = new HashMap<>();
    private final Map<UUID, Integer> pendingPrice = new HashMap<>();

    public HdvGui(JavaPlugin plugin, HdvManager hdvManager, PointsManager pointsManager, WeaponManager weaponManager) {
        this.plugin = plugin;
        this.hdvManager = hdvManager;
        this.pointsManager = pointsManager;
        this.weaponManager = weaponManager;
    }

    public void open(Player player) {
        int p = page.getOrDefault(player.getUniqueId(), 0);
        openPage(player, p);
    }

    private void openPage(Player player, int p) {
        page.put(player.getUniqueId(), Math.max(0, p));
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fillGlass(inv);

        List<HdvListing> listings = hdvManager.allListings();
        int per = 45;
        int from = p * per;
        int to = Math.min(listings.size(), from + per);
        int slot = 0;
        for (int i = from; i < to; i++) {
            HdvListing l = listings.get(i);
            ItemStack it = l.item().clone();
            ItemMeta meta = it.getItemMeta();
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.text(" ", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Prix: " + l.price() + " points", NamedTextColor.GOLD, TextDecoration.BOLD));
            lore.add(Component.text("Vendeur: " + pointsManager.resolveName(l.seller()), NamedTextColor.GRAY));
            lore.add(Component.text("ID: " + l.id(), NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Clic: acheter", NamedTextColor.GREEN));
            meta.lore(lore);
            it.setItemMeta(meta);
            inv.setItem(slot++, it);
        }

        inv.setItem(SLOT_PREV, button(Material.ARROW, "Page precedente", NamedTextColor.YELLOW));
        inv.setItem(SLOT_NEXT, button(Material.ARROW, "Page suivante", NamedTextColor.YELLOW));
        inv.setItem(SLOT_SELL, button(Material.EMERALD, "Vendre", NamedTextColor.GREEN));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "Fermer", NamedTextColor.RED));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.4f, 1.1f);
    }

    public void openSell(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_SELL);
        fillGlass(inv);
        pendingPrice.putIfAbsent(player.getUniqueId(), 100);
        inv.setItem(SELL_INFO, sellInfo(player));
        inv.setItem(SELL_CONFIRM, sellConfirm(player));
        inv.setItem(SELL_PRICE_1, button(Material.LIME_DYE, "+1", NamedTextColor.GREEN));
        inv.setItem(SELL_PRICE_10, button(Material.GREEN_DYE, "+10", NamedTextColor.GREEN));
        inv.setItem(SELL_PRICE_100, button(Material.EMERALD, "+100", NamedTextColor.GREEN));
        inv.setItem(SELL_PRICE_1000, button(Material.EMERALD_BLOCK, "+1000", NamedTextColor.GREEN));
        inv.setItem(SELL_DEPOSIT, null);
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "Fermer", NamedTextColor.RED));
        player.openInventory(inv);
    }

    private ItemStack sellInfo(Player player) {
        int price = pendingPrice.getOrDefault(player.getUniqueId(), 100);
        ItemStack i = new ItemStack(Material.PAPER);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text("Mise en vente", NamedTextColor.GOLD));
        m.lore(List.of(
                Component.text("Prix: " + price + " points", NamedTextColor.YELLOW),
                Component.text("Depose l'arme au centre", NamedTextColor.GRAY),
                Component.text("Puis confirme", NamedTextColor.DARK_GRAY)
        ));
        i.setItemMeta(m);
        return i;
    }

    private ItemStack sellConfirm(Player player) {
        int price = pendingPrice.getOrDefault(player.getUniqueId(), 100);
        ItemStack i = new ItemStack(Material.ANVIL);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text("Confirmer (" + price + ")", NamedTextColor.GREEN, TextDecoration.BOLD));
        m.lore(List.of(Component.text("Clic: mettre en vente", NamedTextColor.GRAY)));
        i.setItemMeta(m);
        return i;
    }

    private static ItemStack button(Material mat, String name, NamedTextColor color) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text(name, color));
        i.setItemMeta(m);
        return i;
    }

    private static void fillGlass(Inventory inv) {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta();
        m.displayName(Component.text(" "));
        g.setItemMeta(m);
        for (int s = 0; s < 54; s++) {
            if (inv.getItem(s) == null) {
                inv.setItem(s, g.clone());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Component t = event.getView().title();
        if (TITLE.equals(t) || TITLE_SELL.equals(t)) {
            // on autorise le drag uniquement dans le slot dépôt du sell
            if (TITLE_SELL.equals(t)) {
                for (int raw : event.getRawSlots()) {
                    if (raw < 54 && raw != SELL_DEPOSIT) {
                        event.setCancelled(true);
                        return;
                    }
                }
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Component t = event.getView().title();
        if (!TITLE.equals(t) && !TITLE_SELL.equals(t)) {
            return;
        }

        if (TITLE_SELL.equals(t)) {
            handleSellClick(event, player);
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int raw = event.getRawSlot();
        if (raw == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (raw == SLOT_SELL) {
            openSell(player);
            return;
        }
        if (raw == SLOT_PREV) {
            openPage(player, Math.max(0, page.getOrDefault(player.getUniqueId(), 0) - 1));
            return;
        }
        if (raw == SLOT_NEXT) {
            openPage(player, page.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        ItemStack cur = event.getCurrentItem();
        if (cur == null || cur.getType().isAir() || cur.getItemMeta() == null || cur.getItemMeta().lore() == null) {
            return;
        }
        String listingId = null;
        for (Component line : Objects.requireNonNull(cur.getItemMeta().lore())) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.startsWith("ID: ")) {
                listingId = plain.substring(4).trim();
                break;
            }
        }
        if (listingId == null) {
            return;
        }
        if (hdvManager.buy(player, listingId)) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.3f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.9f);
        }
        open(player);
    }

    private void handleSellClick(InventoryClickEvent event, Player player) {
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();

        // dépôt: libre
        if (raw == SELL_DEPOSIT && event.getClickedInventory() == top) {
            event.setCancelled(false);
            Bukkit.getScheduler().runTask(plugin, () -> {
                top.setItem(SELL_INFO, sellInfo(player));
                top.setItem(SELL_CONFIRM, sellConfirm(player));
            });
            return;
        }

        event.setCancelled(true);

        if (raw == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        int inc = switch (raw) {
            case SELL_PRICE_1 -> 1;
            case SELL_PRICE_10 -> 10;
            case SELL_PRICE_100 -> 100;
            case SELL_PRICE_1000 -> 1000;
            default -> 0;
        };
        if (inc > 0) {
            int cur = pendingPrice.getOrDefault(player.getUniqueId(), 100);
            pendingPrice.put(player.getUniqueId(), Math.min(1_000_000, cur + inc));
            top.setItem(SELL_INFO, sellInfo(player));
            top.setItem(SELL_CONFIRM, sellConfirm(player));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
            return;
        }

        if (raw == SELL_CONFIRM) {
            ItemStack deposited = top.getItem(SELL_DEPOSIT);
            if (deposited == null || deposited.getType().isAir()) {
                player.sendActionBar(Component.text("Depose un objet.", NamedTextColor.RED));
                return;
            }
            // Option: limiter aux armes DPMR si tu veux
            String id = weaponManager.readWeaponId(deposited);
            if (id == null) {
                player.sendActionBar(Component.text("HDV: uniquement armes DPMR pour l'instant.", NamedTextColor.RED));
                return;
            }

            int price = pendingPrice.getOrDefault(player.getUniqueId(), 100);
            ItemStack one = deposited.clone();
            one.setAmount(1);

            String listingId = hdvManager.createListing(player, one, price);
            if (listingId == null) {
                return;
            }
            // Retirer 1 item du dépôt
            if (deposited.getAmount() <= 1) {
                top.setItem(SELL_DEPOSIT, null);
            } else {
                deposited.setAmount(deposited.getAmount() - 1);
                top.setItem(SELL_DEPOSIT, deposited);
            }
            player.sendMessage(Component.text("Annonce creee: " + listingId + " (" + price + " pts)", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
            top.setItem(SELL_INFO, sellInfo(player));
            top.setItem(SELL_CONFIRM, sellConfirm(player));
            return;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        page.remove(uuid);
        pendingPrice.remove(uuid);
    }

    @EventHandler
    public void onSellClose(InventoryCloseEvent event) {
        if (!TITLE_SELL.equals(event.getView().title())) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        ItemStack deposited = event.getView().getTopInventory().getItem(SELL_DEPOSIT);
        if (deposited == null || deposited.getType().isAir()) {
            return;
        }
        event.getView().getTopInventory().setItem(SELL_DEPOSIT, null);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(deposited.clone());
        if (!leftover.isEmpty()) {
            leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
        }
    }
}

