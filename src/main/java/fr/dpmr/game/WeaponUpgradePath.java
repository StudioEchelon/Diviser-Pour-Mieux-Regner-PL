package fr.dpmr.game;

import fr.dpmr.i18n.GameLocale;

import java.util.Locale;

/**
 * Trois voies exclusives (style Bloons TD) : une seule active par arme.
 */
public enum WeaponUpgradePath {
    ASSAULT("ASSAULT", "Onslaught", "Damage, fire rate, ricochets, explosions"),
    SURVIVAL("SURVIVAL", "Fortified", "Magazine, reload speed, lifesteal, tanking"),
    TECH("TECH", "Omniscient", "Accuracy, range, slows, chain lightning");

    private final String shortLabel;
    private final String styleName;
    private final String blurb;

    WeaponUpgradePath(String shortLabel, String styleName, String blurb) {
        this.shortLabel = shortLabel;
        this.styleName = styleName;
        this.blurb = blurb;
    }

    public String shortLabel() {
        return shortLabel;
    }

    public String styleName() {
        return styleName;
    }

    public String blurb() {
        return blurb;
    }

    public String shortLabel(GameLocale loc) {
        if (loc == GameLocale.FR) {
            return shortLabel;
        }
        return switch (this) {
            case ASSAULT -> "ASSAULT";
            case SURVIVAL -> "SURVIVAL";
            case TECH -> "TECH";
        };
    }

    public String styleName(GameLocale loc) {
        if (loc == GameLocale.FR) {
            return styleName;
        }
        return switch (this) {
            case ASSAULT -> "Onslaught";
            case SURVIVAL -> "Fortified";
            case TECH -> "Omniscient";
        };
    }

    public String blurb(GameLocale loc) {
        if (loc == GameLocale.FR) {
            return blurb;
        }
        return switch (this) {
            case ASSAULT -> "Damage, fire rate, ricochets, small explosions";
            case SURVIVAL -> "Magazine, reload speed, lifesteal, tanking";
            case TECH -> "Accuracy, range, slows, chain lightning";
        };
    }

    public static WeaponUpgradePath fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
