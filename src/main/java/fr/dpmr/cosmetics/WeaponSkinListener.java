package fr.dpmr.cosmetics;

import fr.dpmr.game.WeaponManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reapplique CustomModelData + lore quand une arme DPMR avec NBT skin entre dans l'inventaire (sol, coffre).
 */
public class WeaponSkinListener implements Listener {

    private final JavaPlugin plugin;
    private final WeaponManager weaponManager;

    public WeaponSkinListener(JavaPlugin plugin, WeaponManager weaponManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (weaponManager.readWeaponId(stack) == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> weaponManager.refreshDpmrWeaponsInInventory(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack cur = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean relevant = (cur != null && weaponManager.readWeaponId(cur) != null)
                || (cursor != null && weaponManager.readWeaponId(cursor) != null);
        if (!relevant) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> weaponManager.refreshDpmrWeaponsInInventory(player));
    }
}
