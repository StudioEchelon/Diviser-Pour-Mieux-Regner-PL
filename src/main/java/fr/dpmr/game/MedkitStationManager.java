package fr.dpmr.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bloc-station de soin façon FPS : item (medikit) flottant qui tourne quand le pickup est prêt ;
 * marcher dessus soigne puis cooldown.
 */
public final class MedkitStationManager {

    private static final class Station {
        final String world;
        final int x;
        final int y;
        final int z;
        long cooldownUntilMs;
        ItemDisplay display;
        float spinAngle;

        Station(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.cooldownUntilMs = 0L;
        }
    }

    private final JavaPlugin plugin;
    private final BandageManager bandageManager;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<String, Station> stations = new HashMap<>();
    private BukkitTask task;
    private ItemStack displayItemCache;
    private long displayItemCacheTime;

    public MedkitStationManager(JavaPlugin plugin, BandageManager bandageManager) {
        this.plugin = plugin;
        this.bandageManager = bandageManager;
        this.file = new File(plugin.getDataFolder(), "medkit-stations.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        loadFromDisk();
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Station s : stations.values()) {
            removeDisplay(s);
        }
    }

    public void reloadFromConfig() {
        displayItemCache = null;
        displayItemCacheTime = 0L;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private boolean enabled() {
        return cfg().getBoolean("medkit-station.enabled", true);
    }

    private double pickupRadius() {
        return Math.max(0.4, cfg().getDouble("medkit-station.pickup-radius", 1.12));
    }

    private int cooldownSeconds() {
        return Math.max(1, cfg().getInt("medkit-station.cooldown-seconds", 22));
    }

    private double healHalfHearts() {
        return Math.max(0.5, cfg().getDouble("medkit-station.heal-half-hearts", 8.0));
    }

    private float displayHeight() {
        return (float) cfg().getDouble("medkit-station.display-height", 0.82);
    }

    private float displayScale() {
        return (float) cfg().getDouble("medkit-station.display-scale", 0.5);
    }

    private float spinSpeedRadPerTick() {
        return (float) cfg().getDouble("medkit-station.spin-speed", 0.12);
    }

    private org.bukkit.Material anchorMaterial() {
        String raw = cfg().getString("medkit-station.anchor-material", "RED_GLAZED_TERRACOTTA");
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(raw != null ? raw : "RED_GLAZED_TERRACOTTA");
        if (m == null || !m.isBlock()) {
            return org.bukkit.Material.RED_GLAZED_TERRACOTTA;
        }
        return m;
    }

    private ItemStack displayItem() {
        long now = System.currentTimeMillis();
        if (displayItemCache != null && now - displayItemCacheTime < 5000L) {
            return displayItemCache.clone();
        }
        displayItemCache = bandageManager.createConsumable(DpmrConsumable.MEDIKIT, 1);
        displayItemCacheTime = now;
        return displayItemCache.clone();
    }

    private static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private void loadFromDisk() {
        stations.clear();
        List<Map<?, ?>> list = yaml.getMapList("stations");
        for (Map<?, ?> m : list) {
            Object w = m.get("world");
            Object ox = m.get("x");
            Object oy = m.get("y");
            Object oz = m.get("z");
            if (w == null || ox == null || oy == null || oz == null) {
                continue;
            }
            String world = String.valueOf(w);
            int x = toInt(ox);
            int y = toInt(oy);
            int z = toInt(oz);
            stations.put(key(world, x, y, z), new Station(world, x, y, z));
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void saveToDisk() {
        yaml.set("stations", null);
        int i = 0;
        for (Station s : stations.values()) {
            String base = "stations." + i + ".";
            yaml.set(base + "world", s.world);
            yaml.set(base + "x", s.x);
            yaml.set(base + "y", s.y);
            yaml.set(base + "z", s.z);
            i++;
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder medkit-stations.yml: " + e.getMessage());
        }
    }

    public void createAtBlock(Player admin, Block block) {
        if (block == null || block.getType().isAir()) {
            admin.sendMessage(Component.text("Bloc invalide.", NamedTextColor.RED));
            return;
        }
        if (block.getType() != anchorMaterial()) {
            admin.sendMessage(Component.text(
                    "Le bloc doit etre: " + anchorMaterial().name() + " (configure medkit-station.anchor-material).",
                    NamedTextColor.RED));
            return;
        }
        World w = block.getWorld();
        if (w == null) {
            return;
        }
        String k = key(w.getName(), block.getX(), block.getY(), block.getZ());
        if (stations.containsKey(k)) {
            admin.sendMessage(Component.text("Station deja enregistree ici.", NamedTextColor.YELLOW));
            return;
        }
        stations.put(k, new Station(w.getName(), block.getX(), block.getY(), block.getZ()));
        saveToDisk();
        admin.sendMessage(Component.text("Station de soin enregistree.", NamedTextColor.GREEN));
    }

    public void deleteAtBlock(Player admin, Block block) {
        if (block == null) {
            admin.sendMessage(Component.text("Bloc invalide.", NamedTextColor.RED));
            return;
        }
        World w = block.getWorld();
        if (w == null) {
            return;
        }
        String k = key(w.getName(), block.getX(), block.getY(), block.getZ());
        Station s = stations.remove(k);
        if (s == null) {
            admin.sendMessage(Component.text("Pas de station ici.", NamedTextColor.RED));
            return;
        }
        removeDisplay(s);
        saveToDisk();
        admin.sendMessage(Component.text("Station supprimee.", NamedTextColor.YELLOW));
    }

    public void listTo(Player admin) {
        if (stations.isEmpty()) {
            admin.sendMessage(Component.text("Aucune station de soin.", NamedTextColor.GRAY));
            return;
        }
        admin.sendMessage(Component.text("Stations (" + stations.size() + "):", NamedTextColor.AQUA));
        for (Station s : stations.values()) {
            admin.sendMessage(Component.text(
                    "  " + s.world + " " + s.x + " " + s.y + " " + s.z,
                    NamedTextColor.GRAY));
        }
    }

    private void tick() {
        if (!enabled()) {
            for (Station s : stations.values()) {
                removeDisplay(s);
            }
            return;
        }
        long now = System.currentTimeMillis();
        org.bukkit.Material anchor = anchorMaterial();
        double r = pickupRadius();
        double r2 = r * r;

        for (Station s : stations.values()) {
            World w = Bukkit.getWorld(s.world);
            if (w == null) {
                removeDisplay(s);
                continue;
            }
            Block b = w.getBlockAt(s.x, s.y, s.z);
            if (!b.getChunk().isLoaded()) {
                removeDisplay(s);
                continue;
            }
            if (b.getType() != anchor) {
                removeDisplay(s);
                continue;
            }

            Location center = b.getLocation().clone().add(0.5, 0.5, 0.5);
            boolean ready = now >= s.cooldownUntilMs;

            if (!ready) {
                removeDisplay(s);
                continue;
            }

            if (s.display == null || !s.display.isValid()) {
                spawnDisplay(s, center);
            }
            if (s.display != null && s.display.isValid()) {
                s.spinAngle += spinSpeedRadPerTick();
                if (s.spinAngle > (float) (Math.PI * 2)) {
                    s.spinAngle -= (float) (Math.PI * 2);
                }
                float h = displayHeight();
                Location dispLoc = center.clone().add(0, h - 0.5, 0);
                s.display.teleport(dispLoc);
                float sc = displayScale();
                Quaternionf rot = new Quaternionf().rotateY(s.spinAngle);
                s.display.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        rot,
                        new Vector3f(sc, sc, sc),
                        new Quaternionf()
                ));
            }

            for (Player p : w.getPlayers()) {
                if (!nearCylinder(p.getLocation(), center, r2) || !needsHeal(p)) {
                    continue;
                }
                applyHeal(p);
                s.cooldownUntilMs = now + cooldownSeconds() * 1000L;
                removeDisplay(s);
                w.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.35f, 1.6f);
                w.spawnParticle(Particle.HEART, p.getLocation().add(0, 1.0, 0), 10, 0.35, 0.4, 0.35, 0.02);
                p.sendActionBar(Component.text("Soin zone (+"
                        + String.format(Locale.ROOT, "%.1f", healHalfHearts() / 2.0)
                        + " coeurs)", NamedTextColor.GREEN));
                break;
            }
        }
    }

