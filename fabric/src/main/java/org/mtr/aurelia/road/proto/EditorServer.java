/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.mtr.aurelia.road.spi.BlockFinder;
import org.mtr.aurelia.road.ops.ApplyJob;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.Message.ApplyProgress;
import org.mtr.aurelia.road.proto.Message.ApplyState;
import org.mtr.aurelia.road.proto.Message.Hello;
import org.mtr.aurelia.road.proto.Message.HelloAck;
import org.mtr.aurelia.road.proto.Message.HelloResult;
import org.mtr.aurelia.road.proto.Message.Limits;
import org.mtr.aurelia.road.proto.Message.Notice;
import org.mtr.aurelia.road.proto.Message.PlanAccepted;
import org.mtr.aurelia.road.proto.Message.PlanRejected;
import org.mtr.aurelia.road.proto.Message.PlanRequest;
import org.mtr.aurelia.road.proto.Message.Redo;
import org.mtr.aurelia.road.proto.Message.StratosBlocks;
import org.mtr.aurelia.road.proto.Message.TrafficSide;
import org.mtr.aurelia.road.proto.Message.TaskRequest;
import org.mtr.aurelia.road.proto.Message.TaskResult;
import org.mtr.aurelia.road.proto.Message.Undo;
import org.mtr.aurelia.road.proto.Message.UndoRedoResult;
import org.mtr.aurelia.road.proto.Message.YourRoads;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.spi.MessageChannel;
import org.mtr.aurelia.road.spi.PermissionCheck;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.Replacement;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * The server's side of the {@code aurelia:roads} protocol (editor-protocol §3, §5), the same on a dedicated server and in
 * singleplayer:
 * <ol>
 *   <li><b>Handshake:</b> {@code Hello} when a player's client can receive the channel, then {@code HelloResult}; editing
 *       needs equal protocol numbers, and a world whose Stratos data isn't read-only (F-5: {@code WORLD_READ_ONLY}).
 *       Then {@code YourRoads}: the player's roads they can still undo, for the node aids (F-5).</li>
 *   <li><b>Plan requests:</b> the permission gate, then the module recomputes the plan from the parameters <i>off the
 *       server thread</i>, with a time limit ({@code PLAN_TOO_COMPLEX}). {@link PlanCheck} compares hashes; a match is
 *       applied through the player's {@link Operations} and reported with {@code PlanAccepted}, {@code ApplyProgress}
 *       about once a second, and a final {@code DONE} or {@code FAILED}.</li>
 *   <li><b>Undo and redo:</b> through the same {@link Operations}, answered with {@code UndoRedoResult}.</li>
 * </ol>
 * <b>Track (T-2):</b> {@code Hello} lists the {@code track} capability when the server can build hidden MTR track
 * ({@link ServerInfo#capabilities}). A plan with track sent to a server without it is refused with
 * {@code TRACK_UNAVAILABLE}, nothing placed. When an apply, undo or redo leaves track unbuilt or unremoved, its player
 * hears it in a {@code Notice} after the operation's own answer (owner, 2026-10-08), and the log gets the details.
 * A player has one request at a time ({@code BUSY}). Malformed records are dropped, logged and counted; disconnecting
 * repeat offenders comes with multiplayer hardening (Phase 2).
 *
 * <p><b>From F-2b</b> an accepted operation is followed through its {@link Operations.Handle}: progress comes from its job
 * once the helper has built it, and when an apply's blocks are all placed and the helper is filing them, one more
 * {@code APPLYING} report says every section is done (the client shows "Placed. Getting undo ready…") until
 * {@code DONE}.
 *
 * <p><b>From R-7</b> a plan may delete or rebuild a placed road ({@link PlanCheck.Decision#replacement}): it goes to
 * {@link Operations#replace}, and its {@code PlanAccepted} waits until its job has checked every block. If the job finds
 * blocks changed by hand and the player hasn't answered yet ({@code MANUAL_EDITS {count}}), or nothing to change
 * ({@code NOTHING_TO_CHANGE}), the request is refused with that code instead ({@code PlanRejected}), nothing written: the
 * tool then asks, and sends the answer in a new request.
 *
 * <p><b>From F-6</b> each plan request needs {@code stratos.use} and the nodes its module names for it
 * ({@link PlanModule#nodes}); undo and redo need {@code stratos.undo} ({@code PERMISSION_DENIED {node}}). Players without
 * {@code stratos.limits.bypass} have the server's {@link EditorLimits}: a plan request over the rate is refused with
 * {@code RATE_LIMITED}, and a plan over the blocks for one edit or for the last minute with {@code VOLUME_LIMIT} or
 * {@code VOLUME_PER_MINUTE}, nothing written. Undo and redo are neither limited nor counted (owner, 2026-10-12).
 *
 * <p><b>From B-3</b> a player may start a world task ({@code TaskRequest}): bake the world or one of their roads to
 * vanilla, redraw every road's markings, or switch the world's driving side ({@link WorldTasks}), once the platform has given
 * the server every world and a {@link BlockFinder} ({@link #worldTasks}). Baking the world, redrawing and switching need {@link #NODE_ADMIN}, baking a road
 * {@code stratos.roads.edit}; none is limited. While one runs, the player's other requests are refused {@code BUSY}.
 *
 * <p><b>From UI-1c</b> the server keeps the world's mark for Stratos blocks, which the platform saves
 * ({@link #stratosBlocks(boolean, Consumer)}): on as soon as any Stratos block is placed ({@link #stratosBlockPlaced}), off
 * when a world bake leaves none and none was placed while it ran. Players who can edit hear it after the handshake
 * ({@code StratosBlocks}, after {@code YourRoads}) and whenever it changes.
 *
 * <p><b>From P9</b> the server also keeps the world's driving side, which the platform gives it ({@link #trafficSide}):
 * players who can edit hear it after the handshake when it is right-hand ({@code TrafficSide}) and whenever it changes. A plan
 * request to draw or rebuild a road for the other side is refused {@code DRIVING_SIDE_MISMATCH} ({@link PlanModule#keepsSide}),
 * and the world task {@link Protocol#TASK_DRIVE_RIGHT} (or {@code DRIVE_LEFT}; {@link #NODE_ADMIN}) switches the world and
 * converts the sender's roads ({@link WorldTasks}); {@link Protocol#TASK_DRIVE_RIGHT_ALL} (or {@code DRIVE_LEFT_ALL}), which
 * needs {@link #NODE_ADMIN} too, converts every player's.
 *
 * <p>Server thread only: the platform calls {@link #receive}, {@link #tick} and {@link #finished} there.
 */
public final class EditorServer {
    /** B-3: baking the world to vanilla and redrawing every road's markings (operator level 4 without a permissions mod). */
    public static final String NODE_ADMIN = "stratos.admin";
    /** B-3: the module a bake's operation record names (its tool is the task). */
    public static final String WORLD_MODULE = "world";
    /** How long a plan may take to work out (editor-protocol §5, proposed 2 s). */
    public static final long PLAN_TIME_LIMIT_NANOS = 2_000_000_000L;
    /** How often a running operation reports its progress. */
    public static final long PROGRESS_INTERVAL_NANOS = 1_000_000_000L;
    /** Sending plan requests at all (editor-protocol §7). */
    public static final String NODE_USE = "stratos.use";
    /** Undo and redo of the player's own operations. */
    public static final String NODE_UNDO = "stratos.undo";
    /** Ignoring the server's rate and volume limits (F-6); the singleplayer world's owner has it too. */
    public static final String NODE_LIMITS_BYPASS = "stratos.limits.bypass";
    /** The window of the blocks-per-minute limit. */
    private static final long MINUTE_NANOS = 60_000_000_000L;

    /** The tools whose operations {@code YourRoads} lists: roads placed, and (R-7) their rebuilds and deletes. */
    private static final List<String> ROAD_TOOLS = List.of("STRAIGHT", "CURVE", "REBUILD", "DELETE");

    /** How many malformed records per player are logged; more are only counted. */
    private static final int LOGGED_MALFORMED = 5;
    private static final long NOT_STARTED = Long.MIN_VALUE;

    /** Where a player's edits go: that world's apply engine and undo histories, and its limits. */
    public record EditWorld(Operations operations, WorldLimits limits) {
        public EditWorld {
            Objects.requireNonNull(operations, "operations");
            Objects.requireNonNull(limits, "limits");
        }
    }

    /**
     * What the server's {@code Hello} says about it. {@code capabilities} are the optional features it has beside its
     * modules (T-2: {@link Protocol#CAPABILITY_TRACK}). {@code limits} are its rate and volume limits when it starts (F-6).
     */
    public record ServerInfo(String modVersion, String coreVersion, int dataVersion, int formatVersion, EditorLimits limits, List<String> capabilities) {
        public ServerInfo {
            capabilities = List.copyOf(capabilities);
        }

        /** A server with no optional features beside its modules. */
        public ServerInfo(String modVersion, String coreVersion, int dataVersion, int formatVersion, EditorLimits limits) {
            this(modVersion, coreVersion, dataVersion, formatVersion, limits, List.of());
        }
    }

    private record Computing(int requestId, FutureTask<PlanCheck.Decision> task, AtomicLong started, EditWorld world, OperationRecord.Draft draft) {
    }

    /** An apply, undo or redo the player started, until its result comes back. */
    private static final class Running {
        final Operations.Kind kind;
        final int requestId;
        final UUID operationId; // the apply's; undo and redo learn theirs from the result
        final Operations.Handle handle;
        long lastProgress;
        boolean filingReported;
        Digest planHash; // R-7: a delete's or rebuild's PlanAccepted, held until its job has checked every block (null once sent)
        int counted; // F-6: the blocks its acceptance adds to the player's last minute (0 when they aren't limited)

        Running(Operations.Kind kind, int requestId, UUID operationId, Operations.Handle handle, long now) {
            this.kind = kind;
            this.requestId = requestId;
            this.operationId = operationId;
            this.handle = handle;
            this.lastProgress = now;
        }
    }

    private static final class Session {
        final MessageChannel channel;
        boolean editing;
        boolean mismatchLogged;
        int malformed;
        Computing computing;
        final List<Running> running = new ArrayList<>();

        Session(MessageChannel channel) {
            this.channel = channel;
        }
    }

    private final ServerInfo info;
    private final Map<String, PlanModule<?>> modules = new LinkedHashMap<>();
    private final PermissionCheck permissions;
    private final Function<UUID, EditWorld> worlds;
    private final Executor planners;
    private final LongSupplier clock;
    private final Supplier<UUID> operationIds;
    private final Consumer<String> log;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    /** F-6: each limited player's request rate and blocks in the last minute, kept when they leave and rejoin. */
    private final Map<UUID, Usage> usage = new HashMap<>();
    private EditorLimits limits;
    private Notice repaired; // F-2+: the repair's notice, for players who can edit and join later
    private WorldTasks tasks; // B-3: null until the platform gives the server its worlds and a block finder
    /** UI-1c: whether the world has Stratos blocks, by the mark; the safe side until the platform says. */
    private boolean stratosBlocks = true;
    /** UI-1c: Stratos blocks placed since the server started, so a world bake knows whether any arrived while it ran. */
    private long stratosPlacements;
    private Consumer<Boolean> stratosBlocksSaver = present -> {
    };
    /** P9: whether the world keeps to the right, which new roads take; the platform says (its game rule). */
    private boolean rightHand;
    private Consumer<Boolean> sideSetter = rightHand -> {
    };

    /**
     * @param modules      the modules that take plan requests (Slice A: roads)
     * @param permissions  the permission gate
     * @param worlds       the world a player edits in now
     * @param planners     where plans are worked out, off the server thread
     * @param clock        nanoseconds, for the time limit and progress reports (also read on the planner threads)
     * @param operationIds new operation ids
     * @param log          where problems are logged
     */
    public EditorServer(ServerInfo info, List<PlanModule<?>> modules, PermissionCheck permissions, Function<UUID, EditWorld> worlds,
            Executor planners, LongSupplier clock, Supplier<UUID> operationIds, Consumer<String> log) {
        this.info = Objects.requireNonNull(info, "info");
        modules.forEach(module -> this.modules.put(module.id(), module));
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.planners = Objects.requireNonNull(planners, "planners");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.operationIds = Objects.requireNonNull(operationIds, "operationIds");
        this.log = Objects.requireNonNull(log, "log");
        this.limits = Objects.requireNonNull(info.limits(), "limits");
    }

    /**
     * Lets players run world tasks (B-3): {@code worlds} are every world (dimension) the server has, {@code finder} reads
     * them for Stratos blocks. Until this is called, a {@code TaskRequest} is refused {@code NOT_AVAILABLE_YET}.
     */
    public void worldTasks(Supplier<List<EditWorld>> worlds, BlockFinder finder) {
        this.tasks = new WorldTasks(worlds, finder, this.planners, this.clock, this.operationIds, this.info.coreVersion(), this.info.dataVersion(),
                this.log, () -> this.stratosPlacements, () -> {
                    if (this.stratosBlocks) {
                        setStratosBlocks(false);
                    }
                }, rightHand -> this.sideSetter.accept(rightHand));
    }

    /**
     * Where a switch of the world's driving side (P9, {@link Protocol#TASK_DRIVE_RIGHT}) sets it: the platform saves it with the
     * world (its game rule) and then tells the server, which tells the players ({@link #trafficSide}).
     */
    public void trafficSideSetter(Consumer<Boolean> setter) {
        this.sideSetter = Objects.requireNonNull(setter, "setter");
    }

    /**
     * The world's mark for Stratos blocks (UI-1c) as the platform saved it, and where a change of it is to be saved. Until
     * this is called the server says the world has them.
     */
    public void stratosBlocks(boolean present, Consumer<Boolean> saver) {
        this.stratosBlocksSaver = Objects.requireNonNull(saver, "saver");
        if (present != this.stratosBlocks) {
            this.stratosBlocks = present;
            tellStratosBlocks();
        }
    }

    /**
     * The world's driving side (P9), as the platform keeps it: players who can edit hear it whenever it changes, so the
     * roads they draw from then on keep to that side. Roads already placed keep the side they were placed with.
     */
    public void trafficSide(boolean rightHand) {
        if (rightHand != this.rightHand) {
            this.rightHand = rightHand;
            TrafficSide message = new TrafficSide(rightHand);
            for (Session session : this.sessions.values()) {
                if (session.editing) {
                    session.channel.send(message);
                }
            }
        }
    }

    /** Whether the world has Stratos blocks, by the mark (UI-1c). */
    public boolean stratosBlocks() {
        return this.stratosBlocks;
    }

    /**
     * A Stratos block was placed, in any dimension and by anything: a tool, an undo, a player or a command (UI-1c). It runs
     * for every block, so it costs next to nothing once the mark is on.
     */
    public void stratosBlockPlaced() {
        this.stratosPlacements++;
        if (!this.stratosBlocks) {
            setStratosBlocks(true);
        }
    }

    private void setStratosBlocks(boolean present) {
        this.stratosBlocks = present;
        this.stratosBlocksSaver.accept(present);
        tellStratosBlocks();
    }

    /** Tells every player who can edit whether the world has Stratos blocks. */
    private void tellStratosBlocks() {
        StratosBlocks message = new StratosBlocks(this.stratosBlocks);
        for (Session session : this.sessions.values()) {
            if (session.editing) {
                session.channel.send(message);
            }
        }
    }

    /**
     * Changes the rate and volume limits (F-6) for requests from now on. Players already joined keep the limits their
     * {@code Hello} told them, which are only for showing.
     */
    public void setLimits(EditorLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Whether the limits apply to {@code player}: to everyone but those with {@link #NODE_LIMITS_BYPASS}. */
    private boolean limited(UUID player) {
        return !this.permissions.has(player, NODE_LIMITS_BYPASS);
    }

    /** The player's client can receive {@code aurelia:roads}: start the handshake with {@code Hello}. */
    public void join(UUID player, MessageChannel channel) {
        Session old = this.sessions.put(player, new Session(channel));
        if (old != null) {
            cancel(old);
        }
        List<String> capabilities = new ArrayList<>(this.modules.keySet());
        capabilities.addAll(this.info.capabilities());
        channel.send(new Hello(Protocol.VERSION, this.info.modVersion(), this.info.coreVersion(), this.info.dataVersion(),
                this.info.formatVersion(), capabilities, limited(player) ? this.limits.hello() : Limits.NONE));
    }

    /** The player left. Operations already running finish, and stay in the player's undo history. */
    public void leave(UUID player) {
        Session session = this.sessions.remove(player);
        if (session != null) {
            cancel(session);
        }
        if (this.tasks != null) {
            this.tasks.cancel(player);
        }
    }

    /**
     * The notice a repair after a crash gets (F-2+): {@code REPAIRED} with the edits rolled back and completed, or
     * {@code REPAIRED_ATTENTION} with those needing attention too.
     */
    public static Notice repairNotice(int rolledBack, int completed, int attention) {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("rolledBack", Integer.toString(rolledBack));
        args.put("completed", Integer.toString(completed));
        if (attention > 0) {
            args.put("attention", Integer.toString(attention));
        }
        return new Notice(attention > 0 ? Protocol.REPAIRED_ATTENTION : Protocol.REPAIRED, args);
    }

    /**
     * The world was repaired after a crash (F-2+): every player online who can edit hears it now, and anyone who can
     * edit hears it when they join later, after the handshake.
     */
    public void repaired(Notice notice) {
        this.repaired = Objects.requireNonNull(notice, "notice");
        for (Map.Entry<UUID, Session> entry : this.sessions.entrySet()) {
            if (entry.getValue().editing && this.permissions.has(entry.getKey(), NODE_USE)) {
                entry.getValue().channel.send(notice);
            }
        }
    }

    /** Stops every plan being worked out (the server is stopping). */
    public void stop() {
        this.sessions.values().forEach(EditorServer::cancel);
        this.sessions.clear();
        this.usage.clear();
        if (this.tasks != null) {
            this.tasks.stop();
        }
    }

    private static void cancel(Session session) {
        if (session.computing != null) {
            session.computing.task().cancel(true);
            session.computing = null;
        }
    }

    /** Bytes that arrived from {@code player} on the channel. */
    public void receive(UUID player, byte[] bytes) {
        Session session = this.sessions.get(player);
        if (session == null) {
            return; // no handshake started: the client never registered the channel
        }
        Message message;
        try {
            message = Messages.decode(bytes, Message.Direction.TO_SERVER);
        } catch (MalformedMessage e) {
            malformed(player, session, e.getMessage());
            return;
        }
        if (message instanceof HelloAck ack) {
            handshake(player, session, ack);
        } else if (message instanceof PlanRequest request) {
            planRequest(player, session, request);
        } else if (message instanceof Undo undo) {
            undoRedo(player, session, undo.requestId(), Operations.Kind.UNDO);
        } else if (message instanceof Redo redo) {
            undoRedo(player, session, redo.requestId(), Operations.Kind.REDO);
        } else if (message instanceof TaskRequest request) {
            taskRequest(player, session, request);
        }
    }

    private void handshake(UUID player, Session session, HelloAck ack) {
        session.editing = ack.protocol() == Protocol.VERSION;
        if (session.editing && this.worlds.apply(player).operations().isReadOnly()) {
            // The downgrade guard (F-5): the world's Stratos data is newer than this Stratos.
            session.editing = false;
            session.channel.send(new HelloResult(false, Optional.of(Protocol.WORLD_READ_ONLY)));
            return;
        }
        if (session.editing) {
            session.channel.send(new HelloResult(true, Optional.empty()));
            sendPalettes(player, session);
            sendYourRoads(player, session);
            session.channel.send(new StratosBlocks(this.stratosBlocks));
            if (this.rightHand) {
                session.channel.send(new TrafficSide(true)); // P9: left-hand is what a client assumes until told
            }
            if (this.repaired != null && this.permissions.has(player, NODE_USE)) {
                session.channel.send(this.repaired); // F-2+: the repair after a crash, before they joined
            }
            return;
        }
        session.channel.send(new HelloResult(false, Optional.of(Protocol.PROTOCOL_MISMATCH)));
        if (!session.mismatchLogged) {
            session.mismatchLogged = true;
            this.log.accept("[stratos] " + player + " has Stratos " + ack.modVersion() + " (protocol " + ack.protocol() + "); this server has "
                    + this.info.modVersion() + " (protocol " + Protocol.VERSION + "), so editing is off for them");
        }
    }

    /** Why a player whose handshake didn't enable editing is refused: the world is read-only, or editing is off. */
    private String notEditing(UUID player) {
        return this.worlds.apply(player).operations().isReadOnly() ? Protocol.WORLD_READ_ONLY : Protocol.EDITING_OFF;
    }

    /**
     * The player changed dimension (F-5): their node aids now need the roads of the new one. Nothing happens for a
     * player who can't edit.
     */
    public void dimensionChanged(UUID player) {
        Session session = this.sessions.get(player);
        if (session != null && session.editing) {
            sendYourRoads(player, session);
        }
    }

    /**
     * Sends {@code player} their straight and curved roads they can still undo or redo in their dimension ({@code
     * YourRoads}, F-5; owner, 2026-10-08), and (R-7) their rebuilds and deletes of them, in the order they were made. A
     * road whose parameters no longer read is left out and logged.
     */
    /** The palettes roads may use here (R-9: the server's, in the hub's order), so the client plans with exactly these. */
    private void sendPalettes(UUID player, Session session) {
        List<String> palettes = new ArrayList<>();
        for (Palette palette : this.worlds.apply(player).limits().palettes().all()) {
            palettes.add(palette.json());
        }
        session.channel.send(new Message.Palettes(palettes));
    }

    private void sendYourRoads(UUID player, Session session) {
        List<YourRoads.Road> roads = new ArrayList<>();
        for (Operations.HistoryEntry entry : this.worlds.apply(player).operations().history(player.toString())) {
            OperationRecord record = entry.record();
            PlanModule<?> module = this.modules.get(record.module());
            if (module == null || !ROAD_TOOLS.contains(record.tool())) {
                continue;
            }
            try {
                roads.add(new YourRoads.Road(record.id(), record.module(), record.tool(), encoded(module, record), entry.undone()));
            } catch (JsonFormatException | IllegalArgumentException e) {
                this.log.accept("[stratos] the parameters of operation " + record.id() + " can't be read back: " + e.getMessage());
            }
            if (roads.size() == YourRoads.MAX_ROADS) {
                break;
            }
        }
        session.channel.send(new YourRoads(roads));
    }

    private static <P> byte[] encoded(PlanModule<P> module, OperationRecord record) throws JsonFormatException {
        return module.encode(module.paramsFromJson(record.tool(), record.params()));
    }

    private void planRequest(UUID player, Session session, PlanRequest request) {
        if (!session.editing) {
            reject(session, request.requestId(), notEditing(player), Map.of());
            return;
        }
        PlanModule<?> module = this.modules.get(request.module());
        if (module == null) {
            reject(session, request.requestId(), Protocol.NOT_AVAILABLE_YET, Map.of("feature", request.module()));
            return;
        }
        startPlan(player, session, request, module);
    }

    private <P> void startPlan(UUID player, Session session, PlanRequest request, PlanModule<P> module) {
        P params;
        try {
            params = module.decode(request.tool(), request.params());
        } catch (MalformedMessage e) {
            malformed(player, session, e.getMessage());
            return;
        }
        List<String> nodes = new ArrayList<>();
        nodes.add(NODE_USE);
        nodes.addAll(module.nodes(params)); // F-6: each request its own nodes
        for (String node : nodes) {
            if (!this.permissions.has(player, node)) {
                reject(session, request.requestId(), Protocol.PERMISSION_DENIED, Map.of("node", node));
                return;
            }
        }
        if (!module.keepsSide(params, this.rightHand)) {
            reject(session, request.requestId(), Protocol.DRIVING_SIDE_MISMATCH, Map.of("side", this.rightHand ? "right" : "left")); // P9
            return;
        }
        if (limited(player) && !usage(player).takeRequest(this.limits, this.clock.getAsLong())) {
            reject(session, request.requestId(), Protocol.RATE_LIMITED, Map.of());
            return;
        }
        EditWorld world = this.worlds.apply(player);
        if (session.computing != null || world.operations().isBusy(player.toString()) || runningTask(player)) {
            reject(session, request.requestId(), Protocol.BUSY, Map.of());
            return;
        }
        // R-5: a road joining another is checked against the records of what is applied here now, copied on this thread.
        boolean joins = module.usesPlaced(params);
        List<OperationRecord> placed = joins ? applied(world.operations(), module.id()) : List.of();
        // Council r4: what the server works out for itself (a road's dashes) replaces the client's, in the plan and the record.
        P settled = module.settle(params, placed);
        // What the operation's record will say it was (G-9); the plan hash is filled in once the plan is accepted.
        OperationRecord.Draft draft = new OperationRecord.Draft(module.id(), module.tool(settled), module.paramsToJson(settled), "", List.of(),
                module.palettes(settled, world.limits())); // R-9: the palette snapshots its record names
        AtomicLong started = new AtomicLong(NOT_STARTED);
        Digest clientHash = request.clientPlanHash();
        String coreVersion = this.info.coreVersion();
        FutureTask<PlanCheck.Decision> task = new FutureTask<>(() -> {
            started.set(this.clock.getAsLong());
            PlanResult result = joins ? module.plan(settled, world.limits(), placed) : module.plan(settled, world.limits());
            return PlanCheck.check(result, clientHash, coreVersion);
        });
        session.computing = new Computing(request.requestId(), task, started, world, draft);
        try {
            this.planners.execute(task);
        } catch (RejectedExecutionException e) {
            session.computing = null;
            this.log.accept("[stratos] couldn't start working out a plan: " + e);
            reject(session, request.requestId(), Protocol.INTERNAL_ERROR, Map.of());
        }
    }

    /**
     * The records of the operations of {@code module} applied in {@code operations}' world now (R-5): those of every
     * player's undo history there, and this session's, whose blocks are all placed.
     */
    static List<OperationRecord> applied(Operations operations, String module) {
        List<OperationRecord> applied = new ArrayList<>();
        for (OperationRecord record : operations.records()) {
            if (record.status() == OperationRecord.Status.APPLIED && record.module().equals(module)) {
                applied.add(record);
            }
        }
        return applied;
    }

    private void undoRedo(UUID player, Session session, int requestId, Operations.Kind kind) {
        Optional<String> refusal = Optional.empty();
        if (!session.editing) {
            refusal = Optional.of(notEditing(player));
        } else if (!this.permissions.has(player, NODE_UNDO)) {
            refusal = Optional.of(Protocol.PERMISSION_DENIED);
        } else if (session.computing != null || runningTask(player)) {
            refusal = Optional.of(Protocol.BUSY);
        }
        if (refusal.isEmpty()) {
            Operations operations = this.worlds.apply(player).operations();
            Operations.Request request = kind == Operations.Kind.UNDO ? operations.undo(player.toString()) : operations.redo(player.toString());
            if (request.isAccepted()) {
                session.running.add(new Running(kind, requestId, null, request.handle().orElseThrow(), this.clock.getAsLong()));
                return;
            }
            refusal = request.refusal().map(Enum::name);
        }
        session.channel.send(new UndoRedoResult(requestId, Optional.empty(), false, refusal, 0));
    }

    /** Whether {@code player} has a world task running (B-3). */
    private boolean runningTask(UUID player) {
        return this.tasks != null && this.tasks.busy(player);
    }

    /**
     * A world task (B-3): refused at once ({@code TaskResult}) when editing is off, the server can't run them, the player
     * lacks the node or has something running; otherwise started.
     */
    private void taskRequest(UUID player, Session session, TaskRequest request) {
        String task = request.task();
        boolean road = task.equals(Protocol.TASK_BAKE_ROAD);
        boolean known = road || task.equals(Protocol.TASK_BAKE_WORLD) || task.equals(Protocol.TASK_REDRAW_MARKINGS)
                || Protocol.isDriveTask(task);
        if (road && request.roadId().isEmpty()) {
            malformed(player, session, "a " + task + " task names no road");
            return;
        }
        Optional<String> refusal = Optional.empty();
        Map<String, String> args = Map.of();
        if (!session.editing) {
            refusal = Optional.of(notEditing(player));
        } else if (!known || this.tasks == null) {
            refusal = Optional.of(Protocol.NOT_AVAILABLE_YET);
            args = Map.of("feature", "world_tasks");
        } else {
            for (String node : List.of(NODE_USE, road ? RoadsModule.NODE_EDIT : NODE_ADMIN)) {
                if (refusal.isEmpty() && !this.permissions.has(player, node)) {
                    refusal = Optional.of(Protocol.PERMISSION_DENIED);
                    args = Map.of("node", node);
                }
            }
        }
        EditWorld world = this.worlds.apply(player);
        if (refusal.isEmpty() && (session.computing != null || !session.running.isEmpty() || runningTask(player)
                || world.operations().isBusy(player.toString()))) {
            refusal = Optional.of(Protocol.BUSY);
        }
        if (refusal.isPresent()) {
            session.channel.send(new TaskResult(request.requestId(), false, refusal, args));
            return;
        }
        this.tasks.start(player, session.channel, request.requestId(), task, request.roadId(), world);
    }

    /** Once per server tick: finished and timed-out plans, and progress reports. */
    public void tick() {
        long now = this.clock.getAsLong();
        for (Map.Entry<UUID, Session> entry : this.sessions.entrySet()) {
            Session session = entry.getValue();
            Computing computing = session.computing;
            if (computing != null) {
                long started = computing.started().get();
                if (computing.task().isDone()) {
                    session.computing = null;
                    finishPlan(entry.getKey(), session, computing, now);
                } else if (started != NOT_STARTED && now - started > PLAN_TIME_LIMIT_NANOS) {
                    computing.task().cancel(true); // interrupts the planner, which stops at its next check
                    session.computing = null;
                    reject(session, computing.requestId(), Protocol.PLAN_TOO_COMPLEX,
                            Map.of("seconds", Long.toString(PLAN_TIME_LIMIT_NANOS / 1_000_000_000L)));
                }
            }
            for (Running running : session.running) {
                Optional<ApplyJob> job = running.handle.job();
                if (running.kind != Operations.Kind.APPLY || job.isEmpty()) {
                    continue;
                }
                if (running.planHash != null) {
                    ApplyJob.Status status = job.get().status();
                    if (status == ApplyJob.Status.LOADING || status == ApplyJob.Status.CHECKING || status == ApplyJob.Status.FAILED) {
                        continue; // R-7: still checking, or refused (finished says so)
                    }
                    accept(entry.getKey(), session, running, job.get());
                    running.lastProgress = now;
                }
                if (!job.get().status().finished()) {
                    if (now - running.lastProgress >= PROGRESS_INTERVAL_NANOS) {
                        running.lastProgress = now;
                        session.channel.send(new ApplyProgress(running.operationId, ApplyState.APPLYING, job.get().sectionsDone(), job.get().sectionCount(),
                                Optional.empty()));
                    }
                } else if (!running.filingReported && job.get().status() == ApplyJob.Status.DONE && running.handle.stage() == Operations.Stage.FILING) {
                    // Every section is placed; the helper is filing them (F-2b).
                    running.filingReported = true;
                    session.channel.send(new ApplyProgress(running.operationId, ApplyState.APPLYING, job.get().sectionCount(), job.get().sectionCount(),
                            Optional.empty()));
                }
            }
        }
        if (this.tasks != null) {
            this.tasks.tick();
        }
    }

    private void finishPlan(UUID player, Session session, Computing computing, long now) {
        PlanCheck.Decision decision;
        try {
            decision = computing.task().get();
        } catch (ExecutionException | CancellationException e) {
            this.log.accept("[stratos] working out a plan for " + player + " failed: " + (e.getCause() != null ? e.getCause() : e));
            reject(session, computing.requestId(), Protocol.INTERNAL_ERROR, Map.of());
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!decision.isAccepted()) {
            session.channel.send(new PlanRejected(computing.requestId(), decision.code().orElseThrow(), decision.args(), decision.serverPlanHash()));
            return;
        }
        if (!decision.sideEffects().isEmpty() && !this.info.capabilities().contains(Protocol.CAPABILITY_TRACK)) {
            // The client offers track only when Hello lists it, so this is a guard (T-2).
            reject(session, computing.requestId(), Protocol.TRACK_UNAVAILABLE, Map.of());
            return;
        }
        BlockPlan plan = decision.plan().orElseThrow();
        // F-6: the volume limits, on the plan's blocks (a delete's are the road's it puts back, a rebuild's its whole road).
        int counted = 0;
        if (limited(player)) {
            Map<String, String> over = usage(player).overLimit(this.limits, plan.blockCount(), now);
            if (!over.isEmpty()) {
                reject(session, computing.requestId(), over.containsKey("seconds") ? Protocol.VOLUME_PER_MINUTE : Protocol.VOLUME_LIMIT, over);
                return;
            }
            counted = plan.blockCount();
        }
        UUID operationId = this.operationIds.get();
        OperationRecord.Draft draft = computing.draft();
        draft = new OperationRecord.Draft(draft.module(), draft.tool(), draft.params(), decision.serverPlanHash().orElseThrow().hex(),
                decision.sideEffects(), draft.palettes());
        Operations operations = computing.world().operations();
        Optional<Replacement> replacement = decision.replacement();
        Operations.Request request;
        if (replacement.isPresent()) {
            // R-7: a delete writes nothing new; its plan (the road being removed) was only for the hash.
            BlockPlan newPlan = replacement.get().delete() ? BlockPlan.builder(plan.dataVersion()).build() : plan;
            request = operations.replace(player.toString(), newPlan, replacement.get(), operationId, draft);
        } else {
            request = operations.apply(player.toString(), plan, operationId, decision.onlyWhere(), draft);
        }
        if (!request.isAccepted()) {
            reject(session, computing.requestId(), request.refusal().map(Enum::name).orElse(Protocol.BUSY), Map.of());
            return;
        }
        Running running = new Running(Operations.Kind.APPLY, computing.requestId(), operationId, request.handle().orElseThrow(), now);
        running.counted = counted;
        if (replacement.isPresent()) {
            running.planHash = decision.serverPlanHash().orElseThrow(); // accepted once its job has checked every block (tick)
        } else {
            count(player, counted, now);
            session.channel.send(new PlanAccepted(computing.requestId(), operationId, decision.serverPlanHash().orElseThrow(), plan.blockCount(),
                    plan.sections().size()));
        }
        session.running.add(running);
    }

    /**
     * Sends a delete's or rebuild's held {@code PlanAccepted} (R-7), with its job's blocks and sections, and (F-6) counts it
     * in the player's last minute. One refused before writing (hand edits, nothing to change) isn't counted.
     */
    private void accept(UUID player, Session session, Running running, ApplyJob job) {
        session.channel.send(new PlanAccepted(running.requestId, running.operationId, running.planHash, job.plan().blockCount(), job.sectionCount()));
        running.planHash = null;
        count(player, running.counted, this.clock.getAsLong());
    }

    private void count(UUID player, int blocks, long now) {
        if (blocks > 0) {
            usage(player).add(blocks, now);
        }
    }

    private Usage usage(UUID player) {
        return this.usage.computeIfAbsent(player, id -> new Usage());
    }

    /** A limited player's plan requests (a token bucket) and their accepted plans' blocks in the last minute (F-6). */
    private static final class Usage {
        private boolean started;
        private long credit; // nanoseconds of requests saved up, full to begin with
        private long lastRequest;
        private final ArrayDeque<long[]> recent = new ArrayDeque<>(); // {when, blocks}, oldest first

        /** Takes one plan request from the bucket, or says the player is over the rate. */
        boolean takeRequest(EditorLimits limits, long now) {
            if (limits.planRequestsPerSecond() == 0) {
                return true;
            }
            long cost = 1_000_000_000L / limits.planRequestsPerSecond();
            long full = cost * limits.planRequestBurst();
            this.credit = this.started ? StrictMath.min(full, this.credit + StrictMath.max(0, now - this.lastRequest)) : full;
            this.started = true;
            this.lastRequest = now;
            if (this.credit < cost) {
                return false;
            }
            this.credit -= cost;
            return true;
        }

        /**
         * Nothing if a plan of {@code blocks} fits the limits; else the refusal's args: {@code blocks} and {@code max} for an
         * edit over the limit for one ({@code VOLUME_LIMIT}), or {@code used}, {@code max} and {@code seconds} for one over
         * the minute's ({@code VOLUME_PER_MINUTE}).
         */
        Map<String, String> overLimit(EditorLimits limits, int blocks, long now) {
            int perOperation = limits.maxBlocksPerOperation();
            int perMinute = limits.maxBlocksPerMinute();
            // An edit bigger than a whole minute's limit would never fit, so that is its limit too.
            int max = perOperation == 0 ? perMinute : perMinute == 0 ? perOperation : StrictMath.min(perOperation, perMinute);
            Map<String, String> args = new LinkedHashMap<>();
            if (max > 0 && blocks > max) {
                args.put("blocks", Integer.toString(blocks));
                args.put("max", Integer.toString(max));
                return args;
            }
            if (perMinute == 0) {
                return args;
            }
            while (!this.recent.isEmpty() && now - this.recent.peekFirst()[0] >= MINUTE_NANOS) {
                this.recent.removeFirst();
            }
            long used = 0;
            for (long[] entry : this.recent) {
                used += entry[1];
            }
            if (used + blocks <= perMinute) {
                return args;
            }
            long freed = 0;
            long wait = 0;
            for (long[] entry : this.recent) {
                freed += entry[1];
                if (used - freed + blocks <= perMinute) {
                    wait = entry[0] + MINUTE_NANOS - now;
                    break;
                }
            }
            args.put("used", Long.toString(used));
            args.put("max", Integer.toString(perMinute));
            args.put("seconds", Long.toString(StrictMath.max(1, (wait + 999_999_999L) / 1_000_000_000L)));
            return args;
        }

        void add(int blocks, long now) {
            this.recent.addLast(new long[] {now, blocks});
        }
    }

    /**
     * An apply, undo or redo finished in the apply engine: tell its player, if this server started it for them, and then
     * about any track it left unbuilt or unremoved ({@code Notice}, T-2).
     */
    public void finished(Operations.Result result) {
        // Side effects that weren't made or removed (track) go to the log with their reasons; skipped ones in one line.
        int skipped = 0;
        for (Operations.SideEffectOutcome outcome : result.sideEffects()) {
            SideEffectWriter.Result what = outcome.result();
            if (what.outcome() == SideEffectWriter.Outcome.SKIPPED) {
                skipped++;
            } else if (what.outcome() != SideEffectWriter.Outcome.DONE) {
                this.log.accept("[stratos] " + result.kind().name().toLowerCase(Locale.ROOT) + " of operation "
                        + result.operationId().map(UUID::toString).orElse("?") + ": " + outcome.effect() + " " + what.outcome()
                        + (what.reason().isEmpty() ? "" : " (" + what.reason() + ")"));
            }
        }
        if (skipped > 0) {
            this.log.accept("[stratos] " + result.kind().name().toLowerCase(Locale.ROOT) + " of operation "
                    + result.operationId().map(UUID::toString).orElse("?") + ": " + skipped
                    + " lanes got no hidden track because their ends aren't MTR node blocks (a road laid with another node aid)");
        }
        // Why an operation failed, for the log: the player's message has only its code (F-1+: the log names the block). A
        // delete's or rebuild's question about hand edits, or its "nothing to change", isn't a failure (R-7).
        boolean asked = result.code().filter(code -> code.equals(Protocol.MANUAL_EDITS) || code.equals(Protocol.NOTHING_TO_CHANGE)).isPresent();
        if (result.code().isPresent() && !asked) {
            result.job().flatMap(ApplyJob::failure).ifPresent(why -> this.log.accept("[stratos] " + result.kind().name().toLowerCase(Locale.ROOT)
                    + " of operation " + result.operationId().map(UUID::toString).orElse("?") + " ended " + result.code().get() + ": " + why));
        }
        UUID player;
        try {
            player = UUID.fromString(result.owner());
        } catch (IllegalArgumentException e) {
            return; // not a player's operation (the GameTests use other owners)
        }
        Optional<UUID> tasking = this.tasks == null ? Optional.empty() : this.tasks.finished(result);
        if (tasking.isPresent()) {
            // the player who ran the task hears of it, even when the operation was another player's road (the override)
            Session tasked = this.sessions.get(tasking.get());
            if (tasked != null) {
                notices(result).forEach(tasked.channel::send); // B-3: a redrawn road's track, as for any rebuild
            }
            return;
        }
        Session session = this.sessions.get(player);
        if (session == null) {
            return;
        }
        Running running = null;
        for (Running candidate : session.running) {
            if (candidate.handle == result.handle()) {
                running = candidate;
            }
        }
        if (running == null) {
            return;
        }
        session.running.remove(running);
        if (running.planHash != null) {
            // R-7: a delete or rebuild not yet accepted. Refused if its job stopped before writing; else accepted now.
            Optional<ApplyJob> job = result.job();
            if (result.code().isPresent() && job.map(ApplyJob::changedBlocks).orElse(0) == 0) {
                Map<String, String> args = new LinkedHashMap<>();
                if (result.code().get().equals(Protocol.MANUAL_EDITS)) {
                    args.put("count", Integer.toString(job.map(ApplyJob::manualEdits).orElse(0)));
                }
                // A rebuild with no block to change still makes the road's missing track again: how many rails it made.
                long made = result.sideEffects().stream().filter(o -> !o.removal() && o.result().outcome() == SideEffectWriter.Outcome.DONE).count();
                if (made > 0) {
                    args.put("track", Long.toString(made));
                }
                reject(session, running.requestId, result.code().get(), args);
                notices(result).forEach(session.channel::send);
                return;
            }
            if (job.isPresent()) {
                accept(player, session, running, job.get());
            }
        }
        if (running.kind == Operations.Kind.APPLY) {
            ApplyState state = result.code().isEmpty() ? ApplyState.DONE : ApplyState.FAILED;
            int done = result.job().map(ApplyJob::sectionsDone).orElse(0);
            int total = result.job().map(ApplyJob::sectionCount).orElse(0);
            session.channel.send(new ApplyProgress(running.operationId, state, done, total, result.code()));
        } else {
            session.channel.send(new UndoRedoResult(running.requestId, result.operationId(), result.code().isEmpty(), result.code(), result.keptByHand()));
        }
        notices(result).forEach(session.channel::send);
    }

    /**
     * What to tell the player about {@code result}'s track, if any of it went wrong: {@code TRACK_NOT_REMOVED} when some
     * it was to remove are still there, then {@code TRACK_NOT_BUILT} when some it was to make weren't (a player's rail
     * already there is fine). An undo removes, an apply or redo makes; from R-7 a delete or rebuild, and their undo and
     * redo, may do both, each counted on its own.
     */
    static List<Notice> notices(Operations.Result result) {
        int[] wrong = new int[2];
        int[] total = new int[2];
        for (Operations.SideEffectOutcome outcome : result.sideEffects()) {
            if (!(outcome.effect() instanceof SideEffect.TrackRail)) {
                continue;
            }
            int i = outcome.removal() ? 0 : 1;
            total[i]++;
            SideEffectWriter.Outcome what = outcome.result().outcome();
            boolean fine = what == SideEffectWriter.Outcome.DONE || (outcome.removal() ? what == SideEffectWriter.Outcome.NOT_THERE
                    : what == SideEffectWriter.Outcome.ALREADY_THERE || what == SideEffectWriter.Outcome.SKIPPED);
            if (!fine) {
                wrong[i]++;
            }
        }
        List<Notice> notices = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            if (wrong[i] > 0) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("count", Integer.toString(wrong[i]));
                args.put("total", Integer.toString(total[i]));
                notices.add(new Notice(i == 0 ? Protocol.TRACK_NOT_REMOVED : Protocol.TRACK_NOT_BUILT, args));
            }
        }
        return notices;
    }

    private void reject(Session session, int requestId, String code, Map<String, String> args) {
        session.channel.send(new PlanRejected(requestId, code, args, Optional.empty()));
    }

    private void malformed(UUID player, Session session, String why) {
        session.malformed++;
        if (session.malformed <= LOGGED_MALFORMED) {
            this.log.accept("[stratos] dropped a malformed message from " + player + " (" + session.malformed + " so far): " + why);
        }
    }

    /** Whether {@code player} has been greeted with {@code Hello} and hasn't left since. */
    public boolean hasSession(UUID player) {
        return this.sessions.containsKey(player);
    }

    /** Whether {@code player}'s handshake enabled editing. */
    public boolean isEditing(UUID player) {
        Session session = this.sessions.get(player);
        return session != null && session.editing;
    }

    /** How many malformed records {@code player} has sent. */
    public int malformedCount(UUID player) {
        Session session = this.sessions.get(player);
        return session == null ? 0 : session.malformed;
    }
}
