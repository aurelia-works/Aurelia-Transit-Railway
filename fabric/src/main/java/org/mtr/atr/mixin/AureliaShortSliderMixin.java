package org.mtr.atr.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaUi;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.screen.WidgetShorterSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Depot, siding and options sliders draw a stone texture themselves, so {@link AureliaSliderMixin} never sees them.
 * This replaces that draw. MTR's click and drag behaviour is untouched.
 */
@Mixin(value = WidgetShorterSlider.class, remap = false)
public abstract class AureliaShortSliderMixin {

	@Shadow
	private int maxValue;

	@Shadow
	private int markerFrequency;

	@Shadow
	private int markerDisplayedRatio;

	@Shadow
	public abstract int getIntValue();

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void atr$flatShortSlider(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!AureliaUi.active()) {
			return;
		}
		final DrawContext context = ((GraphicsHolderDrawContext) (Object) graphicsHolder).atr$getDrawContext();
		if (context == null) {
			return;
		}
		final ClickableWidget widget = (ClickableWidget) (Object) this;
		final int w = widget.getWidth();
		final int h = widget.getHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		final Text message = widget.getMessage();
		AureliaChrome.shortSlider(context, MinecraftClient.getInstance().textRenderer, widget.getX(), widget.getY(), w, h, this.getIntValue(), this.maxValue, this.markerFrequency, this.markerDisplayedRatio, widget.isHovered(), message);
		ci.cancel();
	}
}
