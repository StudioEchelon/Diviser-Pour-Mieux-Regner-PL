package fr.dpmr.command;

import fr.dpmr.gui.GunGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GunCommand implements CommandExecutor {

    private final GunGui gui;

    public GunCommand(GunGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Commande joueur uniquement.", NamedTextColor.RED));
            return true;
        }
        gui.open(player);
        return true;
    }
}
