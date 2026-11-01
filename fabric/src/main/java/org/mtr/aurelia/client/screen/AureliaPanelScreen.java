package org.mtr.aurelia.client.screen;

import com.aureliatransit.architecture.client.AirportWrenchScreen;
import com.aureliatransit.architecture.client.StationStudioClient;
import com.aureliatransit.architecture.client.StationWrenchScreen;
import com.aureliatransit.architecture.registry.ModItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.item.Item;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.mtr.aurelia.client.Announcer;
import org.mtr.aurelia.client.ui.AureliaUiSettings;
import org.mtr.aurelia.client.MtrBridge;
import org.mtr.aurelia.client.PassengerClient;
import org.mtr.aurelia.client.ZipClient;
import org.mtr.aurelia.client.PassengerDrawSettings;
import org.mtr.aurelia.client.RideClient;
import org.mtr.aurelia.client.ScriptingHandshake;
import org.mtr.mod.InitClient;
import org.mtr.mod.Items;
import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;
import org.mtr.mod.item.ItemRailBuilder;
import org.mtr.mod.packet.PacketUpdateRailBuilder;
import org.mtr.mod.passenger.PassengerNetwork;
import org.mtr.mod.passenger.PassengerService;
import org.mtr.aurelia.client.BuildClient;
import org.mtr.mod.building.BuildMath;
import org.mtr.mod.building.BuildSettings;
import org.mtr.mod.zip.ZipSettings;
import org.mtr.mod.resource.PantographEnd;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

/**
 * The Aurelia panel: one screen with tabs for ATR's own switches and tools. Rendering rows read and write ATR's client
 * config ({@code config/mtr.json}); Tools are ATU's hub; Sound holds the Announcer switches; Riding holds sitting while
 * riding; Builder sets the Rail Builder in the player's hand.
 */
public final class AureliaPanelScreen extends Screen {

	public enum Tab {RENDERING, TOOLS, SOUND, ARCHITECTURE, RIDING, BUILDER, PANTOGRAPH, TRAIN_SOUND, PASSENGERS, ZIP, BUILD, LOOK}

	private static final int TAB_WIDTH = 90;
	private static final int BUTTON_WIDTH = 200;
	private static final int VALUE_WIDTH = 80;
	private static final int ROW = 24;
	private static final int TOP = 56;

	private sealed interface Row permits Toggle, Distance {
		String key();
	}

	private record Toggle(String key, BooleanSupplier get, Runnable toggle) implements Row {
	}

	private record Distance(String key, int max, IntSupplier get, IntConsumer set) implements Row {
	}

	/** {@code needsEdit}: greyed out without MTR edit permission. Depot health only reads until Regenerate is pressed. */
	private record Tool(String key, boolean needsEdit, Function<Screen, Screen> open) {
	}

	private static final List<Tool> TOOLS = List.of(
			new Tool("presets", true, TrainPresetScreen::new),
			new Tool("stops", true, RouteStopsScreen::new),
			new Tool("return", true, ReturnRouteScreen::new),
			new Tool("dwell", true, PlatformDwellScreen::new),
			new Tool("departures", true, DepartureEditorScreen::new),
			new Tool("overlap", true, StationOverlapScreen::new),
			new Tool("height", true, ZoneHeightScreen::new),
			new Tool("codes", true, StationCodesScreen::new),
			new Tool("depots", false, DepotHealthScreen::new),
			new Tool("speed", false, SpeedAuditScreen::new),
			new Tool("trains", false, TrainInspectorScreen::new),
			// /tp permission is checked by the server; the screen says so when the player lacks it.
			new Tool("jump", false, JumpScreen::new)
	);

	@Nullable
	private final Screen parent;
	private final Client config = Config.getClient();
	private final List<Row> renderingRows = List.of(
			new Toggle("options.mtr.hide_rain_inside_vehicles", config::getHideRainInsideVehicles, config::toggleHideRainInsideVehicles),
			new Toggle("options.mtr.faster_door_checks", config::getFasterDoorChecks, config::toggleFasterDoorChecks),
			new Toggle("options.mtr.cheap_shadows", config::getCheapShadows, config::toggleCheapShadows),
			new Toggle("options.mtr.rail_lod", config::getRailLod, config::toggleRailLod),
			new Distance("options.mtr.rail_simple_chunks", Client.RAIL_SIMPLE_CHUNKS_MAX, config::getRailSimpleChunks, config::setRailSimpleChunks),
			new Distance("options.mtr.rail_far_chunks", Client.RAIL_FAR_CHUNKS_MAX, config::getRailFarChunks, config::setRailFarChunks),
			new Toggle("options.mtr.shader_pack_fast_path", config::getShaderPackFastPath, config::toggleShaderPackFastPath),
			new Toggle("options.mtr.perf_on_f3", config::getPerfOnF3, config::togglePerfOnF3),
			new Toggle("options.mtr.merge_train_parts", config::getMergeTrainParts, config::toggleMergeTrainParts),
			new Toggle("options.mtr.train_lod", config::getTrainLod, config::toggleTrainLod),
			new Distance("options.mtr.train_interior_chunks", Client.TRAIN_INTERIOR_CHUNKS_MAX, config::getTrainInteriorChunks, config::setTrainInteriorChunks),
			new Distance("options.mtr.train_box_chunks", Client.TRAIN_BOX_CHUNKS_MAX, config::getTrainBoxChunks, config::setTrainBoxChunks),
			new Toggle("options.mtr.faster_model_loading", config::getFasterModelLoading, config::toggleFasterModelLoading),
			new Toggle("options.mtr.background_model_loading", config::getBackgroundModelLoading, config::toggleBackgroundModelLoading),
			new Toggle("options.mtr.free_unused_models", config::getFreeUnusedModels, config::toggleFreeUnusedModels)
	);

