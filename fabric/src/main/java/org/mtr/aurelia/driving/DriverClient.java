package org.mtr.aurelia.driving;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.signal.PathSignals;
import org.mtr.aurelia.signal.SignalAspect;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;
import org.mtr.mod.data.VehicleExtension;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * The driver's cab for a player in a train's driving seat: AWS, TPWS and the cab HUD that shows them.
 *
 * <p>It builds on MTR's own manual driving, which already has seven power notches, eight brake notches
 * (B8 is the emergency brake) and one door toggle. The extra keys here only add what MTR has no key for: the AWS
 * acknowledge and the TPWS reset. Protection brakes are sent as the same one-notch brake edges MTR's brake key sends, so
 * the server's simulation is unchanged.</p>
 */
public final class DriverClient {

	private static final int SERVICE_BRAKE_LEVEL = -7;
	private static final int EMERGENCY_BRAKE_LEVEL = -8;
	private static final double STANDSTILL = 0.1;
	private static final double MOVING = 0.5;
	private static final long SOUND_GAP_MILLIS = 350;

	private static final int RED = 0xFFFF3B30;
	private static final int YELLOW = 0xFFFFC107;
	private static final int GREEN = 0xFF34C759;
	private static final int LAMP_OFF = 0xFF2A2A2A;
	private static final int PANEL_BG = 0xE8181818;
	private static final int PANEL_BORDER = 0xFF444444;
	private static final int TRACK = 0xFF111111;

	private static KeyBinding awsKey;
	private static KeyBinding tpwsResetKey;

	@Nullable
	private static VehicleExtension vehicle;
	private static final AwsProtection AWS = new AwsProtection();
	private static final TpwsProtection TPWS = new TpwsProtection();
	private static List<PathSignals.Boundary> previousBoundaries = Collections.emptyList();
	private static int previousHeadIndex = -1;
	private static int signalRefreshTicks;
	@Nullable
	private static SignalAspect nextAspect;
	private static double metresToNextSignal = Double.NaN;
	private static boolean manualMode;
	private static long lastHornMillis;
	private static long lastBellMillis;
	private static boolean tpwsArmed;

	private DriverClient() {
	}

	public static void register() {
		awsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.driving.aws_ack", GLFW.GLFW_KEY_J, "category.mtr.keybinding"));
		tpwsResetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.driving.tpws_reset", GLFW.GLFW_KEY_K, "category.mtr.keybinding"));
		ClientTickEvents.END_CLIENT_TICK.register(DriverClient::tick);
		HudRenderCallback.EVENT.register((context, tickDelta) -> renderHud(context));
	}

	/** True while the player drives a train with a valid key, so the cab HUD (with its own speed) is up. */
	public static boolean isDriving() {
		return vehicle != null;
	}

