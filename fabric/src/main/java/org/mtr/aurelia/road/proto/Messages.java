/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mtr.aurelia.road.proto.Message.ApplyProgress;
import org.mtr.aurelia.road.proto.Message.ApplyState;
import org.mtr.aurelia.road.proto.Message.Direction;
import org.mtr.aurelia.road.proto.Message.Hello;
import org.mtr.aurelia.road.proto.Message.HelloAck;
import org.mtr.aurelia.road.proto.Message.HelloResult;
import org.mtr.aurelia.road.proto.Message.Limits;
import org.mtr.aurelia.road.proto.Message.Notice;
import org.mtr.aurelia.road.proto.Message.PlanAccepted;
import org.mtr.aurelia.road.proto.Message.PlanRejected;
import org.mtr.aurelia.road.proto.Message.PlanRequest;
import org.mtr.aurelia.road.proto.Message.Redo;
import org.mtr.aurelia.road.proto.Message.StratosBlocks;
import org.mtr.aurelia.road.proto.Message.TaskProgress;
import org.mtr.aurelia.road.proto.Message.TaskRequest;
import org.mtr.aurelia.road.proto.Message.TaskResult;
import org.mtr.aurelia.road.proto.Message.Undo;
import org.mtr.aurelia.road.proto.Message.UndoRedoResult;
import org.mtr.aurelia.road.proto.Message.Palettes;
import org.mtr.aurelia.road.proto.Message.YourRoads;

/**
 * Encodes and decodes {@link Message}s (editor-protocol §2): a VarInt message id, then the record's fields in the order
 * they are declared, in {@link ProtoWriter}'s encoding. Decoding checks the direction, the size limit, every field and
 * that nothing is left over; anything wrong throws {@link MalformedMessage} and nothing else.
 */
public final class Messages {
    private Messages() {
    }

