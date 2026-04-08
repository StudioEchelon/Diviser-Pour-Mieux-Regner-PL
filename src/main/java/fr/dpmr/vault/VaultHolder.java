package fr.dpmr.vault;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marque l'inventaire du coffre-fort personnel (2 pages x 54 cases, comme 2 doubles coffres).
 */
public final class VaultHolder implements InventoryHolder {

    public static final int PAGE_SLOTS = 54;
    public static final int PAGE_COUNT = 2;
    public static final int TOTAL_STORAGE_SLOTS = PAGE_SLOTS * PAGE_COUNT;

    private Inventory inventory;
    private final UUID owner;
    private final int page;

    private VaultHolder(UUID owner, int page) {
        this.owner = owner;
        this.page = page;
    }

    void attach(Inventory inv) {
        this.inventory = inv;
    }

    public UUID owner() {
        return owner;
    }

    public int page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static Inventory create(Component title, UUID owner, int page) {
        VaultHolder holder = new VaultHolder(owner, page);
        Inventory inv = Bukkit.createInventory(holder, PAGE_SLOTS, title);
        holder.attach(inv);
        return inv;
    }

    public static VaultHolder from(Inventory top) {
        if (top == null) {
            return null;
        }
        InventoryHolder raw = top.getHolder(false);
        return raw instanceof VaultHolder h ? h : null;
    }
}
