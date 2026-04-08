package fr.dpmr.game;

import fr.dpmr.i18n.GameLocale;
import fr.dpmr.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Clash-Royale-style weapon tooltip: rarity in color, then 5-6 clean stat lines.
 */
public final class WeaponLoreBuilder {

    public record WeaponKillMeterLore(boolean show, int meter0To100, boolean maxed, List<WeaponKillPerk> perks) {
    }

    public static WeaponKillMeterLore killMeterFor(ItemStack stack, WeaponProfile w, JavaPlugin plugin) {
        if (!WeaponKillPerkState.enabled(plugin) || stack == null || w == null || !w.supportsKillPerkMeter()) {
            return new WeaponKillMeterLore(false, 0, false, List.of());
        }
        return new WeaponKillMeterLore(true,
                WeaponKillPerkState.meter(stack, plugin),
                WeaponKillPerkState.isMaxed(stack, plugin),
                WeaponKillPerkState.perks(stack, plugin));
    }

    private WeaponLoreBuilder() {
    }

    public static void apply(ItemMeta meta, WeaponProfile w,
                             WeaponUpgradeState st, BombUpgradeState bombSt, MortarUpgradeState mortarSt,
                             RocketUpgradeState rocketSt, RevolverUpgradeState revolverSt, JerrycanUpgradeState jerrySt,
                             GameLocale loc, Integer ammoCurrent, int ammoMax,
                             WeaponKillMeterLore killMeter) {
        meta.displayName(Component.text(w.displayName(), w.color())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();

        boolean bold = w.rarity().ordinal() >= WeaponRarity.LEGENDARY.ordinal();
        Component rarityComp = bold
                ? Component.text(w.rarity().displayFr(), w.rarity().color(), TextDecoration.BOLD)
                : Component.text(w.rarity().displayFr(), w.rarity().color());
        lore.add(rarityComp.decoration(TextDecoration.ITALIC, false));

        lore.add(Component.empty());

        double hearts = w.baseDamage() / 2.0;
        lore.add(stat(l(loc, "D\u00e9g\u00e2ts", "Damage"),
                String.format(Locale.ROOT, "%.1f \u2764", hearts)));

        if (w.pellets() > 1) {
            lore.add(stat(l(loc, "Plombs", "Pellets"),
                    String.valueOf(w.pellets())));
        }

        if (w.cooldownTicks() > 0) {
            double rof = 20.0 / w.cooldownTicks();
            lore.add(stat(l(loc, "Cadence", "Fire rate"),
                    String.format(Locale.ROOT, "%.1f", rof)
                            + (loc == GameLocale.FR ? " tir/s" : "/s")));
        }

        double reloadSec = w.reloadTicks() / 20.0;
        lore.add(stat(l(loc, "Recharge", "Reload"),
                String.format(Locale.ROOT, "%.1fs", reloadSec)));

        if (ammoCurrent != null) {
            lore.add(stat(l(loc, "Chargeur", "Magazine"),
                    ammoCurrent + "/" + ammoMax));
        } else {
            lore.add(stat(l(loc, "Chargeur", "Magazine"),
                    String.valueOf(ammoMax)));
        }

        lore.add(stat(l(loc, "Port\u00e9e", "Range"),
                (int) w.baseRange()
                        + (loc == GameLocale.FR ? " blocs" : " blocks")));

        if (killMeter != null && killMeter.show()) {
            lore.add(Component.empty());
            if (killMeter.maxed()) {
                lore.add(Component.text(l(loc, "Am\u00e9liorations (max)", "Upgrades (max)"),
                                NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                String bar = killMeterBar(killMeter.meter0To100());
                lore.add(Component.text()
                        .append(Component.text(l(loc, "\u00c9volution", "Evolution") + ": ",
                                        NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))
                        .append(Component.text(bar + " ", NamedTextColor.DARK_AQUA)
                                .decoration(TextDecoration.ITALIC, false))
                        .append(Component.text(killMeter.meter0To100() + "%", NamedTextColor.AQUA)
                                .decoration(TextDecoration.ITALIC, false))
                        .build());
            }
            for (WeaponKillPerk p : killMeter.perks()) {
                lore.add(Component.text("  + " + I18n.string(loc, p.nameKey()),
                                NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        if (meta instanceof Damageable d && d.hasMaxDamage()) {
            int max = d.getMaxDamage();
            int left = max - Math.min(max, d.getDamage());
            lore.add(Component.empty());
            NamedTextColor dc = left <= max / 4 ? NamedTextColor.RED
                    : left <= max / 2 ? NamedTextColor.YELLOW
                    : NamedTextColor.DARK_GRAY;
            lore.add(Component.text(left + " / " + max
                            + (loc == GameLocale.FR ? " tirs" : " uses"), dc)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
    }

    private static Component stat(String label, String value) {
        return Component.text()
                .append(Component.text(label + ": ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(value, NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .build();
    }

    private static String l(GameLocale loc, String fr, String en) {
        return loc == GameLocale.FR ? fr : en;
    }

    private static String killMeterBar(int pct) {
        int filled = (int) Math.round(pct / 10.0);
        filled = Math.max(0, Math.min(10, filled));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? '\u2588' : '\u2591');
        }
        return sb.toString();
    }
}
