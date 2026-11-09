package com.aureliatransit.architecture.network;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.InfoDisplayBlock;
import com.aureliatransit.architecture.block.NameSignBlock;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.InfoDisplayBlockEntity;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.world.StationActions;
import com.aureliatransit.architecture.station.world.StationBuildSettings;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import com.aureliatransit.architecture.station.world.WrenchMode;
import com.aureliatransit.architecture.text.ConfigurableTextData;
import com.aureliatransit.architecture.text.SignData;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import com.aureliatransit.architecture.wayfinding.WayfindingEditable;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds;
import com.aureliatransit.architecture.block.elevated.LiftStatusPanelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client-to-server configuration packets of the interactive content: sign text and information text, and the station wrench
 * screen. Every handler
 * decodes with bounded reads, then on the server thread checks permission and reach ({@link EditValidation}), the
 * block type and the block entity before applying re-sanitized data.
 */
public final class ModPackets {

	public static final Identifier UPDATE_SIGN = AureliaTransitArchitecture.id("update_sign");
	public static final Identifier UPDATE_INFO_TEXT = AureliaTransitArchitecture.id("update_info_text");
	public static final Identifier UPDATE_WAYFINDING = AureliaTransitArchitecture.id("update_wayfinding");
	/** A name sign row's look and mount: owner position + the look's block id + whether it hangs on a wall. */
	public static final Identifier UPDATE_NAME_SIGN_LOOK = AureliaTransitArchitecture.id("update_name_sign_look");
	/** Lift status panel status (A8): block position + status ordinal. */
	public static final Identifier UPDATE_LIFT_STATUS = AureliaTransitArchitecture.id("update_lift_status");
	/** Station wrench screen: hand + action + description; the server saves the description on the wrench and runs the action. */
	public static final Identifier STATION_WRENCH = AureliaTransitArchitecture.id("station_wrench");
	/** Station Studio (the browser page the client hosts): action + description + controls, run for the player without a wrench. */
	public static final Identifier STATION_STUDIO = AureliaTransitArchitecture.id("station_studio");
	/** Station build pace: blocks per tick, client asks and the server sends the current value (and on join). */
	public static final Identifier STATION_PACE = AureliaTransitArchitecture.id("station_pace");

	private ModPackets() {
	}

