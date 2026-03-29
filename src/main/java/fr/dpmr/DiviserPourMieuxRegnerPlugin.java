package fr.dpmr;

import fr.dpmr.i18n.I18n;
import fr.dpmr.i18n.PlayerLanguageStore;
import fr.dpmr.command.LangCommand;
import fr.dpmr.command.CosmeticsCommand;
import fr.dpmr.command.ArmesCommand;
import fr.dpmr.command.BoutiqueCommand;
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
import fr.dpmr.chat.ChatFormatListener;
import fr.dpmr.crate.CrateManager;
import fr.dpmr.data.ClanManager;
import fr.dpmr.data.PointsManager;
import fr.dpmr.game.ApocalypseManager;
import fr.dpmr.game.AutoFeedManager;
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
import fr.dpmr.game.WeaponManager;
import fr.dpmr.gui.GiveGui;
import fr.dpmr.gui.ShopGui;
import fr.dpmr.gui.WeaponBrowseGui;
import fr.dpmr.hdv.HdvGui;
import fr.dpmr.hdv.HdvManager;
import fr.dpmr.resourcepack.ResourcePackManager;
import fr.dpmr.profile.PlayerProfileManager;
import fr.dpmr.profile.ProfileGui;
import fr.dpmr.npc.NpcSpawnerManager;
import fr.dpmr.npc.WarWorldNpcManager;
import fr.dpmr.armor.ArmorManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
    private WeaponManager weaponManager;
    private LaunchpadManager launchpadManager;
    private ModificationTableRegistry modificationTableRegistry;
    private BandageManager bandageManager;
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
    private SafeRespawnManager safeRespawnManager;
    private NpcSpawnerManager npcSpawnerManager;
    private WarWorldNpcManager warWorldNpcManager;
    private ArmorManager armorManager;
    private PlayerHealthDisplayManager playerHealthDisplayManager;
    private AutoFeedManager autoFeedManager;
    private RadarManager radarManager;
    private PlayerLanguageStore languageStore;

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
        zoneManager = new fr.dpmr.zone.ZoneManager(this);
        weaponManager = new WeaponManager(this, cosmeticsManager, zoneManager, armorManager, languageStore);
        radarManager = new RadarManager(this, weaponManager);
        launchpadManager = new LaunchpadManager(this, cosmeticsManager);
        modificationTableRegistry = new ModificationTableRegistry(this);
        modificationTableRegistry.load();
        bandageManager = new BandageManager(this);
        lootManager = new LootManager(this, weaponManager, bandageManager, armorManager, languageStore);
        crateManager = new CrateManager(this, pointsManager, weaponManager, bandageManager);
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
        gameScoreboard = new GameScoreboard(this, pointsManager, clanManager, apocalypseManager::isGameRunning,
                () -> dynamicObjectiveManager.getSidebarLineText());
        dynamicObjectiveManager.setGameScoreboard(gameScoreboard);
        apocalypseManager.setGameScoreboard(gameScoreboard);
        apocalypseManager.setDynamicObjectiveManager(dynamicObjectiveManager);
        gameScoreboard.start();
        topHologramManager = new TopHologramManager(this, pointsManager);
        topHologramManager.start();
        WeaponBrowseGui weaponBrowseGui = new WeaponBrowseGui(weaponManager);
        ShopGui shopGui = new ShopGui(this, weaponManager, pointsManager);
        CosmeticsGui cosmeticsGui = new CosmeticsGui(cosmeticsManager, pointsManager);
        hdvManager = new HdvManager(this, pointsManager);
        HdvGui hdvGui = new HdvGui(this, hdvManager, pointsManager, weaponManager);
        resourcePackManager = new ResourcePackManager(this);
        resourcePackManager.startLocalPackServerIfConfigured();
        resourcePackManager.startManifestPolling();
        ProfileGui profileGui = new ProfileGui(pointsManager, playerProfileManager);
        getServer().getPluginManager().registerEvents(weaponBrowseGui, this);
        getServer().getPluginManager().registerEvents(shopGui, this);
        getServer().getPluginManager().registerEvents(cosmeticsGui, this);
        getServer().getPluginManager().registerEvents(hdvGui, this);
        getServer().getPluginManager().registerEvents(zoneManager, this);
        getServer().getPluginManager().registerEvents(profileGui, this);
        getServer().getPluginManager().registerEvents(playerProfileManager, this);
        getServer().getPluginManager().registerEvents(crateManager, this);
        getServer().getPluginManager().registerEvents(safeRespawnManager, this);
        getServer().getPluginManager().registerEvents(npcSpawnerManager, this);
        getServer().getPluginManager().registerEvents(autoFeedManager, this);
        getServer().getPluginManager().registerEvents(familiarPetManager, this);
        getServer().getPluginManager().registerEvents(new ChatFormatListener(pointsManager, clanManager), this);
        getServer().getPluginManager().registerEvents(resourcePackManager, this);
        getServer().getPluginManager().registerEvents(apocalypseManager, this);
        getServer().getPluginManager().registerEvents(dynamicObjectiveManager, this);
        getServer().getPluginManager().registerEvents(lootManager, this);
        getServer().getPluginManager().registerEvents(weaponManager, this);
        getServer().getPluginManager().registerEvents(radarManager, this);
        getServer().getPluginManager().registerEvents(launchpadManager, this);
        getServer().getPluginManager().registerEvents(bandageManager, this);
        getServer().getPluginManager().registerEvents(new ModificationTableListener(this, weaponManager, modificationTableRegistry), this);

        GiveGui giveGui = new GiveGui(weaponManager, bandageManager, radarManager, launchpadManager);
        getServer().getPluginManager().registerEvents(giveGui, this);

        PluginCommand command = getCommand("dpmr");
        if (command != null) {
            DpmrCommand dpmrCommand = new DpmrCommand(apocalypseManager, lootManager, weaponManager, bandageManager, giveGui, modificationTableRegistry, launchpadManager, zoneManager, familiarPetManager, crateManager, npcSpawnerManager, armorManager, safeRespawnManager, warWorldNpcManager, resourcePackManager);
            command.setExecutor(dpmrCommand);
            command.setTabCompleter(dpmrCommand);
        } else {
            getLogger().severe("Commande /dpmr introuvable dans plugin.yml");
        }
        registerSimpleCommand("points", new PointsCommand(pointsManager));
        TopCommand topCommand = new TopCommand(pointsManager);
        registerSimpleCommand("topdpmr", topCommand);
        registerSimpleCommand("leaderboard", topCommand);
        registerSimpleCommand("armes", new ArmesCommand(weaponBrowseGui));
        registerSimpleCommand("boutique", new BoutiqueCommand(shopGui));
        registerSimpleCommand("cosmetics", new CosmeticsCommand(cosmeticsGui, cosmeticsManager, pointsManager));
        registerSimpleCommand("hdv", new HdvCommand(hdvGui));
        registerSimpleCommand("ressourcepack", new RessourcePackCommand(resourcePackManager));
        registerSimpleCommand("hologramtop", new HologramTopCommand(topHologramManager));
        registerSimpleCommand("clan", new ClanCommand(clanManager));
        registerSimpleCommand("profil", new ProfileCommand(profileGui));
        registerSimpleCommand("lang", new LangCommand(languageStore));
        registerSimpleCommand("killdpmrnpcs", new KillDpmNpcsCommand(npcSpawnerManager));

        getLogger().info("Diviser Pour Mieux Regner active.");
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
        getLogger().info("Diviser Pour Mieux Regner desactive.");
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
                getLogger().severe("config.yml introuvable dans le .jar — impossible de fusionner les CMD armes.");
                return;
            }
            FileConfiguration cfg = getConfig();
            YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            int cmdAdded = mergeWeaponCustomModelData(cfg, def);
            mergeResourcePackSubsection(cfg, def, "local");
            mergeResourcePackSubsection(cfg, def, "manifest");
            saveConfig();
            reloadConfig();
            getLogger().info("config.yml : fusion jar — weapons.custom-model-data +" + cmdAdded + " cle(s), resource-pack local/manifest complete si manquant.");
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
