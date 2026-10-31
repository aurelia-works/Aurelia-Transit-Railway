package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.entity.DeparturesBoardBlockEntity;
import com.aureliatransit.architecture.transit.StationSnapshot;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

import java.util.function.BiFunction;

/**
 * The hall departures board. The model is the frame; the rows are drawn by the client renderer from
 * {@link com.aureliatransit.architecture.airport.AirportDepartures}. The snapshot function is installed on the client.
 * The renderer refreshes the rows while the board is on screen, so the block has no ticker. A dedicated server keeps
 * the empty snapshot.
 */
public class DeparturesBoardBlock extends FacingShapedBlock implements BlockEntityProvider {

	public static BiFunction<BlockPos, Boolean, StationSnapshot> snapshot = (pos, withServices) -> StationSnapshot.EMPTY;

	public DeparturesBoardBlock(Settings settings, Placement placement, VoxelShape northOutline) {
		super(settings, placement, northOutline);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new DeparturesBoardBlockEntity(pos, state);
	}

	/** What the client tick asks for. The client installer passes services and the automatic station association. */
	public static StationSnapshot resolve(BlockPos pos) {
		final StationSnapshot found = snapshot.apply(pos, true);
		return found == null ? StationSnapshot.EMPTY : found;
	}
}
