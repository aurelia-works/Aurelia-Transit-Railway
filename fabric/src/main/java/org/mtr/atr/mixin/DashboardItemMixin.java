package org.mtr.atr.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.mtr.mod.Items;
import org.mtr.mod.item.ItemDashboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Titles the primary {@code mtr:dashboard} item as the unified Transit Dashboard while keeping every
 * {@code mtr:dashboard*} ID registered. Item tooltips are appended via Fabric's {@code ItemTooltipCallback}
 * in {@link org.mtr.aurelia.dashboard.UnifiedDashboardClient} because MTR's {@code ItemExtension.appendTooltip} is final.
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
}
