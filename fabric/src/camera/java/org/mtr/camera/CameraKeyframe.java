package org.mtr.camera;

/**
 * One trailer keyframe: the camera position, the direction it looks and the field of view.
 * Pure Java (no Minecraft types) so the path maths is covered by plain unit tests.
 */
public record CameraKeyframe(double x, double y, double z, double yaw, double pitch, double fov) {

	/** Used when a saved keyframe has no {@code fov} key. */
	public static final double DEFAULT_FOV = 70;
}
