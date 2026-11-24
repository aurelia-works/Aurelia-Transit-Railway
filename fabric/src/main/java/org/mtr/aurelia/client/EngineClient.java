package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.mtr.mod.engine.EngineFeature;
import org.mtr.mod.engine.EngineLag;
import org.mtr.mod.engine.EngineSettings;

import java.util.EnumMap;
import java.util.Map;

/** The engine switches the server last sent, for the Aurelia panel's Engine tab. */
public final class EngineClient {

	private static volatile Map<EngineFeature, Boolean> values = new EnumMap<>(EngineFeature.class);
	private static volatile EngineLag.Reading lag = EngineLag.Reading.NONE;
	private static volatile long lagAt;

	private EngineClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(EngineSettings.SYNC, (client, handler, buf, response) -> {
			final Map<EngineFeature, Boolean> read = EngineSettings.read(buf);
			client.execute(() -> values = read);
		});
		ClientPlayNetworking.registerGlobalReceiver(EngineSettings.LAG, (client, handler, buf, response) -> {
			final EngineLag.Reading read = new EngineLag.Reading(buf.readVarLong(), buf.readVarLong());
			client.execute(() -> {
				lag = read;
				lagAt = System.currentTimeMillis();
			});
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			values = new EnumMap<>(EngineFeature.class);
			lag = EngineLag.Reading.NONE;
			lagAt = 0;
		});
	}

	/** On until the server says otherwise, as every feature starts on. */
	public static boolean enabled(EngineFeature feature) {
		return values.getOrDefault(feature, true);
	}

	/** The last lag reading, or null when none came in the last few seconds (readout off, or not an operator). */
	public static EngineLag.Reading lag() {
		return System.currentTimeMillis() - lagAt < 3000 ? lag : null;
	}

	public static void request(EngineFeature feature, boolean value) {
		if (ClientPlayNetworking.canSend(EngineSettings.SET)) {
			ClientPlayNetworking.send(EngineSettings.SET, EngineSettings.request(feature, value));
			final Map<EngineFeature, Boolean> next = new EnumMap<>(EngineFeature.class);
			next.putAll(values);
			next.put(feature, value);
			values = next;
		}
	}
}
