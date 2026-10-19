package dev.cesium.module.movement;

import dev.cesium.module.Category;
import dev.cesium.module.Module;
import dev.cesium.setting.BoolSetting;
import dev.cesium.setting.NumberSetting;
import dev.cesium.util.Mc;
import net.minecraft.client.player.LocalPlayer;

/**
 * Horizontal speed boost while moving on the ground. Adds velocity toward the input direction,
 * capped at the configured max so it does not stack forever.
 */
public final class Speed extends Module {
	private final NumberSetting max = add(new NumberSetting("Max", 0.42, 0.2, 1.2, 0.01));
	private final NumberSetting accel = add(new NumberSetting("Accel", 0.12, 0.02, 0.5, 0.01));
	private final BoolSetting autoJump = add(new BoolSetting("Auto jump", true));

	public Speed() {
		super("Speed", "Moves faster on the ground", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		if (!Mc.inGame()) {
			return;
		}
		LocalPlayer p = Mc.player();
		float fwd = p.input.forwardImpulse;
		float strafe = p.input.leftImpulse;
		if (fwd == 0f && strafe == 0f) {
			return;
		}
		if (!p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isFallFlying()) {
			return;
		}

		if (autoJump.get()) {
			p.jumpFromGround();
		}

		double yaw = Math.toRadians(p.getYRot());
		double dirX = -Math.sin(yaw) * fwd + Math.cos(yaw) * strafe;
		double dirZ = Math.cos(yaw) * fwd + Math.sin(yaw) * strafe;
		double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
		if (len < 1e-6) {
			return;
		}
		dirX /= len;
		dirZ /= len;

		double vx = p.getDeltaMovement().x;
		double vz = p.getDeltaMovement().z;
		double targetX = dirX * max.get();
		double targetZ = dirZ * max.get();
		double k = accel.get();
		p.setDeltaMovement(vx + (targetX - vx) * k, p.getDeltaMovement().y, vz + (targetZ - vz) * k);
	}
}
