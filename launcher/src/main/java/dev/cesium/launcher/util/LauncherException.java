package dev.cesium.launcher.util;

/** Checked exception whose message is safe to show directly to the user. */
public class LauncherException extends Exception {
    public LauncherException(String message) {
        super(message);
    }

    public LauncherException(String message, Throwable cause) {
        super(message, cause);
    }
}
