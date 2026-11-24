/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.tool;

import net.minecraft.client.util.InputUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.preview.PreviewChoices;
import org.mtr.aurelia.road.proto.EditorClient;
import org.mtr.aurelia.road.proto.PlanModule;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.road.tool.CurveTool;
import org.mtr.aurelia.road.road.tool.EditRoadsTool;
import org.mtr.aurelia.road.road.tool.JoinableRoads;
import org.mtr.aurelia.road.road.tool.NodesTool;
import org.mtr.aurelia.road.road.tool.RoadChoices;
import org.mtr.aurelia.road.road.tool.RoadSettings;
import org.mtr.aurelia.road.road.tool.StraightTool;
import org.mtr.aurelia.road.spi.ToolEnvironment;
import org.mtr.aurelia.road.tool.Commit;
import org.mtr.aurelia.road.tool.Question;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.Tool;
import org.mtr.aurelia.road.tool.ToolInput;
import org.mtr.aurelia.road.tool.ToolSession;
import org.mtr.aurelia.road.tool.WorldTask;
import org.mtr.aurelia.road.undo.SideEffect;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.fabric.client.editor.StratosEditorClient;
import org.mtr.aurelia.road.fabric.client.nodes.StratosNodes;
import org.mtr.aurelia.road.fabric.client.preview.StratosPreview;
import org.mtr.aurelia.road.fabric.net.EditorNetworking;
import org.mtr.aurelia.road.fabric.palette.GamePalettes;

/**
 * Crosshair tool mode (owner, 2026-10-05; spec mod-suite-v1 §6.2). In ATR the tool key takes the straight road tool
 * until the hub comes. While it's active and no
 * screen is open:
 * <ul>
 *   <li>the game's use and attack clicks (right and left button by default) go to the tool;</li>
 *   <li>their "held" state is cleared every tick, so the game never mines, places or repeats a use;</li>
 *   <li>the tool sees the block aimed at, up to {@link #REACH} blocks away;</li>
 *   <li>{@link #UNDO_KEY} and {@link #REDO_KEY} undo and redo the player's Stratos edits (owner, 2026-10-06: R-2).</li>
 * </ul>
 * {@link #TOOL_KEY} puts the tool away, or brings the last one back. While a tool is active, the server's answers
 * show in its HUD ({@link #showInHud}). The tools' plans are worked out on the "Stratos tool planner" thread (R-2).
 * Client thread only.
 */
public final class StratosTools {
    /**
     * Puts the tool away, or brings the last one back. A vanilla keybind, rebindable under Controls, looked up by name.
     * The default is ` (the key left of 1): in the full pack every letter key already has a default binding (V, the first
     * choice, is ReplayMod's), and the harness checks that this one stays free ({@code check.keys}).
     */
    public static final KeyBinding TOOL_KEY = new KeyBinding("key.stratos.tool", InputUtil.Type.KEYSYM,
            InputUtil.fromTranslationKey("key.keyboard.grave.accent").getCode(), "key.categories.stratos");

    /**
     * Undoes the player's latest Stratos edit, while a tool is in hand (owner, 2026-10-06: a stray press in normal play
     * changes nothing). The default is - ([ and ] are JSBlock's and Xaero's, Ctrl+Z is Axiom's).
     */
    public static final KeyBinding UNDO_KEY = new KeyBinding("key.stratos.undo", InputUtil.Type.KEYSYM,
            InputUtil.fromTranslationKey("key.keyboard.minus").getCode(), "key.categories.stratos");

    /** Redoes the player's latest undone Stratos edit, while a tool is in hand. The default is =. */
    public static final KeyBinding REDO_KEY = new KeyBinding("key.stratos.redo", InputUtil.Type.KEYSYM,
            InputUtil.fromTranslationKey("key.keyboard.equal").getCode(), "key.categories.stratos");

