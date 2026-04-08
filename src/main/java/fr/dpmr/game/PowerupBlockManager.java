package fr.dpmr.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Colored blocks (or small ItemDisplay markers) that grant a temporary buff when shot.
 * Markers glow, optionally spin/bob, and are smaller than a full block.
 */
public final class PowerupBlockManager implements Listener {

    public enum Kind {
        RAPID_FIRE,
        INVULNERABILITY,
        KILL_COINS,
        BULLET_SHIELD,
        STEALTH
    }

    public enum MountStyle {
        FLOAT,
        WALL
    }

    public static final class MarkerDefinition {
        public final String id;
        public final String worldName;
        public final double x;
        public final double y;
        public final double z;
        public final Kind kind;
        public final MountStyle mount;
        /** Non-null for {@link MountStyle#WALL}. */
        public final BlockFace wallFace;

        public MarkerDefinition(String id, String worldName, double x, double y, double z,
                                Kind kind, MountStyle mount, BlockFace wallFace) {
            this.id = id;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
            this.mount = mount;
            this.wallFace = wallFace != null ? wallFace : BlockFace.SELF;
        }

        public Location location(World world) {
            return new Location(world, x, y, z);
        }
    }

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private Predicate<Player> mainHandIsDpmrWeapon = p -> false;
    private boolean enabled = true;

    private final EnumMap<Kind, Material> materials = new EnumMap<>(Kind.class);
    private final EnumMap<Kind, Integer> durationSeconds = new EnumMap<>(Kind.class);
    private double rapidCooldownMultiplier = 0.2;
    private double killRewardMultiplier = 2.0;

    private final Map<UUID, Long> invulnerableUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> bulletShieldUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rapidFireUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> killCoinBonusUntil = new ConcurrentHashMap<>();

    private NamespacedKey keyMarkerKind;
    private NamespacedKey keyMarkerEntry;

    private boolean displaysEnabled = true;
    private float displayScale = 0.38f;
    private boolean displayGlow = true;
    private boolean displayParticleAura = true;
    private double spinDegreesPerTick = 3.0;
    private double bobAmplitude = 0.06;
    private long markerAnimTick;

    private final Map<String, MarkerDefinition> markerDefinitions = new LinkedHashMap<>();
    private final Map<String, UUID> entityByMarkerId = new ConcurrentHashMap<>();
    /** Base Y for bob animation (FLOAT). */
    private final Map<String, Double> floatBaseY = new ConcurrentHashMap<>();

    private org.bukkit.scheduler.BukkitTask animTask;

    public PowerupBlockManager(org.bukkit.plugin.java.JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyMarkerKind = new NamespacedKey(plugin, "powerup_kind");
        this.keyMarkerEntry = new NamespacedKey(plugin, "powerup_entry");
        reloadFromConfig();
    }

    public void setMainHandWeaponTest(Predicate<Player> test) {
        this.mainHandIsDpmrWeapon = test != null ? test : p -> false;
    }

    /**
     * Call after {@code registerEvents} so chunk/world listeners are active.
     */
    public void startMarkers() {
        shutdownMarkers();
        loadMarkerStorage();
        spawnAllInLoadedWorlds();
        startAnimTask();
    }

