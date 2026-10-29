package org.mtr.mod.data;

import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Item;
import org.mtr.mapping.holder.Items;
import org.mtr.mod.Init;
import org.mtr.mod.config.Config;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * The item a server accepts as ticket credit. Resolved when a machine opens and again when a purchase is paid,
 * so an edit to {@code config/mtr.json} applies without restarting. The client is told the id; it does not read its own config.
 */
public final class TicketCurrency {

	private static final Set<String> WARNED = Collections.synchronizedSet(new java.util.HashSet<>());

	private TicketCurrency() {
	}

	public static final class Resolved {
		public final String id;
		public final Item item;

		Resolved(String id, Item item) {
			this.id = id;
			this.item = item;
		}
	}

	public static Resolved resolve() {
		Config.refreshTicketSettings();
		final String configured = Config.getServer().getTicketCurrency();
		final TicketRules.CurrencyResolution resolution = TicketRules.resolve(configured, TicketCurrency::itemPresent);
		if (resolution.problem != TicketRules.Problem.NONE) {
			warnOnce(resolution);
			return new Resolved(TicketRules.DEFAULT_CURRENCY_ID, Items.getEmeraldMapped());
		}
		final Item item = item(resolution.id);
		if (item == null) {
			warnOnce(resolution);
			return new Resolved(TicketRules.DEFAULT_CURRENCY_ID, Items.getEmeraldMapped());
		}
		return new Resolved(resolution.id, item);
	}

	public static net.minecraft.item.Item minecraftItem() {
		return resolve().item.data;
	}

	/** The item for an id the server already accepted, or null when this side does not have it registered. */
	@Nullable
	public static Item item(@Nullable String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		final Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return null;
		}
		final net.minecraft.util.Identifier raw = identifier.data;
		if (net.minecraft.item.Items.AIR.equals(net.minecraft.registry.Registries.ITEM.get(raw)) || !net.minecraft.registry.Registries.ITEM.containsId(raw)) {
			return null;
		}
		return new Item(net.minecraft.registry.Registries.ITEM.get(raw));
	}

	private static boolean itemPresent(String id) {
		return item(id) != null;
	}

	private static void warnOnce(TicketRules.CurrencyResolution resolution) {
		if (!WARNED.add(resolution.problem + "\0" + resolution.configured)) {
			return;
		}
		Init.LOGGER.warn("Ticket currency '{}' cannot be used ({}); paying with {} instead", resolution.configured, resolution.problem, TicketRules.DEFAULT_CURRENCY_ID);
	}
}
