package com.aureliatransit.architecture.bridge.span;

import com.aureliatransit.architecture.bridge.BridgeStampPlan;
import com.aureliatransit.architecture.station.CellKind;
import com.aureliatransit.architecture.station.SiteProbe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a laid rail needs under, round and over it: reads the ground along the rail, splits the rail into stretches (on the
 * ground, in a cutting, over a gap, through a hill) and plans each one. Gaps get a bridge in the chosen style, or one picked
 * from the gap; hills get a lined tunnel with a portal at each end; cuttings are dug clear; short dips are filled. Masts and
 * wire run along the whole rail when asked.
 *
 * <p>Every block is placed from the rail's own curve: each column is measured across and along the rail from its centre, so a
 * curved rail gets a curved bridge. Stays, cables, hangers, truss diagonals and the overhead line are {@link BridgeMember}s on
 * {@link Anchor} blocks, drawn straight by the renderer instead of stepped in blocks. Pure logic, no Minecraft classes.
 */
public final class RailBridgePlanner {

	/** Free height over the rail level kept for trains and the contact wire. */
	public static final int CLEARANCE = 5;
	/** Deepest a pier or fill reaches below the rail. */
	static final int SCAN_DOWN = 96;
	/** A dip this shallow and short is filled instead of bridged. */
	static final int FILL_DEPTH = 3;
	static final int FILL_LENGTH = 6;
	/** A hill shorter than this along the rail is cut through instead of tunnelled. */
	static final int MIN_TUNNEL = 8;
	/** Blocks at each end of a bridge that sit on the bank as an abutment. */
	static final int ABUTMENT = 2;
	/** How far wing walls run out from a tunnel mouth. */
	static final int WING = 5;
	/** Height of a truss over the rail, to the underside of its top chord. */
	static final int TRUSS_HEIGHT = 7;

	public static final String AIR = "minecraft:air";
	static final String CONCRETE = "minecraft:light_gray_concrete";
	static final String WHITE = "minecraft:white_concrete";
	static final String STEEL = "minecraft:gray_concrete";
	static final String DECK = "minecraft:smooth_stone";
	static final String STONE = "minecraft:stone_bricks";
	static final String STONE_RING = "minecraft:polished_andesite";
	static final String BALLAST = "minecraft:gravel";
	static final String TOWER = "minecraft:red_terracotta";
	static final String FOOTING = "minecraft:stone";
	static final String EARTH = "minecraft:dirt";
	static final String TURF = "minecraft:grass_block";

	private static final int NONE = Integer.MIN_VALUE;

	public enum Kind {
		GROUND,
		/** A short shallow dip, filled with earth under the rail. */
		FILL,
		/** The ground reaches into the train's space: dug out, sides sloped back. */
		CUTTING,
		BRIDGE,
		/** The ground covers the train's space: a lined tunnel, portals at its ends. */
		TUNNEL
	}

	/** One stretch of the rail, {@code s0} to {@code s1} blocks along it. */
	public record Stretch(Kind kind, double s0, double s1, SpanStyle style, int deepest, boolean water) {

		public double length() {
			return s1 - s0;
		}
	}

	public record Cell(int x, int y, int z, String block) {
	}

	/** A block that draws {@code members}; {@code skin} is the block it stands in for (null: nothing but the members). */
	public record Anchor(int x, int y, int z, String skin, List<BridgeMember> members) {
	}

	/**
	 * @param unloaded columns that could not be read (unloaded chunks); nothing is planned there
	 */
	public record Result(List<Cell> cells, List<Anchor> anchors, List<Stretch> stretches, int unloaded) {
	}

	/** A column's place relative to the rail: {@code s} along it, {@code a} across (left positive), and the rail's level. */
	private record Local(double s, double a, int rail, double error) {
	}

	private final BridgeStampPlan.Path path;
	private final SiteProbe site;
	private final SpanSettings settings;
	private final double length;
	private final int half;
	private final Map<Long, Integer> grounds = new HashMap<>();
	private final Map<Long, Cell> cells = new LinkedHashMap<>();
	private final Map<Long, AnchorBuilder> anchors = new LinkedHashMap<>();
	private final List<Stretch> stretches = new ArrayList<>();
	private int unloaded;

	private RailBridgePlanner(BridgeStampPlan.Path path, SiteProbe site, SpanSettings settings) {
		this.path = path;
		this.site = site;
		this.settings = settings;
		this.length = Math.max(0, path.length());
		this.half = settings.halfWidth();
	}

	public static Result plan(BridgeStampPlan.Path path, SiteProbe site, SpanSettings settings) {
		final RailBridgePlanner planner = new RailBridgePlanner(path, site, settings);
		planner.stretches.addAll(planner.analyse());
		planner.columns();
		for (final Stretch stretch : planner.stretches) {
			if (stretch.kind() == Kind.BRIDGE) {
				planner.members(stretch);
			}
		}
		if (settings.masts()) {
			planner.masts();
		}
		final List<Anchor> anchors = new ArrayList<>();
		for (final AnchorBuilder anchor : planner.anchors.values()) {
			planner.cells.remove(key(anchor.x, anchor.y, anchor.z));
			anchors.add(new Anchor(anchor.x, anchor.y, anchor.z, anchor.skin, List.copyOf(anchor.members)));
		}
		return new Result(List.copyOf(planner.cells.values()), List.copyOf(anchors), List.copyOf(planner.stretches), planner.unloaded);
	}

