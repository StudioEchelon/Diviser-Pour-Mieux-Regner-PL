package fr.dpmr.gui;

import fr.dpmr.game.WeaponProfile;
import fr.dpmr.game.WeaponRarity;
import fr.dpmr.npc.NpcSkins;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Shop UI: rarity heads, tier-colored glass panes, shared backdrop.
 */
public final class BoutiqueUi {

    private static final TextColor SUBTLE = TextColor.color(0x9AA0A6);
    private static final TextColor ACCENT_GOLD = TextColor.color(0xD4AF37);

    private BoutiqueUi() {
    }

    public static int countWeapons(WeaponRarity rarity) {
        int n = 0;
        for (WeaponProfile w : WeaponProfile.values()) {
            if (w.rarity() == rarity) {
                n++;
            }
        }
        return n;
    }

    public static Component titleHub() {
        return Component.text()
                .append(Component.text("DPMR ", NamedTextColor.DARK_GRAY))
                .append(Component.text("· ", TextColor.color(0x5A5A68)))
                .append(Component.text("Weapon Shop", TextColor.color(0xF2F2F2), TextDecoration.BOLD))
                .build();
    }

    public static Component titleBuyCategory(WeaponRarity rarity) {
        return Component.text()
                .append(Component.text("Buy ", NamedTextColor.DARK_GRAY))
                .append(Component.text("· ", TextColor.color(0x5A5A68)))
                .append(Component.text(rarity.displayFr(), rarity.color(), TextDecoration.BOLD))
                .build();
    }

    public static Component titleSell() {
        return Component.text()
                .append(Component.text("Sell ", TextColor.color(0xE8C170), TextDecoration.BOLD))
                .append(Component.text("· ", TextColor.color(0x5A5A68)))
                .append(Component.text("Trade-in", NamedTextColor.DARK_GRAY))
                .build();
    }

    public static ItemStack rarityPortrait(WeaponRarity rarity) {
        String hash = switch (rarity) {
            case COMMON -> "1000cfb2ff1a984c3b33f72a25f49dff16839650bed677073d2d4528efd8eabe";
            case UNCOMMON -> "100191e52d207a0ef4972ff8393e4ed1277b1b872e72e7830aff09e938f337ec";
            case RARE -> "1003701ba9a2ed6364ac2f513b357e7472d207447f889be695eaf3590abfb037";
            case EPIC -> "10073479cfcfe3c1a592b903e783af33e8ae1d3d928a9f92febce4af5419f5b9";
            case LEGENDARY -> "13a6c9d854e84902b5e8b85faedcda975d1dcbb0803734274a172edc25e9316d";
            case MYTHIC -> "13b906faedc09c9f10fe17478a282d15bdb310bd512be345e9eb182ab6f210b5";
            case GHOST -> "1002f90ba0b230f6e9b22f163ec99a93da45c7f148f93d234652b507f6dbb374";
        };
        return NpcSkins.playerHeadFromTextureHash(hash, "dpmr-boutique-rarity-" + rarity.name());
    }

    /** Stained glass pane matching shop tier colour (e.g. lime for Common). */
    public static Material rarityTierPaneMaterial(WeaponRarity rarity) {
        return switch (rarity) {
            case COMMON -> Material.LIME_STAINED_GLASS_PANE;
            case UNCOMMON -> Material.GREEN_STAINED_GLASS_PANE;
            case RARE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case EPIC -> Material.MAGENTA_STAINED_GLASS_PANE;
            case LEGENDARY -> Material.YELLOW_STAINED_GLASS_PANE;
            case MYTHIC -> Material.PINK_STAINED_GLASS_PANE;
            case GHOST -> Material.PURPLE_STAINED_GLASS_PANE;
        };
    }

    public static ItemStack hubRarityButton(WeaponRarity rarity, int weaponCount) {
        ItemStack sk = rarityPortrait(rarity);
        ItemMeta m = sk.getItemMeta();
        m.displayName(Component.text(rarity.displayFr(), rarity.color(), TextDecoration.BOLD));
        String w = weaponCount == 1 ? "weapon" : "weapons";
        m.lore(List.of(
                Component.text(weaponCount + " " + w + " in this tier", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to browse", SUBTLE)
        ));
        sk.setItemMeta(m);
        return sk;
    }

    /** Tier-coloured pane under the portrait; same navigation as the head. */
    public static ItemStack hubRarityTierPane(WeaponRarity rarity, int weaponCount) {
        ItemStack p = new ItemStack(rarityTierPaneMaterial(rarity));
        ItemMeta m = p.getItemMeta();
        m.displayName(Component.text(rarity.displayFr(), rarity.color(), TextDecoration.BOLD));
        String w = weaponCount == 1 ? "weapon" : "weapons";
        m.lore(List.of(
                Component.text(weaponCount + " " + w, NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to browse", SUBTLE)
        ));
        p.setItemMeta(m);
        return p;
    }

    public static ItemStack pane(Material material) {
        ItemStack p = new ItemStack(material);
        ItemMeta m = p.getItemMeta();
        m.displayName(Component.text(" "));
        p.setItemMeta(m);
        return p;
    }

    public static boolean isBorderSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return row == 0 || row == 5 || col == 0 || col == 8;
    }

    public static void fillBackdrop(Inventory inv) {
        ItemStack border = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack inner = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 0; s < inv.getSize(); s++) {
            if (inv.getItem(s) != null) {
                continue;
            }
            inv.setItem(s, isBorderSlot(s) ? border.clone() : inner.clone());
        }
    }

    public static ItemStack categoryShowcase(WeaponRarity rarity, int weaponCount) {
        ItemStack sk = rarityPortrait(rarity);
        ItemMeta m = sk.getItemMeta();
        m.displayName(Component.text("Tier ", NamedTextColor.DARK_GRAY)
                .append(Component.text(rarity.displayFr(), rarity.color(), TextDecoration.BOLD)));
        String mdl = weaponCount == 1 ? "model" : "models";
        m.lore(List.of(
                Component.text(weaponCount + " " + mdl + " listed", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Prices in DPMR points", ACCENT_GOLD)
        ));
        sk.setItemMeta(m);
        return sk;
    }
}
