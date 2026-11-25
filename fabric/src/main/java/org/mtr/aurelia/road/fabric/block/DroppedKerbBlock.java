/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;

/**
 * A dropped kerb (task B-1): the kerb stone flush with the road along the side it faces, tactile paving behind it, buff
 * or red ({@code colour}). Roads will place them at crossings; until then they are placed by hand.
 */
public class DroppedKerbBlock extends EdgeBlock {
    public static final EnumProperty<TactileColour> COLOUR = EnumProperty.of("colour", TactileColour.class);

    public DroppedKerbBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(COLOUR, TactileColour.BUFF));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(COLOUR);
    }
}
