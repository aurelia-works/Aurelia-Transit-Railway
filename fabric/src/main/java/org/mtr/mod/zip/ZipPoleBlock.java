package org.mtr.mod.zip;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class ZipPoleBlock extends Block {

	private static final VoxelShape POLE = VoxelShapes.cuboid(0.4375, 0, 0.4375, 0.5625, 1, 0.5625);

	public ZipPoleBlock(Settings settings) {
		super(settings);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return POLE;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		final ItemStack stack = player.getStackInHand(hand);
		if (stack.isOf(ZipContent.HANDLE)) {
			return ZipLines.ride(player, world, pos);
		}
		if (stack.isOf(ZipContent.SEAT)) {
			return ZipLines.placeSeat(player, world, pos, hand);
		}
		if (stack.isEmpty()) {
			return ZipLines.boardSeat(player, world, pos);
		}
		return ActionResult.PASS;
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && !world.isClient()) {
			ZipLines.removePole(world, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
