package org.mtr.mod.sound;

import org.mtr.aurelia.sound.ProfileSounds;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.SoundEvent;
import org.mtr.mod.config.Config;

import javax.annotation.Nullable;
import java.util.Random;

public class BveVehicleSound extends VehicleSoundBase {

	private final BveVehicleSoundConfig config;
	@Nullable
	private ProfileSounds profileSounds;
	private String appliedLoopKey = "";
	private VehicleLoopingSoundInstance[] profileMotor = new VehicleLoopingSoundInstance[0];
	private final ProfileLayerLoops profileLayers = new ProfileLayerLoops();

	private float oldSpeedChange;
	private boolean oldOnRoute = false;

	private float motorCurrentOutput = 0;
	private float motorBreakerTimer = -1;

	private float mrPress;
	private boolean isCompressorActive;
	private boolean isCompressorActiveLastElapsed;

	/** The run sound to fall back to when a rail has no sound index or no matching run sound: the lowest one defined. */
	private final int defaultRunSoundIndex;
	/** The rail run sound of the call now going through {@link #playMotorSound}, set only for the length of {@link #playVehicleSound}. */
	@Nullable
	private VehicleSoundBase.RunSoundInfo pendingRunSound;

	/** The run sounds given a volume on the last frame, so one that leaves the blend can be silenced once; -1 is none. */
	private int playingRunIndexOld = -1;
	private int playingRunIndexNew = -1;

	private final VehicleLoopingSoundHolder vehicleLoopingSoundHolder;
	private boolean hasBogieSample;
	private double lastBogieX;
	private double lastBogieZ;
	private double lastHeading;

	public BveVehicleSound(BveVehicleSoundConfig config) {
		this.config = config;

		mrPress = randomInt(config.config.mrPressMin, config.config.mrPressMax + 1);
		isCompressorActive = randomInt(0, 20) == 0; // Currently set to 1/20 at client-side load
		isCompressorActiveLastElapsed = isCompressorActive;

		final VehicleLoopingSoundInstance[] soundLoopMotor = new VehicleLoopingSoundInstance[config.config.motor.length];
		for (int i = 0; i < Math.min(config.config.motor.length, config.motorData.getSoundCount()); i++) {
			if (config.config.motor[i] != null) {
				soundLoopMotor[i] = new VehicleLoopingSoundInstance(config.config.motor[i]);
			}
		}
		final VehicleLoopingSoundInstance[] soundLoopFlange = new VehicleLoopingSoundInstance[config.config.flange.length];
		for (int i = 0; i < config.config.flange.length; i++) {
			if (config.config.flange[i] != null) {
				soundLoopFlange[i] = new VehicleLoopingSoundInstance(config.config.flange[i]);
			}
		}
		final Int2ObjectOpenHashMap<VehicleLoopingSoundInstance> soundLoopRun = new Int2ObjectOpenHashMap<>();
		int lowestRunSoundIndex = -1;
		for (final Int2ObjectMap.Entry<SoundEvent> entry : config.config.runByIndex.int2ObjectEntrySet()) {
			soundLoopRun.put(entry.getIntKey(), new VehicleLoopingSoundInstance(entry.getValue()));
			lowestRunSoundIndex = lowestRunSoundIndex == -1 ? entry.getIntKey() : Math.min(lowestRunSoundIndex, entry.getIntKey());
		}
		defaultRunSoundIndex = lowestRunSoundIndex;
		vehicleLoopingSoundHolder = new VehicleLoopingSoundHolder(
				soundLoopMotor,
				soundLoopRun,
				soundLoopFlange,
				config.config.noise == null ? null : new VehicleLoopingSoundInstance(config.config.noise),
				config.config.shoe == null ? null : new VehicleLoopingSoundInstance(config.config.shoe),
				config.config.compressorLoop == null ? null : new VehicleLoopingSoundInstance(config.config.compressorLoop)
		);
	}

