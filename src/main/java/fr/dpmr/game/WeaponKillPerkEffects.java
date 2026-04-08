package fr.dpmr.game;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class WeaponKillPerkEffects {

    private static final double RAPID_COOLDOWN_EACH = 0.88;
    private static final double STABILIZER_SPREAD_EACH = 0.87;
    /** Second salve hitscan : fraction des dégâts de base. */
    public static final double DOUBLE_TAP_DAMAGE_SCALE = 0.68;

    private WeaponKillPerkEffects() {
    }

    public static double cooldownMultiplier(ItemStack weapon, JavaPlugin plugin) {
        if (!WeaponKillPerkState.enabled(plugin) || weapon == null) {
            return 1.0;
        }
        List<WeaponKillPerk> p = WeaponKillPerkState.perks(weapon, plugin);
        double m = 1.0;
        for (WeaponKillPerk k : p) {
            if (k == WeaponKillPerk.RAPID_FIRE) {
                m *= RAPID_COOLDOWN_EACH;
            }
        }
        return m;
    }

    public static double spreadMultiplier(ItemStack weapon, JavaPlugin plugin) {
        if (!WeaponKillPerkState.enabled(plugin) || weapon == null) {
            return 1.0;
        }
        List<WeaponKillPerk> p = WeaponKillPerkState.perks(weapon, plugin);
        double m = 1.0;
        for (WeaponKillPerk k : p) {
            if (k == WeaponKillPerk.STABILIZER) {
                m *= STABILIZER_SPREAD_EACH;
            }
        }
        return m;
    }

    public static boolean hasDoubleTap(ItemStack weapon, JavaPlugin plugin) {
        if (!WeaponKillPerkState.enabled(plugin) || weapon == null) {
            return false;
        }
        return WeaponKillPerkState.perks(weapon, plugin).contains(WeaponKillPerk.DOUBLE_TAP);
    }
}
