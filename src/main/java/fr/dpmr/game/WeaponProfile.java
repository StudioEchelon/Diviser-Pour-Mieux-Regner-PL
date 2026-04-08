package fr.dpmr.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Armes DPMR (catalogue reduit).
 */
public enum WeaponProfile {

    // --- Commun ---
    JERRYCAN(Material.BUCKET, "J-20 « Incendiaire »", NamedTextColor.YELLOW,
            0.0, 24, 10, 60, 18, 4,
            FireMode.PROJECTILE_GASOLINE, Particle.DRIPPING_HONEY, Sound.ITEM_BUCKET_EMPTY,
            0.8f, 1.2f, 1.6, 1, 1.1, 2, WeaponRarity.COMMON, ScopeProfile.NONE, -1),

    CM_BAMBOU(Material.BAMBOO, "Sarbacane bambou", NamedTextColor.GREEN, 3.8, 30, 16, 64, 20, 4,
            FireMode.PROJECTILE_SNOWBALL, Particle.CLOUD, Sound.ENTITY_SNOWBALL_THROW, 0.55f, 1.4f, 1.2, 1, 1.9, 0, WeaponRarity.COMMON,
            ScopeProfile.NONE, -1),

    GLITCH(Material.REPEATING_COMMAND_BLOCK, "GLITCH", NamedTextColor.GREEN, 4.8, 44, 22, 66, 8, 1,
            FireMode.HITSCAN, Particle.ENCHANTED_HIT, Sound.BLOCK_NOTE_BLOCK_BIT, 0.4f, 2.0f, 3.2, 1, 0, 2, WeaponRarity.COMMON, ScopeProfile.NONE, -1),

    GLOCK18(Material.STICK, "GLOCK 18", NamedTextColor.DARK_GRAY, 4.0, 32, 18, 72, 22, 2,
            FireMode.HITSCAN, Particle.CRIT, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 1.7f, 2.5, 1, 0, 2, WeaponRarity.COMMON, ScopeProfile.NONE, -1),

    /** Pistolet basique, chargeur type barillet (6 coups). */
    REVOLVER(Material.STICK, "Revolver", NamedTextColor.GRAY, 4.8, 30, 6, 36, 38, 10,
            FireMode.HITSCAN, Particle.SMOKE, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.48f, 1.35f, 2.2, 1, 0, 2,
            WeaponRarity.COMMON, ScopeProfile.NONE, -1),

    /** Variante rustique (texture chataigne / bronze). */
    CM_REVOLVER_CHATAIGNE(Material.STICK, "Revolver chataigne", NamedTextColor.GOLD, 4.4, 28, 6, 36, 36, 11,
            FireMode.HITSCAN, Particle.SMOKE, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.46f, 1.32f, 2.35, 1, 0, 2,
            WeaponRarity.COMMON, ScopeProfile.NONE, -1),

    /** Fusil a pompe basique, rarete commune (texture grise). */
    CM_SHOTGUN(Material.GOLDEN_HOE, "Classique", NamedTextColor.GRAY, 10.0, 21, 6, 36, 50, 24,
            FireMode.HITSCAN, Particle.SMOKE, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.82f, 6.4, 10, 0, 3,
            WeaponRarity.COMMON, ScopeProfile.NONE, -1),

    CM_PEPITE(Material.IRON_NUGGET, "Pepites", NamedTextColor.WHITE, 4.2, 30, 15, 60, 20, 3,
            FireMode.HITSCAN, Particle.FIREWORK, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.46f, 1.6f, 2.5, 1, 0, 2, WeaponRarity.COMMON,
            ScopeProfile.NONE, -1),

    CM_PANNEAU(Material.OAK_SIGN, "Pancarte claquee", NamedTextColor.GOLD, 4.0, 32, 10, 40, 26, 5,
            FireMode.HITSCAN, Particle.CAMPFIRE_COSY_SMOKE, Sound.BLOCK_WOOD_HIT, 0.5f, 1.15f, 1.9, 1, 0, 2, WeaponRarity.COMMON,
            ScopeProfile.NONE, -1),

    HURRICANE(Material.GOLDEN_HORSE_ARMOR, "HURRICANE", NamedTextColor.GREEN, 2.4, 38, 35, 140, 18, 1,
            FireMode.HITSCAN, Particle.SOUL_FIRE_FLAME, Sound.BLOCK_NOTE_BLOCK_PLING, 0.35f, 1.9f, 2.8, 1, 0, 2, WeaponRarity.COMMON, ScopeProfile.NONE, -1),

