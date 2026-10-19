package dev.cesium.launcher.util;

import java.nio.file.Path;

/** All locations the launcher reads from or writes to, rooted at one directory. */
public record LauncherPaths(Path root) {

    public static LauncherPaths defaults() {
        return new LauncherPaths(Path.of(System.getProperty("user.home"), ".cesium"));
    }

    public Path accountsFile() {
        return root.resolve("accounts.json");
    }

    public Path settingsFile() {
        return root.resolve("settings.json");
    }

    public Path propertiesFile() {
        return root.resolve("launcher.properties");
    }

    public Path authlibInjectorJar() {
        return root.resolve("authlib-injector.jar");
    }

    public Path localModJar() {
        return root.resolve("cesium-mod.jar");
    }

    public Path defaultGameDir() {
        return root.resolve("game");
    }
}