    /** The bytes of {@code message}. Throws if they would be over its direction's size limit (a bug in Stratos). */
    public static byte[] encode(Message message) {
        ProtoWriter out = new ProtoWriter().varInt(message.id());
        if (message instanceof Hello hello) {
            out.varInt(hello.protocol()).string(hello.modVersion(), Message.MAX_NAME_BYTES).string(hello.coreVersion(), Message.MAX_NAME_BYTES)
                    .nonNegative(hello.dataVersion()).nonNegative(hello.formatVersion());
            names(out, hello.capabilities());
            out.nonNegative(hello.limits().maxBlocksPerOperation()).nonNegative(hello.limits().maxBlocksPerMinute());
        } else if (message instanceof HelloAck ack) {
            out.varInt(ack.protocol()).string(ack.modVersion(), Message.MAX_NAME_BYTES);
            names(out, ack.capabilities());
        } else if (message instanceof HelloResult result) {
            out.bool(result.editingEnabled()).optionalString(result.reasonCode(), Message.MAX_NAME_BYTES);
        } else if (message instanceof PlanRequest request) {
            out.nonNegative(request.requestId()).string(request.module(), Message.MAX_NAME_BYTES).string(request.tool(), Message.MAX_NAME_BYTES)
                    .bytes(request.params(), Protocol.MAX_TO_SERVER_BYTES).raw(request.clientPlanHash().bytes());
        } else if (message instanceof PlanAccepted accepted) {
            out.nonNegative(accepted.requestId()).uuid(accepted.operationId()).raw(accepted.planHash().bytes())
                    .nonNegative(accepted.blockCount()).nonNegative(accepted.sectionCount());
        } else if (message instanceof PlanRejected rejected) {
            out.nonNegative(rejected.requestId()).string(rejected.reasonCode(), Message.MAX_NAME_BYTES)
                    .stringMap(rejected.args(), Message.MAX_ARGS, Message.MAX_NAME_BYTES, Message.MAX_ARG_BYTES)
                    .present(rejected.serverPlanHash());
            rejected.serverPlanHash().ifPresent(hash -> out.raw(hash.bytes()));
        } else if (message instanceof ApplyProgress progress) {
            out.uuid(progress.operationId()).enumValue(progress.state()).nonNegative(progress.sectionsDone())
                    .nonNegative(progress.sectionsTotal()).optionalString(progress.reasonCode(), Message.MAX_NAME_BYTES);
        } else if (message instanceof Undo undo) {
            out.nonNegative(undo.requestId());
        } else if (message instanceof Redo redo) {
            out.nonNegative(redo.requestId());
        } else if (message instanceof UndoRedoResult result) {
            out.nonNegative(result.requestId()).optionalUuid(result.operationId()).bool(result.ok())
                    .optionalString(result.reasonCode(), Message.MAX_NAME_BYTES).nonNegative(result.keptByHand());
        } else if (message instanceof Notice notice) {
            out.string(notice.code(), Message.MAX_NAME_BYTES).stringMap(notice.args(), Message.MAX_ARGS, Message.MAX_NAME_BYTES, Message.MAX_ARG_BYTES);
        } else if (message instanceof YourRoads yours) {
            out.count(yours.roads(), YourRoads.MAX_ROADS);
            for (YourRoads.Road road : yours.roads()) {
                out.uuid(road.operationId()).string(road.module(), Message.MAX_NAME_BYTES).string(road.tool(), Message.MAX_NAME_BYTES)
                        .bytes(road.params(), Protocol.MAX_TO_SERVER_BYTES).bool(road.undone());
            }
        } else if (message instanceof Palettes palettes) {
            out.count(palettes.palettes(), Palettes.MAX_PALETTES);
            palettes.palettes().forEach(palette -> out.string(palette, Palettes.MAX_PALETTE_BYTES));
        } else if (message instanceof TaskRequest request) {
            out.nonNegative(request.requestId()).string(request.task(), Message.MAX_NAME_BYTES).optionalUuid(request.roadId());
        } else if (message instanceof TaskProgress progress) {
            out.nonNegative(progress.requestId()).string(progress.stage(), Message.MAX_NAME_BYTES).nonNegative(progress.done())
                    .nonNegative(progress.total());
        } else if (message instanceof TaskResult result) {
            out.nonNegative(result.requestId()).bool(result.ok()).optionalString(result.reasonCode(), Message.MAX_NAME_BYTES)
                    .stringMap(result.args(), Message.MAX_ARGS, Message.MAX_NAME_BYTES, Message.MAX_ARG_BYTES);
        } else if (message instanceof StratosBlocks blocks) {
            out.bool(blocks.present());
        } else {
            throw new IllegalArgumentException("Unknown message " + message);
        }
        if (out.size() > message.direction().maxBytes()) {
            throw new IllegalArgumentException(message.getClass().getSimpleName() + " would be " + out.size() + " bytes; the most "
                    + message.direction() + " is " + message.direction().maxBytes());
        }
        return out.toByteArray();
    }

