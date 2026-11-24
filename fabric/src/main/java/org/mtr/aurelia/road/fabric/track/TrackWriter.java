/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.track;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.SideEffect;
import org.mtr.aurelia.road.undo.SideEffect.TrackRail;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.TwoPositionsBase;
import org.mtr.core.operation.RailsRequest;
import org.mtr.core.operation.RailsResponse;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.data.RailType;
import org.mtr.mod.packet.PacketDeleteData;
import org.mtr.mod.packet.PacketUpdateData;

/**
 * The roads' hidden MTR track on the server, through MTR's own data path. Stratos had this in a second mod; inside ATR
 * the roads and MTR are one mod, so it is wired in directly ({@link org.mtr.aurelia.road.fabric.apply.SideEffects}).
 * Stratos calls it once an operation's blocks are placed (or put back), so the lane nodes are already there, joined.
 * <ul>
 *   <li><b>First it asks MTR</b> which of the rails exist: one {@code "rails"} query by id ({@code Init.sendMessageC2S}).
 *       MTR answers a few ticks later; with no answer within {@value #ANSWER_SECONDS} s, every rail asked about is
 *       {@code FAILED}.</li>
 *   <li><b>Making</b>, on the server thread: a rail already there is a player's ({@code ALREADY_THERE}: left alone and
 *       never owned, as one sent with the same id would replace it). A lane whose ends aren't MTR node blocks (a road
 *       laid with another node aid, such as an old road's blue wool) is {@code SKIPPED}: it can't have track, which is
 *       not a failure. Otherwise, with both node blocks facing the lane's way, {@code Rail.newRail} as MTR's connector
 *       builds one: the angles from {@code Rail.getAngles} given each node block, the road's vertical shape and radius,
 *       <b>no styles</b> (hidden track), the speed from the lane's start node in slot 1 and from its end in slot 2, and
 *       the flags of MTR's connector at the nearest speed at or below the lane's ({@link RailType}). Then
 *       {@code PacketUpdateData.sendDirectlyToServerRail}; MTR sends it to the players itself.</li>
 *   <li><b>Removing:</b> a rail gone already is {@code NOT_THERE}; so is one that isn't the rail the road made
 *       ({@link #ours}): a player who laid their own rail between the same two nodes replaced ours under the same id, and
 *       theirs is left. Player rails can have no styles either, so the check is the whole rail: no styles, the lane's
 *       speeds and the road's flags. Ours goes with {@code PacketDeleteData.sendDirectlyToServerRailId}; MTR resets any
 *       node block still there itself.</li>
 *   <li><b>Then it asks MTR again</b> about the rails it sent, and answers only once MTR has: made means MTR has it,
 *       removed means it doesn't. MTR resets a freed node when its answer to the removal comes back, so without this a
 *       redo right after an undo could have its joined nodes reset under it. A made rail MTR doesn't confirm in time is
 *       counted as made, so undo still tries to remove it (removing one that isn't there is harmless); a removal it
 *       doesn't confirm is {@code FAILED}, and the rail stays the road's.</li>
 * </ul>
 * Called on the server thread; the answer completes there too.
 */
public final class TrackWriter implements SideEffectWriter {
    /** How long MTR has to answer a rails query. */
    static final long ANSWER_SECONDS = 5;

    private final MinecraftServer server;

