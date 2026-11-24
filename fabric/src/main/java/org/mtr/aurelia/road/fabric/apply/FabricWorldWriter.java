/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.apply;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Clearable;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.mtr.aurelia.road.spi.ChunkXZ;
import org.mtr.aurelia.road.spi.WorldWriter;

/**
 * The core's {@link WorldWriter} for one server world (spec mod-suite-v1 §5). Blocks are set the way vanilla does,
 * with {@code NOTIFY_LISTENERS | FORCE_STATE | SKIP_DROPS} (§6.5): no neighbour updates and no drops,
 * while the game updates lighting and heightmaps, marks the chunk for saving and tells players. Server thread only.
 *
 * <p><b>From F-1+:</b>
 * <ul>
 *   <li>a block entity's data is its full NBT as SNBT ({@code createNbtWithIdentifyingData}), and is loaded back into the new
 *       block entity of a block put back, which is then sent to players;</li>
 *   <li>a block entity being replaced is emptied first ({@code Clearable.clear}, as {@code /setblock} does): Minecraft
 *       drops a container's items when its block goes, whatever the flags, and undo would then bring them back twice;</li>
 *   <li>only Minecraft's own block entities may be replaced (owner, 2026-10-08): other mods' may keep data elsewhere, or
 *       not empty cleanly;</li>
 *   <li>the outer shell's neighbour updates are Minecraft's {@code updateNeighbor}, without shape updates (owner).</li>
 * </ul>
 */
public final class FabricWorldWriter implements WorldWriter {
    /** Stratos's own chunk ticket, so its loaded chunks never mix with another mod's. */
    static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("stratos_apply", Comparator.comparingLong(ChunkPos::toLong));

    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
    private static final String MINECRAFT = "minecraft";

    private final ServerWorld level;
    private final Map<String, BlockState> parsed = new HashMap<>();
    private final Map<BlockState, String> serialised = new IdentityHashMap<>();
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();
    // The block read last (F-1+): the engine asks for a block's state, then whether it has a block entity, then writes
    // it; remembering it saves looking the block up twice more per write. Forgotten on every write and neighbour update,
    // and at the start of every tick (forgetReads), so it never outlives the moment it was read.
    private int lastX;
    private int lastY;
    private int lastZ;
    private BlockState lastState;

    public FabricWorldWriter(ServerWorld level) {
        this.level = level;
    }

    public ServerWorld level() {
        return this.level;
    }

