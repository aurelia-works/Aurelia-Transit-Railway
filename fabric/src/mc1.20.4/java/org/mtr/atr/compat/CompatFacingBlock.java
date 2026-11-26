package org.mtr.atr.compat;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The Minecraft 1.20.4 side of {@code src/mc1.20.1/java}'s {@code CompatFacingBlock}. */
public abstract class CompatFacingBlock extends HorizontalFacingBlock {

	protected CompatFacingBlock(Settings settings) {
		super(settings);
	}

	/** Minecraft reads block codecs only for its own blocks; this one stands for the registered block itself. */
	@Override
	protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
		return MapCodec.unit(this);
	}

	@Override
	public final BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		beforeBreak(world, pos, state, player);
		return super.onBreak(world, pos, state, player);
	}

	/** Runs as a player starts breaking the block, before vanilla's handling. */
	protected void beforeBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
	}
}