    // --- Peu commun ---
    AK47(Material.IRON_AXE, "AK-47", NamedTextColor.GRAY, 7.5, 48, 30, 120, 36, 3,
            FireMode.HITSCAN, Particle.SMOKE, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.85f, 0.95f, 1.8, 1, 0, 3, WeaponRarity.UNCOMMON, ScopeProfile.NONE, -1),

    CARABINE_MK18(Material.GOLDEN_PICKAXE, "Carabine MK18", NamedTextColor.GOLD, 6.8, 52, 30, 120, 32, 5,
            FireMode.HITSCAN, Particle.SMOKE, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 0.82f, 1.08f, 1.35, 1, 0, 3, WeaponRarity.UNCOMMON, ScopeProfile.NONE, -1),

    FUSIL_POMPE_RL(Material.BRUSH, "Fusil a pompe", NamedTextColor.DARK_GRAY, 13.0, 22, 7, 42, 48, 22,
            FireMode.HITSCAN, Particle.SMOKE, Sound.ENTITY_GENERIC_EXPLODE, 0.72f, 0.82f, 6.2, 10, 0, 3, WeaponRarity.UNCOMMON, ScopeProfile.NONE, -1),

    NOVA(Material.BLAZE_ROD, "NOVA", NamedTextColor.GOLD, 6.0, 52, 14, 56, 28, 2,
            FireMode.HITSCAN, Particle.FLAME, Sound.ENTITY_BLAZE_SHOOT, 0.85f, 1.35f, 0.6, 1, 0, 3, WeaponRarity.UNCOMMON, ScopeProfile.NONE, -1),

    PULSE(Material.IRON_HORSE_ARMOR, "PULSE", NamedTextColor.AQUA, 5.0, 48, 16, 64, 25, 2,
            FireMode.HITSCAN, Particle.ELECTRIC_SPARK, Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f, 1.5f, 1.0, 1, 0, 4, WeaponRarity.UNCOMMON, ScopeProfile.NONE, -1),

    /** Mitraillette Thompson : skins vendus a part (PDC {@code weapon_cosmetic}). Item de base : carrot_on_a_stick (CMD fiable). */
    THOMPSON(Material.CARROT_ON_A_STICK, "Thompson", NamedTextColor.GRAY, 6.0, 44, 28, 112, 30, 3,
            FireMode.HITSCAN, Particle.SMOKE, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.78f, 1.12f, 2.4, 1, 0, 3,
            WeaponRarity.COMMON, ScopeProfile.NONE, -1),

    UC_EPEE_BOIS(Material.WOODEN_SWORD, "Rapiere bois", NamedTextColor.GOLD, 5.4, 41, 14, 56, 27, 4,
            FireMode.HITSCAN, Particle.SWEEP_ATTACK, Sound.ENTITY_PLAYER_ATTACK_WEAK, 0.5f, 1.3f, 1.5, 1, 0, 3, WeaponRarity.UNCOMMON,
            ScopeProfile.NONE, -1),

    /**
     * Couteau : tres courte portee, gros degats par coup (pas un one-shot full vie / armure),
     * cadence limitee. Les skins cosmetics changent uniquement modele / particules / sons.
     */
    COUTEAU_COMBAT(Material.IRON_SWORD, "Couteau de combat", NamedTextColor.DARK_RED,
            14.0, 4.8, 6, 24, 38, 22,
            FireMode.HITSCAN, Particle.SWEEP_ATTACK, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f, 1.05f, 0.12, 1, 2.4, 4,
            WeaponRarity.UNCOMMON, ScopeProfile.NONE, -1),

    // --- Rare ---
    BOMB_ORB(Material.FIRE_CHARGE, "Lance-bombes", NamedTextColor.RED, 7.0, 40, 4, 24, 45, 12,
            FireMode.PROJECTILE_BOMB, Particle.LAVA, Sound.ENTITY_TNT_PRIMED, 0.9f, 0.95f, 1.8, 1, 1.2, 5, WeaponRarity.RARE, ScopeProfile.NONE, -1),

    /** Lance-roquettes visuel « voiture en pierre », chargeur 1, rechargement long, grosse explosion. */
    CLIO3(Material.DISPENSER, "Clio 3", NamedTextColor.DARK_GRAY,
            26.0, 96, 1, 12, 120, 52,
            FireMode.PROJECTILE_CLIO3, Particle.CAMPFIRE_COSY_SMOKE, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.72f, 0.06, 1, 0.76, 6,
            WeaponRarity.EPIC, ScopeProfile.NONE, 3),

