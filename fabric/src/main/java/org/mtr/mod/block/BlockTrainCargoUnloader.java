package org.mtr.mod.block;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.BlockEntityTypes;
import org.mtr.mod.cargo.CargoMachines;
import org.mtr.mod.cargo.CargoService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class BlockTrainCargoUnloader extends BlockTrainSensorBase {

	@Nonnull
	@Override
	public BlockEntityExtension createBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new BlockEntity(blockPos, blockState);
	}

	@Override
	public void addTooltips(ItemStack stack, @Nullable BlockView world, List<MutableText> tooltip, TooltipContext options) {
		tooltip.add(TextHelper.translatable("tooltip." + stack.getItem().getTranslationKey()).formatted(TextFormatting.GRAY));
	}

	public static class BlockEntity extends BlockEntityBase {

		private int transferTicks;

		public BlockEntity(BlockPos pos, BlockState state) {
			super(BlockEntityTypes.TRAIN_CARGO_UNLOADER.get(), pos, state);
		}

		@Override
		public void blockEntityTick() {
			if (++transferTicks < CargoMachines.CYCLE_TICKS) {
				return;
			}
			transferTicks = 0;
			CargoService.transfer(this, false);
		}
	}
}
