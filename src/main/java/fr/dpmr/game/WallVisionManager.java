package fr.dpmr.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lunettes de scan (violet) et lunettes infrarouges (rouge) : clic droit, glow + contour coloré pour le porteur.
 */
public final class WallVisionManager implements Listener {

    private enum GoggleKind {
        SCAN("wall-vision-goggles", "g"),
        INFRARED("infrared-goggles", "i");

        final String configPath;
        final String teamPrefix;

        GoggleKind(String configPath, String teamPrefix) {
            this.configPath = configPath;
            this.teamPrefix = teamPrefix;
        }
    }

    private final JavaPlugin plugin;
    private final NamespacedKey keyScan;
    private final NamespacedKey keyInfrared;
    private final Map<UUID, Long> cooldownScanMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownInfraredMs = new ConcurrentHashMap<>();

    public WallVisionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyScan = new NamespacedKey(plugin, "dpmr_wall_goggles");
        this.keyInfrared = new NamespacedKey(plugin, "dpmr_infrared_goggles");
    }

    public ItemStack createGoggles(int amount) {
        ItemStack item = new ItemStack(Material.SPYGLASS, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Lunettes de scan", NamedTextColor.DARK_PURPLE));
        meta.lore(List.of(
                Component.text("Clic droit : voir où sont les joueurs", NamedTextColor.GRAY),
                Component.text("Glow violet 15 s (à travers les murs)", NamedTextColor.LIGHT_PURPLE)
        ));
        int cmd = plugin.getConfig().getInt("wall-vision-goggles.custom-model-data", 4002);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        meta.getPersistentDataContainer().set(keyScan, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createInfraredGoggles(int amount) {
        ItemStack item = new ItemStack(Material.SPYGLASS, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Lunettes infrarouges", NamedTextColor.RED));
        meta.lore(List.of(
                Component.text("Clic droit : tous les joueurs en surbrillance", NamedTextColor.GRAY),
                Component.text("Glow rouge (vision thermique)", NamedTextColor.DARK_RED)
        ));
        int cmd = plugin.getConfig().getInt("infrared-goggles.custom-model-data", 4003);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        meta.getPersistentDataContainer().set(keyInfrared, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGoggles(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(keyScan, PersistentDataType.BYTE);
    }

    public boolean isInfraredGoggles(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(keyInfrared, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cooldownScanMs.remove(id);
        cooldownInfraredMs.remove(id);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack hand = event.getItem();
        GoggleKind kind = resolveKind(hand);
        if (kind == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Map<UUID, Long> cdMap = kind == GoggleKind.SCAN ? cooldownScanMs : cooldownInfraredMs;
        long until = cdMap.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) {
            int left = (int) Math.ceil((until - now) / 1000.0);
            String label = kind == GoggleKind.SCAN ? "Lunettes de scan" : "Lunettes infrarouges";
            player.sendActionBar(Component.text(label + " : recharge " + left + " s", NamedTextColor.RED));
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        String path = kind.configPath;
        int rawRadius = cfg.getInt(path + ".scan-radius", 0);
        boolean fullWorld = rawRadius <= 0;
        int radius = fullWorld ? 0 : Math.max(8, rawRadius);
        int effectTicks = Math.max(20, cfg.getInt(path + ".effect-ticks", 300));
        int cdSec = Math.max(1, cfg.getInt(path + ".cooldown-seconds", 20));
        cdMap.put(player.getUniqueId(), now + cdSec * 1000L);

        List<Player> found = new ArrayList<>();
        double r2 = radius * (double) radius;
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player) || !other.isOnline()) {
                continue;
            }
            if (!fullWorld && other.getLocation().distanceSquared(player.getLocation()) > r2) {
                continue;
            }
            found.add(other);
        }

        final Scoreboard board = player.getScoreboard();
        final String teamId = teamName(player.getUniqueId(), kind);
        NamedTextColor teamColor = kind == GoggleKind.SCAN ? NamedTextColor.DARK_PURPLE : NamedTextColor.RED;
        Team team = board.getTeam(teamId);
        if (team != null) {
            for (String e : new HashSet<>(team.getEntries())) {
                team.removeEntry(e);
            }
        } else {
            team = board.registerNewTeam(teamId);
            team.color(teamColor);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }

        for (Player target : found) {
            team.addEntry(target.getName());
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, effectTicks, 0, false, false, true));
        }

        final int clearAfter = effectTicks;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Team t = board.getTeam(teamId);
            if (t == null) {
                return;
            }
            for (Player target : found) {
                t.removeEntry(target.getName());
            }
            if (t.getEntries().isEmpty()) {
                t.unregister();
            }
        }, clearAfter);

        String where = fullWorld ? "monde entier" : radius + " m";
        TextColor barColor = kind == GoggleKind.SCAN ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.RED;
        String modeLabel = kind == GoggleKind.SCAN ? "Scan" : "Infrarouge";
        player.sendActionBar(Component.text(
                modeLabel + " : " + found.size() + " joueur(s) — " + where + " — " + (effectTicks / 20) + " s",
                barColor));
        if (kind == GoggleKind.SCAN) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.4f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.85f, 1.5f);
        }
    }

    private GoggleKind resolveKind(ItemStack hand) {
        if (isGoggles(hand)) {
            return GoggleKind.SCAN;
        }
        if (isInfraredGoggles(hand)) {
            return GoggleKind.INFRARED;
        }
        return null;
    }

    private static String teamName(UUID uuid, GoggleKind kind) {
        String hex = uuid.toString().replace("-", "");
        return kind.teamPrefix + hex.substring(0, Math.min(15, hex.length()));
    }
}
