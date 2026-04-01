package fr.dpmr.cosmetics;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

public enum CosmeticProfile {
    // Shot trails
    SHOT_HEART("shot_heart", CosmeticType.SHOT, "Tir: Coeurs", 120, Material.POPPY, Particle.HEART, NamedTextColor.RED, null, null),
    SHOT_END_ROD("shot_endrod", CosmeticType.SHOT, "Tir: End Rod", 160, Material.END_ROD, Particle.END_ROD, NamedTextColor.LIGHT_PURPLE, null, null),
    SHOT_SOUL("shot_soul", CosmeticType.SHOT, "Tir: Ames", 140, Material.SOUL_SOIL, Particle.SOUL, NamedTextColor.AQUA, null, null),
    SHOT_ELECTRIC("shot_electric", CosmeticType.SHOT, "Tir: Etincelles", 180, Material.LIGHTNING_ROD, Particle.ELECTRIC_SPARK, NamedTextColor.YELLOW, null, null),

    // Auras
    AURA_ENCHANT("aura_enchant", CosmeticType.AURA, "Aura: Enchant", 220, Material.ENCHANTED_BOOK, Particle.ENCHANT, NamedTextColor.LIGHT_PURPLE, null, null),
    AURA_CLOUD("aura_cloud", CosmeticType.AURA, "Aura: Nuage", 180, Material.WHITE_DYE, Particle.CLOUD, NamedTextColor.WHITE, null, null),
    AURA_FLAME("aura_flame", CosmeticType.AURA, "Aura: Flammes", 260, Material.BLAZE_POWDER, Particle.FLAME, NamedTextColor.GOLD, null, null),
    AURA_SNOW("aura_snow", CosmeticType.AURA, "Aura: Neige", 200, Material.SNOWBALL, Particle.SNOWFLAKE, NamedTextColor.AQUA, null, null),

    // Parachute FX
    PARA_CLOUD("para_cloud", CosmeticType.PARACHUTE, "Parachute: Nuage", 220, Material.WHITE_BANNER, Particle.CLOUD, NamedTextColor.WHITE, null, null),
    PARA_CHERRY("para_cherry", CosmeticType.PARACHUTE, "Parachute: Petales", 260, Material.PINK_PETALS, Particle.CHERRY_LEAVES, NamedTextColor.LIGHT_PURPLE, null, null),
    PARA_ENDROD("para_endrod", CosmeticType.PARACHUTE, "Parachute: End Rod", 300, Material.SPECTRAL_ARROW, Particle.END_ROD, NamedTextColor.AQUA, null, null),

    // Vanity (items)
    VANITY_CROWN("crown", CosmeticType.VANITY, "Couronne", 300, Material.GOLDEN_HELMET, null, NamedTextColor.GOLD, null, null),
    VANITY_WINGS("wings", CosmeticType.VANITY, "Ailes", 420, Material.ELYTRA, null, NamedTextColor.AQUA, null, null),
    VANITY_CAPE("cape", CosmeticType.VANITY, "Cape", 360, Material.LEATHER_CHESTPLATE, null, NamedTextColor.DARK_PURPLE, null, null),
    VANITY_COLLAR("collar", CosmeticType.VANITY, "Collier", 240, Material.CHAIN, null, NamedTextColor.YELLOW, null, null),

    // Couteau DPMR (COUTEAU_COMBAT) — degats identiques, modele + particules + son au coup
    KNIFE_KARAMBIT("knife_karambit", CosmeticType.KNIFE_SKIN, "Couteau: Karambit", 200, Material.GOLDEN_SWORD,
            Particle.SWEEP_ATTACK, NamedTextColor.GOLD, Sound.ENTITY_PLAYER_ATTACK_SWEEP, null),
    KNIFE_BUTTERFLY("knife_butterfly", CosmeticType.KNIFE_SKIN, "Couteau: Papillon", 220, Material.STONE_SWORD,
            Particle.CRIT, NamedTextColor.GRAY, Sound.ENTITY_PLAYER_ATTACK_STRONG, null),
    KNIFE_M9_BAYONET("knife_m9_bayonet", CosmeticType.KNIFE_SKIN, "Couteau: Bayonette M9", 180, Material.DIAMOND_SWORD,
            Particle.DAMAGE_INDICATOR, NamedTextColor.AQUA, Sound.ENTITY_PLAYER_ATTACK_CRIT, null),
    KNIFE_FLIP("knife_flip", CosmeticType.KNIFE_SKIN, "Couteau: Flip", 160, Material.NETHERITE_SWORD,
            Particle.ELECTRIC_SPARK, NamedTextColor.DARK_PURPLE, Sound.ITEM_SPYGLASS_STOP_USING, null),

