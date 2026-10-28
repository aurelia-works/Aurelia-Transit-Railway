package org.mtr.mod.servlet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mtr.core.Main;
import org.mtr.core.servlet.HttpResponseStatus;
import org.mtr.core.servlet.ServletBase;
import org.mtr.core.servlet.Webserver;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.libraries.javax.servlet.AsyncContext;
import org.mtr.libraries.javax.servlet.http.HttpServlet;
import org.mtr.libraries.javax.servlet.http.HttpServletRequest;
import org.mtr.libraries.javax.servlet.http.HttpServletResponse;
import org.mtr.libraries.org.eclipse.jetty.servlet.ServletHolder;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * {@code /atr/api/map/network} (stations, routes and their shapes along the rails) and {@code /atr/api/map/vehicles} (live vehicle positions) for AureliaMap.
 * Responses are built on the simulation thread and cached (network 60 s, vehicles 1 s), so many viewers cost the server one build per period.
 * Pick a dimension with {@code ?dimension=<index>} like Core's {@code /mtr/api/map} endpoints.
 */
public final class MapLayerServlet extends HttpServlet {

	private static final Logger LOGGER = LogManager.getLogger("MinecraftTransitRailway");
	private static final long NETWORK_CACHE_MILLIS = 60_000;
	private static final long VEHICLES_CACHE_MILLIS = 1_000;

	private final Supplier<Main> mainSupplier;
	private final Map<String, Cached> networkCache = new ConcurrentHashMap<>();
	private final Map<String, Cached> vehiclesCache = new ConcurrentHashMap<>();
	private final Map<String, MapLayerJson.ShapeCache> shapeCaches = new ConcurrentHashMap<>();
	@Nullable
	private volatile ObjectImmutableList<Simulator> simulators;

	private MapLayerServlet(Supplier<Main> mainSupplier) {
		this.mainSupplier = mainSupplier;
	}

	@Override
	protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
		final AsyncContext asyncContext = httpServletRequest.startAsync();
		asyncContext.setTimeout(0);
		final String path = httpServletRequest.getPathInfo() == null ? "" : httpServletRequest.getPathInfo().replace("/", "");
		final ObjectImmutableList<Simulator> simulators = getSimulators();
		int dimension = 0;
		try {
			final String dimensionParameter = httpServletRequest.getParameter("dimension");
			dimension = dimensionParameter == null ? 0 : Integer.parseInt(dimensionParameter);
		} catch (NumberFormatException ignored) {
		}

		if (simulators == null) {
			send(httpServletResponse, asyncContext, HttpResponseStatus.INTERNAL_SERVER_ERROR, null);
		} else if (dimension < 0 || dimension >= simulators.size()) {
			send(httpServletResponse, asyncContext, HttpResponseStatus.BAD_REQUEST, null);
		} else {
			final Simulator simulator = simulators.get(dimension);
			switch (path) {
				case "network":
					simulator.run(() -> send(httpServletResponse, asyncContext, HttpResponseStatus.OK, networkCache.computeIfAbsent(simulator.dimension, key -> new Cached(NETWORK_CACHE_MILLIS)).get(() -> MapLayerJson.network(simulator, shapeCaches.computeIfAbsent(simulator.dimension, key -> new MapLayerJson.ShapeCache())))));
					break;
				case "vehicles":
					simulator.run(() -> send(httpServletResponse, asyncContext, HttpResponseStatus.OK, vehiclesCache.computeIfAbsent(simulator.dimension, key -> new Cached(VEHICLES_CACHE_MILLIS)).get(() -> MapLayerJson.vehicles(simulator))));
					break;
				default:
					send(httpServletResponse, asyncContext, HttpResponseStatus.NOT_FOUND, null);
			}
		}
	}

	/**
	 * Core keeps its simulators private and builds the webserver before {@link Main} is returned, so they are looked up on the first request.
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	private ObjectImmutableList<Simulator> getSimulators() {
		if (simulators == null) {
			final Main main = mainSupplier.get();
			if (main != null) {
				try {
					final Field field = Main.class.getDeclaredField("simulators");
					field.setAccessible(true);
					simulators = (ObjectImmutableList<Simulator>) field.get(main);
				} catch (Exception e) {
					LOGGER.error("Map layer cannot reach the simulators", e);
				}
			}
		}
		return simulators;
	}

	private static void send(HttpServletResponse httpServletResponse, AsyncContext asyncContext, HttpResponseStatus httpResponseStatus, @Nullable JsonObject data) {
		final JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("code", httpResponseStatus.code);
		jsonObject.addProperty("currentTime", System.currentTimeMillis());
		if (data != null) {
			jsonObject.add("data", data);
		}
		ServletBase.sendResponse(httpServletResponse, asyncContext, jsonObject.toString(), ServletBase.getMimeType("json"), httpResponseStatus);
	}

	public static void setup(Webserver webserver, Supplier<Main> mainSupplier) {
		webserver.addServlet(new ServletHolder(new MapLayerServlet(mainSupplier)), "/atr/api/map/*");
	}

	/**
	 * One cached response. Only touched on its simulator's thread.
	 */
	private static final class Cached {

		private final long lifetimeMillis;
		private long builtMillis;
		@Nullable
		private JsonObject jsonObject;

		private Cached(long lifetimeMillis) {
			this.lifetimeMillis = lifetimeMillis;
		}

		private JsonObject get(Supplier<JsonObject> builder) {
			final long now = System.currentTimeMillis();
			if (jsonObject == null || now - builtMillis >= lifetimeMillis) {
				jsonObject = builder.get();
				builtMillis = now;
			}
			return jsonObject;
		}
	}
}
