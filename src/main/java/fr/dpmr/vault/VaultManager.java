package fr.dpmr.vault;

import fr.dpmr.i18n.GameLocale;
import fr.dpmr.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bloc configurable : stockage personnel (2 pages x 54 cases). Clic = page 1, sneak + clic = page 2.
 */
public final class VaultManager implements Listener {

    private final JavaPlugin plugin;
    private final VaultStore store;

    private boolean enabled = true;
    private Material blockMaterial = Material.VAULT;
    private String usePermission = "dpmr.vault.use";

    public VaultManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.store = new VaultStore(plugin);
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        enabled = cfg.getBoolean("vault.enabled", true);
        usePermission = cfg.getString("vault.use-permission", "dpmr.vault.use");
        if (usePermission == null) {
            usePermission = "";
        }
        String matName = cfg.getString("vault.block-material", "VAULT");
        Material m = Material.matchMaterial(matName != null ? matName : "VAULT");
        if (m == null || !m.isBlock()) {
            plugin.getLogger().warning("vault.block-material invalide: " + matName + " — utilisation de VAULT.");
            m = Material.VAULT;
        }
        blockMaterial = m;
    }

    public VaultStore store() {
        return store;
    }

    public void save() {
        store.save();
    }

    private boolean mayUse(Player player) {
        if (!enabled) {
            player.sendMessage(I18n.component(player, NamedTextColor.RED, "vault.disabled"));
            return false;
        }
        if (!usePermission.isEmpty() && !player.hasPermission(usePermission)) {
            player.sendMessage(I18n.component(player, NamedTextColor.RED, "vault.no_permission"));
            return false;
        }
        return true;
    }

    private Component title(Player player, int page) {
        GameLocale loc = I18n.locale(player);
        int humanPage = page + 1;
        String raw = I18n.string(loc, "vault.title", humanPage, VaultHolder.PAGE_COUNT);
        return Component.text(raw, NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
    }

    public void open(Player player, int page) {
        if (!mayUse(player)) {
            return;
        }
        page = Math.max(0, Math.min(VaultHolder.PAGE_COUNT - 1, page));
        Inventory inv = VaultHolder.create(title(player, page), player.getUniqueId(), page);
        inv.setContents(store.copyPageForGui(player.getUniqueId(), page));
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.35f, 1.2f);
        player.sendActionBar(I18n.component(player, NamedTextColor.GRAY, "vault.hint_pages"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != blockMaterial) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!mayUse(player)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int page = player.isSneaking() ? 1 : 0;
        open(player, page);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        VaultHolder holder = VaultHolder.from(event.getInventory());
        if (holder == null) {
            return;
        }
        store.setPage(holder.owner(), holder.page(), event.getInventory().getContents());
        save();
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        VaultHolder holder = VaultHolder.from(top);
        if (holder == null || !holder.owner().equals(event.getPlayer().getUniqueId())) {
            return;
        }
        store.setPage(holder.owner(), holder.page(), top.getContents());
        save();
    }
}
