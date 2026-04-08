package fr.dpmr.game;

import fr.dpmr.i18n.I18n;
import fr.dpmr.i18n.PlayerLanguageStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coffres visuels (BlockDisplay) portés par de petits ballons colorés, dérivent dans le ciel.
 * Une balle / hitscan les détruit et donne un loot (rare, configurable).
 */
public final class BalloonChestManager implements Listener {

    private final JavaPlugin plugin;
    private final LootManager lootManager;
    private final PlayerLanguageStore languageStore;
    private final NamespacedKey keyBalloonChest;

    private BukkitTask spawnTask;
    private BukkitTask moveTask;

    private final Map<UUID, BalloonSession> sessions = new ConcurrentHashMap<>();

    public BalloonChestManager(JavaPlugin plugin, LootManager lootManager, PlayerLanguageStore languageStore) {
        this.plugin = plugin;
        this.lootManager = lootManager;
        this.languageStore = languageStore;
        this.keyBalloonChest = new NamespacedKey(plugin, "dpmr_balloon_chest");
    }

    public boolean isBalloonChest(BlockDisplay display) {
        if (display == null || !display.isValid()) {
            return false;
        }
        return display.getPersistentDataContainer().has(keyBalloonChest, PersistentDataType.BYTE);
    }

    /** Pop depuis hitscan (arme sans projectile physique). */
    public void tryPopFromHitscan(Player shooter, BlockDisplay chest) {
        if (shooter == null || !isBalloonChest(chest)) {
            return;
        }
        pop(shooter, chest.getUniqueId(), chest.getLocation());
    }

