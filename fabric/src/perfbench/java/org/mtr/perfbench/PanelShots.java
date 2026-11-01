package org.mtr.perfbench;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import org.mtr.aurelia.client.MtrBridge;
import org.mtr.aurelia.client.RideClient;
import org.mtr.aurelia.client.screen.*;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.Init;
import org.mtr.mod.Items;
import org.mtr.mod.item.ItemRailBuilder;
import org.mtr.mod.screen.ConfigScreen;
import org.mtr.mod.screen.DashboardScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Dev only, {@code -Datr.panelshots=true} with the perf bench run: opens MTR's dashboard, MTR's options, every tab of the
 * Aurelia panel and every tool, screenshots each into {@code screenshots/panel-*.png}, writes panelshots.txt and quits.
 */
public final class PanelShots implements ClientModInitializer {

	static final boolean ACTIVE = Boolean.getBoolean("atr.panelshots");

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR panel shots");
	private static final int START_TICKS = 30 * 20;
	private static final int SCREEN_TICKS = 2 * 20;

	private record Shot(String name, Function<Screen, Screen> open) {
	}

	private final List<Shot> shots = new ArrayList<>();
	private final List<String> results = new ArrayList<>();
	private int ticks;
	private int index = -1;
	private int screenTicks;
	private Screen panel;

