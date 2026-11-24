/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.world;

import java.io.IOException;
import org.mtr.aurelia.road.spi.Storage;

/**
 * One step of the world-data format, {@code formatVersion} {@link #from()} to {@code from() + 1} (world-data-format §7;
 * task F-5): a pure function over the files under {@code stratos/}. {@link Migrations} backs the files up first and writes
 * {@code meta.json} after the last step.
 *
 * <p>A step must be safe to run again over its own partial output: a crash before {@code meta.json} is written leaves
 * the old version in {@code meta.json}, and the step simply runs again on the next start.
 */
public interface Migration {
    /** The format this step reads; it writes {@code from() + 1}. */
    int from();

    /** Rewrites the files under {@code storage} from {@link #from()} to the next format. Never writes {@code meta.json}. */
    void migrate(Storage storage) throws IOException;
}
