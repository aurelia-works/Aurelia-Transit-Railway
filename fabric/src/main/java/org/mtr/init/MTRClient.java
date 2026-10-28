package org.mtr.init;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.mtr.mod.InitClient;
import org.mtr.mod.render.MtrFrameStats;

public final class MTRClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		InitClient.init();
		WorldRenderEvents.END.register(context -> MtrFrameStats.INSTANCE.frame(System.nanoTime()));
	}
}
