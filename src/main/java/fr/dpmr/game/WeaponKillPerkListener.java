package fr.dpmr.game;

import fr.dpmr.gui.WeaponKillPerkGui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attribue les kills à l'arme tenue en main (dernier hit) et remplit la jauge.
 */
public final class WeaponKillPerkListener implements Listener {

    public record KillCredit(UUID killerId, String weaponInstanceId) {
    }

    private final JavaPlugin plugin;
    private final WeaponManager weaponManager;
    private final WeaponKillPerkGui killPerkGui;
    private final Map<UUID, KillCredit> lastHit = new ConcurrentHashMap<>();

    public WeaponKillPerkListener(JavaPlugin plugin, WeaponManager weaponManager, WeaponKillPerkGui killPerkGui) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
        this.killPerkGui = killPerkGui;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity victim)) {
            return;
        }
        Player attacker = resolveDamager(event);
        if (attacker == null) {
            return;
        }
        ItemStack main = attacker.getInventory().getItemInMainHand();
        String wid = weaponManager.readWeaponId(main);
        WeaponProfile w = wid != null ? WeaponProfile.fromId(wid) : null;
        if (w == null || !w.supportsKillPerkMeter()) {
            return;
        }
        weaponManager.touchWeaponInstanceId(attacker, main);
        String inst = weaponManager.readWeaponInstanceId(main);
        if (inst == null || inst.isBlank()) {
            return;
        }
        lastHit.put(victim.getUniqueId(), new KillCredit(attacker.getUniqueId(), inst));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, "dpmr_fake_npc"), org.bukkit.persistence.PersistentDataType.BYTE)) {
            return;
        }
        KillCredit c = lastHit.remove(victim.getUniqueId());
        if (c == null) {
            c = creditFromKiller(victim);
        }
        if (c == null) {
            return;
        }
        applyKillCredit(c, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        KillCredit c = lastHit.remove(event.getEntity().getUniqueId());
        if (c == null) {
            return;
        }
        applyKillCredit(c, false);
    }

    private void applyKillCredit(KillCredit c, boolean playerKill) {
        if (!WeaponKillPerkState.enabled(plugin)) {
            return;
        }
        Player killer = Bukkit.getPlayer(c.killerId());
        if (killer == null || !killer.isOnline()) {
            return;
        }
        int delta = playerKill
                ? plugin.getConfig().getInt("weapons.kill-perks.meter-player-kill", 28)
                : plugin.getConfig().getInt("weapons.kill-perks.meter-mob-kill", 6);
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack weapon = weaponManager.findWeaponStackByInstance(killer, c.weaponInstanceId());
            if (weapon == null) {
                return;
            }
            WeaponProfile w = WeaponProfile.fromId(weaponManager.readWeaponId(weapon));
            if (w == null || !w.supportsKillPerkMeter()) {
                return;
            }
            if (WeaponKillPerkState.isMaxed(weapon, plugin)) {
                weaponManager.refreshWeaponMeta(weapon, killer);
                return;
            }
            weaponManager.touchWeaponInstanceId(killer, weapon);
            int meter = WeaponKillPerkState.addMeter(weapon, delta, plugin);
            weaponManager.refreshWeaponMeta(weapon, killer);
            if (meter >= WeaponKillPerkState.METER_MAX && !WeaponKillPerkState.isMaxed(weapon, plugin)) {
                killPerkGui.open(killer, c.weaponInstanceId());
            }
        });
    }

    /**
     * Si le dernier {@link EntityDamageByEntityEvent} n'a pas été reçu (ex. timings / plugins),
     * utiliser l'arme en main du {@link Player#getKiller()}.
     */
    private KillCredit creditFromKiller(Player victim) {
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return null;
        }
        ItemStack main = killer.getInventory().getItemInMainHand();
        String wid = weaponManager.readWeaponId(main);
        WeaponProfile w = wid != null ? WeaponProfile.fromId(wid) : null;
        if (w == null || !w.supportsKillPerkMeter()) {
            return null;
        }
        weaponManager.touchWeaponInstanceId(killer, main);
        String inst = weaponManager.readWeaponInstanceId(main);
        if (inst == null || inst.isBlank()) {
            return null;
        }
        return new KillCredit(killer.getUniqueId(), inst);
    }

    private static Player resolveDamager(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
