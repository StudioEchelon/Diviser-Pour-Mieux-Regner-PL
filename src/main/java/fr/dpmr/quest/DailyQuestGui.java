package fr.dpmr.quest;

import fr.dpmr.data.ClanManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chest GUI and event hooks for the three daily quest systems (Combat, Gathering, Challenge).
 */
public final class DailyQuestGui implements Listener {

    private static final int GUI_SIZE = 54;
    private static final int[] QUEST_SLOTS = {10, 13, 16};
    private static final int[] CLAIM_SLOTS = {37, 40, 43};

    private final JavaPlugin plugin;
    private final DailyQuestManager manager;
    private final ClanManager clanManager;
    private final NamespacedKey fakeNpcKey;

    public DailyQuestGui(JavaPlugin plugin, DailyQuestManager manager, ClanManager clanManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.clanManager = clanManager;
        this.fakeNpcKey = new NamespacedKey(plugin, "dpmr_fake_npc");
    }

    public void open(Player player) {
        if (!manager.isEnabled()) {
            player.sendMessage(Component.text("Daily quests are disabled.", NamedTextColor.RED));
            return;
        }
        manager.ensureQuestForToday(player);
        Inventory inv = DailyQuestHolder.create(title(), GUI_SIZE);
        fillBackdrop(inv);
        List<DailyQuestManager.QuestSnapshot> snaps = manager.snapshots(player.getUniqueId());
        int shown = Math.min(manager.slotCount(), QUEST_SLOTS.length);
        for (int i = 0; i < shown; i++) {
            DailyQuestManager.QuestSnapshot snap = i < snaps.size() ? snaps.get(i) : null;
            int qSlot = QUEST_SLOTS[i];
            int cSlot = CLAIM_SLOTS[i];
            if (snap == null) {
                inv.setItem(qSlot, item(Material.BARRIER, NamedTextColor.RED,
                        "No active quest",
                        List.of(Component.text("Track " + (i + 1), NamedTextColor.DARK_GRAY))));
                inv.setItem(cSlot, item(Material.GRAY_DYE, NamedTextColor.GRAY, "Claim reward", List.of()));
            } else {
                inv.setItem(qSlot, questIcon(snap));
                inv.setItem(cSlot, claimButton(snap));
            }
        }
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.35f, 1.15f);
    }

    private static Component title() {
        return Component.text("Daily Quests", NamedTextColor.GOLD, TextDecoration.BOLD);
    }

    private static ItemStack item(Material mat, NamedTextColor nameColor, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, nameColor, TextDecoration.BOLD));
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static String systemName(int slot) {
        return switch (slot) {
            case 0 -> "Combat";
            case 1 -> "Gathering";
            case 2 -> "Challenge";
            default -> "Track " + (slot + 1);
        };
    }

    private ItemStack questIcon(DailyQuestManager.QuestSnapshot snap) {
        String headline = switch (snap.type()) {
            case KILL_PLAYERS -> "Eliminate players";
            case KILL_HOSTILE_MOBS -> "Slay hostile mobs";
            case CATCH_FISH -> "Catch fish";
            case CHOP_LOGS -> "Chop logs";
            case DEAL_PLAYER_DAMAGE -> "Damage players";
        };
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(systemName(snap.slot()), NamedTextColor.DARK_AQUA, TextDecoration.ITALIC));
        lore.add(Component.empty());
        lore.add(Component.text(switch (snap.type()) {
            case KILL_PLAYERS -> "Valid PvP eliminations (same rules as kill points).";
            case KILL_HOSTILE_MOBS -> "Hostile mobs you kill count (zombies, skeletons, etc.).";
            case CATCH_FISH -> "Successful catches with a fishing rod.";
            case CHOP_LOGS -> "Break any overworld log block.";
            case DEAL_PLAYER_DAMAGE -> "Melee or projectile damage to enemy players (half-hearts).";
        }, NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Progress: ", NamedTextColor.YELLOW)
                .append(Component.text(snap.progress() + " / " + snap.target(), NamedTextColor.WHITE)));
        if (snap.claimed()) {
            lore.add(Component.text("Reward claimed.", NamedTextColor.GREEN));
        } else if (snap.complete()) {
            lore.add(Component.text("Complete — claim below.", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("Resets daily at midnight (calendar).", NamedTextColor.DARK_GRAY));
        }
        Material icon = switch (snap.type()) {
            case KILL_PLAYERS -> Material.IRON_SWORD;
            case KILL_HOSTILE_MOBS -> Material.BONE;
            case CATCH_FISH -> Material.FISHING_ROD;
            case CHOP_LOGS -> Material.OAK_LOG;
            case DEAL_PLAYER_DAMAGE -> Material.GOLDEN_SWORD;
        };
        return item(icon, NamedTextColor.AQUA, headline, lore);
    }

    private ItemStack claimButton(DailyQuestManager.QuestSnapshot snap) {
        int reward = manager.rewardPointsForType(snap.type());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(systemName(snap.slot()) + " reward: " + reward + " points", NamedTextColor.GOLD));
        lore.add(Component.empty());
        if (snap.claimed()) {
            return item(Material.GRAY_DYE, NamedTextColor.GRAY, "Already claimed", lore);
        }
        if (!snap.complete()) {
            lore.add(Component.text("Finish the objective first.", NamedTextColor.RED));
            return item(Material.GRAY_DYE, NamedTextColor.DARK_GRAY, "Claim reward", lore);
        }
        lore.add(Component.text("Click to claim.", NamedTextColor.GREEN));
        return item(Material.LIME_DYE, NamedTextColor.GREEN, "Claim reward", lore);
    }

    private static void fillBackdrop(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (!isQuestOrClaimSlot(i)) {
                inv.setItem(i, pane);
            }
        }
    }

    private static boolean isQuestOrClaimSlot(int index) {
        for (int s : QUEST_SLOTS) {
            if (s == index) {
                return true;
            }
        }
        for (int s : CLAIM_SLOTS) {
            if (s == index) {
                return true;
            }
        }
        return false;
    }

    private static int claimIndexForSlot(int clicked) {
        for (int i = 0; i < CLAIM_SLOTS.length; i++) {
            if (CLAIM_SLOTS[i] == clicked) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        // ensureQuestForToday peut ecrire daily-quests.yml sur le thread principal : decaler apres spawn/chunks.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && p.isOnline()) {
                manager.ensureQuestForToday(p);
            }
        }, 3L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        Player victim = event.getEntity();
        if (victim.getPersistentDataContainer().has(fakeNpcKey, PersistentDataType.BYTE)) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        if (!countsAsQuestPlayerKill(killer, victim)) {
            return;
        }
        notifyQuestProgress(killer, manager.addProgress(killer, DailyQuestType.KILL_PLAYERS, 1));
    }

    private boolean countsAsQuestPlayerKill(Player killer, Player victim) {
        String kc = clanManager.getPlayerClan(killer.getUniqueId());
        String vc = clanManager.getPlayerClan(victim.getUniqueId());
        if (kc != null && kc.equalsIgnoreCase(vc)) {
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        Player killer = resolveKiller(event.getEntity());
        if (killer == null) {
            return;
        }
        notifyQuestProgress(killer, manager.addProgress(killer, DailyQuestType.KILL_HOSTILE_MOBS, 1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        notifyQuestProgress(event.getPlayer(), manager.addProgress(event.getPlayer(), DailyQuestType.CATCH_FISH, 1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogBreak(BlockBreakEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        Block block = event.getBlock();
        if (!Tag.LOGS.isTagged(block.getType())) {
            return;
        }
        notifyQuestProgress(event.getPlayer(), manager.addProgress(event.getPlayer(), DailyQuestType.CHOP_LOGS, 1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (victim.getPersistentDataContainer().has(fakeNpcKey, PersistentDataType.BYTE)) {
            return;
        }
        Player attacker = resolveDamager(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (!countsAsQuestPlayerKill(attacker, victim)) {
            return;
        }
        int dealt = (int) Math.ceil(event.getFinalDamage());
        if (dealt > 0) {
            notifyQuestProgress(attacker, manager.addProgress(attacker, DailyQuestType.DEAL_PLAYER_DAMAGE, dealt));
        }
    }

    private void notifyQuestProgress(Player player, List<Integer> completedTracks) {
        if (completedTracks == null || completedTracks.isEmpty()) {
            return;
        }
        player.sendActionBar(Component.text("Daily quest complete — open /dailyquest to claim your reward.", NamedTextColor.GREEN));
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

    private static Player resolveKiller(org.bukkit.entity.LivingEntity dead) {
        Player killer = dead.getKiller();
        if (killer != null) {
            return killer;
        }
        EntityDamageEvent last = dead.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent by)) {
            return null;
        }
        Entity damager = by.getDamager();
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (DailyQuestHolder.from(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        DailyQuestHolder holder = DailyQuestHolder.from(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        int track = claimIndexForSlot(event.getSlot());
        if (track < 0 || track >= manager.slotCount()) {
            return;
        }
        List<DailyQuestManager.QuestSnapshot> snaps = manager.snapshots(player.getUniqueId());
        DailyQuestManager.QuestSnapshot snap = track < snaps.size() ? snaps.get(track) : null;
        if (snap == null || snap.claimed() || !snap.complete()) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 0.9f);
            return;
        }
        int gained = manager.tryClaim(player, track);
        if (gained < 0) {
            player.sendMessage(Component.text("Could not claim right now.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 0.9f);
            return;
        }
        if (gained > 0) {
            player.sendMessage(Component.text(systemName(track) + " daily quest complete! +" + gained + " points.", NamedTextColor.GOLD));
        } else {
            player.sendMessage(Component.text(systemName(track) + " daily quest complete!", NamedTextColor.GOLD));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.25f);
        player.closeInventory();
    }
}
