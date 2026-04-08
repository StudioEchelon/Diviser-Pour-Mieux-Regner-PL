package fr.dpmr.game;

import fr.dpmr.zone.ZoneManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Familiers visibles (tête = skin du joueur, armure cuir, arme en main) avec 5 rôles distincts.
 */
public class FamiliarPetManager implements Listener {

    private final JavaPlugin plugin;
    private final ZoneManager zoneManager;
    private final NamespacedKey keyFamiliarPet;
    private final Map<UUID, PetState> pets = new HashMap<>();
    private BukkitTask task;

    private static final class PetState {
        final ArmorStand stand;
        final PetType type;
        long lastShotMs;
        long lastMedicPulseMs;
        long lastMeleeMs;
        int auraTick;

        PetState(ArmorStand stand, PetType type) {
            this.stand = stand;
            this.type = type;
            this.lastShotMs = 0L;
            this.lastMedicPulseMs = 0L;
            this.lastMeleeMs = 0L;
            this.auraTick = 0;
        }
    }

    public FamiliarPetManager(JavaPlugin plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        this.keyFamiliarPet = new NamespacedKey(plugin, "dpmr_familiar_pet");
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID u : pets.keySet().toArray(UUID[]::new)) {
            dismiss(u);
        }
    }

    public boolean has(UUID owner) {
        PetState s = pets.get(owner);
        return s != null && s.stand.isValid();
    }

    public void summon(Player owner, PetType type) {
        if (type == null) {
            owner.sendMessage(Component.text("Type de familier inconnu.", NamedTextColor.RED));
            return;
        }
        if (has(owner.getUniqueId())) {
            dismiss(owner.getUniqueId());
        }
        Location spawn = owner.getLocation().add(0.6, 0, 0.6);
        ArmorStand as = owner.getWorld().spawn(spawn, ArmorStand.class);
        as.setInvisible(false);
        as.setMarker(false);
        as.setSmall(true);
        as.setArms(true);
        as.setBasePlate(false);
        as.setGravity(false);
        as.setCanPickupItems(false);
        as.setInvulnerable(true);
        as.setPersistent(true);
        as.setCustomNameVisible(true);
        as.customName(Component.text(type.displayFr(), type.nameColor()));
        as.getPersistentDataContainer().set(keyFamiliarPet, PersistentDataType.BYTE, (byte) 1);
        applySkinAndGear(owner, as, type);
        pets.put(owner.getUniqueId(), new PetState(as, type));
        World ow = owner.getWorld();
        Location burst = spawn.clone().add(0, 0.5, 0);
        ow.spawnParticle(Particle.DUST, burst, 28, 0.35, 0.45, 0.35, 0.04, type.beamDust());
        ow.spawnParticle(Particle.END_ROD, burst, 10, 0.2, 0.35, 0.2, 0.02);
        ow.playSound(owner.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.45f, 1.35f);
        ow.playSound(owner.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.55f, 1.2f);
        owner.sendMessage(Component.text("Familier invoqué : ", NamedTextColor.GRAY)
                .append(Component.text(type.displayFr(), type.nameColor(), TextDecoration.ITALIC)));
    }

    private void applySkinAndGear(Player owner, ArmorStand as, PetType type) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(owner);
        head.setItemMeta(sm);
        var eq = as.getEquipment();
        eq.setHelmet(head);
        eq.setChestplate(type.leatherPiece(Material.LEATHER_CHESTPLATE));
        eq.setLeggings(type.leatherPiece(Material.LEATHER_LEGGINGS));
        eq.setBoots(type.leatherPiece(Material.LEATHER_BOOTS));
        eq.setItemInMainHand(type.createMainHand());
        ItemStack off = type.createOffHand();
        eq.setItemInOffHand(off != null ? off : new ItemStack(Material.AIR));
    }

    public void dismiss(UUID owner) {
        PetState s = pets.remove(owner);
        if (s != null && s.stand.isValid()) {
            s.stand.remove();
        }
    }

    private void tick() {
        for (Map.Entry<UUID, PetState> e : new HashMap<>(pets).entrySet()) {
            UUID ownerId = e.getKey();
            PetState state = e.getValue();
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !owner.isOnline() || state.stand == null || !state.stand.isValid()) {
                dismiss(ownerId);
                continue;
            }
            follow(owner, state.stand);
            tickAmbientAura(state);
            switch (state.type) {
                case GUNNER -> behaveGunner(owner, state);
                case MEDIC -> behaveMedic(owner, state);
                case SNIPER -> behaveSniper(owner, state);
                case SCOUT -> behaveScout(owner, state);
                case BRUTE -> behaveBrute(owner, state);
            }
        }
    }

    private void tickAmbientAura(PetState state) {
        state.auraTick++;
        if ((state.auraTick & 3) != 0) {
            return;
        }
        World w = state.stand.getWorld();
        Location c = state.stand.getLocation().add(0, 0.35, 0);
        w.spawnParticle(Particle.DUST, c, 2, 0.22, 0.4, 0.22, 0.01, state.type.auraDust());
        if (state.auraTick % 16 == 0) {
            w.spawnParticle(Particle.WITCH, c.add(0, 0.15, 0), 1, 0.08, 0.1, 0.08, 0);
        }
    }

    private void follow(Player owner, ArmorStand pet) {
        Location o = owner.getLocation();
        Location p = pet.getLocation();
        double distSq = o.distanceSquared(p);
        Vector back = owner.getLocation().getDirection().clone().setY(0);
        if (back.lengthSquared() < 0.01) {
            back = new Vector(0, 0, -1);
        } else {
            back.normalize().multiply(-1.0);
        }
        double bob = Math.sin(owner.getTicksLived() / 10.0) * 0.04;
        Location target = o.clone().add(back.multiply(1.05)).add(0, 0.2 + bob, 0);
        target.setYaw(o.getYaw());
        target.setPitch(0);

        if (distSq > 20 * 20) {
            pet.teleport(target);
            return;
        }
        if (distSq > 3.5 * 3.5) {
            Vector v = target.toVector().subtract(p.toVector()).multiply(0.35);
            pet.teleport(p.add(v));
        } else {
            pet.teleport(target);
        }
    }

    private boolean combatOk(Player owner, Location at) {
        return zoneManager == null || zoneManager.isCombatAllowed(at);
    }

    private void face(ArmorStand pet, Location point) {
        Location eye = pet.getEyeLocation();
        Vector to = point.toVector().subtract(eye.toVector());
        if (to.lengthSquared() < 0.0001) {
            return;
        }
        Location L = eye.clone();
        L.setDirection(to);
        pet.setRotation(L.getYaw(), 0);
    }

    private void behaveGunner(Player owner, PetState state) {
        if (!combatOk(owner, owner.getLocation())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - state.lastShotMs < 620) {
            return;
        }
        LivingEntity target = findEnemyPlayer(owner, state.stand, state.stand.getEyeLocation(), 13.0);
        if (target == null || !combatOk(owner, target.getLocation())) {
            return;
        }
        state.lastShotMs = now;
        face(state.stand, target.getEyeLocation());
        shootHitscan(owner, state.stand, state.stand.getEyeLocation(), target, 13.0, 1.55, 3.6, state.type);
    }

    private void behaveMedic(Player owner, PetState state) {
        long now = System.currentTimeMillis();
        if (now - state.lastMedicPulseMs < 3200) {
            return;
        }
        state.lastMedicPulseMs = now;
        Location c = state.stand.getLocation().add(0, 0.5, 0);
        World w = c.getWorld();
        if (w == null) {
            return;
        }
        if (owner.getLocation().distanceSquared(c) > 5.5 * 5.5) {
            return;
        }
        w.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 0.28f, 1.45f);
        w.spawnParticle(Particle.HAPPY_VILLAGER, c, 5, 0.35, 0.4, 0.35, 0.02);
        w.spawnParticle(Particle.DUST, c, 8, 0.4, 0.35, 0.4, 0.02, state.type.beamDust());
        double max = owner.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        owner.setHealth(Math.min(max, owner.getHealth() + 0.7));
    }

    private void behaveSniper(Player owner, PetState state) {
        if (!combatOk(owner, owner.getLocation())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - state.lastShotMs < 2100) {
            return;
        }
        LivingEntity target = findEnemyPlayer(owner, state.stand, state.stand.getEyeLocation(), 21.0);
        if (target == null || !combatOk(owner, target.getLocation())) {
            return;
        }
        state.lastShotMs = now;
        face(state.stand, target.getEyeLocation());
        shootHitscan(owner, state.stand, state.stand.getEyeLocation(), target, 21.0, 2.85, 1.25, state.type);
    }

    private void behaveScout(Player owner, PetState state) {
        if (!combatOk(owner, owner.getLocation())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - state.lastShotMs < 460) {
            return;
        }
        LivingEntity target = findEnemyPlayer(owner, state.stand, state.stand.getEyeLocation(), 9.0);
        if (target == null || !combatOk(owner, target.getLocation())) {
            return;
        }
        state.lastShotMs = now;
        face(state.stand, target.getEyeLocation());
        shootHitscan(owner, state.stand, state.stand.getEyeLocation(), target, 9.0, 0.95, 5.2, state.type);
    }

    private void behaveBrute(Player owner, PetState state) {
        if (!combatOk(owner, owner.getLocation())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - state.lastMeleeMs < 950) {
            return;
        }
        LivingEntity target = findEnemyPlayer(owner, state.stand, state.stand.getLocation().add(0, 0.8, 0), 3.5);
        if (target == null || !combatOk(owner, target.getLocation())) {
            return;
        }
        if (target.getLocation().distanceSquared(state.stand.getLocation()) > 2.35 * 2.35) {
            return;
        }
        state.lastMeleeMs = now;
        face(state.stand, target.getLocation().add(0, 1, 0));
        World w = state.stand.getWorld();
        double dmg = 2.85 * ThreadLocalRandom.current().nextDouble(0.94, 1.06);
        boolean lethal = target instanceof Player victim && victim.getHealth() <= dmg;
        target.damage(dmg, owner);
        if (lethal && target instanceof Player victim) {
            announcePetKill(owner, victim);
        }
        w.playSound(state.stand.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.42f, 0.72f);
        w.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation(), 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.18, 0.32, 0.18, 0.02);
    }

    private LivingEntity findEnemyPlayer(Player owner, LivingEntity looker, Location from, double range) {
        World w = from.getWorld();
        if (w == null) {
            return null;
        }
        LivingEntity best = null;
        double bestD = range * range;
        for (Player p : w.getPlayers()) {
            if (p == owner || !p.isValid() || p.isDead()) {
                continue;
            }
            double d = p.getLocation().distanceSquared(from);
            if (d > bestD) {
                continue;
            }
            if (!looker.hasLineOfSight(p)) {
                continue;
            }
            bestD = d;
            best = p;
        }
        return best;
    }

    private void shootHitscan(Player owner, ArmorStand shooter, Location origin, LivingEntity target,
                              double range, double baseDmg, double spreadDeg, PetType petType) {
        World w = origin.getWorld();
        if (w == null) {
            return;
        }
        Vector dir = target.getEyeLocation().toVector().subtract(origin.toVector()).normalize();
        dir = applySpread(origin, dir, spreadDeg);

        RayTraceResult hit = w.rayTraceEntities(origin, dir, range, 0.35, ent -> {
            if (!(ent instanceof Player p) || p == owner || !p.isValid() || p.isDead()) {
                return false;
            }
            GameMode gm = p.getGameMode();
            return gm != GameMode.SPECTATOR && gm != GameMode.CREATIVE;
        });

        drawBeam(w, origin, dir, range, petType.beamDust());
        playPetShotSound(w, origin, petType);

        if (hit != null && hit.getHitEntity() instanceof LivingEntity living) {
            double dmg = baseDmg * ThreadLocalRandom.current().nextDouble(0.92, 1.08);
            boolean lethal = living instanceof Player victim && victim.getHealth() <= dmg;
            living.damage(dmg, owner);
            if (lethal && living instanceof Player victim) {
                announcePetKill(owner, victim);
            }
            w.spawnParticle(Particle.DAMAGE_INDICATOR, living.getLocation().add(0, 1, 0), 3, 0.12, 0.22, 0.12, 0.01);
        }
    }

    private static void playPetShotSound(World w, Location origin, PetType petType) {
        Sound sound = Sound.ITEM_CROSSBOW_SHOOT;
        float pitch = 1.18f;
        float volume = 0.26f;
        switch (petType) {
            case SNIPER -> {
                sound = Sound.ENTITY_ARROW_SHOOT;
                pitch = 0.52f;
                volume = 0.22f;
            }
            case SCOUT -> pitch = 1.42f;
            case GUNNER -> pitch = 1.05f;
            default -> {
            }
        }
        w.playSound(origin, sound, volume, pitch);
    }

    private void announcePetKill(Player owner, Player victim) {
        Component msg = Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text(victim.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" a été terrassé par le familier de ", NamedTextColor.GRAY))
                .append(Component.text(owner.getName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" ✧", NamedTextColor.GOLD));
        double rSq = 72 * 72;
        Location o = owner.getLocation();
        for (Player p : owner.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(o) <= rSq) {
                p.sendMessage(msg);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPetDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) {
            return;
        }
        for (PetState pet : pets.values()) {
            if (pet.stand.getUniqueId().equals(stand.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private static Vector applySpread(Location eye, Vector baseDir, double maxDegrees) {
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

    private static void drawBeam(World world, Location start, Vector direction, double range, Particle.DustOptions dust) {
        Vector step = direction.clone().normalize().multiply(0.3);
        Location cursor = start.clone();
        int max = Math.max(1, (int) (range / 0.3));
        for (int i = 0; i < max; i++) {
            world.spawnParticle(Particle.DUST, cursor, 1, 0.008, 0.008, 0.008, 0, dust);
            cursor.add(step);
        }
    }
}
