/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.editor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.Digest;
import org.mtr.aurelia.road.proto.EditorClient;
import org.mtr.aurelia.road.proto.Message;
import org.mtr.aurelia.road.proto.Message.ApplyProgress;
import org.mtr.aurelia.road.proto.Message.ApplyState;
import org.mtr.aurelia.road.proto.Message.Notice;
import org.mtr.aurelia.road.proto.Message.PlanRejected;
import org.mtr.aurelia.road.proto.Message.UndoRedoResult;
import org.mtr.aurelia.road.proto.Messages;
import org.mtr.aurelia.road.proto.PlanModule;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.undo.SideEffect;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.fabric.net.EditorNetworking;
import org.mtr.aurelia.road.fabric.palette.GamePalettes;
import org.mtr.aurelia.road.fabric.net.StratosPacket;
import org.mtr.aurelia.road.fabric.client.nodes.StratosNodes;
import org.mtr.aurelia.road.fabric.client.tool.StratosTools;

/**
 * The client's end of {@code stratos:main}: frames core's {@link EditorClient} on Fabric's networking API, and tells the
 * player what happened: in the tool's HUD while a tool is in hand (R-1), otherwise in chat (refusals, from the lang file:
 * {@code stratos.road.reject.<code>} or {@code stratos.reject.<code>}) or the action bar (progress, undo and redo). From T-2
 * the server's {@code Notice}s too ({@code stratos.notice.<code>}: track that wasn't built or removed), after the answer
 * they follow, so they are what the player reads last.
 *
 * <p>Also the small API the road tool and the harness use: {@link #sendPlanned} (R-2), {@link #requestRoad},
 * {@link #undo}, {@link #redo}, {@link #addListener}. Client thread only.
 */
public final class StratosEditorClient {
    private static final List<Consumer<String>> MESSAGE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<BiConsumer<Boolean, UndoRedoResult>> UNDO_REDO_LISTENERS = new CopyOnWriteArrayList<>();
    /** Which requests were undos (true) or redos (false), to word their results. */
    private static final Map<Integer, Boolean> UNDO_REQUESTS = new HashMap<>();
    /** R-7: rebuild and delete requests by request id, then their operations: their progress is worded as such ("Deleted."). */
    private static final Map<Integer, String> EDIT_REQUESTS = new HashMap<>();
    private static final Map<java.util.UUID, String> EDIT_OPERATIONS = new HashMap<>();
    /** B-3: the world tasks the hub started, by request id (a tool's own, such as baking a road, answer in its HUD). */
    private static final Map<Integer, String> TASK_REQUESTS = new HashMap<>();
    private static final List<Consumer<Message.TaskResult>> TASK_LISTENERS = new CopyOnWriteArrayList<>();
    private static EditorClient core;

    private StratosEditorClient() {
    }

