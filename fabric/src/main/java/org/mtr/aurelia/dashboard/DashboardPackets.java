package org.mtr.aurelia.dashboard;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.mtr.core.data.TransportMode;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mod.packet.PacketOpenDashboardScreen;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Requests a fresh dashboard data sync ({@code LIST_DATA}) and opens the unified Transit Dashboard when opening from
 * the ATR menu / Aurelia panel shortcuts, plus pure item ID helpers shared between client tooltips and server checks.
 */
public final class DashboardPackets {

	public static final Identifier OPEN_DASHBOARD = new Identifier("mtr", "atr_open_dashboard");
	public static final String PRIMARY_DASHBOARD_ITEM_ID = "mtr:dashboard";
	private static final Set<String> DASHBOARD_ITEM_IDS = Set.of(
			PRIMARY_DASHBOARD_ITEM_ID,
			"mtr:dashboard_2",
			"mtr:dashboard_3",
			"mtr:dashboard_4"
	);

	private DashboardPackets() {
	}

	public static boolean isPrimaryDashboardItemId(@Nullable String itemId) {
		return PRIMARY_DASHBOARD_ITEM_ID.equals(itemId);
	}

	public static boolean isDashboardItemId(@Nullable String itemId) {
		return itemId != null && DASHBOARD_ITEM_IDS.contains(itemId);
	}

	/**
	 * Returns the ordered tooltip translation keys for a dashboard item ID:
	 * mode list + sneak-edit hint for {@code mtr:dashboard}, legacy hint for {@code mtr:dashboard_2..4}, or empty.
	 */
	public static List<String> tooltipTranslationKeys(@Nullable String itemId) {
		if (!isDashboardItemId(itemId)) {
			return List.of();
		}
		if (isPrimaryDashboardItemId(itemId)) {
			return List.of("item.aurelia.transit_dashboard.modes", "item.aurelia.transit_dashboard.sneak");
		}
		return List.of("item.aurelia.transit_dashboard.legacy");
	}

	/** Operators, or anyone carrying a dashboard item as in MTR. Creative mode alone no longer opens it. */
	public static boolean isAllowed(boolean operator, boolean carryingDashboard) {
		return operator || carryingDashboard;
	}

	public static boolean hasDashboardItem(@Nullable PlayerEntity player) {
		if (player == null) {
			return false;
		}
		final PlayerInventory inventory = player.getInventory();
		for (int i = 0; i < inventory.size(); i++) {
			final ItemStack stack = inventory.getStack(i);
			if (!stack.isEmpty() && isDashboardItemId(Registries.ITEM.getId(stack.getItem()).toString())) {
				return true;
			}
		}
		return false;
	}

	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(OPEN_DASHBOARD, (server, player, handler, buf, responseSender) -> {
			if (buf.isReadable()) {
				buf.readString(32);
			}
			server.execute(() -> {
				if (!isAllowed(player.hasPermissionLevel(2), hasDashboardItem(player))) {
					return;
				}
				// Always reply with TRAIN + DEFAULT so pending menu requests never collide with legacy dashboard_2..4 packets.
				PacketOpenDashboardScreen.sendDirectlyToServer(
						new ServerWorld(player.getServerWorld()),
						new org.mtr.mapping.holder.ServerPlayerEntity(player),
						TransportMode.TRAIN
				);
			});
		});
	}
}
