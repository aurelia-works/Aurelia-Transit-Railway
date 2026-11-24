/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

/**
 * The footway's road-side block (task B-1; ADR-0044): a 125 mm kerb stone along the side it faces, flags behind, a
 * straight kerb or a corner ({@link KerbShape}). Roads get theirs from core, which works out each kerb's facing and
 * shape. Placed by hand it takes its shape from the kerbs beside it then, as stairs do, and keeps it: it never changes
 * when its neighbours do, so a road stays exactly as planned.
 */
public class KerbBlock extends EdgeBlock {
    public static final EnumProperty<KerbShape> SHAPE = EnumProperty.of("shape", KerbShape.class);

    public KerbBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(SHAPE, KerbShape.STRAIGHT));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(SHAPE);
    }

    @Override
    protected BlockState placed(ItemPlacementContext context) {
        return shaped(super.placed(context), context.getWorld(), context.getBlockPos());
    }

    /**
     * {@code state} turned into a corner where the kerbs beside it turn: a kerb behind facing a side next to this one
     * makes an outer corner, one in front an inner corner.
     */
    private BlockState shaped(BlockState state, BlockView world, BlockPos pos) {
        Direction facing = state.get(FACING);
        BlockState behind = world.getBlockState(pos.offset(facing.getOpposite()));
        if (behind.isOf(this)) {
            Direction other = behind.get(FACING);
            if (other == facing.rotateYClockwise()) {
                return state.with(SHAPE, KerbShape.OUTER);
            }
            if (other == facing.rotateYCounterclockwise()) {
                return state.with(FACING, other).with(SHAPE, KerbShape.OUTER);
            }
        }
        BlockState front = world.getBlockState(pos.offset(facing));
        if (front.isOf(this)) {
            Direction other = front.get(FACING);
            if (other == facing.rotateYClockwise()) {
                return state.with(SHAPE, KerbShape.INNER);
            }
            if (other == facing.rotateYCounterclockwise()) {
                return state.with(FACING, other).with(SHAPE, KerbShape.INNER);
            }
        }
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        if (mirror == BlockMirror.NONE || state.get(SHAPE) == KerbShape.STRAIGHT) {
            return super.mirror(state, mirror);
        }
        // A corner spans the side it faces and the next clockwise; mirrored, they run the other way round, so it faces
        // the mirror of that next side.
        return state.with(FACING, mirror.apply(state.get(FACING).rotateYClockwise()));
    }
}
