package fr.dpmr.gui;

import fr.dpmr.data.PointsManager;
import fr.dpmr.game.WeaponManager;
import fr.dpmr.game.WeaponProfile;
import fr.dpmr.game.WeaponRarity;
import fr.dpmr.npc.NpcSkins;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopGui implements Listener {

    private static final int SLOT_BACK = 49;
    private static final int SLOT_SELL_HUB = 31;
    private static final int[] HUB_RARITY_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    /** Coloured tier panes directly under each rarity head (row 3). */
    private static final int[] HUB_RARITY_GLASS_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    private static final int SELL_DEPOSIT_SLOT = 22;
    private static final int SELL_BUTTON_SLOT = 31;
    private static final int SELL_ALL_BUTTON_SLOT = 33;
    private static final int SELL_INFO_SLOT = 13;
    private static final int BUY_SHOWCASE_SLOT = 22;
    private static final int[] WEAPON_SLOTS = {
            10, 11, 12, 13, 14,
            19, 20, 21, 23, 24,
            28, 29, 30, 31, 32
    };

    private final JavaPlugin plugin;
    private final WeaponManager weaponManager;
    private final PointsManager pointsManager;

    public ShopGui(JavaPlugin plugin, WeaponManager weaponManager, PointsManager pointsManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
        this.pointsManager = pointsManager;
    }

    public void openHub(Player player) {
        Inventory inv = BoutiqueHolder.create(54, BoutiqueUi.titleHub(), BoutiqueHolder.Kind.HUB, null);
        WeaponRarity[] order = WeaponRarity.values();
        for (int i = 0; i < order.length && i < HUB_RARITY_SLOTS.length; i++) {
            int count = BoutiqueUi.countWeapons(order[i]);
            inv.setItem(HUB_RARITY_SLOTS[i], BoutiqueUi.hubRarityButton(order[i], count));
            if (i < HUB_RARITY_GLASS_SLOTS.length) {
                inv.setItem(HUB_RARITY_GLASS_SLOTS[i], BoutiqueUi.hubRarityTierPane(order[i], count));
            }
        }
        inv.setItem(SLOT_SELL_HUB, hubSellButton());
        inv.setItem(SLOT_BACK, closeItem());
        BoutiqueUi.fillBackdrop(inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.4f, 1.1f);
    }

    public void openSellPage(Player player) {
        Inventory inv = BoutiqueHolder.create(54, BoutiqueUi.titleSell(), BoutiqueHolder.Kind.SELL, null);
        inv.setItem(45, backHubShop());
        inv.setItem(SLOT_BACK, closeItem());
        inv.setItem(SELL_INFO_SLOT, sellInfoHead(player.getUniqueId(), null));
        inv.setItem(SELL_BUTTON_SLOT, sellButtonItem(false, 0));
        inv.setItem(SELL_ALL_BUTTON_SLOT, sellAllButtonItem());
        BoutiqueUi.fillBackdrop(inv);
        inv.setItem(SELL_DEPOSIT_SLOT, null);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.35f, 1.25f);
    }

    public void openShopPage(Player player, WeaponRarity rarity) {
        Inventory inv = BoutiqueHolder.create(54, BoutiqueUi.titleBuyCategory(rarity), BoutiqueHolder.Kind.BUY_CATEGORY, rarity);
        List<WeaponProfile> list = new ArrayList<>();
        for (WeaponProfile w : WeaponProfile.values()) {
            if (w.rarity() == rarity) {
                list.add(w);
            }
        }
        list.sort(Comparator.comparing(Enum::name));
        int n = Math.min(WEAPON_SLOTS.length, list.size());
        FileConfiguration cfg = plugin.getConfig();
        for (int i = 0; i < n; i++) {
            WeaponProfile w = list.get(i);
            int price = cfg.getInt("boutique.prices." + w.name(), defaultBuyPriceByRarity(rarity));
            ItemStack gun = weaponManager.createWeaponItem(w.name(), player);
            if (gun != null) {
                ItemMeta meta = gun.getItemMeta();
                if (meta == null) {
                    inv.setItem(WEAPON_SLOTS[i], gun);
                    continue;
                }
                List<Component> lore = meta.lore();
                List<Component> nl = new ArrayList<>();
                if (lore != null) {
                    nl.addAll(lore);
                }
                nl.add(Component.empty());
                nl.add(Component.text("―――――――――――――", NamedTextColor.DARK_GRAY));
                nl.add(Component.text("◇ ", rarity.color())
                        .append(Component.text("Price  ", NamedTextColor.GRAY))
                        .append(Component.text(price + " pts", NamedTextColor.GOLD, TextDecoration.BOLD)));
                nl.add(Component.text("Left-click to purchase", TextColor.color(0x6BCB8A)));
                meta.lore(nl);
                gun.setItemMeta(meta);
                inv.setItem(WEAPON_SLOTS[i], gun);
            }
        }
        inv.setItem(BUY_SHOWCASE_SLOT, BoutiqueUi.categoryShowcase(rarity, list.size()));
        inv.setItem(45, backHubShop());
        inv.setItem(SLOT_BACK, closeItem());
        BoutiqueUi.fillBackdrop(inv);
        player.openInventory(inv);
    }

    private static ItemStack hubSellButton() {
        ItemStack sk = NpcSkins.playerHeadFromTextureHash(
                "10457243a1e3e23bff45c0efd5628fec95993f53f2f1aaa96877d0bb761412bf", "dpmr-boutique-sell");
        ItemMeta m = sk.getItemMeta();
        m.displayName(Component.text("Weapon trade-in", TextColor.color(0xE8C170), TextDecoration.BOLD));
        m.lore(List.of(
                Component.text("Sell DPMR weapons for points", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to open", TextColor.color(0x9AA0A6))
        ));
        sk.setItemMeta(m);
        return sk;
    }

    private int buyPrice(String weaponId) {
        FileConfiguration cfg = plugin.getConfig();
        WeaponProfile w = WeaponProfile.fromId(weaponId);
        int def = defaultBuyPriceByRarity(w != null ? w.rarity() : WeaponRarity.COMMON);
        return cfg.getInt("boutique.prices." + weaponId, def);
    }

    private int sellPrice(String weaponId) {
        FileConfiguration cfg = plugin.getConfig();
        int override = cfg.getInt("boutique.sell-prices." + weaponId, -1);
        if (override >= 0) {
            return Math.max(0, override);
        }
        WeaponProfile w = WeaponProfile.fromId(weaponId);
        int defSell = defaultSellPriceByRarity(w != null ? w.rarity() : WeaponRarity.COMMON);
        double mult = cfg.getDouble("boutique.sell-multiplier", 0.5);
        int computed = (int) Math.round(buyPrice(weaponId) * mult);
        return Math.max(1, Math.max(defSell, computed));
    }

    private static int defaultBuyPriceByRarity(WeaponRarity rarity) {
        return switch (rarity) {
            case COMMON -> 45;
            case UNCOMMON -> 140;
            case RARE -> 420;
            case EPIC -> 1050;
            case LEGENDARY -> 2600;
            case MYTHIC -> 4200;
            case GHOST -> 6000;
        };
    }

    private static int defaultSellPriceByRarity(WeaponRarity rarity) {
        return switch (rarity) {
            case COMMON -> 18;
            case UNCOMMON -> 55;
            case RARE -> 170;
            case EPIC -> 420;
            case LEGENDARY -> 980;
            case MYTHIC -> 1680;
            case GHOST -> 2400;
        };
    }

    private ItemStack sellButtonItem(boolean enabled, int price) {
        Material mat = enabled ? Material.EMERALD_BLOCK : Material.GRAY_DYE;
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(enabled
                ? Component.text("Confirm sale · " + price + " pts", NamedTextColor.GREEN, TextDecoration.BOLD)
                : Component.text("Deposit a weapon in the slot above", NamedTextColor.GRAY));
        i.setItemMeta(m);
        return i;
    }

    private ItemStack sellAllButtonItem() {
        ItemStack i = new ItemStack(Material.HOPPER);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text("Sell all from inventory", NamedTextColor.AQUA, TextDecoration.BOLD));
        m.lore(List.of(
                Component.text("Instantly sells every DPMR weapon", NamedTextColor.GRAY),
                Component.text("you are carrying for points.", NamedTextColor.DARK_GRAY)
        ));
        i.setItemMeta(m);
        return i;
    }

    private ItemStack sellInfoHead(UUID playerId, String weaponId) {
        ItemStack sk = NpcSkins.playerHeadFromTextureHash(
                "10505f1e9765e47e7fb9208e2ad8f171668decf39465ec40d3a589f8cbeb9722", "dpmr-boutique-balance");
        ItemMeta m = sk.getItemMeta();
        int pts = pointsManager.getPoints(playerId);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Solde : ", NamedTextColor.GRAY).append(Component.text(pts + " pts", NamedTextColor.GOLD, TextDecoration.BOLD)));
        lore.add(Component.empty());
        if (weaponId != null) {
            lore.add(Component.text("Arme : ", NamedTextColor.GRAY).append(Component.text(weaponId, NamedTextColor.AQUA)));
            lore.add(Component.text("Offre : +" + sellPrice(weaponId) + " pts", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("Drag a DPMR weapon", NamedTextColor.GRAY));
            lore.add(Component.text("into the center slot", NamedTextColor.DARK_GRAY));
        }
        m.displayName(Component.text("Comptoir", TextColor.color(0xF0D78C), TextDecoration.BOLD));
        m.lore(lore);
        sk.setItemMeta(m);
        return sk;
    }

    private static ItemStack backHubShop() {
        ItemStack i = new ItemStack(Material.ARROW);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text("← Back to shop", NamedTextColor.YELLOW));
        m.lore(List.of(Component.text("Return to rarity selection", NamedTextColor.DARK_GRAY)));
        i.setItemMeta(m);
        return i;
    }

    private static ItemStack closeItem() {
        ItemStack i = new ItemStack(Material.BARRIER);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text("Close", NamedTextColor.RED, TextDecoration.BOLD));
        i.setItemMeta(m);
        return i;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        BoutiqueHolder bh = BoutiqueHolder.from(event.getView().getTopInventory());
        if (bh == null) {
            return;
        }
        if (bh.kind() == BoutiqueHolder.Kind.SELL) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getView().getTopInventory().getSize() && rawSlot != SELL_DEPOSIT_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        if (bh.kind() == BoutiqueHolder.Kind.HUB || bh.kind() == BoutiqueHolder.Kind.BUY_CATEGORY) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        BoutiqueHolder bh = BoutiqueHolder.from(event.getView().getTopInventory());
        if (bh == null) {
            return;
        }

        if (bh.kind() == BoutiqueHolder.Kind.SELL) {
            handleSellClick(event, player);
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        int slot = event.getRawSlot();
        ItemStack cur = event.getCurrentItem();
        if (cur == null || cur.getType().isAir()) {
            return;
        }

        if (bh.kind() == BoutiqueHolder.Kind.HUB) {
            if (slot == SLOT_BACK) {
                player.closeInventory();
                return;
            }
            if (slot == SLOT_SELL_HUB) {
                openSellPage(player);
                return;
            }
            WeaponRarity[] order = WeaponRarity.values();
            for (int i = 0; i < HUB_RARITY_SLOTS.length && i < order.length; i++) {
                if (slot == HUB_RARITY_SLOTS[i]) {
                    openShopPage(player, order[i]);
                    return;
                }
            }
            return;
        }

        if (bh.kind() == BoutiqueHolder.Kind.BUY_CATEGORY) {
            if (slot == BUY_SHOWCASE_SLOT) {
                return;
            }
            if (slot == 45) {
                openHub(player);
                return;
            }
            if (slot == SLOT_BACK) {
                player.closeInventory();
                return;
            }
            String id = weaponManager.readWeaponId(cur);
            if (id == null) {
                return;
            }
            int price = buyPrice(id);
            int pts = pointsManager.getPoints(player.getUniqueId());
            if (pts < price) {
                player.sendMessage(Component.text("Not enough points (" + pts + "/" + price + ").", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);
                return;
            }
            pointsManager.addPoints(player.getUniqueId(), -price);
            pointsManager.saveAsync();
            ItemStack give = weaponManager.createWeaponItem(id, player);
            if (give != null) {
                player.getInventory().addItem(give);
            }
            player.sendMessage(Component.text("Purchased " + id + " (-" + price + " pts).", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.3f);
        }
    }

    @EventHandler
    public void onSellClose(InventoryCloseEvent event) {
        BoutiqueHolder bh = BoutiqueHolder.from(event.getView().getTopInventory());
        if (bh == null || bh.kind() != BoutiqueHolder.Kind.SELL) {
            return;
        }
        ItemStack deposited = event.getView().getTopInventory().getItem(SELL_DEPOSIT_SLOT);
        if (deposited == null || deposited.getType().isAir()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        event.getView().getTopInventory().setItem(SELL_DEPOSIT_SLOT, null);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(deposited.clone());
        if (!leftover.isEmpty()) {
            leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
        }
    }

    private void handleSellClick(InventoryClickEvent event, Player player) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        int raw = event.getRawSlot();

        if (clicked != null && clicked.equals(top)) {
            if (raw == 45) {
                event.setCancelled(true);
                openHub(player);
                return;
            }
            if (raw == SLOT_BACK) {
                event.setCancelled(true);
                player.closeInventory();
                return;
            }
            if (raw == SELL_BUTTON_SLOT) {
                event.setCancelled(true);
                ItemStack deposited = top.getItem(SELL_DEPOSIT_SLOT);
                String id = weaponManager.readWeaponId(deposited);
                if (id == null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.9f);
                    return;
                }
                int price = sellPrice(id);
                top.setItem(SELL_DEPOSIT_SLOT, null);
                pointsManager.addPoints(player.getUniqueId(), price);
                pointsManager.saveAsync();
                refreshSellView(player, top);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.35f);
                player.sendMessage(Component.text("Vendu: " + id + " (+" + price + " pts)", NamedTextColor.GREEN));
                return;
            }
            if (raw == SELL_ALL_BUTTON_SLOT) {
                event.setCancelled(true);
                int soldCount = 0;
                int earned = 0;
                for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    String id = weaponManager.readWeaponId(stack);
                    if (id == null) {
                        continue;
                    }
                    int amount = stack.getAmount();
                    soldCount += amount;
                    earned += sellPrice(id) * amount;
                    player.getInventory().setItem(slot, null);
                }
                if (soldCount <= 0) {
                    player.sendActionBar(Component.text("No DPMR weapons to sell.", NamedTextColor.GRAY));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.9f);
                    return;
                }
                pointsManager.addPoints(player.getUniqueId(), earned);
                pointsManager.saveAsync();
                refreshSellView(player, top);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.35f);
                player.sendMessage(Component.text("Bulk sale: " + soldCount + " weapon(s) (+" + earned + " pts).", NamedTextColor.GREEN));
                return;
            }

            if (raw == SELL_DEPOSIT_SLOT) {
                event.setCancelled(false);
                Bukkit.getScheduler().runTask(plugin, () -> refreshSellView(player, top));
                return;
            }

            event.setCancelled(true);
            return;
        }

        Inventory bottom = event.getView().getBottomInventory();
        if (clicked != null && clicked.equals(bottom)) {
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir()) {
                return;
            }
            String id = weaponManager.readWeaponId(current);
            if (id == null) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("You can only sell DPMR weapons.", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 0.9f);
                return;
            }
            ItemStack existing = top.getItem(SELL_DEPOSIT_SLOT);
            if (existing != null && !existing.getType().isAir()) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("Deposit slot already in use.", NamedTextColor.YELLOW));
                return;
            }
            event.setCancelled(true);
            ItemStack one = current.clone();
            one.setAmount(1);
            top.setItem(SELL_DEPOSIT_SLOT, one);
            int bottomSlot = event.getSlot();
            if (current.getAmount() <= 1) {
                bottom.setItem(bottomSlot, null);
            } else {
                current.setAmount(current.getAmount() - 1);
                bottom.setItem(bottomSlot, current);
            }
            refreshSellView(player, top);
        }
    }

    private void refreshSellView(Player player, Inventory top) {
        ItemStack deposited = top.getItem(SELL_DEPOSIT_SLOT);
        String id = weaponManager.readWeaponId(deposited);
        top.setItem(SELL_INFO_SLOT, sellInfoHead(player.getUniqueId(), id));
        if (id != null) {
            top.setItem(SELL_BUTTON_SLOT, sellButtonItem(true, sellPrice(id)));
        } else {
            top.setItem(SELL_BUTTON_SLOT, sellButtonItem(false, 0));
        }
        player.updateInventory();
    }

}
