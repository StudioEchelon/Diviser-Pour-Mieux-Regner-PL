package fr.dpmr.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Effets visuels et sonores pour l'atelier d'armement (lodestone).
 */
public final class ModificationTableFx {

    private ModificationTableFx() {
    }

    public static void playAccessDenied(Player player, Block lodestone) {
        Location c = lodestone.getLocation().add(0.5, 0.55, 0.5);
        World w = c.getWorld();
        w.spawnParticle(Particle.SMOKE, c, 10, 0.2, 0.1, 0.2, 0.012);
        player.playSound(c, Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.55f);
        player.playSound(c, Sound.UI_BUTTON_CLICK, 0.35f, 0.7f);
    }

    /** Ouverture de l'interface — particules sobres, sons courts. */
    public static void playOpenBurst(Player player, Block table) {
        Location c = table.getLocation().add(0.5, 1.0, 0.5);
        World w = c.getWorld();
        w.spawnParticle(Particle.ENCHANT, c, 48, 0.35, 0.28, 0.35, 0.35);
        w.spawnParticle(Particle.END_ROD, c, 18, 0.28, 0.22, 0.28, 0.04);
        ringHorizontal(w, c, Particle.GLOW, 14, 0.75);

        player.playSound(c, Sound.BLOCK_LODESTONE_PLACE, 0.55f, 1.12f);
        player.playSound(c, Sound.BLOCK_SMITHING_TABLE_USE, 0.45f, 1.05f);
        player.sendActionBar(Component.text("Atelier d'armement", NamedTextColor.GOLD));
    }