	@Override
	public void onInitializeClient() {
		if (!ACTIVE) {
			return;
		}
		shots.add(new Shot("00-dashboard", parent -> new DashboardScreen(TransportMode.TRAIN)));
		shots.add(new Shot("01-mtr-options", parent -> new ConfigScreen(null)));
		shots.add(new Shot("02-panel-rendering", parent -> new AureliaPanelScreen(null, AureliaPanelScreen.Tab.RENDERING)));
		shots.add(new Shot("03-panel-sound", parent -> new AureliaPanelScreen(null, AureliaPanelScreen.Tab.SOUND)));
		shots.add(new Shot("04-panel-tools", parent -> panel = new AureliaPanelScreen(null, AureliaPanelScreen.Tab.TOOLS)));
		shots.add(new Shot("05-presets", TrainPresetScreen::new));
		shots.add(new Shot("06-stops", RouteStopsScreen::new));
		shots.add(new Shot("07-return", ReturnRouteScreen::new));
		shots.add(new Shot("08-dwell", PlatformDwellScreen::new));
		shots.add(new Shot("09-departures", DepartureEditorScreen::new));
		shots.add(new Shot("10-overlap", StationOverlapScreen::new));
		shots.add(new Shot("11-height", ZoneHeightScreen::new));
		shots.add(new Shot("12-codes", StationCodesScreen::new));
		shots.add(new Shot("13-depots", DepotHealthScreen::new));
		shots.add(new Shot("14-speed", SpeedAuditScreen::new));
		shots.add(new Shot("15-trains", TrainInspectorScreen::new));
		shots.add(new Shot("16-jump", JumpScreen::new));
		shots.add(new Shot("17-panel-builder", parent -> new AureliaPanelScreen(null, AureliaPanelScreen.Tab.BUILDER)));
		// No screen: the Rail Builder's work beside the player, built while the screens above were shot
		shots.add(new Shot("18-rail-builder-world", parent -> null));
		shots.add(new Shot("19-panel-riding", parent -> new AureliaPanelScreen(null, AureliaPanelScreen.Tab.RIDING)));
		// The local player seated, seen from the front in third person
		shots.add(new Shot("20-sitting", parent -> {
			RideClient.forceSittingForScreenshots(true);
			MinecraftClient.getInstance().options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
			return null;
		}));
		shots.add(new Shot("21-panel-architecture", parent -> new AureliaPanelScreen(null, AureliaPanelScreen.Tab.ARCHITECTURE)));
		shots.add(new Shot("22-panel-look", parent -> new AureliaPanelScreen(null, AureliaPanelScreen.Tab.LOOK)));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}
		ticks++;
		if (index < 0) {
			if (ticks == START_TICKS - 5 * 20 && client.getServer() != null) {
				// Edit tools need MTR edit permission (creative or survival); the bench world's player has neither
				final UUID id = client.player.getUuid();
				client.getServer().execute(() -> {
					final ServerPlayerEntity player = client.getServer().getPlayerManager().getPlayer(id);
					if (player != null) {
						player.changeGameMode(GameMode.CREATIVE);
						startRailBuilder(player);
					}
				});
			}
			if (ticks >= START_TICKS) {
				index = 0;
				open(client);
			}
			return;
		}
		if (++screenTicks < SCREEN_TICKS) {
			return;
		}
		final Shot shot = shots.get(index);
		final String screenName = client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName();
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "panel-" + shot.name() + ".png", client.getFramebuffer(), message -> {
		});
		results.add(shot.name() + ": open " + screenName + ", canEdit " + MtrBridge.canEdit());
		index++;
		if (index >= shots.size()) {
			finish(client);
		} else {
			open(client);
		}
	}

	/**
	 * Puts a Rail Builder (stone bricks, the default settings) in the player's hand and builds along a rail that starts
	 * 6 blocks to the player's side at foot level and drops 12 blocks over 60, so it runs from the surface into the ground.
	 * The player then looks along it from above.
	 */
	private static void startRailBuilder(ServerPlayerEntity player) {
		final ItemStack itemStack = new ItemStack(Items.RAIL_BUILDER.get().data);
		ItemRailBuilder.write(itemStack, new ItemRailBuilder.Settings(5, 5, true, true, true));
		itemStack.getOrCreateNbt().putInt("block_id", net.minecraft.block.Block.getRawIdFromState(Blocks.STONE_BRICKS.getDefaultState()));
		player.setStackInHand(Hand.MAIN_HAND, itemStack);

		final int x = player.getBlockX();
		final int y = player.getBlockY();
		final int z = player.getBlockZ() + 6;
		final Rail rail = Rail.newRail(new Position(x, y, z), Angle.E, new Position(x + 60, y - 12, z), Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), 80, 80, false, false, true, false, false, TransportMode.TRAIN);
		// The bench world is flat: a stone hill over the rail's far half, its near face on the rail's middle and its north half
		// cut away for the last third, so one build shows bridge, tunnel and a cutting walled on the hill side only
		for (int hillX = x + 25; hillX <= x + 60; hillX++) {
			for (int hillZ = z - 8; hillZ <= z + 8; hillZ++) {
				if (hillX > x + 45 && hillZ < z) {
					continue;
				}
				for (int hillY = -60; hillY <= y; hillY++) {
					player.getWorld().setBlockState(new net.minecraft.util.math.BlockPos(hillX, hillY, hillZ), Blocks.STONE.getDefaultState());
				}
			}
		}
		final org.mtr.mapping.holder.ServerWorld serverWorld = new org.mtr.mapping.holder.ServerWorld((net.minecraft.server.world.ServerWorld) player.getWorld());
		Init.getRailActionModule(serverWorld, module -> module.markRailForBuilder(rail, new org.mtr.mapping.holder.ServerPlayerEntity(player), 2, 5, new org.mtr.mapping.holder.BlockState(Blocks.STONE_BRICKS.getDefaultState()), true, true, true));
		player.teleport((net.minecraft.server.world.ServerWorld) player.getWorld(), x + 20, y + 14, z - 22, -40, 35);
		LOGGER.info("Rail Builder started at {} {} {} along +x", x, y, z);
	}

	private void open(MinecraftClient client) {
		screenTicks = 0;
		final Shot shot = shots.get(index);
		try {
			client.setScreen(shot.open().apply(panel));
		} catch (RuntimeException e) {
			LOGGER.error("Could not open {}", shot.name(), e);
			results.add(shot.name() + ": FAILED " + e);
			// No screenshot of whatever screen is still open
			index++;
			if (index >= shots.size()) {
				finish(client);
			} else {
				open(client);
			}
		}
	}

	private void finish(MinecraftClient client) {
		try {
			Files.write(Path.of("panelshots.txt"), results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write panelshots.txt", e);
		}
		LOGGER.info("Panel shots done: {}", results.size());
		client.scheduleStop();
	}
}
