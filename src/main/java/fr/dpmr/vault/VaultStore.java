package fr.dpmr.vault;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stockage persistant par joueur : {@link VaultHolder#PAGE_SLOTS} cases par page, {@link VaultHolder#PAGE_COUNT} pages.
 */
public final class VaultStore {

    private static final int TOTAL_SLOTS = VaultHolder.TOTAL_STORAGE_SLOTS;

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;
    private final Map<UUID, ItemStack[]> byPlayer = new HashMap<>();

    public VaultStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vault-storage.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private static ItemStack[] ensureBlank() {
        return new ItemStack[TOTAL_SLOTS];
    }

    private ItemStack[] ensure(UUID uuid) {
        return byPlayer.computeIfAbsent(uuid, u -> ensureBlank());
    }

    public void setPage(UUID uuid, int page, ItemStack[] pageContents) {
        if (pageContents == null || pageContents.length != VaultHolder.PAGE_SLOTS) {
            return;
        }
        ItemStack[] all = ensure(uuid);
        int off = page * VaultHolder.PAGE_SLOTS;
        for (int i = 0; i < VaultHolder.PAGE_SLOTS; i++) {
            ItemStack it = pageContents[i];
            all[off + i] = (it == null || it.getType() == Material.AIR) ? null : it.clone();
        }
    }

    /**
     * Copie les 54 cases de la page dans un tableau neuf (pour {@link Inventory#setContents}).
     */
    public ItemStack[] copyPageForGui(UUID uuid, int page) {
        ItemStack[] all = ensure(uuid);
        ItemStack[] out = new ItemStack[VaultHolder.PAGE_SLOTS];
        int off = page * VaultHolder.PAGE_SLOTS;
        for (int i = 0; i < VaultHolder.PAGE_SLOTS; i++) {
            ItemStack it = all[off + i];
            out[i] = (it == null || it.getType() == Material.AIR) ? null : it.clone();
        }
        return out;
    }

    private void load() {
        byPlayer.clear();
        yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ItemStack[] all = ensureBlank();
                String base = "players." + key + ".slots";
                for (int i = 0; i < TOTAL_SLOTS; i++) {
                    ItemStack it = yaml.getItemStack(base + "." + i);
                    if (it != null && it.getType() != Material.AIR) {
                        all[i] = it.clone();
                    }
                }
                byPlayer.put(id, all);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("vault-storage.yml: UUID invalide " + key);
            }
        }
    }

    public void save() {
        yaml.set("players", null);
        for (Map.Entry<UUID, ItemStack[]> e : byPlayer.entrySet()) {
            String base = "players." + e.getKey() + ".slots";
            ItemStack[] all = e.getValue();
            if (all == null) {
                continue;
            }
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                ItemStack it = all[i];
                if (it == null || it.getType() == Material.AIR) {
                    yaml.set(base + "." + i, null);
                } else {
                    yaml.set(base + "." + i, it.clone());
                }
            }
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible d'enregistrer vault-storage.yml: " + ex.getMessage());
        }
    }
}
