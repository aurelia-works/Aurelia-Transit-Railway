package com.aureliatransit.architecture.earthworks.world;

import com.aureliatransit.architecture.earthworks.EarthJob;
import com.aureliatransit.architecture.earthworks.EarthLook;
import com.aureliatransit.architecture.earthworks.EarthPlanner;
import com.aureliatransit.architecture.earthworks.EarthTool;
import com.aureliatransit.architecture.station.world.BlockStateResolver;
import com.aureliatransit.architecture.station.world.StationActions;
import com.aureliatransit.architecture.station.world.StationBuildJob;
import com.aureliatransit.architecture.station.world.StationSessions;
import com.aureliatransit.architecture.station.world.WorldSiteProbe;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Earthworks mode of the wrench. A wrench click on a block sets point A, the next one point B (a third starts again at
 * A). {@code /atr_earthworks build <tool> [width height look]}, which the wrench screen sends, plans the job a slice per tick
 * as the player's site task and then hands the changes to a {@link StationBuildJob}: it builds bottom up over time, and
 * {@code /atr_earthworks undo} (the station undo) puts the ground back, even after a restart.
 */
public final class EarthworksCommand {

	/** The two clicked points, in the world they were clicked in. */
	private record Points(RegistryKey<World> world, BlockPos a, BlockPos b) {
	}

	private static final Map<UUID, Points> POINTS = new HashMap<>();

