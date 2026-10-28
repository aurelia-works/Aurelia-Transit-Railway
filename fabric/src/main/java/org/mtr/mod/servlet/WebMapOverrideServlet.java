package org.mtr.mod.servlet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mtr.core.servlet.HttpResponseStatus;
import org.mtr.core.servlet.ServletBase;
import org.mtr.core.servlet.Webserver;
import org.mtr.libraries.javax.servlet.AsyncContext;
import org.mtr.libraries.javax.servlet.http.HttpServlet;
import org.mtr.libraries.javax.servlet.http.HttpServletRequest;
import org.mtr.libraries.javax.servlet.http.HttpServletResponse;
import org.mtr.libraries.org.eclipse.jetty.servlet.ServletHolder;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves a built copy of the system map website (the Angular {@code dist/website/browser} folder) in place of the one inside Transport Simulation Core.
 * Only active when the {@code ATR_WEBMAP_DIR} environment variable names that folder, so a normal install is unchanged.
 */
public final class WebMapOverrideServlet extends HttpServlet {

	private static final Logger LOGGER = LogManager.getLogger("MinecraftTransitRailway");
	private static final String ENVIRONMENT_VARIABLE = "ATR_WEBMAP_DIR";

	private final Path root;

	private WebMapOverrideServlet(Path root) {
		this.root = root;
	}

	/**
	 * Core's own {@code WebServlet} strips every slash from the path, so it can only serve a flat folder. The new site has {@code assets/i18n/*.json}.
	 */
	@Override
	protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
		final AsyncContext asyncContext = httpServletRequest.startAsync();
		asyncContext.setTimeout(0);
		final String requestUri = httpServletRequest.getRequestURI();
		final String path = requestUri.endsWith("/") ? requestUri.substring(1) + "index.html" : requestUri.substring(1);
		final String content = read(root, path);
		if (content == null) {
			ServletBase.sendResponse(httpServletResponse, asyncContext, "..", "", HttpResponseStatus.REDIRECT);
		} else {
			ServletBase.sendResponse(httpServletResponse, asyncContext, content, ServletBase.getMimeType(path), HttpResponseStatus.OK);
		}
	}

	/**
	 * Mapped to {@code /*} so it beats Core's {@code /} servlet, while the longer {@code /mtr/api/map/*} and {@code /oba/api/where/*} mappings still win.
	 */
	public static void setup(Webserver webserver) {
		final String directory = System.getenv(ENVIRONMENT_VARIABLE);
		if (directory == null || directory.isEmpty()) {
			return;
		}

		final Path root = Paths.get(directory).toAbsolutePath().normalize();
		if (Files.isRegularFile(root.resolve("index.html"))) {
			webserver.addServlet(new ServletHolder(new WebMapOverrideServlet(root)), "/*");
			LOGGER.info("Serving the system map from {}", root);
		} else {
			LOGGER.warn("{} is set to {} but there is no index.html there, using the built-in system map", ENVIRONMENT_VARIABLE, root);
		}
	}

	@Nullable
	public static String read(Path root, String resource) {
		try {
			final Path path = root.resolve(resource).normalize();
			return path.startsWith(root) && Files.isRegularFile(path) ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : null;
		} catch (Exception e) {
			LOGGER.error("", e);
			return null;
		}
	}
}
