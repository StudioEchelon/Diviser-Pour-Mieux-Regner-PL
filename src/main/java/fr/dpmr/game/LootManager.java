package fr.dpmr.game;

import fr.dpmr.armor.ArmorManager;
import fr.dpmr.i18n.GameLocale;
import fr.dpmr.i18n.I18n;
import fr.dpmr.i18n.PlayerLanguageStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LootManager implements Listener {

    private static final String MENU_TITLE = "DPMR Configuration Loot";
    private static final Component MENU_TITLE_COMPONENT = Component.text(MENU_TITLE);

    private final JavaPlugin plugin;
    private final NamespacedKey keyPortableLootChest;
    private final NamespacedKey keyChestBreakerAxe;
    private final WeaponManager weaponManager;
    private final BandageManager bandageManager;
    private final ArmorManager armorManager;
    private final PlayerLanguageStore languageStore;
    private BalloonChestManager balloonChestManager;
    private BukkitTask airdropTask;
    private BukkitTask boostTask;
    private BukkitTask chestSpawnTask;

    private final Set<String> zoneChestKeys = new HashSet<>();
    private final Set<String> filledThisChest = new HashSet<>();
    private final Map<String, Long> configuredChestCooldownUntil = new HashMap<>();
    private final Map<String, ArmorStand> configuredChestHolograms = new HashMap<>();
    private BukkitTask configuredChestHudTask;

    private final Set<String> airdropOpened = new HashSet<>();
    private final Map<String, BukkitTask> airdropOpenTasks = new HashMap<>();
    private final Map<String, BlockDisplay> airdropFallingDisplays = new HashMap<>();
    private final Map<String, ArmorStand> airdropHolograms = new HashMap<>();
    private final Map<String, AirdropType> airdropTypes = new HashMap<>();

    private enum AirdropType {
        TACTIQUE("Tactique", NamedTextColor.GOLD, Material.CHEST, Particle.CLOUD, Particle.EXPLOSION, Sound.BLOCK_ANVIL_LAND),
        MEDICAL("Medical", NamedTextColor.GREEN, Material.TRAPPED_CHEST, Particle.HAPPY_VILLAGER, Particle.TOTEM_OF_UNDYING, Sound.BLOCK_AMETHYST_BLOCK_RESONATE),
        TECHNO("Techno", NamedTextColor.AQUA, Material.ENDER_CHEST, Particle.ELECTRIC_SPARK, Particle.END_ROD, Sound.BLOCK_BEACON_ACTIVATE);

        final String label;
        final NamedTextColor color;
        final Material fallingMaterial;
        final Particle trailParticle;
        final Particle landParticle;
        final Sound landSound;

        AirdropType(String label, NamedTextColor color, Material fallingMaterial, Particle trailParticle, Particle landParticle, Sound landSound) {
            this.label = label;
            this.color = color;
            this.fallingMaterial = fallingMaterial;
            this.trailParticle = trailParticle;
            this.landParticle = landParticle;
            this.landSound = landSound;
        }
    }

    public LootManager(JavaPlugin plugin, WeaponManager weaponManager, BandageManager bandageManager,
                      ArmorManager armorManager, PlayerLanguageStore languageStore) {
        this.plugin = plugin;
        this.keyPortableLootChest = new NamespacedKey(plugin, "dpmr_portable_loot_chest");
        this.keyChestBreakerAxe = new NamespacedKey(plugin, "dpmr_chest_breaker_axe");
        this.weaponManager = weaponManager;
        this.bandageManager = bandageManager;
        this.armorManager = armorManager;
        this.languageStore = languageStore;
    }

    public void setBalloonChestManager(BalloonChestManager balloonChestManager) {
        this.balloonChestManager = balloonChestManager;
    }

    public Location pickBalloonChestSpawnSurface(Player nearPlayer) {
        if (nearPlayer != null && nearPlayer.getWorld() != null) {
            Location base = nearPlayer.getLocation().getBlock().getLocation();
            Location raw = base.clone().add(ThreadLocalRandom.current().nextInt(-20, 21), 0, ThreadLocalRandom.current().nextInt(-20, 21));
            return clampAirdropToGround(raw);
        }
        return clampAirdropToGround(pickAirdropTarget());
    }

    public LootChestTier parseBalloonChestLootTier() {
        String t = plugin.getConfig().getString("loot.balloon-chest.loot-tier", "TIER_2");
        try {
            return LootChestTier.valueOf(t.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return LootChestTier.TIER_2;
        }
    }

    public void grantBalloonChestLoot(Player player, Location entityLoc, LootChestTier tier) {
        ItemStack loot = generateSingleChestLoot(tier);
        if (loot != null && !loot.getType().isAir()) {
            giveLootToPlayer(player, loot.clone());
            Location popupBase = new Location(entityLoc.getWorld(),
                    Math.floor(entityLoc.getX()), Math.floor(entityLoc.getY()), Math.floor(entityLoc.getZ()));
            showLootPopupAboveChest(popupBase, player, List.of(loot));
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.9f, 1.15f);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.55f, 1.15f);
        }
    }

    private static String airdropTypeMessageKey(AirdropType type) {
        return switch (type) {
            case TACTIQUE -> "airdrop.type_tactical";
            case MEDICAL -> "airdrop.type_medical";
            case TECHNO -> "airdrop.type_techno";
        };
    }

    private String englishAirdropTypeLabel(AirdropType type) {
        return I18n.string(GameLocale.EN, airdropTypeMessageKey(type));
    }

    public ItemStack createPortableLootChestItem(int amount) {
        return createPortableLootChestItem(amount, 1);
    }

    /**
     * Coffre portable : 1–3 bois / piégé / ender ; 4–6 maritime (barrel / smoker / blast furnace, dans l'eau).
     */
    public ItemStack createPortableLootChestItem(int amount, int tier) {
        LootChestTier lt = LootChestTier.fromPortableTier(tier);
        int t = Math.max(1, Math.min(6, tier));
        ItemStack item = new ItemStack(lt.material(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (lt.isMaritime()) {
            String title = switch (t) {
                case 5 -> "Maritime Chest II";
                case 6 -> "Maritime Chest III";
                default -> "Maritime Chest";
            };
            meta.displayName(Component.text(title, lt.labelColor(), TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Place: must replace a water block; registers this DPMR chest.", NamedTextColor.GRAY),
                    Component.text("Right-click: loot (no GUI); higher maritime tiers = more rolls.", NamedTextColor.DARK_GRAY)
            ));
        } else {
            meta.displayName(Component.text("Coffre loot DPMR (" + lt.roman() + ")", lt.labelColor(), TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Pose : enregistre un coffre DPMR à cet emplacement.", NamedTextColor.GRAY),
                    Component.text("Clic : 1 loot aléatoire (sans GUI), affiché au-dessus.", NamedTextColor.DARK_GRAY)
            ));
        }
        meta.getPersistentDataContainer().set(keyPortableLootChest, PersistentDataType.BYTE, (byte) t);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createChestBreakerAxeItem() {
        ItemStack item = new ItemStack(Material.STONE_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("AxeChest", NamedTextColor.RED, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Right-click a LootChest to remove its spawn.", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(keyChestBreakerAxe, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public int purgeAllLootHolograms() {
        int removed = 0;
        for (ArmorStand holo : configuredChestHolograms.values()) {
            if (removeArmorStandSafe(holo)) {
                removed++;
            }
        }
        configuredChestHolograms.clear();
        for (ArmorStand holo : airdropHolograms.values()) {
            if (removeArmorStandSafe(holo)) {
                removed++;
            }
        }
        airdropHolograms.clear();
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (!stand.isValid() || stand.customName() == null) {
                    continue;
                }
                String plain = PlainTextComponentSerializer.plainText()
                        .serialize(stand.customName())
                        .toLowerCase(Locale.ROOT);
                if (plain.contains("lootchest") || plain.contains("airdrop") || plain.contains("coffre")
                        || plain.contains("maritime")) {
                    if (removeArmorStandSafe(stand)) {
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    private static boolean removeArmorStandSafe(ArmorStand stand) {
        if (stand == null || !stand.isValid()) {
            return false;
        }
        stand.remove();
        return true;
    }

    public void startLoops() {
        sanitizeConfiguredWeaponPools();
        refreshConfiguredChestHolograms();
        startConfiguredChestHudTask();
        FileConfiguration cfg = plugin.getConfig();
        long airdropEvery = Math.max(30, cfg.getLong("loot.airdrop-interval-seconds", 300));
        long boostEvery = Math.max(15, cfg.getLong("boosts.spawn-interval-seconds", 120));
        long chestSpawnEvery = Math.max(30, cfg.getLong("loot.chest-spawn-interval-seconds", 180));
        airdropTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnAirdrop, airdropEvery * 20L, airdropEvery * 20L);
        boostTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnBoost, boostEvery * 20L, boostEvery * 20L);
        chestSpawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::trySpawnZoneChest, chestSpawnEvery * 20L, chestSpawnEvery * 20L);
        if (balloonChestManager != null) {
            balloonChestManager.startSchedule();
        }
    }

    /**
     * Garantit que toutes les armes du catalogue sont presentes dans les listes config
     * (merge sans retirer d'IDs custom). Les coffres utilisent des poids de rarete decroissants
     * (COMMON le plus frequent, GHOST le plus rare — voir {@code rollRarityForChestTier}).
     */
    private void sanitizeConfiguredWeaponPools() {
        FileConfiguration cfg = plugin.getConfig();
        List<String> allIds = Arrays.stream(WeaponProfile.values()).map(Enum::name).toList();
        LinkedHashSet<String> chestMerged = new LinkedHashSet<>(cfg.getStringList("loot.chest-weapons"));
        for (String id : allIds) {
            chestMerged.add(id);
        }
        cfg.set("loot.chest-weapons", new ArrayList<>(chestMerged));

        LinkedHashSet<String> legMerged = new LinkedHashSet<>(cfg.getStringList("loot.legendary-weapons"));
        for (WeaponProfile w : WeaponProfile.values()) {
            if (w.rarity() == WeaponRarity.LEGENDARY || w.rarity() == WeaponRarity.MYTHIC
                    || w.rarity() == WeaponRarity.GHOST) {
                legMerged.add(w.name());
            }
        }
        cfg.set("loot.legendary-weapons", new ArrayList<>(legMerged));
        plugin.saveConfig();
    }

    public void stopLoops() {
        if (airdropTask != null) {
            airdropTask.cancel();
            airdropTask = null;
        }
        if (boostTask != null) {
            boostTask.cancel();
            boostTask = null;
        }
        if (chestSpawnTask != null) {
            chestSpawnTask.cancel();
            chestSpawnTask = null;
        }
        for (String key : new HashSet<>(zoneChestKeys)) {
            removeChestBlockAtKey(key);
        }
        zoneChestKeys.clear();
        filledThisChest.clear();
        configuredChestCooldownUntil.clear();
        if (configuredChestHudTask != null) {
            configuredChestHudTask.cancel();
            configuredChestHudTask = null;
        }
        for (ArmorStand holo : configuredChestHolograms.values()) {
            if (holo != null && holo.isValid()) {
                holo.remove();
            }
        }
        configuredChestHolograms.clear();
        for (ArmorStand holo : airdropHolograms.values()) {
            if (holo != null && holo.isValid()) {
                holo.remove();
            }
        }
        airdropHolograms.clear();
        for (BlockDisplay disp : airdropFallingDisplays.values()) {
            if (disp != null && disp.isValid()) {
                disp.remove();
            }
        }
        airdropFallingDisplays.clear();
        airdropTypes.clear();
        if (balloonChestManager != null) {
            balloonChestManager.stopSchedule();
        }
    }

    public void openAdminMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE_COMPONENT);
        inv.setItem(10, named(Material.CHEST, "Ajouter coffre d'armes", NamedTextColor.GREEN));
        inv.setItem(11, named(Material.OAK_SIGN, "Zone: position 1", NamedTextColor.YELLOW));
        inv.setItem(12, named(Material.SPRUCE_SIGN, "Zone: position 2", NamedTextColor.GOLD));
        inv.setItem(13, named(Material.LIME_DYE, "Activer zone + monde", NamedTextColor.GREEN));
        inv.setItem(14, named(Material.CROSSBOW, "Ajouter arme DPMR tenue", NamedTextColor.GOLD));
        inv.setItem(15, named(Material.NETHERITE_SWORD, "Ajouter arme legendaire DPMR", NamedTextColor.LIGHT_PURPLE));
        inv.setItem(16, named(Material.BEACON, "Forcer largage legendaire", NamedTextColor.AQUA));
        player.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!MENU_TITLE_COMPONENT.equals(event.getView().title())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) {
            return;
        }
        switch (clicked.getType()) {
            case CHEST -> {
                Block target = player.getTargetBlockExact(6);
                if (target == null || !LootChestTier.isLootChestBlock(target.getType())) {
                    player.sendMessage(Component.text("Regarde un coffre DPMR (bois, piégé, ender, tonneau, fumoir, haut fourneau) à moins de 6 blocs.", NamedTextColor.RED));
                    return;
                }
                saveLocation("loot.weapon-chests", target.getLocation());
                player.sendMessage(Component.text("Coffre fixe: disparait apres pillage puis respawn (voir config).", NamedTextColor.GREEN));
            }
            case OAK_SIGN -> {
                saveZoneCorner(player, "loot.chest-spawn-zone.corner-a");
                player.sendMessage(Component.text("Zone coffre: position 1 enregistree.", NamedTextColor.YELLOW));
            }
            case SPRUCE_SIGN -> {
                saveZoneCorner(player, "loot.chest-spawn-zone.corner-b");
                player.sendMessage(Component.text("Zone coffre: position 2 enregistree.", NamedTextColor.GOLD));
            }
            case LIME_DYE -> {
                plugin.getConfig().set("loot.chest-spawn-zone.enabled", true);
                plugin.getConfig().set("loot.chest-spawn-zone.world", player.getWorld().getName());
                plugin.saveConfig();
                player.sendMessage(Component.text("Chest zone active on this world. Set 2 corners with the menu signs.", NamedTextColor.GREEN));
            }
            case CROSSBOW -> addHeldWeapon(player, "loot.chest-weapons");
            case NETHERITE_SWORD -> addHeldWeapon(player, "loot.legendary-weapons");
            case BEACON -> {
                spawnAirdrop();
                player.sendMessage(Component.text("Largage force.", NamedTextColor.AQUA));
            }
            default -> { }
        }
    }

    @EventHandler
    public void onPortableLootChestPlace(BlockPlaceEvent event) {
        if (!LootChestTier.isLootChestBlock(event.getBlockPlaced().getType())) {
            return;
        }
        ItemStack inHand = event.getItemInHand();
        if (inHand == null || !inHand.hasItemMeta()) {
            return;
        }
        Byte tag = inHand.getItemMeta().getPersistentDataContainer().get(keyPortableLootChest, PersistentDataType.BYTE);
        if (tag == null || tag < 1 || tag > 6) {
            return;
        }
        Player player = event.getPlayer();
        if (tag >= 4 && tag <= 6) {
            if (event.getBlockReplacedState().getType() != Material.WATER) {
                event.setCancelled(true);
                player.sendMessage(Component.text("Maritime Chests must be placed inside water (replace a water block).", NamedTextColor.RED));
                return;
            }
            Block placed = event.getBlockPlaced();
            BlockData data = placed.getBlockData();
            if (data instanceof Waterlogged wl) {
                wl.setWaterlogged(true);
                placed.setBlockData(wl);
            }
        }
        saveLocation("loot.weapon-chests", event.getBlockPlaced().getLocation());
        if (tag >= 4 && tag <= 6) {
            player.sendMessage(Component.text("Maritime Chest placed and registered.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Coffre DPMR place et enregistre.", NamedTextColor.GREEN));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.35f);
    }

    private void saveZoneCorner(Player player, String path) {
        Location loc = player.getLocation().getBlock().getLocation();
        plugin.getConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getConfig().set(path + ".x", loc.getBlockX());
        plugin.getConfig().set(path + ".y", loc.getBlockY());
        plugin.getConfig().set(path + ".z", loc.getBlockZ());
        plugin.saveConfig();
    }

    @EventHandler
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!LootChestTier.isLootChestBlock(block.getType())) {
            return;
        }
        if (isChestBreakerAxe(event.getItem())) {
            if (breakConfiguredChestSpawn(block, event.getPlayer())) {
                event.setCancelled(true);
            }
            return;
        }
        Location loc = block.getLocation();
        if (!isDpmrChest(loc)) {
            return;
        }
        String configuredKey = configuredChestKeyFromStructure(block);
        if (configuredKey != null) {
            long left = configuredChestCooldownUntil.getOrDefault(configuredKey, 0L) - System.currentTimeMillis();
            if (left > 0L) {
                event.setCancelled(true);
                long sec = Math.max(1L, (long) Math.ceil(left / 1000.0));
                event.getPlayer().sendActionBar(Component.text("Coffre loot : " + sec + "s", NamedTextColor.YELLOW));
                return;
            }
        }
        Set<String> structureKeys = connectedChestKeys(block);
        if (structureKeys.stream().anyMatch(filledThisChest::contains)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        filledThisChest.addAll(structureKeys);
        LootChestTier tier = LootChestTier.fromMaterial(block.getType());
        List<ItemStack> bundle = generateChestLootBundle(tier);
        if (bundle.isEmpty()) {
            for (String k : structureKeys) {
                filledThisChest.remove(k);
            }
            player.sendMessage(Component.text("Rien dans ce coffre.", NamedTextColor.RED));
            return;
        }
        for (ItemStack loot : bundle) {
            giveLootToPlayer(player, loot.clone());
        }
        List<String> cfgChests = plugin.getConfig().getStringList("loot.weapon-chests");
        boolean touchesConfigured = structureKeys.stream().anyMatch(cfgChests::contains);
        String canonical = touchesConfigured
                ? structureKeys.stream().filter(cfgChests::contains).min(String::compareTo).orElse(null)
                : null;
        showLootPopupAboveChest(loc, player, bundle);
        if (tier.isMaritime()) {
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.85f, 1.05f);
            block.getWorld().spawnParticle(Particle.SPLASH, loc.clone().add(0.5, 0.85, 0.5), 14, 0.35, 0.2, 0.35, 0.02);
            block.getWorld().spawnParticle(Particle.BUBBLE_POP, loc.clone().add(0.5, 0.9, 0.5), 10, 0.3, 0.15, 0.3, 0.01);
            if (block.getType() == Material.SMOKER) {
                block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0.5, 1.0, 0.5), 14, 0.35, 0.15, 0.35, 0.01);
            } else if (block.getType() == Material.BLAST_FURNACE) {
                block.getWorld().spawnParticle(Particle.SMALL_FLAME, loc.clone().add(0.5, 0.85, 0.5), 10, 0.3, 0.12, 0.3, 0.01);
            }
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.85f, 1.1f);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.55f, 1.15f);
        finishDpmrChestAfterLoot(block, player, structureKeys, touchesConfigured, canonical);
    }

    private void giveLootToPlayer(Player player, ItemStack loot) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(loot);
        for (ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private void showLootPopupAboveChest(Location chestBlockLoc, Player viewer, List<ItemStack> loots) {
        if (loots == null || loots.isEmpty()) {
            return;
        }
        World world = chestBlockLoc.getWorld();
        if (world == null) {
            return;
        }
        double baseY = 1.55;
        double step = 0.3;
        for (int i = 0; i < loots.size(); i++) {
            ItemStack loot = loots.get(i);
            Location at = chestBlockLoc.clone().add(0.5, baseY + i * step, 0.5);
            Component itemLine = describeLootStack(loot);
            ArmorStand line = world.spawn(at, ArmorStand.class);
            line.setInvisible(true);
            line.setMarker(true);
            line.setGravity(false);
            line.setCustomNameVisible(true);
            line.customName(Component.text(viewer.getName(), NamedTextColor.AQUA)
                    .append(Component.text(" : ", NamedTextColor.DARK_GRAY))
                    .append(itemLine));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (line.isValid()) {
                    line.remove();
                }
            }, 100L);
        }
    }

    private static Component describeLootStack(ItemStack stack) {
        if (stack != null && stack.hasItemMeta() && stack.getItemMeta().displayName() != null) {
            return stack.getItemMeta().displayName();
        }
        if (stack == null || stack.getType().isAir()) {
            return Component.text("?", NamedTextColor.GRAY);
        }
        return Component.translatable(stack.getType().translationKey());
    }

    private void finishDpmrChestAfterLoot(Block block, Player player, Set<String> structureKeys,
                                          boolean touchesConfigured, String canonicalKey) {
        for (String k : structureKeys) {
            filledThisChest.remove(k);
        }
        if (touchesConfigured && canonicalKey != null) {
            scheduleConfiguredChestRespawn(canonicalKey);
            player.sendActionBar(Component.text("Coffre en recharge.", NamedTextColor.GRAY));
        } else {
            removeChestBlocks(block);
            for (String k : structureKeys) {
                zoneChestKeys.remove(k);
            }
            player.sendActionBar(Component.text("Coffre ramassé.", NamedTextColor.GRAY));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.85f, 0.85f);
    }

    /**
     * Nombre d'objets independants par ouverture (defaut : tier I = 1, II = 2, III = 3).
     */
    private static int lootRollsForTier(FileConfiguration cfg, LootChestTier tier) {
        String path = "loot.chest-tier." + tier.configSection() + ".loot-rolls";
        int def = switch (tier) {
            case TIER_1, MARITIME_1 -> 1;
            case TIER_2, MARITIME_2 -> 2;
            case TIER_3, MARITIME_3 -> 3;
        };
        return Math.max(1, cfg.getInt(path, def));
    }

    /**
     * Plusieurs tirages : chaque roll suit les memes poids (arme / armure / conso + rarete).
     */
    private List<ItemStack> generateChestLootBundle(LootChestTier tier) {
        FileConfiguration cfg = plugin.getConfig();
        int n = lootRollsForTier(cfg, tier);
        List<ItemStack> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ItemStack one = generateSingleChestLoot(tier);
            if (one != null && !one.getType().isAir()) {
                out.add(one);
            }
        }
        return out;
    }

    /**
     * Un seul objet : arme, armure ou consommable selon les poids du tier.
     */
    private ItemStack generateSingleChestLoot(LootChestTier tier) {
        FileConfiguration cfg = plugin.getConfig();
        String base = "loot.chest-tier." + tier.configSection() + ".";
        int w = Math.max(0, cfg.getInt(base + "category-weapon-weight", 50));
        int a = Math.max(0, cfg.getInt(base + "category-armor-weight", 25));
        int c = Math.max(0, cfg.getInt(base + "category-consumable-weight", 25));
        int total = w + a + c;
        if (total <= 0) {
            w = 50;
            a = 25;
            c = 25;
            total = 100;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        if (roll < w) {
            WeaponRarity rarity = rollRarityForChestTier(cfg, tier);
            WeaponProfile p = rollWeaponForChest(cfg, null, rarity);
            if (p == null) {
                return armorManager.createRandomArmorPiece();
            }
            ItemStack weapon = weaponManager.createWeaponItem(p.name());
            return weapon != null ? weapon : armorManager.createRandomArmorPiece();
        }
        if (roll < w + a) {
            return armorManager.createRandomArmorPiece();
        }
        return rollSingleConsumableStack(cfg);
    }

    private ItemStack rollSingleConsumableStack(FileConfiguration cfg) {
        int perMin = Math.max(1, cfg.getInt("loot.bandages-per-stack-min", 1));
        int perMax = Math.max(perMin, cfg.getInt("loot.bandages-per-stack-max", 3));
        if (ThreadLocalRandom.current().nextBoolean()) {
            int n = perMin == perMax ? perMin : ThreadLocalRandom.current().nextInt(perMin, perMax + 1);
            return bandageManager.createConsumable(bandageManager.rollLootHealConsumable(), n);
        }
        int shMin = Math.max(1, cfg.getInt("loot.shield-potion-per-stack-min", 1));
        int shMax = Math.max(shMin, cfg.getInt("loot.shield-potion-per-stack-max", 1));
        int n = shMin == shMax ? shMin : ThreadLocalRandom.current().nextInt(shMin, shMax + 1);
        return bandageManager.createConsumable(bandageManager.rollLootShieldPotion(), n);
    }

    /** COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC, GHOST */
    private static int[] defaultRarityWeightsForTier(LootChestTier tier) {
        return switch (tier) {
            case TIER_1 -> new int[]{100, 28, 10, 4, 2, 0, 0};
            case TIER_2 -> new int[]{55, 28, 12, 4, 2, 1, 0};
            case TIER_3 -> new int[]{30, 25, 20, 12, 8, 4, 1};
            case MARITIME_1 -> new int[]{72, 26, 12, 7, 3, 0, 0};
            case MARITIME_2 -> new int[]{55, 28, 14, 8, 4, 1, 0};
            case MARITIME_3 -> new int[]{38, 26, 18, 12, 6, 2, 0};
        };
    }

    private WeaponRarity rollRarityForChestTier(FileConfiguration cfg, LootChestTier tier) {
        String base = "loot.chest-tier." + tier.configSection() + ".rarity-weights.";
        int[] def = defaultRarityWeightsForTier(tier);
        int c = Math.max(0, cfg.getInt(base + "COMMON", def[0]));
        int u = Math.max(0, cfg.getInt(base + "UNCOMMON", def[1]));
        int r = Math.max(0, cfg.getInt(base + "RARE", def[2]));
        int e = Math.max(0, cfg.getInt(base + "EPIC", def[3]));
        int l = Math.max(0, cfg.getInt(base + "LEGENDARY", def[4]));
        int m = Math.max(0, cfg.getInt(base + "MYTHIC", def[5]));
        int g = Math.max(0, cfg.getInt(base + "GHOST", def[6]));
        int sum = c + u + r + e + l + m + g;
        if (sum <= 0) {
            return WeaponRarity.COMMON;
        }
        int pick = ThreadLocalRandom.current().nextInt(sum);
        if (pick < c) {
            return WeaponRarity.COMMON;
        }
        pick -= c;
        if (pick < u) {
            return WeaponRarity.UNCOMMON;
        }
        pick -= u;
        if (pick < r) {
            return WeaponRarity.RARE;
        }
        pick -= r;
        if (pick < e) {
            return WeaponRarity.EPIC;
        }
        pick -= e;
        if (pick < l) {
            return WeaponRarity.LEGENDARY;
        }
        pick -= l;
        if (pick < m + g) {
            return pick < m ? WeaponRarity.MYTHIC : WeaponRarity.GHOST;
        }
        return WeaponRarity.COMMON;
    }

    @EventHandler
    public void onBoostPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getItem().getItemStack();
        if (item.getType() != Material.BEACON || item.getItemMeta() == null || item.getItemMeta().displayName() == null) {
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).toUpperCase(Locale.ROOT);
        if (plain.contains("INVINCIBILITY")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 10, 4));
        } else if (plain.contains("RAPID_FIRE")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 15, 3));
        } else if (plain.contains("SPEED")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 15, 2));
        } else {
            return;
        }
        player.sendMessage(Component.text("Boost active.", NamedTextColor.GREEN));
    }

    public void spawnAirdrop() {
        Location rawTarget = pickAirdropTarget();
        Location target = clampAirdropToGround(rawTarget);
        if (target == null || target.getWorld() == null) {
            return;
        }
        World world = target.getWorld();
        int spawnH = Math.max(10, plugin.getConfig().getInt("loot.airdrop.spawn-height", 28));
        Location spawn = target.clone().add(0.5, spawnH, 0.5);

        String key = locKey(target);
        if (airdropFallingDisplays.containsKey(key) || airdropTypes.containsKey(key)) {
            return;
        }
        AirdropType type = randomAirdropType();
        airdropTypes.put(key, type);

        world.spawnParticle(Particle.FIREWORK, spawn, 45, 1.2, 1.2, 1.2, 0.02);
        world.playSound(spawn, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9f, 1.1f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            String tlabel = I18n.string(languageStore.get(p), airdropTypeMessageKey(type));
            p.sendMessage(I18n.component(languageStore.get(p), type.color, "airdrop.approaching", tlabel));
        }

        BlockDisplay disp = world.spawn(spawn, BlockDisplay.class);
        disp.setBlock(Bukkit.createBlockData(type.fallingMaterial));
        disp.setTransformation(new Transformation(
                new org.joml.Vector3f(0f, 0f, 0f),
                new org.joml.Quaternionf(),
                new org.joml.Vector3f(1f, 1f, 1f),
                new org.joml.Quaternionf()
        ));
        airdropFallingDisplays.put(key, disp);
        ArmorStand holo = spawnAirdropHologram(spawn.clone().add(0, 1.25, 0), type.color,
                "Airdrop " + englishAirdropTypeLabel(type) + " incoming");
        airdropHolograms.put(key, holo);

        double speed = Math.max(0.01, plugin.getConfig().getDouble("loot.airdrop.fall-speed", 0.06));
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!disp.isValid()) {
                airdropFallingDisplays.remove(key);
                removeAirdropHologram(key);
                task.cancel();
                return;
            }
            Location cur = disp.getLocation();
            Location next = cur.clone().add(0, -speed, 0);
            disp.teleport(next);
            ArmorStand h = airdropHolograms.get(key);
            if (h != null && h.isValid()) {
                h.teleport(next.clone().add(0, 1.25, 0));
            }
            world.spawnParticle(type.trailParticle, next.clone().add(0, 0.4, 0), 2, 0.05, 0.05, 0.05, 0.001);

            if (next.getY() <= target.getY() + 0.5) {
                disp.remove();
                airdropFallingDisplays.remove(key);
                placeAirdropChest(target);
                updateAirdropHologramLanded(key, target.clone().add(0.5, 1.6, 0.5));
                world.playSound(target, type.landSound, 0.9f, 1.2f);
                world.spawnParticle(type.landParticle, target.clone().add(0.5, 0.6, 0.5), 12, 0.25, 0.25, 0.25, 0.01);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String tlabel = I18n.string(languageStore.get(p), airdropTypeMessageKey(type));
                    p.sendMessage(I18n.component(languageStore.get(p), type.color, "airdrop.landed",
                            tlabel, target.getBlockX(), target.getBlockY(), target.getBlockZ()));
                }
                task.cancel();
            }
        }, 1L, 1L);
    }

    private AirdropType randomAirdropType() {
        AirdropType[] values = AirdropType.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private Location clampAirdropToGround(Location target) {
        if (target == null || target.getWorld() == null) {
            return null;
        }
        World world = target.getWorld();
        int x = target.getBlockX();
        int z = target.getBlockZ();
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Location out = new Location(world, x, groundY + 1, z);
        Material under = out.clone().subtract(0, 1, 0).getBlock().getType();
        if (!under.isSolid()) {
            return null;
        }
        return out;
    }

    public void setAirdropLocation(Player player) {
        Location loc = player.getLocation().getBlock().getLocation();
        plugin.getConfig().set("loot.airdrop.fixed-enabled", true);
        plugin.getConfig().set("loot.airdrop.fixed-location.world", loc.getWorld().getName());
        plugin.getConfig().set("loot.airdrop.fixed-location.x", loc.getBlockX());
        plugin.getConfig().set("loot.airdrop.fixed-location.y", loc.getBlockY());
        plugin.getConfig().set("loot.airdrop.fixed-location.z", loc.getBlockZ());
        plugin.saveConfig();
        player.sendMessage(Component.text("Airdrop fixe: " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(), NamedTextColor.AQUA));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
    }

    private Location pickAirdropTarget() {
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean("loot.airdrop.fixed-enabled", false)) {
            String w = cfg.getString("loot.airdrop.fixed-location.world", "world");
            World world = Bukkit.getWorld(w);
            if (world == null) {
                return null;
            }
            int x = cfg.getInt("loot.airdrop.fixed-location.x", 0);
            int y = cfg.getInt("loot.airdrop.fixed-location.y", 80);
            int z = cfg.getInt("loot.airdrop.fixed-location.z", 0);
            return new Location(world, x, y, z);
        }
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return null;
        }
        Player ref = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        Location base = ref.getLocation().getBlock().getLocation();
        return base.add(ThreadLocalRandom.current().nextInt(-25, 26), 0, ThreadLocalRandom.current().nextInt(-25, 26));
    }

    private void placeAirdropChest(Location targetBlock) {
        Location loc = targetBlock.getBlock().getLocation();
        Block b = loc.getBlock();
        if (!b.getType().isAir() && b.getType().isSolid()) {
            return;
        }
        String key = locKey(loc);
        AirdropType dropType = airdropTypes.getOrDefault(key, AirdropType.TACTIQUE);
        Material chestMat = switch (dropType) {
            case TACTIQUE -> Material.CHEST;
            case MEDICAL -> Material.TRAPPED_CHEST;
            case TECHNO -> Material.ENDER_CHEST;
        };
        b.setType(chestMat);
        airdropOpened.remove(key);
    }

    private ItemStack generateSingleAirdropLoot(AirdropType type) {
        if (type == AirdropType.MEDICAL) {
            return generateMedicalAirdropSingleItem();
        }
        LootChestTier tier = switch (type) {
            case TACTIQUE -> LootChestTier.TIER_1;
            case TECHNO -> LootChestTier.TIER_3;
            default -> LootChestTier.TIER_1;
        };
        return generateSingleChestLoot(tier);
    }

    /** Un seul lot consommable / soin pour largage médical. */
    private ItemStack generateMedicalAirdropSingleItem() {
        FileConfiguration cfg = plugin.getConfig();
        String base = "loot.airdrop.medical.";
        int pick = ThreadLocalRandom.current().nextInt(5);
        if (pick <= 1) {
            int bandPerMin = Math.max(1, cfg.getInt(base + "bandages-per-stack-min", 2));
            int bandPerMax = Math.max(bandPerMin, cfg.getInt(base + "bandages-per-stack-max", 6));
            int n = bandPerMin == bandPerMax ? bandPerMin : ThreadLocalRandom.current().nextInt(bandPerMin, bandPerMax + 1);
            return bandageManager.createConsumable(bandageManager.rollLootHealConsumable(), n);
        }
        if (pick == 2) {
            int shPerMin = Math.max(1, cfg.getInt(base + "shield-potion-per-stack-min", 1));
            int shPerMax = Math.max(shPerMin, cfg.getInt(base + "shield-potion-per-stack-max", 2));
            int n = shPerMin == shPerMax ? shPerMin : ThreadLocalRandom.current().nextInt(shPerMin, shPerMax + 1);
            return bandageManager.createConsumable(bandageManager.rollLootShieldPotion(), n);
        }
        if (pick == 3) {
            int mediPerMin = Math.max(1, cfg.getInt(base + "medikit-per-stack-min", 1));
            int mediPerMax = Math.max(mediPerMin, cfg.getInt(base + "medikit-per-stack-max", 2));
            int n = mediPerMin == mediPerMax ? mediPerMin : ThreadLocalRandom.current().nextInt(mediPerMin, mediPerMax + 1);
            return bandageManager.createConsumable(DpmrConsumable.MEDIKIT, n);
        }
        if (pick == 4 && cfg.getBoolean(base + "include-lance-soin", true)) {
            ItemStack lance = weaponManager.createWeaponItem(WeaponProfile.LANCE_SOIN.name());
            if (lance != null) {
                return lance;
            }
        }
        if (cfg.getBoolean(base + "include-serum-soin", true)) {
            ItemStack serum = weaponManager.createWeaponItem(WeaponProfile.SERUM_SOIN.name());
            if (serum != null) {
                return serum;
            }
        }
        return bandageManager.createConsumable(bandageManager.rollLootHealConsumable(), 4);
    }

    private void startAirdropOpen(Player player, Location chestLoc) {
        String key = locKey(chestLoc);
        if (airdropOpened.contains(key)) {
            I18n.actionBar(player, NamedTextColor.GRAY, "airdrop.already_open");
            return;
        }
        if (!airdropTypes.containsKey(key)) {
            return;
        }
        String sessionKey = key + ":" + player.getUniqueId();
        if (airdropOpenTasks.containsKey(sessionKey)) {
            return;
        }
        int sec = Math.max(1, plugin.getConfig().getInt("loot.airdrop.open-seconds", 5));
        int totalTicks = sec * 20;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.25f);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = totalTicks;
            @Override
            public void run() {
                if (!player.isOnline()) {
                    BukkitTask t = airdropOpenTasks.remove(sessionKey);
                    if (t != null) {
                        t.cancel();
                    }
                    return;
                }
                left--;
                int pct = (int) Math.round(100.0 * (totalTicks - left) / totalTicks);
                I18n.actionBar(player, NamedTextColor.GOLD, "airdrop.opening", pct);
                if (left % 10 == 0) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
                }
                if (left <= 0) {
                    airdropOpened.add(key);
                    AirdropType dropType = airdropTypes.get(key);
                    ItemStack loot = dropType != null ? generateSingleAirdropLoot(dropType) : generateSingleChestLoot(LootChestTier.TIER_1);
                    if (loot != null && !loot.getType().isAir()) {
                        giveLootToPlayer(player, loot.clone());
                        showLootPopupAboveChest(chestLoc, player, List.of(loot));
                        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.9f, 1.15f);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.55f, 1.15f);
                    }
                    completeAirdropClaim(key, chestLoc);
                    BukkitTask t = airdropOpenTasks.remove(sessionKey);
                    if (t != null) {
                        t.cancel();
                    }
                }
            }
        }, 1L, 1L);
        airdropOpenTasks.put(sessionKey, task);
    }

    private void completeAirdropClaim(String key, Location chestLoc) {
        if (chestLoc.getWorld() != null) {
            Block block = chestLoc.getBlock();
            if (LootChestTier.isLootChestBlock(block.getType())) {
                block.setType(Material.AIR);
                chestLoc.getWorld().playSound(chestLoc, Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.2f);
                chestLoc.getWorld().spawnParticle(Particle.END_ROD, chestLoc.clone().add(0.5, 0.6, 0.5), 18, 0.35, 0.35, 0.35, 0.02);
            }
        }
        airdropOpened.remove(key);
        removeAirdropHologram(key);
    }

    @EventHandler
    public void onAirdropInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!LootChestTier.isLootChestBlock(event.getClickedBlock().getType())) {
            return;
        }
        Location loc = event.getClickedBlock().getLocation();
        String key = locKey(loc);
        if (!airdropTypes.containsKey(key)) {
            return;
        }
        event.setCancelled(true);
        startAirdropOpen(event.getPlayer(), loc);
    }

    private ArmorStand spawnAirdropHologram(Location at, NamedTextColor color, String text) {
        if (at.getWorld() == null) {
            return null;
        }
        ArmorStand as = at.getWorld().spawn(at, ArmorStand.class);
        as.setInvisible(true);
        as.setMarker(true);
        as.setGravity(false);
        as.setCustomNameVisible(true);
        as.customName(Component.text(text, color, TextDecoration.BOLD));
        return as;
    }

    private void updateAirdropHologramLanded(String key, Location at) {
        ArmorStand holo = airdropHolograms.get(key);
        if (holo == null || !holo.isValid()) {
            return;
        }
        AirdropType type = airdropTypes.getOrDefault(key, AirdropType.TACTIQUE);
        holo.teleport(at);
        holo.customName(Component.text("Airdrop " + type.label, type.color, TextDecoration.BOLD)
                .append(Component.text(" • Right-click", NamedTextColor.YELLOW)));
    }

    private void removeAirdropHologram(String key) {
        ArmorStand holo = airdropHolograms.remove(key);
        if (holo != null && holo.isValid()) {
            holo.remove();
        }
        airdropTypes.remove(key);
    }

    public void forceSpawnZoneChest() {
        trySpawnZoneChest();
    }

    private void trySpawnZoneChest() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("loot.chest-spawn-zone.enabled", false)) {
            return;
        }
        int maxActive = Math.max(1, cfg.getInt("loot.chest-spawn-max-active", 5));
        if (zoneChestKeys.size() >= maxActive) {
            return;
        }
        Location surface = randomSurfaceInZone();
        if (surface == null) {
            return;
        }
        Block b = surface.getBlock();
        b.setType(rollZoneChestTier(cfg).material());
        String key = locKey(surface);
        zoneChestKeys.add(key);
        Bukkit.broadcast(Component.text("[DPMR] A loot chest has appeared!", NamedTextColor.GREEN));
        surface.getWorld().playSound(surface, Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
    }

    private Location randomSurfaceInZone() {
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("loot.chest-spawn-zone.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        int ax = cfg.getInt("loot.chest-spawn-zone.corner-a.x", 0);
        int ay = cfg.getInt("loot.chest-spawn-zone.corner-a.y", 60);
        int az = cfg.getInt("loot.chest-spawn-zone.corner-a.z", 0);
        int bx = cfg.getInt("loot.chest-spawn-zone.corner-b.x", 16);
        int by = cfg.getInt("loot.chest-spawn-zone.corner-b.y", 80);
        int bz = cfg.getInt("loot.chest-spawn-zone.corner-b.z", 16);
        int minX = Math.min(ax, bx);
        int maxX = Math.max(ax, bx);
        int minZ = Math.min(az, bz);
        int maxZ = Math.max(az, bz);
        int minY = Math.min(ay, by);
        int maxY = Math.max(ay, by);
        if (maxX - minX < 1 || maxZ - minZ < 1) {
            return null;
        }
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(minX, maxX + 1);
            int z = ThreadLocalRandom.current().nextInt(minZ, maxZ + 1);
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (y < minY) {
                y = minY;
            }
            if (y > maxY) {
                continue;
            }
            Location loc = new Location(world, x, y + 1, z);
            if (loc.getBlock().getType().isAir() && loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                return loc;
            }
        }
        return null;
    }

    private LootChestTier rollZoneChestTier(FileConfiguration cfg) {
        int w1 = Math.max(0, cfg.getInt("loot.zone-chest-tier-weights.tier-1", 50));
        int w2 = Math.max(0, cfg.getInt("loot.zone-chest-tier-weights.tier-2", 35));
        int w3 = Math.max(0, cfg.getInt("loot.zone-chest-tier-weights.tier-3", 15));
        int total = w1 + w2 + w3;
        if (total <= 0) {
            return LootChestTier.TIER_1;
        }
        int r = ThreadLocalRandom.current().nextInt(total);
        if (r < w1) {
            return LootChestTier.TIER_1;
        }
        r -= w1;
        if (r < w2) {
            return LootChestTier.TIER_2;
        }
        return LootChestTier.TIER_3;
    }

    private void spawnBoost() {
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        Player ref = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        Location dropLoc = ref.getLocation().clone().add(
                ThreadLocalRandom.current().nextDouble(-20, 20),
                2.5,
                ThreadLocalRandom.current().nextDouble(-20, 20)
        );
        List<String> boosts = plugin.getConfig().getStringList("boosts.available");
        if (boosts.isEmpty()) {
            return;
        }
        String boost = boosts.get(ThreadLocalRandom.current().nextInt(boosts.size())).toUpperCase(Locale.ROOT);
        ItemStack stack = named(Material.BEACON, "BOOST_" + boost, NamedTextColor.AQUA);
        Item item = dropLoc.getWorld().dropItem(dropLoc, stack);
        item.setGlowing(true);
        Bukkit.broadcast(Component.text("[DPMR] A boost has appeared!", NamedTextColor.AQUA));
    }

    /**
     * Coffre événement hélicoptère : plusieurs armes épique/légendaire, armures, soins.
     */
    public void fillHelicopterSupplyChest(Inventory inventory) {
        inventory.clear();
        FileConfiguration cfg = plugin.getConfig();
        String base = "npc-war-world.helicopter.chest.";
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int wMin = Math.max(1, cfg.getInt(base + "weapon-rolls-min", 2));
        int wMax = Math.max(wMin, cfg.getInt(base + "weapon-rolls-max", 4));
        double legChance = Math.min(1.0, Math.max(0.0, cfg.getDouble(base + "legendary-chance-per-weapon", 0.38)));
        int weaponRolls = wMin == wMax ? wMin : rnd.nextInt(wMin, wMax + 1);
        Set<WeaponProfile> rolledThisChest = new HashSet<>();
        for (int i = 0; i < weaponRolls; i++) {
            WeaponRarity tier = rnd.nextDouble() < legChance ? WeaponRarity.LEGENDARY : WeaponRarity.EPIC;
            WeaponProfile rolled = rollWeaponForChest(cfg, rolledThisChest, tier);
            if (rolled == null) {
                continue;
            }
            rolledThisChest.add(rolled);
            ItemStack weapon = weaponManager.createWeaponItem(rolled.name());
            placeLootItem(inventory, weapon, true);
        }
        int armorMin = Math.max(0, cfg.getInt(base + "armor-pieces-min", 1));
        int armorMax = Math.max(armorMin, cfg.getInt(base + "armor-pieces-max", 2));
        armorMax = Math.min(4, armorMax);
        int armorPieces = armorMin == armorMax ? armorMin : rnd.nextInt(armorMin, armorMax + 1);
        for (int a = 0; a < armorPieces; a++) {
            placeLootItem(inventory, armorManager.createRandomArmorPiece(), true);
        }
        int bandStacksMin = Math.max(0, cfg.getInt(base + "bandage-stacks-min", 3));
        int bandStacksMax = Math.max(bandStacksMin, cfg.getInt(base + "bandage-stacks-max", 6));
        int stacks = rnd.nextInt(bandStacksMin, bandStacksMax + 1);
        int perStackMin = Math.max(1, cfg.getInt(base + "bandages-per-stack-min", 2));
        int perStackMax = Math.max(perStackMin, cfg.getInt(base + "bandages-per-stack-max", 5));
        for (int s = 0; s < stacks; s++) {
            int bandCount = rnd.nextInt(perStackMin, perStackMax + 1);
            DpmrConsumable healType = bandageManager.rollLootHealConsumable();
            placeLootItem(inventory, bandageManager.createConsumable(healType, bandCount), false);
        }
        int shieldStacksMin = Math.max(0, cfg.getInt(base + "shield-potion-stacks-min", 1));
        int shieldStacksMax = Math.max(shieldStacksMin, cfg.getInt(base + "shield-potion-stacks-max", 2));
        int shieldStacks = rnd.nextInt(shieldStacksMin, shieldStacksMax + 1);
        int shieldPerMin = Math.max(1, cfg.getInt(base + "shield-potion-per-stack-min", 1));
        int shieldPerMax = Math.max(shieldPerMin, cfg.getInt(base + "shield-potion-per-stack-max", 2));
        for (int s = 0; s < shieldStacks; s++) {
            int n = rnd.nextInt(shieldPerMin, shieldPerMax + 1);
            DpmrConsumable shieldType = bandageManager.rollLootShieldPotion();
            placeLootItem(inventory, bandageManager.createConsumable(shieldType, n), false);
        }
    }

    /** Armes / armures au centre du coffre en priorité, soins autour. */
    private static void placeLootItem(Inventory inventory, ItemStack stack, boolean preferCenter) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        int slot = preferCenter ? firstPreferredEmptySlot(inventory) : firstEmptySlot(inventory);
        if (slot < 0) {
            slot = firstEmptySlot(inventory);
        }
        if (slot >= 0) {
            inventory.setItem(slot, stack);
        }
    }

    private static int firstPreferredEmptySlot(Inventory inventory) {
        int size = inventory.getSize();
        int center = size / 2;
        if (slotEmpty(inventory, center)) {
            return center;
        }
        for (int radius = 1; radius < size; radius++) {
            for (int sign : new int[]{-1, 1}) {
                int s = center + sign * radius;
                if (s >= 0 && s < size && slotEmpty(inventory, s)) {
                    return s;
                }
            }
        }
        return -1;
    }

    private static boolean slotEmpty(Inventory inventory, int slot) {
        ItemStack it = inventory.getItem(slot);
        return it == null || it.getType().isAir();
    }

    private static int firstEmptySlot(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType().isAir()) {
                return i;
            }
        }
        return -1;
    }

    private boolean isDpmrChest(Location location) {
        Block b = location.getBlock();
        if (!LootChestTier.isLootChestBlock(b.getType())) {
            return false;
        }
        for (String k : connectedChestKeys(b)) {
            if (zoneChestKeys.contains(k)) {
                return true;
            }
            for (String raw : plugin.getConfig().getStringList("loot.weapon-chests")) {
                if (raw.equals(k)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> connectedChestKeys(Block start) {
        Set<String> keys = new HashSet<>();
        if (!LootChestTier.isLootChestBlock(start.getType())) {
            return keys;
        }
        Material kind = start.getType();
        keys.add(locKey(start.getLocation()));
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block n = start.getRelative(face);
            if (n.getType() == kind) {
                keys.add(locKey(n.getLocation()));
            }
        }
        return keys;
    }

    private static String locKey(Location location) {
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }

    private void scheduleConfiguredChestRespawn(String canonicalKey) {
        long sec = Math.max(5L, plugin.getConfig().getLong("loot.configured-chest-respawn-seconds", 120));
        configuredChestCooldownUntil.put(canonicalKey, System.currentTimeMillis() + sec * 1000L);
        updateConfiguredChestHologram(canonicalKey);
    }

    private void startConfiguredChestHudTask() {
        if (configuredChestHudTask != null) {
            configuredChestHudTask.cancel();
        }
        configuredChestHudTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickConfiguredChestHud, 20L, 20L);
    }

    private void tickConfiguredChestHud() {
        long now = System.currentTimeMillis();
        List<String> finishedCooldown = new ArrayList<>();
        for (Map.Entry<String, Long> e : configuredChestCooldownUntil.entrySet()) {
            if (e.getValue() <= now) {
                finishedCooldown.add(e.getKey());
            }
        }
        for (String key : finishedCooldown) {
            configuredChestCooldownUntil.remove(key);
            Location loc = parseLocKey(key);
            if (loc != null && loc.getWorld() != null && LootChestTier.isLootChestBlock(loc.getBlock().getType())) {
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.75f, 1.35f);
                loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0.5, 1.0, 0.5), 8, 0.25, 0.2, 0.25, 0.01);
            }
            updateConfiguredChestHologram(key);
        }
        for (String key : new ArrayList<>(configuredChestCooldownUntil.keySet())) {
            updateConfiguredChestHologram(key);
        }
        tickEpicConfiguredChestParticles();
        tickMaritimeConfiguredChestParticles();
    }

    /** Particules autour des coffres configurés tier III (ender). */
    private void tickEpicConfiguredChestParticles() {
        for (String key : plugin.getConfig().getStringList("loot.weapon-chests")) {
            Location loc = parseLocKey(key);
            if (loc == null || loc.getWorld() == null || loc.getBlock().getType() != Material.ENDER_CHEST) {
                continue;
            }
            Location center = loc.clone().add(0.5, 1.15, 0.5);
            World w = loc.getWorld();
            w.spawnParticle(Particle.ENCHANT, center, 10, 0.4, 0.25, 0.4, 0.02);
            w.spawnParticle(Particle.END_ROD, center, 3, 0.12, 0.08, 0.12, 0.01);
        }
    }

    /** Particules autour des Maritime Chest (barrel) enregistrés. */
    private void tickMaritimeConfiguredChestParticles() {
        for (String key : plugin.getConfig().getStringList("loot.weapon-chests")) {
            Location loc = parseLocKey(key);
            if (loc == null || loc.getWorld() == null || loc.getBlock().getType() != Material.BARREL) {
                continue;
            }
            Location center = loc.clone().add(0.5, 1.0, 0.5);
            World w = loc.getWorld();
            w.spawnParticle(Particle.BUBBLE_POP, center, 6, 0.38, 0.18, 0.38, 0.02);
            w.spawnParticle(Particle.BUBBLE_COLUMN_UP, center, 5, 0.32, 0.12, 0.32, 0.015);
            w.spawnParticle(Particle.DRIPPING_WATER, loc.clone().add(0.5, 1.15, 0.5), 2, 0.2, 0.1, 0.2, 0);
        }
    }

    private boolean isChestBreakerAxe(ItemStack item) {
        if (item == null || item.getType() != Material.STONE_AXE || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(keyChestBreakerAxe, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean breakConfiguredChestSpawn(Block block, Player player) {
        String key = configuredChestKeyFromStructure(block);
        if (key == null) {
            player.sendActionBar(Component.text("This chest is not a configured LootChest.", NamedTextColor.GRAY));
            return false;
        }
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList("loot.weapon-chests"));
        if (!list.remove(key)) {
            player.sendActionBar(Component.text("Spawn already removed.", NamedTextColor.GRAY));
            return false;
        }
        plugin.getConfig().set("loot.weapon-chests", list);
        plugin.saveConfig();
        configuredChestCooldownUntil.remove(key);
        ArmorStand holo = configuredChestHolograms.remove(key);
        if (holo != null && holo.isValid()) {
            holo.remove();
        }
        removeChestBlocks(block);
        player.sendMessage(Component.text("Spawn LootChest retire: " + key, NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.9f, 0.75f);
        return true;
    }

    private String configuredChestKeyFromStructure(Block block) {
        Set<String> keys = connectedChestKeys(block);
        List<String> cfg = plugin.getConfig().getStringList("loot.weapon-chests");
        return keys.stream().filter(cfg::contains).min(String::compareTo).orElse(null);
    }

    private void refreshConfiguredChestHolograms() {
        List<String> configured = plugin.getConfig().getStringList("loot.weapon-chests");
        Set<String> wanted = new HashSet<>(configured);
        for (String existing : new HashSet<>(configuredChestHolograms.keySet())) {
            if (!wanted.contains(existing)) {
                ArmorStand holo = configuredChestHolograms.remove(existing);
                if (holo != null && holo.isValid()) {
                    holo.remove();
                }
            }
        }
        for (String key : configured) {
            updateConfiguredChestHologram(key);
        }
    }

    private void updateConfiguredChestHologram(String key) {
        Location loc = parseLocKey(key);
        if (loc == null || loc.getWorld() == null || !LootChestTier.isLootChestBlock(loc.getBlock().getType())) {
            ArmorStand old = configuredChestHolograms.remove(key);
            if (old != null && old.isValid()) {
                old.remove();
            }
            return;
        }
        LootChestTier tier = LootChestTier.fromMaterial(loc.getBlock().getType());
        String holoTitle = tier.configuredHologramTitle();
        Location holoLoc = loc.clone().add(0.5, 1.35, 0.5);
        ArmorStand holo = configuredChestHolograms.get(key);
        if (holo == null || !holo.isValid()) {
            holo = spawnAirdropHologram(holoLoc, tier.labelColor(), holoTitle);
            configuredChestHolograms.put(key, holo);
        } else if (holoLoc.getWorld() != null && holo.getWorld() == holoLoc.getWorld()
                && holo.getLocation().distanceSquared(holoLoc) > 0.04) {
            holo.teleport(holoLoc);
        }
        long leftMs = configuredChestCooldownUntil.getOrDefault(key, 0L) - System.currentTimeMillis();
        Component ready = Component.text(tier.isMaritime() ? "Ready" : "Prêt", NamedTextColor.GREEN);
        if (leftMs > 0L) {
            long sec = Math.max(1L, (long) Math.ceil(leftMs / 1000.0));
            holo.customName(Component.text(holoTitle, tier.labelColor(), TextDecoration.BOLD)
                    .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(sec + "s", NamedTextColor.YELLOW)));
        } else {
            holo.customName(Component.text(holoTitle, tier.labelColor(), TextDecoration.BOLD)
                    .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                    .append(ready));
        }
    }

    private static Location parseLocKey(String key) {
        String[] split = key.split(";");
        if (split.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(split[0]);
        if (world == null) {
            return null;
        }
        try {
            int x = Integer.parseInt(split[1]);
            int y = Integer.parseInt(split[2]);
            int z = Integer.parseInt(split[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private WeaponProfile rollWeaponForChest(FileConfiguration cfg, Set<WeaponProfile> excluded, WeaponRarity forcedRarity) {
        WeaponRarity targetTier = forcedRarity;
        if (targetTier == null) {
            WeaponRarity rolledTier = rollRarityTier(cfg);
            targetTier = rolledTier;
            // Nerf leger des coffres aleatoires (zone): legendaire tres rare; epique possible.
            if (targetTier == WeaponRarity.LEGENDARY) {
                targetTier = ThreadLocalRandom.current().nextDouble() < 0.85 ? WeaponRarity.RARE : WeaponRarity.EPIC;
            } else if (targetTier == WeaponRarity.EPIC) {
                double d = ThreadLocalRandom.current().nextDouble();
                if (d < 0.55) {
                    targetTier = WeaponRarity.EPIC;
                } else if (d < 0.82) {
                    targetTier = WeaponRarity.RARE;
                } else {
                    targetTier = WeaponRarity.UNCOMMON;
                }
            } else if (targetTier == WeaponRarity.RARE) {
                targetTier = ThreadLocalRandom.current().nextDouble() < 0.45 ? WeaponRarity.UNCOMMON : WeaponRarity.RARE;
            }
        }
        Set<WeaponProfile> poolSet = new LinkedHashSet<>();
        for (String id : cfg.getStringList("loot.chest-weapons")) {
            WeaponProfile p = WeaponProfile.fromId(id);
            if (p != null) {
                poolSet.add(p);
            }
        }
        List<WeaponProfile> pool = new ArrayList<>(poolSet);
        // Si la config est trop pauvre, on élargit le pool pour eviter les coffres monotones.
        if (pool.size() <= 1) {
            pool.addAll(Arrays.asList(WeaponProfile.values()));
        }
        if (pool.isEmpty()) {
            pool.addAll(Arrays.asList(WeaponProfile.values()));
        }
        WeaponRarity finalTier = targetTier;
        List<WeaponProfile> match = pool.stream()
                .filter(p -> p.rarity() == finalTier)
                .filter(p -> excluded == null || !excluded.contains(p))
                .toList();
        List<WeaponProfile> pick = match;
        if (pick.isEmpty()) {
            pick = pool.stream()
                    .filter(p -> excluded == null || !excluded.contains(p))
                    .toList();
        }
        if (pick.isEmpty()) {
            pick = pool;
        }
        return pick.get(ThreadLocalRandom.current().nextInt(pick.size()));
    }

    private static WeaponRarity rollRarityTier(FileConfiguration cfg) {
        int c = Math.max(0, cfg.getInt("loot.rarity-weights.COMMON", 48));
        int u = Math.max(0, cfg.getInt("loot.rarity-weights.UNCOMMON", 28));
        int r = Math.max(0, cfg.getInt("loot.rarity-weights.RARE", 14));
        int e = Math.max(0, cfg.getInt("loot.rarity-weights.EPIC", 7));
        int l = Math.max(0, cfg.getInt("loot.rarity-weights.LEGENDARY", 3));
        int m = Math.max(0, cfg.getInt("loot.rarity-weights.MYTHIC", 1));
        int g = Math.max(0, cfg.getInt("loot.rarity-weights.GHOST", 0));
        int total = c + u + r + e + l + m + g;
        if (total <= 0) {
            return WeaponRarity.COMMON;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        if (roll < c) {
            return WeaponRarity.COMMON;
        }
        roll -= c;
        if (roll < u) {
            return WeaponRarity.UNCOMMON;
        }
        roll -= u;
        if (roll < r) {
            return WeaponRarity.RARE;
        }
        roll -= r;
        if (roll < e) {
            return WeaponRarity.EPIC;
        }
        roll -= e;
        if (roll < l) {
            return WeaponRarity.LEGENDARY;
        }
        roll -= l;
        if (roll < m + g) {
            return roll < m ? WeaponRarity.MYTHIC : WeaponRarity.GHOST;
        }
        return WeaponRarity.COMMON;
    }

    private void removeChestBlockAtKey(String key) {
        String[] split = key.split(";");
        if (split.length != 4) {
            return;
        }
        World world = Bukkit.getWorld(split[0]);
        if (world == null) {
            return;
        }
        int x = Integer.parseInt(split[1]);
        int y = Integer.parseInt(split[2]);
        int z = Integer.parseInt(split[3]);
        Block b = world.getBlockAt(x, y, z);
        if (LootChestTier.isLootChestBlock(b.getType())) {
            removeChestBlocks(b);
        }
    }

    private void removeChestBlocks(Block start) {
        if (!LootChestTier.isLootChestBlock(start.getType())) {
            return;
        }
        Material kind = start.getType();
        Set<Block> blocks = new HashSet<>();
        blocks.add(start);
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block n = start.getRelative(face);
            if (n.getType() == kind) {
                blocks.add(n);
            }
        }
        for (Block b : blocks) {
            b.setType(Material.AIR);
        }
    }

    private void saveLocation(String path, Location location) {
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(path));
        String key = locKey(location);
        if (!list.contains(key)) {
            list.add(key);
            plugin.getConfig().set(path, list);
            plugin.saveConfig();
            if ("loot.weapon-chests".equals(path)) {
                updateConfiguredChestHologram(key);
            }
        }
    }

    private void addHeldWeapon(Player player, String path) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage(Component.text("Hold a weapon in your hand.", NamedTextColor.RED));
            return;
        }
        String weaponId = held.getItemMeta() != null && held.getItemMeta().displayName() != null
                ? PlainTextComponentSerializer.plainText().serialize(held.getItemMeta().displayName())
                : held.getType().name();
        weaponId = weaponId.replace("ARME ", "").trim().toUpperCase(Locale.ROOT);
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(path));
        if (!list.contains(weaponId)) {
            list.add(weaponId);
            plugin.getConfig().set(path, list);
            plugin.saveConfig();
        }
        player.sendMessage(Component.text("Arme ajoutee: " + weaponId, NamedTextColor.GREEN));
    }

    private ItemStack named(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        item.setItemMeta(meta);
        return item;
    }
}
