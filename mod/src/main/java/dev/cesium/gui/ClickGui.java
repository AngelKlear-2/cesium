package dev.cesium.gui;

import dev.cesium.core.Anim;
import dev.cesium.core.Easing;
import dev.cesium.core.Theme;
import dev.cesium.module.Category;
import dev.cesium.module.Module;
import dev.cesium.module.ModuleManager;
import dev.cesium.setting.BoolSetting;
import dev.cesium.setting.ModeSetting;
import dev.cesium.setting.NumberSetting;
import dev.cesium.setting.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immediate-mode ClickGUI. Fade-in on open, eased category expansion, animated toggles and sliders.
 * Panels can be dragged by their header; click a row to toggle, right-click to rebind its key.
 */
public final class ClickGui extends Screen {
	private static final int PANEL_W = 130;
	private static final int HEADER_H = 18;
	private static final int ROW_H = 16;
	private static final int SETTING_H = 14;
	private static final int GAP = 6;

	private final ModuleManager modules;
	private final Map<Category, int[]> pos = new EnumMap<>(Category.class);
	private final Map<Category, Anim> expand = new EnumMap<>(Category.class);
	private final Map<Module, Anim> toggleAnim = new HashMap<>();
	private final Map<Setting<?>, Anim> sliderAnim = new HashMap<>();
	private final Anim open = new Anim(0f, 10f);

	private long lastNanos = System.nanoTime();
	private Category dragCategory;
	private int dragDx;
	private int dragDy;
	private NumberSetting sliderDrag;
	private Module waitingForKey;

	public ClickGui(ModuleManager modules) {
		super(Component.literal(Theme.TITLE));
		this.modules = modules;
		int x = 10;
		for (Category c : Category.values()) {
			pos.put(c, new int[]{x, 30});
			expand.put(c, new Anim(1f, 12f));
			x += PANEL_W + GAP;
		}
	}

	@Override
	protected void init() {
		open.snap(0f);
		open.setTarget(1f);
		lastNanos = System.nanoTime();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float dt = (System.nanoTime() - lastNanos) / 1_000_000_000f;
		lastNanos = System.nanoTime();
		float t = Easing.outCubic(open.update(dt));
		int a = (int) (255 * t);

		g.fill(0, 0, width, height, Theme.withAlpha(0x000000, (int) (90 * t)));
		g.drawString(font, Theme.TITLE, 10, 10, Theme.withAlpha(Theme.ACID, a), true);
		g.drawString(font, "RSHIFT to close", width - 96, 10, Theme.withAlpha(Theme.TEXT_DIM, a), true);

		for (Category c : Category.values()) {
			drawPanel(g, c, mouseX, mouseY, dt, a);
		}

		if (dragCategory != null) {
			int[] p = pos.get(dragCategory);
			p[0] = mouseX - dragDx;
			p[1] = mouseY - dragDy;
		}
	}

	private void drawPanel(GuiGraphics g, Category c, int mx, int my, float dt, int a) {
		int[] p = pos.get(c);
		List<Module> mods = modules.inCategory(c);

		Anim ex = expand.get(c);
		float k = Easing.inOutCubic(ex.update(dt));
		int body = 0;
		for (Module m : mods) {
			body += ROW_H + (int) (visibleSettings(m).size() * SETTING_H * k);
		}
		int h = HEADER_H + body + 2;

		g.fill(p[0], p[1], p[0] + PANEL_W, p[1] + h, Theme.withAlpha(Theme.PANEL_BG, (int) (0xCC * a / 255f)));
		g.fill(p[0], p[1], p[0] + PANEL_W, p[1] + 2, Theme.withAlpha(Theme.ACID, a));
		g.drawString(font, c.title, p[0] + 6, p[1] + 6, Theme.withAlpha(Theme.ACID, a), true);

		int y = p[1] + HEADER_H;
		for (Module m : mods) {
			boolean hover = mx >= p[0] && mx < p[0] + PANEL_W && my >= y && my < y + ROW_H;
			Anim tg = toggleAnim.computeIfAbsent(m, mm -> new Anim(mm.isEnabled() ? 1f : 0f, 14f));
			tg.setTarget(m.isEnabled() ? 1f : 0f);
			float tv = tg.update(dt);

			g.fill(p[0] + 3, y, p[0] + PANEL_W - 3, y + ROW_H - 2,
					Theme.withAlpha(hover ? Theme.ROW_HOVER : Theme.ROW_BG, a));
			String label = waitingForKey == m ? "> press key <" : m.name();
			int col = Theme.lerp(Theme.withAlpha(Theme.WHITE, a), Theme.withAlpha(Theme.ACID, a), tv);
			g.drawString(font, label, p[0] + 8, y + 4, col, false);

			int pillX = p[0] + PANEL_W - 24;
			g.fill(pillX, y + 4, pillX + 14, y + 10, Theme.withAlpha(Theme.ACID_SOFT, a));
			int knobX = pillX + Math.round(tv * 8);
			g.fill(knobX, y + 4, knobX + 6, y + 10,
					Theme.withAlpha(m.isEnabled() ? Theme.ACID : Theme.TEXT_DIM, a));
			y += ROW_H;

			if (k > 0.01f) {
				int alpha = (int) (a * k);
				for (Setting<?> s : visibleSettings(m)) {
					drawSetting(g, p[0], y, s, dt, alpha);
					y += (int) (SETTING_H * k);
				}
			}
		}
	}

