package fr.dpmr.game;

import fr.dpmr.i18n.GameLocale;
import fr.dpmr.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MortarUpgradeEffects {

    private MortarUpgradeEffects() {
    }

    public static int goldCostForTier(int tier) {
        return tier * 3;
    }

    public static String tierTitle(MortarUpgradePath path, int tier) {
        return tierTitle(path, tier, GameLocale.FR);
    }

    public static String tierTitle(MortarUpgradePath path, int tier, GameLocale loc) {
        if (loc == GameLocale.EN) {
            return switch (path) {
                case INCENDIARY -> switch (tier) {
                    case 1 -> "I — Ember shell";
                    case 2 -> "II — Fireburst";
                    case 3 -> "III — Inferno ring";
                    case 4 -> "IV — Scorched ground";
                    case 5 -> "V — Hellfire barrage";
                    default -> "?";
                };
                case BARRAGE -> switch (tier) {
                    case 1 -> "I — Twin shells";
                    case 2 -> "II — Triple volley";
                    case 3 -> "III — Rolling thunder";
                    case 4 -> "IV — Five-fold rain";
                    case 5 -> "V — Six-shot salvo";
                    default -> "?";
                };
                case ACID -> switch (tier) {
                    case 1 -> "I — Irritant vapor";
                    case 2 -> "II — Sticky fumes";
                    case 3 -> "III — Toxic burst";
                    case 4 -> "IV — Lingering cloud";
                    case 5 -> "V — Withering pool";
                    default -> "?";
                };
            };
        }
        return switch (path) {
            case INCENDIARY -> switch (tier) {
                case 1 -> "I — Obus braise";
                case 2 -> "II — Gerbe de feu";
                case 3 -> "III — Anneau infernal";
                case 4 -> "IV — Sol calcine";
                case 5 -> "V — Pluie de l'enfer";
                default -> "?";
            };
            case BARRAGE -> switch (tier) {
                case 1 -> "I — Double obus";
                case 2 -> "II — Triple volley";
                case 3 -> "III — Tonnerre roulant";
                case 4 -> "IV — Pluie quintuple";
                case 5 -> "V — Salve de six";
                default -> "?";
            };
            case ACID -> switch (tier) {
                case 1 -> "I — Vapeur irritante";
                case 2 -> "II — Fumee collante";
                case 3 -> "III — Eclat toxique";
                case 4 -> "IV — Nuage persistant";
                case 5 -> "V — Mare putride";
                default -> "?";
            };
        };
    }

    public static List<String> tierLines(MortarUpgradePath path, int tier) {
        return switch (path) {
            case INCENDIARY -> switch (tier) {
                case 1 -> List.of("+2 cœurs brulure cible", "+5% degats zone");
                case 2 -> List.of("+4 cœurs brulure", "+12% degats zone");
                case 3 -> List.of("+6 cœurs, zone flammes elargie", "+18% degats");
                case 4 -> List.of("+8 cœurs, particules infernales", "+25% degats");
                case 5 -> List.of("+10 cœurs brulure max", "+35% degats zone");
                default -> List.of();
            };
            case BARRAGE -> switch (tier) {
                case 1 -> List.of("+1 obus par tir (2 total)");
                case 2 -> List.of("+2 obus (3 total), leger ecart");
                case 3 -> List.of("+3 obus (4 total)");
                case 4 -> List.of("+4 obus (5 total)");
                case 5 -> List.of("+5 obus (6 total), dispersion");
                default -> List.of();
            };
            case ACID -> switch (tier) {
                case 1 -> List.of("Corrosion directe + poison visible 4 s", "Nuage vert court");
                case 2 -> List.of("Poison fort 5 s + lenteur", "Nuage DOT prolonge");
                case 3 -> List.of("Poison II + degats magiques", "Fumee toxique dense");
                case 4 -> List.of("Nausee + nuage large", "DOT renforce");
                case 5 -> List.of("Poison max + wither sur monstres", "Tempete acide");
                default -> List.of();
            };
        };
    }

    /** Obus supplementaires (voie Barrage). Sans voie : 0. */
    public static int extraShellsPerShot(MortarUpgradeState st) {
        if (st.isEmpty() || st.path() != MortarUpgradePath.BARRAGE) {
            return 0;
        }
        return st.tier();
    }

    public static double incendiaryDamageMul(MortarUpgradeState st) {
        if (st.isEmpty() || st.path() != MortarUpgradePath.INCENDIARY) {
            return 1.0;
        }
        return 1.0 + 0.07 * st.tier();
    }

    /** Demi-cœurs de brûlure appliqués aux victimes dans la zone (voie incendiaire). */
    public static double incendiaryBurnHalfHearts(MortarUpgradeState st) {
        if (st.isEmpty() || st.path() != MortarUpgradePath.INCENDIARY) {
            return 0;
        }
        return 5.0 * st.tier();
    }

    public static int incendiaryFireTicks(MortarUpgradeState st) {
        if (st.isEmpty() || st.path() != MortarUpgradePath.INCENDIARY) {
            return 0;
        }
        return 48 * st.tier();
    }

    /** Demi-cœurs de corrosion (touche tout le monde, y compris non-vivants sensibles aux degats). */
    public static double acidCorrosionHalfHearts(MortarUpgradeState st) {
        if (st.isEmpty() || st.path() != MortarUpgradePath.ACID) {
            return 0;
        }
        return 2.2 + 1.15 * st.tier();
    }

    public static boolean canBuyTier(MortarUpgradeState current, MortarUpgradePath path, int tier) {
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

    public static boolean pathLocked(MortarUpgradeState current, MortarUpgradePath path) {
        return !current.isEmpty() && current.path() != path;
    }

    public static boolean tierOwned(MortarUpgradeState current, MortarUpgradePath path, int tier) {
        return !current.isEmpty() && current.path() == path && current.tier() >= tier;
    }

    public static List<Component> loreLines(MortarUpgradeState st) {
        return loreLines(st, GameLocale.FR);
    }

    public static List<Component> loreLines(MortarUpgradeState st, GameLocale loc) {
        List<Component> lines = new ArrayList<>();
        if (st.isEmpty()) {
            return lines;
        }
        lines.add(Component.text(I18n.string(loc, "upgrade.mortar_header", st.path().styleName(loc)), NamedTextColor.GOLD));
        for (int t = 1; t <= st.tier(); t++) {
            lines.add(Component.text("  " + tierTitle(st.path(), t, loc), NamedTextColor.YELLOW));
        }
        return lines;
    }
}
