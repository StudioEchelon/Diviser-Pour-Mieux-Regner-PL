package fr.dpmr.gui;

import fr.dpmr.game.WeaponManager;
import fr.dpmr.game.WeaponProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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

public class GunGui implements Listener {

    private static final Component TITLE = Component.text()
            .append(Component.text("DPMR ", NamedTextColor.DARK_GRAY))
            .append(Component.text("\u00b7 ", TextColor.color(0x5A5A68)))
            .append(Component.text("Arsenal", TextColor.color(0xF2F2F2), TextDecoration.BOLD))
            .build();

    private static final WeaponProfile[] CATALOG = {
            WeaponProfile.THOMPSON,
            WeaponProfile.FUSIL_POMPE_RL,
            WeaponProfile.REVOLVER,
            WeaponProfile.EP_POMPE_ACIDE,
            WeaponProfile.CARABINE_RARE,
            WeaponProfile.LG_POMPE_DOUBLE_CYBER,
            WeaponProfile.INFERNO,
            WeaponProfile.EP_FUSIL_POMPE,
            WeaponProfile.EP_REVOLVER_CYBER,
            WeaponProfile.CM_SHOTGUN,
            WeaponProfile.DOUBLE_PULSE,
    };

    private static final int[] WEAPON_SLOTS = {
            10, 11, 12, 13, 14, 15,
            19, 20, 21, 22, 23
    };

    private static final int SLOT_CLOSE = 31;

    private final WeaponManager weaponManager;

    public GunGui(WeaponManager weaponManager) {
        this.weaponManager = weaponManager;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new GunHolder(), 36, TITLE);

        boolean admin = player.hasPermission("dpmr.admin");

        for (int i = 0; i < CATALOG.length && i < WEAPON_SLOTS.length; i++) {
            ItemStack item = weaponManager.createWeaponItem(CATALOG[i].name(), player);
            if (item == null) {
                continue;
            }
            if (admin) {
                ItemMeta meta = item.getItemMeta();
                List<Component> lore = meta.lore();
                if (lore == null) {
                    lore = new ArrayList<>();
                }
                lore = new ArrayList<>(lore);
                lore.add(Component.empty());
                lore.add(Component.text("\u25b8 Clic pour recevoir", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(WEAPON_SLOTS[i], item);
        }

        inv.setItem(SLOT_CLOSE, closeButton());
        fillDecor(inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.2f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GunHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GunHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (!player.hasPermission("dpmr.admin")) {
            return;
        }

        for (int i = 0; i < WEAPON_SLOTS.length; i++) {
            if (slot == WEAPON_SLOTS[i] && i < CATALOG.length) {
                ItemStack give = weaponManager.createWeaponItem(CATALOG[i].name(), player);
                if (give != null) {
                    player.getInventory().addItem(give);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.15f);
                    player.sendActionBar(Component.text("+ " + CATALOG[i].displayName(),
                            CATALOG[i].rarity().color()));
                }
                return;
            }
        }
    }

    private static ItemStack closeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Fermer", NamedTextColor.RED, TextDecoration.BOLD));
        item.setItemMeta(meta);
        return item;
    }

    private static void fillDecor(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass.clone());
            }
        }
    }

    static final class GunHolder implements org.bukkit.inventory.InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
