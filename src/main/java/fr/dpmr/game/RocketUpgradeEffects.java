package fr.dpmr.game;

import fr.dpmr.i18n.GameLocale;
import fr.dpmr.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class RocketUpgradeEffects {

    private RocketUpgradeEffects() {
    }

    public static int goldCostForTier(int tier) {
        return tier * 3;
    }

    public static String tierTitle(RocketUpgradePath path, int tier) {
        return tierTitle(path, tier, GameLocale.FR);
    }

    public static String tierTitle(RocketUpgradePath path, int tier, GameLocale loc) {
        if (loc == GameLocale.EN) {
            return switch (path) {
                case GUIDED -> switch (tier) {
                    case 1 -> "I — Soft lock";
                    case 2 -> "II — Tracking";
                    case 3 -> "III — Aggressive turn";
                    case 4 -> "IV — Long acquisition";
                    case 5 -> "V — Predator";
                    default -> "?";
                };
                case DEVASTATOR -> switch (tier) {
                    case 1 -> "I — Shrapnel";
                    case 2 -> "II — HE shell";
                    case 3 -> "III — Satchel";
                    case 4 -> "IV — Bunker buster";
                    case 5 -> "V — Ground zero";
                    default -> "?";
                };
                case DRONE -> switch (tier) {
                    case 1 -> "I — Scout drone";
                    case 2 -> "II — Strafing";
                    case 3 -> "III — Sustained fire";
                    case 4 -> "IV — Hunter drone";
                    case 5 -> "V — Gunship";
                    default -> "?";
                };
            };
        }
        return switch (path) {
            case GUIDED -> switch (tier) {
                case 1 -> "I — Accroche leger";
                case 2 -> "II — Suivi";
                case 3 -> "III — Virage serre";
                case 4 -> "IV — Portee de lock";
                case 5 -> "V — Predateur";
                default -> "?";
            };
            case DEVASTATOR -> switch (tier) {
                case 1 -> "I — Shrapnel";
                case 2 -> "II — Charge HE";
                case 3 -> "III — Sacoche";
                case 4 -> "IV — Bunker";
                case 5 -> "V — Epicentre";
                default -> "?";
            };
            case DRONE -> switch (tier) {
                case 1 -> "I — Drone eclaireur";
                case 2 -> "II — Rafales";
                case 3 -> "III — Tir soutenu";
                case 4 -> "IV — Drone chasseur";
                case 5 -> "V — Gunship";
                default -> "?";
            };
        };
    }

    public static List<String> tierLines(RocketUpgradePath path, int tier) {
        return switch (path) {
            case GUIDED -> switch (tier) {
                case 1 -> List.of("+6 blocs de detection", "Virage +15%");
                case 2 -> List.of("+14 blocs", "Virage +22%");
                case 3 -> List.of("+22 blocs", "Virage +30%");
                case 4 -> List.of("+30 blocs", "Virage +38%");
                case 5 -> List.of("+38 blocs", "Virage max, trainee");
                default -> List.of();
            };
            case DEVASTATOR -> switch (tier) {
                case 1 -> List.of("+10% rayon explosion");
                case 2 -> List.of("+22% rayon");
                case 3 -> List.of("+15% degats zone");
                case 4 -> List.of("+28% degats");
                case 5 -> List.of("+40% rayon et degats");
                default -> List.of();
            };
            case DRONE -> switch (tier) {
                case 1 -> List.of("Drone ~4 s, tir /7 t");
                case 2 -> List.of("~5 s, tir /6 t");
                case 3 -> List.of("~6 s, degats +");
                case 4 -> List.of("~7 s, portee tir +");
                case 5 -> List.of("~9 s, mitrailleuse dense");
                default -> List.of();
            };
        };
    }

    /** Portee horizontale pour acquérir une cible (blocs). */
    public static double guidedAcquisitionRange(int tier) {
        return 22 + tier * 7.5;
    }

    /** 0..1 melange vers la cible chaque tick. */
    public static double guidedTurnLerp(int tier) {
        return Math.min(0.52, 0.13 + tier * 0.072);
    }

    public static double devastatorRadiusMul(RocketUpgradeState st) {
        if (st.isEmpty() || st.path() != RocketUpgradePath.DEVASTATOR) {
            return 1.0;
        }
        return 1.0 + 0.09 * st.tier() * st.tier();
    }

    public static double devastatorDamageMul(RocketUpgradeState st) {
        if (st.isEmpty() || st.path() != RocketUpgradePath.DEVASTATOR) {
            return 1.0;
        }
        return 1.0 + 0.075 * st.tier() * st.tier();
    }

    public static int droneLifetimeTicks(int tier) {
        return 78 + tier * 26;
    }

    public static long droneFirePeriodTicks(int tier) {
        return Math.max(3L, 9 - tier);
    }

    public static double droneShotHalfHearts(int tier) {
        return 2.6 + tier * 0.45;
    }

    public static double droneGunRange(int tier) {
        return 15 + tier * 1.2;
    }

    public static boolean canBuyTier(RocketUpgradeState current, RocketUpgradePath path, int tier) {
        if (tier < 1 || tier > 5) {
            return false;
        }
        if (current.isEmpty()) {
            return tier == 1;
        }
        if (current.path() != path) {
            return false;
        }
        return tier == current.tier() + 1;
    }

    public static boolean pathLocked(RocketUpgradeState current, RocketUpgradePath path) {
        return !current.isEmpty() && current.path() != path;
    }

    public static boolean tierOwned(RocketUpgradeState current, RocketUpgradePath path, int tier) {
        return !current.isEmpty() && current.path() == path && current.tier() >= tier;
    }

    public static List<Component> loreLines(RocketUpgradeState st) {
        return loreLines(st, GameLocale.FR);
    }

    public static List<Component> loreLines(RocketUpgradeState st, GameLocale loc) {
        List<Component> lines = new ArrayList<>();
        if (st.isEmpty()) {
            return lines;
        }
        lines.add(Component.text(I18n.string(loc, "upgrade.rocket_header", st.path().styleName(loc)), NamedTextColor.RED));
        for (int t = 1; t <= st.tier(); t++) {
            lines.add(Component.text("  " + tierTitle(st.path(), t, loc), NamedTextColor.GOLD));
        }
        return lines;
    }
}
