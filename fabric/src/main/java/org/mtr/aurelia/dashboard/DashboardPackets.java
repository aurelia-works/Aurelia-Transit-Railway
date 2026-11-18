package org.mtr.aurelia.dashboard;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.EnumHelper;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mod.packet.PacketOpenDashboardScreen;

import java.util.Set;

/**
 * Requests a fresh dashboard data sync ({@code LIST_DATA}) and opens the unified Transit Dashboard when switching modes
 * or opening from the ATR menu / Aurelia panel shortcuts.
 */
public final class DashboardPackets {

	public static final Identifier OPEN_DASHBOARD = new Identifier("mtr", "atr_open_dashboard");
	private static final Set<String> DASHBOARD_ITEM_IDS = Set.of(
			"mtr:dashboard",
			"mtr:dashboard_2",
			"mtr:dashboard_3",
			"mtr:dashboard_4"
	);

	private DashboardPackets() {
	}

	public static boolean isDashboardItemId(String itemId) {
		return DASHBOARD_ITEM_IDS.contains(itemId);
	}

	public static boolean isAllowed(boolean creative, boolean operator, boolean carryingDashboard) {
		return creative || operator || carryingDashboard;
	}

	private static boolean hasDashboardItem(ServerPlayerEntity player) {
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
			final String modeName = buf.readString(32);
			final TransportMode transportMode = EnumHelper.valueOf(TransportMode.TRAIN, modeName);
			server.execute(() -> {
				if (!isAllowed(player.isCreative(), player.hasPermissionLevel(2), hasDashboardItem(player))) {
					return;
				}
				PacketOpenDashboardScreen.sendDirectlyToServer(
						new ServerWorld(player.getServerWorld()),
						new org.mtr.mapping.holder.ServerPlayerEntity(player),
						transportMode
				);
			});
		});
	}
}
