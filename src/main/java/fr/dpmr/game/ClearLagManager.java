package fr.dpmr.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Periodically removes common lag sources (drops, stray projectiles, end crystals, unmarked armor stands, etc.)
 * while keeping DPMR-tagged entities (holograms, fake NPCs, familiar pets, powerup markers).
 */
public final class ClearLagManager {

    private final JavaPlugin plugin;
    private final NamespacedKey keyFakeNpc;
    private final NamespacedKey keyHologram;
    private final NamespacedKey keyCaptureHologram;
    private final NamespacedKey keyFamiliarPet;
    private final NamespacedKey keyPowerupEntry;
    private BukkitTask task;

    private boolean enabled;
    private long intervalTicks;
    private String broadcastTemplate;
    private boolean removeDroppedItems;
    private boolean removeExperienceOrbs;
    private boolean removeEndCrystals;
    private boolean removeProjectiles;
    private boolean removeStrayArmorStands;
    private boolean removeStrayItemDisplays;
    private boolean removeVillagers;
    private boolean removeWanderingTraders;

    public ClearLagManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyFakeNpc = new NamespacedKey(plugin, "dpmr_fake_npc");
        this.keyHologram = new NamespacedKey(plugin, "dpmr_hologram");
        this.keyCaptureHologram = new NamespacedKey(plugin, "dpmr_capture_hologram");
        this.keyFamiliarPet = new NamespacedKey(plugin, "dpmr_familiar_pet");
        this.keyPowerupEntry = new NamespacedKey(plugin, "powerup_entry");
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        org.bukkit.configuration.ConfigurationSection sec = cfg.getConfigurationSection("clear-lag");
        if (sec == null) {
            enabled = false;
            intervalTicks = 20L * 60 * 10;
            broadcastTemplate = "";
            applyDefaults();
            return;
        }
        enabled = sec.getBoolean("enabled", true);
        int minutes = Math.max(1, sec.getInt("interval-minutes", 10));
        intervalTicks = minutes * 20L * 60L;
        broadcastTemplate = sec.getString("message", defaultMessage());
        if (broadcastTemplate == null) {
            broadcastTemplate = "";
        }
        removeDroppedItems = sec.getBoolean("remove-dropped-items", true);
        removeExperienceOrbs = sec.getBoolean("remove-experience-orbs", true);
        removeEndCrystals = sec.getBoolean("remove-end-crystals", true);
        removeProjectiles = sec.getBoolean("remove-projectiles", true);
        removeStrayArmorStands = sec.getBoolean("remove-stray-armor-stands", true);
        removeStrayItemDisplays = sec.getBoolean("remove-stray-item-displays", true);
        removeVillagers = sec.getBoolean("remove-villagers", false);
        removeWanderingTraders = sec.getBoolean("remove-wandering-traders", false);
    }

    private void applyDefaults() {
        removeDroppedItems = true;
        removeExperienceOrbs = true;
        removeEndCrystals = true;
        removeProjectiles = true;
        removeStrayArmorStands = true;
        removeStrayItemDisplays = true;
        removeVillagers = false;
        removeWanderingTraders = false;
    }

    private static String defaultMessage() {
        return "Clear lag: %count% entities were removed (drops, stray projectiles, crystals, etc.).";
    }

    public void start() {
        stop();
        if (!enabled) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::runOnce, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void runOnce() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (shouldRemove(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Clear lag: removed " + removed + " entities.");
        }
        if (broadcastTemplate.isEmpty() || removed == 0) {
            return;
        }
        String text = broadcastTemplate.replace("%count%", Integer.toString(removed));
        Component line = Component.text(text, NamedTextColor.GRAY);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    private boolean shouldRemove(Entity entity) {
        if (entity instanceof Player) {
            return false;
        }
        if (removeDroppedItems && entity instanceof Item) {
            return true;
        }
        if (removeExperienceOrbs && entity instanceof ExperienceOrb) {
            return true;
        }
        if (removeEndCrystals && entity instanceof EnderCrystal) {
            return true;
        }
        if (removeProjectiles && entity instanceof Projectile p && !(p instanceof FishHook)) {
            return true;
        }
        if (removeStrayArmorStands && entity instanceof ArmorStand as) {
            return !isProtectedArmorStand(as);
        }
        if (removeStrayItemDisplays && entity instanceof ItemDisplay disp) {
            return !isProtectedItemDisplay(disp);
        }
        if (removeVillagers && entity instanceof Villager) {
            return true;
        }
        if (removeWanderingTraders && entity instanceof WanderingTrader) {
            return true;
        }
        return false;
    }

    private boolean isProtectedArmorStand(ArmorStand as) {
        PersistentDataContainer pdc = as.getPersistentDataContainer();
        if (pdc.has(keyHologram, PersistentDataType.BYTE)) {
            return true;
        }
        if (pdc.has(keyCaptureHologram, PersistentDataType.BYTE)) {
            return true;
        }
        if (pdc.has(keyFamiliarPet, PersistentDataType.BYTE)) {
            return true;
        }
        for (org.bukkit.NamespacedKey k : pdc.getKeys()) {
            if ("dpmr_hologram".equals(k.getKey())) {
                return true;
            }
        }
        if (pdc.has(keyFakeNpc, PersistentDataType.BYTE)) {
            return true;
        }
        if (pdc.has(keyFakeNpc, PersistentDataType.INTEGER)) {
            Integer v = pdc.get(keyFakeNpc, PersistentDataType.INTEGER);
            if (v != null && v != 0) {
                return true;
            }
        }
        for (org.bukkit.NamespacedKey k : pdc.getKeys()) {
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
        return false;
    }

    private boolean isProtectedItemDisplay(ItemDisplay disp) {
        return disp.getPersistentDataContainer().has(keyPowerupEntry, PersistentDataType.STRING);
    }
}
