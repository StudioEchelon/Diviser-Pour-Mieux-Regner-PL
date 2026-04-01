package fr.dpmr.cosmetics;

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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
/**
 * Boutique des skins d'arme (Thompson, etc.) : achat, equipement, NBT {@code dpmr:weapon_cosmetic}.
 */
public class SkinGui implements Listener {

    public static final Component TITLE = Component.text("Skins d'armes", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final int SLOT_CLOSE = 49;
    /** Choisir l'apparence de base (sans skin payant). */
    private static final int SLOT_DEFAULT = 4;

    private final CosmeticsManager cosmeticsManager;
    private final PointsManager pointsManager;
    private final WeaponManager weaponManager;

    public SkinGui(CosmeticsManager cosmeticsManager, PointsManager pointsManager, WeaponManager weaponManager) {
        this.cosmeticsManager = cosmeticsManager;
        this.pointsManager = pointsManager;
        this.weaponManager = weaponManager;
    }

    public void openThompson(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        int slot = 10;
        for (CosmeticProfile p : CosmeticProfile.values()) {
            if (p.type() != CosmeticType.WEAPON_SKIN || p.weaponSkinFor() == null
                    || !p.weaponSkinFor().equals("THOMPSON")) {
                continue;
            }
            if (slot == 17) {
                slot = 19;
            }
            if (slot == 26) {
                slot = 28;
            }
            if (slot >= 35) {
                break;
            }
            inv.setItem(slot++, entryItem(player, p));
        }
        inv.setItem(SLOT_DEFAULT, defaultThompsonItem(player));
        inv.setItem(SLOT_CLOSE, closeItem());
        fillGlass(inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.15f);
    }

    private ItemStack defaultThompsonItem(Player player) {
        boolean noSkin = cosmeticsManager.selectedWeaponSkin(player.getUniqueId(), "THOMPSON") == null;
        ItemStack i = new ItemStack(Material.SPYGLASS);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text("Thompson — basique", NamedTextColor.GRAY));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Apparence par defaut (sans variante payante)", NamedTextColor.DARK_GRAY));
        lore.add(Component.text(noSkin ? "Actif" : "Clic: utiliser", noSkin ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        m.lore(lore);
        i.setItemMeta(m);
        return i;
    }

    private ItemStack entryItem(Player player, CosmeticProfile p) {
        boolean owned = cosmeticsManager.isOwned(player.getUniqueId(), p);
        ItemStack i = new ItemStack(p.icon());
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text(p.displayName(), p.color()));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Prix: " + p.price() + " points", NamedTextColor.GOLD));
        lore.add(Component.text(owned ? "Clic: equiper" : "Clic: acheter", owned ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        m.lore(lore);
        i.setItemMeta(m);
        return i;
    }

    private static ItemStack closeItem() {
        ItemStack i = new ItemStack(Material.BARRIER);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text("Fermer", NamedTextColor.RED));
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
        if (TITLE.equals(event.getView().title())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().title())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_DEFAULT) {
            cosmeticsManager.clearWeaponSkinSelection(player.getUniqueId(), "THOMPSON");
            weaponManager.syncWeaponSkinFromSelection(player, "THOMPSON");
            player.sendMessage(Component.text("Thompson basique equipee (pas de variante).", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.35f, 1.4f);
            openThompson(player);
            return;
        }

        ItemStack cur = event.getCurrentItem();
        if (cur == null || cur.getType().isAir() || cur.getItemMeta() == null || cur.getItemMeta().displayName() == null) {
            return;
        }

        CosmeticProfile picked = null;
        for (CosmeticProfile p : CosmeticProfile.values()) {
            if (p.type() != CosmeticType.WEAPON_SKIN || !"THOMPSON".equals(p.weaponSkinFor())) {
                continue;
            }
            if (p.icon() == cur.getType()) {
                picked = p;
                break;
            }
        }
        if (picked == null) {
            return;
        }

        boolean owned = cosmeticsManager.isOwned(player.getUniqueId(), picked);
        if (!owned) {
            int pts = pointsManager.getPoints(player.getUniqueId());
            if (pts < picked.price()) {
                player.sendMessage(Component.text("Pas assez de points (" + pts + "/" + picked.price() + ").", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.85f);
                return;
            }
            cosmeticsManager.buy(player.getUniqueId(), picked);
            player.sendMessage(Component.text("Achete: " + picked.displayName() + " (-" + picked.price() + " pts)", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.3f);
        }

        cosmeticsManager.setSelected(player.getUniqueId(), picked);
        weaponManager.syncWeaponSkinFromSelection(player, "THOMPSON");
        player.sendActionBar(Component.text("Skin equipe: " + picked.displayName(), NamedTextColor.GOLD));
        openThompson(player);
    }
}
