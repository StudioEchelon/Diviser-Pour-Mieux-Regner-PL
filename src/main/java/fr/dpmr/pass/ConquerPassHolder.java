package fr.dpmr.pass;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marque les inventaires coffre du Conquer Pass (combat pass).
 */
public final class ConquerPassHolder implements InventoryHolder {

    private Inventory inventory;
    private int page;

    private ConquerPassHolder(int page) {
        this.page = page;
    }

    void attach(Inventory inv) {
        this.inventory = inv;
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static Inventory create(Component title, int page) {
        ConquerPassHolder holder = new ConquerPassHolder(page);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.attach(inv);
        return inv;
    }

    public static ConquerPassHolder from(Inventory top) {
        if (top == null) {
            return null;
        }
        InventoryHolder raw = top.getHolder(false);
        return raw instanceof ConquerPassHolder h ? h : null;
    }
}
