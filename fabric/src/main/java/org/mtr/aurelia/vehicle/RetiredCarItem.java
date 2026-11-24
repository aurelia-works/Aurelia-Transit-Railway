package org.mtr.aurelia.vehicle;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The retired {@code mtr:road_car}: kept so old stacks load, and says so in its tooltip. */
public final class RetiredCarItem extends Item {

	public RetiredCarItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.mtr.road_car.retired").formatted(Formatting.GRAY));
	}
}
