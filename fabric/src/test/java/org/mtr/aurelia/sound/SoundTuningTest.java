package org.mtr.aurelia.sound;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.mod.sound.SoundTuning;

/**
 * Train sound pitch: BVE packs ask for pitches past vanilla's 2, and old-style motor clips must not jump at each step.
 */
public final class SoundTuningTest {

	@Test
	public void widePitchKeepsWhatThePackAsksFor() {
		Assertions.assertEquals(3.2F, SoundTuning.pitch(3.2F, true, 4), "a BVE table asking for 3.2 gets 3.2");
		Assertions.assertEquals(2F, SoundTuning.pitch(3.2F, false, 4), "switched off: vanilla's limit");
		Assertions.assertEquals(4F, SoundTuning.pitch(9F, true, 4));
		Assertions.assertEquals(0.25F, SoundTuning.pitch(0.1F, true, 4));
		Assertions.assertEquals(1F, SoundTuning.pitch(Float.NaN, true, 4));
	}

	@Test
	public void motorPitchRisesThroughAStepAndMeetsTheNextClip() {
		Assertions.assertEquals(0.9F, SoundTuning.motorPitch(3.0, 10, 0.2));
		Assertions.assertEquals(1.1F, SoundTuning.motorPitch(3.999, 10, 0.2));
		Assertions.assertEquals(1.1F, SoundTuning.motorPitch(25, 10, 0.2), "past the last clip it holds the top");
		Assertions.assertEquals(1F, SoundTuning.motorPitch(3.5, 10, 0), "smoothing off: MTR's pitch 1");
		Assertions.assertTrue(SoundTuning.doorsStartClosing(0.98, 1));
		Assertions.assertFalse(SoundTuning.doorsStartClosing(0.5, 0.52));
	}
}