	private EarthworksCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		final String[] tools = Arrays.stream(EarthTool.values()).map(EarthTool::id).toArray(String[]::new);
		final String[] looks = Arrays.stream(EarthLook.values()).map(EarthLook::id).toArray(String[]::new);
		dispatcher.register(CommandManager.literal("atr_earthworks").requires(source -> source.hasPermissionLevel(StationActions.PERMISSION_LEVEL))
				.then(CommandManager.literal("build")
						.then(CommandManager.argument("tool", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(tools, builder))
								.executes(context -> build(context, 0, 0, null))
								.then(CommandManager.argument("width", IntegerArgumentType.integer(1, EarthJob.MAX_WIDTH))
										.then(CommandManager.argument("height", IntegerArgumentType.integer(EarthJob.MIN_HEIGHT, EarthJob.MAX_HEIGHT))
												.executes(context -> build(context, IntegerArgumentType.getInteger(context, "width"),
														IntegerArgumentType.getInteger(context, "height"), null))
												.then(CommandManager.argument("look", StringArgumentType.word())
														.suggests((context, builder) -> CommandSource.suggestMatching(looks, builder))
														.executes(context -> build(context, IntegerArgumentType.getInteger(context, "width"),
																IntegerArgumentType.getInteger(context, "height"),
																StringArgumentType.getString(context, "look"))))))))
				.then(CommandManager.literal("points").executes(EarthworksCommand::points))
				.then(CommandManager.literal("clear").executes(context -> {
					final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
					POINTS.remove(player.getUuid());
					player.sendMessage(Text.literal("Earthworks points cleared; click point A with the wrench.").formatted(Formatting.GRAY), true);
					return 1;
				}))
				.then(CommandManager.literal("undo").executes(context -> StationActions.undo(context.getSource().getPlayerOrThrow()) ? 1 : 0)));
	}

	/** A wrench click in Earthworks mode: sets A, then B; a click after both are set starts again at A. */
	public static void point(ServerPlayerEntity player, BlockPos pos) {
		final UUID id = player.getUuid();
		final Points old = POINTS.get(id);
		final RegistryKey<World> world = player.getServerWorld().getRegistryKey();
		if (old == null || old.b() != null || !old.world().equals(world)) {
			POINTS.put(id, new Points(world, pos.toImmutable(), null));
			player.sendMessage(Text.literal("Earthworks point A: " + where(pos) + ". Click point B.").formatted(Formatting.AQUA), true);
			return;
		}
		POINTS.put(id, new Points(world, old.a(), pos.toImmutable()));
		final double dx = pos.getX() - old.a().getX();
		final double dz = pos.getZ() - old.a().getZ();
		final double length = Math.sqrt(dx * dx + dz * dz);
		player.sendMessage(Text.literal("Earthworks point B: " + where(pos) + " (" + Math.round(length) + " blocks from A, "
				+ (pos.getY() - old.a().getY()) + " up). Open the wrench to pick a tool and build.").formatted(Formatting.AQUA), true);
	}

	public static void forget(UUID player) {
		POINTS.remove(player);
	}

	private static int points(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		final Points points = POINTS.get(player.getUuid());
		player.sendMessage(Text.literal(points == null ? "No earthworks points yet: click point A with the wrench in Earthworks mode."
				: "A: " + where(points.a()) + (points.b() == null ? "; B not set" : ", B: " + where(points.b()))), false);
		return 1;
	}

	private static int build(CommandContext<ServerCommandSource> context, int width, int height, String lookId) throws CommandSyntaxException {
		final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		if (!StationActions.allowed(player)) {
			return 0;
		}
		final EarthTool tool = EarthTool.byId(StringArgumentType.getString(context, "tool"));
		if (tool == null) {
			error(player, "No earthworks tool by that name; try level, slope, wall, cut_cover, bore or viaduct.");
			return 0;
		}
		final UUID id = player.getUuid();
		final Points points = POINTS.get(id);
		if (points == null || points.b() == null) {
			error(player, "Click two blocks with the wrench in Earthworks mode first: point A, then point B.");
			return 0;
		}
		final ServerWorld world = player.getServer().getWorld(points.world());
		if (world == null || player.getServerWorld() != world) {
			error(player, "Your earthworks points are in another dimension; go there or click new ones.");
			return 0;
		}
		if (StationSessions.job(id) != null || StationSessions.task(id) != null) {
			error(player, "A build or survey of yours is still running; /ata_station cancel stops it.");
			return 0;
		}
		final EarthJob job = new EarthJob(tool, point(points.a()), point(points.b()), width, height, EarthLook.byId(lookId));
		final String problem = job.problem();
		if (problem != null) {
			error(player, problem);
			return 0;
		}
		final Consumer<Text> out = StationSessions.messenger(player.getServer(), id);
		StationSessions.startTask(id, new PlanTask(world, id, job, out));
		return 1;
	}

	/** Plans the job a slice per tick, then starts its build. */
	private static final class PlanTask implements StationSessions.Task {

		private final ServerWorld world;
		private final UUID owner;
		private final EarthJob job;
		private final Consumer<Text> out;
		private final EarthPlanner planner;
		private final WorldSiteProbe probe;
		private int steps;

		PlanTask(ServerWorld world, UUID owner, EarthJob job, Consumer<Text> out) {
			this.world = world;
			this.owner = owner;
			this.job = job;
			this.out = out;
			planner = new EarthPlanner(job);
			probe = WorldSiteProbe.loading(world);
		}

		@Override
		public boolean step(long deadline) {
			if (!planner.step(probe, deadline)) {
				if (++steps % 20 == 0) {
					final ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
					if (player != null) {
						player.sendMessage(Text.literal("Surveying for " + label(job.tool()) + ": "
								+ Math.round(100.0 * planner.done() / Math.max(1, planner.total())) + "%"), true);
					}
				}
				return false;
			}
			probe.release();
			start(planner.result());
			return true;
		}

		@Override
		public void cancel() {
			probe.release();
		}

		private void start(EarthPlanner.Result result) {
			if (result.tooBig()) {
				out.accept(Text.literal("That job is too big (more than " + EarthPlanner.MAX_OPS + " block changes); do it in parts.").formatted(Formatting.RED));
				return;
			}
			if (result.ops().isEmpty()) {
				out.accept(Text.literal("Nothing to change: the ground already matches.").formatted(Formatting.YELLOW));
				return;
			}
			final BlockStateResolver.Resolution resolution = BlockStateResolver.resolve(result.ops());
			if (!resolution.ok()) {
				out.accept(Text.literal("Unknown blocks: " + String.join(", ", resolution.missing())).formatted(Formatting.RED));
				return;
			}
			final StationBuildJob build = StationBuildJob.build(world, owner, resolution, true, out, null);
			if (!StationSessions.start(build)) {
				out.accept(Text.literal("A build of yours is still running; /ata_station cancel stops it.").formatted(Formatting.RED));
				return;
			}
			// the undo log is now this job's: a station rebuild must not take it for the last station's
			StationSessions.forgetBuilt(owner);
			final int grade = job.gradeOneIn();
			out.accept(Text.literal("Building " + label(job.tool()) + ": " + result.ops().size() + " changes, bottom up"
					+ (job.tool().line() && grade > 0 ? ", grade 1 in " + grade : "")
					+ (result.skipped() == 0 ? "" : "; " + result.skipped() + " cells left alone (block entities, rail nodes, unbreakable)")
					+ (result.columnsLeft() == 0 ? "" : "; " + result.columnsLeft() + " columns not done (no ground in reach, too tall, or not under ground)")
					+ ". Undo puts it all back.").formatted(Formatting.GREEN));
		}
	}

	private static String label(EarthTool tool) {
		return switch (tool) {
			case LEVEL -> "level ground";
			case SLOPE -> "a slope";
			case WALL -> "a retaining wall";
			case CUT_COVER -> "a cut-and-cover tunnel";
			case BORE -> "a bored tunnel";
			case VIADUCT -> "a viaduct";
		};
	}

	private static EarthJob.Point point(BlockPos pos) {
		return new EarthJob.Point(pos.getX(), pos.getY(), pos.getZ());
	}

	private static String where(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private static void error(ServerPlayerEntity player, String message) {
		player.sendMessage(Text.literal(message).formatted(Formatting.RED), false);
	}
}
