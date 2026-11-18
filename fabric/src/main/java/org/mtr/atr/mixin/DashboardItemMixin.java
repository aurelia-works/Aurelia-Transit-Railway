package org.mtr.atr.mixin;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.mtr.mod.Items;
import org.mtr.mod.item.ItemDashboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Titles the primary {@code mtr:dashboard} item as the unified Transit Dashboard and adds mode tooltips to all
 * dashboard items while keeping every {@code mtr:dashboard*} ID registered.
 */
@Mixin(Item.class)
public abstract class DashboardItemMixin {

	@Unique
	private boolean atr$isPrimaryDashboard() {
		return (Object) this instanceof ItemDashboard
				&& Items.RAILWAY_DASHBOARD != null
				&& Items.RAILWAY_DASHBOARD.isPresent()
				&& (Object) this == Items.RAILWAY_DASHBOARD.get().data;
	}

	@Inject(method = "getName()Lnet/minecraft/text/Text;", at = @At("HEAD"), cancellable = true)
	private void atr$primaryDashboardName(CallbackInfoReturnable<Text> cir) {
		if (atr$isPrimaryDashboard()) {
			cir.setReturnValue(Text.translatable("item.aurelia.transit_dashboard"));
		}
	}

	@Inject(method = "getName(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/text/Text;", at = @At("HEAD"), cancellable = true)
	private void atr$primaryDashboardStackName(ItemStack stack, CallbackInfoReturnable<Text> cir) {
		if (atr$isPrimaryDashboard()) {
			cir.setReturnValue(Text.translatable("item.aurelia.transit_dashboard"));
		}
	}

	@Inject(method = "appendTooltip", at = @At("TAIL"))
	private void atr$dashboardTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context, CallbackInfo ci) {
		if (!((Object) this instanceof ItemDashboard)) {
			return;
		}
		if (atr$isPrimaryDashboard()) {
			tooltip.add(Text.translatable("item.aurelia.transit_dashboard.modes").formatted(Formatting.GOLD));
			tooltip.add(Text.translatable("item.aurelia.transit_dashboard.sneak").formatted(Formatting.GRAY));
		} else {
			tooltip.add(Text.translatable("item.aurelia.transit_dashboard.legacy").formatted(Formatting.GRAY));
		}
	}
}
