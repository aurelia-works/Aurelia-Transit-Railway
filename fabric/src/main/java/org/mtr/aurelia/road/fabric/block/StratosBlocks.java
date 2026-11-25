/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.block;

import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.Instrument;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.mtr.aurelia.road.fabric.StratosMod;

/**
 * Stratos's own road blocks (task B-1; ADR-0044), named by material, not country: the UK set the Stratos UK palette
 * builds with. Each has an item and a place in the Stratos creative tab; they are mined with a pickaxe like stone, and
 * sound like it. Ids stay in Stratos's {@code stratos:} namespace, so its roads and palettes name them unchanged.
 *
 * <p>Map colours (which the preview draws with too) match the textures' average colours: asphalt and gullies dark grey,
 * flags and kerbs light grey, tactile paving buff (white terracotta's) or red.
 */
public final class StratosBlocks {
    public static final LayeredBlock ASPHALT = new LayeredBlock(stone(MapColor.GRAY));
    public static final LayeredBlock FOOTWAY_FLAGS = new LayeredBlock(stone(MapColor.LIGHT_GRAY));
    public static final LayeredBlock FOOTWAY_ASPHALT = new LayeredBlock(stone(MapColor.GRAY));
    public static final KerbBlock KERB = new KerbBlock(stone(MapColor.LIGHT_GRAY));
    public static final DroppedKerbBlock DROPPED_KERB = new DroppedKerbBlock(stone(StratosBlocks::droppedKerbColour));
    public static final EdgeBlock GULLY = new EdgeBlock(stone(MapColor.GRAY));

    /** The blocks by name, in the creative tab's order. */
    private static final List<Named> BLOCKS = List.of(
            new Named("asphalt", ASPHALT),
            new Named("footway_flags", FOOTWAY_FLAGS),
            new Named("footway_asphalt", FOOTWAY_ASPHALT),
            new Named("kerb", KERB),
            new Named("dropped_kerb", DROPPED_KERB),
            new Named("gully", GULLY));

    private record Named(String name, Block block) {
    }

    private StratosBlocks() {
    }

    /** Registers the blocks, their items and the Stratos creative tab. Called once, before the palettes are read. */
    public static void register() {
        for (Named named : BLOCKS) {
            Identifier id = new Identifier(StratosMod.MOD_ID, named.name());
            Registry.register(Registries.BLOCK, id, named.block());
            Item item = named.block() == DROPPED_KERB ? new DroppedKerbItem(DROPPED_KERB, new Item.Settings()) : new BlockItem(named.block(), new Item.Settings());
            Registry.register(Registries.ITEM, id, item);
        }
        ItemGroup tab = FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.stratos.road_blocks"))
                .icon(() -> new ItemStack(ASPHALT))
                .entries((context, entries) -> {
                    for (Named named : BLOCKS) {
                        entries.add(named.block());
                        if (named.block() == DROPPED_KERB) {
                            entries.add(DroppedKerbItem.red());
                        }
                    }
                })
                .build();
        Registry.register(Registries.ITEM_GROUP, new Identifier(StratosMod.MOD_ID, "road_blocks"), tab);
    }

    private static MapColor droppedKerbColour(BlockState state) {
        return state.get(DroppedKerbBlock.COLOUR) == TactileColour.RED ? MapColor.RED : MapColor.TERRACOTTA_WHITE;
    }

    private static AbstractBlock.Settings stone(MapColor colour) {
        return stone(state -> colour);
    }

    /** Like stone: a pickaxe to drop anything, its strength and sounds. */
    private static AbstractBlock.Settings stone(Function<BlockState, MapColor> colour) {
        return AbstractBlock.Settings.create()
                .mapColor(colour)
                .instrument(Instrument.BASEDRUM)
                .requiresTool()
                .strength(1.5F, 6.0F)
                .sounds(BlockSoundGroup.STONE);
    }
}