	/**
	 * MTR 4.0.5 shape, kept for add-ons: no rail information, so the train's default run sound plays.
	 */
	@Override
	public void playMotorSound(BlockPos blockPos, float speed, float speedChange, float acceleration, boolean isOnRoute) {
		final VehicleSoundBase.RunSoundInfo runSound = pendingRunSound;
		pendingRunSound = null;
		sound(new VehicleSoundParameters(runSound, blockPos, speed, speedChange, acceleration, isOnRoute));
	}

	@Override
	public void playVehicleSound(VehicleSoundParameters parameters) {
		// Every train goes through playMotorSound, on a path or not, so an add-on that subclassed this class in MTR 4.0.5
		// and overrode playMotorSound still runs. The rail's run sound rides along in a field for this class's own
		// playMotorSound to pick up; playMotorSound must never call back here.
		pendingRunSound = parameters.runSound();
		try {
			playMotorSound(parameters.blockPos(), parameters.speed(), parameters.speedChange(), parameters.acceleration(), parameters.isOnRoute());
		} finally {
			pendingRunSound = null;
		}
	}

	private void sound(VehicleSoundParameters parameters) {
		final BlockPos blockPos = parameters.blockPos();
		final float speed = parameters.speed();
		final float speedChange = parameters.speedChange();
		final boolean isOnRoute = parameters.isOnRoute();
		final float secondsElapsed = MinecraftClient.getInstance().getLastFrameDuration() / 20;
		final float speedKilometersPerHour = speed * 3600;
		final float speedMetersPerSecond = speed * 1000;
		final ProfileSounds profile = profileSounds;
		final float motorVolumeMultiply = profile == null ? config.config.motorVolumeMultiply : config.config.motorVolumeMultiply * profile.motorVolume;
		final float motorPitchMultiply = profile == null ? 1 : profile.pitchAt(speedKilometersPerHour);

		// Rolling noise, blended between the two rails a car can be on at a joint
		final VehicleSoundBase.RunSoundInfo runSound = parameters.runSound();
		final int runIndexOld = resolveRunSoundIndex(runSound == null ? defaultRunSoundIndex : runSound.index());
		final int runIndexNew = resolveRunSoundIndex(runSound == null ? defaultRunSoundIndex : runSound.nextIndex());
		final float runSoundBlendRatio = runSound == null ? 1 : runSound.blendLevel();
		final float runVolume = Math.min(1, speedMetersPerSecond * 0.04F);
		final float runPitch = speedMetersPerSecond * 0.04F;
		// Only the two run sounds in the blend are updated each frame; one that just left the blend is silenced once
		if (playingRunIndexOld != runIndexOld && playingRunIndexOld != runIndexNew) {
			setRunSoundData(playingRunIndexOld, 0, 1, blockPos);
		}
		if (playingRunIndexNew != playingRunIndexOld && playingRunIndexNew != runIndexOld && playingRunIndexNew != runIndexNew) {
			setRunSoundData(playingRunIndexNew, 0, 1, blockPos);
		}
		if (runIndexOld == runIndexNew) {
			setRunSoundData(runIndexOld, runVolume, runPitch, blockPos);
		} else {
			setRunSoundData(runIndexOld, runVolume * (1 - runSoundBlendRatio), runPitch, blockPos);
			setRunSoundData(runIndexNew, runVolume * runSoundBlendRatio, runPitch, blockPos);
		}
		playingRunIndexOld = runIndexOld;
		playingRunIndexNew = runIndexNew;

		// Simulation of circuit breaker in traction controller
		float motorTarget = Math.signum(speedChange);
		if (motorTarget == 0 && speedMetersPerSecond != 0) {
			motorTarget = config.config.motorOutputAtCoast;
		}
		if (motorTarget < 0 && speedMetersPerSecond < config.config.regenerationLimit) {
			motorCurrentOutput = 0; // Regeneration brake cut off below limit speed
			motorBreakerTimer = -1;
		} else if (motorTarget > 0 && speedMetersPerSecond < 1) {
			motorCurrentOutput = 1; // Disable delay at startup
			motorBreakerTimer = -1;
		} else if (motorTarget != motorCurrentOutput && motorBreakerTimer < 0) {
			motorBreakerTimer = 0;
			if (motorTarget != 0 && motorCurrentOutput != 0) {
				motorCurrentOutput = 0; // Loose behavior but sounds OK
			}
		}
		if (motorBreakerTimer >= 0) {
			motorBreakerTimer += secondsElapsed;
			if (motorBreakerTimer > config.config.breakerDelay) {
				motorBreakerTimer = -1;
				motorCurrentOutput = motorTarget;
			}
		}

		// Clamp to a minimum volume whenever the inverter/motor is active
		if (motorCurrentOutput != 0) {
			motorCurrentOutput = Math.signum(motorCurrentOutput) * (0.3f + Math.abs(motorCurrentOutput) * (1 - 0.3f));
		}

		// Simulation of main reservoir air compressor
		if (mrPress <= config.config.mrPressMin) {
			isCompressorActive = true;
			mrPress = config.config.mrPressMin;
		} else if (mrPress >= config.config.mrPressMax) {
			isCompressorActive = false;
			mrPress = config.config.mrPressMax;
		}
		if (isCompressorActive) {
			mrPress += (int) (secondsElapsed * config.config.mrCompressorSpeed);
		}
		if (vehicleLoopingSoundHolder.soundLoopCompressor != null) {
			// NOTE: Attack sound playback is not to BVE specification.
			vehicleLoopingSoundHolder.soundLoopCompressor.setData(isCompressorActive ? (profile == null ? 1 : profile.compressorVolume) : 0, 1, blockPos);
		}
		if (isCompressorActive && !isCompressorActiveLastElapsed) {
			playCompressorAttack(blockPos, profile);
		} else if (!isCompressorActive && isCompressorActiveLastElapsed) {
			playCompressorRelease(blockPos, profile);
		}

		// Motor noise
		if (profile != null && profileMotor.length > 0) {
			for (int i = 0; i < config.motorData.getSoundCount(); i++) {
				if (vehicleLoopingSoundHolder.soundLoopMotor[i] != null) {
					vehicleLoopingSoundHolder.soundLoopMotor[i].setData(0, 1, blockPos);
				}
			}
			final float gain = profile.motorGainAt(speedKilometersPerHour) * motorVolumeMultiply * (profile.motorAlwaysOn ? 1 : Math.max(0.25F, Math.abs(motorCurrentOutput)));
			for (final VehicleLoopingSoundInstance motor : profileMotor) {
				motor.setData(gain, motorPitchMultiply, blockPos);
			}
		} else {
			for (int i = 0; i < config.motorData.getSoundCount(); i++) {
				if (vehicleLoopingSoundHolder.soundLoopMotor[i] != null) {
					vehicleLoopingSoundHolder.soundLoopMotor[i].setData(config.motorData.getVolume(i, speedKilometersPerHour, motorCurrentOutput) * motorVolumeMultiply, config.motorData.getPitch(i, speedKilometersPerHour, motorCurrentOutput) * motorPitchMultiply, blockPos);
				}
			}
		}

		if (profile != null) {
			profileLayers.play(blockPos, speedKilometersPerHour, config.config.motorVolumeMultiply, Math.max(0.25F, Math.abs(motorCurrentOutput)));
		}

		// Flange squeal from the bogie's change of heading and its speed. Only the chosen loaded clip is audible.
		playFlange(blockPos, speedKilometersPerHour);

		// Brake shoe rubbing noise (below regeneration brake cutoff limit)
		if (vehicleLoopingSoundHolder.soundLoopShoe != null) {
			final float shoePitch = 1 / (speedMetersPerSecond + 1) + 1;
			float shoeGain = speedMetersPerSecond < config.config.regenerationLimit && speedChange < 0 ? 1 : 0;
			if (speedMetersPerSecond < 1.39) {
				final float t = speedMetersPerSecond * speedMetersPerSecond;
				shoeGain *= 1.5552F * t - 0.746496F * speedMetersPerSecond * t;
			} else if (speedMetersPerSecond > 12.5) {
				final float t = speedMetersPerSecond - 12.5F;
				shoeGain *= 1 / (0.1F * t * t + 1);
			}
			vehicleLoopingSoundHolder.soundLoopShoe.setData(shoeGain, shoePitch, blockPos);
		}

		// Constant loop noise
		if (vehicleLoopingSoundHolder.soundLoopNoise != null) {
			vehicleLoopingSoundHolder.soundLoopNoise.setData(isOnRoute ? 1 : 0, 1, blockPos);
		}

		// Air brake application and release noise
		if (oldSpeedChange < 0 && speedChange >= 0) {
			playBrakeRelease(blockPos, profile);
			if (speedMetersPerSecond < config.config.regenerationLimit) {
				playSoundInWorld(config.config.airZero, blockPos);
			}
		} else if (oldSpeedChange <= 0 && speedChange > 0 && speedMetersPerSecond < 0.3) {
			playSoundInWorld(config.config.airHigh, blockPos);
		} else if (oldSpeedChange >= 0 && speedChange < 0) {
			mrPress -= (int) config.config.mrServiceBrakeReduce;
			playSoundInWorld(config.config.brakeHandleApply, blockPos);
		}

		// Emergency brake application after returning to depot
		if (oldOnRoute && !isOnRoute) {
			playSoundInWorld(config.config.brakeEmergency, blockPos);
		}

		oldSpeedChange = speedChange;
		oldOnRoute = isOnRoute;
		isCompressorActiveLastElapsed = isCompressorActive;
	}

