package dev.cesium.launcher.auth;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.cesium.launcher.util.FileSecurity;
import dev.cesium.launcher.util.Json;
import dev.cesium.launcher.util.LauncherException;
import dev.cesium.launcher.util.LauncherPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persists accounts to accounts.json and remembers which one was used last. */
public final class AccountStore {
    private static final Gson GSON = Json.PRETTY;

    private static final class Data {
        String lastUsed;
        List<Account> accounts = new ArrayList<>();
    }

    private final Path file;
    private Data data = new Data();

    public AccountStore(LauncherPaths paths) {
        this.file = paths.accountsFile();
    }

    public synchronized void load() throws LauncherException {
        if (!Files.isRegularFile(file)) {
            data = new Data();
            return;
        }
        try {
            Data loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
            data = loaded == null ? new Data() : loaded;
            if (data.accounts == null) {
                data.accounts = new ArrayList<>();
            }
            data.accounts.removeIf(a -> a == null || a.type() == null || a.username() == null);
        } catch (IOException | JsonParseException e) {
            data = new Data();
            throw new LauncherException("Could not read saved accounts (" + file + "). They will be ignored.", e);
        }
    }

    public synchronized List<Account> accounts() {
        return List.copyOf(data.accounts);
    }

    public synchronized Optional<Account> lastUsed() {
        if (data.lastUsed == null) {
            return Optional.empty();
        }
        return data.accounts.stream().filter(a -> a.key().equals(data.lastUsed)).findFirst();
    }

    /** Adds or replaces the account, marks it as last used and writes the file. */
    public synchronized void saveAndSelect(Account account) throws LauncherException {
        data.accounts.removeIf(a -> a.key().equals(account.key()));
        data.accounts.add(account);
        data.lastUsed = account.key();
        write();
    }

    public synchronized void remove(Account account) throws LauncherException {
        data.accounts.removeIf(a -> a.key().equals(account.key()));
        if (account.key().equals(data.lastUsed)) {
            data.lastUsed = null;
        }
        write();
    }

    /** Forgets which account was last used without deleting any account. */
    public synchronized void clearSelection() throws LauncherException {
        data.lastUsed = null;
        write();
    }

    private void write() throws LauncherException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            FileSecurity.restrictToOwner(tmp);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            FileSecurity.restrictToOwner(file);
        } catch (IOException e) {
            throw new LauncherException("Could not save accounts to " + file + ": " + e.getMessage(), e);
        }
    }
}
