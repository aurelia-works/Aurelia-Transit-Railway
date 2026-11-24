/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.EditorClient;
import org.mtr.aurelia.road.proto.MalformedMessage;
import org.mtr.aurelia.road.proto.Message.YourRoads;
import org.mtr.aurelia.road.road.RoadHistory;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.road.nodes.NodeAids;
import org.mtr.aurelia.road.road.nodes.PlacedRoads;
import org.mtr.aurelia.road.road.tool.NodesTool;
import org.mtr.aurelia.road.spi.BlockReader;
import org.mtr.aurelia.road.tool.Commit;
import org.mtr.aurelia.road.tool.HudModel;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.Tool;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.fabric.client.editor.StratosEditorClient;
import org.mtr.aurelia.road.fabric.client.tool.StratosTools;
import org.mtr.aurelia.road.fabric.net.EditorNetworking;

/**
 * The node aids in the game (task R-6; owner, 2026-10-06; spec roads-v1 §9): core's {@link NodeAids} fed from the
 * client.
 * <ul>
 *   <li><b>Roads placed this visit:</b> each road a tool placed ({@link #placed}), set aside when its operation is
 *       undone and back when redone; all forgotten when leaving the world. On joining, and on a change of dimension, the
 *       server lists the player's roads they can still undo there (F-5: {@code YourRoads}), and those replace them.</li>
 *   <li><b>When the aids show</b> ({@link #visible()}): while the main hand holds MTR's Rail Node or a rail connector
 *       (runtime ids) and no Stratos tool is in hand, or while the Lane nodes tool is in hand.</li>
 *   <li><b>The checker</b> reads the client's blocks twice a second while they show.</li>
 *   <li><b>The overlay</b> ({@link NodeOverlay}) and the panel ({@link #panel()}, drawn by the tool HUD when no tool is
 *       in hand).</li>
 * </ul>
 * Client thread only.
 */
public final class StratosNodes {
    /** How often the checker reads the blocks while the aids show: twice a second (owner, 2026-10-06). */
    static final int REFRESH_TICKS = 10;

