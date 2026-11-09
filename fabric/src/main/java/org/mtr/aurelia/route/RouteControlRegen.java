package org.mtr.aurelia.route;

import org.mtr.core.Main;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Route;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.Init;
import org.mtr.mod.data.RouteControlOptions;

import java.lang.reflect.Field;

/**
 * Regenerates the depots a route-control edit belongs to, on the simulator thread and after the saved control map has
 * been published. This is the server side of the editor's save: without it the first regeneration after an edit could
 * still use the cached paths built from the previous control data.
 *
 * <p>Reaches {@code Init.main} and {@code Main.simulators} reflectively, the same way the coupling and cargo services
 * do; MTR exposes no public accessor for either.
 */
public final class RouteControlRegen {

	private static Field initMainField;
	private static Field simulatorsField;
	private static boolean loggedMissingAccess;

	private RouteControlRegen() {
	}

	/** Regenerates every depot that serves the route. */
	public static void route(long routeId) {
		withSimulators(simulator -> {
			final ObjectArrayList<Depot> depots = new ObjectArrayList<>();
			for (final Depot depot : simulator.depots) {
				if (depot.getRouteIds().contains(routeId)) {
					depots.add(depot);
				}
			}
			if (!depots.isEmpty()) {
				Depot.generateDepots(simulator, depots);
			}
		});
	}

	/** Regenerates depots whose routes use the free-platform fallback, after its master switch changed. */
	public static void fallbackMaster() {
		fallbackDepots();
	}

	/** Regenerates depots with any non-empty route control (safety net when the pre-load read failed). */
	public static boolean controlledDepots() {
		return withSimulators(simulator -> {
			final ObjectArrayList<Depot> depots = new ObjectArrayList<>();
			for (final Depot depot : simulator.depots) {
				for (final Route route : depot.routes) {
					if (!RouteControlOptions.server(route.getId()).isEmpty()) {
						depots.add(depot);
						break;
					}
				}
			}
			if (!depots.isEmpty()) {
				Depot.generateDepots(simulator, depots);
			}
		});
	}

	/** Regenerates depots with a route that uses the free-platform fallback (at generation time it is occupancy-based). */
	public static boolean fallbackDepots() {
		return withSimulators(simulator -> {
			final ObjectArrayList<Depot> depots = new ObjectArrayList<>();
			for (final Depot depot : simulator.depots) {
				for (final Route route : depot.routes) {
					if (RouteControlOptions.server(route.getId()).fallback()) {
						depots.add(depot);
						break;
					}
				}
			}
			if (!depots.isEmpty()) {
				Depot.generateDepots(simulator, depots);
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static boolean withSimulators(java.util.function.Consumer<Simulator> action) {
		try {
			if (initMainField == null) {
				initMainField = Init.class.getDeclaredField("main");
				initMainField.setAccessible(true);
			}
			if (simulatorsField == null) {
				simulatorsField = Main.class.getDeclaredField("simulators");
				simulatorsField.setAccessible(true);
			}

			final Main main = (Main) initMainField.get(null);
			final Iterable<Simulator> simulators = main == null ? null : (Iterable<Simulator>) simulatorsField.get(main);
			if (simulators == null) {
				return false;
			}
			for (final Simulator simulator : simulators) {
				simulator.run(() -> action.accept(simulator));
			}
			return true;
		} catch (Exception e) {
			if (!loggedMissingAccess) {
				loggedMissingAccess = true;
				Init.LOGGER.error("[ATR] Route control could not reach the simulator; regenerate the depot by hand", e);
			}
			// A missing field will not fix itself; stop the startup retry loop.
			return true;
		}
	}
}
