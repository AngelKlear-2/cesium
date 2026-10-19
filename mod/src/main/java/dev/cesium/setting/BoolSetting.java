package dev.cesium.setting;

import java.util.function.Supplier;

public final class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String name, boolean def) {
		this(name, def, null);
	}

	public BoolSetting(String name, boolean def, Supplier<Boolean> visible) {
		super(name, def, visible);
	}

	public void toggle() {
		set(!value);
	}

	@Override
	public String serialize() {
		return Boolean.toString(value);
	}

	@Override
	public boolean deserialize(String raw) {
		if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
			return false;
		}
		value = Boolean.parseBoolean(raw);
		return true;
	}
}