    /**
     * Anneau tournant + scintillement tant que le joueur garde l'interface ouverte.
     */
    public static BukkitTask startAmbient(JavaPlugin plugin, Player player, Block table) {
        Location core = table.getLocation().add(0.5, 1.15, 0.5);
        return new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (!ModificationTableListener.GUI_TITLE.equals(player.getOpenInventory().title())) {
                    cancel();
                    return;
                }
                step++;
                if (step > 600) {
                    cancel();
                    return;
                }
                World w = core.getWorld();
                double phase = step * 0.12;
                int n = 10;
                for (int i = 0; i < n; i++) {
                    double a = phase + (Math.PI * 2 / n) * i;
                    double r = 0.7 + Math.sin(step * 0.06) * 0.08;
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    double y = Math.sin(phase + i * 0.4) * 0.12;
                    w.spawnParticle(Particle.WITCH, core.clone().add(x, y, z), 1, 0, 0, 0, 0);
                }
                w.spawnParticle(Particle.ENCHANT, core, 1, 0.35, 0.18, 0.35, 0);
                if (step % 38 == 0) {
                    player.playSound(core, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.08f, 1.6f + (step % 3) * 0.04f);
                }
            }
        }.runTaskTimer(plugin, 2L, 3L);
    }

    public static void playWeaponUpgradeSuccess(Player player, WeaponUpgradePath path) {
        Location c = player.getLocation().add(0, 1.1, 0);
        World w = c.getWorld();
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, c, 45, 0.45, 0.45, 0.45, 0.12);
        switch (path) {
            case ASSAULT -> {
                w.spawnParticle(Particle.FLAME, c, 35, 0.4, 0.35, 0.4, 0.05);
                w.spawnParticle(Particle.CRIT, c, 40, 0.5, 0.4, 0.5, 0.08);
                ringHorizontal(w, c, Particle.LAVA, 16, 0.9);
            }
            case SURVIVAL -> {
                w.spawnParticle(Particle.HEART, c, 12, 0.5, 0.3, 0.5, 0);
                w.spawnParticle(Particle.HAPPY_VILLAGER, c, 50, 0.55, 0.35, 0.55, 0.06);
                w.spawnParticle(Particle.COMPOSTER, c, 20, 0.35, 0.2, 0.35, 0.04);
            }
            case TECH -> {
                w.spawnParticle(Particle.ELECTRIC_SPARK, c, 60, 0.5, 0.45, 0.5, 0.15);
                w.spawnParticle(Particle.ENCHANT, c, 70, 0.45, 0.4, 0.45, 0.5);
                ringHorizontal(w, c, Particle.FIREWORK, 18, 1.0);
            }
        }
        player.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 1.28f);
        player.playSound(c, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.45f);
        player.playSound(c, Sound.BLOCK_SMITHING_TABLE_USE, 0.35f, 1.12f);
    }

    public static void playJerrycanUpgradeSuccess(Player player, JerrycanUpgradePath path) {
        Location c = player.getLocation().add(0, 1.0, 0);
        World w = c.getWorld();
        w.spawnParticle(Particle.LAVA, c, 30, 0.4, 0.35, 0.4, 0.04);
        w.spawnParticle(Particle.FLAME, c, 45, 0.45, 0.3, 0.45, 0.03);
        w.spawnParticle(Particle.SMOKE, c, 22, 0.35, 0.2, 0.35, 0.02);
        switch (path) {
            case THERMAL -> {
                w.spawnParticle(Particle.SMALL_FLAME, c, 40, 0.5, 0.4, 0.5, 0.02);
                ringHorizontal(w, c, Particle.FLAME, 14, 0.9);
            }
            case VISCOUS -> {
                w.spawnParticle(Particle.DRIPPING_DRIPSTONE_LAVA, c, 28, 0.4, 0.25, 0.4, 0.02);
                w.spawnParticle(Particle.ENTITY_EFFECT, c, 20, 0.35, 0.2, 0.35, 0.05);
            }
            case BREACH -> {
                w.spawnParticle(Particle.EXPLOSION, c, 1, 0.05, 0.05, 0.05, 0);
                w.spawnParticle(Particle.CRIT, c, 35, 0.45, 0.35, 0.45, 0.08);
            }
        }
        player.playSound(c, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.2f);
        player.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.32f);
        player.playSound(c, Sound.ITEM_BUCKET_FILL_LAVA, 0.35f, 1.15f);
    }

    public static void playMortarUpgradeSuccess(Player player, MortarUpgradePath path) {
        Location c = player.getLocation().add(0, 1.0, 0);
        World w = c.getWorld();
        w.spawnParticle(Particle.SMOKE, c, 35, 0.45, 0.3, 0.45, 0.04);
        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c, 22, 0.4, 0.25, 0.4, 0.02);
        switch (path) {
            case INCENDIARY -> {
                w.spawnParticle(Particle.FLAME, c, 40, 0.5, 0.35, 0.5, 0.04);
                ringHorizontal(w, c, Particle.SMALL_FLAME, 12, 0.85);
            }
            case BARRAGE -> w.spawnParticle(Particle.FIREWORK, c, 48, 0.55, 0.4, 0.55, 0.1);
            case ACID -> {
                w.spawnParticle(Particle.DRIPPING_DRIPSTONE_LAVA, c, 32, 0.45, 0.3, 0.45, 0.03);
                w.spawnParticle(Particle.ENTITY_EFFECT, c, 24, 0.4, 0.25, 0.4, 0.06);
            }
        }
        player.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.25f);
        player.playSound(c, Sound.BLOCK_ANVIL_USE, 0.35f, 1.15f);
        player.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.28f, 1.35f);
    }

    public static void playRevolverUpgradeSuccess(Player player, int tier) {
        Location c = player.getLocation().add(0, 1.0, 0);
        World w = c.getWorld();
        w.spawnParticle(Particle.SMOKE, c, 22, 0.38, 0.26, 0.38, 0.03);
        w.spawnParticle(Particle.CRIT, c, 18, 0.35, 0.22, 0.35, 0.06);
        if (tier >= 2) {
            w.spawnParticle(Particle.LAVA, c, 12, 0.32, 0.2, 0.32, 0.02);
            w.spawnParticle(Particle.EXPLOSION, c, 1, 0.06, 0.06, 0.06, 0);
        }
        if (tier >= 3) {
            w.spawnParticle(Particle.END_ROD, c, 24, 0.42, 0.3, 0.42, 0.04);
        }
        player.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.15f + tier * 0.06f);
        player.playSound(c, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.35f, 1.25f);
    }

    public static void playRocketUpgradeSuccess(Player player, RocketUpgradePath path) {
        Location c = player.getLocation().add(0, 1.0, 0);
        World w = c.getWorld();
        w.spawnParticle(Particle.SMOKE, c, 28, 0.4, 0.28, 0.4, 0.035);
        w.spawnParticle(Particle.END_ROD, c, 22, 0.38, 0.25, 0.38, 0.04);
        switch (path) {
            case GUIDED -> {
                w.spawnParticle(Particle.FIREWORK, c, 32, 0.45, 0.32, 0.45, 0.06);
                ringHorizontal(w, c, Particle.GLOW, 10, 0.72);
            }
            case DEVASTATOR -> {
                w.spawnParticle(Particle.LAVA, c, 28, 0.42, 0.3, 0.42, 0.04);
                w.spawnParticle(Particle.EXPLOSION, c, 1, 0.08, 0.08, 0.08, 0);
            }
            case DRONE -> {
                w.spawnParticle(Particle.CRIT, c, 36, 0.48, 0.35, 0.48, 0.08);
                w.spawnParticle(Particle.SMALL_FLAME, c, 18, 0.35, 0.22, 0.35, 0.02);
            }
        }
        player.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 0.48f, 1.28f);
        player.playSound(c, Sound.ITEM_CROSSBOW_LOADING_END, 0.4f, 1.18f);
        player.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.32f, 1.22f);
    }

    public static void playBombUpgradeSuccess(Player player, BombUpgradePath path) {
        Location c = player.getLocation().add(0, 1.0, 0);
        World w = c.getWorld();
        w.spawnParticle(Particle.EXPLOSION, c, 1, 0.1, 0.1, 0.1, 0);
        w.spawnParticle(Particle.LAVA, c, 25, 0.4, 0.3, 0.4, 0.05);
        w.spawnParticle(Particle.SMOKE, c, 30, 0.35, 0.2, 0.35, 0.03);
        switch (path) {
            case SALVO -> w.spawnParticle(Particle.FIREWORK, c, 55, 0.5, 0.4, 0.5, 0.12);
            case RICOCHET -> {
                w.spawnParticle(Particle.SPLASH, c, 40, 0.45, 0.35, 0.45, 0.1);
                ringHorizontal(w, c, Particle.DRIPPING_WATER, 14, 0.85);
            }
            case OVERLOAD -> {
                w.spawnParticle(Particle.DRIPPING_LAVA, c, 35, 0.5, 0.5, 0.5, 0.08);
                w.spawnParticle(Particle.SONIC_BOOM, c.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
            }
        }
        player.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 0.52f, 1.3f);
        player.playSound(c, Sound.BLOCK_NOTE_BLOCK_BELL, 0.42f, 1.45f);
        player.playSound(c, Sound.ENTITY_GENERIC_SMALL_FALL, 0.28f, 1.2f);
    }

    public static void playCannotAfford(Player player) {
        Location c = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.DUST_PLUME, c, 5, 0.15, 0.12, 0.15, 0.008);
        player.playSound(c, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.4f, 0.55f);
        player.playSound(c, Sound.UI_BUTTON_CLICK, 0.3f, 0.65f);
    }

    public static void playTierLocked(Player player) {
        Location c = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.SMOKE, c, 4, 0.12, 0.08, 0.12, 0.008);
        player.playSound(c, Sound.BLOCK_NOTE_BLOCK_BASS, 0.32f, 0.55f);
    }

    public static void playClosePickup(Player player) {
        Location c = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.ENCHANT, c, 10, 0.22, 0.2, 0.22, 0.05);
        player.playSound(c, Sound.ENTITY_ITEM_PICKUP, 0.45f, 1.08f);
        player.playSound(c, Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.35f, 1.15f);
    }

    private static void ringHorizontal(World w, Location center, Particle p, int points, double radius) {
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 / points) * i;
            w.spawnParticle(p, center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, 0, 0, 0, 0);
        }
    }
}
