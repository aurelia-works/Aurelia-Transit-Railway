package org.mtr.aurelia.route;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.*;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.data.LiveDestination;

/**
 * MTR #1010: a PIDS kept the old destination until the depot's path was refreshed.
 */
public final class LiveDestinationTest {

	@Test
	public void editedDestinationShowsWithoutAPathRefresh() {
		final Data data = new Data() {
		};
		final Position a1 = new Position(0, 64, 0);
		final Position a2 = new Position(20, 64, 0);
		final Position b1 = new Position(120, 64, 0);
		final Position b2 = new Position(140, 64, 0);
		data.rails.add(Rail.newPlatformRail(a1, Angle.E, a2, Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), TransportMode.TRAIN));
		data.rails.add(Rail.newPlatformRail(b1, Angle.E, b2, Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), TransportMode.TRAIN));
		final Platform platformA = new Platform(a1, a2, TransportMode.TRAIN, data);
		final Platform platformB = new Platform(b1, b2, TransportMode.TRAIN, data);
		data.platforms.add(platformA);
		data.platforms.add(platformB);
		final Route route = new Route(TransportMode.TRAIN, data);
		final RoutePlatformData first = new RoutePlatformData(platformA.getId());
		first.setCustomDestination("Old Town");
		route.getRoutePlatforms().add(first);
		route.getRoutePlatforms().add(new RoutePlatformData(platformB.getId()));
		data.routes.add(route);
		data.sync();

		// What the depot wrote into the timetable when it generated
		final String baked = route.getDestination(0);
		Assertions.assertEquals("Old Town", baked);

		// The player edits the destination; the depot is not regenerated
		first.setCustomDestination("New Town");
		Assertions.assertEquals("New Town", LiveDestination.forArrival(baked, route, 0));

		// No route (removed while the train runs): keep what the timetable has
		Assertions.assertEquals("Old Town", LiveDestination.forArrival(baked, null, 0));
	}
}
