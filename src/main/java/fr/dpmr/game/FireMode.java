package fr.dpmr.game;

public enum FireMode {
    HITSCAN,
    PROJECTILE_SNOWBALL,
    PROJECTILE_ARROW,
    PROJECTILE_EGG,
    PROJECTILE_LLAMA_SPIT,
    /** Châtaignes = petites explosions zone */
    PROJECTILE_CHESTNUT,
    /** 5 projectiles (pompe) */
    PROJECTILE_SHOTGUN_FIVE,
    /** Fusil a pompe 4 directions (avant/arriere/gauche/droite) */
    HITSCAN_CROSS,
    /** Essence: projectile qui laisse une zone inflammable */
    PROJECTILE_GASOLINE,
    /** Bombe avec upgrades rebond / salve */
    PROJECTILE_BOMB,
    /** Bazooka visuel : blocs pierre (forme voiture), 1 obus, explosion zone */
    PROJECTILE_CLIO3,
    /** Mortier : trajectoire en cloche vers le point vise, atelier 3 voies */
    PROJECTILE_MORTAR,
    /** Lance-roquettes : missile / demolition / drone (atelier) */
    PROJECTILE_ROCKET,
    /** Missile nucleaire (charge longue, zone visible) */
    NUCLEAR_STRIKE,
    /** Rayon sans degats — attire le joueur vers le point vise (grappin) */
    GRAPPLE_BEAM,
    /** Flacon jeté : zone au sol (soin / buff alliés ou debuff zone) */
    PROJECTILE_SERUM_ZONE,
    /** Projectile : soigne un joueur touché (pas le tireur) */
    PROJECTILE_HEAL_DART,
    /** Hitscan : puissance et portée selon la tension de l’arc (clic droit maintenu, relâcher pour tirer). */
    HITSCAN_BOW_CHARGE,
    /** Pose une tourelle en blocs, monte dessus, tir au clic droit ; sneak : ranger */
    TURRET_DEPLOY
}
