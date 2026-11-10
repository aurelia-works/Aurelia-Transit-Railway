package org.mtr.aurelia.sound;

import java.util.List;

/**
 * An extra looping motor sound that plays beside a profile's main motor, with its own pitch and volume curves. A
 * layer without a pitch curve keeps its pitch at every speed, so an inverter hiss can stay put while the traction
 * notes under it rise. Pure data, read from a profile's {@code motor_layers}.
 */
public final class MotorLayer {

	public final List<String> sounds;
	/** Multiplier on the train's own motor volume, 0-2. */
	public final float volume;
	/** Sounds while stopped too; otherwise it follows the motor's power like the main motor does. */
	public final boolean always;
	private final double[] pitchSpeeds;
	private final float[] pitches;
	private final double[] gainSpeeds;
	private final float[] gains;

	MotorLayer(List<String> sounds, float volume, boolean always, double[] pitchSpeeds, float[] pitches, double[] gainSpeeds, float[] gains) {
		this.sounds = sounds;
		this.volume = volume;
		this.always = always;
		this.pitchSpeeds = pitchSpeeds;
		this.pitches = pitches;
		this.gainSpeeds = gainSpeeds;
		this.gains = gains;
	}

	/** The pitch at a speed in km/h; 1 at every speed when the layer has no pitch curve. */
	public float pitchAt(double speedKilometersPerHour) {
		return pitchSpeeds.length == 0 ? 1 : SoundProfile.interpolate(speedKilometersPerHour, pitchSpeeds, pitches);
	}

	/** The volume at a speed in km/h; 1 at every speed when the layer has no volume curve. */
	public float gainAt(double speedKilometersPerHour) {
		return gainSpeeds.length == 0 ? 1 : SoundProfile.interpolate(speedKilometersPerHour, gainSpeeds, gains);
	}
}
