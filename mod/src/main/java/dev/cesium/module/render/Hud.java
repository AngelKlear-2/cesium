package dev.cesium.module.render;

import dev.cesium.core.Anim;
import dev.cesium.core.Easing;
import dev.cesium.core.Theme;
import dev.cesium.module.Category;
import dev.cesium.module.Module;
import dev.cesium.module.ModuleManager;
import dev.cesium.CesiumClient;
import dev.cesium.setting.BoolSetting;
import dev.cesium.util.Mc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * On-screen overlay: animated watermark, active module list, FPS and coordinates.
 */
public final class Hud extends Module {
	private final BoolSetting watermark = add(new BoolSetting("Watermark", true));
	private final BoolSetting moduleList = add(new BoolSetting("Module list", true));
	private final BoolSetting fps = add(new BoolSetting("FPS", true));
	private final BoolSetting coords = add(new BoolSetting("Coordinates", true));

	private final Anim watermarkReveal = new Anim(0f, 4f);
	private final Anim listReveal = new Anim(0f, 6f);
	private long lastNanos = System.nanoTime();

	public Hud() {
		super("HUD", "Watermark, module list, FPS and position", Category.RENDER);
		setEnabled(true);
	}

	@Override
	public void onRender(GuiGraphics g, float partialTick) {
		Minecraft mc = Mc.mc();
		Font font = mc.font;
		float dt = (System.nanoTime() - lastNanos) / 1_000_000_000f;
		lastNanos = System.nanoTime();

		watermarkReveal.setTarget(1f);
		float wm = Easing.outCubic(watermarkReveal.update(dt));

		if (watermark.get()) {
			int alpha = (int) (255 * wm);
			String text = Theme.TITLE;
			int w = font.width(text) + 12;
			int x = 6;
			int y = 6;
			int boxW = (int) (w * wm);
			g.fill(x, y, x + boxW, y + 14, Theme.withAlpha(Theme.PANEL_BG, (int) (0xCC * wm)));
			g.fill(x, y + 13, x + boxW, y + 14, Theme.withAlpha(Theme.ACID, alpha));
			if (wm > 0.5f) {
				g.drawString(font, text, x + 6, y + 3, Theme.withAlpha(Theme.ACID, alpha), true);
			}
		}

		int y = 26;
		if (fps.get()) {
			String s = mc.getFps() + " FPS";
			g.drawString(font, s, 6, y, Theme.ACID, true);
			y += 10;
		}
		if (coords.get() && Mc.inGame()) {
			var p = Mc.player();
			String s = String.format(Locale.ROOT, "%.1f / %.1f / %.1f", p.getX(), p.getY(), p.getZ());
			g.drawString(font, s, 6, y, Theme.WHITE, true);
		}

		if (moduleList.get()) {
			drawModuleList(g, font, dt);
		}
	}

	private void drawModuleList(GuiGraphics g, Font font, float dt) {
		ModuleManager mm = CesiumClient.get() == null ? null : CesiumClient.get().modules();
		if (mm == null) {
			return;
		}
		List<Module> active = new ArrayList<>();
		for (Module m : mm.all()) {
			if (m.isEnabled() && m != this) {
				active.add(m);
			}
		}
		active.sort(Comparator.comparingInt((Module m) -> font.width(m.name())).reversed());

		int screenW = Mc.mc().getWindow().getGuiScaledWidth();
		listReveal.setTarget(1f);
		float lr = Easing.outCubic(listReveal.update(dt));
		int y = 6;
		for (Module m : active) {
			String name = m.name();
			int w = font.width(name) + 10;
			int x = screenW - (int) (w * lr) - 6;
			g.fill(x, y, screenW - 6, y + 11, Theme.withAlpha(Theme.PANEL_BG, (int) (0xB3 * lr)));
			g.fill(screenW - 8, y, screenW - 6, y + 11, Theme.withAlpha(Theme.ACID, (int) (255 * lr)));
			g.drawString(font, name, x + 5, y + 2, Theme.withAlpha(Theme.ACID, (int) (255 * lr)), false);
			y += 12;
		}
	}
}
