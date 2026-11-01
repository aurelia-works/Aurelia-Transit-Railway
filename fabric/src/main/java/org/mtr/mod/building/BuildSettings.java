package org.mtr.mod.building;

/**
 * The Aurelia panel switches for the building tools. They live in the world's {@code atr_build} file, not in
 * {@code config/mtr.json}.
 */
public final class BuildSettings {

	public static final BuildSettings DEFAULT = new BuildSettings(true, 64, 0x3D7EFF, true, 15, 16, true, BuildMath.Mode.FULL, 512, 16);
	public static final int SIDE_MIN = 8;
	public static final int SIDE_MAX = 128;
	public static final int REACH_MIN = 4;
	public static final int REACH_MAX = 32;
	public static final int BLOCKS_MIN = 64;
	public static final int BLOCKS_MAX = 4096;
	public static final int TICK_MIN = 1;
	public static final int TICK_MAX = 64;
	public static final int[] COLOURS = {0x3D7EFF, 0xE23D3D, 0x3DCC6A, 0xE2B43D, 0xB43DE2, 0x3DD0E2, 0xE27A3D, 0xF2F2F2};

	private final boolean stationEnabled;
	private final int stationSide;
	private final int stationColour;
	private final boolean railEnabled;
	private final int snapDegrees;
	private final int railReach;
	private final boolean tunnelEnabled;
	private final BuildMath.Mode tunnelMode;
	private final int tunnelMaxBlocks;
	private final int blocksPerTick;

	public BuildSettings(boolean stationEnabled, int stationSide, int stationColour, boolean railEnabled, int snapDegrees, int railReach, boolean tunnelEnabled, BuildMath.Mode tunnelMode, int tunnelMaxBlocks, int blocksPerTick) {
		this.stationEnabled = stationEnabled;
		this.stationSide = stationSide;
		this.stationColour = stationColour;
		this.railEnabled = railEnabled;
		this.snapDegrees = snapDegrees;
		this.railReach = railReach;
		this.tunnelEnabled = tunnelEnabled;
		this.tunnelMode = tunnelMode == null ? BuildMath.Mode.FULL : tunnelMode;
		this.tunnelMaxBlocks = tunnelMaxBlocks;
		this.blocksPerTick = blocksPerTick;
	}

	public boolean stationEnabled() {
		return stationEnabled;
	}

	public int stationSide() {
		return stationSide;
	}

	public int stationColour() {
		return stationColour;
	}

	public boolean railEnabled() {
		return railEnabled;
	}

	public int snapDegrees() {
		return snapDegrees;
	}

	public int railReach() {
		return railReach;
	}

	public boolean tunnelEnabled() {
		return tunnelEnabled;
	}

	public BuildMath.Mode tunnelMode() {
		return tunnelMode;
	}

	public int tunnelMaxBlocks() {
		return tunnelMaxBlocks;
	}

	public int blocksPerTick() {
		return blocksPerTick;
	}

	public BuildSettings clamped() {
		return new BuildSettings(stationEnabled, clamp(stationSide, SIDE_MIN, SIDE_MAX), stationColour & 0xFFFFFF, railEnabled, snap(snapDegrees), clamp(railReach, REACH_MIN, REACH_MAX), tunnelEnabled, tunnelMode, clamp(tunnelMaxBlocks, BLOCKS_MIN, BLOCKS_MAX), clamp(blocksPerTick, TICK_MIN, TICK_MAX));
	}

	public int nextColour() {
		for (int i = 0; i < COLOURS.length; i++) {
			if (COLOURS[i] == (stationColour & 0xFFFFFF)) {
				return COLOURS[(i + 1) % COLOURS.length];
			}
		}
		return COLOURS[0];
	}

	private static int snap(int degrees) {
		if (degrees >= 90) {
			return 90;
		}
		if (degrees >= 45) {
			return 45;
		}
		if (degrees >= 15) {
			return 15;
		}
		return 0;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
