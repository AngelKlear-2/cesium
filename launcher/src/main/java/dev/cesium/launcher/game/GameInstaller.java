package dev.cesium.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cesium.launcher.util.HttpService;
import dev.cesium.launcher.util.Json;
import dev.cesium.launcher.util.LauncherException;
import dev.cesium.launcher.util.LauncherPaths;
import dev.cesium.launcher.util.OsInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads everything a merged version needs: client jar, libraries, asset index and assets.
 * Reports progress as a fraction plus a status line. Existing files with matching hashes are kept.
 */
public final class GameInstaller {
    public interface Progress {
        void report(double fraction, String status);
    }

    private static final String ASSET_BASE = "https://resources.download.minecraft.net/";
    private static final String DEFAULT_MAVEN = "https://libraries.minecraft.net/";

    /** Result of an install: everything the launcher needs to build the command line. */
    public record Installed(Path clientJar, List<Path> classpath, Path assetsDir, String assetIndexId,
                            Path nativesDir) {
    }

    private final HttpService http;
    private final LauncherPaths paths;
    private final OsInfo os = OsInfo.current();

    public GameInstaller(HttpService http, LauncherPaths paths) {
        this.http = http;
        this.paths = paths;
    }

    /**
     * @param vanilla the vanilla version JSON (client + asset index come from here)
     * @param merged  the launchable JSON (libraries come from here, may include Fabric)
     */
    public Installed install(JsonObject vanilla, JsonObject merged, Path gameDir, Progress progress)
            throws LauncherException {
        try {
            Path assets = paths.root().resolve("assets");
            Path libs = paths.root().resolve("libraries");
            Path natives = paths.root().resolve("versions").resolve(Json.str(merged, "id")).resolve("natives");
            Files.createDirectories(gameDir);
            Files.createDirectories(natives);

            progress.report(0.02, "Downloading client");
            String vid = Json.str(vanilla, "id");
            Path clientJar = paths.root().resolve("versions").resolve(vid).resolve(vid + ".jar");
            JsonObject client = vanilla.getAsJsonObject("downloads").getAsJsonObject("client");
            http.download(Json.str(client, "url"), clientJar, Json.str(client, "sha1"));

            progress.report(0.05, "Downloading libraries");
            List<Path> classpath = new ArrayList<>();
            JsonArray libraries = merged.getAsJsonArray("libraries");
            int done = 0;
            for (JsonElement el : libraries) {
                JsonObject lib = el.getAsJsonObject();
                if (!Rules.allowed(lib.get("rules"), os)) {
                    continue;
                }
                Path target = downloadLibrary(lib, libs);
                if (target != null) {
                    classpath.add(target);
                }
                done++;
                progress.report(0.05 + 0.25 * done / libraries.size(),
                        "Downloading libraries " + done + "/" + libraries.size());
            }
            classpath.add(clientJar);

            progress.report(0.32, "Downloading asset index");
            JsonObject indexRef = vanilla.getAsJsonObject("assetIndex");
            String indexId = Json.str(indexRef, "id");
            Path indexFile = assets.resolve("indexes").resolve(indexId + ".json");
            http.download(Json.str(indexRef, "url"), indexFile, Json.str(indexRef, "sha1"));
            JsonObject objects = Json.parseObject(Files.readString(indexFile, StandardCharsets.UTF_8))
                    .getAsJsonObject("objects");
            downloadAssets(objects, assets, progress);

            progress.report(1.0, "Ready");
            return new Installed(clientJar, classpath, assets, indexId, natives);
        } catch (IOException e) {
            throw new LauncherException("Disk error while installing: " + e.getMessage(), e);
        }
    }

    /** Returns the local jar path, or null when the library has no artifact to fetch. */
    private Path downloadLibrary(JsonObject lib, Path libsRoot) throws LauncherException, IOException {
        String name = Json.str(lib, "name");
        JsonObject downloads = lib.has("downloads") ? lib.getAsJsonObject("downloads") : null;
        String relPath;
        String url;
        String sha1;
        if (downloads != null && downloads.has("artifact")) {
            JsonObject artifact = downloads.getAsJsonObject("artifact");
            relPath = Json.str(artifact, "path");
            url = Json.str(artifact, "url");
            sha1 = Json.str(artifact, "sha1");
        } else {
            relPath = mavenPath(name);
            String base = lib.has("url") ? Json.str(lib, "url") : DEFAULT_MAVEN;
            url = base + relPath;
            sha1 = null;
        }
        if (url == null || url.isBlank()) {
            return null;
        }
        Path target = libsRoot.resolve(relPath);
        http.download(url, target, sha1);
        return target;
    }

    /** group:artifact:version[:classifier] to group/path/artifact/version/artifact-version[-classifier].jar */
    static String mavenPath(String name) {
        String[] parts = name.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private void downloadAssets(JsonObject objects, Path assetsRoot, Progress progress) throws LauncherException {
        int total = objects.size();
        AtomicInteger done = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (Map.Entry<String, JsonElement> e : objects.entrySet()) {
                String hash = Json.str(e.getValue().getAsJsonObject(), "hash");
                String sub = hash.substring(0, 2);
                Path target = assetsRoot.resolve("objects").resolve(sub).resolve(hash);
                futures.add(pool.submit(() -> {
                    http.download(ASSET_BASE + sub + "/" + hash, target, hash);
                    int n = done.incrementAndGet();
                    if (n % 50 == 0 || n == total) {
                        progress.report(0.32 + 0.68 * n / (double) total, "Downloading assets " + n + "/" + total);
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LauncherException("Download cancelled.", ie);
                } catch (java.util.concurrent.ExecutionException ee) {
                    Throwable cause = ee.getCause();
                    if (cause instanceof LauncherException le) {
                        throw le;
                    }
                    throw new LauncherException("Asset download failed: " + cause, cause);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
