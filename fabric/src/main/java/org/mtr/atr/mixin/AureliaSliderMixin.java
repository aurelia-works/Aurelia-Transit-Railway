package org.mtr.atr.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.client.ui.AureliaUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flat track and handle for sliders that use {@link SliderWidget#renderButton}. The label stays the widget's own text. */
@Mixin(SliderWidget.class)
public abstract class AureliaSliderMixin {

	@Shadow
	protected double value;

	@Inject(method = "renderButton(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
	private void atr$flatSlider(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!AureliaUi.active()) {
			return;
		}
		final ClickableWidget widget = (ClickableWidget) (Object) this;
		final int w = widget.getWidth();
		final int h = widget.getHeight();
		if (!widget.visible || w <= 0 || h <= 0) {
			return;
		}
		final int x = widget.getX();
		final int y = widget.getY();
		AureliaChrome.slider(context, x, y, w, h, this.value, widget.isHovered());
		final Text message = ((AureliaWidgetText) (Object) this).atr$message();
		if (message != null) {
			final int color = widget.active ? AureliaPalette.FG : AureliaPalette.FG_LIGHT;
			context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, message, x + w / 2, y + (h - 8) / 2, color);
		}
		ci.cancel();
	}
}
