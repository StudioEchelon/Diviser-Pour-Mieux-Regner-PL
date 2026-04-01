package fr.dpmr;

import fr.dpmr.i18n.I18n;
import fr.dpmr.i18n.PlayerLanguageStore;
import fr.dpmr.command.LangCommand;
import fr.dpmr.command.CosmeticsCommand;
import fr.dpmr.command.SkinCommand;
import fr.dpmr.command.ArmesCommand;
import fr.dpmr.command.BoutiqueCommand;
import fr.dpmr.clan.ClanMenuListener;
import fr.dpmr.command.ClanCommand;
import fr.dpmr.command.DpmrCommand;
import fr.dpmr.command.KillDpmNpcsCommand;
import fr.dpmr.command.HologramTopCommand;
import fr.dpmr.command.HdvCommand;
import fr.dpmr.command.PointsCommand;
import fr.dpmr.command.ProfileCommand;
import fr.dpmr.command.RessourcePackCommand;
import fr.dpmr.command.TopCommand;
import fr.dpmr.cosmetics.CosmeticsGui;
import fr.dpmr.cosmetics.CosmeticsManager;
import fr.dpmr.cosmetics.SkinGui;
import fr.dpmr.cosmetics.WeaponSkinListener;
import fr.dpmr.chat.ChatFormatListener;
import fr.dpmr.crate.CrateManager;
import fr.dpmr.crate.LootboxManager;
import fr.dpmr.command.LootboxCommand;
import fr.dpmr.data.ClanManager;
import fr.dpmr.data.PointsManager;
import fr.dpmr.game.ApocalypseManager;
import fr.dpmr.game.BountyManager;
import fr.dpmr.game.AutoFeedManager;
import fr.dpmr.game.CaptureMoneyZoneManager;
import fr.dpmr.game.MedkitStationManager;
import fr.dpmr.game.BandageManager;
import fr.dpmr.game.DynamicObjectiveManager;
import fr.dpmr.game.SafeRespawnManager;
import fr.dpmr.game.GameScoreboard;
import fr.dpmr.game.LaunchpadManager;
import fr.dpmr.game.LootManager;
import fr.dpmr.game.TopHologramManager;
import fr.dpmr.game.ModificationTableListener;
import fr.dpmr.game.ModificationTableRegistry;
import fr.dpmr.game.FamiliarPetManager;
import fr.dpmr.game.PlayerHealthDisplayManager;
import fr.dpmr.game.RadarManager;
import fr.dpmr.game.WallVisionManager;
import fr.dpmr.game.PokerCardManager;
import fr.dpmr.game.PowerupBlockManager;
import fr.dpmr.game.WeaponManager;
import fr.dpmr.gui.GiveGui;
import fr.dpmr.gui.ShopGui;
import fr.dpmr.gui.WeaponBrowseGui;
import fr.dpmr.hdv.HdvGui;
import fr.dpmr.hdv.HdvManager;
import fr.dpmr.resourcepack.ResourcePackManager;
import fr.dpmr.profile.PlayerProfileManager;
import fr.dpmr.profile.ProfileGui;
import fr.dpmr.quest.DailyQuestGui;
import fr.dpmr.quest.DailyQuestManager;
import fr.dpmr.command.DailyQuestCommand;
import fr.dpmr.command.ConquerPassCommand;
import fr.dpmr.command.KitCommand;
import fr.dpmr.command.TrophiesCommand;
import fr.dpmr.kit.EvolvingKitManager;
import fr.dpmr.kit.KitProgressStore;
import fr.dpmr.pass.ConquerPassGui;
import fr.dpmr.pass.ConquerPassManager;
import fr.dpmr.mastery.MasteryNametagManager;
import fr.dpmr.trophy.TrophyManager;
import fr.dpmr.vault.VaultManager;
import fr.dpmr.npc.NpcSpawnerManager;
import fr.dpmr.npc.WarWorldNpcManager;
import fr.dpmr.armor.ArmorManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class DiviserPourMieuxRegnerPlugin extends JavaPlugin {

    private ApocalypseManager apocalypseManager;
    private PointsManager pointsManager;
    private ClanManager clanManager;
    private LootManager lootManager;
    private PowerupBlockManager powerupBlockManager;
    private PokerCardManager pokerCardManager;
    private WeaponManager weaponManager;
    private LaunchpadManager launchpadManager;
    private ModificationTableRegistry modificationTableRegistry;
    private BandageManager bandageManager;
    private MedkitStationManager medkitStationManager;
    private CaptureMoneyZoneManager captureMoneyZoneManager;
    private GameScoreboard gameScoreboard;
    private DynamicObjectiveManager dynamicObjectiveManager;
    private TopHologramManager topHologramManager;
    private CosmeticsManager cosmeticsManager;
    private fr.dpmr.zone.ZoneManager zoneManager;
    private FamiliarPetManager familiarPetManager;
    private HdvManager hdvManager;
    private ResourcePackManager resourcePackManager;
    private PlayerProfileManager playerProfileManager;
    private CrateManager crateManager;
    private LootboxManager lootboxManager;
    private SafeRespawnManager safeRespawnManager;
    private NpcSpawnerManager npcSpawnerManager;
    private WarWorldNpcManager warWorldNpcManager;
    private ArmorManager armorManager;
    private PlayerHealthDisplayManager playerHealthDisplayManager;
    private AutoFeedManager autoFeedManager;
    private RadarManager radarManager;
    private WallVisionManager wallVisionManager;
    private PlayerLanguageStore languageStore;
    private DailyQuestManager dailyQuestManager;
    private ConquerPassManager conquerPassManager;
    private VaultManager vaultManager;
    private KitProgressStore kitProgressStore;
    private EvolvingKitManager evolvingKitManager;
    private TrophyManager trophyManager;
    private MasteryNametagManager masteryNametagManager;
    private BountyManager bountyManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeConfigDefaultsFromJar();

        languageStore = new PlayerLanguageStore(this);
        I18n.init(this, languageStore);

        pointsManager = new PointsManager(this);
        armorManager = new ArmorManager(this);
        playerProfileManager = new PlayerProfileManager(this, pointsManager);
        pointsManager.setGainMultiplierProvider(playerProfileManager::pointsMultiplier);
        playerProfileManager.start();
        cosmeticsManager = new CosmeticsManager(this, pointsManager);
        clanManager = new ClanManager(this);
        trophyManager = new TrophyManager(this, pointsManager, clanManager);
        masteryNametagManager = new MasteryNametagManager(this, pointsManager);
        zoneManager = new fr.dpmr.zone.ZoneManager(this);
        powerupBlockManager = new PowerupBlockManager(this);
        pokerCardManager = new PokerCardManager(this, powerupBlockManager);
        weaponManager = new WeaponManager(this, cosmeticsManager, zoneManager, armorManager, languageStore, powerupBlockManager);
        powerupBlockManager.setMainHandWeaponTest(p -> weaponManager.isDpmrWeapon(p.getInventory().getItemInMainHand()));
        pointsManager.setKillRewardMultiplierProvider(powerupBlockManager::killRewardBonusMultiplier);
        radarManager = new RadarManager(this, weaponManager);
        wallVisionManager = new WallVisionManager(this);
        launchpadManager = new LaunchpadManager(this, cosmeticsManager);
        modificationTableRegistry = new ModificationTableRegistry(this);
        modificationTableRegistry.load();
        bandageManager = new BandageManager(this);
        medkitStationManager = new MedkitStationManager(this, bandageManager);
        medkitStationManager.start();
        captureMoneyZoneManager = new CaptureMoneyZoneManager(this, pointsManager);
        captureMoneyZoneManager.start();
        lootManager = new LootManager(this, weaponManager, bandageManager, armorManager, languageStore);
        crateManager = new CrateManager(this, pointsManager, weaponManager, bandageManager);
        lootboxManager = new LootboxManager(this, pointsManager, weaponManager, bandageManager);
        kitProgressStore = new KitProgressStore(this);
        evolvingKitManager = new EvolvingKitManager(this, kitProgressStore, bandageManager, weaponManager, pointsManager);
        evolvingKitManager.start();
        safeRespawnManager = new SafeRespawnManager(this);
        npcSpawnerManager = new NpcSpawnerManager(this, pointsManager, weaponManager);
        warWorldNpcManager = new WarWorldNpcManager(this, npcSpawnerManager, zoneManager, lootManager);
        warWorldNpcManager.start();
        autoFeedManager = new AutoFeedManager(this);
        autoFeedManager.start();
        playerHealthDisplayManager = new PlayerHealthDisplayManager();
        playerHealthDisplayManager.enable();
        familiarPetManager = new FamiliarPetManager(this, zoneManager);
        familiarPetManager.start();
        apocalypseManager = new ApocalypseManager(this, pointsManager, clanManager, lootManager);
        dynamicObjectiveManager = new DynamicObjectiveManager(this, pointsManager, clanManager);
        bountyManager = new BountyManager(this, pointsManager, clanManager);
        gameScoreboard = new GameScoreboard(this, pointsManager, clanManager, apocalypseManager::isGameRunning,
                () -> dynamicObjectiveManager.getSidebarLineText(), bountyManager::getSidebarLine);
        dynamicObjectiveManager.setGameScoreboard(gameScoreboard);
        apocalypseManager.setGameScoreboard(gameScoreboard);
        apocalypseManager.setDynamicObjectiveManager(dynamicObjectiveManager);
        gameScoreboard.start();
        bountyManager.start();
        topHologramManager = new TopHologramManager(this, pointsManager);
        topHologramManager.start();
        WeaponBrowseGui weaponBrowseGui = new WeaponBrowseGui(weaponManager);
        ShopGui shopGui = new ShopGui(this, weaponManager, pointsManager);
        CosmeticsGui cosmeticsGui = new CosmeticsGui(cosmeticsManager, pointsManager, weaponManager);
        SkinGui skinGui = new SkinGui(cosmeticsManager, pointsManager, weaponManager);
        hdvManager = new HdvManager(this, pointsManager);
        HdvGui hdvGui = new HdvGui(this, hdvManager, pointsManager, weaponManager);
        resourcePackManager = new ResourcePackManager(this);
        resourcePackManager.startLocalPackServerIfConfigured();
        resourcePackManager.startManifestPolling();
        ProfileGui profileGui = new ProfileGui(pointsManager, playerProfileManager, trophyManager);
        getServer().getPluginManager().registerEvents(weaponBrowseGui, this);
        getServer().getPluginManager().registerEvents(shopGui, this);
        getServer().getPluginManager().registerEvents(cosmeticsGui, this);
        getServer().getPluginManager().registerEvents(skinGui, this);
        getServer().getPluginManager().registerEvents(new WeaponSkinListener(this, weaponManager), this);
        getServer().getPluginManager().registerEvents(hdvGui, this);
        getServer().getPluginManager().registerEvents(zoneManager, this);
        getServer().getPluginManager().registerEvents(profileGui, this);
        getServer().getPluginManager().registerEvents(playerProfileManager, this);
        getServer().getPluginManager().registerEvents(crateManager, this);
        getServer().getPluginManager().registerEvents(lootboxManager, this);
        getServer().getPluginManager().registerEvents(trophyManager, this);
        getServer().getPluginManager().registerEvents(masteryNametagManager, this);
        getServer().getPluginManager().registerEvents(safeRespawnManager, this);
        getServer().getPluginManager().registerEvents(npcSpawnerManager, this);
        getServer().getPluginManager().registerEvents(autoFeedManager, this);
        getServer().getPluginManager().registerEvents(familiarPetManager, this);
        ClanMenuListener clanMenuListener = new ClanMenuListener(this, clanManager);
        getServer().getPluginManager().registerEvents(clanMenuListener, this);
        getServer().getPluginManager().registerEvents(new ChatFormatListener(pointsManager, clanManager), this);
        getServer().getPluginManager().registerEvents(resourcePackManager, this);
        getServer().getPluginManager().registerEvents(apocalypseManager, this);
        getServer().getPluginManager().registerEvents(bountyManager, this);
        getServer().getPluginManager().registerEvents(dynamicObjectiveManager, this);
        getServer().getPluginManager().registerEvents(lootManager, this);
        getServer().getPluginManager().registerEvents(powerupBlockManager, this);
        getServer().getPluginManager().registerEvents(pokerCardManager, this);
        powerupBlockManager.startMarkers();
        getServer().getPluginManager().registerEvents(weaponManager, this);
        getServer().getPluginManager().registerEvents(radarManager, this);
        getServer().getPluginManager().registerEvents(wallVisionManager, this);
        getServer().getPluginManager().registerEvents(launchpadManager, this);
        getServer().getPluginManager().registerEvents(bandageManager, this);
        getServer().getPluginManager().registerEvents(new ModificationTableListener(this, weaponManager, modificationTableRegistry), this);

        GiveGui giveGui = new GiveGui(weaponManager, bandageManager, radarManager, wallVisionManager, launchpadManager, pokerCardManager);
        getServer().getPluginManager().registerEvents(giveGui, this);

        vaultManager = new VaultManager(this);
        getServer().getPluginManager().registerEvents(vaultManager, this);

        PluginCommand command = getCommand("dpmr");
        if (command != null) {
            DpmrCommand dpmrCommand = new DpmrCommand(apocalypseManager, lootManager, weaponManager, bandageManager, giveGui, modificationTableRegistry, launchpadManager, zoneManager, familiarPetManager, crateManager, npcSpawnerManager, armorManager, safeRespawnManager, warWorldNpcManager, resourcePackManager, medkitStationManager, captureMoneyZoneManager, powerupBlockManager, pokerCardManager, vaultManager);
            command.setExecutor(dpmrCommand);
            command.setTabCompleter(dpmrCommand);
        } else {
            getLogger().severe("Command /dpmr missing from plugin.yml");
        }
        registerSimpleCommand("points", new PointsCommand(pointsManager));
        TopCommand topCommand = new TopCommand(pointsManager);
        registerSimpleCommand("topdpmr", topCommand);
        registerSimpleCommand("leaderboard", topCommand);
        registerSimpleCommand("armes", new ArmesCommand(weaponBrowseGui));
        registerSimpleCommand("boutique", new BoutiqueCommand(shopGui));
        registerSimpleCommand("cosmetics", new CosmeticsCommand(cosmeticsGui, cosmeticsManager, pointsManager, weaponManager));
        registerSimpleCommand("skin", new SkinCommand(skinGui));
        registerSimpleCommand("hdv", new HdvCommand(hdvGui));
        registerSimpleCommand("ressourcepack", new RessourcePackCommand(resourcePackManager));
        registerSimpleCommand("hologramtop", new HologramTopCommand(topHologramManager));
        ClanCommand clanCommand = new ClanCommand(clanManager);
        PluginCommand clanPluginCmd = getCommand("clan");
        if (clanPluginCmd != null) {
            clanPluginCmd.setExecutor(clanCommand);
            clanPluginCmd.setTabCompleter(clanCommand);
        } else {
            getLogger().severe("Command /clan missing from plugin.yml");
        }
        registerSimpleCommand("profil", new ProfileCommand(profileGui));
        registerSimpleCommand("lang", new LangCommand(languageStore));
        registerSimpleCommand("killdpmrnpcs", new KillDpmNpcsCommand(npcSpawnerManager));
        LootboxCommand lootboxCommand = new LootboxCommand(lootboxManager);
        PluginCommand lootboxCmd = getCommand("lootbox");
        if (lootboxCmd != null) {
            lootboxCmd.setExecutor(lootboxCommand);
            lootboxCmd.setTabCompleter(lootboxCommand);
        } else {
            getLogger().severe("Command /lootbox missing from plugin.yml");
        }

        KitCommand kitCommand = new KitCommand(evolvingKitManager);
        PluginCommand kitCmd = getCommand("kit");
        if (kitCmd != null) {
            kitCmd.setExecutor(kitCommand);
            kitCmd.setTabCompleter(kitCommand);
        } else {
            getLogger().severe("Command /kit missing from plugin.yml");
        }

        dailyQuestManager = new DailyQuestManager(this, pointsManager);
        DailyQuestGui dailyQuestGui = new DailyQuestGui(this, dailyQuestManager, clanManager);
        getServer().getPluginManager().registerEvents(dailyQuestGui, this);
        registerSimpleCommand("dailyquest", new DailyQuestCommand(dailyQuestGui));

        conquerPassManager = new ConquerPassManager(this, pointsManager);
        ConquerPassGui conquerPassGui = new ConquerPassGui(this, conquerPassManager, clanManager);
        getServer().getPluginManager().registerEvents(conquerPassGui, this);
        registerSimpleCommand("conquerpass", new ConquerPassCommand(conquerPassGui));
        registerSimpleCommand("trophies", new TrophiesCommand(trophyManager));

        for (Player p : getServer().getOnlinePlayers()) {
            trophyManager.recheckPlayer(p);
            masteryNametagManager.apply(p);
        }

        getLogger().info("Diviser Pour Mieux Regner enabled.");
    }

    @Override
    public void onDisable() {
        if (familiarPetManager != null) {
            familiarPetManager.stop();
        }
        if (topHologramManager != null) {
            topHologramManager.stop();
        }
        if (apocalypseManager != null) {
            apocalypseManager.stopGame();
        }
        if (pointsManager != null) {
            pointsManager.save();
        }
        if (playerProfileManager != null) {
            playerProfileManager.stop();
            playerProfileManager.save();
        }
        if (clanManager != null) {
            clanManager.save();
        }
        if (crateManager != null) {
            crateManager.save();
        }
        if (lootboxManager != null) {
            lootboxManager.save();
        }
        if (evolvingKitManager != null) {
            evolvingKitManager.stop();
        }
        if (launchpadManager != null) {
            launchpadManager.save();
        }
        if (warWorldNpcManager != null) {
            warWorldNpcManager.stop();
        }
        if (npcSpawnerManager != null) {
            npcSpawnerManager.shutdown();
            npcSpawnerManager.save();
        }
        if (playerHealthDisplayManager != null) {
            playerHealthDisplayManager.disable();
        }
        if (autoFeedManager != null) {
            autoFeedManager.stop();
        }
        if (resourcePackManager != null) {
            resourcePackManager.shutdown();
        }
        if (medkitStationManager != null) {
            medkitStationManager.stop();
        }
        if (captureMoneyZoneManager != null) {
            captureMoneyZoneManager.stop();
        }
        if (dailyQuestManager != null) {
            dailyQuestManager.save();
        }
        if (conquerPassManager != null) {
            conquerPassManager.save();
        }
        if (vaultManager != null) {
            vaultManager.save();
        }
        if (trophyManager != null) {
            trophyManager.save();
        }
        if (bountyManager != null) {
            bountyManager.stop();
        }
        getLogger().info("Diviser Pour Mieux Regner disabled.");
    }

    private void registerSimpleCommand(String commandName, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            getLogger().severe("Commande /" + commandName + " introuvable dans plugin.yml");
            return;
        }
        command.setExecutor(executor);
    }

    /**
     * Ajoute les cles manquantes du config.yml du jar (armes CMD, resource-pack, etc.) sans ecraser ton fichier.
     */
    private void mergeConfigDefaultsFromJar() {
        try (InputStream stream = getResource("config.yml")) {
            if (stream == null) {
                getLogger().severe("config.yml not found in jar — cannot merge weapon CMD defaults.");
                return;
            }
            FileConfiguration cfg = getConfig();
            YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            int cmdAdded = mergeWeaponCustomModelData(cfg, def);
            int knifeAdded = mergeCosmeticsKnifeSkins(cfg, def);
            int weaponSkinAdded = mergeCosmeticsWeaponSkins(cfg, def);
            mergeResourcePackSubsection(cfg, def, "local");
            mergeResourcePackSubsection(cfg, def, "manifest");
            mergeDailyQuestDefaults(cfg, def);
            mergeEvolvingKitsDefaults(cfg, def);
            mergeConquerPassDefaults(cfg, def);
            mergeVaultDefaults(cfg, def);
            mergeBountyDefaults(cfg, def);
            saveConfig();
            reloadConfig();
            getLogger().info("config.yml: merged jar defaults — weapons.custom-model-data +" + cmdAdded
                    + " key(s), cosmetics.knife-skins +" + knifeAdded + " key(s), cosmetics.weapon-skins +"
                    + weaponSkinAdded + " key(s), resource-pack local/manifest filled if missing.");
        } catch (IOException e) {
            getLogger().warning("Fusion config.yml par defaut: " + e.getMessage());
        }
    }

    /**
     * Fusionne {@code weapons.custom-model-data} via sections explicites (chemins avec tirets : fiable sur tous les YAML Bukkit).
     */
    private static int mergeWeaponCustomModelData(FileConfiguration cfg, YamlConfiguration def) {
        ConfigurationSection wDef = def.getConfigurationSection("weapons");
        ConfigurationSection from = wDef != null ? wDef.getConfigurationSection("custom-model-data") : null;
        if (from == null) {
            return 0;
        }
        ConfigurationSection wCfg = cfg.getConfigurationSection("weapons");
        if (wCfg == null) {
            wCfg = cfg.createSection("weapons");
        }
        ConfigurationSection into = wCfg.getConfigurationSection("custom-model-data");
        if (into == null) {
            into = wCfg.createSection("custom-model-data");
        }
        int added = 0;
        for (String key : from.getKeys(false)) {
            Object defVal = from.get(key);
            if (defVal instanceof ConfigurationSection) {
                continue;
            }
            if (!into.contains(key)) {
                into.set(key, defVal);
                added++;
                continue;
            }
            Object cur = into.get(key);
            if (cur instanceof Number cn && cn.intValue() == 0
                    && defVal instanceof Number dn && dn.intValue() > 0) {
                into.set(key, defVal);
                added++;
            }
        }
        return added;
    }

    private static int mergeCosmeticsKnifeSkins(FileConfiguration cfg, YamlConfiguration def) {
        ConfigurationSection cDef = def.getConfigurationSection("cosmetics");
        ConfigurationSection from = cDef != null ? cDef.getConfigurationSection("knife-skins") : null;
        if (from == null) {
            return 0;
        }
        ConfigurationSection cCfg = cfg.getConfigurationSection("cosmetics");
        if (cCfg == null) {
            cCfg = cfg.createSection("cosmetics");
        }
        ConfigurationSection into = cCfg.getConfigurationSection("knife-skins");
        if (into == null) {
            into = cCfg.createSection("knife-skins");
        }
        int added = 0;
        for (String key : from.getKeys(false)) {
            if (!into.contains(key)) {
                into.set(key, from.get(key));
                added++;
            }
        }
        return added;
    }

    private static int mergeCosmeticsWeaponSkins(FileConfiguration cfg, YamlConfiguration def) {
        ConfigurationSection cDef = def.getConfigurationSection("cosmetics");
        ConfigurationSection from = cDef != null ? cDef.getConfigurationSection("weapon-skins") : null;
        if (from == null) {
            return 0;
        }
        ConfigurationSection cCfg = cfg.getConfigurationSection("cosmetics");
        if (cCfg == null) {
            cCfg = cfg.createSection("cosmetics");
        }
        ConfigurationSection into = cCfg.getConfigurationSection("weapon-skins");
        if (into == null) {
            into = cCfg.createSection("weapon-skins");
        }
        int added = 0;
        for (String key : from.getKeys(false)) {
            if (!into.contains(key)) {
                into.set(key, from.get(key));
                added++;
            }
        }
        return added;
    }

    private static void mergeLootboxDefaults(FileConfiguration cfg, YamlConfiguration def) {
        ConfigurationSection from = def.getConfigurationSection("lootbox");
        if (from == null) {
            return;
        }
        ConfigurationSection into = cfg.getConfigurationSection("lootbox");
        if (into == null) {
            into = cfg.createSection("lootbox");
        }
        for (String key : from.getKeys(false)) {
            if (!into.contains(key)) {
                into.set(key, from.get(key));
            }
        }
    }

    /** Adds {@code evolving-kits} from the jar if missing (no overwrite). */
    private static void mergeEvolvingKitsDefaults(FileConfiguration cfg, YamlConfiguration def) {
        if (cfg.contains("evolving-kits")) {
            return;
        }
        Object block = def.get("evolving-kits");
        if (block != null) {
            cfg.set("evolving-kits", block);
        }
    }

    private static void mergeDailyQuestDefaults(FileConfiguration cfg, YamlConfiguration def) {
        ConfigurationSection from = def.getConfigurationSection("daily-quests");
        if (from == null) {
            return;
        }
        ConfigurationSection into = cfg.getConfigurationSection("daily-quests");
        if (into == null) {
            into = cfg.createSection("daily-quests");
        }
        for (String key : from.getKeys(false)) {
            if (!into.contains(key)) {
                into.set(key, from.get(key));
            }
        }
    }

    private static void mergeConquerPassDefaults(FileConfiguration cfg, YamlConfiguration def) {
        ConfigurationSection from = def.getConfigurationSection("conquer-pass");
        if (from == null) {
            return;
        }
        ConfigurationSection into = cfg.getConfigurationSection("conquer-pass");
        if (into == null) {
            into = cfg.createSection("conquer-pass");
        }
        mergeConfigSectionMissing(into, from);
    }

    private static void mergeBountyDefaults(FileConfiguration cfg, YamlConfiguration def) {
        ConfigurationSection from = def.getConfigurationSection("bounty");
        if (from == null) {
            return;
        }
        ConfigurationSection into = cfg.getConfigurationSection("bounty");
        if (into == null) {
            into = cfg.createSection("bounty");
        }
        mergeConfigSectionMissing(into, from);
    }

    private static void mergeVaultDefaults(FileConfiguration cfg, YamlConfiguration def) {
        ConfigurationSection from = def.getConfigurationSection("vault");
        if (from == null) {
            return;
        }
        ConfigurationSection into = cfg.getConfigurationSection("vault");
        if (into == null) {
            into = cfg.createSection("vault");
        }
        mergeConfigSectionMissing(into, from);
    }

    private static void mergeConfigSectionMissing(ConfigurationSection into, ConfigurationSection from) {
        for (String key : from.getKeys(false)) {
            if (!into.contains(key)) {
                into.set(key, from.get(key));
                continue;
            }
            if (from.isConfigurationSection(key) && into.isConfigurationSection(key)) {
                ConfigurationSection subIn = into.getConfigurationSection(key);
                ConfigurationSection subFrom = from.getConfigurationSection(key);
                if (subIn != null && subFrom != null) {
                    mergeConfigSectionMissing(subIn, subFrom);
                }
            }
        }
    }

    private static void mergeResourcePackSubsection(FileConfiguration cfg, YamlConfiguration def, String subsection) {
        ConfigurationSection rpDef = def.getConfigurationSection("resource-pack");
        ConfigurationSection from = rpDef != null ? rpDef.getConfigurationSection(subsection) : null;
        if (from == null) {
            return;
        }
        ConfigurationSection rpCfg = cfg.getConfigurationSection("resource-pack");
        if (rpCfg == null) {
            rpCfg = cfg.createSection("resource-pack");
        }
        ConfigurationSection into = rpCfg.getConfigurationSection(subsection);
        if (into == null) {
            into = rpCfg.createSection(subsection);
        }
        for (String key : from.getKeys(false)) {
            Object defVal = from.get(key);
            if (!into.contains(key)) {
                into.set(key, defVal);
            }
        }
    }
}
