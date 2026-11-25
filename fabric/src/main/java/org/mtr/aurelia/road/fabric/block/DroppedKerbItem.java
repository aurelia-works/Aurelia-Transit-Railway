/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.block;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * The dropped kerb's item (task B-1). The creative tab also offers it with red tactile paving: the game's own
 * {@code BlockStateTag} sets {@code colour=red} on what it places, and the item is named for it.
 */
public final class DroppedKerbItem extends BlockItem {
    private static final String BLOCK_STATE_TAG = "BlockStateTag";

    public DroppedKerbItem(DroppedKerbBlock block, Settings settings) {
        super(block, settings);
    }

    /** A dropped kerb that places with red tactile paving. */
    public static ItemStack red() {
        ItemStack stack = new ItemStack(StratosBlocks.DROPPED_KERB);
        NbtCompound state = new NbtCompound();
        state.putString(DroppedKerbBlock.COLOUR.getName(), TactileColour.RED.asString());
        stack.getOrCreateNbt().put(BLOCK_STATE_TAG, state);
        return stack;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        NbtCompound state = stack.getSubNbt(BLOCK_STATE_TAG);
        boolean red = state != null && TactileColour.RED.asString().equals(state.getString(DroppedKerbBlock.COLOUR.getName()));
        return red ? getTranslationKey() + ".red" : getTranslationKey();
    }
}
