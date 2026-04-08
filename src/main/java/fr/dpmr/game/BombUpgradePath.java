package fr.dpmr.game;

import fr.dpmr.i18n.GameLocale;

import java.util.Locale;

/**
 * Voies exclusives pour l'arme a bombes (table d'armement).
 */
public enum BombUpgradePath {
    SALVO("SALVO", "Salvo", "More bombs per shot"),
    RICOCHET("RICOCHET", "Ricochet", "Bomb bounces off walls"),
    OVERLOAD("OVERLOAD", "Overload", "Explosion radius and damage");

    private final String shortLabel;
    private final String styleName;
    private final String blurb;

    BombUpgradePath(String shortLabel, String styleName, String blurb) {
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
            case SALVO -> "Salvo";
            case RICOCHET -> "Ricochet";
            case OVERLOAD -> "Overload";
        };
    }

    public static BombUpgradePath fromId(String raw) {
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
