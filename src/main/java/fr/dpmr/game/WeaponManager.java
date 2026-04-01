package fr.dpmr.game;

import fr.dpmr.cosmetics.CosmeticProfile;
import fr.dpmr.i18n.GameLocale;
import fr.dpmr.i18n.I18n;
import fr.dpmr.i18n.PlayerLanguageStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
public class WeaponManager implements Listener {

    private static final float VANILLA_WALK_SPEED = 0.2f;
    private static final long GRAPPLE_COOLDOWN_MS = 15_000L;
    private static final String PROJ_KIND_CHESTNUT = "chestnut";
    private static final String PROJ_KIND_BOMB = "bomb";
    private static final String PROJ_KIND_ZAP = "zap";
    private static final String PROJ_KIND_GAS = "gas";
    private static final String PROJ_KIND_SERUM = "serum";
    private static final String PROJ_KIND_HEAL_DART = "heal_dart";
    private static final String PROJ_KIND_CLIO3 = "clio3";
    private static final String PROJ_KIND_MORTAR = "mortar";
    private static final String PROJ_KIND_ROCKET = "rocket";
    /** Gravite snowball (blocs/tick^2) pour calcul trajectoire en cloche. */
    private static final double MORTAR_PROJECTILE_GRAVITY = 0.03;
    private static final long TURRET_DEPLOY_COOLDOWN_MS = 2800L;
    private static final double TURRET_MAX_PLACE_RANGE = 5.25;

    private record Clio3Part(double lx, double ly, double lz, Material mat) {
    }

    /**
     * Offsets locaux (lx = droite, ly = haut, lz = avant) pour une silhouette type petite citadine.
     */
    private static final List<Clio3Part> CLIO3_PARTS = List.of(
            new Clio3Part(0, 0.02, 0.54, Material.STONE),
            new Clio3Part(-0.24, 0.07, 0.30, Material.STONE_BRICKS),
            new Clio3Part(0.24, 0.07, 0.30, Material.STONE_BRICKS),
            new Clio3Part(0, 0.12, 0.14, Material.SMOOTH_STONE),
            new Clio3Part(-0.20, 0.22, -0.06, Material.COBBLESTONE),
            new Clio3Part(0.20, 0.22, -0.06, Material.COBBLESTONE),
            new Clio3Part(0, 0.30, -0.14, Material.STONE),
            new Clio3Part(-0.24, 0.07, -0.40, Material.STONE_BRICKS),
            new Clio3Part(0.24, 0.07, -0.40, Material.STONE_BRICKS),
            new Clio3Part(0, 0.02, -0.56, Material.STONE),
            new Clio3Part(0, 0.06, -0.02, Material.STONE_SLAB),
            new Clio3Part(-0.14, 0.18, 0.08, Material.ANDESITE),
            new Clio3Part(0.14, 0.18, 0.08, Material.ANDESITE),
            new Clio3Part(0, 0.14, -0.32, Material.COBBLESTONE)
    );

    private static final class Clio3VisualSession {
        final List<BlockDisplay> displays;
        BukkitTask task;

        Clio3VisualSession(List<BlockDisplay> displays, BukkitTask task) {
            this.displays = displays;
            this.task = task;
        }
    }

    private final Map<UUID, Clio3VisualSession> clio3VisualByProjectile = new HashMap<>();
    private final Map<UUID, BukkitTask> rocketHomingTasks = new HashMap<>();

    /** Seuil vertical (pieds → tête) pour compter un headshot hitscan / projectile. */
    private static final double HEADSHOT_HEIGHT_RATIO = 0.78;
    private static final double HEADSHOT_DAMAGE_MULTIPLIER = 1.58;

    private final JavaPlugin plugin;
    private final fr.dpmr.cosmetics.CosmeticsManager cosmeticsManager;
    private final fr.dpmr.zone.ZoneManager zoneManager;
    private final fr.dpmr.armor.ArmorManager armorManager;
    private final PlayerLanguageStore languageStore;
    private final PowerupBlockManager powerupBlockManager;
    private final NamespacedKey keyWeaponId;
    private final NamespacedKey keyProjDamage;
    private final NamespacedKey keyProjOwner;
    private final NamespacedKey keyProjKind;
    private final NamespacedKey keyProjWeaponId;
    private final NamespacedKey keyUpgradePayload;
    private final NamespacedKey keyBombBounceLeft;
    private final NamespacedKey keyZapBounceLeft;
    private final NamespacedKey keyBombPayload;
    private final NamespacedKey keyGasExpireAt;
    private final NamespacedKey keyGasMini;
    private final NamespacedKey keySerumWeaponId;
    private final NamespacedKey keyCombatDrone;
    /** Identifiant unique par item d'arme : munitions / reload independants entre deux armes du meme type. */
    private final NamespacedKey keyWeaponInstance;
    /** ArmorStand siege de tourelle portative : valeur = UUID du proprietaire. */
    private final NamespacedKey keyTurretOwner;
    /** Fleche ajoutee temporairement pour arc / sniper charge (sans consommer de vraies fleches). */
    private final NamespacedKey keyVirtArrow;
    /** Multiplicateur dégâts pour certains PNJ (sentinelles zone war). */
    private final NamespacedKey keyNpcShotScale;
    /** ID cosmétique ({@link CosmeticProfile#id()}) pour skins d'arme (Thompson). */
    private final NamespacedKey keyWeaponCosmetic;

    private static final class DeployedTurret {
        final List<Block> blocks;
        final ArmorStand seat;

        DeployedTurret(List<Block> blocks, ArmorStand seat) {
            this.blocks = List.copyOf(blocks);
            this.seat = seat;
        }
    }

    private final Map<UUID, DeployedTurret> deployedTurretByOwner = new HashMap<>();
    private final Map<UUID, Long> lastTurretDeployMs = new HashMap<>();

    private final Map<UUID, Map<String, Integer>> clipAmmo = new HashMap<>();
    private final Map<UUID, Long> lastShotTimeMs = new HashMap<>();
    /** Recul cumulatif (decroit entre les tirs) pour dispersion supplementaire */
    private final Map<UUID, Double> recoilMeter = new HashMap<>();
    /** M134 : chaleur 0..1, augmente a chaque tir, refroidit hors tir. */
    private final Map<UUID, Double> minigunHeat = new HashMap<>();
    private final Map<UUID, Long> minigunHeatLastMs = new HashMap<>();
    private static final double MINIGUN_HEAT_PER_SHOT = 0.028;
    /** Decroissance exponentielle par seconde (hors tir). */
    private static final double MINIGUN_HEAT_DECAY_PER_SEC = 0.42;
    /** Dispersion supplementaire (degres) a chaleur max. */
    private static final double MINIGUN_HEAT_SPREAD_DEG = 6.5;
    private final Map<UUID, ReloadSession> reloadSessions = new HashMap<>();
    private final Map<UUID, Integer> techShotCount = new HashMap<>();
    private final Map<UUID, BukkitTask> nuclearTasks = new HashMap<>();
    /** Flaque d'essence + upgrades J-20 au moment du depot */
    private static final class GasPuddleEntry {
        final long expireAtMs;
        final JerrycanUpgradeState jerry;

        GasPuddleEntry(long expireAtMs, JerrycanUpgradeState jerry) {
            this.expireAtMs = expireAtMs;
            this.jerry = jerry == null || jerry.isEmpty() ? JerrycanUpgradeState.NONE : jerry;
        }
    }

    private final Map<String, GasPuddleEntry> gasolinePuddles = new HashMap<>();

    private static final class ReloadSession {
        final String weaponId;
        final String instanceId;
        final int totalTicks;
        int elapsedTicks;
        BukkitTask task;

        ReloadSession(String weaponId, String instanceId, int totalTicks) {
            this.weaponId = weaponId;
            this.instanceId = instanceId;
            this.totalTicks = Math.max(1, totalTicks);
            this.elapsedTicks = 0;
        }
    }

