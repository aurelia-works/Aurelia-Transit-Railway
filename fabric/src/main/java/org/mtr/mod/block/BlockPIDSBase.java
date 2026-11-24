package org.mtr.mod.block;

import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockExtension;
import org.mtr.mapping.mapper.BlockWithEntity;
import org.mtr.mapping.mapper.DirectionHelper;
import org.mtr.mod.Blocks;
import org.mtr.mod.Init;
import org.mtr.mod.packet.PacketOpenPIDSConfigScreen;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

public abstract class BlockPIDSBase extends BlockExtension implements DirectionHelper, BlockWithEntity {

	public final int maxArrivals;
	public final BiPredicate<World, BlockPos> canStoreData;
	public final BiFunction<World, BlockPos, BlockPos> getBlockPosWithData;

	public BlockPIDSBase(int maxArrivals, BiPredicate<World, BlockPos> canStoreData, BiFunction<World, BlockPos, BlockPos> getBlockPosWithData) {
		super(Blocks.createDefaultBlockSettings(true, blockState -> 5).nonOpaque());
		this.maxArrivals = maxArrivals;
		this.canStoreData = canStoreData;
		this.getBlockPosWithData = getBlockPosWithData;
	}

	@Nonnull
	@Override
	public ActionResult onUse2(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		final BlockPos dataBlockPos = getBlockPosWithData.apply(world, pos);
		final BlockEntity dataEntity = world.getBlockEntity(dataBlockPos);
		if (dataEntity != null && dataEntity.data instanceof BlockEntityBase) {
			final ActionResult dyeResult = PIDSColorHelper.applyDye(world, dataBlockPos, player, (BlockEntityBase) dataEntity.data);
			if (dyeResult != null) {
				return dyeResult;
			}
		}

		return IBlock.checkHoldingBrush(world, player, () -> {
			final BlockPos newBlockPos = getBlockPosWithData.apply(world, pos);
			final BlockEntity entity = world.getBlockEntity(newBlockPos);
			if (entity != null && entity.data instanceof BlockEntityBase) {
				((BlockEntityBase) entity.data).markDirty2();
				Init.REGISTRY.sendPacketToClient(ServerPlayerEntity.cast(player), new PacketOpenPIDSConfigScreen(newBlockPos, ((BlockEntityBase) entity.data).maxArrivals));
			}
		});
	}

	public static abstract class BlockEntityBase extends BlockEntityExtension {

		public final int maxArrivals;
		public final BiPredicate<World, BlockPos> canStoreData;
		public final BiFunction<World, BlockPos, BlockPos> getBlockPosWithData;

		private final String[] messages;
		private final boolean[] hideArrivalArray;
		private final LongAVLTreeSet platformIds = new LongAVLTreeSet();
		private int displayPage;
		@Nullable
		private Integer customColor;
		private static final String KEY_MESSAGE = "message";
		private static final String KEY_HIDE_ARRIVAL = "hide_arrival";
		private static final String KEY_PLATFORM_IDS = "platform_ids";
		private static final String KEY_DISPLAY_PAGE = "display_page";
		private static final String KEY_CUSTOM_COLOR = "custom_color";

		public BlockEntityBase(int maxArrivals, BiPredicate<World, BlockPos> canStoreData, BiFunction<World, BlockPos, BlockPos> getBlockPosWithData, BlockEntityType<?> type, BlockPos pos, BlockState state) {
			super(type, pos, state);
			this.maxArrivals = maxArrivals;
			this.canStoreData = canStoreData;
			this.getBlockPosWithData = getBlockPosWithData;
			messages = new String[maxArrivals];
			for (int i = 0; i < maxArrivals; i++) {
				messages[i] = "";
			}
			hideArrivalArray = new boolean[maxArrivals];
		}

		@Override
		public void readCompoundTag(CompoundTag compoundTag) {
			for (int i = 0; i < maxArrivals; i++) {
				messages[i] = compoundTag.getString(KEY_MESSAGE + i);
				hideArrivalArray[i] = compoundTag.getBoolean(KEY_HIDE_ARRIVAL + i);
			}

			platformIds.clear();
			final long[] platformIdsArray = compoundTag.getLongArray(KEY_PLATFORM_IDS);
			for (final long platformId : platformIdsArray) {
				platformIds.add(platformId);
			}

			displayPage = compoundTag.getInt(KEY_DISPLAY_PAGE);
			customColor = PIDSColorHelper.parse(compoundTag.getString(KEY_CUSTOM_COLOR));
		}

		@Override
		public void writeCompoundTag(CompoundTag compoundTag) {
			for (int i = 0; i < maxArrivals; i++) {
				compoundTag.putString(KEY_MESSAGE + i, messages[i] == null ? "" : messages[i]);
				compoundTag.putBoolean(KEY_HIDE_ARRIVAL + i, hideArrivalArray[i]);
			}
			compoundTag.putLongArray(KEY_PLATFORM_IDS, new ArrayList<>(platformIds));
			compoundTag.putInt(KEY_DISPLAY_PAGE, displayPage);
			compoundTag.putString(KEY_CUSTOM_COLOR, PIDSColorHelper.serialize(customColor));
		}

