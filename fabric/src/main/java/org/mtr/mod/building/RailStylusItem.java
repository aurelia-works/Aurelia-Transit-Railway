package org.mtr.mod.building;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;

public final class RailStylusItem extends Item {

	public RailStylusItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getPlayer() == null) {
			return ActionResult.PASS;
		}
		return BuildTools.drawRail(context.getPlayer(), context.getWorld(), context.getBlockPos(), context.getPlayer().isSneaking());
	}
}
