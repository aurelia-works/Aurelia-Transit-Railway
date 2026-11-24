package org.mtr.aurelia.dashboard;

import org.mtr.core.data.TransportMode;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;

/**
 * Pure state machine for pending UI-shortcut dashboard open requests ({@code AureliaPanelScreen}).
 * <ul>
 *   <li>Expires unanswered requests after {@link #EXPIRY_MS} ms and clears on disconnect.</li>
 *   <li>Consumes a pending request ONLY when {@code packetMode == TransportMode.TRAIN} and {@code isDefaultScreen},
 *       so legacy {@code mtr:dashboard_2..4} items ({@code BOAT}/{@code CABLE_CAR}/{@code AIRPLANE}) and sneak-edits
 *       are never hijacked.</li>
 *   <li>Honors {@code forceReplaceScreen} only when {@code currentScreen == null || currentScreen == originScreen}.</li>
 * </ul>
 */
public final class DashboardOpenState {

	public static final long EXPIRY_MS = 3_000L;

	public record PacketResolution(
			boolean handledByUnified,
			@Nullable DashboardMode targetMode,
			boolean forceReplaceScreen,
			boolean updateLastMode
	) {
	}

	private record PendingOpenRequest(
			DashboardMode requestedMode,
			long requestedAtMs,
			@Nullable WeakReference<Object> originScreen
	) {
		boolean matchesOrigin(@Nullable Object currentScreen) {
			if (currentScreen == null) {
				return true;
			}
			return originScreen != null && currentScreen == originScreen.get();
		}
	}

	@Nullable
	private PendingOpenRequest pending;

	public void arm(@Nullable DashboardMode requestedMode, @Nullable Object originScreen, long nowMs) {
		if (requestedMode == null) {
			return;
		}
		this.pending = new PendingOpenRequest(
				requestedMode,
				nowMs,
				originScreen != null ? new WeakReference<>(originScreen) : null
		);
	}

	public void clear() {
		this.pending = null;
	}

	public boolean hasPending(long nowMs) {
		pruneExpired(nowMs);
		return this.pending != null;
	}

	private void pruneExpired(long nowMs) {
		if (this.pending != null && nowMs - this.pending.requestedAtMs() > EXPIRY_MS) {
			this.pending = null;
		}
	}

	public PacketResolution resolvePacket(
			TransportMode packetMode,
			boolean isDefaultScreen,
			@Nullable DashboardMode rememberedMode,
			@Nullable Object currentScreen,
			long nowMs
	) {
		pruneExpired(nowMs);

		if (!isDefaultScreen) {
			// Sneak-editing a station/platform/depot/siding: let MTR open its editor without mutating lastMode.
			return new PacketResolution(false, null, false, false);
		}

		if (packetMode == TransportMode.TRAIN && this.pending != null) {
			final PendingOpenRequest consumed = this.pending;
			this.pending = null;
			final boolean force = consumed.matchesOrigin(currentScreen);
			return new PacketResolution(true, consumed.requestedMode(), force, true);
		}

		final DashboardMode resolved = DashboardMode.resolveOpenMode(packetMode, true, rememberedMode);
		return new PacketResolution(true, resolved, false, true);
	}
}
