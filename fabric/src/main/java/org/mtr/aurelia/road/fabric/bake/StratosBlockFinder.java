/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.bake;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.function.LongToIntFunction;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.storage.StorageIoWorker;
import org.mtr.aurelia.road.bake.Bake;
import org.mtr.aurelia.road.bake.BakeMap;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.spi.BlockFinder;
import org.mtr.aurelia.road.fabric.apply.ApplyService;
import org.mtr.aurelia.road.fabric.block.LayeredBlock;
import org.mtr.aurelia.road.fabric.block.StratosBlocks;
import org.mtr.aurelia.road.fabric.paint.PaintLines;
import org.mtr.aurelia.road.fabric.paint.PaintStore;

/**
 * Finds Stratos's blocks in a world without loading a chunk (task B-3: bake to vanilla), in the spirit of S15's offline
 * rename, but through the game's own chunk storage while the world is open.
 * <ul>
 *   <li><b>The whole world:</b> the dimension is saved first as {@code /save-all flush} saves it (every changed chunk,
 *       those being unloaded too, written to disk), so a chunk's saved data is as it is now unless it is loaded; then every
 *       region file's header lists the chunks saved. A chunk loaded now is read from memory, section by section, skipping
 *       any whose palette has no Stratos block ({@code maybeHas}), on the server thread, a few milliseconds a tick; any
 *       other is read from its saved data through the storage, off the server thread, unpacking only the sections whose
 *       palette names a Stratos block. Without the full save, a chunk just unloading could be read as it was saved before
 *       (found in B-3's harness run).</li>
 *   <li><b>A road's blocks:</b> its few chunks are kept loaded with a ticket of Stratos's own and read from memory.</li>
 * </ul>
 * A Stratos block is any in Stratos's namespace; asphalt with paint on it reads as its road line (ATR draws lines as
 * paint). The paint is copied when a search starts. Saved chunks are read at most {@link #IN_FLIGHT} at a time, so the game's
 * own chunk loading isn't kept waiting.
 */
public final class StratosBlockFinder implements BlockFinder {
    /** The longest a tick's slice of reading loaded chunks may run. */
    static final long TICK_BUDGET_NANOS = 2_000_000L;
    /** Saved chunks being read at once. */
    static final int IN_FLIGHT = 16;
    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    /** The ticket that keeps a road's chunks loaded while they are read: the chunk itself, in full. */
    static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("stratos_find", Comparator.comparingLong(ChunkPos::toLong));

    private final MinecraftServer server;
    private final ExecutorService reader;

    public StratosBlockFinder(MinecraftServer server) {
        this.server = server;
        this.reader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Stratos finder");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Stops the reading thread (the server is stopping). */
    public void close() {
        this.reader.shutdownNow();
    }

    @Override
    public Search find(String dimension, Optional<Set<Long>> cells) {
        for (ServerWorld level : this.server.getWorlds()) {
            if (ApplyService.dimension(level).equals(dimension)) {
                return new LevelSearch(level, cells);
            }
        }
        LevelSearch none = new LevelSearch(null, cells);
        none.fail("no dimension " + dimension);
        return none;
    }

    /** Whether {@code state} is one of Stratos's blocks. */
    static boolean isStratos(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("stratos");
    }

    /** The chunks saved in the region files of {@code regionFolder}, as {@link ChunkPos#toLong}s. */
    static Set<Long> savedChunks(Path regionFolder) throws IOException {
        Set<Long> chunks = new LinkedHashSet<>();
        if (!Files.isDirectory(regionFolder)) {
            return chunks;
        }
        List<Path> files;
        try (Stream<Path> listed = Files.list(regionFolder)) {
            files = listed.sorted().toList();
        }
        for (Path file : files) {
            Matcher name = REGION.matcher(file.getFileName().toString());
            if (!name.matches() || Files.size(file) < 4096) {
                continue;
            }
            int regionX = Integer.parseInt(name.group(1));
            int regionZ = Integer.parseInt(name.group(2));
            ByteBuffer header = ByteBuffer.allocate(4096);
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                while (header.hasRemaining() && channel.read(header) >= 0) {
                    // read the whole offsets table
                }
            }
            header.flip();
            for (int i = 0; i < 1024; i++) {
                if (header.getInt(i * 4) != 0) {
                    chunks.add(ChunkPos.toLong(regionX * 32 + (i & 31), regionZ * 32 + (i >> 5)));
                }
            }
        }
        return chunks;
    }

