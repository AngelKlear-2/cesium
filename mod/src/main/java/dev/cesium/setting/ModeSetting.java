package dev.cesium.setting;

import java.util.List;
import java.util.function.Supplier;

public final class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String name, String def, List<String> modes) {
		this(name, def, modes, null);
	}

	public ModeSetting(String name, String def, List<String> modes, Supplier<Boolean> visible) {
		super(name, def, visible);
		if (!modes.contains(def)) {
			throw new IllegalArgumentException("default mode " + def + " not in " + modes);
		}
		this.modes = List.copyOf(modes);
	}

	public List<String> modes() {
		return modes;
	}

	public void cycle() {
		int i = modes.indexOf(value);
		value = modes.get((i + 1) % modes.size());
	}

	@Override
	public void set(String v) {
		if (modes.contains(v)) {
			super.set(v);
		}
	}

	@Override
	public String serialize() {
		return value;
	}

	@Override
	public boolean deserialize(String raw) {
		if (!modes.contains(raw)) {
			return false;
		}
		value = raw;
		return true;
	}
}