	/** The style a gap gets when the player left it to the planner. */
	public static SpanStyle autoStyle(double length, int deepest, boolean water) {
		if (water) {
			if (length >= 150) {
				return SpanStyle.SUSPENSION;
			}
			if (length >= 80) {
				return SpanStyle.CABLE_STAYED;
			}
			return length >= 24 ? SpanStyle.TRUSS : SpanStyle.GIRDER;
		}
		return deepest >= 14 && length >= 36 ? SpanStyle.ARCH : SpanStyle.GIRDER;
	}

	// ---- stretches --------------------------------------------------------------------------------------------------

	private record Run(Kind kind, int i0, int i1, int deepest, boolean water) {
	}

	/** Samples the rail every block and groups the samples into stretches. */
	private List<Stretch> analyse() {
		final int n = Math.max(1, (int) Math.ceil(length));
		final Kind[] kinds = new Kind[n + 1];
		final int[] gaps = new int[n + 1];
		final boolean[] wet = new boolean[n + 1];
		for (int i = 0; i <= n; i++) {
			final BridgeStampPlan.Sample sample = path.at(Math.min(length, i));
			final int x = (int) Math.floor(sample.x());
			final int z = (int) Math.floor(sample.z());
			final int rail = rail(sample);
			final int ground = ground(x, z, rail);
			if (ground == NONE) {
				// deeper than a pier reaches: still a bridge, its piers stop short
				kinds[i] = Kind.BRIDGE;
				gaps[i] = SCAN_DOWN;
				continue;
			}
			if (ground >= rail + CLEARANCE) {
				kinds[i] = Kind.TUNNEL;
			} else if (ground >= rail) {
				kinds[i] = Kind.CUTTING;
			} else if (ground >= rail - 1) {
				kinds[i] = Kind.GROUND;
			} else {
				kinds[i] = Kind.BRIDGE;
				gaps[i] = rail - 1 - ground;
				for (int y = ground + 1; y < rail && !wet[i]; y++) {
					wet[i] = site.kind(x, y, z) == CellKind.FLUID;
				}
			}
		}
		List<Run> runs = runs(kinds, gaps, wet);
		final List<Run> fixed = new ArrayList<>();
		for (int r = 0; r < runs.size(); r++) {
			final Run run = runs.get(r);
			final int span = run.i1() - run.i0() + 1;
			Kind kind = run.kind();
			if (kind == Kind.BRIDGE && span < FILL_LENGTH && run.deepest() <= FILL_DEPTH && !run.water()) {
				kind = Kind.FILL;
			} else if (kind == Kind.TUNNEL && (span < MIN_TUNNEL || !settings.portals())) {
				kind = settings.portals() ? Kind.CUTTING : Kind.GROUND;
			} else if (kind == Kind.GROUND && span < 4 && r > 0 && r < runs.size() - 1
					&& runs.get(r - 1).kind() == Kind.BRIDGE && runs.get(r + 1).kind() == Kind.BRIDGE) {
				// an island or a pier-high rock in the middle of a crossing: one bridge over it
				kind = Kind.BRIDGE;
			}
			fixed.add(new Run(kind, run.i0(), run.i1(), run.deepest(), run.water()));
		}
		runs = merge(fixed);
		final List<Stretch> out = new ArrayList<>();
		for (int r = 0; r < runs.size(); r++) {
			final Run run = runs.get(r);
			double s0 = run.i0() == 0 ? 0 : run.i0() - 0.5;
			double s1 = run.i1() == n ? length : Math.min(length, run.i1() + 0.5);
			if (run.kind() == Kind.BRIDGE) {
				// the abutments sit on the banks
				s0 = Math.max(0, s0 - ABUTMENT);
				s1 = Math.min(length, s1 + ABUTMENT);
			} else {
				if (r > 0 && runs.get(r - 1).kind() == Kind.BRIDGE) {
					s0 = Math.min(s1, s0 + ABUTMENT);
				}
				if (r < runs.size() - 1 && runs.get(r + 1).kind() == Kind.BRIDGE) {
					s1 = Math.max(s0, s1 - ABUTMENT);
				}
			}
			final SpanStyle style = run.kind() != Kind.BRIDGE ? SpanStyle.AUTO
					: settings.style() != SpanStyle.AUTO ? settings.style() : autoStyle(s1 - s0, run.deepest(), run.water());
			out.add(new Stretch(run.kind(), s0, s1, style, run.deepest(), run.water()));
		}
		return out;
	}

	private static List<Run> runs(Kind[] kinds, int[] gaps, boolean[] wet) {
		final List<Run> runs = new ArrayList<>();
		int start = 0;
		for (int i = 1; i <= kinds.length; i++) {
			if (i == kinds.length || kinds[i] != kinds[start]) {
				int deepest = 0;
				boolean water = false;
				for (int k = start; k < i; k++) {
					deepest = Math.max(deepest, gaps[k]);
					water |= wet[k];
				}
				runs.add(new Run(kinds[start], start, i - 1, deepest, water));
				start = i;
			}
		}
		return runs;
	}