    /**
     * The Stratos blocks in a chunk's saved data {@code tag}, at chunk {@code x}, {@code z}: each section whose palette
     * names one is unpacked (the 1.18+ format: as few bits as the palette needs, at least 4, entries never spanning two
     * longs). Any thread.
     */
    public static List<Bake.Found> foundIn(NbtCompound tag, int chunkX, int chunkZ, LongToIntFunction paint) {
        List<Bake.Found> found = new ArrayList<>();
        NbtList sections = tag.getList("sections", NbtElement.COMPOUND_TYPE);
        for (int s = 0; s < sections.size(); s++) {
            NbtCompound section = sections.getCompound(s);
            NbtCompound states = section.getCompound("block_states");
            NbtList palette = states.getList("palette", NbtElement.COMPOUND_TYPE);
            BlockState[] stratos = new BlockState[palette.size()];
            boolean any = false;
            for (int i = 0; i < palette.size(); i++) {
                NbtCompound entry = palette.getCompound(i);
                if (BakeMap.isStratos(entry.getString("Name"))) {
                    stratos[i] = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), entry);
                    any = true;
                }
            }
            if (!any) {
                continue;
            }
            int baseX = ChunkSectionPos.getBlockCoord(chunkX);
            int baseY = ChunkSectionPos.getBlockCoord(section.getByte("Y"));
            int baseZ = ChunkSectionPos.getBlockCoord(chunkZ);
            long[] data = states.getLongArray("data");
            int bits = palette.size() <= 1 ? 0 : StrictMath.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            int perLong = bits == 0 ? 0 : 64 / bits;
            long mask = bits == 0 ? 0 : (1L << bits) - 1;
            for (int i = 0; i < 4096; i++) {
                int index = 0;
                if (bits > 0) {
                    int word = i / perLong;
                    if (word >= data.length) {
                        break; // a short array: the chunk's data is damaged; read what is there
                    }
                    index = (int) ((data[word] >>> ((i % perLong) * bits)) & mask);
                }
                if (index < stratos.length && stratos[index] != null) {
                    found.add(found(baseX + (i & 15), baseY + (i >> 8), baseZ + ((i >> 4) & 15), stratos[index], paint));
                }
            }
        }
        return found;
    }

    /** The Stratos blocks in loaded chunk {@code chunk}. Server thread. */
    public static List<Bake.Found> foundIn(WorldChunk chunk, LongToIntFunction paint) {
        List<Bake.Found> found = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        ChunkPos pos = chunk.getPos();
        for (int s = 0; s < sections.length; s++) {
            ChunkSection section = sections[s];
            if (section.isEmpty() || !section.hasAny(StratosBlockFinder::isStratos)) {
                continue;
            }
            int baseY = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(s));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (isStratos(state)) {
                            found.add(found(pos.getStartX() + x, baseY + y, pos.getStartZ() + z, state, paint));
                        }
                    }
                }
            }
        }
        return found;
    }

    /**
     * What the bake sees at (x, y, z): the block's state, or, for asphalt with paint on it ({@link PaintStore}), the road
     * line's state that the world writer stands for there, so the bake finds lines as it does in Stratos.
     */
    static Bake.Found found(int x, int y, int z, BlockState state, LongToIntFunction paint) {
        if (state.isOf(StratosBlocks.ASPHALT)) {
            int value = paint.applyAsInt(BlockPos.asLong(x, y, z));
            if (value != 0 && PaintLines.valid(value)) {
                return new Bake.Found(x, y, z, new PaintLines.Line(PaintLines.kind(value), PaintLines.chord(value), state.get(LayeredBlock.LAYERS)).state());
            }
        }
        return new Bake.Found(x, y, z, BlockArgumentParser.stringifyBlockState(state));
    }

    /** One dimension's search. Server thread, but for the reading thread's parts. */
    private final class LevelSearch implements Search {
        private final ServerWorld level;
        /** The dimension's paint, copied when the search starts: saved chunks are read off the server thread. */
        private final LongToIntFunction paint;
        private final Optional<Set<Long>> cells;
        private final Deque<Long> chunks = new ArrayDeque<>();
        private final List<Bake.Found> found = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger done = new AtomicInteger();
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicReference<String> failure = new AtomicReference<>();
        /** A road's chunks, held loaded until the search ends. */
        private final List<ChunkPos> held = new ArrayList<>();
        private volatile CompletableFuture<Set<Long>> listing;
        private int total;
        private boolean listed;
        private boolean cancelled;

        LevelSearch(ServerWorld level, Optional<Set<Long>> cells) {
            this.level = level;
            this.cells = cells;
            if (level == null) {
                this.paint = position -> 0;
                return;
            }
            this.paint = PaintStore.of(level).snapshot()::get;
            if (cells.isPresent()) {
                Set<Long> chunkKeys = new LinkedHashSet<>();
                for (long key : cells.get()) {
                    chunkKeys.add(ChunkPos.toLong(ChunkSectionPos.getSectionCoord(x(key)), ChunkSectionPos.getSectionCoord(z(key))));
                }
                for (long key : chunkKeys) {
                    ChunkPos pos = new ChunkPos(key);
                    level.getChunkManager().addTicket(TICKET, pos, 0, pos);
                    this.held.add(pos);
                }
                start(chunkKeys);
                return;
            }
            // The whole world: save everything as /save-all flush does, then list what the region files hold.
            level.getChunkManager().save(true);
            StorageIoWorker storage = (StorageIoWorker) level.getChunkManager().threadedAnvilChunkStorage.getWorker();
            Path regions = DimensionType.getSaveDirectory(level.getRegistryKey(), level.getServer().getSavePath(WorldSavePath.ROOT)).resolve("region");
            this.listing = storage.completeAll(false).thenApplyAsync(nothing -> {
                try {
                    return savedChunks(regions);
                } catch (IOException e) {
                    throw new IllegalStateException("couldn't read the region files in " + regions + ": " + e, e);
                }
            }, StratosBlockFinder.this.reader);
        }

        private void start(Set<Long> chunkKeys) {
            this.chunks.addAll(chunkKeys);
            this.total = chunkKeys.size();
            this.listed = true;
        }

        void fail(String why) {
            this.failure.compareAndSet(null, why);
        }

        @Override
        public void tick() {
            if (finished()) {
                return;
            }
            if (!this.listed) {
                CompletableFuture<Set<Long>> listing = this.listing;
                if (!listing.isDone()) {
                    return;
                }
                try {
                    start(listing.join());
                } catch (RuntimeException e) {
                    fail(String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                    return;
                }
            }
            if (!this.held.isEmpty()) {
                readHeld();
                return;
            }
            long start = System.nanoTime();
            while (!this.chunks.isEmpty() && System.nanoTime() - start < TICK_BUDGET_NANOS && this.failure.get() == null) {
                long key = this.chunks.peekFirst();
                int chunkX = ChunkPos.getPackedX(key);
                int chunkZ = ChunkPos.getPackedZ(key);
                WorldChunk loaded = this.level.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (loaded != null) {
                    this.chunks.removeFirst();
                    keep(foundIn(loaded, this.paint));
                    this.done.incrementAndGet();
                    continue;
                }
                if (this.inFlight.get() >= IN_FLIGHT) {
                    break; // the next tick carries on
                }
                this.chunks.removeFirst();
                this.inFlight.incrementAndGet();
                this.level.getChunkManager().threadedAnvilChunkStorage.getNbt(new ChunkPos(chunkX, chunkZ)).thenAcceptAsync(tag -> {
                    tag.ifPresent(data -> keep(foundIn(data, chunkX, chunkZ, this.paint)));
                    this.done.incrementAndGet();
                }, StratosBlockFinder.this.reader).whenComplete((nothing, error) -> {
                    if (error != null) {
                        fail("chunk " + chunkX + ", " + chunkZ + " couldn't be read: " + error);
                    }
                    this.inFlight.decrementAndGet();
                });
            }
        }

        /** A road's chunks: each read from memory once its ticket has loaded it; the tickets go once all are read. */
        private void readHeld() {
            long start = System.nanoTime();
            int waiting = this.chunks.size();
            for (int i = 0; i < waiting && System.nanoTime() - start < TICK_BUDGET_NANOS; i++) {
                long key = this.chunks.removeFirst();
                WorldChunk loaded = this.level.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
                if (loaded == null) {
                    this.chunks.addLast(key); // still loading
                    continue;
                }
                keep(foundIn(loaded, this.paint));
                this.done.incrementAndGet();
            }
            if (this.chunks.isEmpty()) {
                release();
            }
        }

        private void release() {
            for (ChunkPos pos : this.held) {
                this.level.getChunkManager().removeTicket(TICKET, pos, 0, pos);
            }
            this.held.clear();
        }

        /** Keeps {@code blocks}, only those among the cells when there are some. Any thread. */
        private void keep(List<Bake.Found> blocks) {
            for (Bake.Found block : blocks) {
                if (this.cells.isEmpty() || this.cells.get().contains(BlockChange.positionKey(block.x(), block.y(), block.z()))) {
                    this.found.add(block);
                }
            }
        }

        @Override
        public int done() {
            return StrictMath.min(this.done.get(), this.total);
        }

        @Override
        public int total() {
            return this.total;
        }

        @Override
        public boolean finished() {
            if (this.cancelled || this.failure.get() != null) {
                return this.inFlight.get() == 0;
            }
            return this.listed && this.chunks.isEmpty() && this.inFlight.get() == 0;
        }

        @Override
        public Optional<String> failure() {
            return this.cancelled ? Optional.of("stopped") : Optional.ofNullable(this.failure.get());
        }

        @Override
        public List<Bake.Found> found() {
            synchronized (this.found) {
                return List.copyOf(new ArrayList<>(this.found));
            }
        }

        @Override
        public void cancel() {
            this.cancelled = true;
            this.chunks.clear();
            release();
            if (this.listing != null) {
                this.listing.cancel(false);
            }
        }
    }

    /** A {@link BlockChange#positionKey}'s x (26 bits, signed). */
    static int x(long key) {
        return (int) (key >> 38);
    }

    /** A {@link BlockChange#positionKey}'s z (26 bits, signed). */
    static int z(long key) {
        return (int) (key << 26 >> 38);
    }
}
