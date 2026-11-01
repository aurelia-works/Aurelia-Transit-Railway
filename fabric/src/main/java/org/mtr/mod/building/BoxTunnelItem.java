package org.mtr.mod.building;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public final class BoxTunnelItem extends Item {

	public BoxTunnelItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getPlayer() == null) {
			return ActionResult.PASS;
		}
		return BuildTools.markTunnel(context.getPlayer(), context.getWorld(), context.getBlockPos(), context.getPlayer().isSneaking());
	}

	@Override
	public TypedActionResult<net.minecraft.item.ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
		final ActionResult result = BuildTools.undoTunnel(user, world);
		return new TypedActionResult<>(result, user.getStackInHand(hand));
	}
}
