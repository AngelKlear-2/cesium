package dev.cesium.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cesium.launcher.util.OsInfo;

/**
 * Evaluates Mojang's "rules" arrays. A missing rules array means allowed. When rules exist the
 * last matching rule wins; if none match, the entry is disallowed.
 * Feature flags (demo user, custom resolution, quick play) are never enabled by this launcher.
 */
public final class Rules {
    private Rules() {
    }

    public static boolean allowed(JsonElement rulesEl, OsInfo os) {
        if (rulesEl == null || rulesEl.isJsonNull() || !rulesEl.isJsonArray()) {
            return true;
        }
        JsonArray rules = rulesEl.getAsJsonArray();
        boolean result = false;
        for (JsonElement el : rules) {
            JsonObject rule = el.getAsJsonObject();
            if (!matches(rule, os)) {
                continue;
            }
            result = "allow".equals(rule.get("action").getAsString());
        }
        return result;
    }

    private static boolean matches(JsonObject rule, OsInfo os) {
        if (rule.has("features")) {
            return false;
        }
        if (!rule.has("os")) {
            return true;
        }
        JsonObject osRule = rule.getAsJsonObject("os");
        if (osRule.has("name") && !osRule.get("name").getAsString().equals(os.name())) {
            return false;
        }
        if (osRule.has("arch") && !osRule.get("arch").getAsString().equals(os.arch())) {
            return false;
        }
        return true;
    }
}
