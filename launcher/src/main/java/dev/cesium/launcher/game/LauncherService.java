package dev.cesium.launcher.game;

import com.google.gson.JsonObject;
import dev.cesium.launcher.auth.Account;
import dev.cesium.launcher.auth.AccountStore;
import dev.cesium.launcher.auth.AccountType;
import dev.cesium.launcher.auth.AuthlibInjector;
import dev.cesium.launcher.auth.ElyAuth;
import dev.cesium.launcher.auth.ElyConfig;
import dev.cesium.launcher.auth.OfflineAccounts;
import dev.cesium.launcher.util.HttpService;
import dev.cesium.launcher.util.LauncherException;
import dev.cesium.launcher.util.LauncherPaths;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * UI-free orchestration: accounts, the install pipeline and game start. The window only
 * calls these methods on a worker thread and reads progress through the callbacks.
 */
public final class LauncherService {
    public static final String LAUNCHER_VERSION = "1.0.0";

    private final LauncherPaths paths;
    private final HttpService http = new HttpService();
    private final AccountStore accounts;
    private final VersionService versions;
    private final GameInstaller installer;
    private final ModsInstaller mods;
    private final GameLauncher launcher = new GameLauncher();
    private final AuthlibInjector injector;

    public LauncherService(LauncherPaths paths) {
        this.paths = paths;
        this.accounts = new AccountStore(paths);
        this.versions = new VersionService(http, paths);
        this.installer = new GameInstaller(http, paths);
        this.mods = new ModsInstaller(http, paths);
        this.injector = new AuthlibInjector(http);
    }

    public AccountStore accounts() {
        return accounts;
    }

    public ElyConfig elyConfig() {
        return ElyConfig.load(paths).orElse(null);
    }

    public Account offline(String name) throws LauncherException {
        Account a = OfflineAccounts.create(name);
        accounts.saveAndSelect(a);
        return a;
    }

    public Account refreshIfNeeded(Account a) throws LauncherException {
        if (a.type() != AccountType.ELYBY || !a.isExpired()) {
            return a;
        }
        ElyConfig cfg = elyConfig();
        if (cfg == null) {
            throw new LauncherException("Ely.by keys are missing, so the session cannot be refreshed.");
        }
        Account fresh = new ElyAuth(http, cfg).refresh(a);
        accounts.saveAndSelect(fresh);
        return fresh;
    }

    public HttpService http() {
        return http;
    }

    public VersionService versions() {
        return versions;
    }

    /**
     * Installs and starts the game. {@code entry} is null for the Cesium Fabric build.
     */
    public Process play(Account account, VersionService.Entry entry, boolean cesiumBuild, int ramGb,
                        String javaPath, GameInstaller.Progress progress, Consumer<String> log)
            throws LauncherException {
        Path gameDir = paths.defaultGameDir();
        String vanillaId = cesiumBuild ? VersionService.CESIUM_MC : entry.id();
        VersionService.Entry vanillaEntry = cesiumBuild
                ? versions.releases().stream().filter(e -> e.id().equals(vanillaId)).findFirst()
                .orElseThrow(() -> new LauncherException("Minecraft " + vanillaId + " not found in the manifest."))
                : entry;

        progress.report(0.0, "Reading version " + vanillaId);
        JsonObject vanilla = versions.vanilla(vanillaEntry);
        JsonObject merged = cesiumBuild ? versions.cesiumFabric(vanilla) : vanilla;

        GameInstaller.Installed installed = installer.install(vanilla, merged, gameDir, progress);

        if (cesiumBuild) {
            progress.report(1.0, "Preparing mods");
            Path modsDir = gameDir.resolve("mods");
            mods.ensureFabricApi(modsDir);
            mods.ensureCesiumMod(modsDir);
        }

        Path injectorJar = null;
        if (account.type() == AccountType.ELYBY) {
            injectorJar = injector.ensure(paths.authlibInjectorJar());
        }

        log.accept("[cesium] starting " + merged.get("id").getAsString() + " as " + account.username());
        return launcher.launch(merged, installed, account, javaPath, ramGb, gameDir, injectorJar,
                LAUNCHER_VERSION, log);
    }

    /** Full Ely.by login: callback server, browser, code exchange. Blocks up to about three minutes. */
    public Account elyLogin(Consumer<String> openBrowser) throws LauncherException {
        ElyConfig cfg = elyConfig();
        if (cfg == null) {
            throw new LauncherException("Ely.by keys are missing. Create dev/applications at account.ely.by "
                    + "and put them in ~/.cesium/launcher.properties (or CESIUM_ELY_CLIENT_ID / _SECRET).");
        }
        ElyAuth auth = new ElyAuth(http, cfg);
        String state = java.util.UUID.randomUUID().toString();
        try (var server = dev.cesium.launcher.auth.OAuthCallbackServer.start(state)) {
            openBrowser.accept(auth.authorizeUrl(server.redirectUri(), state));
            String code;
            try {
                code = server.codeFuture().get(3, java.util.concurrent.TimeUnit.MINUTES);
            } catch (java.util.concurrent.TimeoutException e) {
                throw new LauncherException("Ely.by login timed out after 3 minutes.", e);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable c = e.getCause();
                throw c instanceof LauncherException le ? le : new LauncherException("Ely.by login failed.", c);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LauncherException("Ely.by login cancelled.", e);
            }
            Account a = auth.login(code, server.redirectUri());
            accounts.saveAndSelect(a);
            return a;
        }
    }
}
