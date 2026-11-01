package org.mtr.atr.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.client.ui.AureliaUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flat box in place of the vanilla checkbox texture. The label stays where MTR put it. */
@Mixin(CheckboxWidget.class)
public abstract class AureliaCheckboxMixin {

	@Shadow
	private boolean checked;

	@Shadow
	private boolean showMessage;

	@Inject(method = "renderButton(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
	private void atr$flatCheckbox(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!AureliaUi.active()) {
			return;
		}
		final ClickableWidget widget = (ClickableWidget) (Object) this;
		final int h = widget.getHeight();
		if (!widget.visible || widget.getWidth() <= 0 || h <= 0) {
			return;
		}
		AureliaChrome.checkbox(context, widget.getX(), widget.getY(), h, this.checked, widget.isHovered());
		if (this.showMessage) {
			context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, widget.getMessage(), widget.getX() + 24, widget.getY() + (h - 8) / 2, widget.active ? AureliaPalette.FG : AureliaPalette.FG_LIGHT);
		}
		ci.cancel();
	}
}
