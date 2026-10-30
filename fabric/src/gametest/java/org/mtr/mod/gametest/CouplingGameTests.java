package org.mtr.mod.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class CouplingGameTests implements FabricGameTest {

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void couplerIsABlockItemAndEntity(TestContext context) {
		final Identifier id = new Identifier("mtr", "train_coupler");
		final Block block = Registries.BLOCK.get(id);
		if (block == net.minecraft.block.Blocks.AIR) {
			throw new GameTestException("mtr:train_coupler is not registered");
		}
		final Item item = Registries.ITEM.get(id);
		if (item == Items.AIR) {
			throw new GameTestException("mtr:train_coupler has no item");
		}
		if (!block.getDefaultState().isIn(BlockTags.PICKAXE_MINEABLE)) {
			throw new GameTestException("mtr:train_coupler is not pickaxe mineable");
		}
		final BlockPos pos = new BlockPos(1, 2, 1);
		context.setBlockState(pos, block.getDefaultState());
		final BlockEntity entity = context.getWorld().getBlockEntity(context.getAbsolutePos(pos));
		if (entity == null) {
			throw new GameTestException("mtr:train_coupler did not create a block entity");
		}
		context.complete();
	}
}
