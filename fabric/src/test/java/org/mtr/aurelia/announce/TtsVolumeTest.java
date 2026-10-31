package org.mtr.aurelia.announce;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.mod.client.TtsVolume;

/**
 * MTR #1006: text-to-speech announcements ignored the game's volume.
 */
public final class TtsVolumeTest {

	@Test
	public void silentWhenMasterOrVoiceIsOff() {
		Assertions.assertTrue(TtsVolume.muted(0, 1));
		Assertions.assertTrue(TtsVolume.muted(1, 0));
		Assertions.assertFalse(TtsVolume.muted(0.4F, 0.6F));
	}
}