	private static List<Setting<?>> visibleSettings(Module m) {
		List<Setting<?>> out = new ArrayList<>();
		for (Setting<?> s : m.settings()) {
			if (s.isVisible()) {
				out.add(s);
			}
		}
		return out;
	}

	private void drawSetting(GuiGraphics g, int x, int y, Setting<?> s, float dt, int a) {
		int fg = Theme.withAlpha(Theme.TEXT_DIM, a);
		if (s instanceof BoolSetting b) {
			g.drawString(font, b.name(), x + 10, y + 3, fg, false);
			g.fill(x + PANEL_W - 24, y + 3, x + PANEL_W - 10, y + 9,
					Theme.withAlpha(b.get() ? Theme.ACID : Theme.ACID_SOFT, a));
		} else if (s instanceof NumberSetting n) {
			Anim an = sliderAnim.computeIfAbsent(s, ss -> new Anim((float) n.ratio(), 16f));
			an.setTarget((float) n.ratio());
			float r = an.update(dt);
			g.drawString(font, n.name() + " " + fmt(n.get()), x + 10, y, fg, false);
			int bw = PANEL_W - 20;
			g.fill(x + 10, y + 10, x + 10 + bw, y + 11, Theme.withAlpha(Theme.ACID_SOFT, a));
			g.fill(x + 10, y + 10, x + 10 + Math.round(bw * r), y + 11, Theme.withAlpha(Theme.ACID, a));
		} else if (s instanceof ModeSetting m) {
			g.drawString(font, m.name() + ": " + m.get(), x + 10, y + 3, fg, false);
		}
	}

	private static String fmt(double v) {
		return Math.abs(v - Math.round(v)) < 1e-6 ? Long.toString(Math.round(v)) : String.format("%.2f", v);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();

		for (Category c : Category.values()) {
			int[] p = pos.get(c);
			if (button == 0 && inside(mx, my, p[0], p[1], PANEL_W, HEADER_H)) {
				dragCategory = c;
				dragDx = (int) mx - p[0];
				dragDy = (int) my - p[1];
				return true;
			}
		}

		for (Category c : Category.values()) {
			int[] p = pos.get(c);
			int y = p[1] + HEADER_H;
			for (Module m : modules.inCategory(c)) {
				if (inside(mx, my, p[0], y, PANEL_W, ROW_H)) {
					if (button == 0) {
						m.toggle();
					} else if (button == 1) {
						waitingForKey = m;
					}
					return true;
				}
				y += ROW_H;
				for (Setting<?> s : visibleSettings(m)) {
					if (inside(mx, my, p[0], y, PANEL_W, SETTING_H)) {
						if (s instanceof BoolSetting b) {
							b.toggle();
						} else if (s instanceof ModeSetting md) {
							md.cycle();
						} else if (s instanceof NumberSetting n) {
							n.setRatio((mx - (p[0] + 10)) / (PANEL_W - 20.0));
							sliderDrag = n;
						}
						return true;
					}
					y += SETTING_H;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragCategory != null) {
			return super.mouseDragged(event, dx, dy);
		}
		if (sliderDrag != null) {
			int[] p = null;
			for (Category c : Category.values()) {
				for (Module m : modules.inCategory(c)) {
					if (visibleSettings(m).contains(sliderDrag)) {
						p = pos.get(c);
					}
				}
			}
			if (p != null) {
				sliderDrag.setRatio((event.x() - (p[0] + 10)) / (PANEL_W - 20.0));
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragCategory = null;
		sliderDrag = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (waitingForKey != null) {
			int code = event.key();
			waitingForKey.setKey(code == 256 ? -1 : code); // 256 = GLFW_KEY_ESCAPE clears the bind
			waitingForKey = null;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
