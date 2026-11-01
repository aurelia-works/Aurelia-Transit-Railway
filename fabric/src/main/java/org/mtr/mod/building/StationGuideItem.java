package org.mtr.mod.building;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;

public final class StationGuideItem extends Item {

	public StationGuideItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getPlayer() == null) {
			return ActionResult.PASS;
		}
		return BuildTools.markStation(context.getPlayer(), context.getWorld(), context.getBlockPos(), context.getPlayer().isSneaking());
	}
}
