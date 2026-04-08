package fr.dpmr.profile;

import fr.dpmr.data.PointsManager;
import fr.dpmr.mastery.MasteryTier;
import fr.dpmr.trophy.TrophyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ProfileGui implements Listener {

    private static final String TITLE = "Profil & Upgrades";
    private final PointsManager pointsManager;
    private final PlayerProfileManager profileManager;
    private final TrophyManager trophyManager;

    public ProfileGui(PointsManager pointsManager, PlayerProfileManager profileManager, TrophyManager trophyManager) {
        this.pointsManager = pointsManager;
        this.profileManager = profileManager;
        this.trophyManager = trophyManager;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        inv.setItem(4, infoItem(player));
        inv.setItem(10, upgradeItem(player, ProfileUpgradeType.VITALITY, " +2 HP max / niveau"));
        inv.setItem(11, upgradeItem(player, ProfileUpgradeType.ARMOR, " -5% degats recus / niveau"));
        inv.setItem(13, upgradeItem(player, ProfileUpgradeType.REGEN, " Regeneration passive hors combat strict non requis"));
        inv.setItem(15, upgradeItem(player, ProfileUpgradeType.DAMAGE, " +4% degats infliges / niveau"));
        inv.setItem(16, upgradeItem(player, ProfileUpgradeType.ECONOMY, " +6% points sur gains plugin"));
        player.openInventory(inv);
    }

    private ItemStack infoItem(Player player) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Ton profil", NamedTextColor.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Points: " + pointsManager.getPoints(player.getUniqueId()), NamedTextColor.GOLD));
        lore.add(Component.text("Kills: " + pointsManager.getKills(player.getUniqueId()), NamedTextColor.RED));
        MasteryTier m = MasteryTier.fromProgress(
                pointsManager.getKills(player.getUniqueId()),
                pointsManager.getPoints(player.getUniqueId()));
        lore.add(Component.text("Mastery: " + m.chatTitle(), NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.text(
                "Trophies: " + trophyManager.unlockedCount(player.getUniqueId()) + "/" + trophyManager.totalDefined()
                        + " (/trophies)",
                NamedTextColor.GREEN));
        lore.add(Component.text("Points depenses: " + profileManager.totalSpent(player.getUniqueId()), NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack upgradeItem(Player player, ProfileUpgradeType type, String bonusText) {
        int lv = profileManager.level(player.getUniqueId(), type);
        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.display(), NamedTextColor.GREEN));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Niveau: " + lv + "/" + type.maxLevel(), NamedTextColor.YELLOW));
        lore.add(Component.text("Bonus:" + bonusText, NamedTextColor.GRAY));
        if (lv >= type.maxLevel()) {
            lore.add(Component.text("MAX atteint", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("Cout prochain niveau: " + type.nextCost(lv) + " points", NamedTextColor.GOLD));
            lore.add(Component.text("Clic pour ameliorer", NamedTextColor.AQUA));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        ProfileUpgradeType type = switch (slot) {
            case 10 -> ProfileUpgradeType.VITALITY;
            case 11 -> ProfileUpgradeType.ARMOR;
            case 13 -> ProfileUpgradeType.REGEN;
            case 15 -> ProfileUpgradeType.DAMAGE;
            case 16 -> ProfileUpgradeType.ECONOMY;
            default -> null;
        };
        if (type == null) {
            return;
        }
        if (!profileManager.upgrade(player, type)) {
            player.sendMessage(Component.text("Upgrade impossible (points insuffisants ou niveau max).", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Upgrade " + type.display() + " achete.", NamedTextColor.GREEN));
        open(player);
    }
}

