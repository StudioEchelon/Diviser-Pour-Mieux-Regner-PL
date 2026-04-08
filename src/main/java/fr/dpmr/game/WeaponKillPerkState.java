package fr.dpmr.game;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/**
 * Jauge 0–100 + jusqu'à {@link #MAX_PERKS} perks sur l'instance d'arme.
 */
public final class WeaponKillPerkState {

    public static final int MAX_PERKS = 3;
    public static final int METER_MAX = 100;

    private WeaponKillPerkState() {
    }

    private static org.bukkit.NamespacedKey keyMeter(JavaPlugin plugin) {
        return new org.bukkit.NamespacedKey(plugin, "dpmr_kill_meter");
    }

    private static org.bukkit.NamespacedKey keyPerks(JavaPlugin plugin) {
        return new org.bukkit.NamespacedKey(plugin, "dpmr_kill_perks");
    }

    public static boolean enabled(JavaPlugin plugin) {
        return plugin.getConfig().getBoolean("weapons.kill-perks.enabled", true);
    }

    public static int meter(ItemStack stack, JavaPlugin plugin) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        Integer v = stack.getItemMeta().getPersistentDataContainer().get(keyMeter(plugin), PersistentDataType.INTEGER);
        if (v == null) {
            return 0;
        }
        return Math.max(0, Math.min(METER_MAX, v));
    }

    public static List<WeaponKillPerk> perks(ItemStack stack, JavaPlugin plugin) {
        if (stack == null || !stack.hasItemMeta()) {
            return List.of();
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(keyPerks(plugin), PersistentDataType.STRING);
        return parsePerks(raw);
    }

    static List<WeaponKillPerk> parsePerks(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<WeaponKillPerk> out = new ArrayList<>();
        for (String p : raw.split(",")) {
            WeaponKillPerk k = WeaponKillPerk.fromId(p);
            if (k != null) {
                out.add(k);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static boolean isMaxed(ItemStack stack, JavaPlugin plugin) {
        return perks(stack, plugin).size() >= MAX_PERKS;
    }

    public static void setMeter(ItemStack stack, int meter, JavaPlugin plugin) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int m = Math.max(0, Math.min(METER_MAX, meter));
        if (m <= 0) {
            pdc.remove(keyMeter(plugin));
        } else {
            pdc.set(keyMeter(plugin), PersistentDataType.INTEGER, m);
        }
        stack.setItemMeta(meta);
    }

    public static void setPerks(ItemStack stack, List<WeaponKillPerk> list, JavaPlugin plugin) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (list == null || list.isEmpty()) {
            pdc.remove(keyPerks(plugin));
        } else {
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (WeaponKillPerk p : list) {
                if (p == null || n >= MAX_PERKS) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(p.name());
                n++;
            }
            pdc.set(keyPerks(plugin), PersistentDataType.STRING, sb.toString());
        }
        stack.setItemMeta(meta);
    }

    /**
     * @return nouveau palier de jauge après ajout (cap 100).
     */
    public static int addMeter(ItemStack stack, int delta, JavaPlugin plugin) {
        if (!enabled(plugin) || stack == null || !stack.hasItemMeta()) {
            return meter(stack, plugin);
        }
        if (isMaxed(stack, plugin)) {
            return METER_MAX;
        }
        int next = Math.min(METER_MAX, meter(stack, plugin) + Math.max(0, delta));
        setMeter(stack, next, plugin);
        return next;
    }

    public static boolean appendPerk(ItemStack stack, WeaponKillPerk perk, JavaPlugin plugin) {
        if (perk == null || stack == null || !stack.hasItemMeta()) {
            return false;
        }
        List<WeaponKillPerk> cur = new ArrayList<>(perks(stack, plugin));
        if (cur.size() >= MAX_PERKS) {
            return false;
        }
        cur.add(perk);
        setPerks(stack, cur, plugin);
        setMeter(stack, 0, plugin);
        return true;
    }
}
