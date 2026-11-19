package org.mtr.aurelia.client.screen;

import com.aureliatransit.architecture.client.AtrMenuScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.mtr.aurelia.client.AureliaClient;
import org.mtr.aurelia.client.MtrBridge;
import org.mtr.aurelia.client.map.MapView;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.client.ui.AureliaUiSettings;
import org.mtr.aurelia.dashboard.DashboardLayout;
import org.mtr.aurelia.dashboard.DashboardMapState;
import org.mtr.aurelia.dashboard.DashboardMode;
import org.mtr.aurelia.dashboard.RoadVehicleListModel;
import org.mtr.aurelia.dashboard.RoadVehicleListModel.Filter;
import org.mtr.aurelia.dashboard.RoadVehicleListModel.RoadVehicleRow;
import org.mtr.aurelia.dashboard.RoadVehicleListModel.StationInterchangeRow;
import org.mtr.aurelia.dashboard.UnifiedDashboardClient;
import org.mtr.aurelia.map.MapCamera;
import org.mtr.aurelia.vehicle.RoadVehicleEntity;
import org.mtr.aurelia.vehicle.RoadVehicleItem;
import org.mtr.aurelia.vehicle.RoadVehicleKind;
import org.mtr.aurelia.vehicle.RoadVehicleSettings;
import org.mtr.aurelia.vehicle.RoadVehicles;
import org.mtr.aurelia.vehicle.client.RoadVehicleClient;
import org.mtr.core.data.Station;
import org.mtr.mod.data.IGui;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * The Bus & Road view of ATR's unified Transit Dashboard.
 * Shares the exact 144 px left panel, right-hand interactive map, ATU/ATR icon buttons, and five-mode header bar
 * with MTR's {@code DashboardScreen}, while giving live visibility and controls for road vehicles and interchanges.
 */
public final class RoadDashboardScreen extends Screen {

	public enum SubTab {VEHICLES, STOPS, SETTINGS}

	private static final Identifier DASHBOARD_BUTTON_TEXTURE = new Identifier(AureliaClient.MOD_ID, "textures/gui/dashboard_button.png");
	private static final int PANEL_WIDTH = DashboardLayout.PANEL_WIDTH;
	private static final int SQUARE_SIZE = IGui.SQUARE_SIZE;
	private static final int REFRESH_TICKS = 10;
	private static final int PANEL_DIVIDER = 0xFF2A3240;
	private static final int CARD_BG = 0xE814181E;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_WARN = 0xFFAA00;
	private static final int COLOR_ACCENT_TEXT = 0x7AB8FF;

	private final MapCamera camera;
	private double preservedMtrScale;
	private final boolean preservedCurrentY;

	private SubTab subTab = SubTab.VEHICLES;
	private Filter vehicleFilter = Filter.ALL;
	private boolean interchangesOnly;
	private String searchQuery = "";
	private int selectedSpawnIndex;

	private List<RoadVehicleRow> allVehicles = List.of();
	private List<RoadVehicleRow> filteredVehicles = List.of();
	@Nullable
	private Integer selectedVehicleId;

	private List<StationInterchangeRow> allStations = List.of();
	private List<StationInterchangeRow> filteredStations = List.of();
	@Nullable
	private Long selectedStationId;

	@Nullable
	private TextFieldWidget searchField;
	@Nullable
	private ButtonWidget filterButton;
	@Nullable
	private SimpleListWidget<Integer> vehicleList;
	@Nullable
	private SimpleListWidget<Long> stationList;
	@Nullable
	private ButtonWidget focusButton;
	@Nullable
	private ButtonWidget teleportButton;
	@Nullable
	private ButtonWidget giveVehicleButton;
	@Nullable
	private ButtonWidget enabledButton;
	@Nullable
	private ButtonWidget bikeShareButton;

	private DashboardLayout layout;
	private boolean draggingMap;
	private int ticks;

	public RoadDashboardScreen(@Nullable DashboardMapState initialState) {
		super(Text.translatable("gui.aurelia.dashboard.road.title"));
		final DashboardMapState state = initialState != null ? initialState : UnifiedDashboardClient.defaultMapState(net.minecraft.client.MinecraftClient.getInstance());
		this.camera = new MapCamera(state.centerX(), state.centerZ(), state.scaleForRoadCamera());
		this.preservedMtrScale = state.scale();
		this.preservedCurrentY = state.currentY();
	}

	public DashboardMapState captureMapState() {
		return new DashboardMapState(camera.centerX(), camera.centerZ(), preservedMtrScale, preservedCurrentY);
	}

