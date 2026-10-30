package org.mtr.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RouteMapGenerator keeps its drawing scale in static fields, and its AtScale helpers change and restore them while
 * a texture is drawn. Only the worker thread may touch them. Resetting them from the render thread, as getResource did
 * after scheduling the job, can land in the middle of a draw and give a map the wrong size.
 */
public final class RouteMapScaleThreadTest {

	private static final String CACHE = "src/main/java/org/mtr/mod/client/DynamicTextureCache.java";

	@Test
	public void scaleIsResetOnTheWorkerThreadBeforeDrawing() throws Exception {
		final String source = Files.readString(Path.of(CACHE));
		final int method = source.indexOf("private DynamicResource getResource(");
		assertTrue(method > 0, "getResource exists");
		final int schedule = source.indexOf("scheduleDynamicTextures(", method);
		assertTrue(schedule > method);
		final int draw = source.indexOf("supplier.get()", schedule);
		assertTrue(draw > schedule, "the job draws with the supplier");
		final int reset = source.indexOf("RouteMapGenerator.setConstants()", method);
		assertTrue(reset > schedule && reset < draw, "setConstants must run inside the worker job, before it draws; found at " + reset + ", job spans " + schedule + " to " + draw);
		assertEquals(-1, source.indexOf("RouteMapGenerator.setConstants()", draw), "no setConstants after the job, on the render thread");
	}
}
