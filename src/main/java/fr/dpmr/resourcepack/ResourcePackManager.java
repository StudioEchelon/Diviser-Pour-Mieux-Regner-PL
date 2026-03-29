package fr.dpmr.resourcepack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ResourcePackManager implements Listener {

    /** Fichier dans le .jar (Gradle : embedDpmrResourcePack). */
    private static final String BUNDLED_ZIP_RESOURCE = "dpmr-pack.zip";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> pendingKick = new HashMap<>();
    private final Map<UUID, Boolean> strictRequest = new HashMap<>();
    private LocalResourcePackHttpHost localHost;
    private BukkitTask manifestRefreshTask;

    private volatile String manifestResolvedUrl = "";
    private volatile String manifestResolvedSha1 = "";
    private volatile String manifestLastError = "";

    public ResourcePackManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Appele depuis onEnable apres load de la config. */
    public void startLocalPackServerIfConfigured() {
        stopLocalPackServer();
        String source = plugin.getConfig().getString("resource-pack.source", "external");
        boolean bundled = "bundled".equalsIgnoreCase(source);
        boolean localFile = "local".equalsIgnoreCase(source);
        if (!bundled && !localFile) {
            return;
        }
        File zip = new File(plugin.getDataFolder(), plugin.getConfig().getString("resource-pack.local.file", "resource-pack/dpmr-pack.zip"));
        if (bundled) {
            if (!extractBundledPackTo(zip)) {
                return;
            }
        } else if (!zip.isFile()) {
            plugin.getLogger().warning("Resource pack mode local: place le fichier zip ici → " + zip.getAbsolutePath());
            return;
        }
        int port = Math.max(1, Math.min(65535, plugin.getConfig().getInt("resource-pack.local.http-port", 8163)));
        String bind = plugin.getConfig().getString("resource-pack.local.bind-address", "0.0.0.0");
        String path = plugin.getConfig().getString("resource-pack.local.http-path", "/dpmr-pack.zip");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        try {
            localHost = new LocalResourcePackHttpHost(zip, bind, port, path);
            localHost.start();
            plugin.getLogger().info("Resource pack local: HTTP " + bind + ":" + port + path + " (SHA1=" + localHost.getSha1Hex() + ")");
        } catch (IOException e) {
            plugin.getLogger().severe("Resource pack local: echec demarrage HTTP (port deja pris ?) — " + e.getMessage());
            localHost = null;
        }
    }

    /**
     * Telecharge le manifest JSON (HTTPS) puis relance eventuellement un rafraichissement periodique.
     * A appeler apres {@link #startLocalPackServerIfConfigured()}.
     */
    public void startManifestPolling() {
        stopManifestScheduler();
        String srcEarly = plugin.getConfig().getString("resource-pack.source", "external");
        String muEarly = plugin.getConfig().getString("resource-pack.manifest.url", "");
        if (muEarly != null && !muEarly.isBlank()
                && ("bundled".equalsIgnoreCase(srcEarly) || "local".equalsIgnoreCase(srcEarly))) {
            plugin.getLogger().warning("resource-pack.manifest.url est rempli mais source=\"" + srcEarly
                    + "\" : le manifest GitHub est IGNORE. Mets resource-pack.source: manifest dans config.yml.");
        }
        if (!manifestWanted()) {
            return;
        }
        String mu = plugin.getConfig().getString("resource-pack.manifest.url", "");
        if (mu == null || mu.isBlank()) {
            if ("manifest".equalsIgnoreCase(plugin.getConfig().getString("resource-pack.source", "external"))) {
                plugin.getLogger().severe("resource-pack.source=manifest mais resource-pack.manifest.url est vide.");
            }
            return;
        }
        refreshManifestAsync();
        int sec = Math.max(0, plugin.getConfig().getInt("resource-pack.manifest.refresh-seconds", 0));
        if (sec > 0) {
            long ticks = sec * 20L;
            manifestRefreshTask = plugin.getServer().getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::runManifestFetch, ticks, ticks);
        }
    }

    public void refreshManifestAsync() {
        if (!manifestWanted()) {
            return;
        }
        String mu = plugin.getConfig().getString("resource-pack.manifest.url", "");
        if (mu == null || mu.isBlank()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::runManifestFetch);
    }

    private void stopManifestScheduler() {
        if (manifestRefreshTask != null) {
            manifestRefreshTask.cancel();
            manifestRefreshTask = null;
        }
    }

    private void runManifestFetch() {
        String mu = plugin.getConfig().getString("resource-pack.manifest.url", "");
        if (mu == null || mu.isBlank()) {
            return;
        }
        try {
            String body = httpGet(mu.trim());
            String u = readJsonString(body, "url");
            if (u == null || u.isBlank()) {
                manifestLastError = "JSON sans champ url";
                plugin.getLogger().warning("Manifest resource pack: champ \"url\" absent ou vide.");
                return;
            }
            u = u.trim();
            String s = readJsonString(body, "sha1");
            if (s != null) {
                s = s.trim();
            }
            manifestResolvedUrl = u;
            manifestResolvedSha1 = s != null ? s : "";
            manifestLastError = "";
            plugin.getLogger().info("Manifest resource pack OK → " + u);
        } catch (Exception e) {
            manifestLastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            plugin.getLogger().warning("Manifest resource pack: echec — " + manifestLastError);
        }
    }

    private static String httpGet(String urlStr) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", "DPMR-ResourcePack")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    /**
     * Lit la valeur d'une cle string JSON simple {@code "key": "value"} (echappements \ supportes).
     */
    static String readJsonString(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        i = json.indexOf(':', i + needle.length());
        if (i < 0) {
            return null;
        }
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char ch = json.charAt(i);
            if (ch == '\\' && i + 1 < json.length()) {
                i++;
                sb.append(json.charAt(i));
                i++;
                continue;
            }
            if (ch == '"') {
                break;
            }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }

    private boolean manifestWanted() {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            return false;
        }
        String src = plugin.getConfig().getString("resource-pack.source", "external");
        if ("local".equalsIgnoreCase(src) || "bundled".equalsIgnoreCase(src)) {
            return false;
        }
        if ("manifest".equalsIgnoreCase(src)) {
            return true;
        }
        String m = plugin.getConfig().getString("resource-pack.manifest.url", "");
        return m != null && !m.isBlank();
    }

    private String effectiveExternalPackUrl() {
        if (manifestWanted()) {
            String m = manifestResolvedUrl;
            if (m != null && !m.isBlank()) {
                return m;
            }
        }
        String u = plugin.getConfig().getString("resource-pack.url", "");
        return u != null ? u : "";
    }

    private String effectiveExternalPackSha1() {
        if (manifestWanted()) {
            String m = manifestResolvedSha1;
            if (m != null && m.length() == 40) {
                return m;
            }
        }
        String s = plugin.getConfig().getString("resource-pack.sha1", "");
        return s != null ? s : "";
    }

    private boolean extractBundledPackTo(File dest) {
        try (InputStream in = plugin.getResource(BUNDLED_ZIP_RESOURCE)) {
            if (in == null) {
                plugin.getLogger().severe("Pack DPMR absent du .jar (dpmr-pack.zip). Sur ta machine: bash scripts/build-resourcepack.sh puis ./gradlew jar");
                return false;
            }
            dest.getParentFile().mkdirs();
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Resource pack bundled: extrait du jar → " + dest.getAbsolutePath() + " (" + dest.length() + " octets)");
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Resource pack bundled: extraction impossible — " + e.getMessage());
            return false;
        }
    }

    /** URL vue par les clients (public-url ou http://public-host:port/chemin). */
    String effectivePublicPackUrl() {
        String explicit = plugin.getConfig().getString("resource-pack.local.public-url", "");
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        String host = plugin.getConfig().getString("resource-pack.local.public-host", "");
        if (host == null || host.isBlank()) {
            return "";
        }
        host = host.trim();
        if (host.startsWith("http://")) {
            host = host.substring(7);
        } else if (host.startsWith("https://")) {
            host = host.substring(8);
        }
        int slash = host.indexOf('/');
        if (slash > 0) {
            host = host.substring(0, slash);
        }
        int port = Math.max(1, Math.min(65535, plugin.getConfig().getInt("resource-pack.local.http-port", 8163)));
        String path = plugin.getConfig().getString("resource-pack.local.http-path", "/dpmr-pack.zip");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "http://" + host + ":" + port + path;
    }

    public void stopLocalPackServer() {
        if (localHost != null) {
            localHost.stop();
            localHost = null;
        }
    }

    /** Arrete HTTP local + timer manifest. */
    public void shutdown() {
        stopManifestScheduler();
        stopLocalPackServer();
    }

    /** Apres /dpmr warworld reload (config deja relue). */
    public void restartLocalPackServerAfterConfigReload() {
        stopManifestScheduler();
        startLocalPackServerIfConfigured();
        startManifestPolling();
    }

    /** Diagnostic admin : pourquoi les textures ne s’appliquent pas (CMD 0, pack local, etc.). */
    public void sendAdminDiagnostics(CommandSender sender) {
        var cfg = plugin.getConfig();
        sender.sendMessage(Component.text("=== DPMR — resource pack & textures ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("resource-pack.enabled: " + cfg.getBoolean("resource-pack.enabled"), NamedTextColor.GRAY));
        String src = cfg.getString("resource-pack.source", "external");
        sender.sendMessage(Component.text("resource-pack.source: " + src, NamedTextColor.GRAY));
        if (manifestWanted()) {
            String murl = cfg.getString("resource-pack.manifest.url", "");
            sender.sendMessage(Component.text("manifest.url: " + (murl == null || murl.isBlank() ? "(vide)" : murl), NamedTextColor.GRAY));
            String resolved = manifestResolvedUrl;
            sender.sendMessage(Component.text("manifest → url resolue: " + (resolved == null || resolved.isBlank() ? "(pas encore chargee)" : resolved),
                    resolved == null || resolved.isBlank() ? NamedTextColor.RED : NamedTextColor.GREEN));
            String rsha = manifestResolvedSha1;
            boolean shaOk = rsha != null && rsha.length() == 40;
            sender.sendMessage(Component.text("manifest → sha1: " + (shaOk ? rsha : "(absent ou invalide — client peut mal mettre a jour)"),
                    shaOk ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            if (manifestLastError != null && !manifestLastError.isBlank()) {
                sender.sendMessage(Component.text("derniere erreur manifest: " + manifestLastError, NamedTextColor.RED));
            }
        }
        if ("local".equalsIgnoreCase(src) || "bundled".equalsIgnoreCase(src)) {
            File zip = new File(plugin.getDataFolder(), cfg.getString("resource-pack.local.file", "resource-pack/dpmr-pack.zip"));
            boolean okFile = zip.isFile();
            sender.sendMessage(Component.text("Fichier sur disque: " + zip.getAbsolutePath(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  existe: " + okFile + (okFile ? " (" + zip.length() + " o)" : ""), okFile ? NamedTextColor.GREEN : NamedTextColor.RED));
            if ("bundled".equalsIgnoreCase(src)) {
                sender.sendMessage(Component.text("  (bundled = copie du zip inclus dans le .jar a chaque demarrage)", NamedTextColor.DARK_GRAY));
            }
            boolean httpOk = localHost != null && localHost.isRunning();
            sender.sendMessage(Component.text("Serveur HTTP integre actif: " + httpOk, httpOk ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (httpOk) {
                sender.sendMessage(Component.text("SHA1 envoye au client: " + localHost.getSha1Hex(), NamedTextColor.AQUA));
            }
            String eff = effectivePublicPackUrl();
            sender.sendMessage(Component.text("URL effective joueurs: " + (eff.isBlank() ? "(vide — remplis public-host ou public-url)" : eff),
                    eff.isBlank() ? NamedTextColor.RED : NamedTextColor.GREEN));
            String ph = cfg.getString("resource-pack.local.public-host", "");
            sender.sendMessage(Component.text("public-host: " + (ph == null || ph.isBlank() ? "(vide)" : ph), NamedTextColor.GRAY));
        }
        if (!"local".equalsIgnoreCase(src) && !"bundled".equalsIgnoreCase(src)) {
            String url = effectiveExternalPackUrl();
            String sha = effectiveExternalPackSha1();
            sender.sendMessage(Component.text("url effective (external/manifest): " + (url.isBlank() ? "(vide)" : url),
                    url.isBlank() ? NamedTextColor.RED : NamedTextColor.GRAY));
            boolean shaOk = sha != null && sha.trim().length() == 40;
            sender.sendMessage(Component.text("sha1 (40 hex): " + (shaOk ? sha : "non / manquant"), shaOk ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        int cm = cfg.getInt("weapons.custom-model-data.CM_SHOTGUN", 0);
        int ep = cfg.getInt("weapons.custom-model-data.EP_FUSIL_POMPE", 0);
        boolean cmdOk = cm > 0 && ep > 0;
        sender.sendMessage(Component.text("Exemples CustomModelData — CM_SHOTGUN=" + cm + " EP_FUSIL_POMPE=" + ep,
                cmdOk ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (!cmdOk) {
            sender.sendMessage(Component.text("Si CMD = 0, les items restent vanilla meme avec un bon pack. Redemarre apres MAJ du .jar (fusion auto config) ou copie les cles depuis le jar.", NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.text("Armes deja en main : elles n’ont pas le nouveau CMD → reprends-les (/dpmr givegun ou boutique).", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Forcer le pack: /ressourcepack — recharger config: /dpmr warworld reload", NamedTextColor.DARK_GRAY));
    }

    public void sendPack(Player player) {
        sendPack(player, true, false);
    }

    /**
     * @param strict si true, peut planifier un kick en mode "pack requis".
     *               si false (refresh manuel), ne kick pas le joueur.
     */
    public void sendPack(Player player, boolean strict) {
        sendPack(player, strict, false);
    }

    /**
     * Relit {@code config.yml} puis renvoie le pack avec anti-cache (pour appliquer un nouveau sha1 / URL sans restart).
     */
    public void sendPackAfterConfigReload(Player player) {
        plugin.reloadConfig();
        restartLocalPackServerAfterConfigReload();
        sendPack(player, false, true);
    }

    /**
     * @param manualRefresh true pour /ressourcepack : ajoute un paramètre d’URL unique pour forcer le client
     *                      (et souvent le CDN) à retélécharger le zip sans changer le fichier hébergé.
     */
    public void sendPack(Player player, boolean strict, boolean manualRefresh) {
        sendPack(player, strict, manualRefresh, 0);
    }

    private void sendPack(Player player, boolean strict, boolean manualRefresh, int manifestRetry) {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            return;
        }
        String prompt = plugin.getConfig().getString("resource-pack.prompt", "Pack DPMR requis.");
        String baseUrl;
        String sha1;
        String src = plugin.getConfig().getString("resource-pack.source", "external");
        if ("local".equalsIgnoreCase(src) || "bundled".equalsIgnoreCase(src)) {
            if (localHost == null || !localHost.isRunning()) {
                player.sendMessage(Component.text("Resource pack local/bundled indisponible (fichier ou port HTTP). Voir les logs serveur.", NamedTextColor.RED));
                return;
            }
            baseUrl = effectivePublicPackUrl();
            if (baseUrl.isBlank()) {
                player.sendMessage(Component.text("Configure resource-pack.local.public-host (ex. 72.61.206.229) ou public-url complete.", NamedTextColor.RED));
                return;
            }
            sha1 = localHost.getSha1Hex();
        } else {
            baseUrl = effectiveExternalPackUrl();
            sha1 = effectiveExternalPackSha1();
            if (baseUrl == null || baseUrl.isBlank()) {
                if (manifestWanted() && manifestRetry < 35) {
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> sendPack(player, strict, manualRefresh, manifestRetry + 1), 10L);
                    return;
                }
                if (manifestWanted()) {
                    player.sendMessage(Component.text("Manifest: URL du zip introuvable (JSON non charge ou erreur reseau). Logs serveur + /dpmr resourcepack", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("resource-pack.url vide. Mets source: manifest + manifest.url GitHub, ou source: external + url + sha1.", NamedTextColor.RED));
                }
                return;
            }
        }
        String url = buildEffectivePackUrl(baseUrl.trim(), manualRefresh);
        byte[] hash = parseSha1(sha1);
        if (hash != null) {
            player.setResourcePack(url, hash, prompt);
        } else {
            player.setResourcePack(url);
        }
        strictRequest.put(player.getUniqueId(), strict);
        player.sendActionBar(Component.text("Telechargement du resource pack...", NamedTextColor.GOLD));
        if (strict) {
            scheduleKickIfStillMissing(player);
        } else {
            cancelPendingKick(player.getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Petit délai pour éviter les soucis juste après login
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendPack(event.getPlayer(), true, false), 20L);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        boolean force = plugin.getConfig().getBoolean("resource-pack.force", true);
        boolean kickOnFailedDownload = plugin.getConfig().getBoolean("resource-pack.kick-on-failed-download", false);
        Player p = event.getPlayer();
        boolean strict = strictRequest.getOrDefault(p.getUniqueId(), true);
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                cancelPendingKick(p.getUniqueId());
                strictRequest.remove(p.getUniqueId());
                p.sendActionBar(Component.text("Resource pack charge.", NamedTextColor.GREEN));
            }
            case DECLINED -> {
                p.sendMessage(Component.text("Le resource pack est requis.", NamedTextColor.RED));
                if (force && strict) {
                    kickNow(p);
                }
            }
            case FAILED_DOWNLOAD -> {
                p.sendMessage(Component.text("Echec de telechargement du pack. Reessaye /ressourcepack.", NamedTextColor.RED));
                // On ne kick pas instant: réseau lent/CDN, laisse le temps.
                if (force && kickOnFailedDownload && strict) {
                    scheduleKickIfStillMissing(p);
                }
            }
            case ACCEPTED -> p.sendActionBar(Component.text("Resource pack accepte...", NamedTextColor.YELLOW));
            default -> {
            }
        }
    }

    private void scheduleKickIfStillMissing(Player player) {
        if (!plugin.getConfig().getBoolean("resource-pack.force", true)) {
            return;
        }
        cancelPendingKick(player.getUniqueId());
        long delay = Math.max(40L, plugin.getConfig().getLong("resource-pack.kick-delay-ticks", 20L * 45L));
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.kick(Component.text("Resource pack requis pour jouer sur ce serveur."));
            }
        }, delay);
        pendingKick.put(player.getUniqueId(), task);
    }

    private void cancelPendingKick(UUID uuid) {
        BukkitTask t = pendingKick.remove(uuid);
        if (t != null) {
            t.cancel();
        }
    }

    private void kickNow(Player player) {
        cancelPendingKick(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin,
                () -> player.kick(Component.text("Resource pack requis pour jouer sur ce serveur.")));
    }

    /**
     * Le client ne retélécharge pas le pack si URL + hash sont identiques. On peut donc :
     * - mettre à jour {@code resource-pack.sha1} après chaque nouveau zip (recommandé) ;
     * - incrémenter {@code resource-pack.pack-revision} (ajouté en query string) ;
     * - sur /ressourcepack, ajouter {@code dpmr_t=} si {@code cache-bust-on-manual-command} est true.
     */
    private String buildEffectivePackUrl(String baseUrl, boolean manualRefresh) {
        StringBuilder q = new StringBuilder();
        int rev = plugin.getConfig().getInt("resource-pack.pack-revision", 0);
        if (rev > 0) {
            q.append("dpmr_pack_rev=").append(rev);
        }
        if (manualRefresh && plugin.getConfig().getBoolean("resource-pack.cache-bust-on-manual-command", true)) {
            if (q.length() > 0) {
                q.append('&');
            }
            q.append("dpmr_t=").append(System.currentTimeMillis());
        }
        if (q.length() == 0) {
            return baseUrl;
        }
        String sep = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + sep + q;
    }

    private static byte[] parseSha1(String hex) {
        if (hex == null) return null;
        String s = hex.trim().toLowerCase();
        if (s.isBlank() || s.length() != 40) return null;
        byte[] out = new byte[20];
        try {
            for (int i = 0; i < out.length; i++) {
                int hi = Character.digit(s.charAt(i * 2), 16);
                int lo = Character.digit(s.charAt(i * 2 + 1), 16);
                if (hi < 0 || lo < 0) return null;
                out[i] = (byte) ((hi << 4) + lo);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