    public TrackWriter(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public CompletableFuture<List<Result>> make(String dimension, List<SideEffect> effects) {
        return run(dimension, effects, true);
    }

    @Override
    public CompletableFuture<List<Result>> remove(String dimension, List<SideEffect> effects) {
        return run(dimension, effects, false);
    }

    private CompletableFuture<List<Result>> run(String dimension, List<SideEffect> effects, boolean making) {
        Result[] results = new Result[effects.size()];
        Map<Integer, TrackRail> rails = new LinkedHashMap<>();
        for (int i = 0; i < effects.size(); i++) {
            if (effects.get(i) instanceof TrackRail rail) {
                rails.put(i, rail);
            } else {
                results[i] = failed("only track is built, not " + effects.get(i));
            }
        }
        ServerWorld level = level(dimension);
        if (level == null) {
            rails.keySet().forEach(i -> results[i] = failed("this server has no dimension " + dimension));
            rails.clear();
        }
        if (rails.isEmpty()) {
            return CompletableFuture.completedFuture(Arrays.asList(results));
        }
        List<String> ids = rails.values().stream().map(TrackWriter::id).distinct().toList();
        Map<Integer, TrackRail> sent = new LinkedHashMap<>();
        return query(level, ids).thenComposeAsync(found -> {
            for (Map.Entry<Integer, TrackRail> entry : rails.entrySet()) {
                TrackRail rail = entry.getValue();
                try {
                    if (found == null) {
                        results[entry.getKey()] = failed("MTR didn't answer within " + ANSWER_SECONDS + " s");
                        continue;
                    }
                    Result result = making ? make(level, rail, found.get(id(rail))) : remove(level, rail, found.get(id(rail)));
                    if (result == null) {
                        sent.put(entry.getKey(), rail);
                    } else {
                        results[entry.getKey()] = result;
                    }
                } catch (RuntimeException e) {
                    StratosMod.LOGGER.error("[stratos] {} rail {} failed", making ? "making" : "removing", rail, e);
                    results[entry.getKey()] = failed(e.toString());
                }
            }
            if (sent.isEmpty()) {
                return CompletableFuture.completedFuture(Arrays.asList(results));
            }
            // MTR answers in the order it is asked, so once it answers this, what was sent is done (and freed nodes reset).
            return query(level, sent.values().stream().map(TrackWriter::id).distinct().toList()).thenApplyAsync(after -> {
                sent.forEach((index, rail) -> results[index] = confirm(rail, after, making));
                return Arrays.asList(results);
            }, this.server);
        }, this.server);
    }

    /** What MTR's second answer {@code after} (null after the time limit) says about {@code rail}, made or removed. */
    private static Result confirm(TrackRail rail, Map<String, Rail> after, boolean making) {
        if (after == null) {
            if (making) {
                StratosMod.LOGGER.warn("[stratos] MTR didn't confirm rail {} within {} s; it was sent, so it counts as made", id(rail), ANSWER_SECONDS);
                return Result.DONE;
            }
            return failed("MTR didn't confirm the removal within " + ANSWER_SECONDS + " s");
        }
        Rail there = after.get(id(rail));
        if (making) {
            return there != null && ours(there, rail) ? Result.DONE : failed("MTR didn't keep the rail it was sent");
        }
        return there == null ? Result.DONE : failed("MTR still has the rail after removing it");
    }

    /** Sends {@code rail} to MTR (null: to be confirmed) unless {@code there} (MTR's rail with its id) exists. Server thread. */
    private static Result make(ServerWorld level, TrackRail rail, Rail there) {
        if (there != null) {
            return new Result(Outcome.ALREADY_THERE, "a rail is already there (styles " + there.getStyles() + "): a player's, left alone");
        }
        if (!isNode(level, rail.ax(), rail.ay(), rail.az()) || !isNode(level, rail.bx(), rail.by(), rail.bz())) {
            return new Result(Outcome.SKIPPED, "an end isn't an MTR node block (a road laid with another node aid)");
        }
        String problem = nodeProblem(level, rail.ax(), rail.ay(), rail.az(), rail.aFacing());
        if (problem == null) {
            problem = nodeProblem(level, rail.bx(), rail.by(), rail.bz(), rail.bFacing());
        }
        if (problem != null) {
            return failed(problem);
        }
        RailType type = railType(rail.speed());
        Position a = new Position(rail.ax(), rail.ay(), rail.az());
        Position b = new Position(rail.bx(), rail.by(), rail.bz());
        var angles = Rail.getAngles(a, angle(level, rail.ax(), rail.ay(), rail.az()), b, angle(level, rail.bx(), rail.by(), rail.bz()));
        Rail.Shape shape = rail.shape() == TrackRail.Shape.TWO_RADII ? Rail.Shape.TWO_RADII : Rail.Shape.QUADRATIC;
        Rail made = Rail.newRail(a, angles.left(), b, angles.right(), shape, rail.radius(), new ObjectArrayList<>(), rail.speedFromA(),
                rail.speedFromB(), false, false, type.canAccelerate, false, type.hasSignal, TransportMode.TRAIN);
        if (made == null || !made.isValid()) {
            return failed("MTR's newRail made no valid rail");
        }
        connect(level, rail.ax(), rail.ay(), rail.az());
        connect(level, rail.bx(), rail.by(), rail.bz());
        PacketUpdateData.sendDirectlyToServerRail(new org.mtr.mapping.holder.ServerWorld(level), made);
        return null;
    }

    /**
     * Marks the node block at {@code (x, y, z)} joined ({@code is_connected}), as MTR's connector does when it joins two
     * nodes. The road's plan places its nodes joined already, but MTR resets a node when the last rail on it is removed:
     * an edited road keeps its start nodes while its old rails go first, and an undo keeps them too. Only that one
     * property changes, and only the players are told (no neighbour updates).
     */
    private static void connect(ServerWorld level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        Property<?> property = state.getBlock().getStateManager().getProperty("is_connected");
        if (property instanceof BooleanProperty connected && !state.get(connected)) {
            level.setBlockState(pos, state.with(connected, true), Block.NOTIFY_LISTENERS);
        }
    }

    /** Sends the removal of our {@code rail}, MTR's {@code there} (null: to be confirmed), unless it is gone or a player's now. Server thread. */
    private static Result remove(ServerWorld level, TrackRail rail, Rail there) {
        if (there == null) {
            return new Result(Outcome.NOT_THERE, "MTR has no rail there now");
        }
        if (!ours(there, rail)) {
            return new Result(Outcome.NOT_THERE, "the rail there now isn't the one the road made (styles " + there.getStyles()
                    + ", " + there.getSpeedLimitKilometersPerHour(false) + "/" + there.getSpeedLimitKilometersPerHour(true)
                    + " km/h): a player's, left alone");
        }
        PacketDeleteData.sendDirectlyToServerRailId(new org.mtr.mapping.holder.ServerWorld(level), id(rail));
        return null;
    }

    /** Asks MTR which of {@code ids} it has; the answer maps each found id to its rail, or is null after the time limit. */
    private CompletableFuture<Map<String, Rail>> query(ServerWorld level, List<String> ids) {
        CompletableFuture<Map<String, Rail>> answer = new CompletableFuture<>();
        RailsRequest request = new RailsRequest();
        ids.forEach(request::addRailId);
        try {
            Init.sendMessageC2S(OperationProcessor.RAILS, new org.mtr.mapping.holder.MinecraftServer(this.server), new org.mtr.mapping.holder.World(level),
                    request, (RailsResponse response) -> {
                        Map<String, Rail> found = new HashMap<>();
                        response.getRails().forEach(rail -> found.put(rail.getHexId(), rail));
                        answer.complete(found);
                    }, RailsResponse.class);
        } catch (RuntimeException e) {
            answer.completeExceptionally(e);
        }
        return answer.completeOnTimeout(null, ANSWER_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Whether MTR's {@code there} is the rail the road made for {@code rail}, as {@link #make} sends it: no styles, the
     * lane's two speeds (either way round: MTR may store the ends swapped) and the flags of its {@link RailType}.
     */
    static boolean ours(Rail there, TrackRail rail) {
        RailType type = railType(rail.speed());
        long one = there.getSpeedLimitKilometersPerHour(false);
        long two = there.getSpeedLimitKilometersPerHour(true);
        boolean speeds = one == rail.speedFromA() && two == rail.speedFromB() || one == rail.speedFromB() && two == rail.speedFromA();
        return there.getStyles().isEmpty() && speeds && there.canAccelerate() == type.canAccelerate && !there.isPlatform()
                && !there.isSiding() && !there.canTurnBack() && there.getTransportMode() == TransportMode.TRAIN;
    }

    private static boolean isNode(ServerWorld level, int x, int y, int z) {
        return MtrNodes.isNode(BlockArgumentParser.stringifyBlockState(level.getBlockState(new BlockPos(x, y, z))));
    }

    /** Why the block at {@code (x, y, z)} can't be one end of a rail: not an MTR node, or facing another way; or null. */
    private static String nodeProblem(ServerWorld level, int x, int y, int z, int facing) {
        String state = BlockArgumentParser.stringifyBlockState(level.getBlockState(new BlockPos(x, y, z)));
        if (!MtrNodes.isNode(state)) {
            return String.format(Locale.ROOT, "no MTR node at %d %d %d (%s)", x, y, z, state);
        }
        OptionalInt found = MtrNodes.facing(state);
        if (found.isEmpty() || found.getAsInt() != facing) {
            return String.format(Locale.ROOT, "the MTR node at %d %d %d faces another way (%s)", x, y, z, state);
        }
        return null;
    }

    private static float angle(ServerWorld level, int x, int y, int z) {
        return BlockNode.getAngle(new org.mtr.mapping.holder.BlockState(level.getBlockState(new BlockPos(x, y, z))));
    }

    /**
     * The rail type of MTR's connector nearest {@code speed} km/h at or below it (the slowest below every one), for its
     * flags: a lane's speed need not be one of MTR's connector speeds.
     */
    static RailType railType(int speed) {
        RailType best = null;
        RailType slowest = null;
        for (RailType type : RailType.values()) {
            // MTR's plain train connectors, WOODEN to DIAMOND; not platforms, sidings, cable cars or runways
            if (type.ordinal() > RailType.DIAMOND.ordinal() || !type.canAccelerate || type.railShape != Rail.Shape.QUADRATIC) {
                continue;
            }
            if (slowest == null || type.speedLimit < slowest.speedLimit) {
                slowest = type;
            }
            if (type.speedLimit <= speed && (best == null || type.speedLimit > best.speedLimit)) {
                best = type;
            }
        }
        return best != null ? best : slowest;
    }

    /** MTR's id for {@code rail}: from its two node positions, so the same two nodes always give the same id. */
    static String id(TrackRail rail) {
        return TwoPositionsBase.getHexId(new Position(rail.ax(), rail.ay(), rail.az()), new Position(rail.bx(), rail.by(), rail.bz()));
    }

    private ServerWorld level(String dimension) {
        Identifier location = Identifier.tryParse(dimension);
        if (location == null) {
            return null;
        }
        return this.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, location));
    }

    private static Result failed(String reason) {
        return new Result(Outcome.FAILED, reason);
    }
}