    /** Hooks the channel. Called once, from the client initialiser. */
    public static void register() {
        String modVersion = StratosMod.version(); // ATR's version: Stratos rides on the mtr mod
        core = new EditorClient(modVersion, CoreInfo.version(), List.of(RoadsModule.ID), message -> {
            if (ClientPlayNetworking.canSend(StratosPacket.TYPE)) {
                ClientPlayNetworking.send(new StratosPacket(Messages.encode(message)));
            }
        }, StratosMod.LOGGER::warn);
        core.addListener(new Feedback());
        ClientPlayNetworking.registerGlobalReceiver(StratosPacket.TYPE, (packet, player, sender) -> core.receive(packet.bytes()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            core.disconnected();
            UNDO_REQUESTS.clear();
            TASK_REQUESTS.clear();
            GamePalettes.setClient(org.mtr.aurelia.road.road.palette.Palettes.BUILT_IN); // R-9: the next server sends its own
        }));
    }

    /** Whether the server's handshake turned editing on. */
    public static boolean editingEnabled() {
        return core != null && core.editingEnabled();
    }

    /**
     * Whether the world has Stratos blocks, as the server last said (UI-1c: its mark); true until it says, and without a
     * server with Stratos (the safe side).
     */
    public static boolean stratosBlocks() {
        return core == null || core.stratosBlocks();
    }

    /** Whether the server's {@code Hello} listed {@code capability} (T-2: {@code track}, when it can build track). */
    public static boolean serverHas(String capability) {
        return core != null && core.serverHas(capability);
    }

    /** Works out a road's plan here and, if it is valid and editing is on, asks the server to place it. */
    public static EditorClient.Attempt requestRoad(RoadPlanParams params) {
        return core.request(RoadsModule.INSTANCE, params, limits());
    }

    /**
     * Asks the server to place {@code plan}, which the caller worked out from {@code params} (the road tool, off the client
     * thread: R-2), with its hash; nothing is sent while editing is off.
     */
    public static <P> EditorClient.Attempt sendPlanned(PlanModule<P> module, P params, BlockPlan plan) {
        return core.sendPlanned(module, params, plan);
    }

    /** As {@link #sendPlanned(PlanModule, Object, BlockPlan)}, with the plan's side effects (T-2: track) in its hash. */
    public static <P> EditorClient.Attempt sendPlanned(PlanModule<P> module, P params, BlockPlan plan, List<SideEffect> sideEffects) {
        EditorClient.Attempt attempt = core.sendPlanned(module, params, plan, sideEffects);
        if (attempt.isSent() && params instanceof RoadPlanParams road
                && (road.tool() == RoadPlanParams.Tool.REBUILD || road.tool() == RoadPlanParams.Tool.DELETE)) {
            EDIT_REQUESTS.put(attempt.requestId().getAsInt(), edited(road) ? "edit" : road.tool().name().toLowerCase(Locale.ROOT));
        }
        return attempt;
    }

    /**
     * Whether {@code request} is a rebuild that edits the road (R-10): other parameters than the road's as the node aids
     * know it, beyond R-9's other palette. Its progress is then worded as an edit ("Edited.").
     */
    private static boolean edited(RoadPlanParams request) {
        if (request.tool() != RoadPlanParams.Tool.REBUILD || request.targetRoadId().isEmpty()) {
            return false;
        }
        return StratosNodes.aids().roads().roads().stream().filter(road -> road.operationId().equals(request.targetRoadId().get())).findFirst()
                .map(road -> !road.params().road().equals(request.withPalette(road.params().paletteId()).road())).orElse(false);
    }

    /**
     * Sends a plan request exactly as given, with no checks here. For tests: the harness sends a wrong hash on purpose.
     * Returns the request id.
     */
    public static int sendPlanRequest(String module, String tool, byte[] params, byte[] hash) {
        return core.sendPlanRequest(module, tool, params, Digest.of(hash));
    }

    /**
     * Asks the server to run world task {@code task} (B-3: baking the world to vanilla, redrawing markings), worded in chat
     * as it goes; empty while editing is off.
     */
    public static OptionalInt task(String task) {
        OptionalInt id = core.task(task, Optional.empty());
        id.ifPresent(i -> TASK_REQUESTS.put(i, task));
        return id;
    }

    /** Sends a tool's world task (B-3: baking a road); its answers go to the tool, not to chat. */
    public static OptionalInt toolTask(String task, Optional<java.util.UUID> roadId) {
        return core.task(task, roadId);
    }

    /** Whether a world task the hub started is still running (B-3: the hub shows it). */
    public static boolean taskRunning() {
        return !TASK_REQUESTS.isEmpty();
    }

    /** Hears how every world task ended (B-3; the harness waits for it). */
    public static void addTaskListener(Consumer<Message.TaskResult> listener) {
        TASK_LISTENERS.add(listener);
    }

    public static void removeTaskListener(Consumer<Message.TaskResult> listener) {
        TASK_LISTENERS.remove(listener);
    }

    /** Asks the server to undo the player's latest operation; empty while editing is off. */
    public static OptionalInt undo() {
        OptionalInt id = core.undo();
        id.ifPresent(i -> UNDO_REQUESTS.put(i, true));
        return id;
    }

    /** Asks the server to redo the player's latest undone operation; empty while editing is off. */
    public static OptionalInt redo() {
        OptionalInt id = core.redo();
        id.ifPresent(i -> UNDO_REQUESTS.put(i, false));
        return id;
    }

    /** Hears the server's answers. */
    public static void addListener(EditorClient.Listener listener) {
        core.addListener(listener);
    }

    public static void removeListener(EditorClient.Listener listener) {
        core.removeListener(listener);
    }

    /**
     * Hears every undo or redo result, with whether it answered an undo (true) or a redo (R-6: the node aids forget an
     * undone road and remember a redone one).
     */
    public static void addUndoRedoListener(BiConsumer<Boolean, UndoRedoResult> listener) {
        UNDO_REDO_LISTENERS.add(listener);
    }

    /** Hears the text of every message shown to the player, as the player reads it. */
    public static void addMessageListener(Consumer<String> listener) {
        MESSAGE_LISTENERS.add(listener);
    }

    public static void removeMessageListener(Consumer<String> listener) {
        MESSAGE_LISTENERS.remove(listener);
    }

    private static WorldLimits limits() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == null ? WorldLimits.OVERWORLD : EditorNetworking.limits(client.world);
    }

    /**
     * The player's message for a refusal or warning code and its arguments, in order: {@code stratos.road.reject.<code>},
     * {@code stratos.reject.<code>} or {@code stratos.road.warn.<code>} (R-2), whichever exists. Ids in the arguments get
     * words: {@code feature}, and from R-3 the road {@code group} and {@code preset}.
     */
    public static Text reason(String code, Map<String, String> args) {
        Object[] values = args.entrySet().stream().map(entry -> switch (entry.getKey()) {
            case "feature" -> feature(entry.getValue());
            case "group" -> named("stratos.road.group.", entry.getValue());
            case "preset" -> named("stratos.road.preset.", entry.getValue());
            case "layout", "joins" -> named("stratos.road.profile.short.", entry.getValue()); // R-5
            default -> entry.getValue();
        }).toArray();
        String lower = code.toLowerCase(Locale.ROOT);
        for (String key : List.of("stratos.road.reject." + lower, "stratos.reject." + lower, "stratos.road.warn." + lower)) {
            if (I18n.hasTranslation(key)) {
                return Text.translatable(key, values);
            }
        }
        return Text.translatable("stratos.reject.unknown", code);
    }

    /** The words for an id under {@code prefix}, or the id itself if the lang file has none. */
    private static Object named(String prefix, String id) {
        String key = prefix + id;
        return I18n.hasTranslation(key) ? Text.translatable(key) : id;
    }

    private static Text feature(String feature) {
        String key = "stratos.feature." + feature;
        return I18n.hasTranslation(key) ? Text.translatable(key) : Text.literal(feature);
    }

    /**
     * Tells the player: in the tool's HUD while a tool is active (owner, 2026-10-05: R-1), otherwise in chat or, for
     * {@code actionBar}, above the hotbar.
     */
    private static void show(Text message, boolean actionBar, boolean error) {
        Text shown = Text.translatable("stratos.chat", message);
        MinecraftClient client = MinecraftClient.getInstance();
        if (!StratosTools.showInHud(message, error) && client.player != null) {
            client.player.sendMessage(shown, actionBar);
        }
        String text = shown.getString();
        MESSAGE_LISTENERS.forEach(listener -> listener.accept(text));
    }

    /** Puts the server's answers in front of the player. */
    private static final class Feedback implements EditorClient.Listener {
        @Override
        public void palettes(org.mtr.aurelia.road.road.palette.Palettes palettes) {
            GamePalettes.setClient(palettes); // R-9: the client plans with the server's palettes
        }

        @Override
        public void planAccepted(org.mtr.aurelia.road.proto.Message.PlanAccepted accepted) {
            String edit = EDIT_REQUESTS.remove(accepted.requestId());
            if (edit != null) {
                EDIT_OPERATIONS.put(accepted.operationId(), edit);
            }
        }

        @Override
        public void editingChanged(boolean enabled, Optional<String> reasonCode) {
            if (!enabled && reasonCode.filter(Protocol.PROTOCOL_MISMATCH::equals).isPresent()) {
                String server = core.serverHello().map(hello -> hello.modVersion()).orElse("?");
                show(Text.translatable("stratos.reject.protocol_mismatch", core.modVersion(), server), false, true);
            } else if (!enabled && reasonCode.filter(Protocol.WORLD_READ_ONLY::equals).isPresent()) {
                show(Text.translatable("stratos.reject.world_read_only"), false, true); // F-5: the downgrade guard
            }
        }

        @Override
        public void planRejected(PlanRejected rejected) {
            EDIT_REQUESTS.remove(rejected.requestId());
            if (rejected.reasonCode().equals(Protocol.PLAN_HASH_MISMATCH) && StratosTools.ownsRequest(rejected.requestId())) {
                // The tool works its plan out again and redraws the preview (editor-protocol §5, R-2).
                show(Text.translatable("stratos.reject.plan_hash_mismatch_refreshed"), false, true);
            } else {
                // R-7: a hand-edit question (the tool's box asks it) and "nothing to change" aren't errors.
                boolean error = !rejected.reasonCode().equals(Protocol.MANUAL_EDITS) && !rejected.reasonCode().equals(Protocol.NOTHING_TO_CHANGE);
                show(reason(rejected.reasonCode(), rejected.args()), false, error);
            }
        }

        @Override
        public void progress(ApplyProgress progress) {
            // R-7: a rebuild's or delete's progress says so ("stratos.progress.delete.done": "Deleted.").
            String edit = EDIT_OPERATIONS.get(progress.operationId());
            String prefix = edit == null ? "stratos.progress." : "stratos.progress." + edit + ".";
            if (progress.state() != ApplyState.APPLYING) {
                EDIT_OPERATIONS.remove(progress.operationId());
            }
            switch (progress.state()) {
                case APPLYING -> {
                    // Every section placed, the server still filing its undo images (F-2b).
                    boolean finishing = progress.sectionsTotal() > 0 && progress.sectionsDone() == progress.sectionsTotal();
                    show(finishing ? Text.translatable(prefix + "finishing")
                            : Text.translatable(prefix + "applying", progress.sectionsDone(), progress.sectionsTotal()), true, false);
                }
                case DONE -> show(Text.translatable(prefix + "done"), true, false);
                case FAILED, ROLLED_BACK -> show(reason(progress.reasonCode().orElse("UNKNOWN"), Map.of()), false, true);
                default -> {
                }
            }
        }

        @Override
        public void undoRedo(UndoRedoResult result) {
            boolean undo = UNDO_REQUESTS.getOrDefault(result.requestId(), true);
            UNDO_REQUESTS.remove(result.requestId());
            UNDO_REDO_LISTENERS.forEach(listener -> listener.accept(undo, result));
            if (!result.ok()) {
                show(reason(result.reasonCode().orElse("UNKNOWN"), Map.of()), false, true);
            } else if (undo && result.keptByHand() > 0) {
                show(Text.translatable("stratos.undo.done_kept", result.keptByHand()), false, false);
            } else {
                show(Text.translatable(undo ? "stratos.undo.done" : "stratos.redo.done"), true, false);
            }
        }

        @Override
        public void taskProgress(Message.TaskProgress progress) {
            if (!TASK_REQUESTS.containsKey(progress.requestId())) {
                return; // a tool's: its HUD says
            }
            Text text = switch (progress.stage()) {
                case Protocol.STAGE_FINDING -> progress.total() == 0 ? Text.translatable("stratos.task.progress.listing")
                        : Text.translatable("stratos.task.progress.finding", progress.done(), progress.total());
                case Protocol.STAGE_BAKING -> Text.translatable("stratos.task.progress.baking", progress.done(), progress.total());
                default -> Text.translatable("stratos.task.progress.redrawing", progress.done(), progress.total());
            };
            show(text, true, false);
        }

        @Override
        public void taskResult(Message.TaskResult result) {
            TASK_LISTENERS.forEach(listener -> listener.accept(result));
            String task = TASK_REQUESTS.remove(result.requestId());
            if (task == null) {
                return; // a tool's: its HUD says
            }
            if (!result.ok()) {
                show(reason(result.reasonCode().orElse("UNKNOWN"), result.args()), false, true);
                return;
            }
            Map<String, String> args = result.args();
            if (task.equals(Protocol.TASK_BAKE_WORLD)) {
                String blocks = args.getOrDefault("blocks", "0");
                String kept = args.getOrDefault("kept", "0");
                show(blocks.equals("0") ? Text.translatable("stratos.task.bake_world.none")
                        : kept.equals("0") ? Text.translatable("stratos.task.bake_world.done", blocks)
                        : Text.translatable("stratos.task.bake_world.done_kept", blocks, kept), false, false);
                return;
            }
            show(Text.translatable("stratos.task.redraw.done", args.getOrDefault("roads", "0"), args.getOrDefault("unchanged", "0")), false, false);
            if (!args.getOrDefault("others", "0").equals("0")) {
                show(Text.translatable("stratos.task.redraw.others", args.get("others")), false, false);
            }
            if (!args.getOrDefault("failed", "0").equals("0")) {
                show(Text.translatable("stratos.task.redraw.failed", args.get("failed")), false, true);
            }
        }

        @Override
        public void notice(Notice notice) {
            String key = "stratos.notice." + notice.code().toLowerCase(Locale.ROOT);
            Text text = I18n.hasTranslation(key) ? Text.translatable(key, notice.args().values().toArray())
                    : Text.translatable("stratos.notice.unknown", notice.code());
            show(text, false, true); // owner, 2026-10-08: a short message on screen (and the server's log has the details)
        }
    }
}