	private static List<Run> merge(List<Run> runs) {
		final List<Run> out = new ArrayList<>();
		for (final Run run : runs) {
			final Run last = out.isEmpty() ? null : out.get(out.size() - 1);
			if (last != null && last.kind() == run.kind()) {
				out.set(out.size() - 1, new Run(run.kind(), last.i0(), run.i1(), Math.max(last.deepest(), run.deepest()), last.water() || run.water()));
			} else {
				out.add(run);
			}
		}
		return out;
	}

	private Stretch stretchAt(double s) {
		for (final Stretch stretch : stretches) {
			if (s <= stretch.s1()) {
				return stretch;
			}
		}
		return stretches.get(stretches.size() - 1);
	}

	// ---- columns ----------------------------------------------------------------------------------------------------

	/** Every column within reach of the rail, measured once from its own centre, then planned by its stretch. */
	private void columns() {
		final int reach = half + 8;
		final Map<Long, Local> locals = new LinkedHashMap<>();
		final int steps = Math.max(1, (int) Math.ceil(length * 4));
		for (int i = 0; i <= steps; i++) {
			final double s = length * i / steps;
			final BridgeStampPlan.Sample sample = path.at(s);
			final double[] t = tangent(sample);
			final int rail = rail(sample);
			for (int k = -4 * reach; k <= 4 * reach; k++) {
				final double a = k / 4.0;
				final int x = (int) Math.floor(sample.x() - t[1] * a);
				final int z = (int) Math.floor(sample.z() + t[0] * a);
				final double cx = x + 0.5 - sample.x();
				final double cz = z + 0.5 - sample.z();
				final double along = cx * t[0] + cz * t[1];
				final double across = -cx * t[1] + cz * t[0];
				final double at = s + along;
				if (at < -0.5 || at > length + 0.5 || Math.abs(across) > reach) {
					continue;
				}
				final long column = column(x, z);
				final Local old = locals.get(column);
				if (old == null || Math.abs(along) < old.error()) {
					locals.put(column, new Local(Math.max(0, Math.min(length, at)), across, rail, Math.abs(along)));
				}
			}
		}
		for (final Map.Entry<Long, Local> entry : locals.entrySet()) {
			final int x = (int) (entry.getKey() >> 32);
			final int z = (int) (long) entry.getKey();
			final Local local = entry.getValue();
			final Stretch stretch = stretchAt(local.s());
			final int ground = ground(x, z, local.rail());
			switch (stretch.kind()) {
				case GROUND -> wings(x, z, local, ground);
				case FILL -> fill(x, z, local, ground);
				case CUTTING -> {
					cutting(x, z, local, ground);
					wings(x, z, local, ground);
				}
				case TUNNEL -> tunnel(x, z, local, stretch);
				case BRIDGE -> bridge(x, z, local, stretch, ground);
			}
		}
	}

	private void fill(int x, int z, Local c, int ground) {
		final double a = Math.abs(c.a());
		if (ground == NONE) {
			return;
		}
		for (int y = ground + 1; y < c.rail(); y++) {
			final int below = c.rail() - 1 - y;
			if (a <= half + 0.5 + below) {
				place(x, y, z, y == c.rail() - 1 && a > 1.5 ? TURF : EARTH, true);
			}
		}
	}

	/** Dug clear over the track, the sides sloped back about one across for each one up. */
	private void cutting(int x, int z, Local c, int ground) {
		final double a = Math.abs(c.a());
		final int top = ground == NONE ? c.rail() + CLEARANCE : Math.min(ground, c.rail() + 16);
		final double out = mouth(c.s());
		for (int y = c.rail(); y <= top; y++) {
			final int h = y - c.rail();
			if (out > 0 ? a < wall(out) : a <= half + 0.5 + Math.max(0, h - 1) * 0.8) {
				clear(x, y, z);
			}
		}
		if (a <= half + 0.5) {
			place(x, c.rail() - 1, z, BALLAST, true);
		}
	}

	private void tunnel(int x, int z, Local c, Stretch stretch) {
		final double a = Math.abs(c.a());
		final double mouth = Math.min(c.s() - stretch.s0(), stretch.s1() - c.s());
		for (int h = -1; h <= CLEARANCE + 4; h++) {
			final int y = c.rail() + h;
			final boolean opening = a <= half + 0.5 && h >= 0 && h < CLEARANCE && !(h == CLEARANCE - 1 && a > half - 0.5);
			if (mouth < 2 && a <= half + 5.5 && h <= CLEARANCE + 4) {
				// the portal: a headwall round the opening, a dressed ring next to it and a coping on top
				if (opening) {
					clear(x, y, z);
				} else if (h == CLEARANCE + 4) {
					place(x, y, z, "minecraft:stone_brick_slab[type=bottom,waterlogged=false]", false);
				} else if (h >= 0 && a <= half + 1.5 && h <= CLEARANCE) {
					place(x, y, z, STONE_RING, false);
				} else {
					place(x, y, z, STONE, false);
				}
				continue;
			}
			if (opening) {
				clear(x, y, z);
			} else if (h == -1 && a <= half + 0.5) {
				place(x, y, z, BALLAST, false);
			} else if (a <= half + 1.5 && h <= CLEARANCE) {
				place(x, y, z, CONCRETE, false);
			}
		}
	}

