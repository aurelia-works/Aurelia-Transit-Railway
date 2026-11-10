package org.mtr.aurelia.sound;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One sound profile from a resource pack: a name, a motor pitch curve and the one-shot or looping sounds that replace
 * the train's own. Any field may be empty, in which case the train keeps its own sound for that part. Pure data; the
 * resource pack loader and the sound engine live elsewhere.
 */
public final class SoundProfile {

	public final String id;
	public final String name;
	/** Multiplier on the train's own motor volume, 0-2. */
	public final float motorVolume;
	/** Looping motor or engine clips from the profile; empty keeps the train's own motor. */
	public final List<String> motorSounds;
	/** The motor keeps sounding while stopped (a diesel engine idles); electric motors ring only under power. */
	public final boolean motorAlwaysOn;
	/** Extra motor loops that play beside the main one, each with its own curves; empty for most profiles. */
	public final List<MotorLayer> motorLayers;
	private final double[] motorSpeeds;
	private final float[] motorPitches;
	private final double[] volumeSpeeds;
	private final float[] volumeCurve;

	@Nullable
	public final String doorChime;
	@Nullable
	public final String doorOpen;
	@Nullable
	public final String doorClose;
	@Nullable
	public final String brakeRelease;
	@Nullable
	public final String compressorLoop;
	@Nullable
	public final String compressorAttack;
	@Nullable
	public final String compressorRelease;
	public final List<String> flange;

	SoundProfile(String id, String name, float motorVolume, List<String> motorSounds, boolean motorAlwaysOn, double[] motorSpeeds, float[] motorPitches,
			double[] volumeSpeeds, float[] volumeCurve, List<MotorLayer> motorLayers,
			@Nullable String doorChime, @Nullable String doorOpen, @Nullable String doorClose, @Nullable String brakeRelease,
			@Nullable String compressorLoop, @Nullable String compressorAttack, @Nullable String compressorRelease, List<String> flange) {
		this.id = id;
		this.name = name;
		this.motorVolume = motorVolume;
		this.motorSounds = motorSounds;
		this.motorAlwaysOn = motorAlwaysOn;
		this.motorSpeeds = motorSpeeds;
		this.motorPitches = motorPitches;
		this.volumeSpeeds = volumeSpeeds;
		this.volumeCurve = volumeCurve;
		this.motorLayers = motorLayers;
		this.doorChime = doorChime;
		this.doorOpen = doorOpen;
		this.doorClose = doorClose;
		this.brakeRelease = brakeRelease;
		this.compressorLoop = compressorLoop;
		this.compressorAttack = compressorAttack;
		this.compressorRelease = compressorRelease;
		this.flange = flange;
	}

	/**
	 * The profile's pitch multiplier at a speed in km/h, interpolated between the curve's points and clamped at both
	 * ends. 1 (leave the train's own pitch alone) when the profile has no curve.
	 */
	public float pitchAt(double speedKilometersPerHour) {
		if (motorSpeeds.length == 0) {
			return 1;
		}
		return interpolate(speedKilometersPerHour, motorSpeeds, motorPitches);
	}

	/** The name to show, falling back to the id's path. */
	public String displayName() {
		if (!name.isBlank()) {
			return name;
		}
		final int colon = id.indexOf(':');
		return colon < 0 ? id : id.substring(colon + 1);
	}

	/**
	 * The profile's motor volume at a speed in km/h, interpolated and clamped like {@link #pitchAt}. 1 when the profile
	 * has no volume curve.
	 */
	public float motorGainAt(double speedKilometersPerHour) {
		if (volumeSpeeds.length == 0) {
			return 1;
		}
		return interpolate(speedKilometersPerHour, volumeSpeeds, volumeCurve);
	}

	static float interpolate(double value, double[] xs, float[] ys) {
		if (value <= xs[0]) {
			return ys[0];
		}
		final int last = xs.length - 1;
		if (value >= xs[last]) {
			return ys[last];
		}
		for (int i = 1; i <= last; i++) {
			if (value <= xs[i]) {
				final double span = xs[i] - xs[i - 1];
				final double through = span <= 0 ? 1 : (value - xs[i - 1]) / span;
				return (float) (ys[i - 1] + (ys[i] - ys[i - 1]) * through);
			}
		}
		return ys[last];
	}

	static List<String> soundList(@Nullable List<String> sounds) {
		if (sounds == null) {
			return Collections.emptyList();
		}
		final List<String> cleaned = new ArrayList<>();
		for (final String sound : sounds) {
			if (sound != null && !sound.isBlank()) {
				cleaned.add(sound.trim());
			}
		}
		return Collections.unmodifiableList(cleaned);
	}

	@Override
	public String toString() {
		return "SoundProfile{id='" + id + "', name='" + name + "', speeds=" + Arrays.toString(motorSpeeds) + ", pitches=" + Arrays.toString(motorPitches) + '}';
	}
}
