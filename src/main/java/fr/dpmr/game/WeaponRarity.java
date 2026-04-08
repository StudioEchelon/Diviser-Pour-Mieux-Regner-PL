package fr.dpmr.game;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum WeaponRarity {
    COMMON(NamedTextColor.GRAY, "Commun"),
    UNCOMMON(NamedTextColor.GREEN, "Peu commun"),
    RARE(NamedTextColor.AQUA, "Rare"),
    EPIC(NamedTextColor.LIGHT_PURPLE, "\u00c9pique"),
    LEGENDARY(NamedTextColor.GOLD, "L\u00e9gendaire"),
    MYTHIC(TextColor.color(0xFF3D9A), "Mythique"),
    GHOST(TextColor.color(0x9B6BFF), "Ghost");

    private final TextColor color;
    private final String displayFr;

    WeaponRarity(TextColor color, String displayFr) {
        this.color = color;
        this.displayFr = displayFr;
    }

    public TextColor color() {
        return color;
    }

    public String displayFr() {
        return displayFr;
    }

    public boolean glint() {
        return this == RARE || this == EPIC || this == LEGENDARY || this == MYTHIC || this == GHOST;
    }

    /** Max durability (shots) before the weapon breaks. */
    public int maxWeaponDurability() {
        return switch (this) {
            case COMMON -> 250;
            case UNCOMMON -> 325;
            case RARE -> 400;
            case EPIC -> 800;
            case LEGENDARY -> 2000;
            case MYTHIC -> 3000;
            case GHOST -> 800;
        };
    }
}
