package org.mtr.aurelia.client;

import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.DirectionHelper;
import org.mtr.mod.InitClient;
import org.mtr.mod.Items;
import org.mtr.mod.block.BlockDriverKeyDispenser;
import org.mtr.mod.block.IBlock;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.item.ItemDriverKey;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;
import org.mtr.mod.render.StoredMatrixTransformations;

/**
 * The keys a driver key box hands out, hanging on the three hooks inside its glass cabinet, in the same order as the key
 * box screen: basic on the left, advanced in the middle, guard on the right. Drawn with the light the block gets.
 */
public final class KeyBoxWorldRenderer {

	private static final Identifier KEY = new Identifier("aurelia", "textures/gui/key_box_key.png");
	/** Hooks on the back wall, in blocks from the middle, seen from the front. */
	private static final float[] HOOKS = {-0.25F, 0, 0.25F};
	private static final float HOOK_Y = 0.6F;
	/** Just in front of the back wall's inner face (the wall is the last 1/16 of the block). */
	private static final float WALL_Z = 0.4375F - 0.01F;
	private static final float KEY_WIDTH = 0.2F;
	private static final float KEY_HEIGHT = KEY_WIDTH * 24 / 16;
	/** The ring's hole is 5 of the sprite's 24 pixels down. */
	private static final float RING = KEY_HEIGHT * 5 / 24;

	private KeyBoxWorldRenderer() {
	}

	/** Draws the hung keys and returns true, or returns false when the box is not in a depot (MTR then draws its red cross). */
	public static boolean render(BlockDriverKeyDispenser.BlockEntity entity, int light) {
		final World world = entity.getWorld2();
		if (world == null || InitClient.findDepot(entity.getPos2()) == null) {
			return false;
		}
		final boolean[] on = {entity.getDispenseBasicDriverKey(), entity.getDispenseAdvancedDriverKey(), entity.getDispenseGuardKey()};
		final int[] colors = {color(Items.BASIC_DRIVER_KEY.get()), color(Items.ADVANCED_DRIVER_KEY.get()), color(Items.GUARD_KEY.get())};
		if (!on[0] && !on[1] && !on[2]) {
			return true;
		}
		final BlockState state = world.getBlockState(entity.getPos2());
		final Direction facing = IBlock.getStatePropertySafe(state, DirectionHelper.FACING);
		final StoredMatrixTransformations transformations = new StoredMatrixTransformations(0.5 + entity.getPos2().getX(), entity.getPos2().getY(), 0.5 + entity.getPos2().getZ());
		transformations.add(graphicsHolder -> graphicsHolder.rotateYDegrees(-facing.asRotation()));
		MainRenderer.scheduleRender(KEY, false, QueuedRenderLayer.EXTERIOR, (graphicsHolder, offset) -> {
			transformations.transform(graphicsHolder, offset);
			for (int i = 0; i < HOOKS.length; i++) {
				if (on[i]) {
					// left to right and top to bottom as seen from the front, so the quad faces the viewer and the ring is at the hook
					IDrawing.drawTexture(graphicsHolder, HOOKS[i] - KEY_WIDTH / 2, HOOK_Y + RING, -WALL_Z, HOOKS[i] + KEY_WIDTH / 2, HOOK_Y + RING - KEY_HEIGHT, -WALL_Z,
							0, 0, 1, 1, facing, IGui.ARGB_BLACK | colors[i], light);
				}
			}
			graphicsHolder.pop();
		});
		return true;
	}

	private static int color(org.mtr.mapping.holder.Item item) {
		return ((ItemDriverKey) item.data).color;
	}
}
