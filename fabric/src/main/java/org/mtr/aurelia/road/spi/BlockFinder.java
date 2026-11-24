/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.spi;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.mtr.aurelia.road.bake.Bake;

/**
 * Finds Stratos's blocks in a world (task B-3: bake to vanilla), in chunks saved or loaded, without loading any. The
 * platform reads each chunk as it is now: a loaded one from memory, any other from its saved data.
 */
public interface BlockFinder {
    /**
     * Starts looking in dimension {@code dimension}: every chunk saved or loaded there, or, with {@code cells}
     * ({@code BlockChange.positionKey}s), only those blocks. Server thread.
     */
    Search find(String dimension, Optional<Set<Long>> cells);

    /** A search under way. Server thread. */
    interface Search {
        /** Does a slice of the work that must happen on the server thread; called once a tick until {@link #finished}. */
        void tick();

        /** Chunks read so far. */
        int done();

        /** Chunks to read; 0 while they are still being listed. */
        int total();

        boolean finished();

        /** Why it failed, once finished, if it did. */
        Optional<String> failure();

        /** Every Stratos block found, once finished without failing. */
        List<Bake.Found> found();

        /** Stops it; it then finishes, failed. */
        void cancel();
    }
}
