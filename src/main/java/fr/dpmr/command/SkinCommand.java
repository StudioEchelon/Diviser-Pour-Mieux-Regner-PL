package fr.dpmr.command;

import fr.dpmr.cosmetics.SkinGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class SkinCommand implements CommandExecutor {

    private final SkinGui skinGui;

    public SkinCommand(SkinGui skinGui) {
        this.skinGui = skinGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Commande reservee aux joueurs.", NamedTextColor.RED));
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "thompson";
        if (sub.equals("thompson")) {
            skinGui.openThompson(player);
            return true;
        }
        player.sendMessage(Component.text("Usage: /skin | /skin thompson", NamedTextColor.YELLOW));
        return true;
    }
}
