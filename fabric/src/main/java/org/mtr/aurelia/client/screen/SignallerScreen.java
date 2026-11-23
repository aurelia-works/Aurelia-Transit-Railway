package org.mtr.aurelia.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.mtr.aurelia.client.EngineClient;
import org.mtr.aurelia.client.MtrBridge;
import org.mtr.core.data.Route;
import org.mtr.mod.engine.JunctionPriority;

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
	private static final int COLOR_EXPRESS = 0x7FD8FF;
	private static final int COLOR_FIRST = 0x55FF88;
	private static final int COLOR_NORMAL = 0xFFFFFF;

	private final Screen parent;
	private TextFieldWidget search;
	private SimpleListWidget<Route> lines;
	/** The list last drawn. The server's answer replaces the set, so a new set means redraw (on a tick, not inside a click). */
	private java.util.Set<Long> shown;

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
		refresh();
	}

	private void refresh() {
		shown = JunctionPriority.signallerRoutes();
		final String query = search.getText().toLowerCase(Locale.ROOT).strip();
		final List<Route> routes = new ArrayList<>(MtrBridge.dashboardData().routes);
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
	}

	private void toggle(Route route) {
		if (route == null || JunctionPriority.isExpress(route) || client == null || client.player == null || !client.player.hasPermissionLevel(2)) {
			return;
		}
		// the list redraws when the server sends the new set back
		EngineClient.requestFirst(route.getId(), !JunctionPriority.signallerRoutes().contains(route.getId()));
	}

	@Override
	public void tick() {
		if (shown != JunctionPriority.signallerRoutes()) {
			refresh();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.signaller.hint"), width / 2, height - 46, 0xA0A0A0);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
