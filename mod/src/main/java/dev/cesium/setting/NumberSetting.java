package dev.cesium.setting;

import java.util.function.Supplier;

public final class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;

	public NumberSetting(String name, double def, double min, double max, double step) {
		this(name, def, min, max, step, null);
	}

	public NumberSetting(String name, double def, double min, double max, double step,
	                     Supplier<Boolean> visible) {
		super(name, def, visible);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	@Override
	public void set(Double v) {
		double clamped = Math.max(min, Math.min(max, v));
		if (step > 0) {
			clamped = Math.round(clamped / step) * step;
		}
		super.set(clamped);
	}

	/** Normalised 0..1 position, used by the slider. */
	public double ratio() {
		return (value - min) / (max - min);
	}

	public void setRatio(double r) {
		set(min + Math.max(0, Math.min(1, r)) * (max - min));
	}

	@Override
	public String serialize() {
		return Double.toString(value);
	}

	@Override
	public boolean deserialize(String raw) {
		try {
			set(Double.parseDouble(raw));
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
