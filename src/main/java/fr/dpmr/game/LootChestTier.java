package fr.dpmr.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * Styles de coffres loot (matériaux vanilla distincts). Le resource pack peut
 * remplacer les textures de ces blocs si besoin d'un rendu 100 % custom.
 */
public enum LootChestTier {
    TIER_1(Material.CHEST, "I", NamedTextColor.GREEN),
    TIER_2(Material.TRAPPED_CHEST, "II", NamedTextColor.GOLD),
    TIER_3(Material.ENDER_CHEST, "III", NamedTextColor.LIGHT_PURPLE),
    /** Tonneau en eau : 1 loot ; placement dans l'eau uniquement. */
    MARITIME_1(Material.BARREL, "Maritime I", NamedTextColor.AQUA),
    /** Fumoir en eau : 2 tirages ; loot entre maritime I et coffre II. */
    MARITIME_2(Material.SMOKER, "Maritime II", NamedTextColor.GOLD),
    /** Haut fourneau en eau : 3 tirages ; loot proche du coffre III. */
    MARITIME_3(Material.BLAST_FURNACE, "Maritime III", NamedTextColor.LIGHT_PURPLE);

    private final Material material;
    private final String roman;
    private final NamedTextColor labelColor;

    LootChestTier(Material material, String roman, NamedTextColor labelColor) {
        this.material = material;
        this.roman = roman;
        this.labelColor = labelColor;
    }

    public Material material() {
        return material;
    }

    public String roman() {
        return roman;
    }

    public boolean isMaritime() {
        return this == MARITIME_1 || this == MARITIME_2 || this == MARITIME_3;
    }

    /** Tier portable 1–6 (1–3 classiques, 4–6 maritime). */
    public static LootChestTier fromPortableTier(int tier) {
        int t = Math.max(1, Math.min(6, tier));
        return switch (t) {
            case 1 -> TIER_1;
            case 2 -> TIER_2;
            case 3 -> TIER_3;
            case 4 -> MARITIME_1;
            case 5 -> MARITIME_2;
            case 6 -> MARITIME_3;
            default -> TIER_1;
        };
    }

    /** Titre au-dessus des coffres enregistrés dans la config. */
    public String configuredHologramTitle() {
        return switch (this) {
            case MARITIME_1 -> "Maritime Chest";
            case MARITIME_2 -> "Maritime Chest II";
            case MARITIME_3 -> "Maritime Chest III";
            default -> "Coffre " + roman;
        };
    }

    public NamedTextColor labelColor() {
        return labelColor;
    }

    public String configSection() {
        return switch (this) {
            case TIER_1 -> "tier-1";
            case TIER_2 -> "tier-2";
            case TIER_3 -> "tier-3";
            case MARITIME_1 -> "maritime";
            case MARITIME_2 -> "maritime-2";
            case MARITIME_3 -> "maritime-3";
        };
    }

    public static LootChestTier fromMaterial(Material m) {
        for (LootChestTier t : values()) {
            if (t.material == m) {
                return t;
            }
        }
        return TIER_1;
    }

    public static boolean isLootChestBlock(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.ENDER_CHEST
                || m == Material.BARREL || m == Material.SMOKER || m == Material.BLAST_FURNACE;
    }
}
