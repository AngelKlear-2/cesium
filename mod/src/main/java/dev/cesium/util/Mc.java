package dev.cesium.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * Tiny accessor for the game client. Looked up lazily because the mod
 * initializer can run before the Minecraft instance is fully constructed.
 */
public final class Mc {
	private Mc() {
	}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static LocalPlayer player() {
		return mc().player;
	}

	public static ClientLevel level() {
		return mc().level;
	}

	/** True when we are in a world and can safely touch player/level. */
	public static boolean inGame() {
		Minecraft mc = mc();
		return mc.player != null && mc.level != null && mc.gameMode != null;
	}
}
