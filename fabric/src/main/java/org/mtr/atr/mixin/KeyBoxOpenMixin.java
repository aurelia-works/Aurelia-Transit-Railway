package org.mtr.atr.mixin;

import org.mtr.mod.packet.ClientPacketHelper;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Empty: ATR's driver key box screen was removed, so MTR's own screen opens. The class stays only because
 * {@code atr.mixins.json} still lists it.
 */
@Mixin(value = ClientPacketHelper.class, remap = false)
public abstract class KeyBoxOpenMixin {
}
