package dev.cesium.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cesium.launcher.util.HttpService;
import dev.cesium.launcher.util.Json;
import dev.cesium.launcher.util.LauncherException;
import dev.cesium.launcher.util.LauncherPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Mojang's version manifest and Fabric's loader profile, and merges them into one
 * launchable version JSON (the same shape the vanilla launcher uses).
 */
public final class VersionService {
    public static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader/";
    public static final String CESIUM_MC = "1.21.11";

    /** One row of the version picker. */
    public record Entry(String id, String type, String url) {
        @Override
        public String toString() {
            return id;
        }
    }

    private final HttpService http;
    private final LauncherPaths paths;

    public VersionService(HttpService http, LauncherPaths paths) {
        this.http = http;
        this.paths = paths;
    }

    public List<Entry> releases() throws LauncherException {
        JsonObject manifest = Json.parseObject(http.getString(MANIFEST_URL));
        List<Entry> out = new ArrayList<>();
        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject v = el.getAsJsonObject();
            if ("release".equals(Json.str(v, "type"))) {
                out.add(new Entry(Json.str(v, "id"), "release", Json.str(v, "url")));
            }
        }
        return out;
    }

    /** Vanilla version JSON, cached under versions/&lt;id&gt;/&lt;id&gt;.json. */
    public JsonObject vanilla(Entry entry) throws LauncherException {
        Path cache = paths.root().resolve("versions").resolve(entry.id()).resolve(entry.id() + ".json");
        try {
            if (Files.isRegularFile(cache)) {
                return Json.parseObject(Files.readString(cache, StandardCharsets.UTF_8));
            }
            String body = http.getString(entry.url());
            Files.createDirectories(cache.getParent());
            Files.writeString(cache, body, StandardCharsets.UTF_8);
            return Json.parseObject(body);
        } catch (IOException e) {
            throw new LauncherException("Could not cache version " + entry.id() + ": " + e.getMessage(), e);
        }
    }

    /** Fabric loader profile for the Cesium build, merged onto the vanilla JSON. */
    public JsonObject cesiumFabric(JsonObject vanillaJson) throws LauncherException {
        JsonArray loaders = Json.parse(http.getString(FABRIC_META + CESIUM_MC)).getAsJsonArray();
        String loaderVersion = null;
        for (JsonElement el : loaders) {
            JsonObject loader = el.getAsJsonObject().getAsJsonObject("loader");
            if (loader.get("stable").getAsBoolean()) {
                loaderVersion = loader.get("version").getAsString();
                break;
            }
        }
        if (loaderVersion == null) {
            throw new LauncherException("Fabric has no stable loader for " + CESIUM_MC + " yet.");
        }
        JsonObject profile = Json.parseObject(http.getString(
                FABRIC_META + CESIUM_MC + "/" + loaderVersion + "/profile/json"));
        return merge(vanillaJson, profile);
    }

    /** Child (Fabric) overrides id, mainClass and arguments; libraries from both are kept, child first. */
    static JsonObject merge(JsonObject base, JsonObject child) {
        JsonObject out = base.deepCopy();
        out.addProperty("id", Json.str(child, "id"));
        out.addProperty("mainClass", Json.str(child, "mainClass"));
        out.remove("inheritsFrom");

        JsonArray libs = new JsonArray();
        if (child.has("libraries")) {
            child.getAsJsonArray("libraries").forEach(libs::add);
        }
        if (base.has("libraries")) {
            base.getAsJsonArray("libraries").forEach(libs::add);
        }
        out.add("libraries", libs);

        if (child.has("arguments")) {
            JsonObject args = out.has("arguments") ? out.getAsJsonObject("arguments") : new JsonObject();
            JsonObject childArgs = child.getAsJsonObject("arguments");
            for (String kind : new String[]{"game", "jvm"}) {
                if (childArgs.has(kind)) {
                    JsonArray merged = args.has(kind) ? args.getAsJsonArray(kind).deepCopy() : new JsonArray();
                    childArgs.getAsJsonArray(kind).forEach(merged::add);
                    args.add(kind, merged);
                }
            }
            out.add("arguments", args);
        }
        return out;
    }
}
