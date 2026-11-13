package org.mtr.aurelia.client.screen;

import org.mtr.aurelia.client.MtrBridge;
import org.mtr.aurelia.route.RouteControl;
import org.mtr.aurelia.route.RouteControlSettings;
import org.mtr.aurelia.route.RouteControlStore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Route control: per-stop platform choice, ordered waypoints, and the fallback switch. Every change is sent to the
 * server at once (like the depot switches); the cached path rebuilds on next read, never per frame.
 */
public final class RouteControlScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int BUTTON_WIDTH = 120;
	private static final int TOP = 32;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_ERROR = 0xFF5555;
	private static final int COLOR_CHOICE = 0xFFD070;
	private static final double RAIL_SNAP_DISTANCE = 8;

	private final Screen parent;
	private TextFieldWidget routeSearch;
	private SimpleListWidget<Route> routeList;
	private ButtonWidget fallbackButton;
	private SimpleListWidget<Integer> stopList;
	private ButtonWidget cycleButton;
	private TextFieldWidget waypointField;
	private SimpleListWidget<Integer> waypointList;
	private ButtonWidget addButton;
	private ButtonWidget railButton;
	private ButtonWidget upButton;
	private ButtonWidget downButton;
	private ButtonWidget removeButton;

	private Route selectedRoute;
	private RouteControl control = RouteControl.EMPTY;
	private Set<Long> occupied = Set.of();
	private Text status = Text.empty();
	private int statusColor = COLOR_DIM;

	public RouteControlScreen(Screen parent) {
		super(Text.translatable("gui.aurelia.control.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int leftWidth = Math.min(170, (width - MARGIN * 3) / 3);
		final int rightX = MARGIN * 2 + leftWidth;
		final int rightWidth = width - rightX - MARGIN;
		final int listBottom = height - 58;
		final int listWidth = rightWidth - BUTTON_WIDTH - 6;
		final int buttonsX = rightX + rightWidth - BUTTON_WIDTH;

		routeSearch = addDrawableChild(new TextFieldWidget(textRenderer, MARGIN, TOP, leftWidth, 16, Text.translatable("gui.aurelia.stops.search")));
		routeSearch.setPlaceholder(Text.translatable("gui.aurelia.stops.search"));
		routeSearch.setChangedListener(text -> refreshRoutes());
		routeList = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP + 20, leftWidth, listBottom - TOP - 20, this::selectRoute));

		final int stopsTop = TOP + 44;
		final int avail = listBottom - TOP;
		final int stopsHeight = Math.max(56, (avail - 100) * 45 / 100);
		final int wayFieldY = stopsTop + stopsHeight + 18;
		final int wayListY = wayFieldY + 20;

		fallbackButton = addDrawableChild(ButtonWidget.builder(fallbackLabel(), button -> toggleFallback()).dimensions(rightX, TOP, listWidth, 20).build());
		stopList = addDrawableChild(new SimpleListWidget<>(client, rightX, stopsTop, listWidth, stopsHeight, stop -> updateButtons()));
		cycleButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.control.cycle"), button -> cyclePlatform()).dimensions(buttonsX, stopsTop, BUTTON_WIDTH, 20).build());

		waypointField = addDrawableChild(new TextFieldWidget(textRenderer, rightX, wayFieldY, listWidth - 140, 16, Text.translatable("gui.aurelia.control.waypoint_hint")));
		waypointField.setPlaceholder(Text.translatable("gui.aurelia.control.waypoint_hint"));
		addButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.control.add"), button -> addWaypoint()).dimensions(rightX + listWidth - 136, wayFieldY - 2, 66, 20).build());
		railButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.control.at_rail"), button -> addWaypointAtRail()).dimensions(rightX + listWidth - 66, wayFieldY - 2, 66, 20).build());
		waypointList = addDrawableChild(new SimpleListWidget<>(client, rightX, wayListY, listWidth, Math.max(24, listBottom - wayListY), waypoint -> updateButtons()));
		upButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.stops.up"), button -> moveWaypoint(-1)).dimensions(buttonsX, wayListY, BUTTON_WIDTH, 20).build());
		downButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.stops.down"), button -> moveWaypoint(1)).dimensions(buttonsX, wayListY + 24, BUTTON_WIDTH, 20).build());
		removeButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.control.remove"), button -> removeWaypoint()).dimensions(buttonsX, wayListY + 48, BUTTON_WIDTH, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());

		final Route keep = selectedRoute;
		refreshRoutes();
		if (keep != null) {
			selectedRoute = null;
			selectRoute(keep);
		}
		updateButtons();
	}

	private Text fallbackLabel() {
		return Text.translatable("gui.aurelia.control.fallback", ScreenTexts.onOrOff(control.fallback()));
	}

	private void refreshRoutes() {
		final String query = routeSearch.getText().toLowerCase(Locale.ROOT).strip();
		final List<Route> routes = new ArrayList<>(MtrBridge.dashboardData().routes);
		routes.sort(Comparator.comparing(route -> MtrBridge.displayName(route.getName()).toLowerCase(Locale.ROOT)));
		final List<SimpleListWidget.Item<Route>> items = new ArrayList<>();
		for (final Route route : routes) {
			final String label = MtrBridge.displayName(route.getName()) + " (" + route.getRoutePlatforms().size() + ")";
			if (query.isEmpty() || label.toLowerCase(Locale.ROOT).contains(query) || route == selectedRoute) {
				items.add(new SimpleListWidget.Item<>(Text.literal(label), route.getHidden() ? COLOR_DIM : COLOR_TEXT, route));
			}
		}
		routeList.setItems(items, selectedRoute);
	}

	private void selectRoute(Route route) {
		selectedRoute = route;
		control = route == null ? RouteControl.EMPTY : MtrBridge.routeControl(route.getId());
		occupied = route == null ? Set.of() : MtrBridge.occupiedPlatforms();
		fallbackButton.setMessage(fallbackLabel());
		setStatus(Text.empty(), COLOR_DIM);
		refreshStops();
		refreshWaypoints();
		updateButtons();
	}

	private int stopCount() {
		return selectedRoute == null ? 0 : selectedRoute.getRoutePlatforms().size();
	}

	private void refreshStops() {
		final List<SimpleListWidget.Item<Integer>> items = new ArrayList<>();
		if (selectedRoute != null) {
			// The cached visits (choices applied) drive the labels: one computation per refresh, never per frame.
			RouteControlStore.visits(selectedRoute, control);
			for (int i = 0; i < stopCount(); i++) {
				final RoutePlatformData data = selectedRoute.getRoutePlatforms().get(i);
				final Platform platform = data == null ? null : data.platform;
				final long effective = control.platformForStop(i, platform == null ? 0 : platform.getId());
				final boolean chosen = platform != null && effective != platform.getId();
				items.add(new SimpleListWidget.Item<>(Text.literal((i + 1) + ". " + stopLabel(platform) + (chosen ? " → " + platformName(effective) : "")),
						platform == null ? COLOR_ERROR : chosen ? COLOR_CHOICE : COLOR_TEXT, i));
			}
		}
		stopList.setItems(items, stopList.selectedValue());
	}

	private String stopLabel(Platform platform) {
		if (platform == null) {
			return Text.translatable("gui.aurelia.stops.missing_platform").getString();
		}
		final String station = platform.area == null ? Text.translatable("gui.aurelia.stops.no_station").getString() : MtrBridge.displayName(platform.area.getName());
		return station + " — " + Text.translatable("gui.aurelia.stops.platform", platform.getName()).getString();
	}

	private String platformName(long platformId) {
		final Platform platform = MtrBridge.dashboardData().platformIdMap.get(platformId);
		return platform == null ? "#" + Long.toHexString(platformId) : Text.translatable("gui.aurelia.stops.platform", platform.getName()).getString();
	}

	private void refreshWaypoints() {
		final List<SimpleListWidget.Item<Integer>> items = new ArrayList<>();
		for (int i = 0; i < control.waypoints().size(); i++) {
			final RouteControl.Waypoint waypoint = control.waypoints().get(i);
			items.add(new SimpleListWidget.Item<>(Text.literal((i + 1) + ". (" + waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z() + ") " + afterLabel(waypoint.afterStop())), COLOR_TEXT, i));
		}
		waypointList.setItems(items, waypointList.selectedValue());
	}

	private String afterLabel(int afterStop) {
		if (selectedRoute == null || afterStop >= stopCount()) {
			return Text.translatable("gui.aurelia.control.after_end").getString();
		}
		final RoutePlatformData data = selectedRoute.getRoutePlatforms().get(afterStop);
		final Platform platform = data == null ? null : data.platform;
		final String station = platform == null || platform.area == null ? "?" : MtrBridge.displayName(platform.area.getName());
		return Text.translatable("gui.aurelia.control.after_stop", afterStop + 1, station).getString();
	}

	private void updateButtons() {
		final boolean canEdit = MtrBridge.canEdit();
		final boolean hasRoute = selectedRoute != null && stopCount() > 0;
		fallbackButton.active = canEdit && hasRoute;
		final Integer stop = stopList.selectedValue();
		final boolean stopOptions = hasRoute && stop != null && stop < stopCount() && stopOptions(stop).size() > 1;
		cycleButton.active = canEdit && stopOptions;
		cycleButton.setMessage(cycleLabel(stop));
		addButton.active = canEdit && hasRoute;
		railButton.active = canEdit && hasRoute;
		final Integer waypoint = waypointList.selectedValue();
		upButton.active = canEdit && waypoint != null && waypoint > 0;
		downButton.active = canEdit && waypoint != null && waypoint < control.waypoints().size() - 1;
		removeButton.active = canEdit && waypoint != null && waypoint < control.waypoints().size();
	}

	private List<Long> stopOptions(int stop) {
		if (selectedRoute == null || stop >= stopCount()) {
			return List.of();
		}
		final RoutePlatformData data = selectedRoute.getRoutePlatforms().get(stop);
		return MtrBridge.sameStationPlatformIds(data == null ? null : data.platform, selectedRoute.getTransportMode());
	}

	private Text cycleLabel(Integer stop) {
		if (selectedRoute == null || stop == null || stop >= stopCount()) {
			return Text.translatable("gui.aurelia.control.cycle");
		}
		final RoutePlatformData data = selectedRoute.getRoutePlatforms().get(stop);
		final long planned = data == null || data.platform == null ? 0 : data.platform.getId();
		final long effective = control.platformForStop(stop, planned);
		final List<Long> options = stopOptions(stop);
		final int index = options.indexOf(effective);
		return Text.translatable("gui.aurelia.control.cycle_platform", platformName(effective), options.size() < 2 ? 1 : index + 1, Math.max(1, options.size()));
	}

	private void toggleFallback() {
		if (!MtrBridge.canEdit() || selectedRoute == null) {
			return;
		}
		save(control.withFallback(!control.fallback()));
		setStatus(Text.translatable(control.fallback() ? "gui.aurelia.control.fallback_on" : "gui.aurelia.control.fallback_off"), COLOR_OK);
	}

	private void cyclePlatform() {
		if (!MtrBridge.canEdit() || selectedRoute == null) {
			return;
		}
		final Integer stop = stopList.selectedValue();
		if (stop == null || stop >= stopCount()) {
			return;
		}
		final List<Long> options = stopOptions(stop);
		if (options.size() < 2) {
			return;
		}
		final RoutePlatformData data = selectedRoute.getRoutePlatforms().get(stop);
		final long planned = data == null || data.platform == null ? 0 : data.platform.getId();
		final int next = (options.indexOf(control.platformForStop(stop, planned)) + 1) % options.size();
		// Cycling back to the planned platform removes the choice instead of storing a no-op.
		save(control.withPlatformChoice(stop, options.get(next) == planned ? 0 : options.get(next)));
	}

	private void addWaypoint() {
		if (!MtrBridge.canEdit() || selectedRoute == null) {
			return;
		}
		final String[] parts = waypointField.getText().strip().split("[,;\\s]+");
		if (parts.length != 3) {
			setStatus(Text.translatable("gui.aurelia.control.bad_coords"), COLOR_ERROR);
			return;
		}
		try {
			final long x = Long.parseLong(parts[0]);
			final long y = Long.parseLong(parts[1]);
			final long z = Long.parseLong(parts[2]);
			final Integer stop = stopList.selectedValue();
			save(control.withWaypoint(x, y, z, stop == null ? stopCount() - 1 : stop));
			waypointField.setText("");
			setStatus(Text.translatable("gui.aurelia.control.waypoint_added", x, y, z), COLOR_OK);
		} catch (NumberFormatException e) {
			setStatus(Text.translatable("gui.aurelia.control.bad_coords"), COLOR_ERROR);
		}
	}

	private void addWaypointAtRail() {
		if (!MtrBridge.canEdit() || selectedRoute == null || client.player == null) {
			return;
		}
		final long[] node = MtrBridge.nearestRailNode(client.player.getX(), client.player.getY(), client.player.getZ(), RAIL_SNAP_DISTANCE);
		if (node == null) {
			setStatus(Text.translatable("gui.aurelia.control.no_rail", (int) RAIL_SNAP_DISTANCE), COLOR_ERROR);
			return;
		}
		final Integer stop = stopList.selectedValue();
		save(control.withWaypoint(node[0], node[1], node[2], stop == null ? stopCount() - 1 : stop));
		setStatus(Text.translatable("gui.aurelia.control.waypoint_added", node[0], node[1], node[2]), COLOR_OK);
	}

	private void moveWaypoint(int delta) {
		if (!MtrBridge.canEdit()) {
			return;
		}
		final Integer waypoint = waypointList.selectedValue();
		if (waypoint == null) {
			return;
		}
		final RouteControl next = control.moveWaypoint(waypoint, waypoint + delta);
		if (!next.equals(control)) {
			save(next);
			waypointList.setItems(waypointListItems(), waypoint + delta);
			updateButtons();
		}
	}

	private List<SimpleListWidget.Item<Integer>> waypointListItems() {
		final List<SimpleListWidget.Item<Integer>> items = new ArrayList<>();
		for (int i = 0; i < control.waypoints().size(); i++) {
			final RouteControl.Waypoint waypoint = control.waypoints().get(i);
			items.add(new SimpleListWidget.Item<>(Text.literal((i + 1) + ". (" + waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z() + ") " + afterLabel(waypoint.afterStop())), COLOR_TEXT, i));
		}
		return items;
	}

	private void removeWaypoint() {
		if (!MtrBridge.canEdit()) {
			return;
		}
		final Integer waypoint = waypointList.selectedValue();
		if (waypoint == null) {
			return;
		}
		save(control.withoutWaypoint(waypoint));
		setStatus(Text.translatable("gui.aurelia.control.waypoint_removed"), COLOR_OK);
	}

	/** Stores the control server-side (dropping choices past the route's stops) and rebuilds the cached path. */
	private void save(RouteControl next) {
		if (selectedRoute == null) {
			return;
		}
		final int stops = stopCount();
		RouteControl kept = next;
		for (final int stop : next.platformChoices().keySet()) {
			if (stop >= stops) {
				kept = kept.withPlatformChoice(stop, 0);
			}
		}
		control = kept;
		MtrBridge.sendRouteControl(selectedRoute.getId(), control);
		RouteControlStore.invalidateRoute(selectedRoute.getId());
		fallbackButton.setMessage(fallbackLabel());
		refreshStops();
		refreshWaypoints();
		updateButtons();
	}

	private void setStatus(Text text, int color) {
		status = text;
		statusColor = color;
	}

	private int takenCount() {
		if (selectedRoute == null || !control.fallback()) {
			return 0;
		}
		int taken = 0;
		for (int i = 0; i < stopCount(); i++) {
			final RoutePlatformData data = selectedRoute.getRoutePlatforms().get(i);
			final long planned = data == null || data.platform == null ? 0 : data.platform.getId();
			if (planned != 0 && occupied.contains(control.platformForStop(i, planned))) {
				taken++;
			}
		}
		return taken;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, COLOR_TEXT);
		final int leftWidth = Math.min(170, (width - MARGIN * 3) / 3);
		final int rightX = MARGIN * 2 + leftWidth;
		if (selectedRoute == null) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia.control.pick_route"), rightX, TOP + 26, COLOR_DIM);
		} else {
			final Text taken = control.fallback()
					? Text.translatable("gui.aurelia.control.taken", takenCount(), stopCount())
					: RouteControlSettings.fallbackMaster()
							? Text.translatable("gui.aurelia.control.fallback_hint")
							: Text.translatable("gui.aurelia.control.master_off");
			context.drawTextWithShadow(textRenderer, taken, rightX, TOP + 24, COLOR_DIM);
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia.control.stops"), rightX, TOP + 34, COLOR_DIM);
		}
		if (MtrBridge.dashboardData().routes.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.stops.no_routes"), MARGIN + leftWidth / 2, height / 2, COLOR_DIM);
		}
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 40, statusColor);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
