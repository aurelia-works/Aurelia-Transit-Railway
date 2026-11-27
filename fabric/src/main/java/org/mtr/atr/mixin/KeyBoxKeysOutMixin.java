package org.mtr.atr.mixin;

import org.mtr.mod.block.BlockDriverKeyDispenser;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Empty: the driver key box no longer remembers which keys are out. Boxes saved with the old {@code atr_key_back_at_*}
 * keys still load; MTR ignores them. The class stays only because {@code atr.mixins.json} still lists it.
 */
@Mixin(value = BlockDriverKeyDispenser.BlockEntity.class, remap = false)
public abstract class KeyBoxKeysOutMixin {
}
