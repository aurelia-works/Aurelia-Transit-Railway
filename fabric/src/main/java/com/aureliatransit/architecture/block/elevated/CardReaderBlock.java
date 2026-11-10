package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.CardReaderKind;
import com.aureliatransit.architecture.block.mtr.MtrFareContract;
import com.aureliatransit.architecture.fare.CardTaps;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import static net.minecraft.block.Block.createCuboidShape;

/**
 * Fare card reader / validator (A10, NS check-in pole). Right-click it (empty hand or a transit card) to tap: MTR's fare
 * system charges the entry or exit fare as for its own ticket barrier, and on success the {@link CheckInLaneBlock check-in
 * lanes} beside the reader open for the passenger. Without MTR's fare system it is a prop.
 */
public class CardReaderBlock extends FacingKindBlock<CardReaderKind> {

	public static final EnumProperty<CardReaderKind> KIND = EnumProperty.of("kind", CardReaderKind.class);

	public CardReaderBlock(Settings settings) {
		super(settings, "card_reader", Placement.TOWARD_PLAYER, CardReaderKind.class, kind -> switch (kind) {
			case POST -> VoxelShapes.union(createCuboidShape(6.5, 0, 6.5, 9.5, 11, 9.5), createCuboidShape(5, 11, 5, 11, 16, 11));
			case WALL -> createCuboidShape(5, 5, 12, 11, 12, 16);
		});
	}

	@Override
	protected EnumProperty<CardReaderKind> kindProperty() {
		return KIND;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		final ActionResult styled = super.onUse(state, world, pos, player, hand, hit);
		if (styled != ActionResult.PASS || hand != Hand.MAIN_HAND || !MtrFareContract.available()
				|| player.getStackInHand(hand).getItem() instanceof BlockItem) {
			return styled;
		}
		if (!world.isClient) {
			final BlockPos reader = pos.toImmutable();
			MtrFareContract.passThrough(world, reader, player, opened -> {
				if (opened) {
					CheckInLaneBlock.openBeside(world, reader, player);
					if (player instanceof ServerPlayerEntity serverPlayer) {
						CardTaps.onPass(serverPlayer, reader.getX() + " " + reader.getY() + " " + reader.getZ());
					}
				}
			});
		}
		return ActionResult.success(world.isClient);
	}
}