    // Thompson (WeaponProfile.THOMPSON) — NBT dpmr:weapon_cosmetic = id cosmétique
    THOMPSON_SKIN_1("thompson_skin_1", CosmeticType.WEAPON_SKIN, "Thompson: Rubis", 200, Material.RED_DYE, null, NamedTextColor.RED, null, "THOMPSON"),
    THOMPSON_SKIN_2("thompson_skin_2", CosmeticType.WEAPON_SKIN, "Thompson: Ambre", 210, Material.ORANGE_DYE, null, NamedTextColor.GOLD, null, "THOMPSON"),
    THOMPSON_SKIN_3("thompson_skin_3", CosmeticType.WEAPON_SKIN, "Thompson: Sable", 200, Material.YELLOW_DYE, null, NamedTextColor.YELLOW, null, "THOMPSON"),
    THOMPSON_SKIN_4("thompson_skin_4", CosmeticType.WEAPON_SKIN, "Thompson: Jade", 220, Material.LIME_DYE, null, NamedTextColor.GREEN, null, "THOMPSON"),
    THOMPSON_SKIN_5("thompson_skin_5", CosmeticType.WEAPON_SKIN, "Thompson: Foret", 215, Material.GREEN_DYE, null, NamedTextColor.DARK_GREEN, null, "THOMPSON"),
    THOMPSON_SKIN_6("thompson_skin_6", CosmeticType.WEAPON_SKIN, "Thompson: Glacier", 225, Material.CYAN_DYE, null, NamedTextColor.AQUA, null, "THOMPSON"),
    THOMPSON_SKIN_7("thompson_skin_7", CosmeticType.WEAPON_SKIN, "Thompson: Azur", 220, Material.LIGHT_BLUE_DYE, null, NamedTextColor.BLUE, null, "THOMPSON"),
    THOMPSON_SKIN_8("thompson_skin_8", CosmeticType.WEAPON_SKIN, "Thompson: Minuit", 230, Material.BLUE_DYE, null, NamedTextColor.DARK_BLUE, null, "THOMPSON"),
    THOMPSON_SKIN_9("thompson_skin_9", CosmeticType.WEAPON_SKIN, "Thompson: Velours", 235, Material.PURPLE_DYE, null, NamedTextColor.LIGHT_PURPLE, null, "THOMPSON"),
    THOMPSON_SKIN_10("thompson_skin_10", CosmeticType.WEAPON_SKIN, "Thompson: Néon", 240, Material.MAGENTA_DYE, null, NamedTextColor.LIGHT_PURPLE, null, "THOMPSON"),
    THOMPSON_SKIN_11("thompson_skin_11", CosmeticType.WEAPON_SKIN, "Thompson: Corail", 220, Material.PINK_DYE, null, NamedTextColor.LIGHT_PURPLE, null, "THOMPSON"),
    THOMPSON_SKIN_12("thompson_skin_12", CosmeticType.WEAPON_SKIN, "Thompson: Graphite", 245, Material.GRAY_DYE, null, NamedTextColor.GRAY, null, "THOMPSON");

    private final String id;
    private final CosmeticType type;
    private final String displayName;
    private final int price;
    private final Material icon;
    private final Particle particle;
    private final NamedTextColor color;
    /** Son supplementaire au coup (couteau) ; null sinon. */
    private final Sound knifeHitSound;
    /** Nom {@link fr.dpmr.game.WeaponProfile#name()} pour les skins d'arme ; null sinon. */
    private final String weaponSkinFor;

    CosmeticProfile(String id, CosmeticType type, String displayName, int price, Material icon,
                    Particle particle, NamedTextColor color, Sound knifeHitSound, String weaponSkinFor) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.price = Math.max(0, price);
        this.icon = icon;
        this.particle = particle;
        this.color = color;
        this.knifeHitSound = knifeHitSound;
        this.weaponSkinFor = weaponSkinFor;
    }

    public String id() {
        return id;
    }

    public CosmeticType type() {
        return type;
    }

    public String displayName() {
        return displayName;
    }

    public int price() {
        return price;
    }

    public Material icon() {
        return icon;
    }

    public Particle particle() {
        return particle;
    }

    public NamedTextColor color() {
        return color;
    }

    /** Son esthetique au contact (skins couteau). */
    public Sound knifeHitSound() {
        return knifeHitSound;
    }

    /** Base d'arme pour un skin WEAPON_SKIN (ex. THOMPSON). */
    public String weaponSkinFor() {
        return weaponSkinFor;
    }

    public static CosmeticProfile fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String id = raw.trim().toLowerCase();
        for (CosmeticProfile p : values()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }
}
