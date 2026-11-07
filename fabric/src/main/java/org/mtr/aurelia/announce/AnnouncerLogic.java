package org.mtr.aurelia.announce;

import com.aureliatransit.architecture.live.announce.FragmentKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Decides when to announce on one ride. Each leg (this platform + next platform) gets at most one "next station" after
 * departure and one "arriving" before the platform.
 * <p>
 * MTR moves to the next leg when the train's head enters a platform, while it is still braking, so a leg only counts
 * as departed once the train has stood still (or opened its doors) since the leg changed. Boarding a moving train
 * therefore stays quiet until the next stop.
 * <p>
 * MTR announces once the train has moved 1.5 train lengths after the doors close, but its rail progress is not public.
 * Like the AureliaEX {@code onboard.js}, "next" fires instead once the doors are shut and the train moves faster than
 * {@link #MIN_SPEED}. "Arriving" fires when the next platform is about {@link #ARRIVE_SECONDS} away (never closer than
 * {@link #ARRIVE_MIN_BLOCKS}); it has no recorded clip, so it is the chime plus text.
 */
public final class AnnouncerLogic {

	public static final double MIN_SPEED = 2;
	public static final double STOPPED_SPEED = 0.1;
	public static final double ARRIVE_SECONDS = 20;
	public static final double ARRIVE_MIN_BLOCKS = 60;
	/** A short leg would put "arriving" right after "next station"; then only "next station" is played. */
	public static final long ARRIVE_GAP_MILLIS = 20_000;

	private long vehicleId = Long.MIN_VALUE;
	private String legKey;
	private boolean departed;
	private String nextKey;
	private String arriveKey;
	private long nextAt;

	public void reset() {
		vehicleId = Long.MIN_VALUE;
		legKey = null;
		departed = false;
		nextKey = null;
		arriveKey = null;
	}

	/** @param hasClip whether a clip key exists in the loaded resource packs */
	public Announcement update(RideSnapshot ride, long nowMillis, Predicate<String> hasClip) {
		if (ride.vehicleId() != vehicleId) {
			reset();
			vehicleId = ride.vehicleId();
		}
		if (ride.nextStation().isBlank()) {
			return null;
		}
		final String key = ride.thisPlatformId() + "|" + ride.nextPlatformId() + "|" + ride.nextStation();
		if (!key.equals(legKey)) {
			legKey = key;
			departed = false;
		}
		if (ride.doorValue() > 0 || ride.speedMetresPerSecond() < STOPPED_SPEED) {
			departed = true;
			return null;
		}
		if (!departed || ride.speedMetresPerSecond() <= MIN_SPEED) {
			return null;
		}
		final boolean near = ride.distanceToNextStop() <= Math.max(ARRIVE_MIN_BLOCKS, ride.speedMetresPerSecond() * ARRIVE_SECONDS);
		if (!key.equals(nextKey)) {
			nextKey = key;
			if (!near) {
				nextAt = nowMillis;
				return ride.announcementsDisabled() ? null : next(ride, hasClip);
			}
			// Departed too close to the next platform for "next station": go straight to "arriving".
			nextAt = nowMillis - ARRIVE_GAP_MILLIS;
		}
		if (near && !key.equals(arriveKey)) {
			arriveKey = key;
			if (nowMillis - nextAt >= ARRIVE_GAP_MILLIS && !ride.announcementsDisabled()) {
				return arriving(ride);
			}
		}
		return null;
	}

	static Announcement next(RideSnapshot ride, Predicate<String> hasClip) {
		final StationName station = StationName.of(ride.nextStation());
		final List<String> lines = new ArrayList<>();
		final List<String> clips = new ArrayList<>();
		final List<Announcement.VoiceFragment> voice = new ArrayList<>();
		lines.add("The next station is " + station.english() + ".");
		voice.add(new Announcement.VoiceFragment("phrase.next_station_is", "The next station is"));
		if (!station.slug().isEmpty()) {
			voice.add(new Announcement.VoiceFragment("station." + station.slug(), station.english()));
		}
		final String nextClip = "next_" + station.slug();
		clips.add(!station.slug().isEmpty() && hasClip.test(nextClip) ? nextClip : "chime");
		addInterchangeLines(ride, lines);
		addInterchangeFragments(ride, voice);
		if (lastStop(ride)) {
			lines.add("This is the last stop.");
			voice.add(new Announcement.VoiceFragment("phrase.this_is_the_last_stop", "This is the last stop."));
			if (hasClip.test("terminus")) {
				clips.add("terminus");
			}
		}
		return new Announcement(Announcement.Kind.NEXT, lines, clips, voice);
	}

	static Announcement arriving(RideSnapshot ride) {
		final StationName station = StationName.of(ride.nextStation());
		final List<String> lines = new ArrayList<>();
		final List<Announcement.VoiceFragment> voice = new ArrayList<>();
		lines.add("Arriving at " + station.english() + ".");
		voice.add(new Announcement.VoiceFragment("phrase.arriving_at", "Arriving at"));
		if (!station.slug().isEmpty()) {
			voice.add(new Announcement.VoiceFragment("station." + station.slug(), station.english()));
		}
		addInterchangeLines(ride, lines);
		addInterchangeFragments(ride, voice);
		if (lastStop(ride)) {
			lines.add("This is the last stop. Please take all your belongings.");
			voice.add(new Announcement.VoiceFragment("phrase.this_is_the_last_stop", "This is the last stop."));
			voice.add(new Announcement.VoiceFragment("phrase.take_belongings", "Please take all of your belongings with you."));
		}
		return new Announcement(Announcement.Kind.ARRIVING, lines, List.of("chime"), voice);
	}

	/** Voice-pack fragments for the interchange and through-service lines added by {@link #addInterchangeLines}. */
	private static void addInterchangeFragments(RideSnapshot ride, List<Announcement.VoiceFragment> voice) {
		final List<String> changeHere = changeHere(ride);
		if (!changeHere.isEmpty()) {
			voice.add(new Announcement.VoiceFragment("phrase.change_here_for", "Change here for"));
			for (final String name : changeHere) {
				voice.add(new Announcement.VoiceFragment("route." + FragmentKeys.normalise(name), name));
			}
		}
		if (continues(ride)) {
			final String route = StationName.routeName(ride.nextRoute());
			final String destination = StationName.of(ride.nextRouteDestination()).english();
			voice.add(new Announcement.VoiceFragment("phrase.this_train_continues_as", "This train continues as"));
			if (!route.isBlank()) {
				voice.add(new Announcement.VoiceFragment("route." + FragmentKeys.normalise(route), route));
			}
			if (!destination.isBlank()) {
				voice.add(new Announcement.VoiceFragment("phrase.to", "to"));
				voice.add(new Announcement.VoiceFragment("station." + StationName.of(destination).slug(), destination));
			}
		}
	}

	private static void addInterchangeLines(RideSnapshot ride, List<String> lines) {
		final List<String> changeHere = changeHere(ride);
		if (!changeHere.isEmpty()) {
			lines.add("Change here for " + joinNames(changeHere) + ".");
		}
		if (continues(ride)) {
			final String destination = StationName.of(ride.nextRouteDestination()).english();
			lines.add("This train continues as " + StationName.of(StationName.routeName(ride.nextRoute())).english() + (destination.isBlank() ? "." : " to " + destination + "."));
		}
	}

	/** The route ends at the next stop and the train does not carry on as another route. */
	static boolean lastStop(RideSnapshot ride) {
		return ride.terminating() && !continues(ride);
	}

	/** MTR's rule for its "change route" line: the route after the next stop differs by name or colour. */
	static boolean continues(RideSnapshot ride) {
		final String nextRoute = StationName.routeName(ride.nextRoute());
		return !nextRoute.isBlank() && (ride.nextRouteColor() != ride.routeColor() || !nextRoute.equals(StationName.routeName(ride.route())));
	}

	/**
	 * English names of the other routes at the next station itself, in MTR's order, without duplicates. Same filter as
	 * MTR's own announcement: the route the train is on and the one it continues as are left out.
	 */
	static List<String> changeHere(RideSnapshot ride) {
		final String route = StationName.routeName(ride.route());
		final String nextRoute = StationName.routeName(ride.nextRoute());
		final List<String> names = new ArrayList<>();
		for (final RideSnapshot.Interchange interchange : ride.interchanges()) {
			final String name = StationName.routeName(interchange.routeName());
			if (!interchange.stationName().equals(ride.nextStation()) || name.isBlank()
					|| interchange.color() == ride.routeColor() && name.equals(route)
					|| interchange.color() == ride.nextRouteColor() && name.equals(nextRoute)) {
				continue;
			}
			final String english = StationName.of(name).english();
			if (!names.contains(english)) {
				names.add(english);
			}
		}
		return names;
	}

	/** "A", "A and B", "A, B and C". */
	static String joinNames(List<String> names) {
		if (names.size() == 1) {
			return names.get(0);
		}
		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}
}