    /** Mortier portable : tir en cloche, atelier (flammes / barrage / acide). */
    LE_MORTIER(Material.BLAST_FURNACE, "Mortier", NamedTextColor.GOLD,
            9.5, 56, 1, 10, 68, 40,
            FireMode.PROJECTILE_MORTAR, Particle.CAMPFIRE_COSY_SMOKE, Sound.ENTITY_GENERIC_EXPLODE, 0.88f, 0.82f, 0.35, 1, 0.98, 6,
            WeaponRarity.EPIC, ScopeProfile.NONE, 2),

    /** Lance-roquettes : atelier guide / demolition / drone mitrailleur. */
    LANCE_ROQUETTE(Material.ENDER_EYE, "Lance-roquettes", NamedTextColor.RED,
            12.0, 80, 1, 8, 88, 38,
            FireMode.PROJECTILE_ROCKET, Particle.SMOKE, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.92f, 0.95f, 0.12, 1, 1.52, 7,
            WeaponRarity.EPIC, ScopeProfile.NONE, 3),

    /**
     * Tourelle portative : clic droit au sol pose des blocs + siege ; tu es monte dessus.
     * Clic droit monte : tir hitscan. Sneak : demonte tout.
     */
    TOURELLE_PORTATIVE(Material.SMITHING_TABLE, "Tourelle portative", NamedTextColor.DARK_AQUA,
            5.8, 42, 35, 105, 52, 4,
            FireMode.TURRET_DEPLOY, Particle.SMOKE, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.82f, 1.05f, 1.85, 1, 0, 3,
            WeaponRarity.EPIC, ScopeProfile.NONE, -1),

    DEAGLE_RL(Material.PRISMARINE_CRYSTALS, "Desert Eagle", NamedTextColor.DARK_GRAY, 10.5, 32, 8, 24, 42, 20,
            FireMode.HITSCAN, Particle.CRIT, Sound.ENTITY_GENERIC_EXPLODE, 0.75f, 0.95f, 2.65, 1, 0, 3, WeaponRarity.RARE, ScopeProfile.NONE, -1),

    FUSIL_POMPE_CROIX(Material.IRON_HOE, "Fusil a pompe croix", NamedTextColor.RED, 16.5, 16, 6, 36, 44, 24,
            FireMode.HITSCAN_CROSS, Particle.SMOKE, Sound.ENTITY_GENERIC_EXPLODE, 0.78f, 0.75f, 4.0, 1, 0, 4, WeaponRarity.RARE, ScopeProfile.NONE, -1),

    LANCE_MARRONS(Material.COCOA_BEANS, "Lance-marrons", NamedTextColor.GOLD, 6.0, 36, 6, 36, 28, 10,
            FireMode.PROJECTILE_CHESTNUT, Particle.CRIT, Sound.ENTITY_SNOWBALL_THROW, 0.95f, 0.9f, 2.0, 1, 1.45, 5, WeaponRarity.RARE, ScopeProfile.NONE, -1),

    RADIANT(Material.GLOWSTONE_DUST, "RADIANT", NamedTextColor.YELLOW, 6.5, 50, 10, 40, 28, 2,
            FireMode.HITSCAN, Particle.HAPPY_VILLAGER, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f, 0.7, 1, 0, 3, WeaponRarity.RARE, ScopeProfile.NONE, -1),

    RZ_COEUR_MER(Material.HEART_OF_THE_SEA, "Coeur des abysses", NamedTextColor.AQUA, 7.8, 56, 8, 32, 40, 5,
            FireMode.HITSCAN, Particle.BUBBLE_COLUMN_UP, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.68f, 0.95f, 0.88, 1, 0, 5, WeaponRarity.RARE,
            ScopeProfile.NONE, -1),

    SERUM_SOIN(Material.HONEY_BOTTLE, "Serum S2 — soin", NamedTextColor.RED, 2.0, 4.2, 8, 40, 52, 15,
            FireMode.PROJECTILE_SERUM_ZONE, Particle.HEART, Sound.ITEM_BOTTLE_FILL, 0.65f, 1.28f, 2.0, 1, 1.22, 8, WeaponRarity.RARE, ScopeProfile.NONE, -1),

    /** Soin par tir : stat « dégâts » = demi-cœurs restaurés (compatible upgrades dégâts tech/survie). */
    LANCE_SOIN(Material.GOLDEN_CARROT, "Lance-soin", NamedTextColor.LIGHT_PURPLE, 6.0, 44, 14, 56, 28, 6,
            FireMode.PROJECTILE_HEAL_DART, Particle.HEART, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.52f, 1.42f, 1.35, 1, 1.82, 7,
            WeaponRarity.RARE, ScopeProfile.NONE, -1),

