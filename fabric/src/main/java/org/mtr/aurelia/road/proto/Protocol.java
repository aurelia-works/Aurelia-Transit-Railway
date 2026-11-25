/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

/**
 * The fixed facts of the {@code aurelia:roads} protocol (editor-protocol §2–§4) and the reason codes core sends. Codes
 * double as i18n keys: protocol codes as {@code stratos.reject.<code in lowercase>}, road codes (roads-v1 §12) as
 * {@code stratos.road.reject.<code in lowercase>}.
 */
public final class Protocol {
    /**
     * The protocol number (ADR-0028). Editing needs equal numbers on the client and the server. Nothing has been
     * released yet, so it stays 1 until the first public build; from then on, every change to a record, its encoding or
     * its meaning bumps it.
     */
    public static final int VERSION = 1;

    /** The channel id; the platform layer frames the records on it. */
    public static final String CHANNEL = "aurelia:roads";

    /** The largest client → server message: about 30 KiB, below Minecraft 1.20.1's 32 KiB (editor-protocol §2). */
    public static final int MAX_TO_SERVER_BYTES = 30_720;

    /** The largest server → client message: 512 KiB, below Minecraft 1.20.1's 1 MiB. */
    public static final int MAX_TO_CLIENT_BYTES = 512 * 1024;

    /** The handshake offered by the server's Stratos and refused because the protocols differ (§9). */
    public static final String PROTOCOL_MISMATCH = "PROTOCOL_MISMATCH";
    /** A request from a client whose handshake hasn't enabled editing. */
    public static final String EDITING_OFF = "EDITING_OFF";
    /** The player may not do this; args {@code node}, the permission it needs (§7). */
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    /** The player's previous request is still being worked out or placed. */
    public static final String BUSY = "BUSY";
    /** F-6: the player is sending plan requests faster than the server's rate limit (§8). */
    public static final String RATE_LIMITED = "RATE_LIMITED";
    /** F-6: the plan changes more blocks than one edit may; args {@code blocks} and {@code max} (§8). Nothing was changed. */
    public static final String VOLUME_LIMIT = "VOLUME_LIMIT";
    /**
     * F-6: the plan would take the player past their blocks per minute; args {@code used} (blocks changed in the last
     * minute), {@code max} and {@code seconds} (until it would fit) (§8). Nothing was changed.
     */
    public static final String VOLUME_PER_MINUTE = "VOLUME_PER_MINUTE";
    /** The server's plan has a different hash; {@code serverPlanHash} is set (§5). */
    public static final String PLAN_HASH_MISMATCH = "PLAN_HASH_MISMATCH";
    /** Working out the plan took longer than the limit; args {@code seconds} (§5). */
    public static final String PLAN_TOO_COMPLEX = "PLAN_TOO_COMPLEX";
    /** A module, tool or option that later tasks add; args {@code feature} (an i18n key under {@code stratos.feature.}). */
    public static final String NOT_AVAILABLE_YET = "NOT_AVAILABLE_YET";
    /** The server failed while working out the plan (a bug); nothing was changed. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    /**
     * The world's Stratos data was saved by a newer Stratos (or game), so it is read-only here (F-5: the downgrade guard,
     * world-data-format §8): in {@code HelloResult}, and for every plan, undo and redo.
     */
    public static final String WORLD_READ_ONLY = "WORLD_READ_ONLY";

    /**
     * The capability a server's {@code Hello} lists when it can build hidden MTR track (T-2; every ATR server can).
     * The client offers the Track node aid only then (owner, 2026-10-08).
     */
    public static final String CAPABILITY_TRACK = "track";
    /** A plan with track sent to a server that can't build it ({@link #CAPABILITY_TRACK}); nothing was placed (T-2). */
    public static final String TRACK_UNAVAILABLE = "TRACK_UNAVAILABLE";
    /**
     * A {@code Notice} (T-2): some of a road's track wasn't built when it was placed or redone; args {@code count} (how many
     * lanes) and {@code total}. The road is placed anyway. The details go to the server's log.
     */
    public static final String TRACK_NOT_BUILT = "TRACK_NOT_BUILT";
    /**
     * A {@code Notice} (T-2): some of a road's track couldn't be removed when it was undone, so it is still there; args
     * {@code count} and {@code total}. The road is undone anyway (G-9; owner, 2026-10-08).
     */
    public static final String TRACK_NOT_REMOVED = "TRACK_NOT_REMOVED";
    /**
     * Stratos is repairing the world after a crash (F-2+): every plan, undo and redo is refused until it is done. A few
     * seconds after the world opens, for a big edit.
     */
    public static final String REPAIRING = "REPAIRING";
    /** The journal couldn't be written (a full disk), so the edit stopped where it was (F-2+): an apply's failure code. */
    public static final String JOURNAL_FAILED = "JOURNAL_FAILED";
    /**
     * A {@code Notice} (F-2+) to players who can edit: Stratos repaired the world after a crash; args {@code rolledBack} and
     * {@code completed}, the edits whose repair changed something.
     */
    public static final String REPAIRED = "REPAIRED";
    /** As {@link #REPAIRED}, when some couldn't be fully repaired; one more arg, {@code attention}. The log says what. */
    public static final String REPAIRED_ATTENTION = "REPAIRED_ATTENTION";
    /**
     * R-7: a delete or rebuild with no answer yet found blocks of the road changed by hand since Stratos placed them, so
     * nothing was written; arg {@code count}. The tool asks the player (overwrite them, or keep them) and sends the answer
     * as {@code manualEdits} in a new request (roads-v1 §12: an "ask").
     */
    public static final String MANUAL_EDITS = "MANUAL_EDITS";
    /** R-7: a delete or rebuild would change no block (the road is as it would be); nothing was written. */
    public static final String NOTHING_TO_CHANGE = "NOTHING_TO_CHANGE";

