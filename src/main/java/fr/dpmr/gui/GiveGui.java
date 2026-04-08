package fr.dpmr.gui;

import fr.dpmr.game.BandageManager;
import fr.dpmr.game.DpmrConsumable;
import fr.dpmr.game.PokerCard;
import fr.dpmr.game.PokerCardManager;
import fr.dpmr.game.PokerRank;
import fr.dpmr.game.PokerSuit;
import fr.dpmr.game.LaunchpadManager;
import fr.dpmr.game.LaunchpadStyle;
import fr.dpmr.game.RadarManager;
import fr.dpmr.game.WallVisionManager;
import fr.dpmr.game.WeaponManager;
import fr.dpmr.game.WeaponProfile;
import fr.dpmr.game.WeaponRarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GiveGui implements Listener {

    public static final Component TITLE_MAIN = Component.text("DPMR — Gives admin", NamedTextColor.LIGHT_PURPLE);
    public static final Component TITLE_WEAPONS = Component.text("DPMR — Armes", NamedTextColor.GOLD);
    public static final Component TITLE_BY_RARITY = Component.text("DPMR — Par rarete", NamedTextColor.AQUA);
    public static final Component TITLE_LAUNCHPADS = Component.text("DPMR — Launchpads", NamedTextColor.GREEN);
    public static final Component TITLE_CONSUMABLES = Component.text("DPMR — Consommables", NamedTextColor.RED);

    private static final int SLOT_WEAPONS_MENU = 11;
    private static final int SLOT_RARITY_MENU = 13;
    private static final int SLOT_CONSUMABLES_MENU = 15;
    private static final int SLOT_WALL_VISION = 10;
    private static final int SLOT_INFRARED = 12;
    private static final int SLOT_RADAR = 16;
    private static final int SLOT_LAUNCHPAD_MENU = 17;
    private static final int SLOT_CLOSE_MAIN = 22;

    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLOSE_GRID = 49;

    private final WeaponManager weaponManager;
    private final BandageManager bandageManager;
    private final RadarManager radarManager;
    private final WallVisionManager wallVisionManager;
    private final LaunchpadManager launchpadManager;
    private final PokerCardManager pokerCardManager;

    public GiveGui(WeaponManager weaponManager, BandageManager bandageManager, RadarManager radarManager,
                   WallVisionManager wallVisionManager, LaunchpadManager launchpadManager,
                   PokerCardManager pokerCardManager) {
        this.weaponManager = weaponManager;
        this.bandageManager = bandageManager;
        this.radarManager = radarManager;
        this.wallVisionManager = wallVisionManager;
        this.launchpadManager = launchpadManager;
        this.pokerCardManager = pokerCardManager;
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MAIN);
        inv.setItem(SLOT_WEAPONS_MENU, button(Material.CROSSBOW, "Toutes les armes", NamedTextColor.GOLD,
                List.of(Component.text("Click: open list", NamedTextColor.GRAY))));
        inv.setItem(SLOT_WALL_VISION, button(Material.SPYGLASS, "Lunettes de scan x1", NamedTextColor.DARK_PURPLE,
                List.of(Component.text("Clic droit : joueurs en glow violet ~15 s", NamedTextColor.GRAY))));
        inv.setItem(SLOT_RARITY_MENU, button(Material.NETHER_STAR, "Filtrer par rarete", NamedTextColor.AQUA,
                List.of(Component.text("Commun → Legendaire", NamedTextColor.GRAY))));
        inv.setItem(SLOT_CONSUMABLES_MENU, button(Material.PAPER, "Consommables (soins & bouclier)", NamedTextColor.RED,
                List.of(Component.text("Click: bandages, medikit, potions, cartes poker", NamedTextColor.GRAY))));
        inv.setItem(SLOT_RADAR, button(Material.COMPASS, "Player radar x1", NamedTextColor.AQUA,
                List.of(Component.text("Left-click radar: reload", NamedTextColor.GRAY))));
        inv.setItem(SLOT_LAUNCHPAD_MENU, button(Material.SLIME_BLOCK, "Launchpads", NamedTextColor.GREEN,
                List.of(Component.text("Click: pick a style (x8)", NamedTextColor.GRAY))));
        inv.setItem(SLOT_CLOSE_MAIN, button(Material.BARRIER, "Fermer", NamedTextColor.RED, List.of()));
        player.openInventory(inv);
    }

    public void openConsumables(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CONSUMABLES);
        inv.setItem(10, button(Material.PAPER, "Bandage (petit) x8", NamedTextColor.RED,
                List.of(Component.text("Soin faible", NamedTextColor.GRAY))));
        inv.setItem(11, button(Material.PAPER, "Bandage (moyen) x8", NamedTextColor.RED,
                List.of(Component.text("Soin standard", NamedTextColor.GRAY))));
        inv.setItem(12, button(Material.PAPER, "Bandage (grand) x8", NamedTextColor.DARK_RED,
                List.of(Component.text("Soin fort", NamedTextColor.GRAY))));
        inv.setItem(13, button(Material.PAPER, "Medikit x4", NamedTextColor.GOLD,
                List.of(Component.text("Soin maximal", NamedTextColor.GRAY))));
        inv.setItem(14, button(Material.POTION, "Potion bouclier (petite) x4", NamedTextColor.AQUA,
                List.of(Component.text("Absorption courte", NamedTextColor.GRAY))));
        inv.setItem(15, button(Material.POTION, "Potion bouclier (moyenne) x4", NamedTextColor.DARK_AQUA,
                List.of(Component.text("Absorption moyenne", NamedTextColor.GRAY))));
        inv.setItem(16, button(Material.POTION, "Potion bouclier (grande) x2", NamedTextColor.BLUE,
                List.of(Component.text("Absorption longue", NamedTextColor.GRAY))));
        inv.setItem(19, button(Material.PAPER, "Cartes poker aleatoires x8", NamedTextColor.GOLD,
                List.of(Component.text("52 couleurs + 2 jokers (power-ups)", NamedTextColor.GRAY))));
        inv.setItem(20, button(Material.PAPER, "As de coeur x1 (tir rapide)", NamedTextColor.RED,
                List.of(Component.text("Demo", NamedTextColor.DARK_GRAY))));
        inv.setItem(21, button(Material.PAPER, "Joker rouge x1 (furtivite)", NamedTextColor.LIGHT_PURPLE,
                List.of(Component.text("Demo", NamedTextColor.DARK_GRAY))));
        inv.setItem(18, button(Material.ARROW, "Retour", NamedTextColor.YELLOW, List.of()));
        inv.setItem(SLOT_CLOSE_MAIN, button(Material.BARRIER, "Fermer", NamedTextColor.RED, List.of()));
        player.openInventory(inv);
    }

    public void openAllWeapons(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_WEAPONS);
        WeaponProfile[] sorted = Arrays.stream(WeaponProfile.values())
                .sorted(Comparator.comparing(WeaponProfile::rarity).thenComparing(Enum::name))
                .toArray(WeaponProfile[]::new);
        int slot = 0;
        for (WeaponProfile w : sorted) {
            if (slot >= 45) {
                break;
            }
            ItemStack gun = weaponManager.createWeaponItem(w.name(), player);
            if (gun != null) {
                inv.setItem(slot++, gun);
            }
        }
        inv.setItem(SLOT_BACK, button(Material.ARROW, "Retour", NamedTextColor.YELLOW, List.of()));
        inv.setItem(SLOT_CLOSE_GRID, button(Material.BARRIER, "Fermer", NamedTextColor.RED, List.of()));
        fillRowDecor(inv, 45, 53);
        player.openInventory(inv);
    }

    public void openRarityMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_BY_RARITY);
        int i = 10;
        for (WeaponRarity r : WeaponRarity.values()) {
            if (i > 16) {
                break;
            }
            Material mat = switch (r) {
                case COMMON -> Material.WHITE_STAINED_GLASS_PANE;
                case UNCOMMON -> Material.LIME_STAINED_GLASS_PANE;
                case RARE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                case EPIC -> Material.MAGENTA_STAINED_GLASS_PANE;
                case LEGENDARY -> Material.ORANGE_STAINED_GLASS_PANE;
                case MYTHIC -> Material.RED_STAINED_GLASS_PANE;
                case GHOST -> Material.PURPLE_STAINED_GLASS_PANE;
            };
            inv.setItem(i++, button(mat, r.displayFr(), r.color(),
                    List.of(Component.text("Browse " + r.displayFr().toLowerCase() + " weapons", NamedTextColor.DARK_GRAY))));
        }
        inv.setItem(SLOT_CLOSE_MAIN, button(Material.BARRIER, "Fermer", NamedTextColor.RED, List.of()));
        player.openInventory(inv);
    }

    public void openWeaponsOfRarity(Player player, WeaponRarity rarity) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(
                "DPMR — " + rarity.displayFr(), rarity.color()));
        List<WeaponProfile> list = Arrays.stream(WeaponProfile.values())
                .filter(w -> w.rarity() == rarity)
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        int slot = 0;
        for (WeaponProfile w : list) {
            if (slot >= 45) {
                break;
            }
            ItemStack gun = weaponManager.createWeaponItem(w.name());
            if (gun != null) {
                inv.setItem(slot++, gun);
            }
        }
        inv.setItem(SLOT_BACK, button(Material.ARROW, "Retour (raretes)", NamedTextColor.YELLOW, List.of()));
        inv.setItem(SLOT_CLOSE_GRID, button(Material.BARRIER, "Fermer", NamedTextColor.RED, List.of()));
        fillRowDecor(inv, 45, 53);
        player.openInventory(inv);
    }

    public void openLaunchpads(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_LAUNCHPADS);
        int slot = 10;
        for (LaunchpadStyle s : LaunchpadStyle.values()) {
            if (slot > 17) {
                break;
            }
            inv.setItem(slot++, launchpadManager.createItem(s, 1));
        }
        inv.setItem(SLOT_CLOSE_MAIN, button(Material.BARRIER, "Fermer", NamedTextColor.RED, List.of()));
        player.openInventory(inv);
    }

    private void fillRowDecor(Inventory inv, int from, int to) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);
        for (int s = from; s <= to; s++) {
            if (s != SLOT_BACK && s != SLOT_CLOSE_GRID && inv.getItem(s) == null) {
                inv.setItem(s, glass.clone());
            }
        }
    }

    private ItemStack button(Material mat, String name, TextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private boolean titlesMatch(Component a, Component b) {
        return a.equals(b);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Component title = event.getView().title();
        if (titlesMatch(title, TITLE_MAIN) || titlesMatch(title, TITLE_WEAPONS) || titlesMatch(title, TITLE_BY_RARITY)
                || titlesMatch(title, TITLE_LAUNCHPADS) || titlesMatch(title, TITLE_CONSUMABLES)
                || isRarityWeaponsView(title)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Component title = event.getView().title();
        boolean main = titlesMatch(title, TITLE_MAIN);
        boolean weapons = titlesMatch(title, TITLE_WEAPONS);
        boolean rarityPick = titlesMatch(title, TITLE_BY_RARITY);
        boolean launchpads = titlesMatch(title, TITLE_LAUNCHPADS);
        boolean consumables = titlesMatch(title, TITLE_CONSUMABLES);
        boolean rarityWeapons = isRarityWeaponsView(title);

        if (!main && !weapons && !rarityPick && !launchpads && !consumables && !rarityWeapons) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }

        if (main) {
            if (slot == SLOT_CLOSE_MAIN) {
                player.closeInventory();
                return;
            }
            if (slot == SLOT_WEAPONS_MENU) {
                openAllWeapons(player);
                return;
            }
            if (slot == SLOT_RARITY_MENU) {
                openRarityMenu(player);
                return;
            }
            if (slot == SLOT_CONSUMABLES_MENU) {
                openConsumables(player);
                return;
            }
            if (slot == SLOT_WALL_VISION) {
                player.getInventory().addItem(wallVisionManager.createGoggles(1));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.35f);
                player.sendMessage(Component.text("+1 lunettes de scan", NamedTextColor.LIGHT_PURPLE));
                return;
            }
            if (slot == SLOT_INFRARED) {
                player.getInventory().addItem(wallVisionManager.createInfraredGoggles(1));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                player.sendMessage(Component.text("+1 lunettes infrarouges", NamedTextColor.RED));
                return;
            }
            if (slot == SLOT_RADAR) {
                player.getInventory().addItem(radarManager.createRadar(1));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.3f);
                player.sendMessage(Component.text("+1 player radar", NamedTextColor.AQUA));
                return;
            }
            if (slot == SLOT_LAUNCHPAD_MENU) {
                openLaunchpads(player);
                return;
            }
            return;
        }

        if (consumables) {
            if (slot == SLOT_CLOSE_MAIN) {
                player.closeInventory();
                return;
            }
            if (slot == 18) {
                openMain(player);
                return;
            }
            if (slot == 10) {
                player.getInventory().addItem(bandageManager.createConsumable(DpmrConsumable.BANDAGE_SMALL, 8));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                player.sendMessage(Component.text("+8 bandages (petit)", NamedTextColor.GREEN));
                return;
            }
            if (slot == 11) {
                player.getInventory().addItem(bandageManager.createConsumable(DpmrConsumable.BANDAGE_MEDIUM, 8));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                player.sendMessage(Component.text("+8 bandages (moyen)", NamedTextColor.GREEN));
                return;
            }
            if (slot == 12) {
                player.getInventory().addItem(bandageManager.createConsumable(DpmrConsumable.BANDAGE_LARGE, 8));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                player.sendMessage(Component.text("+8 bandages (grand)", NamedTextColor.GREEN));
                return;
            }
            if (slot == 13) {
                player.getInventory().addItem(bandageManager.createConsumable(DpmrConsumable.MEDIKIT, 4));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                player.sendMessage(Component.text("+4 medikits", NamedTextColor.GOLD));
                return;
            }
            if (slot == 14) {
                player.getInventory().addItem(bandageManager.createConsumable(DpmrConsumable.SHIELD_POTION_SMALL, 4));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.25f);
                player.sendMessage(Component.text("+4 potions bouclier (petite)", NamedTextColor.AQUA));
                return;
            }
            if (slot == 15) {
                player.getInventory().addItem(bandageManager.createConsumable(DpmrConsumable.SHIELD_POTION_MEDIUM, 4));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.25f);
                player.sendMessage(Component.text("+4 potions bouclier (moyenne)", NamedTextColor.DARK_AQUA));
                return;
            }
            if (slot == 16) {
                player.getInventory().addItem(bandageManager.createConsumable(DpmrConsumable.SHIELD_POTION_LARGE, 2));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.25f);
                player.sendMessage(Component.text("+2 potions bouclier (grande)", NamedTextColor.BLUE));
                return;
            }
            if (slot == 19) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < 8; i++) {
                    player.getInventory().addItem(pokerCardManager.createCard(PokerCard.randomFullDeck(r), 1));
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.85f, 1.3f);
                player.sendMessage(Component.text("+8 cartes poker (aleatoires)", NamedTextColor.GOLD));
                return;
            }
            if (slot == 20) {
                player.getInventory().addItem(pokerCardManager.createCard(PokerCard.of(PokerSuit.HEARTS, PokerRank.ACE), 1));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.85f, 1.35f);
                player.sendMessage(Component.text("+1 As de coeur", NamedTextColor.RED));
                return;
            }
            if (slot == 21) {
                player.getInventory().addItem(pokerCardManager.createCard(PokerCard.redJoker(), 1));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.85f, 1.2f);
                player.sendMessage(Component.text("+1 Joker rouge", NamedTextColor.LIGHT_PURPLE));
                return;
            }
            return;
        }

        if (launchpads) {
            if (slot == SLOT_CLOSE_MAIN) {
                player.closeInventory();
                return;
            }
            if (slot >= 10 && slot <= 17) {
                LaunchpadStyle st = launchpadManager.readStyleFromItem(current);
                if (st != null) {
                    player.getInventory().addItem(launchpadManager.createItem(st, 8));
                    player.playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.7f, 1.25f);
                    player.sendActionBar(Component.text("+8 launchpads " + st.id(), NamedTextColor.GREEN));
                }
            }
            return;
        }

        if (rarityPick) {
            if (slot == SLOT_CLOSE_MAIN) {
                player.closeInventory();
                return;
            }
            if (slot >= 10 && slot <= 16) {
                int idx = slot - 10;
                WeaponRarity[] vals = WeaponRarity.values();
                if (idx >= 0 && idx < vals.length) {
                    openWeaponsOfRarity(player, vals[idx]);
                }
            }
            return;
        }

        if (weapons || rarityWeapons) {
            if (slot == SLOT_BACK) {
                if (rarityWeapons) {
                    openRarityMenu(player);
                } else {
                    openMain(player);
                }
                return;
            }
            if (slot == SLOT_CLOSE_GRID) {
                player.closeInventory();
                return;
            }
            if (slot < 45) {
                String id = weaponManager.readWeaponId(current);
                if (id != null) {
                    ItemStack give = weaponManager.createWeaponItem(id, player);
                    if (give != null) {
                        player.getInventory().addItem(give);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.15f);
                        player.sendActionBar(Component.text("Recu: " + id, NamedTextColor.GOLD));
                    }
                }
            }
        }
    }

    private boolean isRarityWeaponsView(Component title) {
        String plain = plainTitle(title);
        return plain.startsWith("DPMR — ") && Arrays.stream(WeaponRarity.values())
                .anyMatch(r -> plain.contains(r.displayFr()));
    }

    private static String plainTitle(Component title) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title);
    }
}
