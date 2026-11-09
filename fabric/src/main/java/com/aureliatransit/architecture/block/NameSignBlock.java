package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.text.SignData;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A station name board. On its own it stands on two legs; placed against the side of a block (or planned with {@code wall=true})
 * it hangs flat on that wall instead: no legs, the panel moved to the back of its block so it sits flush with the wall.
 * <p>
 * Every operator's board is a look of one sign: only {@link #MAIN_ID} is in the creative tab, and the MTR brush editor switches
 * a placed row to another look or mount ({@link #restyle}). Each look keeps its own block id so old worlds and add-ons load.
 */
public class NameSignBlock extends TextSignBlock {
	public static final BooleanProperty WALL = BooleanProperty.of("wall");
	/** The look players pick up: the plain station name sign. */
	public static final String MAIN_ID = "station_name_sign";
	private static final List<NameSignBlock> LOOKS = new ArrayList<>();
	/** How far back the wall form's panel sits from the standing form's, in model pixels: the panel's back face meets the wall. */
	public static final float WALL_SHIFT = 6.25F;
	private final TextLayout wallLayout;
	private final Map<Direction, VoxelShape> wallOutlines;

	public NameSignBlock(Settings settings, TextLayout layout, VoxelShape northOutline) {
		super(settings, layout, northOutline);
		wallLayout = new TextLayout(layout.centerY(), layout.height(), layout.width(), layout.frontZ() + WALL_SHIFT, -1F, layout.joins(), layout.style(),
				layout.textInset());
		// the wall form is the standing outline without its legs (the boxes standing on the block's floor), moved back
		VoxelShape panel = VoxelShapes.empty();
		for (final Box box : northOutline.getBoundingBoxes()) {
			if (box.minY > 0) {
				panel = VoxelShapes.union(panel, VoxelShapes.cuboid(box.offset(0, 0, WALL_SHIFT / 16)));
			}
		}
		wallOutlines = Shapes.horizontalMap(panel);
		setDefaultState(getDefaultState().with(WALL, false));
		LOOKS.add(this);
	}

	/** Every look, in the order they were registered. */
	public static List<NameSignBlock> looks() {
		return Collections.unmodifiableList(LOOKS);
	}

	/** True for the looks kept out of the creative tab: every name sign but {@link #MAIN_ID}. */
	public boolean hiddenLook() {
		return !Registries.BLOCK.getId(this).getPath().equals(MAIN_ID);
	}

	/**
	 * Turns the row whose text {@code owner} holds into {@code look}, standing or on a wall: each block of the row is swapped for
	 * the look's block with the same facing, joins and nudge, and the text moves over to the new owner.
	 */
	public static void restyle(World world, BlockPos owner, NameSignBlock look, boolean wall) {
		final BlockState ownerState = world.getBlockState(owner);
		if (!(ownerState.getBlock() instanceof NameSignBlock current) || !(world.getBlockEntity(owner) instanceof TextSignBlockEntity sign)) {
			return;
		}
		final SignData data = sign.getData();
		final List<BlockPos> row = new ArrayList<>();
		BlockPos pos = owner;
		BlockState state = ownerState;
		for (int i = 0; i < MAX_ROW && state.isOf(current); i++) {
			row.add(pos);
			if (!state.get(RIGHT)) {
				break;
			}
			pos = pos.offset(localRight(state.get(FACING)));
			state = world.getBlockState(pos);
		}
		for (final BlockPos cell : row) {
			world.setBlockState(cell, copyState(world.getBlockState(cell), look.getDefaultState()).with(WALL, wall));
		}
		if (world.getBlockEntity(owner) instanceof TextSignBlockEntity moved) {
			moved.setData(data);
		}
	}

	private static BlockState copyState(BlockState from, BlockState to) {
		BlockState result = to;
		for (final Property<?> property : from.getProperties()) {
			if (result.contains(property)) {
				result = copy(from, result, property);
			}
		}
		return result;
	}

	private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
		return to.with(property, from.get(property));
	}

	@Override
	public TextLayout getLayout(BlockState state) {
		return state.contains(WALL) && state.get(WALL) ? wallLayout : getLayout();
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final BlockState state = super.getPlacementState(ctx);
		return state == null ? null : state.with(WALL, ctx.getSide() == state.get(FACING));
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(WALL) ? FreePlacement.outline(state, wallOutlines.get(state.get(FACING))) : super.getOutlineShape(state, world, pos, context);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(WALL) ? FreePlacement.collision(state, wallOutlines.get(state.get(FACING))) : super.getCollisionShape(state, world, pos, context);
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(WALL);
	}
}
