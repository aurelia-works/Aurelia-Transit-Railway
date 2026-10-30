package org.mtr.mod.block;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.TextFormatting;
import org.mtr.mapping.holder.TooltipContext;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.holder.ActionResult;
import org.mtr.mapping.holder.BlockHitResult;
import org.mtr.mapping.holder.BlockView;
import org.mtr.mapping.holder.Hand;
import org.mtr.mod.BlockEntityTypes;
import org.mtr.mod.Init;
import org.mtr.mod.coupling.Coupling.Mode;
import org.mtr.mod.coupling.CouplingService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Designates the station it sits in. A brush cycles off, split, and couple. Sneak and brush chooses how many cars stay
 * on the front of a split. The siding's saved train is unchanged until a train is actually stopped in that station.
 */
public class BlockTrainCoupler extends BlockTrainSensorBase {

	@Nonnull
	@Override
	public ActionResult onUse2(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		return IBlock.checkHoldingBrush(world, player, () -> {
			final org.mtr.mapping.holder.BlockEntity entity = world.getBlockEntity(pos);
			if (entity != null && entity.data instanceof BlockEntity) {
				final BlockEntity coupler = (BlockEntity) entity.data;
				if (player.isSneaking()) {
					coupler.cycleKeep();
				} else {
					coupler.cycleMode();
				}
				player.sendMessage(new org.mtr.mapping.holder.Text(coupler.message().data), true);
			}
		});
	}

	@Override
	public void onBreak2(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		CouplingService.forget(Init.getWorldId(world), pos);
		super.onBreak2(world, pos, state, player);
	}

	@Override
	public void addTooltips(ItemStack stack, @Nullable BlockView world, List<MutableText> tooltip, TooltipContext options) {
		tooltip.add(TextHelper.translatable("tooltip.block.mtr.train_coupler").formatted(TextFormatting.GRAY));
	}

	@Nonnull
	@Override
	public BlockEntityExtension createBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new BlockEntity(blockPos, blockState);
	}

	public static class BlockEntity extends BlockEntityBase {

		private Mode mode = Mode.NONE;
		private int keep;
		private static final String KEY_MODE = "coupler_mode";
		private static final String KEY_KEEP = "coupler_keep";

		public BlockEntity(BlockPos pos, BlockState state) {
			super(BlockEntityTypes.TRAIN_COUPLER.get(), pos, state);
		}

		@Override
		public void blockEntityTick() {
			final World world = getWorld2();
			if (world == null || world.isClient()) {
				return;
			}
			CouplingService.watch(Init.getWorldId(world), getPos2(), mode, keep);
		}

		@Override
		public void readCompoundTag(CompoundTag compoundTag) {
			mode = modeOf(compoundTag.getString(KEY_MODE));
			keep = compoundTag.getInt(KEY_KEEP);
			super.readCompoundTag(compoundTag);
		}

		@Override
		public void writeCompoundTag(CompoundTag compoundTag) {
			compoundTag.putString(KEY_MODE, mode.name());
			compoundTag.putInt(KEY_KEEP, keep);
			super.writeCompoundTag(compoundTag);
		}

		public void cycleMode() {
			if (mode == Mode.NONE) {
				mode = Mode.SPLIT;
			} else if (mode == Mode.SPLIT) {
				mode = Mode.COUPLE;
			} else {
				mode = Mode.NONE;
			}
			markDirty2();
		}

		public void cycleKeep() {
			keep = (keep + 1) % 9;
			markDirty2();
		}

		public Mode getMode() {
			return mode;
		}

		public int getKeep() {
			return keep;
		}

		private MutableText message() {
			if (mode == Mode.SPLIT) {
				return TextHelper.translatable(keep <= 0 ? "gui.mtr.train_coupler.split_half" : "gui.mtr.train_coupler.split", keep);
			}
			if (mode == Mode.COUPLE) {
				return TextHelper.translatable("gui.mtr.train_coupler.couple");
			}
			return TextHelper.translatable("gui.mtr.train_coupler.off");
		}

		private static Mode modeOf(String name) {
			if ("SPLIT".equals(name)) {
				return Mode.SPLIT;
			}
			if ("COUPLE".equals(name)) {
				return Mode.COUPLE;
			}
			return Mode.NONE;
		}
	}
}
