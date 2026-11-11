package org.mtr.aurelia.vehicle;

import com.aureliatransit.architecture.block.elevated.BicycleRackBlock;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Bike share on the station's bike racks: right-click a full rack to take its bike and ride off; get off next to an empty
 * rack and the bike goes back into it. Hired bikes never drop as items, so a rack is the only place bikes come from.
 */
public final class BikeShare {

	/** How far from the bike an empty rack takes it back, in blocks. */
	private static final int RETURN_REACH = 2;

	private BikeShare() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			final BlockPos pos = hit.getBlockPos();
			final BlockState state = world.getBlockState(pos);
			if (hand != Hand.MAIN_HAND || player.isSpectator() || player.shouldCancelInteraction() || player.hasVehicle()
					|| !(state.getBlock() instanceof BicycleRackBlock) || !state.get(BicycleRackBlock.BIKE)) {
				return ActionResult.PASS;
			}
			return world.isClient() ? ActionResult.SUCCESS : hire(player, world, pos, state);
		});
	}

	private static ActionResult hire(PlayerEntity player, World world, BlockPos rack, BlockState state) {
		if (!RoadVehicles.settings(false).bikeShare()) {
			player.sendMessage(Text.translatable("message.mtr.bike_share.off"), true);
			return ActionResult.FAIL;
		}
		// Out of the back of the rack first (racks stand front to front in pairs), else out of the front.
		final Direction facing = state.get(BicycleRackBlock.FACING);
		for (final Direction side : new Direction[]{facing.getOpposite(), facing}) {
			final RoadVehicleEntity bike = new RoadVehicleEntity(RoadVehicles.ENTITY, world);
			bike.setKind(RoadVehicleKind.BICYCLE);
			bike.hireFrom(rack);
			final Vec3d at = Vec3d.ofBottomCenter(rack.offset(side));
			bike.refreshPositionAndAngles(at.x, at.y, at.z, side.asRotation(), 0);
			if (world.isSpaceEmpty(bike, bike.getBoundingBox()) && !world.isSpaceEmpty(bike, bike.getBoundingBox().offset(0, -0.2, 0))) {
				world.setBlockState(rack, state.with(BicycleRackBlock.BIKE, false));
				world.spawnEntity(bike);
				player.startRiding(bike);
				player.sendMessage(Text.translatable("message.mtr.bike_share.hired"), true);
				return ActionResult.CONSUME;
			}
		}
		player.sendMessage(Text.translatable("message.mtr.bike_share.no_room"), true);
		return ActionResult.FAIL;
	}

	/** After its rider gets off: back into the nearest empty rack in reach, if there is one. */
	static void dock(RoadVehicleEntity bike, @Nullable Entity rider) {
		final BlockPos rack = nearestEmptyRack(bike.getWorld(), bike.getBlockPos());
		if (rack == null) {
			if (rider instanceof PlayerEntity player) {
				player.sendMessage(Text.translatable("message.mtr.bike_share.not_docked"), true);
			}
			return;
		}
		refill(bike, rack);
		if (rider instanceof PlayerEntity player) {
			player.sendMessage(Text.translatable("message.mtr.bike_share.returned"), true);
		}
	}

	/** A hired bike broken up: back to the rack it came from if that rack is still empty, else it is gone. */
	static void recall(RoadVehicleEntity bike, @Nullable BlockPos home) {
		if (home != null && isEmptyRack(bike.getWorld(), home)) {
			refill(bike, home);
		} else {
			bike.discard();
		}
	}

	private static void refill(RoadVehicleEntity bike, BlockPos rack) {
		final World world = bike.getWorld();
		world.setBlockState(rack, world.getBlockState(rack).with(BicycleRackBlock.BIKE, true));
		bike.discard();
	}

	@Nullable
	private static BlockPos nearestEmptyRack(World world, BlockPos centre) {
		for (final BlockPos pos : BlockPos.iterateOutwards(centre, RETURN_REACH, 1, RETURN_REACH)) {
			if (isEmptyRack(world, pos)) {
				return pos.toImmutable();
			}
		}
		return null;
	}

	private static boolean isEmptyRack(World world, BlockPos pos) {
		final BlockState state = world.getBlockState(pos);
		return state.getBlock() instanceof BicycleRackBlock && !state.get(BicycleRackBlock.BIKE);
	}
}
