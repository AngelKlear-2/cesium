package dev.cesium.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cesium.launcher.util.HttpService;
import dev.cesium.launcher.util.Json;
import dev.cesium.launcher.util.LauncherException;
import dev.cesium.launcher.util.LauncherPaths;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Puts Fabric API and the Cesium mod jar into the game's mods folder for 1.21.11. */
public final class ModsInstaller {
    private static final String MODRINTH_FABRIC_API = "https://api.modrinth.com/v2/project/fabric-api/version";
    private static final String CESIUM_RELEASE = "https://api.github.com/repos/AngelKlear-2/cesium/releases/latest";

    private final HttpService http;
    private final LauncherPaths paths;

    public ModsInstaller(HttpService http, LauncherPaths paths) {
        this.http = http;
        this.paths = paths;
    }

    public void ensureFabricApi(Path mods) throws LauncherException {
        String q = "?game_versions=" + enc("[\"" + VersionService.CESIUM_MC + "\"]")
                + "&loaders=" + enc("[\"fabric\"]");
        JsonArray versions = Json.parse(http.getString(MODRINTH_FABRIC_API + q)).getAsJsonArray();
        if (versions.isEmpty()) {
            throw new LauncherException("Modrinth has no Fabric API build for " + VersionService.CESIUM_MC + ".");
        }
        JsonObject version = versions.get(0).getAsJsonObject();
        JsonObject file = null;
        for (JsonElement f : version.getAsJsonArray("files")) {
            JsonObject candidate = f.getAsJsonObject();
            if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
                file = candidate;
                break;
            }
        }
        if (file == null) {
            file = version.getAsJsonArray("files").get(0).getAsJsonObject();
        }
        String filename = Json.str(file, "filename");
        Path target = mods.resolve(filename);
        deleteMatching(mods, "fabric-api-", target);
        http.download(Json.str(file, "url"), target, null);
    }

    public void ensureCesiumMod(Path mods) throws LauncherException {
        Path source = paths.localModJar();
        if (!Files.isRegularFile(source)) {
            JsonObject release = Json.parseObject(http.getString(CESIUM_RELEASE));
            String url = null;
            for (JsonElement el : release.getAsJsonArray("assets")) {
                JsonObject a = el.getAsJsonObject();
                if ("cesium-mod.jar".equals(Json.str(a, "name"))) {
                    url = Json.str(a, "browser_download_url");
                }
            }
            if (url == null) {
                throw new LauncherException("The latest Cesium release has no cesium-mod.jar asset.");
            }
            source = mods.resolveSibling("cesium-mod.download.jar");
            http.download(url, source, null);
        }
        Path target = mods.resolve("cesium-mod.jar");
        deleteMatching(mods, "cesium-", target);
        try {
            Files.createDirectories(mods);
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new LauncherException("Could not copy the Cesium mod into " + mods + ": " + e.getMessage(), e);
        }
    }

    /** Removes older jars with the given prefix so two builds never load together. */
    private static void deleteMatching(Path dir, String prefix, Path keep) throws LauncherException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> victims = s.filter(p -> p.getFileName().toString().startsWith(prefix)
                    && p.getFileName().toString().endsWith(".jar") && !p.equals(keep)).toList();
            for (Path p : victims) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new LauncherException("Could not clean old mods in " + dir + ": " + e.getMessage(), e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
