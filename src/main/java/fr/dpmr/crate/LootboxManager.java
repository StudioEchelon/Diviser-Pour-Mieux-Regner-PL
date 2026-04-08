package fr.dpmr.crate;

import fr.dpmr.data.PointsManager;
import fr.dpmr.game.BandageManager;
import fr.dpmr.game.WeaponManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Daily free lootbox (1 reward) and Ultimate lootbox item (5 rewards) with Overwatch-style reel reveal.
 */
public class LootboxManager implements Listener {

    private static final byte ULTIMATE_TAG = 1;

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final WeaponManager weaponManager;
    private final BandageManager bandageManager;
    private final File dataFile;
    private final YamlConfiguration data;
    private final org.bukkit.NamespacedKey keyUltimateLootbox;
    private final Set<UUID> opening = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public LootboxManager(JavaPlugin plugin, PointsManager pointsManager, WeaponManager weaponManager,
                          BandageManager bandageManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.weaponManager = weaponManager;
        this.bandageManager = bandageManager;
        this.dataFile = new File(plugin.getDataFolder(), "lootboxes.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        this.keyUltimateLootbox = new org.bukkit.NamespacedKey(plugin, "dpmr_lootbox_ultimate");
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save lootboxes.yml: " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("lootbox.enabled", true);
    }

    private int dailyCooldownHours() {
        return Math.max(1, plugin.getConfig().getInt("lootbox.daily-cooldown-hours", 24));
    }

    private long cooldownMillis() {
        return dailyCooldownHours() * 60L * 60L * 1000L;
    }

    public void tryOpenDaily(Player player) {
        if (!isEnabled()) {
            player.sendMessage(Component.text("Lootboxes are disabled.", NamedTextColor.RED));
            return;
        }
        if (opening.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("Already opening a lootbox.", NamedTextColor.YELLOW));
            return;
        }
        long now = System.currentTimeMillis();
        long last = data.getLong("daily." + player.getUniqueId(), 0L);
        long next = last + cooldownMillis();
        if (last > 0 && now < next) {
            long remain = next - now;
            long hours = remain / (60 * 60 * 1000);
            long mins = (remain / (60 * 1000)) % 60;
            player.sendMessage(Component.text(
                    "Your next Daily Loot Box is available in " + hours + "h " + mins + "m.",
                    NamedTextColor.GOLD));
            return;
        }
        List<CrateRewards.LootReward> pool = CrateRewards.buildPool(false, pointsManager, bandageManager, weaponManager);
        if (pool.isEmpty()) {
            player.sendMessage(Component.text("Loot pool is empty.", NamedTextColor.RED));
            return;
        }
        CrateRewards.LootReward won = randomWeighted(pool);
        playDailyReveal(player, pool, won);
    }

    private void recordDailyClaim(Player player) {
        data.set("daily." + player.getUniqueId(), System.currentTimeMillis());
        save();
    }

