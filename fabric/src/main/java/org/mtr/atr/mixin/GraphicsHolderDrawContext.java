package org.mtr.atr.mixin;

import net.minecraft.client.gui.DrawContext;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code drawContext} is package-private. The short-slider restyle needs it and must not allocate a {@code GuiDrawing}. */
@Mixin(value = GraphicsHolder.class, remap = false)
public interface GraphicsHolderDrawContext {

	@Accessor("drawContext")
	DrawContext atr$getDrawContext();
}
