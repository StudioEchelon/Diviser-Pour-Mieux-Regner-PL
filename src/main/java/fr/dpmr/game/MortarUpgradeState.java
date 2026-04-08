package fr.dpmr.game;

import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public record MortarUpgradeState(MortarUpgradePath path, int tier) {

    public static final MortarUpgradeState NONE = new MortarUpgradeState(null, 0);

    public boolean isEmpty() {
        return path == null || tier <= 0;
    }

    public static MortarUpgradeState read(ItemStack stack, JavaPlugin plugin) {
        if (stack == null || !stack.hasItemMeta()) {
            return NONE;
        }
        return readPayload(stack.getItemMeta().getPersistentDataContainer(), plugin);
    }

    public static void write(ItemStack stack, MortarUpgradeState state, JavaPlugin plugin) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        writePayload(meta.getPersistentDataContainer(), state, plugin);
        stack.setItemMeta(meta);
    }

    public static MortarUpgradeState readPayload(PersistentDataContainer pdc, JavaPlugin plugin) {
        var key = new org.bukkit.NamespacedKey(plugin, "dpmr_mortar_pl");
        String raw = pdc.get(key, PersistentDataType.STRING);
        if (raw == null || !raw.contains(":")) {
            return NONE;
        }
        String[] p = raw.split(":", 2);
        MortarUpgradePath path = MortarUpgradePath.fromId(p[0]);
        try {
            int t = Integer.parseInt(p[1]);
            if (path == null || t <= 0) {
                return NONE;
            }
            return new MortarUpgradeState(path, Math.min(5, t));
        } catch (NumberFormatException e) {
            return NONE;
        }
    }

    public static void writePayload(PersistentDataContainer pdc, MortarUpgradeState state, JavaPlugin plugin) {
        var key = new org.bukkit.NamespacedKey(plugin, "dpmr_mortar_pl");
        if (state.isEmpty()) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, state.path.name() + ":" + state.tier);
        }
    }
}
