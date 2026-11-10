package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

/**
 * A station plan drawn from above, one pixel per block, the way an architect's plan reads: the walking level (tracks,
 * platforms with their edge line, stairs, lifts, gates, building walls); {@link #roofed} marks what a roof covers, which
 * {@link #at(int, int, boolean)} can show as a light wash.
 * The long side runs across the image. Built from a {@link StationPlan} on any thread; no Minecraft classes, so the in-game
 * editor uploads {@link #argb()} as a texture and a test can read it.
 *
 * @param turned true when the track direction (v) runs across the image: pixel x = v - vMin, y = u - uMin; otherwise
 *               x = u - uMin, y = v - vMin
 */
public record PlanTopView(int width, int height, int[] argb, boolean[] roofed, int uMin, int vMin, boolean turned, List<Label> labels) {

	/** A track number at a pixel, for the screen to write over the plan. */
	public record Label(int x, int y, String text) {
	}

	public static final int BACKGROUND = 0xFF1B2026;
	private static final int TRACK = 0xFF3A3530;
	private static final int RAIL = 0xFF8C8F94;
	private static final int ROOF_WASH = 0xFFDCE6F0;
	/** The largest image drawn: a plan bigger than this is cut, which no planner station reaches. */
	private static final int MAX_SIDE = 1024;

	/** The plan from above. An empty plan gives a 1 x 1 image. */
	public static PlanTopView of(StationPlan plan) {
		int uMin = Integer.MAX_VALUE, uMax = Integer.MIN_VALUE, vMin = Integer.MAX_VALUE, vMax = Integer.MIN_VALUE;
		for (final PlannedBlock block : plan.blocks()) {
			uMin = Math.min(uMin, block.u());
			uMax = Math.max(uMax, block.u());
			vMin = Math.min(vMin, block.v());
			vMax = Math.max(vMax, block.v());
		}
		if (uMin > uMax) {
			return new PlanTopView(1, 1, new int[]{BACKGROUND}, new boolean[1], 0, 0, false, List.of());
		}
		final int across = Math.min(MAX_SIDE, uMax - uMin + 1);
		final int along = Math.min(MAX_SIDE, vMax - vMin + 1);
		final boolean turned = along >= across;
		final int width = turned ? along : across;
		final int height = turned ? across : along;
		final int[] rank = new int[width * height];
		final int[] argb = new int[width * height];
		final boolean[] roofed = new boolean[width * height];
		java.util.Arrays.fill(argb, BACKGROUND);

		// tracks first, under everything: the bed colour along each track's centre line, its rails either side where they show
		final TrackLayout layout = plan.layout();
		if (layout != null) {
			for (final TrackLayout.Track track : layout.tracks()) {
				for (int v = vMin; v <= vMax; v++) {
					paint(argb, rank, width, height, turned, track.u() - uMin, v - vMin, TRACK, 1);
					paint(argb, rank, width, height, turned, track.u() - 1 - uMin, v - vMin, RAIL, 1);
					paint(argb, rank, width, height, turned, track.u() + 1 - uMin, v - vMin, RAIL, 1);
				}
			}
		}
		for (final PlannedBlock block : plan.blocks()) {
			final int a = block.u() - uMin;
			final int b = block.v() - vMin;
			if (a >= across || b >= along) {
				continue;
			}
			if (roof(block.role())) {
				roofed[index(width, turned, a, b)] = true;
				continue;
			}
			paint(argb, rank, width, height, turned, a, b, colour(block.role()), rank(block.role()));
		}
		final List<Label> labels = new ArrayList<>();
		if (layout != null) {
			for (final TrackLayout.Track track : layout.tracks()) {
				final int a = track.u() - uMin;
				if (a >= 0 && a < across) {
					labels.add(turned ? new Label(0, a, Integer.toString(track.number())) : new Label(a, 0, Integer.toString(track.number())));
				}
			}
		}
		return new PlanTopView(width, height, argb, roofed, uMin, vMin, turned, List.copyOf(labels));
	}

	/** The colour at pixel x, y (ARGB), with a light wash where a roof covers it when {@code roof}. */
	public int at(int x, int y, boolean roof) {
		final int i = y * width + x;
		return roof && roofed[i] ? mix(argb[i], ROOF_WASH, argb[i] == BACKGROUND ? 0.18 : 0.22) : argb[i];
	}

	private static boolean roof(Role role) {
		return switch (role) {
			case ROOF, ROOF_EDGE, ROOF_LIGHT, BUILDING_ROOF, BEAM, RIB, COLUMN_CAP -> true;
			default -> false;
		};
	}

	/** Which of the blocks in one column is drawn: the higher rank wins (gates over walls over platforms over track). */
	private static int rank(Role role) {
		return switch (role) {
			case GATE, BARRIER -> 9;
			case LIFT -> 8;
			case STAIR, ESCALATOR -> 7;
			case WALL, WINDOW, COLUMN, BRIDGE_PIER -> 6;
			case PLATFORM_EDGE -> 5;
			case PLATFORM_SURFACE, RAMP_LOWER, RAMP_UPPER -> 4;
			case FLOOR, LIGHT_PANEL, BRIDGE_DECK -> 3;
			case BUFFER_STOP, BENCH, BIN, LAMP, FIXTURE, NAME_SIGN, RAILING -> 2;
			default -> 1;
		};
	}

	private static int colour(Role role) {
		return switch (role) {
			case GATE, BARRIER -> 0xFFD9534F;
			case LIFT -> 0xFF3D85C6;
			case STAIR, ESCALATOR -> 0xFF6FA8DC;
			case WALL, COLUMN, BRIDGE_PIER -> 0xFF5E5149;
			case WINDOW -> 0xFF7FB3C8;
			case PLATFORM_EDGE -> 0xFFE8C547;
			case PLATFORM_SURFACE -> 0xFFC9C3B6;
			case RAMP_LOWER, RAMP_UPPER -> 0xFFB3AB9B;
			case FLOOR, LIGHT_PANEL -> 0xFFA39B90;
			case BRIDGE_DECK -> 0xFF8E9AA6;
			case BUFFER_STOP -> 0xFFC0392B;
			case BENCH, BIN, LAMP, FIXTURE, NAME_SIGN, RAILING -> 0xFF8A8F96;
			case TRACK_BED, RAIL_NODE -> TRACK;
			default -> 0xFF4C535B;
		};
	}

	private static int index(int width, boolean turned, int a, int b) {
		return turned ? a * width + b : b * width + a;
	}

	private static void paint(int[] argb, int[] rank, int width, int height, boolean turned, int a, int b, int colour, int r) {
		final int x = turned ? b : a;
		final int y = turned ? a : b;
		if (x < 0 || y < 0 || x >= width || y >= height) {
			return;
		}
		final int i = y * width + x;
		if (r >= rank[i]) {
			rank[i] = r;
			argb[i] = colour;
		}
	}

	private static int mix(int from, int to, double t) {
		final int r = (int) Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
		final int g = (int) Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
		final int b = (int) Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
