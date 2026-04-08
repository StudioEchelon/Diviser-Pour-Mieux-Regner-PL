package fr.dpmr.game;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paliers 1–3 lineaires pour le revolver de base (atelier exclusif).
 */
public record RevolverUpgradeState(int tier) {

    public static final RevolverUpgradeState NONE = new RevolverUpgradeState(0);

    public boolean isEmpty() {
        return tier <= 0;
    }

    public static RevolverUpgradeState read(ItemStack stack, JavaPlugin plugin) {
        if (stack == null || !stack.hasItemMeta()) {
            return NONE;
        }
        return readPayload(stack.getItemMeta().getPersistentDataContainer(), plugin);
    }

    public static void write(ItemStack stack, RevolverUpgradeState state, JavaPlugin plugin) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        writePayload(meta.getPersistentDataContainer(), state, plugin);
        stack.setItemMeta(meta);
    }

    public static RevolverUpgradeState readPayload(PersistentDataContainer pdc, JavaPlugin plugin) {
        var key = new org.bukkit.NamespacedKey(plugin, "dpmr_revolver_tier");
        Integer t = pdc.get(key, PersistentDataType.INTEGER);
        if (t == null || t <= 0) {
            return NONE;
        }
        return new RevolverUpgradeState(Math.min(3, t));
    }

    public static void writePayload(PersistentDataContainer pdc, RevolverUpgradeState state, JavaPlugin plugin) {
        var key = new org.bukkit.NamespacedKey(plugin, "dpmr_revolver_tier");
        if (state.isEmpty()) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.INTEGER, state.tier());
        }
    }
}
