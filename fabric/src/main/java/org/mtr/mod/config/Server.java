package org.mtr.mod.config;

import org.mtr.core.serializer.ReaderBase;
import org.mtr.mod.generated.config.ServerSchema;

public final class Server extends ServerSchema {

	public Server(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public int getWebserverPort() {
		return (int) webserverPort;
	}

	public boolean getUseThreadedSimulation() {
		return useThreadedSimulation;
	}

	public boolean getUseThreadedFileLoading() {
		return useThreadedFileLoading;
	}

	public boolean forceShutDownStrayThreads() {
		return forceShutDownStrayThreads;
	}

	/** Raw configured item id, including values that are not a real item. Blank becomes the emerald default. */
	public String getTicketCurrency() {
		return ticketCurrency == null || ticketCurrency.isBlank() ? org.mtr.mod.data.TicketRules.DEFAULT_CURRENCY_ID : ticketCurrency;
	}

	public boolean getFreeTicketBarriers() {
		return freeTicketBarriers;
	}

	/** Copies only the ticket keys, so a config reload cannot reset the webserver port or the threading flags. */
	void copyTicketSettingsFrom(Server other) {
		ticketCurrency = other.ticketCurrency;
		freeTicketBarriers = other.freeTicketBarriers;
	}
}