	private Tab tab;
	private int firstRow;
	private int passengerFirst;
	private int lookFirst;
	private static final AureliaUiSettings.ScreenId[] LOOK_IDS = AureliaUiSettings.ScreenId.values();
	private static final Text LOOK_HINT = Text.translatable("gui.aurelia.look.hint");
	private int buildFirst;
	private static final int BUILD_ROWS = 10;
	/** Server caps, then this client's drawing switches. The tab scrolls when the window is short. */
	private static final int PASSENGER_ROWS = 9;

	public AureliaPanelScreen(@Nullable Screen parent, Tab tab) {
		super(Text.translatable("gui.aurelia.panel.title"));
		this.parent = parent;
		this.tab = tab;
	}

	@Override
	protected void init() {
		final Tab[] tabs = Tab.values();
		final int tabWidth = Math.min(TAB_WIDTH, (width - 16 - (tabs.length - 1) * 4) / tabs.length);
		final int tabsLeft = (width - tabs.length * tabWidth - (tabs.length - 1) * 4) / 2;
		for (int i = 0; i < tabs.length; i++) {
			final Tab target = tabs[i];
			final ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.panel.tab." + target.name().toLowerCase()), b -> {
				tab = target;
				firstRow = 0;
				lookFirst = 0;
				buildFirst = 0;
				clearAndInit();
			}).dimensions(tabsLeft + i * (tabWidth + 4), 28, tabWidth, 20).build());
			button.active = target != tab;
		}
		switch (tab) {
			case RENDERING -> initRendering();
			case TOOLS -> initTools();
			case SOUND -> initSound();
			case ARCHITECTURE -> initArchitecture();
			case RIDING -> initRiding();
			case BUILDER -> initBuilder();
			case PANTOGRAPH -> initPantograph();
			case TRAIN_SOUND -> initTrainSound();
			case PASSENGERS -> initPassengers();
			case ZIP -> initZip();
			case BUILD -> initBuild();
			case LOOK -> initLook();
		}
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions((width - BUTTON_WIDTH) / 2, height - 28, BUTTON_WIDTH, 20).build());
	}

	private int visibleRows() {
		return Math.max(1, (height - 36 - TOP) / ROW);
	}

	private void initRendering() {
		firstRow = Math.max(0, Math.min(firstRow, renderingRows.size() - visibleRows()));
		final int valueX = width - 16 - VALUE_WIDTH;
		for (int i = firstRow; i < Math.min(renderingRows.size(), firstRow + visibleRows()); i++) {
			final int y = TOP + (i - firstRow) * ROW;
			if (renderingRows.get(i) instanceof Toggle toggle) {
				addDrawableChild(ButtonWidget.builder(ScreenTexts.onOrOff(toggle.get().getAsBoolean()), b -> {
					toggle.toggle().run();
					b.setMessage(ScreenTexts.onOrOff(toggle.get().getAsBoolean()));
				}).dimensions(valueX, y, VALUE_WIDTH, 20).build());
			} else if (renderingRows.get(i) instanceof Distance distance) {
				addDrawableChild(new DistanceSlider(valueX, y, distance));
			}
		}
	}

	private void initTools() {
		final int columns = Math.min(3, (TOOLS.size() + visibleRows() - 1) / visibleRows());
		final int buttonWidth = columns == 1 ? BUTTON_WIDTH : Math.min(BUTTON_WIDTH, (width - 16 - (columns - 1) * 8) / columns);
		final int rows = (TOOLS.size() + columns - 1) / columns;
		final int left = (width - columns * buttonWidth - (columns - 1) * 8) / 2;
		final boolean canEdit = MtrBridge.canEdit();
		for (int i = 0; i < TOOLS.size(); i++) {
			final Tool tool = TOOLS.get(i);
			final ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.tools." + tool.key()), b -> client.setScreen(tool.open().apply(this)))
					.dimensions(left + (i / rows) * (buttonWidth + 8), TOP + (i % rows) * ROW, buttonWidth, 20).build());
			button.active = canEdit || !tool.needsEdit();
		}
	}

	private void initSound() {
		final int left = (width - BUTTON_WIDTH) / 2;
		final ButtonWidget announcer = addDrawableChild(ButtonWidget.builder(announcerLabel(), b -> {
			Announcer.setEnabled(!Announcer.enabled());
			b.setMessage(announcerLabel());
		}).dimensions(left, TOP, BUTTON_WIDTH, 20).build());
		announcer.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.tools.announcer.tooltip")));
		final ButtonWidget platform = addDrawableChild(ButtonWidget.builder(platformLabel(), b -> {
			Announcer.setPlatformEnabled(!Announcer.platformEnabled());
			b.setMessage(platformLabel());
		}).dimensions(left, TOP + ROW, BUTTON_WIDTH, 20).build());
		platform.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.tools.platform.tooltip")));
		final ButtonWidget actionBar = addDrawableChild(ButtonWidget.builder(actionBarLabel(), b -> {
			Announcer.setActionBar(!Announcer.actionBar());
			b.setMessage(actionBarLabel());
		}).dimensions(left, TOP + ROW * 3, BUTTON_WIDTH, 20).build());
		actionBar.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.tools.announcer.action_bar.tooltip")));
		final ButtonWidget wording = addDrawableChild(ButtonWidget.builder(wordingLabel(), b -> {
			Announcer.cycleWording();
			b.setMessage(wordingLabel());
		}).dimensions(left, TOP + ROW * 4, BUTTON_WIDTH, 20).build());
		wording.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.tools.announcer.wording.tooltip")));
		final ButtonWidget melodies = addDrawableChild(ButtonWidget.builder(melodiesLabel(), b -> {
			ScriptingHandshake.setMelodies(!ScriptingHandshake.melodiesEnabled());
			b.setMessage(melodiesLabel());
		}).dimensions(left, TOP + ROW * 2, BUTTON_WIDTH, 20).build());
		melodies.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.tools.melodies.tooltip")));
	}

	/**
	 * Whether each ride starts sitting (the sit key flips it on the way), and how far a sitting rider drops onto the seat.
	 */
	private void initRiding() {
		final int left = (width - BUTTON_WIDTH) / 2;
		addDrawableChild(ButtonWidget.builder(sitLabel(), b -> {
			RideClient.setSitWhenBoarding(!RideClient.sitWhenBoarding());
			b.setMessage(sitLabel());
		}).dimensions(left, TOP, BUTTON_WIDTH, 20).build()).setTooltip(Tooltip.of(Text.translatable("gui.aurelia.riding.sit.tooltip")));
		addDrawableChild(new SliderWidget(left, TOP + ROW, BUTTON_WIDTH, 20, Text.empty(), RideClient.sitDrop() / RideClient.MAX_SIT_DROP) {
			{
				updateMessage();
			}

			@Override
			protected void updateMessage() {
				setMessage(Text.translatable("gui.aurelia.riding.drop", String.format("%.2f", value * RideClient.MAX_SIT_DROP)));
			}

			@Override
			protected void applyValue() {
				RideClient.setSitDrop(value * RideClient.MAX_SIT_DROP);
			}

			@Override
			public void onRelease(double mouseX, double mouseY) {
				super.onRelease(mouseX, mouseY);
				RideClient.saveSitDrop();
			}
		});
	}

	/**
	 * Moving pantographs: whether they animate, how long a raise takes, which end rises when a part does not say, and the
	 * speed above which they stay up even off a route.
	 */
	private void initPantograph() {
		final int left = (width - BUTTON_WIDTH) / 2;
		addDrawableChild(ButtonWidget.builder(pantographAnimationLabel(), b -> {
			config.togglePantographAnimation();
			b.setMessage(pantographAnimationLabel());
		}).dimensions(left, TOP, BUTTON_WIDTH, 20).build()).setTooltip(Tooltip.of(Text.translatable("gui.aurelia.pantograph.animation.tooltip")));
		addDrawableChild(new PantographSlider(left, TOP + ROW, Client.PANTOGRAPH_RAISE_SECONDS_MAX, 0.1, config::getPantographRaiseSeconds, config::setPantographRaiseSeconds,
				seconds -> Text.translatable("gui.aurelia.pantograph.raise_time", String.format("%.1f", seconds))));
		addDrawableChild(ButtonWidget.builder(pantographEndLabel(), b -> {
			config.cyclePantographDefaultEnd();
			b.setMessage(pantographEndLabel());
		}).dimensions(left, TOP + ROW * 2, BUTTON_WIDTH, 20).build()).setTooltip(Tooltip.of(Text.translatable("gui.aurelia.pantograph.end.tooltip")));
		addDrawableChild(new PantographSlider(left, TOP + ROW * 3, Client.PANTOGRAPH_STAY_UP_KILOMETERS_PER_HOUR_MAX, 5, config::getPantographStayUpKilometersPerHour, config::setPantographStayUpKilometersPerHour,
				speed -> speed == 0 ? Text.translatable("gui.aurelia.pantograph.speed.off") : Text.translatable("gui.aurelia.pantograph.speed", speed.intValue())));
	}

	/**
	 * Train sounds: the pitch range past vanilla's 0.5-2, pitch bending across old-style motor steps, a door chime and
	 * the flange squeal volume.
	 */
	private void initTrainSound() {
		final int left = (width - BUTTON_WIDTH) / 2;
		addDrawableChild(ButtonWidget.builder(widePitchLabel(), b -> {
			config.toggleSoundWidePitch();
			b.setMessage(widePitchLabel());
		}).dimensions(left, TOP, BUTTON_WIDTH, 20).build()).setTooltip(Tooltip.of(Text.translatable("gui.aurelia.train_sound.wide_pitch.tooltip")));
		addDrawableChild(new RangeSlider(left, TOP + ROW, Client.SOUND_MAX_PITCH_MIN, Client.SOUND_MAX_PITCH_MAX, 0.25, config::getSoundMaxPitch, config::setSoundMaxPitch,
				pitch -> Text.translatable("gui.aurelia.train_sound.max_pitch", String.format("%.2f", pitch))));
		addDrawableChild(new RangeSlider(left, TOP + ROW * 2, 0, Client.MOTOR_PITCH_SMOOTHING_MAX, 0.05, config::getMotorPitchSmoothing, config::setMotorPitchSmoothing,
				spread -> spread == 0 ? Text.translatable("gui.aurelia.train_sound.motor_smoothing.off") : Text.translatable("gui.aurelia.train_sound.motor_smoothing", Math.round(spread * 100))))
				.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.train_sound.motor_smoothing.tooltip")));
		addDrawableChild(ButtonWidget.builder(doorChimeLabel(), b -> {
			config.cycleDoorChime();
			b.setMessage(doorChimeLabel());
		}).dimensions(left, TOP + ROW * 3, BUTTON_WIDTH, 20).build()).setTooltip(Tooltip.of(Text.translatable("gui.aurelia.train_sound.door_chime.tooltip")));
		addDrawableChild(new RangeSlider(left, TOP + ROW * 4, 0, 1, 0.05, config::getDoorChimeVolume, config::setDoorChimeVolume,
				volume -> Text.translatable("gui.aurelia.train_sound.door_chime_volume", Math.round(volume * 100))));
		addDrawableChild(new RangeSlider(left, TOP + ROW * 5, 0, 1, 0.05, config::getFlangeVolume, config::setFlangeVolume,
				volume -> volume == 0 ? Text.translatable("gui.aurelia.train_sound.flange_volume.off") : Text.translatable("gui.aurelia.train_sound.flange_volume", Math.round(volume * 100))));
	}

	private Text widePitchLabel() {
		return Text.translatable("gui.aurelia.train_sound.wide_pitch", ScreenTexts.onOrOff(config.getSoundWidePitch()));
	}

	private Text doorChimeLabel() {
		return Text.translatable("gui.aurelia.train_sound.door_chime", Text.translatable("gui.aurelia.train_sound.door_chime." + config.getDoorChime()));
	}

	/** Zip lines: on/off, top speed, brake and how far the cable sags. Operators only; the ride is shared. */
	private void initZip() {
		final int left = (width - BUTTON_WIDTH) / 2;
		final boolean operator = client != null && client.player != null && client.player.hasPermissionLevel(2);
		final ZipSettings now = ZipClient.settings();
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.zip.enabled", ScreenTexts.onOrOff(now.enabled())), b -> {
			final ZipSettings current = ZipClient.settings();
			ZipClient.request(new ZipSettings(!current.enabled(), current.speed(), current.brake(), current.sag()));
			b.setMessage(Text.translatable("gui.aurelia.zip.enabled", ScreenTexts.onOrOff(ZipClient.settings().enabled())));
		}).dimensions(left, TOP, BUTTON_WIDTH, 20).build()).active = operator;
		addDrawableChild(new RangeSlider(left, TOP + ROW, ZipSettings.SPEED_MIN, ZipSettings.SPEED_MAX, 1, () -> ZipClient.settings().speed(), speed -> {
			final ZipSettings current = ZipClient.settings();
			ZipClient.request(new ZipSettings(current.enabled(), speed, current.brake(), current.sag()));
		}, speed -> Text.translatable("gui.aurelia.zip.speed", speed.intValue()))).active = operator;
		addDrawableChild(new RangeSlider(left, TOP + ROW * 2, 0, ZipSettings.BRAKE_MAX, 0.1, () -> ZipClient.settings().brake(), brake -> {
			final ZipSettings current = ZipClient.settings();
			ZipClient.request(new ZipSettings(current.enabled(), current.speed(), brake, current.sag()));
		}, brake -> Text.translatable("gui.aurelia.zip.brake", String.format("%.1f", brake)))).active = operator;
		addDrawableChild(new RangeSlider(left, TOP + ROW * 3, 0, ZipSettings.SAG_MAX, 0.01, () -> ZipClient.settings().sag(), sag -> {
			final ZipSettings current = ZipClient.settings();
			ZipClient.request(new ZipSettings(current.enabled(), current.speed(), current.brake(), sag));
		}, sag -> Text.translatable("gui.aurelia.zip.sag", String.format("%.0f", sag * 100)))).active = operator;
	}

	/** One switch per restyled screen. Off returns that screen to MTR's widgets. */
	private void initLook() {
		lookFirst = Math.max(0, Math.min(lookFirst, LOOK_IDS.length - visibleRows()));
		final int left = (width - BUTTON_WIDTH) / 2;
		for (int i = lookFirst; i < Math.min(LOOK_IDS.length, lookFirst + visibleRows()); i++) {
			final AureliaUiSettings.ScreenId id = LOOK_IDS[i];
			addDrawableChild(ButtonWidget.builder(lookLabel(id), b -> {
				AureliaUiSettings.toggle(id);
				b.setMessage(lookLabel(id));
			}).dimensions(left, TOP + (i - lookFirst) * ROW, BUTTON_WIDTH, 20).build()).setTooltip(Tooltip.of(LOOK_HINT));
		}
	}

	private static Text lookLabel(AureliaUiSettings.ScreenId id) {
		return Text.translatable("gui.aurelia.look." + id.key, ScreenTexts.onOrOff(AureliaUiSettings.enabled(id)));
	}

	/** Building tools: station guide, rail stylus and the box tunnel. Operators only; the dig is shared. */
	private void initBuild() {
		buildFirst = Math.max(0, Math.min(buildFirst, BUILD_ROWS - passengerVisible()));
		final int left = (width - BUTTON_WIDTH) / 2;
		final boolean operator = client != null && client.player != null && client.player.hasPermissionLevel(2);
		final BuildSettings now = BuildClient.settings();
		if (buildRow(0)) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.build.station", ScreenTexts.onOrOff(now.stationEnabled())), b -> {
				final BuildSettings current = BuildClient.settings();
				sendBuild(new BuildSettings(!current.stationEnabled(), current.stationSide(), current.stationColour(), current.railEnabled(), current.snapDegrees(), current.railReach(), current.tunnelEnabled(), current.tunnelMode(), current.tunnelMaxBlocks(), current.blocksPerTick()));
				b.setMessage(Text.translatable("gui.aurelia.build.station", ScreenTexts.onOrOff(BuildClient.settings().stationEnabled())));
			}).dimensions(left, buildY(0), BUTTON_WIDTH, 20).build()).active = operator;
		}
		if (buildRow(1)) {
			addDrawableChild(new RangeSlider(left, buildY(1), BuildSettings.SIDE_MIN, BuildSettings.SIDE_MAX, 8, () -> BuildClient.settings().stationSide(), side -> {
				final BuildSettings current = BuildClient.settings();
				sendBuild(new BuildSettings(current.stationEnabled(), (int) side, current.stationColour(), current.railEnabled(), current.snapDegrees(), current.railReach(), current.tunnelEnabled(), current.tunnelMode(), current.tunnelMaxBlocks(), current.blocksPerTick()));
			}, side -> Text.translatable("gui.aurelia.build.side", side.intValue()))).active = operator;
		}
		if (buildRow(2)) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.build.colour", Integer.toHexString(now.stationColour())), b -> {
				final BuildSettings current = BuildClient.settings();
				sendBuild(new BuildSettings(current.stationEnabled(), current.stationSide(), current.nextColour(), current.railEnabled(), current.snapDegrees(), current.railReach(), current.tunnelEnabled(), current.tunnelMode(), current.tunnelMaxBlocks(), current.blocksPerTick()));
				b.setMessage(Text.translatable("gui.aurelia.build.colour", Integer.toHexString(BuildClient.settings().stationColour())));
			}).dimensions(left, buildY(2), BUTTON_WIDTH, 20).build()).active = operator;
		}
		if (buildRow(3)) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.build.rail", ScreenTexts.onOrOff(now.railEnabled())), b -> {
				final BuildSettings current = BuildClient.settings();
				sendBuild(new BuildSettings(current.stationEnabled(), current.stationSide(), current.stationColour(), !current.railEnabled(), current.snapDegrees(), current.railReach(), current.tunnelEnabled(), current.tunnelMode(), current.tunnelMaxBlocks(), current.blocksPerTick()));
				b.setMessage(Text.translatable("gui.aurelia.build.rail", ScreenTexts.onOrOff(BuildClient.settings().railEnabled())));
			}).dimensions(left, buildY(3), BUTTON_WIDTH, 20).build()).active = operator;
		}
		if (buildRow(4)) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.build.snap", now.snapDegrees() == 0 ? Text.translatable("gui.aurelia.build.snap.off") : now.snapDegrees()), b -> {
				final BuildSettings current = BuildClient.settings();
				final int next = current.snapDegrees() == 0 ? 15 : current.snapDegrees() == 15 ? 45 : current.snapDegrees() == 45 ? 90 : 0;
				sendBuild(new BuildSettings(current.stationEnabled(), current.stationSide(), current.stationColour(), current.railEnabled(), next, current.railReach(), current.tunnelEnabled(), current.tunnelMode(), current.tunnelMaxBlocks(), current.blocksPerTick()));
				final int shown = BuildClient.settings().snapDegrees();
				b.setMessage(Text.translatable("gui.aurelia.build.snap", shown == 0 ? Text.translatable("gui.aurelia.build.snap.off") : shown));
			}).dimensions(left, buildY(4), BUTTON_WIDTH, 20).build()).active = operator;
		}
		if (buildRow(5)) {
			addDrawableChild(new RangeSlider(left, buildY(5), BuildSettings.REACH_MIN, BuildSettings.REACH_MAX, 1, () -> BuildClient.settings().railReach(), reach -> {
				final BuildSettings current = BuildClient.settings();
				sendBuild(new BuildSettings(current.stationEnabled(), current.stationSide(), current.stationColour(), current.railEnabled(), current.snapDegrees(), (int) reach, current.tunnelEnabled(), current.tunnelMode(), current.tunnelMaxBlocks(), current.blocksPerTick()));
			}, reach -> Text.translatable("gui.aurelia.build.reach", reach.intValue()))).active = operator;
		}
		if (buildRow(6)) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.build.tunnel", ScreenTexts.onOrOff(now.tunnelEnabled())), b -> {
				final BuildSettings current = BuildClient.settings();
				sendBuild(new BuildSettings(current.stationEnabled(), current.stationSide(), current.stationColour(), current.railEnabled(), current.snapDegrees(), current.railReach(), !current.tunnelEnabled(), current.tunnelMode(), current.tunnelMaxBlocks(), current.blocksPerTick()));
				b.setMessage(Text.translatable("gui.aurelia.build.tunnel", ScreenTexts.onOrOff(BuildClient.settings().tunnelEnabled())));
			}).dimensions(left, buildY(6), BUTTON_WIDTH, 20).build()).active = operator;
		}
		if (buildRow(7)) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.build.mode", Text.translatable("gui.aurelia.build.mode." + now.tunnelMode().name().toLowerCase())), b -> {
				final BuildSettings current = BuildClient.settings();
				final BuildMath.Mode next = current.tunnelMode().next();
				sendBuild(new BuildSettings(current.stationEnabled(), current.stationSide(), current.stationColour(), current.railEnabled(), current.snapDegrees(), current.railReach(), current.tunnelEnabled(), next, current.tunnelMaxBlocks(), current.blocksPerTick()));
				b.setMessage(Text.translatable("gui.aurelia.build.mode", Text.translatable("gui.aurelia.build.mode." + BuildClient.settings().tunnelMode().name().toLowerCase())));
			}).dimensions(left, buildY(7), BUTTON_WIDTH, 20).build()).active = operator;
		}
		if (buildRow(8)) {
			addDrawableChild(new RangeSlider(left, buildY(8), BuildSettings.BLOCKS_MIN, BuildSettings.BLOCKS_MAX, 64, () -> BuildClient.settings().tunnelMaxBlocks(), max -> {
				final BuildSettings current = BuildClient.settings();
				sendBuild(new BuildSettings(current.stationEnabled(), current.stationSide(), current.stationColour(), current.railEnabled(), current.snapDegrees(), current.railReach(), current.tunnelEnabled(), current.tunnelMode(), (int) max, current.blocksPerTick()));
			}, max -> Text.translatable("gui.aurelia.build.max", max.intValue()))).active = operator;
		}
		if (buildRow(9)) {
			addDrawableChild(new RangeSlider(left, buildY(9), BuildSettings.TICK_MIN, BuildSettings.TICK_MAX, 1, () -> BuildClient.settings().blocksPerTick(), pace -> {
				final BuildSettings current = BuildClient.settings();
				sendBuild(new BuildSettings(current.stationEnabled(), current.stationSide(), current.stationColour(), current.railEnabled(), current.snapDegrees(), current.railReach(), current.tunnelEnabled(), current.tunnelMode(), current.tunnelMaxBlocks(), (int) pace));
			}, pace -> Text.translatable("gui.aurelia.build.pace", pace.intValue()))).active = operator;
		}
	}

	private boolean buildRow(int row) {
		return row >= buildFirst && row < buildFirst + passengerVisible();
	}

	private int buildY(int row) {
		return TOP + (row - buildFirst) * ROW;
	}

	private void sendBuild(BuildSettings next) {
		BuildClient.request(next);
	}

	/**
	 * Passengers. The first five rows are the server caps (operators only): on/off, how many arrive per platform per
	 * minute, the most a platform and the whole dimension may hold, and seats per car. The rest are this client's
	 * drawing switches, which anyone can change. The measured cost is drawn under the rows.
	 */
	private void initPassengers() {
		passengerFirst = Math.max(0, Math.min(passengerFirst, PASSENGER_ROWS - passengerVisible()));
		final int left = (width - BUTTON_WIDTH) / 2;
		final boolean operator = client != null && client.player != null && client.player.hasPermissionLevel(2);
		if (passengerRow(0)) {
			final ButtonWidget enabled = addDrawableChild(ButtonWidget.builder(passengersLabel(PassengerClient.settings().enabled()), b -> {
				final PassengerNetwork.Settings now = PassengerClient.settings();
				PassengerClient.request(new PassengerNetwork.Settings(!now.enabled(), now.perPlatformPerMinute(), now.maxWaitingPerPlatform(), now.maxTotal(), now.seatsPerCar()));
				b.setMessage(passengersLabel(!now.enabled()));
			}).dimensions(left, passengerY(0), BUTTON_WIDTH, 20).build());
			enabled.active = operator;
			enabled.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.passengers.tooltip")));
		}
		if (passengerRow(1)) {
			addPassengerSlider(left, 1, operator, 0, PassengerNetwork.Settings.MAX_PER_MINUTE, 1, () -> PassengerClient.settings().perPlatformPerMinute(),
					value -> sendPassengers(s -> new PassengerNetwork.Settings(s.enabled(), (int) value, s.maxWaitingPerPlatform(), s.maxTotal(), s.seatsPerCar())),
					value -> Text.translatable("gui.aurelia.passengers.per_minute", value.intValue()));
		}
		if (passengerRow(2)) {
			addPassengerSlider(left, 2, operator, 0, PassengerNetwork.Settings.MAX_WAITING, 5, () -> PassengerClient.settings().maxWaitingPerPlatform(),
					value -> sendPassengers(s -> new PassengerNetwork.Settings(s.enabled(), s.perPlatformPerMinute(), (int) value, s.maxTotal(), s.seatsPerCar())),
					value -> Text.translatable("gui.aurelia.passengers.max_waiting", value.intValue()));
		}
		if (passengerRow(3)) {
			addPassengerSlider(left, 3, operator, 0, PassengerNetwork.Settings.MAX_TOTAL, 250, () -> PassengerClient.settings().maxTotal(),
					value -> sendPassengers(s -> new PassengerNetwork.Settings(s.enabled(), s.perPlatformPerMinute(), s.maxWaitingPerPlatform(), (int) value, s.seatsPerCar())),
					value -> Text.translatable("gui.aurelia.passengers.max_total", value.intValue()));
		}
		if (passengerRow(4)) {
			addPassengerSlider(left, 4, operator, 1, PassengerNetwork.Settings.MAX_SEATS, 1, () -> PassengerClient.settings().seatsPerCar(),
					value -> sendPassengers(s -> new PassengerNetwork.Settings(s.enabled(), s.perPlatformPerMinute(), s.maxWaitingPerPlatform(), s.maxTotal(), (int) value)),
					value -> Text.translatable("gui.aurelia.passengers.seats", value.intValue()));
		}
		if (passengerRow(5)) {
			final ButtonWidget draw = addDrawableChild(ButtonWidget.builder(drawLabel(), b -> {
				PassengerDrawSettings.setDraw(!PassengerDrawSettings.get().draw());
				b.setMessage(drawLabel());
			}).dimensions(left, passengerY(5), BUTTON_WIDTH, 20).build());
			draw.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.passengers.draw.tooltip")));
		}
		if (passengerRow(6)) {
			addPassengerSlider(left, 6, true, 0, PassengerDrawSettings.DRAW_DISTANCE_MAX, 8, () -> PassengerDrawSettings.get().drawDistance(),
					value -> PassengerDrawSettings.setDrawDistance((int) value),
					value -> Text.translatable("gui.aurelia.passengers.draw_distance", value.intValue()));
		}
		if (passengerRow(7)) {
			addPassengerSlider(left, 7, true, 0, PassengerDrawSettings.DETAIL_DISTANCE_MAX, 4, () -> PassengerDrawSettings.get().detailDistance(),
					value -> PassengerDrawSettings.setDetailDistance((int) value),
					value -> Text.translatable("gui.aurelia.passengers.detail_distance", value.intValue()));
		}
		if (passengerRow(8)) {
			addPassengerSlider(left, 8, true, 0, PassengerDrawSettings.MAX_PER_PLACE_MAX, 1, () -> PassengerDrawSettings.get().maxPerPlace(),
					value -> PassengerDrawSettings.setMaxPerPlace((int) value),
					value -> Text.translatable("gui.aurelia.passengers.max_drawn", value.intValue()));
		}
	}

	/** Rows left after the waiting/riding line and the server-cost line. */
	private int passengerVisible() {
		return Math.max(1, visibleRows() - 2);
	}

	private boolean passengerRow(int row) {
		return row >= passengerFirst && row < passengerFirst + passengerVisible();
	}

	private int passengerY(int row) {
		return TOP + (row - passengerFirst) * ROW;
	}

	private void addPassengerSlider(int left, int row, boolean active, double min, double max, double step, DoubleSupplier get, DoubleConsumer set, Function<Double, Text> label) {
		final RangeSlider slider = addDrawableChild(new RangeSlider(left, passengerY(row), min, max, step, get, set, label));
		slider.active = active;
	}

	private static void sendPassengers(UnaryOperator<PassengerNetwork.Settings> change) {
		PassengerClient.request(change.apply(PassengerClient.settings()));
	}

	private static Text passengersLabel(boolean enabled) {
		return Text.translatable("gui.aurelia.passengers.enabled", ScreenTexts.onOrOff(enabled));
	}

	private static Text drawLabel() {
		return Text.translatable("gui.aurelia.passengers.draw", ScreenTexts.onOrOff(PassengerDrawSettings.get().draw()));
	}

	private Text pantographAnimationLabel() {
		return Text.translatable("gui.aurelia.pantograph.animation", ScreenTexts.onOrOff(config.getPantographAnimation()));
	}

	private Text pantographEndLabel() {
		final PantographEnd end = config.getPantographDefaultEnd();
		return Text.translatable("gui.aurelia.pantograph.end", Text.translatable("gui.aurelia.pantograph.end." + (end == PantographEnd.DEFAULT ? PantographEnd.BOTH : end).name().toLowerCase()));
	}

	private static Text sitLabel() {
		return Text.translatable("gui.aurelia.riding.sit", ScreenTexts.onOrOff(RideClient.sitWhenBoarding()));
	}

	/**
	 * Width and height sliders and the three part switches. Each change is written to the stack here and sent to the server.
	 */
	private void initBuilder() {
		final boolean offHand = !isRailBuilder(client.player.getMainHandStack()) && isRailBuilder(client.player.getOffHandStack());
		final net.minecraft.item.ItemStack itemStack = offHand ? client.player.getOffHandStack() : client.player.getMainHandStack();
		if (!isRailBuilder(itemStack)) {
			return;
		}
		final int left = (width - BUTTON_WIDTH) / 2;
		final UnaryOperator<UnaryOperator<ItemRailBuilder.Settings>> update = change -> settings -> {
			final ItemRailBuilder.Settings newSettings = change.apply(settings);
			ItemRailBuilder.write(itemStack, newSettings);
			InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateRailBuilder(offHand, newSettings));
			return newSettings;
		};
		addDrawableChild(new BuilderSlider(left, TOP, "gui.aurelia.builder.width", ItemRailBuilder.MIN_WIDTH, ItemRailBuilder.MAX_WIDTH, 2, ItemRailBuilder.read(itemStack).width(),
				value -> update.apply(settings -> new ItemRailBuilder.Settings(value, settings.height(), settings.deck(), settings.clear(), settings.walls())).apply(ItemRailBuilder.read(itemStack))));
		addDrawableChild(new BuilderSlider(left, TOP + ROW, "gui.aurelia.builder.height", ItemRailBuilder.MIN_HEIGHT, ItemRailBuilder.MAX_HEIGHT, 1, ItemRailBuilder.read(itemStack).height(),
				value -> update.apply(settings -> new ItemRailBuilder.Settings(settings.width(), value, settings.deck(), settings.clear(), settings.walls())).apply(ItemRailBuilder.read(itemStack))));
		addBuilderToggle(left, TOP + ROW * 2, "gui.aurelia.builder.deck", itemStack, ItemRailBuilder.Settings::deck,
				settings -> new ItemRailBuilder.Settings(settings.width(), settings.height(), !settings.deck(), settings.clear(), settings.walls()), update);
		addBuilderToggle(left, TOP + ROW * 3, "gui.aurelia.builder.clear", itemStack, ItemRailBuilder.Settings::clear,
				settings -> new ItemRailBuilder.Settings(settings.width(), settings.height(), settings.deck(), !settings.clear(), settings.walls()), update);
		addBuilderToggle(left, TOP + ROW * 4, "gui.aurelia.builder.walls", itemStack, ItemRailBuilder.Settings::walls,
				settings -> new ItemRailBuilder.Settings(settings.width(), settings.height(), settings.deck(), settings.clear(), !settings.walls()), update);
	}

	private void addBuilderToggle(int x, int y, String key, net.minecraft.item.ItemStack itemStack, Function<ItemRailBuilder.Settings, Boolean> get, UnaryOperator<ItemRailBuilder.Settings> flip, UnaryOperator<UnaryOperator<ItemRailBuilder.Settings>> update) {
		addDrawableChild(ButtonWidget.builder(Text.translatable(key, ScreenTexts.onOrOff(get.apply(ItemRailBuilder.read(itemStack)))), b -> {
			final ItemRailBuilder.Settings settings = update.apply(flip).apply(ItemRailBuilder.read(itemStack));
			b.setMessage(Text.translatable(key, ScreenTexts.onOrOff(get.apply(settings))));
		}).dimensions(x, y, BUTTON_WIDTH, 20).build());
	}

	private static boolean isRailBuilder(net.minecraft.item.ItemStack itemStack) {
		return itemStack.getItem() == Items.RAIL_BUILDER.get().data;
	}

	/** ATA's tools: Station Studio opens in the browser; each wrench screen edits the wrench in the player's hand. */
	private void initArchitecture() {
		final int left = (width - BUTTON_WIDTH) / 2;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.architecture.studio"), b -> StationStudioClient.open())
				.dimensions(left, TOP, BUTTON_WIDTH, 20).build())
				.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.architecture.studio.tooltip")));
		addWrenchButton("station", ModItems.STATION_WRENCH, StationWrenchScreen::open, TOP + ROW);
		addWrenchButton("airport", ModItems.AIRPORT_WRENCH, AirportWrenchScreen::open, TOP + ROW * 2);
	}

	private void addWrenchButton(String key, Item wrench, Consumer<Hand> open, int y) {
		final Hand hand = heldHand(wrench);
		final ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.architecture." + key), b -> open.accept(hand))
				.dimensions((width - BUTTON_WIDTH) / 2, y, BUTTON_WIDTH, 20).build());
		button.active = hand != null;
		button.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.architecture." + key + (hand == null ? ".hold" : ".tooltip"))));
	}

	@Nullable
	private Hand heldHand(Item item) {
		if (client == null || client.player == null) {
			return null;
		}
		for (final Hand hand : Hand.values()) {
			if (client.player.getStackInHand(hand).isOf(item)) {
				return hand;
			}
		}
		return null;
	}

	private static Text platformLabel() {
		return Text.translatable("gui.aurelia.tools.platform", ScreenTexts.onOrOff(Announcer.platformEnabled()));
	}

	private static Text melodiesLabel() {
		return Text.translatable("gui.aurelia.tools.melodies", ScreenTexts.onOrOff(ScriptingHandshake.melodiesEnabled()));
	}

	private static Text announcerLabel() {
		return Text.translatable("gui.aurelia.tools.announcer", ScreenTexts.onOrOff(Announcer.enabled()));
	}

	private static Text actionBarLabel() {
		return Text.translatable("gui.aurelia.tools.announcer.action_bar", ScreenTexts.onOrOff(Announcer.actionBar()));
	}

	private static Text wordingLabel() {
		return Text.translatable("gui.aurelia.tools.announcer.wording", Text.translatable("gui.aurelia.tools.announcer.wording." + Announcer.wording().id()));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (tab == Tab.RENDERING) {
			final int newFirstRow = Math.max(0, Math.min(renderingRows.size() - visibleRows(), firstRow - (int) Math.signum(amount)));
			if (newFirstRow != firstRow) {
				firstRow = newFirstRow;
				clearAndInit();
			}
			return true;
		}
		if (tab == Tab.LOOK) {
			final int newFirst = Math.max(0, Math.min(LOOK_IDS.length - visibleRows(), lookFirst - (int) Math.signum(amount)));
			if (newFirst != lookFirst) {
				lookFirst = newFirst;
				clearAndInit();
			}
			return true;
		}
		if (tab == Tab.BUILD) {
			final int newFirstRow = Math.max(0, Math.min(BUILD_ROWS - passengerVisible(), buildFirst - (int) Math.signum(amount)));
			if (newFirstRow != buildFirst) {
				buildFirst = newFirstRow;
				clearAndInit();
			}
			return true;
		}
		if (tab == Tab.PASSENGERS) {
			final int newFirstRow = Math.max(0, Math.min(PASSENGER_ROWS - passengerVisible(), passengerFirst - (int) Math.signum(amount)));
			if (newFirstRow != passengerFirst) {
				passengerFirst = newFirstRow;
				clearAndInit();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
		if (tab == Tab.RENDERING) {
			for (int i = firstRow; i < Math.min(renderingRows.size(), firstRow + visibleRows()); i++) {
				context.drawTextWithShadow(textRenderer, Text.translatable(renderingRows.get(i).key()), 16, TOP + (i - firstRow) * ROW + 6, 0xFFFFFF);
			}
			if (renderingRows.size() > visibleRows()) {
				context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.panel.scroll", firstRow + 1, Math.min(renderingRows.size(), firstRow + visibleRows()), renderingRows.size()), width / 2, height - 44, 0xAAAAAA);
			}
		} else if (tab == Tab.PASSENGERS) {
			final PassengerService.Stats stats = PassengerClient.stats();
			final int statsY = TOP + passengerVisible() * ROW;
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.passengers.stats", stats.waiting(), stats.riding(), stats.boarded(), stats.alighted()), width / 2, statsY + 6, 0xFFFFFF);
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.passengers.cost", String.format("%.2f", stats.costMicros() / 1000D)), width / 2, statsY + 18, 0xAAAAAA);
			if (client.player != null && !client.player.hasPermissionLevel(2)) {
				context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.passengers.operators"), width / 2, TOP - 10, 0xFF5555);
			}
		} else if (tab == Tab.BUILD && client.player != null && !client.player.hasPermissionLevel(2)) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.build.operators"), width / 2, TOP - 10, 0xFF5555);
		} else if (tab == Tab.ZIP && client.player != null && !client.player.hasPermissionLevel(2)) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.zip.operators"), width / 2, TOP - 10, 0xFF5555);
		} else if (tab == Tab.TOOLS && !MtrBridge.canEdit()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.no_permission"), width / 2, TOP - 10, 0xFF5555);
		} else if (tab == Tab.BUILDER && !isRailBuilder(client.player.getMainHandStack()) && !isRailBuilder(client.player.getOffHandStack())) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.builder.hold"), width / 2, TOP + 6, 0xAAAAAA);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		Config.save();
		client.setScreen(parent);
	}

	/** A Rail Builder number from {@code min} to {@code max} in steps of {@code step}. */
	private static final class BuilderSlider extends SliderWidget {

		private final String key;
		private final int min;
		private final int steps;
		private final int step;
		private final IntConsumer set;

		private BuilderSlider(int x, int y, String key, int min, int max, int step, int current, IntConsumer set) {
			super(x, y, BUTTON_WIDTH, 20, Text.empty(), (double) (current - min) / step / ((max - min) / step));
			this.key = key;
			this.min = min;
			this.steps = (max - min) / step;
			this.step = step;
			this.set = set;
			updateMessage();
		}

		private int current() {
			return min + (int) Math.round(value * steps) * step;
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable(key, current()));
		}

		@Override
		protected void applyValue() {
			set.accept(current());
		}
	}

	/** Whole chunks from 0 (off, shown as "-") to the row's maximum. */
	private static final class DistanceSlider extends SliderWidget {

		private final Distance row;

		private DistanceSlider(int x, int y, Distance row) {
			super(x, y, VALUE_WIDTH, 20, Text.empty(), (double) row.get().getAsInt() / row.max());
			this.row = row;
			updateMessage();
		}

		private int chunks() {
			return (int) Math.round(value * row.max());
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.literal(chunks() == 0 ? "-" : String.valueOf(chunks())));
		}

		@Override
		protected void applyValue() {
			row.set().accept(chunks());
		}
	}

	/** A number from 0 to {@code max} in steps of {@code step}, written to the client config as it moves. */
	private static final class PantographSlider extends SliderWidget {

		private final double max;
		private final double step;
		private final DoubleConsumer set;
		private final Function<Double, Text> label;

		private PantographSlider(int x, int y, double max, double step, DoubleSupplier get, DoubleConsumer set, Function<Double, Text> label) {
			super(x, y, BUTTON_WIDTH, 20, Text.empty(), get.getAsDouble() / max);
			this.max = max;
			this.step = step;
			this.set = set;
			this.label = label;
			updateMessage();
		}

		private double current() {
			return Math.round(value * max / step) * step;
		}

		@Override
		protected void updateMessage() {
			setMessage(label.apply(current()));
		}

		@Override
		protected void applyValue() {
			set.accept(current());
		}
	}

	/** A number from {@code min} to {@code max} in steps of {@code step}, written to the client config as it moves. */
	private static final class RangeSlider extends SliderWidget {

		private final double min;
		private final double max;
		private final double step;
		private final DoubleConsumer set;
		private final Function<Double, Text> label;

		private RangeSlider(int x, int y, double min, double max, double step, DoubleSupplier get, DoubleConsumer set, Function<Double, Text> label) {
			super(x, y, BUTTON_WIDTH, 20, Text.empty(), (get.getAsDouble() - min) / (max - min));
			this.min = min;
			this.max = max;
			this.step = step;
			this.set = set;
			this.label = label;
			updateMessage();
		}

		private double current() {
			return min + Math.round(value * (max - min) / step) * step;
		}

		@Override
		protected void updateMessage() {
			setMessage(label.apply(current()));
		}

		@Override
		protected void applyValue() {
			set.accept(current());
		}
	}
}
