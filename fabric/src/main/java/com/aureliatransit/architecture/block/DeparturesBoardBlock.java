package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.entity.DeparturesBoardBlockEntity;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import com.aureliatransit.architecture.transit.StationSnapshot;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.function.BiFunction;

/**
 * The hall departures board. The model is the frame; the rows are drawn by the client renderer from
 * {@link com.aureliatransit.architecture.airport.AirportDepartures}. The snapshot function is installed on the client.
 * A dedicated server keeps the empty snapshot and does not tick.
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

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (!world.isClient || type != ModBlockEntities.DEPARTURES_BOARD) {
			return null;
		}
		return (BlockEntityTicker<T>) (world1, pos, state1, blockEntity) -> ((DeparturesBoardBlockEntity) blockEntity).clientTick();
	}

	/** What the client tick asks for. The client installer passes services and the automatic station association. */
	public static StationSnapshot resolve(BlockPos pos) {
		final StationSnapshot found = snapshot.apply(pos, true);
		return found == null ? StationSnapshot.EMPTY : found;
	}
}
