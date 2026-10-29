package org.mtr.aurelia.ride;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.UUID;

/**
 * Whether the local player sits or stands while riding a vehicle. Each ride starts from the panel's default; the sit key
 * flips it until the player gets off. Off a vehicle the player always stands.
 */
public final class RidePose {

	private boolean defaultSitting;
	private boolean riding;
	private boolean sitting;
	private boolean changed;

	public RidePose(boolean defaultSitting) {
		this.defaultSitting = defaultSitting;
	}

	public void setDefault(boolean defaultSitting) {
		this.defaultSitting = defaultSitting;
	}

	/**
	 * Called every client tick.
	 *
	 * @return whether the player sits now
	 */
	public boolean tick(boolean ridingNow) {
		if (ridingNow && !riding) {
			set(defaultSitting);
		} else if (!ridingNow) {
			set(false);
		}
		riding = ridingNow;
		return sitting;
	}

	/**
	 * The sit key: sit down or stand up, only while riding.
	 */
	public void toggle() {
		if (riding) {
			set(!sitting);
		}
	}

	public boolean isSitting() {
		return sitting;
	}

	/**
	 * Whether the pose changed since the last call, so it is sent to the server once.
	 */
	public boolean changed() {
		final boolean result = changed;
		changed = false;
		return result;
	}

	private void set(boolean value) {
		if (sitting != value) {
			sitting = value;
			changed = true;
		}
	}

	/**
	 * Which other players sit, as the server reports them. A seated rider's client repeats the pose every few seconds, so a
	 * report older than {@link #EXPIRE_MILLIS} means the player left or logged out seated, and they are drawn standing.
	 */
	public static final class Others {

		public static final long EXPIRE_MILLIS = 12_000;

		private final Object2LongOpenHashMap<UUID> sittingSince = new Object2LongOpenHashMap<>();

		public void set(UUID uuid, boolean value, long nowMillis) {
			if (value) {
				sittingSince.put(uuid, nowMillis);
			} else {
				sittingSince.removeLong(uuid);
			}
		}

		public boolean isSitting(UUID uuid, long nowMillis) {
			return sittingSince.containsKey(uuid) && nowMillis - sittingSince.getLong(uuid) <= EXPIRE_MILLIS;
		}

		public void clear() {
			sittingSince.clear();
		}
	}
}
