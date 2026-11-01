package com.aureliatransit.architecture.bridge.world;

import com.aureliatransit.architecture.bridge.BridgeStampPlan;
import com.aureliatransit.architecture.bridge.BridgeTemplate;
import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.world.BlockStateResolver;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /atr_bridge}: capture a bridge section from the world, list or delete templates, preview a template along a rail,
 * build it and undo the last build. Operators only (level 2), like the other ATA building commands.
 *
 * <p>The rail is named by two of its MTR rail nodes, the same two clicks the Bridge Creator takes. Capture uses an
 * explicit origin block in the box, normally the rail block at the section's centre, so the template knows where the
 * rail runs through it.
 */
final class BridgeCommand {

	private BridgeCommand() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("atr_bridge").requires(source -> source.hasPermissionLevel(2))
				.executes(BridgeCommand::help)
				.then(CommandManager.literal("help").executes(BridgeCommand::help))
				.then(CommandManager.literal("capture")
						.then(CommandManager.argument("name", StringArgumentType.word())
								.then(CommandManager.argument("from", BlockPosArgumentType.blockPos())
										.then(CommandManager.argument("to", BlockPosArgumentType.blockPos())
												.then(CommandManager.argument("origin", BlockPosArgumentType.blockPos())
														.executes(context -> capture(context, null))
														.then(CommandManager.argument("facing", StringArgumentType.word())
																.executes(context -> capture(context, StringArgumentType.getString(context, "facing")))))))))
				.then(CommandManager.literal("build")
						.then(CommandManager.argument("name", StringArgumentType.word())
								.then(CommandManager.argument("from", BlockPosArgumentType.blockPos())
										.then(CommandManager.argument("to", BlockPosArgumentType.blockPos())
												.executes(context -> start(context, false, 0))
												.then(CommandManager.argument("height", IntegerArgumentType.integer(-64, 64))
														.executes(context -> start(context, false, IntegerArgumentType.getInteger(context, "height"))))))))
				.then(CommandManager.literal("preview")
						.then(CommandManager.argument("name", StringArgumentType.word())
								.then(CommandManager.argument("from", BlockPosArgumentType.blockPos())
										.then(CommandManager.argument("to", BlockPosArgumentType.blockPos())
												.executes(context -> start(context, true, 0))
												.then(CommandManager.argument("height", IntegerArgumentType.integer(-64, 64))
														.executes(context -> start(context, true, IntegerArgumentType.getInteger(context, "height"))))))))
				.then(CommandManager.literal("undo").executes(BridgeCommand::undo))
				.then(CommandManager.literal("list").executes(BridgeCommand::list))
				.then(CommandManager.literal("delete")
						.then(CommandManager.argument("name", StringArgumentType.word()).executes(BridgeCommand::delete))));
	}

	private static int help(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		source.sendFeedback(() -> Text.literal("Bridge templates"), false);
		source.sendFeedback(() -> Text.literal("/atr_bridge capture <name> <from> <to> <origin> [north|east|south|west]"), false);
		source.sendFeedback(() -> Text.literal("  Capture the box, with the origin block at the rail level and centre."), false);
		source.sendFeedback(() -> Text.literal("/atr_bridge preview <name> <rail node> <rail node> [height]"), false);
		source.sendFeedback(() -> Text.literal("/atr_bridge build <name> <rail node> <rail node> [height]"), false);
		source.sendFeedback(() -> Text.literal("/atr_bridge undo | list | delete <name>"), false);
		source.sendFeedback(() -> Text.literal("Templates live in " + BridgeTemplateStore.config().root() + ".").formatted(Formatting.GRAY), false);
		return 1;
	}

	private static int capture(CommandContext<ServerCommandSource> context, String facingText) {
		final ServerCommandSource source = context.getSource();
		final ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			error(source, "Run this as a player standing near the bridge section.");
			return 0;
		}
		final Heading facing;
		if (facingText == null) {
			facing = BlockStateResolver.heading(player.getHorizontalFacing());
		} else {
			facing = heading(facingText);
			if (facing == null) {
				error(source, "Facing must be north, east, south or west.");
				return 0;
			}
		}
		try {
			final String name = BridgeTemplateStore.normalise(StringArgumentType.getString(context, "name"));
			final BlockPos from = BlockPosArgumentType.getBlockPos(context, "from");
			final BlockPos to = BlockPosArgumentType.getBlockPos(context, "to");
			final BlockPos origin = BlockPosArgumentType.getBlockPos(context, "origin");
			final BridgeCapture.Result result = BridgeCapture.capture(player.getServerWorld(), from, to, origin, facing);
			final BridgeTemplate template = result.template();
			BridgeTemplateStore.config().save(name, template);
			player.sendMessage(Text.literal("Saved bridge template '" + name + "': " + template.cells().size() + " blocks, "
					+ template.sizeX() + "x" + template.sizeY() + "x" + template.sizeZ() + ", facing " + facing.name().toLowerCase(Locale.ROOT) + "."), false);
			if (result.skippedRailNodes() + result.skippedBlockEntities() + result.skippedFluids() > 0) {
				player.sendMessage(Text.literal("Left out " + result.skippedRailNodes() + " rail nodes, "
						+ result.skippedBlockEntities() + " block entities and " + result.skippedFluids() + " fluids.").formatted(Formatting.GRAY), false);
			}
			return template.cells().size();
		} catch (IllegalArgumentException | IOException e) {
			error(source, e.getMessage() == null ? "Cannot capture that bridge section." : e.getMessage());
			return 0;
		}
	}

	private static int start(CommandContext<ServerCommandSource> context, boolean preview, int height) {
		final ServerCommandSource source = context.getSource();
		final ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			error(source, "Run this as a player standing near the rail.");
			return 0;
		}
		final String name;
		try {
			name = BridgeTemplateStore.normalise(StringArgumentType.getString(context, "name"));
		} catch (IllegalArgumentException e) {
			error(source, e.getMessage());
			return 0;
		}
		final BlockPos from = BlockPosArgumentType.getBlockPos(context, "from");
		final BlockPos to = BlockPosArgumentType.getBlockPos(context, "to");
		final BridgeTemplate template;
		try {
			template = BridgeTemplateStore.config().load(name);
		} catch (IOException e) {
			error(source, e.getMessage());
			return 0;
		}
		if (!preview && BridgeSessions.hasJob(player.getUuid())) {
			error(source, "A bridge task is already running; wait for it to finish before starting another.");
			return 0;
		}
		final ServerWorld world = player.getServerWorld();
		final UUID owner = player.getUuid();
		BridgeRails.getRail(world, player, from, to, rail -> {
			final ServerPlayerEntity current = world.getServer().getPlayerManager().getPlayer(owner);
			if (current == null || current.getServerWorld() != world) {
				return;
			}
			final BridgeStampPlan plan;
			try {
				plan = BridgeStampPlan.of(template, BridgeRails.path(rail), 1.0, height);
			} catch (IllegalArgumentException e) {
				current.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
				return;
			}
			if (plan.size() == 0) {
				current.sendMessage(Text.literal("The template has no blocks to place.").formatted(Formatting.RED), false);
				return;
			}
			if (preview) {
				BridgeSessions.setPreview(new BridgePreview(owner, world.getRegistryKey(), plan));
				current.sendMessage(Text.literal("Showing " + plan.size() + " bridge blocks for " + BridgePreview.DURATION_TICKS / 20 + " s.").formatted(Formatting.GRAY), false);
			} else if (!BridgeSessions.start(owner, new BridgeBuildJob(world, owner, plan, template))) {
				current.sendMessage(Text.literal("A bridge task is already running.").formatted(Formatting.RED), false);
			}
		});
		source.sendFeedback(() -> Text.literal("Looking up the rail...").formatted(Formatting.GRAY), false);
		return 1;
	}

	private static int undo(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		final ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			error(source, "Run this as a player.");
			return 0;
		}
		if (BridgeSessions.hasJob(player.getUuid())) {
			error(source, "A bridge task is still running; wait for it to finish before undoing.");
			return 0;
		}
		final RegistryKey<World> dimension = BridgeSessions.undoDimension(player.getUuid());
		if (dimension != null && !dimension.equals(player.getServerWorld().getRegistryKey())) {
			error(source, "The last bridge was built in " + dimension.getValue() + "; go there to undo it.");
			return 0;
		}
		if (!BridgeSessions.undo(player.getServerWorld(), player.getUuid())) {
			error(source, "Nothing to undo, or the last bridge changed nothing.");
			return 0;
		}
		source.sendFeedback(() -> Text.literal("Undoing the last bridge...").formatted(Formatting.GRAY), false);
		return 1;
	}

	private static int list(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		try {
			final BridgeTemplateStore store = BridgeTemplateStore.config();
			final List<String> names = store.list();
			if (names.isEmpty()) {
				source.sendFeedback(() -> Text.literal("No bridge templates yet. Capture one with /atr_bridge capture.").formatted(Formatting.GRAY), false);
			} else {
				source.sendFeedback(() -> Text.literal("Bridge templates: " + String.join(", ", names)), false);
			}
			return names.size();
		} catch (IOException e) {
			error(source, "Cannot read the template folder: " + e.getMessage());
			return 0;
		}
	}

	private static int delete(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		try {
			final String name = BridgeTemplateStore.normalise(StringArgumentType.getString(context, "name"));
			if (BridgeTemplateStore.config().delete(name)) {
				source.sendFeedback(() -> Text.literal("Deleted bridge template '" + name + "'."), false);
				return 1;
			}
			error(source, "No bridge template named '" + name + "'.");
			return 0;
		} catch (IllegalArgumentException | IOException e) {
			error(source, e.getMessage());
			return 0;
		}
	}

	private static Heading heading(String text) {
		try {
			return Heading.valueOf(text.strip().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static void error(ServerCommandSource source, String message) {
		source.sendError(Text.literal(message).formatted(Formatting.RED));
	}
}
