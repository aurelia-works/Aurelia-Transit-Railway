package org.mtr.mod.sound;

import org.mtr.aurelia.sound.ProfileSounds;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.SoundEvent;
import org.mtr.mapping.mapper.SoundHelper;
import org.mtr.mod.Init;
import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;

import javax.annotation.Nullable;

public abstract class VehicleSoundBase {

	public abstract void playMotorSound(BlockPos blockPos, float speed, float speedChange, float acceleration, boolean isOnRoute);

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
}
