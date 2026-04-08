package fr.dpmr.crate;

import fr.dpmr.data.PointsManager;
import fr.dpmr.game.BandageManager;
import fr.dpmr.game.DpmrConsumable;
import fr.dpmr.game.WeaponManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared weighted loot for physical crates and lootboxes.
 */
public final class CrateRewards {

    private CrateRewards() {}

    public interface LootReward {
        ItemStack preview();

        void give(Player player);

        String label();

        int weight();
    }

    /**
     * @param legendTier if true, adds high-tier weapons and large point bundles (legend crate / ultimate lootbox).
     */
    public static List<LootReward> buildPool(boolean legendTier, PointsManager pointsManager,
                                            BandageManager bandageManager, WeaponManager weaponManager) {
        List<LootReward> rewards = new ArrayList<>();
        rewards.add(itemReward("Bandages (medium) x6", () -> bandageManager.createBandage(6), 14));
        rewards.add(itemReward("Bandages (large) x4", () -> bandageManager.createConsumable(DpmrConsumable.BANDAGE_LARGE, 4), 10));
        rewards.add(itemReward("Medikit x2", () -> bandageManager.createConsumable(DpmrConsumable.MEDIKIT, 2), 8));
        rewards.add(itemReward("Large shield potion x2", () -> bandageManager.createConsumable(DpmrConsumable.SHIELD_POTION_LARGE, 2), 6));
        rewards.add(pointsReward(20, 16, pointsManager));
        rewards.add(pointsReward(50, 10, pointsManager));
        rewards.add(weaponReward("CARABINE_MK18", 7, weaponManager));
        rewards.add(weaponReward("AK47", 7, weaponManager));
        rewards.add(weaponReward("PULSE", 6, weaponManager));
        rewards.add(weaponReward("FUSIL_POMPE_RL", 6, weaponManager));
        rewards.add(weaponReward("DRAGUNOV_SVD", 4, weaponManager));
        if (legendTier) {
            rewards.add(weaponReward("AWP", 3, weaponManager));
            rewards.add(weaponReward("DIVISER_POUR_MIEUX_REGNER", 1, weaponManager));
            rewards.add(pointsReward(180, 3, pointsManager));
        }
        return rewards;
    }

    /** Physical crates: "legend" / "legendaire" enable top tier. */
    public static List<LootReward> poolForCrateId(String crateId, PointsManager pointsManager,
                                                  BandageManager bandageManager, WeaponManager weaponManager) {
        boolean legend = crateId.equalsIgnoreCase("legend") || crateId.equalsIgnoreCase("legendaire");
        return buildPool(legend, pointsManager, bandageManager, weaponManager);
    }

    private static LootReward itemReward(String label, Supplier<ItemStack> supplier, int weight) {
        return new LootReward() {
            @Override
            public ItemStack preview() {
                return supplier.get().clone();
            }

            @Override
            public void give(Player player) {
                player.getInventory().addItem(supplier.get());
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public int weight() {
                return weight;
            }
        };
    }

    private static LootReward pointsReward(int amount, int weight, PointsManager pointsManager) {
        return new LootReward() {
            @Override
            public ItemStack preview() {
                ItemStack it = new ItemStack(Material.SUNFLOWER);
                ItemMeta meta = it.getItemMeta();
                meta.displayName(Component.text("Points +" + amount, NamedTextColor.YELLOW));
                it.setItemMeta(meta);
                return it;
            }

            @Override
            public void give(Player player) {
                pointsManager.addPoints(player.getUniqueId(), amount);
                pointsManager.saveAsync();
            }

            @Override
            public String label() {
                return amount + " points";
            }

            @Override
            public int weight() {
                return weight;
            }
        };
    }

    private static LootReward weaponReward(String weaponId, int weight, WeaponManager weaponManager) {
        return new LootReward() {
            @Override
            public ItemStack preview() {
                ItemStack w = weaponManager.createWeaponItem(weaponId);
                return w != null ? w : new ItemStack(Material.BARRIER);
            }

            @Override
            public void give(Player player) {
                ItemStack w = weaponManager.createWeaponItem(weaponId);
                if (w != null) {
                    player.getInventory().addItem(w);
                }
            }

            @Override
            public String label() {
                return weaponId;
            }

            @Override
            public int weight() {
                return weight;
            }
        };
    }
}
