package org.mtr.aurelia.driving;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.crew.CrewClient;
import org.mtr.aurelia.crew.CrewTrain;
import org.mtr.aurelia.signal.PathSignals;
import org.mtr.aurelia.signal.SignalAspect;
import org.mtr.core.data.PathData;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleObjectImmutablePair;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;
import org.mtr.mod.data.VehicleExtension;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * The driver's cab for a player in a train's driving seat: AWS, TPWS, the score and the cab HUD.
 *
 * <p>It builds on MTR's own manual driving, which already has seven power notches, eight brake notches
 * (B8 is the emergency brake) and one door toggle. The extra keys here only add what MTR has no key for: an AWS/TPWS
 * acknowledge, and separate open-left, open-right and close door commands. Protection brakes are sent as the same
 * one-notch brake edges MTR's brake key sends, so the server's simulation is unchanged.</p>
 */
public final class DriverClient {

	private static final int SERVICE_BRAKE_LEVEL = -7;
	private static final int EMERGENCY_BRAKE_LEVEL = -8;
	private static final double STANDSTILL = 0.1;
	private static final double MOVING = 0.5;
	private static final double PLATFORM_STOP_RANGE = 5;
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
	private static KeyBinding openLeftKey;
	private static KeyBinding openRightKey;
	private static KeyBinding closeDoorsKey;

	@Nullable
	private static VehicleExtension vehicle;
	private static final AwsProtection AWS = new AwsProtection();
	private static final TpwsProtection TPWS = new TpwsProtection();
	private static final DriverScore SCORE = new DriverScore();
	private static List<PathSignals.Boundary> previousBoundaries = Collections.emptyList();
	private static int previousHeadIndex = -1;
	private static int signalRefreshTicks;
	@Nullable
	private static SignalAspect nextAspect;
	private static double metresToNextSignal = Double.NaN;
	private static boolean overspeedLatched;
	private static boolean moving;
	private static boolean manualMode;
	private static DoorSides.Side lastDoorSide = DoorSides.Side.NONE;
	private static long lastHornMillis;
	private static long lastBellMillis;
	private static boolean tpwsArmed;
	private static DoorSides.Side platformSide = DoorSides.Side.NONE;
	private static int platformSideTicks;
	@Nullable
	private static PathData stopPath;
	private static double stopDistance = Double.NaN;
	private static int stopRefreshTicks;
	/** The vehicle whose doors were open last tick, or -1: the crew signals reset when doors open, not while they stay open. */
	private static long doorsOpenVehicle = -1;

	private DriverClient() {
	}

	public static void register() {
		awsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.driving.aws_ack", GLFW.GLFW_KEY_J, "category.mtr.keybinding"));
		tpwsResetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.driving.tpws_reset", GLFW.GLFW_KEY_K, "category.mtr.keybinding"));
		openLeftKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.driving.open_left", GLFW.GLFW_KEY_COMMA, "category.mtr.keybinding"));
		openRightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.driving.open_right", GLFW.GLFW_KEY_PERIOD, "category.mtr.keybinding"));
		closeDoorsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.driving.close_doors", GLFW.GLFW_KEY_SEMICOLON, "category.mtr.keybinding"));
		ClientTickEvents.END_CLIENT_TICK.register(DriverClient::tick);
		HudRenderCallback.EVENT.register((context, tickDelta) -> renderHud(context));
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(DriverClient::renderStopMarker);
	}

	/** True while the player drives a train with a valid key, so the cab HUD (with its own speed) is up. */
	public static boolean isDriving() {
		return vehicle != null;
	}

