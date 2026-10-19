package dev.cesium.module;

import dev.cesium.module.combat.KillAura;
import dev.cesium.module.movement.Flight;
import dev.cesium.module.movement.NoFall;
import dev.cesium.module.movement.Speed;
import dev.cesium.module.player.AutoTotem;
import dev.cesium.module.player.ChestStealer;
import dev.cesium.module.render.Esp;
import dev.cesium.module.render.Hud;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Owns every module instance. Modules are registered once at startup. */
public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();

	public ModuleManager() {
		register(new KillAura());
		register(new Flight());
		register(new Speed());
		register(new NoFall());
		register(new Esp());
		register(new Hud());
		register(new AutoTotem());
		register(new ChestStealer());
	}

	private void register(Module m) {
		modules.add(m);
	}

	public List<Module> all() {
		return Collections.unmodifiableList(modules);
	}

	public List<Module> inCategory(Category c) {
		return modules.stream().filter(m -> m.category() == c).toList();
	}

	public Optional<Module> byName(String name) {
		return modules.stream().filter(m -> m.name().equalsIgnoreCase(name)).findFirst();
	}

	/** Ticks every enabled module. Called from the client tick event. */
	public void tick() {
		for (Module m : modules) {
			if (m.isEnabled()) {
				m.onTick();
			}
		}
	}

	/** Renders every enabled module. Called from the HUD / world render event. */
	public void render(GuiGraphics g, float partialTick) {
		for (Module m : modules) {
			if (m.isEnabled()) {
				m.onRender(g, partialTick);
			}
		}
	}

	/** Dispatches a key press to modules bound to that key. Returns true if one was toggled. */
	public boolean onKey(int keyCode) {
		boolean handled = false;
		for (Module m : modules) {
			if (m.key() == keyCode && keyCode != -1) {
				m.toggle();
				handled = true;
			}
		}
		return handled;
	}
}
