package dev.cesium.core;

public final class Easing {
	private Easing() {
	}

	public static float clamp01(float t) {
		return t < 0f ? 0f : Math.min(t, 1f);
	}

	public static float outCubic(float t) {
		float u = 1f - clamp01(t);
		return 1f - u * u * u;
	}

	public static float inCubic(float t) {
		t = clamp01(t);
		return t * t * t;
	}

	public static float inOutCubic(float t) {
		t = clamp01(t);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	public static float outBack(float t) {
		t = clamp01(t);
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}
}
