package com.aureliatransit.architecture.earthworks;

import com.aureliatransit.architecture.station.CellKind;
import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.SiteProbe;
import com.aureliatransit.architecture.station.WorldOp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns an {@link EarthJob} into world changes ({@link WorldOp}s) by reading the ground through a {@link SiteProbe}, one
 * column at a time, so a big job can be planned a slice per server tick ({@link #step}). The ops go to the station build job,
 * which builds bottom up over time and keeps an undo that survives a restart.
 *
 * <p>It never plans a write into a protected cell (block entities, unbreakable blocks), an MTR rail node or an unloaded
 * chunk; those are counted in {@link Result#skipped}. Trees do not count as ground: a level site clears them, and a pier
 * goes past their leaves to the soil. Pure logic, no Minecraft classes.
 */
public final class EarthPlanner {

	/** How far above the target height a cut reaches, trees included. */
	static final int SCAN_UP = 48;
	/** How far below the target height the ground is looked for (fill depth, pier height). */
	static final int SCAN_DOWN = 64;
	/** Free height kept above a viaduct deck, for trains and overhead line. */
	static final int CLEARANCE = 5;
	/** Blocks between viaduct piers. */
	static final int PIER_SPACING = 12;
	/** The tallest retaining wall. */
	static final int MAX_WALL = 32;
	/** Ops a job may plan before it is refused as too big. */
	public static final int MAX_OPS = 1_000_000;

	private static final int NONE = Integer.MIN_VALUE;

	/**
	 * @param skipped     cells left alone because they are protected, MTR rail nodes, out of the world or not loaded
	 * @param columnsLeft columns not (fully) built: no ground within reach, a hill taller than a cut reaches, or a tunnel that
	 *                    comes out of the ground where only a covered one can go
	 * @param tooBig      true when the job passed {@link #MAX_OPS} and planning stopped
	 */
	public record Result(List<WorldOp> ops, int skipped, int columnsLeft, boolean tooBig) {
	}

	/** One column of the job: where it is, how far along (t 0 at A, 1 at B, {@code along} in blocks) and how far across. */
	private record Column(int x, int z, double t, int side, double along) {
	}

	private final EarthJob job;
	private final List<Column> columns;
	/** Unit vector across the line, to the left looking from A to B. */
	private final double px;
	private final double pz;
	private final Map<Long, WorldOp> ops = new LinkedHashMap<>();
	private int next;
	private int skipped;
	private int left;
	private boolean tooBig;

	public EarthPlanner(EarthJob job) {
		this.job = job;
		final double length = job.length();
		final double ux = length > 0 ? (job.b().x() - job.a().x()) / length : 1;
		final double uz = length > 0 ? (job.b().z() - job.a().z()) / length : 0;
		px = -uz;
		pz = ux;
		columns = job.tool().line() ? lineColumns(job, ux, uz, px, pz) : areaColumns(job);
	}

	/** Plans the whole job at once. */
	public static Result plan(EarthJob job, SiteProbe site) {
		final EarthPlanner planner = new EarthPlanner(job);
		while (!planner.step(site, Long.MAX_VALUE)) {
			// a deadline that never passes plans it all in one step
		}
		return planner.result();
	}

	/** Plans columns until done or {@code deadline} ({@link System#nanoTime}) has passed, at least one; true when done. */
	public boolean step(SiteProbe site, long deadline) {
		while (next < columns.size()) {
			if (ops.size() > MAX_OPS) {
				tooBig = true;
				next = columns.size();
				break;
			}
			column(site, columns.get(next++));
			if (System.nanoTime() >= deadline) {
				break;
			}
		}
		return next >= columns.size();
	}

	public int done() {
		return next;
	}

	public int total() {
		return columns.size();
	}

	public Result result() {
		return new Result(List.copyOf(ops.values()), skipped, left, tooBig);
	}

	// ---- columns ----------------------------------------------------------------------------------------------------

	private static List<Column> areaColumns(EarthJob job) {
		final int x0 = Math.min(job.a().x(), job.b().x());
		final int x1 = Math.max(job.a().x(), job.b().x());
		final int z0 = Math.min(job.a().z(), job.b().z());
		final int z1 = Math.max(job.a().z(), job.b().z());
		final double dx = job.b().x() - job.a().x();
		final double dz = job.b().z() - job.a().z();
		final double length2 = dx * dx + dz * dz;
		final List<Column> list = new ArrayList<>((x1 - x0 + 1) * (z1 - z0 + 1));
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				final double t = length2 == 0 ? 0 : clamp(((x - job.a().x()) * dx + (z - job.a().z()) * dz) / length2);
				list.add(new Column(x, z, t, 0, 0));
			}
		}
		return list;
	}

	/**
	 * The cells within the job's reach of the line from A's centre to B's, in order along it. Candidates come from sampling
	 * the line every half block; each cell's distance across and along is then measured from its own centre, so a diagonal
	 * line gets the same rows as a straight one.
	 */
	private static List<Column> lineColumns(EarthJob job, double ux, double uz, double px, double pz) {
		final int half = (job.width() - 1) / 2;
		final int reach = job.tool() == EarthTool.WALL ? half : half + 1;
		final double length = job.length();
		final double ax = job.a().x() + 0.5;
		final double az = job.a().z() + 0.5;
		final int steps = Math.max(1, (int) Math.ceil(length * 2));
		final Set<Long> seen = new HashSet<>();
		final List<Column> list = new ArrayList<>();
		for (int i = 0; i <= steps; i++) {
			final double s = length * i / steps;
			final double cx = ax + ux * s;
			final double cz = az + uz * s;
			for (int k = -2 * reach - 1; k <= 2 * reach + 1; k++) {
				final int x = (int) Math.floor(cx + px * k / 2.0);
				final int z = (int) Math.floor(cz + pz * k / 2.0);
				if (!seen.add(((long) x << 32) ^ (z & 0xFFFFFFFFL))) {
					continue;
				}
				final double rx = x + 0.5 - ax;
				final double rz = z + 0.5 - az;
				final int side = (int) Math.round(Math.abs(rx * px + rz * pz));
				final double along = Math.max(0, Math.min(length, rx * ux + rz * uz));
				if (side <= reach) {
					list.add(new Column(x, z, length > 0 ? along / length : 0, side, along));
				}
			}
		}
		return list;
	}

	private void column(SiteProbe site, Column c) {
		switch (job.tool()) {
			case LEVEL, SLOPE -> area(site, c);
			case WALL -> wall(site, c);
			case CUT_COVER -> cutAndCover(site, c);
			case BORE -> bore(site, c);
			case VIADUCT -> viaduct(site, c);
		}
	}

	// ---- tools ------------------------------------------------------------------------------------------------------

	/** Level and Slope: everything above the target height goes; below it, hollows fill with earth under turf. */
	private void area(SiteProbe site, Column c) {
		final int target = job.tool() == EarthTool.LEVEL ? job.a().y() : job.heightAt(c.t());
		final int top = ground(site, c.x(), c.z(), target + SCAN_UP, target - SCAN_DOWN);
		if (top == NONE) {
			left++;
			return;
		}
		if (top >= target + SCAN_UP) {
			// a hill taller than the cut reaches: what is above stays
			left++;
		}
		for (int y = target + 1; y <= target + SCAN_UP; y++) {
			clear(site, c.x(), y, c.z());
		}
		if (top < target) {
			for (int y = top + 1; y < target; y++) {
				fill(site, c.x(), y, c.z(), EarthLook.EARTH);
			}
			fill(site, c.x(), target, c.z(), EarthLook.TURF);
		} else if (EarthLook.EARTH.equals(site.describe(c.x(), target, c.z()))) {
			// a cut through soil: the new surface grows grass, as the old one did
			set(site, c.x(), target, c.z(), WorldOp.Kind.FILL, Role.FOUNDATION, EarthLook.TURF);
		}
	}

	/** A retaining wall: from the lower ground beside it up to the clicked height, topped with a coping. */
	private void wall(SiteProbe site, Column c) {
		final int top = job.heightAt(c.t());
		final int half = (job.width() - 1) / 2;
		int bottom = NONE;
		for (int side = -1; side <= 1; side++) {
			final int x = (int) Math.floor(c.x() + 0.5 + px * side * (half + 2));
			final int z = (int) Math.floor(c.z() + 0.5 + pz * side * (half + 2));
			final int ground = ground(site, x, z, top + SCAN_UP, top - SCAN_DOWN);
			if (ground != NONE) {
				bottom = bottom == NONE ? ground : Math.min(bottom, ground);
			}
		}
		if (bottom == NONE) {
			left++;
			return;
		}
		if (bottom >= top) {
			// ground as high as the wall on both sides: nothing to hold back
			return;
		}
		if (top - bottom > MAX_WALL) {
			left++;
			bottom = top - MAX_WALL;
		}
		for (int y = bottom; y < top; y++) {
			set(site, c.x(), y, c.z(), WorldOp.Kind.PLACE, Role.WALL, job.look().structure());
		}
		set(site, c.x(), top, c.z(), WorldOp.Kind.PLACE, Role.WALL, job.look().surface());
	}

	/**
	 * A box tunnel: floor, walls and roof round a clear space. Where the ground does not cover the roof it is an open cutting:
	 * walls up to the ground, nothing over it, and the earth above the tunnel's space dug out.
	 */
	private void cutAndCover(SiteProbe site, Column c) {
		final int half = (job.width() - 1) / 2;
		final int floor = job.heightAt(c.t());
		final int roof = floor + job.height() + 1;
		final int ground = ground(site, c.x(), c.z(), roof + SCAN_UP, floor - SCAN_DOWN);
		if (ground == NONE) {
			left++;
			return;
		}
		final boolean covered = ground > roof;
		final String structure = job.look().structure();
		for (int y = ground + 1; y < floor; y++) {
			// the tunnel crosses a hollow: earth under it, as an embankment
			fill(site, c.x(), y, c.z(), EarthLook.EARTH);
		}
		if (c.side() <= half) {
			set(site, c.x(), floor, c.z(), WorldOp.Kind.PLACE, Role.FLOOR, job.look().surface());
			for (int y = floor + 1; y < roof; y++) {
				clear(site, c.x(), y, c.z());
			}
			if (covered) {
				set(site, c.x(), roof, c.z(), WorldOp.Kind.PLACE, Role.ROOF, structure);
			} else {
				for (int y = roof; y <= ground; y++) {
					clear(site, c.x(), y, c.z());
				}
			}
		} else {
			final int wallTop = covered ? roof : Math.max(floor + 1, ground);
			for (int y = floor; y <= wallTop; y++) {
				set(site, c.x(), y, c.z(), WorldOp.Kind.PLACE, Role.WALL, structure);
			}
		}
	}

	/**
	 * A round tunnel: a ring of lining round a clear circle, with a flat floor at the clicked height. A bored tunnel stays
	 * under ground; a column the ground does not cover all round is left out (its portal is a cut-and-cover job).
	 */
	private void bore(SiteProbe site, Column c) {
		final int floor = job.heightAt(c.t());
		final int half = job.width() / 2;
		final double radius = job.width() / 2.0;
		final double centre = floor + 0.5 + radius;
		final int ringTop = (int) Math.floor(centre + radius + 1);
		final int ground = ground(site, c.x(), c.z(), ringTop + SCAN_UP, floor - SCAN_DOWN);
		if (ground == NONE || ground <= ringTop) {
			left++;
			return;
		}
		for (int y = floor - 1; y <= ringTop; y++) {
			final double dy = y + 0.5 - centre;
			final double distance = Math.sqrt(c.side() * c.side() + dy * dy);
			if (y > floor && distance < radius) {
				clear(site, c.x(), y, c.z());
			} else if (distance < radius + 1 || (y == floor && c.side() <= half)) {
				set(site, c.x(), y, c.z(), WorldOp.Kind.PLACE, Role.WALL, job.look().lining());
			}
		}
	}

	/** A deck at the clicked heights with parapets, a girder under it, piers to the ground and clear space over it. */
	private void viaduct(SiteProbe site, Column c) {
		final int half = (job.width() - 1) / 2;
		final int deck = job.heightAt(c.t());
		final String structure = job.look().structure();
		if (c.side() <= half) {
			set(site, c.x(), deck, c.z(), WorldOp.Kind.PLACE, Role.BRIDGE_DECK, job.look().surface());
			for (int y = deck + 1; y <= deck + CLEARANCE; y++) {
				clear(site, c.x(), y, c.z());
			}
		} else {
			set(site, c.x(), deck, c.z(), WorldOp.Kind.PLACE, Role.BRIDGE_DECK, structure);
			set(site, c.x(), deck + 1, c.z(), WorldOp.Kind.PLACE, Role.RAILING, job.look().parapet());
		}
		final int ground = ground(site, c.x(), c.z(), deck - 1, deck - SCAN_DOWN);
		if (ground != NONE && ground >= deck - 1) {
			// the deck sits on the ground here
			return;
		}
		set(site, c.x(), deck - 1, c.z(), WorldOp.Kind.PLACE, Role.BEAM, structure);
		final boolean pierRow = Math.floorMod((int) Math.floor(c.along()), PIER_SPACING) < 2 || c.along() >= job.length() - 2;
		if (!pierRow || c.side() > Math.max(1, half - 1)) {
			return;
		}
		if (ground == NONE) {
			// deeper than a pier reaches (a ravine or deep water)
			left++;
			return;
		}
		for (int y = ground + 1; y <= deck - 2; y++) {
			set(site, c.x(), y, c.z(), WorldOp.Kind.PLACE, Role.BRIDGE_PIER, structure);
		}
	}

	// ---- cells ------------------------------------------------------------------------------------------------------

	/**
	 * The highest ground block in the column from {@code from} down to {@code to}, or {@link #NONE}. Logs, leaves and other tree
	 * parts are not ground; a protected cell or rail node is (nothing is dug under it). An unloaded chunk has none.
	 */
	private static int ground(SiteProbe site, int x, int z, int from, int to) {
		for (int y = from; y >= to; y--) {
			final CellKind kind = site.kind(x, y, z);
			switch (kind) {
				case SOLID -> {
					if (!tree(site.describe(x, y, z))) {
						return y;
					}
				}
				case PROTECTED, RAIL_NODE -> {
					return y;
				}
				case UNLOADED -> {
					return NONE;
				}
				default -> {
				}
			}
		}
		return NONE;
	}

	private static boolean tree(String id) {
		return id.endsWith("_log") || id.endsWith("_leaves") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae")
				|| id.endsWith("mushroom_block") || id.endsWith("bamboo") || id.endsWith("vine") || id.endsWith("vines");
	}

	/** Air in the cell, unless it is air already or not ours to touch. */
	private void clear(SiteProbe site, int x, int y, int z) {
		switch (site.kind(x, y, z)) {
			case AIR, OUT_OF_WORLD -> {
			}
			case SOFT, SOLID, FLUID -> put(new WorldOp(WorldOp.Kind.CLEAR, x, y, z, null, WorldOp.AIR, Heading.NORTH, ""));
			default -> skipped++;
		}
	}

	/** Earth (or {@code block}) in an empty cell; solid ground is left as it is. */
	private void fill(SiteProbe site, int x, int y, int z, String block) {
		switch (site.kind(x, y, z)) {
			case SOLID -> {
			}
			case AIR, SOFT, FLUID -> put(new WorldOp(WorldOp.Kind.FILL, x, y, z, Role.FOUNDATION, block, Heading.NORTH, ""));
			default -> skipped++;
		}
	}

	/** {@code block} in the cell whatever it holds now, unless it holds that block already or is not ours to touch. */
	private void set(SiteProbe site, int x, int y, int z, WorldOp.Kind kind, Role role, String block) {
		switch (site.kind(x, y, z)) {
			case SOLID -> {
				if (!block.equals(site.describe(x, y, z))) {
					put(new WorldOp(kind, x, y, z, role, block, Heading.NORTH, ""));
				}
			}
			case AIR, SOFT, FLUID -> put(new WorldOp(kind, x, y, z, role, block, Heading.NORTH, ""));
			default -> skipped++;
		}
	}

	/** The last op planned for a cell wins. */
	private void put(WorldOp op) {
		final long key = ((long) (op.x() & 0x3FFFFFF) << 38) | ((long) (op.z() & 0x3FFFFFF) << 12) | (op.y() & 0xFFF);
		ops.remove(key);
		ops.put(key, op);
	}

	private static double clamp(double t) {
		return Math.max(0, Math.min(1, t));
	}
}
