package org.mtr.aurelia.client.voice;

import com.mojang.text2speech.Narrator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

/**
 * Every spoken announcement goes through here: MTR's text-to-speech announcements and ATA's live announcements. It
 * uses ATR's own voice for the game's language when the player has it downloaded and switched on, and the computer's
 * own voice otherwise, so a missing or broken download never silences an announcement. Callers check the volume first
 * ({@code TtsVolume}).
 */
public final class AnnouncerVoice {

	private AnnouncerVoice() {
	}

	public static void say(String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		final VoiceCatalog.Voice voice = current();
		if (voice != null && NeuralVoice.ready(voice)) {
			NeuralVoice.say(voice, VoiceSettings.speed(), text, volume(), () -> MinecraftClient.getInstance().execute(() -> Narrator.getNarrator().say(text, true)));
		} else {
			Narrator.getNarrator().say(text, true);
		}
	}

	/** Whether our own voice will speak, so ATA can speak even where the computer has no voice. */
	public static boolean oursReady() {
		return NeuralVoice.ready(current());
	}

	/** The voice for the game's language when our own voice is switched on, else null. */
	public static VoiceCatalog.Voice current() {
		final String language = gameLanguage();
		return language == null || !VoiceSettings.ours() ? null : VoiceSettings.voice(language);
	}

	/** Our language for the game's language setting, or null when no voice of ours speaks it. */
	public static String gameLanguage() {
		final MinecraftClient client = MinecraftClient.getInstance();
		return client == null || client.options == null ? null : VoiceCatalog.language(client.options.language);
	}

	private static float volume() {
		final MinecraftClient client = MinecraftClient.getInstance();
		return client == null || client.options == null ? 1 : client.options.getSoundVolume(SoundCategory.MASTER) * client.options.getSoundVolume(SoundCategory.VOICE);
	}
}
