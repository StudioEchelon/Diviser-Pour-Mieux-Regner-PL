package fr.dpmr.game;

import fr.dpmr.i18n.GameLocale;

import java.util.Locale;

/**
 * Voies exclusives pour le mortier (table d'armement).
 */
public enum MortarUpgradePath {
    INCENDIARY("FIRE", "Incendiary", "Fire shells — burn and heat zone"),
    BARRAGE("BARRAGE", "Barrage", "Arcing shell volley"),
    ACID("ACID", "Acid", "Corrosive smoke shell — poison and lingering cloud");

    private final String shortLabel;
    private final String styleName;
    private final String blurb;

    MortarUpgradePath(String shortLabel, String styleName, String blurb) {
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

    public String styleName(GameLocale loc) {
        if (loc == GameLocale.FR) {
            return styleName;
        }
        return switch (this) {
            case INCENDIARY -> "Incendiary";
            case BARRAGE -> "Barrage";
            case ACID -> "Acid";
        };
    }

    public static MortarUpgradePath fromId(String raw) {
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