    /**
     * Sniper d'appoint (rare) : lunette comme les snipers, mais moins de degats et moins precis
     * que Dragunov / AWP.
     */
    CARABINE_RARE(Material.IRON_PICKAXE, "Carabine", NamedTextColor.AQUA,
            10.0, 70, 12, 48, 46, 11,
            FireMode.HITSCAN, Particle.ELECTRIC_SPARK, Sound.ENTITY_ARROW_SHOOT, 0.88f, 0.78f, 0.42, 1, 0, 3,
            WeaponRarity.RARE,
            ScopeProfile.sniper(0.058, 1.22, 1.06, 11.0), -1),

    // --- Epique ---
    DRAGUNOV_SVD(Material.CROSSBOW, "Dragunov SVD", NamedTextColor.DARK_GREEN, 16.0, 90, 10, 40, 60, 16,
            FireMode.HITSCAN, Particle.END_ROD, Sound.ENTITY_ARROW_SHOOT, 0.95f, 0.68f, 0.14, 1, 0, 3, WeaponRarity.EPIC,
            ScopeProfile.sniper(0.024, 1.5, 1.12, 16), -1),

    EP_DRAGON(Material.DRAGON_BREATH, "Souffle du dragon", NamedTextColor.DARK_PURPLE, 9.0, 54, 8, 28, 44, 6,
            FireMode.PROJECTILE_EGG, Particle.DRAGON_BREATH, Sound.ENTITY_GENERIC_EXPLODE, 0.72f, 0.92f, 0.7, 1, 2.25, 0, WeaponRarity.EPIC,
            ScopeProfile.NONE, -1),

    EP_GRIMOIRE(Material.ENCHANTED_BOOK, "Grimoire", NamedTextColor.LIGHT_PURPLE, 8.9, 56, 7, 24, 45, 6,
            FireMode.HITSCAN, Particle.ENCHANT, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.71f, 1.05f, 0.72, 1, 0, 6, WeaponRarity.EPIC,
            ScopeProfile.NONE, -1),

    EP_NETH_INGOT(Material.NETHERITE_INGOT, "Lingot noir", NamedTextColor.DARK_GRAY, 9.5, 59, 6, 22, 50, 7,
            FireMode.HITSCAN, Particle.LAVA, Sound.BLOCK_NETHERITE_BLOCK_HIT, 0.78f, 0.85f, 0.62, 1, 0, 5, WeaponRarity.EPIC,
            ScopeProfile.NONE, -1),

    EP_PHANTOM(Material.PHANTOM_MEMBRANE, "Membrane spectrale", NamedTextColor.GRAY, 9.1, 57, 8, 26, 46, 6,
            FireMode.HITSCAN, Particle.END_ROD, Sound.ENTITY_PHANTOM_FLAP, 0.73f, 0.93f, 0.68, 1, 0, 5, WeaponRarity.EPIC,
            ScopeProfile.NONE, -1),

    EP_REVOLVER_CYBER(Material.STICK, "Cyber", NamedTextColor.AQUA, 6.5, 32, 6, 30, 34, 7,
            FireMode.HITSCAN, Particle.ELECTRIC_SPARK, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.52f, 1.55f, 1.85, 1, 0, 3,
            WeaponRarity.RARE, ScopeProfile.NONE, -1),

    GRAPPIN_RL(Material.FISHING_ROD, "Pistolet-grappin", NamedTextColor.AQUA, 0.0, 38, 10, 40, 35, 0,
            FireMode.GRAPPLE_BEAM, Particle.ENCHANT, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.7f, 1.25f, 0.0, 1, 0, 4, WeaponRarity.EPIC, ScopeProfile.NONE, -1),

    SOLAR(Material.BLAZE_POWDER, "SOLAR", NamedTextColor.GOLD, 8.0, 46, 8, 24, 38, 4,
            FireMode.HITSCAN, Particle.LAVA, Sound.ENTITY_GENERIC_BURN, 0.9f, 1.1f, 0.9, 1, 0, 4, WeaponRarity.EPIC, ScopeProfile.NONE, -1),

    LG_TOTEM(Material.TOTEM_OF_UNDYING, "Totem de guerre", NamedTextColor.GOLD, 12.5, 78, 8, 28, 48, 10,
            FireMode.HITSCAN, Particle.TOTEM_OF_UNDYING, Sound.ITEM_TOTEM_USE, 0.85f, 0.78f, 0.35, 1, 0, 4, WeaponRarity.EPIC,
            ScopeProfile.NONE, -1),

