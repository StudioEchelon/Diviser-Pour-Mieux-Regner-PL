package fr.dpmr.npc;

import fr.dpmr.data.PointsManager;
import fr.dpmr.game.WeaponManager;
import fr.dpmr.npc.citizens.CitizensCombatNpcBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import fr.dpmr.game.WeaponProfile;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class NpcSpawnerManager implements Listener {

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final WeaponManager weaponManager;
    private final File file;
    private final YamlConfiguration yaml;
    private final NamespacedKey keyNpcKind;
    private final NamespacedKey keyNpcReward;
    private final NamespacedKey keyNpcBaseName;
    private final NamespacedKey keyFakeNpc;
    private final NamespacedKey keyNpcSpawnSource;
    private final NamespacedKey keyNpcShotScale;
    private final Map<String, NpcSpawnerDef> spawners = new HashMap<>();
    /** Blocs ou un spawn DPMR est autorise (contourne WorldGuard / annulations). */
    private final Set<String> spawnBypassBlockKeys = ConcurrentHashMap.newKeySet();
    /** Handle Citizens par UUID entite : {@code getNPC(mort)} echoue souvent, il faut detruire via le handle tout de suite. */
    private final Map<UUID, Object> citizensCombatNpcHandles = new ConcurrentHashMap<>();

    private enum NpcKind {
        MILITARY, ZOMBIE, RAIDER, PATROL;

        static NpcKind from(String raw) {
            if (raw == null || raw.isBlank()) {
                return MILITARY;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return MILITARY;
            }
        }
    }

    private record NpcSpawnerDef(String id, String world, int x, int y, int z, NpcKind kind, int rewardPoints, int goldMin, int goldMax, int despawnTicks, double npcShotDamageScale) {
        NpcSpawnerDef(String id, String world, int x, int y, int z, NpcKind kind, int rewardPoints, int goldMin, int goldMax, int despawnTicks) {
            this(id, world, x, y, z, kind, rewardPoints, goldMin, goldMax, despawnTicks, 1.0);
        }
        NpcSpawnerDef(String id, String world, int x, int y, int z, NpcKind kind, int rewardPoints, int goldMin, int goldMax) {
            this(id, world, x, y, z, kind, rewardPoints, goldMin, goldMax, 225, 1.0);
        }
    }

    private static final String[] WEAPONS_MILITARY = {"CARABINE_MK18", "AK47", "PULSE"};
    private static final String[] WEAPONS_ZOMBIE = {"CM_BAMBOU", "GLOCK18", "CM_PEPITE"};
    private static final String[] WEAPONS_RAIDER = {"DRAGUNOV_SVD", "DEAGLE_RL", "AWP"};
    /** Sentinelles zone war : un seul pistolet léger, tir espacé côté stats. */
    private static final String[] WEAPONS_PATROL = {"GLOCK18"};
    public NpcSpawnerManager(JavaPlugin plugin, PointsManager pointsManager, WeaponManager weaponManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.weaponManager = weaponManager;
        this.file = new File(plugin.getDataFolder(), "npc-spawners.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        this.keyNpcKind = new NamespacedKey(plugin, "dpmr_npc_kind");
        this.keyNpcReward = new NamespacedKey(plugin, "dpmr_npc_reward");
        this.keyNpcBaseName = new NamespacedKey(plugin, "dpmr_npc_base_name");
        this.keyFakeNpc = new NamespacedKey(plugin, "dpmr_fake_npc");
        this.keyNpcSpawnSource = new NamespacedKey(plugin, "dpmr_npc_spawn_src");
        this.keyNpcShotScale = new NamespacedKey(plugin, "dpmr_npc_shot_scale");
        load();
    }

    /** Retire tous les PNJ DPMR (obligatoire au reload / disable du plugin). */
    public void shutdown() {
        killAllSpawnedFakeNpcs();
    }

    /**
     * Nos PNJ portent {@code dpmr_fake_npc} ; si le namespace du plugin a change (jar renomme, etc.) la cle
     * actuelle ne matche plus — on accepte toute cle nommee {@code dpmr_fake_npc} ou le couple kind+reward
     * sous le meme namespace.
     */
    private boolean isDpmrFakeCombatNpc(LivingEntity e) {
        var pdc = e.getPersistentDataContainer();
        if (pdc.has(keyFakeNpc, PersistentDataType.BYTE)) {
            return true;
        }
        if (pdc.has(keyFakeNpc, PersistentDataType.INTEGER)) {
            Integer v = pdc.get(keyFakeNpc, PersistentDataType.INTEGER);
            return v != null && v != 0;
        }
        for (NamespacedKey k : pdc.getKeys()) {
            if (!"dpmr_fake_npc".equals(k.getKey())) {
                continue;
            }
            if (pdc.has(k, PersistentDataType.BYTE)) {
                return true;
            }
            if (pdc.has(k, PersistentDataType.INTEGER)) {
                Integer v = pdc.get(k, PersistentDataType.INTEGER);
                if (v != null && v != 0) {
                    return true;
                }
            }
        }
        for (NamespacedKey k : pdc.getKeys()) {
            if (!"dpmr_npc_kind".equals(k.getKey())) {
                continue;
            }
            String kindStr = pdc.get(k, PersistentDataType.STRING);
            if (kindStr == null) {
                continue;
            }
            try {
                NpcKind.valueOf(kindStr);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            NamespacedKey rewardKey = new NamespacedKey(k.getNamespace(), "dpmr_npc_reward");
            if (pdc.has(rewardKey, PersistentDataType.INTEGER)) {
                return true;
            }
        }
        return false;
    }

    private Integer readNpcRewardPoints(LivingEntity e) {
        var pdc = e.getPersistentDataContainer();
        Integer v = pdc.get(keyNpcReward, PersistentDataType.INTEGER);
        if (v != null) {
            return v;
        }
        for (NamespacedKey k : pdc.getKeys()) {
            if ("dpmr_npc_reward".equals(k.getKey())) {
                return pdc.get(k, PersistentDataType.INTEGER);
            }
        }
        return null;
    }

    private String readNpcSpawnSourceTag(LivingEntity e) {
        var pdc = e.getPersistentDataContainer();
        String tag = pdc.get(keyNpcSpawnSource, PersistentDataType.STRING);
        if (tag != null) {
            return tag;
        }
        for (NamespacedKey k : pdc.getKeys()) {
            if ("dpmr_npc_spawn_src".equals(k.getKey())) {
                return pdc.get(k, PersistentDataType.STRING);
            }
        }
        return null;
    }

    private static String blockBypassKey(Location loc) {
        if (loc.getWorld() == null) {
            return "";
        }
        return loc.getWorld().getName() + "|" + loc.getBlockX() + "|" + loc.getBlockY() + "|" + loc.getBlockZ();
    }

    /**
     * Si WorldGuard (ou autre) annule le spawn, on re-autorise uniquement pour nos spawns prevus.
     */
    /** false = on reçoit aussi les events déjà annulés (ex. WorldGuard) pour pouvoir les ré-autoriser. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreatureSpawnBypass(CreatureSpawnEvent event) {
        if (spawnBypassBlockKeys.contains(blockBypassKey(event.getEntity().getLocation()))) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntitySpawnBypass(EntitySpawnEvent event) {
        Entity e = event.getEntity();
        if (spawnBypassBlockKeys.contains(blockBypassKey(e.getLocation()))) {
            event.setCancelled(false);
        }
    }

    private void load() {
        spawners.clear();
        if (!yaml.isConfigurationSection("spawners")) {
            return;
        }
        for (String key : Objects.requireNonNull(yaml.getConfigurationSection("spawners")).getKeys(false)) {
            String base = "spawners." + key + ".";
            String world = yaml.getString(base + "world", "");
            int x = yaml.getInt(base + "x", 0);
            int y = yaml.getInt(base + "y", 0);
            int z = yaml.getInt(base + "z", 0);
            NpcKind kind = NpcKind.from(yaml.getString(base + "kind", "MILITARY"));
            int reward = Math.max(1, yaml.getInt(base + "reward-points", 4));
            int gMin = Math.max(0, yaml.getInt(base + "reward-gold-min", 1));
            int gMax = Math.max(gMin, yaml.getInt(base + "reward-gold-max", 3));
            int despawn = Math.max(40, yaml.getInt(base + "despawn-ticks", 225));
            double shotScale = yaml.getDouble(base + "npc-shot-damage-scale", 1.0);
            if (shotScale <= 0 || Double.isNaN(shotScale)) {
                shotScale = 1.0;
            }
            shotScale = Math.min(4.0, shotScale);
            if (!world.isBlank()) {
                spawners.put(key.toLowerCase(Locale.ROOT), new NpcSpawnerDef(key.toLowerCase(Locale.ROOT), world, x, y, z, kind, reward, gMin, gMax, despawn, shotScale));
            }
        }
    }

    public void save() {
        yaml.set("spawners", null);
        for (NpcSpawnerDef def : spawners.values()) {
            String base = "spawners." + def.id + ".";
            yaml.set(base + "world", def.world);
            yaml.set(base + "x", def.x);
            yaml.set(base + "y", def.y);
            yaml.set(base + "z", def.z);
            yaml.set(base + "kind", def.kind.name());
            yaml.set(base + "reward-points", def.rewardPoints);
            yaml.set(base + "reward-gold-min", def.goldMin);
            yaml.set(base + "reward-gold-max", def.goldMax);
            yaml.set(base + "despawn-ticks", def.despawnTicks());
            if (def.npcShotDamageScale() > 0 && Math.abs(def.npcShotDamageScale() - 1.0) > 1e-4) {
                yaml.set(base + "npc-shot-damage-scale", def.npcShotDamageScale());
            } else {
                yaml.set(base + "npc-shot-damage-scale", null);
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder npc-spawners.yml: " + e.getMessage());
        }
    }

    public void create(Player admin, String id, Location blockLoc, String kindRaw, int rewardPoints, int goldMin, int goldMax) {
        if (blockLoc.getWorld() == null) {
            return;
        }
        String key = id.toLowerCase(Locale.ROOT);
        NpcKind kind = NpcKind.from(kindRaw);
        int gMin = Math.max(0, goldMin);
        int gMax = Math.max(gMin, goldMax);
        spawners.put(key, new NpcSpawnerDef(
                key, blockLoc.getWorld().getName(),
                blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ(),
                kind, Math.max(1, rewardPoints), gMin, gMax, 225, 1.0
        ));
        save();
        admin.sendMessage(Component.text(
                "Spawner NPC '" + key + "' cree (" + kind.name() + ") @ "
                        + blockLoc.getBlockX() + " " + blockLoc.getBlockY() + " " + blockLoc.getBlockZ(),
                NamedTextColor.GREEN));
    }

    public void delete(Player admin, String id) {
        String key = id.toLowerCase(Locale.ROOT);
        if (spawners.remove(key) != null) {
            save();
            admin.sendMessage(Component.text("Spawner NPC '" + key + "' supprime.", NamedTextColor.YELLOW));
        } else {
            admin.sendMessage(Component.text("Spawner not found.", NamedTextColor.RED));
        }
    }

    public java.util.List<String> list() {
        return spawners.keySet().stream().sorted().toList();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() != Material.SPAWNER) {
            return;
        }
        Location at = event.getClickedBlock().getLocation();
        NpcSpawnerDef def = findByLocation(at);
        if (def == null) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text(
                "Spawner '" + def.id + "' — spawn PNJ désactivé", NamedTextColor.GRAY));
    }

    private NpcSpawnerDef findByLocation(Location loc) {
        if (loc.getWorld() == null) {
            return null;
        }
        for (NpcSpawnerDef def : spawners.values()) {
            if (!def.world.equals(loc.getWorld().getName())) {
                continue;
            }
            if (def.x == loc.getBlockX() && def.y == loc.getBlockY() && def.z == loc.getBlockZ()) {
                return def;
            }
        }
        return null;
    }

    private ItemStack weaponItemFor(NpcKind kind) {
        String[] ids = switch (kind) {
            case MILITARY -> WEAPONS_MILITARY;
            case ZOMBIE -> WEAPONS_ZOMBIE;
            case RAIDER -> WEAPONS_RAIDER;
            case PATROL -> WEAPONS_PATROL;
        };
        String id = ids[ThreadLocalRandom.current().nextInt(ids.length)];
        ItemStack w = weaponManager.createWeaponItem(id);
        return w != null ? w : new ItemStack(Material.IRON_SWORD);
    }

    private void applyNpcRewardsPdc(LivingEntity living, NpcSpawnerDef def, String displayName, String spawnSource) {
        living.getPersistentDataContainer().set(keyNpcKind, PersistentDataType.STRING, def.kind.name());
        living.getPersistentDataContainer().set(keyNpcReward, PersistentDataType.INTEGER, def.rewardPoints);
        living.getPersistentDataContainer().set(keyFakeNpc, PersistentDataType.BYTE, (byte) 1);
        living.getPersistentDataContainer().set(keyNpcBaseName, PersistentDataType.STRING, displayName);
        if (spawnSource != null && !spawnSource.isBlank()) {
            living.getPersistentDataContainer().set(keyNpcSpawnSource, PersistentDataType.STRING, spawnSource);
        } else {
            living.getPersistentDataContainer().remove(keyNpcSpawnSource);
        }
        double s = def.npcShotDamageScale();
        if (s > 0 && Math.abs(s - 1.0) > 1e-4 && !Double.isNaN(s)) {
            living.getPersistentDataContainer().set(keyNpcShotScale, PersistentDataType.DOUBLE, Math.min(4.0, s));
        } else {
            living.getPersistentDataContainer().remove(keyNpcShotScale);
        }
    }

    /** Désactivé : aucun spawn de PNJ de combat par le plugin. */
    public boolean spawnSingleCustomNpc(Location at, String kindName, int rewardPoints, int goldMin, int goldMax,
            int despawnTicks, String spawnSource) {
        return false;
    }

    /** Désactivé : aucun spawn de PNJ de combat par le plugin. */
    public boolean spawnSingleCustomNpc(Location at, String kindName, int rewardPoints, int goldMin, int goldMax,
            int despawnTicks, String spawnSource, double npcShotDamageScale) {
        return false;
    }

    /**
     * Retire tous les PNJ de combat DPMR (spawners, war world, etc.) sans donner de recompenses.
     */
    public int killAllSpawnedFakeNpcs() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity ent : w.getEntities()) {
                if (!(ent instanceof LivingEntity e) || !e.isValid()) {
                    continue;
                }
                if (!isDpmrFakeCombatNpc(e)) {
                    continue;
                }
                n++;
                if (e instanceof Player pl && CitizensCombatNpcBridge.isCitizensNpcEntity(pl)) {
                    Object h = citizensCombatNpcHandles.remove(pl.getUniqueId());
                    if (h != null) {
                        CitizensCombatNpcBridge.destroyNpc(h);
                    } else {
                        CitizensCombatNpcBridge.destroyNpcForEntity(pl);
                    }
                } else {
                    e.remove();
                }
            }
        }
        return n;
    }

    public int countFakeNpcsWithSpawnSource(World world, String spawnSource) {
        if (world == null || spawnSource == null || spawnSource.isBlank()) {
            return 0;
        }
        int n = 0;
        for (Entity ent : world.getEntities()) {
            if (!(ent instanceof LivingEntity e) || !isDpmrFakeCombatNpc(e)) {
                continue;
            }
            String tag = readNpcSpawnSourceTag(e);
            if (spawnSource.equals(tag)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Un PNJ : Citizens joueur si dispo, sinon ArmorStand — arme DPMR reelle en main.
     */
    private boolean spawnOneCombatNpc(NpcSpawnerDef def, Location spawn) {
        return spawnOneCombatNpc(def, spawn, null);
    }

    private boolean spawnOneCombatNpc(NpcSpawnerDef def, Location spawn, String spawnSource) {
        World world = spawn.getWorld();
        if (world == null) {
            return false;
        }
        if (!world.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) {
            world.loadChunk(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4);
        }
        Location spawnLoc = findSpawnableLocation(spawn);
        String displayName = randomNpcName();
        ItemStack mainWeapon = weaponItemFor(def.kind);
        UUID skinSeed = UUID.randomUUID();
        ItemStack npcHelmet = NpcSkins.randomHeadForKind(def.kind.name(), skinSeed);

        if (CitizensCombatNpcBridge.isCitizensPresent()) {
            CitizensCombatNpcBridge.CitizensSpawnResult cit = CitizensCombatNpcBridge.trySpawn(
                    plugin, spawnLoc, displayName, def.kind.name(), mainWeapon, npcHelmet);
            if (cit != null && cit.living() != null && cit.living().isValid()) {
                applyNpcRewardsPdc(cit.living(), def, displayName, spawnSource);
                if (def.kind == NpcKind.PATROL) {
                    applyPatrolVitality(cit.living());
                }
                cit.living().setSilent(plugin.getConfig().getBoolean("npc-spawners.silent-npcs", false));
                polishCitizensNpcPlayer(cit.living());
                refreshNpcNameplate(cit.living());
                citizensCombatNpcHandles.put(cit.living().getUniqueId(), cit.citizensNpcHandle());
                world.spawnParticle(org.bukkit.Particle.SMOKE, spawnLoc.clone().add(0, 1, 0), 14, 0.35, 0.3, 0.35, 0.02);
                world.playSound(spawnLoc, Sound.ENTITY_EVOKER_CAST_SPELL, 0.5f, 1.4f);
                startCombatLoop(cit.living(), cit.citizensNpcHandle(), def, mainWeapon.clone());
                return true;
            }
        }

        String bypassKey = blockBypassKey(spawnLoc);
        spawnBypassBlockKeys.add(bypassKey);
        ArmorStand spawned = null;
        try {
            Entity ent = world.spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
            if (ent instanceof ArmorStand as) {
                spawned = as;
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Spawn ArmorStand NPC: " + ex.getMessage());
        } finally {
            Bukkit.getScheduler().runTaskLater(plugin, () -> spawnBypassBlockKeys.remove(bypassKey), 15L);
        }
        final ArmorStand stand = spawned;
        if (stand == null || !stand.isValid()) {
            return false;
        }
        configureFakeNpcStand(stand, def, displayName, mainWeapon, npcHelmet);
        applyNpcRewardsPdc(stand, def, displayName, spawnSource);
        stand.setSilent(plugin.getConfig().getBoolean("npc-spawners.silent-npcs", false));
        refreshNpcNameplate(stand);

        world.spawnParticle(org.bukkit.Particle.SMOKE, spawnLoc.clone().add(0, 1, 0), 14, 0.35, 0.3, 0.35, 0.02);
        world.playSound(spawnLoc, Sound.ENTITY_EVOKER_CAST_SPELL, 0.5f, 1.4f);

        startCombatLoop(stand, null, def, mainWeapon.clone());
        return true;
    }

    private void startCombatLoop(LivingEntity body, Object citizensNpcHandle, NpcSpawnerDef def, ItemStack weaponHeld) {
        FakeNpcStats stats = FakeNpcStats.forKind(def.kind);
        String wid = weaponManager.readWeaponId(weaponHeld);
        WeaponProfile wp = WeaponProfile.fromId(wid);
        final double shootRange = wp != null ? wp.baseRange() * 0.96 : stats.fallbackShootRange;
        final long fireIntervalMs = wp != null
                ? Math.max(150L, wp.cooldownTicks() * 50L + ThreadLocalRandom.current().nextInt(-30, 40))
                : 280L;
        final long[] lastShotMs = {0L};
        final int[] tickCount = {0};
        final int despawnAfterTicks = Math.max(40, def.despawnTicks());
        final BukkitTask[] task = new BukkitTask[1];
        long combatPeriod = Math.max(2L, plugin.getConfig().getLong("npc-spawners.combat-tick-period", 3L));
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!body.isValid() || body.isDead()) {
                if (citizensNpcHandle != null) {
                    citizensCombatNpcHandles.remove(body.getUniqueId());
                    CitizensCombatNpcBridge.destroyNpc(citizensNpcHandle);
                }
                task[0].cancel();
                return;
            }
            tickCount[0]++;
            if (tickCount[0] >= despawnAfterTicks) {
                if (citizensNpcHandle != null) {
                    citizensCombatNpcHandles.remove(body.getUniqueId());
                    CitizensCombatNpcBridge.destroyNpc(citizensNpcHandle);
                } else {
                    body.remove();
                }
                task[0].cancel();
                return;
            }
            refreshNpcNameplate(body);
            Player target = nearestPlayer(body.getLocation(), stats.acquireRange);
            if (target != null && body.hasLineOfSight(target)) {
                nudgeToward(body, target.getLocation(), stats);
            }
            if (target == null) {
                return;
            }
            face(body, target.getEyeLocation());
            long now = System.currentTimeMillis();
            if (now - lastShotMs[0] < fireIntervalMs) {
                return;
            }
            if (body.getLocation().distanceSquared(target.getLocation()) > shootRange * shootRange) {
                return;
            }
            if (!body.hasLineOfSight(target)) {
                return;
            }
            lastShotMs[0] = now;
            weaponManager.npcFireWeapon(body, weaponHeld);
        }, combatPeriod, combatPeriod);
    }

    private static final class FakeNpcStats {
        final double acquireRange;
        final double moveSpeed;
        final double fallbackShootRange;

        FakeNpcStats(double acquireRange, double moveSpeed, double fallbackShootRange) {
            this.acquireRange = acquireRange;
            this.moveSpeed = moveSpeed;
            this.fallbackShootRange = fallbackShootRange;
        }

        static FakeNpcStats forKind(NpcKind kind) {
            return switch (kind) {
                case MILITARY -> new FakeNpcStats(22, 0.34, 14);
                case ZOMBIE -> new FakeNpcStats(14, 0.38, 10);
                case RAIDER -> new FakeNpcStats(24, 0.30, 16);
                case PATROL -> new FakeNpcStats(16, 0.28, 11);
            };
        }
    }

    private static void applyPatrolVitality(LivingEntity living) {
        var maxAttr = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxAttr != null) {
            maxAttr.setBaseValue(14.0);
        }
        living.setHealth(14.0);
    }

    private void refreshNpcNameplate(LivingEntity living) {
        String base = living.getPersistentDataContainer().get(keyNpcBaseName, PersistentDataType.STRING);
        if (base == null || base.isBlank()) {
            return;
        }
        var maxAttr = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        double hp = Math.min(max, Math.max(0.0, living.getHealth()));
        long hpDisp = Math.round(hp);
        long maxDisp = Math.max(1L, Math.round(max));
        NamedTextColor hpColor = hp <= max * 0.25
                ? NamedTextColor.RED
                : (hp <= max * 0.5 ? NamedTextColor.YELLOW : NamedTextColor.GREEN);
        living.customName(Component.text(base, NamedTextColor.WHITE)
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Component.text(hpDisp + "/" + maxDisp, hpColor)));
        living.setCustomNameVisible(true);
    }

    /**
     * PNJ Citizens (entité Player) : évite le double affichage vie / liste, regroupe sous une équipe.
     */
    private void polishCitizensNpcPlayer(LivingEntity living) {
        if (!(living instanceof Player p)) {
            return;
        }
        p.playerListName(Component.empty());
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("dpmr_npc");
        if (team == null) {
            team = sb.registerNewTeam("dpmr_npc");
            team.color(NamedTextColor.GRAY);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        try {
            team.addEntry(p.getName());
        } catch (IllegalStateException ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFakeNpcDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (!isDpmrFakeCombatNpc(living)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (living.isValid() && !living.isDead()) {
                refreshNpcNameplate(living);
            }
        });
    }

    private void nudgeToward(LivingEntity entity, Location target, FakeNpcStats stats) {
        Location s = entity.getLocation();
        if (s.distanceSquared(target) <= 2.8 * 2.8) {
            return;
        }
        Vector delta = target.toVector().subtract(s.toVector());
        delta.setY(0);
        if (delta.lengthSquared() < 0.01) {
            return;
        }
        delta.normalize();
        if (ThreadLocalRandom.current().nextDouble() < 0.14) {
            Vector perp = new Vector(-delta.getZ(), 0, delta.getX());
            if (perp.lengthSquared() > 0.001) {
                perp.normalize().multiply(0.12 + ThreadLocalRandom.current().nextDouble(0, 0.1));
                delta.add(perp);
                delta.normalize();
            }
        }
        double step = Math.min(0.52, stats.moveSpeed * 2.15);
        if (entity instanceof Player p) {
            Vector horiz = delta.clone().multiply(step * 4.2);
            Vector v = p.getVelocity();
            v.setX(horiz.getX());
            v.setZ(horiz.getZ());
            if (p.isOnGround()) {
                v.setY(Math.max(v.getY(), 0.02) + 0.06);
            }
            p.setVelocity(v);
            p.setFallDistance(0f);
            return;
        }
        delta.multiply(step);
        Location next = s.clone().add(delta);
        next.setYaw(s.getYaw());
        next.setPitch(0);
        if (next.getBlock().getType().isAir() && next.clone().add(0, 1, 0).getBlock().getType().isAir()) {
            entity.teleport(next);
        }
    }

    private void configureFakeNpcStand(ArmorStand stand, NpcSpawnerDef def, String displayName, ItemStack mainWeapon,
            ItemStack helmet) {
        stand.setInvisible(false);
        stand.setMarker(false);
        stand.setSmall(false);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(true);
        stand.setCanPickupItems(false);
        stand.setRemoveWhenFarAway(false);
        stand.setPersistent(false);
        stand.setCollidable(true);
        stand.setInvulnerable(false);
        var maxAttr = stand.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxAttr != null) {
            if (def.kind == NpcKind.PATROL) {
                maxAttr.setBaseValue(14.0);
            } else {
                maxAttr.setBaseValue(20.0);
            }
        }
        stand.setHealth(def.kind == NpcKind.PATROL ? 14.0 : 20.0);
        var eq = stand.getEquipment();
        if (eq == null) {
            return;
        }
        eq.setChestplate(new ItemStack(Material.AIR));
        eq.setLeggings(new ItemStack(Material.AIR));
        eq.setBoots(new ItemStack(Material.AIR));
        eq.setHelmet(helmet != null ? helmet.clone() : NpcSkins.randomHeadForKind(def.kind.name(), UUID.randomUUID()));
        eq.setItemInMainHand(mainWeapon != null ? mainWeapon.clone() : new ItemStack(Material.IRON_SWORD));
        eq.setItemInOffHand(new ItemStack(Material.AIR));
        lockAllArmorStandSlots(stand);
    }

    private static void lockAllArmorStandSlots(ArmorStand stand) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
            stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }
    }

    private static String randomNpcName() {
        return NpcNameGenerator.randomDisplayName();
    }

    private Location findSpawnableLocation(Location base) {
        Location loc = base.clone();
        World w = loc.getWorld();
        if (w == null) {
            return base;
        }
        for (int i = 0; i < 6; i++) {
            boolean feetAir = w.getBlockAt(loc).getType().isAir();
            boolean headAir = w.getBlockAt(loc.clone().add(0, 1, 0)).getType().isAir();
            if (feetAir && headAir) {
                return loc;
            }
            loc.add(0, 1, 0);
        }
        return base.clone().add(0, 1, 0);
    }

    private static Player nearestPlayer(Location from, double range) {
        if (from.getWorld() == null) {
            return null;
        }
        Player best = null;
        double bestD = range * range;
        for (Player p : from.getWorld().getPlayers()) {
            if (!p.isValid() || p.isDead()) {
                continue;
            }
            if (CitizensCombatNpcBridge.isCitizensNpcEntity(p)) {
                continue;
            }
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                continue;
            }
            double d = p.getLocation().distanceSquared(from);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private void face(LivingEntity pet, Location point) {
        Location eye = pet.getEyeLocation();
        Vector to = point.toVector().subtract(eye.toVector());
        if (to.lengthSquared() < 0.0001) {
            return;
        }
        Location L = eye.clone();
        L.setDirection(to);
        pet.setRotation(L.getYaw(), L.getPitch());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcDeath(EntityDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (living instanceof Player) {
            return;
        }
        Integer reward = readNpcRewardPoints(living);
        if (reward == null) {
            return;
        }
        if (isDpmrFakeCombatNpc(living)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        grantFakeNpcKillRewards(living, reward);
    }

    /**
     * PNJ Citizens = entite Player : pas d'EntityDeathEvent. Il faut detruire le NPC tout de suite (handle
     * enregistre), sinon Citizens / vanilla tp au spawn monde avant le tick suivant.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFakeNpcPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (!isDpmrFakeCombatNpc(p)) {
            return;
        }
        Integer reward = readNpcRewardPoints(p);
        if (reward == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        grantFakeNpcKillRewards(p, reward);
        Object h = citizensCombatNpcHandles.remove(p.getUniqueId());
        if (h != null) {
            CitizensCombatNpcBridge.destroyNpc(h);
        } else {
            CitizensCombatNpcBridge.destroyNpcForEntity(p);
        }
    }

    /** Filet si le PNJ a quand meme ete renvoye au spawn monde avant destruction. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFakeNpcRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (!isDpmrFakeCombatNpc(p)) {
            return;
        }
        Object h = citizensCombatNpcHandles.remove(p.getUniqueId());
        if (h != null) {
            CitizensCombatNpcBridge.destroyNpc(h);
        } else {
            CitizensCombatNpcBridge.destroyNpcForEntity(p);
        }
    }

    private void grantFakeNpcKillRewards(LivingEntity living, int reward) {
        if (isDpmrFakeCombatNpc(living)) {
            int ingots = ThreadLocalRandom.current().nextInt(1, 3);
            World dw = living.getWorld();
            if (dw != null) {
                dw.dropItemNaturally(living.getLocation(), new ItemStack(Material.GOLD_INGOT, ingots));
            }
        }
        Player killer = resolveNpcKiller(living);
        if (killer == null) {
            return;
        }
        int pts = Math.max(1, reward);
        int gained = pointsManager.addKillRewardPoints(killer.getUniqueId(), pts);
        pointsManager.addKill(killer.getUniqueId());
        pointsManager.saveAsync();
        killer.sendActionBar(Component.text("+" + gained + " pts", NamedTextColor.GOLD));
    }

    /**
     * {@link LivingEntity#getKiller()} est souvent null sur ArmorStand (coups, projectiles DPMR) : on lit la derniere cause.
     */
    private static Player resolveNpcKiller(LivingEntity dead) {
        Player killer = dead.getKiller();
        if (killer != null) {
            return killer;
        }
        EntityDamageEvent last = dead.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent by)) {
            return null;
        }
        Entity damager = by.getDamager();
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFakeNpcManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand stand = event.getRightClicked();
        if (!isDpmrFakeCombatNpc(stand)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFakeNpcInteract(PlayerInteractAtEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof ArmorStand stand)) {
            return;
        }
        if (!isDpmrFakeCombatNpc(stand)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFakeNpcEntityInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity le) || !isDpmrFakeCombatNpc(le)) {
            return;
        }
        event.setCancelled(true);
    }
}