    public ItemStack createUltimateLootboxItem(int amount) {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("lootbox.ultimate-material", "NETHER_STAR"));
        if (mat == null) {
            mat = Material.NETHER_STAR;
        }
        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Ultimate Loot Box", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Right-click to open.", NamedTextColor.GRAY),
                Component.text("Contains 5 random rewards.", NamedTextColor.DARK_PURPLE)
        ));
        meta.getPersistentDataContainer().set(keyUltimateLootbox, PersistentDataType.BYTE, ULTIMATE_TAG);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isUltimateItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer().get(keyUltimateLootbox, PersistentDataType.BYTE);
        return tag != null && tag == ULTIMATE_TAG;
    }

    @EventHandler
    public void onUltimateOpen(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!isEnabled() || !isUltimateItem(event.getItem())) {
            return;
        }
        if (opening.contains(player.getUniqueId())) {
            return;
        }
        List<CrateRewards.LootReward> pool = CrateRewards.buildPool(true, pointsManager, bandageManager, weaponManager);
        if (pool.isEmpty()) {
            player.sendMessage(Component.text("Loot pool is empty.", NamedTextColor.RED));
            return;
        }
        event.setCancelled(true);
        opening.add(player.getUniqueId());
        ItemStack hand = player.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);
        List<CrateRewards.LootReward> five = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            five.add(randomWeighted(pool));
        }
        playUltimateReveal(player, pool, five);
    }

    private Location reelCenter(Player player) {
        Vector horiz = new Vector(player.getLocation().getDirection().getX(), 0,
                player.getLocation().getDirection().getZ());
        if (horiz.lengthSquared() < 0.001) {
            horiz = new Vector(1, 0, 0);
        }
        horiz.normalize();
        Location base = player.getLocation().clone();
        base.setY(base.getY() + player.getEyeHeight() * 0.82);
        Location center = base.add(horiz.multiply(1.85));
        return center;
    }

    private Vector reelRight(Player player) {
        Vector horiz = new Vector(player.getLocation().getDirection().getX(), 0,
                player.getLocation().getDirection().getZ());
        if (horiz.lengthSquared() < 0.001) {
            horiz = new Vector(1, 0, 0);
        }
        horiz.normalize();
        return new Vector(-horiz.getZ(), 0, horiz.getX()).normalize();
    }

    private void playDailyReveal(Player player, List<CrateRewards.LootReward> pool, CrateRewards.LootReward winner) {
        Location center = reelCenter(player);
        Vector right = reelRight(player);
        if (center.getWorld() == null) {
            opening.remove(player.getUniqueId());
            return;
        }
        player.showTitle(Title.title(
                Component.text("Opening", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Daily Loot Box", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(200))
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.35f, 1.4f);

        List<ArmorStand> reels = spawnReel(center, right, pool, 9, 0.4);
        int centerIdx = reels.size() / 2;
        runReelAnimation(player, center, pool, winner, reels, centerIdx, 72, () -> {
            for (ArmorStand as : reels) {
                as.remove();
            }
            winner.give(player);
            recordDailyClaim(player);
            player.sendMessage(Component.text("Daily reward: ", NamedTextColor.GREEN)
                    .append(Component.text(winner.label(), NamedTextColor.WHITE)));
            player.showTitle(Title.title(
                    Component.text("You got!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text(winner.label(), NamedTextColor.AQUA),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(350))
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            opening.remove(player.getUniqueId());
        });
    }

    private void playUltimateReveal(Player player, List<CrateRewards.LootReward> pool,
                                    List<CrateRewards.LootReward> fiveResults) {
        Location center = reelCenter(player);
        Vector right = reelRight(player);
        if (center.getWorld() == null) {
            opening.remove(player.getUniqueId());
            player.getInventory().addItem(createUltimateLootboxItem(1));
            return;
        }
        CrateRewards.LootReward featured = fiveResults.get(0);

        player.showTitle(Title.title(
                Component.text("Opening", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("Ultimate Loot Box", NamedTextColor.DARK_PURPLE),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1400), Duration.ofMillis(200))
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.25f, 1.2f);

        List<ArmorStand> reels = spawnReel(center, right, pool, 9, 0.4);
        int centerIdx = reels.size() / 2;
        runReelAnimation(player, center, pool, featured, reels, centerIdx, 88, () -> {
            for (ArmorStand as : reels) {
                as.remove();
            }
            ultimateBonusRing(player, center, right, fiveResults, 0);
        });
    }

    private void ultimateBonusRing(Player player, Location center, Vector right,
                                   List<CrateRewards.LootReward> five, int step) {
        if (step >= 4) {
            for (CrateRewards.LootReward r : five) {
                r.give(player);
            }
            player.sendMessage(Component.text("Ultimate Loot Box — 5 rewards claimed!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            for (CrateRewards.LootReward r : five) {
                player.sendMessage(Component.text("• ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(r.label(), NamedTextColor.WHITE)));
            }
            player.showTitle(Title.title(
                    Component.text("Legendary haul!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text("5 items added to your inventory", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2800), Duration.ofMillis(400))
            ));
            Location pLoc = player.getLocation();
            if (pLoc.getWorld() != null) {
                pLoc.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, pLoc.clone().add(0, 1, 0), 80, 0.6, 0.5, 0.6, 0.08);
                pLoc.getWorld().playSound(pLoc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.15f);
                pLoc.getWorld().playSound(pLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 0.85f);
            }
            opening.remove(player.getUniqueId());
            return;
        }
        CrateRewards.LootReward bonus = five.get(step + 1);
        Vector horizPerp = right.clone().normalize();
        double angle = (step - 1.5) * 0.95;
        Location at = center.clone().add(horizPerp.clone().multiply(angle * 0.85)).add(0, -0.15 - step * 0.08, 0);
        if (at.getWorld() == null) {
            opening.remove(player.getUniqueId());
            return;
        }
        ArmorStand as = at.getWorld().spawn(at, ArmorStand.class);
        as.setVisible(false);
        as.setGravity(false);
        as.setMarker(true);
        as.getEquipment().setHelmet(bonus.preview());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.25f + step * 0.08f);
        at.getWorld().spawnParticle(org.bukkit.Particle.GLOW, at.clone().add(0, 0.35, 0), 12, 0.15, 0.12, 0.15, 0.01);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            as.remove();
            ultimateBonusRing(player, center, right, five, step + 1);
        }, 14L);
    }

    private List<ArmorStand> spawnReel(Location center, Vector right, List<CrateRewards.LootReward> pool,
                                       int count, double spacing) {
        List<ArmorStand> reels = new ArrayList<>();
        int half = count / 2;
        for (int i = -half; i <= half; i++) {
            Location at = center.clone().add(right.clone().multiply(i * spacing));
            ArmorStand as = Objects.requireNonNull(at.getWorld()).spawn(at, ArmorStand.class);
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.getEquipment().setHelmet(randomReward(pool).preview());
            reels.add(as);
        }
        return reels;
    }

    private void runReelAnimation(Player player, Location center, List<CrateRewards.LootReward> pool,
                                  CrateRewards.LootReward winner, List<ArmorStand> reels, int centerIdx,
                                  int endTick, Runnable onFinish) {
        final int[] tick = {0};
        final BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;
            int t = tick[0];
            int speed = t < 28 ? 1 : (t < 52 ? 2 : (t < endTick - 8 ? 3 : 5));
            if (t % speed == 0) {
                for (int i = 0; i < reels.size(); i++) {
                    CrateRewards.LootReward rr = randomReward(pool);
                    if (i == centerIdx && t > endTick - 10) {
                        rr = winner;
                    }
                    reels.get(i).getEquipment().setHelmet(rr.preview());
                }
                float pitch = 0.9f + (t % 9) * 0.06f;
                center.getWorld().playSound(center, Sound.UI_BUTTON_CLICK, 0.35f + Math.min(0.35f, t / 120f), pitch);
            }
            if (t % 2 == 0) {
                center.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, center, 3, 0.55, 0.22, 0.55, 0.02);
                center.getWorld().spawnParticle(org.bukkit.Particle.WAX_ON,
                        center.clone().add(0, 0.25, 0), 2, 0.35, 0.1, 0.35, 0);
            }
            if (t == endTick / 2) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.4f, 1.5f);
            }
            if (t >= endTick) {
                taskRef[0].cancel();
                for (ArmorStand as : reels) {
                    Location slot = as.getLocation();
                    if (slot.getWorld() != null) {
                        slot.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, slot.clone().add(0, 0.4, 0),
                                14, 0.12, 0.12, 0.12, 0.02);
                    }
                }
                center.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, center.clone().add(0, 0.35, 0),
                        45, 0.5, 0.35, 0.5, 0.06);
                center.getWorld().playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.55f, 1.15f);
                Bukkit.getScheduler().runTaskLater(plugin, onFinish, 4L);
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
