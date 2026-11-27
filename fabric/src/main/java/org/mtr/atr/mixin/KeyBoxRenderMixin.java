package org.mtr.atr.mixin;

import org.mtr.mod.render.RenderDriverKeyDispenser;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Empty: the keys hanging inside the driver key box were removed, so MTR's renderer draws the box. The class stays only
 * because {@code atr.mixins.json} still lists it.
 */
@Mixin(value = RenderDriverKeyDispenser.class, remap = false)
public abstract class KeyBoxRenderMixin {
}
