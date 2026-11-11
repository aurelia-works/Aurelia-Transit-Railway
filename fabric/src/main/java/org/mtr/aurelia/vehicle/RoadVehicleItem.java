package org.mtr.aurelia.vehicle;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/** Places a road vehicle on the clicked block, facing the way the player looks. */
public final class RoadVehicleItem extends Item {

	private final RoadVehicleKind kind;

	public RoadVehicleItem(RoadVehicleKind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		final World world = context.getWorld();
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		final PlayerEntity player = context.getPlayer();
		if (!RoadVehicles.settings(false).enabled()) {
			if (player != null) {
				player.sendMessage(Text.translatable("message.mtr.road_vehicle.off"), true);
			}
			return ActionResult.FAIL;
		}
		final Vec3d at = context.getSide() == Direction.UP ? context.getHitPos() : Vec3d.ofBottomCenter(context.getBlockPos().offset(context.getSide()));
		final RoadVehicleEntity vehicle = new RoadVehicleEntity(RoadVehicles.ENTITY, world);
		vehicle.setKind(kind);
		vehicle.refreshPositionAndAngles(at.x, at.y, at.z, player == null ? 0 : player.getYaw(), 0);
		if (!world.isSpaceEmpty(vehicle, vehicle.getBoundingBox())) {
			if (player != null) {
				player.sendMessage(Text.translatable("message.mtr.road_vehicle.no_room"), true);
			}
			return ActionResult.FAIL;
		}
		world.spawnEntity(vehicle);
		world.emitGameEvent(player, GameEvent.ENTITY_PLACE, at);
		if (player == null || !player.getAbilities().creativeMode) {
			context.getStack().decrement(1);
		}
		return ActionResult.CONSUME;
	}
}
