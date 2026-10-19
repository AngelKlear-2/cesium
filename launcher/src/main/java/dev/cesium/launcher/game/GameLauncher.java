package dev.cesium.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cesium.launcher.auth.Account;
import dev.cesium.launcher.auth.AccountType;
import dev.cesium.launcher.util.Json;
import dev.cesium.launcher.util.LauncherException;
import dev.cesium.launcher.util.OsInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds the JVM and game argument lists from a merged version JSON and starts the game process.
 */
public final class GameLauncher {
    private final OsInfo os = OsInfo.current();

    /** @return the started process, already piping its output to {@code logLine}. */
    public Process launch(JsonObject merged, GameInstaller.Installed installed, Account account, String javaPath,
                          int ramGb, Path gameDir, Path authlibInjector, String launcherVersion,
                          Consumer<String> logLine) throws LauncherException {
        Map<String, String> vars = Map.ofEntries(
                Map.entry("auth_player_name", account.username()),
                Map.entry("version_name", Json.str(merged, "id")),
                Map.entry("game_directory", gameDir.toAbsolutePath().toString()),
                Map.entry("assets_root", installed.assetsDir().toAbsolutePath().toString()),
                Map.entry("assets_index_name", installed.assetIndexId()),
                Map.entry("auth_uuid", account.uuidNoDashes()),
                Map.entry("auth_access_token", account.gameAccessToken()),
                Map.entry("clientid", ""),
                Map.entry("auth_xuid", ""),
                Map.entry("user_type", account.type() == AccountType.ELYBY ? "mojang" : "legacy"),
                Map.entry("version_type", "release"),
                Map.entry("user_properties", "{}"),
                Map.entry("natives_directory", installed.nativesDir().toAbsolutePath().toString()),
                Map.entry("launcher_name", "Cesium"),
                Map.entry("launcher_version", launcherVersion),
                Map.entry("classpath", joinClasspath(installed.classpath())),
                Map.entry("classpath_separator", os.classpathSeparator()),
                Map.entry("library_directory", installed.clientJar().getParent().getParent().getParent().resolve("libraries")
                        .toAbsolutePath().toString()));

        List<String> cmd = new ArrayList<>();
        cmd.add(javaPath);
        cmd.add("-Xmx" + ramGb + "G");
        cmd.add("-Xms512M");
        cmd.add("-Dminecraft.launcher.brand=Cesium");
        cmd.add("-Dminecraft.launcher.version=" + launcherVersion);

        if (account.type() == AccountType.ELYBY && authlibInjector != null) {
            cmd.add("-javaagent:" + authlibInjector.toAbsolutePath() + "=https://authserver.ely.by");
        }

        boolean hasJvmArgs = false;
        if (merged.has("arguments") && merged.getAsJsonObject("arguments").has("jvm")) {
            hasJvmArgs = true;
            for (String a : expand(merged.getAsJsonObject("arguments").getAsJsonArray("jvm"), vars)) {
                cmd.add(a);
            }
        }
        if (!hasJvmArgs) {
            cmd.add("-Djava.library.path=" + vars.get("natives_directory"));
            cmd.add("-cp");
            cmd.add(vars.get("classpath"));
        }

        cmd.add(Json.str(merged, "mainClass"));

        if (merged.has("arguments") && merged.getAsJsonObject("arguments").has("game")) {
            cmd.addAll(expand(merged.getAsJsonObject("arguments").getAsJsonArray("game"), vars));
        } else if (merged.has("minecraftArguments")) {
            for (String part : Json.str(merged, "minecraftArguments").split(" ")) {
                cmd.add(substitute(part, vars));
            }
        }

        try {
            Process p = new ProcessBuilder(cmd)
                    .directory(gameDir.toFile())
                    .redirectErrorStream(true)
                    .start();
            Thread reader = new Thread(() -> pipe(p, logLine), "cesium-game-log");
            reader.setDaemon(true);
            reader.start();
            return p;
        } catch (IOException e) {
            throw new LauncherException("Could not start Java at " + javaPath + ": " + e.getMessage(), e);
        }
    }

    private static void pipe(Process p, Consumer<String> logLine) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                logLine.accept(line);
            }
        } catch (IOException ignored) {
            // process ended or stream closed
        }
    }

    private static String joinClasspath(List<Path> paths) {
        StringBuilder sb = new StringBuilder();
        String sep = OsInfo.current().classpathSeparator();
        for (Path p : paths) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(p.toAbsolutePath());
        }
        return sb.toString();
    }

    /** Expands an arguments array: plain strings and {rules,value} objects. */
    private List<String> expand(JsonArray arr, Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el.isJsonPrimitive()) {
                out.add(substitute(el.getAsString(), vars));
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            if (!Rules.allowed(obj.get("rules"), os)) {
                continue;
            }
            JsonElement value = obj.get("value");
            if (value.isJsonArray()) {
                for (JsonElement v : value.getAsJsonArray()) {
                    out.add(substitute(v.getAsString(), vars));
                }
            } else {
                out.add(substitute(value.getAsString(), vars));
            }
        }
        return out;
    }

    private static String substitute(String s, Map<String, String> vars) {
        String out = s;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("${" + e.getKey() + "}", e.getValue());
        }
        return out;
    }
}
