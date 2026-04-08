package fr.dpmr.game;

import fr.dpmr.data.ClanManager;
import fr.dpmr.data.PointsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ApocalypseManager implements Listener {

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final ClanManager clanManager;
    private final LootManager lootManager;
    private GameScoreboard gameScoreboard;
    private DynamicObjectiveManager dynamicObjectiveManager;
    private boolean gameRunning = false;
    private boolean lastNightState = false;
    private BukkitTask loopTask;
    private int ticksSinceDisaster = 0;

    public ApocalypseManager(JavaPlugin plugin, PointsManager pointsManager, ClanManager clanManager, LootManager lootManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.clanManager = clanManager;
        this.lootManager = lootManager;
    }

    public void setGameScoreboard(GameScoreboard gameScoreboard) {
        this.gameScoreboard = gameScoreboard;
    }

    public void setDynamicObjectiveManager(DynamicObjectiveManager dynamicObjectiveManager) {
        this.dynamicObjectiveManager = dynamicObjectiveManager;
    }

    public DynamicObjectiveManager getDynamicObjectiveManager() {
        return dynamicObjectiveManager;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public void startGame() {
        if (gameRunning) {
            return;
        }
        gameRunning = true;
        ticksSinceDisaster = 0;
        loopTask = Bukkit.getScheduler().runTaskTimer(plugin, this::gameLoop, 20L, 20L);
        lootManager.startLoops();
        if (dynamicObjectiveManager != null) {
            dynamicObjectiveManager.onGameStart();
        }
        if (gameScoreboard != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                gameScoreboard.applySidebar(p);
            }
        }
        Bukkit.broadcast(Component.text("[DPMR] Le mode apocalypse commence !", NamedTextColor.RED));
    }

    public void stopGame() {
        if (!gameRunning) {
            return;
        }
        gameRunning = false;
        if (loopTask != null) {
            loopTask.cancel();
            loopTask = null;
        }
        lootManager.stopLoops();
        if (dynamicObjectiveManager != null) {
            dynamicObjectiveManager.onGameStop();
        }
        Bukkit.broadcast(Component.text("[DPMR] Le mode apocalypse est termine.", NamedTextColor.GRAY));
    }

    public void forceDisaster() {
        if (!gameRunning) {
            return;
        }
        triggerDisaster();
    }

    private void gameLoop() {
        if (!gameRunning) {
            return;
        }
        World world = Bukkit.getWorlds().getFirst();
        long time = world.getTime();
        boolean isNight = time >= 13000 && time <= 23000;

        if (isNight != lastNightState) {
            lastNightState = isNight;
            if (isNight) {
                Bukkit.broadcast(Component.text("[DPMR] La nuit tombe... restez caches.", NamedTextColor.DARK_RED));
            } else {
                Bukkit.broadcast(Component.text("[DPMR] Le soleil se leve. Respirez.", NamedTextColor.GOLD));
            }
        }

        if (isNight) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, true, false, true));
            }
        }

        ticksSinceDisaster++;
        if (ticksSinceDisaster >= 180) {
            ticksSinceDisaster = 0;
            if (isNight) {
                triggerDisaster();
            }
        }
    }

    private void triggerDisaster() {
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        Player target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        Location base = target.getLocation();
        Location strike = base.clone().add(
                ThreadLocalRandom.current().nextDouble(-8.0, 8.0),
                0.0,
                ThreadLocalRandom.current().nextDouble(-8.0, 8.0)
        );
        strike.getWorld().strikeLightning(strike);
        Bukkit.broadcast(Component.text("[DPMR] Catastrophe: un eclair frappe pres de " + target.getName() + " !", NamedTextColor.YELLOW));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (gameRunning) {
            event.getPlayer().sendMessage(Component.text("[DPMR] Le mode apocalypse est en cours.", NamedTextColor.RED));
        }
        if (gameScoreboard != null) {
            gameScoreboard.applySidebar(event.getPlayer());
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!gameRunning) {
            return;
        }
        if (!(event.getEntity() instanceof Monster monster)) {
            return;
        }
        World world = monster.getWorld();
        long time = world.getTime();
        boolean isNight = time >= 13000 && time <= 23000;
        if (!isNight) {
            return;
        }

        monster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 0));
        monster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60, 0));

        if (monster.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            double health = monster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            monster.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health + 6.0);
            monster.setHealth(Math.min(monster.getHealth() + 6.0, monster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!gameRunning) {
            return;
        }
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() > 0.15) {
            return;
        }

        ItemStack token = new ItemStack(Material.IRON_NUGGET);
        ItemMeta meta = token.getItemMeta();
        meta.displayName(Component.text("Fragment d'apocalypse", NamedTextColor.DARK_PURPLE));
        token.setItemMeta(meta);
        event.getDrops().add(token);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (victim.getPersistentDataContainer().has(new NamespacedKey(plugin, "dpmr_fake_npc"), PersistentDataType.BYTE)) {
            event.deathMessage(null);
            return;
        }
        Player killer = victim.getKiller();
        Entity killerEntity = resolveKillerEntity(victim);
        event.deathMessage(buildDeathMessage(victim, killer, killerEntity));
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        String killerClan = clanManager.getPlayerClan(killer.getUniqueId());
        String victimClan = clanManager.getPlayerClan(victim.getUniqueId());
        if (killerClan != null && killerClan.equalsIgnoreCase(victimClan)) {
            return;
        }
        int pointsPerKill = Math.max(1, plugin.getConfig().getInt("points.per-player-kill", 10));
        int gained = pointsManager.addKillRewardPoints(killer.getUniqueId(), pointsPerKill);
        pointsManager.addKill(killer.getUniqueId());
        pointsManager.saveAsync();
        killer.sendMessage(Component.text("+" + gained + " points (elimination de " + victim.getName() + ")", NamedTextColor.GOLD));
    }

    private Component buildDeathMessage(Player victim, Player killer, Entity killerEntity) {
        String victimName = victim.getName();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            String weaponName = readKillerWeaponName(killer);
            return Component.text(killer.getName() + " a elimine " + victimName + " avec " + weaponName, NamedTextColor.GRAY);
        }
        if (killerEntity instanceof Monster) {
            return Component.text(victimName + " a ete tue par " + describeMob(killerEntity) + ".", NamedTextColor.GRAY);
        }
        EntityDamageEvent last = victim.getLastDamageCause();
        if (last == null) {
            return Component.text(victimName + " est mort.", NamedTextColor.GRAY);
        }
        return switch (last.getCause()) {
            case FALL -> Component.text(victimName + " s'est ecrase au sol.", NamedTextColor.GRAY);
            case VOID -> Component.text(victimName + " est tombe dans le vide.", NamedTextColor.GRAY);
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> Component.text(victimName + " a brule vif.", NamedTextColor.GRAY);
            case DROWNING -> Component.text(victimName + " s'est noye.", NamedTextColor.GRAY);
            case SUFFOCATION -> Component.text(victimName + " a suffoque.", NamedTextColor.GRAY);
            case STARVATION -> Component.text(victimName + " est mort de faim.", NamedTextColor.GRAY);
            case POISON, WITHER, MAGIC -> Component.text(victimName + " est mort empoisonne.", NamedTextColor.GRAY);
            case LIGHTNING -> Component.text(victimName + " a ete foudroye.", NamedTextColor.GRAY);
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> Component.text(victimName + " a explose.", NamedTextColor.GRAY);
            case PROJECTILE -> Component.text(victimName + " a ete transperce par un projectile.", NamedTextColor.GRAY);
            case FLY_INTO_WALL -> Component.text(victimName + " s'est ecrase contre un mur.", NamedTextColor.GRAY);
            case CRAMMING -> Component.text(victimName + " a ete etouffe dans la foule.", NamedTextColor.GRAY);
            case KILL, SUICIDE -> Component.text(victimName + " s'est donne la mort.", NamedTextColor.GRAY);
            default -> Component.text(victimName + " est mort (" + last.getCause().name().toLowerCase() + ").", NamedTextColor.GRAY);
        };
    }

    private Entity resolveKillerEntity(Player victim) {
        EntityDamageEvent last = victim.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    private String describeMob(Entity mob) {
        if (mob.customName() != null) {
            String custom = PlainTextComponentSerializer.plainText().serialize(mob.customName()).trim();
            if (!custom.isEmpty()) {
                return custom;
            }
        }
        return "un " + mob.getType().name().toLowerCase().replace('_', ' ');
    }

    private String readKillerWeaponName(Player killer) {
        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return "poings";
        }
        if (hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
            Component display = hand.getItemMeta().displayName();
            if (display != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(display).trim();
                if (!plain.isEmpty()) {
                    return plain;
                }
            }
        }
        return hand.getType().name().toLowerCase().replace('_', ' ');
    }
}
