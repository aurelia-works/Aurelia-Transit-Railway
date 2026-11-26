package org.mtr.mod.block;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.tool.HolderBase;
import org.mtr.mod.generated.lang.TranslationProvider;

import javax.annotation.Nonnull;
import java.util.List;

public class BlockPIDSPole extends BlockPoleCheckBase {

	public BlockPIDSPole(BlockSettings blockSettings) {
		super(blockSettings);
	}

	@Nonnull
	@Override
	public VoxelShape getOutlineShape2(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return IBlock.getVoxelShapeByDirection(7, 0, 12, 9, 16, 14, IBlock.getStatePropertySafe(state, FACING));
	}

	@Override
	protected boolean isBlock(Block block) {
		return block.data instanceof BlockPIDSHorizontalBase || block.data instanceof BlockPIDSPole;
	}

	@Override
	protected Text getTooltipBlockText() {
		return TranslationProvider.BLOCK_MTR_PIDS_1.getText();
	}

	@Override
	public void addBlockProperties(List<HolderBase<?>> properties) {
		properties.add(FACING);
	}
}
