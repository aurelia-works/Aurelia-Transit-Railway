package org.mtr.aurelia.client;

import net.minecraft.client.MinecraftClient;
import org.mtr.aurelia.Operators;

import javax.annotation.Nullable;

/**
 * The client's view of {@link Operators}: an operator, or the player whose game runs the integrated server (the
 * single-player host). Only greys buttons out and keeps item use in step; the server checks again.
 */
public final class ClientOperators {

	private ClientOperators() {
	}

	public static void register() {
		Operators.setClientHost(player -> MinecraftClient.getInstance().isIntegratedServerRunning());
	}

	public static boolean isOperator(@Nullable MinecraftClient client) {
		return client != null && client.player != null && Operators.isOperator(client.player);
	}
}
