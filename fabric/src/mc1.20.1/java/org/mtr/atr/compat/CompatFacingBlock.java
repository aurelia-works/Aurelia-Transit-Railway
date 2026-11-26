package org.mtr.atr.compat;

import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * {@link HorizontalFacingBlock} for both Minecraft versions. 1.20.4 wants a block codec and has {@code onBreak} return the
 * state, so blocks put their break handling in {@link #beforeBreak} and the 1.20.4 side of this class supplies the codec.
 */
public abstract class CompatFacingBlock extends HorizontalFacingBlock {

	protected CompatFacingBlock(Settings settings) {
		super(settings);
	}

	@Override
	public final void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		beforeBreak(world, pos, state, player);
		super.onBreak(world, pos, state, player);
	}

	/** Runs as a player starts breaking the block, before vanilla's handling. */
	protected void beforeBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
	}
}