		public void setData(String[] messages, boolean[] hideArrivalArray, LongAVLTreeSet platformIds, int displayPage) {
			System.arraycopy(messages, 0, this.messages, 0, Math.min(messages.length, this.messages.length));
			System.arraycopy(hideArrivalArray, 0, this.hideArrivalArray, 0, Math.min(hideArrivalArray.length, this.hideArrivalArray.length));
			this.platformIds.clear();
			this.platformIds.addAll(platformIds);
			this.displayPage = displayPage;
			markDirty2();
		}

		public int getDisplayPage() {
			return displayPage;
		}

		public LongAVLTreeSet getPlatformIds() {
			return platformIds;
		}

		public String getMessage(int index) {
			if (index >= 0 && index < maxArrivals) {
				if (messages[index] == null) {
					messages[index] = "";
				}
				return messages[index];
			} else {
				return "";
			}
		}

		public boolean getHideArrival(int index) {
			if (index >= 0 && index < maxArrivals) {
				return hideArrivalArray[index];
			} else {
				return false;
			}
		}

		/**
		 * The colour a dye put on this PIDS, or null to use the block's own text colours.
		 */
		@Nullable
		public Integer getCustomColor() {
			return customColor;
		}

		public abstract boolean showArrivalNumber();

		public abstract boolean alternateLines();

		public abstract int textColorArrived();

		public abstract int textColor();
	}

	/**
	 * Dyeing and un-dyeing a PIDS. The colour is saved with the display's other data, so it survives a reload.
	 */
	static class PIDSColorHelper {

		/**
		 * Applies a held dye or a milk bucket, or returns null when the player is holding neither, so the caller can fall
		 * back to the brush. Called on both sides; only the server changes the block entity.
		 */
		@Nullable
		static ActionResult applyDye(World world, BlockPos blockPos, PlayerEntity player, BlockEntityBase blockEntity) {
			for (final Hand hand : Hand.values()) {
				final ItemStack itemStack = player.getStackInHand(hand);
				final net.minecraft.item.Item item = itemStack.getItem().data;
				if (item instanceof net.minecraft.item.DyeItem dyeItem) {
					if (!world.isClient()) {
						blockEntity.customColor = convert(dyeItem.getColor());
						blockEntity.markDirty2();
						world.playSound(null, blockPos, new SoundEvent(net.minecraft.sound.SoundEvents.ITEM_DYE_USE), SoundCategory.BLOCKS, 1, 1);
						if (!player.isCreative()) {
							itemStack.decrement(1);
						}
					}
					return ActionResult.SUCCESS;
				} else if (item == net.minecraft.item.Items.MILK_BUCKET) {
					if (!world.isClient()) {
						blockEntity.customColor = null;
						blockEntity.markDirty2();
						world.playSound(null, blockPos, new SoundEvent(net.minecraft.sound.SoundEvents.ITEM_BUCKET_FILL_TADPOLE), SoundCategory.BLOCKS, 1, 1);
						if (!player.isCreative()) {
							player.data.setStackInHand(hand.data, new net.minecraft.item.ItemStack(net.minecraft.item.Items.BUCKET));
						}
					}
					return ActionResult.SUCCESS;
				}
			}
			return null;
		}

		/**
		 * The RGB colour a dye gives a PIDS, tuned so the text stays readable on the dark screen.
		 */
		static int convert(net.minecraft.util.DyeColor dyeColor) {
			switch (dyeColor) {
				case WHITE:
					return 0xF9FFFE;
				case ORANGE:
					return 0xFF9900;
				case MAGENTA:
					return 0xEB4CDF;
				case LIGHT_BLUE:
					return 0x4DB3F9;
				case YELLOW:
					return 0xFED83D;
				case LIME:
					return 0x98DB24;
				case PINK:
					return 0xF87CC4;
				case GRAY:
					return 0x6D7778;
				case LIGHT_GRAY:
					return 0xA9A9A2;
				case CYAN:
					return 0x09F6F6;
				case PURPLE:
					return 0xBE4EFB;
				case BLUE:
					return 0x6F79EC;
				case BROWN:
					return 0xB3652D;
				case GREEN:
					return 0x1FAD1F;
				case RED:
					return 0xFF5555;
				case BLACK:
				default:
					return 0x000000;
			}
		}

		/**
		 * Six hex digits, or an empty string when the PIDS has no custom colour.
		 */
		static String serialize(@Nullable Integer color) {
			return color == null ? "" : String.format("%06X", color);
		}

		/**
		 * Reads a colour written by {@link #serialize}, returning null (no custom colour) when the tag is missing.
		 */
		@Nullable
		static Integer parse(String string) {
			try {
				return Integer.parseInt(string, 16);
			} catch (NumberFormatException e) {
				return null;
			}
		}
	}
}
