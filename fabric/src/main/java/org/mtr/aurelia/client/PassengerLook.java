package org.mtr.aurelia.client;

/**
 * Who one drawn passenger is: picked from where they are and their place in the queue, so the same person keeps the same
 * look from frame to frame. No Minecraft types, so it can be tested without a client.
 */
public record PassengerLook(long skinSeed, float height, float turn, float headTurn, float phase, Pose pose, int bagColor) {

	public enum Pose {RELAXED, PHONE, SHOULDER_BAG, BACKPACK, SUITCASE}

	private static final int[] BAG_COLORS = {0x2B2F3A, 0x6B3E26, 0x8C1C2E, 0x1F5C4D, 0x2C4F8C, 0xB08A3C, 0x5A5F6B};
	private static final Pose[] POSES = Pose.values();

	public static PassengerLook of(long place, int index) {
		long bits = mix(place * 0x9E3779B97F4A7C15L + index + 1);
		final long skinSeed = bits;
		bits = mix(bits);
		final float height = 0.88F + (bits & 0xFF) / 255F * 0.16F;
		final float turn = ((bits >>> 8 & 0xFF) / 255F - 0.5F) * 70;
		final float headTurn = ((bits >>> 16 & 0xFF) / 255F - 0.5F) * 1.2F;
		final float phase = (bits >>> 24 & 0xFF) / 255F * 6.2831855F;
		final Pose pose = POSES[(int) ((bits >>> 32 & 0xFFFF) % POSES.length)];
		final int bagColor = BAG_COLORS[(int) ((bits >>> 48 & 0xFFFF) % BAG_COLORS.length)];
		return new PassengerLook(skinSeed, height, turn, headTurn, phase, pose, bagColor);
	}

	/** The degrees of Minecraft yaw that face along {@code dx, dz}. */
	public static float yawToward(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
	}

	private static long mix(long value) {
		long z = value + 0x9E3779B97F4A7C15L;
		z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
		z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
		return z ^ z >>> 31;
	}
}
