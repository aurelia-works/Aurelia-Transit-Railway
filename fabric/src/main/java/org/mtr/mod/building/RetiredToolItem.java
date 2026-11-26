package org.mtr.mod.building;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A retired building tool (Station Guide, Rail Stylus, Box Tunnel): kept so old stacks load, and says so in its tooltip. */
public final class RetiredToolItem extends Item {

	public RetiredToolItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.mtr.build_tool.retired").formatted(Formatting.GRAY));
	}
}
