package org.mtr.atr.mixin;

import org.mtr.aurelia.dashboard.UnifiedDashboardClient;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.packet.ClientPacketHelper;
import org.mtr.mod.packet.PacketOpenDashboardScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes incoming dashboard open packets through {@link UnifiedDashboardClient}: legacy items open their starting
 * transport mode, while the primary Transit Dashboard (and ATR menu / panel entries) opens the last selected mode
 * (including Bus & Road).
 */
@Mixin(value = ClientPacketHelper.class, remap = false)
public abstract class DashboardOpenMixin {

	@Inject(method = "openDashboardScreen", at = @At("HEAD"), cancellable = true)
	private static void atr$openUnifiedDashboard(
			TransportMode transportMode,
			PacketOpenDashboardScreen.ScreenType screenType,
			long id,
			CallbackInfo ci
	) {
		if (UnifiedDashboardClient.openFromPacket(transportMode, screenType, id)) {
			ci.cancel();
		}
	}
}
