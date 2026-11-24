package org.mtr.aurelia.client.screen;

import org.mtr.aurelia.client.ClientOperators;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.mtr.aurelia.client.EngineClient;
import org.mtr.aurelia.client.MtrBridge;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.core.data.Route;
import org.mtr.mod.InitClient;
import org.mtr.mod.engine.JunctionPriority;
import org.mtr.mod.packet.PacketFetchNetworkData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The signaller: which lines go first at junctions. High Speed lines always do; an operator clicks any other line to
 * let it go first too. Reached only from the Engine tab of the Aurelia panel.
 */
public final class SignallerScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int COLOR_EXPRESS = AureliaPalette.ACCENT_LIGHT;
	private static final int COLOR_FIRST = AureliaPalette.SUCCESS;
	private static final int COLOR_NORMAL = AureliaPalette.TEXT;

	private final Screen parent;
	private TextFieldWidget search;
	private SimpleListWidget<Route> lines;
	/** The list last drawn. The server's answer replaces the set, so a new set means redraw (on a tick, not inside a click). */
	private java.util.Set<Long> shown;
	/** How many routes the list was drawn from; the network can arrive after the screen opens. */
	private int shownRoutes;
	/** Ticks left to wait for the network before saying the world has no lines. */
	private int loadingTicks;
	private boolean noMatch;

	public SignallerScreen(Screen parent) {
		super(Text.translatable("gui.aurelia.signaller.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int listWidth = Math.min(320, width - MARGIN * 2);
		final int left = (width - listWidth) / 2;
		search = addDrawableChild(new TextFieldWidget(textRenderer, left, TOP, listWidth, 16, Text.translatable("gui.aurelia.stops.search")));
		search.setPlaceholder(Text.translatable("gui.aurelia.stops.search"));
		search.setChangedListener(text -> refresh());
		lines = addDrawableChild(new SimpleListWidget<>(client, left, TOP + 20, listWidth, height - TOP - 20 - 58, this::toggle));
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());
		if (MtrBridge.dashboardData().routes.isEmpty()) {
			// the Dashboard has not loaded the network this session: load it without opening the Dashboard
			loadingTicks = 60;
			InitClient.REGISTRY_CLIENT.sendPacketToServer(PacketFetchNetworkData.create());
		}
		refresh();
	}

	private void refresh() {
		shown = JunctionPriority.signallerRoutes();
		final String query = search.getText().toLowerCase(Locale.ROOT).strip();
		final List<Route> routes = new ArrayList<>(MtrBridge.dashboardData().routes);
		shownRoutes = routes.size();
		routes.sort(Comparator.comparing(route -> MtrBridge.displayName(route.getName()).toLowerCase(Locale.ROOT)));
		final List<SimpleListWidget.Item<Route>> items = new ArrayList<>();
		for (final Route route : routes) {
			final String name = MtrBridge.displayName(route.getName());
			if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			final boolean express = JunctionPriority.isExpress(route);
			final boolean first = express || JunctionPriority.signallerRoutes().contains(route.getId());
			final Text state = Text.translatable(express ? "gui.aurelia.signaller.express" : first ? "gui.aurelia.signaller.first" : "gui.aurelia.signaller.normal");
			items.add(new SimpleListWidget.Item<>(Text.literal(name + "  ").append(state), express ? COLOR_EXPRESS : first ? COLOR_FIRST : COLOR_NORMAL, route));
		}
		lines.setItems(items, null);
		noMatch = items.isEmpty() && !routes.isEmpty();
	}

	private void toggle(Route route) {
		if (route == null || JunctionPriority.isExpress(route) || !ClientOperators.isOperator(client)) {
			return;
		}
		// the list redraws when the server sends the new set back
		EngineClient.requestFirst(route.getId(), !JunctionPriority.signallerRoutes().contains(route.getId()));
	}

	@Override
	public void tick() {
		if (loadingTicks > 0) {
			loadingTicks--;
		}
		if (shown != JunctionPriority.signallerRoutes() || shownRoutes != MtrBridge.dashboardData().routes.size()) {
			refresh();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, AureliaPalette.TEXT);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.signaller.hint"), width / 2, height - 46, AureliaPalette.TEXT_DIM);
		final String empty = shownRoutes == 0 ? (loadingTicks > 0 ? "gui.aurelia.signaller.loading" : "gui.aurelia.signaller.none") : noMatch ? "gui.aurelia.signaller.no_match" : null;
		if (empty != null) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable(empty), width / 2, TOP + 40, AureliaPalette.TEXT_DIM);
		}
	}

	/** In single player a pausing screen stops the train simulation, which would never answer the network request. */
	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
