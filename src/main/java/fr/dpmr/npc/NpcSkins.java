package fr.dpmr.npc;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Tetes joueur pour PNJ : pools charges depuis {@code /npc/skins-*.txt} (hash texture Mojang).
 * Chaque spawn utilise un {@link UUID} pour choisir une texture dans le pool et un profil crane unique.
 */
public final class NpcSkins {

    private static final String TEXTURE_PREFIX = "http://textures.minecraft.net/texture/";

    private static final String[] MILITARY_HASHES = loadHashes("/npc/skins-military.txt", defaultMilitaryHashes());
    private static final String[] RAIDER_HASHES = loadHashes("/npc/skins-raider.txt", defaultRaiderHashes());
    private static final String[] ZOMBIE_HASHES = loadHashes("/npc/skins-zombie.txt", defaultZombieHashes());

    private NpcSkins() {
    }

    /**
     * @param uniqueSeed par spawn ({@link UUID#randomUUID()}) pour repartir les skins sur tout le pool.
     */
    public static ItemStack randomHeadForKind(String kindName, UUID uniqueSeed) {
        String k = kindName == null ? "" : kindName.trim().toUpperCase(Locale.ROOT);
        UUID seed = uniqueSeed != null ? uniqueSeed : UUID.randomUUID();
        String[] pool = switch (k) {
            case "ZOMBIE" -> ZOMBIE_HASHES;
            case "RAIDER" -> RAIDER_HASHES;
            case "PATROL" -> MILITARY_HASHES;
            default -> MILITARY_HASHES;
        };
        if (pool.length == 0) {
            pool = defaultMilitaryHashes();
        }
        String hash = pool[pickIndex(seed, pool.length)];
        String url = TEXTURE_PREFIX + hash;
        String salt = "dpmr-" + k.toLowerCase(Locale.ROOT);
        return headFromTextureUrl(url, salt, seed);
    }

    /** @deprecated utiliser {@link #randomHeadForKind(String, UUID)} */
    @Deprecated
    public static ItemStack randomHeadForKind(String kindName) {
        return randomHeadForKind(kindName, UUID.randomUUID());
    }

    private static int pickIndex(UUID seed, int bound) {
        if (bound <= 1) {
            return 0;
        }
        long msb = seed.getMostSignificantBits();
        long lsb = seed.getLeastSignificantBits();
        long mix = msb ^ lsb ^ (msb >>> 33) ^ (lsb << 17);
        return Math.floorMod((int) mix ^ (int) (mix >>> 32), bound);
    }

    /**
     * Tete joueur (texture Mojang) pour GUIs — hash hex 64 caracteres.
     */
    public static ItemStack playerHeadFromTextureHash(String hash64, String profileSalt) {
        if (hash64 == null || hash64.length() != 64) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
        String salt = profileSalt != null ? profileSalt : "dpmr-head";
        UUID seed = UUID.nameUUIDFromBytes(salt.getBytes(StandardCharsets.UTF_8));
        return headFromTextureUrl(TEXTURE_PREFIX + hash64, salt, seed);
    }

    /** Nom valide pour {@link Bukkit#createPlayerProfile(UUID, String)} (Paper refuse un espace seul). */
    private static final String GUI_HEAD_PROFILE_NAME = "DPMR_Item";

    private static ItemStack headFromTextureUrl(String textureUrl, String uuidSalt, UUID instanceSeed) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (!(head.getItemMeta() instanceof SkullMeta sm)) {
            return head;
        }
        try {
            byte[] uuidBytes = (uuidSalt + "|" + textureUrl + "|" + instanceSeed).getBytes(StandardCharsets.UTF_8);
            UUID profileUuid = UUID.nameUUIDFromBytes(uuidBytes);
            PlayerProfile profile = Bukkit.createPlayerProfile(profileUuid, GUI_HEAD_PROFILE_NAME);
            PlayerTextures tex = profile.getTextures();
            tex.setSkin(URI.create(textureUrl).toURL());
            sm.setOwnerProfile(profile);
            head.setItemMeta(sm);
        } catch (Throwable t) {
            // Evite de casser toute la GUI (boutique, /armes) si le profil / URL echoue
            head.setItemMeta(sm);
        }
        return head;
    }

    private static String[] loadHashes(String resourcePath, String[] fallback) {
        List<String> out = new ArrayList<>(1024);
        try (InputStream in = NpcSkins.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        if (line.length() == 64 && line.chars().allMatch(c ->
                                (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                            out.add(line);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return out.isEmpty() ? fallback : out.toArray(new String[0]);
    }

    private static String[] defaultMilitaryHashes() {
        return new String[]{
                "935939dad57ef510da489550674f8b3159eaa1dbae99ef2cc1b187a72f84141",
                "512c96f33d8165169d10c4e2f65ed969ec88f714385dafac9e2c81451e56"
        };
    }

    private static String[] defaultRaiderHashes() {
        return new String[]{
                "935939dad57ef510da489550674f8b3159eaa1dbae99ef2cc1b187a72f84141",
                "512c96f33d8165169d10c4e2f65ed969ec88f714385dafac9e2c81451e56",
                "b37fd5f6a47496596b6fc69f1c0d7e2cdd89d61d84430abdd55578c3ef9bff"
        };
    }

    private static String[] defaultZombieHashes() {
        return new String[]{
                "b37fd5f6a47496596b6fc69f1c0d7e2cdd89d61d84430abdd55578c3ef9bff"
        };
    }

    /** @deprecated utiliser {@link #randomHeadForKind(String, UUID)} */
    @Deprecated
    public static ItemStack zombiePlayerHead(String ignoredDisplayName) {
        return randomHeadForKind("ZOMBIE", UUID.randomUUID());
    }
}