	/**
	 * Wing walls: retaining walls along both sides of the cutting in front of a tunnel mouth, flaring a little and stepping down
	 * with the ground they hold back. Where the ground is lower than the rail there is nothing to hold and no wall.
	 */
	private void wings(int x, int z, Local c, int ground) {
		final double out = mouth(c.s());
		if (out <= 0 || ground == NONE || ground < c.rail()) {
			return;
		}
		final double a = Math.abs(c.a());
		final double inner = wall(out);
		if (a >= inner && a < inner + 1) {
			final int top = Math.min(ground + 1, c.rail() + CLEARANCE + 3 - (int) Math.round(out * 0.8));
			for (int y = c.rail() - 1; y <= top; y++) {
				place(x, y, z, STONE, false);
			}
		}
	}

	/** Blocks from {@code s} out to the nearest tunnel mouth when it is in the wing walls' reach, else -1. */
	private double mouth(double s) {
		if (!settings.portals()) {
			return -1;
		}
		double best = -1;
		for (final Stretch tunnel : stretches) {
			if (tunnel.kind() != Kind.TUNNEL) {
				continue;
			}
			final double out = s < tunnel.s0() ? tunnel.s0() - s : s > tunnel.s1() ? s - tunnel.s1() : -1;
			if (out > 0 && out <= WING && (best < 0 || out < best)) {
				best = out;
			}
		}
		return best;
	}

	/** Across distance of a wing wall's inner face {@code out} blocks from the mouth. */
	private double wall(double out) {
		return half + 1.5 + out * 0.5;
	}

	// ---- bridges ----------------------------------------------------------------------------------------------------

	private void bridge(int x, int z, Local c, Stretch stretch, int ground) {
		final double a = Math.abs(c.a());
		final double u = c.s() - stretch.s0();
		final double end = Math.min(u, stretch.s1() - c.s());
		final boolean inner = a <= half + 0.5;
		final boolean edge = !inner && a <= half + 1.5;
		final int rail = c.rail();
		if (end < ABUTMENT - 0.5 && stretch.style() != SpanStyle.SUSPENSION) {
			// an abutment: solid from the bank up to the deck
			if (a <= half + 1.5 && ground != NONE) {
				final String body = stretch.style() == SpanStyle.ARCH || stretch.style() == SpanStyle.TRUSS ? STONE : CONCRETE;
				for (int y = ground + 1; y < rail - 1; y++) {
					place(x, y, z, body, false);
				}
			}
		}
		switch (stretch.style()) {
			case TRUSS -> truss(x, z, c, stretch, ground, inner, edge);
			case ARCH -> arch(x, z, c, stretch, ground, inner, edge);
			case CABLE_STAYED -> cableStayed(x, z, c, stretch, ground, inner, edge);
			case SUSPENSION -> suspension(x, z, c, stretch, ground, inner, edge);
			default -> girder(x, z, c, stretch, ground, inner, edge);
		}
	}

	/** V1: a concrete box girder, tapering underneath, on square piers with a wider head and a footing. */
	private void girder(int x, int z, Local c, Stretch stretch, int ground, boolean inner, boolean edge) {
		final double a = Math.abs(c.a());
		final int rail = c.rail();
		if (inner) {
			place(x, rail - 1, z, DECK, false);
			place(x, rail - 2, z, CONCRETE, false);
			if (a <= half - 0.5) {
				place(x, rail - 3, z, CONCRETE, false);
			}
		} else if (edge) {
			place(x, rail - 1, z, CONCRETE, false);
			place(x, rail, z, CONCRETE, false);
		}
		final double pier = nearest(c.s(), stretch, Math.max(1, (int) Math.round(stretch.length() / settings.pierSpacing())));
		final double d = Math.abs(c.s() - pier);
		if (pier < 0) {
			return;
		}
		if (d <= 1.5 && a <= half - 0.5) {
			place(x, rail - 4, z, CONCRETE, false);
		}
		if (d <= 1.0 && a <= 1.5) {
			pierDown(x, z, rail - 5, ground, CONCRETE);
		}
		if (d <= 2.0 && a <= 2.5 && ground != NONE && ground < rail - 5) {
			place(x, ground, z, FOOTING, false);
		}
	}

	/** V2: a steel through truss in bays of about six, with stone piers between spans of at most 48. */
	private void truss(int x, int z, Local c, Stretch stretch, int ground, boolean inner, boolean edge) {
		final double a = Math.abs(c.a());
		final int rail = c.rail();
		final int spans = Math.max(1, (int) Math.ceil(stretch.length() / 48));
		final double spanLength = stretch.length() / spans;
		final double u = c.s() - stretch.s0();
		final int span = Math.min(spans - 1, (int) (u / spanLength));
		final double v = u - span * spanLength;
		final int bays = Math.max(2, (int) Math.round(spanLength / 6));
		final double bay = spanLength / bays;
		final double node = Math.round(v / bay) * bay;
		final boolean atNode = Math.abs(v - node) <= 0.5;
		final boolean interior = node > 0.5 && node < spanLength - 0.5;
		if (inner) {
			place(x, rail - 1, z, DECK, false);
			place(x, rail - 2, z, STEEL, false);
		} else if (edge) {
			place(x, rail - 1, z, STEEL, false);
			if (atNode && interior) {
				for (int h = 0; h < TRUSS_HEIGHT; h++) {
					place(x, rail + h, z, STEEL, false);
				}
			}
			if (v >= bay - 0.5 && v <= spanLength - bay + 0.5) {
				place(x, rail + TRUSS_HEIGHT, z, STEEL, false);
			}
		}
		if (atNode && interior && a <= half + 1.5) {
			place(x, rail + TRUSS_HEIGHT, z, STEEL, false);
		}
		// stone piers at the joins between spans
		final double join = Math.round(u / spanLength) * spanLength;
		if (join > 0.5 && join < stretch.length() - 0.5 && Math.abs(u - join) <= 1.5 && a <= half + 1.5) {
			pierDown(x, z, rail - 3, ground, STONE);
		}
	}

