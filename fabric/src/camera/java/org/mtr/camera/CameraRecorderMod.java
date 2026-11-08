package org.mtr.camera;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Dev-only trailer camera recorder (the {@code atr_camera} mod, {@code src/camera}, never in the
 * release jar). F8 drops a keyframe where the player stands, F9 plays the Catmull-Rom path back at
 * the set blocks-per-second speed, F10 opens the panel. While playing, the HUD hides (when the
 * toggle is on) and the field of view follows the keyframes; both are restored at the end.
 * Paths save and load as JSON in {@code <run dir>/camera-paths/}.
 */
public final class CameraRecorderMod implements ClientModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR trailer camera");

	private static KeyBinding addKey;
	private static KeyBinding playKey;
	private static KeyBinding panelKey;

	private static final List<CameraKeyframe> FRAMES = new ArrayList<>();
	private static CameraPath path = new CameraPath(List.of());
	private static boolean playing;
	private static double distance;
	private static double speed = CameraPathJson.DEFAULT_SPEED;
	private static boolean hideHud = CameraPathJson.DEFAULT_HIDE_HUD;
	private static boolean restoreHud;
	private static int restoreFov;
	private static String status = "";

	@Override
	public void onInitializeClient() {
		addKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.atr_camera.add", GLFW.GLFW_KEY_F8, "key.categories.atr_camera"));
		playKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.atr_camera.play", GLFW.GLFW_KEY_F9, "key.categories.atr_camera"));
		panelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.atr_camera.panel", GLFW.GLFW_KEY_F10, "key.categories.atr_camera"));
		ClientTickEvents.END_CLIENT_TICK.register(CameraRecorderMod::tick);
		LOGGER.info("[ATR] Trailer camera recorder ready (dev only)");
	}

	public static List<CameraKeyframe> frames() {
		return List.copyOf(FRAMES);
	}

	public static int frameCount() {
		return FRAMES.size();
	}

	public static boolean playing() {
		return playing;
	}

	public static double speed() {
		return speed;
	}

	public static void setSpeed(double value) {
		speed = Math.max(1, Math.min(30, value));
	}

	public static boolean hideHud() {
		return hideHud;
	}

	public static void setHideHud(boolean value) {
		hideHud = value;
	}

	public static String status() {
		return status;
	}

	public static String progress() {
		return playing ? String.format(java.util.Locale.ROOT, "%.1f / %.1f", distance, path.length()) : "";
	}

	public static void addFrameFromPlayer(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		FRAMES.add(new CameraKeyframe(
				client.player.getX(), client.player.getY(), client.player.getZ(),
				client.player.getYaw(), client.player.getPitch(), client.options.getFov().getValue()
		));
		path = new CameraPath(FRAMES);
		status = "frame " + FRAMES.size() + " added";
	}

	public static void clearFrames() {
		FRAMES.clear();
		path = new CameraPath(List.of());
		if (playing) {
			stop(MinecraftClient.getInstance());
		}
		status = "frames cleared";
	}

	public static void togglePlay() {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (playing) {
			stop(client);
		} else {
			play(client);
		}
	}

	public static List<String> savedPaths() {
		final Path dir = pathsDir();
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (final Stream<Path> files = Files.list(dir)) {
			return files.filter(file -> file.getFileName().toString().endsWith(".json"))
					.map(file -> file.getFileName().toString().replaceAll("\\.json$", ""))
					.sorted(Comparator.naturalOrder()).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	public static int savedFrameCount(String name) {
		try {
			return CameraPathJson.read(Files.readString(fileFor(name), StandardCharsets.UTF_8)).loaded();
		} catch (RuntimeException | IOException e) {
			return -1;
		}
	}

	public static void save(String name) throws IOException {
		if (FRAMES.isEmpty()) {
			throw new IOException("no frames to save");
		}
		final String fileName = fileName(name);
		Files.createDirectories(pathsDir());
		Files.writeString(fileFor(fileName), CameraPathJson.write(path, speed, hideHud), StandardCharsets.UTF_8);
		status = "saved " + fileName + " (" + FRAMES.size() + " frames)";
	}

	public static void load(String name) throws IOException {
		final CameraPathJson.LoadResult result;
		try {
			result = CameraPathJson.read(Files.readString(fileFor(name), StandardCharsets.UTF_8));
		} catch (IllegalArgumentException e) {
			throw new IOException("not a camera path: " + name, e);
		}
		FRAMES.clear();
		FRAMES.addAll(result.file().path().keyframes());
		path = new CameraPath(FRAMES);
		speed = Math.max(1, Math.min(30, result.file().speed()));
		hideHud = result.file().hideHud();
		if (playing) {
			stop(MinecraftClient.getInstance());
		}
		status = "loaded " + name + " (" + result.loaded() + " frames" + (result.skipped() == 0 ? "" : ", " + result.skipped() + " skipped") + ")";
	}

	public static void delete(String name) throws IOException {
		if (!Files.deleteIfExists(fileFor(name))) {
			throw new IOException("no saved path: " + name);
		}
		status = "deleted " + name;
	}

	static void fail(String message) {
		status = message;
	}

	private static void play(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}
		if (FRAMES.size() < 2) {
			status = "need at least 2 frames to play";
			return;
		}
		restoreHud = client.options.hudHidden;
		restoreFov = client.options.getFov().getValue();
		distance = 0;
		playing = true;
		status = "playing";
	}

	private static void stop(MinecraftClient client) {
		playing = false;
		if (client != null) {
			client.options.hudHidden = restoreHud;
			client.options.getFov().setValue(restoreFov);
		}
		status = "stopped";
	}

	private static void tick(MinecraftClient client) {
		while (addKey.wasPressed()) {
			if (client.player != null && client.world != null && client.currentScreen == null) {
				addFrameFromPlayer(client);
				client.player.sendMessage(Text.translatable("gui.atr_camera.added", FRAMES.size()), true);
			}
		}
		while (playKey.wasPressed()) {
			togglePlay();
		}
		while (panelKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new CameraPanelScreen(null));
			}
		}
		if (!playing) {
			return;
		}
		if (client.player == null || client.world == null) {
			stop(client);
			return;
		}
		distance += speed / 20;
		if (distance >= path.length()) {
			apply(client, path.sample(path.size() - 1));
			stop(client);
			return;
		}
		if (hideHud) {
			client.options.hudHidden = true;
		}
		apply(client, path.sampleAtDistance(distance));
	}

	private static void apply(MinecraftClient client, CameraKeyframe keyframe) {
		client.player.refreshPositionAndAngles(keyframe.x(), keyframe.y(), keyframe.z(), (float) keyframe.yaw(), (float) keyframe.pitch());
		client.player.setVelocity(Vec3d.ZERO);
		client.player.fallDistance = 0;
		client.options.getFov().setValue(Math.max(30, Math.min(110, (int) Math.round(keyframe.fov()))));
	}

	private static Path pathsDir() {
		return MinecraftClient.getInstance().runDirectory.toPath().resolve("camera-paths");
	}

	private static Path fileFor(String name) {
		return pathsDir().resolve(fileName(name) + ".json");
	}

	private static String fileName(String name) {
		final String clean = name.replaceAll("[^A-Za-z0-9_.-]", "_");
		if (clean.isEmpty()) {
			throw new IllegalArgumentException("name is empty");
		}
		return clean;
	}
}
