package dev.cesium.core;

/**
 * Frame-rate independent exponential smoothing toward a target value.
 */
public final class Anim {
	private float value;
	private float target;
	private final float speed;

	public Anim(float initial, float speed) {
		this.value = initial;
		this.target = initial;
		this.speed = speed;
	}

	public Anim(float initial) {
		this(initial, 14f);
	}

	public void setTarget(float target) {
		this.target = target;
	}

	public void snap(float v) {
		this.value = v;
		this.target = v;
	}

	/** Advances the animation by {@code dt} seconds and returns the new value. */
	public float update(float dt) {
		float k = 1f - (float) Math.exp(-speed * Math.max(0f, dt));
		value += (target - value) * k;
		if (Math.abs(target - value) < 0.0005f) {
			value = target;
		}
		return value;
	}

	public float get() {
		return value;
	}

	public float target() {
		return target;
	}
}
