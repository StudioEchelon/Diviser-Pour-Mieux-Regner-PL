package fr.dpmr.pass;

import fr.dpmr.data.ClanManager;
import fr.dpmr.gui.BoutiqueUi;
import fr.dpmr.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
 * Interface coffre 54 cases : piste gratuite et premium, pagination.
 */
public final class ConquerPassGui implements Listener {

    private static final int SLOT_INFO = 4;
    private static final int SLOT_PREV = 0;
    private static final int SLOT_NEXT = 8;
    private static final int SLOT_PREMIUM_BUY = 49;
    private static final int[] FREE_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] PREM_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    private final JavaPlugin plugin;
    private final ConquerPassManager manager;
    private final ClanManager clanManager;
    private final NamespacedKey fakeNpcKey;

    public ConquerPassGui(JavaPlugin plugin, ConquerPassManager manager, ClanManager clanManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.clanManager = clanManager;
        this.fakeNpcKey = new NamespacedKey(plugin, "dpmr_fake_npc");
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        if (!manager.isEnabled()) {
            player.sendMessage(I18n.component(player, NamedTextColor.RED, "conquerpass.disabled"));
            return;
        }
        manager.ensureSeason(player);
        int maxP = manager.maxPage();
        int p = Math.max(0, Math.min(page, maxP));
        Inventory inv = ConquerPassHolder.create(title(player), p);
        ConquerPassHolder holder = ConquerPassHolder.from(inv);
        if (holder != null) {
            holder.setPage(p);
        }
        fill(inv, player);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.4f, 1.05f);
    }

    private Component title(Player player) {
        return Component.text(I18n.string(player, "conquerpass.gui_title"), NamedTextColor.GOLD, TextDecoration.BOLD);
    }

    private void fill(Inventory inv, Player player) {
        ConquerPassHolder holder = ConquerPassHolder.from(inv);
        int page = holder != null ? holder.page() : 0;
        ItemStack border = BoutiqueUi.pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack inner = BoutiqueUi.pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 0; s < inv.getSize(); s++) {
            inv.setItem(s, BoutiqueUi.isBorderSlot(s) ? border.clone() : inner.clone());
        }

        UUID id = player.getUniqueId();
        int xp = manager.getXp(id);
        int xpt = manager.xpPerTier();
        int unlocked = manager.unlockedTierLevel(id);
        int maxT = manager.maxTier();
        int needNext = unlocked < maxT ? (unlocked + 1) * xpt - xp : 0;

        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.empty());
        infoLore.add(Component.text(I18n.string(player, "conquerpass.season_label", manager.seasonId()), NamedTextColor.GRAY));
        infoLore.add(Component.text(I18n.string(player, "conquerpass.xp_line", xp, unlocked, maxT), NamedTextColor.YELLOW));
        if (needNext > 0) {
            infoLore.add(Component.text(I18n.string(player, "conquerpass.xp_to_next", needNext), NamedTextColor.DARK_AQUA));
        } else if (unlocked >= maxT) {
            infoLore.add(Component.text(I18n.string(player, "conquerpass.track_complete"), NamedTextColor.GREEN));
        }
        infoLore.add(Component.empty());
        infoLore.add(Component.text(I18n.string(player, "conquerpass.hint_xp"), NamedTextColor.DARK_GRAY));

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta im = info.getItemMeta();
        im.displayName(I18n.title(player, NamedTextColor.AQUA, "conquerpass.info_title"));
        im.lore(infoLore);
        info.setItemMeta(im);
        inv.setItem(SLOT_INFO, info);

        int ps = Math.min(7, manager.pageSize());
        int base = page * ps;
        for (int i = 0; i < FREE_SLOTS.length; i++) {
            if (i >= ps) {
                inv.setItem(FREE_SLOTS[i], border.clone());
                inv.setItem(PREM_SLOTS[i], border.clone());
                continue;
            }
            int tier = base + i + 1;
            inv.setItem(FREE_SLOTS[i], tierStack(player, tier, true));
            inv.setItem(PREM_SLOTS[i], tierStack(player, tier, false));
        }

        ItemStack prev = navArrow(player, Material.ARROW, "conquerpass.page_prev", page > 0);
        inv.setItem(SLOT_PREV, prev);

        ItemStack next = navArrow(player, Material.ARROW, "conquerpass.page_next", page < manager.maxPage());
        inv.setItem(SLOT_NEXT, next);

        fillPremiumBuySlot(inv, player);
    }

    private ItemStack navArrow(Player player, Material mat, String nameKey, boolean active) {
        ItemStack st = new ItemStack(active ? mat : Material.GRAY_DYE);
        ItemMeta m = st.getItemMeta();
        m.displayName(Component.text(I18n.string(player, nameKey),
                active ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(I18n.string(player, active ? "conquerpass.nav_active" : "conquerpass.nav_inactive"),
                NamedTextColor.GRAY));
        m.lore(lore);
        st.setItemMeta(m);
        return st;
    }

    private void fillPremiumBuySlot(Inventory inv, Player player) {
        int cost = manager.premiumUnlockPoints();
        if (manager.hasPremiumAccess(player)) {
            ItemStack ok = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta m = ok.getItemMeta();
            m.displayName(I18n.title(player, NamedTextColor.GOLD, "conquerpass.premium_owned_title"));
            m.lore(List.of(Component.text(I18n.string(player, "conquerpass.premium_owned_lore"), NamedTextColor.GRAY)));
            ok.setItemMeta(m);
            inv.setItem(SLOT_PREMIUM_BUY, ok);
            return;
        }
        if (cost <= 0) {
            ItemStack h = new ItemStack(Material.BOOK);
            ItemMeta m = h.getItemMeta();
            m.displayName(I18n.title(player, NamedTextColor.LIGHT_PURPLE, "conquerpass.premium_perm_title"));
            m.lore(List.of(
                    Component.text(I18n.string(player, "conquerpass.premium_perm_lore"), NamedTextColor.GRAY)
            ));
            h.setItemMeta(m);
            inv.setItem(SLOT_PREMIUM_BUY, h);
            return;
        }
        ItemStack buy = new ItemStack(Material.GOLD_INGOT);
        ItemMeta m = buy.getItemMeta();
        m.displayName(I18n.title(player, NamedTextColor.GOLD, "conquerpass.premium_buy_title"));
        m.lore(List.of(
                Component.text(I18n.string(player, "conquerpass.premium_buy_cost", cost), NamedTextColor.YELLOW),
                Component.empty(),
                Component.text(I18n.string(player, "conquerpass.premium_buy_click"), NamedTextColor.GREEN)
        ));
        buy.setItemMeta(m);
        inv.setItem(SLOT_PREMIUM_BUY, buy);
    }

    private ItemStack tierStack(Player player, int tier, boolean freeTrack) {
        UUID id = player.getUniqueId();
        if (tier > manager.maxTier()) {
            return BoutiqueUi.pane(Material.BLACK_STAINED_GLASS_PANE);
        }
        int reward = freeTrack ? manager.freeRewardPoints(tier) : manager.premiumRewardPoints(tier);
        boolean reached = manager.tierReached(id, tier);
        boolean claimed = freeTrack ? manager.isFreeClaimed(id, tier) : manager.isPremiumClaimed(id, tier);
        boolean hasPremium = manager.hasPremiumAccess(player);

        String trackKey = freeTrack ? "conquerpass.track_free" : "conquerpass.track_premium";
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(I18n.string(player, "conquerpass.tier_label", tier), NamedTextColor.WHITE, TextDecoration.BOLD));
        lore.add(Component.empty());
        if (!freeTrack && !hasPremium) {
            lore.add(Component.text(I18n.string(player, "conquerpass.locked_premium"), NamedTextColor.RED));
            lore.add(Component.empty());
        }
        if (reward > 0) {
            lore.add(Component.text(I18n.string(player, "conquerpass.reward_points", reward), NamedTextColor.GOLD));
        } else {
            lore.add(Component.text(I18n.string(player, "conquerpass.reward_none"), NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.empty());

        Material mat;
        NamedTextColor nameColor;
        if (!reached) {
            mat = Material.BARRIER;
            nameColor = NamedTextColor.DARK_RED;
            lore.add(Component.text(I18n.string(player, "conquerpass.state_locked"), NamedTextColor.RED));
        } else if (claimed) {
            mat = Material.GRAY_STAINED_GLASS_PANE;
            nameColor = NamedTextColor.DARK_GRAY;
            lore.add(Component.text(I18n.string(player, "conquerpass.state_claimed"), NamedTextColor.GREEN));
        } else if (!freeTrack && !hasPremium) {
            mat = Material.IRON_BARS;
            nameColor = NamedTextColor.GRAY;
            lore.add(Component.text(I18n.string(player, "conquerpass.state_need_pass"), NamedTextColor.RED));
        } else {
            mat = freeTrack ? Material.LIME_CONCRETE : Material.GOLD_BLOCK;
            nameColor = freeTrack ? NamedTextColor.GREEN : NamedTextColor.GOLD;
            lore.add(Component.text(I18n.string(player, "conquerpass.state_claim"), NamedTextColor.YELLOW));
        }

        ItemStack st = new ItemStack(mat);
        ItemMeta m = st.getItemMeta();
        m.displayName(Component.text(I18n.string(player, trackKey), nameColor, TextDecoration.BOLD));
        m.lore(lore);
        st.setItemMeta(m);
        return st;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && p.isOnline()) {
                manager.ensureSeason(p);
            }
        }, 3L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!manager.isEnabled() || manager.xpPlayerKill() <= 0) {
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
        if (!countsAsClanKill(killer, victim)) {
            return;
        }
        manager.addXp(killer, manager.xpPlayerKill());
    }

    private boolean countsAsClanKill(Player killer, Player victim) {
        String kc = clanManager.getPlayerClan(killer.getUniqueId());
        String vc = clanManager.getPlayerClan(victim.getUniqueId());
        if (kc != null && kc.equalsIgnoreCase(vc)) {
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (!manager.isEnabled() || manager.xpMobKill() <= 0) {
            return;
        }
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        Player killer = resolveKiller(event.getEntity());
        if (killer == null) {
            return;
        }
        manager.addXp(killer, manager.xpMobKill());
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
        if (ConquerPassHolder.from(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        ConquerPassHolder holder = ConquerPassHolder.from(event.getView().getTopInventory());
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
        int slot = event.getSlot();
        int page = holder.page();

        if (slot == SLOT_PREV) {
            if (page > 0) {
                holder.setPage(page - 1);
                fill(event.getView().getTopInventory(), player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.1f);
            } else {
                fail(player);
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            if (page < manager.maxPage()) {
                holder.setPage(page + 1);
                fill(event.getView().getTopInventory(), player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.15f);
            } else {
                fail(player);
            }
            return;
        }
        if (slot == SLOT_PREMIUM_BUY) {
            if (manager.hasPremiumAccess(player)) {
                fail(player);
                return;
            }
            if (manager.premiumUnlockPoints() <= 0) {
                player.sendMessage(I18n.component(player, NamedTextColor.GRAY, "conquerpass.premium_perm_hint"));
                fail(player);
                return;
            }
            String err = manager.tryBuyPremium(player);
            if (err != null) {
                player.sendMessage(I18n.component(player, NamedTextColor.RED, err));
                fail(player);
                return;
            }
            player.sendMessage(I18n.component(player, NamedTextColor.GOLD, "conquerpass.premium_unlocked"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 1.2f);
            fill(event.getView().getTopInventory(), player);
            return;
        }

        int ps = Math.min(7, manager.pageSize());
        int tier = -1;
        boolean free = false;
        for (int i = 0; i < ps; i++) {
            if (slot == FREE_SLOTS[i]) {
                tier = page * ps + i + 1;
                free = true;
                break;
            }
            if (slot == PREM_SLOTS[i]) {
                tier = page * ps + i + 1;
                free = false;
                break;
            }
        }
        if (tier < 0) {
            return;
        }

        String failKey = free ? manager.tryClaimFree(player, tier) : manager.tryClaimPremium(player, tier);
        if (failKey != null) {
            player.sendMessage(I18n.component(player, NamedTextColor.RED, failKey));
            fail(player);
            return;
        }
        player.sendMessage(I18n.component(player, NamedTextColor.GOLD, "conquerpass.reward_granted", tier));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55f, 1.15f);
        fill(event.getView().getTopInventory(), player);
    }

    private void fail(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.45f, 0.95f);
    }
}
