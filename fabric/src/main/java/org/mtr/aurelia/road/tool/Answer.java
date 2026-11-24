/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What became of a tool's {@link Commit} (task R-2), in order: {@link NotSent}, or {@link Sent} and then the server's
 * answers ({@link Rejected}, or {@link Accepted}, any {@link Progress}, then {@link Done} or {@link Failed}). The player
 * reads the server's answers in the HUD's message strip (R-1), so a tool only changes its state.
 *
 * <p>From B-3 a tool may start a world task instead ({@link Tool#takeTask}): {@link NotSent}, or {@link Sent}, any
 * {@link Progress}, then {@link TaskDone} or {@link Rejected} (refused, or stopped).
 */
public sealed interface Answer {
    /** Refused before anything was sent: by the client's own planner, or because editing is off. */
    record NotSent(String code, Map<String, String> args) implements Answer {
        public NotSent {
            Objects.requireNonNull(code, "code");
            args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
        }
    }

    /** Sent as request {@code requestId}. */
    record Sent(int requestId) implements Answer {
    }

    /** The server refused it, such as {@code PLAN_HASH_MISMATCH} or {@code BUSY}. Nothing was changed. */
    record Rejected(String code, Map<String, String> args) implements Answer {
        public Rejected {
            Objects.requireNonNull(code, "code");
            args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
        }
    }

    /** The server accepted it and is placing it. */
    record Accepted(int blockCount, int sectionCount) implements Answer {
    }

    /** Placing has reached {@code sectionsDone} of {@code sectionsTotal}. */
    record Progress(int sectionsDone, int sectionsTotal) implements Answer {
    }

    /** Placed. */
    record Done() implements Answer {
    }

    /** B-3: the world task ended well, having done what {@code args} say ({@code TaskResult}'s). */
    record TaskDone(Map<String, String> args) implements Answer {
        public TaskDone {
            args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
        }
    }

    /** Placing stopped, for {@code code}; what was placed so far can be undone. */
    record Failed(String code) implements Answer {
        public Failed {
            Objects.requireNonNull(code, "code");
        }
    }
}
