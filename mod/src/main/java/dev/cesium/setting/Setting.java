package dev.cesium.setting;

import java.util.function.Supplier;

/** A named, typed value owned by a module. The GUI and config code work with this base type. */
public abstract class Setting<T> {
	private final String name;
	private final Supplier<Boolean> visible;
	protected T value;

	protected Setting(String name, T defaultValue, Supplier<Boolean> visible) {
		this.name = name;
		this.value = defaultValue;
		this.visible = visible == null ? () -> true : visible;
	}

	public final String name() {
		return name;
	}

	public final T get() {
		return value;
	}

	public void set(T v) {
		this.value = v;
	}

	public final boolean isVisible() {
		return visible.get();
	}

	/** Serialised form used by the config file. */
	public abstract String serialize();

	/** Parses a value written by {@link #serialize()}. Returns false when the input is invalid. */
	public abstract boolean deserialize(String raw);
}
