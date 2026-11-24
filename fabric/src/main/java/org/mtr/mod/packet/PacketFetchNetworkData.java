package org.mtr.mod.packet;

import org.mtr.core.operation.ListDataResponse;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.serializer.WriterBase;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mod.client.MinecraftClientData;

import javax.annotation.Nonnull;

/**
 * Loads the network (routes, stations, depots) into the Dashboard's client copy without opening the Dashboard. ATR
 * screens that list routes, such as the signaller, send it when they open before the Dashboard has been opened.
 * Same server call and same data as {@link PacketOpenDashboardScreen}.
 */
public final class PacketFetchNetworkData extends PacketRequestResponseBase {

	public PacketFetchNetworkData(PacketBufferReceiver packetBufferReceiver) {
		super(packetBufferReceiver);
	}

	private PacketFetchNetworkData(String content) {
		super(content);
	}

	/** The request a client sends to the server. */
	public static PacketFetchNetworkData create() {
		return new PacketFetchNetworkData(new JsonObject().toString());
	}

	@Override
	protected void runClientInbound(JsonReader jsonReader) {
		new ListDataResponse(jsonReader, MinecraftClientData.getDashboardInstance()).write();
	}

	@Override
	protected PacketRequestResponseBase getInstance(String content) {
		return new PacketFetchNetworkData(content);
	}

	@Override
	protected SerializedDataBase getDataInstance(JsonReader jsonReader) {
		return new SerializedDataBase() {
			@Override
			public void updateData(ReaderBase readerBase) {
			}

			@Override
			public void serializeData(WriterBase writerBase) {
			}
		};
	}

	@Nonnull
	@Override
	protected String getKey() {
		return OperationProcessor.LIST_DATA;
	}

	@Override
	protected ResponseType responseType() {
		return ResponseType.PLAYER;
	}
}
