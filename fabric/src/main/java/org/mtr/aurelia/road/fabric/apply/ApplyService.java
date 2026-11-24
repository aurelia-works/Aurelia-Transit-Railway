/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.apply;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.journal.Recovery;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.UndoStore;
import org.mtr.aurelia.road.world.Migrations;
import org.mtr.aurelia.road.world.WorldData;
import org.mtr.aurelia.road.fabric.StratosMod;

/**
 * Runs Stratos's edits on the server (spec mod-suite-v1 §6.5–6.6): per dimension, the apply engine and each player's undo
 * and redo history ({@link Operations}), run at the end of every server tick with {@link #BUDGET_NANOS} in total across
 * all dimensions. Works the same on a dedicated server and in singleplayer. Server thread only.
 *
 * <p><b>Saved with the world (F-5):</b> when the server starts, the world's Stratos data ({@link WorldData}: records, undo
 * history, {@code meta.json}) is loaded, read-only if it is newer than this Stratos, and the {@code stratos_meta} record
 * ({@link StratosSavedData}) makes every world save write it. {@code -Dstratos.debug.undoMemoryBytes=<n>} lowers the
 * in-memory budget for undo images (the GameTests use 0, so every image goes through the disk).
 *
 * <p><b>Side effects (T-2):</b> the operations' hidden track is carried out by the server's track writer
 * ({@link SideEffects}), after each operation's blocks.
 *
 * <p><b>The helper (F-2b):</b> the work that grows with an edit (building its job, unpacking and compressing its undo
 * images, hashing its sections) and all of Stratos's writing (undo images, saves) run on one daemon thread, "Stratos
 * helper" ({@link HelperThread}), made with the world's data and stopped once the server has stopped. When the server
 * stops, {@link Operations#stop} files what was placed and {@link WorldData#stop} finishes the writing, before the last
 * save, which is then written on the server thread.
 *
 * <p><b>The journal (F-2+):</b> loading the world's data reads its journal; once the server has started, the repair after
 * a crash (if any) starts in each dimension it needs ({@link WorldData#startRepair}), and when it is done its report goes
 * to the {@link #addRepairListener repair listeners} (the network layer's notice). The journal thread is the helper's
 * second thread. Once the server has stopped (every chunk written), {@link WorldData#stopped} deletes the journal if the
 * last save was written.
 */
public final class ApplyService {
    /** At most 5 ms of Stratos block placing per tick (spec mod-suite-v1 §6.5, §7). */
    public static final long BUDGET_NANOS = 5_000_000L;

    /**
     * When the engine stops starting new work in a tick: 0.5 ms before the budget runs out, so a slow block or a short
     * pause of the server thread still ends within {@link #BUDGET_NANOS}. Measured in F-1's GameTest: without the spare
     * time, about one run in five had a slice a fraction of a millisecond over. Longer pauses (garbage collection, the OS)
     * are outside Stratos's control; the GameTest names them and tolerates them when rare (owner, 2026-10-05).
     */
    public static final long START_BUDGET_NANOS = BUDGET_NANOS - 500_000L;

    /** A finished apply, undo or redo, for whoever needs to hear about it (the network layer, the GameTests). */
    public record Finished(ServerWorld level, Operations.Result result) {
    }

    private static final Map<ServerWorld, World> WORLDS = new LinkedHashMap<>();
    private static final List<Consumer<Finished>> LISTENERS = new ArrayList<>();
    private static final List<Consumer<Recovery.Report>> REPAIR_LISTENERS = new ArrayList<>();
    /** The world's Stratos data, from the server's start until it has stopped (its last save needs it). */
    private static WorldData data;
    /** The world's {@code stratos_meta} record, loaded with {@link #data}. */
    private static StratosSavedData saved;
    /** The "Stratos helper" thread (F-2b), made with {@link #data} and stopped with it. */
    private static HelperThread helper;

    private record World(FabricWorldWriter writer, Operations operations) {
    }

    private ApplyService() {
    }

    /** Hooks the service into the server's start, tick and shutdown. Called once, from the mod initialiser. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ApplyService::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(ApplyService::started);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (data != null) {
                data.stopped(); // F-2+: every chunk is written now; a clean stop's journal goes
            }
            WORLDS.clear();
            data = null;
            saved = null;
            if (helper != null) {
                helper.shutdownNow(); // nothing is left for it: the stop filed everything on the server thread
                helper = null;
            }
            SideEffects.stopped();
        });
    }

    /** Applies {@code plan} in {@code level} as {@code owner}'s next operation (a player's UUID as text). */
    public static Operations.Request apply(ServerWorld level, String owner, BlockPlan plan) {
        return world(level).operations().apply(owner, plan);
    }

    /** Undoes {@code owner}'s latest operation in {@code level}. */
    public static Operations.Request undo(ServerWorld level, String owner) {
        return world(level).operations().undo(owner);
    }

    /** Redoes {@code owner}'s latest undone operation in {@code level}. */
    public static Operations.Request redo(ServerWorld level, String owner) {
        return world(level).operations().redo(owner);
    }

