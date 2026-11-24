/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * The rasteriser (spec roads-v1 §7, task G-6): turns a {@link org.mtr.aurelia.road.road.raster.RoadPiece} into
 * columns and a block plan by classifying every column once against the lane curves around it. Lane curves are lines
 * ({@link org.mtr.aurelia.road.road.raster.LineSegment}), arcs ({@link org.mtr.aurelia.road.road.raster.ArcSegment})
 * and, from G-6+, whole lanes ({@link org.mtr.aurelia.road.road.raster.LanePath}) rebuilt from MTR's own pieces.
 * A solved piece ({@code RoadPiece.solved}) has square ends at its centre-line nodes.
 *
 * <p>From G-7 a piece keeps its lanes' rails for heights, each column gets a target top, and
 * {@link org.mtr.aurelia.road.road.raster.SurfaceFit} stacks it: a slab on a half step, the surface block, its
 * foundation and the clearance above.
 *
 * <p>Work is done relative to each piece's anchor block, with every comparison snapped to 2⁻²⁰ block, so the same road
 * gives the same blocks wherever it is. The blocks come from a {@link org.mtr.aurelia.road.road.raster.BlockPalette}:
 * from R-9 a palette file ({@code road.palette}). {@link org.mtr.aurelia.road.road.raster.RasterAscii} draws the
 * result for the ASCII snapshot tests.
 */
package org.mtr.aurelia.road.road.raster;
