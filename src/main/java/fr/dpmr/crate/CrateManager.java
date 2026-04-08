package fr.dpmr.crate;

import fr.dpmr.data.PointsManager;
import fr.dpmr.game.BandageManager;
import fr.dpmr.game.WeaponManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CrateManager implements Listener {

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final WeaponManager weaponManager;
    private final BandageManager bandageManager;
    private final File file;
    private final YamlConfiguration yaml;
    private final org.bukkit.NamespacedKey keyCrateKeyType;
    private final Map<String, CrateDef> crates = new HashMap<>();
    private final Set<UUID> opening = new HashSet<>();

    private record CrateDef(String id, String world, int x, int y, int z) {}

    public CrateManager(JavaPlugin plugin, PointsManager pointsManager, WeaponManager weaponManager, BandageManager bandageManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.weaponManager = weaponManager;
        this.bandageManager = bandageManager;
        this.file = new File(plugin.getDataFolder(), "crates.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        this.keyCrateKeyType = new org.bukkit.NamespacedKey(plugin, "dpmr_crate_key_type");
        load();
    }

    private void load() {
        crates.clear();
        if (!yaml.isConfigurationSection("crates")) {
            return;
        }
        for (String id : Objects.requireNonNull(yaml.getConfigurationSection("crates")).getKeys(false)) {
            String base = "crates." + id + ".";
            String world = yaml.getString(base + "world", "");
            int x = yaml.getInt(base + "x", 0);
            int y = yaml.getInt(base + "y", 0);
            int z = yaml.getInt(base + "z", 0);
            if (!world.isBlank()) {
                crates.put(id.toLowerCase(Locale.ROOT), new CrateDef(id.toLowerCase(Locale.ROOT), world, x, y, z));
            }
        }
    }

    public void save() {
        yaml.set("crates", null);
        for (CrateDef def : crates.values()) {
            String base = "crates." + def.id + ".";
            yaml.set(base + "world", def.world);
            yaml.set(base + "x", def.x);
            yaml.set(base + "y", def.y);
            yaml.set(base + "z", def.z);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder crates.yml: " + e.getMessage());
        }
    }

    public void createCrate(Player admin, String id, Location at) {
        if (at.getWorld() == null) {
            return;
        }
        String k = id.toLowerCase(Locale.ROOT);
        crates.put(k, new CrateDef(k, at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ()));
        save();
        admin.sendMessage(Component.text("Caisse '" + k + "' creee.", NamedTextColor.GREEN));
    }

    public void deleteCrate(Player admin, String id) {
        String k = id.toLowerCase(Locale.ROOT);
        if (crates.remove(k) == null) {
            admin.sendMessage(Component.text("Crate not found.", NamedTextColor.RED));
            return;
        }
        save();
        admin.sendMessage(Component.text("Caisse '" + k + "' supprimee.", NamedTextColor.YELLOW));
    }

    public List<String> listCrates() {
        return crates.keySet().stream().sorted().toList();
    }

    public ItemStack createKey(String crateId, int amount) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, Math.max(1, amount));
        ItemMeta meta = key.getItemMeta();
        meta.displayName(Component.text("Crate key: " + crateId, NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("Use on crate " + crateId, NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(keyCrateKeyType, PersistentDataType.STRING, crateId.toLowerCase(Locale.ROOT));
        key.setItemMeta(meta);
        return key;
    }

    private String readKeyType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(keyCrateKeyType, PersistentDataType.STRING);
    }

    @EventHandler
    public void onUseCrate(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (opening.contains(player.getUniqueId())) {
            return;
        }
        CrateDef def = crateAt(event.getClickedBlock().getLocation());
        if (def == null) {
            return;
        }
        String keyType = readKeyType(event.getItem());
        if (keyType == null || !keyType.equals(def.id)) {
            player.sendActionBar(Component.text("You need a '" + def.id + "' key", NamedTextColor.RED));
            return;
        }
        event.setCancelled(true);
        consumeOneKey(player);
        openAnimation(player, def, event.getClickedBlock().getLocation().add(0.5, 1.15, 0.5));
    }

    private CrateDef crateAt(Location loc) {
        for (CrateDef def : crates.values()) {
            if (loc.getWorld() == null || !def.world.equals(loc.getWorld().getName())) {
                continue;
            }
            if (def.x == loc.getBlockX() && def.y == loc.getBlockY() && def.z == loc.getBlockZ()) {
                return def;
            }
        }
        return null;
    }

    private void consumeOneKey(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);
    }

    private void openAnimation(Player player, CrateDef def, Location center) {
        opening.add(player.getUniqueId());
        List<CrateRewards.LootReward> pool = CrateRewards.poolForCrateId(def.id, pointsManager, bandageManager, weaponManager);
        if (pool.isEmpty()) {
            opening.remove(player.getUniqueId());
            return;
        }
        Vector dir = player.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.001) {
            dir = new Vector(1, 0, 0);
        }
        dir.normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

        List<ArmorStand> reels = new ArrayList<>();
        for (int i = -2; i <= 2; i++) {
            Location at = center.clone().add(right.clone().multiply(i * 0.55));
            ArmorStand as = center.getWorld().spawn(at, ArmorStand.class);
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.getEquipment().setHelmet(randomReward(pool).preview());
            reels.add(as);
        }

        final int[] tick = {0};
        final CrateRewards.LootReward[] result = {randomWeighted(pool)};
        final BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;
            int speed = tick[0] < 25 ? 1 : (tick[0] < 45 ? 2 : 4);
            if (tick[0] % speed == 0) {
                for (int i = 0; i < reels.size(); i++) {
                    CrateRewards.LootReward rr = randomReward(pool);
                    if (i == 2 && tick[0] > 48) {
                        rr = result[0];
                    }
                    reels.get(i).getEquipment().setHelmet(rr.preview());
                }
                center.getWorld().playSound(center, Sound.UI_BUTTON_CLICK, 0.45f, 1.35f);
            }
            center.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, center, 2, 0.45, 0.15, 0.45, 0.01);
            if (tick[0] >= 58) {
                taskRef[0].cancel();
                for (ArmorStand as : reels) {
                    as.remove();
                }
                result[0].give(player);
                player.sendMessage(Component.text("Caisse " + def.id + " -> " + result[0].label(), NamedTextColor.GOLD));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
                opening.remove(player.getUniqueId());
            }
        }, 0L, 2L);
    }

    private CrateRewards.LootReward randomWeighted(List<CrateRewards.LootReward> pool) {
        int total = pool.stream().mapToInt(CrateRewards.LootReward::weight).sum();
        int r = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        int acc = 0;
        for (CrateRewards.LootReward reward : pool) {
            acc += reward.weight();
            if (r < acc) {
                return reward;
            }
        }
        return pool.get(0);
    }

    private CrateRewards.LootReward randomReward(List<CrateRewards.LootReward> pool) {
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

}

