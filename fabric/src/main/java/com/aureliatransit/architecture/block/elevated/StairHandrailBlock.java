package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.StairRailEdge;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.StairRailPart;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.EnumMap;
import java.util.Map;

import static com.aureliatransit.architecture.util.Shapes.box;

/**
 * A handrail that climbs a stair: one block per step on the stair's cheek wall, rising a block per block toward its front,
 * so a flight's rail is one sloped line instead of a level piece per step. The station generator lays it; the piece
 * ({@code kind}: the foot with its newel post, a run piece, or the top one that runs on level into the landing's rail) is
 * cycled by right-clicking with the brush like the other station pieces. Where the landing's rail stands on the side of its
 * block over the drop, the stair's rail stands on the same side ({@code edge}, seen looking up the stair), so they line up.
 */
public class StairHandrailBlock extends FacingKindBlock<StairRailPart> {

	public static final EnumProperty<StairRailPart> KIND = EnumProperty.of("kind", StairRailPart.class);
	public static final EnumProperty<StairRailEdge> EDGE = EnumProperty.of("edge", StairRailEdge.class);

	private static final Map<StairRailEdge, Map<Direction, VoxelShape>> OUTLINES = shapes(16);
	/**
	 * A block and a half above the cheek wall, as the level railings: the block's foot is the tread of the step beside it, so
	 * a passenger on any step has the rail 1.5 above their feet and cannot jump over it.
	 */
	private static final Map<StairRailEdge, Map<Direction, VoxelShape>> COLLISIONS = shapes(RailingBlock.COLLISION_HEIGHT);

	public StairHandrailBlock(Settings settings) {
		super(settings, "stair_handrail", Placement.AWAY_FROM_PLAYER, StairRailPart.class, kind -> box(7, 0, 0, 9, 16, 16));
		setDefaultState(getDefaultState().with(EDGE, StairRailEdge.NONE));
	}

	/** The rail's 2-px-wide strip along the block, at {@code edge}, {@code height} px high, per facing. */
	private static Map<StairRailEdge, Map<Direction, VoxelShape>> shapes(double height) {
		final Map<StairRailEdge, Map<Direction, VoxelShape>> out = new EnumMap<>(StairRailEdge.class);
		for (final StairRailEdge edge : StairRailEdge.values()) {
			// facing north, the left side is west (low x)
			final double x = edge == StairRailEdge.LEFT ? 0.5 : edge == StairRailEdge.RIGHT ? 13.5 : 7;
			out.put(edge, Shapes.horizontalMap(box(x, 0, 0, x + 2, height, 16)));
		}
		return out;
	}

	@Override
	protected EnumProperty<StairRailPart> kindProperty() {
		return KIND;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(EDGE);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return OUTLINES.get(state.get(EDGE)).get(state.get(FACING));
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return COLLISIONS.get(state.get(EDGE)).get(state.get(FACING));
	}
}
