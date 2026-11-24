package com.aureliatransit.architecture.bridge.world;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.BridgeMemberBlock;
import com.aureliatransit.architecture.block.entity.BridgeMemberBlockEntity;
import com.aureliatransit.architecture.bridge.BridgeStampPlan;
import com.aureliatransit.architecture.bridge.span.RailBridgePlanner;
import com.aureliatransit.architecture.bridge.span.SpanSettings;
import com.aureliatransit.architecture.bridge.span.SpanStyle;
import com.aureliatransit.architecture.registry.ElevatedBlocks;
import com.aureliatransit.architecture.station.StylePalette;
import com.aureliatransit.architecture.station.world.StationActions;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import com.aureliatransit.architecture.station.world.WorldSiteProbe;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The wrench's bridge builder: in Bridge mode, click a rail's two nodes and everything the rail needs is built along it, bridges
 * in the style on the wrench (or one picked per gap), tunnels with portals, cuttings, fills and the overhead line. Building on
 * the same rail again first takes down what the last build put there, so changing the style and clicking again restyles it.
 * The choices live on the wrench stack; the wrench screen's Bridge page sets them.
 */
public final class RailBridges {

	/** Client to server: the wrench screen's bridge choices for the wrench in a hand. */
	public static final Identifier SETTINGS_PACKET = AureliaTransitArchitecture.id("bridge_span_settings");
	private static final String SETTINGS_KEY = "BridgeSpan";
	private static final String FROM_KEY = "BridgeFrom";
	private static final String FROM_WORLD_KEY = "BridgeFromWorld";

	private RailBridges() {
	}

	// ---- choices on the stack ---------------------------------------------------------------------------------------

	public static SpanSettings settings(ItemStack stack) {
		final NbtCompound nbt = stack.getSubNbt(SETTINGS_KEY);
		if (nbt == null) {
			return SpanSettings.DEFAULT;
		}
		final SpanSettings d = SpanSettings.DEFAULT;
		return new SpanSettings(SpanStyle.of(nbt.getString("Style")),
				nbt.contains("Half") ? nbt.getInt("Half") : d.halfWidth(),
				nbt.contains("Piers") ? nbt.getInt("Piers") : d.pierSpacing(),
				!nbt.contains("Masts") || nbt.getBoolean("Masts"),
				nbt.contains("MastSpacing") ? nbt.getInt("MastSpacing") : d.mastSpacing(),
				nbt.contains("Towers") ? nbt.getDouble("Towers") : d.towerScale(),
				!nbt.contains("Portals") || nbt.getBoolean("Portals"));
	}

	public static void setSettings(ItemStack stack, SpanSettings settings) {
		final NbtCompound nbt = stack.getOrCreateSubNbt(SETTINGS_KEY);
		nbt.putString("Style", settings.style().id());
		nbt.putInt("Half", settings.halfWidth());
		nbt.putInt("Piers", settings.pierSpacing());
		nbt.putBoolean("Masts", settings.masts());
		nbt.putInt("MastSpacing", settings.mastSpacing());
		nbt.putDouble("Towers", settings.towerScale());
		nbt.putBoolean("Portals", settings.portals());
	}

