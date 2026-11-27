package com.aureliatransit.architecture.station.world;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The retired Station Codex: kept registered so old inventories keep the stack. It does nothing and says so. */
public class StationCodexItem extends Item {

	public StationCodexItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.aurelia_transit_architecture.station_codex.retired").formatted(Formatting.GRAY));
	}
}
