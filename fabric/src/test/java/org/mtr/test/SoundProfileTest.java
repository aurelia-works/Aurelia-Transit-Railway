package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.sound.SoundProfile;
import org.mtr.aurelia.sound.SoundProfileJson;
import org.mtr.aurelia.sound.SoundProfileRegistry;
import org.mtr.aurelia.sound.SoundProfileStore;

import java.util.Map;

public final class SoundProfileTest {

	private static final String SAMPLE = """
			{
				"name": "Test EMU",
				"motor": {
					"sounds": ["aurelia:test/motor"],
					"volume": 0.8,
					"pitch_curve": [
						{"speed": 80, "pitch": 1.4},
						{"speed": 0, "pitch": 0.7}
					],
					"volume_curve": [
						{"speed": 0, "volume": 0.2},
						{"speed": 80, "volume": 1.0}
					]
				},
				"door_chime": "aurelia:test/chime",
				"door_open": "aurelia:test/door_open",
				"door_close": "aurelia:test/door_close",
				"brake_release": "aurelia:test/brake",
				"compressor": "aurelia:test/compressor",
				"compressor_attack": "aurelia:test/compressor_attack",
				"compressor_release": "aurelia:test/compressor_release",
				"flange": ["aurelia:test/flange"]
			}
			""";

	@Test
	public void aProfileJsonLoadsItsSoundsAndCurves() {
		final SoundProfile profile = SoundProfileJson.parse("test:emu", SAMPLE);
		Assertions.assertNotNull(profile);
		Assertions.assertEquals("Test EMU", profile.displayName());
		Assertions.assertEquals(1, profile.motorSounds.size());
		Assertions.assertEquals("aurelia:test/chime", profile.doorChime);
		Assertions.assertEquals("aurelia:test/compressor", profile.compressorLoop);
		Assertions.assertEquals(1, profile.flange.size());

		// The curve is sorted by speed and rounded to the ends
		Assertions.assertEquals(0.7F, profile.pitchAt(0), 0.001F);
		Assertions.assertEquals(1.05F, profile.pitchAt(40), 0.001F);
		Assertions.assertEquals(1.4F, profile.pitchAt(200), 0.001F);
		Assertions.assertEquals(0.2F, profile.motorGainAt(0), 0.001F);
		Assertions.assertEquals(1.0F, profile.motorGainAt(120), 0.001F);
	}

	@Test
	public void aMissingProfileLeavesTheTrainAlone() {
		final SoundProfile profile = SoundProfileJson.parse("test:one", SAMPLE);
		Assertions.assertNotNull(profile);
		SoundProfileRegistry.replace(Map.of("test:one", profile));

		Assertions.assertNull(SoundProfileRegistry.find("test:gone"));
		Assertions.assertEquals("test:one", SoundProfileRegistry.nextId(""));
		Assertions.assertEquals("", SoundProfileRegistry.nextId("test:one"));
		Assertions.assertEquals("test:one", SoundProfileRegistry.nextId("test:gone"));

		final SoundProfileStore.Settings missing = SoundProfileStore.Settings.DEFAULT.withProfile("test:gone");
		Assertions.assertNull(SoundProfileStore.resolve(missing));
		Assertions.assertNull(SoundProfileStore.resolve(SoundProfileStore.Settings.DEFAULT));

		// A broken file is skipped rather than taking the loaded profiles down
		Assertions.assertNull(SoundProfileJson.parse("test:broken", "{not json"));
	}
}
