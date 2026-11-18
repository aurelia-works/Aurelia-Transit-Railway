package org.mtr.aurelia.dashboard;

import org.mtr.core.data.TransportMode;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The five modes of ATR's unified Transit Dashboard: the four MTR rail/water/cable/air modes plus Bus & Road.
 * Pure data with no Minecraft client types so unit tests can exercise mode resolution directly.
 */
public enum DashboardMode {

	TRAIN("train", TransportMode.TRAIN, 26, 42),
	ROAD("road", null, 56, 72),
	BOAT("boat", TransportMode.BOAT, 22, 38),
	CABLE_CAR("cable_car", TransportMode.CABLE_CAR, 49, 65),
	AIRPLANE("airplane", TransportMode.AIRPLANE, 27, 43);

	private static final String KEY_PREFIX = "gui.aurelia.dashboard.mode.";

	public final String id;
	@Nullable
	public final TransportMode transportMode;
	public final int baseTextWidth;
	public final int idealButtonWidth;

	DashboardMode(String id, @Nullable TransportMode transportMode, int baseTextWidth, int idealButtonWidth) {
		this.id = id;
		this.transportMode = transportMode;
		this.baseTextWidth = baseTextWidth;
		this.idealButtonWidth = idealButtonWidth;
	}

	public boolean isMtrMode() {
		return transportMode != null;
	}

	public String labelKey() {
		return KEY_PREFIX + id;
	}

	public String tooltipKey() {
		return KEY_PREFIX + id + ".tooltip";
	}

	/** Cycles forwards or backwards across all five modes, wrapping at either end. */
	public DashboardMode next(int step) {
		final DashboardMode[] modes = values();
		return modes[Math.floorMod(ordinal() + step, modes.length)];
	}

	public static DashboardMode fromTransportMode(@Nullable TransportMode transportMode) {
		if (transportMode == null) {
			return TRAIN;
		}
		return switch (transportMode) {
			case TRAIN -> TRAIN;
			case BOAT -> BOAT;
			case CABLE_CAR -> CABLE_CAR;
			case AIRPLANE -> AIRPLANE;
		};
	}

	public static DashboardMode byId(@Nullable String raw, DashboardMode fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		final String normalized = raw.strip().toLowerCase(Locale.ROOT);
		for (final DashboardMode mode : values()) {
			if (mode.id.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
				return mode;
			}
		}
		return fallback;
	}

	/**
	 * Resolves which mode an incoming dashboard open packet should start on:
	 * <ul>
	 *   <li>Legacy items ({@code dashboard_2} Boat, {@code dashboard_3} Cable Car, {@code dashboard_4} Plane) start on
	 *       their dedicated mode.</li>
	 *   <li>Sneak-opening a station/platform/depot/siding ({@code !isDefaultScreen}) uses that packet's transport mode.</li>
	 *   <li>Opening the primary {@code mtr:dashboard} ({@code TRAIN} + default screen) opens {@code rememberedMode}
	 *       (defaulting to {@link #TRAIN}).</li>
	 * </ul>
	 */
	public static DashboardMode resolveOpenMode(TransportMode packetMode, boolean isDefaultScreen, @Nullable DashboardMode rememberedMode) {
		if (!isDefaultScreen) {
			return fromTransportMode(packetMode);
		}
		if (packetMode != null && packetMode != TransportMode.TRAIN) {
			return fromTransportMode(packetMode);
		}
		return rememberedMode != null ? rememberedMode : TRAIN;
	}
}