    LG_WITHER(Material.WITHER_SKELETON_SKULL, "Ossuaire", NamedTextColor.DARK_GRAY, 15.0, 92, 3, 12, 68, 24,
            FireMode.HITSCAN, Particle.SOUL, Sound.ENTITY_WITHER_SHOOT, 0.92f, 0.62f, 0.14, 1, 0, 2, WeaponRarity.EPIC,
            ScopeProfile.sniper(0.019, 1.62, 1.25, 22), -1),

    /** Pompe a canon lisse, rarete legendaire. */
    EP_FUSIL_POMPE(Material.DIAMOND_HOE, "\u00c9pique", NamedTextColor.GOLD,
            15.0, 21, 8, 40, 46, 20,
            FireMode.HITSCAN, Particle.DRAGON_BREATH, Sound.ENTITY_GENERIC_EXPLODE, 0.78f, 0.84f, 5.6, 10, 0, 5,
            WeaponRarity.LEGENDARY, ScopeProfile.NONE, -1),

    EP_POMPE_ACIDE(Material.IRON_SHOVEL, "Acide", NamedTextColor.GREEN,
            15.5, 20, 7, 35, 42, 20,
            FireMode.HITSCAN, Particle.DRIPPING_DRIPSTONE_LAVA, Sound.ENTITY_SLIME_JUMP, 0.76f, 0.88f, 5.8, 10, 0, 4,
            WeaponRarity.RARE, ScopeProfile.NONE, -1),

    // --- Legendaire ---
    AWP(Material.TRIPWIRE_HOOK, "AWP", NamedTextColor.DARK_GREEN, 18.0, 95, 5, 15, 65, 22,
            FireMode.HITSCAN, Particle.CRIT, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.55f, 0.1, 1, 0, 2, WeaponRarity.LEGENDARY,
            ScopeProfile.sniper(0.02, 1.65, 1.28, 20), -1),

    /**
     * Sniper légendaire : maintiens le clic droit (tension d’arc) — plus c’est long, plus le rayon porte loin et frappe fort.
     */
    LG_TRAQUEUR(Material.BOW, "Traqueur", NamedTextColor.GOLD,
            12.0, 78, 4, 16, 78, 28,
            FireMode.HITSCAN_BOW_CHARGE, Particle.END_ROD, Sound.ENTITY_ARROW_SHOOT,
            0.95f, 0.72f, 0.085, 1, 0, 3, WeaponRarity.LEGENDARY,
            ScopeProfile.sniper(0.018, 1.58, 1.22, 19), -1),

    LG_REVOLVER_FLAMME(Material.STICK, "Revolver flamme", NamedTextColor.RED,
            9.0, 36, 6, 24, 36, 10,
            FireMode.HITSCAN, Particle.FLAME, Sound.ITEM_FIRECHARGE_USE, 0.58f, 1.25f, 1.95, 1, 0, 4,
            WeaponRarity.LEGENDARY, ScopeProfile.NONE, -1),

    /** Double canon classique : 2 cartouches, salve dense. */
    LG_POMPE_DOUBLE(Material.STONE_HOE, "Pompe double", NamedTextColor.GOLD,
            16.5, 17, 2, 14, 52, 28,
            FireMode.HITSCAN, Particle.SMOKE, Sound.ENTITY_GENERIC_EXPLODE, 0.85f, 0.72f, 7.2, 12, 0, 4,
            WeaponRarity.LEGENDARY, ScopeProfile.NONE, -1),

    DOUBLE_PULSE(Material.DIAMOND_HORSE_ARMOR, "Double pulse", NamedTextColor.GOLD,
            8.5, 52, 24, 72, 28, 2,
            FireMode.HITSCAN, Particle.ELECTRIC_SPARK, Sound.ENTITY_EVOKER_CAST_SPELL, 0.75f, 1.4f, 0.8, 1, 0, 5,
            WeaponRarity.LEGENDARY, ScopeProfile.NONE, -1),

    // --- Mythique ---
    DIVISER_POUR_MIEUX_REGNER(Material.NETHERITE_HOE, "Diviser Pour Mieux Regner", NamedTextColor.DARK_RED,
            34.0, 12, 4, 20, 60, 34,
            FireMode.HITSCAN, Particle.FLAME, Sound.ENTITY_GENERIC_EXPLODE,
            1.0f, 0.78f, 7.5, 10, 0, 4, WeaponRarity.MYTHIC, ScopeProfile.NONE, -1),

    INFERNO(Material.FIRE_CHARGE, "Feu", NamedTextColor.RED, 9.5, 42, 7, 21, 34, 4,
            FireMode.HITSCAN, Particle.FLAME, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.15f, 1.0, 1, 0, 5, WeaponRarity.EPIC, ScopeProfile.NONE, -1),