    /**
     * The elevation and shape keys (owner, 2026-10-06: R-4), the block above the arrow keys, all free in the full pack:
     * Page Up / Page Down raise and lower the end that follows the aim (or a held handle), Home switches the vertical
     * shape, Insert / Delete step Two radii's radius and End opens the box to type it. Tool in hand only.
     */
    public static final KeyBinding RAISE_KEY = key("key.stratos.raise", "key.keyboard.page.up");
    public static final KeyBinding LOWER_KEY = key("key.stratos.lower", "key.keyboard.page.down");
    public static final KeyBinding SHAPE_KEY = key("key.stratos.shape", "key.keyboard.home");
    public static final KeyBinding RADIUS_UP_KEY = key("key.stratos.radius_up", "key.keyboard.insert");
    public static final KeyBinding RADIUS_DOWN_KEY = key("key.stratos.radius_down", "key.keyboard.delete");
    public static final KeyBinding RADIUS_TYPE_KEY = key("key.stratos.radius_type", "key.keyboard.end");

    /**
     * Switches the road tools' node aid: Track (only where the server can build it), blue wool, MTR nodes (only with MTR's
     * node block in the game), none (owner, 2026-10-06: R-6; 2026-10-08: T-2). The default is ', free in the full pack.
     * Tool in hand only.
     */
    public static final KeyBinding NODES_KEY = key("key.stratos.nodes", "key.keyboard.apostrophe");

    /** The keys that work only with a tool in hand (R-4, R-6). */
    private static final KeyBinding[] TOOL_KEYS = {RAISE_KEY, LOWER_KEY, SHAPE_KEY, RADIUS_UP_KEY, RADIUS_DOWN_KEY, RADIUS_TYPE_KEY, NODES_KEY};

    /**
     * The road tools' shared settings (R-4): the shape, and (R-6) the node aid, with MTR nodes only when the game has them
     * and (T-2) Track only when the server's {@code Hello} lists {@code track}.
     */
    private static final RoadSettings ROAD_SETTINGS = new RoadSettings(() -> EditorNetworking.modBlocks().contains(MtrNodes.BLOCK),
            () -> StratosEditorClient.serverHas(Protocol.CAPABILITY_TRACK), GamePalettes::client);

    /** The road choices' file, under the game's config folder (UI-1a; owner, 2026-10-15); from UI-1c the preview's settings too. */
    private static final String CHOICES_FILE = "stratos/client.json";

    /** The road choices and (UI-1c) the preview's settings last written to (or read from) {@link #choicesFile}. */
    private static RoadChoices savedChoices;
    private static PreviewChoices savedPreview;

    /** How far the tools can reach, in blocks (only loaded chunks can be aimed at). */
    public static final double REACH = 256;

    /** How long a message stays in the HUD. */
    static final long MESSAGE_NANOS = 6_000_000_000L;

    /** A server answer shown in the HUD until {@code until} (System.nanoTime). */
    record Message(Text text, boolean error, long until) {
    }

