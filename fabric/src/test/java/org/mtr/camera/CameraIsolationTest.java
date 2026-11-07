package org.mtr.camera;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Guards the TR-1 isolation rule: the camera recorder lives in the dev-only {@code camera}
 * source set, never in the release jar. The release manifest (this test's classpath carries
 * the main resources) must not mention it, while the dev maths stays loadable for tests.
 * A Gradle task ({@code verifyNoCameraInReleaseJar}) scans the built jar itself.
 */
public final class CameraIsolationTest {

	@Test
	public void releaseManifestHasNoCameraRecorder() throws Exception {
		final String json;
		try (final InputStream inputStream = CameraIsolationTest.class.getResourceAsStream("/fabric.mod.json")) {
			Assertions.assertNotNull(inputStream, "main fabric.mod.json is on the test classpath");
			json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}
		Assertions.assertFalse(json.contains("atr_camera"), "release mod id must stay mtr only");
		Assertions.assertFalse(json.contains("org.mtr.camera"), "release entrypoints must not mention the recorder");
		Assertions.assertFalse(json.contains("org/mtr/camera"), "release manifest must not mention the recorder");
	}

	@Test
	public void devCameraMathsStaysLoadableForTests() throws Exception {
		Assertions.assertNotNull(Class.forName("org.mtr.camera.CameraPath"));
		Assertions.assertNotNull(Class.forName("org.mtr.camera.CameraPathJson"));
		Assertions.assertNotNull(Class.forName("org.mtr.camera.CameraKeyframe"));
	}
}