	/** V3: a stone viaduct of round arches on battered piers, ballast on top and a dressed ring round each arch. */
	private void arch(int x, int z, Local c, Stretch stretch, int ground, boolean inner, boolean edge) {
		final double a = Math.abs(c.a());
		if (a > half + 1.5) {
			return;
		}
		final int rail = c.rail();
		final double u = c.s() - stretch.s0();
		final int bays = Math.max(1, (int) Math.round(stretch.length() / Math.max(10, settings.pierSpacing() * 0.7)));
		final double bay = stretch.length() / bays;
		final double du = u - (Math.floor(u / bay) + 0.5) * bay;
		final double radius = (bay - 3) / 2;
		final double centre = -3 - radius;
		if (inner) {
			place(x, rail - 1, z, BALLAST, false);
		} else {
			place(x, rail - 1, z, STONE, false);
			place(x, rail, z, STONE, false);
		}
		final int bottom = ground == NONE ? rail - SCAN_DOWN : ground + 1;
		for (int y = rail - 2; y >= bottom; y--) {
			final double h = y + 0.5 - rail;
			final boolean open;
			final double distance = Math.sqrt(du * du + (h - centre) * (h - centre));
			if (h > centre) {
				open = distance <= radius;
			} else {
				// below the springing the piers thicken a block in every ten
				open = Math.abs(du) <= radius - (centre - h) / 10;
			}
			if (open) {
				continue;
			}
			place(x, y, z, h > centre - 0.5 && distance <= radius + 1 ? STONE_RING : STONE, false);
		}
	}

	/** V4: a slim concrete deck hung from white A-frame pylons by a semi-fan of stays. */
	private void cableStayed(int x, int z, Local c, Stretch stretch, int ground, boolean inner, boolean edge) {
		final double a = Math.abs(c.a());
		final int rail = c.rail();
		if (inner) {
			place(x, rail - 1, z, DECK, false);
			place(x, rail - 2, z, CONCRETE, false);
		} else if (edge) {
			place(x, rail - 1, z, CONCRETE, false);
			place(x, rail - 2, z, CONCRETE, false);
			place(x, rail, z, CONCRETE, false);
		}
		for (final double pylon : pylons(stretch)) {
			final double d = Math.abs(c.s() - pylon);
			if (d > 1.0) {
				continue;
			}
			final int height = pylonHeight(stretch);
			final double leg = half + 2.5;
			final int straight = CLEARANCE + 2;
			if (Math.abs(a - leg) <= 1.0) {
				pierDown(x, z, rail - 3, ground, WHITE);
			}
			if (a <= leg + 1.0) {
				place(x, rail - 3, z, WHITE, false);
			}
			for (int h = -2; h <= height; h++) {
				final double at = h <= straight ? leg : leg + (1.0 - leg) * Math.min(1, (h - straight) / (double) (height - 5 - straight));
				final boolean head = h >= height - 5 && a <= 1.5;
				if (head || (h <= straight && Math.abs(a - at) <= 1.0 && !(h >= 0 && h < CLEARANCE && a <= half + 0.5))) {
					place(x, rail + h, z, WHITE, false);
				}
			}
		}
	}

	/** V6: a steel deck on red towers with portal beams, main cables over the towers and hangers to the deck. */
	private void suspension(int x, int z, Local c, Stretch stretch, int ground, boolean inner, boolean edge) {
		final double a = Math.abs(c.a());
		final int rail = c.rail();
		final double end = Math.min(c.s() - stretch.s0(), stretch.s1() - c.s());
		if (end <= 3 && a <= half + 3.5) {
			// the anchorage: a concrete block each end that holds the main cables
			final int bottom = ground == NONE ? rail - 8 : ground + 1;
			for (int y = bottom; y <= rail + 1; y++) {
				if (!(y >= rail && a <= half + 0.5)) {
					place(x, y, z, CONCRETE, false);
				}
			}
		}
		if (inner) {
			place(x, rail - 1, z, DECK, false);
			place(x, rail - 2, z, STEEL, false);
		} else if (a <= half + 2.5) {
			place(x, rail - 1, z, STEEL, false);
			place(x, rail - 2, z, STEEL, false);
			if (edge) {
				place(x, rail, z, CONCRETE, false);
			}
		}
		final int height = towerHeight(stretch);
		final double leg = half + 2.5;
		for (final double tower : towers(stretch)) {
			if (Math.abs(c.s() - tower) > 1.0) {
				continue;
			}
			if (Math.abs(a - leg) <= 1.0) {
				pierDown(x, z, rail - 3, ground, TOWER);
				for (int h = -2; h <= height; h++) {
					place(x, rail + h, z, TOWER, false);
				}
			}
			if (a <= leg + 1.0) {
				for (final int h : new int[]{-3, CLEARANCE + 3, CLEARANCE + 4, (CLEARANCE + 4 + height) / 2, height - 2, height - 1, height}) {
					if (h <= height) {
						place(x, rail + h, z, TOWER, false);
					}
				}
			}
		}
	}

