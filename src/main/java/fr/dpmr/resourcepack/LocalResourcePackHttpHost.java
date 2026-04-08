package fr.dpmr.resourcepack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.Executors;

/**
 * Sert un fichier .zip sur HTTP pour que les clients Minecraft puissent le telecharger
 * (obligatoire : le jeu ne charge un pack que via URL + hash).
 */
final class LocalResourcePackHttpHost {

    private final File zipFile;
    private final String bindAddress;
    private final int port;
    private final String contextPath;
    /** When non-null/non-blank, {@link #start()} skips reading the whole file for SHA-1 (heavy on main thread). */
    private final String precomputedSha1Hex;
    private HttpServer server;
    private String sha1Hex = "";

    LocalResourcePackHttpHost(File zipFile, String bindAddress, int port, String contextPath) {
        this(zipFile, bindAddress, port, contextPath, null);
    }

    LocalResourcePackHttpHost(File zipFile, String bindAddress, int port, String contextPath, String precomputedSha1Hex) {
        this.zipFile = zipFile;
        this.bindAddress = bindAddress == null || bindAddress.isBlank() ? "0.0.0.0" : bindAddress.trim();
        this.port = port;
        this.contextPath = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
        this.precomputedSha1Hex = precomputedSha1Hex;
    }

    void start() throws IOException {
        if (!zipFile.isFile()) {
            throw new IOException("Fichier pack introuvable: " + zipFile.getAbsolutePath());
        }
        if (precomputedSha1Hex != null && !precomputedSha1Hex.isBlank()) {
            sha1Hex = precomputedSha1Hex;
        } else {
            sha1Hex = sha1HexOfFile(zipFile);
        }
        InetSocketAddress addr;
        if ("0.0.0.0".equals(bindAddress)) {
            addr = new InetSocketAddress(port);
        } else {
            addr = new InetSocketAddress(bindAddress, port);
        }
        server = HttpServer.create(addr, 0);
        server.createContext(contextPath, ex -> handleRequest(ex));
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "dpmr-resourcepack-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
    }

    private void handleRequest(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }
        if (!contextPath.equals(ex.getRequestURI().getPath())) {
            byte[] msg = "Not found".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(msg);
            }
            return;
        }
        long len = zipFile.length();
        ex.getResponseHeaders().set("Content-Type", "application/zip");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"dpmr-pack.zip\"");
        ex.sendResponseHeaders(200, len);
        try (OutputStream os = ex.getResponseBody(); FileInputStream in = new FileInputStream(zipFile)) {
            in.transferTo(os);
        }
    }

    void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
    }

    boolean isRunning() {
        return server != null;
    }

    String getSha1Hex() {
        return sha1Hex;
    }

    /** Full-file read; call from an async worker, not the server main thread, for large zips. */
    static String sha1HexOfFile(File f) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (DigestInputStream in = new DigestInputStream(new FileInputStream(f), md)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
