package dev.cesium.launcher.auth;

import com.google.gson.JsonObject;
import dev.cesium.launcher.util.HttpService;
import dev.cesium.launcher.util.Json;
import dev.cesium.launcher.util.LauncherException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Ely.by OAuth2 authorization-code flow: authorize URL, code exchange, refresh and profile lookup. */
public final class ElyAuth {
    static final String AUTHORIZE = "https://account.ely.by/oauth2/v1";
    static final String TOKEN = "https://account.ely.by/api/oauth2/v1/token";
    static final String INFO = "https://account.ely.by/api/account/v1/info";
    static final String SCOPE = "account_info minecraft_server_session";

    private final HttpService http;
    private final ElyConfig config;

    public ElyAuth(HttpService http, ElyConfig config) {
        this.http = http;
        this.config = config;
    }

    public String authorizeUrl(String redirectUri, String state) {
        return AUTHORIZE
                + "?client_id=" + enc(config.clientId())
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state);
    }

    /** Exchanges the authorization code and loads the profile. */
    public Account login(String code, String redirectUri) throws LauncherException {
        JsonObject token = Json.parseObject(http.postForm(TOKEN, Map.of(
                "grant_type", "authorization_code",
                "client_id", config.clientId(),
                "client_secret", config.clientSecret(),
                "redirect_uri", redirectUri,
                "code", code)));
        return toAccount(token);
    }

    /** Refreshes an expired Ely.by account. Throws when the refresh token was revoked. */
    public Account refresh(Account old) throws LauncherException {
        if (!old.canRefresh()) {
            throw new LauncherException("Ely.by session expired. Log in again.");
        }
        JsonObject token = Json.parseObject(http.postForm(TOKEN, Map.of(
                "grant_type", "refresh_token",
                "client_id", config.clientId(),
                "client_secret", config.clientSecret(),
                "refresh_token", old.refreshToken())));
        return toAccount(token);
    }

    private Account toAccount(JsonObject token) throws LauncherException {
        String access = Json.str(token, "access_token");
        if (access == null || access.isBlank()) {
            throw new LauncherException("Ely.by did not return an access token.");
        }
        String refresh = Json.str(token, "refresh_token");
        long expiresIn = token.has("expires_in") ? token.get("expires_in").getAsLong() : 0L;
        long expiresAt = expiresIn > 0 ? Instant.now().getEpochSecond() + expiresIn : 0L;

        JsonObject info = Json.parseObject(http.getString(INFO, Map.of("Authorization", "Bearer " + access)));
        String username = Json.str(info, "username");
        String uuid = Json.str(info, "uuid");
        if (username == null || uuid == null) {
            throw new LauncherException("Ely.by profile response was missing username or uuid.");
        }
        return new Account(AccountType.ELYBY, username, withDashes(uuid), access, refresh, expiresAt);
    }

    static String withDashes(String uuid) {
        if (uuid.contains("-")) {
            return uuid;
        }
        return UUID.fromString(uuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5")).toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
