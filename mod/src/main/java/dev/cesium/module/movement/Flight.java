package dev.cesium.module.movement;

import dev.cesium.module.Category;
import dev.cesium.module.Module;
import dev.cesium.setting.ModeSetting;
import dev.cesium.setting.NumberSetting;
import dev.cesium.util.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Flight with two modes.
 * Vanilla: sets the velocity every tick from the movement input, no gravity.
 * Velocity: keeps a short upward impulse and nudges horizontal velocity, so vertical
 * drift looks natural and the server sees a moving player.
 */
public final class Flight extends Module {
	private final ModeSetting mode = add(new ModeSetting("Mode", "Vanilla", List.of("Vanilla", "Velocity")));
	private final NumberSetting speed = add(new NumberSetting("Speed", 0.6, 0.1, 3.0, 0.05));
	private final NumberSetting verticalSpeed = add(new NumberSetting("Vertical", 0.5, 0.1, 2.0, 0.05));

	public Flight() {
		super("Flight", "Fly around freely", Category.MOVEMENT);
	}

	@Override
	protected void onEnable() {
		LocalPlayer p = Mc.player();
		if (p != null) {
			p.getAbilities().flying = false;
		}
	}

	@Override
	protected void onDisable() {
		LocalPlayer p = Mc.player();
		if (p != null) {
			p.setNoGravity(false);
		}
	}

	@Override
	public void onTick() {
		if (!Mc.inGame()) {
			return;
		}
		LocalPlayer p = Mc.player();
		double yaw = Math.toRadians(p.getYRot());
		float fwd = p.input.forwardImpulse;
		float strafe = p.input.leftImpulse;

		double hx = -Math.sin(yaw) * fwd + Math.cos(yaw) * strafe;
		double hz = Math.cos(yaw) * fwd + Math.sin(yaw) * strafe;
		double h = speed.get();
		double v = verticalSpeed.get();

		double vy;
		if (p.input.jumping) {
			vy = v;
		} else if (p.input.shiftKeyDown) {
			vy = -v;
		} else {
			vy = 0.0;
		}

		if (mode.get().equals("Velocity")) {
			p.setNoGravity(false);
			Vec3 cur = p.getDeltaMovement();
			double baseY = p.input.jumping ? v : (p.input.shiftKeyDown ? -v : Math.max(-0.02, cur.y * 0.6));
			p.setDeltaMovement(hx * h, baseY, hz * h);
			p.fallDistance = 0f;
		} else {
			p.setNoGravity(true);
			p.setDeltaMovement(hx * h, vy, hz * h);
			p.fallDistance = 0f;
		}
	}
}
