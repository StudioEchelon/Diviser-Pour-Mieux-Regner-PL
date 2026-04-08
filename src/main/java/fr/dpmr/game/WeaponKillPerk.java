package fr.dpmr.game;

import org.bukkit.Material;

/**
 * Améliorations type roguelike (choix au palier) — stockées sur l'item d'arme.
 */
public enum WeaponKillPerk {

    RAPID_FIRE(Material.SUGAR, "killperk.rapid_fire.name", "killperk.rapid_fire.desc"),
    DOUBLE_TAP(Material.IRON_INGOT, "killperk.double_tap.name", "killperk.double_tap.desc"),
    STABILIZER(Material.FEATHER, "killperk.stabilizer.name", "killperk.stabilizer.desc");

    private final Material icon;
    private final String nameKey;
    private final String descKey;

    WeaponKillPerk(Material icon, String nameKey, String descKey) {
        this.icon = icon;
        this.nameKey = nameKey;
        this.descKey = descKey;
    }

    public Material icon() {
        return icon;
    }

    public String nameKey() {
        return nameKey;
    }

    public String descKey() {
        return descKey;
    }

    public static WeaponKillPerk fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
