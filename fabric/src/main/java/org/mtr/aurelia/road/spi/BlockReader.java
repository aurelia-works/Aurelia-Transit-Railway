/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.spi;

import java.util.Optional;

/**
 * Reads blocks the client can see (task R-6: the node checker). Block states are strings, such as
 * {@code mtr:rail[facing=true,is_22_5=false,is_45=false,is_connected=false,waterlogged=false]}, with every property
 * named. Client thread only.
 */
@FunctionalInterface
public interface BlockReader {
    /** The state of the block at a position, or empty when its chunk isn't loaded here. */
    Optional<String> state(int x, int y, int z);
}
