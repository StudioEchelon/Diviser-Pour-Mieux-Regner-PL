package fr.dpmr.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Items « carte de poker » : clic droit pour appliquer le même buff que les blocs power-up.
 */
public final class PokerCardManager implements Listener {

    private final JavaPlugin plugin;
    private final PowerupBlockManager powerupBlockManager;
    private final NamespacedKey keyCard;

    private boolean enabled = true;
    private int cooldownSeconds = 3;
    private Material itemMaterial = Material.PAPER;
    private int customModelData = 3100;

    private final Map<UUID, Long> lastUseMs = new ConcurrentHashMap<>();

    public PokerCardManager(JavaPlugin plugin, PowerupBlockManager powerupBlockManager) {
        this.plugin = plugin;
        this.powerupBlockManager = powerupBlockManager;
        this.keyCard = new NamespacedKey(plugin, "dpmr_poker_card");
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        enabled = cfg.getBoolean("poker-cards.enabled", true);
        cooldownSeconds = Math.max(0, cfg.getInt("poker-cards.use-cooldown-seconds", 3));
        String matName = cfg.getString("poker-cards.item-material", "PAPER");
        Material m = Material.matchMaterial(matName != null ? matName : "PAPER");
        itemMaterial = m != null ? m : Material.PAPER;
        customModelData = Math.max(0, cfg.getInt("poker-cards.custom-model-data", 3100));
    }

    public ItemStack createCard(PokerCard card, int amount) {
        if (card == null) {
            return new ItemStack(Material.AIR);
        }
        int n = Math.max(1, amount);
        ItemStack item = new ItemStack(itemMaterial, n);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName(card));
        meta.lore(List.of(
                Component.text("Clic droit : activer le power-up", NamedTextColor.GRAY),
                Component.text(kindHint(card.powerupKind()), NamedTextColor.DARK_GRAY)));
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        meta.getPersistentDataContainer().set(keyCard, PersistentDataType.STRING, card.storageKey());
        item.setItemMeta(meta);
        return item;
    }

    private static String kindHint(PowerupBlockManager.Kind k) {
        return switch (k) {
            case RAPID_FIRE -> "Tir rapide";
            case INVULNERABILITY -> "Invulnérabilité";
            case KILL_COINS -> "Prime / points de kill";
            case BULLET_SHIELD -> "Bouclier anti-balles";
            case STEALTH -> "Furtivité";
        };
    }

    private static Component displayName(PokerCard card) {
        if (card.isJoker()) {
            String sub = "JOKER_RED".equals(card.storageKey()) ? "rouge" : "noir";
            return Component.text("Joker (" + sub + ") — furtivité", NamedTextColor.LIGHT_PURPLE);
        }
        PokerSuit s = card.suit();
        PokerRank r = card.rank();
        NamedTextColor color = s == PokerSuit.HEARTS || s == PokerSuit.DIAMONDS
                ? NamedTextColor.RED
                : NamedTextColor.DARK_GRAY;
        return Component.text(r.frShort() + " de " + s.frName() + " " + s.symbol(), color);
    }

    public PokerCard getCardType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(keyCard, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        return PokerCard.fromStorageKey(id);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (!enabled) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        PokerCard card = getCardType(stack);
        if (card == null) {
            return;
        }
        event.setCancelled(true);

        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            Long last = lastUseMs.get(player.getUniqueId());
            if (last != null) {
                long remain = cooldownSeconds * 1000L - (now - last);
                if (remain > 0) {
                    int secLeft = (int) Math.ceil(remain / 1000.0);
                    player.sendActionBar(Component.text(
                            "Carte en recharge (" + secLeft + "s)", NamedTextColor.GRAY));
                    return;
                }
            }
        }

        PowerupBlockManager.Kind kind = card.powerupKind();
        double mult = card.durationMultiplier();
        boolean ok = powerupBlockManager.applyPowerupFromItem(player, kind, mult);
        if (!ok) {
            player.sendActionBar(Component.text("Power-up indisponible.", NamedTextColor.RED));
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
        lastUseMs.put(player.getUniqueId(), System.currentTimeMillis());
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.5f);
    }

    public void clearCooldown(UUID id) {
        lastUseMs.remove(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUseMs.remove(event.getPlayer().getUniqueId());
    }
}
