package com.aureliatransit.architecture.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * The overhead-line mast. With {@code ballast=gravel} or {@code ballast=andesite} its foot stands in the half block of that
 * ballast that a raised shoulder is made of, so a mast on the shoulder keeps the shoulder unbroken instead of standing on top
 * of it (Jah 11-10). {@code ballast=true} is the gravel state the first fix wrote; it still draws gravel, so old worlds keep
 * their shoulder. Placed by hand it has no ballast.
 */
public class CatenaryMastBlock extends AxisShapedBlock {

	/** Which ballast, if any, the mast's foot stands in; {@link #LEGACY_GRAVEL} is the state the first gravel fix wrote. */
	public enum Ballast implements StringIdentifiable {
		NONE("none"),
		/** What the first gravel fix wrote; still draws gravel, so old worlds keep their shoulder. */
		LEGACY_GRAVEL("true"),
		GRAVEL("gravel"),
		ANDESITE("andesite");

		private final String name;

		Ballast(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return name;
		}
	}

	public static final EnumProperty<Ballast> BALLAST = EnumProperty.of("ballast", Ballast.class);
	private static final VoxelShape SLAB = Block.createCuboidShape(0, 0, 0, 16, 8, 16);

	public CatenaryMastBlock(Settings settings, VoxelShape shapeAlongX) {
		super(settings, shapeAlongX);
		setDefaultState(getDefaultState().with(BALLAST, Ballast.NONE));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(BALLAST);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		final VoxelShape mast = super.getOutlineShape(state, world, pos, context);
		return state.get(BALLAST) == Ballast.NONE ? mast : VoxelShapes.union(mast, SLAB);
	}
}
