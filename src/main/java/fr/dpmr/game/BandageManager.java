package fr.dpmr.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BandageManager implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey keyBandageLegacy;
    private final NamespacedKey keyConsumable;
    private final Map<UUID, Long> lastHealConsumableMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastShieldConsumableMs = new ConcurrentHashMap<>();

    public BandageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyBandageLegacy = new NamespacedKey(plugin, "dpmr_bandage");
        this.keyConsumable = new NamespacedKey(plugin, "dpmr_consumable");
    }

    public ItemStack createBandage(int amount) {
        return createConsumable(DpmrConsumable.BANDAGE_MEDIUM, amount);
    }

    public ItemStack createConsumable(DpmrConsumable type, int amount) {
        FileConfiguration cfg = plugin.getConfig();
        int n = Math.max(1, amount);
        ItemStack item = new ItemStack(type.material(), n);
        String ck = type.configKey();
        if (type.material() == Material.POTION) {
            PotionMeta pMeta = (PotionMeta) item.getItemMeta();
            pMeta.setBasePotionType(PotionType.WATER);
            applyMetaCommon(pMeta, type, cfg, ck);
            item.setItemMeta(pMeta);
        } else {
            ItemMeta meta = item.getItemMeta();
            applyMetaCommon(meta, type, cfg, ck);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyMetaCommon(ItemMeta meta, DpmrConsumable type, FileConfiguration cfg, String ck) {
        meta.displayName(displayName(type));
        meta.lore(lore(type));
        int cmd = cfg.getInt("consumables." + ck + ".custom-model-data", defaultCmd(type));
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        meta.getPersistentDataContainer().set(keyConsumable, PersistentDataType.STRING, type.name());
    }

    /** Met a jour nom / lore / CMD des bandages & potions DPMR deja en inventaire. */
    public void refreshConsumableStack(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        DpmrConsumable type = getConsumableType(item);
        if (type == null) {
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        String ck = type.configKey();
        if (type.material() == Material.POTION && item.getItemMeta() instanceof PotionMeta pMeta) {
            applyMetaCommon(pMeta, type, cfg, ck);
            item.setItemMeta(pMeta);
        } else if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            applyMetaCommon(meta, type, cfg, ck);
            item.setItemMeta(meta);
        }
    }

    public void refreshDpmrConsumablesInInventory(Player player) {
        if (player == null) {
            return;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            refreshConsumableStack(stack);
        }
        refreshConsumableStack(player.getInventory().getItemInOffHand());
    }

    @EventHandler
    public void onJoinRefreshConsumableModels(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refreshDpmrConsumablesInInventory(p), 28L);
    }

    private static int defaultCmd(DpmrConsumable type) {
        return switch (type) {
            case BANDAGE_SMALL -> 3002;
            case BANDAGE_MEDIUM -> 3001;
            case BANDAGE_LARGE -> 3003;
            case MEDIKIT -> 3004;
            case SHIELD_POTION_SMALL -> 3011;
            case SHIELD_POTION_MEDIUM -> 3012;
            case SHIELD_POTION_LARGE -> 3013;
        };
    }

    private static Component displayName(DpmrConsumable type) {
        return switch (type) {
            case BANDAGE_SMALL -> Component.text("Bandage (petit)", NamedTextColor.RED);
            case BANDAGE_MEDIUM -> Component.text("Bandage (moyen)", NamedTextColor.RED);
            case BANDAGE_LARGE -> Component.text("Bandage (grand)", NamedTextColor.DARK_RED);
            case MEDIKIT -> Component.text("Medikit", NamedTextColor.GOLD);
            case SHIELD_POTION_SMALL -> Component.text("Potion de bouclier (petite)", NamedTextColor.AQUA);
            case SHIELD_POTION_MEDIUM -> Component.text("Potion de bouclier (moyenne)", NamedTextColor.DARK_AQUA);
            case SHIELD_POTION_LARGE -> Component.text("Potion de bouclier (grande)", NamedTextColor.BLUE);
        };
    }

    private static List<Component> lore(DpmrConsumable type) {
        if (type.heal()) {
            return List.of(Component.text("Right-click to heal", NamedTextColor.GRAY));
        }
        return List.of(Component.text("Right-click for absorption hearts", NamedTextColor.GRAY));
    }

    public DpmrConsumable getConsumableType(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(keyConsumable, PersistentDataType.STRING);
        if (id != null) {
            try {
                return DpmrConsumable.valueOf(id);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (item.getItemMeta().getPersistentDataContainer().has(keyBandageLegacy, PersistentDataType.BYTE)) {
            return DpmrConsumable.BANDAGE_MEDIUM;
        }
        return null;
    }

    public boolean isBandage(ItemStack item) {
        return getConsumableType(item) != null;
    }

    /**
     * Tirage pondéré pour le loot des coffres (soins uniquement).
     */
    public DpmrConsumable rollLootHealConsumable() {
        FileConfiguration cfg = plugin.getConfig();
        DpmrConsumable[] types = {
                DpmrConsumable.BANDAGE_SMALL,
                DpmrConsumable.BANDAGE_MEDIUM,
                DpmrConsumable.BANDAGE_LARGE,
                DpmrConsumable.MEDIKIT
        };
        int[] w = new int[types.length];
        int total = 0;
        for (int i = 0; i < types.length; i++) {
            int def = switch (types[i]) {
                case BANDAGE_SMALL -> 28;
                case BANDAGE_MEDIUM -> 40;
                case BANDAGE_LARGE -> 22;
                case MEDIKIT -> 10;
                default -> 0;
            };
            w[i] = Math.max(0, cfg.getInt("loot.bandage-type-weights." + types[i].configKey(), def));
            total += w[i];
        }
        if (total <= 0) {
            return DpmrConsumable.BANDAGE_MEDIUM;
        }
        int r = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        for (int i = 0; i < types.length; i++) {
            acc += w[i];
            if (r < acc) {
                return types[i];
            }
        }
        return types[types.length - 1];
    }

    public DpmrConsumable rollLootShieldPotion() {
        FileConfiguration cfg = plugin.getConfig();
        DpmrConsumable[] types = {
                DpmrConsumable.SHIELD_POTION_SMALL,
                DpmrConsumable.SHIELD_POTION_MEDIUM,
                DpmrConsumable.SHIELD_POTION_LARGE
        };
        int[] w = new int[types.length];
        int total = 0;
        for (int i = 0; i < types.length; i++) {
            int def = switch (types[i]) {
                case SHIELD_POTION_SMALL -> 50;
                case SHIELD_POTION_MEDIUM -> 35;
                case SHIELD_POTION_LARGE -> 15;
                default -> 0;
            };
            w[i] = Math.max(0, cfg.getInt("loot.shield-potion-type-weights." + types[i].configKey(), def));
            total += w[i];
        }
        if (total <= 0) {
            return DpmrConsumable.SHIELD_POTION_SMALL;
        }
        int r = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        for (int i = 0; i < types.length; i++) {
            acc += w[i];
            if (r < acc) {
                return types[i];
            }
        }
        return types[types.length - 1];
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack hand = event.getItem();
        DpmrConsumable type = getConsumableType(hand);
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        FileConfiguration cfg = plugin.getConfig();
        if (type.heal()) {
            applyHeal(player, hand, type, cfg);
        } else {
            applyShield(player, hand, type, cfg);
        }
    }

    private static double defaultHealHalfHearts(DpmrConsumable type, FileConfiguration cfg) {
        return switch (type) {
            case BANDAGE_SMALL -> 2.0;
            case BANDAGE_MEDIUM -> cfg.getDouble("bandage.heal-half-hearts", 6.0);
            case BANDAGE_LARGE -> 10.0;
            case MEDIKIT -> 16.0;
            default -> 6.0;
        };
    }

    private void applyHeal(Player player, ItemStack hand, DpmrConsumable type, FileConfiguration cfg) {
        int cooldownSec = cfg.getInt("consumables.heal-cooldown-seconds", cfg.getInt("bandage.cooldown-seconds", 5));
        long cooldownMs = Math.max(0, cooldownSec) * 1000L;
        if (cooldownMs > 0) {
            Long last = lastHealConsumableMs.get(player.getUniqueId());
            if (last != null) {
                long remain = cooldownMs - (System.currentTimeMillis() - last);
                if (remain > 0) {
                    int secLeft = (int) Math.ceil(remain / 1000.0);
                    player.sendActionBar(Component.text(
                            "Soin en rechargement (" + secLeft + "s)",
                            NamedTextColor.GRAY));
                    return;
                }
            }
        }
        String ck = type.configKey();
        double heal = cfg.getDouble("consumables." + ck + ".heal-half-hearts", defaultHealHalfHearts(type, cfg));
        var maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        double next = Math.min(max, player.getHealth() + heal);
        if (next <= player.getHealth()) {
            player.sendActionBar(Component.text("You are already too healthy to heal.", NamedTextColor.GRAY));
            return;
        }
        player.setHealth(next);
        hand.setAmount(hand.getAmount() - 1);
        if (cooldownMs > 0) {
            lastHealConsumableMs.put(player.getUniqueId(), System.currentTimeMillis());
        }
        player.playSound(player.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 1f, 1.2f);
        player.sendActionBar(Component.text("Soigne +" + (int) (heal / 2) + " coeurs", NamedTextColor.GREEN));
    }

    private void applyShield(Player player, ItemStack hand, DpmrConsumable type, FileConfiguration cfg) {
        int cooldownSec = cfg.getInt("consumables.shield-cooldown-seconds", 8);
        long cooldownMs = Math.max(0, cooldownSec) * 1000L;
        if (cooldownMs > 0) {
            Long last = lastShieldConsumableMs.get(player.getUniqueId());
            if (last != null) {
                long remain = cooldownMs - (System.currentTimeMillis() - last);
                if (remain > 0) {
                    int secLeft = (int) Math.ceil(remain / 1000.0);
                    player.sendActionBar(Component.text(
                            "Potion de bouclier en rechargement (" + secLeft + "s)",
                            NamedTextColor.GRAY));
                    return;
                }
            }
        }
        String ck = type.configKey();
        int amplifier = cfg.getInt("consumables." + ck + ".absorption-amplifier", switch (type) {
            case SHIELD_POTION_SMALL -> 0;
            case SHIELD_POTION_MEDIUM -> 1;
            case SHIELD_POTION_LARGE -> 2;
            default -> 0;
        });
        int durationSec = cfg.getInt("consumables." + ck + ".duration-seconds", switch (type) {
            case SHIELD_POTION_SMALL -> 45;
            case SHIELD_POTION_MEDIUM -> 75;
            case SHIELD_POTION_LARGE -> 120;
            default -> 60;
        });
        int ticks = Math.max(1, durationSec) * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, amplifier, false, true, true), true);
        hand.setAmount(hand.getAmount() - 1);
        if (cooldownMs > 0) {
            lastShieldConsumableMs.put(player.getUniqueId(), System.currentTimeMillis());
        }
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.85f, 1.35f);
        int hearts = 2 * (amplifier + 1);
        player.sendActionBar(Component.text("Absorption +" + hearts + " coeurs (" + durationSec + "s)", NamedTextColor.AQUA));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastHealConsumableMs.remove(id);
        lastShieldConsumableMs.remove(id);
    }
}
