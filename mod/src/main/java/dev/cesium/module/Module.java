package dev.cesium.module;

import dev.cesium.setting.Setting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every feature. Subclasses declare settings as fields via {@link #add(Setting)}
 * and override {@link #onEnable()}, {@link #onDisable()} and {@link #onTick()}.
 */
public abstract class Module {
	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;
	private int key = -1;

	protected Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	public final String name() {
		return name;
	}

	public final String description() {
		return description;
	}

	public final Category category() {
		return category;
	}

	public final boolean isEnabled() {
		return enabled;
	}

	public final int key() {
		return key;
	}

	public final void setKey(int key) {
		this.key = key;
	}

	public final List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	protected final <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public final void toggle() {
		setEnabled(!enabled);
	}

	public final void setEnabled(boolean value) {
		if (enabled == value) {
			return;
		}
		enabled = value;
		try {
			if (value) {
				onEnable();
			} else {
				onDisable();
			}
		} catch (RuntimeException e) {
			enabled = false;
			throw e;
		}
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called once per client tick while enabled. */
	public void onTick() {
	}

	/** Called every rendered frame while enabled. Use for HUD drawing. */
	public void onRender(GuiGraphics g, float partialTick) {
	}
}