    public void startSchedule() {
        stopSchedule();
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("loot.balloon-chest.enabled", true)) {
            return;
        }
        long intervalSec = Math.max(60L, cfg.getLong("loot.balloon-chest.check-interval-seconds", 360));
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSpawn, intervalSec * 20L, intervalSec * 20L);
        moveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMove, 1L, 1L);
    }

    public void stopSchedule() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        if (moveTask != null) {
            moveTask.cancel();
            moveTask = null;
        }
        removeAll();
    }

    public void removeAll() {
        for (BalloonSession s : new ArrayList<>(sessions.values())) {
            s.removeEntities();
        }
        sessions.clear();
    }

    /**
     * Spawn forcé (admin / test). Retourne false si aucune position valide.
     */
    public boolean forceSpawnNear(Player reference) {
        Location surface = lootManager.pickBalloonChestSpawnSurface(reference);
        if (surface == null || surface.getWorld() == null) {
            return false;
        }
        FileConfiguration cfg = plugin.getConfig();
        double height = Math.max(12, cfg.getDouble("loot.balloon-chest.height-above-ground", 22));
        Location spawn = surface.clone().add(0.5, height, 0.5);
        spawnBalloonAt(spawn);
        return true;
    }

    private void tickSpawn() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("loot.balloon-chest.enabled", true)) {
            return;
        }
        double chance = Math.min(1.0, Math.max(0.0, cfg.getDouble("loot.balloon-chest.spawn-chance-per-check", 0.18)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        int maxActive = Math.max(1, cfg.getInt("loot.balloon-chest.max-active", 3));
        if (sessions.size() >= maxActive) {
            return;
        }
        Location surface = lootManager.pickBalloonChestSpawnSurface(null);
        if (surface == null) {
            return;
        }
        double height = Math.max(12, cfg.getDouble("loot.balloon-chest.height-above-ground", 22));
        Location spawn = surface.clone().add(0.5, height, 0.5);
        spawnBalloonAt(spawn);
        boolean broadcast = cfg.getBoolean("loot.balloon-chest.broadcast-on-spawn", false);
        if (broadcast) {
            int x = surface.getBlockX();
            int z = surface.getBlockZ();
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(I18n.component(languageStore.get(p), NamedTextColor.AQUA, "balloon_chest.spawn_hint", x, z));
            }
        }
    }

    private void spawnBalloonAt(Location chestLoc) {
        World world = chestLoc.getWorld();
        if (world == null) {
            return;
        }
        BlockDisplay chest = world.spawn(chestLoc, BlockDisplay.class);
        chest.getPersistentDataContainer().set(keyBalloonChest, PersistentDataType.BYTE, (byte) 1);
        chest.setBlock(Bukkit.createBlockData(org.bukkit.Material.CHEST));
        chest.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(0.82f, 0.82f, 0.82f),
                new Quaternionf()
        ));

        List<BlockDisplay> balloons = new ArrayList<>(3);
        org.bukkit.Material[] colors = {
                org.bukkit.Material.RED_WOOL,
                org.bukkit.Material.LIGHT_BLUE_WOOL,
                org.bukkit.Material.LIME_WOOL
        };
        float[][] offsets = {
                {0.55f, 1.05f, 0.15f},
                {-0.48f, 1.12f, -0.22f},
                {0.12f, 1.18f, -0.52f}
        };
        for (int i = 0; i < 3; i++) {
            BlockDisplay b = world.spawn(chestLoc.clone(), BlockDisplay.class);
            b.setBlock(Bukkit.createBlockData(colors[i]));
            b.setTransformation(new Transformation(
                    new Vector3f(offsets[i][0], offsets[i][1], offsets[i][2]),
                    new Quaternionf(),
                    new Vector3f(0.38f, 0.38f, 0.38f),
                    new Quaternionf()
            ));
            balloons.add(b);
        }

        ArmorStand label = world.spawn(chestLoc.clone().add(0, 1.35, 0), ArmorStand.class);
        label.setInvisible(true);
        label.setMarker(true);
        label.setGravity(false);
        label.setCustomNameVisible(true);
        label.customName(Component.text("Butin", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Tire dessus", NamedTextColor.YELLOW)));

        BalloonSession session = new BalloonSession(chest, balloons, label, ThreadLocalRandom.current().nextDouble() * Math.PI * 2);
        sessions.put(chest.getUniqueId(), session);
        world.playSound(chestLoc, Sound.BLOCK_WOOL_PLACE, 0.7f, 1.5f);
        world.spawnParticle(Particle.CLOUD, chestLoc, 8, 0.35, 0.2, 0.35, 0.02);
    }

    private void tickMove() {
        Iterator<Map.Entry<UUID, BalloonSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BalloonSession> e = it.next();
            BalloonSession s = e.getValue();
            if (!s.chest.isValid()) {
                s.removeEntities();
                it.remove();
                continue;
            }
            s.tick++;
            Location base = s.chest.getLocation();
            World w = base.getWorld();
            if (w == null) {
                s.removeEntities();
                it.remove();
                continue;
            }
            double t = s.tick * 0.042;
            double driftX = Math.sin(s.phase + t * 0.7) * 0.028 + Math.sin(t * 0.31) * 0.012;
            double driftZ = Math.cos(s.phase * 0.8 + t * 0.55) * 0.028 + Math.cos(t * 0.27) * 0.012;
            double bob = Math.sin(t * 1.15) * 0.018;
            Location next = base.clone().add(driftX, bob, driftZ);
            s.chest.teleport(next);
            for (int i = 0; i < s.balloons.size(); i++) {
                BlockDisplay b = s.balloons.get(i);
                if (b.isValid()) {
                    b.teleport(next);
                }
            }
            if (s.label != null && s.label.isValid()) {
                s.label.teleport(next.clone().add(0, 1.35, 0));
            }
            w.spawnParticle(Particle.CLOUD, next.clone().add(0, 1.0, 0), 1, 0.12, 0.08, 0.12, 0.001);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileHitBalloon(ProjectileHitEvent event) {
        Entity hit = event.getHitEntity();
        if (!(hit instanceof BlockDisplay chest)) {
            return;
        }
        if (!isBalloonChest(chest)) {
            return;
        }
        Projectile proj = event.getEntity();
        if (!(proj.getShooter() instanceof Player shooter)) {
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean("loot.balloon-chest.dpmr-projectiles-only", true)) {
            if (!proj.getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "dpmr_proj_dmg"),
                    org.bukkit.persistence.PersistentDataType.DOUBLE)) {
                return;
            }
        }
        pop(shooter, chest.getUniqueId(), chest.getLocation());
        event.setCancelled(true);
        proj.remove();
    }

    private void pop(Player shooter, UUID chestId, Location at) {
        BalloonSession session = sessions.remove(chestId);
        if (session == null) {
            return;
        }
        session.removeEntities();
        World world = at.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.EXPLOSION, at, 2, 0.2, 0.2, 0.2, 0.01);
            world.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.4, 0), 28, 0.45, 0.35, 0.45, 0.04);
            world.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.95f, 0.75f);
            world.playSound(at, Sound.ENTITY_LEASH_KNOT_BREAK, 0.85f, 1.25f);
        }
        LootChestTier tier = lootManager.parseBalloonChestLootTier();
        lootManager.grantBalloonChestLoot(shooter, at, tier);
        I18n.actionBar(shooter, NamedTextColor.GOLD, "balloon_chest.popped");
    }

    private static final class BalloonSession {
        final BlockDisplay chest;
        final List<BlockDisplay> balloons;
        final ArmorStand label;
        final double phase;
        int tick;

        BalloonSession(BlockDisplay chest, List<BlockDisplay> balloons, ArmorStand label, double phase) {
            this.chest = chest;
            this.balloons = balloons;
            this.label = label;
            this.phase = phase;
        }

        void removeEntities() {
            if (chest.isValid()) {
                chest.remove();
            }
            for (BlockDisplay b : balloons) {
                if (b.isValid()) {
                    b.remove();
                }
            }
            if (label != null && label.isValid()) {
                label.remove();
            }
        }
    }
}
