package fr.dpmr.game;

import fr.dpmr.i18n.GameLocale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ModificationTableListener implements Listener {

    public static final Component GUI_TITLE = Component.text("⚒ Armory bench", NamedTextColor.GOLD, TextDecoration.BOLD);

    private static final int SLOT_WEAPON = 13;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_CLOSE = 49;
    /** Paliers I–V au-dessus des colonnes (arme au centre). */
    private static final int[] SLOT_TIER_HEADER = {10, 11, 12, 14, 15};
    /** Libellés des 3 voies (à gauche de chaque ligne d’upgrade). */
    private static final int SLOT_PATH_A = 18;
    private static final int SLOT_PATH_B = 27;
    private static final int SLOT_PATH_C = 36;

    private final JavaPlugin plugin;
    private final WeaponManager weaponManager;
    private final ModificationTableRegistry registry;

    private final Map<UUID, BukkitTask> workshopAmbientTasks = new HashMap<>();

    public ModificationTableListener(JavaPlugin plugin, WeaponManager weaponManager, ModificationTableRegistry registry) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
        this.registry = registry;
        Bukkit.getScheduler().runTask(plugin, this::ensureAllTableCrystals);
    }

    @EventHandler
    public void onTableClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!registry.isTable(block)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        openWorkshop(player, block);
    }

    @EventHandler
    public void onTableCrystalClick(PlayerInteractAtEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof EnderCrystal crystal)) {
            return;
        }
        Byte marker = crystal.getPersistentDataContainer().get(tableCrystalKey(), PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) {
            return;
        }
        Block anchor = crystal.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (!registry.isTable(anchor)) {
            return;
        }
        event.setCancelled(true);
        openWorkshop(event.getPlayer(), anchor);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTableCrystalDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        Byte marker = crystal.getPersistentDataContainer().get(tableCrystalKey(), PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTableCrystalPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        Byte marker = crystal.getPersistentDataContainer().get(tableCrystalKey(), PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) {
            return;
        }
        event.setCancelled(true);
    }

    public void ensureCrystalAt(Block tableBlock) {
        if (tableBlock == null || tableBlock.getWorld() == null) {
            return;
        }
        Location base = tableBlock.getLocation().add(0.5, 1.0, 0.5);
        for (Entity nearby : tableBlock.getWorld().getNearbyEntities(base, 0.4, 0.8, 0.4)) {
            if (nearby instanceof EnderCrystal crystal) {
                Byte marker = crystal.getPersistentDataContainer().get(tableCrystalKey(), PersistentDataType.BYTE);
                if (marker != null && marker == (byte) 1) {
                    return;
                }
            }
        }
        EnderCrystal crystal = tableBlock.getWorld().spawn(base, EnderCrystal.class);
        crystal.setShowingBottom(false);
        crystal.setInvulnerable(true);
        crystal.setBeamTarget(null);
        crystal.getPersistentDataContainer().set(tableCrystalKey(), PersistentDataType.BYTE, (byte) 1);
    }

    public void removeCrystalAt(Block tableBlock) {
        if (tableBlock == null || tableBlock.getWorld() == null) {
            return;
        }
        Location base = tableBlock.getLocation().add(0.5, 1.0, 0.5);
        for (Entity nearby : tableBlock.getWorld().getNearbyEntities(base, 0.7, 1.2, 0.7)) {
            if (!(nearby instanceof EnderCrystal crystal)) {
                continue;
            }
            Byte marker = crystal.getPersistentDataContainer().get(tableCrystalKey(), PersistentDataType.BYTE);
            if (marker != null && marker == (byte) 1) {
                crystal.remove();
            }
        }
    }

    private void ensureAllTableCrystals() {
        for (String key : registry.listKeys()) {
            Location loc = ModificationTableRegistry.decode(plugin, key);
            if (loc == null || loc.getWorld() == null) {
                continue;
            }
            ensureCrystalAt(loc.getBlock());
        }
    }

    private org.bukkit.NamespacedKey tableCrystalKey() {
        return new org.bukkit.NamespacedKey(plugin, "dpmr_modtable_crystal");
    }

    private void openWorkshop(Player player, Block table) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (weaponManager.readWeaponId(main) == null) {
            player.sendMessage(Component.text("Hold a DPMR weapon to use the bench.", NamedTextColor.RED));
            ModificationTableFx.playAccessDenied(player, table);
            return;
        }
        ItemStack put = main.clone();
        put.setAmount(1);
        if (main.getAmount() <= 1) {
            main.setAmount(0);
        } else {
            main.setAmount(main.getAmount() - 1);
        }

        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);
        fillBorder(inv);
        fillTierHeaders(inv);
        WeaponProfile wp = WeaponProfile.fromId(weaponManager.readWeaponId(put));
        inv.setItem(SLOT_INFO, wp != null && wp.isBombWeapon() ? bombInfoBook()
                : (wp != null && wp.isMortarWeapon() ? mortarInfoBook()
                : (wp != null && wp.isRocketWeapon() ? rocketInfoBook()
                : (wp != null && wp.isRevolverWorkshopWeapon() ? revolverInfoBook()
                : (wp == WeaponProfile.JERRYCAN ? jerryInfoBook() : infoBook())))));
        inv.setItem(SLOT_WEAPON, put);
        inv.setItem(SLOT_CLOSE, closeButton());
        refreshPathButtons(inv, put);
        BukkitTask previous = workshopAmbientTasks.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }
        player.openInventory(inv);
        ModificationTableFx.playOpenBurst(player, table);
        workshopAmbientTasks.put(player.getUniqueId(), ModificationTableFx.startAmbient(plugin, player, table));
    }

    private static ItemStack infoBook() {
        ItemStack book = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(Component.text("Three paths · one active", NamedTextColor.AQUA, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Each row is a specialization.", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Assault — ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                        .append(Component.text("damage, fire rate, explosions", NamedTextColor.RED)),
                Component.text("Survival — ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                        .append(Component.text("ammo, reload, lifesteal", NamedTextColor.GREEN)),
                Component.text("Tech — ", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
                        .append(Component.text("accuracy, range, chains", NamedTextColor.AQUA)),
                Component.empty(),
                Component.text("Payment: gold ingots (see each tier).", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        book.setItemMeta(meta);
        return book;
    }

    private static ItemStack bombInfoBook() {
        ItemStack book = new ItemStack(Material.TNT_MINECART);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(Component.text("Bomb · three exclusive paths", NamedTextColor.RED, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Salvo — ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("more bombs per shot", NamedTextColor.YELLOW)),
                Component.text("Ricochet — ", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("bounces off walls", NamedTextColor.GRAY)),
                Component.text("Cataclysm — ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                        .append(Component.text("area and damage", NamedTextColor.LIGHT_PURPLE)),
                Component.empty(),
                Component.text("Payment: gold ingots.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        book.setItemMeta(meta);
        return book;
    }

    private static ItemStack mortarInfoBook() {
        ItemStack book = new ItemStack(Material.TARGET);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(Component.text("Mortar · three exclusive paths", NamedTextColor.GOLD, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Incendiary — ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                        .append(Component.text("area damage, burn, wider zone", NamedTextColor.RED)),
                Component.text("Barrage — ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .append(Component.text("multiple shells per arcing shot", NamedTextColor.GOLD)),
                Component.text("Acid — ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                        .append(Component.text("poison, slow, lingering cloud", NamedTextColor.GREEN)),
                Component.empty(),
                Component.text("Payment: gold ingots.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        book.setItemMeta(meta);
        return book;
    }

    private static ItemStack rocketInfoBook() {
        ItemStack book = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(Component.text("Rocket launcher · three exclusive paths", NamedTextColor.RED, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Guided missile — ", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("locks the nearest target", NamedTextColor.GRAY)),
                Component.text("Demolition — ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("radius and blast damage", NamedTextColor.YELLOW)),
                Component.text("Drone — ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                        .append(Component.text("deploys a drone that strafes nearby", NamedTextColor.LIGHT_PURPLE)),
                Component.empty(),
                Component.text("Payment: gold ingots.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        book.setItemMeta(meta);
        return book;
    }

    private static ItemStack revolverInfoBook() {
        ItemStack book = new ItemStack(Material.STICK);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(Component.text("Revolver — 3 tiers (in order)", NamedTextColor.GRAY, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("I — ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .append(Component.text("2 hits per shot, x2 cylinder, fire rate", NamedTextColor.GOLD)),
                Component.text("II — ", NamedTextColor.RED, TextDecoration.BOLD)
                        .append(Component.text("area explosion on each hit", NamedTextColor.RED)),
                Component.text("III — ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                        .append(Component.text("hit a player: 4 piercing projectiles", NamedTextColor.GRAY)),
                Component.empty(),
                Component.text("Payment: gold ingots. Unlock I, then II, then III.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        book.setItemMeta(meta);
        return book;
    }

    private static ItemStack jerryInfoBook() {
        ItemStack book = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(Component.text("J-20 · three exclusive paths", NamedTextColor.GOLD, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("A — ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                        .append(Component.text("Expansion thermique", NamedTextColor.RED)),
                Component.text("B — ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                        .append(Component.text("Persistance visqueuse", NamedTextColor.GREEN)),
                Component.text("C — ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .append(Component.text("Breach tactics", NamedTextColor.GOLD)),
                Component.empty(),
                Component.text("Payment: gold ingots.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        book.setItemMeta(meta);
        return book;
    }

    private static ItemStack closeButton() {
        ItemStack b = new ItemStack(Material.IRON_DOOR);
        ItemMeta m = b.getItemMeta();
        m.displayName(Component.text("Leave bench", NamedTextColor.WHITE, TextDecoration.BOLD));
        m.lore(List.of(
                Component.text("Your weapon is in your inventory.", NamedTextColor.GRAY),
                Component.text("Close", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        b.setItemMeta(m);
        return b;
    }

    private static void fillTierHeaders(Inventory inv) {
        String[] romans = {"I", "II", "III", "IV", "V"};
        for (int t = 0; t < 5; t++) {
            ItemStack p = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            ItemMeta m = p.getItemMeta();
            m.displayName(Component.text("Tier " + romans[t], NamedTextColor.GRAY, TextDecoration.ITALIC));
            m.lore(List.of(Component.text("Left to right", NamedTextColor.DARK_GRAY)));
            p.setItemMeta(m);
            inv.setItem(SLOT_TIER_HEADER[t], p);
        }
    }

    private void fillBorder(Inventory inv) {
        ItemStack glass = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            if (isReservedSlot(i)) {
                continue;
            }
            inv.setItem(i, glass.clone());
        }
    }

    private static boolean isReservedSlot(int i) {
        if (i == SLOT_WEAPON || i == SLOT_INFO || i == SLOT_CLOSE) {
            return true;
        }
        for (int h : SLOT_TIER_HEADER) {
            if (i == h) {
                return true;
            }
        }
        if (i == SLOT_PATH_A || i == SLOT_PATH_B || i == SLOT_PATH_C) {
            return true;
        }
        for (WeaponUpgradePath p : WeaponUpgradePath.values()) {
            for (int t = 1; t <= 5; t++) {
                if (slotFor(p, t) == i) {
                    return true;
                }
            }
        }
        for (BombUpgradePath p : BombUpgradePath.values()) {
            for (int t = 1; t <= 5; t++) {
                if (slotForBomb(p, t) == i) {
                    return true;
                }
            }
        }
        for (JerrycanUpgradePath p : JerrycanUpgradePath.values()) {
            for (int t = 1; t <= 5; t++) {
                if (slotForJerry(p, t) == i) {
                    return true;
                }
            }
        }
        for (MortarUpgradePath p : MortarUpgradePath.values()) {
            for (int t = 1; t <= 5; t++) {
                if (slotForMortar(p, t) == i) {
                    return true;
                }
            }
        }
        for (RocketUpgradePath p : RocketUpgradePath.values()) {
            for (int t = 1; t <= 5; t++) {
                if (slotForRocket(p, t) == i) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int slotFor(WeaponUpgradePath path, int tier) {
        return 19 + path.ordinal() * 9 + (tier - 1);
    }

    private static int slotForBomb(BombUpgradePath path, int tier) {
        return 19 + path.ordinal() * 9 + (tier - 1);
    }

    private static int slotForJerry(JerrycanUpgradePath path, int tier) {
        return 19 + path.ordinal() * 9 + (tier - 1);
    }

    private static int slotForMortar(MortarUpgradePath path, int tier) {
        return 19 + path.ordinal() * 9 + (tier - 1);
    }

    private static int slotForRocket(RocketUpgradePath path, int tier) {
        return 19 + path.ordinal() * 9 + (tier - 1);
    }

    /** Paliers I–III sur la premiere ligne d'upgrade (colonnes I, II, III). */
    private static int slotForRevolverTier(int tier) {
        return 18 + tier;
    }

    private void refreshPathButtons(Inventory inv, ItemStack weapon) {
        WeaponProfile wp = WeaponProfile.fromId(weaponManager.readWeaponId(weapon));
        if (wp != null && wp.isBombWeapon()) {
            BombUpgradeState current = BombUpgradeState.read(weapon, plugin);
            for (BombUpgradePath path : BombUpgradePath.values()) {
                for (int tier = 1; tier <= 5; tier++) {
                    int slot = slotForBomb(path, tier);
                    inv.setItem(slot, iconBomb(weapon, current, path, tier));
                }
            }
            applyBombPathRowLabels(inv);
            return;
        }
        if (wp != null && wp.isMortarWeapon()) {
            MortarUpgradeState current = MortarUpgradeState.read(weapon, plugin);
            for (MortarUpgradePath path : MortarUpgradePath.values()) {
                for (int tier = 1; tier <= 5; tier++) {
                    int slot = slotForMortar(path, tier);
                    inv.setItem(slot, iconMortar(weapon, current, path, tier));
                }
            }
            applyMortarPathRowLabels(inv);
            return;
        }
        if (wp != null && wp.isRocketWeapon()) {
            RocketUpgradeState current = RocketUpgradeState.read(weapon, plugin);
            for (RocketUpgradePath path : RocketUpgradePath.values()) {
                for (int tier = 1; tier <= 5; tier++) {
                    int slot = slotForRocket(path, tier);
                    inv.setItem(slot, iconRocket(weapon, current, path, tier));
                }
            }
            applyRocketPathRowLabels(inv);
            return;
        }
        if (wp != null && wp.isRevolverWorkshopWeapon()) {
            RevolverUpgradeState current = RevolverUpgradeState.read(weapon, plugin);
            for (int tier = 1; tier <= 3; tier++) {
                inv.setItem(slotForRevolverTier(tier), iconRevolver(weapon, current, tier));
            }
            applyRevolverPathRowLabels(inv);
            return;
        }
        if (wp == WeaponProfile.JERRYCAN) {
            JerrycanUpgradeState current = JerrycanUpgradeState.read(weapon, plugin);
            for (JerrycanUpgradePath path : JerrycanUpgradePath.values()) {
                for (int tier = 1; tier <= 5; tier++) {
                    int slot = slotForJerry(path, tier);
                    inv.setItem(slot, iconJerry(weapon, current, path, tier));
                }
            }
            applyJerryPathRowLabels(inv);
            return;
        }
        WeaponUpgradeState current = WeaponUpgradeState.read(weapon, plugin);
        for (WeaponUpgradePath path : WeaponUpgradePath.values()) {
            for (int tier = 1; tier <= 5; tier++) {
                int slot = slotFor(path, tier);
                inv.setItem(slot, iconForSlot(weapon, current, path, tier));
            }
        }
        applyWeaponPathRowLabels(inv);
    }

    private static void applyWeaponPathRowLabels(Inventory inv) {
        inv.setItem(SLOT_PATH_A, pathRowLabelWeapon(Material.BLAZE_POWDER, NamedTextColor.RED, WeaponUpgradePath.ASSAULT));
        inv.setItem(SLOT_PATH_B, pathRowLabelWeapon(Material.GOLDEN_APPLE, NamedTextColor.GREEN, WeaponUpgradePath.SURVIVAL));
        inv.setItem(SLOT_PATH_C, pathRowLabelWeapon(Material.AMETHYST_SHARD, NamedTextColor.LIGHT_PURPLE, WeaponUpgradePath.TECH));
    }

    private static void applyBombPathRowLabels(Inventory inv) {
        inv.setItem(SLOT_PATH_A, pathRowLabelBomb(Material.FIREWORK_ROCKET, NamedTextColor.GOLD, BombUpgradePath.SALVO));
        inv.setItem(SLOT_PATH_B, pathRowLabelBomb(Material.SPECTRAL_ARROW, NamedTextColor.AQUA, BombUpgradePath.RICOCHET));
        inv.setItem(SLOT_PATH_C, pathRowLabelBomb(Material.TNT, NamedTextColor.DARK_PURPLE, BombUpgradePath.OVERLOAD));
    }

    private static void applyJerryPathRowLabels(Inventory inv) {
        inv.setItem(SLOT_PATH_A, pathRowLabelJerry(Material.BLAZE_POWDER, NamedTextColor.RED, JerrycanUpgradePath.THERMAL));
        inv.setItem(SLOT_PATH_B, pathRowLabelJerry(Material.SLIME_BALL, NamedTextColor.DARK_GREEN, JerrycanUpgradePath.VISCOUS));
        inv.setItem(SLOT_PATH_C, pathRowLabelJerry(Material.NETHERITE_SCRAP, NamedTextColor.YELLOW, JerrycanUpgradePath.BREACH));
    }

    private static void applyMortarPathRowLabels(Inventory inv) {
        inv.setItem(SLOT_PATH_A, pathRowLabelMortar(Material.BLAZE_POWDER, NamedTextColor.DARK_RED, MortarUpgradePath.INCENDIARY));
        inv.setItem(SLOT_PATH_B, pathRowLabelMortar(Material.FIREWORK_ROCKET, NamedTextColor.GOLD, MortarUpgradePath.BARRAGE));
        inv.setItem(SLOT_PATH_C, pathRowLabelMortar(Material.SLIME_BALL, NamedTextColor.DARK_GREEN, MortarUpgradePath.ACID));
    }

    private static void applyRocketPathRowLabels(Inventory inv) {
        inv.setItem(SLOT_PATH_A, pathRowLabelRocket(Material.SPECTRAL_ARROW, NamedTextColor.AQUA, RocketUpgradePath.GUIDED));
        inv.setItem(SLOT_PATH_B, pathRowLabelRocket(Material.TNT, NamedTextColor.GOLD, RocketUpgradePath.DEVASTATOR));
        inv.setItem(SLOT_PATH_C, pathRowLabelRocket(Material.IRON_HORSE_ARMOR, NamedTextColor.LIGHT_PURPLE, RocketUpgradePath.DRONE));
    }

    private static void applyRevolverPathRowLabels(Inventory inv) {
        inv.setItem(SLOT_PATH_A, pathRowLabelRevolverHeader());
        inv.setItem(SLOT_PATH_B, pane(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(SLOT_PATH_C, pane(Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    private static ItemStack pathRowLabelRevolverHeader() {
        ItemStack s = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text("Revolver upgrades", NamedTextColor.GOLD, TextDecoration.BOLD));
        m.lore(List.of(
                Component.text("Buy all three chips I, II, III (top row).", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Replaces Assault / Survival / Tech paths.", NamedTextColor.DARK_RED, TextDecoration.ITALIC)
        ));
        s.setItemMeta(m);
        return s;
    }

    private static ItemStack pathRowLabelWeapon(Material mat, NamedTextColor accent, WeaponUpgradePath path) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text(path.styleName(), accent, TextDecoration.BOLD));
        m.lore(List.of(
                Component.text(path.blurb(), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("→ Tiers I–V on this row", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        s.setItemMeta(m);
        return s;
    }

    private static ItemStack pathRowLabelBomb(Material mat, NamedTextColor accent, BombUpgradePath path) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text(path.styleName(), accent, TextDecoration.BOLD));
        m.lore(List.of(
                Component.text(path.blurb(), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("→ Tiers I–V on this row", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        s.setItemMeta(m);
        return s;
    }

    private static ItemStack pathRowLabelJerry(Material mat, NamedTextColor accent, JerrycanUpgradePath path) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text(path.styleName(), accent, TextDecoration.BOLD));
        m.lore(List.of(
                Component.text(path.blurb(), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("→ Tiers I–V on this row", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        s.setItemMeta(m);
        return s;
    }

    private static ItemStack pathRowLabelMortar(Material mat, NamedTextColor accent, MortarUpgradePath path) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text(path.styleName(), accent, TextDecoration.BOLD));
        m.lore(List.of(
                Component.text(path.blurb(), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("→ Tiers I–V on this row", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        s.setItemMeta(m);
        return s;
    }

    private static ItemStack pathRowLabelRocket(Material mat, NamedTextColor accent, RocketUpgradePath path) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text(path.styleName(), accent, TextDecoration.BOLD));
        m.lore(List.of(
                Component.text(path.blurb(), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("→ Tiers I–V on this row", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        s.setItemMeta(m);
        return s;
    }

    private ItemStack iconMortar(ItemStack weapon, MortarUpgradeState current, MortarUpgradePath path, int tier) {
        boolean lockedPath = MortarUpgradeEffects.pathLocked(current, path);
        boolean owned = MortarUpgradeEffects.tierOwned(current, path, tier);
        boolean canBuy = MortarUpgradeEffects.canBuyTier(current, path, tier);
        int cost = MortarUpgradeEffects.goldCostForTier(tier);

        if (lockedPath) {
            return pane(Material.RED_STAINED_GLASS_PANE, "Path locked",
                    List.of(
                            Component.text("Another mortar path is already taken.", NamedTextColor.DARK_RED),
                            Component.text("Switch weapon to reset.", NamedTextColor.GRAY, TextDecoration.ITALIC)
                    ));
        }
        if (owned) {
            ItemStack ok = new ItemStack(Material.EMERALD);
            ItemMeta m = ok.getItemMeta();
            m.displayName(Component.text(MortarUpgradeEffects.tierTitle(path, tier), NamedTextColor.GREEN, TextDecoration.BOLD));
            m.lore(mortarLore(path, tier, loreOwnedBadge()));
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            ok.setItemMeta(m);
            return ok;
        }
        if (canBuy) {
            ItemStack gold = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = gold.getItemMeta();
            meta.displayName(Component.text(MortarUpgradeEffects.tierTitle(path, tier), NamedTextColor.GOLD, TextDecoration.BOLD));
            List<Component> lore = mortarLore(path, tier, null);
            lore.add(Component.empty());
            lore.add(Component.text("Cost: ", NamedTextColor.GRAY)
                    .append(Component.text(cost + " gold ingots", NamedTextColor.GOLD, TextDecoration.BOLD)));
            lore.add(Component.text("Click to purchase", NamedTextColor.GREEN, TextDecoration.BOLD));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            gold.setItemMeta(meta);
            return gold;
        }
        return pane(Material.GRAY_STAINED_GLASS_PANE, "Tier locked",
                List.of(Component.text("Unlock the previous tier first.", NamedTextColor.DARK_GRAY)));
    }

    private ItemStack iconRocket(ItemStack weapon, RocketUpgradeState current, RocketUpgradePath path, int tier) {
        boolean lockedPath = RocketUpgradeEffects.pathLocked(current, path);
        boolean owned = RocketUpgradeEffects.tierOwned(current, path, tier);
        boolean canBuy = RocketUpgradeEffects.canBuyTier(current, path, tier);
        int cost = RocketUpgradeEffects.goldCostForTier(tier);

        if (lockedPath) {
            return pane(Material.RED_STAINED_GLASS_PANE, "Path locked",
                    List.of(
                            Component.text("Another rocket path is already taken.", NamedTextColor.DARK_RED),
                            Component.text("Switch weapon to reset.", NamedTextColor.GRAY, TextDecoration.ITALIC)
                    ));
        }
        if (owned) {
            ItemStack ok = new ItemStack(Material.EMERALD);
            ItemMeta m = ok.getItemMeta();
            m.displayName(Component.text(RocketUpgradeEffects.tierTitle(path, tier), NamedTextColor.GREEN, TextDecoration.BOLD));
            m.lore(rocketLore(path, tier, loreOwnedBadge()));
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            ok.setItemMeta(m);
            return ok;
        }
        if (canBuy) {
            ItemStack gold = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = gold.getItemMeta();
            meta.displayName(Component.text(RocketUpgradeEffects.tierTitle(path, tier), NamedTextColor.GOLD, TextDecoration.BOLD));
            List<Component> lore = rocketLore(path, tier, null);
            lore.add(Component.empty());
            lore.add(Component.text("Cost: ", NamedTextColor.GRAY)
                    .append(Component.text(cost + " gold ingots", NamedTextColor.GOLD, TextDecoration.BOLD)));
            lore.add(Component.text("Click to purchase", NamedTextColor.GREEN, TextDecoration.BOLD));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            gold.setItemMeta(meta);
            return gold;
        }
        return pane(Material.GRAY_STAINED_GLASS_PANE, "Tier locked",
                List.of(Component.text("Unlock the previous tier first.", NamedTextColor.DARK_GRAY)));
    }

    private static List<Component> rocketLore(RocketUpgradePath path, int tier, Component extra) {
        List<String> lines = RocketUpgradeEffects.tierLines(path, tier);
        List<Component> out = new java.util.ArrayList<>();
        for (String s : lines) {
            out.add(Component.text(s, NamedTextColor.GRAY));
        }
        if (extra != null) {
            out.add(extra);
        }
        return out;
    }

    private ItemStack iconRevolver(ItemStack weapon, RevolverUpgradeState current, int tier) {
        boolean owned = RevolverUpgradeEffects.tierOwned(current, tier);
        boolean canBuy = RevolverUpgradeEffects.canBuyTier(current, tier);
        int cost = RevolverUpgradeEffects.goldCostForTier(tier);

        if (owned) {
            ItemStack ok = new ItemStack(Material.EMERALD);
            ItemMeta m = ok.getItemMeta();
            m.displayName(Component.text(RevolverUpgradeEffects.tierTitle(tier, GameLocale.EN), NamedTextColor.GREEN, TextDecoration.BOLD));
            m.lore(revolverTierLore(tier, loreOwnedBadge()));
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            ok.setItemMeta(m);
            return ok;
        }
        if (canBuy) {
            ItemStack gold = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = gold.getItemMeta();
            meta.displayName(Component.text(RevolverUpgradeEffects.tierTitle(tier, GameLocale.EN), NamedTextColor.GOLD, TextDecoration.BOLD));
            List<Component> lore = revolverTierLore(tier, null);
            lore.add(Component.empty());
            lore.add(Component.text("Cost: ", NamedTextColor.GRAY)
                    .append(Component.text(cost + " gold ingots", NamedTextColor.GOLD, TextDecoration.BOLD)));
            lore.add(Component.text("Click to purchase", NamedTextColor.GREEN, TextDecoration.BOLD));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            gold.setItemMeta(meta);
            return gold;
        }
        return pane(Material.GRAY_STAINED_GLASS_PANE, "Tier locked",
                List.of(Component.text("Unlock the previous tier first.", NamedTextColor.DARK_GRAY)));
    }

    private static List<Component> revolverTierLore(int tier, Component extra) {
        List<String> lines = RevolverUpgradeEffects.tierLines(tier);
        List<Component> out = new java.util.ArrayList<>();
        for (String s : lines) {
            out.add(Component.text(s, NamedTextColor.GRAY));
        }
        if (extra != null) {
            out.add(extra);
        }
        return out;
    }

    private static List<Component> mortarLore(MortarUpgradePath path, int tier, Component extra) {
        List<String> lines = MortarUpgradeEffects.tierLines(path, tier);
        List<Component> out = new java.util.ArrayList<>();
        for (String s : lines) {
            out.add(Component.text(s, NamedTextColor.GRAY));
        }
        if (extra != null) {
            out.add(extra);
        }
        return out;
    }

    private ItemStack iconBomb(ItemStack weapon, BombUpgradeState current, BombUpgradePath path, int tier) {
        boolean lockedPath = BombUpgradeEffects.pathLocked(current, path);
        boolean owned = BombUpgradeEffects.tierOwned(current, path, tier);
        boolean canBuy = BombUpgradeEffects.canBuyTier(current, path, tier);
        int cost = BombUpgradeEffects.goldCostForTier(tier);

        if (lockedPath) {
            return pane(Material.RED_STAINED_GLASS_PANE, "Path locked",
                    List.of(
                            Component.text("✕ Another bomb path is already taken.", NamedTextColor.DARK_RED),
                            Component.text("Switch weapon to reset.", NamedTextColor.GRAY, TextDecoration.ITALIC)
                    ));
        }
        if (owned) {
            ItemStack ok = new ItemStack(Material.EMERALD);
            ItemMeta m = ok.getItemMeta();
            m.displayName(Component.text(BombUpgradeEffects.tierTitle(path, tier), NamedTextColor.GREEN, TextDecoration.BOLD));
            m.lore(bombLore(path, tier, loreOwnedBadge()));
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            ok.setItemMeta(m);
            return ok;
        }
        if (canBuy) {
            ItemStack gold = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = gold.getItemMeta();
            meta.displayName(Component.text(BombUpgradeEffects.tierTitle(path, tier), NamedTextColor.GOLD, TextDecoration.BOLD));
            List<Component> lore = bombLore(path, tier, null);
            lore.add(Component.empty());
            lore.add(Component.text("Cost: ", NamedTextColor.GRAY)
                    .append(Component.text(cost + " gold ingots", NamedTextColor.GOLD, TextDecoration.BOLD)));
            lore.add(Component.text("Click to purchase", NamedTextColor.GREEN, TextDecoration.BOLD));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            gold.setItemMeta(meta);
            return gold;
        }
        return pane(Material.GRAY_STAINED_GLASS_PANE, "Tier locked",
                List.of(Component.text("Unlock the previous tier first.", NamedTextColor.DARK_GRAY)));
    }

    private static List<Component> bombLore(BombUpgradePath path, int tier, Component extra) {
        List<String> lines = BombUpgradeEffects.tierLines(path, tier);
        List<Component> out = new java.util.ArrayList<>();
        for (String s : lines) {
            out.add(Component.text(s, NamedTextColor.GRAY));
        }
        if (extra != null) {
            out.add(extra);
        }
        return out;
    }

    private ItemStack iconJerry(ItemStack weapon, JerrycanUpgradeState current, JerrycanUpgradePath path, int tier) {
        boolean lockedPath = JerrycanUpgradeEffects.pathLocked(current, path);
        boolean owned = JerrycanUpgradeEffects.tierOwned(current, path, tier);
        boolean canBuy = JerrycanUpgradeEffects.canBuyTier(current, path, tier);
        int cost = JerrycanUpgradeEffects.goldCostForTier(tier);

        if (lockedPath) {
            return pane(Material.RED_STAINED_GLASS_PANE, "Path locked",
                    List.of(
                            Component.text("✕ Another J-20 path is already taken.", NamedTextColor.DARK_RED),
                            Component.text("Switch jerrycan to reset.", NamedTextColor.GRAY, TextDecoration.ITALIC)
                    ));
        }
        if (owned) {
            ItemStack ok = new ItemStack(Material.EMERALD);
            ItemMeta m = ok.getItemMeta();
            m.displayName(Component.text(JerrycanUpgradeEffects.tierTitle(path, tier), NamedTextColor.GREEN, TextDecoration.BOLD));
            m.lore(jerryLore(path, tier, loreOwnedBadge()));
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            ok.setItemMeta(m);
            return ok;
        }
        if (canBuy) {
            ItemStack gold = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = gold.getItemMeta();
            meta.displayName(Component.text(JerrycanUpgradeEffects.tierTitle(path, tier), NamedTextColor.GOLD, TextDecoration.BOLD));
            List<Component> lore = jerryLore(path, tier, null);
            lore.add(Component.empty());
            lore.add(Component.text("Cost: ", NamedTextColor.GRAY)
                    .append(Component.text(cost + " gold ingots", NamedTextColor.GOLD, TextDecoration.BOLD)));
            lore.add(Component.text("Click to purchase", NamedTextColor.GREEN, TextDecoration.BOLD));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            gold.setItemMeta(meta);
            return gold;
        }
        return pane(Material.GRAY_STAINED_GLASS_PANE, "Tier locked",
                List.of(Component.text("Unlock the previous tier first.", NamedTextColor.DARK_GRAY)));
    }

    private static List<Component> jerryLore(JerrycanUpgradePath path, int tier, Component extra) {
        List<String> lines = JerrycanUpgradeEffects.tierLines(path, tier);
        List<Component> out = new java.util.ArrayList<>();
        for (String s : lines) {
            out.add(Component.text(s, NamedTextColor.GRAY));
        }
        if (extra != null) {
            out.add(extra);
        }
        return out;
    }

    private ItemStack iconForSlot(ItemStack weapon, WeaponUpgradeState current, WeaponUpgradePath path, int tier) {
        boolean lockedPath = WeaponUpgradeEffects.pathLockedForDisplay(current, path);
        boolean owned = WeaponUpgradeEffects.tierAlreadyOwned(current, path, tier);
        boolean canBuy = WeaponUpgradeEffects.canBuyTier(current, path, tier);
        int cost = WeaponUpgradeEffects.goldCostForTier(tier);

        if (lockedPath) {
            return pane(Material.RED_STAINED_GLASS_PANE, "Path locked",
                    List.of(
                            Component.text("✕ Another path is already in use.", NamedTextColor.DARK_RED),
                            Component.text("Switch weapon to reset.", NamedTextColor.GRAY, TextDecoration.ITALIC)
                    ));
        }
        if (owned) {
            ItemStack ok = new ItemStack(Material.EMERALD);
            ItemMeta m = ok.getItemMeta();
            m.displayName(Component.text(WeaponUpgradeEffects.tierTitle(path, tier), NamedTextColor.GREEN, TextDecoration.BOLD));
            m.lore(loreTier(path, tier, loreOwnedBadge()));
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            ok.setItemMeta(m);
            return ok;
        }
        if (canBuy) {
            ItemStack gold = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = gold.getItemMeta();
            meta.displayName(Component.text(WeaponUpgradeEffects.tierTitle(path, tier), NamedTextColor.GOLD, TextDecoration.BOLD));
            List<Component> lore = loreTier(path, tier, null);
            lore.add(Component.empty());
            lore.add(Component.text("Cost: ", NamedTextColor.GRAY)
                    .append(Component.text(cost + " gold ingots", NamedTextColor.GOLD, TextDecoration.BOLD)));
            lore.add(Component.text("Click to purchase", NamedTextColor.GREEN, TextDecoration.BOLD));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            gold.setItemMeta(meta);
            return gold;
        }
        return pane(Material.GRAY_STAINED_GLASS_PANE, "Tier locked",
                List.of(Component.text("Unlock the previous tier first.", NamedTextColor.DARK_GRAY)));
    }

    private static Component loreOwnedBadge() {
        return Component.text("✓ Upgrade installed", NamedTextColor.GREEN, TextDecoration.BOLD);
    }

    private static List<Component> loreTier(WeaponUpgradePath path, int tier, Component extra) {
        List<String> lines = WeaponUpgradeEffects.tierDescriptionLines(path, tier);
        List<Component> out = new java.util.ArrayList<>();
        for (String s : lines) {
            out.add(Component.text(s, NamedTextColor.GRAY));
        }
        if (extra != null) {
            out.add(extra);
        }
        return out;
    }

    private static ItemStack pane(Material mat, String name, List<Component> lore) {
        ItemStack p = new ItemStack(mat);
        ItemMeta m = p.getItemMeta();
        m.displayName(Component.text(name, NamedTextColor.WHITE));
        if (lore != null && !lore.isEmpty()) {
            m.lore(lore);
        }
        p.setItemMeta(m);
        return p;
    }

    private static ItemStack pane(Material mat, String name) {
        return pane(mat, name, List.of());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!GUI_TITLE.equals(event.getView().title())) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < 54 && raw != SLOT_WEAPON) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!GUI_TITLE.equals(event.getView().title())) {
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        boolean topInv = event.getClickedInventory() != null && event.getClickedInventory().equals(top);

        if (topInv) {
            if (raw == SLOT_CLOSE) {
                event.setCancelled(true);
                player.closeInventory();
                return;
            }
            if (raw == SLOT_INFO) {
                event.setCancelled(true);
                return;
            }
            for (int h : SLOT_TIER_HEADER) {
                if (raw == h) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (raw == SLOT_PATH_A || raw == SLOT_PATH_B || raw == SLOT_PATH_C) {
                event.setCancelled(true);
                return;
            }
            if (raw == SLOT_WEAPON) {
                event.setCancelled(false);
                return;
            }
            event.setCancelled(true);
            tryPurchase(player, top, raw);
        }
    }

    private void tryPurchase(Player player, Inventory top, int rawSlot) {
        ItemStack weapon = top.getItem(SLOT_WEAPON);
        if (weapon == null || weaponManager.readWeaponId(weapon) == null) {
            player.sendMessage(Component.text("Place the weapon in the center slot.", NamedTextColor.RED));
            return;
        }
        WeaponProfile wp = WeaponProfile.fromId(weaponManager.readWeaponId(weapon));
        if (wp != null && wp.isBombWeapon()) {
            tryBombPurchase(player, top, rawSlot, weapon);
            return;
        }
        if (wp != null && wp.isMortarWeapon()) {
            tryMortarPurchase(player, top, rawSlot, weapon);
            return;
        }
        if (wp != null && wp.isRocketWeapon()) {
            tryRocketPurchase(player, top, rawSlot, weapon);
            return;
        }
        if (wp != null && wp.isRevolverWorkshopWeapon()) {
            tryRevolverPurchase(player, top, rawSlot, weapon);
            return;
        }
        if (wp == WeaponProfile.JERRYCAN) {
            tryJerryPurchase(player, top, rawSlot, weapon);
            return;
        }
        WeaponUpgradePath path = null;
        int tier = -1;
        for (WeaponUpgradePath p : WeaponUpgradePath.values()) {
            for (int t = 1; t <= 5; t++) {
                if (slotFor(p, t) == rawSlot) {
                    path = p;
                    tier = t;
                    break;
                }
            }
            if (path != null) {
                break;
            }
        }
        if (path == null || tier < 1) {
            return;
        }
        WeaponUpgradeState current = WeaponUpgradeState.read(weapon, plugin);
        if (!WeaponUpgradeEffects.canBuyTier(current, path, tier)) {
            ModificationTableFx.playTierLocked(player);
            return;
        }
        int cost = WeaponUpgradeEffects.goldCostForTier(tier);
        if (countGoldIngots(player) < cost) {
            player.sendMessage(Component.text("You need " + cost + " gold ingots.", NamedTextColor.RED));
            ModificationTableFx.playCannotAfford(player);
            return;
        }
        if (!removeGoldIngots(player, cost)) {
            return;
        }

        WeaponUpgradeState next = new WeaponUpgradeState(path, tier);
        WeaponUpgradeState.write(weapon, next, plugin);
        if (wp != null && path == WeaponUpgradePath.SURVIVAL && tier == 1) {
            weaponManager.bumpReserveAmmo(player, wp, WeaponUpgradeEffects.extraReserveSlots(wp, next));
        }
        weaponManager.refreshWeaponMeta(weapon, player);
        refreshPathButtons(top, weapon);
        ModificationTableFx.playWeaponUpgradeSuccess(player, path);
        player.sendMessage(Component.text("Tier " + tier + " (" + path.styleName() + ") installed!", NamedTextColor.GREEN));
    }

    private void tryBombPurchase(Player player, Inventory top, int rawSlot, ItemStack weapon) {
        BombUpgradePath path = null;
        int tier = -1;
        for (BombUpgradePath p : BombUpgradePath.values()) {
            for (int t = 1; t <= 5; t++) {
                if (slotForBomb(p, t) == rawSlot) {
                    path = p;
                    tier = t;
                    break;
                }
            }
            if (path != null) {
                break;
            }
        }
        if (path == null || tier < 1) {
            return;
        }
        BombUpgradeState current = BombUpgradeState.read(weapon, plugin);
        if (!BombUpgradeEffects.canBuyTier(current, path, tier)) {
            ModificationTableFx.playTierLocked(player);
            return;
        }
        int cost = BombUpgradeEffects.goldCostForTier(tier);
        if (countGoldIngots(player) < cost) {
            player.sendMessage(Component.text("You need " + cost + " gold ingots.", NamedTextColor.RED));
            ModificationTableFx.playCannotAfford(player);
            return;
        }
        if (!removeGoldIngots(player, cost)) {
            return;
        }
        BombUpgradeState next = new BombUpgradeState(path, tier);
        BombUpgradeState.write(weapon, next, plugin);
        weaponManager.refreshWeaponMeta(weapon);
        refreshPathButtons(top, weapon);
        ModificationTableFx.playBombUpgradeSuccess(player, path);
        player.sendMessage(Component.text("Bomb tier " + tier + " (" + path.styleName() + ")!", NamedTextColor.GREEN));
    }

    private void tryMortarPurchase(Player player, Inventory top, int rawSlot, ItemStack weapon) {
        MortarUpgradePath path = null;
        int tier = -1;
        for (MortarUpgradePath p : MortarUpgradePath.values()) {
            for (int t = 1; t <= 5; t++) {
                if (slotForMortar(p, t) == rawSlot) {
                    path = p;
                    tier = t;
                    break;
                }
            }
            if (path != null) {
                break;
            }
        }
        if (path == null || tier < 1) {
            return;
        }
        MortarUpgradeState current = MortarUpgradeState.read(weapon, plugin);
        if (!MortarUpgradeEffects.canBuyTier(current, path, tier)) {
            ModificationTableFx.playTierLocked(player);
            return;
        }
        int cost = MortarUpgradeEffects.goldCostForTier(tier);
        if (countGoldIngots(player) < cost) {
            player.sendMessage(Component.text("You need " + cost + " gold ingots.", NamedTextColor.RED));
            ModificationTableFx.playCannotAfford(player);
            return;
        }
        if (!removeGoldIngots(player, cost)) {
            return;
        }
        MortarUpgradeState next = new MortarUpgradeState(path, tier);
        MortarUpgradeState.write(weapon, next, plugin);
        weaponManager.refreshWeaponMeta(weapon, player);
        refreshPathButtons(top, weapon);
        ModificationTableFx.playMortarUpgradeSuccess(player, path);
        player.sendMessage(Component.text("Mortar tier " + tier + " (" + path.styleName() + ")!", NamedTextColor.GREEN));
    }

    private void tryRocketPurchase(Player player, Inventory top, int rawSlot, ItemStack weapon) {
        RocketUpgradePath path = null;
        int tier = -1;
        for (RocketUpgradePath p : RocketUpgradePath.values()) {
            for (int t = 1; t <= 5; t++) {
                if (slotForRocket(p, t) == rawSlot) {
                    path = p;
                    tier = t;
                    break;
                }
            }
            if (path != null) {
                break;
            }
        }
        if (path == null || tier < 1) {
            return;
        }
        RocketUpgradeState current = RocketUpgradeState.read(weapon, plugin);
        if (!RocketUpgradeEffects.canBuyTier(current, path, tier)) {
            ModificationTableFx.playTierLocked(player);
            return;
        }
        int cost = RocketUpgradeEffects.goldCostForTier(tier);
        if (countGoldIngots(player) < cost) {
            player.sendMessage(Component.text("You need " + cost + " gold ingots.", NamedTextColor.RED));
            ModificationTableFx.playCannotAfford(player);
            return;
        }
        if (!removeGoldIngots(player, cost)) {
            return;
        }
        RocketUpgradeState next = new RocketUpgradeState(path, tier);
        RocketUpgradeState.write(weapon, next, plugin);
        weaponManager.refreshWeaponMeta(weapon, player);
        refreshPathButtons(top, weapon);
        ModificationTableFx.playRocketUpgradeSuccess(player, path);
        player.sendMessage(Component.text("Rocket launcher tier " + tier + " (" + path.styleName() + ")!", NamedTextColor.GREEN));
    }

    private void tryRevolverPurchase(Player player, Inventory top, int rawSlot, ItemStack weapon) {
        int tier = -1;
        for (int t = 1; t <= 3; t++) {
            if (slotForRevolverTier(t) == rawSlot) {
                tier = t;
                break;
            }
        }
        if (tier < 1) {
            return;
        }
        RevolverUpgradeState current = RevolverUpgradeState.read(weapon, plugin);
        if (!RevolverUpgradeEffects.canBuyTier(current, tier)) {
            ModificationTableFx.playTierLocked(player);
            return;
        }
        int cost = RevolverUpgradeEffects.goldCostForTier(tier);
        if (countGoldIngots(player) < cost) {
            player.sendMessage(Component.text("You need " + cost + " gold ingots.", NamedTextColor.RED));
            ModificationTableFx.playCannotAfford(player);
            return;
        }
        if (!removeGoldIngots(player, cost)) {
            return;
        }
        RevolverUpgradeState next = new RevolverUpgradeState(tier);
        if (tier == 1) {
            WeaponUpgradeState.write(weapon, WeaponUpgradeState.NONE, plugin);
        }
        RevolverUpgradeState.write(weapon, next, plugin);
        if (tier == 1) {
            weaponManager.bumpRevolverClipAfterUpgrade(player, weapon);
        }
        weaponManager.refreshWeaponMeta(weapon, player);
        refreshPathButtons(top, weapon);
        ModificationTableFx.playRevolverUpgradeSuccess(player, tier);
        player.sendMessage(Component.text("Revolver tier " + tier + " (" + RevolverUpgradeEffects.tierTitle(tier, GameLocale.EN) + ")!", NamedTextColor.GREEN));
    }

    private void tryJerryPurchase(Player player, Inventory top, int rawSlot, ItemStack weapon) {
        JerrycanUpgradePath path = null;
        int tier = -1;
        for (JerrycanUpgradePath p : JerrycanUpgradePath.values()) {
            for (int t = 1; t <= 5; t++) {
                if (slotForJerry(p, t) == rawSlot) {
                    path = p;
                    tier = t;
                    break;
                }
            }
            if (path != null) {
                break;
            }
        }
        if (path == null || tier < 1) {
            return;
        }
        JerrycanUpgradeState current = JerrycanUpgradeState.read(weapon, plugin);
        if (!JerrycanUpgradeEffects.canBuyTier(current, path, tier)) {
            ModificationTableFx.playTierLocked(player);
            return;
        }
        int cost = JerrycanUpgradeEffects.goldCostForTier(tier);
        if (countGoldIngots(player) < cost) {
            player.sendMessage(Component.text("You need " + cost + " gold ingots.", NamedTextColor.RED));
            ModificationTableFx.playCannotAfford(player);
            return;
        }
        if (!removeGoldIngots(player, cost)) {
            return;
        }
        JerrycanUpgradeState next = new JerrycanUpgradeState(path, tier);
        JerrycanUpgradeState.write(weapon, next, plugin);
        if (path == JerrycanUpgradePath.BREACH && tier == 3) {
            weaponManager.bumpJerryClipOnUpgrade(player, weapon);
        }
        weaponManager.refreshWeaponMeta(weapon, player);
        refreshPathButtons(top, weapon);
        ModificationTableFx.playJerrycanUpgradeSuccess(player, path);
        player.sendMessage(Component.text("J-20 tier " + tier + " (" + path.styleName() + ")!", NamedTextColor.GREEN));
    }

    private static int countGoldIngots(Player player) {
        int n = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == Material.GOLD_INGOT) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private static boolean removeGoldIngots(Player player, int need) {
        if (countGoldIngots(player) < need) {
            return false;
        }
        int left = need;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (s != null && s.getType() == Material.GOLD_INGOT) {
                int take = Math.min(left, s.getAmount());
                int na = s.getAmount() - take;
                left -= take;
                if (na <= 0) {
                    contents[i] = null;
                } else {
                    s.setAmount(na);
                }
            }
        }
        player.getInventory().setContents(contents);
        return left == 0;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!GUI_TITLE.equals(event.getView().title())) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        ItemStack w = event.getInventory().getItem(SLOT_WEAPON);
        if (w == null || weaponManager.readWeaponId(w) == null) {
            return;
        }
        event.getInventory().setItem(SLOT_WEAPON, null);
        HashMap<Integer, ItemStack> over = player.getInventory().addItem(w);
        for (ItemStack drop : over.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        BukkitTask ambient = workshopAmbientTasks.remove(player.getUniqueId());
        if (ambient != null) {
            ambient.cancel();
        }
        ModificationTableFx.playClosePickup(player);
    }
}
