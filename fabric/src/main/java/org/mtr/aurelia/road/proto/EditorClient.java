/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.Message.ApplyProgress;
import org.mtr.aurelia.road.proto.Message.Hello;
import org.mtr.aurelia.road.proto.Message.HelloAck;
import org.mtr.aurelia.road.proto.Message.HelloResult;
import org.mtr.aurelia.road.proto.Message.Notice;
import org.mtr.aurelia.road.proto.Message.PlanAccepted;
import org.mtr.aurelia.road.proto.Message.PlanRejected;
import org.mtr.aurelia.road.proto.Message.PlanRequest;
import org.mtr.aurelia.road.proto.Message.Redo;
import org.mtr.aurelia.road.proto.Message.TaskProgress;
import org.mtr.aurelia.road.proto.Message.TaskRequest;
import org.mtr.aurelia.road.proto.Message.TaskResult;
import org.mtr.aurelia.road.proto.Message.Undo;
import org.mtr.aurelia.road.proto.Message.UndoRedoResult;
import org.mtr.aurelia.road.proto.Message.YourRoads;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.PaletteException;
import org.mtr.aurelia.road.road.palette.Palettes;
import org.mtr.aurelia.road.spi.MessageChannel;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * The client's side of the {@code stratos:main} protocol (editor-protocol §3, §5): it answers the server's
 * {@code Hello}, knows whether editing is on, works out a plan locally and sends it with its hash, sends undo and redo,
 * and passes every answer to its {@link Listener}s. Client thread only.
 *
 * <p>From R-9 it keeps the server's palettes ({@link #palettes}), which the client plans with: Stratos's built-in ones
 * until they arrive, and again after the next {@code Hello}.
 */
public final class EditorClient {
    /** Hears the server's answers. */
    public interface Listener {
        /** The handshake ended (or the connection did): whether editing is on, and why not if it is off. */
        default void editingChanged(boolean enabled, Optional<String> reasonCode) {
        }

        default void planAccepted(PlanAccepted accepted) {
        }

        default void planRejected(PlanRejected rejected) {
        }

        default void progress(ApplyProgress progress) {
        }

        default void undoRedo(UndoRedoResult result) {
        }

        /** The player's roads they can still undo in this dimension (F-5): they replace any the client knew. */
        default void yourRoads(YourRoads roads) {
        }

        /** The server's palettes arrived (R-9). */
        default void palettes(Palettes palettes) {
        }

        /** Something the player should know (T-2: track that wasn't built or removed). */
        default void notice(Notice notice) {
        }

        /** How far a world task has got (B-3). */
        default void taskProgress(TaskProgress progress) {
        }

        /** How a world task ended (B-3). */
        default void taskResult(TaskResult result) {
        }

        /** Whether the world has Stratos blocks, by the server's mark (UI-1c): after the handshake and when it changes. */
        default void stratosBlocks(boolean present) {
        }
    }

    /**
     * A plan request: sent with its request id, or refused here before anything was sent (the local planner's code, or
     * {@code EDITING_OFF}). {@code plan} is the plan the client worked out, when it made one.
     */
    public record Attempt(OptionalInt requestId, Optional<BlockPlan> plan, Optional<String> code, Map<String, String> args) {
        public Attempt {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(code, "code");
            args = Fields.args(args);
        }

        public boolean isSent() {
            return this.requestId.isPresent();
        }
    }

    private final String modVersion;
    private final String coreVersion;
    private final List<String> capabilities;
    private final MessageChannel channel;
    private final Consumer<String> log;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private Hello hello;
    private boolean editing;
    private Palettes palettes = Palettes.BUILT_IN;
    private boolean stratosBlocks = true;
    private int nextRequestId;
    private int malformed;

    /**
     * @param channel sends to the server
     * @param log     where problems are logged
     */
    public EditorClient(String modVersion, String coreVersion, List<String> capabilities, MessageChannel channel, Consumer<String> log) {
        this.modVersion = Objects.requireNonNull(modVersion, "modVersion");
        this.coreVersion = Objects.requireNonNull(coreVersion, "coreVersion");
        this.capabilities = List.copyOf(capabilities);
        this.channel = Objects.requireNonNull(channel, "channel");
        this.log = Objects.requireNonNull(log, "log");
    }

    public void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    /** The palettes roads may use: the server's once they arrive (R-9), else Stratos's built-in ones. */
    public Palettes palettes() {
        return this.palettes;
    }

    /** Bytes that arrived from the server on the channel. */
    public void receive(byte[] bytes) {
        Message message;
        try {
            message = Messages.decode(bytes, Message.Direction.TO_CLIENT);
        } catch (MalformedMessage e) {
            this.malformed++;
            this.log.accept("[stratos] dropped a malformed message from the server (" + this.malformed + " so far): " + e.getMessage());
            return;
        }
        if (message instanceof Hello h) {
            this.hello = h;
            this.editing = false;
            this.palettes = Palettes.BUILT_IN;
            this.stratosBlocks = true;
            this.channel.send(new HelloAck(Protocol.VERSION, this.modVersion, this.capabilities));
        } else if (message instanceof HelloResult result) {
            this.editing = result.editingEnabled() && this.hello != null;
            this.listeners.forEach(l -> l.editingChanged(this.editing, result.reasonCode()));
        } else if (message instanceof PlanAccepted accepted) {
            this.listeners.forEach(l -> l.planAccepted(accepted));
        } else if (message instanceof PlanRejected rejected) {
            this.listeners.forEach(l -> l.planRejected(rejected));
        } else if (message instanceof ApplyProgress progress) {
            this.listeners.forEach(l -> l.progress(progress));
        } else if (message instanceof UndoRedoResult result) {
            this.listeners.forEach(l -> l.undoRedo(result));
        } else if (message instanceof YourRoads roads) {
            this.listeners.forEach(l -> l.yourRoads(roads));
        } else if (message instanceof Message.Palettes sent) {
            List<Palette> read = new ArrayList<>();
            for (String json : sent.palettes()) {
                try {
                    Palette palette = Palette.read(json, "a palette from the server");
                    if (read.stream().noneMatch(p -> p.id().equals(palette.id()))) {
                        read.add(palette);
                    }
                } catch (PaletteException e) {
                    this.log.accept("[stratos] dropped a palette the server sent: " + e.getMessage());
                }
            }
            this.palettes = Palettes.of(read, List.of(BuiltInPalettes.provisional()));
            this.listeners.forEach(l -> l.palettes(this.palettes));
        } else if (message instanceof Notice notice) {
            this.listeners.forEach(l -> l.notice(notice));
        } else if (message instanceof TaskProgress progress) {
            this.listeners.forEach(l -> l.taskProgress(progress));
        } else if (message instanceof TaskResult result) {
            this.listeners.forEach(l -> l.taskResult(result));
        } else if (message instanceof Message.StratosBlocks blocks) {
            this.stratosBlocks = blocks.present();
            this.listeners.forEach(l -> l.stratosBlocks(blocks.present()));
        }
    }

