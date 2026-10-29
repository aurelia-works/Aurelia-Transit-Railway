package org.mtr.mod.item;

import net.minecraft.nbt.NbtCompound;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.Init;
import org.mtr.mod.generated.lang.TranslationProvider;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The bridge, tunnel and tunnel wall creators in one ({@link org.mtr.mod.data.RailBuilder}). Width, height and which
 * parts it builds are kept on the item and set from the Aurelia panel's Builder tab; shift-click saves the block it builds
 * the deck and walls with.
 */
public class ItemRailBuilder extends ItemNodeModifierSelectableBlockBase {

	public static final int MIN_WIDTH = 1;
	public static final int MAX_WIDTH = 15;
	public static final int MIN_HEIGHT = 2;
	public static final int MAX_HEIGHT = 10;
	private static final int DEFAULT_WIDTH = 5;
	private static final int DEFAULT_HEIGHT = 5;

	private static final String TAG_WIDTH = "rail_builder_width";
	private static final String TAG_HEIGHT = "rail_builder_height";
	private static final String TAG_DECK = "rail_builder_deck";
	private static final String TAG_CLEAR = "rail_builder_clear";
	private static final String TAG_WALLS = "rail_builder_walls";

	public ItemRailBuilder(ItemSettings itemSettings) {
		super(true, DEFAULT_HEIGHT, DEFAULT_WIDTH, itemSettings);
	}

	@Override
	protected void onConnect(Rail rail, ServerPlayerEntity serverPlayerEntity, ItemStack itemStack, int radius, int height) {
		final Settings settings = read(itemStack.data);
		// Without a saved block only the clearance can be built
		final BlockState blockState = getSavedState(itemStack);
		final boolean hasBlock = !blockState.isAir();
		Init.getRailActionModule(serverPlayerEntity.getServerWorld(), railActionModule -> railActionModule.markRailForBuilder(rail, serverPlayerEntity, radius, height, blockState, settings.deck && hasBlock, settings.clear, settings.walls && hasBlock));
	}

	@Override
	protected int getWidth(ItemStack itemStack) {
		return read(itemStack.data).width;
	}

	@Override
	protected int getHeight(ItemStack itemStack) {
		return read(itemStack.data).height;
	}

	@Override
	public void addTooltips(ItemStack stack, @Nullable World world, List<MutableText> tooltip, TooltipContext options) {
		final Settings settings = read(stack.data);
		// What a use builds: the deck and walls need a saved block
		final boolean hasBlock = !getSavedState(stack).isAir();
		tooltip.add(TranslationProvider.TOOLTIP_MTR_RAIL_BUILDER_PARTS.getMutableText(onOff(settings.deck && hasBlock), onOff(settings.clear), onOff(settings.walls && hasBlock)).formatted(TextFormatting.GRAY));
		tooltip.add(TranslationProvider.TOOLTIP_MTR_RAIL_BUILDER_SMART.getMutableText().formatted(TextFormatting.GRAY));
		tooltip.add(TranslationProvider.TOOLTIP_MTR_RAIL_BUILDER_PANEL.getMutableText().formatted(TextFormatting.GRAY).formatted(TextFormatting.ITALIC));
		super.addTooltips(stack, world, tooltip, options);
	}

	public static Settings read(net.minecraft.item.ItemStack itemStack) {
		final NbtCompound nbt = itemStack.getNbt();
		if (nbt == null || !nbt.contains(TAG_WIDTH)) {
			return new Settings(DEFAULT_WIDTH, DEFAULT_HEIGHT, true, true, true);
		}
		return new Settings(
				Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, nbt.getInt(TAG_WIDTH))),
				Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, nbt.getInt(TAG_HEIGHT))),
				nbt.getBoolean(TAG_DECK), nbt.getBoolean(TAG_CLEAR), nbt.getBoolean(TAG_WALLS)
		);
	}

	public static void write(net.minecraft.item.ItemStack itemStack, Settings settings) {
		final NbtCompound nbt = itemStack.getOrCreateNbt();
		nbt.putInt(TAG_WIDTH, Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, settings.width)));
		nbt.putInt(TAG_HEIGHT, Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, settings.height)));
		nbt.putBoolean(TAG_DECK, settings.deck);
		nbt.putBoolean(TAG_CLEAR, settings.clear);
		nbt.putBoolean(TAG_WALLS, settings.walls);
	}

	private static String onOff(boolean value) {
		return TextHelper.translatable(value ? "options.on" : "options.off").getString();
	}

	public record Settings(int width, int height, boolean deck, boolean clear, boolean walls) {
	}
}
