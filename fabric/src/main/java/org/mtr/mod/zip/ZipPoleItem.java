package org.mtr.mod.zip;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;

/** Places a pole, or links the pole that was clicked to the previous one. */
public final class ZipPoleItem extends BlockItem {

	public ZipPoleItem(Settings settings) {
		super(ZipContent.POLE, settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getWorld().getBlockState(context.getBlockPos()).isOf(ZipContent.POLE)) {
			return ZipLines.link(context.getPlayer(), context.getWorld(), context.getBlockPos());
		}
		if (context.getPlayer() != null && !ZipLines.mayBuild(context.getPlayer())) {
			if (!context.getWorld().isClient()) {
				ZipLines.tell(context.getPlayer(), "message.mtr.zip.creative");
			}
			return ActionResult.FAIL;
		}
		return super.useOnBlock(context);
	}

	@Override
	protected boolean canPlace(ItemPlacementContext context, net.minecraft.block.BlockState state) {
		return context.getPlayer() != null && ZipLines.mayBuild(context.getPlayer()) && super.canPlace(context, state);
	}
}
