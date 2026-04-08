package fr.dpmr.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.MissingResourceException;

import org.bukkit.configuration.file.YamlConfiguration;

public final class I18n {

    private static YamlConfiguration en;
    private static YamlConfiguration fr;
    private static PlayerLanguageStore languages;

    private I18n() {
    }

    public static void init(JavaPlugin plugin, PlayerLanguageStore langStore) {
        languages = langStore;
        en = loadYaml(plugin, "lang/en.yml");
        fr = loadYaml(plugin, "lang/fr.yml");
    }

    private static YamlConfiguration loadYaml(JavaPlugin plugin, String path) {
        var in = plugin.getResource(path);
        if (in == null) {
            plugin.getLogger().warning("Missing resource: " + path);
            return new YamlConfiguration();
        }
        YamlConfiguration y = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            y.load(reader);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load " + path + ": " + e.getMessage());
        }
        return y;
    }

    public static GameLocale locale(Player player) {
        if (player == null || languages == null) {
            return GameLocale.EN;
        }
        return languages.get(player.getUniqueId());
    }

    public static String string(GameLocale loc, String key, Object... args) {
        YamlConfiguration primary = loc == GameLocale.FR ? fr : en;
        String pattern = primary.getString(key);
        if (pattern == null || pattern.isEmpty()) {
            pattern = en.getString(key, key);
        }
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException | MissingResourceException e) {
            return pattern;
        }
    }

    public static String string(CommandSender sender, String key, Object... args) {
        return string(sender instanceof Player p ? locale(p) : GameLocale.EN, key, args);
    }

    public static Component component(GameLocale loc, NamedTextColor color, String key, Object... args) {
        return Component.text(string(loc, key, args), color);
    }

    public static Component component(Player player, NamedTextColor color, String key, Object... args) {
        return component(locale(player), color, key, args);
    }

    public static void actionBar(Player player, NamedTextColor color, String key, Object... args) {
        player.sendActionBar(component(player, color, key, args));
    }

    public static void broadcast(NamedTextColor color, String key, Object... args) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(component(p, color, key, args));
        }
    }

    public static Component title(Player player, NamedTextColor color, String key, Object... args) {
        return component(player, color, key, args);
    }
}
