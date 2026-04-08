package fr.dpmr.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Types de familiers de combat / soutien.
 */
public enum PetType {
    GUNNER("Artilleur", NamedTextColor.RED, Color.fromRGB(160, 40, 55), Material.CROSSBOW, Material.AIR),
    MEDIC("Soigneur", NamedTextColor.GREEN, Color.fromRGB(45, 195, 120), Material.SPLASH_POTION, Material.GOLDEN_APPLE),
    SNIPER("Tireur d'élite", NamedTextColor.DARK_GRAY, Color.fromRGB(72, 82, 98), Material.BOW, Material.AIR),
    SCOUT("Éclaireur", NamedTextColor.GOLD, Color.fromRGB(235, 205, 70), Material.CROSSBOW, Material.FEATHER),
    BRUTE("Colosse", NamedTextColor.DARK_RED, Color.fromRGB(110, 28, 35), Material.IRON_SWORD, Material.AIR);

    private final String displayFr;
    private final NamedTextColor nameColor;
    private final Color leatherTint;
    private final Material main;
    private final Material off;

    PetType(String displayFr, NamedTextColor nameColor, Color leatherTint, Material main, Material off) {
        this.displayFr = displayFr;
        this.nameColor = nameColor;
        this.leatherTint = leatherTint;
        this.main = main;
        this.off = off;
    }

    public String displayFr() {
        return displayFr;
    }

    public NamedTextColor nameColor() {
        return nameColor;
    }

    public ItemStack createMainHand() {
        ItemStack stack = new ItemStack(main);
        styleHeldItem(stack);
        return stack;
    }

    public ItemStack createOffHand() {
        if (off == Material.AIR) {
            return null;
        }
        ItemStack stack = new ItemStack(off);
        if (off != Material.FEATHER) {
            styleHeldItem(stack);
        }
        return stack;
    }

    /** Particules / trajectoires alignées sur la teinte du familier. */
    public Particle.DustOptions beamDust() {
        return new Particle.DustOptions(leatherTint, 0.85f);
    }

    public Particle.DustOptions auraDust() {
        return new Particle.DustOptions(leatherTint, 0.55f);
    }

    private void styleHeldItem(ItemStack stack) {
        stack.editMeta((ItemMeta meta) -> {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
    }

    public ItemStack leatherPiece(Material piece) {
        ItemStack stack = new ItemStack(piece);
        LeatherArmorMeta meta = (LeatherArmorMeta) stack.getItemMeta();
        meta.setColor(leatherTint);
        stack.setItemMeta(meta);
        return stack;
    }

    public static PetType fromArg(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "gunner", "mitrailleur" -> GUNNER;
            case "medic", "medecin" -> MEDIC;
            case "sniper" -> SNIPER;
            case "scout", "eclaireur" -> SCOUT;
            case "brute" -> BRUTE;
            default -> null;
        };
    }
}