    private static final NodeAids AIDS = new NodeAids(new PlacedRoads());
    /** Works out the plans of the roads the server lists (F-5), off the client thread. */
    private static final ExecutorService ROAD_PLANNER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Stratos node aids");
        thread.setDaemon(true);
        return thread;
    });
    /** Raised by every list of roads and by leaving the world, so an older list's late result is dropped. */
    private static int roadsGeneration;
    private static int roadsRestored;
    private static boolean visible;
    private static int ticks;
    private static long refreshes;

    private StratosNodes() {
    }

    /** Hooks the aids into the game. Called once, from the client initialiser, after the editor client. */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StratosNodes::tick);
        StratosEditorClient.addUndoRedoListener((undo, result) -> {
            if (result.ok()) {
                result.operationId().ifPresent(id -> {
                    if (undo) {
                        AIDS.roads().undone(id);
                    } else {
                        AIDS.roads().redone(id);
                    }
                });
            }
        });
        StratosEditorClient.addListener(new EditorClient.Listener() {
            @Override
            public void yourRoads(YourRoads roads) {
                restore(roads);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            roadsGeneration++;
            AIDS.clear();
        }));
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(NodeOverlay::render);
    }

    /**
     * The server listed the player's roads they can still undo (F-5; owner, 2026-10-08): their plans are worked out on
     * the "Stratos node aids" thread, then they replace the roads the aids know.
     */
    private static void restore(YourRoads yours) {
        int generation = ++roadsGeneration;
        MinecraftClient client = MinecraftClient.getInstance();
        WorldLimits limits = client.world == null ? WorldLimits.OVERWORLD : EditorNetworking.limits(client.world);
        List<YourRoads.Road> listed = yours.roads();
        ROAD_PLANNER.execute(() -> {
            List<PlacedRoads.Known> known = new ArrayList<>();
            for (YourRoads.Road road : listed) {
                if (!road.module().equals(RoadsModule.INSTANCE.id())) {
                    continue;
                }
                try {
                    RoadPlanParams params = RoadsModule.INSTANCE.decode(road.tool(), road.params());
                    if (params.tool() == RoadPlanParams.Tool.DELETE) {
                        known.add(new PlacedRoads.Known(road.operationId(), params, null, road.undone())); // R-7: no plan of its own
                        continue;
                    }
                    RoadPlanner.asPlaced(params, limits).plan().ifPresent(plan -> known.add(new PlacedRoads.Known(road.operationId(), params, plan,
                            road.undone())));
                } catch (MalformedMessage | RuntimeException e) {
                    StratosMod.LOGGER.warn("[stratos] the node aids couldn't work out road {}: {}", road.operationId(), e.toString());
                }
            }
            client.execute(() -> {
                if (generation != roadsGeneration) {
                    return; // a newer list came, or the player left
                }
                AIDS.roads().replace(known);
                roadsRestored = known.size();
                if (client.world != null) {
                    AIDS.refresh(reader(client.world));
                    refreshes++;
                }
            });
        });
    }

    /** How many roads the last list from the server put back (the harness checks it after a restart). */
    public static int roadsRestored() {
        return roadsRestored;
    }

    /** The node aids' state (the Lane nodes tool shares it). */
    public static NodeAids aids() {
        return AIDS;
    }

    /**
     * A tool's commit was placed as {@code operationId}: a straight or curved road is remembered, and (R-7) a rebuild or
     * delete changes the road it edits.
     */
    public static void placed(Commit<?> commit, UUID operationId) {
        if (commit.params() instanceof RoadPlanParams params && RoadHistory.roadOf(operationId, params).isPresent()) {
            AIDS.roads().placed(operationId, params, commit.plan());
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                AIDS.refresh(reader(client.world)); // checked at once, so the panel has its counts
                refreshes++;
            }
        }
    }

    /** Whether the overlay and the panel show now. */
    public static boolean visible() {
        return visible;
    }

    /** How many frames have drawn the overlay (the harness checks it shows). */
    public static long overlayFrames() {
        return NodeOverlay.framesDrawn();
    }

    /** How many times the checker has read the blocks (the harness waits for a fresh read). */
    public static long refreshes() {
        return refreshes;
    }

    private static void tick(MinecraftClient client) {
        visible = client.world != null && client.player != null && shows(client);
        if (client.player != null) {
            AIDS.player(client.player.getX(), client.player.getZ());
        }
        if (visible && ++ticks >= REFRESH_TICKS) {
            ticks = 0;
            AIDS.refresh(reader(client.world));
            refreshes++;
        }
    }

    private static boolean shows(MinecraftClient client) {
        Optional<Tool> active = StratosTools.toolActive() ? StratosTools.session().active() : Optional.empty();
        if (active.isPresent()) {
            return active.get() instanceof NodesTool;
        }
        ItemStack held = client.player.getMainHandStack();
        return !held.isEmpty() && MtrNodes.isNodeItem(Registries.ITEM.getId(held.getItem()).toString());
    }

    /** The block the camera looks at (as far as the tools reach), for the panel. */
    static Optional<Target> aim(MinecraftClient client) {
        return StratosTools.toolActive() ? StratosTools.aim() : StratosTools.pick(client);
    }

    /** The camera's yaw. */
    static float yaw(MinecraftClient client) {
        Entity camera = client.getCameraEntity();
        return camera == null ? 0F : camera.getYaw();
    }

    /** The panel's model while the aids show and no tool is in hand: the Lane nodes panel, without key rows. */
    public static Optional<HudModel> panel() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!visible || StratosTools.toolActive() || client.world == null) {
            return Optional.empty();
        }
        Optional<NodeAids.Focus> focus = AIDS.focus(aim(client));
        return Optional.of(new HudModel("stratos.nodes.title", 0, 0, AIDS.state(focus, false), AIDS.lines(focus, yaw(client)), Optional.empty(),
                List.of()));
    }

    /** Reads the client's blocks as state strings, every property named; empty where the chunk isn't loaded here. */
    static BlockReader reader(ClientWorld level) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        return (x, y, z) -> {
            if (!level.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
                return Optional.empty();
            }
            return Optional.of(BlockArgumentParser.stringifyBlockState(level.getBlockState(cursor.set(x, y, z))));
        };
    }
}