	/** Places {@code block} from {@code top} down to just over the ground (piers stop short of a ravine they cannot reach). */
	private void pierDown(int x, int z, int top, int ground, String block) {
		if (ground == NONE) {
			return;
		}
		for (int y = top; y > ground; y--) {
			place(x, y, z, block, false);
		}
	}

	/** The nearest of {@code count - 1} evenly spaced piers inside the stretch, or -1 when there are none. */
	private static double nearest(double s, Stretch stretch, int count) {
		if (count < 2) {
			return -1;
		}
		final double spacing = stretch.length() / count;
		final double k = Math.max(1, Math.min(count - 1, Math.round((s - stretch.s0()) / spacing)));
		return stretch.s0() + k * spacing;
	}

	private static double[] pylons(Stretch stretch) {
		return stretch.length() < 120 ? new double[]{stretch.s0() + stretch.length() / 2}
				: new double[]{stretch.s0() + stretch.length() * 0.25, stretch.s0() + stretch.length() * 0.75};
	}

	private int pylonHeight(Stretch stretch) {
		final double reach = stretch.length() < 120 ? stretch.length() / 2 : stretch.length() * 0.25;
		return (int) Math.round(Math.max(18, Math.min(70, reach * 0.45)) * settings.towerScale());
	}

	private static double[] towers(Stretch stretch) {
		return new double[]{stretch.s0() + stretch.length() * 0.2, stretch.s0() + stretch.length() * 0.8};
	}

	private int towerHeight(Stretch stretch) {
		return (int) Math.round(Math.max(18, Math.min(90, stretch.length() * 0.6 / 8 + 8)) * settings.towerScale());
	}

	// ---- members ----------------------------------------------------------------------------------------------------

	private void members(Stretch stretch) {
		switch (stretch.style()) {
			case TRUSS -> trussMembers(stretch);
			case CABLE_STAYED -> stays(stretch);
			case SUSPENSION -> cables(stretch);
			default -> {
			}
		}
	}

	/** Warren diagonals and the sloping end posts, each held by the top-chord node it meets. */
	private void trussMembers(Stretch stretch) {
		final int spans = Math.max(1, (int) Math.ceil(stretch.length() / 48));
		final double spanLength = stretch.length() / spans;
		final int bays = Math.max(2, (int) Math.round(spanLength / 6));
		final double bay = spanLength / bays;
		for (int span = 0; span < spans; span++) {
			final double start = stretch.s0() + span * spanLength;
			for (final int side : new int[]{-1, 1}) {
				final double a = side * (half + 1.0);
				for (int k = 0; k < bays; k++) {
					final double s0 = start + k * bay;
					final double s1 = s0 + bay;
					final double[] bottom;
					final double[] top;
					if (k == 0) {
						bottom = point(s0, a, 0);
						top = point(s1, a, TRUSS_HEIGHT);
					} else if (k == bays - 1) {
						bottom = point(s1, a, 0);
						top = point(s0, a, TRUSS_HEIGHT);
					} else if (k % 2 == 1) {
						bottom = point(s0, a, 0);
						top = point(s1, a, TRUSS_HEIGHT);
					} else {
						bottom = point(s1, a, 0);
						top = point(s0, a, TRUSS_HEIGHT);
					}
					anchor(STEEL, new double[]{top[0], top[1] + 0.5, top[2]}).add(BridgeMember.Material.STEEL, top, bottom, 0.45f);
				}
			}
		}
	}

	/** Stays from each pylon head out to both deck edges every five blocks, fanning down the head. */
	private void stays(Stretch stretch) {
		final double[] pylons = pylons(stretch);
		final int height = pylonHeight(stretch);
		for (int p = 0; p < pylons.length; p++) {
			final double pylon = pylons[p];
			final double back = p == 0 ? stretch.s0() + 3 : (pylons[p - 1] + pylon) / 2 + 2;
			final double fore = p == pylons.length - 1 ? stretch.s1() - 3 : (pylon + pylons[p + 1]) / 2 - 2;
			final AnchorBuilder head = anchor(WHITE, point(pylon, 0, height - 1.5));
			// the A-frame's legs from where they stop rising straight up to the head, drawn smooth
			final int straight = CLEARANCE + 2;
			for (final int side : new int[]{-1, 1}) {
				head.add(BridgeMember.Material.WHITE, point(pylon, side * (half + 2.5), straight + 0.5), point(pylon, side * 1.0, height - 5), 2.0f);
			}
			for (final int direction : new int[]{-1, 1}) {
				final double limit = direction < 0 ? back : fore;
				for (int k = 1; ; k++) {
					final double s = pylon + direction * (4 + 5 * k);
					if (direction < 0 ? s < limit : s > limit) {
						break;
					}
					final double drop = Math.min(height * 0.35, k * 0.7);
					for (final int side : new int[]{-1, 1}) {
						final double[] top = point(pylon, side * 0.6, height - 1 - drop);
						final double[] deck = point(s, side * (half + 1.0), 0.6);
						head.add(BridgeMember.Material.WHITE, top, deck, 0.16f);
					}
				}
			}
		}
	}

