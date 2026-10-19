package dev.cesium.config;

import dev.cesium.module.Module;
import dev.cesium.module.ModuleManager;
import dev.cesium.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Saves module state (enabled, key, settings) to config/cesium.properties.
 * Plain key=value, one entry per setting, so it can be hand-edited.
 */
public final class ConfigManager {
	private static final Logger LOG = Logger.getLogger("Cesium");
	private final ModuleManager modules;
	private final Path file;

	public ConfigManager(ModuleManager modules) {
		this.modules = modules;
		this.file = FabricLoader.getInstance().getConfigDir().resolve("cesium.properties");
	}

	public void load() {
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				int eq = line.indexOf('=');
				if (line.isBlank() || line.startsWith("#") || eq < 0) {
					continue;
				}
				apply(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
			}
		} catch (IOException e) {
			LOG.log(Level.WARNING, "Could not read " + file, e);
		}
	}

	private void apply(String key, String value) {
		int dot = key.indexOf('.');
		if (dot < 0) {
			return;
		}
		String modName = key.substring(0, dot);
		String field = key.substring(dot + 1);
		modules.byName(modName).ifPresent(m -> {
			if (field.equals("enabled")) {
				m.setEnabled(Boolean.parseBoolean(value));
			} else if (field.equals("key")) {
				try {
					m.setKey(Integer.parseInt(value));
				} catch (NumberFormatException ignored) {
					// keep default
				}
			} else {
				for (Setting<?> s : m.settings()) {
					if (s.name().equalsIgnoreCase(field)) {
						s.deserialize(value);
					}
				}
			}
		});
	}

	public void save() {
		Map<String, String> out = new LinkedHashMap<>();
		for (Module m : modules.all()) {
			String base = m.name();
			out.put(base + ".enabled", Boolean.toString(m.isEnabled()));
			out.put(base + ".key", Integer.toString(m.key()));
			for (Setting<?> s : m.settings()) {
				out.put(base + "." + s.name(), s.serialize());
			}
		}
		StringBuilder sb = new StringBuilder("# Cesium - 1.21.11\n");
		out.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.log(Level.WARNING, "Could not save " + file, e);
		}
	}
}