    LG_ANCRE(Material.RESPAWN_ANCHOR, "Ancre du vide", NamedTextColor.RED, 14.2, 93, 4, 14, 66, 21,
            FireMode.HITSCAN, Particle.REVERSE_PORTAL, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.91f, 0.64f, 0.15, 1, 0, 3, WeaponRarity.LEGENDARY,
            ScopeProfile.NONE, -1),

    LG_BEACON(Material.BEACON, "Rayon phare", NamedTextColor.AQUA, 16.0, 98, 3, 10, 72, 26,
            FireMode.HITSCAN, Particle.END_ROD, Sound.BLOCK_BEACON_ACTIVATE, 0.95f, 0.58f, 0.1, 1, 0, 3, WeaponRarity.LEGENDARY,
            ScopeProfile.NONE, -1),

    MINIGUN_M134(Material.NETHERITE_AXE, "M134 Minigun", NamedTextColor.DARK_RED, 6.2, 58, 180, 540, 35, 1,
            FireMode.HITSCAN, Particle.SMOKE, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.95f, 0.92f, 2.9, 1, 0, 4, WeaponRarity.LEGENDARY,
            ScopeProfile.NONE, 2),

    THUNDER(Material.NETHER_STAR, "THUNDER", NamedTextColor.AQUA, 12.0, 62, 3, 9, 72, 12,
            FireMode.HITSCAN, Particle.SONIC_BOOM, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.55f, 0.18, 1, 0, 1, WeaponRarity.MYTHIC, ScopeProfile.NONE, -1),

    LG_POMPE_DOUBLE_CYBER(Material.NETHERITE_PICKAXE, "Double cyber", NamedTextColor.DARK_AQUA,
            18.5, 18, 2, 12, 54, 26,
            FireMode.HITSCAN, Particle.ELECTRIC_SPARK, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.88f, 6.9, 12, 0, 5,
            WeaponRarity.EPIC, ScopeProfile.NONE, -1),

    LG_TETE_DRAGON(Material.DRAGON_HEAD, "Rugissement", NamedTextColor.DARK_PURPLE, 13.5, 90, 4, 16, 62, 20,
            FireMode.HITSCAN, Particle.DRAGON_BREATH, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9f, 0.68f, 0.18, 1, 0, 3, WeaponRarity.MYTHIC,
            ScopeProfile.sniper(0.02, 1.6, 1.22, 20), -1),

    // --- Ghost ---
    GHOST_NUKE(Material.END_CRYSTAL, "Ghost — Missile", NamedTextColor.LIGHT_PURPLE, 0.0, 0, 1, 3, 600, 200,
            FireMode.NUCLEAR_STRIKE, Particle.SOUL_FIRE_FLAME, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f, 0, 1, 0, 8, WeaponRarity.GHOST, ScopeProfile.NONE, -1),

    GHOST_LANCE_FLAMME(Material.BLAZE_ROD, "Ghost Lance-flammes", NamedTextColor.DARK_PURPLE,
            14.5, 18, 120, 360, 20, 1,
            FireMode.HITSCAN, Particle.FLAME, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.4f, 3.6, 1, 0, 8, WeaponRarity.GHOST, ScopeProfile.NONE, -1),

    GHOST_POMPE(Material.NETHERITE_SHOVEL, "Ghost Pompe", NamedTextColor.DARK_PURPLE,
            27.0, 16, 7, 35, 52, 22,
            FireMode.HITSCAN, Particle.SOUL_FIRE_FLAME, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f, 6.8, 10, 0, 6, WeaponRarity.GHOST, ScopeProfile.NONE, -1),

    GHOST_DIVISER(Material.NETHERITE_HOE, "Ghost Diviser Pour Mieux Regner", NamedTextColor.DARK_PURPLE,
            44.0, 13, 4, 20, 70, 38,
            FireMode.HITSCAN, Particle.SOUL_FIRE_FLAME, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.62f, 7.0, 10, 0, 6, WeaponRarity.GHOST, ScopeProfile.NONE, -1);

    private final Material material;
    private final String displayName;
    private final NamedTextColor color;
    private final double damage;
    private final double range;
    private final int clipSize;
    private final int reserveAmmo;
    private final int reloadTicks;
    private final int cooldownTicks;
    private final FireMode fireMode;
    private final Particle trailParticle;
    private final Sound shootSound;
    private final float soundVolume;
    private final float soundPitch;
    private final double spreadDegrees;
    private final int pellets;
    private final double projectileSpeed;
    private final int trailDensity;
    private final WeaponRarity rarity;
    private final ScopeProfile scopeProfile;
    private final int heavyHoldSlowAmplifier;

