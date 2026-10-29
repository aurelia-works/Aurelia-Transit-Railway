package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.mtr.aurelia.ride.RidePose;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.packet.PacketRidePose;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/**
 * Sitting or standing while riding a train or bus. The sit key (X by default; shift is MTR's "get off") flips the pose for
 * the current ride; each ride starts from the panel's "Sit when boarding". The pose goes to the server, which tells the
 * other players, so they see it too. The body and camera drop by the panel's sit height while sitting.
 */
public final class RideClient {

	public static final double MAX_SIT_DROP = 1;
	/**
	 * Ticks between repeats of the pose while riding, so players who arrive later see it.
	 */
	private static final int RESEND_TICKS = 100;

	private static final RidePose LOCAL = new RidePose(false);
	private static final RidePose.Others OTHERS = new RidePose.Others();
	private static boolean sitWhenBoarding;
	private static double sitDrop = 0.35;
	private static int resendTicks;
	/**
	 * Dev only: the perf bench's screenshots draw the local player sitting without a vehicle.
	 */
	private static boolean forceSitting;

	private RideClient() {
	}

	public static void register() {
		load();
		LOCAL.setDefault(sitWhenBoarding);
		final KeyBinding sitKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.sit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.mtr"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}
			while (sitKey.wasPressed()) {
				LOCAL.toggle();
			}
			final boolean riding = VehicleRidingMovement.isRidingAnyVehicle();
			LOCAL.tick(riding);
			if (LOCAL.changed() || riding && ++resendTicks >= RESEND_TICKS) {
				resendTicks = 0;
				InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketRidePose(client.player.getUuid(), LOCAL.isSitting()));
			}
		});
	}

	/**
	 * Whether a player is drawn sitting: the local player from the key and panel, others as the server reported.
	 */
	public static boolean isSitting(UUID uuid) {
		final MinecraftClient client = MinecraftClient.getInstance();
		return client.player != null && client.player.getUuid().equals(uuid) ? forceSitting || LOCAL.isSitting() : OTHERS.isSitting(uuid);
	}

	public static void forceSittingForScreenshots(boolean value) {
		forceSitting = value;
	}

	public static void setOther(UUID uuid, boolean sitting) {
		OTHERS.set(uuid, sitting);
	}

	public static boolean sitWhenBoarding() {
		return sitWhenBoarding;
	}

	public static void setSitWhenBoarding(boolean value) {
		sitWhenBoarding = value;
		LOCAL.setDefault(value);
		save();
	}

	public static double sitDrop() {
		return sitDrop;
	}

	public static void setSitDrop(double value) {
		sitDrop = Math.max(0, Math.min(MAX_SIT_DROP, value));
		save();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(AureliaClient.MOD_ID).resolve("riding.properties");
	}

	private static void load() {
		final Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			final Properties properties = new Properties();
			properties.load(reader);
			sitWhenBoarding = "true".equals(properties.getProperty("sit_when_boarding"));
			final String drop = properties.getProperty("sit_drop");
			if (drop != null) {
				sitDrop = Math.max(0, Math.min(MAX_SIT_DROP, Double.parseDouble(drop)));
			}
		} catch (IOException | NumberFormatException e) {
			AureliaClient.LOGGER.error("[ATR] Could not read {}", file, e);
		}
	}

	private static void save() {
		final Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				final Properties properties = new Properties();
				properties.setProperty("sit_when_boarding", Boolean.toString(sitWhenBoarding));
				properties.setProperty("sit_drop", Double.toString(sitDrop));
				properties.store(writer, "Aurelia Transit Railway riding");
			}
		} catch (IOException e) {
			AureliaClient.LOGGER.error("[ATR] Could not save {}", file, e);
		}
	}
}
