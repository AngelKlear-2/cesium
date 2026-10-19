package dev.cesium.launcher.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public final class Json {
    public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Json() {
    }

    public static JsonObject parseObject(String text) throws LauncherException {
        try {
            JsonElement el = JsonParser.parseString(text);
            if (!el.isJsonObject()) {
                throw new LauncherException("Unexpected response from server (not a JSON object).");
            }
            return el.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new LauncherException("Server returned malformed JSON.", e);
        }
    }

    public static JsonElement parse(String text) throws LauncherException {
        try {
            return JsonParser.parseString(text);
        } catch (JsonParseException e) {
            throw new LauncherException("Server returned malformed JSON.", e);
        }
    }

    public static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsString();
    }
}
