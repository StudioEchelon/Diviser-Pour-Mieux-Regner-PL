package fr.dpmr.command;

import fr.dpmr.cosmetics.CosmeticProfile;
import fr.dpmr.cosmetics.CosmeticType;
import fr.dpmr.cosmetics.CosmeticsGui;
import fr.dpmr.cosmetics.CosmeticsManager;
import fr.dpmr.data.PointsManager;
import fr.dpmr.game.WeaponManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class CosmeticsCommand implements CommandExecutor {

    private final CosmeticsGui gui;
    private final CosmeticsManager cosmeticsManager;
    private final PointsManager pointsManager;
    private final WeaponManager weaponManager;

    public CosmeticsCommand(CosmeticsGui gui, CosmeticsManager cosmeticsManager, PointsManager pointsManager,
                            WeaponManager weaponManager) {
        this.gui = gui;
        this.cosmeticsManager = cosmeticsManager;
        this.pointsManager = pointsManager;
        this.weaponManager = weaponManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            gui.open(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("buy") && args.length >= 2) {
            CosmeticProfile p = CosmeticProfile.fromId(args[1]);
            if (p == null) {
                player.sendMessage(Component.text("Cosmetic inconnu.", NamedTextColor.RED));
                return true;
            }
            int pts = pointsManager.getPoints(player.getUniqueId());
            if (pts < p.price()) {
                player.sendMessage(Component.text("Not enough points (" + pts + "/" + p.price() + ").", NamedTextColor.RED));
                return true;
            }
            cosmeticsManager.buy(player.getUniqueId(), p);
            cosmeticsManager.setSelected(player.getUniqueId(), p);
            player.sendMessage(Component.text("Achete: " + p.displayName(), NamedTextColor.GREEN));
            if (p.type() == CosmeticType.VANITY) {
                cosmeticsManager.giveVanity(player, p);
            } else if (p.type() == CosmeticType.KNIFE_SKIN) {
                weaponManager.refreshDpmrWeaponsInInventory(player);
            } else if (p.type() == CosmeticType.WEAPON_SKIN && p.weaponSkinFor() != null) {
                weaponManager.syncWeaponSkinFromSelection(player, p.weaponSkinFor());
            }
            return true;
        }
        player.sendMessage(Component.text("Usage: /cosmetics | /cosmetics buy <id>", NamedTextColor.YELLOW));
        return true;
    }
}

