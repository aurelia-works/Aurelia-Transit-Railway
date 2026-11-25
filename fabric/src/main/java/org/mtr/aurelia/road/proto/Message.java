/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The records of the {@code aurelia:roads} channel built so far: ids 0–9 of editor-protocol §4 (handshake, plan request,
 * accept and reject, apply progress, undo and redo), 17 ({@code Notice}, T-2), 18 ({@code YourRoads}, F-5), 19
 * ({@code Palettes}, R-9), 20–22 (world tasks, B-3), 23 ({@code StratosBlocks}, UI-1c) and 24
 * ({@code TrafficSide}, P9). Ids 10–16 (cancel, road-graph
 * data, transfers) arrive with the tasks that need them. {@link Messages} encodes and decodes them.
 *
 * <p>Every record checks its fields when it is made, with the same maximums the decoder enforces, so a record that
 * exists can always be sent.
 */
public sealed interface Message {
    /** Identifiers, versions, codes, module and tool names: at most 64 bytes of UTF-8. */
    int MAX_NAME_BYTES = 64;
    /** A reason's argument values: at most 256 bytes. */
    int MAX_ARG_BYTES = 256;
    /** At most 16 arguments per reason. */
    int MAX_ARGS = 16;
    /** At most 32 capabilities. */
    int MAX_CAPABILITIES = 32;

    /** Which way a record travels. */
    enum Direction {
        TO_SERVER(Protocol.MAX_TO_SERVER_BYTES),
        TO_CLIENT(Protocol.MAX_TO_CLIENT_BYTES);

        private final int maxBytes;

        Direction(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        /** The largest message allowed this way (editor-protocol §2). */
        public int maxBytes() {
            return this.maxBytes;
        }
    }

    /** The message id (editor-protocol §4). */
    int id();

    Direction direction();

    /** What the server tells the client about its volume limits (editor-protocol §8); 0 means no limit. F-6 sets them. */
    record Limits(int maxBlocksPerOperation, int maxBlocksPerMinute) {
        public static final Limits NONE = new Limits(0, 0);

        public Limits {
            Fields.nonNegative(maxBlocksPerOperation, "maxBlocksPerOperation");
            Fields.nonNegative(maxBlocksPerMinute, "maxBlocksPerMinute");
        }
    }

