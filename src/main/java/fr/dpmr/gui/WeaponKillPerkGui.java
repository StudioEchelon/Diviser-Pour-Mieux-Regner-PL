package fr.dpmr.gui;

import fr.dpmr.game.WeaponKillPerk;
import fr.dpmr.game.WeaponKillPerkState;
import fr.dpmr.game.WeaponManager;
import fr.dpmr.game.WeaponProfile;
import fr.dpmr.i18n.GameLocale;
import fr.dpmr.i18n.I18n;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Coffre 3 choix (style diep.io / Archero) pour valider un perk sur l'arme.
 */
public final class WeaponKillPerkGui implements Listener {

    private static final int SIZE = 27;
    private static final int[] SLOTS = {11, 13, 15};
    private static final WeaponKillPerk[] ORDER = {
            WeaponKillPerk.RAPID_FIRE,
            WeaponKillPerk.DOUBLE_TAP,
            WeaponKillPerk.STABILIZER
    };

    private final JavaPlugin plugin;
    private final WeaponManager weaponManager;

    public WeaponKillPerkGui(JavaPlugin plugin, WeaponManager weaponManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
    }

    public void open(Player player, String weaponInstanceId) {
        GameLocale loc = I18n.locale(player);
        Component title = Component.text(I18n.string(loc, "killperk.gui_title"))
                .color(NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false);
        Inventory inv = Bukkit.createInventory(new WeaponKillPerkHolder(weaponInstanceId), SIZE, title);
        ItemStack weapon = weaponManager.findWeaponStackByInstance(player, weaponInstanceId);
        List<WeaponKillPerk> owned = weapon != null ? WeaponKillPerkState.perks(weapon, plugin) : List.of();

        for (int i = 0; i < ORDER.length && i < SLOTS.length; i++) {
            WeaponKillPerk pk = ORDER[i];
            inv.setItem(SLOTS[i], perkButton(player, pk, owned.contains(pk)));
        }
        fillDecor(inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.4f, 1.15f);
    }

    private ItemStack perkButton(Player player, WeaponKillPerk perk, boolean alreadyOwned) {
        GameLocale loc = I18n.locale(player);
        Material mat = alreadyOwned ? Material.BARRIER : perk.icon();
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String name = I18n.string(loc, perk.nameKey());
        meta.displayName(Component.text(name, alreadyOwned ? NamedTextColor.DARK_GRAY : NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(I18n.string(loc, perk.descKey()), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (alreadyOwned) {
            lore.add(Component.empty());
            lore.add(Component.text(I18n.string(loc, "killperk.already_owned"), NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text(I18n.string(loc, "killperk.click_to_take"), NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static void fillDecor(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);
        for (int i = 0; i < SIZE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass.clone());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WeaponKillPerkHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WeaponKillPerkHolder holder)) {
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
        WeaponKillPerk picked = null;
        for (int i = 0; i < SLOTS.length; i++) {
            if (slot == SLOTS[i]) {
                picked = ORDER[i];
                break;
            }
        }
        if (picked == null) {
            return;
        }
        ItemStack weapon = weaponManager.findWeaponStackByInstance(player, holder.weaponInstanceId());
        if (weapon == null) {
            player.closeInventory();
            return;
        }
        WeaponProfile w = WeaponProfile.fromId(weaponManager.readWeaponId(weapon));
        if (w == null || !w.supportsKillPerkMeter()) {
            player.closeInventory();
            return;
        }
        if (WeaponKillPerkState.perks(weapon, plugin).contains(picked)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 0.8f);
            return;
        }
        if (WeaponKillPerkState.isMaxed(weapon, plugin)) {
            player.closeInventory();
            return;
        }
        if (!WeaponKillPerkState.appendPerk(weapon, picked, plugin)) {
            player.closeInventory();
            return;
        }
        weaponManager.refreshWeaponMeta(weapon, player);
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.35f);
        GameLocale loc = I18n.locale(player);
        player.sendMessage(Component.text(
                I18n.string(loc, "killperk.taken", I18n.string(loc, picked.nameKey())),
                NamedTextColor.LIGHT_PURPLE));
    }

    public record WeaponKillPerkHolder(String weaponInstanceId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
