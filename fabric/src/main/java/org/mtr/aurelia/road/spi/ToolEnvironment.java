/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.spi;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Future;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.EditorClient;
import org.mtr.aurelia.road.proto.PlanModule;
import org.mtr.aurelia.road.tool.Commit;
import org.mtr.aurelia.road.tool.WorldTask;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * What the platform gives the tool session (task R-2; spec mod-suite-v1 §5): the world's limits, a thread to work plans
 * out on (plans never block a frame, §7), the way back to the client thread, and a way to send a plan to the server.
 */
public interface ToolEnvironment {
    /** The current world's build height and border. Client thread. */
    WorldLimits limits();

    /** Starts {@code work} on the tool planner thread. Cancelling the returned future (with interruption) stops it. */
    Future<?> background(Runnable work);

    /** Runs {@code task} on the client thread, after the work in hand. Called from the tool planner thread. */
    void onClientThread(Runnable task);

    /**
     * Sends a plan request for {@code plan} and its side effects (T-2: track) with their hash, or says why it wasn't sent.
     * Client thread.
     */
    <P> EditorClient.Attempt send(PlanModule<P> module, P params, BlockPlan plan, List<SideEffect> sideEffects);

    /**
     * Sends a world task's request (B-3), returning its request id, or nothing while editing is off. Client thread.
     */
    default OptionalInt sendTask(WorldTask task) {
        return OptionalInt.empty();
    }

    /** Reports something that went wrong while working a plan out (a bug). Any thread. */
    void problem(String message, Throwable error);

    /**
     * A tool's commit was placed in full as operation {@code operationId} (R-6: the road tools' roads are remembered for
     * the node aids). Client thread; whether or not the tool is still in hand.
     */
    default void placed(Commit<?> commit, UUID operationId) {
    }
}