	private static void tick(MinecraftClient client) {
		final boolean acknowledge = drain(awsKey);
		final boolean tpwsReset = drain(tpwsResetKey);
		final boolean openLeft = drain(openLeftKey);
		final boolean openRight = drain(openRightKey);
		final boolean close = drain(closeDoorsKey);

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

		// The stop path is stable for a whole platform, so look it up twice a second rather than every frame.
		if (++stopRefreshTicks >= 10 || stopPath != null && stopPath.getEndDistance() < vehicle.getRailProgress() - 10) {
			stopRefreshTicks = 0;
			stopPath = PathSignals.stopPath(vehicle);
		}
		stopDistance = stopPath == null ? Double.NaN : stopPath.getEndDistance() - vehicle.getRailProgress();

		if (acknowledge) {
			AWS.acknowledge();
		}
		if (tpwsReset) {
			tpwsReset();
		}
		if (openLeft || openRight || close) {
			doors(client.player, openLeft, openRight, close);
		}

		// the crew's signals count for one departure: doors opening at a stop mean the next one needs new ones; a right away
		// rung while they are still open stands for that departure
		final boolean doorsOpen = vehicle.persistentVehicleData.getDoorValue() > 0.01 || vehicle.vehicleExtraData.getDoorMultiplier() > 0;
		final CrewTrain crew = CrewClient.train(vehicle.getId());
		if (crew != null && doorsOpen && doorsOpenVehicle != vehicle.getId()) {
			crew.reset();
		}
		doorsOpenVehicle = doorsOpen ? vehicle.getId() : -1;

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

		if (speedMetresPerSecond <= MOVING) {
			if (++platformSideTicks >= 10) {
				platformSideTicks = 0;
				platformSide = DoorSides.of(vehicle);
			}
		} else {
			platformSide = DoorSides.Side.NONE;
			platformSideTicks = 0;
		}

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
							SCORE.spad();
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
			if (moving) {
				moving = false;
				SCORE.cancel();
			}
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

		final double overspeedMargin = Math.max(TpwsProtection.OVERSPEED_MARGIN_METRES_PER_SECOND, limitMetresPerSecond * TpwsProtection.OVERSPEED_FRACTION);
		final boolean overspeed = limitMetresPerSecond > 0 && speedMetresPerSecond > limitMetresPerSecond + overspeedMargin;
		if (overspeed && !overspeedLatched) {
			SCORE.overspeed();
			overspeedLatched = true;
		} else if (!overspeed) {
			overspeedLatched = false;
		}

		if (speedMetresPerSecond > MOVING && !moving) {
			moving = true;
			SCORE.depart(now);
			if (crew != null) {
				if (crew.status() != CrewTrain.Status.READY) {
					SCORE.noRightAway();
					message("gui.aurelia.crew.no_right_away");
				}
				crew.reset();
			}
		} else if (speedMetresPerSecond <= STANDSTILL && moving) {
			// Only a stop at a platform ends the run. A stop at a signal before the platform keeps the clock running,
			// as it would on a real run.
			final DoubleObjectImmutablePair<DoubleDoubleImmutablePair> details = vehicle.getPlatformStoppingDetails();
			if (details != null && Math.abs(details.leftDouble()) < PLATFORM_STOP_RANGE) {
				moving = false;
				SCORE.stop(now, details.leftDouble(), true);
			}
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

	private static void doors(ClientPlayerEntity player, boolean openLeft, boolean openRight, boolean close) {
		if (vehicle == null) {
			return;
		}

		final double speed = vehicle.getSpeed() * 1000;
		final boolean doorsOpen = vehicle.persistentVehicleData.getDoorValue() > 0.01 || vehicle.vehicleExtraData.getDoorMultiplier() > 0;

		if (close) {
			if (!doorsOpen && speed <= STANDSTILL) {
				player.sendMessage(Text.translatable("gui.aurelia.driving.doors.closed"), true);
			} else {
				VehicleRidingMovement.requestAureliaDoors();
				lastDoorSide = DoorSides.Side.NONE;
				player.sendMessage(Text.translatable("gui.aurelia.driving.doors.closing"), true);
			}
			return;
		}

		if (speed > MOVING) {
			player.sendMessage(Text.translatable("gui.aurelia.driving.doors.locked"), true);
			return;
		}

		if (doorsOpen) {
			player.sendMessage(Text.translatable("gui.aurelia.driving.doors.open"), true);
			return;
		}

		final DoorSides.Side side = DoorSides.of(vehicle);
		platformSide = side;
		if (openLeft && (side == DoorSides.Side.LEFT || side == DoorSides.Side.BOTH)) {
			VehicleRidingMovement.requestAureliaDoors();
			lastDoorSide = DoorSides.Side.LEFT;
			player.sendMessage(Text.translatable("gui.aurelia.driving.doors.left"), true);
		} else if (openRight && (side == DoorSides.Side.RIGHT || side == DoorSides.Side.BOTH)) {
			VehicleRidingMovement.requestAureliaDoors();
			lastDoorSide = DoorSides.Side.RIGHT;
			player.sendMessage(Text.translatable("gui.aurelia.driving.doors.right"), true);
		} else if (openLeft) {
			player.sendMessage(Text.translatable("gui.aurelia.driving.doors.no_left"), true);
		} else if (openRight) {
			player.sendMessage(Text.translatable("gui.aurelia.driving.doors.no_right"), true);
		}
	}

	private static void message(String key) {
		final ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.sendMessage(Text.translatable(key), true);
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
		SCORE.cancel();
		previousBoundaries = Collections.emptyList();
		previousHeadIndex = -1;
		signalRefreshTicks = 0;
		nextAspect = null;
		metresToNextSignal = Double.NaN;
		stopPath = null;
		stopDistance = Double.NaN;
		stopRefreshTicks = 0;
		overspeedLatched = false;
		moving = false;
		manualMode = false;
		tpwsArmed = false;
		platformSide = DoorSides.Side.NONE;
		platformSideTicks = 0;
		lastDoorSide = DoorSides.Side.NONE;
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
		final boolean score = config.getDrivingScoreHud();
		final DriverHudLayout.Fit fit = DriverHudLayout.fit(context.getScaledWindowWidth(), context.getScaledWindowHeight(), config.getDrivingHudScale(), score);

		final MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(fit.x(), fit.y(), 0);
		matrices.scale((float) fit.scale(), (float) fit.scale(), 1);

		drawPanel(context, score);
		drawSpeed(context, client);
		drawPower(context, client);
		drawDoors(context, client);
		drawAws(context, client, config);
		drawTpws(context, client, config);
		drawSignal(context, client);
		drawCrew(context, client);
		drawStop(context, client);
		if (score) {
			drawScore(context, client);
		}

		matrices.pop();
	}

	private static void drawPanel(DrawContext context, boolean score) {
		final int height = score ? DriverHudLayout.BASE_HEIGHT_SCORE : DriverHudLayout.BASE_HEIGHT;
		context.fill(0, 0, DriverHudLayout.BASE_WIDTH, height, PANEL_BG);
		context.fill(0, 0, DriverHudLayout.BASE_WIDTH, 2, 0xFF090909);
		context.fill(0, 2, DriverHudLayout.BASE_WIDTH, 3, AureliaPalette.ACCENT);
		context.fill(0, height - 1, DriverHudLayout.BASE_WIDTH, height, 0xFF000000);
		context.fill(0, 0, 1, height, 0xFF000000);
		context.fill(DriverHudLayout.BASE_WIDTH - 1, 0, DriverHudLayout.BASE_WIDTH, height, 0xFF000000);
		for (final int separator : DriverHudLayout.SEPARATORS) {
			context.fill(separator, 6, separator + 1, DriverHudLayout.TOP_HEIGHT - 4, PANEL_BORDER);
		}
		context.fill(6, DriverHudLayout.TOP_HEIGHT, DriverHudLayout.BASE_WIDTH - 6, DriverHudLayout.TOP_HEIGHT + 1, PANEL_BORDER);
		if (score) {
			context.fill(6, DriverHudLayout.SCORE_Y - 2, DriverHudLayout.BASE_WIDTH - 6, DriverHudLayout.SCORE_Y - 1, PANEL_BORDER);
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
		final String state;
		if (open) {
			state = lastDoorSide == DoorSides.Side.LEFT ? "OPEN L" : lastDoorSide == DoorSides.Side.RIGHT ? "OPEN R" : "OPEN";
		} else {
			state = "CLOSED";
		}
		context.drawText(client.textRenderer, "DOORS", 174, 4, AureliaPalette.FG_LIGHT, false);
		context.drawText(client.textRenderer, state, 174, 16, open ? GREEN : AureliaPalette.FG, false);
		context.fill(174, 32, 216, 42, AureliaPalette.FIELD);
		context.fill(176, 34, 182, 40, doorLamp(DoorSides.Side.LEFT, open));
		context.fill(208, 34, 214, 40, doorLamp(DoorSides.Side.RIGHT, open));
		context.fill(186, 35, 204, 39, 0xFF333333);
		context.drawText(client.textRenderer, platformText(), 174, 46, platformColour(), false);
		context.drawText(client.textRenderer, carsText(vehicle == null ? 0 : vehicle.vehicleExtraData.immutableVehicleCars.size()), 174, 58, AureliaPalette.FG_LIGHT, false);
		context.drawText(client.textRenderer, (vehicle == null ? 0 : Math.round(vehicle.vehicleExtraData.getTotalVehicleLength())) + " m", 174, 70, AureliaPalette.FG_LIGHT, false);
	}

	private static int doorLamp(DoorSides.Side side, boolean open) {
		final boolean platformed = platformSide == side || platformSide == DoorSides.Side.BOTH;
		if (open && platformed) {
			return GREEN;
		}
		return platformed ? 0xFF2E7D32 : 0xFF333333;
	}

	private static String platformText() {
		switch (platformSide) {
			case LEFT:
				return "PLATFORM L";
			case RIGHT:
				return "PLATFORM R";
			case BOTH:
				return "PLATFORM B";
			default:
				return "PLATFORM -";
		}
	}

	private static int platformColour() {
		return platformSide == DoorSides.Side.NONE ? AureliaPalette.FG_LIGHT : GREEN;
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

	private static void drawStop(DrawContext context, MinecraftClient client) {
		if (vehicle == null) {
			return;
		}
		final int cars = vehicle.vehicleExtraData.immutableVehicleCars.size();
		final int length = (int) Math.round(vehicle.vehicleExtraData.getTotalVehicleLength());
		context.drawText(client.textRenderer, carsText(cars) + " / " + length + " m", 6, 94, AureliaPalette.FG_LIGHT, false);

		final DoubleObjectImmutablePair<DoubleDoubleImmutablePair> details = vehicle.getPlatformStoppingDetails();
		if (details == null) {
			context.drawText(client.textRenderer, "STOP -", 120, 94, AureliaPalette.FG_LIGHT, false);
			return;
		}
		final double distance = details.leftDouble();
		if (distance < -5 || distance > 600) {
			context.drawText(client.textRenderer, "STOP -", 120, 94, AureliaPalette.FG_LIGHT, false);
			return;
		}

		final boolean atStop = Math.abs(distance) <= 0.5;
		final int color = atStop ? GREEN : Math.abs(distance) <= 5 ? YELLOW : AureliaPalette.FG;
		context.drawText(client.textRenderer, atStop ? "STOP" : "STOP " + Math.round(distance) + " m", 120, 94, color, false);
		context.fill(DriverHudLayout.STOP_TAPE_LEFT, 98, DriverHudLayout.STOP_TAPE_RIGHT, 102, TRACK);
		context.fill(366, 96, 372, 104, GREEN);
		final int pointer = DriverHudLayout.STOP_TAPE_LEFT + (int) Math.round(DriverHudLayout.stopPointerFraction(distance) * (DriverHudLayout.STOP_TAPE_RIGHT - DriverHudLayout.STOP_TAPE_LEFT));
		context.fill(pointer, 95, pointer + 2, 105, color);
	}

	/** The crew lamp under AWS and TPWS, only on a train a guard or dispatcher works. */
	private static void drawCrew(DrawContext context, MinecraftClient client) {
		final CrewTrain crew = vehicle == null ? null : CrewClient.train(vehicle.getId());
		if (crew == null || !crew.working()) {
			return;
		}
		final CrewTrain.Status status = crew.status();
		final String text;
		final int color;
		switch (status) {
			case READY -> {
				text = crew.guard() ? "RTS" : "RIGHT AWAY";
				color = GREEN;
			}
			case RIGHT_AWAY -> {
				text = "RA: WAIT RTS";
				color = YELLOW;
			}
			default -> {
				text = "WAIT";
				color = YELLOW;
			}
		}
		context.drawText(client.textRenderer, crew.guard() ? "GUARD" : "DISPATCH", 238, 58, AureliaPalette.FG_LIGHT, false);
		lamp(context, 240, 72, true, color);
		context.drawText(client.textRenderer, text, 252, 70, color, false);
	}

	private static void drawScore(DrawContext context, MinecraftClient client) {
		final DriverScore.Run last = SCORE.getLastRun();
		if (SCORE.isRunning()) {
			final long elapsed = System.currentTimeMillis() - SCORE.runStartedMillis();
			context.drawText(client.textRenderer, "RUN " + time(elapsed) + "   " + SCORE.currentSpads() + " SPAD   " + SCORE.currentOverspeeds() + " OVS"
					+ (SCORE.currentNoRightAways() > 0 ? "   " + SCORE.currentNoRightAways() + " NRA" : ""), 8, 114, AureliaPalette.FG, false);
		} else if (last != null) {
			final String accuracy = Double.isNaN(last.stopErrorMetres()) ? "no platform" : String.format("%.1f m", last.stopErrorMetres());
			context.drawText(client.textRenderer, "LAST " + last.points() + " pts   " + accuracy + "   " + last.spads() + " SPAD   " + last.overspeeds() + " OVS   "
					+ (last.noRightAways() > 0 ? last.noRightAways() + " NRA   " : "") + time(last.elapsedMillis()), 8, 114, AureliaPalette.FG, false);
		} else {
			context.drawText(client.textRenderer, "NO RUN YET", 8, 114, AureliaPalette.FG_LIGHT, false);
		}
	}

	private static String carsText(int cars) {
		return cars + (cars == 1 ? " CAR" : " CARS");
	}

	private static String time(long millis) {
		final long seconds = Math.max(0, millis) / 1000;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
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

	// ---------------------------------------------------------------------------------------------------------------
	// Stop marker in the world
	// ---------------------------------------------------------------------------------------------------------------

	private static void renderStopMarker(WorldRenderContext context) {
		if (vehicle == null || stopPath == null || Double.isNaN(stopDistance) || stopDistance < -10 || stopDistance > 400
				|| !Config.getClient().getDrivingStopMarker() || context.consumers() == null || context.camera() == null || context.matrixStack() == null) {
			return;
		}
		final PathData pathData = stopPath;

		final double local = Math.min(pathData.getRailLength(), pathData.getEndDistance() - pathData.getStartDistance());
		final Vector end = pathData.getPosition(Math.max(0, local));
		final Vector before = pathData.getPosition(Math.max(0, local - 1));
		final double dx = end.x - before.x;
		final double dz = end.z - before.z;
		final double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 0.001) {
			return;
		}
		final double acrossX = -dz / length;
		final double acrossZ = dx / length;
		final double alongX = dx / length;
		final double alongZ = dz / length;
		final Vec3d camera = context.camera().getPos();
		final Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
		final VertexConsumer quads = context.consumers().getBuffer(RenderLayer.getDebugQuads());

		// A stop board beside the track, facing the driver: a yellow board on a black post, geometry only.
		final double sideX = end.x + acrossX * 2.6;
		final double sideZ = end.z + acrossZ * 2.6;
		final double boardY = end.y + 2.15;
		quad(quads, matrix,
				sideX - acrossX * 0.95 - alongX * 0.04, boardY - 0.45, sideZ - acrossZ * 0.95 - alongZ * 0.04,
				sideX + acrossX * 0.95 - alongX * 0.04, boardY - 0.45, sideZ + acrossZ * 0.95 - alongZ * 0.04,
				sideX + acrossX * 0.95 + alongX * 0.04, boardY + 0.45, sideZ + acrossZ * 0.95 + alongZ * 0.04,
				sideX - acrossX * 0.95 + alongX * 0.04, boardY + 0.45, sideZ - acrossZ * 0.95 + alongZ * 0.04,
				250, 200, 30, 255);
		quad(quads, matrix,
				sideX - acrossX * 0.95 - alongX * 0.06, boardY - 0.08, sideZ - acrossZ * 0.95 - alongZ * 0.06,
				sideX + acrossX * 0.95 - alongX * 0.06, boardY - 0.08, sideZ + acrossZ * 0.95 - alongZ * 0.06,
				sideX + acrossX * 0.95 - alongX * 0.06, boardY + 0.08, sideZ + acrossZ * 0.95 - alongZ * 0.06,
				sideX - acrossX * 0.95 - alongX * 0.06, boardY + 0.08, sideZ - acrossZ * 0.95 - alongZ * 0.06,
				20, 20, 20, 255);
		box(quads, matrix, camera,
				sideX - 0.06, end.y + 0.9, sideZ - 0.06,
				sideX + 0.06, end.y + 1.7, sideZ + 0.06,
				30, 30, 30, 255);

		// The car count on the board, billboarded so the driver can read it from the cab.
		final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		if (textRenderer != null) {
			final String label = (vehicle == null ? 0 : vehicle.vehicleExtraData.immutableVehicleCars.size()) + " CAR STOP";
			final Vec3d toCamera = camera.subtract(sideX, boardY, sideZ);
			final double distanceToCamera = Math.max(0.01, toCamera.length());
			final double textX = sideX + toCamera.x * 0.08 / distanceToCamera;
			final double textY = boardY + toCamera.y * 0.08 / distanceToCamera;
			final double textZ = sideZ + toCamera.z * 0.08 / distanceToCamera;
			final MatrixStack matrices = context.matrixStack();
			matrices.push();
			matrices.translate(textX - camera.x, textY - camera.y, textZ - camera.z);
			matrices.multiply(context.camera().getRotation());
			matrices.scale(-0.025F, -0.025F, 0.025F);
			final int width = textRenderer.getWidth(label);
			textRenderer.draw(label, -width / 2F, -4, 0xFF101010, false, matrices.peek().getPositionMatrix(), context.consumers(), TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
			matrices.pop();
			if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
				immediate.draw();
			}
		}
	}

	private static void box(VertexConsumer quads, Matrix4f matrix, Vec3d camera, double x1, double y1, double z1, double x2, double y2, double z2, int r, int g, int b, int a) {
		final float minX = (float) (Math.min(x1, x2) - camera.x);
		final float minY = (float) (Math.min(y1, y2) - camera.y);
		final float minZ = (float) (Math.min(z1, z2) - camera.z);
		final float maxX = (float) (Math.max(x1, x2) - camera.x);
		final float maxY = (float) (Math.max(y1, y2) - camera.y);
		final float maxZ = (float) (Math.max(z1, z2) - camera.z);

		quad(quads, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
		quad(quads, matrix, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
		quad(quads, matrix, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
		quad(quads, matrix, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
		quad(quads, matrix, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
		quad(quads, matrix, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
	}

	private static void quad(VertexConsumer quads, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int b, int a) {
		quads.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).next();
		quads.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).next();
		quads.vertex(matrix, (float) x3, (float) y3, (float) z3).color(r, g, b, a).next();
		quads.vertex(matrix, (float) x4, (float) y4, (float) z4).color(r, g, b, a).next();
	}
}
