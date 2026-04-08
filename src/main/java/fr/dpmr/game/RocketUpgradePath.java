package fr.dpmr.game;

import fr.dpmr.i18n.GameLocale;

import java.util.Locale;

/**
 * Voies exclusives pour le lance-roquettes (table d'armement).
 */
public enum RocketUpgradePath {
    GUIDED("LOCK", "Guided missile", "Locks the nearest player or mob"),
    DEVASTATOR("MEGA", "Demolition", "Blast radius and damage"),
    DRONE("DRONE", "Attack drone", "Deploys a drone that strafes nearby");

    private final String shortLabel;
    private final String styleName;
    private final String blurb;

    RocketUpgradePath(String shortLabel, String styleName, String blurb) {
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
            case GUIDED -> "Guided missile";
            case DEVASTATOR -> "Demolition";
            case DRONE -> "Attack drone";
        };
    }

    public static RocketUpgradePath fromId(String raw) {
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
