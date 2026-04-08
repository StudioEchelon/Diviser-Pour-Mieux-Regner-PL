package fr.dpmr.cosmetics;

import fr.dpmr.data.PointsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CosmeticsManager {

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final File file;
    private final YamlConfiguration yaml;

    public CosmeticsManager(JavaPlugin plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.file = new File(plugin.getDataFolder(), "cosmetics.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        save();
        auraTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAuras, 10L, 10L);
    }

    private org.bukkit.scheduler.BukkitTask auraTask;

    public void stop() {
        if (auraTask != null) {
            auraTask.cancel();
            auraTask = null;
        }
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder cosmetics.yml: " + e.getMessage());
        }
    }

    public void saveAsync() {
        String data = yaml.saveToString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.nio.file.Files.writeString(file.toPath(), data, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de sauvegarder cosmetics.yml (async): " + e.getMessage());
            }
        });
    }

    private String base(UUID uuid) {
        return "players." + uuid;
    }

    public boolean isOwned(UUID uuid, CosmeticProfile profile) {
        if (profile == null) {
            return false;
        }
        return yaml.getStringList(base(uuid) + ".owned").contains(profile.id());
    }

    public void grant(UUID uuid, CosmeticProfile profile) {
        if (profile == null) {
            return;
        }
        String path = base(uuid) + ".owned";
        List<String> owned = new ArrayList<>(yaml.getStringList(path));
        if (!owned.contains(profile.id())) {
            owned.add(profile.id());
            yaml.set(path, owned);
            saveAsync();
        }
    }

    public boolean buy(UUID uuid, CosmeticProfile profile) {
        if (profile == null) {
            return false;
        }
        if (isOwned(uuid, profile)) {
            return true;
        }
        int pts = pointsManager.getPoints(uuid);
        if (pts < profile.price()) {
            return false;
        }
        pointsManager.addPoints(uuid, -profile.price());
        pointsManager.saveAsync();
        grant(uuid, profile);
        return true;
    }

    public String selectedShot(UUID uuid) {
        return yaml.getString(base(uuid) + ".selected.shot", "");
    }

    public String selectedAura(UUID uuid) {
        return yaml.getString(base(uuid) + ".selected.aura", "");
    }

    public String selectedParachute(UUID uuid) {
        return yaml.getString(base(uuid) + ".selected.parachute", "");
    }

    /** Skin couteau equipe (COUTEAU_COMBAT) ; null si aucun ou pas possede. */
    public CosmeticProfile selectedKnifeProfile(UUID uuid) {
        CosmeticProfile p = CosmeticProfile.fromId(yaml.getString(base(uuid) + ".selected.knife", ""));
        return p != null && p.type() == CosmeticType.KNIFE_SKIN && isOwned(uuid, p) ? p : null;
    }

    /**
     * Skin d'arme selectionne pour une base {@link fr.dpmr.game.WeaponProfile} (ex. THOMPSON) ;
     * null = apparence de base (pas de NBT cosmétique).
     */
    public CosmeticProfile selectedWeaponSkin(UUID uuid, String weaponProfileName) {
        if (weaponProfileName == null || weaponProfileName.isBlank()) {
            return null;
        }
        String id = yaml.getString(base(uuid) + ".selected.weapon-skin." + weaponProfileName, "");
        CosmeticProfile p = CosmeticProfile.fromId(id);
        if (p == null || p.type() != CosmeticType.WEAPON_SKIN) {
            return null;
        }
        if (p.weaponSkinFor() == null || !p.weaponSkinFor().equalsIgnoreCase(weaponProfileName)) {
            return null;
        }
        return isOwned(uuid, p) ? p : null;
    }

    /** Retire le skin equipe pour cette arme (retour apparence de base). */
    public void clearWeaponSkinSelection(UUID uuid, String weaponProfileName) {
        if (weaponProfileName == null || weaponProfileName.isBlank()) {
            return;
        }
        yaml.set(base(uuid) + ".selected.weapon-skin." + weaponProfileName, null);
        saveAsync();
    }

    public void setSelected(UUID uuid, CosmeticProfile profile) {
        if (profile == null) {
            return;
        }
        if (!isOwned(uuid, profile)) {
            return;
        }
        String path = switch (profile.type()) {
            case SHOT -> base(uuid) + ".selected.shot";
            case AURA -> base(uuid) + ".selected.aura";
            case PARACHUTE -> base(uuid) + ".selected.parachute";
            case VANITY -> base(uuid) + ".selected.vanity";
            case KNIFE_SKIN -> base(uuid) + ".selected.knife";
            case WEAPON_SKIN -> base(uuid) + ".selected.weapon-skin." + profile.weaponSkinFor();
            default -> throw new IllegalStateException("Unhandled cosmetic type: " + profile.type());
        };
        yaml.set(path, profile.id());
        saveAsync();
    }

    public Particle shotParticle(UUID uuid) {
        CosmeticProfile p = CosmeticProfile.fromId(selectedShot(uuid));
        return p != null && p.type() == CosmeticType.SHOT ? p.particle() : null;
    }

    public Particle auraParticle(UUID uuid) {
        CosmeticProfile p = CosmeticProfile.fromId(selectedAura(uuid));
        return p != null && p.type() == CosmeticType.AURA ? p.particle() : null;
    }

    public Particle parachuteParticle(UUID uuid) {
        CosmeticProfile p = CosmeticProfile.fromId(selectedParachute(uuid));
        return p != null && p.type() == CosmeticType.PARACHUTE ? p.particle() : null;
    }

    private void tickAuras() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Particle part = auraParticle(p.getUniqueId());
            if (part == null) {
                continue;
            }
            p.getWorld().spawnParticle(part, p.getLocation().add(0, 1.0, 0), 10, 0.45, 0.55, 0.45, 0.02);
        }
    }

    public ItemStack createVanityItem(CosmeticProfile profile) {
        if (profile == null || profile.type() != CosmeticType.VANITY) {
            return null;
        }
        ItemStack item = new ItemStack(profile.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(profile.displayName(), profile.color()));
        meta.lore(List.of(
                Component.text("Cosmetic DPMR", NamedTextColor.GRAY),
                Component.text("Ne donne aucun avantage", NamedTextColor.DARK_GRAY)
        ));
        if (meta instanceof LeatherArmorMeta lam && profile == CosmeticProfile.VANITY_CAPE) {
            lam.setColor(Color.fromRGB(110, 40, 170));
        }
        item.setItemMeta(meta);
        return item;
    }

    public void giveVanity(Player player, CosmeticProfile profile) {
        ItemStack it = createVanityItem(profile);
        if (it == null) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(it);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(s -> player.getWorld().dropItemNaturally(player.getLocation(), s));
        }
        player.sendMessage(Component.text("Cosmetic recu: " + profile.displayName(), NamedTextColor.GREEN));
    }
}