    /** Works the tools' plans out away from the game (spec mod-suite-v1 §7); one daemon thread, newest job first. */
    private static final ExecutorService PLANNER = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "Stratos tool planner");
        thread.setDaemon(true);
        return thread;
    });

    private static ToolSession session;
    private static Message message;
    private static Optional<Target> aim = Optional.empty();

    private StratosTools() {
    }

    private static KeyBinding key(String name, String defaultKey) {
        return new KeyBinding(name, InputUtil.Type.KEYSYM, InputUtil.fromTranslationKey(defaultKey).getCode(), "key.categories.stratos");
    }

    /** The road tools' shared shape settings (the radius box changes them). */
    public static RoadSettings roadSettings() {
        return ROAD_SETTINGS;
    }

    /**
     * Chooses the road tools' palette (R-9; owner, 2026-10-10): what the hub's palette button does (the next one,
     * {@link #nextPalette}), and what the harness calls. A road half placed is planned again in it. An id the server
     * hasn't sent changes nothing; returns whether it was chosen.
     */
    public static boolean choosePalette(String paletteId) {
        boolean chosen = ROAD_SETTINGS.setPalette(paletteId);
        if (chosen && session != null) {
            session.settingsChanged();
        }
        return chosen;
    }

    /** The hub's palette button: the next palette in the server's order, wrapping round. */
    public static void nextPalette() {
        ROAD_SETTINGS.cyclePalette();
        if (session != null) {
            session.settingsChanged();
        }
    }

    /**
     * Chooses the road tools' node aid (UI-1a; owner, 2026-10-15): what the toolbar's Nodes pop-up does. Track where it
     * can't be built, or MTR nodes where the game hasn't got them, changes nothing; returns whether it was chosen.
     */
    public static boolean chooseNodeAids(RoadPlanParams.NodeAids aids) {
        boolean chosen = ROAD_SETTINGS.setNodeAids(aids);
        if (chosen && session != null) {
            session.settingsChanged();
        }
        return chosen;
    }

    /** Where the road choices and (UI-1c) the preview's settings are kept across restarts: config/stratos/client.json. */
    public static Path choicesFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(CHOICES_FILE);
    }

    /**
     * Takes back the road choices (UI-1a) and the preview's settings (UI-1c) kept across restarts; none, or an unreadable
     * file, leaves the defaults.
     */
    private static void loadChoices() {
        Path file = choicesFile();
        if (Files.isRegularFile(file)) {
            try {
                String text = Files.readString(file);
                ROAD_SETTINGS.restore(RoadChoices.fromJson(text));
                StratosPreview.restore(PreviewChoices.fromJson(text));
            } catch (IOException e) {
                StratosMod.LOGGER.warn("[stratos] couldn't read the road choices in {}: {}", file, e.toString());
            }
        }
        savedChoices = ROAD_SETTINGS.choices();
        savedPreview = StratosPreview.choices();
    }

    /**
     * Keeps the road choices (layout, palette, node aid) once they have changed, whichever way: the toolbar, the {@code '}
     * key, or following a road being edited. Asked every tick; it writes only after a change.
     */
    private static void saveChoices() {
        RoadChoices now = ROAD_SETTINGS.choices();
        PreviewChoices preview = StratosPreview.choices();
        if (now.equals(savedChoices) && preview.equals(savedPreview)) {
            return;
        }
        savedChoices = now;
        savedPreview = preview;
        Path file = choicesFile();
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            com.google.gson.JsonObject json = now.toJsonObject();
            preview.writeTo(json);
            Files.writeString(temp, org.mtr.aurelia.road.json.StrictJson.write(json));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            StratosMod.LOGGER.warn("[stratos] couldn't keep the road choices in {}: {}", file, e.toString());
        }
    }

    /** The layouts the road tools can build, in the hub's order (G-4+): every built-in profile. */
    public static List<RoadProfile> layouts() {
        return RoadProfiles.all();
    }

    /**
     * Chooses the road tools' layout (G-4+; owner, 2026-10-07): what the hub's Layout list does, and what the harness
     * calls. A road half placed is planned again with the new layout. An unknown id changes nothing.
     */
    public static void chooseLayout(String profileId) {
        RoadProfiles.byId(profileId).ifPresent(profile -> {
            ROAD_SETTINGS.setProfile(profile);
            if (session != null) {
                session.settingsChanged();
            }
        });
    }

    /** Whether the radius box is open (for tests). */
    public static boolean radiusBoxOpen() {
        return MinecraftClient.getInstance().currentScreen instanceof RadiusScreen;
    }

    /** Types {@code text} into the open radius box, as a player would (the harness). */
    public static void typeInRadiusBox(String text) {
        if (MinecraftClient.getInstance().currentScreen instanceof RadiusScreen box) {
            box.field().setText(text);
        }
    }

    /** Presses Enter in the open radius box (the harness). */
    public static void enterInRadiusBox() {
        if (MinecraftClient.getInstance().currentScreen instanceof RadiusScreen box) {
            box.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }
    }

    /**
     * Registers the keys, the built-in tools, input capture and the HUD. Called once, from the client initialiser, after
     * {@code StratosEditorClient.register()}.
     */
    public static void register() {
        KeyBindingHelper.registerKeyBinding(TOOL_KEY);
        KeyBindingHelper.registerKeyBinding(UNDO_KEY);
        KeyBindingHelper.registerKeyBinding(REDO_KEY);
        for (KeyBinding mapping : TOOL_KEYS) {
            KeyBindingHelper.registerKeyBinding(mapping);
        }
        loadChoices();
        session = new ToolSession(StratosPreview.preview(), new Environment());
        // R-5: both road tools may join the ends of the roads the node aids know (yours you can still undo, and this visit's).
        JoinableRoads joins = new JoinableRoads(() -> StratosNodes.aids().roads().roads());
        session.register(new StraightTool(ROAD_SETTINGS, joins));
        session.register(new CurveTool(ROAD_SETTINGS, joins));
        session.register(new NodesTool(StratosNodes.aids()));
        // R-7: pick one of the node aids' roads, then rebuild or delete it.
        session.register(new EditRoadsTool(StratosNodes.aids().roads(), ROAD_SETTINGS));
        StratosEditorClient.addListener(session);
        ClientTickEvents.START_CLIENT_TICK.register(StratosTools::tick);
        HudRenderCallback.EVENT.register(ToolHud::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(StratosTools::leave));
    }

    /**
     * At the start of every client tick, before the game handles its own key bindings: the tool key, then, with a tool
     * active, undo and redo, the aim and the clicks.
     */
    private static void tick(MinecraftClient client) {
        saveChoices();
        boolean playing = client.currentScreen == null && client.player != null && client.world != null;
        while (TOOL_KEY.wasPressed()) {
            if (playing) {
                // ATR: no hub yet, so the first press takes the straight road tool.
                if (session.active().isEmpty() && session.last().isEmpty()) {
                    select(StraightTool.ID);
                } else {
                    session.toggle();
                }
            }
        }
        boolean tooling = playing && session.active().isPresent();
        while (UNDO_KEY.wasPressed()) {
            if (tooling) {
                undoRedo(StratosEditorClient.undo());
            }
        }
        while (REDO_KEY.wasPressed()) {
            if (tooling) {
                undoRedo(StratosEditorClient.redo());
            }
        }
        if (!tooling) {
            aim = Optional.empty();
            for (KeyBinding mapping : TOOL_KEYS) {
                while (mapping.wasPressed()) {
                    // without a tool in hand these keys do nothing
                }
            }
            return;
        }
        aim = pick(client);
        Entity camera = client.getCameraEntity();
        float yaw = camera == null ? 0F : camera.getYaw();
        session.input(new ToolInput.Aim(aim, yaw));
        while (client.options.useKey.wasPressed()) {
            session.input(new ToolInput.Use(aim, yaw));
        }
        while (client.options.attackKey.wasPressed()) {
            session.input(new ToolInput.Attack(aim, yaw));
        }
        adjust(RAISE_KEY, ToolInput.Adjustment.RAISE, yaw);
        adjust(LOWER_KEY, ToolInput.Adjustment.LOWER, yaw);
        adjust(SHAPE_KEY, ToolInput.Adjustment.SHAPE, yaw);
        adjust(RADIUS_UP_KEY, ToolInput.Adjustment.RADIUS_UP, yaw);
        adjust(RADIUS_DOWN_KEY, ToolInput.Adjustment.RADIUS_DOWN, yaw);
        adjust(NODES_KEY, ToolInput.Adjustment.NODES, yaw);
        boolean typeRadius = false;
        while (RADIUS_TYPE_KEY.wasPressed()) {
            typeRadius = true;
        }
        if (typeRadius) {
            client.setScreen(new RadiusScreen());
        }
        // R-7: a tool's question opens its box (Esc there picks the way out, so it doesn't open again).
        Optional<Question> question = session.question();
        if (question.isPresent() && client.currentScreen == null) {
            client.setScreen(new QuestionScreen(question.get()));
        }
        // The tool owns the buttons: the game must not keep mining or using while they are held.
        client.options.useKey.setPressed(false);
        client.options.attackKey.setPressed(false);
    }

    /** Whether a tool's question box is open (R-7). */
    public static boolean questionOpen() {
        return MinecraftClient.getInstance().currentScreen instanceof QuestionScreen;
    }

    /** The question in the open box, if one is (the harness reads it). */
    public static Optional<Question> openQuestion() {
        return MinecraftClient.getInstance().currentScreen instanceof QuestionScreen box ? Optional.of(box.question()) : Optional.empty();
    }

    /** Presses the button of {@code choice} in the open question box, as a player would (the harness). */
    public static void choose(String choice) {
        if (MinecraftClient.getInstance().currentScreen instanceof QuestionScreen box) {
            box.choose(choice);
        }
    }

    /** Every press of {@code mapping} since the last tick, as {@code adjustment}. */
    private static void adjust(KeyBinding mapping, ToolInput.Adjustment adjustment, float yaw) {
        while (mapping.wasPressed()) {
            session.input(new ToolInput.Adjust(adjustment, aim, yaw));
        }
    }

    /** An undo or redo was sent, or (editing off) the HUD says why not. */
    private static void undoRedo(OptionalInt sent) {
        if (sent.isEmpty()) {
            showInHud(Text.translatable("stratos.reject.editing_off"), true);
        }
    }

    /** The block the camera looks at, up to {@link #REACH} blocks away, through fluids (R-6: the node panel uses it too). */
    public static Optional<Target> pick(MinecraftClient client) {
        Entity camera = client.getCameraEntity();
        if (camera == null) {
            return Optional.empty();
        }
        HitResult hit = camera.raycast(REACH, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        return Optional.of(new Target(pos.getX(), pos.getY(), pos.getZ(), hit.getPos().distanceTo(camera.getCameraPosVec(1.0F))));
    }

    /** The player's tools (the hub lists them; the harness drives them). */
    public static ToolSession session() {
        return session;
    }

    /** The tools of one module, in order. */
    public static List<Tool> tools(String moduleId) {
        return session.tools(moduleId);
    }

    /** Takes the tool {@code id} in hand. */
    public static void select(String id) {
        session.select(id);
        message = null;
    }

    /** Puts the tool in hand away. */
    public static void leave() {
        session.leave();
        message = null;
    }

    public static boolean toolActive() {
        return session != null && session.active().isPresent();
    }

    /** Whether request {@code requestId} came from the tool in hand, so a mismatch refreshes its preview (editor-protocol §5). */
    public static boolean ownsRequest(int requestId) {
        return session != null && session.ownsRequest(requestId);
    }

    /** The block aimed at in the last tick, while a tool is active. */
    public static Optional<Target> aim() {
        return aim;
    }

    /**
     * Shows a server answer in the tool's HUD (owner, 2026-10-05), if a tool is active or the toolbar is open (UI-1b: the
     * action bar would show between the toolbar's tabs). Returns false otherwise, so the caller uses chat or the action
     * bar instead.
     */
    public static boolean showInHud(Text text, boolean error) {
        if (!toolActive()) {
            return false;
        }
        message = new Message(text, error, System.nanoTime() + MESSAGE_NANOS);
        return true;
    }

    /** The text of the server answer the HUD shows now, if any (R-7: the harness reads it). */
    public static Optional<String> messageText() {
        return message().map(shown -> shown.text().getString());
    }

    /** The message to show now, if one hasn't expired. */
    static Optional<Message> message() {
        if (message != null && System.nanoTime() - message.until() > 0) {
            message = null;
        }
        return Optional.ofNullable(message);
    }

    /** What the tool session gets from the game (core's {@link ToolEnvironment}). */
    private static final class Environment implements ToolEnvironment {
        @Override
        public WorldLimits limits() {
            MinecraftClient client = MinecraftClient.getInstance();
            return client.world == null ? WorldLimits.OVERWORLD : EditorNetworking.limits(client.world);
        }

        @Override
        public Future<?> background(Runnable work) {
            return PLANNER.submit(work);
        }

        @Override
        public void onClientThread(Runnable task) {
            MinecraftClient.getInstance().execute(task);
        }

        @Override
        public <P> EditorClient.Attempt send(PlanModule<P> module, P params, BlockPlan plan, List<SideEffect> sideEffects) {
            return StratosEditorClient.sendPlanned(module, params, plan, sideEffects);
        }

        @Override
        public OptionalInt sendTask(WorldTask task) {
            return StratosEditorClient.toolTask(task.task(), task.roadId()); // B-3: a road's bake
        }

        @Override
        public void problem(String text, Throwable error) {
            StratosMod.LOGGER.error("[stratos] {}", text, error);
        }

        @Override
        public void placed(Commit<?> commit, UUID operationId) {
            StratosNodes.placed(commit, operationId);
        }
    }
}