    /** {@code level}'s apply engine and per-player undo histories, for the network layer (F-3). */
    public static Operations operations(ServerWorld level) {
        return world(level).operations();
    }

    /** The world's Stratos data (F-5), loaded on first use. */
    public static WorldData data(MinecraftServer server) {
        if (data == null) {
            data = load(server);
        }
        return data;
    }

    /** Whether the world has Stratos blocks, by the mark in its {@code stratos_meta} record (UI-1c). */
    public static boolean stratosBlocks(MinecraftServer server) {
        data(server);
        return saved.stratosBlocks();
    }

    /** Sets the world's mark for Stratos blocks (UI-1c), saved with the world. */
    public static void stratosBlocks(MinecraftServer server, boolean present) {
        data(server);
        saved.stratosBlocks(present);
    }

    /** Hears about every finished apply, undo and redo. */
    public static void addListener(Consumer<Finished> listener) {
        LISTENERS.add(listener);
    }

    /** Hears once when a repair after a crash has finished and changed something, or needs attention (F-2+). */
    public static void addRepairListener(Consumer<Recovery.Report> listener) {
        REPAIR_LISTENERS.add(listener);
    }

    /**
     * The server has started: the world's data is loaded, and a repair after a crash (F-2+) starts in each dimension it
     * needs. Those dimensions' worlds are made now, so their apply engines tick.
     */
    private static void started(MinecraftServer server) {
        WorldData loaded = data(server);
        Map<String, ServerWorld> levels = new LinkedHashMap<>();
        for (ServerWorld level : server.getWorlds()) {
            levels.put(dimension(level), level);
        }
        for (String dimension : loaded.repairDimensions()) {
            ServerWorld level = levels.get(dimension);
            if (level != null) {
                world(level);
            }
        }
        loaded.startRepair(levels::containsKey);
    }

    private static World world(ServerWorld level) {
        return WORLDS.computeIfAbsent(level, l -> new World(new FabricWorldWriter(l), data(l.getServer()).operations(dimension(l))));
    }

    /** A dimension's name in Stratos's files, such as {@code minecraft:overworld}. */
    public static String dimension(ServerWorld level) {
        return level.getRegistryKey().getValue().toString();
    }

    private static WorldData load(MinecraftServer server) {
        FabricWorldWriter.warmUp();
        long memory = Long.getLong("stratos.debug.undoMemoryBytes", UndoStore.DEFAULT_MEMORY_BYTES);
        helper = new HelperThread();
        // The track writer (T-2): none until the MTR track writer is wired in.
        WorldData.Settings settings = new WorldData.Settings(memory, UndoStore.DEFAULT_MAX_OPERATIONS, UndoStore.DEFAULT_MAX_BYTES, UUID::randomUUID,
                SideEffects.writer(server), Instant::now, SharedConstants.getGameVersion().getSaveVersion().getId(),
                "stratos " + StratosMod.version(), helper);
        WorldData loaded = WorldData.load(FabricStorage.of(server), settings, Migrations.ALL, line -> StratosMod.LOGGER.warn(line));
        saved = server.getOverworld().getPersistentStateManager().getOrCreate(StratosSavedData::load, StratosSavedData::create, StratosSavedData.NAME);
        if (saved.formatVersion() > CoreInfo.FORMAT_VERSION) {
            loaded.makeReadOnly(WorldData.ReadOnly.NEWER_FORMAT, "its stratos_meta record has formatVersion " + saved.formatVersion()
                    + "; this Stratos reads " + CoreInfo.FORMAT_VERSION);
        }
        saved.attach(loaded);
        return loaded;
    }

    private static void tick(MinecraftServer server) {
        long left = START_BUDGET_NANOS;
        for (Map.Entry<ServerWorld, World> entry : WORLDS.entrySet()) {
            World world = entry.getValue();
            if (left > 0 && !world.operations().scheduler().isIdle()) {
                world.writer().forgetReads(); // nothing remembered from an earlier tick (F-1+)
                left -= world.operations().scheduler().tick(world.writer(), left, System::nanoTime);
            }
            for (Operations.Result result : world.operations().afterTick()) {
                Finished finished = new Finished(entry.getKey(), result);
                LISTENERS.forEach(listener -> listener.accept(finished));
            }
        }
        if (data != null) {
            data.afterTick(); // saves and image writes the helper has finished (F-2b); the repair (F-2+)
            data.takeRepairReport().ifPresent(report -> REPAIR_LISTENERS.forEach(listener -> listener.accept(report)));
        }
    }

    /**
     * Stops every job and releases its chunks, files what was placed and finishes the writing on this thread (F-2b:
     * {@link Operations#stop}, {@link WorldData#stop}); the worlds are about to be saved one last time (which writes
     * Stratos's data through {@link StratosSavedData}, on this thread and complete) and go away.
     */
    private static void stopAll() {
        for (World world : WORLDS.values()) {
            world.operations().stop(world.writer());
        }
        if (data != null) {
            data.stop();
        }
    }
}
