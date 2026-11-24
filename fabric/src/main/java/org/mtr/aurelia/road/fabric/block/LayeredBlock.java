/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.mtr.aurelia.road.fabric.net.EditorNetworking;

/**
 * A road surface in eighth-block layers (task B-1; ADR-0044): {@code layers} 1 to 8, one layer 125 mm, the same texture
 * at every height and the collision at the drawn height (unlike snow's, a layer lower). Placed like snow: a new block is
 * one layer, and using the same item on its top adds one, up to a full block; it drops one item per layer.
 *
 * <p>Every Stratos block is one, so its {@link #onBlockAdded} turns the world's mark for Stratos blocks on (UI-1c),
 * however the block was placed.
 */
public class LayeredBlock extends Block {
    public static final IntProperty LAYERS = Properties.LAYERS;
    private static final VoxelShape[] SHAPES = new VoxelShape[9];

    static {
        for (int layers = 1; layers <= 8; layers++) {
            SHAPES[layers] = Block.createCuboidShape(0, 0, 0, 16, 2 * layers, 16);
        }
    }

    public LayeredBlock(Settings settings) {
        super(settings);
        setDefaultState(this.stateManager.getDefaultState().with(LAYERS, 8));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient && world.getServer() != null) {
            EditorNetworking.stratosBlockPlaced(world.getServer());
        }
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    /** A new block placed by {@code context}, before its layer count (one) is set: what a subclass turns to face. */
    protected BlockState placed(ItemPlacementContext context) {
        return getDefaultState();
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        BlockState here = context.getWorld().getBlockState(context.getBlockPos());
        if (here.isOf(this)) {
            return here.with(LAYERS, StrictMath.min(8, here.get(LAYERS) + 1));
        }
        return placed(context).with(LAYERS, 1);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        if (!context.getStack().isOf(asItem()) || state.get(LAYERS) >= 8) {
            return false;
        }
        // Clicked on: only its top takes another layer; placed against a neighbour, it fills in like snow.
        return !context.canReplaceExisting() || context.getSide() == Direction.UP;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(LAYERS)];
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(LAYERS)];
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getSidesShape(BlockState state, BlockView world, BlockPos pos) {
        return SHAPES[state.get(LAYERS)];
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(LAYERS)];
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasSidedTransparency(BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canPathfindThrough(BlockState state, BlockView world, BlockPos pos, NavigationType type) {
        // As snow: a mob walks over half a block or less.
        return type == NavigationType.LAND && state.get(LAYERS) < 5;
    }
}
