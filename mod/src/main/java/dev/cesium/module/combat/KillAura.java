package dev.cesium.module.combat;

import dev.cesium.core.Theme;
import dev.cesium.module.Category;
import dev.cesium.module.Module;
import dev.cesium.setting.BoolSetting;
import dev.cesium.setting.ModeSetting;
import dev.cesium.setting.NumberSetting;
import dev.cesium.util.Mc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Attacks living entities in range. Rotates the camera smoothly toward the target before each hit.
 * CPS is capped by a randomised delay so the swing timing is not perfectly regular.
 */
public final class KillAura extends Module {
	private final NumberSetting cps = add(new NumberSetting("CPS", 10, 1, 20, 1));
	private final NumberSetting range = add(new NumberSetting("Range", 4.2, 2.0, 6.0, 0.1));
	private final ModeSetting rotations = add(new ModeSetting("Rotations", "Smooth",
			List.of("Off", "Smooth", "Instant")));
	private final NumberSetting smoothness = add(new NumberSetting("Smoothness", 0.35, 0.05, 1.0, 0.05,
			() -> !rotations.get().equals("Off")));
	private final BoolSetting multi = add(new BoolSetting("Multi-target", false));
	private final NumberSetting maxTargets = add(new NumberSetting("Max targets", 3, 1, 8, 1,
			multi::get));
	private final ModeSetting sorting = add(new ModeSetting("Sort", "Distance",
			List.of("Distance", "Health", "Angle")));
	private final BoolSetting players = add(new BoolSetting("Players", true));
	private final BoolSetting mobs = add(new BoolSetting("Mobs", false));
	private final BoolSetting visibleOnly = add(new BoolSetting("Visible only", true));

	private long nextAttackNanos;
	private LivingEntity lastTarget;

	public KillAura() {
		super("KillAura", "Attacks entities in range", Category.COMBAT);
	}

	@Override
	protected void onDisable() {
		lastTarget = null;
	}

	@Override
	public void onTick() {
		if (!Mc.inGame()) {
			return;
		}
		LocalPlayer self = Mc.player();
		List<LivingEntity> targets = findTargets(self);
		if (targets.isEmpty()) {
			lastTarget = null;
			return;
		}
		LivingEntity primary = targets.get(0);
		lastTarget = primary;

		if (!rotations.get().equals("Off")) {
			rotateTowards(self, primary);
		}

		long now = System.nanoTime();
		if (now < nextAttackNanos) {
			return;
		}
		double jitter = 1.0 + (Math.random() - 0.5) * 0.2;
		nextAttackNanos = now + (long) (1_000_000_000L / cps.get() * jitter);

		Minecraft mc = Mc.mc();
		MultiPlayerGameMode gm = mc.gameMode;
		if (gm == null) {
			return;
		}
		int hits = multi.get() ? Math.min(maxTargets.get().intValue(), targets.size()) : 1;
		for (int i = 0; i < hits; i++) {
			gm.attack(self, targets.get(i));
		}
		self.swing(InteractionHand.MAIN_HAND);
	}

	private List<LivingEntity> findTargets(LocalPlayer self) {
		List<LivingEntity> out = new ArrayList<>();
		double r = range.get();
		for (Entity e : Mc.level().entitiesForRendering()) {
			if (!(e instanceof LivingEntity le) || e == self || !le.isAlive()) {
				continue;
			}
			if (e instanceof Player && !players.get()) {
				continue;
			}
			if (!(e instanceof Player) && !mobs.get()) {
				continue;
			}
			if (self.distanceTo(le) > r) {
				continue;
			}
			if (visibleOnly.get() && !self.hasLineOfSight(le)) {
				continue;
			}
			out.add(le);
		}
		Comparator<LivingEntity> cmp = switch (sorting.get()) {
			case "Health" -> Comparator.comparingDouble(LivingEntity::getHealth);
			case "Angle" -> Comparator.comparingDouble(le -> angleTo(self, le));
			default -> Comparator.comparingDouble(self::distanceTo);
		};
		out.sort(cmp);
		return out;
	}

	private static double angleTo(LocalPlayer self, LivingEntity target) {
		double[] want = yawPitch(self.getEyePosition(), target.getEyePosition());
		return Math.abs(Mth.wrapDegrees((float) want[0] - self.getYRot()));
	}

	private void rotateTowards(LocalPlayer self, LivingEntity target) {
		double[] want = yawPitch(self.getEyePosition(), target.getEyePosition());
		float wantYaw = (float) want[0];
		float wantPitch = (float) want[1];
		if (rotations.get().equals("Instant")) {
			self.setYRot(wantYaw);
			self.setXRot(wantPitch);
			return;
		}
		float k = smoothness.get().floatValue();
		float dyaw = Mth.wrapDegrees(wantYaw - self.getYRot());
		float dpitch = wantPitch - self.getXRot();
		self.setYRot(self.getYRot() + dyaw * k);
		self.setXRot(Mth.clamp(self.getXRot() + dpitch * k, -90f, 90f));
	}

	/** Returns {yaw, pitch} in Minecraft's convention (yaw 0 = +Z, pitch positive = looking down). */
	private static double[] yawPitch(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
		return new double[]{yaw, pitch};
	}

	/** Exposed for the HUD / debugging. */
	public LivingEntity lastTarget() {
		return lastTarget;
	}

	/** Accent color used when drawing the target indicator. */
	public static int accent() {
		return Theme.ACID;
	}
}
