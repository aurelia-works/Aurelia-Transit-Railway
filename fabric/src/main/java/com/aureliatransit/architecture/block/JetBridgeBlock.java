package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.airport.AircraftSample;
import com.aureliatransit.architecture.block.entity.JetBridgeBlockEntity;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

import java.util.List;
import java.util.function.Supplier;

/**
 * The docking head of a boarding bridge. The walkway behind it is ordinary blocks; this one grows a tunnel toward the
 * aircraft while the plane is stopped with its doors open, and pulls the tunnel back when the plane leaves. The
 * extension is computed on the client from the vehicles the client already has. The renderer advances it while the cab is
 * on screen, so the block has no ticker, a dedicated server does not run it, and nothing is written to the chunk.
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
}
