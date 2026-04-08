package fr.dpmr.quest;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks daily quest chest GUIs.
 */
public final class DailyQuestHolder implements InventoryHolder {

    private Inventory inventory;

    private DailyQuestHolder() {
    }

    void attach(Inventory inv) {
        this.inventory = inv;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static Inventory create(Component title, int size) {
        if (size < 9 || size > 54 || size % 9 != 0) {
            size = 54;
        }
        DailyQuestHolder holder = new DailyQuestHolder();
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.attach(inv);
        return inv;
    }

    public static DailyQuestHolder from(Inventory top) {
        if (top == null) {
            return null;
        }
        InventoryHolder raw = top.getHolder(false);
        return raw instanceof DailyQuestHolder h ? h : null;
    }
}
