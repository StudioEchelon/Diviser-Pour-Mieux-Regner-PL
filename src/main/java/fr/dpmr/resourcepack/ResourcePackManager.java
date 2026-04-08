package fr.dpmr.resourcepack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

    /**
     * Extraction du zip + SHA-1 du fichier complet + bind HTTP : travail lourd déplacé hors du thread principal
     * pour ne pas bloquer le démarrage du serveur (pack volumineux = plusieurs secondes en synchrone).
     */
    public void startLocalPackServerIfConfigured() {
        stopLocalPackServer();
        String source = plugin.getConfig().getString("resource-pack.source", "external");
        boolean bundled = "bundled".equalsIgnoreCase(source);
        boolean localFile = "local".equalsIgnoreCase(source);
        if (!bundled && !localFile) {
            return;
        }
        File zip = new File(plugin.getDataFolder(), plugin.getConfig().getString("resource-pack.local.file", "resource-pack/dpmr-pack.zip"));
        if (!bundled && !zip.isFile()) {
            plugin.getLogger().warning("Resource pack mode local: place le fichier zip ici → " + zip.getAbsolutePath());
            return;
        }
        int port = Math.max(1, Math.min(65535, plugin.getConfig().getInt("resource-pack.local.http-port", 8163)));
        String bind = plugin.getConfig().getString("resource-pack.local.bind-address", "0.0.0.0");
        String pathRaw = plugin.getConfig().getString("resource-pack.local.http-path", "/dpmr-pack.zip");
        final String path = pathRaw.startsWith("/") ? pathRaw : "/" + pathRaw;
        plugin.getLogger().info("Resource pack local: preparation (extraction/SHA-1) en arriere-plan…");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (bundled && !extractBundledPackTo(zip)) {
                    return;
                }
                if (!zip.isFile()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getLogger().warning(
                            "Resource pack: fichier zip introuvable apres extraction → " + zip.getAbsolutePath()));
                    return;
                }
                String sha1 = LocalResourcePackHttpHost.sha1HexOfFile(zip);
                plugin.getServer().getScheduler().runTask(plugin, () -> startLocalHttpHostOnMainThread(zip, bind, port, path, sha1));
            } catch (IOException e) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.getLogger().severe("Resource pack local: echec preparation (SHA-1 / fichier) — " + e.getMessage()));
            }
        });
    }

    private void startLocalHttpHostOnMainThread(File zip, String bind, int port, String path, String sha1) {
        try {
            localHost = new LocalResourcePackHttpHost(zip, bind, port, path, sha1);
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
            sender.sendMessage(Component.text("manifest.url: " + (murl == null || murl.isBlank() ? "(empty)" : murl), NamedTextColor.GRAY));
            String resolved = manifestResolvedUrl;
            sender.sendMessage(Component.text("manifest → resolved URL: " + (resolved == null || resolved.isBlank() ? "(not loaded yet)" : resolved),
                    resolved == null || resolved.isBlank() ? NamedTextColor.RED : NamedTextColor.GREEN));
            String rsha = manifestResolvedSha1;
            boolean shaOk = rsha != null && rsha.length() == 40;
            sender.sendMessage(Component.text("manifest → sha1: " + (shaOk ? rsha : "(missing or invalid — client may not update)"),
                    shaOk ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            if (manifestLastError != null && !manifestLastError.isBlank()) {
                sender.sendMessage(Component.text("Last manifest error: " + manifestLastError, NamedTextColor.RED));
            }
        }
        if ("local".equalsIgnoreCase(src) || "bundled".equalsIgnoreCase(src)) {
            File zip = new File(plugin.getDataFolder(), cfg.getString("resource-pack.local.file", "resource-pack/dpmr-pack.zip"));
            boolean okFile = zip.isFile();
            sender.sendMessage(Component.text("File on disk: " + zip.getAbsolutePath(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  exists: " + okFile + (okFile ? " (" + zip.length() + " bytes)" : ""), okFile ? NamedTextColor.GREEN : NamedTextColor.RED));
            if ("bundled".equalsIgnoreCase(src)) {
                sender.sendMessage(Component.text("  (bundled = zip copied from jar on each start)", NamedTextColor.DARK_GRAY));
            }
            boolean httpOk = localHost != null && localHost.isRunning();
            sender.sendMessage(Component.text("Built-in HTTP server running: " + httpOk, httpOk ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (httpOk) {
                sender.sendMessage(Component.text("SHA1 sent to client: " + localHost.getSha1Hex(), NamedTextColor.AQUA));
            }
            String eff = effectivePublicPackUrl();
            sender.sendMessage(Component.text("Effective player URL: " + (eff.isBlank() ? "(empty — set public-host or public-url)" : eff),
                    eff.isBlank() ? NamedTextColor.RED : NamedTextColor.GREEN));
            String ph = cfg.getString("resource-pack.local.public-host", "");
            sender.sendMessage(Component.text("public-host: " + (ph == null || ph.isBlank() ? "(empty)" : ph), NamedTextColor.GRAY));
        }
        if (!"local".equalsIgnoreCase(src) && !"bundled".equalsIgnoreCase(src)) {
            String url = effectiveExternalPackUrl();
            String sha = effectiveExternalPackSha1();
            sender.sendMessage(Component.text("Effective URL (external/manifest): " + (url.isBlank() ? "(empty)" : url),
                    url.isBlank() ? NamedTextColor.RED : NamedTextColor.GRAY));
            boolean shaOk = sha != null && sha.trim().length() == 40;
            sender.sendMessage(Component.text("sha1 (40 hex): " + (shaOk ? sha : "missing / invalid"), shaOk ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        int cm = cfg.getInt("weapons.custom-model-data.CM_SHOTGUN", 0);
        int ep = cfg.getInt("weapons.custom-model-data.EP_FUSIL_POMPE", 0);
        boolean cmdOk = cm > 0 && ep > 0;
        sender.sendMessage(Component.text("CustomModelData examples — CM_SHOTGUN=" + cm + " EP_FUSIL_POMPE=" + ep,
                cmdOk ? NamedTextColor.GREEN : NamedTextColor.RED));
        int th = cfg.getInt("weapons.custom-model-data.THOMPSON", 0);
        int ts1 = cfg.getInt("cosmetics.weapon-skins.thompson_skin_1", 0);
        sender.sendMessage(Component.text("Thompson — weapons.custom-model-data.THOMPSON=" + th
                        + " | cosmetics.weapon-skins.thompson_skin_1=" + ts1
                        + " (vanilla item: carrot_on_a_stick, modele: carrot_on_a_stick.json)",
                th > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (ts1 <= 0) {
            sender.sendMessage(Component.text("Skin thompson_skin_1 = 0 ou absent : les variantes payantes n'auront pas le bon CMD (ajoute cosmetics.weapon-skins dans config).", NamedTextColor.YELLOW));
        }
        if (!cmdOk) {
            sender.sendMessage(Component.text("If CMD = 0, items stay vanilla even with a good pack. Restart after jar update (auto config merge) or copy keys from the jar.", NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.text("Pack bundled: rebuild avec « bash scripts/build-resourcepack.sh && ./gradlew jar » pour embarquer le zip a jour.", NamedTextColor.DARK_AQUA));
        sender.sendMessage(Component.text("Weapons already held won't get the new CMD — re-get them (/dpmr givegun or shop).", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Force pack: /ressourcepack — reload config: /dpmr warworld reload — diag: /dpmr resourcepack", NamedTextColor.DARK_GRAY));
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
        String prompt = plugin.getConfig().getString("resource-pack.prompt", "DPMR pack required.");
        String baseUrl;
        String sha1;
        String src = plugin.getConfig().getString("resource-pack.source", "external");
        if ("local".equalsIgnoreCase(src) || "bundled".equalsIgnoreCase(src)) {
            if (localHost == null || !localHost.isRunning()) {
                player.sendMessage(Component.text("Local/bundled resource pack unavailable (file or HTTP port). Check server logs.", NamedTextColor.RED));
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
                    player.sendMessage(Component.text("Manifest: zip URL not found (JSON not loaded or network error). Server logs + /dpmr resourcepack", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("resource-pack.url empty. Set source: manifest + manifest.url, or source: external + url + sha1.", NamedTextColor.RED));
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
        player.sendActionBar(Component.text("Downloading resource pack...", NamedTextColor.GOLD));
        if (strict) {
            scheduleKickIfStillMissing(player);
        } else {
            cancelPendingKick(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelPendingKick(uuid);
        strictRequest.remove(uuid);
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
                p.sendActionBar(Component.text("Resource pack loaded.", NamedTextColor.GREEN));
            }
            case DECLINED -> {
                p.sendMessage(Component.text("The resource pack is required.", NamedTextColor.RED));
                if (force && strict) {
                    kickNow(p);
                }
            }
            case FAILED_DOWNLOAD -> {
                p.sendMessage(Component.text("Pack download failed. Try /ressourcepack again.", NamedTextColor.RED));
                // On ne kick pas instant: réseau lent/CDN, laisse le temps.
                if (force && kickOnFailedDownload && strict) {
                    scheduleKickIfStillMissing(p);
                }
            }
            case ACCEPTED -> p.sendActionBar(Component.text("Resource pack accepted...", NamedTextColor.YELLOW));
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
                player.kick(Component.text("Resource pack required to play on this server."));
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
