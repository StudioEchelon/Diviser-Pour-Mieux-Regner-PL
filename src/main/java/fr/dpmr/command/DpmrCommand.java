package fr.dpmr.command;

import fr.dpmr.game.ApocalypseManager;
import fr.dpmr.game.BandageManager;
import fr.dpmr.game.DpmrConsumable;
import fr.dpmr.game.DynamicObjectiveManager;
import fr.dpmr.game.LaunchpadManager;
import fr.dpmr.game.LaunchpadStyle;
import fr.dpmr.game.BalloonChestManager;
import fr.dpmr.game.LootManager;
import fr.dpmr.game.ModificationTableRegistry;
import fr.dpmr.game.FamiliarPetManager;
import fr.dpmr.game.SafeRespawnManager;
import fr.dpmr.game.MedkitStationManager;
import fr.dpmr.game.CaptureMoneyZoneManager;
import fr.dpmr.game.PokerCard;
import fr.dpmr.game.PokerCardManager;
import fr.dpmr.game.PokerRank;
import fr.dpmr.game.PokerSuit;
import fr.dpmr.game.PowerupBlockManager;
import fr.dpmr.game.PetType;
import fr.dpmr.game.WeaponKillPerkState;
import fr.dpmr.game.WeaponManager;
import fr.dpmr.game.WeaponProfile;
import fr.dpmr.gui.WeaponKillPerkGui;
import fr.dpmr.crate.CrateManager;
import fr.dpmr.npc.NpcSpawnerManager;
import fr.dpmr.resourcepack.ResourcePackManager;
import fr.dpmr.vault.VaultManager;
import fr.dpmr.armor.ArmorManager;
import fr.dpmr.armor.ArmorProfile;
import fr.dpmr.cosmetics.SkinGui;
import fr.dpmr.gui.GiveGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DpmrCommand implements CommandExecutor, TabCompleter {

    private final ApocalypseManager apocalypseManager;
    private final LootManager lootManager;
    private final BalloonChestManager balloonChestManager;
    private final WeaponManager weaponManager;
    private final BandageManager bandageManager;
    private final GiveGui giveGui;
    private final ModificationTableRegistry modTableRegistry;
    private final LaunchpadManager launchpadManager;
    private final fr.dpmr.zone.ZoneManager zoneManager;
    private final FamiliarPetManager familiarPetManager;
    private final CrateManager crateManager;
    private final NpcSpawnerManager npcSpawnerManager;
    private final ArmorManager armorManager;
    private final SafeRespawnManager safeRespawnManager;
    private final ResourcePackManager resourcePackManager;
    private final MedkitStationManager medkitStationManager;
    private final CaptureMoneyZoneManager captureMoneyZoneManager;
    private final PowerupBlockManager powerupBlockManager;
    private final PokerCardManager pokerCardManager;
    private final VaultManager vaultManager;
    private final SkinGui skinGui;
    private final WeaponKillPerkGui weaponKillPerkGui;

    public DpmrCommand(ApocalypseManager apocalypseManager, LootManager lootManager, BalloonChestManager balloonChestManager,
                       WeaponManager weaponManager,
                       BandageManager bandageManager, GiveGui giveGui, ModificationTableRegistry modTableRegistry,
                       LaunchpadManager launchpadManager, fr.dpmr.zone.ZoneManager zoneManager, FamiliarPetManager familiarPetManager,
                       CrateManager crateManager, NpcSpawnerManager npcSpawnerManager, ArmorManager armorManager,
                       SafeRespawnManager safeRespawnManager,
                       ResourcePackManager resourcePackManager, MedkitStationManager medkitStationManager,
                       CaptureMoneyZoneManager captureMoneyZoneManager, PowerupBlockManager powerupBlockManager,
                       PokerCardManager pokerCardManager,
                       VaultManager vaultManager,
                       SkinGui skinGui,
                       WeaponKillPerkGui weaponKillPerkGui) {
        this.apocalypseManager = apocalypseManager;
        this.lootManager = lootManager;
        this.balloonChestManager = balloonChestManager;
        this.weaponManager = weaponManager;
        this.bandageManager = bandageManager;
        this.giveGui = giveGui;
        this.modTableRegistry = modTableRegistry;
        this.launchpadManager = launchpadManager;
        this.zoneManager = zoneManager;
        this.familiarPetManager = familiarPetManager;
        this.crateManager = crateManager;
        this.npcSpawnerManager = npcSpawnerManager;
        this.armorManager = armorManager;
        this.safeRespawnManager = safeRespawnManager;
        this.resourcePackManager = resourcePackManager;
        this.medkitStationManager = medkitStationManager;
        this.captureMoneyZoneManager = captureMoneyZoneManager;
        this.powerupBlockManager = powerupBlockManager;
        this.pokerCardManager = pokerCardManager;
        this.vaultManager = vaultManager;
        this.skinGui = skinGui;
        this.weaponKillPerkGui = weaponKillPerkGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("skin")) {
            if (!sender.hasPermission("dpmr.skin")) {
                sender.sendMessage(Component.text("Pas la permission.", NamedTextColor.RED));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Joueurs uniquement.", NamedTextColor.RED));
                return true;
            }
            skinGui.openThompson(player);
            return true;
        }

        if (!sender.hasPermission("dpmr.admin")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /dpmr <start|stop|status|...|killperk|skin|killnpcs|...>", NamedTextColor.YELLOW));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                apocalypseManager.startGame();
                sender.sendMessage(Component.text("Apocalypse mode is on.", NamedTextColor.GREEN));
            }
            case "stop" -> {
                apocalypseManager.stopGame();
                sender.sendMessage(Component.text("Apocalypse mode is off.", NamedTextColor.GRAY));
            }
            case "status" -> {
                boolean running = apocalypseManager.isGameRunning();
                sender.sendMessage(Component.text(
                        "Etat actuel: " + (running ? "EN COURS" : "ARRETE"),
                        running ? NamedTextColor.RED : NamedTextColor.GRAY
                ));
                DynamicObjectiveManager dom = apocalypseManager.getDynamicObjectiveManager();
                if (dom != null) {
                    sender.sendMessage(Component.text(dom.getStatusSummary(), NamedTextColor.DARK_AQUA));
                }
            }
            case "forceevent" -> {
                apocalypseManager.forceDisaster();
                sender.sendMessage(Component.text("Evenement catastrophe force.", NamedTextColor.LIGHT_PURPLE));
            }
            case "airdrop" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("set")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                        return true;
                    }
                    lootManager.setAirdropLocation(player);
                    sender.sendMessage(Component.text("Position airdrop enregistree.", NamedTextColor.GREEN));
                    return true;
                }
                lootManager.spawnAirdrop();
                sender.sendMessage(Component.text("Airdrop force.", NamedTextColor.AQUA));
            }
            case "forcechest" -> {
                lootManager.forceSpawnZoneChest();
                sender.sendMessage(Component.text("Attempting zone chest spawn.", NamedTextColor.GREEN));
            }
            case "balloonchest" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (balloonChestManager.forceSpawnNear(player)) {
                    sender.sendMessage(Component.text("Coffre ballon spawn (au-dessus de la zone).", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Impossible de placer un coffre ballon (monde / position).", NamedTextColor.RED));
                }
            }
            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                safeRespawnManager.setSpawn(player.getLocation());
                sender.sendMessage(Component.text("DPMR respawn point set here.", NamedTextColor.GREEN));
            }
            case "givebandage" -> {
                int amount = 1;
                String targetName = null;
                if (args.length >= 2) {
                    try {
                        amount = Math.max(1, Integer.parseInt(args[1]));
                        if (args.length >= 3) {
                            targetName = args[2];
                        }
                    } catch (NumberFormatException ignored) {
                        targetName = args[1];
                    }
                }
                Player target;
                if (targetName != null) {
                    target = Bukkit.getPlayerExact(targetName);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("Usage: /dpmr givebandage [amount] <player>", NamedTextColor.YELLOW));
                    return true;
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(bandageManager.createBandage(amount));
                sender.sendMessage(Component.text("Bandages given to " + target.getName() + " x" + amount, NamedTextColor.GREEN));
            }
            case "giveconsumable" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr giveconsumable <type> [amount] [player]", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("Ex: bandage-small, bandage-medium, medikit, shield-potion-large", NamedTextColor.GRAY));
                    return true;
                }
                DpmrConsumable ct = DpmrConsumable.fromConfigKey(args[1]);
                if (ct == null) {
                    try {
                        ct = DpmrConsumable.valueOf(args[1].toUpperCase(Locale.ROOT).replace('-', '_'));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(Component.text("Type inconnu: " + args[1], NamedTextColor.RED));
                        return true;
                    }
                }
                int amount = 1;
                String targetName = null;
                if (args.length >= 3) {
                    try {
                        amount = Math.max(1, Integer.parseInt(args[2]));
                        if (args.length >= 4) {
                            targetName = args[3];
                        }
                    } catch (NumberFormatException ignored) {
                        targetName = args[2];
                    }
                }
                Player target;
                if (targetName != null) {
                    target = Bukkit.getPlayerExact(targetName);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("Usage: /dpmr giveconsumable <type> [amount] <player>", NamedTextColor.YELLOW));
                    return true;
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(bandageManager.createConsumable(ct, amount));
                sender.sendMessage(Component.text("Objet " + ct.configKey() + " donne a " + target.getName() + " x" + amount, NamedTextColor.GREEN));
            }
            case "givepokercard" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr givepokercard <carte> [amount] [player]", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("Ex: hearts-ace, spades-king, clubs-jack, joker-red, joker-black", NamedTextColor.GRAY));
                    return true;
                }
                PokerCard card = PokerCard.fromStorageKey(args[1].replace('-', '_'));
                if (card == null) {
                    sender.sendMessage(Component.text("Cle carte inconnue: " + args[1], NamedTextColor.RED));
                    return true;
                }
                int amount = 1;
                String targetName = null;
                if (args.length >= 3) {
                    try {
                        amount = Math.max(1, Integer.parseInt(args[2]));
                        if (args.length >= 4) {
                            targetName = args[3];
                        }
                    } catch (NumberFormatException ignored) {
                        targetName = args[2];
                    }
                }
                Player target;
                if (targetName != null) {
                    target = Bukkit.getPlayerExact(targetName);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("Usage: /dpmr givepokercard <carte> [amount] <player>", NamedTextColor.YELLOW));
                    return true;
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(pokerCardManager.createCard(card, amount));
                sender.sendMessage(Component.text("Carte " + card.storageKey() + " -> " + target.getName() + " x" + amount, NamedTextColor.GREEN));
            }
            case "givechest" -> {
                int tier = 1;
                int amount = 1;
                String targetName = null;
                int idx = 1;
                if (args.length >= 2) {
                    String a = args[1].toLowerCase(Locale.ROOT);
                    if ("1".equals(a) || "2".equals(a) || "3".equals(a)) {
                        tier = Integer.parseInt(a);
                        idx = 2;
                    } else if ("4".equals(a) || "maritime".equals(a) || "maritime1".equals(a) || "m1".equals(a)) {
                        tier = 4;
                        idx = 2;
                    } else if ("5".equals(a) || "maritime2".equals(a) || "m2".equals(a)) {
                        tier = 5;
                        idx = 2;
                    } else if ("6".equals(a) || "maritime3".equals(a) || "m3".equals(a)) {
                        tier = 6;
                        idx = 2;
                    }
                }
                if (args.length > idx) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[idx])));
                        idx++;
                    } catch (NumberFormatException ignored) {
                        targetName = args[idx];
                        idx++;
                    }
                }
                if (args.length > idx) {
                    targetName = args[idx];
                }
                Player target;
                if (targetName != null) {
                    target = Bukkit.getPlayerExact(targetName);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("Usage: /dpmr givechest [1|2|3|4|5|6|maritime|maritime2|maritime3] [amount] <player>", NamedTextColor.YELLOW));
                    return true;
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(lootManager.createPortableLootChestItem(amount, tier));
                String chestKind = switch (tier) {
                    case 4 -> "Maritime I";
                    case 5 -> "Maritime II";
                    case 6 -> "Maritime III";
                    default -> "tier " + tier;
                };
                sender.sendMessage(Component.text("Loot chest (" + chestKind + ") → " + target.getName() + " x" + amount, NamedTextColor.GREEN));
            }
            case "giveaxechest" -> {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("Usage: /dpmr giveaxechest [player]", NamedTextColor.YELLOW));
                    return true;
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(lootManager.createChestBreakerAxeItem());
                sender.sendMessage(Component.text("AxeChest given to " + target.getName(), NamedTextColor.GREEN));
            }
            case "clearholograms" -> {
                int removed = lootManager.purgeAllLootHolograms();
                sender.sendMessage(Component.text("Hologrammes supprimes: " + removed, NamedTextColor.GREEN));
            }
            case "menu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                lootManager.openAdminMenu(player);
            }
            case "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                giveGui.openMain(player);
            }
            case "guns" -> sender.sendMessage(Component.text("Guns: " + String.join(", ", weaponManager.getWarfareWeaponIds()), NamedTextColor.AQUA));
            case "launchpads" -> sender.sendMessage(Component.text(
                    "Styles launchpad: " + String.join(", ", launchpadManager.getAllStyleIds()),
                    NamedTextColor.GREEN));
            case "endportal" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr endportal <add|set|remove <index>|list|clear|off>", NamedTextColor.YELLOW));
                    return true;
                }
                String action = args[1].toLowerCase(Locale.ROOT);
                if (action.equals("add")) {
                    int count = launchpadManager.addEndPortalTarget(player);
                    if (count < 0) {
                        sender.sendMessage(Component.text("Limite atteinte: 10 zones max.", NamedTextColor.RED));
                        return true;
                    }
                    sender.sendMessage(Component.text("Zone EndPortal ajoutee (" + count + "/10).", NamedTextColor.GREEN));
                    return true;
                }
                if (action.equals("set")) {
                    launchpadManager.setEndPortalTarget(player);
                    sender.sendMessage(Component.text("Single EndPortal zone set (1/10).", NamedTextColor.GREEN));
                    return true;
                }
                if (action.equals("list")) {
                    List<org.bukkit.Location> list = launchpadManager.listEndPortalTargets();
                    sender.sendMessage(Component.text("Zones EndPortal: " + list.size() + "/10", NamedTextColor.AQUA));
                    for (int i = 0; i < list.size(); i++) {
                        org.bukkit.Location l = list.get(i);
                        String world = l.getWorld() != null ? l.getWorld().getName() : "world";
                        sender.sendMessage(Component.text(
                                (i + 1) + ". " + world + " " + (int) l.getX() + " " + (int) l.getY() + " " + (int) l.getZ(),
                                NamedTextColor.GRAY));
                    }
                    return true;
                }
                if (action.equals("remove")) {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("Usage: /dpmr endportal remove <index>", NamedTextColor.YELLOW));
                        return true;
                    }
                    int idx;
                    try {
                        idx = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("Index invalide.", NamedTextColor.RED));
                        return true;
                    }
                    if (!launchpadManager.removeEndPortalTarget(idx)) {
                        sender.sendMessage(Component.text("Index not found.", NamedTextColor.RED));
                        return true;
                    }
                    sender.sendMessage(Component.text("Zone EndPortal retiree.", NamedTextColor.YELLOW));
                    return true;
                }
                if (action.equals("clear")) {
                    launchpadManager.clearEndPortalTargets();
                    sender.sendMessage(Component.text("All EndPortal zones cleared.", NamedTextColor.YELLOW));
                    return true;
                }
                if (action.equals("off")) {
                    launchpadManager.disableEndPortalTarget();
                    sender.sendMessage(Component.text("End portal markers disabled.", NamedTextColor.GRAY));
                    return true;
                }
                sender.sendMessage(Component.text("Actions: add | set | remove | list | clear | off", NamedTextColor.RED));
            }
            case "givelaunchpad" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr givelaunchpad <style> [amount] [player]", NamedTextColor.YELLOW));
                    return true;
                }
                LaunchpadStyle lp = LaunchpadStyle.fromId(args[1]);
                if (lp == null) {
                    sender.sendMessage(Component.text("Unknown style. /dpmr launchpads", NamedTextColor.RED));
                    return true;
                }
                int amount = 8;
                String targetName = null;
                if (args.length >= 3) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
                        if (args.length >= 4) {
                            targetName = args[3];
                        }
                    } catch (NumberFormatException ignored) {
                        targetName = args[2];
                    }
                }
                Player target;
                if (targetName != null) {
                    target = Bukkit.getPlayerExact(targetName);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("Specify a player from the console.", NamedTextColor.RED));
                    return true;
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(launchpadManager.createItem(lp, amount));
                sender.sendMessage(Component.text("Launchpads " + lp.id() + " x" + amount + " -> " + target.getName(), NamedTextColor.GREEN));
            }
            case "modtable" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr modtable <set|unset|list> (look at a block)", NamedTextColor.YELLOW));
                    return true;
                }
                String m = args[1].toLowerCase(Locale.ROOT);
                if (m.equals("list")) {
                    sender.sendMessage(Component.text("Armory benches: " + modTableRegistry.count(), NamedTextColor.AQUA));
                    for (String k : modTableRegistry.listKeys()) {
                        sender.sendMessage(Component.text(" - " + k, NamedTextColor.GRAY));
                    }
                    return true;
                }
                Block target = player.getTargetBlockExact(6);
                if (target == null || target.getType().isAir()) {
                    sender.sendMessage(Component.text("Look at a solid block (within 6 blocks).", NamedTextColor.RED));
                    return true;
                }
                if (m.equals("set")) {
                    if (modTableRegistry.add(target)) {
                        sender.sendMessage(Component.text("Armory bench registered here.", NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("Already registered.", NamedTextColor.GRAY));
                    }
                } else if (m.equals("unset")) {
                    if (modTableRegistry.remove(target)) {
                        sender.sendMessage(Component.text("Bench removed.", NamedTextColor.YELLOW));
                    } else {
                        sender.sendMessage(Component.text("Not a registered bench.", NamedTextColor.GRAY));
                    }
                } else {
                    sender.sendMessage(Component.text("set | unset | list", NamedTextColor.RED));
                }
            }
            case "givegun" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr givegun <id> [player]", NamedTextColor.YELLOW));
                    return true;
                }
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("Specify a player from the console.", NamedTextColor.RED));
                    return true;
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                var wp = fr.dpmr.game.WeaponProfile.fromId(args[1]);
                var gun = weaponManager.createWeaponItem(args[1], target);
                if (gun == null || wp == null) {
                    sender.sendMessage(Component.text(
                            "ID d'arme inconnu. /dpmr guns (combat) ou tabulation pour la liste complete.",
                            NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(gun);
                sender.sendMessage(Component.text("Weapon given to " + target.getName(), NamedTextColor.GREEN));
            }
            case "givearmor" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr givearmor <ASSAULT|HEAVY|BREACHER|MARKSMAN|EOD> [player]", NamedTextColor.YELLOW));
                    return true;
                }
                ArmorProfile profile = ArmorProfile.fromId(args[1]);
                if (profile == null) {
                    sender.sendMessage(Component.text("Profil armure invalide.", NamedTextColor.RED));
                    return true;
                }
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("Specify a player from the console.", NamedTextColor.RED));
                    return true;
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(armorManager.createArmorSet(profile).toArray(ItemStack[]::new));
                sender.sendMessage(Component.text("Set armure " + profile.name() + " donne a " + target.getName(), NamedTextColor.GREEN));
            }
            case "zone" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr zone <wand|safe|war|info|clearpoints|pointinfo> ...", NamedTextColor.YELLOW));
                    return true;
                }
                String t = args[1].toLowerCase(Locale.ROOT);
                if (t.equals("info")) {
                    zoneManager.describeTo(player);
                    return true;
                }
                if (t.equals("clearpoints")) {
                    zoneManager.clearPolyPoints(player);
                    return true;
                }
                if (t.equals("pointinfo")) {
                    sender.sendMessage(Component.text("Points selectionnes: " + zoneManager.getPolyPointCount(player), NamedTextColor.AQUA));
                    return true;
                }
                if (t.equals("wand")) {
                    player.getInventory().addItem(zoneManager.createZoneWand("sel", 0));
                    sender.sendMessage(Component.text("Selection wand given (WorldEdit-style).", NamedTextColor.GREEN));
                    return true;
                }
                if (!t.equals("safe") && !t.equals("war")) {
                    sender.sendMessage(Component.text("wand | safe | war | info | clearpoints | pointinfo", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /dpmr zone " + t + " <set <rayon>|setsel|setpoly|delete>", NamedTextColor.YELLOW));
                    return true;
                }
                String action = args[2].toLowerCase(Locale.ROOT);
                if (action.equals("delete")) {
                    if (t.equals("safe")) {
                        zoneManager.deleteSafeZone(player);
                    } else {
                        zoneManager.deleteWarZone(player);
                    }
                    return true;
                }
                if (action.equals("setsel")) {
                    if (t.equals("safe")) {
                        zoneManager.setSafeZoneFromSelection(player);
                    } else {
                        zoneManager.setWarZoneFromSelection(player);
                    }
                    return true;
                }
                if (action.equals("setpoly")) {
                    if (t.equals("safe")) {
                        zoneManager.setSafeZoneFromPoly(player);
                    } else {
                        zoneManager.setWarZoneFromPoly(player);
                    }
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /dpmr zone " + t + " " + action + " <rayon>", NamedTextColor.YELLOW));
                    return true;
                }
                double r;
                try {
                    r = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Rayon invalide.", NamedTextColor.RED));
                    return true;
                }
                if (action.equals("set")) {
                    if (t.equals("safe")) {
                        zoneManager.setSafeZone(player, r);
                    } else {
                        zoneManager.setWarZone(player, r);
                    }
                    return true;
                }
                sender.sendMessage(Component.text("Actions: set | setsel | setpoly | delete", NamedTextColor.RED));
            }
            case "pet" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr pet <gunner|medic|sniper|scout|brute|dismiss>", NamedTextColor.YELLOW));
                    return true;
                }
                String a = args[1].toLowerCase(Locale.ROOT);
                if (a.equals("dismiss")) {
                    familiarPetManager.dismiss(player.getUniqueId());
                    player.sendMessage(Component.text("Familier retire.", NamedTextColor.GRAY));
                    return true;
                }
                PetType type = PetType.fromArg(a);
                if (type != null) {
                    familiarPetManager.summon(player, type);
                    return true;
                }
                sender.sendMessage(Component.text("Types: gunner medic sniper scout brute — dismiss to remove", NamedTextColor.RED));
            }
            case "crate" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr crate <create|delete|list|key> ...", NamedTextColor.YELLOW));
                    return true;
                }
                String action = args[1].toLowerCase(Locale.ROOT);
                if (action.equals("list")) {
                    sender.sendMessage(Component.text("Caisses: " + String.join(", ", crateManager.listCrates()), NamedTextColor.AQUA));
                    return true;
                }
                if (action.equals("create")) {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("Usage: /dpmr crate create <id>", NamedTextColor.YELLOW));
                        return true;
                    }
                    Block b = player.getTargetBlockExact(6);
                    if (b == null || b.getType().isAir()) {
                        sender.sendMessage(Component.text("Look at a valid block (within 6 blocks).", NamedTextColor.RED));
                        return true;
                    }
                    crateManager.createCrate(player, args[2], b.getLocation());
                    return true;
                }
                if (action.equals("delete")) {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("Usage: /dpmr crate delete <id>", NamedTextColor.YELLOW));
                        return true;
                    }
                    crateManager.deleteCrate(player, args[2]);
                    return true;
                }
                if (action.equals("key")) {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("Usage: /dpmr crate key <id> [amount]", NamedTextColor.YELLOW));
                        return true;
                    }
                    int amount = 1;
                    if (args.length >= 4) {
                        try {
                            amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                        } catch (NumberFormatException e) {
                            sender.sendMessage(Component.text("Nombre invalide.", NamedTextColor.RED));
                            return true;
                        }
                    }
                    player.getInventory().addItem(crateManager.createKey(args[2], amount));
                    sender.sendMessage(Component.text("Keys given: " + args[2] + " x" + amount, NamedTextColor.GREEN));
                    return true;
                }
                sender.sendMessage(Component.text("Actions crate: create | delete | list | key", NamedTextColor.RED));
            }
            case "killnpcs" -> {
                int removed = npcSpawnerManager.killAllSpawnedFakeNpcs();
                sender.sendMessage(Component.text("PNJ DPMR retires: " + removed, NamedTextColor.GREEN));
            }
            case "warworld" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("reload")) {
                    sender.sendMessage(Component.text("Usage: /dpmr warworld reload", NamedTextColor.YELLOW));
                    return true;
                }
                resourcePackManager.restartLocalPackServerAfterConfigReload();
                medkitStationManager.reloadFromConfig();
                powerupBlockManager.reloadFromConfig();
                pokerCardManager.reloadFromConfig();
                vaultManager.reloadFromConfig();
                sender.sendMessage(Component.text("config.yml recharge + resource pack (local/manifest) + bonus power-ups + cartes poker + coffre-fort.", NamedTextColor.GREEN));
            }
            case "medkit" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr medkit <create|delete|list>", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("Regarde le bloc ancre (config: medkit-station.anchor-material).", NamedTextColor.GRAY));
                    return true;
                }
                String ma = args[1].toLowerCase(Locale.ROOT);
                if (ma.equals("list")) {
                    medkitStationManager.listTo(player);
                    return true;
                }
                Block b = player.getTargetBlockExact(6);
                if (ma.equals("create")) {
                    medkitStationManager.createAtBlock(player, b);
                    return true;
                }
                if (ma.equals("delete")) {
                    medkitStationManager.deleteAtBlock(player, b);
                    return true;
                }
                sender.sendMessage(Component.text("create | delete | list", NamedTextColor.RED));
            }
            case "capturezone" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr capturezone <create|delete|list> ...", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("create <id> [rayon] — centre = ta position ; 1 joueur a la fois gagne des points.", NamedTextColor.GRAY));
                    return true;
                }
                String ca = args[1].toLowerCase(Locale.ROOT);
                if (ca.equals("list")) {
                    sender.sendMessage(Component.text("Zones: " + String.join(", ", captureMoneyZoneManager.listIds()), NamedTextColor.AQUA));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /dpmr capturezone create <id> [rayon] | delete <id>", NamedTextColor.YELLOW));
                    return true;
                }
                if (ca.equals("delete")) {
                    captureMoneyZoneManager.delete(player, args[2]);
                    return true;
                }
                if (ca.equals("create")) {
                    double rad = captureMoneyZoneManager.configuredDefaultRadius();
                    if (args.length >= 4) {
                        try {
                            rad = Double.parseDouble(args[3]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(Component.text("Rayon invalide.", NamedTextColor.RED));
                            return true;
                        }
                    }
                    captureMoneyZoneManager.create(player, args[2], rad);
                    return true;
                }
                sender.sendMessage(Component.text("create | delete | list", NamedTextColor.RED));
            }
            case "resourcepack" -> resourcePackManager.sendAdminDiagnostics(sender);
            case "powerup" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /dpmr powerup <add|remove|list> ...", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("add <kind> [wall|float] — wall: viser un bloc ; float: orbe dans les airs devant toi", NamedTextColor.GRAY));
                    return true;
                }
                String pa = args[1].toLowerCase(Locale.ROOT);
                if (pa.equals("list")) {
                    var defs = powerupBlockManager.markerDefinitionsView();
                    if (defs.isEmpty()) {
                        sender.sendMessage(Component.text("No hologram markers (see powerup-markers.yml).", NamedTextColor.GRAY));
                        return true;
                    }
                    sender.sendMessage(Component.text("Power-up markers: " + defs.size(), NamedTextColor.AQUA));
                    for (var d : defs.values()) {
                        sender.sendMessage(Component.text(
                                " - " + d.id.substring(0, Math.min(8, d.id.length())) + "… " + d.kind + " "
                                        + d.mount + " @ " + d.worldName + " "
                                        + String.format(Locale.ROOT, "%.1f %.1f %.1f", d.x, d.y, d.z),
                                NamedTextColor.GRAY));
                    }
                    return true;
                }
                if (pa.equals("remove")) {
                    if (powerupBlockManager.removeMarkerPlayerLooksAt(player, 10)) {
                        sender.sendMessage(Component.text("Marker removed.", NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("Look at a power-up orb (or unknown id).", NamedTextColor.RED));
                    }
                    return true;
                }
                if (pa.equals("add")) {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("Usage: /dpmr powerup add <kind> [wall|float]", NamedTextColor.YELLOW));
                        sender.sendMessage(Component.text("Kinds: rapid-fire, invulnerability, kill-coins, bullet-shield, stealth", NamedTextColor.GRAY));
                        return true;
                    }
                    PowerupBlockManager.Kind kind = powerupBlockManager.parseKindString(args[2]);
                    if (kind == null) {
                        sender.sendMessage(Component.text("Unknown kind.", NamedTextColor.RED));
                        return true;
                    }
                    PowerupBlockManager.MountStyle mount = PowerupBlockManager.MountStyle.WALL;
                    if (args.length >= 4 && args[3].equalsIgnoreCase("float")) {
                        mount = PowerupBlockManager.MountStyle.FLOAT;
                    } else if (args.length >= 4 && args[3].equalsIgnoreCase("wall")) {
                        mount = PowerupBlockManager.MountStyle.WALL;
                    }
                    PowerupBlockManager.MarkerDefinition def = powerupBlockManager.createDefinitionFromPlayerLook(player, kind, mount);
                    if (def == null) {
                        sender.sendMessage(Component.text("Wall mode needs a solid block in sight (use float for air).", NamedTextColor.RED));
                        return true;
                    }
                    powerupBlockManager.addMarker(def);
                    sender.sendMessage(Component.text(
                            "Added " + kind + " marker (" + def.mount + "). id=" + def.id.substring(0, 8) + "…",
                            NamedTextColor.GREEN));
                    return true;
                }
                sender.sendMessage(Component.text("add | remove | list", NamedTextColor.RED));
            }
            default ->             case "killperk" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Joueurs uniquement.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2 || !args[1].equalsIgnoreCase("fill")) {
                    sender.sendMessage(Component.text(
                            "Usage: /dpmr killperk fill [gui] — jauge évolution 100 % sur l''arme en main ; gui = ouvrir le coffre.",
                            NamedTextColor.YELLOW));
                    return true;
                }
                boolean openGui = args.length >= 3 && args[2].equalsIgnoreCase("gui");
                ItemStack main = player.getInventory().getItemInMainHand();
                weaponManager.touchWeaponInstanceId(player, main);
                String wid = weaponManager.readWeaponId(main);
                WeaponProfile w = wid != null ? WeaponProfile.fromId(wid) : null;
                var pl = weaponManager.getPlugin();
                if (w == null || !w.supportsKillPerkMeter()) {
                    sender.sendMessage(Component.text(
                            "Tiens une arme rayonnée DPMR (hitscan / sniper arc / pompe croix) en main principale.",
                            NamedTextColor.RED));
                    return true;
                }
                if (WeaponKillPerkState.isMaxed(main, pl)) {
                    sender.sendMessage(Component.text("Cette arme a déjà 3 améliorations kill-perk.", NamedTextColor.RED));
                    return true;
                }
                if (!WeaponKillPerkState.enabled(pl)) {
                    sender.sendMessage(Component.text(
                            "Astuce : weapons.kill-perks.enabled est false — la jauge ne s''affiche pas en jeu tant que c''est désactivé.",
                            NamedTextColor.YELLOW));
                }
                WeaponKillPerkState.setMeter(main, WeaponKillPerkState.METER_MAX, pl);
                weaponManager.refreshWeaponMeta(main, player);
                sender.sendMessage(Component.text("Jauge évolution : 100 %.", NamedTextColor.GREEN));
                if (openGui) {
                    String inst = weaponManager.readWeaponInstanceId(main);
                    if (inst != null && !inst.isBlank()) {
                        weaponKillPerkGui.open(player, inst);
                    }
                }
            }
            default -> sender.sendMessage(Component.text("Sous-commande inconnue.", NamedTextColor.RED));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("start", "stop", "status", "forceevent", "menu", "gui", "airdrop", "forcechest", "balloonchest", "setspawn", "givebandage", "giveconsumable", "givepokercard", "givechest", "giveaxechest",
                            "clearholograms",
                            "endportal",
                            "givegun", "guns", "givearmor", "givelaunchpad", "launchpads", "modtable", "zone", "pet", "crate", "killnpcs", "warworld", "resourcepack",
                            "medkit", "capturezone", "powerup", "killperk", "skin").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("killperk")) {
            return List.of("fill").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("killperk") && args[1].equalsIgnoreCase("fill")) {
            return List.of("gui").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("endportal")) {
            return List.of("add", "set", "remove", "list", "clear", "off").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("endportal") && args[1].equalsIgnoreCase("remove")) {
            int n = launchpadManager.listEndPortalTargets().size();
            java.util.List<String> out = new java.util.ArrayList<>();
            for (int i = 1; i <= n; i++) {
                out.add(String.valueOf(i));
            }
            return out.stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("warworld")) {
            return List.of("reload").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("crate")) {
            return List.of("create", "delete", "list", "key").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("crate") && (args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("key"))) {
            return crateManager.listCrates().stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("airdrop")) {
            return List.of("set").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("zone")) {
            return List.of("wand", "safe", "war", "info", "clearpoints", "pointinfo").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("zone") && !args[1].equalsIgnoreCase("info")) {
            return List.of("set", "setsel", "setpoly", "delete").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pet")) {
            return List.of("gunner", "medic", "sniper", "scout", "brute", "dismiss").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("givechest")) {
            return List.of("1", "2", "3", "4", "5", "6", "maritime", "maritime2", "maritime3").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("giveconsumable")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            return java.util.Arrays.stream(DpmrConsumable.values())
                    .map(DpmrConsumable::configKey)
                    .filter(s -> s.startsWith(p))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("givepokercard")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            List<String> keys = new ArrayList<>();
            for (PokerSuit s : PokerSuit.values()) {
                for (PokerRank r : PokerRank.values()) {
                    keys.add(s.name().toLowerCase(Locale.ROOT) + "-" + r.name().toLowerCase(Locale.ROOT));
                }
            }
            keys.add("joker-red");
            keys.add("joker-black");
            return keys.stream()
                    .filter(k -> k.startsWith(p))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("givegun")) {
            return weaponManager.getAllWeaponIds().stream()
                    .map(String::toLowerCase)
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("givearmor")) {
            return java.util.Arrays.stream(ArmorProfile.values())
                    .map(Enum::name)
                    .map(String::toLowerCase)
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("givelaunchpad")) {
            return launchpadManager.getAllStyleIds().stream()
                    .map(String::toLowerCase)
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("modtable")) {
            return List.of("set", "unset", "list").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("medkit")) {
            return List.of("create", "delete", "list").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("capturezone")) {
            return List.of("create", "delete", "list").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("capturezone") && args[1].equalsIgnoreCase("delete")) {
            return captureMoneyZoneManager.listIds().stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("powerup")) {
            return List.of("add", "remove", "list").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("powerup") && args[1].equalsIgnoreCase("add")) {
            String p = args[2].toLowerCase(Locale.ROOT);
            return List.of("rapid-fire", "invulnerability", "kill-coins", "bullet-shield", "stealth").stream()
                    .filter(s -> s.startsWith(p))
                    .toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("powerup") && args[1].equalsIgnoreCase("add")) {
            String p = args[3].toLowerCase(Locale.ROOT);
            return List.of("wall", "float").stream()
                    .filter(s -> s.startsWith(p))
                    .toList();
        }
        return List.of();
    }
}