	@Override
	protected void init() {
		searchField = null;
		filterButton = null;
		vehicleList = null;
		stationList = null;
		focusButton = null;
		teleportButton = null;
		giveVehicleButton = null;
		enabledButton = null;
		bikeShareButton = null;

		// MAGIC only injects its (150, 6) button into MTR's DashboardScreen, so RoadDashboardScreen places ATU/ATR at y=6.
		layout = DashboardLayout.compute(width, height, false);

		// Left panel sub-tabs (48 px each across 144 px, matching Stations | Routes | Depots)
		final int tabW = PANEL_WIDTH / 3;
		addSubTabButton(0, SubTab.VEHICLES, "vehicles", tabW);
		addSubTabButton(tabW, SubTab.STOPS, "stops", tabW);
		addSubTabButton(tabW * 2, SubTab.SETTINGS, "settings", PANEL_WIDTH - tabW * 2);

		switch (subTab) {
			case VEHICLES -> initVehiclesPanel();
			case STOPS -> initStopsPanel();
			case SETTINGS -> initSettingsPanel();
		}

		// Top-left map icon buttons (ATU panel + ATR menu)
		final DashboardLayout.Rect atuRect = layout.atuButton();
		final TexturedButtonWidget atuButton = new TexturedButtonWidget(
				atuRect.x(), atuRect.y(), atuRect.width(), atuRect.height(),
				0, 0, DashboardLayout.ICON_BUTTON_SIZE, DASHBOARD_BUTTON_TEXTURE,
				DashboardLayout.ICON_BUTTON_SIZE, DashboardLayout.ICON_BUTTON_SIZE * 2,
				b -> client.setScreen(new AureliaPanelScreen(this, AureliaPanelScreen.Tab.VEHICLES)),
				Text.translatable("gui.aurelia.dashboard_button")
		);
		atuButton.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.panel.title")));
		addDrawableChild(atuButton);

		final DashboardLayout.Rect atrRect = layout.atrMenuButton();
		final TexturedButtonWidget atrButton = new TexturedButtonWidget(
				atrRect.x(), atrRect.y(), atrRect.width(), atrRect.height(),
				0, 0, DashboardLayout.ICON_BUTTON_SIZE, DASHBOARD_BUTTON_TEXTURE,
				DashboardLayout.ICON_BUTTON_SIZE, DashboardLayout.ICON_BUTTON_SIZE * 2,
				b -> client.setScreen(new AtrMenuScreen(this, AtrMenuScreen.Tab.BUILD)),
				Text.translatable("gui.aurelia.menu.title")
		);
		atrButton.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.menu.title")));
		addDrawableChild(atrButton);

		// Unified 5-mode switcher bar
		UnifiedDashboardClient.attachModeButtons(client, this, DashboardMode.ROAD, layout, this::addDrawableChild);

		// Bottom-right map zoom & centre buttons
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> zoomBy(2.0))
				.dimensions(width - SQUARE_SIZE * 2, height - SQUARE_SIZE * 2, SQUARE_SIZE, SQUARE_SIZE).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> zoomBy(0.5))
				.dimensions(width - SQUARE_SIZE, height - SQUARE_SIZE * 2, SQUARE_SIZE, SQUARE_SIZE).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.map.centre"), b -> centreOnPlayer())
				.dimensions(width - 60, height - SQUARE_SIZE, 60, SQUARE_SIZE).build());

		refreshData();
	}

	private void addSubTabButton(int x, SubTab target, String key, int w) {
		final ButtonWidget btn = addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.aurelia.dashboard.road.tab." + key),
				b -> selectSubTab(target)
		).dimensions(x, 0, w, SQUARE_SIZE).build());
		btn.active = subTab != target;
	}

	private void selectSubTab(SubTab target) {
		if (subTab == target) {
			return;
		}
		subTab = target;
		clearAndInit();
	}

	private void initVehiclesPanel() {
		searchField = addDrawableChild(new TextFieldWidget(textRenderer, 4, 24, PANEL_WIDTH - 8, 16, Text.translatable("gui.aurelia.dashboard.road.search")));
		searchField.setPlaceholder(Text.translatable("gui.aurelia.dashboard.road.search"));
		searchField.setText(searchQuery);
		searchField.setChangedListener(text -> {
			searchQuery = text;
			applyVehicleFilter();
		});

		filterButton = addDrawableChild(ButtonWidget.builder(vehicleFilterLabel(), b -> {
			cycleVehicleFilter();
			b.setMessage(vehicleFilterLabel());
			applyVehicleFilter();
		}).dimensions(4, 43, PANEL_WIDTH - 8, 18).build());

		final int listTop = 64;
		final int listHeight = Math.max(24, height - 42 - listTop);
		vehicleList = addDrawableChild(new SimpleListWidget<>(client, 4, listTop, PANEL_WIDTH - 8, listHeight, id -> {
			selectedVehicleId = id;
			updateActionButtons();
		}));

		final int half = PANEL_WIDTH / 2;
		focusButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.dashboard.road.locate"), b -> focusSelectedVehicle())
				.dimensions(0, height - SQUARE_SIZE * 2, half, SQUARE_SIZE).build());
		focusButton.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.dashboard.road.locate.tooltip")));

		teleportButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.jump.go"), b -> teleportToSelectedVehicle())
				.dimensions(half, height - SQUARE_SIZE * 2, PANEL_WIDTH - half, SQUARE_SIZE).build());

		addVehicleSpawnRow(height - SQUARE_SIZE, 0, PANEL_WIDTH);
	}

	private void initStopsPanel() {
		searchField = addDrawableChild(new TextFieldWidget(textRenderer, 4, 24, PANEL_WIDTH - 8, 16, Text.translatable("gui.aurelia.dashboard.road.search_stops")));
		searchField.setPlaceholder(Text.translatable("gui.aurelia.dashboard.road.search_stops"));
		searchField.setText(searchQuery);
		searchField.setChangedListener(text -> {
			searchQuery = text;
			applyStationFilter();
		});

		filterButton = addDrawableChild(ButtonWidget.builder(stationFilterLabel(), b -> {
			interchangesOnly = !interchangesOnly;
			b.setMessage(stationFilterLabel());
			applyStationFilter();
		}).dimensions(4, 43, PANEL_WIDTH - 8, 18).build());

		final int listTop = 64;
		final int listHeight = Math.max(24, height - 42 - listTop);
		stationList = addDrawableChild(new SimpleListWidget<>(client, 4, listTop, PANEL_WIDTH - 8, listHeight, id -> {
			selectedStationId = id;
			updateActionButtons();
		}));

		final int half = PANEL_WIDTH / 2;
		focusButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.dashboard.road.locate"), b -> focusSelectedStation())
				.dimensions(0, height - SQUARE_SIZE * 2, half, SQUARE_SIZE).build());
		teleportButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.jump.go"), b -> teleportToSelectedStation())
				.dimensions(half, height - SQUARE_SIZE * 2, PANEL_WIDTH - half, SQUARE_SIZE).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.tools.jump"), b -> client.setScreen(new JumpScreen(this)))
				.dimensions(0, height - SQUARE_SIZE, PANEL_WIDTH, SQUARE_SIZE).build());
	}

	private void initSettingsPanel() {
		final boolean mayEdit = RoadVehicleClient.mayEdit(client);
		final int x = 4;
		final int w = PANEL_WIDTH - 8;

		enabledButton = addDrawableChild(ButtonWidget.builder(vehiclesEnabledLabel(), b -> {
			final RoadVehicleSettings next = RoadVehicles.settings(true).withEnabled(!RoadVehicles.settings(true).enabled());
			RoadVehicles.setClientSettings(next);
			b.setMessage(vehiclesEnabledLabel());
			RoadVehicleClient.request(next);
		}).dimensions(x, 26, w, 20).build());
		enabledButton.active = mayEdit;

		bikeShareButton = addDrawableChild(ButtonWidget.builder(bikeShareLabel(), b -> {
			final RoadVehicleSettings next = RoadVehicles.settings(true).withBikeShare(!RoadVehicles.settings(true).bikeShare());
			RoadVehicles.setClientSettings(next);
			b.setMessage(bikeShareLabel());
			RoadVehicleClient.request(next);
		}).dimensions(x, 48, w, 20).build());
		bikeShareButton.active = mayEdit;

		final CompactSlider speedSlider = addDrawableChild(new CompactSlider(
				x, 70, w,
				RoadVehicleSettings.SPEED_MIN, RoadVehicleSettings.SPEED_MAX, 5,
				() -> RoadVehicles.settings(true).maxSpeedKmh(),
				val -> RoadVehicles.setClientSettings(RoadVehicles.settings(true).withSpeed((int) val)),
				val -> RoadVehicleClient.request(RoadVehicles.settings(true).withSpeed((int) val)),
				val -> Text.translatable("gui.aurelia.vehicles.speed", val.intValue())
		));
		speedSlider.active = mayEdit;

		final CompactSlider sizeSlider = addDrawableChild(new CompactSlider(
				x, 92, w,
				RoadVehicleSettings.SIZE_MIN, RoadVehicleSettings.SIZE_MAX, 5,
				() -> RoadVehicles.settings(true).sizePercent(),
				val -> RoadVehicles.setClientSettings(RoadVehicles.settings(true).withSize((int) val)),
				val -> RoadVehicleClient.request(RoadVehicles.settings(true).withSize((int) val)),
				val -> Text.translatable("gui.aurelia.vehicles.size", val.intValue())
		));
		sizeSlider.active = mayEdit;

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.dashboard.road.paint_panel"),
				b -> client.setScreen(new AureliaPanelScreen(this, AureliaPanelScreen.Tab.VEHICLES)))
				.dimensions(x, 114, w, 20).build())
				.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.dashboard.road.paint_panel.tooltip")));

		addVehicleSpawnRow(136, x, w);
	}

	private void addVehicleSpawnRow(int y, int x, int totalWidth) {
		final List<Item> vehicleItems = registeredRoadVehicleItems();
		if (vehicleItems.isEmpty()) {
			return;
		}
		selectedSpawnIndex = Math.floorMod(selectedSpawnIndex, vehicleItems.size());
		final boolean multiple = vehicleItems.size() > 1;
		final int giveWidth = multiple ? totalWidth - 22 : totalWidth;
		final boolean canGive = canGiveItems();

		giveVehicleButton = addDrawableChild(ButtonWidget.builder(giveVehicleLabel(vehicleItems.get(selectedSpawnIndex)), b -> {
			final List<Item> current = registeredRoadVehicleItems();
			if (!current.isEmpty() && canGiveItems() && client != null && client.player != null) {
				final Item chosen = current.get(Math.floorMod(selectedSpawnIndex, current.size()));
				client.player.networkHandler.sendChatCommand("give @s " + Registries.ITEM.getId(chosen));
			}
		}).dimensions(x, y, giveWidth, SQUARE_SIZE).build());
		giveVehicleButton.active = canGive;
		giveVehicleButton.setTooltip(Tooltip.of(Text.translatable(
				canGive ? "gui.aurelia.dashboard.road.give.tooltip" : "gui.aurelia.dashboard.road.give.ops")));

		if (multiple) {
			addDrawableChild(ButtonWidget.builder(Text.literal("▸"), b -> {
				selectedSpawnIndex = (selectedSpawnIndex + 1) % vehicleItems.size();
				if (giveVehicleButton != null) {
					giveVehicleButton.setMessage(giveVehicleLabel(vehicleItems.get(selectedSpawnIndex)));
				}
			}).dimensions(x + giveWidth + 2, y, 20, SQUARE_SIZE).build())
					.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.dashboard.road.cycle_item")));
		}
	}

	private static List<Item> registeredRoadVehicleItems() {
		final List<Item> items = new ArrayList<>();
		items.add(RoadVehicles.CAR_ITEM);
		for (final Item item : Registries.ITEM) {
			if (item instanceof RoadVehicleItem && !items.contains(item)) {
				items.add(item);
			}
		}
		return items;
	}

	private static Text giveVehicleLabel(Item item) {
		return Text.translatable("gui.aurelia.dashboard.road.give", item.getName());
	}

	private static Text vehiclesEnabledLabel() {
		return Text.translatable("gui.aurelia.vehicles.enabled", ScreenTexts.onOrOff(RoadVehicles.settings(true).enabled()));
	}

	private static Text bikeShareLabel() {
		return Text.translatable("gui.aurelia.vehicles.bike_share", ScreenTexts.onOrOff(RoadVehicles.settings(true).bikeShare()));
	}

	private Text vehicleFilterLabel() {
		return Text.translatable("gui.aurelia.dashboard.road.filter", Text.translatable(vehicleFilter.translationKey()));
	}

	private Text stationFilterLabel() {
		return Text.translatable(interchangesOnly ? "gui.aurelia.dashboard.road.stops.interchanges" : "gui.aurelia.dashboard.road.stops.all");
	}

	private void cycleVehicleFilter() {
		final Set<String> kinds = new LinkedHashSet<>();
		for (final RoadVehicleKind kind : RoadVehicleKind.values()) {
			kinds.add(kind.id);
		}
		for (final RoadVehicleRow row : allVehicles) {
			kinds.add(row.kindId());
		}
		final List<Filter> available = RoadVehicleListModel.availableFilters(kinds);
		final int idx = available.indexOf(vehicleFilter);
		vehicleFilter = available.get((idx + 1) % available.size());
	}

	@Override
	public void tick() {
		if (searchField != null) {
			searchField.tick();
		}
		if (subTab == SubTab.SETTINGS) {
			if (enabledButton != null) {
				enabledButton.setMessage(vehiclesEnabledLabel());
			}
			if (bikeShareButton != null) {
				bikeShareButton.setMessage(bikeShareLabel());
			}
		}
		if (++ticks % REFRESH_TICKS == 0) {
			refreshData();
		}
	}

	private void refreshData() {
		collectVehicles();
		if (subTab == SubTab.VEHICLES) {
			applyVehicleFilter();
		} else if (subTab == SubTab.STOPS) {
			collectStations();
			applyStationFilter();
		}
	}

	private void collectVehicles() {
		if (client == null || client.world == null) {
			allVehicles = List.of();
			return;
		}
		final double px = client.player == null ? camera.centerX() : client.player.getX();
		final double py = client.player == null ? 64 : client.player.getY();
		final double pz = client.player == null ? camera.centerZ() : client.player.getZ();
		final RoadVehicleSettings settings = RoadVehicles.settings(true);

		final List<RoadVehicleRow> rows = new ArrayList<>();
		for (final Entity entity : client.world.getEntities()) {
			if (!(entity instanceof RoadVehicleEntity road) || road.isRemoved()) {
				continue;
			}
			final RoadVehicleKind kind = road.kind();
			final PlayerEntity driver = road.driver();
			final String driverName = driver != null
					? driver.getEntityName()
					: (road.getFirstPassenger() != null ? road.getFirstPassenger().getName().getString() : "");
			final boolean localRiding = client.player != null && road.hasPassenger(client.player);
			final double dx = road.getX() - px;
			final double dy = road.getY() - py;
			final double dz = road.getZ() - pz;
			rows.add(new RoadVehicleRow(
					road.getId(),
					kind.id,
					road.getName().getString(),
					RoadVehicleListModel.speedKmhFromMetresPerTick(road.speed()),
					driverName,
					road.getPassengerList().size(),
					kind.seats.length,
					localRiding,
					road.hired(),
					settings.colour(kind),
					road.getYaw(),
					road.getX(),
					road.getY(),
					road.getZ(),
					Math.sqrt(dx * dx + dy * dy + dz * dz)
			));
		}
		allVehicles = rows;
	}

	private void collectStations() {
		final var data = MtrBridge.dashboardData();
		final double refX = client != null && client.player != null ? client.player.getX() : camera.centerX();
		final double refZ = client != null && client.player != null ? client.player.getZ() : camera.centerZ();
		allStations = RoadVehicleListModel.buildStationRows(data.stations, data.routes, refX, refZ, MtrBridge::displayName);
	}

	private void applyVehicleFilter() {
		filteredVehicles = RoadVehicleListModel.filterAndSortVehicles(allVehicles, vehicleFilter, searchQuery);
		if (vehicleList == null) {
			return;
		}
		boolean keepSelection = false;
		final List<SimpleListWidget.Item<Integer>> items = new ArrayList<>(filteredVehicles.size());
		for (final RoadVehicleRow row : filteredVehicles) {
			final int color = row.localRiding() ? COLOR_OK : (row.passengers() > 0 ? AureliaPalette.FG : AureliaPalette.FG_LIGHT);
			items.add(new SimpleListWidget.Item<>(Text.literal(row.listLabel()), color, row.entityId()));
			keepSelection |= selectedVehicleId != null && selectedVehicleId == row.entityId();
		}
		if (!keepSelection) {
			selectedVehicleId = filteredVehicles.isEmpty() ? null : filteredVehicles.get(0).entityId();
		}
		vehicleList.setItems(items, selectedVehicleId);
		updateActionButtons();
	}

	private void applyStationFilter() {
		filteredStations = RoadVehicleListModel.filterAndSortStations(allStations, interchangesOnly, searchQuery);
		if (stationList == null) {
			return;
		}
		boolean keepSelection = false;
		final List<SimpleListWidget.Item<Long>> items = new ArrayList<>(filteredStations.size());
		for (final StationInterchangeRow row : filteredStations) {
			final String prefix = row.isInterchange() ? "⇄ " : "■ ";
			final String name = row.name().isEmpty() ? Text.translatable("gui.mtr.untitled").getString() : row.name();
			final String label = String.format(Locale.ROOT, "%s%s (%dm)", prefix, name, Math.round(row.distance()));
			items.add(new SimpleListWidget.Item<>(Text.literal(label), row.isInterchange() ? COLOR_ACCENT_TEXT : AureliaPalette.FG, row.stationId()));
			keepSelection |= selectedStationId != null && selectedStationId == row.stationId();
		}
		if (!keepSelection) {
			selectedStationId = filteredStations.isEmpty() ? null : filteredStations.get(0).stationId();
		}
		stationList.setItems(items, selectedStationId);
		updateActionButtons();
	}

	@Nullable
	private RoadVehicleRow selectedVehicle() {
		if (selectedVehicleId == null) {
			return null;
		}
		for (final RoadVehicleRow row : allVehicles) {
			if (row.entityId() == selectedVehicleId) {
				return row;
			}
		}
		return null;
	}

	@Nullable
	private StationInterchangeRow selectedStation() {
		if (selectedStationId == null) {
			return null;
		}
		for (final StationInterchangeRow row : allStations) {
			if (row.stationId() == selectedStationId) {
				return row;
			}
		}
		return null;
	}

	@Nullable
	private static Station findStationById(long stationId) {
		final var data = MtrBridge.dashboardData();
		final Station direct = data.stationIdMap.get(stationId);
		if (direct != null) {
			return direct;
		}
		for (final Station station : data.stations) {
			if (station.getId() == stationId) {
				return station;
			}
		}
		return null;
	}

	private boolean canTeleport() {
		return client != null && client.player != null && client.player.hasPermissionLevel(2);
	}

	/** Uses {@code /give @s}, which requires operator permission level 2 (matching {@link AtrMenuScreen}). */
	private boolean canGiveItems() {
		return client != null && client.player != null && client.player.hasPermissionLevel(2);
	}

	private void updateActionButtons() {
		if (subTab == SubTab.VEHICLES) {
			final RoadVehicleRow selected = selectedVehicle();
			if (focusButton != null) {
				focusButton.active = selected != null;
			}
			if (teleportButton != null) {
				teleportButton.active = selected != null && !selected.localRiding() && canTeleport();
			}
		} else if (subTab == SubTab.STOPS) {
			final StationInterchangeRow selected = selectedStation();
			if (focusButton != null) {
				focusButton.active = selected != null;
			}
			if (teleportButton != null) {
				teleportButton.active = selected != null && canTeleport();
			}
		}
	}

	private void focusSelectedVehicle() {
		final RoadVehicleRow selected = selectedVehicle();
		if (selected != null) {
			focusWorldPoint(selected.x(), selected.z());
		}
	}

	private void focusSelectedStation() {
		final StationInterchangeRow selected = selectedStation();
		if (selected != null) {
			focusWorldPoint(selected.x(), selected.z());
		}
	}

	private void focusWorldPoint(double worldX, double worldZ) {
		camera.center(worldX, worldZ);
		camera.zoomAt(MapCamera.MAX_SCALE / Math.max(0.25, camera.scale()), PANEL_WIDTH + (width - PANEL_WIDTH) / 2.0, height / 2.0, PANEL_WIDTH, 0, width - PANEL_WIDTH, height);
		camera.center(worldX, worldZ);
		preservedMtrScale = Math.max(8.0, camera.scale());
	}

	private void teleportToSelectedVehicle() {
		final RoadVehicleRow selected = selectedVehicle();
		if (selected == null || !canTeleport() || client == null || client.player == null) {
			return;
		}
		client.player.networkHandler.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f", selected.x(), selected.y() + 1.0, selected.z()));
		client.setScreen(null);
	}

	private void teleportToSelectedStation() {
		final StationInterchangeRow selected = selectedStation();
		if (selected == null || !canTeleport() || client == null || client.player == null) {
			return;
		}
		final Station station = findStationById(selected.stationId());
		final String command = RoadVehicleListModel.stationTeleportCommand(station, selected, client.player.getY());
		client.player.networkHandler.sendCommand(command);
		client.setScreen(null);
	}

	private void zoomBy(double factor) {
		final int mapW = Math.max(1, width - PANEL_WIDTH);
		camera.zoomAt(factor, PANEL_WIDTH + mapW / 2.0, height / 2.0, PANEL_WIDTH, 0, mapW, height);
		preservedMtrScale = camera.scale();
	}

	private void centreOnPlayer() {
		if (client != null && client.player != null) {
			camera.center(client.player.getX(), client.player.getZ());
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		final int mapW = Math.max(1, width - PANEL_WIDTH);
		MapView.draw(context, PANEL_WIDTH, 0, mapW, height, camera, true);
		drawMapOverlays(context, PANEL_WIDTH, 0, mapW, height);

		// Left panel background matching DashboardScreen + AureliaDashboardMixin
		final int panelBg = AureliaUiSettings.enabled(AureliaUiSettings.ScreenId.DASHBOARD) ? AureliaPalette.BG : IGui.ARGB_BACKGROUND;
		context.fill(0, 0, PANEL_WIDTH, height, panelBg);
		context.fill(PANEL_WIDTH - 1, 0, PANEL_WIDTH, height, PANEL_DIVIDER);

		renderSelectedInfoCard(context);
		renderMouseWorldCoords(context, mouseX, mouseY, mapW);
		if (subTab == SubTab.SETTINGS) {
			renderSettingsStatus(context);
		}

		super.render(context, mouseX, mouseY, delta);

		// Draw empty-state labels after SimpleListWidget so its 0x80000000 backdrop does not dim the text.
		if (subTab == SubTab.VEHICLES && filteredVehicles.isEmpty()) {
			context.drawCenteredTextWithShadow(
					textRenderer,
					textRenderer.trimToWidth(Text.translatable("gui.aurelia.dashboard.road.none"), PANEL_WIDTH - 12).getString(),
					PANEL_WIDTH / 2,
					76 + Math.max(12, (height - 110) / 2),
					0x888888
			);
		} else if (subTab == SubTab.STOPS && filteredStations.isEmpty()) {
			context.drawCenteredTextWithShadow(
					textRenderer,
					textRenderer.trimToWidth(Text.translatable("gui.aurelia.dashboard.road.no_stops"), PANEL_WIDTH - 12).getString(),
					PANEL_WIDTH / 2,
					76 + Math.max(12, (height - 110) / 2),
					0x888888
			);
		}
	}

	private void drawMapOverlays(DrawContext context, int mapLeft, int mapTop, int mapWidth, int mapHeight) {
		context.enableScissor(mapLeft, mapTop, mapLeft + mapWidth, mapTop + mapHeight);

		// Highlight dashboard stations & selected station
		for (final Station station : MtrBridge.dashboardData().stations) {
			if (!MtrBridge.validCorners(station)) {
				continue;
			}
			final int x1 = (int) Math.floor(camera.screenX(station.getMinX(), mapLeft, mapWidth));
			final int y1 = (int) Math.floor(camera.screenY(station.getMinZ(), mapTop, mapHeight));
			final int x2 = (int) Math.ceil(camera.screenX(station.getMaxX(), mapLeft, mapWidth));
			final int y2 = (int) Math.ceil(camera.screenY(station.getMaxZ(), mapTop, mapHeight));
			if (x2 < mapLeft || y2 < mapTop || x1 > mapLeft + mapWidth || y1 > mapTop + mapHeight) {
				continue;
			}
			final boolean isSelected = subTab == SubTab.STOPS && selectedStationId != null && selectedStationId == station.getId();
			context.fill(x1, y1, Math.max(x1 + 2, x2), Math.max(y1 + 2, y2), (isSelected ? 0x58000000 : 0x28000000) | (station.getColor() & 0xFFFFFF));
			if (isSelected) {
				AureliaChrome.frame(context, x1 - 1, y1 - 1, Math.max(4, x2 - x1 + 2), Math.max(4, y2 - y1 + 2), 0xFF4A90E2);
			}
		}

		// Live road vehicle markers on map (matches active filter on Vehicles sub-tab)
		final List<RoadVehicleRow> visibleMarkers = subTab == SubTab.VEHICLES ? filteredVehicles : allVehicles;
		for (final RoadVehicleRow row : visibleMarkers) {
			final int sx = (int) Math.round(camera.screenX(row.x(), mapLeft, mapWidth));
			final int sy = (int) Math.round(camera.screenY(row.z(), mapTop, mapHeight));
			if (sx < mapLeft - 8 || sy < mapTop - 8 || sx > mapLeft + mapWidth + 8 || sy > mapTop + mapHeight + 8) {
				continue;
			}
			final boolean isSelected = subTab == SubTab.VEHICLES && selectedVehicleId != null && selectedVehicleId == row.entityId();
			if (isSelected) {
				AureliaChrome.frame(context, sx - 6, sy - 6, 13, 13, 0xFF4A90E2);
			}
			context.fill(sx - 4, sy - 4, sx + 5, sy + 5, 0xFF000000);
			context.fill(sx - 3, sy - 3, sx + 4, sy + 4, 0xFF000000 | (row.colour() & 0xFFFFFF));
			// Heading dot
			final double rad = Math.toRadians(row.yaw());
			final int hx = sx + (int) Math.round(-Math.sin(rad) * 4);
			final int hy = sy + (int) Math.round(Math.cos(rad) * 4);
			context.fill(hx - 1, hy - 1, hx + 2, hy + 2, 0xFFFFFFFF);
		}

		context.disableScissor();
	}

	private void renderSelectedInfoCard(DrawContext context) {
		final int cardX = PANEL_WIDTH + 6;
		final int maxCardW = Math.min(230, width - PANEL_WIDTH - 72);
		if (maxCardW < 90) {
			return;
		}
		final int cardH = 40;
		final int cardY = height - cardH - 4;

		if (subTab == SubTab.VEHICLES) {
			final RoadVehicleRow selected = selectedVehicle();
			if (selected == null) {
				return;
			}
			context.fill(cardX, cardY, cardX + maxCardW, cardY + cardH, CARD_BG);
			AureliaChrome.frame(context, cardX, cardY, maxCardW, cardH, AureliaPalette.BORDER);
			context.fill(cardX + 4, cardY + 4, cardX + 12, cardY + 12, 0xFF000000 | (selected.colour() & 0xFFFFFF));
			AureliaChrome.frame(context, cardX + 4, cardY + 4, 8, 8, 0xFFFFFFFF);

			final String titleLine = selected.kindLabel() + (selected.hired() ? " (" + Text.translatable("gui.aurelia.dashboard.road.hired").getString() + ")" : "");
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(titleLine, maxCardW - 18), cardX + 15, cardY + 4, AureliaPalette.FG);

			final String occ = selected.driverName().isEmpty()
					? Text.translatable("gui.aurelia.dashboard.road.parked", selected.passengers(), Math.max(1, selected.capacity())).getString()
					: Text.translatable("gui.aurelia.dashboard.road.driven", selected.driverName(), selected.passengers(), Math.max(1, selected.capacity())).getString();
			final String line2 = selected.roundedSpeedKmh() + " km/h · " + occ;
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(line2, maxCardW - 8), cardX + 4, cardY + 16,
					selected.roundedSpeedKmh() > 0 ? COLOR_OK : AureliaPalette.FG_LIGHT);
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(selected.coordinatesLabel(), maxCardW - 8), cardX + 4, cardY + 27, 0x9AA0A6);
		} else if (subTab == SubTab.STOPS) {
			final StationInterchangeRow selected = selectedStation();
			if (selected == null) {
				return;
			}
			context.fill(cardX, cardY, cardX + maxCardW, cardY + cardH, CARD_BG);
			AureliaChrome.frame(context, cardX, cardY, maxCardW, cardH, AureliaPalette.BORDER);
			context.fill(cardX + 4, cardY + 4, cardX + 12, cardY + 12, 0xFF000000 | (selected.color() & 0xFFFFFF));

			final String name = selected.name().isEmpty() ? Text.translatable("gui.mtr.untitled").getString() : selected.name();
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, maxCardW - 18), cardX + 15, cardY + 4, AureliaPalette.FG);

			final Text summary = Text.translatable("gui.aurelia.dashboard.road.stop_summary", selected.routes(), selected.platforms(), selected.zone());
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(summary, maxCardW - 8).getString(), cardX + 4, cardY + 16,
					selected.isInterchange() ? COLOR_ACCENT_TEXT : AureliaPalette.FG_LIGHT);
			final String coords = String.format(Locale.ROOT, "%d, %d (%d m)", selected.blockX(), selected.blockZ(), Math.round(selected.distance()));
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(coords, maxCardW - 8), cardX + 4, cardY + 27, 0x9AA0A6);
		}
	}

	private void renderMouseWorldCoords(DrawContext context, int mouseX, int mouseY, int mapWidth) {
		if (mouseX < PANEL_WIDTH || layout == null) {
			return;
		}
		final double wx = camera.worldX(mouseX, PANEL_WIDTH, mapWidth);
		final double wz = camera.worldZ(mouseY, 0, height);
		final String posText = String.format(Locale.ROOT, "(%.1f, %.1f)", wx, wz);
		context.drawTextWithShadow(textRenderer, posText, width - 6 - textRenderer.getWidth(posText), layout.mouseCoordsY(), AureliaPalette.FG);
	}

	private void renderSettingsStatus(DrawContext context) {
		final RoadVehicleSettings settings = RoadVehicles.settings(true);
		final int y = 162;
		if (y + 24 > height) {
			return;
		}
		// Car and bike paint swatches
		context.fill(6, y, 18, y + 10, 0xFFFFFFFF);
		context.fill(7, y + 1, 17, y + 9, 0xFF000000 | settings.colour());
		context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "#%06X", settings.colour()), 21, y + 1, AureliaPalette.FG_LIGHT);

		context.fill(76, y, 88, y + 10, 0xFFFFFFFF);
		context.fill(77, y + 1, 87, y + 9, 0xFF000000 | settings.bikeColour());
		context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "#%06X", settings.bikeColour()), 91, y + 1, AureliaPalette.FG_LIGHT);

		if (!RoadVehicleClient.mayEdit(client) && y + 22 < height) {
			context.drawCenteredTextWithShadow(textRenderer,
					textRenderer.trimToWidth(Text.translatable("gui.aurelia.vehicles.operators"), PANEL_WIDTH - 8).getString(),
					PANEL_WIDTH / 2, y + 14, COLOR_WARN);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (mouseX >= PANEL_WIDTH) {
			if (searchField != null) {
				searchField.setFocused(false);
			}
			setFocused(null);
			if (button == 0) {
				if (trySelectMapMarker(mouseX, mouseY)) {
					return true;
				}
				draggingMap = true;
				return true;
			}
		}
		return false;
	}

	private boolean trySelectMapMarker(double mouseX, double mouseY) {
		final int mapW = Math.max(1, width - PANEL_WIDTH);
		final List<RoadVehicleRow> candidates = subTab == SubTab.VEHICLES ? filteredVehicles : allVehicles;
		for (final RoadVehicleRow row : candidates) {
			final double sx = camera.screenX(row.x(), PANEL_WIDTH, mapW);
			final double sy = camera.screenY(row.z(), 0, height);
			if (Math.abs(mouseX - sx) <= 6 && Math.abs(mouseY - sy) <= 6) {
				selectedVehicleId = row.entityId();
				if (subTab != SubTab.VEHICLES) {
					if (!RoadVehicleListModel.matchesVehicleFilterAndQuery(row, vehicleFilter, searchQuery)) {
						vehicleFilter = Filter.ALL;
						searchQuery = "";
					}
					selectSubTab(SubTab.VEHICLES);
				} else {
					applyVehicleFilter();
				}
				if (vehicleList != null) {
					vehicleList.scrollToSelected();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingMap = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingMap) {
			camera.drag(deltaX, deltaY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX >= PANEL_WIDTH) {
			final int mapW = Math.max(1, width - PANEL_WIDTH);
			camera.zoomAt(amount > 0 ? 1.25 : 0.8, mouseX, mouseY, PANEL_WIDTH, 0, mapW, height);
			preservedMtrScale = camera.scale();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if ((searchField == null || !searchField.isFocused()) && keyCode == GLFW.GLFW_KEY_C) {
			centreOnPlayer();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/**
	 * Previews slider changes locally during drag and sends one C2S update packet on mouse release or arrow-key step.
	 */
	private static final class CompactSlider extends SliderWidget {

		private final double min;
		private final double max;
		private final double step;
		private final DoubleConsumer preview;
		private final DoubleConsumer commit;
		private final Function<Double, Text> label;
		private double lastCommitted;

		private CompactSlider(
				int x,
				int y,
				int width,
				double min,
				double max,
				double step,
				DoubleSupplier get,
				DoubleConsumer preview,
				DoubleConsumer commit,
				Function<Double, Text> label
		) {
			super(x, y, width, 20, Text.empty(), (get.getAsDouble() - min) / (max - min));
			this.min = min;
			this.max = max;
			this.step = step;
			this.preview = preview;
			this.commit = commit;
			this.label = label;
			this.lastCommitted = current();
			updateMessage();
		}

		private double current() {
			return min + Math.round(value * (max - min) / step) * step;
		}

		private void commitIfChanged() {
			final double now = current();
			if (Double.compare(now, lastCommitted) != 0) {
				lastCommitted = now;
				commit.accept(now);
			}
		}

		@Override
		protected void updateMessage() {
			setMessage(label.apply(current()));
		}

		@Override
		protected void applyValue() {
			preview.accept(current());
		}

		@Override
		public void onRelease(double mouseX, double mouseY) {
			super.onRelease(mouseX, mouseY);
			commitIfChanged();
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			final boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
			if (handled && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
				commitIfChanged();
			}
			return handled;
		}
	}
}