	@Override
	protected void playDoorSound(BlockPos blockPos, boolean isOpen) {
		final ProfileSounds profile = profileSounds;
		if (profile != null && (isOpen ? profile.doorOpen : profile.doorClose) != null) {
			ScheduledSound.schedule(blockPos, isOpen ? profile.doorOpen : profile.doorClose, (float) (2 * profile.doorVolume), 1);
		} else {
			ScheduledSound.schedule(blockPos, isOpen ? config.config.doorOpen : config.config.doorClose, 2, 1);
		}
	}

	@Override
	public void dispose() {
		for (final VehicleLoopingSoundInstance motor : profileMotor) {
			motor.dispose();
		}
		profileLayers.dispose();
		vehicleLoopingSoundHolder.dispose();
	}

	@Override
	protected double getDoorCloseSoundTime() {
		return config.config.doorCloseSoundLength;
	}

	@Override
	public void setSoundProfile(@Nullable ProfileSounds profile) {
		final String loopKey = profile == null ? "" : profile.loopKey;
		if (profileSounds == profile && appliedLoopKey.equals(loopKey)) {
			return;
		}
		profileSounds = profile;
		if (!appliedLoopKey.equals(loopKey)) {
			appliedLoopKey = loopKey;
			rebuildProfileLoops();
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

	/** Swaps the flange, compressor and motor loops for the profile's, or back to the train's own. */
	private void rebuildProfileLoops() {
		final ProfileSounds profile = profileSounds;
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
		profileLayers.rebuild(profile);
		if (profile != null && profile.flange.length > 0) {
			final VehicleLoopingSoundInstance[] flange = new VehicleLoopingSoundInstance[profile.flange.length];
			for (int i = 0; i < flange.length; i++) {
				flange[i] = new VehicleLoopingSoundInstance(profile.flange[i]);
			}
			vehicleLoopingSoundHolder.replaceFlange(flange);
		} else {
			final VehicleLoopingSoundInstance[] flange = new VehicleLoopingSoundInstance[config.config.flange.length];
			for (int i = 0; i < flange.length; i++) {
				if (config.config.flange[i] != null) {
					flange[i] = new VehicleLoopingSoundInstance(config.config.flange[i]);
				}
			}
			vehicleLoopingSoundHolder.replaceFlange(flange);
		}
		final SoundEvent compressor = profile != null && profile.compressorLoop != null ? profile.compressorLoop : config.config.compressorLoop;
		vehicleLoopingSoundHolder.replaceCompressor(compressor == null ? null : new VehicleLoopingSoundInstance(compressor));
	}

	private void playCompressorAttack(BlockPos blockPos, @Nullable ProfileSounds profile) {
		if (profile != null && profile.compressorAttack != null) {
			playSoundInWorld(profile.compressorAttack, blockPos, profile.compressorVolume);
		} else {
			playSoundInWorld(config.config.compressorAttack, blockPos);
		}
	}

	private void playCompressorRelease(BlockPos blockPos, @Nullable ProfileSounds profile) {
		if (profile != null && profile.compressorRelease != null) {
			playSoundInWorld(profile.compressorRelease, blockPos, profile.compressorVolume);
		} else {
			playSoundInWorld(config.config.compressorRelease, blockPos);
		}
	}

	private void playBrakeRelease(BlockPos blockPos, @Nullable ProfileSounds profile) {
		if (profile != null && profile.brakeRelease != null) {
			playSoundInWorld(profile.brakeRelease, blockPos, profile.brakeVolume);
		} else {
			playSoundInWorld(config.config.brakeHandleRelease, blockPos);
		}
	}

	private static void playSoundInWorld(@Nullable SoundEvent soundEvent, BlockPos blockPos, float gain) {
		ScheduledSound.schedule(blockPos, soundEvent, gain, 1);
	}

	private void playFlange(BlockPos blockPos, float speedKilometersPerHour) {
		final VehicleLoopingSoundInstance[] flanges = vehicleLoopingSoundHolder.soundLoopFlange;
		int loaded = 0;
		for (final VehicleLoopingSoundInstance flange : flanges) {
			if (flange != null) {
				loaded++;
			}
		}
		final double x = blockPos.getX();
		final double z = blockPos.getZ();
		double curvature = 0;
		if (hasBogieSample) {
			final double dx = x - lastBogieX;
			final double dz = z - lastBogieZ;
			final double distance = Math.hypot(dx, dz);
			if (distance >= FlangeSqueal.MIN_STEP) {
				final double heading = FlangeSqueal.heading(dx, dz);
				curvature = FlangeSqueal.curvature(lastHeading, heading, distance);
				lastHeading = heading;
				lastBogieX = x;
				lastBogieZ = z;
			}
		} else {
			hasBogieSample = true;
			lastBogieX = x;
			lastBogieZ = z;
		}
		final float gain = loaded == 0 ? 0 : FlangeSqueal.gain(curvature, speedKilometersPerHour) * (float) Config.getClient().getFlangeVolume() * (profileSounds == null ? 1 : profileSounds.flangeVolume);
		final float pitch = FlangeSqueal.pitch(speedKilometersPerHour);
		final int slot = FlangeSqueal.slot(speedKilometersPerHour, loaded);
		int seen = 0;
		for (final VehicleLoopingSoundInstance flange : flanges) {
			if (flange == null) {
				continue;
			}
			flange.setData(seen == slot ? gain : 0, pitch, blockPos);
			seen++;
		}
	}

	private static void playSoundInWorld(@Nullable SoundEvent soundEvent, BlockPos blockPos) {
		ScheduledSound.schedule(blockPos, soundEvent, 1, 1);
	}

	private static int randomInt(int minInclusive, int maxExclusive) {
		return new Random().nextInt(maxExclusive - minInclusive) + minInclusive;
	}

	private void setRunSoundData(int index, float volume, float pitch, BlockPos blockPos) {
		final VehicleLoopingSoundInstance instance = vehicleLoopingSoundHolder.soundLoopRun.get(index);
		if (instance != null) {
			instance.setData(volume, pitch, blockPos);
		}
	}

	/**
	 * A rail's sound index when the train defines that run sound, otherwise the train's own default.
	 */
	private int resolveRunSoundIndex(int index) {
		return vehicleLoopingSoundHolder.soundLoopRun.containsKey(index) ? index : defaultRunSoundIndex;
	}

	private static class VehicleLoopingSoundHolder {

		private final VehicleLoopingSoundInstance[] soundLoopMotor;
		private final Int2ObjectOpenHashMap<VehicleLoopingSoundInstance> soundLoopRun;
		private VehicleLoopingSoundInstance[] soundLoopFlange;
		@Nullable
		private final VehicleLoopingSoundInstance soundLoopNoise;
		@Nullable
		private final VehicleLoopingSoundInstance soundLoopShoe;
		@Nullable
		private VehicleLoopingSoundInstance soundLoopCompressor;

		/** The MTR 4.0.5 constructor. Add-ons hook it by descriptor, so it stays even though new code passes the flange array. */
		@SuppressWarnings("unused")
		private VehicleLoopingSoundHolder(
				VehicleLoopingSoundInstance[] soundLoopMotor,
				@Nullable VehicleLoopingSoundInstance soundLoopRun,
				@Nullable VehicleLoopingSoundInstance soundLoopFlange,
				@Nullable VehicleLoopingSoundInstance soundLoopNoise,
				@Nullable VehicleLoopingSoundInstance soundLoopShoe,
				@Nullable VehicleLoopingSoundInstance soundLoopCompressor
		) {
			this(soundLoopMotor, runSoundMap(soundLoopRun), soundLoopFlange == null ? new VehicleLoopingSoundInstance[0] : new VehicleLoopingSoundInstance[]{soundLoopFlange}, soundLoopNoise, soundLoopShoe, soundLoopCompressor);
		}

		private static Int2ObjectOpenHashMap<VehicleLoopingSoundInstance> runSoundMap(@Nullable VehicleLoopingSoundInstance soundLoopRun) {
			final Int2ObjectOpenHashMap<VehicleLoopingSoundInstance> map = new Int2ObjectOpenHashMap<>();
			if (soundLoopRun != null) {
				map.put(0, soundLoopRun);
			}
			return map;
		}

		private VehicleLoopingSoundHolder(
				VehicleLoopingSoundInstance[] soundLoopMotor,
				Int2ObjectOpenHashMap<VehicleLoopingSoundInstance> soundLoopRun,
				VehicleLoopingSoundInstance[] soundLoopFlange,
				@Nullable VehicleLoopingSoundInstance soundLoopNoise,
				@Nullable VehicleLoopingSoundInstance soundLoopShoe,
				@Nullable VehicleLoopingSoundInstance soundLoopCompressor
		) {
			this.soundLoopMotor = soundLoopMotor;
			this.soundLoopRun = soundLoopRun;
			this.soundLoopFlange = soundLoopFlange;
			this.soundLoopNoise = soundLoopNoise;
			this.soundLoopShoe = soundLoopShoe;
			this.soundLoopCompressor = soundLoopCompressor;
		}

		public void dispose() {
			for (VehicleLoopingSoundInstance instance : soundLoopMotor) {
				if (instance != null) {
					instance.dispose();
				}
			}
			for (final VehicleLoopingSoundInstance instance : soundLoopRun.values()) {
				instance.dispose();
			}
			for (final VehicleLoopingSoundInstance flange : soundLoopFlange) {
				if (flange != null) {
					flange.dispose();
				}
			}
			if (soundLoopNoise != null) {
				soundLoopNoise.dispose();
			}
			if (soundLoopShoe != null) {
				soundLoopShoe.dispose();
			}
			if (soundLoopCompressor != null) {
				soundLoopCompressor.dispose();
			}
		}

		private void replaceFlange(VehicleLoopingSoundInstance[] next) {
			for (final VehicleLoopingSoundInstance flange : soundLoopFlange) {
				if (flange != null) {
					flange.dispose();
				}
			}
			soundLoopFlange = next;
		}

		private void replaceCompressor(@Nullable VehicleLoopingSoundInstance next) {
			if (soundLoopCompressor != null) {
				soundLoopCompressor.dispose();
			}
			soundLoopCompressor = next;
		}
	}
}
