package dev.cesium.launcher.auth;

import dev.cesium.launcher.util.LauncherException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

public final class OfflineAccounts {
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private OfflineAccounts() {
    }

    public static boolean isValidName(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    public static Account create(String name) throws LauncherException {
        if (!isValidName(name)) {
            throw new LauncherException("Nickname must be 3-16 characters: letters, digits or underscore.");
        }
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return Account.offline(name, uuid.toString());
    }
}
