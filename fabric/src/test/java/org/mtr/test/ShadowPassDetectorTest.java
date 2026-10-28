package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.mod.render.ShadowPassDetector;

import java.util.concurrent.atomic.AtomicInteger;

public final class ShadowPassDetectorTest {

	@Test
	public void recognisesShaderPackClasses() {
		Assertions.assertTrue(ShadowPassDetector.isShaderClass("net.irisshaders.iris.pipeline.ShadowRenderer"));
		Assertions.assertTrue(ShadowPassDetector.isShaderClass("net.optifine.shaders.ShadersRender"));
		Assertions.assertFalse(ShadowPassDetector.isShaderClass("net.minecraft.client.render.WorldRenderer"));
		Assertions.assertFalse(ShadowPassDetector.isShaderClass("org.mtr.mod.render.MainRenderer"));
	}

	@Test
	public void withoutShaderModAlwaysFalseAndNeverWalks() {
		final AtomicInteger walks = new AtomicInteger();
		final AtomicInteger slow = new AtomicInteger();
		final ShadowPassDetector detector = new ShadowPassDetector(false, () -> {
			walks.incrementAndGet();
			return true;
		});
		detector.begin();
		for (int i = 0; i < 300; i++) {
			Assertions.assertFalse(detector.renderingShadows(() -> {
				slow.incrementAndGet();
				return true;
			}));
		}
		detector.end();
		Assertions.assertEquals(0, walks.get());
		Assertions.assertEquals(0, slow.get());
	}

	@Test
	public void walksOncePerPassAndAnswersFromIt() {
		final AtomicInteger walks = new AtomicInteger();
		final ShadowPassDetector detector = new ShadowPassDetector(true, () -> {
			walks.incrementAndGet();
			return true;
		});
		detector.begin();
		Assertions.assertEquals(1, walks.get());
		for (int i = 0; i < 100; i++) {
			Assertions.assertTrue(detector.renderingShadows(() -> false));
		}
		Assertions.assertEquals(1, walks.get());
		detector.end();
	}

	@Test
	public void outsideAPassUsesTheSlowCheck() {
		final ShadowPassDetector detector = new ShadowPassDetector(true, () -> true);
		Assertions.assertFalse(detector.renderingShadows(() -> false));
		Assertions.assertTrue(detector.renderingShadows(() -> true));
	}

	@Test
	public void periodicCheckDisagreementFallsBackToTheSlowCheckForGood() {
		final boolean[] walkAnswer = {false};
		final ShadowPassDetector detector = new ShadowPassDetector(true, () -> walkAnswer[0]);
		detector.begin();
		Assertions.assertFalse(detector.renderingShadows(() -> true));
		walkAnswer[0] = true;
		// The 128th call walks again, sees the difference and returns the walked answer.
		boolean sawDifference = false;
		for (int i = 0; i < 200 && !sawDifference; i++) {
			sawDifference = detector.renderingShadows(() -> false);
		}
		Assertions.assertTrue(sawDifference);
		Assertions.assertTrue(detector.mismatched());
		// From now on only the slow answer counts.
		Assertions.assertFalse(detector.renderingShadows(() -> false));
		Assertions.assertTrue(detector.renderingShadows(() -> true));
	}
}
