package fr.dpmr.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class RadarManager implements Listener {

    private final JavaPlugin plugin;
    private final WeaponManager weaponManager;
    private final NamespacedKey keyRadar;

    public RadarManager(JavaPlugin plugin, WeaponManager weaponManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
        this.keyRadar = new NamespacedKey(plugin, "dpmr_radar");
    }

    public ItemStack createRadar(int amount) {
        ItemStack item = new ItemStack(Material.COMPASS, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Player radar", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Left-click: reload held weapon", NamedTextColor.GRAY),
                Component.text("Right-click: utility / tracking", NamedTextColor.DARK_GRAY)
        ));
        int cmd = plugin.getConfig().getInt("radar.custom-model-data", 4001);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        meta.getPersistentDataContainer().set(keyRadar, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isRadar(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(keyRadar, PersistentDataType.BYTE);
    }

    public void refreshRadarStack(ItemStack item) {
        if (!isRadar(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        int cmd = plugin.getConfig().getInt("radar.custom-model-data", 4001);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        item.setItemMeta(meta);
    }

    public void refreshRadarsInInventory(Player player) {
        if (player == null) {
            return;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null) {
                refreshRadarStack(stack);
            }
        }
        refreshRadarStack(player.getInventory().getItemInOffHand());
    }

    @EventHandler
    public void onJoinRefreshRadarModels(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refreshRadarsInInventory(p), 28L);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack hand = event.getItem();
        if (!isRadar(hand)) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            boolean ok = weaponManager.triggerManualReload(player);
            if (ok) {
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.7f, 1.25f);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.45f, 1.15f);
            }
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        int radius = Math.max(20, cfg.getInt("radar.scan-radius", 90));
        long players = player.getWorld().getPlayers().stream()
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .filter(p -> p.getLocation().distanceSquared(player.getLocation()) <= radius * radius)
                .count();
        player.sendActionBar(Component.text("Radar: " + players + " player(s) nearby [" + radius + "m]", NamedTextColor.AQUA));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.45f, 1.75f);
    }
}

