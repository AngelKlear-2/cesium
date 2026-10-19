package dev.cesium.launcher.util;

import java.util.Locale;

/** Operating system and architecture as Mojang version JSON rules name them. */
public final class OsInfo {
    private final String name;
    private final String arch;

    private OsInfo(String name, String arch) {
        this.name = name;
        this.arch = arch;
    }

    public static OsInfo current() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String name;
        if (os.contains("win")) {
            name = "windows";
        } else if (os.contains("mac") || os.contains("darwin")) {
            name = "osx";
        } else {
            name = "linux";
        }
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String arch;
        if (a.equals("aarch64") || a.equals("arm64")) {
            arch = "arm64";
        } else if (a.equals("x86") || a.equals("i386") || a.equals("i686")) {
            arch = "x86";
        } else {
            arch = "x86_64";
        }
        return new OsInfo(name, arch);
    }

    /** One of windows, linux, osx. */
    public String name() {
        return name;
    }

    /** One of x86_64, x86, arm64. */
    public String arch() {
        return arch;
    }

    public boolean isWindows() {
        return name.equals("windows");
    }

    public boolean isMac() {
        return name.equals("osx");
    }

    public String classpathSeparator() {
        return isWindows() ? ";" : ":";
    }

    /** Suffix used by legacy `natives` map entries and ${arch} substitution. */
    public String bitness() {
        return arch.equals("x86") ? "32" : "64";
    }

    /** Path of the java executable in the running JVM's home. */
    public String defaultJavaPath() {
        String exe = isWindows() ? "java.exe" : "java";
        return java.nio.file.Path.of(System.getProperty("java.home"), "bin", exe).toString();
    }
}