    /** Id 0, server → client, on join: the server's versions, capabilities and limits (§3). */
    record Hello(int protocol, String modVersion, String coreVersion, int dataVersion, int formatVersion, List<String> capabilities,
            Limits limits) implements Message {
        public Hello {
            Fields.name(modVersion, "modVersion");
            Fields.name(coreVersion, "coreVersion");
            Fields.nonNegative(dataVersion, "dataVersion");
            Fields.nonNegative(formatVersion, "formatVersion");
            capabilities = Fields.names(capabilities, MAX_CAPABILITIES, "capabilities");
            Objects.requireNonNull(limits, "limits");
        }

        @Override
        public int id() {
            return 0;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /** Id 1, client → server: the client's protocol, version and capabilities (§3). */
    record HelloAck(int protocol, String modVersion, List<String> capabilities) implements Message {
        public HelloAck {
            Fields.name(modVersion, "modVersion");
            capabilities = Fields.names(capabilities, MAX_CAPABILITIES, "capabilities");
        }

        @Override
        public int id() {
            return 1;
        }

        @Override
        public Direction direction() {
            return Direction.TO_SERVER;
        }
    }

    /** Id 2, server → client: whether the client may edit, and why not ({@code PROTOCOL_MISMATCH}) if it may not. */
    record HelloResult(boolean editingEnabled, Optional<String> reasonCode) implements Message {
        public HelloResult {
            Fields.optionalName(reasonCode, "reasonCode");
        }

        @Override
        public int id() {
            return 2;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /**
     * Id 3, client → server: a plan to work out from a module's parameters ({@code params}, the module's own record,
     * such as the roads module's {@code RoadPlanParams}) and the hash of the plan the client worked out (§5).
     */
    record PlanRequest(int requestId, String module, String tool, byte[] params, Digest clientPlanHash) implements Message {
        public PlanRequest {
            Fields.nonNegative(requestId, "requestId");
            Fields.name(module, "module");
            Fields.name(tool, "tool");
            if (params.length > Protocol.MAX_TO_SERVER_BYTES) {
                throw new IllegalArgumentException("params has " + params.length + " bytes; the most is " + Protocol.MAX_TO_SERVER_BYTES);
            }
            params = params.clone();
            Objects.requireNonNull(clientPlanHash, "clientPlanHash");
        }

        @Override
        public byte[] params() {
            return this.params.clone();
        }

        @Override
        public int id() {
            return 3;
        }

        @Override
        public Direction direction() {
            return Direction.TO_SERVER;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PlanRequest that && this.requestId == that.requestId && this.module.equals(that.module)
                    && this.tool.equals(that.tool) && Arrays.equals(this.params, that.params) && this.clientPlanHash.equals(that.clientPlanHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.requestId, this.module, this.tool, Arrays.hashCode(this.params), this.clientPlanHash);
        }

        @Override
        public String toString() {
            return "PlanRequest[requestId=" + this.requestId + ", module=" + this.module + ", tool=" + this.tool + ", params="
                    + this.params.length + " bytes, clientPlanHash=" + this.clientPlanHash + "]";
        }
    }

    /** Id 4, server → client: the server's plan matched and is queued as operation {@code operationId}. */
    record PlanAccepted(int requestId, UUID operationId, Digest planHash, int blockCount, int sectionCount) implements Message {
        public PlanAccepted {
            Fields.nonNegative(requestId, "requestId");
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(planHash, "planHash");
            Fields.nonNegative(blockCount, "blockCount");
            Fields.nonNegative(sectionCount, "sectionCount");
        }

        @Override
        public int id() {
            return 4;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /**
     * Id 5, server → client: the request was refused with nothing changed. {@code args} fill in the message, in order;
     * {@code serverPlanHash} is set for {@code PLAN_HASH_MISMATCH}.
     */
    record PlanRejected(int requestId, String reasonCode, Map<String, String> args, Optional<Digest> serverPlanHash) implements Message {
        public PlanRejected {
            Fields.nonNegative(requestId, "requestId");
            Fields.name(reasonCode, "reasonCode");
            args = Fields.args(args);
            Objects.requireNonNull(serverPlanHash, "serverPlanHash");
        }

        @Override
        public int id() {
            return 5;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /** Where an operation is (§4). {@code QUEUED} and {@code ROLLED_BACK} come with the journal (F-2+). */
    enum ApplyState {
        QUEUED,
        APPLYING,
        DONE,
        ROLLED_BACK,
        FAILED
    }

    /**
     * Id 6, server → client: an operation's progress, about once a second while it runs and once when it ends. A
     * {@code FAILED} operation carries its reason code (the apply engine's, such as {@code BLOCK_ENTITY}).
     */
    record ApplyProgress(UUID operationId, ApplyState state, int sectionsDone, int sectionsTotal, Optional<String> reasonCode)
            implements Message {
        public ApplyProgress {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(state, "state");
            Fields.nonNegative(sectionsDone, "sectionsDone");
            Fields.nonNegative(sectionsTotal, "sectionsTotal");
            if (sectionsDone > sectionsTotal) {
                throw new IllegalArgumentException("sectionsDone (" + sectionsDone + ") is more than sectionsTotal (" + sectionsTotal + ")");
            }
            Fields.optionalName(reasonCode, "reasonCode");
        }

        @Override
        public int id() {
            return 6;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /** Id 7, client → server: undo the player's latest operation. */
    record Undo(int requestId) implements Message {
        public Undo {
            Fields.nonNegative(requestId, "requestId");
        }

        @Override
        public int id() {
            return 7;
        }

        @Override
        public Direction direction() {
            return Direction.TO_SERVER;
        }
    }

    /** Id 8, client → server: redo the player's latest undone operation. */
    record Redo(int requestId) implements Message {
        public Redo {
            Fields.nonNegative(requestId, "requestId");
        }

        @Override
        public int id() {
            return 8;
        }

        @Override
        public Direction direction() {
            return Direction.TO_SERVER;
        }
    }

    /**
     * Id 9, server → client: how an undo or redo ended. {@code keptByHand} counts the blocks an undo left alone because
     * they were changed by hand since (owner, 2026-10-05: F-2).
     */
    record UndoRedoResult(int requestId, Optional<UUID> operationId, boolean ok, Optional<String> reasonCode, int keptByHand)
            implements Message {
        public UndoRedoResult {
            Fields.nonNegative(requestId, "requestId");
            Objects.requireNonNull(operationId, "operationId");
            Fields.optionalName(reasonCode, "reasonCode");
            Fields.nonNegative(keptByHand, "keptByHand");
        }

        @Override
        public int id() {
            return 9;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /**
     * Id 17, server → client: something the player should know that isn't the answer to a request, as a code and its
     * arguments, in order (shown as {@code stratos.notice.<code in lowercase>}). From T-2: track that wasn't built or
     * removed ({@code TRACK_NOT_BUILT}, {@code TRACK_NOT_REMOVED}), sent after the operation's own answer.
     */
    record Notice(String code, Map<String, String> args) implements Message {
        public Notice {
            Fields.name(code, "code");
            args = Fields.args(args);
        }

        @Override
        public int id() {
            return 17;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /**
     * Id 18, server → client (F-5): the player's roads in their dimension that they can still undo or redo, in the order
     * they were made, so the node aids know them after a restart (owner, 2026-10-08). Sent after the handshake and when
     * the player changes dimension; it replaces the client's list.
     */
    record YourRoads(List<Road> roads) implements Message {
        /** At most this many: the undo cap's 200 operations, twice over for parts split off by a stopped undo. */
        public static final int MAX_ROADS = 400;

        public YourRoads {
            if (roads.size() > MAX_ROADS) {
                throw new IllegalArgumentException("roads has " + roads.size() + " entries; the most is " + MAX_ROADS);
            }
            roads = List.copyOf(roads);
        }

        /** One road: its operation, the module and tool that planned it, its parameters (the module's encoding), and whether it is undone. */
        public record Road(UUID operationId, String module, String tool, byte[] params, boolean undone) {
            public Road {
                Objects.requireNonNull(operationId, "operationId");
                Fields.name(module, "module");
                Fields.name(tool, "tool");
                if (params.length > Protocol.MAX_TO_SERVER_BYTES) {
                    throw new IllegalArgumentException("params has " + params.length + " bytes; the most is " + Protocol.MAX_TO_SERVER_BYTES);
                }
                params = params.clone();
            }

            @Override
            public byte[] params() {
                return this.params.clone();
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof Road that && this.operationId.equals(that.operationId) && this.module.equals(that.module)
                        && this.tool.equals(that.tool) && Arrays.equals(this.params, that.params) && this.undone == that.undone;
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.operationId, this.module, this.tool, Arrays.hashCode(this.params), this.undone);
            }

            @Override
            public String toString() {
                return "Road[operationId=" + this.operationId + ", module=" + this.module + ", tool=" + this.tool + ", params=" + this.params.length
                        + " bytes, undone=" + this.undone + "]";
            }
        }

        @Override
        public int id() {
            return 18;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /**
     * Id 19, server → client (R-9): the palettes roads may use on this server, as it resolved them, each as its JSON (the
     * palette file format with one candidate per role, {@code Palette.json}), in the hub's order. Sent after the handshake,
     * before {@code YourRoads}; the client plans with exactly these, so its plans hash as the server's do.
     */
    record Palettes(List<String> palettes) implements Message {
        /** At most this many palettes. */
        public static final int MAX_PALETTES = 64;
        /** At most this many bytes of UTF-8 per palette. */
        public static final int MAX_PALETTE_BYTES = 8192;

        public Palettes {
            if (palettes.size() > MAX_PALETTES) {
                throw new IllegalArgumentException("palettes has " + palettes.size() + " entries; the most is " + MAX_PALETTES);
            }
            for (String palette : palettes) {
                if (palette.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PALETTE_BYTES) {
                    throw new IllegalArgumentException("a palette is longer than " + MAX_PALETTE_BYTES + " bytes");
                }
            }
            palettes = List.copyOf(palettes);
        }

        @Override
        public int id() {
            return 19;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /**
     * Id 20, client → server (B-3): start a world task, {@code task} being one of {@link Protocol#TASK_BAKE_WORLD},
     * {@link Protocol#TASK_BAKE_ROAD} (with the road's id), {@link Protocol#TASK_REDRAW_MARKINGS}, {@link Protocol#TASK_DRIVE_LEFT},
     * {@link Protocol#TASK_DRIVE_RIGHT} and their operator override {@code _ALL} forms. Answered with
     * {@code TaskProgress} while it runs and one {@code TaskResult}.
     */
    record TaskRequest(int requestId, String task, Optional<UUID> roadId) implements Message {
        public TaskRequest {
            Fields.nonNegative(requestId, "requestId");
            Fields.name(task, "task");
            Objects.requireNonNull(roadId, "roadId");
        }

        @Override
        public int id() {
            return 20;
        }

        @Override
        public Direction direction() {
            return Direction.TO_SERVER;
        }
    }

    /**
     * Id 21, server → client (B-3): how far a world task has got, about once a second: {@code stage} is
     * {@link Protocol#STAGE_FINDING} (chunks read of the chunks to read), {@link Protocol#STAGE_BAKING} (sections done of
     * the operation's sections) or {@link Protocol#STAGE_REDRAWING} (roads done of the roads to redraw) or {@link Protocol#STAGE_SWITCHING} (roads done of the roads to switch).
     */
    record TaskProgress(int requestId, String stage, int done, int total) implements Message {
        public TaskProgress {
            Fields.nonNegative(requestId, "requestId");
            Fields.name(stage, "stage");
            Fields.nonNegative(done, "done");
            Fields.nonNegative(total, "total");
            if (done > total) {
                throw new IllegalArgumentException("done (" + done + ") is more than total (" + total + ")");
            }
        }

        @Override
        public int id() {
            return 21;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /**
     * Id 22, server → client (B-3): how a world task ended. {@code ok} with what it did in {@code args} (a bake's
     * {@code blocks} and {@code kept}; a redraw's {@code roads}, {@code unchanged}, {@code others} and {@code failed}), or
     * refused or stopped with {@code reasonCode} (anything done so far can be undone).
     */
    record TaskResult(int requestId, boolean ok, Optional<String> reasonCode, Map<String, String> args) implements Message {
        public TaskResult {
            Fields.nonNegative(requestId, "requestId");
            Fields.optionalName(reasonCode, "reasonCode");
            args = Fields.args(args);
        }

        @Override
        public int id() {
            return 22;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /**
     * Id 23, server → client (UI-1c): whether the world has Stratos blocks, by the server's mark (on once any Stratos block
     * is placed, off when Bake world leaves none), for the hub's This world box. Sent after the handshake, after
     * {@code YourRoads}, and again whenever the mark changes.
     */
    record StratosBlocks(boolean present) implements Message {
        @Override
        public int id() {
            return 23;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }

    /**
     * Id 24, server → client (P9): the world's driving side, which new roads take. Sent after the handshake when the world
     * keeps to the right (left is what a client assumes until told), and to every player who can edit when it changes. A
     * client from before P9 drops it as an unknown id and keeps building left-hand roads.
     */
    record TrafficSide(boolean rightHand) implements Message {
        @Override
        public int id() {
            return 24;
        }

        @Override
        public Direction direction() {
            return Direction.TO_CLIENT;
        }
    }
}
