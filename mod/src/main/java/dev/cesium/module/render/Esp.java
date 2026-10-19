package dev.cesium.module.render;

import dev.cesium.module.Category;
import dev.cesium.module.Module;
import dev.cesium.setting.BoolSetting;
import dev.cesium.setting.NumberSetting;
import dev.cesium.util.Mc;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity ESP through the vanilla glowing outline. Glow is set per entity and restored on disable,
 * so the world is never left with stuck outlines.
 */
public final class Esp extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 64, 8, 128, 1));
	private final BoolSetting players = add(new BoolSetting("Players", true));
	private final BoolSetting mobs = add(new BoolSetting("Mobs", false));
	private final BoolSetting teamCheck = add(new BoolSetting("Team check", true));

	private final Set<Entity> glowing = Collections.newSetFromMap(new java.util.WeakHashMap<>());

	public Esp() {
		super("ESP", "Outlines entities in the acid-green accent", Category.RENDER);
	}

	@Override
	public void onTick() {
		if (!Mc.inGame()) {
			return;
		}
		Set<Entity> want = new HashSet<>();
		double r = range.get();
		for (Entity e : Mc.level().entitiesForRendering()) {
			if (!(e instanceof LivingEntity le) || e == Mc.player() || !le.isAlive()) {
				continue;
			}
			boolean isPlayer = e instanceof Player;
			if (isPlayer ? !players.get() : !mobs.get()) {
				continue;
			}
			if (isPlayer && teamCheck.get() && sameTeam((Player) e)) {
				continue;
			}
			if (Mc.player().distanceTo(le) > r) {
				continue;
			}
			want.add(le);
		}

		for (Entity e : glowing) {
			if (!want.contains(e)) {
				e.setGlowingTag(false);
			}
		}
		glowing.clear();
		for (Entity e : want) {
			e.setGlowingTag(true);
			glowing.add(e);
		}
	}

	private static boolean sameTeam(Player other) {
		Player self = Mc.player();
		return self.getTeam() != null && self.isAlliedTo(other);
	}

	@Override
	protected void onDisable() {
		for (Entity e : glowing) {
			e.setGlowingTag(false);
		}
		glowing.clear();
	}
}