	public static PacketByteBuf writeSign(BlockPos pos, SignData data) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		data.write(buf);
		return buf;
	}

	public static PacketByteBuf writeNameSignLook(BlockPos owner, Identifier look, boolean wall) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(owner);
		buf.writeIdentifier(look);
		buf.writeBoolean(wall);
		return buf;
	}

	public static PacketByteBuf writeLiftStatus(BlockPos pos, ElevatedKinds.LiftStatus status) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeVarInt(status.ordinal());
		return buf;
	}

	public static PacketByteBuf writeInfoText(BlockPos pos, ConfigurableTextData text) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		text.write(buf);
		return buf;
	}

	public static PacketByteBuf writeWayfinding(BlockPos pos, WayfindingData data) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		data.write(buf);
		return buf;
	}

	public static PacketByteBuf writeStationWrench(Hand hand, StationWrenchItem.Action action, String description, String recipe) {
		return writeStationWrench(hand, action, description, recipe, WrenchMode.STATION);
	}

	public static PacketByteBuf writeStationWrench(Hand hand, StationWrenchItem.Action action, String description, String recipe, WrenchMode mode) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(hand.ordinal());
		buf.writeVarInt(action.ordinal());
		buf.writeString(description, StationDescriptionParser.MAX_INPUT);
		buf.writeString(recipe, StationWrenchItem.MAX_RECIPE);
		buf.writeVarInt(mode.ordinal());
		return buf;
	}

	public static PacketByteBuf writeStationStudio(StationWrenchItem.Action action, String description, String recipe) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(action.ordinal());
		buf.writeString(description, StationDescriptionParser.MAX_INPUT);
		buf.writeString(recipe, StationWrenchItem.MAX_RECIPE);
		return buf;
	}

	public static PacketByteBuf writeStationPace(int blocksPerTick) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(blocksPerTick);
		return buf;
	}

	/** Tells one player the server's current build pace (on join and after a change), when their client knows the channel. */
	public static void sendStationPace(ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, STATION_PACE)) {
			ServerPlayNetworking.send(player, STATION_PACE, writeStationPace(StationBuildSettings.blocksPerTick(player.getServer())));
		}
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(STATION_PACE, (server, player, handler, buf, responseSender) -> {
			final int pace;
			try {
				pace = buf.readVarInt();
			} catch (RuntimeException e) {
				return;
			}
			server.execute(() -> {
				if (!player.hasPermissionLevel(StationActions.PERMISSION_LEVEL)) {
					return;
				}
				StationBuildSettings.set(server, pace);
				for (final ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
					sendStationPace(online);
				}
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(STATION_STUDIO, (server, player, handler, buf, responseSender) -> {
			final StationWrenchItem.Action action;
			final String description;
			final String recipe;
			try {
				final int actionIndex = buf.readVarInt();
				if (actionIndex < 0 || actionIndex >= StationWrenchItem.Action.values().length) {
					return;
				}
				action = StationWrenchItem.Action.values()[actionIndex];
				description = buf.readString(StationDescriptionParser.MAX_INPUT);
				recipe = buf.readString(StationWrenchItem.MAX_RECIPE);
			} catch (RuntimeException e) {
				return;
			}
			server.execute(() -> StationWrenchItem.performFromStudio(player, action, description, recipe));
		});
		ServerPlayNetworking.registerGlobalReceiver(STATION_WRENCH, (server, player, handler, buf, responseSender) -> {
			final Hand hand;
			final StationWrenchItem.Action action;
			final String description;
			final String recipe;
			final WrenchMode mode;
			try {
				final int handIndex = buf.readVarInt();
				final int actionIndex = buf.readVarInt();
				if (handIndex < 0 || handIndex >= Hand.values().length || actionIndex < 0 || actionIndex >= StationWrenchItem.Action.values().length) {
					return;
				}
				hand = Hand.values()[handIndex];
				action = StationWrenchItem.Action.values()[actionIndex];
				description = buf.readString(StationDescriptionParser.MAX_INPUT);
				recipe = buf.readString(StationWrenchItem.MAX_RECIPE);
				final int modeIndex = buf.readVarInt();
				mode = modeIndex < 0 || modeIndex >= WrenchMode.values().length ? WrenchMode.STATION : WrenchMode.values()[modeIndex];
			} catch (RuntimeException e) {
				return;
			}
			server.execute(() -> StationWrenchItem.perform(player, hand, action, description, recipe, mode));
		});
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_WAYFINDING, (server, player, handler, buf, responseSender) -> {
			final BlockPos pos;
			final WayfindingData data;
			try {
				pos = buf.readBlockPos();
				data = WayfindingData.read(buf);
			} catch (RuntimeException e) {
				return;
			}
			server.execute(() -> {
				final ServerWorld world = player.getServerWorld();
				if (world.isChunkLoaded(pos) && EditValidation.canEdit(player, pos) && world.getBlockEntity(pos) instanceof WayfindingEditable editable) {
					editable.setWayfinding(data);
				}
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_SIGN, (server, player, handler, buf, responseSender) -> {
			final BlockPos pos = buf.readBlockPos();
			final SignData data;
			try {
				data = SignData.read(buf);
			} catch (RuntimeException e) {
				return; // malformed or oversized payload: ignore
			}
			server.execute(() -> applySign(player, pos, data));
		});
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_NAME_SIGN_LOOK, (server, player, handler, buf, responseSender) -> {
			final BlockPos pos = buf.readBlockPos();
			final Identifier look;
			try {
				look = buf.readIdentifier();
			} catch (RuntimeException e) {
				return;
			}
			final boolean wall = buf.readBoolean();
			server.execute(() -> applyNameSignLook(player, pos, look, wall));
		});
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_LIFT_STATUS, (server, player, handler, buf, responseSender) -> {
			final BlockPos pos = buf.readBlockPos();
			final ElevatedKinds.LiftStatus status = ElevatedKinds.LiftStatus.byOrdinal(buf.readVarInt());
			server.execute(() -> applyLiftStatus(player, pos, status));
		});
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_INFO_TEXT, (server, player, handler, buf, responseSender) -> {
			final BlockPos pos = buf.readBlockPos();
			final ConfigurableTextData text;
			try {
				text = ConfigurableTextData.read(buf);
			} catch (RuntimeException e) {
				return;
			}
			server.execute(() -> applyInfoText(player, pos, text));
		});
	}

	private static void applySign(ServerPlayerEntity player, BlockPos pos, SignData data) {
		final ServerWorld world = player.getServerWorld();
		if (!world.isChunkLoaded(pos)) {
			return;
		}
		final BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof TextSignBlock block && isRowInReach(player, block, state, pos)
				&& world.getBlockEntity(pos) instanceof TextSignBlockEntity sign) {
			sign.setData(data);
		}
	}

	private static void applyNameSignLook(ServerPlayerEntity player, BlockPos pos, Identifier look, boolean wall) {
		final ServerWorld world = player.getServerWorld();
		if (!world.isChunkLoaded(pos)) {
			return;
		}
		final BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof NameSignBlock block && isRowInReach(player, block, state, pos)
				&& Registries.BLOCK.get(look) instanceof NameSignBlock target) {
			NameSignBlock.restyle(world, pos, target, wall);
		}
	}

	private static void applyLiftStatus(ServerPlayerEntity player, BlockPos pos, ElevatedKinds.LiftStatus status) {
		final ServerWorld world = player.getServerWorld();
		if (!world.isChunkLoaded(pos) || !EditValidation.canEdit(player, pos)) {
			return;
		}
		final BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof LiftStatusPanelBlock && state.get(LiftStatusPanelBlock.STATUS) != status) {
			world.setBlockState(pos, state.with(LiftStatusPanelBlock.STATUS, status));
		}
	}

	private static void applyInfoText(ServerPlayerEntity player, BlockPos pos, ConfigurableTextData text) {
		final ServerWorld world = player.getServerWorld();
		if (!world.isChunkLoaded(pos) || !EditValidation.canEdit(player, pos)) {
			return;
		}
		if (world.getBlockState(pos).getBlock() instanceof InfoDisplayBlock && world.getBlockEntity(pos) instanceof InfoDisplayBlockEntity display) {
			display.setText(text);
		}
	}

	/**
	 * The player may edit a joined row from any of its blocks, so reach and permission are checked against each block of the row.
	 */
	private static boolean isRowInReach(ServerPlayerEntity player, TextSignBlock block, BlockState ownerState, BlockPos owner) {
		BlockPos current = owner;
		BlockState currentState = ownerState;
		for (int i = 0; i < TextSignBlock.MAX_ROW; i++) {
			if (EditValidation.canEdit(player, current)) {
				return true;
			}
			if (!currentState.get(TextSignBlock.RIGHT)) {
				return false;
			}
			current = current.offset(TextSignBlock.localRight(currentState.get(TextSignBlock.FACING)));
			if (!player.getServerWorld().isChunkLoaded(current)) {
				return false;
			}
			currentState = player.getServerWorld().getBlockState(current);
			if (!currentState.isOf(block)) {
				return false;
			}
		}
		return false;
	}
}
