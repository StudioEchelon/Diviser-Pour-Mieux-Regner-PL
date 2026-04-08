package fr.dpmr.game;

import fr.dpmr.data.PointsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leaderboard holograms persisted in holograms.yml. On enable, old DPMR stands near each column are
 * removed (PDC {@code dpmr_hologram}) before spawning, so reload does not stack duplicates.
 */
public class TopHologramManager implements Listener {

    public enum LeaderboardMode {
        POINTS,
        KILLS
    }

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final File file;
    private final NamespacedKey keyHologram;
    private YamlConfiguration yaml;
    private final List<HologramColumn> columns = new ArrayList<>();
    private BukkitTask task;

    public TopHologramManager(JavaPlugin plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
        this.keyHologram = new NamespacedKey(plugin, "dpmr_hologram");
        loadYaml();
        migrateLegacyLocations();
        loadColumnsFromYaml();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        respawnAllColumns();
    }

    private void loadYaml() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    private void migrateLegacyLocations() {
        List<String> legacy = yaml.getStringList("locations");
        if (legacy == null || legacy.isEmpty()) {
            return;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String loc : legacy) {
            if (loc == null || loc.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", UUID.randomUUID().toString());
            row.put("location", loc.trim());
            row.put("mode", LeaderboardMode.POINTS.name());
            out.add(row);
        }
        yaml.set("columns", out);
        yaml.set("locations", null);
        saveYaml();
    }

    @SuppressWarnings("unchecked")
    private void loadColumnsFromYaml() {
        columns.clear();
        List<?> raw = yaml.getList("columns");
        if (raw == null) {
            return;
        }
        for (Object o : raw) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) o;
            String idStr = String.valueOf(m.getOrDefault("id", ""));
            String loc = String.valueOf(m.getOrDefault("location", ""));
            String modeStr = String.valueOf(m.getOrDefault("mode", "POINTS"));
            UUID id;
            try {
                id = UUID.fromString(idStr);
            } catch (Exception e) {
                id = UUID.randomUUID();
            }
            LeaderboardMode mode = LeaderboardMode.POINTS;
            try {
                mode = LeaderboardMode.valueOf(modeStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
            if (!loc.isBlank() && loc.split(";").length == 4) {
                columns.add(new HologramColumn(id, loc.trim(), mode));
            }
        }
    }

    private void persistColumns() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (HologramColumn c : columns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.id.toString());
            row.put("location", c.locationLine);
            row.put("mode", c.mode.name());
            out.add(row);
        }
        yaml.set("columns", out);
        saveYaml();
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeAllStands();
    }

    private void tick() {
        List<Map.Entry<UUID, Integer>> topPoints = pointsManager.getTop(10);
        List<Map.Entry<UUID, Integer>> topKills = pointsManager.getTopKills(10);
        for (HologramColumn col : columns) {
            ArmorStand[] stands = col.stands;
            if (stands == null || stands.length < 11) {
                continue;
            }
            boolean valid = false;
            for (ArmorStand as : stands) {
                if (as != null && as.isValid()) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                continue;
            }
            List<Map.Entry<UUID, Integer>> top = col.mode == LeaderboardMode.KILLS ? topKills : topPoints;
            String title = col.mode == LeaderboardMode.KILLS ? "TOP 10 — KILLS" : "TOP 10 — POINTS";
            stands[0].customName(Component.text(title, NamedTextColor.GOLD));
            for (int i = 0; i < 10; i++) {
                if (i < top.size()) {
                    Map.Entry<UUID, Integer> e = top.get(i);
                    String name = pointsManager.resolveName(e.getKey());
                    stands[i + 1].customName(Component.text((i + 1) + ". " + name + " — " + e.getValue(), NamedTextColor.YELLOW));
                } else {
                    stands[i + 1].customName(Component.text("-", NamedTextColor.DARK_GRAY));
                }
            }
        }
    }

    public void addColumn(Player player, LeaderboardMode mode) {
        Location feet = player.getLocation().getBlock().getLocation();
        HologramColumn col = new HologramColumn(UUID.randomUUID(), encode(feet), mode);
        columns.add(col);
        persistColumns();
        Location base = feet.clone().add(0.5, 2.4, 0.5);
        col.stands = spawnStandsAt(base, keyHologram);
        String label = mode == LeaderboardMode.KILLS ? "kills" : "points";
        player.sendMessage(Component.text("Hologramme top 10 (" + label + ") pose ici.", NamedTextColor.GREEN));
    }

    private void respawnAllColumns() {
        removeAllStands();
        for (HologramColumn col : columns) {
            Location base = decodeToBase(col.locationLine);
            if (base == null) {
                col.stands = null;
                continue;
            }
            purgeOurHologramsNear(base, keyHologram);
            col.stands = spawnStandsAt(base, keyHologram);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        String name = event.getWorld().getName();
        for (HologramColumn col : columns) {
            if (!col.locationLine.startsWith(name + ";")) {
                continue;
            }
            if (col.stands != null && standsAlive(col.stands)) {
                continue;
            }
            Location base = decodeToBase(col.locationLine);
            if (base == null) {
                continue;
            }
            purgeOurHologramsNear(base, keyHologram);
            col.stands = spawnStandsAt(base, keyHologram);
        }
    }

    private static boolean standsAlive(ArmorStand[] stands) {
        if (stands == null || stands.length < 1) {
            return false;
        }
        return stands[0] != null && stands[0].isValid();
    }

    private void removeAllStands() {
        for (HologramColumn col : columns) {
            if (col.stands == null) {
                continue;
            }
            for (ArmorStand as : col.stands) {
                if (as != null && as.isValid()) {
                    as.remove();
                }
            }
            col.stands = null;
        }
    }

    /**
     * Supprime uniquement les armor stands marques par ce plugin (evite doublons au reload et ne touche pas aux autres hologrammes).
     */
    private void purgeOurHologramsNear(Location base, NamespacedKey hologramKey) {
        World w = base.getWorld();
        if (w == null) {
            return;
        }
        for (Entity e : w.getNearbyEntities(base, 6, 18, 6)) {
            if (!(e instanceof ArmorStand as)) {
                continue;
            }
            if (!as.getPersistentDataContainer().has(hologramKey, PersistentDataType.BYTE)) {
                continue;
            }
            as.remove();
        }
    }

    private static ArmorStand[] spawnStandsAt(Location base, NamespacedKey hologramKey) {
        World w = base.getWorld();
        if (w == null) {
            return null;
        }
        ArmorStand[] stands = new ArmorStand[11];
        for (int i = 0; i < 11; i++) {
            Location line = base.clone().add(0, -i * 0.28, 0);
            ArmorStand as = w.spawn(line, ArmorStand.class);
            as.setGravity(false);
            as.setInvisible(true);
            as.setMarker(true);
            as.setCustomNameVisible(true);
            as.customName(Component.text(i == 0 ? "TOP 10" : "-", NamedTextColor.GRAY));
            as.getPersistentDataContainer().set(hologramKey, PersistentDataType.BYTE, (byte) 1);
            stands[i] = as;
        }
        return stands;
    }

    private static String encode(Location blockFeet) {
        return blockFeet.getWorld().getName() + ";" + blockFeet.getBlockX() + ";" + blockFeet.getBlockY() + ";" + blockFeet.getBlockZ();
    }

    /** Centre de la ligne 0 (haut du classement), ou null si monde absent. */
    private static Location decodeToBase(String raw) {
        String[] p = raw.split(";");
        if (p.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(p[0]);
        if (world == null) {
            return null;
        }
        try {
            int bx = Integer.parseInt(p[1]);
            int by = Integer.parseInt(p[2]);
            int bz = Integer.parseInt(p[3]);
            return new Location(world, bx + 0.5, by + 2.4, bz + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Location decodeBlockFeet(String raw) {
        String[] p = raw.split(";");
        if (p.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(p[0]);
        if (world == null) {
            return null;
        }
        try {
            int bx = Integer.parseInt(p[1]);
            int by = Integer.parseInt(p[2]);
            int bz = Integer.parseInt(p[3]);
            return new Location(world, bx, by, bz);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void saveYaml() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("holograms.yml: " + e.getMessage());
        }
    }

    public int columnCount() {
        return columns.size();
    }

    /**
     * Retire l'hologramme DPMR le plus proche (compare aux positions enregistrees, pas seulement aux entites chargees).
     */
    public boolean removeNearestColumn(Player player, double maxDistance) {
        double maxSq = maxDistance * maxDistance;
        int bestIdx = -1;
        double bestD = maxSq;
        Location pl = player.getLocation();
        for (int i = 0; i < columns.size(); i++) {
            HologramColumn col = columns.get(i);
            Location feet = decodeBlockFeet(col.locationLine);
            if (feet == null) {
                continue;
            }
            if (!feet.getWorld().equals(pl.getWorld())) {
                continue;
            }
            double d = feet.distanceSquared(pl.getBlock().getLocation());
            if (d < bestD) {
                bestD = d;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) {
            return false;
        }
        HologramColumn removed = columns.remove(bestIdx);
        if (removed.stands != null) {
            for (ArmorStand as : removed.stands) {
                if (as != null && as.isValid()) {
                    as.remove();
                }
            }
        }
        Location base = decodeToBase(removed.locationLine);
        if (base != null) {
            purgeOurHologramsNear(base, keyHologram);
        }
        persistColumns();
        player.sendMessage(Component.text("Hologramme top 10 retire.", NamedTextColor.GREEN));
        return true;
    }

    static final class HologramColumn {
        final UUID id;
        final String locationLine;
        final LeaderboardMode mode;
        ArmorStand[] stands;

        HologramColumn(UUID id, String locationLine, LeaderboardMode mode) {
            this.id = id;
            this.locationLine = locationLine;
            this.mode = mode;
        }
    }
}