	private static void tick(MinecraftClient client) {
		final boolean acknowledge = drain(awsKey);
		final boolean tpwsReset = drain(tpwsResetKey);

		if (client.player == null) {
			reset();
			return;
		}

		final VehicleExtension found = findVehicle(client);
		if (found != vehicle) {
			reset();
			vehicle = found;
		}

		if (vehicle == null) {
			return;
		}

		if (acknowledge) {
			AWS.acknowledge();
		}
		if (tpwsReset) {
			tpwsReset();
		}

		if (!vehicle.vehicleExtraData.getIsManualAllowed()) {
			AWS.clear();
			TPWS.update(false, 0, 0, false);
			manualMode = false;
			return;
		}

		manualMode = vehicle.vehicleExtraData.getIsCurrentlyManual();
		final Client config = Config.getClient();
		final long now = System.currentTimeMillis();
		final double speedMetresPerSecond = vehicle.getSpeed() * 1000;
		final double limitMetresPerSecond = speedLimitMetresPerSecond();

		// A signal boundary is where the next signal stands. The list was built last tick, before the head reached it,
		// so it is not polluted by this train occupying the block it is entering. A stopped train refreshes it a few
		// times a second; a moving one every tick, so no boundary is jumped over.
		boolean spad = false;
		final int headIndex = PathSignals.headIndex(vehicle);
		if (speedMetresPerSecond <= MOVING && signalRefreshTicks < 5) {
			signalRefreshTicks++;
		} else {
			signalRefreshTicks = 0;
			if (manualMode && previousHeadIndex >= 0 && headIndex >= previousHeadIndex) {
				for (final PathSignals.Boundary boundary : previousBoundaries) {
					if (boundary.signal() && boundary.railIndex() > previousHeadIndex && boundary.railIndex() <= headIndex) {
						if (boundary.aspect() == SignalAspect.RED) {
							spad = true;
							DriverSounds.trip();
							if (config.getDrivingAws()) {
								AWS.trigger(now);
								horn(now);
							}
						} else if (boundary.aspect().isRestrictive()) {
							if (config.getDrivingAws()) {
								AWS.trigger(now);
								horn(now);
							}
						} else if (config.getDrivingAws()) {
							bell(now);
						}
					}
				}
			}

			final List<PathSignals.Boundary> boundaries = PathSignals.ahead(vehicle, headIndex);
			nextAspect = null;
			metresToNextSignal = Double.NaN;
			for (final PathSignals.Boundary boundary : boundaries) {
				if (boundary.signal()) {
					nextAspect = boundary.aspect();
					metresToNextSignal = boundary.metresFromHead();
					break;
				}
			}
			previousBoundaries = boundaries;
			previousHeadIndex = headIndex;
		}

		if (!manualMode) {
			AWS.clear();
			TPWS.update(false, 0, 0, false);
			return;
		}

		if (config.getDrivingAws()) {
			AWS.tick(now, Math.round(config.getDrivingAwsSeconds() * 1000));
			if (AWS.isBrakeApplied()) {
				brake(SERVICE_BRAKE_LEVEL);
			}
		} else {
			AWS.clear();
		}

		final boolean overspeedArmed = nextAspect != null && nextAspect.isRestrictive() && !Double.isNaN(metresToNextSignal) && metresToNextSignal <= TpwsProtection.OVERSPEED_WINDOW_METRES;
		tpwsArmed = config.getDrivingTpws() && overspeedArmed;
		final TpwsProtection.Trip trip = TPWS.update(config.getDrivingTpws(), speedMetresPerSecond, overspeedArmed ? limitMetresPerSecond : 0, spad);
		if (trip != TpwsProtection.Trip.NONE) {
			brake(EMERGENCY_BRAKE_LEVEL);
		}
	}

	private static void tpwsReset() {
		if (TPWS.getTrip() != TpwsProtection.Trip.NONE) {
			final boolean stopped = vehicle != null && vehicle.getSpeed() * 1000 <= STANDSTILL;
			if (TPWS.reset(stopped)) {
				message("gui.aurelia.driving.tpws.reset");
			} else {
				message("gui.aurelia.driving.tpws.stop");
			}
		}
	}

