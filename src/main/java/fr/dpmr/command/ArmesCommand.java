package fr.dpmr.command;

import fr.dpmr.gui.WeaponBrowseGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ArmesCommand implements CommandExecutor {

    private final WeaponBrowseGui gui;

    public ArmesCommand(WeaponBrowseGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        gui.openHub(player);
        return true;
    }
}
