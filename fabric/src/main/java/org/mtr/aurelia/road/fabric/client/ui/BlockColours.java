/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.ui;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import org.mtr.aurelia.road.fabric.paint.PaintLines;

/**
 * Block colours for the hub's pictures (task UI-1a): a palette's blocks in the colour Minecraft gives them on maps, read
 * as the preview reads them ({@code PreviewRenderer}).
 */
public final class BlockColours {
    /** A block with no map colour (glass, say): a light grey. */
    public static final int NO_COLOUR = 0xA0A0A0;
    /** A state this game can't read: impossible to miss. */
    public static final int UNKNOWN = 0xFF00FF;

    private static final Map<String, Integer> COLOURS = new ConcurrentHashMap<>();

    private BlockColours() {
    }

    /** The map colour (RGB) of a block state string such as {@code stratos:asphalt[layers=8]}, parsed once. */
    public static int map(String state) {
        return COLOURS.computeIfAbsent(state, s -> {
            PaintLines.Line line = PaintLines.parse(s);
            if (line != null) {
                return line.kind().paint().equals("white") ? 0xFFFFFF : 0xE5B334; // a road line: its paint
            }
            try {
                BlockState blockState = BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), s, false).blockState();
                int rgb = blockState.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).color;
                return rgb == 0 ? NO_COLOUR : rgb;
            } catch (CommandSyntaxException e) {
                return UNKNOWN;
            }
        });
    }
}
