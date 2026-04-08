package fr.dpmr.game;

import fr.dpmr.data.PointsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Zone spherique : une seule personne a la fois gagne des points en restant dedans.
 * Hologramme au-dessus indique qui controle (genere la monnaie).
 */
public final class CaptureMoneyZoneManager {

    private static final class Zone {
        final String id;
        final String world;
        final double x;
        final double y;
        final double z;
        final double radius;

        Zone(String id, String world, double x, double y, double z, double radius) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
    }

    private final JavaPlugin plugin;
    /** Marque les lignes d'hologramme zone capture (purge doublons / distinction des autres hologrammes). */
    private final NamespacedKey keyCaptureHologram;
    private final NamespacedKey keyCaptureZoneId;
    private final PointsManager pointsManager;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<String, Zone> zones = new LinkedHashMap<>();
    /** Joueur actuellement elu pour gagner des points dans cette zone (doit rester dedans). */
    private final Map<String, UUID> earnerByZone = new HashMap<>();
    private final Map<String, ArmorStand[]> holograms = new HashMap<>();
    private BukkitTask task;

    public CaptureMoneyZoneManager(JavaPlugin plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.keyCaptureHologram = new NamespacedKey(plugin, "dpmr_capture_hologram");
        this.keyCaptureZoneId = new NamespacedKey(plugin, "dpmr_capture_zone_id");
        this.pointsManager = pointsManager;
        this.file = new File(plugin.getDataFolder(), "capture-money-zones.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        loadFromDisk();
    }

    public void start() {
        if (task != null) {
            return;
        }
        int period = Math.max(1, plugin.getConfig().getInt("capture-money-zones.tick-interval-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, period);
        for (Zone z : zones.values()) {
            ensureHologram(z);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (ArmorStand[] stands : holograms.values()) {
            removeStands(stands);
        }
        holograms.clear();
        earnerByZone.clear();
    }

    private void loadFromDisk() {
        zones.clear();
        if (!yaml.isConfigurationSection("zones")) {
            return;
        }
        for (String id : Objects.requireNonNull(yaml.getConfigurationSection("zones")).getKeys(false)) {
            String base = "zones." + id + ".";
            String w = yaml.getString(base + "world", "");
            if (w.isBlank()) {
                continue;
            }
            double x = yaml.getDouble(base + "x", 0);
            double y = yaml.getDouble(base + "y", 64);
            double z = yaml.getDouble(base + "z", 0);
            double r = yaml.getDouble(base + "radius", plugin.getConfig().getDouble("capture-money-zones.default-radius", 5));
            r = Math.max(1.0, r);
            zones.put(id.toLowerCase(Locale.ROOT), new Zone(id.toLowerCase(Locale.ROOT), w, x, y, z, r));
        }
    }

    private void saveToDisk() {
        yaml.set("zones", null);
        for (Zone z : zones.values()) {
            String base = "zones." + z.id + ".";
            yaml.set(base + "world", z.world);
            yaml.set(base + "x", z.x);
            yaml.set(base + "y", z.y);
            yaml.set(base + "z", z.z);
            yaml.set(base + "radius", z.radius);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder capture-money-zones.yml: " + e.getMessage());
        }
    }

    public boolean create(Player admin, String id, double radius) {
        String key = id.toLowerCase(Locale.ROOT);
        if (zones.containsKey(key)) {
            admin.sendMessage(Component.text("ID deja utilise: " + key, NamedTextColor.RED));
            return false;
        }
        Location loc = admin.getLocation();
        World w = loc.getWorld();
        if (w == null) {
            return false;
        }
        double cx = loc.getX();
        double cy = loc.getY();
        double cz = loc.getZ();
        double r = Math.max(1.0, radius);
        Zone z = new Zone(key, w.getName(), cx, cy, cz, r);
        zones.put(key, z);
        saveToDisk();
        ensureHologram(z);
        admin.sendMessage(Component.text("Zone de capture '" + key + "' creee (r=" + (int) r + ").", NamedTextColor.GREEN));
        return true;
    }

    public boolean delete(Player admin, String id) {
        String key = id.toLowerCase(Locale.ROOT);
        Zone z = zones.remove(key);
        if (z == null) {
            admin.sendMessage(Component.text("Zone inconnue: " + key, NamedTextColor.RED));
            return false;
        }
        earnerByZone.remove(key);
        ArmorStand[] stands = holograms.remove(key);
        removeStands(stands);
        saveToDisk();
        admin.sendMessage(Component.text("Zone '" + key + "' supprimee.", NamedTextColor.YELLOW));
        return true;
    }

    public java.util.List<String> listIds() {
        return zones.keySet().stream().sorted().toList();
    }

    public double configuredDefaultRadius() {
        return Math.max(1.0, plugin.getConfig().getDouble("capture-money-zones.default-radius", 5));
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("capture-money-zones.enabled", true)) {
            for (Zone z : zones.values()) {
                updateHologramText(z, null);
            }
            return;
        }
        int gain = Math.max(1, plugin.getConfig().getInt("capture-money-zones.points-per-tick", 1));

        for (Zone z : zones.values()) {
            World w = Bukkit.getWorld(z.world);
            if (w == null) {
                updateHologramText(z, null);
                continue;
            }

            ensureHologram(z);

            Location center = new Location(w, z.x, z.y, z.z);
            double r2 = z.radius * z.radius;

            UUID currentId = earnerByZone.get(z.id);
            Player earner = null;

            if (currentId != null) {
                Player p = Bukkit.getPlayer(currentId);
                if (p != null && p.getWorld() == w && distanceSq(p.getLocation(), center) <= r2) {
                    earner = p;
                } else {
                    earnerByZone.remove(z.id);
                }
            }

            if (earner == null) {
                Player closest = null;
                double best = Double.MAX_VALUE;
                for (Player p : w.getPlayers()) {
                    if (p.getWorld() != w) {
                        continue;
                    }
                    double d2 = distanceSq(p.getLocation(), center);
                    if (d2 > r2) {
                        continue;
                    }
                    if (d2 < best) {
                        best = d2;
                        closest = p;
                    }
                }
                if (closest != null) {
                    earnerByZone.put(z.id, closest.getUniqueId());
                    earner = closest;
                }
            }

            if (earner != null) {
                pointsManager.addPoints(earner.getUniqueId(), gain);
                Location pulse = earner.getLocation();
                w.spawnParticle(Particle.HAPPY_VILLAGER, pulse.clone().add(0, 0.2, 0), 8, 0.55, 0.2, 0.55, 0);
                w.spawnParticle(Particle.END_ROD, pulse.clone().add(0, 1.0, 0), 3, 0.25, 0.35, 0.25, 0.02);
                w.spawnParticle(Particle.CRIT, pulse.clone().add(0, 0.35, 0), 4, 0.35, 0.12, 0.35, 0);
            }

            updateHologramText(z, earner);
        }
    }

    private static double distanceSq(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private double hologramYOffset() {
        return Math.max(0.5, plugin.getConfig().getDouble("capture-money-zones.hologram-y-offset", 2.6));
    }

    private void ensureHologram(Zone z) {
        ArmorStand[] stands = holograms.get(z.id);
        if (stands != null && stands.length == 5 && allValid(stands)) {
            return;
        }
        removeStands(stands);
        holograms.remove(z.id);

        World w = Bukkit.getWorld(z.world);
        if (w == null) {
            return;
        }
        Location base = new Location(w, z.x, z.y + hologramYOffset(), z.z);
        purgeDenseLegacyInvisibleColumn(w, base);
        purgeCaptureHologramsNear(w, base, z.id);
        ArmorStand[] created = new ArmorStand[5];
        for (int i = 0; i < 5; i++) {
            Location line = base.clone().add(0, -i * 0.28, 0);
            ArmorStand as = w.spawn(line, ArmorStand.class);
            as.setGravity(false);
            as.setInvisible(true);
            as.setMarker(true);
            as.setCustomNameVisible(true);
            as.customName(Component.text("-", NamedTextColor.GRAY));
            as.getPersistentDataContainer().set(keyCaptureHologram, PersistentDataType.BYTE, (byte) 1);
            as.getPersistentDataContainer().set(keyCaptureZoneId, PersistentDataType.STRING, z.id);
            created[i] = as;
        }
        holograms.put(z.id, created);
    }

    /**
     * Anciens doublons sans PDC : si une colonne invis/marker s'empile anormalement, on nettoie avant respawn.
     */
    private void purgeDenseLegacyInvisibleColumn(World w, Location base) {
        List<ArmorStand> candidates = new ArrayList<>();
        NamespacedKey keyTop = new NamespacedKey(plugin, "dpmr_hologram");
        NamespacedKey keyNpc = new NamespacedKey(plugin, "dpmr_fake_npc");
        NamespacedKey keyPet = new NamespacedKey(plugin, "dpmr_familiar_pet");
        for (Entity e : w.getNearbyEntities(base, 0.5, 4.0, 0.5)) {
            if (!(e instanceof ArmorStand as)) {
                continue;
            }
            if (!as.isInvisible() || !as.isMarker() || as.hasGravity()) {
                continue;
            }
            var pdc = as.getPersistentDataContainer();
            if (pdc.has(keyCaptureHologram, PersistentDataType.BYTE)) {
                continue;
            }
            if (pdc.has(keyTop, PersistentDataType.BYTE)) {
                continue;
            }
            if (pdc.has(keyNpc, PersistentDataType.BYTE) || pdc.has(keyNpc, PersistentDataType.INTEGER)) {
                continue;
            }
            if (pdc.has(keyPet, PersistentDataType.BYTE)) {
                continue;
            }
            candidates.add(as);
        }
        if (candidates.size() < 48) {
            return;
        }
        plugin.getLogger().warning("Capture-money: purge de " + candidates.size()
                + " ArmorStands invisibles empiles (legacy sans tag) pres de " + base.getBlockX() + ", "
                + base.getBlockY() + ", " + base.getBlockZ() + " (" + w.getName() + ")");
        for (ArmorStand as : candidates) {
            as.remove();
        }
    }

    /**
     * Supprime les armor stands deja marques pour cette zone (evite empilement au reload / re-spawn).
     */
    private void purgeCaptureHologramsNear(World w, Location base, String zoneId) {
        for (Entity e : w.getNearbyEntities(base, 4, 6, 4)) {
            if (!(e instanceof ArmorStand as)) {
                continue;
            }
            if (!as.getPersistentDataContainer().has(keyCaptureHologram, PersistentDataType.BYTE)) {
                continue;
            }
            String id = as.getPersistentDataContainer().get(keyCaptureZoneId, PersistentDataType.STRING);
            if (zoneId.equals(id)) {
                as.remove();
            }
        }
    }

    private static boolean allValid(ArmorStand[] stands) {
        for (ArmorStand as : stands) {
            if (as == null || !as.isValid()) {
                return false;
            }
        }
        return true;
    }

    private static void removeStands(ArmorStand[] stands) {
        if (stands == null) {
            return;
        }
        for (ArmorStand as : stands) {
            if (as != null && as.isValid()) {
                as.remove();
            }
        }
    }

    private void updateHologramText(Zone z, Player earner) {
        ArmorStand[] stands = holograms.get(z.id);
        if (stands == null || stands.length < 5) {
            return;
        }
        if (!allValid(stands)) {
            ensureHologram(z);
            stands = holograms.get(z.id);
            if (stands == null || stands.length < 5) {
                return;
            }
        }

        World w = Bukkit.getWorld(z.world);
        if (w != null) {
            Location base = new Location(w, z.x, z.y + hologramYOffset(), z.z);
            for (int i = 0; i < 5; i++) {
                stands[i].teleport(base.clone().add(0, -i * 0.28, 0));
            }
        }

        stands[0].customName(Component.text("Zone de capture", NamedTextColor.GOLD));
        stands[1].customName(Component.text(z.id, NamedTextColor.AQUA));
        if (earner != null) {
            stands[2].customName(Component.text(
                    "Controle: " + earner.getName(),
                    NamedTextColor.GREEN));
            stands[3].customName(Component.text("+1", NamedTextColor.GREEN, TextDecoration.BOLD));
            stands[4].customName(Component.text("+1", NamedTextColor.GREEN, TextDecoration.BOLD));
        } else {
            stands[2].customName(Component.text("Controle: — (libre)", NamedTextColor.DARK_GRAY));
            stands[3].customName(Component.text(" ", NamedTextColor.DARK_GRAY));
            stands[4].customName(Component.text(" ", NamedTextColor.DARK_GRAY));
        }
    }
}