    /**
     * Runs Minecraft's block-state parser and printer once, when the server starts. The first use loads their classes
     * (several milliseconds), which must not land inside a 5 ms apply slice.
     */
    static void warmUp() {
        try {
            BlockArgumentParser.stringifyBlockState(BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), "minecraft:stone", false).blockState());
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("Minecraft's block-state parser rejected minecraft:stone", e);
        }
    }

    @Override
    public int dataVersion() {
        return SharedConstants.getGameVersion().getSaveVersion().getId();
    }

    @Override
    public boolean isValidState(String state) {
        return parse(state) != null;
    }

    /** The block state for {@code state}, parsed once with Minecraft's own parser; null if it isn't one. */
    private BlockState parse(String state) {
        BlockState cached = this.parsed.get(state);
        if (cached != null || this.parsed.containsKey(state)) {
            return cached;
        }
        BlockState result;
        try {
            result = BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), state, false).blockState();
        } catch (CommandSyntaxException e) {
            result = null;
        }
        this.parsed.put(state, result);
        return result;
    }

    @Override
    public String blockState(int x, int y, int z) {
        // Always read afresh, and remembered for the calls about this block that follow.
        this.lastState = this.level.getBlockState(this.cursor.set(x, y, z));
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        // Block states are shared objects, so each one's text is worked out once (no new string per read).
        return this.serialised.computeIfAbsent(this.lastState, BlockArgumentParser::stringifyBlockState);
    }

    /** The block's state: the one just read if it is this block, else read now. */
    private BlockState known(int x, int y, int z) {
        if (this.lastState != null && x == this.lastX && y == this.lastY && z == this.lastZ) {
            return this.lastState;
        }
        return this.level.getBlockState(this.cursor.set(x, y, z));
    }

    /** Forgets the block read last (F-1+): the apply service calls it before each tick's work (and F-2+'s GameTest). */
    public void forgetReads() {
        this.lastState = null;
    }

    @Override
    public boolean hasBlockEntity(int x, int y, int z) {
        return known(x, y, z).hasBlockEntity();
    }

    @Override
    public String blockEntityData(int x, int y, int z) {
        BlockEntity entity = this.level.getBlockEntity(this.cursor.set(x, y, z));
        return entity == null ? null : entity.createNbtWithIdentifyingData().toString();
    }

    @Override
    public boolean canReplaceBlockEntity(int x, int y, int z) {
        BlockEntity entity = this.level.getBlockEntity(this.cursor.set(x, y, z));
        if (entity == null) {
            return true;
        }
        Identifier type = BlockEntityType.getId(entity.getType());
        Identifier block = Registries.BLOCK.getId(entity.getCachedState().getBlock());
        return type != null && MINECRAFT.equals(type.getNamespace()) && MINECRAFT.equals(block.getNamespace());
    }

    @Override
    public String setBlockState(int x, int y, int z, String state, String data) {
        BlockState blockState = parse(state);
        if (blockState == null) {
            throw new IllegalArgumentException("'" + state + "' is not a block state");
        }
        if (known(x, y, z).hasBlockEntity()) {
            BlockEntity old = this.level.getBlockEntity(this.cursor.set(x, y, z));
            if (old != null) {
                Clearable.clear(old); // nothing drops (F-1+)
            }
        }
        this.lastState = null; // the block changes now; a neighbour's reaction may change others
        this.level.setBlockState(this.cursor.set(x, y, z), blockState, FLAGS);
        if (!blockState.hasBlockEntity()) {
            return null;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockEntity entity = this.level.getBlockEntity(pos);
        if (entity == null) {
            return null;
        }
        if (data != null) {
            try {
                entity.readNbt(StringNbtReader.parse(data));
            } catch (CommandSyntaxException e) {
                throw new IllegalArgumentException("The saved data of the block at (" + x + ", " + y + ", " + z + ") can't be read: " + e.getMessage(), e);
            }
            entity.markDirty();
            this.level.updateListeners(pos, blockState, blockState, Block.NOTIFY_LISTENERS);
        }
        return entity.createNbtWithIdentifyingData().toString();
    }

    @Override
    public void neighbourChanged(int x, int y, int z, int fromX, int fromY, int fromZ) {
        if (this.level.isOutOfHeightLimit(y) || this.level.getChunkManager().getWorldChunk(ChunkSectionPos.getSectionCoord(x),
                ChunkSectionPos.getSectionCoord(z)) == null) {
            return;
        }
        BlockState state = this.level.getBlockState(this.cursor.set(x, y, z));
        if (state.isAir()) {
            return; // air doesn't react, and a road's neighbours are often air
        }
        this.lastState = null; // the neighbour's reaction may change blocks
        // Immutable positions: a block may keep the one it is given (a scheduled tick does).
        BlockPos from = new BlockPos(fromX, fromY, fromZ);
        this.level.updateNeighbor(new BlockPos(x, y, z), this.level.getBlockState(from).getBlock(), from);
    }

    @Override
    public void retainChunks(Collection<ChunkXZ> chunks) {
        for (ChunkXZ chunk : chunks) {
            ChunkPos pos = new ChunkPos(chunk.x(), chunk.z());
            // Distance 1: the chunk itself at block-ticking level, its neighbours fully loaded (for lighting).
            this.level.getChunkManager().addTicket(TICKET, pos, 1, pos);
        }
    }

    @Override
    public void releaseChunks(Collection<ChunkXZ> chunks) {
        for (ChunkXZ chunk : chunks) {
            ChunkPos pos = new ChunkPos(chunk.x(), chunk.z());
            this.level.getChunkManager().removeTicket(TICKET, pos, 1, pos);
        }
    }

    /** Stratos told Distant Horizons here; ATR ships no far-view support, so there is nothing to tell. */
    @Override
    public void blocksChanged(Collection<ChunkXZ> chunks) {
    }

    /**
     * Whether the chunk is loaded in full, never waiting for it. Not {@code isChunkLoaded}: that says yes as soon as a ticket
     * asks for the chunk, and the first block read then froze the server thread until it had loaded (found in F-2b:
     * 100–600 ms in the GameTests).
     */
    @Override
    public boolean isLoaded(ChunkXZ chunk) {
        return this.level.getChunkManager().getWorldChunk(chunk.x(), chunk.z()) != null;
    }
}