    WeaponProfile(Material material, String displayName, NamedTextColor color,
                  double damage, double range, int clipSize, int reserveAmmo,
                  int reloadTicks, int cooldownTicks,
                  FireMode fireMode, Particle trailParticle, Sound shootSound,
                  float soundVolume, float soundPitch, double spreadDegrees,
                  int pellets, double projectileSpeed, int trailDensity, WeaponRarity rarity,
                  ScopeProfile scopeProfile, int heavyHoldSlowAmplifier) {
        this.material = material;
        this.displayName = displayName;
        this.color = color;
        this.damage = damage;
        this.range = range;
        this.clipSize = clipSize;
        this.reserveAmmo = reserveAmmo;
        this.reloadTicks = reloadTicks;
        this.cooldownTicks = cooldownTicks;
        this.fireMode = fireMode;
        this.trailParticle = trailParticle;
        this.shootSound = shootSound;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
        this.spreadDegrees = spreadDegrees;
        this.pellets = Math.max(1, pellets);
        this.projectileSpeed = projectileSpeed;
        this.trailDensity = Math.max(1, trailDensity);
        this.rarity = rarity;
        this.scopeProfile = scopeProfile;
        this.heavyHoldSlowAmplifier = heavyHoldSlowAmplifier;
    }

    public static WeaponProfile fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public Material material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor color() {
        return color;
    }

    public double damage() {
        return damage;
    }

    public double range() {
        return range;
    }

    public int clipSize() {
        return clipSize;
    }

    public int reserveAmmo() {
        return reserveAmmo;
    }

