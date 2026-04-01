package fr.dpmr.cosmetics;

public enum CosmeticType {
    SHOT,
    AURA,
    PARACHUTE,
    VANITY,
    /** Skins couteau DPMR : modele + FX, memes degats que l'arme de base. */
    KNIFE_SKIN,
    /** Skins d'arme (ex. Thompson) : NBT + CustomModelData, achetables au shop. */
    WEAPON_SKIN
}
