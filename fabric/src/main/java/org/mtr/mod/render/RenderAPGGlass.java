package org.mtr.mod.render;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.World;
import org.mtr.mod.block.BlockAPGGlass;
import org.mtr.mod.block.IBlock;
import org.mtr.mod.client.DynamicTextureCache;
import org.mtr.mod.client.DynamicTextureLimits;
import org.mtr.mod.client.IDrawing;

public class RenderAPGGlass extends RenderRouteBase<BlockAPGGlass.BlockEntity> {

	private static final float COLOR_STRIP_START = 0.75F;
	private static final float COLOR_STRIP_END = 0.78125F;
	/** Route-map and station-name insets, in sixteenths of a block. */
	public static final float MAP_TOP = 8;
	public static final float MAP_BOTTOM = 4;
	public static final float MAP_SIDE = 8;

	public RenderAPGGlass(Argument dispatcher) {
		super(dispatcher, 4, MAP_TOP, MAP_BOTTOM, MAP_SIDE, false, 2, BlockAPGGlass.ARROW_DIRECTION);
	}

	/** Aspect ratio of a run of this many automatic platform gate blocks, matching {@link RenderRouteBase}. */
	public static float mapAspectRatio(int blocks) {
		return DynamicTextureLimits.runAspectRatio(blocks, MAP_TOP / 16F, MAP_BOTTOM / 16F, MAP_SIDE / 16F);
	}

	@Override
	protected RenderType getRenderType(World world, BlockPos pos, BlockState state) {
		if (IBlock.getStatePropertySafe(state, HALF) == DoubleBlockHalf.LOWER) {
			return RenderType.NONE;
		} else if ((Math.floorMod(pos.getX(), 8) < 4) == (Math.floorMod(pos.getZ(), 8) < 4)) {
			return RenderType.ARROW;
		} else {
			return RenderType.ROUTE;
		}
	}

	@Override
	protected void renderAdditional(StoredMatrixTransformations storedMatrixTransformations, long platformId, BlockState state, int leftBlocks, int rightBlocks, Direction facing, int color, int light) {
		if (IBlock.getStatePropertySafe(state, HALF) == DoubleBlockHalf.UPPER && IBlock.getStatePropertySafe(state, SIDE_EXTENDED) != EnumSide.SINGLE) {
			final boolean isLeft = isLeft(state);
			final boolean isRight = isRight(state);
			MainRenderer.scheduleRender(DynamicTextureCache.instance.getColorStrip(platformId).identifier, false, QueuedRenderLayer.EXTERIOR, (graphicsHolder, offset) -> {
				storedMatrixTransformations.transform(graphicsHolder, offset);
				IDrawing.drawTexture(graphicsHolder, isLeft ? sidePadding : 0, COLOR_STRIP_START, 0, isRight ? 1 - sidePadding : 1, COLOR_STRIP_END, 0, facing, color, light);
				IDrawing.drawTexture(graphicsHolder, isRight ? 1 - sidePadding : 1, COLOR_STRIP_START, 0.125F, isLeft ? sidePadding : 0, COLOR_STRIP_END, 0.125F, facing, color, light);
				graphicsHolder.pop();
			});

			final int blocks = leftBlocks + rightBlocks + 1;
			final float width = blocks - sidePadding * 2;
			MainRenderer.scheduleRender(DynamicTextureCache.instance.getSingleRowStationName(platformId, DynamicTextureLimits.runAspectRatio(blocks, topPadding, bottomPadding, sidePadding)).identifier, false, QueuedRenderLayer.EXTERIOR, (graphicsHolder, offset) -> {
				storedMatrixTransformations.transform(graphicsHolder, offset);
				IDrawing.drawTexture(graphicsHolder, 1 - (rightBlocks == 0 ? sidePadding : 0), topPadding, 0.125F, leftBlocks == 0 ? sidePadding : 0, 1 - bottomPadding, 0.125F, (rightBlocks - (rightBlocks == 0 ? 0 : sidePadding)) / width, 0, (width - leftBlocks + (leftBlocks == 0 ? 0 : sidePadding)) / width, 1, facing, color, light);
				graphicsHolder.pop();
			});
		}
	}
}