    public void shutdownMarkers() {
        if (animTask != null) {
            animTask.cancel();
            animTask = null;
        }
        for (UUID id : entityByMarkerId.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        entityByMarkerId.clear();
        floatBaseY.clear();
    }

    public void reloadFromConfig() {
        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("bonus-powerup-blocks.enabled", true);
        materials.clear();
        durationSeconds.clear();
        putKind(cfg, Kind.RAPID_FIRE, "rapid-fire", Material.BLUE_CONCRETE);
        putKind(cfg, Kind.INVULNERABILITY, "invulnerability", Material.GRAY_CONCRETE);
        putKind(cfg, Kind.KILL_COINS, "kill-coins", Material.YELLOW_CONCRETE);
        putKind(cfg, Kind.BULLET_SHIELD, "bullet-shield", Material.PURPLE_CONCRETE);
        putKind(cfg, Kind.STEALTH, "stealth", Material.CYAN_CONCRETE);
        rapidCooldownMultiplier = cfg.getDouble("bonus-powerup-blocks.rapid-fire.cooldown-multiplier", 0.2);
        rapidCooldownMultiplier = Math.max(0.05, Math.min(1.0, rapidCooldownMultiplier));
        killRewardMultiplier = Math.max(1.0, cfg.getDouble("bonus-powerup-blocks.kill-coins.reward-multiplier", 2.0));

        displaysEnabled = cfg.getBoolean("bonus-powerup-blocks.displays.enabled", true);
        displayScale = (float) Math.max(0.12, Math.min(1.2, cfg.getDouble("bonus-powerup-blocks.displays.scale", 0.38)));
        displayGlow = cfg.getBoolean("bonus-powerup-blocks.displays.glow", true);
        displayParticleAura = cfg.getBoolean("bonus-powerup-blocks.displays.particle-aura", true);
        spinDegreesPerTick = cfg.getDouble("bonus-powerup-blocks.displays.spin-degrees-per-tick", 3.0);
        bobAmplitude = Math.max(0, cfg.getDouble("bonus-powerup-blocks.displays.bob-amplitude", 0.06));

        if (plugin.isEnabled()) {
            shutdownMarkers();
            markerDefinitions.clear();
            loadMarkerStorage();
            spawnAllInLoadedWorlds();
            startAnimTask();
        }
    }

    public Map<String, MarkerDefinition> markerDefinitionsView() {
        return Map.copyOf(markerDefinitions);
    }

    public void addMarker(MarkerDefinition def) {
        markerDefinitions.put(def.id, def);
        saveMarkerStorage();
        trySpawn(def);
    }

    public boolean removeMarkerById(String id) {
        MarkerDefinition removed = markerDefinitions.remove(id);
        if (removed == null) {
            return false;
        }
        UUID ent = entityByMarkerId.remove(id);
        if (ent != null) {
            Entity e = Bukkit.getEntity(ent);
            if (e != null) {
                e.remove();
            }
        }
        floatBaseY.remove(id);
        saveMarkerStorage();
        return true;
    }

    /**
     * Remove marker the player is looking at (ray trace).
     */
    public boolean removeMarkerPlayerLooksAt(Player player, double reach) {
        RayTraceResult rt = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                reach,
                0.35,
                e -> markerKind(e) != null);
        return rt != null && rt.getHitEntity() != null && removeMarkerById(markerEntryId(rt.getHitEntity()));
    }

