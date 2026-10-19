package dev.cesium.core;

/**
 * Acid-minimalism palette. All colors are ARGB ints.
 */
public final class Theme {
	public static final String TITLE = "Cesium - 1.21.11";

	public static final int BLACK = 0xFF0A0A0A;
	public static final int ACID = 0xFFB6FF00;
	public static final int WHITE = 0xFFFFFFFF;

	public static final int PANEL_BG = 0xCC0A0A0A;
	public static final int ROW_BG = 0x66000000;
	public static final int ROW_HOVER = 0x22B6FF00;
	public static final int ACID_DIM = 0xFF5E8500;
	public static final int ACID_SOFT = 0x33B6FF00;
	public static final int TEXT_DIM = 0xFFB0B0B0;
	public static final int RED = 0xFFFF4D4D;

	private Theme() {
	}

	/** Multiplies the alpha channel of an ARGB color by {@code a} (0..1). */
	public static int alpha(int argb, float a) {
		int src = argb >>> 24;
		int out = Math.round(src * Math.max(0f, Math.min(1f, a)));
		return (out << 24) | (argb & 0x00FFFFFF);
	}

	/** Replaces the alpha channel with {@code a} (0..255). */
	public static int withAlpha(int argb, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0x00FFFFFF);
	}

	public static int lerp(int from, int to, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int a = (int) (((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t));
		int r = (int) (((from >>> 16) & 0xFF) + ((((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t));
		int g = (int) (((from >>> 8) & 0xFF) + ((((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t));
		int b = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Black -> acid green gradient sample, t in 0..1. */
	public static int acidGradient(float t) {
		return lerp(BLACK, ACID, t);
	}
}