    public int reloadTicks() {
        return reloadTicks;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public FireMode fireMode() {
        return fireMode;
    }

    public Particle trailParticle() {
        return trailParticle;
    }

    public Sound shootSound() {
        return shootSound;
    }

    public float soundVolume() {
        return soundVolume;
    }

    public float soundPitch() {
        return soundPitch;
    }

    public double spreadDegrees() {
        return spreadDegrees;
    }

    public int pellets() {
        return pellets;
    }

    public double projectileSpeed() {
        return projectileSpeed;
    }

    public int trailDensity() {
        return trailDensity;
    }

    public WeaponRarity rarity() {
        return rarity;
    }

    public ScopeProfile scopeProfile() {
        return scopeProfile;
    }

    public boolean hasScope() {
        return scopeProfile.enabled();
    }

    public int heavyHoldSlowAmplifier() {
        return heavyHoldSlowAmplifier;
    }

    public boolean hasHeavyWeight() {
        return heavyHoldSlowAmplifier >= 0;
    }

    public double baseSpreadDegrees() {
        return spreadDegrees;
    }

    public double baseRange() {
        return range;
    }

    public double baseDamage() {
        return damage;
    }

    public double effectiveSpread(Player player) {
        return scopeProfile.spreadDegrees(this, player);
    }

    public double effectiveRange(Player player) {
        return scopeProfile.range(this, player);
    }

    public double effectiveDamage(Player player) {
        return scopeProfile.damage(this, player);
    }

    public double damagePerPellet(Player player) {
        return effectiveDamage(player) / pellets;
    }

    @Deprecated
    public double damagePerPellet() {
        return damage / pellets;
    }

    public String modeLabel() {
        return switch (fireMode) {
            case HITSCAN -> "Rayon";
            case PROJECTILE_SNOWBALL -> "Glace";
            case PROJECTILE_ARROW -> "Fleche";
            case PROJECTILE_EGG -> "Acide";
            case PROJECTILE_LLAMA_SPIT -> "Crachat";
            case PROJECTILE_CHESTNUT -> "Chataignes";
            case PROJECTILE_SHOTGUN_FIVE -> "Pompe 5";
            case HITSCAN_CROSS -> "Pompe croix";
            case PROJECTILE_GASOLINE -> "Essence";
            case PROJECTILE_BOMB -> "Bombe";
            case PROJECTILE_CLIO3 -> "Clio lancee";
            case PROJECTILE_MORTAR -> "Mortier";
            case PROJECTILE_ROCKET -> "Roquette";
            case NUCLEAR_STRIKE -> "Nucleaire";
            case GRAPPLE_BEAM -> "Grappin";
            case PROJECTILE_SERUM_ZONE -> "Serum de zone";
            case PROJECTILE_HEAL_DART -> "Soin par tir";
            case HITSCAN_BOW_CHARGE -> "Rayon charge (arc)";
            case TURRET_DEPLOY -> "Tourelle";
        };
    }

    /**
     * Recul cumulatif ajoute apres un tir (decroit entre les tirs).
     */
    public double recoilAddedPerShot() {
        if (fireMode == FireMode.GRAPPLE_BEAM || fireMode == FireMode.NUCLEAR_STRIKE
                || fireMode == FireMode.PROJECTILE_SERUM_ZONE || fireMode == FireMode.PROJECTILE_HEAL_DART) {
            return 0;
        }
        double r = 0.42 + spreadDegrees() * 0.052 + (cooldownTicks() / 50.0) * 0.095;
        if (pellets() >= 8) {
            r += 2.1;
        } else if (pellets() > 1) {
            r += 0.72;
        }
        if (hasScope()) {
            r += 1.35;
        }
        return Math.min(7.0, r);
    }

    public boolean isGrapplingHook() {
        return fireMode == FireMode.GRAPPLE_BEAM;
    }

    public boolean isBombWeapon() {
        return this == BOMB_ORB;
    }

    public boolean isMortarWeapon() {
        return this == LE_MORTIER;
    }

    public boolean isRocketWeapon() {
        return this == LANCE_ROQUETTE;
    }

    public boolean isTurretWeapon() {
        return fireMode == FireMode.TURRET_DEPLOY;
    }

    /** Revolver de base : atelier lineaire 3 paliers (remplace les 3 voies classiques). */
    public boolean isRevolverWorkshopWeapon() {
        return this == REVOLVER;
    }

    public boolean isNuclearWeapon() {
        return this == GHOST_NUKE;
    }

    public boolean isMinigun() {
        return name().contains("MINIGUN");
    }

    public WeaponDamageType damageType() {
        if (fireMode == FireMode.PROJECTILE_BOMB || fireMode == FireMode.PROJECTILE_CLIO3
                || fireMode == FireMode.PROJECTILE_MORTAR || fireMode == FireMode.PROJECTILE_ROCKET
                || fireMode == FireMode.NUCLEAR_STRIKE) {
            return WeaponDamageType.EXPLOSIVE;
        }
        if (fireMode == FireMode.GRAPPLE_BEAM || fireMode == FireMode.PROJECTILE_SERUM_ZONE
                || fireMode == FireMode.PROJECTILE_GASOLINE || fireMode == FireMode.PROJECTILE_HEAL_DART) {
            return WeaponDamageType.UTILITY;
        }
        if (fireMode == FireMode.TURRET_DEPLOY) {
            return WeaponDamageType.ASSAULT_RIFLE;
        }
        String n = name();
        if (pellets >= 6 || n.contains("POMPE") || n.contains("SHOTGUN") || n.contains("M1014") || n.contains("SPAS")
                || n.contains("MODEL870")) {
            return WeaponDamageType.SHOTGUN;
        }
        if (n.equals("CARABINE_RARE")
                || n.contains("SNIPER") || n.contains("AWP") || n.contains("BARRETT") || n.contains("M24") || n.contains("L96")
                || n.contains("M82") || n.contains("DRAGUNOV") || n.contains("WITHER") || n.contains("TETE_DRAGON")
                || n.contains("TRAQUEUR")) {
            return WeaponDamageType.SNIPER;
        }
        if (n.contains("GLOCK") || n.contains("DEAGLE") || n.contains("USP") || n.contains("PISTOLET")
                || n.contains("REVOLVER")) {
            return WeaponDamageType.PISTOL;
        }
        if (n.contains("MINIGUN")) {
            return WeaponDamageType.ASSAULT_RIFLE;
        }
        if (n.contains("THOMPSON") || n.contains("MP") || n.contains("UZI") || n.contains("P90") || n.contains("MITRAILLETTE")) {
            return WeaponDamageType.SMG;
        }
        if (this == COUTEAU_COMBAT) {
            return WeaponDamageType.SMG;
        }
        return WeaponDamageType.ASSAULT_RIFLE;
    }

    public boolean isWarfareWeapon() {
        return switch (damageType()) {
            case PISTOL, SMG, ASSAULT_RIFLE, SHOTGUN, SNIPER, EXPLOSIVE -> true;
            case UTILITY -> false;
        };
    }

    /**
     * Armes « à feu » rayonnées : jauge de kills + perks roguelike (pas outils / explosifs / tourelle / couteau).
     */
    public boolean supportsKillPerkMeter() {
        if (this == COUTEAU_COMBAT) {
            return false;
        }
        if (isTurretWeapon() || isNuclearWeapon()) {
            return false;
        }
        if (damageType() == WeaponDamageType.EXPLOSIVE || damageType() == WeaponDamageType.UTILITY) {
            return false;
        }
        return switch (fireMode) {
            case HITSCAN, HITSCAN_CROSS, HITSCAN_BOW_CHARGE -> true;
            default -> false;
        };
    }
}
