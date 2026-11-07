package org.mtr.aurelia.driving;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * The three cab sounds, synthesized for ATR (no sampled railway audio). AWS uses a two-tone horn for a restrictive
 * signal and a soft bell for a clear one, as the real system does; TPWS uses a hard low buzzer.
 */
public final class DriverSounds {

	private static final SoundEvent HORN = SoundEvent.of(new Identifier("aurelia", "aws_horn"));
	private static final SoundEvent BELL = SoundEvent.of(new Identifier("aurelia", "aws_bell"));
	private static final SoundEvent TRIP = SoundEvent.of(new Identifier("aurelia", "tpws_trip"));

	private DriverSounds() {
	}

	public static void horn() {
		play(HORN, 1.0F, 1.0F);
	}

	public static void bell() {
		play(BELL, 0.65F, 1.0F);
	}

	public static void trip() {
		play(TRIP, 0.8F, 1.0F);
	}

	private static void play(SoundEvent soundEvent, float volume, float pitch) {
		final ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.playSound(soundEvent, volume, pitch);
		}
	}
}
