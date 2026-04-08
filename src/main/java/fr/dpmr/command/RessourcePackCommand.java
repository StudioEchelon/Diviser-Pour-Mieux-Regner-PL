package fr.dpmr.command;

import fr.dpmr.resourcepack.ResourcePackManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RessourcePackCommand implements CommandExecutor {

    private final ResourcePackManager rpm;

    public RessourcePackCommand(ResourcePackManager rpm) {
        this.rpm = rpm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        rpm.sendPackAfterConfigReload(player);
        player.sendMessage(Component.text("config.yml relu + pack renvoye (anti-cache). Pense a mettre a jour resource-pack.sha1 apres chaque nouveau zip.", NamedTextColor.GRAY));
        return true;
    }
}