    /** The message in {@code bytes}, which arrived travelling {@code direction}. */
    public static Message decode(byte[] bytes, Direction direction) throws MalformedMessage {
        if (bytes.length > direction.maxBytes()) {
            throw new MalformedMessage("a message of " + bytes.length + " bytes is over the " + direction + " limit of " + direction.maxBytes());
        }
        ProtoReader in = new ProtoReader(bytes);
        int id = in.varInt();
        Message message;
        try {
            message = switch (id) {
                case 0 -> new Hello(in.varInt(), in.string(Message.MAX_NAME_BYTES, "modVersion"), in.string(Message.MAX_NAME_BYTES, "coreVersion"),
                        in.nonNegative("dataVersion"), in.nonNegative("formatVersion"), names(in),
                        new Limits(in.nonNegative("maxBlocksPerOperation"), in.nonNegative("maxBlocksPerMinute")));
                case 1 -> new HelloAck(in.varInt(), in.string(Message.MAX_NAME_BYTES, "modVersion"), names(in));
                case 2 -> new HelloResult(in.bool(), in.optionalString(Message.MAX_NAME_BYTES, "reasonCode"));
                case 3 -> new PlanRequest(in.nonNegative("requestId"), in.string(Message.MAX_NAME_BYTES, "module"),
                        in.string(Message.MAX_NAME_BYTES, "tool"), in.bytes(Protocol.MAX_TO_SERVER_BYTES, "params"), digest(in, "clientPlanHash"));
                case 4 -> new PlanAccepted(in.nonNegative("requestId"), in.uuid(), digest(in, "planHash"), in.nonNegative("blockCount"),
                        in.nonNegative("sectionCount"));
                case 5 -> new PlanRejected(in.nonNegative("requestId"), in.string(Message.MAX_NAME_BYTES, "reasonCode"),
                        in.stringMap(Message.MAX_ARGS, Message.MAX_NAME_BYTES, Message.MAX_ARG_BYTES, "args"),
                        in.present() ? Optional.of(digest(in, "serverPlanHash")) : Optional.empty());
                case 6 -> new ApplyProgress(in.uuid(), in.enumValue(ApplyState.values(), "state"), in.nonNegative("sectionsDone"),
                        in.nonNegative("sectionsTotal"), in.optionalString(Message.MAX_NAME_BYTES, "reasonCode"));
                case 7 -> new Undo(in.nonNegative("requestId"));
                case 8 -> new Redo(in.nonNegative("requestId"));
                case 9 -> new UndoRedoResult(in.nonNegative("requestId"), in.optionalUuid(), in.bool(),
                        in.optionalString(Message.MAX_NAME_BYTES, "reasonCode"), in.nonNegative("keptByHand"));
                case 17 -> new Notice(in.string(Message.MAX_NAME_BYTES, "code"),
                        in.stringMap(Message.MAX_ARGS, Message.MAX_NAME_BYTES, Message.MAX_ARG_BYTES, "args"));
                case 18 -> yourRoads(in);
                case 19 -> palettes(in);
                case 20 -> new TaskRequest(in.nonNegative("requestId"), in.string(Message.MAX_NAME_BYTES, "task"), in.optionalUuid());
                case 21 -> new TaskProgress(in.nonNegative("requestId"), in.string(Message.MAX_NAME_BYTES, "stage"), in.nonNegative("done"),
                        in.nonNegative("total"));
                case 22 -> new TaskResult(in.nonNegative("requestId"), in.bool(), in.optionalString(Message.MAX_NAME_BYTES, "reasonCode"),
                        in.stringMap(Message.MAX_ARGS, Message.MAX_NAME_BYTES, Message.MAX_ARG_BYTES, "args"));
                case 23 -> new StratosBlocks(in.bool());
                default -> throw new MalformedMessage("unknown message id " + id);
            };
        } catch (IllegalArgumentException e) {
            // A field that passed its own check but breaks a rule across fields, such as sectionsDone > sectionsTotal.
            throw new MalformedMessage("message " + id + ": " + e.getMessage());
        }
        in.end();
        if (message.direction() != direction) {
            throw new MalformedMessage(message.getClass().getSimpleName() + " can't travel " + direction);
        }
        return message;
    }

    private static void names(ProtoWriter out, List<String> names) {
        out.count(names, Message.MAX_CAPABILITIES);
        names.forEach(name -> out.string(name, Message.MAX_NAME_BYTES));
    }

    private static List<String> names(ProtoReader in) throws MalformedMessage {
        int count = in.count(Message.MAX_CAPABILITIES, "capabilities");
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(in.string(Message.MAX_NAME_BYTES, "capability"));
        }
        return names;
    }

    private static Digest digest(ProtoReader in, String field) throws MalformedMessage {
        return Digest.of(in.raw(Digest.LENGTH, field));
    }

    private static Palettes palettes(ProtoReader in) throws MalformedMessage {
        int count = in.count(Palettes.MAX_PALETTES, "palettes");
        List<String> palettes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            palettes.add(in.string(Palettes.MAX_PALETTE_BYTES, "palette"));
        }
        return new Palettes(palettes);
    }

    private static YourRoads yourRoads(ProtoReader in) throws MalformedMessage {
        int count = in.count(YourRoads.MAX_ROADS, "roads");
        List<YourRoads.Road> roads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            roads.add(new YourRoads.Road(in.uuid(), in.string(Message.MAX_NAME_BYTES, "module"), in.string(Message.MAX_NAME_BYTES, "tool"),
                    in.bytes(Protocol.MAX_TO_SERVER_BYTES, "params"), in.bool()));
        }
        return new YourRoads(roads);
    }
}