    public Kind parseKindString(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Kind.valueOf(norm);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static MountStyle parseMount(String s) {
        if (s == null) {
            return MountStyle.FLOAT;
        }
        String u = s.toUpperCase(Locale.ROOT);
        if ("WALL".equals(u)) {
            return MountStyle.WALL;
        }
        return MountStyle.FLOAT;
    }

    private static BlockFace parseBlockFace(String name) {
        if (name == null) {
            return null;
        }
        try {
            return BlockFace.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private File markerFile() {
        return new File(plugin.getDataFolder(), "powerup-markers.yml");
    }

    private void loadMarkerStorage() {
        markerDefinitions.clear();
        File f = markerFile();
        if (!f.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection markers = yaml.getConfigurationSection("markers");
        if (markers == null) {
            return;
        }
        for (String id : markers.getKeys(false)) {
            ConfigurationSection one = markers.getConfigurationSection(id);
            if (one == null) {
                continue;
            }
            String world = one.getString("world", "");
            if (world.isEmpty()) {
                continue;
            }
            Kind kind = parseKindString(one.getString("kind", ""));
            if (kind == null) {
                continue;
            }
            double x = one.getDouble("x");
            double y = one.getDouble("y");
            double z = one.getDouble("z");
            MountStyle mount = parseMount(one.getString("mount", "FLOAT"));
            BlockFace face = BlockFace.SELF;
            if (mount == MountStyle.WALL) {
                BlockFace p = parseBlockFace(one.getString("face", "NORTH"));
                if (p != null) {
                    face = p;
                }
            }
            markerDefinitions.put(id, new MarkerDefinition(id, world, x, y, z, kind, mount, face));
        }
    }

    public void saveMarkerStorage() {
        File f = markerFile();
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("markers");
        for (MarkerDefinition def : markerDefinitions.values()) {
            ConfigurationSection s = root.createSection(def.id);
            s.set("world", def.worldName);
            s.set("x", def.x);
            s.set("y", def.y);
            s.set("z", def.z);
            s.set("kind", def.kind.name());
            s.set("mount", def.mount.name());
            if (def.mount == MountStyle.WALL && def.wallFace != BlockFace.SELF) {
                s.set("face", def.wallFace.name());
            }
        }
        try {
            yaml.save(f);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save powerup-markers.yml", e);
        }
    }

    private void spawnAllInLoadedWorlds() {
        if (!displaysEnabled) {
            return;
        }
        for (MarkerDefinition def : markerDefinitions.values()) {
            trySpawn(def);
        }
    }

    private void startAnimTask() {
        if (animTask != null) {
            animTask.cancel();
            animTask = null;
        }
        if (!displaysEnabled || !plugin.isEnabled()) {
            return;
        }
        animTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            markerAnimTick++;
            animateMarkers();
        }, 1L, 1L);
    }

    private void animateMarkers() {
        if (!displaysEnabled) {
            return;
        }
        for (Map.Entry<String, UUID> en : entityByMarkerId.entrySet()) {
            String mid = en.getKey();
            Entity e = Bukkit.getEntity(en.getValue());
            if (!(e instanceof ItemDisplay disp) || !e.isValid()) {
                continue;
            }
            MarkerDefinition def = markerDefinitions.get(mid);
            if (def == null) {
                continue;
            }
            Location loc = disp.getLocation();
            if (def.mount == MountStyle.FLOAT) {
                double base = floatBaseY.getOrDefault(mid, loc.getY());
                float yaw = (float) ((markerAnimTick * spinDegreesPerTick) % 360.0);
                double bob = bobAmplitude > 0 ? Math.sin(markerAnimTick * 0.08) * bobAmplitude : 0;
                loc.setY(base + bob);
                disp.teleport(loc);
                disp.setRotation(yaw, 0f);
                if (displayParticleAura && markerAnimTick % 8 == 0) {
                    loc.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, loc, 3, 0.12, 0.12, 0.12, 0);
                }
            } else if (displayParticleAura && markerAnimTick % 10 == 0) {
                loc.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, loc, 1, 0.08, 0.08, 0.08, 0.002);
            }
        }
    }

