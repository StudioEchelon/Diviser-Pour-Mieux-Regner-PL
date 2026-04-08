package fr.dpmr.zone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.HeightMap;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ZoneManager implements Listener {

    private final JavaPlugin plugin;
    private final org.bukkit.NamespacedKey keyZoneWandSel;
    private final Map<UUID, ZoneView> lastZoneView = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, List<Location>> polySelections = new HashMap<>();
    private List<SafeRegion> cachedSafeRegions;
    private List<WarRegion> cachedWarRegions;
    private boolean safeEnabled;
    private boolean warEnabled;

    private static final class Selection {
        Location pos1;
        Location pos2;
    }

    private enum ZoneView {
        SAFE,
        PVP,
        NEUTRAL
    }

    public ZoneManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyZoneWandSel = new org.bukkit.NamespacedKey(plugin, "dpmr_zone_wand_sel");
        invalidateZoneCache();
    }

    public void invalidateZoneCache() {
        FileConfiguration cfg = plugin.getConfig();
        safeEnabled = cfg.getBoolean("zones.safe.enabled", false);
        warEnabled = cfg.getBoolean("zones.war.enabled", false);
        cachedSafeRegions = loadSafeRegions(cfg);
        cachedWarRegions = loadWarRegions(cfg);
    }

    /**
     * Vrai si le segment [from → to] traverse ou est entièrement dans une zone safe (même monde).
     * Utilisé pour annuler les dégâts des armes dont le trajet coupe une safe.
     */
    public boolean isWeaponDamageBlockedAlongLine(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return false;
        }
        if (!safeEnabled) {
            return false;
        }
        for (SafeRegion r : cachedSafeRegions) {
            if (!r.world.equals(from.getWorld().getName())) {
                continue;
            }
            if (r.segmentIntersects(from, to)) {
                return true;
            }
        }
        return false;
    }

    public void setSafeZone(Player player, double radius) {
        migrateLegacySafeIfNeeded();
        Location loc = player.getLocation();
        FileConfiguration cfg = plugin.getConfig();
        appendSafeRegion(sphereRegionMap(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), Math.max(1.0, radius)));
        cfg.set("zones.safe.enabled", true);
        clearLegacySafeFlatKeys(cfg);
        plugin.saveConfig();
        invalidateZoneCache();
        player.sendMessage(Component.text("Safe zone ajoutee (sphere r=" + Math.round(radius) + ") — total: " + countSafeRegions(cfg), NamedTextColor.GREEN));
    }

    public void setWarZone(Player player, double radius) {
        migrateLegacyWarIfNeeded();
        Location loc = player.getLocation();
        FileConfiguration cfg = plugin.getConfig();
        appendWarRegion(sphereRegionMap(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), Math.max(1.0, radius)));
        cfg.set("zones.war.enabled", true);
        clearLegacyWarFlatKeys(cfg);
        plugin.saveConfig();
        invalidateZoneCache();
        player.sendMessage(Component.text("Zone PVP ajoutee (sphere r=" + Math.round(radius) + ") — total: " + countWarRegions(cfg), NamedTextColor.GOLD));
    }

    public boolean setSafeZoneFromSelection(Player player) {
        Selection sel = selections.get(player.getUniqueId());
        if (sel == null || sel.pos1 == null || sel.pos2 == null) {
            player.sendMessage(Component.text("Incomplete selection: set pos1 and pos2 with the wand.", NamedTextColor.RED));
            return false;
        }
        if (sel.pos1.getWorld() == null || sel.pos2.getWorld() == null || sel.pos1.getWorld() != sel.pos2.getWorld()) {
            player.sendMessage(Component.text("Pos1 and Pos2 must be in the same world.", NamedTextColor.RED));
            return false;
        }
        migrateLegacySafeIfNeeded();
        FileConfiguration cfg = plugin.getConfig();
        appendSafeRegion(cuboidRegionMap(sel.pos1, sel.pos2));
        cfg.set("zones.safe.enabled", true);
        clearLegacySafeFlatKeys(cfg);
        plugin.saveConfig();
        invalidateZoneCache();
        player.sendMessage(Component.text("Cuboid safe ajoute — total: " + countSafeRegions(cfg), NamedTextColor.GREEN));
        return true;
    }

    public boolean setWarZoneFromSelection(Player player) {
        Selection sel = selections.get(player.getUniqueId());
        if (sel == null || sel.pos1 == null || sel.pos2 == null) {
            player.sendMessage(Component.text("Incomplete selection: set pos1 and pos2 with the wand.", NamedTextColor.RED));
            return false;
        }
        if (sel.pos1.getWorld() == null || sel.pos2.getWorld() == null || sel.pos1.getWorld() != sel.pos2.getWorld()) {
            player.sendMessage(Component.text("Pos1 and Pos2 must be in the same world.", NamedTextColor.RED));
            return false;
        }
        migrateLegacyWarIfNeeded();
        FileConfiguration cfg = plugin.getConfig();
        appendWarRegion(cuboidRegionMap(sel.pos1, sel.pos2));
        cfg.set("zones.war.enabled", true);
        clearLegacyWarFlatKeys(cfg);
        plugin.saveConfig();
        invalidateZoneCache();
        player.sendMessage(Component.text("Cuboid PVP ajoute — total: " + countWarRegions(cfg), NamedTextColor.GOLD));
        return true;
    }

    public void setSafeZoneAt(Player player, Location loc, double radius) {
        migrateLegacySafeIfNeeded();
        FileConfiguration cfg = plugin.getConfig();
        appendSafeRegion(sphereRegionMap(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), Math.max(1.0, radius)));
        cfg.set("zones.safe.enabled", true);
        clearLegacySafeFlatKeys(cfg);
        plugin.saveConfig();
        invalidateZoneCache();
        player.sendMessage(Component.text("Safe zone ajoutee (r=" + Math.round(radius) + ") — total: " + countSafeRegions(cfg), NamedTextColor.GREEN));
    }

    public void setWarZoneAt(Player player, Location loc, double radius) {
        migrateLegacyWarIfNeeded();
        FileConfiguration cfg = plugin.getConfig();
        appendWarRegion(sphereRegionMap(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), Math.max(1.0, radius)));
        cfg.set("zones.war.enabled", true);
        clearLegacyWarFlatKeys(cfg);
        plugin.saveConfig();
        invalidateZoneCache();
        player.sendMessage(Component.text("Zone PVP ajoutee (r=" + Math.round(radius) + ") — total: " + countWarRegions(cfg), NamedTextColor.GOLD));
    }

    public void deleteSafeZone(Player player) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("zones.safe.regions", new ArrayList<>());
        cfg.set("zones.safe.enabled", false);
        clearLegacySafeFlatKeys(cfg);
        plugin.saveConfig();
        invalidateZoneCache();
        player.sendMessage(Component.text("Toutes les zones safe supprimees.", NamedTextColor.YELLOW));
    }

    public void deleteWarZone(Player player) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("zones.war.regions", new ArrayList<>());
        cfg.set("zones.war.enabled", false);
        clearLegacyWarFlatKeys(cfg);
        plugin.saveConfig();
        invalidateZoneCache();
        player.sendMessage(Component.text("Toutes les zones PVP supprimees.", NamedTextColor.YELLOW));
    }

    public ItemStack createZoneWand(String type, double radius) {
        ItemStack it = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Wand Zones (points + WorldEdit)", NamedTextColor.AQUA));
        meta.lore(java.util.List.of(
                Component.text("Left-click block: Pos1", NamedTextColor.GRAY),
                Component.text("Right-click block: Pos2 + add a point", NamedTextColor.GRAY),
                Component.text("Sneak + right-click: clear points", NamedTextColor.YELLOW),
                Component.text("/dpmr zone <safe|war> setpoly", NamedTextColor.DARK_AQUA),
                Component.text("/dpmr zone clearpoints", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(keyZoneWandSel, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public void clearPolyPoints(Player player) {
        polySelections.remove(player.getUniqueId());
        player.sendMessage(Component.text("Zone points cleared.", NamedTextColor.YELLOW));
    }

    public boolean setSafeZoneFromPoly(Player player) {
        return setZoneFromPoly("safe", player, NamedTextColor.GREEN, "Safe polygonale ajoutee.", true);
    }

    public boolean setWarZoneFromPoly(Player player) {
        return setZoneFromPoly("war", player, NamedTextColor.GOLD, "Zone PVP polygonale ajoutee.", false);
    }

    public int getPolyPointCount(Player player) {
        return polySelections.getOrDefault(player.getUniqueId(), List.of()).size();
    }

    public boolean isInSafeZone(Location loc) {
        if (!safeEnabled) {
            return false;
        }
        for (SafeRegion r : cachedSafeRegions) {
            if (r.contains(loc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Si war zone activée: combat autorisé uniquement dedans.
     * Sinon: combat autorisé partout sauf safe zone.
     */
    public boolean isCombatAllowed(Location loc) {
        if (isInSafeZone(loc)) {
            return false;
        }
        if (!warEnabled) {
            return true;
        }
        return isInsideWarRegion(loc);
    }

    /**
     * {@code true} si la position est dans la région war (monde + forme), sans tenir compte de la safe.
     */
    public boolean isInsideWarRegion(Location loc) {
        if (!warEnabled) {
            return false;
        }
        for (WarRegion r : cachedWarRegions) {
            if (r.contains(loc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pieds du joueur sur surface solide, dans la war zone (air aux pieds + tête).
     */
    public Location pickRandomSurfaceFeetInWarZone(int maxAttempts) {
        if (!warEnabled) {
            return null;
        }
        FileConfiguration cfg = plugin.getConfig();
        List<WarRegion> wars = cachedWarRegions;
        if (wars.isEmpty()) {
            return null;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < maxAttempts; i++) {
            WarRegion wr = wars.get(rnd.nextInt(wars.size()));
            World world = org.bukkit.Bukkit.getWorld(wr.world);
            if (world == null) {
                continue;
            }
            Location probe = wr.randomXZ(world, cfg, rnd);
            if (probe == null) {
                continue;
            }
            int y = world.getHighestBlockYAt(probe.getBlockX(), probe.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight() - 1;
            int[] yBounds = wr.verticalBounds(cfg);
            if (yBounds != null) {
                minY = yBounds[0];
                maxY = yBounds[1];
            }
            if (y < minY || y > maxY) {
                continue;
            }
            Location feet = new Location(world, probe.getBlockX() + 0.5, y + 1.0, probe.getBlockZ() + 0.5);
            if (!feet.getBlock().getType().isAir()) {
                continue;
            }
            if (!feet.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                continue;
            }
            if (!feet.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                continue;
            }
            if (!isInsideWarRegion(feet)) {
                continue;
            }
            return feet;
        }
        return null;
    }

    /**
     * Hors safe : autour du spawn vanilla du monde (si war désactivée).
     */
    public Location pickRandomSurfaceNearWorldSpawn(String worldName, int spreadBlocks, int maxAttempts) {
        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        Location spawn = world.getSpawnLocation();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int spread = Math.max(8, spreadBlocks);
        for (int i = 0; i < maxAttempts; i++) {
            int x = spawn.getBlockX() + rnd.nextInt(-spread, spread + 1);
            int z = spawn.getBlockZ() + rnd.nextInt(-spread, spread + 1);
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Location feet = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            if (isInSafeZone(feet)) {
                continue;
            }
            if (!feet.getBlock().getType().isAir() || !feet.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                continue;
            }
            if (!feet.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                continue;
            }
            return feet;
        }
        return null;
    }

    public void describeTo(Player player) {
        FileConfiguration cfg = plugin.getConfig();
        int ns = countSafeRegions(cfg);
        int nw = countWarRegions(cfg);
        player.sendMessage(Component.text("Zones:", NamedTextColor.AQUA));
        player.sendMessage(Component.text(" - safe: " + (cfg.getBoolean("zones.safe.enabled", false) ? "ON (" + ns + ")" : "OFF"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(" - war: " + (cfg.getBoolean("zones.war.enabled", false) ? "ON (" + nw + ")" : "OFF"), NamedTextColor.GRAY));
    }

    @EventHandler
    public void onZoneWandUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        Byte isSelWand = meta.getPersistentDataContainer().get(keyZoneWandSel, PersistentDataType.BYTE);
        if (isSelWand == null || isSelWand != 1) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("dpmr.admin")) {
            player.sendMessage(Component.text("Missing dpmr.admin permission", NamedTextColor.RED));
            return;
        }
        event.setCancelled(true);
        if (event.getClickedBlock() == null) {
            return;
        }
        Location at = event.getClickedBlock().getLocation();
        Selection sel = selections.computeIfAbsent(player.getUniqueId(), k -> new Selection());
        if (action == Action.LEFT_CLICK_BLOCK) {
            sel.pos1 = at;
            player.sendMessage(Component.text("Pos1 = " + fmt(at), NamedTextColor.GREEN));
        } else {
            sel.pos2 = at;
            player.sendMessage(Component.text("Pos2 = " + fmt(at), NamedTextColor.GOLD));
            if (player.isSneaking()) {
                clearPolyPoints(player);
                return;
            }
            List<Location> pts = polySelections.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
            pts.add(at);
            player.sendActionBar(Component.text("Point ajoute (#" + pts.size() + ")", NamedTextColor.AQUA));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastZoneView.remove(uuid);
        selections.remove(uuid);
        polySelections.remove(uuid);
    }

    @EventHandler
    public void onMoveTitle(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() == to.getWorld()) {
            return;
        }
        Player p = event.getPlayer();
        ZoneView now = currentZone(to);
        ZoneView prev = lastZoneView.getOrDefault(p.getUniqueId(), currentZone(from));
        if (now == prev) {
            return;
        }
        lastZoneView.put(p.getUniqueId(), now);
        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1100), Duration.ofMillis(300));
        switch (now) {
            case SAFE -> p.showTitle(Title.title(
                    Component.text("ZONE SAFE", NamedTextColor.GREEN),
                    Component.text("PVP off", NamedTextColor.GRAY),
                    times
            ));
            case PVP -> p.showTitle(Title.title(
                    Component.text("ZONE PVP", NamedTextColor.RED),
                    Component.text("Combat autorise", NamedTextColor.GOLD),
                    times
            ));
            case NEUTRAL -> p.showTitle(Title.title(
                    Component.text("ZONE NEUTRE", NamedTextColor.YELLOW),
                    Component.text("Standard", NamedTextColor.GRAY),
                    times
            ));
        }
    }

    private ZoneView currentZone(Location loc) {
        if (isInSafeZone(loc)) {
            return ZoneView.SAFE;
        }
        if (isCombatAllowed(loc)) {
            return ZoneView.PVP;
        }
        return ZoneView.NEUTRAL;
    }

    private static String fmt(Location at) {
        return at.getWorld().getName() + " " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
    }

    private boolean setZoneFromPoly(String key, Player player, NamedTextColor color, String okMsg, boolean safe) {
        List<Location> pts = polySelections.get(player.getUniqueId());
        if (pts == null || pts.size() < 3) {
            player.sendMessage(Component.text("Need at least 3 points (wand right-click).", NamedTextColor.RED));
            return false;
        }
        String world = pts.get(0).getWorld() != null ? pts.get(0).getWorld().getName() : null;
        if (world == null) {
            player.sendMessage(Component.text("Invalid world.", NamedTextColor.RED));
            return false;
        }
        for (Location p : pts) {
            if (p.getWorld() == null || !world.equals(p.getWorld().getName())) {
                player.sendMessage(Component.text("All points must be in the same world.", NamedTextColor.RED));
                return false;
            }
        }
        int minY = pts.stream().mapToInt(Location::getBlockY).min().orElse(player.getLocation().getBlockY());
        int maxY = pts.stream().mapToInt(Location::getBlockY).max().orElse(player.getLocation().getBlockY()) + 5;
        Map<String, Object> polyMap = polygonRegionMap(world, minY, maxY, pts);
        FileConfiguration cfg = plugin.getConfig();
        if (safe) {
            migrateLegacySafeIfNeeded();
            appendSafeRegion(polyMap);
            cfg.set("zones.safe.enabled", true);
            clearLegacySafeFlatKeys(cfg);
            plugin.saveConfig();
            invalidateZoneCache();
            player.sendMessage(Component.text(okMsg + " (" + pts.size() + " points) — total: " + countSafeRegions(cfg), color));
        } else {
            migrateLegacyWarIfNeeded();
            appendWarRegion(polyMap);
            cfg.set("zones.war.enabled", true);
            clearLegacyWarFlatKeys(cfg);
            plugin.saveConfig();
            invalidateZoneCache();
            player.sendMessage(Component.text(okMsg + " (" + pts.size() + " points) — total: " + countWarRegions(cfg), color));
        }
        return true;
    }

    // --- Config: listes de régions + legacy ---

    private void migrateLegacySafeIfNeeded() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getMapList("zones.safe.regions").isEmpty()) {
            return;
        }
        if (!cfg.getBoolean("zones.safe.enabled", false)) {
            return;
        }
        String w = cfg.getString("zones.safe.world", "");
        if (w == null || w.isEmpty()) {
            return;
        }
        Map<String, Object> leg = legacySafeAsMap(cfg);
        if (leg == null) {
            return;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(leg);
        cfg.set("zones.safe.regions", list);
        clearLegacySafeFlatKeys(cfg);
    }

    private void migrateLegacyWarIfNeeded() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getMapList("zones.war.regions").isEmpty()) {
            return;
        }
        if (!cfg.getBoolean("zones.war.enabled", false)) {
            return;
        }
        String w = cfg.getString("zones.war.world", "");
        if (w == null || w.isEmpty()) {
            return;
        }
        Map<String, Object> leg = legacyWarAsMap(cfg);
        if (leg == null) {
            return;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(leg);
        cfg.set("zones.war.regions", list);
        clearLegacyWarFlatKeys(cfg);
    }

    private static Map<String, Object> legacySafeAsMap(FileConfiguration cfg) {
        String shape = cfg.getString("zones.safe.shape", "sphere");
        String world = cfg.getString("zones.safe.world", "");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shape", shape);
        m.put("world", world);
        if ("cuboid".equalsIgnoreCase(shape)) {
            m.put("min", Map.of(
                    "x", cfg.getInt("zones.safe.min.x"),
                    "y", cfg.getInt("zones.safe.min.y"),
                    "z", cfg.getInt("zones.safe.min.z")));
            m.put("max", Map.of(
                    "x", cfg.getInt("zones.safe.max.x"),
                    "y", cfg.getInt("zones.safe.max.y"),
                    "z", cfg.getInt("zones.safe.max.z")));
        } else if ("polygon".equalsIgnoreCase(shape)) {
            m.put("min", Map.of("y", cfg.getInt("zones.safe.min.y")));
            m.put("max", Map.of("y", cfg.getInt("zones.safe.max.y")));
            m.put("points", new ArrayList<>(cfg.getStringList("zones.safe.points")));
        } else {
            m.put("x", cfg.getDouble("zones.safe.x", 0));
            m.put("y", cfg.getDouble("zones.safe.y", 0));
            m.put("z", cfg.getDouble("zones.safe.z", 0));
            m.put("radius", Math.max(0, cfg.getDouble("zones.safe.radius", 0)));
        }
        return m;
    }

    private static Map<String, Object> legacyWarAsMap(FileConfiguration cfg) {
        String shape = cfg.getString("zones.war.shape", "sphere");
        String world = cfg.getString("zones.war.world", "");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shape", shape);
        m.put("world", world);
        if ("cuboid".equalsIgnoreCase(shape)) {
            m.put("min", Map.of(
                    "x", cfg.getInt("zones.war.min.x"),
                    "y", cfg.getInt("zones.war.min.y"),
                    "z", cfg.getInt("zones.war.min.z")));
            m.put("max", Map.of(
                    "x", cfg.getInt("zones.war.max.x"),
                    "y", cfg.getInt("zones.war.max.y"),
                    "z", cfg.getInt("zones.war.max.z")));
        } else if ("polygon".equalsIgnoreCase(shape)) {
            m.put("min", Map.of("y", cfg.getInt("zones.war.min.y")));
            m.put("max", Map.of("y", cfg.getInt("zones.war.max.y")));
            m.put("points", new ArrayList<>(cfg.getStringList("zones.war.points")));
        } else {
            m.put("x", cfg.getDouble("zones.war.x", 0));
            m.put("y", cfg.getDouble("zones.war.y", 0));
            m.put("z", cfg.getDouble("zones.war.z", 0));
            m.put("radius", Math.max(0, cfg.getDouble("zones.war.radius", 0)));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private void appendSafeRegion(Map<String, Object> region) {
        FileConfiguration cfg = plugin.getConfig();
        List<Map<?, ?>> raw = cfg.getMapList("zones.safe.regions");
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<?, ?> e : raw) {
            list.add((Map<String, Object>) (Map<?, ?>) e);
        }
        list.add(region);
        cfg.set("zones.safe.regions", list);
    }

    @SuppressWarnings("unchecked")
    private void appendWarRegion(Map<String, Object> region) {
        FileConfiguration cfg = plugin.getConfig();
        List<Map<?, ?>> raw = cfg.getMapList("zones.war.regions");
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<?, ?> e : raw) {
            list.add((Map<String, Object>) (Map<?, ?>) e);
        }
        list.add(region);
        cfg.set("zones.war.regions", list);
    }

    private static void clearLegacySafeFlatKeys(FileConfiguration cfg) {
        cfg.set("zones.safe.shape", null);
        cfg.set("zones.safe.world", null);
        cfg.set("zones.safe.x", null);
        cfg.set("zones.safe.y", null);
        cfg.set("zones.safe.z", null);
        cfg.set("zones.safe.radius", null);
        cfg.set("zones.safe.min", null);
        cfg.set("zones.safe.max", null);
        cfg.set("zones.safe.points", null);
    }

    private static void clearLegacyWarFlatKeys(FileConfiguration cfg) {
        cfg.set("zones.war.shape", null);
        cfg.set("zones.war.world", null);
        cfg.set("zones.war.x", null);
        cfg.set("zones.war.y", null);
        cfg.set("zones.war.z", null);
        cfg.set("zones.war.radius", null);
        cfg.set("zones.war.min", null);
        cfg.set("zones.war.max", null);
        cfg.set("zones.war.points", null);
    }

    private static Map<String, Object> sphereRegionMap(String world, double x, double y, double z, double radius) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shape", "sphere");
        m.put("world", world);
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        m.put("radius", radius);
        return m;
    }

    private static Map<String, Object> cuboidRegionMap(Location a, Location b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shape", "cuboid");
        m.put("world", a.getWorld().getName());
        m.put("min", Map.of(
                "x", Math.min(a.getBlockX(), b.getBlockX()),
                "y", Math.min(a.getBlockY(), b.getBlockY()),
                "z", Math.min(a.getBlockZ(), b.getBlockZ())));
        m.put("max", Map.of(
                "x", Math.max(a.getBlockX(), b.getBlockX()),
                "y", Math.max(a.getBlockY(), b.getBlockY()),
                "z", Math.max(a.getBlockZ(), b.getBlockZ())));
        return m;
    }

    private static Map<String, Object> polygonRegionMap(String world, int minY, int maxY, List<Location> pts) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shape", "polygon");
        m.put("world", world);
        m.put("min", Map.of("y", minY));
        m.put("max", Map.of("y", maxY));
        List<String> raw = new ArrayList<>();
        for (Location p : pts) {
            raw.add(p.getBlockX() + ";" + p.getBlockZ());
        }
        m.put("points", raw);
        return m;
    }

    private static int countSafeRegions(FileConfiguration cfg) {
        if (!cfg.getBoolean("zones.safe.enabled", false)) {
            return 0;
        }
        List<Map<?, ?>> r = cfg.getMapList("zones.safe.regions");
        if (!r.isEmpty()) {
            return r.size();
        }
        return cfg.getString("zones.safe.world", "").isEmpty() ? 0 : 1;
    }

    private static int countWarRegions(FileConfiguration cfg) {
        if (!cfg.getBoolean("zones.war.enabled", false)) {
            return 0;
        }
        List<Map<?, ?>> r = cfg.getMapList("zones.war.regions");
        if (!r.isEmpty()) {
            return r.size();
        }
        return cfg.getString("zones.war.world", "").isEmpty() ? 0 : 1;
    }

    private List<SafeRegion> loadSafeRegions(FileConfiguration cfg) {
        List<SafeRegion> out = new ArrayList<>();
        List<Map<?, ?>> raw = cfg.getMapList("zones.safe.regions");
        if (!raw.isEmpty()) {
            for (Map<?, ?> e : raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) (Map<?, ?>) e;
                SafeRegion r = SafeRegion.parse(m);
                if (r != null) {
                    out.add(r);
                }
            }
            return out;
        }
        Map<String, Object> leg = legacySafeAsMap(cfg);
        SafeRegion r = SafeRegion.parse(leg);
        if (r != null && cfg.getBoolean("zones.safe.enabled", false)) {
            String w = cfg.getString("zones.safe.world", "");
            if (w != null && !w.isEmpty()) {
                out.add(r);
            }
        }
        return out;
    }

    private List<WarRegion> loadWarRegions(FileConfiguration cfg) {
        List<WarRegion> out = new ArrayList<>();
        List<Map<?, ?>> raw = cfg.getMapList("zones.war.regions");
        if (!raw.isEmpty()) {
            for (Map<?, ?> e : raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) (Map<?, ?>) e;
                WarRegion r = WarRegion.parse(m);
                if (r != null) {
                    out.add(r);
                }
            }
            return out;
        }
        Map<String, Object> leg = legacyWarAsMap(cfg);
        WarRegion r = WarRegion.parse(leg);
        if (r != null && cfg.getBoolean("zones.war.enabled", false)) {
            String w = cfg.getString("zones.war.world", "");
            if (w != null && !w.isEmpty()) {
                out.add(r);
            }
        }
        return out;
    }

    private abstract static class SafeRegion {
        final String world;

        SafeRegion(String world) {
            this.world = world;
        }

        abstract boolean contains(Location loc);

        abstract boolean segmentIntersects(Location a, Location b);

        static SafeRegion parse(Map<String, Object> m) {
            if (m == null || m.isEmpty()) {
                return null;
            }
            String shape = String.valueOf(m.getOrDefault("shape", "sphere"));
            String w = m.get("world") != null ? m.get("world").toString() : "";
            if (w.isEmpty()) {
                return null;
            }
            if ("cuboid".equalsIgnoreCase(shape)) {
                return SafeCuboid.parse(w, m);
            }
            if ("polygon".equalsIgnoreCase(shape)) {
                return SafePolygon.parse(w, m);
            }
            return SafeSphere.parse(w, m);
        }
    }

    private static final class SafeSphere extends SafeRegion {
        private final double cx, cy, cz, r2;

        SafeSphere(String world, double cx, double cy, double cz, double r) {
            super(world);
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.r2 = r * r;
        }

        static SafeSphere parse(String world, Map<String, Object> m) {
            double x = num(m.get("x"));
            double y = num(m.get("y"));
            double z = num(m.get("z"));
            double r = Math.max(0, num(m.get("radius")));
            return new SafeSphere(world, x, y, z, r);
        }

        @Override
        boolean contains(Location loc) {
            if (loc.getWorld() == null || !world.equals(loc.getWorld().getName())) {
                return false;
            }
            double dx = loc.getX() - cx;
            double dy = loc.getY() - cy;
            double dz = loc.getZ() - cz;
            return dx * dx + dy * dy + dz * dz <= r2 + 1e-9;
        }

        @Override
        boolean segmentIntersects(Location a, Location b) {
            if (a.getWorld() == null || !world.equals(a.getWorld().getName())) {
                return false;
            }
            Vector p0 = a.toVector();
            Vector d = b.toVector().subtract(p0);
            Vector f = p0.clone().subtract(new Vector(cx, cy, cz));
            double aCoef = d.lengthSquared();
            if (aCoef < 1e-12) {
                return contains(a);
            }
            double bCoef = 2 * f.dot(d);
            double cCoef = f.lengthSquared() - r2;
            double disc = bCoef * bCoef - 4 * aCoef * cCoef;
            if (disc < 0) {
                return false;
            }
            double sq = Math.sqrt(disc);
            double t1 = (-bCoef - sq) / (2 * aCoef);
            double t2 = (-bCoef + sq) / (2 * aCoef);
            return tMax(t1, t2) >= 0 && tMin(t1, t2) <= 1;
        }
    }

    private static final class SafeCuboid extends SafeRegion {
        private final int minX, minY, minZ, maxX, maxY, maxZ;

        SafeCuboid(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            super(world);
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }

        static SafeCuboid parse(String world, Map<String, Object> m) {
            Map<String, Object> mn = childMap(m, "min");
            Map<String, Object> mx = childMap(m, "max");
            return new SafeCuboid(world,
                    intv(mn, "x"), intv(mn, "y"), intv(mn, "z"),
                    intv(mx, "x"), intv(mx, "y"), intv(mx, "z"));
        }

        @Override
        boolean contains(Location loc) {
            if (loc.getWorld() == null || !world.equals(loc.getWorld().getName())) {
                return false;
            }
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        @Override
        boolean segmentIntersects(Location a, Location b) {
            if (a.getWorld() == null || !world.equals(a.getWorld().getName())) {
                return false;
            }
            double ax = minX;
            double ay = minY;
            double az = minZ;
            double bx = maxX + 1.0;
            double by = maxY + 1.0;
            double bz = maxZ + 1.0;
            return segmentIntersectsAabb(a.toVector(), b.toVector(), ax, ay, az, bx, by, bz);
        }
    }

    private static final class SafePolygon extends SafeRegion {
        private final int minY, maxY;
        private final List<int[]> xz;

        SafePolygon(String world, int minY, int maxY, List<int[]> xz) {
            super(world);
            this.minY = minY;
            this.maxY = maxY;
            this.xz = xz;
        }

        static SafePolygon parse(String world, Map<String, Object> m) {
            Map<String, Object> mn = childMap(m, "min");
            Map<String, Object> mx = childMap(m, "max");
            int minY = intv(mn, "y");
            int maxY = intv(mx, "y");
            List<int[]> pts = new ArrayList<>();
            Object pl = m.get("points");
            if (pl instanceof List<?> list) {
                for (Object o : list) {
                    if (o == null) {
                        continue;
                    }
                    String[] sp = o.toString().split(";");
                    if (sp.length != 2) {
                        continue;
                    }
                    try {
                        pts.add(new int[]{Integer.parseInt(sp[0].trim()), Integer.parseInt(sp[1].trim())});
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (pts.size() < 3) {
                return null;
            }
            return new SafePolygon(world, minY, maxY, pts);
        }

        @Override
        boolean contains(Location loc) {
            if (loc.getWorld() == null || !world.equals(loc.getWorld().getName())) {
                return false;
            }
            int y = loc.getBlockY();
            if (y < minY || y > maxY) {
                return false;
            }
            return pointInPolygonXZ(loc.getX(), loc.getZ(), xz);
        }

        @Override
        boolean segmentIntersects(Location a, Location b) {
            if (a.getWorld() == null || !world.equals(a.getWorld().getName())) {
                return false;
            }
            double ya = a.getY();
            double yb = b.getY();
            double lo = Math.min(ya, yb);
            double hi = Math.max(ya, yb);
            if (hi < minY || lo > maxY) {
                return false;
            }
            if (contains(a) || contains(b)) {
                return true;
            }
            double ax = a.getX();
            double az = a.getZ();
            double bx = b.getX();
            double bz = b.getZ();
            int n = xz.size();
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                int[] pi = xz.get(i);
                int[] pj = xz.get(j);
                if (segmentsIntersect2D(ax, az, bx, bz, pi[0], pi[1], pj[0], pj[1])) {
                    return true;
                }
            }
            return false;
        }
    }

    private abstract static class WarRegion {
        final String world;

        WarRegion(String world) {
            this.world = world;
        }

        abstract boolean contains(Location loc);

        abstract Location randomXZ(World w, FileConfiguration cfgIgnored, ThreadLocalRandom rnd);

        /** {@code null} si pas de bornes Y explicites (sphere / full height). */
        int[] verticalBounds(FileConfiguration cfg) {
            return null;
        }

        static WarRegion parse(Map<String, Object> m) {
            if (m == null || m.isEmpty()) {
                return null;
            }
            String shape = String.valueOf(m.getOrDefault("shape", "sphere"));
            String w = m.get("world") != null ? m.get("world").toString() : "";
            if (w.isEmpty()) {
                return null;
            }
            if ("cuboid".equalsIgnoreCase(shape)) {
                return WarCuboid.parse(w, m);
            }
            if ("polygon".equalsIgnoreCase(shape)) {
                return WarPolygon.parse(w, m);
            }
            return WarSphere.parse(w, m);
        }
    }

    private static final class WarSphere extends WarRegion {
        private final double cx, cy, cz, r2;

        WarSphere(String world, double cx, double cy, double cz, double r) {
            super(world);
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.r2 = r * r;
        }

        static WarSphere parse(String world, Map<String, Object> m) {
            double x = num(m.get("x"));
            double y = num(m.get("y"));
            double z = num(m.get("z"));
            double rad = Math.max(0, num(m.get("radius")));
            return new WarSphere(world, x, y, z, rad);
        }

        @Override
        boolean contains(Location loc) {
            if (loc.getWorld() == null || !world.equals(loc.getWorld().getName())) {
                return false;
            }
            double dx = loc.getX() - cx;
            double dy = loc.getY() - cy;
            double dz = loc.getZ() - cz;
            return dx * dx + dy * dy + dz * dz <= r2 + 1e-9;
        }

        @Override
        Location randomXZ(World w, FileConfiguration cfg, ThreadLocalRandom rnd) {
            double r = Math.sqrt(r2);
            for (int t = 0; t < 8; t++) {
                double u = rnd.nextDouble();
                double v = rnd.nextDouble();
                double dx = (u * 2 - 1) * r;
                double dz = (v * 2 - 1) * r;
                if (dx * dx + dz * dz <= r2) {
                    return new Location(w, cx + dx, 0, cz + dz);
                }
            }
            return null;
        }
    }

    private static final class WarCuboid extends WarRegion {
        private final int minX, minZ, maxX, maxZ;
        private final int minY, maxY;

        WarCuboid(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            super(world);
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }

        static WarCuboid parse(String world, Map<String, Object> m) {
            Map<String, Object> mn = childMap(m, "min");
            Map<String, Object> mx = childMap(m, "max");
            return new WarCuboid(world,
                    intv(mn, "x"), intv(mn, "y"), intv(mn, "z"),
                    intv(mx, "x"), intv(mx, "y"), intv(mx, "z"));
        }

        @Override
        boolean contains(Location loc) {
            if (loc.getWorld() == null || !world.equals(loc.getWorld().getName())) {
                return false;
            }
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        @Override
        Location randomXZ(World w, FileConfiguration cfg, ThreadLocalRandom rnd) {
            int xa = minX;
            int xb = maxX;
            int za = minZ;
            int zb = maxZ;
            int x = xa == xb ? xa : rnd.nextInt(Math.min(xa, xb), Math.max(xa, xb) + 1);
            int z = za == zb ? za : rnd.nextInt(Math.min(za, zb), Math.max(za, zb) + 1);
            return new Location(w, x, 0, z);
        }

        @Override
        int[] verticalBounds(FileConfiguration cfg) {
            return new int[]{minY, maxY};
        }
    }

    private static final class WarPolygon extends WarRegion {
        private final int minY, maxY;
        private final List<int[]> xz;

        WarPolygon(String world, int minY, int maxY, List<int[]> xz) {
            super(world);
            this.minY = minY;
            this.maxY = maxY;
            this.xz = xz;
        }

        static WarPolygon parse(String world, Map<String, Object> m) {
            Map<String, Object> mn = childMap(m, "min");
            Map<String, Object> mx = childMap(m, "max");
            int minY = intv(mn, "y");
            int maxY = intv(mx, "y");
            List<int[]> pts = new ArrayList<>();
            Object pl = m.get("points");
            if (pl instanceof List<?> list) {
                for (Object o : list) {
                    if (o == null) {
                        continue;
                    }
                    String[] sp = o.toString().split(";");
                    if (sp.length != 2) {
                        continue;
                    }
                    try {
                        pts.add(new int[]{Integer.parseInt(sp[0].trim()), Integer.parseInt(sp[1].trim())});
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (pts.size() < 3) {
                return null;
            }
            return new WarPolygon(world, minY, maxY, pts);
        }

        @Override
        boolean contains(Location loc) {
            if (loc.getWorld() == null || !world.equals(loc.getWorld().getName())) {
                return false;
            }
            int y = loc.getBlockY();
            if (y < minY || y > maxY) {
                return false;
            }
            return pointInPolygonXZ(loc.getX(), loc.getZ(), xz);
        }

        @Override
        Location randomXZ(World world, FileConfiguration cfg, ThreadLocalRandom rnd) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int[] p : xz) {
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
            if (minX == Integer.MAX_VALUE) {
                return null;
            }
            for (int t = 0; t < 12; t++) {
                int x = minX == maxX ? minX : rnd.nextInt(minX, maxX + 1);
                int z = minZ == maxZ ? minZ : rnd.nextInt(minZ, maxZ + 1);
                Location test = new Location(world, x + 0.5, 64, z + 0.5);
                if (pointInPolygonXZ(test.getX(), test.getZ(), xz)) {
                    return new Location(world, x, 0, z);
                }
            }
            return null;
        }

        @Override
        int[] verticalBounds(FileConfiguration cfg) {
            return new int[]{minY, maxY};
        }
    }

    private static double tMin(double t1, double t2) {
        return Math.min(t1, t2);
    }

    private static double tMax(double t1, double t2) {
        return Math.max(t1, t2);
    }

    private static boolean segmentIntersectsAabb(Vector p0, Vector p1,
                                                 double minX, double minY, double minZ,
                                                 double maxX, double maxY, double maxZ) {
        Vector d = p1.clone().subtract(p0);
        double t0 = 0;
        double t1 = 1;
        for (int axis = 0; axis < 3; axis++) {
            double p = axis == 0 ? p0.getX() : axis == 1 ? p0.getY() : p0.getZ();
            double di = axis == 0 ? d.getX() : axis == 1 ? d.getY() : d.getZ();
            double pMin = axis == 0 ? minX : axis == 1 ? minY : minZ;
            double pMax = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
            if (Math.abs(di) < 1e-12) {
                if (p < pMin || p >= pMax) {
                    return false;
                }
                continue;
            }
            double inv = 1.0 / di;
            double tNear = (pMin - p) * inv;
            double tFar = (pMax - p) * inv;
            if (tNear > tFar) {
                double tmp = tNear;
                tNear = tFar;
                tFar = tmp;
            }
            t0 = Math.max(t0, tNear);
            t1 = Math.min(t1, tFar);
            if (t0 > t1) {
                return false;
            }
        }
        return true;
    }

    private static boolean pointInPolygonXZ(double x, double z, List<int[]> pts) {
        if (pts.size() < 3) {
            return false;
        }
        boolean inside = false;
        for (int i = 0, j = pts.size() - 1; i < pts.size(); j = i++) {
            double xi = pts.get(i)[0];
            double zi = pts.get(i)[1];
            double xj = pts.get(j)[0];
            double zj = pts.get(j)[1];
            boolean intersect = ((zi > z) != (zj > z))
                    && (x < (xj - xi) * (z - zi) / ((zj - zi) + 1e-9) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static boolean segmentsIntersect2D(double ax, double az, double bx, double bz,
                                               double cx, double cz, double dx, double dz) {
        double o1 = orient(ax, az, bx, bz, cx, cz);
        double o2 = orient(ax, az, bx, bz, dx, dz);
        double o3 = orient(cx, cz, dx, dz, ax, az);
        double o4 = orient(cx, cz, dx, dz, bx, bz);
        if (o1 * o2 < 0 && o3 * o4 < 0) {
            return true;
        }
        return Math.abs(o1) < 1e-9 && onSegment(ax, az, bx, bz, cx, cz)
                || Math.abs(o2) < 1e-9 && onSegment(ax, az, bx, bz, dx, dz)
                || Math.abs(o3) < 1e-9 && onSegment(cx, cz, dx, dz, ax, az)
                || Math.abs(o4) < 1e-9 && onSegment(cx, cz, dx, dz, bx, bz);
    }

    private static double orient(double ax, double az, double bx, double bz, double cx, double cz) {
        return (bz - az) * (cx - bx) - (bx - ax) * (cz - bz);
    }

    private static boolean onSegment(double ax, double az, double bx, double bz, double px, double pz) {
        return px <= Math.max(ax, bx) + 1e-9 && px + 1e-9 >= Math.min(ax, bx)
                && pz <= Math.max(az, bz) + 1e-9 && pz + 1e-9 >= Math.min(az, bz);
    }

    private static double num(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return 0;
    }

    private static int intv(Map<String, Object> m, String k) {
        Object o = m.get(k);
        if (o instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> childMap(Map<String, Object> m, String k) {
        Object o = m.get(k);
        if (o instanceof Map<?, ?> raw) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                if (e.getKey() != null) {
                    out.put(e.getKey().toString(), e.getValue());
                }
            }
            return out;
        }
        return Map.of();
    }
}
