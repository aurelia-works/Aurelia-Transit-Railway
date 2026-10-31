package org.mtr.aurelia.client.screen;

import org.mtr.aurelia.client.MtrBridge;
import org.mtr.aurelia.depot.DepotHealth;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.mtr.core.data.Depot;
import org.mtr.mod.data.DepotOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every depot's last path generation result plus setup mistakes, worst first. MTR's own depot screen shows this for one
 * depot at a time; here it is the whole network. Reads data only when opened, on Refresh and after Regenerate.
 */
public final class DepotHealthScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int BUTTON_WIDTH = 110;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_WARN = 0xFFAA00;
	private static final int COLOR_ERROR = 0xFF5555;

	private final Screen parent;
	private SimpleListWidget<DepotHealth.DepotInfo> depotList;
	private ButtonWidget regenerateOneButton;
	private ButtonWidget regenerateAllButton;
	private ButtonWidget deployButton;
	private ButtonWidget clearButton;
	private ButtonWidget allDoorsButton;
	private ButtonWidget announcementsButton;

	private final Map<Long, List<DepotHealth.Problem>> problems = new LinkedHashMap<>();
	private DepotHealth.DepotInfo selected;
	private int errorDepots;
	private Text status = Text.empty();
	private int statusColor = COLOR_DIM;

	public DepotHealthScreen(Screen parent) {
		super(Text.translatable("gui.aurelia.depots.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		final int listBottom = height - 58;
		depotList = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP, half, listBottom - TOP, depot -> {
			selected = depot;
			updateButtons();
		}));
		final int quarter = (half - 4) / 2;
		deployButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.depots.instant_deploy"), button -> withSelectedDepot(MtrBridge::instantDeploy, "gui.aurelia.depots.deployed")).dimensions(rightX, listBottom - 92, quarter, 20).build());
		clearButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.depots.clear_vehicles"), button -> withSelectedDepot(MtrBridge::clearVehicles, "gui.aurelia.depots.cleared")).dimensions(rightX + quarter + 4, listBottom - 92, quarter, 20).build());
		allDoorsButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> toggle(DepotOptions.Option.ALL_DOORS)).dimensions(rightX, listBottom - 68, quarter, 20).build());
		announcementsButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> toggle(DepotOptions.Option.NO_ANNOUNCEMENTS)).dimensions(rightX + quarter + 4, listBottom - 68, quarter, 20).build());
		regenerateOneButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.depots.regenerate_one"), button -> regenerate(false)).dimensions(rightX, listBottom - 44, half, 20).build());
		regenerateAllButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.depots.regenerate_all"), button -> regenerate(true)).dimensions(rightX, listBottom - 20, half - BUTTON_WIDTH / 2 - 4, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.depots.refresh"), button -> refresh()).dimensions(rightX + half - BUTTON_WIDTH / 2, listBottom - 20, BUTTON_WIDTH / 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());
		refresh();
	}

	private void refresh() {
		final long keep = selected == null ? 0 : selected.id();
		final List<DepotHealth.DepotInfo> depots = new ArrayList<>();
		final java.util.Map<Long, List<String>> suspendedByDepot = new java.util.HashMap<>();
		MtrBridge.dashboardData().depots.forEach(depot -> {
			depots.add(MtrBridge.depotInfo(depot));
			final List<String> hit = new ArrayList<>();
			for (final String suspended : com.aureliatransit.architecture.client.wayfinding.logic.ClientServiceMessages.suspendedRoutes()) {
				depot.routes.forEach(route -> {
					if (com.aureliatransit.architecture.wayfinding.RouteHold.affects(java.util.List.of(new com.aureliatransit.architecture.wayfinding.RouteHold(1, suspended, Long.MAX_VALUE)), route.getName(), 0) && !hit.contains(suspended)) {
						hit.add(suspended);
					}
				});
			}
			suspendedByDepot.put(depot.getId(), hit);
		});
		depots.sort(DepotHealth.worstFirst());
		problems.clear();
		errorDepots = 0;
		final List<SimpleListWidget.Item<DepotHealth.DepotInfo>> items = new ArrayList<>();
		DepotHealth.DepotInfo reselect = null;
		for (final DepotHealth.DepotInfo depot : depots) {
			final List<DepotHealth.Problem> depotProblems = DepotHealth.check(depot, suspendedByDepot.getOrDefault(depot.id(), List.of()));
			problems.put(depot.id(), depotProblems);
			final int rank = DepotHealth.rank(depotProblems);
			if (rank == 0) {
				errorDepots++;
			}
			final String mark = rank == 0 ? "✖ " : rank == 1 ? "! " : "✔ ";
			items.add(new SimpleListWidget.Item<>(Text.literal(mark + depot.name()), rank == 0 ? COLOR_ERROR : rank == 1 ? COLOR_WARN : COLOR_OK, depot));
			if (depot.id() == keep) {
				reselect = depot;
			}
		}
		selected = reselect;
		depotList.setItems(items, reselect);
		updateButtons();
	}

	private void updateButtons() {
		regenerateOneButton.active = MtrBridge.canEdit() && selected != null;
		regenerateAllButton.active = MtrBridge.canEdit() && errorDepots > 0;
		final boolean depotActions = MtrBridge.canEdit() && selected != null;
		deployButton.active = depotActions;
		clearButton.active = depotActions;
		allDoorsButton.active = depotActions;
		announcementsButton.active = depotActions;
		final DepotOptions.Flags flags = selected == null ? DepotOptions.Flags.NONE : DepotOptions.client(selected.id());
		allDoorsButton.setMessage(Text.translatable(flags.allDoors() ? "gui.aurelia.depots.all_doors_on" : "gui.aurelia.depots.all_doors_off"));
		announcementsButton.setMessage(Text.translatable(flags.noAnnouncements() ? "gui.aurelia.depots.announcements_off" : "gui.aurelia.depots.announcements_on"));
	}

	@Override
	public void tick() {
		// The switches show what the server last sent, so a change shows once the server has saved it
		updateButtons();
	}

	private void withSelectedDepot(java.util.function.Consumer<Depot> action, String doneKey) {
		final Depot depot = selected == null || !MtrBridge.canEdit() ? null : MtrBridge.dashboardData().depotIdMap.get(selected.id());
		if (depot != null) {
			action.accept(depot);
			status = Text.translatable(doneKey, selected.name());
			statusColor = COLOR_OK;
		}
	}

	private void toggle(DepotOptions.Option option) {
		if (selected != null && MtrBridge.canEdit()) {
			MtrBridge.setDepotOption(selected.id(), option, !DepotOptions.client(selected.id()).get(option));
		}
	}

	/** "Path refreshed 5 min ago (31/10/2026 14:02)", as MTR's depot screen shows it, or nothing before the first generation. */
	private static Text pathRefreshed(long lastGeneratedMillis) {
		if (lastGeneratedMillis <= 0) {
			return Text.translatable("gui.aurelia.depots.path_never");
		}
		final long minutes = Math.max(0, (System.currentTimeMillis() - lastGeneratedMillis) / 60000);
		final String ago = minutes < 60 ? minutes + " min" : minutes < 60 * 48 ? minutes / 60 + " h" : minutes / 60 / 24 + " d";
		return Text.translatable("gui.aurelia.depots.path_refreshed", ago, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(new java.util.Date(lastGeneratedMillis)));
	}

	/** Same request as the Generate button in MTR's depot screen; only on click. */
	private void regenerate(boolean allWithErrors) {
		if (!MtrBridge.canEdit()) {
			return;
		}
		final List<Depot> depots = new ArrayList<>();
		if (allWithErrors) {
			MtrBridge.dashboardData().depots.forEach(depot -> {
				if (DepotHealth.rank(problems.getOrDefault(depot.getId(), List.of())) == 0) {
					depots.add(depot);
				}
			});
		} else if (selected != null) {
			final Depot depot = MtrBridge.dashboardData().depotIdMap.get(selected.id());
			if (depot != null) {
				depots.add(depot);
			}
		}
		final int count = MtrBridge.regenerateDepots(depots);
		status = Text.translatable("gui.aurelia.depots.regenerating", count);
		statusColor = COLOR_OK;
	}

	private static Text describe(DepotHealth.Problem problem) {
		final String key = "gui.aurelia.depots.problem." + problem.key();
		if (problem.key().equals("path_between")) {
			return Text.translatable(key, MtrBridge.platformLabel((Long) problem.args().get(0)), MtrBridge.platformLabel((Long) problem.args().get(1)));
		}
		return Text.translatable(key, problem.args().toArray());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, COLOR_TEXT);
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		if (problems.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.depots.none"), MARGIN + half / 2, height / 2, COLOR_DIM);
		}
		if (selected == null) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia.depots.summary", problems.size(), errorDepots), rightX, TOP + 2, COLOR_DIM);
		} else {
			context.drawTextWithShadow(textRenderer, Text.literal(selected.name()), rightX, TOP + 2, COLOR_TEXT);
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia.depots.counts", selected.routeCount(), selected.sidingCount()), rightX, TOP + 14, COLOR_DIM);
			final Depot depot = MtrBridge.dashboardData().depotIdMap.get(selected.id());
			context.drawTextWithShadow(textRenderer, pathRefreshed(depot == null ? 0 : depot.getLastGeneratedMillis()), rightX, TOP + 26, COLOR_DIM);
			int y = TOP + 42;
			final List<DepotHealth.Problem> depotProblems = problems.getOrDefault(selected.id(), List.of());
			if (depotProblems.isEmpty()) {
				context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia.depots.healthy"), rightX, y, COLOR_OK);
			}
			for (final DepotHealth.Problem problem : depotProblems) {
				if (y > height - 160) {
					break;
				}
				final int color = problem.severity() == DepotHealth.Severity.ERROR ? COLOR_ERROR : COLOR_WARN;
				for (final var line : textRenderer.wrapLines(describe(problem), half)) {
					context.drawTextWithShadow(textRenderer, line, rightX, y, color);
					y += 10;
				}
				y += 3;
			}
		}
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 40, statusColor);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** For the dev self-test. */
	public Map<Long, List<DepotHealth.Problem>> problemsForTest() {
		return problems;
	}

	/** For the dev self-test: same as clicking the depot row. */
	public void selectDepotForTest(long depotId) {
		for (final SimpleListWidget.Row<DepotHealth.DepotInfo> row : depotList.children()) {
			if (row.value().id() == depotId) {
				depotList.setSelected(row);
				return;
			}
		}
	}
}
