package com.aureliatransit.architecture.earthworks;

import com.aureliatransit.architecture.station.Box;
import com.aureliatransit.architecture.station.CellKind;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.SiteProbe;
import com.aureliatransit.architecture.station.StationPlacement;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.SurveyReport;
import com.aureliatransit.architecture.station.WorldOp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The ground a station at street level stands on: Earthworks' {@link EarthTool#LEVEL} over the station's footprint and a
 * {@link #MARGIN} round it, at the height of the ground under the tracks (local y = -1). A hill is cut away above that height and a
 * hollow filled up to it, so a station planned into a hillside or over a dip stands on a level pad instead of being refused.
 *
 * <p>The pad is planned in tiles of at most {@link EarthJob#MAX_AREA_SIDE} a side, so a long platform works; a tile cannot reach
 * {@link EarthPlanner#MAX_OPS} (a column changes at most {@link EarthPlanner#SCAN_DOWN} + 1 cells). Its ops join the survey's in one
 * {@link SurveyReport}, so the build and its ground are one job and one undo. The survey's own rules win: no pad op lands in a
 * planned cell, a track envelope, platform headroom, a support box or a cell the survey already clears or fills. Earthworks never
 * writes a protected cell, an MTR rail node, an unloaded chunk or out of the world, so neither does the pad.
 *
 * <p>Underground stations and airports get no pad: the first are dug under the street, and an airport's site is too big to
 * level as part of one build (an Earthworks job of its own does that). Pure logic, no Minecraft classes.
 */
public final class StationPad {

	/** Cells levelled round the footprint on every side. */
	public static final int MARGIN = 2;

	private final StationPlan plan;
	private final LocalFrame frame;
	private final List<EarthPlanner> tiles = new ArrayList<>();
	private int tile;

	private StationPad(StationPlan plan, LocalFrame frame) {
		this.plan = plan;
		this.frame = frame;
		if (StationPlacement.depth(plan.spec()) != 0 || plan.spec().isAirport()) {
			return;
		}
		int uMin = Integer.MAX_VALUE;
		int uMax = Integer.MIN_VALUE;
		int vMin = Integer.MAX_VALUE;
		int vMax = Integer.MIN_VALUE;
		for (final PlannedBlock block : plan.blocks()) {
			uMin = Math.min(uMin, block.u());
			uMax = Math.max(uMax, block.u());
			vMin = Math.min(vMin, block.v());
			vMax = Math.max(vMax, block.v());
		}
		for (final Box box : boxes(plan)) {
			uMin = Math.min(uMin, box.uMin());
			uMax = Math.max(uMax, box.uMax());
			vMin = Math.min(vMin, box.vMin());
			vMax = Math.max(vMax, box.vMax());
		}
		if (uMin > uMax) {
			return;
		}
		uMin -= MARGIN;
		uMax += MARGIN;
		vMin -= MARGIN;
		vMax += MARGIN;
		final int x0 = Math.min(frame.x(uMin, vMin), frame.x(uMax, vMax));
		final int x1 = Math.max(frame.x(uMin, vMin), frame.x(uMax, vMax));
		final int z0 = Math.min(frame.z(uMin, vMin), frame.z(uMax, vMax));
		final int z1 = Math.max(frame.z(uMin, vMin), frame.z(uMax, vMax));
		final int target = frame.y(-1);
		final int side = EarthJob.MAX_AREA_SIDE;
		for (int x = x0; x <= x1; x += side) {
			for (int z = z0; z <= z1; z += side) {
				tiles.add(new EarthPlanner(new EarthJob(EarthTool.LEVEL, new EarthJob.Point(x, target, z),
						new EarthJob.Point(Math.min(x1, x + side - 1), target, Math.min(z1, z + side - 1)), 0, 0, EarthLook.STONE)));
			}
		}
	}

	/** The pad for {@code plan} built in {@code frame}; one with nothing to do for an underground station or an airport. */
	public static StationPad start(StationPlan plan, LocalFrame frame) {
		return new StationPad(plan, frame);
	}

	/** Plans columns until done or {@code deadline} ({@link System#nanoTime}) has passed; true when done. */
	public boolean step(SiteProbe site, long deadline) {
		while (tile < tiles.size()) {
			if (!tiles.get(tile).step(site, deadline)) {
				return false;
			}
			tile++;
			if (System.nanoTime() >= deadline) {
				break;
			}
		}
		return tile >= tiles.size();
	}

	public boolean done() {
		return tile >= tiles.size();
	}

	/**
	 * {@code report} with the pad's ops added: clears still top-down, fills bottom-up, then the planned blocks. The pad's changes
	 * to cells that are not air count as terrain changes. A survey without its terrain ops gets none from the pad either.
	 *
	 * @throws IllegalStateException before the pad is planned
	 */
	public SurveyReport merge(SurveyReport report, SiteProbe site) {
		if (!done()) {
			throw new IllegalStateException("pad not planned");
		}
		if (!report.replaceTerrain() || tiles.isEmpty()) {
			return report;
		}
		final Set<Long> taken = new HashSet<>();
		final List<WorldOp> clears = new ArrayList<>();
		final List<WorldOp> fills = new ArrayList<>();
		final List<WorldOp> places = new ArrayList<>();
		for (final WorldOp op : report.ops()) {
			taken.add(key(op.x(), op.y(), op.z()));
			(op.kind() == WorldOp.Kind.CLEAR ? clears : op.kind() == WorldOp.Kind.FILL ? fills : places).add(op);
		}
		final Set<Long> planned = new HashSet<>();
		for (final PlannedBlock block : plan.blocks()) {
			planned.add(key(block.u(), block.y(), block.v()));
		}
		final List<Box> reserved = boxes(plan);
		int changes = 0;
		for (final EarthPlanner planner : tiles) {
			for (final WorldOp op : planner.result().ops()) {
				final int u = frame.u(op.x(), op.z());
				final int v = frame.v(op.x(), op.z());
				final int y = op.y() - frame.y(0);
				if (taken.contains(key(op.x(), op.y(), op.z())) || planned.contains(key(u, y, v)) || inside(reserved, u, y, v)) {
					continue;
				}
				if (op.kind() == WorldOp.Kind.CLEAR || site.kind(op.x(), op.y(), op.z()) != CellKind.AIR) {
					changes++;
				}
				(op.kind() == WorldOp.Kind.CLEAR ? clears : fills).add(op);
			}
		}
		clears.sort(Comparator.comparingInt(WorldOp::y).reversed());
		fills.sort(Comparator.comparingInt(WorldOp::y));
		final List<WorldOp> ops = new ArrayList<>(clears.size() + fills.size() + places.size());
		ops.addAll(clears);
		ops.addAll(fills);
		ops.addAll(places);
		return new SurveyReport(report.issues(), report.counts(), ops, report.terrainChanges() + changes, report.replaceTerrain());
	}

	/** The boxes whose cells the survey alone decides: track envelopes, headroom and supports. */
	private static List<Box> boxes(StationPlan plan) {
		final List<Box> boxes = new ArrayList<>(plan.trackEnvelopes());
		boxes.addAll(plan.headroom());
		boxes.addAll(plan.supportAreas());
		return boxes;
	}

	private static boolean inside(List<Box> boxes, int u, int y, int v) {
		for (final Box box : boxes) {
			if (box.contains(u, y, v)) {
				return true;
			}
		}
		return false;
	}

	private static long key(int a, int y, int b) {
		return ((long) (a & 0x3FFFFFF) << 38) | ((long) (b & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}
}
