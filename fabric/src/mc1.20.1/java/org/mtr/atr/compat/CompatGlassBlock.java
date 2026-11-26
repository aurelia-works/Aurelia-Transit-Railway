package org.mtr.atr.compat;

import net.minecraft.block.AbstractGlassBlock;

/** Full-cube glass whose faces between neighbours of the same block are culled. Minecraft 1.20.4 names this class differently. */
public abstract class CompatGlassBlock extends AbstractGlassBlock {

	protected CompatGlassBlock(Settings settings) {
		super(settings);
	}
}