    /** The connection ended: editing is off until the next server says {@code Hello}. */
    public void disconnected() {
        boolean was = this.editing;
        this.hello = null;
        this.editing = false;
        if (was) {
            this.listeners.forEach(l -> l.editingChanged(false, Optional.empty()));
        }
    }

    /** Whether the server's handshake enabled editing. */
    public boolean editingEnabled() {
        return this.editing;
    }

    /**
     * Whether the world has Stratos blocks, as the server last said (UI-1c); until it says, it has (the safe side: the hub
     * then warns before Stratos is removed).
     */
    public boolean stratosBlocks() {
        return this.stratosBlocks;
    }

    /** The server's {@code Hello}, if a server with Stratos said it. */
    public Optional<Hello> serverHello() {
        return Optional.ofNullable(this.hello);
    }

    /** Whether the server's {@code Hello} listed {@code capability} (T-2: {@code track}). */
    public boolean serverHas(String capability) {
        return this.hello != null && this.hello.capabilities().contains(capability);
    }

    public String modVersion() {
        return this.modVersion;
    }

    /**
     * Works out the plan for {@code params} in a world with {@code limits} and, if the planner accepts them and editing
     * is on, sends a {@code PlanRequest} with the plan's hash.
     */
    public <P> Attempt request(PlanModule<P> module, P params, WorldLimits limits) {
        PlanResult planned = module.plan(params, limits);
        if (!planned.isPlanned()) {
            return new Attempt(OptionalInt.empty(), Optional.empty(), planned.code(), planned.args());
        }
        return sendPlanned(module, params, planned.plan().orElseThrow(), planned.sideEffects());
    }

    /** As {@link #sendPlanned(PlanModule, Object, BlockPlan, List)}, for a plan without side effects. */
    public <P> Attempt sendPlanned(PlanModule<P> module, P params, BlockPlan plan) {
        return sendPlanned(module, params, plan, List.of());
    }

    /**
     * Sends a {@code PlanRequest} for {@code plan} and its side effects (T-2: track), which the caller has already worked
     * out from {@code params} (the road tool works plans out off the client thread, R-2), with their hash; or nothing
     * while editing is off.
     */
    public <P> Attempt sendPlanned(PlanModule<P> module, P params, BlockPlan plan, List<SideEffect> sideEffects) {
        if (!this.editing) {
            return new Attempt(OptionalInt.empty(), Optional.of(plan), Optional.of(Protocol.EDITING_OFF), Map.of());
        }
        Digest hash = Digest.of(PlanHash.of(plan, sideEffects, this.coreVersion));
        int id = sendPlanRequest(module.id(), module.tool(params), module.encode(params), hash);
        return new Attempt(OptionalInt.of(id), Optional.of(plan), Optional.empty(), Map.of());
    }

    /**
     * Sends a {@code PlanRequest} exactly as given, with no local checks. For tests (the harness sends a wrong hash on
     * purpose); tools use {@link #request}.
     */
    public int sendPlanRequest(String module, String tool, byte[] params, Digest hash) {
        int id = nextId();
        this.channel.send(new PlanRequest(id, module, tool, params, hash));
        return id;
    }

    /** Asks the server to undo the player's latest operation; empty (nothing sent) while editing is off. */
    public OptionalInt undo() {
        if (!this.editing) {
            return OptionalInt.empty();
        }
        int id = nextId();
        this.channel.send(new Undo(id));
        return OptionalInt.of(id);
    }

    /** Asks the server to redo the player's latest undone operation; empty (nothing sent) while editing is off. */
    public OptionalInt redo() {
        if (!this.editing) {
            return OptionalInt.empty();
        }
        int id = nextId();
        this.channel.send(new Redo(id));
        return OptionalInt.of(id);
    }

    /**
     * Asks the server to run world task {@code task} (B-3: {@code Protocol.TASK_*}), with the road it is about, if any;
     * empty (nothing sent) while editing is off.
     */
    public OptionalInt task(String task, Optional<UUID> roadId) {
        if (!this.editing) {
            return OptionalInt.empty();
        }
        int id = nextId();
        this.channel.send(new TaskRequest(id, task, roadId));
        return OptionalInt.of(id);
    }

    private int nextId() {
        int id = this.nextRequestId;
        this.nextRequestId = id == Integer.MAX_VALUE ? 0 : id + 1;
        return id;
    }

    /** How many malformed records the server has sent. */
    public int malformedCount() {
        return this.malformed;
    }
}
