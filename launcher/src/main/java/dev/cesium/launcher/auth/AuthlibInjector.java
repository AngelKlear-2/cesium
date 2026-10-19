package dev.cesium.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.cesium.launcher.util.HttpService;
import dev.cesium.launcher.util.Json;
import dev.cesium.launcher.util.LauncherException;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Makes sure authlib-injector.jar is present. Ely.by accounts need it as a -javaagent so the
 * game talks to authserver.ely.by instead of Mojang's session servers.
 */
public final class AuthlibInjector {
    private static final String LATEST = "https://api.github.com/repos/yushijinhun/authlib-injector/releases/latest";

    private final HttpService http;

    public AuthlibInjector(HttpService http) {
        this.http = http;
    }

    public Path ensure(Path target) throws LauncherException {
        if (Files.isRegularFile(target)) {
            return target;
        }
        JsonObject release = Json.parseObject(http.getString(LATEST));
        JsonArray assets = release.getAsJsonArray("assets");
        String url = null;
        for (var el : assets) {
            JsonObject a = el.getAsJsonObject();
            String name = Json.str(a, "name");
            if (name != null && name.startsWith("authlib-injector-") && name.endsWith(".jar")) {
                url = Json.str(a, "browser_download_url");
                break;
            }
        }
        if (url == null) {
            throw new LauncherException("No authlib-injector jar in the latest GitHub release.");
        }
        http.download(url, target, null);
        return target;
    }
}
