package dev.cesium.launcher.auth;

/**
 * A saved player identity. For OFFLINE accounts the tokens are unused; for ELYBY accounts
 * {@code expiresAt} is the access token expiry in epoch seconds (0 when unknown).
 */
public record Account(AccountType type, String username, String uuid, String accessToken,
                      String refreshToken, long expiresAt) {

    public static Account offline(String username, String uuid) {
        return new Account(AccountType.OFFLINE, username, uuid, "0", null, 0L);
    }

    /** UUID without dashes, as Minecraft's --uuid argument and Ely.by use it. */
    public String uuidNoDashes() {
        return uuid == null ? "" : uuid.replace("-", "");
    }

    /** Token handed to the game as auth_access_token. */
    public String gameAccessToken() {
        return type == AccountType.OFFLINE || accessToken == null || accessToken.isBlank() ? "0" : accessToken;
    }

    public boolean isExpired() {
        return type == AccountType.ELYBY && expiresAt > 0 && System.currentTimeMillis() / 1000L >= expiresAt - 60;
    }

    public boolean canRefresh() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    /** Stable key used to identify this account in the store. */
    public String key() {
        return type + ":" + uuidNoDashes();
    }
}
