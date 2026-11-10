package org.mtr.mod.sound;

import org.mtr.aurelia.sound.ProfileSounds;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.SoundEvent;

import javax.annotation.Nullable;

/**
 * The looping sounds of a profile's extra motor layers, shared by the BVE and legacy train sounds. Each layer follows
 * its own pitch and volume curves; the caller says how hard the motor is working.
 */
public final class ProfileLayerLoops {

	private ProfileSounds.Layer[] layers = new ProfileSounds.Layer[0];
	private VehicleLoopingSoundInstance[][] loops = new VehicleLoopingSoundInstance[0][];

	/** Swaps the loops for the profile's layers, or drops them when the profile has none. */
	public void rebuild(@Nullable ProfileSounds profile) {
		dispose();
		if (profile == null || profile.motorLayers.length == 0) {
			return;
		}
		layers = profile.motorLayers;
		loops = new VehicleLoopingSoundInstance[layers.length][];
		for (int i = 0; i < layers.length; i++) {
			final SoundEvent[] sounds = layers[i].sounds;
			loops[i] = new VehicleLoopingSoundInstance[sounds.length];
			for (int j = 0; j < sounds.length; j++) {
				loops[i][j] = new VehicleLoopingSoundInstance(sounds[j]);
			}
		}
	}

	/**
	 * Sets every layer's volume and pitch for this frame.
	 *
	 * @param volumeMultiply the train's own motor volume
	 * @param drive          0-1, how hard the motor works; layers marked {@code always} ignore it
	 */
	public void play(BlockPos blockPos, double speedKilometersPerHour, float volumeMultiply, float drive) {
		for (int i = 0; i < layers.length; i++) {
			final ProfileSounds.Layer layer = layers[i];
			final float gain = layer.gainAt(speedKilometersPerHour) * layer.volume * volumeMultiply * (layer.always ? 1 : drive);
			final float pitch = layer.pitchAt(speedKilometersPerHour);
			for (final VehicleLoopingSoundInstance loop : loops[i]) {
				loop.setData(gain, pitch, blockPos);
			}
		}
	}

	public void silence(BlockPos blockPos) {
		for (final VehicleLoopingSoundInstance[] layerLoops : loops) {
			for (final VehicleLoopingSoundInstance loop : layerLoops) {
				loop.setData(0, 1, blockPos);
			}
		}
	}

	public void dispose() {
		for (final VehicleLoopingSoundInstance[] layerLoops : loops) {
			for (final VehicleLoopingSoundInstance loop : layerLoops) {
				loop.dispose();
			}
		}
		layers = new ProfileSounds.Layer[0];
		loops = new VehicleLoopingSoundInstance[0][];
	}
}
