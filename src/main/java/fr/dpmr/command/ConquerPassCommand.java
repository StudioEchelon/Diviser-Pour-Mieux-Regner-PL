package fr.dpmr.command;

import fr.dpmr.i18n.GameLocale;
import fr.dpmr.i18n.I18n;
import fr.dpmr.pass.ConquerPassGui;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ConquerPassCommand implements CommandExecutor {

    private final ConquerPassGui gui;

    public ConquerPassCommand(ConquerPassGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(I18n.component(GameLocale.EN, NamedTextColor.RED, "conquerpass.players_only"));
            return true;
        }
        gui.open(player);
        return true;
    }
}
