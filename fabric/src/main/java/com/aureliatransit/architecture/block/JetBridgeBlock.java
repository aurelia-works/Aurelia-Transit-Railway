package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.airport.AircraftSample;
import com.aureliatransit.architecture.block.entity.JetBridgeBlockEntity;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Supplier;

/**
 * The docking head of a boarding bridge. The walkway behind it is ordinary blocks; this one grows a tunnel toward the
 * aircraft while the plane is stopped with its doors open, and pulls the tunnel back when the plane leaves. The
 * extension is computed on the client from the vehicles the client already has, so a dedicated server does not tick it
 * and nothing is written to the chunk.
 */
public class JetBridgeBlock extends FacingShapedBlock implements BlockEntityProvider {

	/** Installed by the client initializer. Empty on a dedicated server. */
	public static Supplier<List<AircraftSample>> samples = List::of;

	public JetBridgeBlock(Settings settings, Placement placement, VoxelShape northOutline) {
		super(settings, placement, northOutline);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new JetBridgeBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (!world.isClient || type != ModBlockEntities.JET_BRIDGE) {
			return null;
		}
		return (BlockEntityTicker<T>) (world1, pos, state1, blockEntity) -> ((JetBridgeBlockEntity) blockEntity).clientTick();
	}
}
