package org.mtr.atr.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.client.ui.AureliaUi;
import org.mtr.mod.screen.WidgetColorSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flat buttons. {@code ButtonWidget} does not declare {@code renderButton}; {@code PressableWidget} does, and MTR's
 * button calls it. Textured buttons and the colour selector draw themselves.
 */
@Mixin(PressableWidget.class)
public abstract class AureliaButtonMixin {

	@Inject(method = "renderButton(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
	private void atr$flatButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!AureliaUi.active() || (Object) this instanceof TexturedButtonWidget || (Object) this instanceof WidgetColorSelector) {
			return;
		}
		final PressableWidget widget = (PressableWidget) (Object) this;
		final int w = widget.getWidth();
		final int h = widget.getHeight();
		if (!widget.visible || w <= 0 || h <= 0) {
			return;
		}
		AureliaChrome.button(context, widget.getX(), widget.getY(), w, h, widget.isHovered(), widget.active, widget.isFocused());
		widget.drawMessage(context, MinecraftClient.getInstance().textRenderer, widget.active ? AureliaPalette.FG : AureliaPalette.FG_LIGHT);
		ci.cancel();
	}
}
