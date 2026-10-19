package dev.cesium.module.movement;

import dev.cesium.module.Category;
import dev.cesium.module.Module;
import dev.cesium.setting.ModeSetting;
import dev.cesium.util.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.List;

/**
 * Stops fall damage. Packet mode reports onGround=true to the server while falling;
 * Clientside mode only zeroes fallDistance locally.
 */
public final class NoFall extends Module {
	private final ModeSetting mode = add(new ModeSetting("Mode", "Packet", List.of("Packet", "Clientside")));

	public NoFall() {
		super("NoFall", "Removes fall damage", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		if (!Mc.inGame()) {
			return;
		}
		LocalPlayer p = Mc.player();
		if (p.fallDistance <= 2.5f) {
			return;
		}
		if (mode.get().equals("Packet") && p.connection != null) {
			p.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, false));
		}
		p.fallDistance = 0f;
	}
}
