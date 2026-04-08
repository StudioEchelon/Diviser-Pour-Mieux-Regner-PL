package fr.dpmr.game;

import fr.dpmr.i18n.GameLocale;
import fr.dpmr.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

public final class RevolverUpgradeEffects {

    private RevolverUpgradeEffects() {
    }

    public static int goldCostForTier(int tier) {
        if (tier < 1 || tier > 3) {
            return 0;
        }
        return tier * 4;
    }

    public static String tierTitle(int tier, GameLocale loc) {
        if (loc == GameLocale.EN) {
            return switch (tier) {
                case 1 -> "I — Twin barrels";
                case 2 -> "II — Explosive rounds";
                case 3 -> "III — Shatter shot";
                default -> "?";
            };
        }
        return switch (tier) {
            case 1 -> "I — Double canon";
            case 2 -> "II — Balles explosives";
            case 3 -> "III — Balle fendue";
            default -> "?";
        };
    }

    public static List<String> tierLines(int tier) {
        return switch (tier) {
            case 1 -> List.of("2 projectiles per shot (1 ammo)", "Double cylinder (x2), faster fire rate");
            case 2 -> List.of("Each hit: small area explosion");
            case 3 -> List.of("Hit a player: 4 piercing trajectories");
            default -> List.of();
        };
    }

    public static boolean canBuyTier(RevolverUpgradeState current, int tier) {
        if (tier < 1 || tier > 3) {
            return false;
        }
        if (current.isEmpty()) {
            return tier == 1;
        }
        return tier == current.tier() + 1;
    }

    public static boolean tierOwned(RevolverUpgradeState current, int tier) {
        return !current.isEmpty() && current.tier() >= tier;
    }

    /** Paliers 1+ : 2 traces par tir. */
    public static int hitscanPellets(RevolverUpgradeState st) {
        if (st.tier() >= 1) {
            return 2;
        }
        return 1;
    }

    /** Paliers 1+ : capacite du barillet x2. */
    public static int clipMultiplier(RevolverUpgradeState st) {
        return st.tier() >= 1 ? 2 : 1;
    }

    /** Paliers 1+ : cooldown entre tirs reduit. */
    public static double cooldownMultiplier(RevolverUpgradeState st) {
        if (st.tier() >= 1) {
            return 0.62;
        }
        return 1.0;
    }

    public static List<Component> loreLines(RevolverUpgradeState st, GameLocale loc) {
        List<Component> lines = new ArrayList<>();
        if (st.isEmpty()) {
            return lines;
        }
        lines.add(Component.text(I18n.string(loc, "upgrade.revolver_header"), NamedTextColor.GOLD));
        for (int t = 1; t <= st.tier(); t++) {
            lines.add(Component.text("  " + tierTitle(t, loc), NamedTextColor.YELLOW));
        }
        return lines;
    }
}