	/** Main cables over both towers, sagging between them, and a hanger to the deck every four blocks. */
	private void cables(Stretch stretch) {
		final double[] towers = towers(stretch);
		final int height = towerHeight(stretch);
		final double mid = (towers[0] + towers[1]) / 2;
		final double main = (towers[1] - towers[0]) / 2;
		final double a0 = stretch.s0() + 1.5;
		final double a1 = stretch.s1() - 1.5;
		for (final int side : new int[]{-1, 1}) {
			final double a = side * (half + 2.5);
			for (int t = 0; t < 2; t++) {
				final double tower = towers[t];
				final AnchorBuilder head = anchor(TOWER, point(tower, a, height - 0.5));
				// the light on top of the leg
				head.add(BridgeMember.Material.LIGHT, point(tower, a, height + 1), point(tower, a, height + 1.4), 0.4f);
				// back span: straight down to the anchorage
				final double anchorage = t == 0 ? a0 : a1;
				head.add(BridgeMember.Material.RED, point(tower, a, height + 0.5), point(anchorage, a, 1.2), 0.5f);
				for (double s = Math.min(tower, anchorage) + 4; s < Math.max(tower, anchorage) - 2; s += 4) {
					final double f = Math.abs(s - anchorage) / Math.abs(tower - anchorage);
					head.add(BridgeMember.Material.RED, point(s, a, 1.2 + f * (height + 0.5 - 1.2)), point(s, a, 0), 0.12f);
				}
				// main span: a parabola from this tower to the middle
				final double from = tower;
				final double to = mid;
				final int pieces = Math.max(2, (int) Math.ceil(Math.abs(to - from) / 4));
				double[] last = point(from, a, height + 0.5);
				for (int k = 1; k <= pieces; k++) {
					final double s = from + (to - from) * k / pieces;
					final double[] next = point(s, a, sag(s, mid, main, height));
					head.add(BridgeMember.Material.RED, last, next, 0.5f);
					last = next;
				}
				for (double s = from + Math.signum(to - from) * 3; Math.signum(to - from) * (to - s) > 0.5; s += Math.signum(to - from) * 4) {
					head.add(BridgeMember.Material.RED, point(s, a, sag(s, mid, main, height)), point(s, a, 0), 0.12f);
				}
			}
		}
	}

	private static double sag(double s, double mid, double main, int height) {
		final double f = (s - mid) / main;
		return 2.5 + (height - 2) * f * f;
	}

	// ---- overhead line (V5) -----------------------------------------------------------------------------------------

	private record Mast(double s, AnchorBuilder anchor, double[] wire, double[] messenger) {
	}

	/** A mast on the left of the track every few tens of blocks (closer on curves), an arm over the track and the two wires. */
	private void masts() {
		final List<Mast> masts = new ArrayList<>();
		double s = 2;
		while (s <= length - 1) {
			final Stretch stretch = stretchAt(s);
			if (stretch.kind() == Kind.TUNNEL) {
				masts.add(null);
				s = stretch.s1() + 3;
				continue;
			}
			masts.add(mast(s, stretch));
			double spacing = settings.mastSpacing();
			while (spacing > 12 && sagitta(s, spacing) > 0.35) {
				spacing -= 2;
			}
			s += spacing;
		}
		for (int i = 0; i + 1 < masts.size(); i++) {
			final Mast here = masts.get(i);
			final Mast next = masts.get(i + 1);
			if (here == null || next == null) {
				continue;
			}
			here.anchor().add(BridgeMember.Material.CABLE, here.wire(), next.wire(), 0.06f);
			here.anchor().add(BridgeMember.Material.CABLE, here.messenger(), next.messenger(), 0.07f);
		}
	}

	private Mast mast(double s, Stretch stretch) {
		final double wireHeight = 5.6;
		final double[] wire = point(s, 0, wireHeight);
		final double[] messenger = point(s, 0, wireHeight + 0.9);
		if (stretch.kind() == Kind.BRIDGE && stretch.style() == SpanStyle.TRUSS) {
			// hung from the truss's top bracing: a drop to the wires, no mast
			final AnchorBuilder anchor = anchor(STEEL, point(s, 0, TRUSS_HEIGHT + 0.5));
			anchor.add(BridgeMember.Material.STEEL, point(s, 0, TRUSS_HEIGHT), wire, 0.1f);
			return new Mast(s, anchor, wire, messenger);
		}
		final double a = -(half + 1.0);
		final BridgeStampPlan.Sample sample = path.at(s);
		final double[] t = tangent(sample);
		final int rail = rail(sample);
		final int x = (int) Math.floor(sample.x() - t[1] * a);
		final int z = (int) Math.floor(sample.z() + t[0] * a);
		int base = 0;
		String skin = null;
		if (stretch.kind() == Kind.BRIDGE) {
			skin = stretch.style() == SpanStyle.ARCH ? STONE : CONCRETE;
		} else {
			while (base < 4 && !open(site.kind(x, rail + base, z))) {
				base++;
			}
		}
		final double[] foot = point(s, a, base);
		final AnchorBuilder anchor = anchor(skin, point(s, a, base + 0.5));
		if (skin == null) {
			anchor.add(BridgeMember.Material.CONCRETE, foot, point(s, a, base + 0.4), 0.6f);
		}
		final double[] top = point(s, a, 7.2);
		anchor.add(BridgeMember.Material.STEEL, foot, top, 0.3f);
		final double[] arm = point(s, a, wireHeight + 0.9);
		anchor.add(BridgeMember.Material.STEEL, arm, point(s, 0.6, wireHeight + 0.9), 0.14f);
		anchor.add(BridgeMember.Material.STEEL, point(s, a, wireHeight - 0.1), point(s, 0.2, wireHeight + 0.85), 0.1f);
		anchor.add(BridgeMember.Material.STEEL, point(s, 0, wireHeight + 0.9), wire, 0.05f);
		return new Mast(s, anchor, wire, messenger);
	}

