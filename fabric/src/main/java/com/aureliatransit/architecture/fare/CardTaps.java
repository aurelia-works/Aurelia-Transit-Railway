package com.aureliatransit.architecture.fare;

import com.aureliatransit.architecture.registry.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.mtr.mod.config.Config;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One alternating tap-in / tap-out per player. The station string is whatever the gate knows (its block position when
 * no station name is available). Zone charging runs only when the policy is enabled and MTR's barriers are free, so
 * MTR's own fare is not charged a second time. The daily cap is applied inside {@link FarePolicy#quote}.
 */
public final class CardTaps {

	private record Open(String station) {
	}

	private static final Map<UUID, Open> OPEN = new ConcurrentHashMap<>();

	private CardTaps() {
	}

	public static void onPass(ServerPlayerEntity player, String place) {
		final ItemStack card = card(player);
		if (card == null) {
			return;
		}
		final long now = System.currentTimeMillis();
		final Open open = OPEN.remove(player.getUuid());
		if (open == null) {
			OPEN.put(player.getUuid(), new Open(place));
			CardHistory.remember(card, new CardHistory.Tap(place, true, 0, now));
			return;
		}
		final FarePolicy policy = FarePolicy.parse(Config.getServer().getZoneFareBase(), Config.getServer().getZoneFareStep(),
				Config.getServer().getDailyFareCap(), Config.getServer().getFareZoneTable());
		int fare = 0;
		if (policy.enabled() && Config.getServer().getFreeTicketBarriers()) {
			fare = policy.quote(open.station(), place, CardHistory.spentOnDay(CardHistory.read(card), now));
			if (fare > 0) {
				com.aureliatransit.architecture.block.mtr.MtrFareContract.addBalance(player.getWorld(), player, -fare);
			}
		}
		CardHistory.remember(card, new CardHistory.Tap(place, false, fare, now));
	}

	private static ItemStack card(PlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			final ItemStack stack = player.getInventory().getStack(i);
			if (stack.isOf(ModItems.TRANSIT_CARD)) {
				return stack;
			}
		}
		return null;
	}
}
