package fr.dpmr;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Purges orphaned DPMR entities (NPCs, hologram ArmorStands) the moment their
 * chunk is loaded from disk, preventing the massive entity-count build-up that
 * caused multi-second freezes.
 */
public final class EntityPurgeListener implements Listener {

    private final NamespacedKey keyFakeNpc;
    private final NamespacedKey keyHologram;
    private final NamespacedKey keyCaptureHologram;
    private final Logger logger;
    private final AtomicInteger totalPurged = new AtomicInteger();

    public EntityPurgeListener(JavaPlugin plugin) {
        this.keyFakeNpc = new NamespacedKey(plugin, "dpmr_fake_npc");
        this.keyHologram = new NamespacedKey(plugin, "dpmr_hologram");
        this.keyCaptureHologram = new NamespacedKey(plugin, "dpmr_capture_hologram");
        this.logger = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        int removed = 0;
        for (Entity entity : event.getEntities()) {
            if (shouldPurge(entity)) {
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) {
            int total = totalPurged.addAndGet(removed);
            logger.info("[EntityPurge] Chunk " + event.getChunk().getX() + "," + event.getChunk().getZ()
                    + " (" + event.getWorld().getName() + "): removed " + removed
                    + " orphaned DPMR entities (session total: " + total + ")");
        }
    }

    private boolean shouldPurge(Entity entity) {
        if (entity instanceof LivingEntity le) {
            if (isDpmrNpc(le)) return true;
        }
        if (entity instanceof ArmorStand as) {
            if (isDpmrHologram(as)) return true;
        }
        return false;
    }

    private boolean isDpmrNpc(LivingEntity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(keyFakeNpc, PersistentDataType.BYTE)) return true;
        if (pdc.has(keyFakeNpc, PersistentDataType.INTEGER)) {
            Integer v = pdc.get(keyFakeNpc, PersistentDataType.INTEGER);
            return v != null && v != 0;
        }
        for (NamespacedKey k : pdc.getKeys()) {
            if (!"dpmr_fake_npc".equals(k.getKey())) continue;
            if (pdc.has(k, PersistentDataType.BYTE)) return true;
            if (pdc.has(k, PersistentDataType.INTEGER)) {
                Integer v = pdc.get(k, PersistentDataType.INTEGER);
                if (v != null && v != 0) return true;
            }
        }
        for (NamespacedKey k : pdc.getKeys()) {
            if (!"dpmr_npc_kind".equals(k.getKey())) continue;
            NamespacedKey rewardKey = new NamespacedKey(k.getNamespace(), "dpmr_npc_reward");
            if (pdc.has(rewardKey, PersistentDataType.INTEGER)) return true;
        }
        return false;
    }

    private boolean isDpmrHologram(ArmorStand as) {
        PersistentDataContainer pdc = as.getPersistentDataContainer();
        if (pdc.has(keyHologram, PersistentDataType.BYTE)) return true;
        if (pdc.has(keyCaptureHologram, PersistentDataType.BYTE)) return true;
        for (NamespacedKey k : pdc.getKeys()) {
            if ("dpmr_hologram".equals(k.getKey())) return true;
            if ("dpmr_capture_hologram".equals(k.getKey())) return true;
        }
        return false;
    }

    public int getTotalPurged() {
        return totalPurged.get();
    }
}
