package org.mtr.aurelia.sound;

import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.SoundEvent;
import org.mtr.mapping.mapper.SoundHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link SoundProfile} with its sound ids resolved to events and the train's volume sliders applied. The sound
 * classes read this; a null instance means the train sounds exactly as it did before profiles existed.
 */
public final class ProfileSounds {

	private final SoundProfile profile;
	public final String loopKey;
	public final float motorVolume;
	public final float doorVolume;
	public final float chimeVolume;
	public final float brakeVolume;
	public final float compressorVolume;
	public final float flangeVolume;
	@Nullable
	public final SoundEvent doorChime;
	@Nullable
	public final SoundEvent doorOpen;
	@Nullable
	public final SoundEvent doorClose;
	@Nullable
	public final SoundEvent brakeRelease;
	@Nullable
	public final SoundEvent compressorLoop;
	@Nullable
	public final SoundEvent compressorAttack;
	@Nullable
	public final SoundEvent compressorRelease;
	public final SoundEvent[] flange;
	public final SoundEvent[] motor;
	public final boolean motorAlwaysOn;
	/** The profile's extra motor loops, resolved; empty for most profiles. */
	public final Layer[] motorLayers;

	private ProfileSounds(SoundProfile profile, SoundProfileStore.Settings settings, @Nullable SoundEvent doorChime, @Nullable SoundEvent doorOpen, @Nullable SoundEvent doorClose,
			@Nullable SoundEvent brakeRelease, @Nullable SoundEvent compressorLoop, @Nullable SoundEvent compressorAttack, @Nullable SoundEvent compressorRelease, SoundEvent[] flange, SoundEvent[] motor) {
		this.profile = profile;
		final StringBuilder layerKey = new StringBuilder();
		final double motorSlider = settings.volume(SoundProfileStore.Part.MOTOR);
		motorLayers = new Layer[profile.motorLayers.size()];
		for (int i = 0; i < motorLayers.length; i++) {
			final MotorLayer layer = profile.motorLayers.get(i);
			motorLayers[i] = new Layer(layer, sounds(layer.sounds), (float) (layer.volume * motorSlider));
			layerKey.append('|').append(String.join(",", layer.sounds));
		}
		loopKey = (profile.compressorLoop == null ? "" : profile.compressorLoop) + "|" + String.join(",", profile.flange) + "|" + String.join(",", profile.motorSounds) + layerKey;
		motorVolume = (float) (profile.motorVolume * motorSlider);
		doorVolume = (float) settings.volume(SoundProfileStore.Part.DOOR);
		chimeVolume = (float) settings.volume(SoundProfileStore.Part.CHIME);
		brakeVolume = (float) settings.volume(SoundProfileStore.Part.BRAKE);
		compressorVolume = (float) settings.volume(SoundProfileStore.Part.COMPRESSOR);
		flangeVolume = (float) settings.volume(SoundProfileStore.Part.FLANGE);
		this.doorChime = doorChime;
		this.doorOpen = doorOpen;
		this.doorClose = doorClose;
		this.brakeRelease = brakeRelease;
		this.compressorLoop = compressorLoop;
		this.compressorAttack = compressorAttack;
		this.compressorRelease = compressorRelease;
		this.flange = flange;
		this.motor = motor;
		motorAlwaysOn = profile.motorAlwaysOn;
	}

	public static ProfileSounds resolve(SoundProfile profile, SoundProfileStore.Settings settings) {
		return new ProfileSounds(profile, settings,
				sound(profile.doorChime),
				sound(profile.doorOpen),
				sound(profile.doorClose),
				sound(profile.brakeRelease),
				sound(profile.compressorLoop),
				sound(profile.compressorAttack),
				sound(profile.compressorRelease),
				sounds(profile.flange),
				sounds(profile.motorSounds));
	}

	public float pitchAt(double speedKilometersPerHour) {
		return profile.pitchAt(speedKilometersPerHour);
	}

	public float motorGainAt(double speedKilometersPerHour) {
		return profile.motorGainAt(speedKilometersPerHour);
	}

	@Nullable
	private static SoundEvent sound(@Nullable String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		try {
			return SoundHelper.createSoundEvent(new Identifier(id));
		} catch (Exception e) {
			return null;
		}
	}

	private static SoundEvent[] sounds(List<String> ids) {
		final List<SoundEvent> events = new ArrayList<>();
		for (final String id : ids) {
			final SoundEvent event = sound(id);
			if (event != null) {
				events.add(event);
			}
		}
		return events.toArray(new SoundEvent[0]);
	}

	/** One {@link MotorLayer} with its sounds resolved and the train's motor slider applied to its volume. */
	public static final class Layer {

		private final MotorLayer layer;
		public final SoundEvent[] sounds;
		public final float volume;
		public final boolean always;

		private Layer(MotorLayer layer, SoundEvent[] sounds, float volume) {
			this.layer = layer;
			this.sounds = sounds;
			this.volume = volume;
			always = layer.always;
		}

		public float pitchAt(double speedKilometersPerHour) {
			return layer.pitchAt(speedKilometersPerHour);
		}

		public float gainAt(double speedKilometersPerHour) {
			return layer.gainAt(speedKilometersPerHour);
		}
	}
}