	private static void message(String key) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.translatable(key), true);
		}
	}

	private static void brake(int targetPowerLevel) {
		if (vehicle != null && vehicle.vehicleExtraData.getPowerLevel() > targetPowerLevel) {
			if (targetPowerLevel <= EMERGENCY_BRAKE_LEVEL) {
				VehicleRidingMovement.requestAureliaEmergencyBrake();
			} else {
				VehicleRidingMovement.requestAureliaBrake();
			}
		}
	}

	private static double speedLimitMetresPerSecond() {
		if (vehicle == null) {
			return 0;
		}
		final double manual = vehicle.vehicleExtraData.getMaxManualSpeed() * 3600;
		final double track = vehicle.getSpeedLimitKilometersPerHour();
		final double kilometresPerHour = track > 0 && manual > 0 ? Math.min(track, manual) : Math.max(track, manual);
		return kilometresPerHour / 3.6;
	}

	private static boolean drain(@Nullable KeyBinding keyBinding) {
		if (keyBinding == null) {
			return false;
		}
		boolean pressed = false;
		while (keyBinding.wasPressed()) {
			pressed = true;
		}
		return pressed;
	}

	private static void horn(long now) {
		if (now - lastHornMillis > SOUND_GAP_MILLIS) {
			lastHornMillis = now;
			DriverSounds.horn();
		}
	}

	private static void bell(long now) {
		if (now - lastBellMillis > SOUND_GAP_MILLIS) {
			lastBellMillis = now;
			DriverSounds.bell();
		}
	}

	private static void reset() {
		vehicle = null;
		AWS.clear();
		TPWS.update(false, 0, 0, false);
		previousBoundaries = Collections.emptyList();
		previousHeadIndex = -1;
		signalRefreshTicks = 0;
		nextAspect = null;
		metresToNextSignal = Double.NaN;
		manualMode = false;
		tpwsArmed = false;
	}

	@Nullable
	private static VehicleExtension findVehicle(MinecraftClient client) {
		if (client.player == null) {
			return null;
		}
		for (final VehicleExtension candidate : MinecraftClientData.getInstance().vehicles) {
			if (VehicleRidingMovement.isRiding(candidate.getId())
					&& VehicleRidingMovement.getValidHoldingKey(candidate.vehicleExtraData.getDepotId()) != null) {
				return candidate;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// HUD
	// ---------------------------------------------------------------------------------------------------------------

	private static void renderHud(DrawContext context) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (vehicle == null || client.player == null || client.currentScreen != null || client.options.hudHidden) {
			return;
		}

		final Client config = Config.getClient();
		final DriverHudLayout.Fit fit = DriverHudLayout.fit(context.getScaledWindowWidth(), context.getScaledWindowHeight(), config.getDrivingHudScale());

		final MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(fit.x(), fit.y(), 0);
		matrices.scale((float) fit.scale(), (float) fit.scale(), 1);

		drawPanel(context);
		drawSpeed(context, client);
		drawPower(context, client);
		drawDoors(context, client);
		drawAws(context, client, config);
		drawTpws(context, client, config);
		drawSignal(context, client);

		matrices.pop();
	}

	private static void drawPanel(DrawContext context) {
		final int height = DriverHudLayout.BASE_HEIGHT;
		context.fill(0, 0, DriverHudLayout.BASE_WIDTH, height, PANEL_BG);
		context.fill(0, 0, DriverHudLayout.BASE_WIDTH, 2, 0xFF090909);
		context.fill(0, 2, DriverHudLayout.BASE_WIDTH, 3, AureliaPalette.ACCENT);
		context.fill(0, height - 1, DriverHudLayout.BASE_WIDTH, height, 0xFF000000);
		context.fill(0, 0, 1, height, 0xFF000000);
		context.fill(DriverHudLayout.BASE_WIDTH - 1, 0, DriverHudLayout.BASE_WIDTH, height, 0xFF000000);
		for (final int separator : DriverHudLayout.SEPARATORS) {
			context.fill(separator, 6, separator + 1, DriverHudLayout.TOP_HEIGHT - 4, PANEL_BORDER);
		}
	}

	private static void drawSpeed(DrawContext context, MinecraftClient client) {
		final double kilometresPerHour = vehicle == null ? 0 : vehicle.getSpeed() * 3600;
		final int limit = vehicle == null ? 0 : vehicle.getSpeedLimitKilometersPerHour();
		final double manual = vehicle == null ? 0 : vehicle.vehicleExtraData.getMaxManualSpeed() * 3600;
		final int maxSpeed = DriverHudLayout.maxDialSpeed(limit, manual);
		final int centreX = DriverHudLayout.DIAL_CENTER_X;
		final int centreY = DriverHudLayout.DIAL_CENTER_Y;
		final int radius = DriverHudLayout.DIAL_RADIUS;
		final boolean overLimit = limit > 0 && kilometresPerHour > limit;

		filledCircle(context, centreX, centreY, radius + 3, 0xFF050505);
		filledCircle(context, centreX, centreY, radius, 0xFF101010);
		for (int value = 0; value <= maxSpeed; value += 10) {
			final boolean major = value % 20 == 0;
			radial(context, centreX, centreY, DriverHudLayout.dialDegrees(value, maxSpeed), radius - (major ? 9 : 6), radius - 2, 1, major ? 0xFFE6E6E6 : 0xFF777777);
		}
		if (limit > 0) {
			radial(context, centreX, centreY, DriverHudLayout.dialDegrees(limit, maxSpeed), radius - 10, radius - 1, 2, GREEN);
		}
		radial(context, centreX, centreY, DriverHudLayout.dialDegrees(Math.min(kilometresPerHour, maxSpeed), maxSpeed), -3, radius - 7, 1, overLimit ? YELLOW : RED);
		filledCircle(context, centreX, centreY, 3, 0xFFB0B0B0);
		context.drawCenteredTextWithShadow(client.textRenderer, String.valueOf(Math.round(kilometresPerHour)), centreX, centreY + radius + 2, overLimit ? YELLOW : AureliaPalette.FG);
		context.drawText(client.textRenderer, "km/h", centreX + 10, centreY + radius + 2, AureliaPalette.FG_LIGHT, false);
		context.drawText(client.textRenderer, limit > 0 ? "LIMIT " + limit : "LIMIT -", 8, 80, YELLOW, false);
	}

	private static void drawPower(DrawContext context, MinecraftClient client) {
		final int level = vehicle == null ? 0 : vehicle.vehicleExtraData.getPowerLevel();
		final String text;
		final int color;
		if (level <= -8) {
			text = "EB";
			color = RED;
		} else if (level < 0) {
			text = "B" + -level;
			color = YELLOW;
		} else if (level > 0) {
			text = "P" + level;
			color = 0xFF6FA8FF;
		} else {
			text = "N";
			color = AureliaPalette.FG;
		}

		context.drawText(client.textRenderer, "POWER", 108, 4, AureliaPalette.FG_LIGHT, false);
		context.fill(DriverHudLayout.POWER_X, DriverHudLayout.POWER_TOP, DriverHudLayout.POWER_X + 6, DriverHudLayout.POWER_BOTTOM, TRACK);
		for (int notch = -8; notch <= 7; notch++) {
			final int y = powerY(notch);
			context.fill(DriverHudLayout.POWER_X - 4, y, DriverHudLayout.POWER_X, y + 1, notch == 0 ? 0xFFBBBBBB : 0xFF3A3A3A);
		}
		final int neutralY = powerY(0);
		final int levelY = powerY(level);
		if (levelY < neutralY) {
			context.fill(DriverHudLayout.POWER_X, levelY, DriverHudLayout.POWER_X + 6, neutralY, color);
		} else {
			context.fill(DriverHudLayout.POWER_X, neutralY, DriverHudLayout.POWER_X + 6, levelY, color);
		}

		context.getMatrices().push();
		context.getMatrices().translate(126, 28, 0);
		context.getMatrices().scale(1.6F, 1.6F, 1);
		context.drawText(client.textRenderer, text, 0, 0, color, false);
		context.getMatrices().pop();
		context.drawText(client.textRenderer, "P7", 126, 14, AureliaPalette.FG_LIGHT, false);
		context.drawText(client.textRenderer, "N", 126, 48, AureliaPalette.FG_LIGHT, false);
		context.drawText(client.textRenderer, "B8", 126, 72, AureliaPalette.FG_LIGHT, false);
	}

	private static int powerY(int notch) {
		return DriverHudLayout.POWER_BOTTOM - (int) Math.round(DriverHudLayout.notchFraction(notch) * (DriverHudLayout.POWER_BOTTOM - DriverHudLayout.POWER_TOP));
	}

	private static void drawDoors(DrawContext context, MinecraftClient client) {
		final boolean open = vehicle != null && (vehicle.persistentVehicleData.getDoorValue() > 0.01 || vehicle.vehicleExtraData.getDoorMultiplier() > 0);
		context.drawText(client.textRenderer, "DOORS", 174, 4, AureliaPalette.FG_LIGHT, false);
		context.drawText(client.textRenderer, open ? "OPEN" : "CLOSED", 174, 16, open ? GREEN : AureliaPalette.FG, false);
		context.drawText(client.textRenderer, carsText(vehicle == null ? 0 : vehicle.vehicleExtraData.immutableVehicleCars.size()), 174, 34, AureliaPalette.FG_LIGHT, false);
		context.drawText(client.textRenderer, (vehicle == null ? 0 : Math.round(vehicle.vehicleExtraData.getTotalVehicleLength())) + " m", 174, 46, AureliaPalette.FG_LIGHT, false);
	}

	private static void drawAws(DrawContext context, MinecraftClient client, Client config) {
		final boolean warning = config.getDrivingAws() && AWS.isWarning();
		final boolean brake = config.getDrivingAws() && AWS.isBrakeApplied();
		context.drawText(client.textRenderer, "AWS", 238, 4, AureliaPalette.FG_LIGHT, false);
		sunflower(context, 240, 16, 16, warning ? YELLOW : LAMP_OFF);
		context.drawText(client.textRenderer, !config.getDrivingAws() ? "OFF" : warning ? "HORN" : "OK", 260, 18, !config.getDrivingAws() ? AureliaPalette.FG_LIGHT : warning ? YELLOW : GREEN, false);
		if (brake) {
			context.drawText(client.textRenderer, "BRAKE", 260, 32, RED, false);
		} else if (warning) {
			context.drawText(client.textRenderer, "ACK", 260, 32, YELLOW, false);
		}
	}

	private static void drawTpws(DrawContext context, MinecraftClient client, Client config) {
		final boolean enabled = config.getDrivingTpws();
		final boolean tripped = enabled && TPWS.isBraking();
		final boolean armed = enabled && tpwsArmed && !tripped;
		context.drawText(client.textRenderer, "TPWS", 292, 4, AureliaPalette.FG_LIGHT, false);
		context.drawText(client.textRenderer, !enabled ? "OFF" : tripped ? "TRIP" : armed ? "ARMED" : "OK", 292, 18, !enabled ? AureliaPalette.FG_LIGHT : tripped ? RED : armed ? YELLOW : GREEN, false);
		if (tripped) {
			context.drawText(client.textRenderer, "STOP", 292, 32, RED, false);
			context.drawText(client.textRenderer, "RST", 292, 44, RED, false);
		}
	}

	private static void sunflower(DrawContext context, int x, int y, int size, int color) {
		final int radius = size / 2;
		filledCircle(context, x + radius, y + radius, radius, color);
		for (int angle = 0; angle < 180; angle += 45) {
			radial(context, x + radius, y + radius, angle, 0, radius, 1, 0xFF111111);
		}
		filledCircle(context, x + radius, y + radius, 2, 0xFF111111);
	}

	private static void drawSignal(DrawContext context, MinecraftClient client) {
		context.drawText(client.textRenderer, "NEXT", 334, 4, AureliaPalette.FG_LIGHT, false);
		lamp(context, 362, 18, nextAspect == SignalAspect.RED, RED);
		lamp(context, 362, 30, nextAspect == SignalAspect.YELLOW || nextAspect == SignalAspect.DOUBLE_YELLOW, YELLOW);
		lamp(context, 362, 42, nextAspect == SignalAspect.DOUBLE_YELLOW, YELLOW);
		lamp(context, 362, 54, nextAspect == SignalAspect.GREEN, GREEN);
		if (!Double.isNaN(metresToNextSignal)) {
			context.drawText(client.textRenderer, distanceText(), 334, 68, AureliaPalette.FG, false);
			context.drawText(client.textRenderer, aspectName(), 334, 80, nextAspect != null && nextAspect.isRestrictive() ? YELLOW : GREEN, false);
		} else {
			context.drawText(client.textRenderer, "-", 334, 72, AureliaPalette.FG_LIGHT, false);
		}
	}

	private static String distanceText() {
		return metresToNextSignal >= 1000 ? String.format("%.1f km", metresToNextSignal / 1000) : Math.round(metresToNextSignal) + " m";
	}

	private static String aspectName() {
		if (nextAspect == null) {
			return "-";
		}
		switch (nextAspect) {
			case RED:
				return "RED";
			case YELLOW:
				return "YEL";
			case DOUBLE_YELLOW:
				return "D-YEL";
			default:
				return "GRN";
		}
	}

	private static void lamp(DrawContext context, int x, int y, boolean lit, int color) {
		filledCircle(context, x, y, 6, 0xFF000000);
		filledCircle(context, x, y, 5, lit ? color : LAMP_OFF);
	}

	private static String carsText(int cars) {
		return cars + (cars == 1 ? " CAR" : " CARS");
	}

	private static void filledCircle(DrawContext context, int x, int y, int radius, int color) {
		for (int row = -radius; row <= radius; row++) {
			final int half = (int) Math.sqrt(Math.max(0, radius * radius - row * row));
			context.fill(x - half, y + row, x + half + 1, y + row + 1, color);
		}
	}

	private static void radial(DrawContext context, int x, int y, double degrees, int inner, int outer, int thickness, int color) {
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0);
		context.getMatrices().multiply(new Quaternionf().rotateZ((float) Math.toRadians(degrees)));
		context.fill(inner, -thickness, outer, thickness, color);
		context.getMatrices().pop();
	}
}
