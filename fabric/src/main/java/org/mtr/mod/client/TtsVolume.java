package org.mtr.mod.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

/**
 * Text-to-speech announcements spoke even with the game's sound turned off (MTR #1006). Minecraft's narrator library
 * (text2speech 1.17.9) takes no volume, so the speech cannot be made quieter; it stays silent when Master or Voice is off.
 */
public final class TtsVolume {

	private TtsVolume() {
	}

	public static boolean muted(float masterVolume, float voiceVolume) {
		return masterVolume <= 0 || voiceVolume <= 0;
	}

	public static boolean muted() {
		final MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.options != null && muted(client.options.getSoundVolume(SoundCategory.MASTER), client.options.getSoundVolume(SoundCategory.VOICE));
	}
}