	public static PacketByteBuf write(Hand hand, SpanSettings settings) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(hand.ordinal());
		buf.writeString(settings.style().id(), 32);
		buf.writeVarInt(settings.halfWidth());
		buf.writeVarInt(settings.pierSpacing());
		buf.writeBoolean(settings.masts());
		buf.writeVarInt(settings.mastSpacing());
		buf.writeDouble(settings.towerScale());
		buf.writeBoolean(settings.portals());
		return buf;
	}

	/** Server: saves the screen's choices on the wrench in the player's hand (operators and creative mode only, as every wrench action). */
	public static void registerPackets() {
		ServerPlayNetworking.registerGlobalReceiver(SETTINGS_PACKET, (server, player, handler, buf, responseSender) -> {
			final Hand hand;
			final SpanSettings settings;
			try {
				final int index = buf.readVarInt();
				if (index < 0 || index >= Hand.values().length) {
					return;
				}
				hand = Hand.values()[index];
				settings = new SpanSettings(SpanStyle.of(buf.readString(32)), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
						buf.readDouble(), buf.readBoolean());
			} catch (RuntimeException e) {
				return;
			}
			server.execute(() -> {
				final ItemStack stack = player.getStackInHand(hand);
				if (stack.getItem() instanceof StationWrenchItem && StationActions.allowed(player)) {
					setSettings(stack, settings);
				}
			});
		});
	}

	/** Sneak-use in Bridge mode: the next style, shown in the action bar. */
	public static void cycleStyle(ServerPlayerEntity player, ItemStack stack) {
		final SpanSettings settings = settings(stack);
		final SpanStyle next = settings.style().next();
		setSettings(stack, settings.withStyle(next));
		player.sendMessage(Text.literal("Bridge style: " + next.label() + (next == SpanStyle.AUTO ? " (picked per gap)" : "")).formatted(Formatting.AQUA), true);
	}

	// ---- clicks -----------------------------------------------------------------------------------------------------

	/** A click on a block in Bridge mode: the first and second node of the rail to build along. */
	public static void click(ServerPlayerEntity player, ItemStack stack, BlockPos pos) {
		final ServerWorld world = player.getServerWorld();
		if (!Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString().equals(StylePalette.MTR_RAIL_NODE)) {
			player.sendMessage(Text.literal("Click a rail node: the block at either end of the rail to build along.").formatted(Formatting.YELLOW), true);
			return;
		}
		final NbtCompound nbt = stack.getOrCreateNbt();
		final String dimension = world.getRegistryKey().getValue().toString();
		if (!nbt.contains(FROM_KEY) || !dimension.equals(nbt.getString(FROM_WORLD_KEY))) {
			nbt.putLong(FROM_KEY, pos.asLong());
			nbt.putString(FROM_WORLD_KEY, dimension);
			player.sendMessage(Text.literal("First rail node set. Now click the rail's other node.").formatted(Formatting.GRAY), true);
			return;
		}
		final BlockPos from = BlockPos.fromLong(nbt.getLong(FROM_KEY));
		nbt.remove(FROM_KEY);
		nbt.remove(FROM_WORLD_KEY);
		if (from.equals(pos)) {
			player.sendMessage(Text.literal("That is the same node. Click the first node again to start over.").formatted(Formatting.YELLOW), true);
			return;
		}
		build(player, from, pos, settings(stack));
	}

	private static void build(ServerPlayerEntity player, BlockPos from, BlockPos to, SpanSettings settings) {
		final ServerWorld world = player.getServerWorld();
		final UUID owner = player.getUuid();
		if (BridgeSessions.hasJob(owner)) {
			player.sendMessage(Text.literal("A bridge task is already running; wait for it to finish.").formatted(Formatting.RED), false);
			return;
		}
		final String rail = railKey(world, from, to);
		BridgeRails.getRail(world, player, from, to, found -> {
			final ServerPlayerEntity current = world.getServer().getPlayerManager().getPlayer(owner);
			if (current == null || current.getServerWorld() != world) {
				return;
			}
			final RailBridgeStore store = RailBridgeStore.of(world);
			final List<BridgeBuildJob.Entry> old = store.get(rail);
			// the last build's cells are surveyed as the ground they replaced, so a restyle plans on the real site
			final Map<BlockPos, BlockState> under = new HashMap<>();
			for (final BridgeBuildJob.Entry entry : old) {
				if (world.getBlockState(entry.pos()).equals(entry.after())) {
					under.put(entry.pos(), entry.before());
				}
			}
			final BridgeStampPlan.Path path = BridgeRails.path(found);
			final WorldSiteProbe probe = WorldSiteProbe.under(world, under);
			final RailBridgePlanner.Result result;
			try {
				result = RailBridgePlanner.plan(path, probe, settings);
			} finally {
				probe.release();
			}
			final Map<String, Integer> palette = new LinkedHashMap<>();
			final List<BridgeStampPlan.Placement> placements = new ArrayList<>();
			final Map<BlockPos, NbtCompound> entities = new HashMap<>();
			final List<RailBridgePlanner.Cell> cells = new ArrayList<>(result.cells());
			cells.sort(Comparator.comparingInt(RailBridgePlanner.Cell::y));
			for (final RailBridgePlanner.Cell cell : cells) {
				placements.add(new BridgeStampPlan.Placement(cell.x(), cell.y(), cell.z(), palette.computeIfAbsent(cell.block(), k -> palette.size()), 0));
			}
			for (final RailBridgePlanner.Anchor anchor : result.anchors()) {
				final BlockState state = ElevatedBlocks.BRIDGE_MEMBER.getDefaultState().with(BridgeMemberBlock.FULL, anchor.skin() != null);
				final int index = palette.computeIfAbsent(BridgeStateCodec.encode(state), k -> palette.size());
				placements.add(new BridgeStampPlan.Placement(anchor.x(), anchor.y(), anchor.z(), index, 0));
				entities.put(new BlockPos(anchor.x(), anchor.y(), anchor.z()), BridgeMemberBlockEntity.encode(anchor.skin(), anchor.members()));
			}
			if (placements.size() > BridgeStampPlan.MAX_PLACEMENTS) {
				current.sendMessage(Text.literal("This rail would need more than " + BridgeStampPlan.MAX_PLACEMENTS + " blocks; build it a shorter rail at a time.").formatted(Formatting.RED), false);
				return;
			}
			final List<String> states = List.copyOf(palette.keySet());
			final BridgeBuildJob job = new BridgeBuildJob(world, owner, placements, states, entities, entries -> RailBridgeStore.of(world).put(rail, entries));
			final List<BridgeTask> tasks = new ArrayList<>();
			if (!under.isEmpty()) {
				tasks.add(new BridgeUndoJob(world, owner, old));
			}
			tasks.add(job);
			if (!BridgeSessions.start(owner, new ChainTask(tasks))) {
				current.sendMessage(Text.literal("A bridge task is already running.").formatted(Formatting.RED), false);
				return;
			}
			current.sendMessage(Text.literal(summary(result, !under.isEmpty())).formatted(Formatting.GRAY), false);
		});
	}

	private static String summary(RailBridgePlanner.Result result, boolean restyle) {
		final StringBuilder text = new StringBuilder(restyle ? "Rebuilding this rail: " : "Building along this rail: ");
		boolean first = true;
		for (final RailBridgePlanner.Stretch stretch : result.stretches()) {
			if (stretch.kind() == RailBridgePlanner.Kind.GROUND) {
				continue;
			}
			text.append(first ? "" : ", ").append(switch (stretch.kind()) {
				case BRIDGE -> stretch.style().label().toLowerCase(java.util.Locale.ROOT) + " bridge";
				case TUNNEL -> "tunnel";
				case CUTTING -> "cutting";
				case FILL -> "fill";
				default -> "";
			}).append(" (").append(Math.round(stretch.length())).append(" blocks)");
			first = false;
		}
		if (first) {
			text.append("the rail is on the ground all the way; only the overhead line");
		}
		if (result.unloaded() > 0) {
			text.append(". Some columns were in unloaded chunks and were left out");
		}
		return text.append('.').toString();
	}

	/** The rail by its two nodes, either way round, in its dimension. */
	private static String railKey(ServerWorld world, BlockPos a, BlockPos b) {
		final long first = Math.min(a.asLong(), b.asLong());
		final long second = Math.max(a.asLong(), b.asLong());
		return world.getRegistryKey().getValue() + "|" + first + "|" + second;
	}
}