    private static boolean nearCylinder(Location feet, Location blockCenter, double r2) {
        double dx = feet.getX() - blockCenter.getX();
        double dz = feet.getZ() - blockCenter.getZ();
        if (dx * dx + dz * dz > r2) {
            return false;
        }
        double dy = feet.getY() - blockCenter.getY();
        return dy >= -1.2 && dy <= 2.0;
    }

    private boolean needsHeal(Player p) {
        var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = attr != null ? attr.getValue() : 20.0;
        return p.getHealth() + 0.25 < max;
    }

    private void applyHeal(Player p) {
        var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = attr != null ? attr.getValue() : 20.0;
        double heal = healHalfHearts();
        double next = Math.min(max, p.getHealth() + heal);
        if (next > p.getHealth()) {
            p.setHealth(next);
        }
    }

    private void spawnDisplay(Station s, Location blockCenter) {
        removeDisplay(s);
        World w = blockCenter.getWorld();
        if (w == null) {
            return;
        }
        float h = displayHeight();
        Location at = blockCenter.clone().add(0, h - 0.5, 0);
        ItemDisplay d = w.spawn(at, ItemDisplay.class);
        d.setPersistent(false);
        d.setGravity(false);
        d.setItemStack(displayItem());
        d.setBillboard(ItemDisplay.Billboard.FIXED);
        float sc = displayScale();
        d.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(sc, sc, sc),
                new Quaternionf()
        ));
        s.display = d;
    }

    private void removeDisplay(Station s) {
        if (s.display != null) {
            if (s.display.isValid()) {
                s.display.remove();
            }
            s.display = null;
        }
    }
}
