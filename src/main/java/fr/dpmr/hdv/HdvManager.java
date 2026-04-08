package fr.dpmr.hdv;

import fr.dpmr.data.PointsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HdvManager {

    private final JavaPlugin plugin;
    private final PointsManager pointsManager;
    private final File file;
    private final YamlConfiguration yaml;

    public HdvManager(JavaPlugin plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        this.file = new File(plugin.getDataFolder(), "hdv.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        save();
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder hdv.yml: " + e.getMessage());
        }
    }

    public void saveAsync() {
        String data = yaml.saveToString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.nio.file.Files.writeString(file.toPath(), data, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de sauvegarder hdv.yml (async): " + e.getMessage());
            }
        });
    }

    public List<HdvListing> allListings() {
        if (!yaml.isConfigurationSection("listings")) {
            return List.of();
        }
        List<HdvListing> out = new ArrayList<>();
        for (String id : Objects.requireNonNull(yaml.getConfigurationSection("listings")).getKeys(false)) {
            String base = "listings." + id + ".";
            String sellerRaw = yaml.getString(base + "seller");
            int price = yaml.getInt(base + "price", -1);
            long created = yaml.getLong(base + "createdAtMs", 0L);
            String itemRaw = yaml.getString(base + "item");
            if (sellerRaw == null || price < 0 || itemRaw == null) {
                continue;
            }
            try {
                UUID seller = UUID.fromString(sellerRaw);
                ItemStack item = ItemStackCodec.decode(itemRaw);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                out.add(new HdvListing(id, seller, price, created, item));
            } catch (IllegalArgumentException ignored) {
            }
        }
        out.sort(Comparator.comparingLong(HdvListing::createdAtMs).reversed());
        return out;
    }

    public HdvListing get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String base = "listings." + id + ".";
        if (yaml.getString(base + "seller") == null) {
            return null;
        }
        try {
            UUID seller = UUID.fromString(Objects.requireNonNull(yaml.getString(base + "seller")));
            int price = yaml.getInt(base + "price", -1);
            long created = yaml.getLong(base + "createdAtMs", 0L);
            ItemStack item = ItemStackCodec.decode(yaml.getString(base + "item"));
            if (price < 0 || item == null) {
                return null;
            }
            return new HdvListing(id, seller, price, created, item);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String createListing(Player seller, ItemStack item, int price) {
        if (seller == null || item == null || item.getType().isAir()) {
            return null;
        }
        if (price <= 0) {
            seller.sendMessage(Component.text("Prix invalide.", NamedTextColor.RED));
            return null;
        }
        String encoded = ItemStackCodec.encode(item);
        if (encoded == null) {
            seller.sendMessage(Component.text("Could not register the item.", NamedTextColor.RED));
            return null;
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        String base = "listings." + id + ".";
        yaml.set(base + "seller", seller.getUniqueId().toString());
        yaml.set(base + "price", price);
        yaml.set(base + "createdAtMs", System.currentTimeMillis());
        yaml.set(base + "item", encoded);
        saveAsync();
        return id;
    }

    public boolean removeListing(String id) {
        if (id == null) {
            return false;
        }
        if (yaml.get("listings." + id) == null) {
            return false;
        }
        yaml.set("listings." + id, null);
        saveAsync();
        return true;
    }

    public boolean buy(Player buyer, String listingId) {
        HdvListing listing = get(listingId);
        if (listing == null) {
            buyer.sendMessage(Component.text("Listing not found.", NamedTextColor.RED));
            return false;
        }
        if (listing.seller().equals(buyer.getUniqueId())) {
            buyer.sendMessage(Component.text("You can't buy your own listing.", NamedTextColor.RED));
            return false;
        }
        int pts = pointsManager.getPoints(buyer.getUniqueId());
        if (pts < listing.price()) {
            buyer.sendMessage(Component.text("Not enough points (" + pts + "/" + listing.price() + ").", NamedTextColor.RED));
            return false;
        }

        // Donner l'item d'abord (sinon perte si inventaire plein)
        Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(listing.item().clone());
        if (!leftover.isEmpty()) {
            buyer.sendMessage(Component.text("Inventaire plein.", NamedTextColor.RED));
            return false;
        }

        // Retirer l'annonce puis transférer les points (anti double achat)
        if (!removeListing(listingId)) {
            buyer.sendMessage(Component.text("Listing already sold.", NamedTextColor.RED));
            // rollback item
            buyer.getInventory().removeItem(listing.item().clone());
            return false;
        }

        pointsManager.addPoints(buyer.getUniqueId(), -listing.price());
        pointsManager.addPoints(listing.seller(), listing.price());
        pointsManager.saveAsync();
        buyer.sendMessage(Component.text("Achat reussi (-" + listing.price() + " pts)", NamedTextColor.GREEN));
        return true;
    }
}

