package org.mtr.aurelia.crossing;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * A barrier's arm, for drawing only: the angle it stands at, swung towards the block's {@code lowered} state over
 * {@link #SWING_MILLIS}. Nothing is saved; a barrier seen for the first time shows its arm where its state puts it.
 */
public final class LevelCrossingBarrierBlockEntity extends BlockEntity {

	/** How long the arm takes to swing between up and down. */
	static final long SWING_MILLIS = 4000;

	private float angle = -1;
	private long lastMillis;

	public LevelCrossingBarrierBlockEntity(BlockPos pos, BlockState state) {
		super(LevelCrossings.BARRIER_ENTITY, pos, state);
	}

	/** The arm's angle above the road, 0 down to 90 up, moved on to {@code nowMillis}. */
	public float angle(boolean lowered, long nowMillis) {
		final float target = lowered ? 0 : 90;
		if (angle < 0) {
			angle = target;
		} else {
			final float step = Math.max(0, nowMillis - lastMillis) * 90F / SWING_MILLIS;
			angle = angle < target ? Math.min(target, angle + step) : Math.max(target, angle - step);
		}
		lastMillis = nowMillis;
		return angle;
	}
}