    /**
     * B-3's world task: bake every Stratos block in every dimension to vanilla ({@code bake.BakeMap}), each dimension's
     * as one operation its player can undo there. Needs {@code stratos.admin}.
     */
    public static final String TASK_BAKE_WORLD = "BAKE_WORLD";
    /** B-3's world task: bake the Stratos blocks of one of the player's roads ({@code roadId}) to vanilla, as one operation. */
    public static final String TASK_BAKE_ROAD = "BAKE_ROAD";
    /**
     * B-3's world task (owner, 2026-10-14): rebuild each of the player's roads whose palette paints line blocks, from its
     * record with today's rules, keeping blocks changed by hand; each road its own operation. Needs {@code stratos.admin}.
     */
    public static final String TASK_REDRAW_MARKINGS = "REDRAW_MARKINGS";
    /**
     * P9's world task: the world's traffic keeps left from now on, and each of the player's roads and junctions that keeps right
     * is rebuilt for it, junctions first, each its own operation. Needs {@code stratos.admin}.
     */
    public static final String TASK_DRIVE_LEFT = "DRIVE_LEFT";
    /** P9's world task: as {@link #TASK_DRIVE_LEFT}, for right-hand traffic. */
    public static final String TASK_DRIVE_RIGHT = "DRIVE_RIGHT";
    /**
     * P9's world task, the operator's override of {@link #TASK_DRIVE_LEFT}: every player's roads and junctions are rebuilt, not
     * only the sender's, each as an operation of the player who drew it (so they can undo it). Needs {@code stratos.admin}, as
     * the switch does.
     */
    public static final String TASK_DRIVE_LEFT_ALL = "DRIVE_LEFT_ALL";
    /** P9's world task: as {@link #TASK_DRIVE_LEFT_ALL}, for right-hand traffic. */
    public static final String TASK_DRIVE_RIGHT_ALL = "DRIVE_RIGHT_ALL";
    /** Whether {@code task} switches the world's driving side (for the sender's roads, or everyone's). */
    public static boolean isDriveTask(String task) {
        return task.equals(TASK_DRIVE_LEFT) || task.equals(TASK_DRIVE_RIGHT) || isDriveAllTask(task);
    }

    /** Whether {@code task} is the operator's override that switches every player's roads. */
    public static boolean isDriveAllTask(String task) {
        return task.equals(TASK_DRIVE_LEFT_ALL) || task.equals(TASK_DRIVE_RIGHT_ALL);
    }

    /** Whether the drive task {@code task} makes the world keep right. */
    public static boolean drivesRight(String task) {
        return task.equals(TASK_DRIVE_RIGHT) || task.equals(TASK_DRIVE_RIGHT_ALL);
    }
    /** A world task's stage: reading chunks for Stratos blocks. */
    public static final String STAGE_FINDING = "FINDING";
    /** A world task's stage: placing a bake's vanilla blocks. */
    public static final String STAGE_BAKING = "BAKING";
    /** A world task's stage: redrawing roads' markings. */
    public static final String STAGE_REDRAWING = "REDRAWING";
    /** A world task's stage: switching roads and junctions to the world's new driving side. */
    public static final String STAGE_SWITCHING = "SWITCHING";
    /**
     * P9: a road or junction for the other side than the world's was drawn or rebuilt; arg {@code side}, {@code left} or
     * {@code right}, the side the world keeps. Nothing was changed. After a switch, roads not yet converted still keep the old side.
     */
    public static final String DRIVING_SIDE_MISMATCH = "DRIVING_SIDE_MISMATCH";
    /** B-3: the world couldn't be read for Stratos blocks (the log says why); nothing was changed since. */
    public static final String FIND_FAILED = "FIND_FAILED";

    private Protocol() {
    }
}
