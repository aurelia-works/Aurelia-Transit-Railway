package com.aureliatransit.architecture.block.glass;

import org.mtr.atr.compat.CompatGlassBlock;

/**
 * Full-cube architectural glass. Faces between adjacent blocks of the same type are culled like vanilla glass.
 */
public class ArchGlassBlock extends CompatGlassBlock {

	public ArchGlassBlock(Settings settings) {
		super(settings);
	}
}
