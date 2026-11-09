package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.aureliatransit.architecture.station.StationPlanner.Cell;

/**
 * A complete, styled station in local coordinates, ready to be validated ({@link LayoutValidator}), surveyed against the
 * world ({@link SiteSurvey}) and built.
 *
 * @param blocks         every block to place, in build order (role order, then y, v, u); at most one block per cell
 * @param trackEnvelopes one per track: columns u-1..u+1, y 0..{@link StationGeometry#ENVELOPE_HEIGHT}-1, the full footprint
 *                       length; must stay free of anything a train would hit
 * @param headroom       one per platform: its columns, the {@link StationGeometry#HEADROOM} cells above the walking surface,
 *                       v 0..platformLength-1; terrain there must be cleared (planned blocks inside are fine)
 * @param supportAreas   cells at y = -1 that must carry the station: under every platform and ramp cell and under every track's
 *                       centre column
 * @param foundationId   block used to fill unsupported ground when terrain changes are approved
 */
public record StationPlan(StationSpec spec, TrackLayout layout, List<PlannedBlock> blocks, List<Box> trackEnvelopes, List<Box> headroom,
		List<Box> supportAreas, String foundationId, List<SkippedBlock> skipped, Map<String, Integer> skippedByBlock) {

	public StationPlan {
		blocks = List.copyOf(blocks);
		trackEnvelopes = List.copyOf(trackEnvelopes);
		headroom = List.copyOf(headroom);
		supportAreas = List.copyOf(supportAreas);
		skipped = List.copyOf(skipped);
		skippedByBlock = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(skippedByBlock));
	}

	/** A plan from before skipped blocks were recorded (no losses, no counts). */
	public StationPlan(StationSpec spec, TrackLayout layout, List<PlannedBlock> blocks, List<Box> trackEnvelopes, List<Box> headroom,
			List<Box> supportAreas, String foundationId) {
		this(spec, layout, blocks, trackEnvelopes, headroom, supportAreas, foundationId, List.of(), Map.of());
	}

	/** Highest y used by any planned block (at least the top of the track envelopes). */
	public int maxY() {
		int max = StationGeometry.ENVELOPE_HEIGHT - 1;
		for (final PlannedBlock block : blocks) {
			max = Math.max(max, block.y());
		}
		return max;
	}

	/** Every cell the station touches or reserves, from the support layer (y = -1) to {@link #maxY()}. */
	public Box footprint() {
		return new Box(0, -1, layout.vMin(), layout.width() - 1, maxY(), layout.vMax());
	}

	public Map<Role, Integer> countByRole() {
		final Map<Role, Integer> counts = new EnumMap<>(Role.class);
		for (final PlannedBlock block : blocks) {
			counts.merge(block.role(), 1, Integer::sum);
		}
		return counts;
	}

	/**
	 * This plan with the player's per-cell edits applied last: an override replaces whatever the planner put in that cell, and
	 * a new cell is added with the role of the block it replaces (fixture when the cell was empty). An override of
	 * {@code minecraft:air} clears the cell. The plan stays deterministic and every other cell is untouched.
	 */
	public StationPlan withOverrides(Map<Cell, StationOverride> overrides) {
		if (overrides.isEmpty()) {
			return this;
		}
		final Map<Cell, PlannedBlock> merged = new java.util.LinkedHashMap<>();
		for (final PlannedBlock block : blocks) {
			merged.put(new Cell(block.u(), block.y(), block.v()), block);
		}
		for (final Map.Entry<Cell, StationOverride> entry : overrides.entrySet()) {
			final Cell cell = entry.getKey();
			final StationOverride override = entry.getValue();
			if (override.blockId().equals("minecraft:air")) {
				merged.remove(cell);
				continue;
			}
			final PlannedBlock old = merged.get(cell);
			merged.put(cell, new PlannedBlock(cell.u(), cell.y(), cell.v(), old == null ? Role.FIXTURE : old.role(), override.blockId(),
					override.front(), old == null ? "" : old.text(), override.state()));
		}
		final List<PlannedBlock> edited = new ArrayList<>(merged.values());
		edited.sort(StationPlanner.BUILD_ORDER);
		return new StationPlan(spec, layout, edited, trackEnvelopes, headroom, supportAreas, foundationId, skipped, skippedByBlock);
	}
}
