package org.mtr.mod.sound;

import org.mtr.aurelia.sound.ProfileSounds;
import org.mtr.core.data.Siding;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.SoundEvent;
import org.mtr.mapping.mapper.SoundHelper;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.config.Config;

import javax.annotation.Nullable;
import java.util.Random;

public class LegacyVehicleSound extends VehicleSoundBase {

	private final String legacySpeedSoundBaseResource;
	private final int legacySpeedSoundCount;
	private final boolean legacyUseAccelerationSoundsWhenCoasting;
	private final boolean legacyConstantPlaybackSpeed;
	private final String legacyDoorSoundBaseResource;
	private final double legacyDoorCloseSoundTime;
	@Nullable
	private ProfileSounds profileSounds;
	private String profileLoopKey = "";
	private VehicleLoopingSoundInstance[] profileMotor = new VehicleLoopingSoundInstance[0];
	private float oldSpeedChange;

	private final char[] SOUND_GROUP_LETTERS = {'a', 'b', 'c'};
	private final int SOUND_GROUP_SIZE = SOUND_GROUP_LETTERS.length;

	private static final String SOUND_ACCELERATION = "_acceleration_";
	private static final String SOUND_DECELERATION = "_deceleration_";
	private static final String SOUND_DOOR_OPEN = "_door_open";
	private static final String SOUND_DOOR_CLOSE = "_door_close";

	public LegacyVehicleSound(@Nullable String legacySpeedSoundBaseResource, int legacySpeedSoundCount, boolean legacyUseAccelerationSoundsWhenCoasting, boolean legacyConstantPlaybackSpeed, String legacyDoorSoundBaseResource, double legacyDoorCloseSoundTime) {
		this.legacySpeedSoundBaseResource = legacySpeedSoundBaseResource;
		this.legacySpeedSoundCount = legacySpeedSoundCount;
		this.legacyUseAccelerationSoundsWhenCoasting = legacyUseAccelerationSoundsWhenCoasting;
		this.legacyConstantPlaybackSpeed = legacyConstantPlaybackSpeed;
		this.legacyDoorSoundBaseResource = legacyDoorSoundBaseResource;
		this.legacyDoorCloseSoundTime = legacyDoorCloseSoundTime;
	}

	@Override
	public void playMotorSound(BlockPos blockPos, float speed, float speedChange, float acceleration, boolean isOnRoute) {
		if (!InitClient.canPlaySound()) {
			for (final VehicleLoopingSoundInstance motor : profileMotor) {
				motor.setData(0, 1, blockPos);
			}
			oldSpeedChange = speedChange;
			return;
		}

		final ProfileSounds profile = profileSounds;
		if (profile != null && profile.brakeRelease != null && oldSpeedChange < 0 && speedChange >= 0) {
			ScheduledSound.schedule(blockPos, profile.brakeRelease, profile.brakeVolume, 1);
		}
		oldSpeedChange = speedChange;

		if (profile != null && profileMotor.length > 0) {
			final float gain = profile.motorGainAt(speed * 3600) * profile.motorVolume * (profile.motorAlwaysOn || speed > 0 ? 1 : 0);
			for (final VehicleLoopingSoundInstance motor : profileMotor) {
				motor.setData(gain, profile.pitchAt(speed * 3600), blockPos);
			}
		} else if (legacySpeedSoundCount > 0 && legacySpeedSoundBaseResource != null) {
			final double referenceAcceleration = legacyConstantPlaybackSpeed ? acceleration : Siding.ACCELERATION_DEFAULT;
			final int floorSpeed = (int) Math.floor(speed / referenceAcceleration / InitClient.MILLIS_PER_SPEED_SOUND);
			if (floorSpeed > 0) {
				final Random random = new Random();

				final int index = Math.min(floorSpeed, legacySpeedSoundCount) - 1;
				final boolean isAccelerating = speedChange == 0 ? legacyUseAccelerationSoundsWhenCoasting || random.nextBoolean() : speedChange > 0;
				final String speedSoundId = legacySpeedSoundBaseResource + (isAccelerating ? SOUND_ACCELERATION : SOUND_DECELERATION) + index / SOUND_GROUP_SIZE + SOUND_GROUP_LETTERS[index % SOUND_GROUP_SIZE];
				final float pitch = SoundTuning.motorPitch(speed / referenceAcceleration / InitClient.MILLIS_PER_SPEED_SOUND, legacySpeedSoundCount, Config.getClient().getMotorPitchSmoothing()) * (profile == null ? 1 : profile.pitchAt(speed * 3600));
				ScheduledSound.schedule(blockPos, SoundHelper.createSoundEvent(new Identifier(Init.MOD_ID, speedSoundId)), profile == null ? 1 : profile.motorVolume, pitch);
			}
		}
	}

	@Override
	public void dispose() {
		for (final VehicleLoopingSoundInstance motor : profileMotor) {
			motor.dispose();
		}
		profileMotor = new VehicleLoopingSoundInstance[0];
	}

	@Override
	protected void playDoorSound(BlockPos blockPos, boolean isOpen) {
		final ProfileSounds profile = profileSounds;
		final SoundEvent profileDoor = profile == null ? null : isOpen ? profile.doorOpen : profile.doorClose;
		if (profile != null && profileDoor != null) {
			ScheduledSound.schedule(blockPos, profileDoor, (float) (2 * profile.doorVolume), 1);
		} else {
			ScheduledSound.schedule(blockPos, SoundHelper.createSoundEvent(new Identifier(Init.MOD_ID, String.format("%s%s", legacyDoorSoundBaseResource, isOpen ? SOUND_DOOR_OPEN : SOUND_DOOR_CLOSE))), 2, 1);
		}
	}

	@Override
	protected double getDoorCloseSoundTime() {
		return legacyDoorCloseSoundTime;
	}

	@Override
	public void setSoundProfile(@Nullable ProfileSounds profile) {
		final String loopKey = profile == null ? "" : profile.loopKey;
		profileSounds = profile;
		if (profileLoopKey.equals(loopKey)) {
			return;
		}
		profileLoopKey = loopKey;
		for (final VehicleLoopingSoundInstance motor : profileMotor) {
			motor.dispose();
		}
		if (profile != null && profile.motor.length > 0) {
			final VehicleLoopingSoundInstance[] motors = new VehicleLoopingSoundInstance[profile.motor.length];
			for (int i = 0; i < motors.length; i++) {
				motors[i] = new VehicleLoopingSoundInstance(profile.motor[i]);
			}
			profileMotor = motors;
		} else {
			profileMotor = new VehicleLoopingSoundInstance[0];
		}
	}

	@Nullable
	@Override
	protected SoundEvent getProfileDoorChime() {
		return profileSounds == null ? null : profileSounds.doorChime;
	}

	@Override
	protected float getProfileDoorChimeVolume() {
		return profileSounds == null ? 1 : (float) (profileSounds.chimeVolume * Config.getClient().getDoorChimeVolume());
	}
}
