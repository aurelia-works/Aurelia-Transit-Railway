package org.mtr.atr.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.client.ui.AureliaUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Repaints the text-field border after MTR has drawn the field, so the cursor, selection and suggestion stay as they are.
 */
@Mixin(TextFieldWidget.class)
public abstract class AureliaFieldMixin {

	@Inject(method = "renderButton(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("RETURN"))
	private void atr$fieldBorder(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!AureliaUi.active()) {
			return;
		}
		final ClickableWidget widget = (ClickableWidget) (Object) this;
		if (!widget.visible || widget.getWidth() <= 0 || widget.getHeight() <= 0) {
			return;
		}
		final int color = widget.isFocused() ? AureliaPalette.ACCENT : AureliaPalette.BORDER;
		AureliaChrome.frame(context, widget.getX() - 1, widget.getY() - 1, widget.getWidth() + 2, widget.getHeight() + 2, color);
	}
}
