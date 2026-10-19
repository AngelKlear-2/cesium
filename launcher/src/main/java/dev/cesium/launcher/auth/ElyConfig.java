package dev.cesium.launcher.auth;

import dev.cesium.launcher.util.LauncherPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/** Ely.by OAuth application credentials. */
public record ElyConfig(String clientId, String clientSecret) {
    public static final String ENV_ID = "CESIUM_ELY_CLIENT_ID";
    public static final String ENV_SECRET = "CESIUM_ELY_CLIENT_SECRET";

    /** Environment first, then launcher.properties. Empty when either value is missing. */
    public static Optional<ElyConfig> load(LauncherPaths paths) {
        String id = blankToNull(System.getenv(ENV_ID));
        String secret = blankToNull(System.getenv(ENV_SECRET));
        if (id == null || secret == null) {
            Properties props = new Properties();
            Path file = paths.propertiesFile();
            if (Files.isRegularFile(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    props.load(r);
                } catch (IOException ignored) {
                    // Treated as missing configuration; the UI explains how to fix it.
                }
            }
            if (id == null) {
                id = blankToNull(props.getProperty("ely.client_id"));
            }
            if (secret == null) {
                secret = blankToNull(props.getProperty("ely.client_secret"));
            }
        }
        if (id == null || secret == null) {
            return Optional.empty();
        }
        return Optional.of(new ElyConfig(id, secret));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
