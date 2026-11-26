package org.mtr.atr.compat;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.TransparentBlock;

/** The Minecraft 1.20.4 side of {@code src/mc1.20.1/java}'s {@code CompatGlassBlock}: 1.20.1's AbstractGlassBlock is TransparentBlock here. */
public abstract class CompatGlassBlock extends TransparentBlock {

	protected CompatGlassBlock(Settings settings) {
		super(settings);
	}

	/** Minecraft reads block codecs only for its own blocks; this one stands for the registered block itself. */
	@Override
	protected MapCodec<? extends TransparentBlock> getCodec() {
		return MapCodec.unit(this);
	}
}