    private void trySpawn(MarkerDefinition def) {
        if (!displaysEnabled || !enabled) {
            return;
        }
        World world = Bukkit.getWorld(def.worldName);
        if (world == null) {
            return;
        }
        Location loc = def.location(world);
        if (!loc.isChunkLoaded()) {
            return;
        }
        UUID existing = entityByMarkerId.get(def.id);
        if (existing != null) {
            Entity old = Bukkit.getEntity(existing);
            if (old != null && old.isValid()) {
                return;
            }
            entityByMarkerId.remove(def.id);
        }
        Material mat = materials.getOrDefault(def.kind, Material.STONE);
        ItemStack icon = new ItemStack(mat);
        icon.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        icon.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        ItemDisplay disp = world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(icon);
            // FIXED so FLOAT markers can spin; WALL uses facing from block normal.
            d.setBillboard(ItemDisplay.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(96f);
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setGravity(false);
            d.setSilent(true);
            if (displayGlow) {
                d.setGlowing(true);
            }
            float s = displayScale;
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new Quaternionf(),
                    new Vector3f(s, s, s),
                    new Quaternionf()));
            if (def.mount == MountStyle.WALL) {
                applyWallRotation(d, def.wallFace);
            } else {
                d.setRotation(0f, 0f);
            }
            var pdc = d.getPersistentDataContainer();
            pdc.set(keyMarkerKind, PersistentDataType.STRING, def.kind.name());
            pdc.set(keyMarkerEntry, PersistentDataType.STRING, def.id);
        });
        entityByMarkerId.put(def.id, disp.getUniqueId());
        if (def.mount == MountStyle.FLOAT) {
            floatBaseY.put(def.id, loc.getY());
        }
    }

    private static void applyWallRotation(ItemDisplay disp, BlockFace wallFace) {
        Vector n = wallFace.getDirection().normalize().multiply(-1);
        double yaw = Math.toDegrees(Math.atan2(-n.getX(), n.getZ()));
        double hyp = Math.sqrt(n.getX() * n.getX() + n.getZ() * n.getZ());
        double pitch = Math.toDegrees(Math.atan2(hyp, n.getY())) - 90.0;
        disp.setRotation((float) yaw, (float) pitch);
    }

    /**
     * {@link MountStyle#FLOAT}: orb in front of the player (mid-air).
     * {@link MountStyle#WALL}: looks at a solid block and places the orb slightly in front of that face.
     */
    public MarkerDefinition createDefinitionFromPlayerLook(Player player, Kind kind, MountStyle requested) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        if (requested == MountStyle.FLOAT) {
            Location pos = eye.clone().add(dir.multiply(2.8));
            String id = UUID.randomUUID().toString();
            return new MarkerDefinition(id, world.getName(), pos.getX(), pos.getY(), pos.getZ(), kind,
                    MountStyle.FLOAT, BlockFace.SELF);
        }
        RayTraceResult blockRt = world.rayTraceBlocks(eye, dir, 12, FluidCollisionMode.NEVER, true);
        if (blockRt == null || blockRt.getHitBlock() == null || blockRt.getHitBlockFace() == null
                || blockRt.getHitPosition() == null) {
            return null;
        }
        Location pos = blockRt.getHitPosition().toLocation(world);
        BlockFace face = blockRt.getHitBlockFace();
        pos.add(face.getDirection().multiply(0.22));
        String id = UUID.randomUUID().toString();
        return new MarkerDefinition(id, world.getName(), pos.getX(), pos.getY(), pos.getZ(), kind,
                MountStyle.WALL, face);
    }

    private String markerEntryId(Entity e) {
        if (e == null) {
            return "";
        }
        String s = e.getPersistentDataContainer().get(keyMarkerEntry, PersistentDataType.STRING);
        return s != null ? s : "";
    }

    public Kind markerKind(Entity e) {
        if (e == null) {
            return null;
        }
        String s = e.getPersistentDataContainer().get(keyMarkerKind, PersistentDataType.STRING);
        if (s == null) {
            return null;
        }
        try {
            return Kind.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public double killRewardBonusMultiplier(UUID uuid) {
        if (!enabled) {
            return 1.0;
        }
        Long until = killCoinBonusUntil.get(uuid);
        if (until == null || System.currentTimeMillis() >= until) {
            killCoinBonusUntil.remove(uuid);
            return 1.0;
        }
        return killRewardMultiplier;
    }

    public double rapidFireCooldownMultiplier(Player player) {
        if (!enabled || player == null) {
            return 1.0;
        }
        Long until = rapidFireUntil.get(player.getUniqueId());
        if (until == null || System.currentTimeMillis() >= until) {
            rapidFireUntil.remove(player.getUniqueId());
            return 1.0;
        }
        return rapidCooldownMultiplier;
    }

    /**
     * Hitscan: closest entity (living or marker) vs block; power-up triggers if marker or configured block wins.
     */
    public void tryHitscanImpact(Player player, Location eye, Vector direction, double range) {
        if (!enabled || player == null || eye == null || eye.getWorld() == null) {
            return;
        }
        World world = eye.getWorld();
        Vector dir = direction.clone().normalize();
        Predicate<Entity> entityPred = e -> {
            if (e.equals(player)) {
                return false;
            }
            if (e instanceof LivingEntity) {
                return true;
            }
            return markerKind(e) != null;
        };
        RayTraceResult entityHit = world.rayTraceEntities(eye, dir, range, 0.55, entityPred);
        RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double distEntity = Double.MAX_VALUE;
        double distBlock = Double.MAX_VALUE;
        if (entityHit != null && entityHit.getHitPosition() != null) {
            distEntity = eye.toVector().distance(entityHit.getHitPosition());
        }
        if (blockHit != null && blockHit.getHitBlock() != null && blockHit.getHitPosition() != null) {
            distBlock = eye.toVector().distance(blockHit.getHitPosition());
        }
        if (entityHit != null && entityHit.getHitEntity() != null) {
            Entity ent = entityHit.getHitEntity();
            if (ent instanceof LivingEntity && ent != player) {
                if (distEntity <= distBlock || blockHit == null || blockHit.getHitBlock() == null) {
                    return;
                }
            }
            Kind mk = markerKind(ent);
            if (mk != null && (blockHit == null || blockHit.getHitBlock() == null || distEntity < distBlock)) {
                tryActivate(player, mk, ent.getLocation(), 1.0);
                return;
            }
        }

        if (distBlock >= distEntity || blockHit == null || blockHit.getHitBlock() == null) {
            return;
        }
        tryActivate(player, blockHit.getHitBlock().getType(), blockHit.getHitBlock().getLocation());
    }

    public boolean tryProjectilePowerupBlock(Player shooter, Block block) {
        if (!enabled || shooter == null || block == null) {
            return false;
        }
        return tryActivate(shooter, block.getType(), block.getLocation());
    }

    public boolean tryProjectilePowerupEntity(Player shooter, Entity hit) {
        if (!enabled || shooter == null || hit == null) {
            return false;
        }
        Kind k = markerKind(hit);
        if (k == null) {
            return false;
        }
        return tryActivate(shooter, k, hit.getLocation(), 1.0);
    }

    /**
     * Même effet que tirer sur un bloc power-up (ex. carte de poker utilisée au clic droit).
     *
     * @param durationMultiplier multiplicateur sur la durée configurée (typiquement 0.9–1.3 selon la hauteur de carte)
     */
    public boolean applyPowerupFromItem(Player player, Kind kind, double durationMultiplier) {
        if (!enabled || player == null || kind == null) {
            return false;
        }
        double mult = Math.max(0.25, Math.min(3.0, durationMultiplier));
        return tryActivate(player, kind, player.getLocation(), mult);
    }

    private boolean tryActivate(Player player, Material type, Location blockLocation) {
        Kind kind = null;
        for (Map.Entry<Kind, Material> e : materials.entrySet()) {
            if (e.getValue() == type) {
                kind = e.getKey();
                break;
            }
        }
        if (kind == null) {
            return false;
        }
        return tryActivate(player, kind, blockLocation, 1.0);
    }

    private boolean tryActivate(Player player, Kind kind, Location effectLocation, double durationMultiplier) {
        long now = System.currentTimeMillis();
        int baseSec = durationSeconds.getOrDefault(kind, 10);
        int sec = Math.max(1, (int) Math.round(baseSec * durationMultiplier));
        long until = now + sec * 1000L;
        World w = effectLocation.getWorld();
        switch (kind) {
            case RAPID_FIRE -> {
                rapidFireUntil.put(player.getUniqueId(), until);
                int approxFactor = Math.max(2, (int) Math.round(1.0 / rapidCooldownMultiplier));
                player.sendMessage(Component.text(
                        "Rapid fire! You shoot about " + approxFactor + "× faster for " + sec + " seconds.",
                        NamedTextColor.AQUA));
            }
            case INVULNERABILITY -> {
                invulnerableUntil.put(player.getUniqueId(), until);
                player.sendMessage(Component.text(
                        "Invulnerability! You take no damage for " + sec + " seconds.",
                        NamedTextColor.GRAY));
            }
            case KILL_COINS -> {
                killCoinBonusUntil.put(player.getUniqueId(), until);
                player.sendMessage(Component.text(
                        "Bounty surge! Kill rewards are multiplied for " + sec + " seconds.",
                        NamedTextColor.YELLOW));
            }
            case BULLET_SHIELD -> {
                bulletShieldUntil.put(player.getUniqueId(), until);
                player.sendMessage(Component.text(
                        "Bullet shield! Gunfire and projectiles from other players cannot hurt you for " + sec + " seconds.",
                        NamedTextColor.LIGHT_PURPLE));
            }
            case STEALTH -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, sec * 20, 0, false, true, true));
                player.sendMessage(Component.text(
                        "Stealth! You are semi-invisible (potion particles visible) for " + sec + " seconds.",
                        NamedTextColor.DARK_AQUA));
            }
        }
        Location center = effectLocation.clone().add(0, 0.15, 0);
        if (w != null) {
            w.playSound(center, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            w.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, center, 18, 0.35, 0.35, 0.35, 0.02);
            w.spawnParticle(org.bukkit.Particle.FIREWORK, center, 6, 0.2, 0.2, 0.2, 0.01);
        }
        return true;
    }

    private void putKind(org.bukkit.configuration.file.FileConfiguration cfg, Kind kind, String path, Material defMat) {
        String raw = cfg.getString("bonus-powerup-blocks." + path + ".material", defMat.name());
        Material m = Material.matchMaterial(raw != null ? raw : defMat.name());
        if (m == null || !m.isBlock()) {
            m = defMat;
        }
        materials.put(kind, m);
        int sec = cfg.getInt("bonus-powerup-blocks." + path + ".duration-seconds", switch (kind) {
            case RAPID_FIRE -> 30;
            case INVULNERABILITY -> 10;
            case KILL_COINS -> 60;
            case BULLET_SHIELD -> 30;
            case STEALTH -> 20;
        });
        durationSeconds.put(kind, Math.max(1, sec));
    }

    private boolean shieldActive(UUID uuid) {
        Long until = bulletShieldUntil.get(uuid);
        if (until == null || System.currentTimeMillis() >= until) {
            bulletShieldUntil.remove(uuid);
            return false;
        }
        return true;
    }

    private boolean isGunlikeDamage(Player victim, Entity damager) {
        if (damager instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player shooter && !shooter.getUniqueId().equals(victim.getUniqueId())) {
                return true;
            }
            return false;
        }
        if (damager instanceof Player attacker && !attacker.getUniqueId().equals(victim.getUniqueId())) {
            return mainHandIsDpmrWeapon.test(attacker);
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvulnerability(EntityDamageEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = invulnerableUntil.get(player.getUniqueId());
        if (until == null || System.currentTimeMillis() >= until) {
            invulnerableUntil.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBulletShield(EntityDamageByEntityEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!shieldActive(victim.getUniqueId())) {
            return;
        }
        if (!isGunlikeDamage(victim, event.getDamager())) {
            return;
        }
        event.setCancelled(true);
        victim.playSound(victim.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.35f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        invulnerableUntil.remove(id);
        bulletShieldUntil.remove(id);
        rapidFireUntil.remove(id);
        killCoinBonusUntil.remove(id);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!displaysEnabled || !enabled) {
            return;
        }
        Chunk chunk = event.getChunk();
        for (MarkerDefinition def : markerDefinitions.values()) {
            if (!def.worldName.equals(chunk.getWorld().getName())) {
                continue;
            }
            int bx = (int) Math.floor(def.x) >> 4;
            int bz = (int) Math.floor(def.z) >> 4;
            if (chunk.getX() == bx && chunk.getZ() == bz) {
                trySpawn(def);
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!displaysEnabled || !enabled) {
            return;
        }
        String name = event.getWorld().getName();
        for (MarkerDefinition def : markerDefinitions.values()) {
            if (def.worldName.equals(name)) {
                trySpawn(def);
            }
        }
    }
}
