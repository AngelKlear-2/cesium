package dev.cesium;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cesium.config.ConfigManager;
import dev.cesium.gui.ClickGui;
import dev.cesium.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric entry point. Wires the module manager, the ClickGUI key and the tick/render events.
 */
public final class CesiumClient implements ClientModInitializer {
	public static final String NAME = "Cesium";
	public static final String VERSION_TITLE = "Cesium - 1.21.11";

	private static CesiumClient instance;

	private final ModuleManager modules = new ModuleManager();
	private final ConfigManager config = new ConfigManager(modules);
	private ClickGui gui;
	private KeyMapping guiKey;

	public static CesiumClient get() {
		return instance;
	}

	public ModuleManager modules() {
		return modules;
	}

	public ConfigManager config() {
		return config;
	}

	public ClickGui gui() {
		return gui;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		gui = new ClickGui(modules);

		guiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.cesium.gui",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyMapping.Category.MISC));

		config.load();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (guiKey.consumeClick()) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.screen == null) {
					mc.setScreen(gui);
				}
			}
			modules.tick();
		});

		HudRenderCallback.EVENT.register((graphics, tick) ->
				modules.render(graphics, tick.getGameTimeDeltaPartialTick(false)));

		Runtime.getRuntime().addShutdownHook(new Thread(config::save, "cesium-config-save"));
	}
}
