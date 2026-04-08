package fr.dpmr.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Clans with leader, invites, YAML persistence.
 */
public class ClanManager {

    private static final int MAX_NAME_LENGTH = 18;
    private static final int MIN_NAME_LENGTH = 2;

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<String, Set<UUID>> clans = new HashMap<>();
    private final Map<UUID, String> playerClan = new HashMap<>();
    private final Map<String, UUID> clanLeader = new HashMap<>();
    /** Player UUID invited -> clan name */
    private final Map<UUID, String> pendingInvites = new HashMap<>();

    public ClanManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        clans.clear();
        playerClan.clear();
        clanLeader.clear();
        pendingInvites.clear();
        if (!yaml.isConfigurationSection("clans")) {
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("clans");
        if (root == null) {
            return;
        }
        for (String clanName : root.getKeys(false)) {
            Set<UUID> members = new HashSet<>();
            String path = "clans." + clanName;
            List<String> rawMembers = yaml.getStringList(path + ".members");
            for (String raw : rawMembers) {
                try {
                    UUID uuid = UUID.fromString(raw);
                    members.add(uuid);
                    playerClan.put(uuid, clanName);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid UUID in clans.yml: " + raw);
                }
            }
            String leadRaw = yaml.getString(path + ".leader");
            UUID leader = null;
            if (leadRaw != null && !leadRaw.isBlank()) {
                try {
                    leader = UUID.fromString(leadRaw);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid leader UUID for clan " + clanName);
                }
            }
            if (leader == null || !members.contains(leader)) {
                leader = members.isEmpty() ? null : members.iterator().next();
            }
            if (!members.isEmpty() && leader != null) {
                clans.put(clanName, members);
                clanLeader.put(clanName, leader);
            }
        }
    }

    public void save() {
        prepareYaml();
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save clans.yml: " + e.getMessage());
        }
    }

    public void saveAsync() {
        prepareYaml();
        String data = yaml.saveToString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.nio.file.Files.writeString(file.toPath(), data, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save clans.yml (async): " + e.getMessage());
            }
        });
    }

    private void prepareYaml() {
        yaml.set("clans", null);
        for (Map.Entry<String, Set<UUID>> entry : clans.entrySet()) {
            String name = entry.getKey();
            String base = "clans." + name;
            yaml.set(base + ".members", entry.getValue().stream().map(UUID::toString).sorted().toList());
            UUID lead = clanLeader.get(name);
            if (lead != null) {
                yaml.set(base + ".leader", lead.toString());
            }
        }
    }

    public static boolean isValidClanName(String raw) {
        if (raw == null) {
            return false;
        }
        String s = raw.trim();
        if (s.length() < MIN_NAME_LENGTH || s.length() > MAX_NAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                return false;
            }
        }
        return true;
    }

    public boolean createClan(String clanName, UUID creator) {
        if (!isValidClanName(clanName) || clans.containsKey(clanName) || playerClan.containsKey(creator)) {
            return false;
        }
        Set<UUID> members = new HashSet<>();
        members.add(creator);
        clans.put(clanName, members);
        playerClan.put(creator, clanName);
        clanLeader.put(clanName, creator);
        return true;
    }

    public boolean joinClan(String clanName, UUID player) {
        Set<UUID> members = clans.get(clanName);
        if (members == null || playerClan.containsKey(player)) {
            return false;
        }
        members.add(player);
        playerClan.put(player, clanName);
        return true;
    }

    public boolean leaveClan(UUID player) {
        String clan = playerClan.remove(player);
        if (clan == null) {
            return false;
        }
        pendingInvites.remove(player);
        Set<UUID> members = clans.get(clan);
        if (members != null) {
            members.remove(player);
            if (members.isEmpty()) {
                clans.remove(clan);
                clanLeader.remove(clan);
            } else {
                UUID lead = clanLeader.get(clan);
                if (lead != null && lead.equals(player)) {
                    clanLeader.put(clan, members.iterator().next());
                }
            }
        }
        return true;
    }

    public boolean isLeader(UUID player) {
        String clan = playerClan.get(player);
        if (clan == null) {
            return false;
        }
        UUID lead = clanLeader.get(clan);
        return lead != null && lead.equals(player);
    }

    public boolean kickMember(UUID leaderUuid, UUID targetUuid) {
        if (!isLeader(leaderUuid)) {
            return false;
        }
        String c1 = playerClan.get(leaderUuid);
        String c2 = playerClan.get(targetUuid);
        if (c1 == null || !c1.equals(c2) || leaderUuid.equals(targetUuid)) {
            return false;
        }
        return leaveClan(targetUuid);
    }

    public boolean invitePlayer(UUID inviterUuid, UUID targetUuid) {
        if (!isLeader(inviterUuid)) {
            return false;
        }
        String clan = playerClan.get(inviterUuid);
        if (clan == null || playerClan.containsKey(targetUuid) || inviterUuid.equals(targetUuid)) {
            return false;
        }
        pendingInvites.put(targetUuid, clan);
        return true;
    }

    public String getPendingInvite(UUID player) {
        return pendingInvites.get(player);
    }

    public boolean acceptInvite(UUID player) {
        String clan = pendingInvites.remove(player);
        if (clan == null) {
            return false;
        }
        if (playerClan.containsKey(player)) {
            return false;
        }
        return joinClan(clan, player);
    }

    public boolean denyInvite(UUID player) {
        return pendingInvites.remove(player) != null;
    }

    /**
     * Invites every online player who is not already in a clan (does not overwrite an existing pending invite).
     * Caller should notify each returned UUID (e.g. clickable /clan accept).
     */
    public List<UUID> inviteAllOnline(UUID inviterUuid) {
        if (!isLeader(inviterUuid)) {
            return List.of();
        }
        String clan = playerClan.get(inviterUuid);
        if (clan == null) {
            return List.of();
        }
        List<UUID> invited = new ArrayList<>();
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (id.equals(inviterUuid)) {
                continue;
            }
            if (playerClan.containsKey(id)) {
                continue;
            }
            if (pendingInvites.containsKey(id)) {
                continue;
            }
            pendingInvites.put(id, clan);
            invited.add(id);
        }
        return invited;
    }

    public String getPlayerClan(UUID player) {
        return playerClan.get(player);
    }

    public Map<String, Set<UUID>> getClans() {
        return Collections.unmodifiableMap(clans);
    }

    public UUID getLeader(String clanName) {
        return clanLeader.get(clanName);
    }

    public Set<UUID> getMembers(String clanName) {
        Set<UUID> m = clans.get(clanName);
        return m == null ? Set.of() : Collections.unmodifiableSet(m);
    }
}