	/** How far a straight wire from s to s + span strays from the track at its middle. */
	private double sagitta(double s, double span) {
		final BridgeStampPlan.Sample a = path.at(s);
		final BridgeStampPlan.Sample b = path.at(Math.min(length, s + span));
		final BridgeStampPlan.Sample m = path.at(Math.min(length, s + span / 2));
		final double mx = (a.x() + b.x()) / 2 - m.x();
		final double mz = (a.z() + b.z()) / 2 - m.z();
		return Math.sqrt(mx * mx + mz * mz);
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	/** World position {@code s} along the rail, {@code a} across it (left positive) and {@code h} over its level. */
	private double[] point(double s, double a, double h) {
		final BridgeStampPlan.Sample sample = path.at(s);
		final double[] t = tangent(sample);
		return new double[]{sample.x() - t[1] * a, rail(sample) + h, sample.z() + t[0] * a};
	}

	private static double[] tangent(BridgeStampPlan.Sample sample) {
		final double size = Math.sqrt(sample.dx() * sample.dx() + sample.dz() * sample.dz());
		return size < 1e-9 ? new double[]{1, 0} : new double[]{sample.dx() / size, sample.dz() / size};
	}

	private static int rail(BridgeStampPlan.Sample sample) {
		return (int) Math.floor(sample.y() + 1e-3);
	}

	/**
	 * The top of the ground under a column, from just over the train's space down to {@link #SCAN_DOWN} under the rail: the
	 * highest solid block that is not part of a tree or a rail node. {@link #NONE} when there is none, or the chunk is not loaded.
	 */
	private int ground(int x, int z, int rail) {
		final long key = column(x, z) ^ ((long) rail << 52);
		final Integer cached = grounds.get(key);
		if (cached != null) {
			return cached;
		}
		int found = NONE;
		for (int y = rail + CLEARANCE + 2; y >= rail - SCAN_DOWN; y--) {
			final CellKind kind = site.kind(x, y, z);
			if (kind == CellKind.UNLOADED) {
				unloaded++;
				break;
			}
			if ((kind == CellKind.SOLID && !tree(site.describe(x, y, z))) || kind == CellKind.PROTECTED) {
				found = y;
				break;
			}
		}
		grounds.put(key, found);
		return found;
	}

	private static boolean tree(String id) {
		return id.endsWith("_log") || id.endsWith("_leaves") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae")
				|| id.endsWith("mushroom_block") || id.endsWith("bamboo") || id.endsWith("vine") || id.endsWith("vines");
	}

	private static boolean open(CellKind kind) {
		return kind == CellKind.AIR || kind == CellKind.SOFT;
	}

	/** {@code block} in the cell; with {@code onlyEmpty}, only where there is no solid block yet. Never a rail node or a protected cell. */
	private void place(int x, int y, int z, String block, boolean onlyEmpty) {
		final CellKind kind = site.kind(x, y, z);
		switch (kind) {
			case AIR, SOFT, FLUID -> cells.put(key(x, y, z), new Cell(x, y, z, block));
			case SOLID -> {
				if (!onlyEmpty && !block.equals(site.describe(x, y, z))) {
					cells.put(key(x, y, z), new Cell(x, y, z, block));
				}
			}
			default -> {
			}
		}
	}

	private void clear(int x, int y, int z) {
		final CellKind kind = site.kind(x, y, z);
		if (kind == CellKind.SOLID || kind == CellKind.SOFT || kind == CellKind.FLUID) {
			cells.put(key(x, y, z), new Cell(x, y, z, AIR));
		}
	}

	/** The anchor in the cell holding {@code at}; the first skin asked for it wins. */
	private AnchorBuilder anchor(String skin, double[] at) {
		final int x = (int) Math.floor(at[0]);
		final int y = (int) Math.floor(at[1]);
		final int z = (int) Math.floor(at[2]);
		return anchors.computeIfAbsent(key(x, y, z), k -> new AnchorBuilder(x, y, z, skin));
	}

	private static long column(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	static long key(int x, int y, int z) {
		return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF;
	}

	private static final class AnchorBuilder {

		private final int x;
		private final int y;
		private final int z;
		private final String skin;
		private final List<BridgeMember> members = new ArrayList<>();

		private AnchorBuilder(int x, int y, int z, String skin) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.skin = skin;
		}

		AnchorBuilder add(BridgeMember.Material material, double[] from, double[] to, float thickness) {
			members.add(new BridgeMember(material, (float) (from[0] - x), (float) (from[1] - y), (float) (from[2] - z),
					(float) (to[0] - x), (float) (to[1] - y), (float) (to[2] - z), thickness));
			return this;
		}
	}
}