    public WeaponManager(JavaPlugin plugin, fr.dpmr.cosmetics.CosmeticsManager cosmeticsManager, fr.dpmr.zone.ZoneManager zoneManager,
                         fr.dpmr.armor.ArmorManager armorManager, PlayerLanguageStore languageStore,
                         PowerupBlockManager powerupBlockManager) {
        this.plugin = plugin;
        this.cosmeticsManager = cosmeticsManager;
        this.zoneManager = zoneManager;
        this.armorManager = armorManager;
        this.languageStore = languageStore;
        this.powerupBlockManager = powerupBlockManager;
        this.keyWeaponId = new NamespacedKey(plugin, "dpmr_weapon_id");
        this.keyProjDamage = new NamespacedKey(plugin, "dpmr_proj_dmg");
        this.keyProjOwner = new NamespacedKey(plugin, "dpmr_proj_owner");
        this.keyProjKind = new NamespacedKey(plugin, "dpmr_proj_kind");
        this.keyProjWeaponId = new NamespacedKey(plugin, "dpmr_proj_weapon");
        this.keyUpgradePayload = new NamespacedKey(plugin, "dpmr_proj_upg");
        this.keyBombBounceLeft = new NamespacedKey(plugin, "dpmr_bomb_bnc");
        this.keyZapBounceLeft = new NamespacedKey(plugin, "dpmr_zap_bnc");
        this.keyBombPayload = new NamespacedKey(plugin, "dpmr_bomb_pl");
        this.keyGasExpireAt = new NamespacedKey(plugin, "dpmr_gas_exp");
        this.keyGasMini = new NamespacedKey(plugin, "dpmr_gas_mini");
        this.keySerumWeaponId = new NamespacedKey(plugin, "dpmr_serum_w");
        this.keyCombatDrone = new NamespacedKey(plugin, "dpmr_combat_drone");
        this.keyWeaponInstance = new NamespacedKey(plugin, "dpmr_weapon_inst");
        this.keyTurretOwner = new NamespacedKey(plugin, "dpmr_turret_owner");
        this.keyVirtArrow = new NamespacedKey(plugin, "dpmr_virt_arrow");
        this.keyNpcShotScale = new NamespacedKey(plugin, "dpmr_npc_shot_scale");
        this.keyWeaponCosmetic = new NamespacedKey(plugin, "weapon_cosmetic");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickScopeAndHeavyEffects, 8L, 8L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickGasolineFx, 10L, 10L);
    }

    /** Segment œil tireur → impact : si ça traverse une zone safe, pas de dégâts d'arme. */
    private boolean weaponShotBlockedBySafeLine(LivingEntity shooter, Vector hitWorldPos, World world) {
        if (zoneManager == null || shooter == null || hitWorldPos == null || world == null) {
            return false;
        }
        Location eye = shooter.getEyeLocation();
        if (!world.equals(eye.getWorld())) {
            return false;
        }
        return zoneManager.isWeaponDamageBlockedAlongLine(eye, hitWorldPos.toLocation(world));
    }

    /** Projectile → victime : dégâts annulés si la ligne traverse une zone safe (comme hitscan). */
    private boolean weaponProjectileToVictimBlockedBySafe(Projectile projectile, LivingEntity victim) {
        if (projectile == null || victim == null) {
            return false;
        }
        if (!(projectile.getShooter() instanceof LivingEntity shooter)) {
            return false;
        }
        Vector mid = victim.getLocation().toVector().add(new Vector(0, victim.getHeight() * 0.4, 0));
        return weaponShotBlockedBySafeLine(shooter, mid, victim.getWorld());
    }

    private void dismantleDeployedTurret(UUID ownerId) {
        DeployedTurret dt = deployedTurretByOwner.remove(ownerId);
        if (dt == null) {
            return;
        }
        if (dt.seat != null && dt.seat.isValid()) {
            for (Entity e : new ArrayList<>(dt.seat.getPassengers())) {
                dt.seat.removePassenger(e);
            }
            dt.seat.remove();
        }
        for (Block b : dt.blocks) {
            if (b != null && b.getWorld() != null) {
                b.setType(Material.AIR);
            }
        }
    }

    private boolean isRidingOwnTurret(Player player) {
        DeployedTurret dt = deployedTurretByOwner.get(player.getUniqueId());
        return dt != null && dt.seat != null && dt.seat.isValid()
                && player.getVehicle() != null && player.getVehicle().equals(dt.seat);
    }

    /**
     * Pose la tourelle au sol (bloc + siege invisible), puis met le joueur en passager au tick suivant.
     * {@link #onShoot} evite le tir / la conso de munitions quand on n'est pas deja monte.
     */
    private void tryDeployPortableTurret(Player player, PlayerInteractEvent event) {
        long now = System.currentTimeMillis();
        long lastMs = lastTurretDeployMs.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastMs < TURRET_DEPLOY_COOLDOWN_MS) {
            int leftSec = (int) Math.ceil((TURRET_DEPLOY_COOLDOWN_MS - (now - lastMs)) / 1000.0);
            I18n.actionBar(player, NamedTextColor.RED, "weapon.turret_deploy_cooldown", leftSec);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.75f);
            return;
        }

        Block placeCell = null;
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null && event.getBlockFace() != null) {
            placeCell = event.getClickedBlock().getRelative(event.getBlockFace());
        }
        if (placeCell == null) {
            Location eye = player.getEyeLocation();
            World world = eye.getWorld();
            RayTraceResult rt = world.rayTraceBlocks(eye, eye.getDirection(), TURRET_MAX_PLACE_RANGE,
                    FluidCollisionMode.NEVER, true);
            if (rt != null && rt.getHitBlock() != null && rt.getHitBlockFace() != null) {
                placeCell = rt.getHitBlock().getRelative(rt.getHitBlockFace());
            }
        }
        if (placeCell == null || placeCell.getWorld() == null) {
            I18n.actionBar(player, NamedTextColor.RED, "weapon.turret_deploy_no_space");
            return;
        }
        Vector cellCenter = placeCell.getLocation().add(0.5, 0.5, 0.5).toVector();
        if (player.getEyeLocation().toVector().distanceSquared(cellCenter) > TURRET_MAX_PLACE_RANGE * TURRET_MAX_PLACE_RANGE + 0.04) {
            I18n.actionBar(player, NamedTextColor.RED, "weapon.turret_deploy_no_space");
            return;
        }

        Block below = placeCell.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid()) {
            I18n.actionBar(player, NamedTextColor.RED, "weapon.turret_deploy_no_space");
            return;
        }
        Block air1 = placeCell.getRelative(BlockFace.UP);
        Block air2 = air1.getRelative(BlockFace.UP);
        if (!placeCell.getType().isAir() || !air1.getType().isAir() || !air2.getType().isAir()) {
            I18n.actionBar(player, NamedTextColor.RED, "weapon.turret_deploy_no_space");
            return;
        }

        World world = placeCell.getWorld();
        dismantleDeployedTurret(player.getUniqueId());

        placeCell.setType(Material.IRON_BLOCK);
        List<Block> blocks = new ArrayList<>(2);
        blocks.add(placeCell);

        float yaw = player.getLocation().getYaw();
        Location seatLoc = placeCell.getLocation().add(0.5, 0.92, 0.5);
        seatLoc.setYaw(yaw);
        seatLoc.setPitch(0f);

        ArmorStand seat = world.spawn(seatLoc, ArmorStand.class, stand -> {
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setMarker(true);
            stand.setVisible(false);
            stand.setSmall(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setCollidable(false);
            stand.setCustomNameVisible(false);
            stand.getPersistentDataContainer().set(keyTurretOwner, PersistentDataType.STRING, player.getUniqueId().toString());
        });
        seat.setRotation(yaw, 0f);

        UUID owner = player.getUniqueId();
        deployedTurretByOwner.put(owner, new DeployedTurret(blocks, seat));
        lastTurretDeployMs.put(owner, now);

        I18n.actionBar(player, NamedTextColor.GREEN, "weapon.turret_deployed");
        world.playSound(seatLoc, Sound.BLOCK_METAL_PLACE, 0.85f, 0.95f);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                dismantleDeployedTurret(owner);
                return;
            }
            DeployedTurret dt = deployedTurretByOwner.get(owner);
            if (dt == null || dt.seat == null || !dt.seat.isValid()) {
                return;
            }
            dt.seat.addPassenger(player);
        });
    }

    /**
     * Lunette (sneak) : Lenteur forte (effet vanilla qui modifie aussi le FOV cote client).
     * Armes lourdes : lenteur en main.
     */
    private void tickScopeAndHeavyEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            WeaponProfile w = fromItem(player.getInventory().getItemInMainHand());
            player.setWalkSpeed(VANILLA_WALK_SPEED);
            if (w == null) {
                continue;
            }
            boolean scoped = w.hasScope() && player.isSneaking();
            int amp = -1;
            if (scoped) {
                amp = 3;
            }
            if (w.hasHeavyWeight()) {
                amp = Math.max(amp, w.heavyHoldSlowAmplifier());
            }
            if (amp >= 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, amp, false, false, true));
            }
        }
    }

    private boolean isVirtArrowStack(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ARROW || !stack.hasItemMeta()) {
            return false;
        }
        Byte b = stack.getItemMeta().getPersistentDataContainer().get(keyVirtArrow, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    private boolean inventoryHasUsableArrow(Player player) {
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == Material.ARROW && s.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private void ensureChargeSniperArrow(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (inventoryHasUsableArrow(player)) {
            return;
        }
        ItemStack arrow = new ItemStack(Material.ARROW, 1);
        ItemMeta meta = arrow.getItemMeta();
        meta.getPersistentDataContainer().set(keyVirtArrow, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("DPMR", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
        arrow.setItemMeta(meta);
        player.getInventory().addItem(arrow);
    }

    private void cleanupVirtArrows(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (isVirtArrowStack(s)) {
                player.getInventory().setItem(i, null);
            }
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isVirtArrowStack(off)) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    public ItemStack createWeaponItem(String weaponId) {
        return createWeaponItem(weaponId, GameLocale.EN);
    }

    public ItemStack createWeaponItem(String weaponId, Player player) {
        return createWeaponItem(weaponId, languageStore.get(player), player.getUniqueId());
    }

    public ItemStack createWeaponItem(String weaponId, GameLocale locale) {
        return createWeaponItem(weaponId, locale, null);
    }

    private ItemStack createWeaponItem(String weaponId, GameLocale locale, UUID viewerUuid) {
        WeaponProfile w = parse(weaponId);
        if (w == null) {
            return null;
        }
        ItemStack item = new ItemStack(w.material());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keyWeaponId, PersistentDataType.STRING, w.name());
        stampWeaponCosmeticForNewItem(meta, w, viewerUuid);
        applyCustomModelData(meta, w, viewerUuid);
        item.setItemMeta(meta);
        assignNewWeaponInstanceId(item);
        meta = item.getItemMeta();
        applyWeaponDurabilityOnMeta(meta, w);
        WeaponLoreBuilder.apply(meta, w, WeaponUpgradeState.NONE, BombUpgradeState.NONE, MortarUpgradeState.NONE, RocketUpgradeState.NONE, RevolverUpgradeState.NONE, JerrycanUpgradeState.NONE, locale, null, maxClipForStack(w, item));
        meta.setEnchantmentGlintOverride(w.rarity().glint());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        applyWeaponDurability(item, w);
        return item;
    }

    private static void applyWeaponDurabilityOnMeta(ItemMeta meta, WeaponProfile w) {
        if (!(meta instanceof Damageable d)) {
            return;
        }
        int max = w.rarity().maxWeaponDurability();
        d.setMaxDamage(max);
        if (d.getDamage() > max) {
            d.setDamage(max);
        }
    }

    private void applyWeaponDurability(ItemStack item, WeaponProfile w) {
        if (item == null || w == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        applyWeaponDurabilityOnMeta(meta, w);
        item.setItemMeta(meta);
    }

    private static boolean isWeaponBroken(ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta()) {
            return false;
        }
        ItemMeta m = weapon.getItemMeta();
        if (!(m instanceof Damageable d) || !d.hasMaxDamage()) {
            return false;
        }
        return d.getDamage() >= d.getMaxDamage();
    }

    private void wearWeaponOnShot(Player player, ItemStack weapon, WeaponProfile w) {
        if (weapon == null || w == null || !weapon.hasItemMeta()) {
            return;
        }
        ItemMeta meta = weapon.getItemMeta();
        applyWeaponDurabilityOnMeta(meta, w);
        if (!(meta instanceof Damageable d) || !d.hasMaxDamage()) {
            weapon.setItemMeta(meta);
            return;
        }
        int max = d.getMaxDamage();
        int next = Math.min(max, d.getDamage() + 1);
        d.setDamage(next);
        weapon.setItemMeta(meta);
        player.getInventory().setItemInMainHand(weapon);
        refreshWeaponMeta(weapon, player);
    }

    private void assignNewWeaponInstanceId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || readWeaponId(stack) == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(keyWeaponInstance, PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
    }

    /**
     * Garantit un UUID sur l'item ; migre l'ancienne munition par type ({@link WeaponProfile#name()}) si besoin.
     */
    private String ensureWeaponInstanceId(Player player, ItemStack stack) {
        WeaponProfile w = fromItem(stack);
        if (w == null || player == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String existing = pdc.get(keyWeaponInstance, PersistentDataType.STRING);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String newId = UUID.randomUUID().toString();
        Map<String, Integer> m = clipAmmo.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        int legacy = m.getOrDefault(w.name(), w.clipSize());
        m.put(newId, legacy);
        m.remove(w.name());
        pdc.set(keyWeaponInstance, PersistentDataType.STRING, newId);
        stack.setItemMeta(meta);
        return newId;
    }

    public void refreshWeaponMeta(ItemStack item) {
        refreshWeaponMeta(item, null);
    }

    public void refreshWeaponMeta(ItemStack item, Player viewer) {
        String id = readWeaponId(item);
        if (id == null) {
            return;
        }
        WeaponProfile w = WeaponProfile.fromId(id);
        if (w == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        WeaponUpgradeState st = WeaponUpgradeState.read(item, plugin);
        BombUpgradeState bs = BombUpgradeState.read(item, plugin);
        MortarUpgradeState ms = MortarUpgradeState.read(item, plugin);
        RocketUpgradeState rs = RocketUpgradeState.read(item, plugin);
        RevolverUpgradeState rvs = RevolverUpgradeState.read(item, plugin);
        JerrycanUpgradeState js = JerrycanUpgradeState.read(item, plugin);
        applyCustomModelData(meta, w, viewer != null ? viewer.getUniqueId() : null);
        GameLocale loc = viewer != null ? languageStore.get(viewer) : GameLocale.EN;
        applyWeaponDurabilityOnMeta(meta, w);
        int magCap = maxClipForStack(w, item);
        Integer magCur = viewer != null ? getClip(viewer, w, item) : null;
        WeaponLoreBuilder.apply(meta, w, st, bs, ms, rs, rvs, js, loc, magCur, magCap);
        meta.setEnchantmentGlintOverride(w.rarity().glint());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private void applyCustomModelData(ItemMeta meta, WeaponProfile w, UUID viewerUuid) {
        int cmd = 0;
        if (w == WeaponProfile.THOMPSON) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String wc = pdc.get(keyWeaponCosmetic, PersistentDataType.STRING);
            if (wc != null && !wc.isBlank()) {
                cmd = plugin.getConfig().getInt("cosmetics.weapon-skins." + wc, 0);
            }
            if (cmd <= 0 && viewerUuid != null && cosmeticsManager != null) {
                CosmeticProfile sel = cosmeticsManager.selectedWeaponSkin(viewerUuid, "THOMPSON");
                if (sel != null) {
                    cmd = plugin.getConfig().getInt("cosmetics.weapon-skins." + sel.id(), 0);
                }
            }
            if (cmd <= 0) {
                cmd = plugin.getConfig().getInt("weapons.custom-model-data." + w.name(), 0);
            }
            if (cmd > 0) {
                meta.setCustomModelData(cmd);
            }
            return;
        }
        if (w == WeaponProfile.COUTEAU_COMBAT && viewerUuid != null && cosmeticsManager != null) {
            CosmeticProfile kp = cosmeticsManager.selectedKnifeProfile(viewerUuid);
            if (kp != null) {
                cmd = plugin.getConfig().getInt("cosmetics.knife-skins." + kp.id(), 0);
            }
        }
        if (cmd <= 0) {
            cmd = plugin.getConfig().getInt("weapons.custom-model-data." + w.name(), 0);
        }
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
    }

    private void stampWeaponCosmeticForNewItem(ItemMeta meta, WeaponProfile w, UUID viewerUuid) {
        if (w != WeaponProfile.THOMPSON || viewerUuid == null || cosmeticsManager == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        CosmeticProfile sel = cosmeticsManager.selectedWeaponSkin(viewerUuid, "THOMPSON");
        if (sel == null) {
            pdc.remove(keyWeaponCosmetic);
        } else {
            pdc.set(keyWeaponCosmetic, PersistentDataType.STRING, sel.id());
        }
    }

    /**
     * Applique le skin selectionné en boutique sur toutes les armes de ce type dans l'inventaire (NBT + modele).
     */
    public void syncWeaponSkinFromSelection(Player player, String weaponProfileName) {
        if (player == null || weaponProfileName == null || cosmeticsManager == null) {
            return;
        }
        CosmeticProfile sel = cosmeticsManager.selectedWeaponSkin(player.getUniqueId(), weaponProfileName);
        for (ItemStack stack : player.getInventory().getContents()) {
            applyWeaponSkinPdcToStack(player, stack, weaponProfileName, sel);
        }
        applyWeaponSkinPdcToStack(player, player.getInventory().getItemInOffHand(), weaponProfileName, sel);
    }

    private void applyWeaponSkinPdcToStack(Player player, ItemStack stack, String weaponProfileName, CosmeticProfile sel) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        String wid = readWeaponId(stack);
        if (wid == null || !wid.equals(weaponProfileName)) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (sel == null) {
            pdc.remove(keyWeaponCosmetic);
        } else {
            pdc.set(keyWeaponCosmetic, PersistentDataType.STRING, sel.id());
        }
        stack.setItemMeta(meta);
        refreshWeaponMeta(stack, player);
    }

    /**
     * Reapplique le CustomModelData depuis la config sur toutes les armes DPMR (inventaire + offhand).
     * Utile apres MAJ du config.yml / resource pack : les items deja obtenus recuperent la bonne texture.
     */
    public void refreshDpmrWeaponsInInventory(Player player) {
        if (player == null) {
            return;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            refreshWeaponIfDpmr(stack, player);
        }
        refreshWeaponIfDpmr(player.getInventory().getItemInOffHand(), player);
    }

    private void refreshWeaponIfDpmr(ItemStack stack, Player viewer) {
        if (stack == null || stack.getType().isAir() || readWeaponId(stack) == null) {
            return;
        }
        refreshWeaponMeta(stack, viewer);
    }

    @EventHandler
    public void onJoinRefreshWeaponModels(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refreshDpmrWeaponsInInventory(p), 28L);
    }

    public void bumpReserveAmmo(Player player, WeaponProfile w, int delta) {
        // Munition de reserve desactivee (rechargement infini).
    }

    public List<String> getAllWeaponIds() {
        return Arrays.stream(WeaponProfile.values()).map(Enum::name).toList();
    }

    public List<String> getWarfareWeaponIds() {
        return Arrays.stream(WeaponProfile.values())
                .filter(WeaponProfile::isWarfareWeapon)
                .map(Enum::name)
                .toList();
    }

    public String readWeaponId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(keyWeaponId, PersistentDataType.STRING);
    }

    /**
     * Tir PNJ : meme logique que les joueurs (hitscan / projectiles), sans effets d'upgrade lies au profil joueur.
     */
    public void npcFireWeapon(LivingEntity npc, ItemStack weaponItem) {
        WeaponProfile w = fromItem(weaponItem);
        if (w == null || npc == null || !npc.isValid()) {
            return;
        }
        double dmgScale = readNpcShotDamageScale(npc);
        Location eye = npc.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(eye, w.shootSound(), w.soundVolume(), w.soundPitch());

        switch (w.fireMode()) {
            case HITSCAN -> npcFireHitscan(npc, w, weaponItem, -1.0, dmgScale);
            case HITSCAN_BOW_CHARGE -> npcFireHitscan(npc, w, weaponItem, 1.0, dmgScale);
            case HITSCAN_CROSS -> npcFireHitscanCross(npc, w, weaponItem, dmgScale);
            case PROJECTILE_SNOWBALL -> npcLaunchSnowball(npc, w, weaponItem, dmgScale);
            case PROJECTILE_CLIO3 -> npcLaunchClio3(npc, w, weaponItem, dmgScale);
            case PROJECTILE_MORTAR -> npcLaunchMortar(npc, w, weaponItem, dmgScale);
            case PROJECTILE_ROCKET -> npcLaunchRocket(npc, w, weaponItem, dmgScale);
            default -> npcFireHitscan(npc, w, weaponItem, -1.0, dmgScale);
        }
    }

    private double readNpcShotDamageScale(LivingEntity npc) {
        if (npc == null) {
            return 1.0;
        }
        Double v = npc.getPersistentDataContainer().get(keyNpcShotScale, PersistentDataType.DOUBLE);
        if (v == null || v <= 0 || Double.isNaN(v)) {
            return 1.0;
        }
        return Math.min(4.0, v);
    }

    /**
     * @param charge01 0..1 multiplie portee/degats (sniper charge) ; valeur negative = pas de scaling
     */
    private void npcFireHitscan(LivingEntity npc, WeaponProfile w, ItemStack weapon, double charge01, double npcDmgScale) {
        double s = npcDmgScale <= 0 || Double.isNaN(npcDmgScale) ? 1.0 : Math.min(4.0, npcDmgScale);
        WeaponUpgradeState st = hitscanUpgradeStateFor(w, weapon);
        RevolverUpgradeState rv = w == WeaponProfile.REVOLVER ? RevolverUpgradeState.read(weapon, plugin) : RevolverUpgradeState.NONE;
        Location eye = npc.getEyeLocation();
        World world = eye.getWorld();
        boolean charged = charge01 >= 0 && charge01 <= 1.0;
        double rMul = charged ? (0.36 + 0.64 * charge01) : 1.0;
        double dMul = charged ? (0.42 + 0.58 * charge01) : 1.0;
        double sMul = charged ? Math.max(0.28, 1.22 - 0.72 * charge01) : 1.0;
        double spread = w.baseSpreadDegrees() * WeaponUpgradeEffects.spreadMultiplier(weapon, st) * sMul;
        double range = w.baseRange() * WeaponUpgradeEffects.rangeMultiplier(weapon, st) * rMul;
        double perPellet = (w.baseDamage() / Math.max(1, w.pellets())) * WeaponUpgradeEffects.damageMultiplier(weapon, st) * dMul * s;

        int pelletCount = w == WeaponProfile.REVOLVER ? RevolverUpgradeEffects.hitscanPellets(rv) : w.pellets();
        boolean splitTriggered = false;
        for (int i = 0; i < pelletCount; i++) {
            Vector dir = applySpread(eye, eye.getDirection(), spread);
            RayTraceResult hit = world.rayTraceEntities(
                    eye,
                    dir,
                    range,
                    0.35,
                    ent -> ent instanceof LivingEntity le && le != npc);
            drawBeamNpc(eye, dir, range, world, w);
            if (hit != null && hit.getHitEntity() instanceof LivingEntity living) {
                Vector hp = hit.getHitPosition();
                boolean blocked = hp != null && weaponShotBlockedBySafeLine(npc, hp, world);
                boolean headshot = weaponAllowsHeadshot(w) && isHeadshotPosition(living, hp);
                double mult = ThreadLocalRandom.current().nextDouble(0.88, 1.12);
                if (headshot) {
                    mult *= HEADSHOT_DAMAGE_MULTIPLIER;
                    world.spawnParticle(Particle.CRIT, living.getEyeLocation(), 14, 0.12, 0.08, 0.12, 0.02);
                }
                double dealt = perPellet * mult;
                dealt = applyArmorReduction(living, w.damageType(), dealt);
                if (!blocked) {
                    living.damage(dealt, npc);
                    if (w == WeaponProfile.DIVISER_POUR_MIEUX_REGNER
                            || w == WeaponProfile.GHOST_DIVISER
                            || w == WeaponProfile.GHOST_LANCE_FLAMME) {
                        living.setFireTicks(Math.max(living.getFireTicks(), 80));
                        world.spawnParticle(Particle.FLAME, living.getLocation().add(0, 1, 0), 18, 0.2, 0.35, 0.2, 0.01);
                        world.playSound(living.getLocation(), Sound.ENTITY_BLAZE_HURT, 0.35f, 1.35f);
                    }
                }
                world.spawnParticle(Particle.DAMAGE_INDICATOR, living.getLocation().add(0, 1, 0), 4, 0.15, 0.3, 0.15, 0.01);
                world.spawnParticle(w.trailParticle(), living.getLocation().add(0, 1, 0), w.trailDensity(), 0.2, 0.2, 0.2, 0.02);
                if (!blocked && w == WeaponProfile.REVOLVER && rv.tier() >= 2) {
                    pulseAreaDamage(npc, living.getLocation().add(0, 0.5, 0), perPellet * 0.42, 2.75);
                    world.spawnParticle(Particle.LAVA, living.getLocation().add(0, 0.6, 0), 6, 0.25, 0.2, 0.25, 0.02);
                }
                if (!blocked && w == WeaponProfile.REVOLVER && rv.tier() >= 3 && living instanceof Player penetrated && !splitTriggered) {
                    Vector hitVec = hit.getHitPosition();
                    if (hitVec != null) {
                        splitTriggered = true;
                        double traveled = eye.toVector().distance(hitVec);
                        double remain = Math.max(1.0, range - traveled + 0.45);
                        revolverSplitAfterPlayerHit(npc, null, penetrated, hitVec, dir, perPellet, remain, world, w, st);
                    }
                }
            }
        }
    }

    private void npcFireHitscanCross(LivingEntity npc, WeaponProfile w, ItemStack weapon, double npcDmgScale) {
        double s = npcDmgScale <= 0 || Double.isNaN(npcDmgScale) ? 1.0 : Math.min(4.0, npcDmgScale);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        Location eye = npc.getEyeLocation();
        World world = eye.getWorld();
        double spread = w.baseSpreadDegrees() * WeaponUpgradeEffects.spreadMultiplier(weapon, st);
        double range = w.baseRange() * WeaponUpgradeEffects.rangeMultiplier(weapon, st);
        double perDirectionDamage = (w.baseDamage() * WeaponUpgradeEffects.damageMultiplier(weapon, st)) / 4.0 * s;

        Vector forward = eye.getDirection().clone().setY(0);
        if (forward.lengthSquared() < 1.0e-6) {
            forward = new Vector(0, 0, 1);
        } else {
            forward.normalize();
        }
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        Vector[] bases = new Vector[]{
                forward,
                forward.clone().multiply(-1),
                right,
                right.clone().multiply(-1)
        };

        for (Vector base : bases) {
            Vector dir = applySpread(eye, base, spread);
            RayTraceResult rayHit = world.rayTraceEntities(
                    eye,
                    dir,
                    range,
                    0.35,
                    ent -> ent instanceof LivingEntity le && le != npc);
            drawBeamNpc(eye, dir, range, world, w);
            if (rayHit != null && rayHit.getHitEntity() instanceof LivingEntity living) {
                Vector hp = rayHit.getHitPosition();
                boolean blocked = hp != null && weaponShotBlockedBySafeLine(npc, hp, world);
                boolean headshot = weaponAllowsHeadshot(w) && isHeadshotPosition(living, hp);
                double mult = ThreadLocalRandom.current().nextDouble(0.88, 1.12);
                if (headshot) {
                    mult *= HEADSHOT_DAMAGE_MULTIPLIER;
                    world.spawnParticle(Particle.CRIT, living.getEyeLocation(), 14, 0.12, 0.08, 0.12, 0.02);
                }
                double dealt = perDirectionDamage * mult;
                dealt = applyArmorReduction(living, w.damageType(), dealt);
                if (!blocked) {
                    living.damage(dealt, npc);
                }
                world.spawnParticle(Particle.DAMAGE_INDICATOR, living.getLocation().add(0, 1, 0), 4, 0.15, 0.3, 0.15, 0.01);
                world.spawnParticle(w.trailParticle(), living.getLocation().add(0, 1, 0), w.trailDensity(), 0.2, 0.2, 0.2, 0.02);
            }
        }
    }

    private void npcLaunchSnowball(LivingEntity npc, WeaponProfile w, ItemStack weapon, double npcDmgScale) {
        double s = npcDmgScale <= 0 || Double.isNaN(npcDmgScale) ? 1.0 : Math.min(4.0, npcDmgScale);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        Location spawn = offsetSpawnLiving(npc);
        double spread = w.baseSpreadDegrees() * WeaponUpgradeEffects.spreadMultiplier(weapon, st);
        double dmg = w.baseDamage() * WeaponUpgradeEffects.damageMultiplier(weapon, st) * s;
        Vector dir = applySpread(npc.getEyeLocation(), npc.getEyeLocation().getDirection(), spread);
        Snowball sb = spawn.getWorld().spawn(spawn, Snowball.class);
        tagProjectile(sb, npc, dmg, weapon);
        sb.setVelocity(dir.multiply(w.projectileSpeed()));
        sb.setShooter(npc);
        trailBurstNpc(spawn, w);
    }

    private void npcLaunchClio3(LivingEntity npc, WeaponProfile w, ItemStack weapon, double npcDmgScale) {
        double s = npcDmgScale <= 0 || Double.isNaN(npcDmgScale) ? 1.0 : Math.min(4.0, npcDmgScale);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        Location spawn = offsetSpawnLiving(npc);
        double spread = w.baseSpreadDegrees() * WeaponUpgradeEffects.spreadMultiplier(weapon, st);
        double dmg = w.baseDamage() * WeaponUpgradeEffects.damageMultiplier(weapon, st) * s;
        Vector dir = applySpread(npc.getEyeLocation(), npc.getEyeLocation().getDirection(), spread);
        Snowball sb = spawn.getWorld().spawn(spawn, Snowball.class);
        tagClio3Projectile(sb, npc, dmg, weapon);
        sb.setVelocity(dir.multiply(w.projectileSpeed()));
        sb.setShooter(npc);
        trailBurstNpc(spawn, w);
    }

    private void npcLaunchMortar(LivingEntity npc, WeaponProfile w, ItemStack weapon, double npcDmgScale) {
        double s = npcDmgScale <= 0 || Double.isNaN(npcDmgScale) ? 1.0 : Math.min(4.0, npcDmgScale);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        MortarUpgradeState ms = MortarUpgradeState.read(weapon, plugin);
        Location spawn = offsetSpawnLiving(npc);
        double dmg = w.baseDamage() * WeaponUpgradeEffects.damageMultiplier(weapon, st) * s;
        double maxRange = w.baseRange() * WeaponUpgradeEffects.rangeMultiplier(weapon, st);
        Location eye = npc.getEyeLocation();
        Location target = findMortarAimLocation(eye, eye.getDirection(), maxRange);
        int shells = 1 + MortarUpgradeEffects.extraShellsPerShot(ms);
        Vector eyeDir = eye.getDirection().clone();
        Vector lateral = new Vector(-eyeDir.getZ(), 0, eyeDir.getX());
        if (lateral.lengthSquared() < 1e-6) {
            lateral = new Vector(1, 0, 0);
        } else {
            lateral.normalize();
        }
        double spread = shells > 1 ? 0.55 + 0.12 * ms.tier() : 0;
        for (int i = 0; i < shells; i++) {
            Location aim = target.clone().add(lateral.clone().multiply(spread * (i - (shells - 1) / 2.0)));
            Snowball sb = spawn.getWorld().spawn(spawn, Snowball.class);
            tagMortarProjectile(sb, npc, dmg, weapon, ms);
            Vector vel = computeMortarVelocity(spawn, aim, MORTAR_PROJECTILE_GRAVITY).multiply(0.92 + w.projectileSpeed() * 0.11);
            sb.setVelocity(vel);
            sb.setShooter(npc);
        }
        trailBurstNpc(spawn, w);
    }

    private static Location offsetSpawnLiving(LivingEntity entity) {
        Location eye = entity.getEyeLocation();
        Vector d = eye.getDirection().normalize().multiply(0.45);
        return eye.clone().add(d);
    }

    private void trailBurstNpc(Location loc, WeaponProfile w) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Particle base = w.trailParticle();
        world.spawnParticle(base, loc, w.trailDensity() * 3, 0.12, 0.12, 0.12, 0.02);
        spawnWeaponShotSignature(world, loc, base, w);
    }

    private static void drawBeamNpc(Location start, Vector direction, double range, World world, WeaponProfile w) {
        Vector step = direction.clone().normalize().multiply(0.45);
        Location cursor = start.clone();
        int max = Math.max(1, (int) (range / 0.45));
        Particle p = w.trailParticle();
        for (int i = 0; i < max; i++) {
            for (int j = 0; j < w.trailDensity(); j++) {
                world.spawnParticle(p, cursor, 1, 0.04, 0.04, 0.04, 0.002);
            }
            cursor.add(step);
        }
    }

    public boolean triggerManualReload(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        WeaponProfile w = fromItem(main);
        if (w == null) {
            I18n.actionBar(player, NamedTextColor.GRAY, "weapon.reload_hold_weapon");
            return false;
        }
        if (w.isNuclearWeapon() || w.isGrapplingHook()) {
            I18n.actionBar(player, NamedTextColor.GRAY, "weapon.reload_not_applicable");
            return false;
        }
        if (reloadSessions.containsKey(player.getUniqueId())) {
            I18n.actionBar(player, NamedTextColor.YELLOW, "weapon.reload_in_progress");
            return false;
        }
        int clip = getClip(player, w, main);
        if (clip >= maxClipFor(player, w, main)) {
            I18n.actionBar(player, NamedTextColor.GRAY, "weapon.reload_full");
            return false;
        }
        startReload(player, w, main);
        return true;
    }

    @EventHandler
    public void onShoot(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        WeaponProfile w = fromItem(main);
        if (w == null) {
            return;
        }
        boolean bowChargeDraw = w.fireMode() == FireMode.HITSCAN_BOW_CHARGE
                && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
        if (!bowChargeDraw) {
            event.setCancelled(true);
        }
        if (zoneManager != null && !zoneManager.isCombatAllowed(player.getLocation())) {
            if (bowChargeDraw) {
                event.setCancelled(true);
            }
            I18n.actionBar(player, NamedTextColor.RED, "weapon.zone_blocked");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (w == WeaponProfile.JERRYCAN && player.isSneaking()) {
                igniteGasoline(player);
                return;
            }
            triggerManualReload(player);
            return;
        }
        if (w.isNuclearWeapon()) {
            handleNuclearStrike(player, w, main);
            return;
        }
        if (w.isTurretWeapon() && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            if (!isRidingOwnTurret(player)) {
                tryDeployPortableTurret(player, event);
                return;
            }
        }
        if (reloadSessions.containsKey(player.getUniqueId())) {
            I18n.actionBar(player, NamedTextColor.YELLOW, "weapon.reload_in_progress");
            return;
        }
        int currentClip = getClip(player, w, main);
        if (currentClip <= 0) {
            startReload(player, w, main);
            return;
        }
        long now = System.currentTimeMillis();
        WeaponUpgradeState st = WeaponUpgradeState.read(main, plugin);
        if (w == WeaponProfile.REVOLVER) {
            st = hitscanUpgradeStateFor(w, main);
        }
        long minDelay = (long) (w.cooldownTicks() * 50L * WeaponUpgradeEffects.cooldownMultiplier(main, st));
        if (w == WeaponProfile.REVOLVER) {
            RevolverUpgradeState rv = RevolverUpgradeState.read(main, plugin);
            minDelay = (long) (minDelay * RevolverUpgradeEffects.cooldownMultiplier(rv));
        }
        if (w == WeaponProfile.JERRYCAN) {
            minDelay = (long) (minDelay * JerrycanUpgradeEffects.jerryCooldownMul(JerrycanUpgradeState.read(main, plugin)));
        }
        if (w.isGrapplingHook()) {
            minDelay = Math.max(minDelay, GRAPPLE_COOLDOWN_MS);
        }
        minDelay = (long) (minDelay * powerupBlockManager.rapidFireCooldownMultiplier(player));
        if (w.isMinigun()) {
            double heat = decayedMinigunHeat(player.getUniqueId(), now);
            if (heat >= 1.0) {
                I18n.actionBar(player, NamedTextColor.RED, "weapon.minigun_overheat");
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 0.6f);
                return;
            }
        }
        if (now - lastShotTimeMs.getOrDefault(player.getUniqueId(), 0L) < minDelay) {
            if (w.isGrapplingHook()) {
                long leftMs = minDelay - (now - lastShotTimeMs.getOrDefault(player.getUniqueId(), 0L));
                int leftSec = (int) Math.ceil(leftMs / 1000.0);
                I18n.actionBar(player, NamedTextColor.RED, "weapon.grapple_cooldown", leftSec);
            }
            return;
        }
        if (isWeaponBroken(main)) {
            I18n.actionBar(player, NamedTextColor.RED, "weapon.broken");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 0.8f);
            return;
        }

        setClip(player, w, currentClip - 1, main);
        if (st.path() == WeaponUpgradePath.SURVIVAL && st.tier() >= 5 && ThreadLocalRandom.current().nextDouble() < 0.11) {
            setClip(player, w, getClip(player, w, main) + 1, main);
        }
        long previousShotMs = lastShotTimeMs.getOrDefault(player.getUniqueId(), 0L);
        lastShotTimeMs.put(player.getUniqueId(), now);
        fire(player, w, main, previousShotMs);
        wearWeaponOnShot(player, player.getInventory().getItemInMainHand(), w);
        sendAmmoActionBar(player, w, main);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChargeSniperBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        WeaponProfile w = fromItem(main);
        if (w == null || w.fireMode() != FireMode.HITSCAN_BOW_CHARGE) {
            return;
        }
        event.setCancelled(true);
        event.setConsumeItem(false);

        if (zoneManager != null && !zoneManager.isCombatAllowed(player.getLocation())) {
            I18n.actionBar(player, NamedTextColor.RED, "weapon.zone_blocked");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        if (reloadSessions.containsKey(player.getUniqueId())) {
            I18n.actionBar(player, NamedTextColor.YELLOW, "weapon.reload_in_progress");
            return;
        }
        int currentClip = getClip(player, w, main);
        if (currentClip <= 0) {
            startReload(player, w, main);
            return;
        }
        long now = System.currentTimeMillis();
        WeaponUpgradeState st = WeaponUpgradeState.read(main, plugin);
        long minDelay = (long) (w.cooldownTicks() * 50L * WeaponUpgradeEffects.cooldownMultiplier(main, st));
        minDelay = (long) (minDelay * powerupBlockManager.rapidFireCooldownMultiplier(player));
        if (now - lastShotTimeMs.getOrDefault(player.getUniqueId(), 0L) < minDelay) {
            return;
        }
        if (isWeaponBroken(main)) {
            I18n.actionBar(player, NamedTextColor.RED, "weapon.broken");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 0.8f);
            return;
        }

        double charge01 = Math.max(0.15, Math.min(1.0, event.getForce()));
        setClip(player, w, currentClip - 1, main);
        if (st.path() == WeaponUpgradePath.SURVIVAL && st.tier() >= 5 && ThreadLocalRandom.current().nextDouble() < 0.11) {
            setClip(player, w, getClip(player, w, main) + 1, main);
        }
        long previousShotMs = lastShotTimeMs.getOrDefault(player.getUniqueId(), 0L);
        lastShotTimeMs.put(player.getUniqueId(), now);

        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        float pitch = w.soundPitch() * (float) (0.78 + 0.32 * charge01);
        world.playSound(eye, w.shootSound(), w.soundVolume(), pitch);
        fireHitscan(player, w, main, previousShotMs, charge01);
        wearWeaponOnShot(player, player.getInventory().getItemInMainHand(), w);
        sendAmmoActionBar(player, w, main);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTraqueurBowDurability(PlayerItemDamageEvent event) {
        WeaponProfile wp = fromItem(event.getItem());
        if (wp != null && wp.fireMode() == FireMode.HITSCAN_BOW_CHARGE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        if (!pdc.has(keyProjDamage, PersistentDataType.DOUBLE)) {
            return;
        }
        Double dmg = pdc.get(keyProjDamage, PersistentDataType.DOUBLE);
        String ownerStr = pdc.get(keyProjOwner, PersistentDataType.STRING);
        if (dmg == null || ownerStr == null) {
            return;
        }
        UUID ownerId;
        try {
            ownerId = UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        org.bukkit.entity.Entity ownerEnt = Bukkit.getServer().getEntity(ownerId);
        LivingEntity projectileOwner = ownerEnt instanceof LivingEntity le ? le : null;
        Player shooter = ownerEnt instanceof Player p ? p : null;
        if (shooter != null && event.getHitEntity() != null) {
            if (powerupBlockManager.tryProjectilePowerupEntity(shooter, event.getHitEntity())) {
                projectile.remove();
                return;
            }
        }
        if (shooter != null && event.getHitBlock() != null && event.getHitEntity() == null) {
            if (powerupBlockManager.tryProjectilePowerupBlock(shooter, event.getHitBlock())) {
                projectile.remove();
                return;
            }
        }
        String kind = pdc.get(keyProjKind, PersistentDataType.STRING);
        String projWeaponId = pdc.get(keyProjWeaponId, PersistentDataType.STRING);
        WeaponProfile projWeapon = WeaponProfile.fromId(projWeaponId);
        WeaponDamageType projType = projWeapon != null ? projWeapon.damageType() : WeaponDamageType.ASSAULT_RIFLE;
        WeaponUpgradeState projSt = readUpgradePayload(pdc);

        if (PROJ_KIND_CHESTNUT.equals(kind)) {
            Location center;
            if (event.getHitEntity() != null) {
                center = event.getHitEntity().getLocation().add(0, 0.5, 0);
            } else if (event.getHitBlock() != null) {
                center = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
            } else {
                center = projectile.getLocation();
            }
            explodeChestnut(center, dmg, ownerId, projectileOwner, projSt);
            projectile.remove();
            return;
        }

        if (PROJ_KIND_CLIO3.equals(kind)) {
            cleanupClio3Visuals(projectile.getUniqueId());
            Location center;
            if (event.getHitEntity() != null) {
                center = event.getHitEntity().getLocation().add(0, 0.5, 0);
            } else if (event.getHitBlock() != null) {
                center = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
            } else {
                center = projectile.getLocation();
            }
            explodeClio3(center, dmg, ownerId, projectileOwner, projSt);
            projectile.remove();
            return;
        }

        if (PROJ_KIND_MORTAR.equals(kind)) {
            MortarUpgradeState mortarSt = MortarUpgradeState.readPayload(pdc, plugin);
            Location center;
            if (event.getHitEntity() != null) {
                center = event.getHitEntity().getLocation().add(0, 0.5, 0);
            } else if (event.getHitBlock() != null) {
                center = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
            } else {
                center = projectile.getLocation();
            }
            explodeMortar(center, dmg, ownerId, projectileOwner, projSt, mortarSt);
            projectile.remove();
            return;
        }

        if (PROJ_KIND_ROCKET.equals(kind)) {
            cancelRocketHoming(projectile.getUniqueId());
            RocketUpgradeState rocketSt = RocketUpgradeState.readPayload(pdc, plugin);
            Location center;
            if (event.getHitEntity() != null) {
                center = event.getHitEntity().getLocation().add(0, 0.5, 0);
            } else if (event.getHitBlock() != null) {
                center = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
            } else {
                center = projectile.getLocation();
            }
            explodeRocket(center, dmg, ownerId, projectileOwner, projSt, rocketSt);
            projectile.remove();
            return;
        }

        if (PROJ_KIND_BOMB.equals(kind)) {
            BombUpgradeState bs = readBombPayloadFromPdc(pdc);
            if (event.getHitEntity() != null) {
                Location center = event.getHitEntity().getLocation().add(0, 0.5, 0);
                explodeBomb(center, dmg, ownerId, projectileOwner, bs);
                projectile.remove();
                return;
            }
            if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
                Integer bLeft = pdc.get(keyBombBounceLeft, PersistentDataType.INTEGER);
                if (bLeft != null && bLeft > 0) {
                    BlockFace face = event.getHitBlockFace();
                    Vector nn = new Vector(face.getModX(), face.getModY(), face.getModZ()).normalize();
                    Vector v = projectile.getVelocity();
                    Vector r = v.clone().subtract(nn.clone().multiply(2 * v.dot(nn))).multiply(0.74);
                    projectile.setVelocity(r);
                    pdc.set(keyBombBounceLeft, PersistentDataType.INTEGER, bLeft - 1);
                    projectile.getWorld().playSound(projectile.getLocation(), Sound.BLOCK_STONE_HIT, 0.55f, 1.45f);
                    return;
                }
                Location center = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
                explodeBomb(center, dmg, ownerId, projectileOwner, bs);
            }
            projectile.remove();
            return;
        }

        if (PROJ_KIND_ZAP.equals(kind)) {
            if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
                Integer bLeft = pdc.get(keyZapBounceLeft, PersistentDataType.INTEGER);
                if (bLeft != null && bLeft > 0) {
                    BlockFace face = event.getHitBlockFace();
                    Vector nn = new Vector(face.getModX(), face.getModY(), face.getModZ()).normalize();
                    Vector v = projectile.getVelocity();
                    Vector r = v.clone().subtract(nn.clone().multiply(2 * v.dot(nn))).multiply(0.88);
                    projectile.setVelocity(r);
                    pdc.set(keyZapBounceLeft, PersistentDataType.INTEGER, bLeft - 1);
                    projectile.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, projectile.getLocation(), 24, 0.15, 0.15, 0.15, 0.08);
                    projectile.getWorld().playSound(projectile.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 1.75f);
                    return;
                }
            }
            Entity hit = event.getHitEntity();
            if (hit instanceof LivingEntity living && !hit.getUniqueId().equals(ownerId)) {
                boolean blocked = weaponProjectileToVictimBlockedBySafe(projectile, living);
                double mult = ThreadLocalRandom.current().nextDouble(0.9, 1.16);
                double dealt = dmg * mult;
                dealt = applyArmorReduction(living, projType, dealt);
                if (!blocked) {
                    living.damage(dealt, projectileOwner != null ? projectileOwner : projectile);
                    if (shooter != null) {
                        applyOnHitUpgradeEffects(shooter, living, projSt, dealt, false);
                    }
                }
                living.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, living.getLocation().add(0, 1, 0), 42, 0.35, 0.45, 0.35, 0.1);
                living.getWorld().playSound(living.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.35f);
            }
            projectile.remove();
            return;
        }

        if (PROJ_KIND_GAS.equals(kind)) {
            Location center;
            if (event.getHitBlock() != null) {
                center = event.getHitBlock().getLocation().add(0.5, 0.05, 0.5);
            } else if (event.getHitEntity() != null) {
                center = event.getHitEntity().getLocation().add(0, 0.05, 0);
            } else {
                center = projectile.getLocation();
            }
            World gw = center.getWorld();
            if (gw == null) {
                projectile.remove();
                return;
            }
            JerrycanUpgradeState jerry = JerrycanUpgradeState.readPdc(pdc, plugin);
            boolean mini = pdc.has(keyGasMini, PersistentDataType.BYTE)
                    && pdc.get(keyGasMini, PersistentDataType.BYTE) == (byte) 1;
            if (JerrycanUpgradeEffects.smokeCloud(jerry)) {
                gw.spawnParticle(Particle.SMOKE, center, 90, 1.4, 0.45, 1.4, 0.05);
                gw.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 55, 1.1, 0.35, 1.1, 0.03);
                gw.playSound(center, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.5f, 0.7f);
                for (Player pl : gw.getPlayers()) {
                    if (!pl.getWorld().equals(gw) || pl.getLocation().distanceSquared(center) > 6.5 * 6.5) {
                        continue;
                    }
                    pl.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 45, 0, false, true, true));
                }
            }
            if (JerrycanUpgradeEffects.instantImpact(jerry)) {
                igniteArea(gw, center, shooter, jerry, mini);
                projectile.remove();
                return;
            }
            addGasolinePuddle(center, 12_000L, jerry);
            gw.spawnParticle(Particle.DRIPPING_HONEY, center, 26, 0.45, 0.05, 0.45, 0.02);
            gw.playSound(center, Sound.BLOCK_HONEY_BLOCK_PLACE, 0.6f, 1.1f);
            projectile.remove();
            return;
        }

        if (PROJ_KIND_SERUM.equals(kind)) {
            String wid = pdc.get(keySerumWeaponId, PersistentDataType.STRING);
            WeaponProfile serumW = WeaponProfile.fromId(wid);
            Location center;
            if (event.getHitBlock() != null) {
                center = event.getHitBlock().getLocation().add(0.5, 0.15, 0.5);
            } else if (event.getHitEntity() != null) {
                center = event.getHitEntity().getLocation().add(0, 0.2, 0);
            } else {
                center = projectile.getLocation();
            }
            startSerumCloud(center, ownerId, shooter, serumW != null ? serumW : WeaponProfile.SERUM_SOIN);
            projectile.remove();
            return;
        }

        if (PROJ_KIND_HEAL_DART.equals(kind)) {
            Entity hitEntity = event.getHitEntity();
            if (hitEntity instanceof Player target && !target.getUniqueId().equals(ownerId)) {
                double healHalfHearts = Math.max(1.0, dmg);
                var maxAttr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double max = maxAttr != null ? maxAttr.getValue() : 20.0;
                double next = Math.min(max, target.getHealth() + healHalfHearts);
                if (next > target.getHealth()) {
                    target.setHealth(next);
                    target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 2.2, 0), 10, 0.35, 0.2, 0.35, 0.02);
                    target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, 1.1, 0), 14, 0.45, 0.55, 0.45, 0.02);
                    target.playSound(target.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 0.85f, 1.35f);
                    if (shooter != null) {
                        I18n.actionBar(shooter, NamedTextColor.LIGHT_PURPLE, "weapon.heal_applied_you",
                                target.getName(), String.format(Locale.ROOT, "%.1f", healHalfHearts / 2.0));
                        I18n.actionBar(target, NamedTextColor.GREEN, "weapon.heal_received", shooter.getName());
                    }
                } else if (shooter != null) {
                    I18n.actionBar(shooter, NamedTextColor.GRAY, "weapon.heal_target_full", target.getName());
                }
            }
            projectile.remove();
            return;
        }

        Entity hit = event.getHitEntity();
        if (hit instanceof LivingEntity living && !hit.getUniqueId().equals(ownerId)) {
            Vector hitPos = projectile.getLocation().toVector();
            boolean headshot = projWeapon != null && weaponAllowsHeadshot(projWeapon)
                    && isHeadshotPosition(living, hitPos);
            double mult = ThreadLocalRandom.current().nextDouble(0.88, 1.12);
            if (headshot) {
                mult *= HEADSHOT_DAMAGE_MULTIPLIER;
                living.getWorld().spawnParticle(Particle.CRIT, living.getEyeLocation(), 12, 0.12, 0.08, 0.12, 0.02);
            }
            double dealt = dmg * mult;
            dealt = applyArmorReduction(living, projType, dealt);
            living.damage(dealt, projectileOwner != null ? projectileOwner : projectile);
            living.getWorld().spawnParticle(Particle.CRIT, living.getLocation().add(0, 1, 0), 10, 0.25, 0.35, 0.25, 0.02);
            living.getWorld().playSound(living.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.5f, 1.2f);
            if (shooter != null) {
                applyOnHitUpgradeEffects(shooter, living, projSt, dealt, false);
                if (headshot) {
                    notifyHeadshot(shooter);
                }
            }
        }
        projectile.remove();
    }

    @EventHandler
    public void onSwapReload(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        WeaponProfile w = fromItem(player.getInventory().getItemInMainHand());
        if (w == null) {
            return;
        }
        event.setCancelled(true);
        startReload(player, w, player.getInventory().getItemInMainHand());
    }

    @EventHandler
    public void onDropReload(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        WeaponProfile w = fromItem(player.getInventory().getItemInMainHand());
        if (w == null) {
            return;
        }
        event.setCancelled(true);
        startReload(player, w, player.getInventory().getItemInMainHand());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancelReload(player);
        player.setWalkSpeed(VANILLA_WALK_SPEED);
        techShotCount.remove(player.getUniqueId());
        recoilMeter.remove(player.getUniqueId());
        minigunHeat.remove(player.getUniqueId());
        minigunHeatLastMs.remove(player.getUniqueId());
        BukkitTask nt = nuclearTasks.remove(player.getUniqueId());
        if (nt != null) {
            nt.cancel();
        }
        dismantleDeployedTurret(player.getUniqueId());
        cleanupVirtArrows(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTurretSneakStow(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (!isRidingOwnTurret(player)) {
            return;
        }
        dismantleDeployedTurret(player.getUniqueId());
        I18n.actionBar(player, NamedTextColor.GRAY, "weapon.turret_stowed");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeathTurret(PlayerDeathEvent event) {
        dismantleDeployedTurret(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (reloadSessions.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> verifyReloadWeaponStillHeld(player));
        }
    }

    private void verifyReloadWeaponStillHeld(Player player) {
        ReloadSession session = reloadSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        ItemStack mh = player.getInventory().getItemInMainHand();
        WeaponProfile held = fromItem(mh);
        if (held == null || !held.name().equals(session.weaponId)) {
            cancelReload(player);
            I18n.actionBar(player, NamedTextColor.RED, "weapon.reload_cancelled");
            return;
        }
        String sid = ensureWeaponInstanceId(player, mh);
        if (sid == null || !sid.equals(session.instanceId)) {
            cancelReload(player);
            I18n.actionBar(player, NamedTextColor.RED, "weapon.reload_cancelled");
        }
    }

    private void fire(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        Location eye = shooter.getEyeLocation();
        World world = eye.getWorld();
        world.playSound(eye, w.shootSound(), w.soundVolume(), w.soundPitch());

        switch (w.fireMode()) {
            case HITSCAN -> fireHitscan(shooter, w, weapon, previousShotMs);
            case HITSCAN_CROSS -> fireHitscanCross(shooter, w, weapon, previousShotMs);
            case PROJECTILE_SNOWBALL -> launchSnowball(shooter, w, weapon, previousShotMs);
            case PROJECTILE_ARROW -> launchArrow(shooter, w, weapon, previousShotMs);
            case PROJECTILE_EGG -> launchEgg(shooter, w, weapon, previousShotMs);
            case PROJECTILE_LLAMA_SPIT -> launchLlamaSpit(shooter, w, weapon, previousShotMs);
            case PROJECTILE_CHESTNUT -> launchChestnut(shooter, w, weapon, previousShotMs);
            case PROJECTILE_SHOTGUN_FIVE -> launchShotgunFive(shooter, w, weapon, previousShotMs);
            case PROJECTILE_GASOLINE -> launchGasoline(shooter, w, weapon, previousShotMs);
            case PROJECTILE_BOMB -> launchBomb(shooter, w, weapon, previousShotMs);
            case PROJECTILE_CLIO3 -> launchClio3(shooter, w, weapon, previousShotMs);
            case PROJECTILE_MORTAR -> launchMortar(shooter, w, weapon, previousShotMs);
            case PROJECTILE_ROCKET -> launchRocket(shooter, w, weapon, previousShotMs);
            case PROJECTILE_SERUM_ZONE -> launchSerumVial(shooter, w, weapon, previousShotMs);
            case PROJECTILE_HEAL_DART -> launchHealDart(shooter, w, weapon, previousShotMs);
            case GRAPPLE_BEAM -> fireGrapple(shooter, w);
            case HITSCAN_BOW_CHARGE, NUCLEAR_STRIKE -> {
            }
            case TURRET_DEPLOY -> fireHitscan(shooter, w, weapon, previousShotMs);
        }
    }

    private double getDecayedRecoil(Player p, long previousShotMs) {
        double stored = recoilMeter.getOrDefault(p.getUniqueId(), 0.0);
        if (previousShotMs <= 0) {
            return stored;
        }
        double dt = (System.currentTimeMillis() - previousShotMs) / 1000.0;
        return stored * Math.exp(-dt * 3.0);
    }

    private void registerRecoil(Player p, WeaponProfile w, long previousShotMs) {
        if (w.isGrapplingHook() || w.isNuclearWeapon()) {
            return;
        }
        double decayed = getDecayedRecoil(p, previousShotMs);
        recoilMeter.put(p.getUniqueId(), Math.min(9.0, decayed + w.recoilAddedPerShot()));
    }

    /** Sprint / marche / sneak / nage modifient la dispersion. */
    private double movementSpreadFactor(Player p) {
        double m = 1.0;
        if (p.isSprinting()) {
            m += 0.42;
        } else if (p.getVelocity().lengthSquared() > 0.06) {
            m += 0.22;
        }
        if (p.isGliding()) {
            m += 0.28;
        }
        if (p.isSwimming()) {
            m += 0.18;
        }
        if (p.isSneaking()) {
            m *= 0.74;
        }
        return m;
    }

    private double effectiveSpreadDegrees(Player shooter, WeaponProfile w, ItemStack weapon, WeaponUpgradeState st, long previousShotMs) {
        double base = w.effectiveSpread(shooter) * WeaponUpgradeEffects.spreadMultiplier(weapon, st);
        double decayedRecoil = getDecayedRecoil(shooter, previousShotMs);
        double spread = (base + decayedRecoil * 0.092) * movementSpreadFactor(shooter);
        if (w.isMinigun()) {
            double heat = decayedMinigunHeat(shooter.getUniqueId(), System.currentTimeMillis());
            spread += heat * MINIGUN_HEAT_SPREAD_DEG;
        }
        return spread;
    }

    private double decayedMinigunHeat(UUID id, long nowMs) {
        double h = minigunHeat.getOrDefault(id, 0.0);
        Long last = minigunHeatLastMs.get(id);
        if (last == null || last <= 0) {
            return h;
        }
        double dt = (nowMs - last) / 1000.0;
        if (dt <= 0) {
            return h;
        }
        return h * Math.exp(-dt * MINIGUN_HEAT_DECAY_PER_SEC);
    }

    private void registerMinigunHeat(UUID id, long nowMs) {
        double h = Math.min(1.0, decayedMinigunHeat(id, nowMs) + MINIGUN_HEAT_PER_SHOT);
        minigunHeat.put(id, h);
        minigunHeatLastMs.put(id, nowMs);
    }

    /**
     * Rayon sans degats : attire le joueur vers le bloc ou l'entite le plus proche sur la ligne de mire.
     */
    private void fireGrapple(Player shooter, WeaponProfile w) {
        Location eye = shooter.getEyeLocation();
        World world = eye.getWorld();
        Vector dir = eye.getDirection();
        double maxRange = w.effectiveRange(shooter);

        RayTraceResult entityHit = world.rayTraceEntities(eye, dir, maxRange, 0.45,
                e -> e instanceof LivingEntity && !e.getUniqueId().equals(shooter.getUniqueId()));
        RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, maxRange, FluidCollisionMode.NEVER, true);

        double distEntity = Double.MAX_VALUE;
        double distBlock = Double.MAX_VALUE;
        if (entityHit != null && entityHit.getHitEntity() != null && entityHit.getHitPosition() != null) {
            distEntity = eye.toVector().distance(entityHit.getHitPosition());
        }
        if (blockHit != null && blockHit.getHitBlock() != null && blockHit.getHitPosition() != null) {
            distBlock = eye.toVector().distance(blockHit.getHitPosition());
        }

        Location target = null;
        if (distEntity < distBlock && distEntity < maxRange && entityHit != null && entityHit.getHitEntity() instanceof LivingEntity le) {
            target = le.getLocation().add(0, Math.min(le.getHeight() * 0.45, 1.1), 0);
        } else if (blockHit != null && blockHit.getHitBlock() != null && distBlock < maxRange) {
            Vector hp = blockHit.getHitPosition();
            target = new Location(world, hp.getX(), hp.getY(), hp.getZ());
        }

        if (target == null) {
            I18n.actionBar(shooter, NamedTextColor.RED, "weapon.grapple_nothing");
            return;
        }

        Vector from = shooter.getLocation().toVector().add(new Vector(0, 0.12, 0));
        Vector delta = target.toVector().subtract(from);
        double len = delta.length();
        if (len < 0.75) {
            I18n.actionBar(shooter, NamedTextColor.GRAY, "weapon.grapple_too_close");
            return;
        }
        delta.normalize();
        double strength = Math.min(2.35, 0.48 + len * 0.056);
        shooter.setVelocity(delta.multiply(strength));

        Location step = eye.clone();
        Vector advance = dir.clone().normalize().multiply(0.35);
        for (int i = 0; i < Math.min(40, (int) (len / 0.35)); i++) {
            step.add(advance);
            world.spawnParticle(Particle.ENCHANT, step, 2, 0.06, 0.06, 0.06, 0.001);
        }
        world.playSound(eye, Sound.ENTITY_BAT_TAKEOFF, 0.45f, 1.35f);
    }

    private void handleNuclearStrike(Player player, WeaponProfile w, ItemStack main) {
        if (nuclearTasks.containsKey(player.getUniqueId())) {
            player.sendMessage(I18n.component(player, NamedTextColor.RED, "weapon.nuke_in_flight"));
            return;
        }
        if (reloadSessions.containsKey(player.getUniqueId())) {
            I18n.actionBar(player, NamedTextColor.YELLOW, "weapon.reload_in_progress");
            return;
        }
        int clip = getClip(player, w, main);
        if (clip <= 0) {
            startReload(player, w, main);
            return;
        }
        if (isWeaponBroken(main)) {
            I18n.actionBar(player, NamedTextColor.RED, "weapon.broken");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 0.8f);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastShotTimeMs.getOrDefault(player.getUniqueId(), 0L) < w.cooldownTicks() * 50L) {
            return;
        }
        RayTraceResult rt = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), 128,
                FluidCollisionMode.NEVER, true);
        if (rt == null || rt.getHitBlock() == null) {
            player.sendMessage(I18n.component(player, NamedTextColor.RED, "weapon.nuke_aim_block"));
            return;
        }
        Location target = rt.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        setClip(player, w, clip - 1, main);
        lastShotTimeMs.put(player.getUniqueId(), now);
        sendAmmoActionBar(player, w, main);
        wearWeaponOnShot(player, player.getInventory().getItemInMainHand(), w);

        int chargeTicks = Math.max(80, plugin.getConfig().getInt("weapons.nuclear-charge-ticks", 400));
        double radius = plugin.getConfig().getDouble("weapons.nuclear-radius", 14.0);
        double damage = plugin.getConfig().getDouble("weapons.nuclear-damage", 48.0);
        World world = target.getWorld();
        if (world == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        final int[] left = {chargeTicks};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                holder[0].cancel();
                nuclearTasks.remove(uuid);
                return;
            }
            left[0]--;
            showNuclearWarningZone(world, target, radius, left[0]);
            if (left[0] % 20 == 0 && left[0] > 0) {
                int sec = (left[0] + 19) / 20;
                I18n.broadcast(NamedTextColor.DARK_RED, "weapon.nuke_broadcast_tick",
                        player.getName(), sec, target.getBlockX(), target.getBlockY(), target.getBlockZ());
            }
            I18n.actionBar(player, NamedTextColor.RED, "weapon.nuke_missile_hud", left[0]);
            if (left[0] <= 0) {
                detonateNuclear(target, radius, damage, player);
                holder[0].cancel();
                nuclearTasks.remove(uuid);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.55f);
            }
        }, 1L, 1L);
        nuclearTasks.put(uuid, holder[0]);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.45f);
        int secTotal = (chargeTicks + 19) / 20;
        I18n.broadcast(NamedTextColor.DARK_RED, "weapon.nuke_broadcast_armed",
                player.getName(), target.getBlockX(), target.getBlockY(), target.getBlockZ(), secTotal);
    }

    private void showNuclearWarningZone(World world, Location center, double radius, int ticksLeft) {
        double r = radius + 0.5;
        for (double a = 0; a < Math.PI * 2; a += Math.PI / 36) {
            double x = center.getX() + Math.cos(a) * r;
            double z = center.getZ() + Math.sin(a) * r;
            Location p = new Location(world, x, center.getY(), z);
            world.spawnParticle(Particle.FLAME, p, 2, 0.05, 0.2, 0.05, 0.01);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, p.clone().add(0, 1.2, 0), 1, 0.05, 0.1, 0.05, 0);
        }
        world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1, 0), 6, 0.2, 0.5, 0.2, 0.02);
    }

    private void detonateNuclear(Location center, double radius, double damage, Player attacker) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, center, 8, radius * 0.25, 0.5, radius * 0.25, 0.02);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
        world.createExplosion(center, 0f, false, false);
        for (LivingEntity e : world.getNearbyLivingEntities(center, radius)) {
            if (e == attacker) {
                continue;
            }
            if (zoneManager != null && !zoneManager.isCombatAllowed(e.getLocation())) {
                continue;
            }
            double dist = e.getLocation().distance(center);
            double falloff = 1.0 - (dist / radius) * 0.75;
            if (falloff <= 0) {
                continue;
            }
            double dealt = damage * falloff * ThreadLocalRandom.current().nextDouble(0.92, 1.08);
            dealt = applyArmorReduction(e, WeaponDamageType.EXPLOSIVE, dealt);
            e.damage(dealt, attacker);
        }
        Bukkit.broadcast(Component.text("[DPMR] Impact nucleaire ! Zone detruite (degats uniquement).", NamedTextColor.GOLD));
    }

    private void launchShotgunFive(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        double dmg = projectileDamage(shooter, w, weapon, st);
        for (int i = 0; i < 5; i++) {
            Location spawn = offsetSpawn(shooter);
            Vector dir = applySpread(shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), spread * 0.88);
            Snowball sb = spawn.getWorld().spawn(spawn, Snowball.class);
            tagProjectile(sb, shooter, dmg, weapon);
            sb.setVelocity(dir.multiply(w.projectileSpeed() * 0.92));
            sb.setShooter(shooter);
        }
        trailBurst(shooter, shooter.getEyeLocation(), w);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void fireHitscanCross(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        Location eye = shooter.getEyeLocation();
        World world = eye.getWorld();
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        double range = w.effectiveRange(shooter) * WeaponUpgradeEffects.rangeMultiplier(weapon, st);
        double perDirectionDamage = (w.effectiveDamage(shooter) * WeaponUpgradeEffects.damageMultiplier(weapon, st)) / 4.0;

        Vector forward = eye.getDirection().clone().setY(0);
        if (forward.lengthSquared() < 1.0e-6) {
            forward = new Vector(0, 0, 1);
        } else {
            forward.normalize();
        }
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        Vector[] bases = new Vector[]{
                forward,
                forward.clone().multiply(-1),
                right,
                right.clone().multiply(-1)
        };

        for (Vector base : bases) {
            Vector dir = applySpread(eye, base, spread);
            RayTraceResult hit = world.rayTraceEntities(
                    eye,
                    dir,
                    range,
                    0.35,
                    entity -> entity instanceof LivingEntity && entity != shooter
            );
            drawBeam(shooter, eye, dir, range, world, w);
            if (hit != null && hit.getHitEntity() instanceof LivingEntity living) {
                boolean headshot = weaponAllowsHeadshot(w) && isHeadshotPosition(living, hit.getHitPosition());
                double mult = ThreadLocalRandom.current().nextDouble(0.88, 1.12);
                if (headshot) {
                    mult *= HEADSHOT_DAMAGE_MULTIPLIER;
                    world.spawnParticle(Particle.CRIT, living.getEyeLocation(), 14, 0.12, 0.08, 0.12, 0.02);
                }
                double dealt = perDirectionDamage * mult;
                dealt = applyArmorReduction(living, w.damageType(), dealt);
                living.damage(dealt, shooter);
                world.spawnParticle(Particle.DAMAGE_INDICATOR, living.getLocation().add(0, 1, 0), 4, 0.15, 0.3, 0.15, 0.01);
                world.spawnParticle(shotParticle(shooter, w), living.getLocation().add(0, 1, 0), w.trailDensity(), 0.2, 0.2, 0.2, 0.02);
                applyOnHitUpgradeEffects(shooter, living, st, dealt, false);
                if (headshot) {
                    notifyHeadshot(shooter);
                }
            }
        }
        powerupBlockManager.tryHitscanImpact(shooter, eye, eye.getDirection(), range);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void launchBomb(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        BombUpgradeState bs = BombUpgradeState.read(weapon, plugin);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        int extra = BombUpgradeEffects.extraBombsPerShot(weapon, bs);
        int total = Math.max(1, 1 + extra);
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        double dmg = projectileDamage(shooter, w, weapon, st);
        int maxB = BombUpgradeEffects.maxBounces(weapon, bs);
        for (int n = 0; n < total; n++) {
            Location spawn = offsetSpawn(shooter);
            Vector dir = applySpread(shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), spread * (0.88 + n * 0.04));
            Snowball sb = spawn.getWorld().spawn(spawn, Snowball.class);
            tagBombProjectile(sb, shooter, dmg, weapon, bs, maxB);
            sb.setVelocity(dir.multiply(w.projectileSpeed() * 0.88));
            sb.setShooter(shooter);
        }
        trailBurst(shooter, shooter.getEyeLocation(), w);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void launchClio3(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        double dmg = projectileDamage(shooter, w, weapon, st);
        Location spawn = offsetSpawn(shooter);
        Vector dir = applySpread(shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), spread);
        Snowball sb = spawn.getWorld().spawn(spawn, Snowball.class);
        tagClio3Projectile(sb, shooter, dmg, weapon);
        sb.setVelocity(dir.multiply(w.projectileSpeed()));
        sb.setShooter(shooter);
        trailBurst(shooter, shooter.getEyeLocation(), w);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void launchMortar(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        MortarUpgradeState ms = MortarUpgradeState.read(weapon, plugin);
        double dmg = projectileDamage(shooter, w, weapon, st);
        Location eye = shooter.getEyeLocation();
        double maxRange = w.effectiveRange(shooter) * WeaponUpgradeEffects.rangeMultiplier(weapon, st);
        double spreadDeg = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        Vector aimDir = applySpread(eye, eye.getDirection(), spreadDeg * 0.32);
        Location target = findMortarAimLocation(eye, aimDir, maxRange);
        Location spawn = offsetSpawn(shooter);
        int shells = 1 + MortarUpgradeEffects.extraShellsPerShot(ms);
        Vector eyeDir = aimDir.clone().normalize();
        Vector lateral = new Vector(-eyeDir.getZ(), 0, eyeDir.getX());
        if (lateral.lengthSquared() < 1e-6) {
            lateral = new Vector(1, 0, 0);
        } else {
            lateral.normalize();
        }
        double spread = shells > 1 ? 0.55 + 0.12 * Math.max(1, ms.tier()) : 0;
        for (int i = 0; i < shells; i++) {
            Location aim = target.clone().add(lateral.clone().multiply(spread * (i - (shells - 1) / 2.0)));
            Snowball sb = spawn.getWorld().spawn(spawn, Snowball.class);
            tagMortarProjectile(sb, shooter, dmg, weapon, ms);
            Vector vel = computeMortarVelocity(spawn, aim, MORTAR_PROJECTILE_GRAVITY).multiply(0.92 + w.projectileSpeed() * 0.11);
            sb.setVelocity(vel);
            sb.setShooter(shooter);
        }
        trailBurst(shooter, shooter.getEyeLocation(), w);
        registerRecoil(shooter, w, previousShotMs);
    }

    private static Location findMortarAimLocation(Location eye, Vector direction, double maxRange) {
        World world = eye.getWorld();
        if (world == null) {
            return eye;
        }
        Vector dir = direction.clone().normalize();
        RayTraceResult rt = world.rayTraceBlocks(eye, dir, maxRange, FluidCollisionMode.NEVER, true);
        if (rt != null && rt.getHitBlock() != null && rt.getHitPosition() != null) {
            return rt.getHitPosition().toLocation(world);
        }
        return eye.clone().add(dir.multiply(maxRange));
    }

    /**
     * Vitesse initiale pour atteindre {@code to} depuis {@code from} avec gravite constante par tick (approx. snowball).
     */
    private static Vector computeMortarVelocity(Location from, Location to, double gravityPerTick) {
        Vector d = to.toVector().subtract(from.toVector());
        double horiz = Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ());
        if (horiz < 0.08) {
            horiz = 0.08;
        }
        double flightTicks = 10 + horiz * 0.92;
        flightTicks = Math.max(12, Math.min(50, flightTicks));
        double T = flightTicks;
        double g = gravityPerTick;
        double vy = (d.getY() + 0.5 * g * T * T) / T;
        Vector flat = new Vector(d.getX(), 0, d.getZ());
        flat.normalize().multiply(horiz / T);
        return new Vector(flat.getX(), vy, flat.getZ());
    }

    private void tagMortarProjectile(Snowball sb, LivingEntity shooter, double dmg, ItemStack weapon, MortarUpgradeState ms) {
        tagProjectile(sb, shooter, dmg, weapon);
        sb.getPersistentDataContainer().set(keyProjKind, PersistentDataType.STRING, PROJ_KIND_MORTAR);
        MortarUpgradeState.writePayload(sb.getPersistentDataContainer(), ms, plugin);
    }

    private void explodeMortar(Location center, double centerDamage, UUID ownerId, LivingEntity damager,
                               WeaponUpgradeState weaponSt, MortarUpgradeState mortarSt) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double radius = 3.45;
        if (!mortarSt.isEmpty() && mortarSt.path() == MortarUpgradePath.INCENDIARY && mortarSt.tier() >= 3) {
            radius += 0.32 * (mortarSt.tier() - 2);
        }
        double dmgMul = MortarUpgradeEffects.incendiaryDamageMul(mortarSt);
        double baseDmg = centerDamage * dmgMul;
        world.spawnParticle(Particle.EXPLOSION, center, 2, 0.18, 0.18, 0.18, 0.02);
        world.spawnParticle(Particle.SMOKE, center, 28, 0.55, 0.35, 0.55, 0.04);
        if (!mortarSt.isEmpty() && mortarSt.path() == MortarUpgradePath.ACID) {
            world.spawnParticle(Particle.DRIPPING_DRIPSTONE_LAVA, center, 35, 0.65, 0.35, 0.65, 0.04);
            world.spawnParticle(Particle.SNEEZE, center, 28, 0.55, 0.3, 0.55, 0.08);
            world.spawnParticle(Particle.WITCH, center, 40, 0.7, 0.35, 0.7, 0.12);
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, 22, 0.5, 0.25, 0.5, 0.02);
            world.spawnParticle(Particle.DUST, center, 45, 0.55, 0.28, 0.55, 0.02,
                    new Particle.DustOptions(Color.fromRGB(55, 210, 95), 1.45f));
            world.playSound(center, Sound.BLOCK_BREWING_STAND_BREW, 0.9f, 0.65f);
            world.playSound(center, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 0.35f, 1.8f);
        }
        if (!mortarSt.isEmpty() && mortarSt.path() == MortarUpgradePath.INCENDIARY) {
            int t = mortarSt.tier();
            world.spawnParticle(Particle.FLAME, center, 32 + t * 10, 0.65, 0.45, 0.65, 0.05);
            world.spawnParticle(Particle.SMALL_FLAME, center, 40 + t * 8, 0.55, 0.4, 0.55, 0.02);
            world.spawnParticle(Particle.LAVA, center, 8, 0.35, 0.15, 0.35, 0);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 18 + t * 5, 0.45, 0.25, 0.45, 0.02);
            world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 0.55f, 0.75f);
            world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 0.4f, 1.1f);
        }
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.88f, 0.78f);
        world.createExplosion(center, 0f, false, false);
        Player shooterPlayer = damager instanceof Player p ? p : null;
        for (LivingEntity e : world.getNearbyLivingEntities(center, radius)) {
            if (e.getUniqueId().equals(ownerId)) {
                continue;
            }
            double dist = e.getLocation().distance(center);
            double falloff = 1.0 - (dist / radius) * 0.68;
            if (falloff <= 0) {
                continue;
            }
            double dealt = baseDmg * falloff * ThreadLocalRandom.current().nextDouble(0.9, 1.1);
            dealt = applyArmorReduction(e, WeaponDamageType.EXPLOSIVE, dealt);
            if (damager != null) {
                e.damage(dealt, damager);
                if (shooterPlayer != null) {
                    applyOnHitUpgradeEffects(shooterPlayer, e, weaponSt, dealt, false);
                }
            } else {
                e.damage(dealt);
            }
            applyMortarSecondaryEffects(e, falloff, ownerId, damager, mortarSt, shooterPlayer, weaponSt);
        }
        if (!mortarSt.isEmpty() && mortarSt.path() == MortarUpgradePath.ACID) {
            startMortarAcidCloud(center, ownerId, damager, weaponSt, mortarSt);
        }
        if (!mortarSt.isEmpty() && mortarSt.path() == MortarUpgradePath.INCENDIARY && mortarSt.tier() >= 2) {
            startMortarLingeringFlame(center, ownerId, damager, weaponSt, mortarSt);
        }
    }

    private void applyMortarSecondaryEffects(LivingEntity e, double falloff, UUID ownerId, LivingEntity damager,
                                             MortarUpgradeState ms, Player shooterPlayer, WeaponUpgradeState weaponSt) {
        if (e.getUniqueId().equals(ownerId) || ms.isEmpty()) {
            return;
        }
        if (zoneManager != null && !zoneManager.isCombatAllowed(e.getLocation())) {
            return;
        }
        switch (ms.path()) {
            case INCENDIARY -> {
                double burn = MortarUpgradeEffects.incendiaryBurnHalfHearts(ms) * falloff;
                if (burn > 0.25 && damager != null) {
                    e.damage(burn * ThreadLocalRandom.current().nextDouble(0.92, 1.08), damager);
                    if (shooterPlayer != null) {
                        applyOnHitUpgradeEffects(shooterPlayer, e, weaponSt, burn, false);
                    }
                }
                int ft = (int) (MortarUpgradeEffects.incendiaryFireTicks(ms) * falloff);
                if (ft > 0) {
                    e.setFireTicks(Math.max(e.getFireTicks(), ft));
                }
                e.getWorld().spawnParticle(Particle.FLAME, e.getLocation().add(0, 1.0, 0), 6 + ms.tier() * 2,
                        0.25, 0.35, 0.25, 0.015);
            }
            case ACID -> {
                int t = ms.tier();
                double cor = MortarUpgradeEffects.acidCorrosionHalfHearts(ms) * falloff;
                if (cor > 0.12 && damager != null) {
                    e.damage(cor * ThreadLocalRandom.current().nextDouble(0.93, 1.07), damager);
                    if (shooterPlayer != null) {
                        applyOnHitUpgradeEffects(shooterPlayer, e, weaponSt, cor, false);
                    }
                }
                int poisonDur = 85 + t * 38;
                int poisonAmp = t >= 4 ? 1 : 0;
                e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDur, poisonAmp, false, true, true));
                if (t >= 2) {
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 45 + t * 18, t >= 4 ? 1 : 0, false, true, true));
                }
                if (t >= 3) {
                    e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 50 + t * 12, 0, false, false, true));
                    e.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 55 + t * 14, 0, false, true, true));
                }
                if (t >= 5 && e instanceof Monster) {
                    e.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 48, 0, false, true, true));
                }
                e.getWorld().spawnParticle(Particle.SNEEZE, e.getLocation().add(0, 1.1, 0), 4 + t, 0.3, 0.2, 0.3, 0.02);
                e.getWorld().spawnParticle(Particle.DUST, e.getLocation().add(0, 0.9, 0), 8, 0.35, 0.25, 0.35, 0.01,
                        new Particle.DustOptions(Color.fromRGB(50, 200, 90), 1.2f));
            }
            case BARRAGE -> {
            }
        }
    }

    private void startMortarAcidCloud(Location center, UUID ownerId, LivingEntity damager,
                                      WeaponUpgradeState weaponSt, MortarUpgradeState ms) {
        World world = center.getWorld();
        if (world == null || ms.path() != MortarUpgradePath.ACID) {
            return;
        }
        int tier = ms.tier();
        int pulses = 4 + tier * 2;
        double cloudRadius = 2.0 + tier * 0.38;
        double dot = 0.75 + tier * 0.38;
        Player shooterPlayer = damager instanceof Player p ? p : null;
        final int[] k = {0};
        BukkitTask[] ref = new BukkitTask[1];
        ref[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            k[0]++;
            if (k[0] > pulses || center.getWorld() == null || !center.getWorld().equals(world)) {
                if (ref[0] != null) {
                    ref[0].cancel();
                }
                return;
            }
            world.spawnParticle(Particle.WITCH, center, 22 + tier * 5, cloudRadius * 0.45, 0.35, cloudRadius * 0.45, 0.1);
            world.spawnParticle(Particle.SNEEZE, center, 12 + tier * 3, cloudRadius * 0.4, 0.25, cloudRadius * 0.4, 0.06);
            world.spawnParticle(Particle.DUST, center, 28 + tier * 6, cloudRadius * 0.5, 0.3, cloudRadius * 0.5, 0.02,
                    new Particle.DustOptions(Color.fromRGB(40, 220, 110), 1.5f));
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, 16, cloudRadius * 0.42, 0.22, cloudRadius * 0.42, 0.03);
            world.playSound(center, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.22f + tier * 0.03f, 0.8f + tier * 0.05f);
            for (LivingEntity ent : world.getNearbyLivingEntities(center, cloudRadius)) {
                if (ent.getUniqueId().equals(ownerId)) {
                    continue;
                }
                if (zoneManager != null && !zoneManager.isCombatAllowed(ent.getLocation())) {
                    continue;
                }
                double dealt = dot * ThreadLocalRandom.current().nextDouble(0.88, 1.08);
                dealt = applyArmorReduction(ent, WeaponDamageType.EXPLOSIVE, dealt);
                if (damager != null) {
                    ent.damage(dealt, damager);
                    if (shooterPlayer != null) {
                        applyOnHitUpgradeEffects(shooterPlayer, ent, weaponSt, dealt, false);
                    }
                }
                int cloudPoison = 25 + tier * 12;
                ent.addPotionEffect(new PotionEffect(PotionEffectType.POISON, cloudPoison, tier >= 4 ? 1 : 0, false, true, true));
            }
        }, 8L, 8L);
    }

    private void startMortarLingeringFlame(Location center, UUID ownerId, LivingEntity damager,
                                           WeaponUpgradeState weaponSt, MortarUpgradeState ms) {
        World world = center.getWorld();
        if (world == null || ms.path() != MortarUpgradePath.INCENDIARY) {
            return;
        }
        int tier = ms.tier();
        double r = 2.1 + tier * 0.42;
        int waves = 5 + tier * 2;
        double emberDmg = 0.35 + tier * 0.22;
        Player shooterPlayer = damager instanceof Player p ? p : null;
        final int[] wv = {0};
        BukkitTask[] ref = new BukkitTask[1];
        ref[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            wv[0]++;
            if (wv[0] > waves || center.getWorld() == null || !center.getWorld().equals(world)) {
                if (ref[0] != null) {
                    ref[0].cancel();
                }
                return;
            }
            world.spawnParticle(Particle.FLAME, center, 20 + tier * 5, r * 0.55, 0.2, r * 0.55, 0.03);
            world.spawnParticle(Particle.SMALL_FLAME, center, 26 + tier * 4, r * 0.5, 0.18, r * 0.5, 0.015);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 8, r * 0.4, 0.12, r * 0.4, 0.01);
            world.playSound(center, Sound.BLOCK_FIRE_AMBIENT, 0.25f, 1.1f);
            for (LivingEntity ent : world.getNearbyLivingEntities(center, r)) {
                if (ent.getUniqueId().equals(ownerId)) {
                    continue;
                }
                double d = emberDmg * ThreadLocalRandom.current().nextDouble(0.9, 1.1);
                d = applyArmorReduction(ent, WeaponDamageType.EXPLOSIVE, d);
                if (damager != null) {
                    ent.damage(d, damager);
                    if (shooterPlayer != null) {
                        applyOnHitUpgradeEffects(shooterPlayer, ent, weaponSt, d, false);
                    }
                }
                ent.setFireTicks(Math.max(ent.getFireTicks(), 25 + tier * 8));
            }
        }, 10L, 10L);
    }

    private void cancelRocketHoming(UUID projectileId) {
        BukkitTask t = rocketHomingTasks.remove(projectileId);
        if (t != null) {
            t.cancel();
        }
    }

    private LivingEntity findNearestRocketTarget(Location from, UUID ownerId, double range) {
        World w = from.getWorld();
        if (w == null) {
            return null;
        }
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity e : w.getNearbyLivingEntities(from, range)) {
            if (e.getUniqueId().equals(ownerId) || !e.isValid() || e.isDead()) {
                continue;
            }
            if (e instanceof ArmorStand) {
                continue;
            }
            double d = e.getLocation().distanceSquared(from);
            if (d < bestD && d > 0.5) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private void startRocketHoming(Snowball sb, UUID ownerId, int tier, LivingEntity shooter) {
        double range = RocketUpgradeEffects.guidedAcquisitionRange(tier);
        double lerp = RocketUpgradeEffects.guidedTurnLerp(tier);
        UUID sbid = sb.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!sb.isValid()) {
                cancelRocketHoming(sbid);
                return;
            }
            Location loc = sb.getLocation();
            World world = loc.getWorld();
            if (world == null) {
                cancelRocketHoming(sbid);
                return;
            }
            LivingEntity target = findNearestRocketTarget(loc, ownerId, range);
            if (target == null) {
                return;
            }
            Vector aim = target.getEyeLocation().toVector().subtract(loc.toVector());
            double len = aim.length();
            if (len < 0.12) {
                return;
            }
            aim.normalize();
            Vector v = sb.getVelocity();
            double speed = Math.max(0.75, Math.min(2.85, v.length() < 0.08 ? 1.35 : v.length()));
            Vector cur = v.lengthSquared() > 1e-5 ? v.clone().normalize() : aim.clone();
            Vector blended = cur.multiply(1.0 - lerp).add(aim.clone().multiply(lerp)).normalize().multiply(speed);
            sb.setVelocity(blended);
            world.spawnParticle(Particle.END_ROD, loc, 2, 0.1, 0.1, 0.1, 0.012);
            world.spawnParticle(Particle.FIREWORK, loc, 2, 0.12, 0.12, 0.12, 0.02);
        }, 1L, 1L);
        rocketHomingTasks.put(sbid, task);
    }

    private void launchRocket(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        RocketUpgradeState rs = RocketUpgradeState.read(weapon, plugin);
        if (!rs.isEmpty() && rs.path() == RocketUpgradePath.DRONE) {
            deployCombatDrone(shooter, w, weapon, rs);
            registerRecoil(shooter, w, previousShotMs);
            return;
        }
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double dmg = projectileDamage(shooter, w, weapon, st);
        Location spawn = offsetSpawn(shooter);
        Snowball sb = spawn.getWorld().spawn(spawn, Snowball.class);
        tagRocketProjectile(sb, shooter, dmg, weapon, rs);
        Vector vel = shooter.getEyeLocation().getDirection().normalize().multiply(w.projectileSpeed());
        sb.setVelocity(vel);
        sb.setShooter(shooter);
        if (!rs.isEmpty() && rs.path() == RocketUpgradePath.GUIDED) {
            startRocketHoming(sb, shooter.getUniqueId(), rs.tier(), shooter);
        }
        trailBurst(shooter, shooter.getEyeLocation(), w);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void npcLaunchRocket(LivingEntity npc, WeaponProfile w, ItemStack weapon, double npcDmgScale) {
        double s = npcDmgScale <= 0 || Double.isNaN(npcDmgScale) ? 1.0 : Math.min(4.0, npcDmgScale);
        RocketUpgradeState rs = RocketUpgradeState.read(weapon, plugin);
        if (!rs.isEmpty() && rs.path() == RocketUpgradePath.DRONE) {
            deployCombatDrone(npc, w, weapon, rs);
            trailBurstNpc(npc.getEyeLocation(), w);
            return;
        }
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double dmg = w.baseDamage() * WeaponUpgradeEffects.damageMultiplier(weapon, st) * s;
        Location spawn = offsetSpawnLiving(npc);
        Snowball sb = spawn.getWorld().spawn(spawn, Snowball.class);
        tagRocketProjectile(sb, npc, dmg, weapon, rs);
        Vector vel = npc.getEyeLocation().getDirection().normalize().multiply(w.projectileSpeed());
        sb.setVelocity(vel);
        sb.setShooter(npc);
        if (!rs.isEmpty() && rs.path() == RocketUpgradePath.GUIDED) {
            startRocketHoming(sb, npc.getUniqueId(), rs.tier(), npc);
        }
        trailBurstNpc(spawn, w);
    }

    private void tagRocketProjectile(Snowball sb, LivingEntity shooter, double dmg, ItemStack weapon, RocketUpgradeState rs) {
        tagProjectile(sb, shooter, dmg, weapon);
        sb.getPersistentDataContainer().set(keyProjKind, PersistentDataType.STRING, PROJ_KIND_ROCKET);
        RocketUpgradeState.writePayload(sb.getPersistentDataContainer(), rs, plugin);
    }

    private void explodeRocket(Location center, double centerDamage, UUID ownerId, LivingEntity damager,
                               WeaponUpgradeState weaponSt, RocketUpgradeState rocketSt) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double radius = 4.15 * RocketUpgradeEffects.devastatorRadiusMul(rocketSt);
        double baseDmg = centerDamage * RocketUpgradeEffects.devastatorDamageMul(rocketSt);
        world.spawnParticle(Particle.EXPLOSION, center, 4, 0.35, 0.35, 0.35, 0.03);
        world.spawnParticle(Particle.LAVA, center, 18, 0.55, 0.35, 0.55, 0.04);
        world.spawnParticle(Particle.FLAME, center, 32, 0.7, 0.45, 0.7, 0.05);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.05f, 0.72f);
        world.createExplosion(center, 0f, false, false);
        Player shooterPlayer = damager instanceof Player p ? p : null;
        for (LivingEntity e : world.getNearbyLivingEntities(center, radius)) {
            if (e.getUniqueId().equals(ownerId)) {
                continue;
            }
            if (zoneManager != null && !zoneManager.isCombatAllowed(e.getLocation())) {
                continue;
            }
            double dist = e.getLocation().distance(center);
            double falloff = 1.0 - (dist / radius) * 0.65;
            if (falloff <= 0) {
                continue;
            }
            double dealt = baseDmg * falloff * ThreadLocalRandom.current().nextDouble(0.9, 1.1);
            dealt = applyArmorReduction(e, WeaponDamageType.EXPLOSIVE, dealt);
            if (damager != null) {
                e.damage(dealt, damager);
                if (shooterPlayer != null) {
                    applyOnHitUpgradeEffects(shooterPlayer, e, weaponSt, dealt, false);
                }
            } else {
                e.damage(dealt);
            }
        }
    }

    private void deployCombatDrone(LivingEntity shooter, WeaponProfile w, ItemStack weapon, RocketUpgradeState rs) {
        if (shooter instanceof Player p) {
            trailBurst(p, shooter.getEyeLocation(), w);
        } else {
            trailBurstNpc(shooter.getEyeLocation(), w);
        }
        int tier = rs.tier();
        Location eye = shooter.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location start = eye.clone().add(dir.clone().multiply(2.1)).add(0, 0.25, 0);
        World world = start.getWorld();
        if (world == null || !shooter.isValid()) {
            return;
        }

        ArmorStand drone = world.spawn(start, ArmorStand.class);
        drone.setMarker(true);
        drone.setInvisible(true);
        drone.setSmall(true);
        drone.setGravity(false);
        drone.setInvulnerable(true);
        drone.setBasePlate(false);
        drone.setPersistent(false);
        drone.setCollidable(false);
        drone.setCustomNameVisible(false);
        drone.getPersistentDataContainer().set(keyCombatDrone, PersistentDataType.BYTE, (byte) 1);

        TextDisplay label = world.spawn(start.clone().add(0, 0.58, 0), TextDisplay.class);
        label.text(Component.text("◆ DRONE ◆", NamedTextColor.GOLD, TextDecoration.BOLD));
        label.setBillboard(Display.Billboard.CENTER);
        label.setSeeThrough(false);
        label.setShadowed(true);
        label.setBackgroundColor(Color.fromARGB(130, 12, 14, 28));
        label.setLineWidth(200);
        label.setBrightness(new Display.Brightness(12, 12));
        label.setPersistent(false);
        label.setInterpolationDuration(2);
        label.setViewRange(56.0f);

        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        UUID ownerId = shooter.getUniqueId();
        int maxLife = RocketUpgradeEffects.droneLifetimeTicks(tier);
        long firePeriod = RocketUpgradeEffects.droneFirePeriodTicks(tier);
        double gunRange = RocketUpgradeEffects.droneGunRange(tier);
        double shotDmg = RocketUpgradeEffects.droneShotHalfHearts(tier);
        Player shooterPlayer = shooter instanceof Player pl ? pl : null;

        final Location[] hover = {start.clone()};
        final int[] tick = {0};
        BukkitTask[] ref = new BukkitTask[1];
        ref[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;
            if (!shooter.isValid()) {
                cleanupCombatDrone(drone, label, ref[0]);
                return;
            }
            if (!drone.isValid() || tick[0] > maxLife) {
                cleanupCombatDrone(drone, label, ref[0]);
                return;
            }
            World droneWorld = drone.getWorld();
            if (droneWorld == null) {
                cleanupCombatDrone(drone, label, ref[0]);
                return;
            }

            LivingEntity chase = findNearestRocketTarget(hover[0], ownerId, 38.0);
            Location goal;
            if (chase != null && chase.isValid()) {
                goal = chase.getLocation().add(0, 1.75, 0);
            } else {
                Location e = shooter.getEyeLocation();
                goal = e.clone().add(e.getDirection().normalize().multiply(2.6)).add(0, 0.4, 0);
            }
            Vector glide = goal.toVector().subtract(hover[0].toVector());
            double glen = glide.length();
            if (glen > 0.035) {
                double speed = Math.min(0.16, glen * 0.06);
                glide.normalize().multiply(speed);
                hover[0].add(glide);
            }

            Location bob = hover[0].clone().add(0, Math.sin(tick[0] * 0.085) * 0.12, 0);
            drone.teleport(bob);
            if (label.isValid()) {
                label.teleport(bob.clone().add(0, 0.72, 0));
            }

            Location gunLoc = bob.clone().add(0, 0.12, 0);
            boolean fireTick = tick[0] >= 4 && (tick[0] - 4) % firePeriod == 0;
            if (fireTick) {
                LivingEntity tgt = findNearestRocketTarget(bob, ownerId, gunRange);
                if (tgt != null) {
                    Vector to = tgt.getEyeLocation().toVector().subtract(gunLoc.toVector());
                    double dist = to.length();
                    if (dist > 0.25 && dist < gunRange + 1.5) {
                        Vector toN = to.clone().normalize();
                        RayTraceResult blockHit = droneWorld.rayTraceBlocks(gunLoc, toN, Math.min(dist + 0.5, gunRange + 1.5),
                                FluidCollisionMode.NEVER, true);
                        double blockDist = blockHit != null && blockHit.getHitPosition() != null
                                ? gunLoc.toVector().distance(blockHit.getHitPosition()) : Double.MAX_VALUE;
                        if (blockDist >= dist - 0.35) {
                            boolean droneBlocked = zoneManager != null
                                    && zoneManager.isWeaponDamageBlockedAlongLine(gunLoc, tgt.getEyeLocation());
                            if (!droneBlocked) {
                                double dealt = shotDmg * ThreadLocalRandom.current().nextDouble(0.92, 1.08);
                                dealt = applyArmorReduction(tgt, WeaponDamageType.EXPLOSIVE, dealt);
                                tgt.damage(dealt, shooter);
                                if (shooterPlayer != null) {
                                    applyOnHitUpgradeEffects(shooterPlayer, tgt, st, dealt, false);
                                }
                            }
                            droneWorld.spawnParticle(Particle.CRIT, tgt.getEyeLocation(), 10, 0.18, 0.14, 0.18, 0.07);
                            droneWorld.playSound(gunLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.38f, 1.5f + tier * 0.04f);
                        }
                    }
                }
            }

            droneWorld.spawnParticle(Particle.SMALL_FLAME, gunLoc, 3, 0.06, 0.06, 0.06, 0.01);
            droneWorld.spawnParticle(Particle.END_ROD, gunLoc, 2, 0.04, 0.04, 0.04, 0.012);
            if (tick[0] % 6 == 0) {
                droneWorld.spawnParticle(Particle.WAX_ON, bob, 2, 0.22, 0.12, 0.22, 0.002);
            }
        }, 1L, 1L);
    }

    private void cleanupCombatDrone(ArmorStand drone, TextDisplay label, BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
        if (label != null && label.isValid()) {
            label.remove();
        }
        if (drone != null && drone.isValid()) {
            World dw = drone.getWorld();
            if (dw != null) {
                dw.spawnParticle(Particle.SMOKE, drone.getLocation().add(0, 0.35, 0), 14, 0.22, 0.18, 0.22, 0.025);
                dw.playSound(drone.getLocation(), Sound.ENTITY_BEE_STING, 0.25f, 1.4f);
            }
            drone.remove();
        }
    }

    private void tagClio3Projectile(Snowball sb, LivingEntity shooter, double dmg, ItemStack weapon) {
        tagProjectile(sb, shooter, dmg, weapon);
        sb.getPersistentDataContainer().set(keyProjKind, PersistentDataType.STRING, PROJ_KIND_CLIO3);
        sb.setInvisible(true);
        startClio3VisualTask(sb);
    }

    private void cleanupClio3Visuals(UUID projectileId) {
        Clio3VisualSession session = clio3VisualByProjectile.remove(projectileId);
        if (session == null) {
            return;
        }
        if (session.task != null) {
            session.task.cancel();
        }
        for (BlockDisplay d : session.displays) {
            if (d != null && d.isValid()) {
                d.remove();
            }
        }
    }

    private void startClio3VisualTask(Snowball sb) {
        World world = sb.getWorld();
        if (world == null) {
            return;
        }
        Location origin = sb.getLocation();
        float scale = 0.24f;
        Transformation tf = new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        );
        List<BlockDisplay> displays = new ArrayList<>(CLIO3_PARTS.size());
        for (Clio3Part part : CLIO3_PARTS) {
            BlockDisplay d = world.spawn(origin, BlockDisplay.class);
            d.setPersistent(false);
            d.setBlock(Bukkit.createBlockData(part.mat()));
            d.setTransformation(tf);
            displays.add(d);
        }
        UUID id = sb.getUniqueId();
        final Vector[] lastForward = new Vector[1];
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!sb.isValid()) {
                cleanupClio3Visuals(id);
                return;
            }
            Location loc = sb.getLocation();
            Vector vel = sb.getVelocity();
            Vector forward;
            if (vel.lengthSquared() > 1.0e-4) {
                forward = vel.clone().normalize();
                lastForward[0] = forward.clone();
            } else if (lastForward[0] != null) {
                forward = lastForward[0];
            } else {
                forward = sb.getLocation().getDirection();
                if (forward.lengthSquared() < 1.0e-6) {
                    forward = new Vector(0, 0, 1);
                } else {
                    forward.normalize();
                }
            }
            Vector worldUp = new Vector(0, 1, 0);
            Vector right = forward.clone().crossProduct(worldUp);
            if (right.lengthSquared() < 1.0e-8) {
                right = new Vector(1, 0, 0);
            } else {
                right.normalize();
            }
            Vector up = right.clone().crossProduct(forward);
            if (up.lengthSquared() < 1.0e-8) {
                up = new Vector(0, 1, 0);
            } else {
                up.normalize();
            }
            for (int i = 0; i < CLIO3_PARTS.size(); i++) {
                Clio3Part p = CLIO3_PARTS.get(i);
                Vector offset = right.clone().multiply(p.lx())
                        .add(up.clone().multiply(p.ly()))
                        .add(forward.clone().multiply(p.lz()));
                BlockDisplay disp = displays.get(i);
                if (disp.isValid()) {
                    disp.teleport(loc.clone().add(offset));
                }
            }
        }, 1L, 1L);
        clio3VisualByProjectile.put(id, new Clio3VisualSession(displays, task));
    }

    private void explodeClio3(Location center, double centerDamage, UUID ownerId, LivingEntity damager, WeaponUpgradeState st) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double radius = 5.85;
        world.spawnParticle(Particle.EXPLOSION, center, 5, 0.35, 0.35, 0.35, 0.02);
        world.spawnParticle(Particle.BLOCK, center, 48, 0.85, 0.4, 0.85, 0.15, Material.STONE.createBlockData());
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.05f, 0.82f);
        world.createExplosion(center, 0f, false, false);
        Player shooterPlayer = damager instanceof Player p ? p : null;
        for (LivingEntity e : world.getNearbyLivingEntities(center, radius)) {
            if (e.getUniqueId().equals(ownerId)) {
                continue;
            }
            if (zoneManager != null && !zoneManager.isCombatAllowed(e.getLocation())) {
                continue;
            }
            double dist = e.getLocation().distance(center);
            double falloff = 1.0 - (dist / radius) * 0.65;
            if (falloff <= 0) {
                continue;
            }
            double dealt = centerDamage * falloff * ThreadLocalRandom.current().nextDouble(0.9, 1.1);
            dealt = applyArmorReduction(e, WeaponDamageType.EXPLOSIVE, dealt);
            if (damager != null) {
                e.damage(dealt, damager);
                if (shooterPlayer != null) {
                    applyOnHitUpgradeEffects(shooterPlayer, e, st, dealt, false);
                }
            } else {
                e.damage(dealt);
            }
        }
    }

    private void tagBombProjectile(Snowball sb, LivingEntity shooter, double dmg, ItemStack weapon, BombUpgradeState bs, int maxBounces) {
        tagProjectile(sb, shooter, dmg, weapon);
        sb.getPersistentDataContainer().set(keyProjKind, PersistentDataType.STRING, PROJ_KIND_BOMB);
        if (maxBounces > 0) {
            sb.getPersistentDataContainer().set(keyBombBounceLeft, PersistentDataType.INTEGER, maxBounces);
        }
        if (!bs.isEmpty()) {
            sb.getPersistentDataContainer().set(keyBombPayload, PersistentDataType.STRING, bs.path().name() + ":" + bs.tier());
        }
    }

    private BombUpgradeState readBombPayloadFromPdc(PersistentDataContainer pdc) {
        String raw = pdc.get(keyBombPayload, PersistentDataType.STRING);
        if (raw == null || !raw.contains(":")) {
            return BombUpgradeState.NONE;
        }
        String[] p = raw.split(":", 2);
        BombUpgradePath path = BombUpgradePath.fromId(p[0]);
        try {
            int tier = Integer.parseInt(p[1]);
            if (path == null || tier <= 0) {
                return BombUpgradeState.NONE;
            }
            return new BombUpgradeState(path, Math.min(5, tier));
        } catch (NumberFormatException e) {
            return BombUpgradeState.NONE;
        }
    }

    private void explodeBomb(Location center, double baseDmg, UUID ownerId, LivingEntity damager, BombUpgradeState bs) {
        double rad = 3.8 * BombUpgradeEffects.radiusMul(bs);
        double dmg = baseDmg * BombUpgradeEffects.damageMul(bs);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, center, 3, 0.2, 0.2, 0.2, 0.02);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.95f, 1.1f);
        world.createExplosion(center, 0f, false, false);
        for (LivingEntity e : world.getNearbyLivingEntities(center, rad)) {
            if (e.getUniqueId().equals(ownerId)) {
                continue;
            }
            if (zoneManager != null && !zoneManager.isCombatAllowed(e.getLocation())) {
                continue;
            }
            double dist = e.getLocation().distance(center);
            double falloff = 1.0 - (dist / rad) * 0.7;
            if (falloff <= 0) {
                continue;
            }
            double dealt = dmg * falloff * ThreadLocalRandom.current().nextDouble(0.9, 1.1);
            dealt = applyArmorReduction(e, WeaponDamageType.EXPLOSIVE, dealt);
            if (damager != null) {
                e.damage(dealt, damager);
            } else {
                e.damage(dealt);
            }
        }
    }

    private WeaponUpgradeState hitscanUpgradeStateFor(WeaponProfile w, ItemStack weapon) {
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        if (w == WeaponProfile.REVOLVER) {
            RevolverUpgradeState rv = RevolverUpgradeState.read(weapon, plugin);
            if (!rv.isEmpty()) {
                return WeaponUpgradeState.NONE;
            }
        }
        return st;
    }

    private static Vector[] revolverSplitDirections(Vector incoming) {
        Vector base = incoming.clone().normalize();
        Vector[] out = new Vector[4];
        out[0] = base.clone();
        out[1] = base.clone().rotateAroundY(Math.toRadians(20));
        out[2] = base.clone().rotateAroundY(Math.toRadians(-20));
        out[3] = base.clone().add(new Vector(0, 0.45, 0)).normalize();
        return out;
    }

    private void revolverSplitAfterPlayerHit(LivingEntity shooter, Player shooterPlayer, Player penetrated,
                                             Vector hitWorld, Vector incomingDir, double perPellet, double remainingRange,
                                             World world, WeaponProfile w, WeaponUpgradeState st) {
        if (world == null || hitWorld == null) {
            return;
        }
        Location start = hitWorld.toLocation(world).add(incomingDir.clone().normalize().multiply(0.35));
        double splitDmg = perPellet * 0.38;
        for (Vector d : revolverSplitDirections(incomingDir)) {
            RayTraceResult r2 = world.rayTraceEntities(
                    start,
                    d,
                    remainingRange,
                    0.32,
                    entity -> entity instanceof LivingEntity le && le != shooter && le != penetrated
            );
            if (shooterPlayer != null) {
                drawBeam(shooterPlayer, start, d, remainingRange, world, w);
            } else {
                drawBeamNpc(start, d, remainingRange, world, w);
            }
            if (r2 != null && r2.getHitEntity() instanceof LivingEntity living) {
                double mult = ThreadLocalRandom.current().nextDouble(0.88, 1.1);
                double dealt = applyArmorReduction(living, w.damageType(), splitDmg * mult);
                living.damage(dealt, shooter);
                world.spawnParticle(Particle.DAMAGE_INDICATOR, living.getLocation().add(0, 1, 0), 3, 0.12, 0.25, 0.12, 0.01);
                world.spawnParticle(Particle.CRIT, living.getEyeLocation(), 6, 0.15, 0.12, 0.15, 0.04);
                if (shooterPlayer != null) {
                    applyOnHitUpgradeEffects(shooterPlayer, living, st, dealt, false);
                }
            }
        }
        world.playSound(start, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.35f, 1.55f);
    }

    private void fireHitscan(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        fireHitscan(shooter, w, weapon, previousShotMs, -1.0);
    }

    private void fireHitscan(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs, double charge01) {
        WeaponUpgradeState st = hitscanUpgradeStateFor(w, weapon);
        RevolverUpgradeState rv = w == WeaponProfile.REVOLVER ? RevolverUpgradeState.read(weapon, plugin) : RevolverUpgradeState.NONE;
        Location eye = shooter.getEyeLocation();
        World world = eye.getWorld();
        boolean charged = charge01 >= 0 && charge01 <= 1.0;
        double rMul = charged ? (0.36 + 0.64 * charge01) : 1.0;
        double dMul = charged ? (0.42 + 0.58 * charge01) : 1.0;
        double sMul = charged ? Math.max(0.28, 1.22 - 0.72 * charge01) : 1.0;
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs) * sMul;
        double range = w.effectiveRange(shooter) * WeaponUpgradeEffects.rangeMultiplier(weapon, st) * rMul;
        double perPellet = w.damagePerPellet(shooter) * WeaponUpgradeEffects.damageMultiplier(weapon, st) * dMul;

        boolean techMarked = false;
        if (st.path() == WeaponUpgradePath.TECH && st.tier() >= 5) {
            int c = techShotCount.merge(shooter.getUniqueId(), 1, Integer::sum);
            if (c % 6 == 0) {
                techMarked = true;
                perPellet *= 1.22;
            }
        }

        int pelletCount = w == WeaponProfile.REVOLVER ? RevolverUpgradeEffects.hitscanPellets(rv) : w.pellets();
        boolean splitTriggered = false;
        for (int i = 0; i < pelletCount; i++) {
            Vector dir = applySpread(eye, eye.getDirection(), spread);
            RayTraceResult hit = world.rayTraceEntities(
                    eye,
                    dir,
                    range,
                    0.35,
                    entity -> entity instanceof LivingEntity && entity != shooter
            );
            drawBeam(shooter, eye, dir, range, world, w);
            if (hit != null && hit.getHitEntity() instanceof LivingEntity living) {
                Vector hp = hit.getHitPosition();
                boolean blocked = hp != null && weaponShotBlockedBySafeLine(shooter, hp, world);
                boolean headshot = weaponAllowsHeadshot(w) && isHeadshotPosition(living, hp);
                double mult = ThreadLocalRandom.current().nextDouble(0.88, 1.12);
                if (st.path() == WeaponUpgradePath.ASSAULT && st.tier() >= 4 && ThreadLocalRandom.current().nextDouble() < 0.12) {
                    mult *= 1.35;
                }
                if (headshot) {
                    mult *= HEADSHOT_DAMAGE_MULTIPLIER;
                    world.spawnParticle(Particle.CRIT, living.getEyeLocation(), 14, 0.12, 0.08, 0.12, 0.02);
                }
                double dealt = perPellet * mult;
                dealt = applyArmorReduction(living, w.damageType(), dealt);
                if (!blocked) {
                    living.damage(dealt, shooter);
                    if (w == WeaponProfile.DIVISER_POUR_MIEUX_REGNER
                            || w == WeaponProfile.GHOST_DIVISER
                            || w == WeaponProfile.GHOST_LANCE_FLAMME) {
                        living.setFireTicks(Math.max(living.getFireTicks(), 80));
                        world.spawnParticle(Particle.FLAME, living.getLocation().add(0, 1, 0), 18, 0.2, 0.35, 0.2, 0.01);
                        world.playSound(living.getLocation(), Sound.ENTITY_BLAZE_HURT, 0.35f, 1.35f);
                    }
                    applyOnHitUpgradeEffects(shooter, living, st, dealt, techMarked);
                    if (headshot) {
                        notifyHeadshot(shooter);
                    }
                    if (st.path() == WeaponUpgradePath.ASSAULT && st.tier() >= 3) {
                        ricochetHitscan(world, shooter, living, perPellet * 0.35);
                    }
                    if (st.path() == WeaponUpgradePath.ASSAULT && st.tier() >= 5 && ThreadLocalRandom.current().nextDouble() < 0.12) {
                        pulseAreaDamage(shooter, living.getLocation().add(0, 0.5, 0), perPellet * 0.4, 2.6);
                    }
                    if (w == WeaponProfile.REVOLVER && rv.tier() >= 2) {
                        pulseAreaDamage(shooter, living.getLocation().add(0, 0.5, 0), perPellet * 0.42, 2.75);
                        world.spawnParticle(Particle.LAVA, living.getLocation().add(0, 0.6, 0), 6, 0.25, 0.2, 0.25, 0.02);
                    }
                    if (w == WeaponProfile.REVOLVER && rv.tier() >= 3 && living instanceof Player penetrated && !splitTriggered) {
                        Vector hitVec = hit.getHitPosition();
                        if (hitVec != null) {
                            splitTriggered = true;
                            double traveled = eye.toVector().distance(hitVec);
                            double remain = Math.max(1.0, range - traveled + 0.45);
                            revolverSplitAfterPlayerHit(shooter, shooter, penetrated, hitVec, dir, perPellet, remain, world, w, st);
                        }
                    }
                }
                world.spawnParticle(Particle.DAMAGE_INDICATOR, living.getLocation().add(0, 1, 0), 4, 0.15, 0.3, 0.15, 0.01);
                world.spawnParticle(shotParticle(shooter, w), living.getLocation().add(0, 1, 0), w.trailDensity(), 0.2, 0.2, 0.2, 0.02);
                if (w == WeaponProfile.COUTEAU_COMBAT && cosmeticsManager != null) {
                    CosmeticProfile k = cosmeticsManager.selectedKnifeProfile(shooter.getUniqueId());
                    if (k != null) {
                        if (k.particle() != null) {
                            world.spawnParticle(k.particle(), living.getLocation().add(0, 1.15, 0), 22, 0.38, 0.28, 0.38, 0.035);
                        }
                        if (k.knifeHitSound() != null) {
                            world.playSound(living.getLocation(), k.knifeHitSound(), 0.52f,
                                    ThreadLocalRandom.current().nextFloat(0.88f, 1.12f));
                        }
                    }
                }
            }
        }
        powerupBlockManager.tryHitscanImpact(shooter, eye, eye.getDirection(), range);
        registerRecoil(shooter, w, previousShotMs);
    }

    private static boolean isHeadshotPosition(LivingEntity target, Vector hitWorldPos) {
        if (hitWorldPos == null || target.getHeight() <= 0.45) {
            return false;
        }
        return hitWorldPos.getY() >= target.getLocation().getY() + target.getHeight() * HEADSHOT_HEIGHT_RATIO;
    }

    private static boolean weaponAllowsHeadshot(WeaponProfile w) {
        if (w == null) {
            return false;
        }
        if (w == WeaponProfile.COUTEAU_COMBAT) {
            return false;
        }
        return switch (w.fireMode()) {
            case HITSCAN, HITSCAN_BOW_CHARGE, HITSCAN_CROSS, PROJECTILE_SNOWBALL, PROJECTILE_ARROW, PROJECTILE_SHOTGUN_FIVE -> true;
            default -> false;
        };
    }

    private void notifyHeadshot(Player shooter) {
        I18n.actionBar(shooter, NamedTextColor.GOLD, "weapon.headshot");
    }

    private void applyOnHitUpgradeEffects(Player shooter, LivingEntity living, WeaponUpgradeState st, double dmgDealt, boolean techMarkedShot) {
        if (st.isEmpty()) {
            return;
        }
        if (st.path() == WeaponUpgradePath.SURVIVAL) {
            if (st.tier() >= 3) {
                double maxH = shooter.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                shooter.setHealth(Math.min(maxH, shooter.getHealth() + dmgDealt * 0.07));
            }
            if (st.tier() >= 4 && ThreadLocalRandom.current().nextDouble() < 0.15) {
                shooter.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60, 0, false, false, true));
            }
        }
        if (st.path() == WeaponUpgradePath.TECH) {
            if (st.tier() >= 3) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0, false, false, true));
            }
            if (st.tier() >= 4 && ThreadLocalRandom.current().nextDouble() < 0.18) {
                chainNearestLiving(shooter, living, dmgDealt * 0.15);
            }
            if (techMarkedShot && st.tier() >= 5) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, false));
            }
        }
    }

    private void ricochetHitscan(World world, Player shooter, LivingEntity first, double damage) {
        LivingEntity best = null;
        double bestD = 16 * 16;
        for (LivingEntity e : world.getNearbyLivingEntities(first.getLocation(), 4.0)) {
            if (e == shooter || e == first || !e.isValid()) {
                continue;
            }
            double d = e.getLocation().distanceSquared(first.getLocation());
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best != null) {
            Location from = first.getLocation().clone().add(0, Math.min(first.getHeight() * 0.45, 1.2), 0);
            if (zoneManager != null && zoneManager.isWeaponDamageBlockedAlongLine(from, best.getEyeLocation())) {
                return;
            }
            double mult = ThreadLocalRandom.current().nextDouble(0.9, 1.08);
            double dealt = applyArmorReduction(best, WeaponDamageType.ASSAULT_RIFLE, damage * mult);
            best.damage(dealt, shooter);
            world.spawnParticle(Particle.CRIT, best.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2, 0.02);
        }
    }

    private void pulseAreaDamage(LivingEntity damager, Location center, double baseDmg, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, center, 1, 0, 0, 0, 0);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.45f, 1.35f);
        for (LivingEntity e : world.getNearbyLivingEntities(center, radius)) {
            if (e == damager) {
                continue;
            }
            if (zoneManager != null && !zoneManager.isCombatAllowed(e.getLocation())) {
                continue;
            }
            double dist = e.getLocation().distance(center);
            double falloff = 1.0 - (dist / radius) * 0.72;
            if (falloff <= 0) {
                continue;
            }
            double dealt = baseDmg * falloff * ThreadLocalRandom.current().nextDouble(0.88, 1.08);
            dealt = applyArmorReduction(e, WeaponDamageType.EXPLOSIVE, dealt);
            e.damage(dealt, damager);
        }
    }

    private void chainNearestLiving(Player shooter, LivingEntity from, double damage) {
        LivingEntity best = null;
        double bestD = 36 * 36;
        for (LivingEntity e : from.getWorld().getNearbyLivingEntities(from.getLocation(), 6.0)) {
            if (e == shooter || e == from) {
                continue;
            }
            double d = e.getLocation().distanceSquared(from.getLocation());
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best != null) {
            if (zoneManager != null && zoneManager.isWeaponDamageBlockedAlongLine(from.getEyeLocation(), best.getEyeLocation())) {
                return;
            }
            double dealt = applyArmorReduction(best, WeaponDamageType.ASSAULT_RIFLE, damage * ThreadLocalRandom.current().nextDouble(0.9, 1.1));
            best.damage(dealt, shooter);
            from.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, best.getLocation().add(0, 1, 0), 30, 0.25, 0.4, 0.25, 0.03);
            from.getWorld().playSound(best.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.25f, 1.6f);
        }
    }

    private void explodeChestnut(Location center, double centerDamage, UUID ownerId, LivingEntity damager, WeaponUpgradeState st) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double radius = 3.6;
        world.spawnParticle(Particle.EXPLOSION, center, 2, 0.15, 0.15, 0.15, 0.02);
        world.spawnParticle(Particle.CRIT, center, 35, 0.5, 0.5, 0.5, 0.08);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.85f, 1.15f);
        world.createExplosion(center, 0f, false, false);
        Player shooterPlayer = damager instanceof Player p ? p : null;
        for (LivingEntity e : world.getNearbyLivingEntities(center, radius)) {
            if (e.getUniqueId().equals(ownerId)) {
                continue;
            }
            double dist = e.getLocation().distance(center);
            double falloff = 1.0 - (dist / radius) * 0.68;
            if (falloff <= 0) {
                continue;
            }
            double dealt = centerDamage * falloff * ThreadLocalRandom.current().nextDouble(0.9, 1.1);
            dealt = applyArmorReduction(e, WeaponDamageType.EXPLOSIVE, dealt);
            if (damager != null) {
                e.damage(dealt, damager);
                if (shooterPlayer != null) {
                    applyOnHitUpgradeEffects(shooterPlayer, e, st, dealt, false);
                }
            } else {
                e.damage(dealt);
            }
        }
    }

    private WeaponUpgradeState readUpgradePayload(PersistentDataContainer pdc) {
        String raw = pdc.get(keyUpgradePayload, PersistentDataType.STRING);
        if (raw == null || !raw.contains(":")) {
            return WeaponUpgradeState.NONE;
        }
        String[] p = raw.split(":", 2);
        WeaponUpgradePath path = WeaponUpgradePath.fromId(p[0]);
        try {
            int tier = Integer.parseInt(p[1]);
            if (path == null || tier <= 0) {
                return WeaponUpgradeState.NONE;
            }
            return new WeaponUpgradeState(path, Math.min(5, tier));
        } catch (NumberFormatException e) {
            return WeaponUpgradeState.NONE;
        }
    }

    private Particle shotParticle(Player shooter, WeaponProfile w) {
        if (cosmeticsManager == null) {
            return w.trailParticle();
        }
        Particle p = cosmeticsManager.shotParticle(shooter.getUniqueId());
        return p != null ? p : w.trailParticle();
    }

    /** Frappe couteau : priorite au skin equipe, sinon cosmétique « tir » puis defaut arme. */
    private Particle beamTrailParticle(Player shooter, WeaponProfile w) {
        if (w == WeaponProfile.COUTEAU_COMBAT && cosmeticsManager != null) {
            CosmeticProfile k = cosmeticsManager.selectedKnifeProfile(shooter.getUniqueId());
            if (k != null && k.particle() != null) {
                return k.particle();
            }
        }
        return shotParticle(shooter, w);
    }

    private void drawBeam(Player shooter, Location start, Vector direction, double range, World world, WeaponProfile w) {
        Vector step = direction.clone().normalize().multiply(0.45);
        Location cursor = start.clone();
        int max = Math.max(1, (int) (range / 0.45));
        Particle p = beamTrailParticle(shooter, w);
        for (int i = 0; i < max; i++) {
            for (int j = 0; j < w.trailDensity(); j++) {
                world.spawnParticle(p, cursor, 1, 0.04, 0.04, 0.04, 0.002);
            }
            cursor.add(step);
        }
    }

    private Vector applySpread(Location eye, Vector baseDir, double maxDegrees) {
        if (maxDegrees <= 0.001) {
            return baseDir.clone().normalize();
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location L = eye.clone();
        L.setDirection(baseDir);
        float yawOff = (float) ((r.nextDouble() * 2 - 1) * maxDegrees);
        float pitchOff = (float) ((r.nextDouble() * 2 - 1) * maxDegrees * 0.62);
        L.setYaw(L.getYaw() + yawOff);
        float np = L.getPitch() + pitchOff;
        np = Math.max(-89.5f, Math.min(89.5f, np));
        L.setPitch(np);
        return L.getDirection().normalize();
    }

    private void launchSnowball(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        Location spawn = offsetSpawn(shooter);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        double dmg = projectileDamage(shooter, w, weapon, st);
        Vector dir = applySpread(shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), spread);
        Snowball sb = spawn.getWorld().spawn(spawn, Snowball.class);
        tagProjectile(sb, shooter, dmg, weapon);
        sb.setVelocity(dir.multiply(w.projectileSpeed()));
        sb.setShooter(shooter);
        trailBurst(shooter, spawn, w);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void launchArrow(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        Location spawn = offsetSpawn(shooter);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        double dmg = projectileDamage(shooter, w, weapon, st);
        Vector dir = applySpread(shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), spread);
        Arrow arrow = spawn.getWorld().spawn(spawn, Arrow.class);
        tagProjectile(arrow, shooter, dmg, weapon);
        arrow.setVelocity(dir.multiply(w.projectileSpeed()));
        arrow.setShooter(shooter);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setDamage(0);
        arrow.setCritical(ThreadLocalRandom.current().nextBoolean());
        trailBurst(shooter, spawn, w);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void launchEgg(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        Location spawn = offsetSpawn(shooter);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        double dmg = projectileDamage(shooter, w, weapon, st);
        Vector dir = applySpread(shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), spread);
        Egg egg = spawn.getWorld().spawn(spawn, Egg.class);
        tagProjectile(egg, shooter, dmg, weapon);
        egg.setVelocity(dir.multiply(w.projectileSpeed()));
        egg.setShooter(shooter);
        trailBurst(shooter, spawn, w);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void launchLlamaSpit(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        Location spawn = offsetSpawn(shooter);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        double dmg = projectileDamage(shooter, w, weapon, st);
        Vector dir = applySpread(shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), spread);
        LlamaSpit spit = spawn.getWorld().spawn(spawn, LlamaSpit.class);
        tagProjectile(spit, shooter, dmg, weapon);
        spit.setVelocity(dir.multiply(w.projectileSpeed()));
        spit.setShooter(shooter);
        trailBurst(shooter, spawn, w);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void launchChestnut(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        Location spawn = offsetSpawn(shooter);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        double dmg = projectileDamage(shooter, w, weapon, st);
        Vector dir = applySpread(shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), spread);
        Snowball nut = spawn.getWorld().spawn(spawn, Snowball.class);
        tagProjectileChestnut(nut, shooter, dmg, weapon);
        nut.setVelocity(dir.multiply(w.projectileSpeed()));
        nut.setShooter(shooter);
        trailBurst(shooter, spawn, w);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void launchSerumVial(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        Location spawn = offsetSpawn(shooter);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        Vector dir = applySpread(shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), spread * 0.85);
        Snowball vial = spawn.getWorld().spawn(spawn, Snowball.class);
        tagSerumVial(vial, shooter, w, weapon);
        vial.setVelocity(dir.multiply(Math.max(0.55, w.projectileSpeed() * 0.92)));
        vial.setShooter(shooter);
        spawn.getWorld().spawnParticle(w.trailParticle(), spawn, w.trailDensity() * 2, 0.1, 0.1, 0.1, 0.02);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void launchHealDart(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        Location spawn = offsetSpawn(shooter);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        double healHalfHearts = Math.max(1.0, projectileDamage(shooter, w, weapon, st));
        Vector dir = applySpread(shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), spread);
        Snowball bolt = spawn.getWorld().spawn(spawn, Snowball.class);
        tagHealDart(bolt, shooter, healHalfHearts, weapon);
        bolt.setVelocity(dir.multiply(w.projectileSpeed()));
        bolt.setShooter(shooter);
        trailBurst(shooter, spawn, w);
        registerRecoil(shooter, w, previousShotMs);
    }

    private void tagHealDart(Snowball projectile, Player shooter, double healHalfHearts, ItemStack weapon) {
        PersistentDataContainer c = projectile.getPersistentDataContainer();
        c.set(keyProjDamage, PersistentDataType.DOUBLE, healHalfHearts);
        c.set(keyProjOwner, PersistentDataType.STRING, shooter.getUniqueId().toString());
        c.set(keyProjKind, PersistentDataType.STRING, PROJ_KIND_HEAL_DART);
        String wid = readWeaponId(weapon);
        if (wid != null) {
            c.set(keyProjWeaponId, PersistentDataType.STRING, wid);
        }
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        if (!st.isEmpty()) {
            c.set(keyUpgradePayload, PersistentDataType.STRING, st.path().name() + ":" + st.tier());
        }
    }

    private void tagSerumVial(Snowball projectile, Player shooter, WeaponProfile w, ItemStack weapon) {
        PersistentDataContainer c = projectile.getPersistentDataContainer();
        c.set(keyProjDamage, PersistentDataType.DOUBLE, 0.0);
        c.set(keyProjOwner, PersistentDataType.STRING, shooter.getUniqueId().toString());
        c.set(keyProjKind, PersistentDataType.STRING, PROJ_KIND_SERUM);
        c.set(keySerumWeaponId, PersistentDataType.STRING, w.name());
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        if (!st.isEmpty()) {
            c.set(keyUpgradePayload, PersistentDataType.STRING, st.path().name() + ":" + st.tier());
        }
    }

    private enum SerumCloudKind {
        HEAL, SPEED, SHIELD, REGEN, CLEANSE, ENEMY_HAZE
    }

    private static SerumCloudKind serumKindFor(WeaponProfile w) {
        if (w == null) {
            return SerumCloudKind.HEAL;
        }
        return switch (w) {
            case SERUM_SOIN -> SerumCloudKind.HEAL;
            default -> SerumCloudKind.HEAL;
        };
    }

    private void startSerumCloud(Location center, UUID ownerId, Player shooter, WeaponProfile w) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        SerumCloudKind kind = serumKindFor(w);
        double radius = Math.max(1.5, w.baseRange());
        double power = w.baseDamage();
        int pulses = 9;
        final int[] idx = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (idx[0] >= pulses) {
                holder[0].cancel();
                return;
            }
            pulseSerumCloud(center, ownerId, shooter, kind, radius, power);
            idx[0]++;
        }, 0L, 10L);
    }

    private void pulseSerumCloud(Location center, UUID ownerId, Player shooter, SerumCloudKind kind, double radius, double power) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(center, Sound.BLOCK_BREWING_STAND_BREW, 0.35f, 1.15f + idxNoise(kind));
        switch (kind) {
            case HEAL -> {
                world.spawnParticle(Particle.HEART, center, 10, radius * 0.35, 0.12, radius * 0.35, 0.02);
                world.spawnParticle(Particle.HAPPY_VILLAGER, center, 6, radius * 0.4, 0.08, radius * 0.4, 0.01);
                double heal = Math.max(0.25, power);
                for (Player p : world.getPlayers()) {
                    if (!p.isValid() || p.isDead() || p.getLocation().distanceSquared(center) > radius * radius) {
                        continue;
                    }
                    double maxH = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    p.setHealth(Math.min(maxH, p.getHealth() + heal));
                }
            }
            case SPEED -> {
                world.spawnParticle(Particle.CLOUD, center, 14, radius * 0.4, 0.15, radius * 0.4, 0.02);
                int amp = power >= 1.25 ? 1 : 0;
                for (Player p : world.getPlayers()) {
                    if (!p.isValid() || p.isDead() || p.getLocation().distanceSquared(center) > radius * radius) {
                        continue;
                    }
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 55, amp, false, true, true));
                }
            }
            case SHIELD -> {
                world.spawnParticle(Particle.END_ROD, center, 12, radius * 0.35, 0.12, radius * 0.35, 0.02);
                int amp = (int) Math.min(3, Math.max(0, Math.round(power) - 1));
                for (Player p : world.getPlayers()) {
                    if (!p.isValid() || p.isDead() || p.getLocation().distanceSquared(center) > radius * radius) {
                        continue;
                    }
                    p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, amp, false, true, true));
                }
            }
            case REGEN -> {
                world.spawnParticle(Particle.WITCH, center, 10, radius * 0.38, 0.1, radius * 0.38, 0.015);
                for (Player p : world.getPlayers()) {
                    if (!p.isValid() || p.isDead() || p.getLocation().distanceSquared(center) > radius * radius) {
                        continue;
                    }
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 65, 0, false, true, true));
                }
            }
            case CLEANSE -> {
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 16, radius * 0.35, 0.2, radius * 0.35, 0.03);
                for (Player p : world.getPlayers()) {
                    if (!p.isValid() || p.isDead() || p.getLocation().distanceSquared(center) > radius * radius) {
                        continue;
                    }
                    stripNegativeEffects(p);
                }
            }
            case ENEMY_HAZE -> {
                world.spawnParticle(Particle.SMOKE, center, 22, radius * 0.42, 0.12, radius * 0.42, 0.02);
                world.spawnParticle(Particle.ENTITY_EFFECT, center, 8, radius * 0.3, 0.1, radius * 0.3, 0.01);
                double dmg = Math.max(0.35, power * 0.45);
                for (Player p : world.getPlayers()) {
                    if (!p.isValid() || p.isDead() || p.getUniqueId().equals(ownerId)) {
                        continue;
                    }
                    if (p.getLocation().distanceSquared(center) > radius * radius) {
                        continue;
                    }
                    if (zoneManager != null && !zoneManager.isCombatAllowed(p.getLocation())) {
                        continue;
                    }
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 38, 1, false, true, true));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 45, 0, false, true, true));
                    p.damage(dmg, shooter != null ? shooter : p);
                }
            }
        }
    }

    private static float idxNoise(SerumCloudKind k) {
        return switch (k) {
            case HEAL -> 0.05f;
            case SPEED -> 0.12f;
            case SHIELD -> 0.08f;
            case REGEN -> -0.06f;
            case CLEANSE -> 0.15f;
            case ENEMY_HAZE -> -0.1f;
        };
    }

    private static void stripNegativeEffects(Player p) {
        for (PotionEffectType t : List.of(
                PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.WEAKNESS,
                PotionEffectType.SLOWNESS, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA,
                PotionEffectType.HUNGER, PotionEffectType.DARKNESS)) {
            if (t != null && p.hasPotionEffect(t)) {
                p.removePotionEffect(t);
            }
        }
    }

    private void launchGasoline(Player shooter, WeaponProfile w, ItemStack weapon, long previousShotMs) {
        JerrycanUpgradeState jerry = JerrycanUpgradeState.read(weapon, plugin);
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        double spread = effectiveSpreadDegrees(shooter, w, weapon, st, previousShotMs);
        double velMul = JerrycanUpgradeEffects.throwVelocityMul(jerry) * w.projectileSpeed();
        int shots = JerrycanUpgradeEffects.tripleVolley(jerry) ? 3 : 1;
        for (int i = 0; i < shots; i++) {
            Location spawn = offsetSpawn(shooter);
            Vector base = shooter.getEyeLocation().getDirection().clone();
            if (shots == 3) {
                base = rotateYawDegrees(base, (i - 1) * 7.0);
            }
            Vector dir = applySpread(shooter.getEyeLocation(), base, spread * 0.65);
            Snowball gas = spawn.getWorld().spawn(spawn, Snowball.class);
            boolean mini = shots == 3;
            tagGasoline(gas, shooter, weapon, mini ? 7000L : 12_000L, mini);
            gas.setVelocity(dir.multiply(Math.max(0.6, velMul)));
            gas.setShooter(shooter);
            spawn.getWorld().spawnParticle(Particle.DRIPPING_HONEY, spawn, 10, 0.12, 0.12, 0.12, 0.02);
        }
        registerRecoil(shooter, w, previousShotMs);
    }

    private static Vector rotateYawDegrees(Vector dir, double deg) {
        if (Math.abs(deg) < 0.001) {
            return dir.clone();
        }
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = dir.getX() * cos - dir.getZ() * sin;
        double z = dir.getX() * sin + dir.getZ() * cos;
        return new Vector(x, dir.getY(), z).normalize();
    }

    private double projectileDamage(Player shooter, WeaponProfile w, ItemStack weapon, WeaponUpgradeState st) {
        double d = w.effectiveDamage(shooter) * WeaponUpgradeEffects.damageMultiplier(weapon, st);
        if (st.path() == WeaponUpgradePath.TECH && st.tier() >= 5) {
            int c = techShotCount.merge(shooter.getUniqueId(), 1, Integer::sum);
            if (c % 6 == 0) {
                d *= 1.22;
            }
        }
        return d;
    }

    private double applyArmorReduction(LivingEntity target, WeaponDamageType type, double raw) {
        if (!(target instanceof Player p) || armorManager == null) {
            return raw;
        }
        return raw * armorManager.damageMultiplier(p, type);
    }

    private static Location offsetSpawn(Player shooter) {
        Location eye = shooter.getEyeLocation();
        Vector d = eye.getDirection().normalize().multiply(0.45);
        return eye.clone().add(d);
    }

    private void trailBurst(Player shooter, Location loc, WeaponProfile w) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Particle base = shotParticle(shooter, w);
        world.spawnParticle(base, loc, w.trailDensity() * 3, 0.12, 0.12, 0.12, 0.02);
        spawnWeaponShotSignature(world, loc, base, w);
    }

    private void spawnWeaponShotSignature(World world, Location origin, Particle particle, WeaponProfile w) {
        int pattern = Math.floorMod(w.ordinal(), 5);
        switch (pattern) {
            case 0 -> {
                // Anneau rapide
                for (int i = 0; i < 12; i++) {
                    double a = i * (Math.PI * 2.0 / 12.0);
                    Location p = origin.clone().add(Math.cos(a) * 0.34, 0.02, Math.sin(a) * 0.34);
                    world.spawnParticle(particle, p, 1, 0.01, 0.01, 0.01, 0.0);
                }
            }
            case 1 -> {
                // Croix 3D courte
                for (double d = -0.28; d <= 0.28; d += 0.08) {
                    world.spawnParticle(particle, origin.clone().add(d, 0, 0), 1, 0, 0, 0, 0);
                    world.spawnParticle(particle, origin.clone().add(0, d * 0.6, 0), 1, 0, 0, 0, 0);
                    world.spawnParticle(particle, origin.clone().add(0, 0, d), 1, 0, 0, 0, 0);
                }
            }
            case 2 -> {
                // Spirale
                for (int i = 0; i < 10; i++) {
                    double a = i * 0.72;
                    double r = 0.08 + i * 0.02;
                    Location p = origin.clone().add(Math.cos(a) * r, i * 0.01, Math.sin(a) * r);
                    world.spawnParticle(particle, p, 1, 0, 0, 0, 0);
                }
            }
            case 3 -> {
                // Cone vers l'avant
                Vector dir = origin.getDirection().normalize();
                for (int i = 1; i <= 5; i++) {
                    Location p = origin.clone().add(dir.clone().multiply(i * 0.09));
                    world.spawnParticle(particle, p, 2, i * 0.02, i * 0.012, i * 0.02, 0.0);
                }
            }
            default -> {
                // Double arc
                for (int i = 0; i <= 8; i++) {
                    double t = i / 8.0;
                    double x = (t - 0.5) * 0.6;
                    double y = 0.14 - Math.pow(t - 0.5, 2) * 0.56;
                    world.spawnParticle(particle, origin.clone().add(x, y, 0.18), 1, 0, 0, 0, 0);
                    world.spawnParticle(particle, origin.clone().add(x, y, -0.18), 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void tagProjectile(Projectile projectile, LivingEntity shooter, double damage, ItemStack weapon) {
        PersistentDataContainer c = projectile.getPersistentDataContainer();
        c.set(keyProjDamage, PersistentDataType.DOUBLE, damage);
        c.set(keyProjOwner, PersistentDataType.STRING, shooter.getUniqueId().toString());
        c.remove(keyProjKind);
        String wid = readWeaponId(weapon);
        if (wid != null) {
            c.set(keyProjWeaponId, PersistentDataType.STRING, wid);
        }
        WeaponUpgradeState st = WeaponUpgradeState.read(weapon, plugin);
        if (!st.isEmpty()) {
            c.set(keyUpgradePayload, PersistentDataType.STRING, st.path().name() + ":" + st.tier());
        }
    }

    private void tagProjectileChestnut(Projectile projectile, LivingEntity shooter, double damage, ItemStack weapon) {
        tagProjectile(projectile, shooter, damage, weapon);
        projectile.getPersistentDataContainer().set(keyProjKind, PersistentDataType.STRING, PROJ_KIND_CHESTNUT);
    }

    private void tagGasoline(Projectile projectile, LivingEntity shooter, ItemStack weapon, long ttlMs, boolean mini) {
        tagProjectile(projectile, shooter, 0.0, weapon);
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        pdc.set(keyProjKind, PersistentDataType.STRING, PROJ_KIND_GAS);
        pdc.set(keyGasExpireAt, PersistentDataType.LONG, System.currentTimeMillis() + Math.max(1000L, ttlMs));
        JerrycanUpgradeState j = JerrycanUpgradeState.read(weapon, plugin);
        JerrycanUpgradeState.writePdc(pdc, j, plugin);
        if (mini) {
            pdc.set(keyGasMini, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private void addGasolinePuddle(Location center, long ttlMs, JerrycanUpgradeState jerry) {
        if (center.getWorld() == null) {
            return;
        }
        String key = center.getWorld().getName() + ";" + center.getBlockX() + ";" + center.getBlockY() + ";" + center.getBlockZ();
        long exp = System.currentTimeMillis() + Math.max(1000L, ttlMs);
        gasolinePuddles.put(key, new GasPuddleEntry(exp, jerry));
    }

    private void tickGasolineFx() {
        long now = System.currentTimeMillis();
        gasolinePuddles.entrySet().removeIf(e -> e.getValue().expireAtMs < now);
        for (String k : gasolinePuddles.keySet()) {
            Location loc = parseSimpleKey(k);
            if (loc == null || loc.getWorld() == null) {
                continue;
            }
            loc.getWorld().spawnParticle(Particle.DRIPPING_HONEY, loc.clone().add(0.5, 0.05, 0.5), 2, 0.35, 0.02, 0.35, 0.01);
        }
    }

    private Location parseSimpleKey(String key) {
        String[] p = key.split(";");
        if (p.length != 4) {
            return null;
        }
        World w = Bukkit.getWorld(p[0]);
        if (w == null) {
            return null;
        }
        try {
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            int z = Integer.parseInt(p[3]);
            return new Location(w, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void igniteGasoline(Player player) {
        World w = player.getWorld();
        RayTraceResult rt = w.rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), 16,
                FluidCollisionMode.NEVER, true);
        Location center = (rt != null && rt.getHitPosition() != null)
                ? new Location(w, rt.getHitPosition().getX(), rt.getHitPosition().getY(), rt.getHitPosition().getZ())
                : player.getLocation();

        boolean ignited = false;
        for (String k : new ArrayList<>(gasolinePuddles.keySet())) {
            GasPuddleEntry entry = gasolinePuddles.get(k);
            Location loc = parseSimpleKey(k);
            if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(w)) {
                continue;
            }
            if (loc.distanceSquared(center) > 4.0 * 4.0) {
                continue;
            }
            gasolinePuddles.remove(k);
            igniteArea(w, loc.clone().add(0.5, 0.1, 0.5), player, entry != null ? entry.jerry : JerrycanUpgradeState.NONE, false);
            ignited = true;
        }
        if (ignited) {
            player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 0.8f, 1.2f);
            I18n.actionBar(player, NamedTextColor.GOLD, "weapon.gas_ignite_ok");
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 0.9f);
            I18n.actionBar(player, NamedTextColor.GRAY, "weapon.gas_ignite_none");
        }
    }

    private void igniteArea(World world, Location center, Player igniter, JerrycanUpgradeState jerry, boolean mini) {
        double baseRadius = mini ? 1.65 : 3.2;
        double radius = baseRadius * JerrycanUpgradeEffects.igniteRadiusMul(jerry);
        double burnDmg = (mini ? 0.42 : 1.0) * JerrycanUpgradeEffects.burnDamageMul(jerry);
        int fireTicks = (mini ? 55 : 100) + JerrycanUpgradeEffects.extraFireTicks(jerry);
        if (JerrycanUpgradeEffects.firePersistsOffPuddle(jerry)) {
            fireTicks = Math.max(fireTicks, 200);
        }

        world.spawnParticle(Particle.FLAME, center, mini ? 28 : 50, 1.0, 0.2, 1.0, 0.02);
        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 0.7f, 1.3f);

        for (LivingEntity e : world.getNearbyLivingEntities(center, radius)) {
            if (zoneManager != null && e instanceof Player pl && !zoneManager.isCombatAllowed(pl.getLocation())) {
                continue;
            }
            e.setFireTicks(Math.max(e.getFireTicks(), fireTicks));
            applyJerryBurnDamage(e, igniter, burnDmg, jerry);
            applyJerrySlowEffects(e, jerry);
        }

        int fireGrid = JerrycanUpgradeEffects.wideGroundFire(jerry) ? 3 : 1;
        for (int dx = -fireGrid; dx <= fireGrid; dx++) {
            for (int dz = -fireGrid; dz <= fireGrid; dz++) {
                if (fireGrid > 1 && dx * dx + dz * dz > fireGrid * fireGrid) {
                    continue;
                }
                Location b = center.clone().add(dx, 0, dz);
                if (!b.getBlock().getType().isAir() && b.getBlock().getType().isSolid()) {
                    Location up = b.clone().add(0, 1, 0);
                    if (up.getBlock().getType().isAir()) {
                        up.getBlock().setType(Material.FIRE);
                    }
                }
            }
        }
    }

    private void applyJerryBurnDamage(LivingEntity target, Player igniter, double amount, JerrycanUpgradeState jerry) {
        if (JerrycanUpgradeEffects.infernoBlanc(jerry)) {
            DamageSource src = DamageSource.builder(DamageType.MAGIC)
                    .withDirectEntity(igniter != null ? igniter : target)
                    .withCausingEntity(igniter != null ? igniter : target)
                    .build();
            target.damage(amount, src);
        } else {
            target.damage(amount, igniter != null ? igniter : target);
        }
    }

    private void applyJerrySlowEffects(LivingEntity e, JerrycanUpgradeState jerry) {
        if (!JerrycanUpgradeEffects.viscousSlow(jerry)) {
            return;
        }
        if (JerrycanUpgradeEffects.etreinteNoire(jerry)) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 5, false, true, true));
            if (e instanceof Player pl) {
                pl.setSprinting(false);
            }
            return;
        }
        if (JerrycanUpgradeEffects.stackingSlow(jerry)) {
            PotionEffect cur = e.getPotionEffect(PotionEffectType.SLOWNESS);
            int amp = cur == null ? 0 : Math.min(2, cur.getAmplifier() + 1);
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, amp, false, true, true));
            return;
        }
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 28, 0, false, true, true));
    }

    private void startReload(Player player, WeaponProfile w, ItemStack weaponInHand) {
        UUID uuid = player.getUniqueId();
        if (reloadSessions.containsKey(uuid)) {
            return;
        }
        String instanceId = ensureWeaponInstanceId(player, weaponInHand);
        if (instanceId == null) {
            return;
        }
        int clip = getClip(player, w, weaponInHand);
        if (clip >= maxClipFor(player, w, weaponInHand)) {
            I18n.actionBar(player, NamedTextColor.GRAY, "weapon.reload_full");
            return;
        }

        WeaponUpgradeState st = WeaponUpgradeState.read(weaponInHand, plugin);
        if (w == WeaponProfile.REVOLVER) {
            st = hitscanUpgradeStateFor(w, weaponInHand);
        }
        int reloadTicks = Math.max(3, (int) Math.round(w.reloadTicks() * 0.82 * WeaponUpgradeEffects.reloadMultiplier(weaponInHand, st)));

        player.sendActionBar(reloadProgressComponent(player, 0, reloadTicks));
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.85f, 1.05f);
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 0.55f, 1.15f);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.55f, 1.25f);
        spawnReloadParticles(player, w, 0);

        ReloadSession session = new ReloadSession(w.name(), instanceId, reloadTicks);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickReload(player, w, session), 1L, 1L);
        session.task = task;
        reloadSessions.put(uuid, session);
    }

    private void tickReload(Player player, WeaponProfile w, ReloadSession session) {
        UUID uuid = player.getUniqueId();
        ItemStack mh = player.getInventory().getItemInMainHand();
        WeaponProfile held = fromItem(mh);
        if (held == null || !held.name().equals(session.weaponId)) {
            cancelReload(player);
            I18n.actionBar(player, NamedTextColor.RED, "weapon.reload_cancelled");
            return;
        }
        String sid = ensureWeaponInstanceId(player, mh);
        if (sid == null || !sid.equals(session.instanceId)) {
            cancelReload(player);
            I18n.actionBar(player, NamedTextColor.RED, "weapon.reload_cancelled");
            return;
        }

        session.elapsedTicks++;
        int t = session.totalTicks;
        int e = session.elapsedTicks;

        if (e == t / 3) {
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.45f, 1.35f);
        } else if (e == 2 * t / 3) {
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.5f, 1.2f);
        }

        if (e % 4 == 0 || e >= t) {
            spawnReloadParticles(player, w, e);
        }

        if (e % 2 == 0 || e >= t) {
            player.sendActionBar(reloadProgressComponent(player, e, t));
        }

        if (e >= t) {
            session.task.cancel();
            reloadSessions.remove(uuid);
            setClip(player, w, maxClipFor(player, w, mh), mh);
            player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 1.08f);
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.9f, 1.25f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.55f, 1.7f);
            sendAmmoActionBar(player, w, mh);
        }
    }

    private void spawnReloadParticles(Player player, WeaponProfile w, int tick) {
        World world = player.getWorld();
        Location hand = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(0.32)).add(0, -0.24, 0);
        Particle base = shotParticle(player, w);
        world.spawnParticle(base, hand, 4, 0.08, 0.06, 0.08, 0.01);
        double phase = tick * 0.22;
        for (int i = 0; i < 6; i++) {
            double a = phase + i * (Math.PI * 2.0 / 6.0);
            Location p = hand.clone().add(Math.cos(a) * 0.14, (i % 2) * 0.02, Math.sin(a) * 0.14);
            world.spawnParticle(Particle.END_ROD, p, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private Component reloadProgressComponent(Player player, int elapsedTicks, int totalTicks) {
        int total = Math.max(1, totalTicks);
        int pct = Math.min(100, (int) Math.round(100.0 * elapsedTicks / total));
        int segments = 12;
        int filled = (int) Math.round(segments * (double) elapsedTicks / total);
        filled = Math.min(segments, Math.max(0, filled));
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            bar.append(i < filled ? '\u2588' : '\u2591');
        }
        return Component.text(I18n.string(I18n.locale(player), "weapon.reload_progress", bar.toString(), pct), NamedTextColor.YELLOW);
    }

    private void cancelReload(Player player) {
        UUID uuid = player.getUniqueId();
        ReloadSession session = reloadSessions.remove(uuid);
        if (session != null && session.task != null) {
            session.task.cancel();
        }
    }

    public boolean isDpmrWeapon(ItemStack item) {
        return fromItem(item) != null;
    }

    private WeaponProfile fromItem(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(keyWeaponId, PersistentDataType.STRING);
        return id == null ? null : parse(id);
    }

    private WeaponProfile parse(String raw) {
        return WeaponProfile.fromId(raw);
    }

    private int maxClipForStack(WeaponProfile w, ItemStack stack) {
        if (stack == null || fromItem(stack) != w) {
            return w.clipSize();
        }
        int base = w.clipSize();
        if (w == WeaponProfile.REVOLVER) {
            RevolverUpgradeState rv = RevolverUpgradeState.read(stack, plugin);
            return base * RevolverUpgradeEffects.clipMultiplier(rv);
        }
        if (w == WeaponProfile.JERRYCAN) {
            return base + JerrycanUpgradeEffects.clipBonus(JerrycanUpgradeState.read(stack, plugin));
        }
        return base;
    }

    private int maxClipFor(Player player, WeaponProfile w, ItemStack weaponStack) {
        ItemStack stack = weaponStack;
        if (stack == null || fromItem(stack) != w) {
            stack = player.getInventory().getItemInMainHand();
        }
        return maxClipForStack(w, stack);
    }

    private int getClip(Player player, WeaponProfile w, ItemStack weaponStack) {
        ItemStack stack = weaponStack != null && fromItem(weaponStack) == w ? weaponStack : player.getInventory().getItemInMainHand();
        if (fromItem(stack) != w) {
            return w.clipSize();
        }
        String ik = ensureWeaponInstanceId(player, stack);
        if (ik == null) {
            return w.clipSize();
        }
        Map<String, Integer> m = clipAmmo.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        int stored = m.getOrDefault(ik, w.clipSize());
        int cap = maxClipFor(player, w, stack);
        if (stored > cap) {
            m.put(ik, cap);
            return cap;
        }
        return stored;
    }

    private void setClip(Player player, WeaponProfile w, int value, ItemStack weaponStack) {
        ItemStack stack = weaponStack != null && fromItem(weaponStack) == w ? weaponStack : player.getInventory().getItemInMainHand();
        if (fromItem(stack) != w) {
            return;
        }
        String ik = ensureWeaponInstanceId(player, stack);
        if (ik == null) {
            return;
        }
        int cap = maxClipFor(player, w, stack);
        clipAmmo.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(ik, Math.max(0, Math.min(cap, value)));
    }

    /** Appele apres achat Double charge (J-20) : +1 munition si sous le nouveau plafond. */
    public void bumpJerryClipOnUpgrade(Player player, ItemStack weapon) {
        WeaponProfile w = fromItem(weapon);
        if (w != WeaponProfile.JERRYCAN) {
            return;
        }
        String ik = ensureWeaponInstanceId(player, weapon);
        if (ik == null) {
            return;
        }
        int cap = w.clipSize() + JerrycanUpgradeEffects.clipBonus(JerrycanUpgradeState.read(weapon, plugin));
        Map<String, Integer> m = clipAmmo.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        int cur = m.getOrDefault(ik, cap);
        m.put(ik, Math.min(cap, cur + 1));
    }

    /** Apres palier I revolver : remplit le barillet elargi ( +6 si deja plein ou proche du max ). */
    public void bumpRevolverClipAfterUpgrade(Player player, ItemStack weapon) {
        WeaponProfile w = fromItem(weapon);
        if (w != WeaponProfile.REVOLVER) {
            return;
        }
        String ik = ensureWeaponInstanceId(player, weapon);
        if (ik == null) {
            return;
        }
        int cap = w.clipSize() * RevolverUpgradeEffects.clipMultiplier(RevolverUpgradeState.read(weapon, plugin));
        Map<String, Integer> m = clipAmmo.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        int cur = m.getOrDefault(ik, w.clipSize());
        m.put(ik, Math.min(cap, cur + w.clipSize()));
    }

    private void sendAmmoActionBar(Player player, WeaponProfile w, ItemStack weaponStack) {
        ItemStack stack = weaponStack != null && fromItem(weaponStack) == w ? weaponStack : player.getInventory().getItemInMainHand();
        if (fromItem(stack) != w) {
            return;
        }
        I18n.actionBar(player, NamedTextColor.GOLD, "weapon.ammo_hud",
                w.displayName(), getClip(player, w, stack), maxClipFor(player, w, stack));
    }
}
