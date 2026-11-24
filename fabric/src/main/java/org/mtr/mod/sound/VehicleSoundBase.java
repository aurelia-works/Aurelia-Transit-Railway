package org.mtr.mod.sound;

import org.mtr.aurelia.sound.ProfileSounds;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.SoundEvent;
import org.mtr.mapping.mapper.SoundHelper;
import org.mtr.mod.Init;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;
import org.mtr.mod.resource.RailResource;

import javax.annotation.Nullable;

public abstract class VehicleSoundBase {

	public abstract void playMotorSound(BlockPos blockPos, float speed, float speedChange, float acceleration, boolean isOnRoute);

	/**
	 * MTR 4.1 entry point: the same motor sound, plus which rail run sound this car is over. The base class has nothing
	 * to do with the rail, so any sound class that has not been updated (add-ons included) keeps working unchanged.
	 */
	public void playVehicleSound(VehicleSoundParameters vehicleSoundParameters) {
		playMotorSound(vehicleSoundParameters.blockPos(), vehicleSoundParameters.speed(), vehicleSoundParameters.speedChange(), vehicleSoundParameters.acceleration(), vehicleSoundParameters.isOnRoute());
	}

	public final void playDoorSound(BlockPos blockPos, double doorValue, double oldDoorValue) {
		if (doorValue > 0 && oldDoorValue == 0) {
			playDoorSound(blockPos, true);
		}
		if (doorValue < getDoorCloseSoundTime() && oldDoorValue >= getDoorCloseSoundTime()) {
			playDoorSound(blockPos, false);
		}
		if (SoundTuning.doorsStartClosing(doorValue, oldDoorValue)) {
			playDoorChimeWithProfile(blockPos);
		}
	}

	public abstract void dispose();

	/**
	 * The chime chosen on the Aurelia panel, once as the doors leave fully open. Every car calls this; the sound
	 * scheduler merges them into one chime at the car nearest the player.
	 */
	private static void playDoorChime(BlockPos blockPos) {
		final Client config = Config.getClient();
		final int chime = config.getDoorChime();
		if (chime > 0 && config.getDoorChimeVolume() > 0) {
			ScheduledSound.schedule(blockPos, SoundHelper.createSoundEvent(new Identifier(Init.MOD_ID, chime == 1 ? "atr_door_chime_two" : "atr_door_chime_three")), (float) config.getDoorChimeVolume(), 1);
		}
	}

	protected abstract void playDoorSound(BlockPos blockPos, boolean isOpen);

	protected abstract double getDoorCloseSoundTime();

	/**
	 * Applies a sound profile chosen on the Aurelia panel, or clears it. Called for each car; the base class has
	 * nothing to change.
	 */
	public void setSoundProfile(@Nullable ProfileSounds profile) {
	}

	/** The profile's door chime, or null to use the chime selected on the panel. */
	@Nullable
	protected SoundEvent getProfileDoorChime() {
		return null;
	}

	protected float getProfileDoorChimeVolume() {
		return 1;
	}

	private void playDoorChimeWithProfile(BlockPos blockPos) {
		final SoundEvent profileChime = getProfileDoorChime();
		if (profileChime == null) {
			playDoorChime(blockPos);
		} else {
			final float volume = Config.getClient().getDoorChime() == 0 ? 0 : getProfileDoorChimeVolume();
			if (volume > 0) {
				ScheduledSound.schedule(blockPos, profileChime, volume, 1);
			}
		}
	}

	public record VehicleSoundParameters(@Nullable RunSoundInfo runSound, BlockPos blockPos, float speed, float speedChange, float acceleration, boolean isOnRoute) {
	}

	/**
	 * Which rail run sound indices this car is between, and how far it has moved from one to the other.
	 *
	 * @param index      the rail the tail of the car is on
	 * @param nextIndex  the rail the head of the car is on (the same as {@code index} when the car is on one rail)
	 * @param blendLevel 0 fully on the tail's rail, 1 fully on the head's rail
	 */
	public record RunSoundInfo(int index, int nextIndex, float blendLevel) {

		public static RunSoundInfo create(Rail railTail, Rail railHead, float blendLevel) {
			final int tailIndex = getSoundIndex(railTail);
			if (railHead.equals(railTail)) {
				return new RunSoundInfo(tailIndex, tailIndex, blendLevel);
			} else {
				return new RunSoundInfo(tailIndex, getSoundIndex(railHead), blendLevel);
			}
		}

		private static int getSoundIndex(Rail rail) {
			final String style = rail.getStyles().isEmpty() ? null : rail.getStyles().get(0);
			if (style == null) {
				return 0;
			} else {
				final RailResource railResource = CustomResourceLoader.getRailById(style);
				return railResource == null ? 0 : railResource.getSoundIndex();
			}
		}
	}
}
