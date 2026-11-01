package org.mtr.atr.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The mapping library's {@code getMessage()} builds a new holder every call. The field is the text the widget already
 * has. The invoker is vanilla's label draw, which scrolls when the words are wider than the button.
 */
@Mixin(ClickableWidget.class)
public interface AureliaWidgetText {

	@Accessor("message")
	Text atr$message();

	@Invoker("drawScrollableText")
	static void atr$drawScrollableText(DrawContext context, TextRenderer textRenderer, Text text, int left, int top, int right, int bottom, int color) {
		throw new AssertionError();
	}
}
