/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.apply;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.mtr.aurelia.road.spi.Storage;

/**
 * The core's {@link Storage} for one world save: files under {@code <world>/aurelia_roads/} (world-data-format §2). Writes go
 * to a temp file in {@code tmp/}, are flushed to disk (F-5) and are then renamed over the target, so a file is never
 * half-written (§5). The journal's segments (F-2+) are the one exception: append-only {@link Log logs}, created new and
 * flushed with {@code FileChannel.force}.
 */
public final class FabricStorage implements Storage {
    private final Path root;

    public FabricStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** The storage of {@code server}'s world save. */
    public static FabricStorage of(MinecraftServer server) {
        return new FabricStorage(server.getSavePath(WorldSavePath.ROOT).resolve("aurelia_roads"));
    }

    public Path root() {
        return this.root;
    }

    /** The real path for {@code path}, which must stay inside {@code <world>/aurelia_roads/}. */
    private Path resolve(String path) {
        Path resolved = this.root.resolve(Storage.checkPath(path)).normalize();
        if (!resolved.startsWith(this.root) || resolved.equals(this.root)) {
            throw new IllegalArgumentException("Refusing storage path '" + path + "': it leaves " + this.root);
        }
        return resolved;
    }

    @Override
    public void write(String path, byte[] data) throws IOException {
        Path target = resolve(path);
        Files.createDirectories(target.getParent());
        Path tmp = this.root.resolve("tmp");
        Files.createDirectories(tmp);
        Path temp = Files.createTempFile(tmp, "write-", ".tmp");
        try {
            // Written and flushed to disk before the rename, so a crash leaves the old file or the new one (§5).
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public Log openLog(String path) throws IOException {
        Path target = resolve(path);
        Files.createDirectories(target.getParent());
        // CREATE_NEW: a segment cut short by a crash is never appended to.
        FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        return new Log() {
            @Override
            public void append(byte[] data) throws IOException {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }

            @Override
            public void flush() throws IOException {
                channel.force(true);
            }

            @Override
            public void close() throws IOException {
                channel.close();
            }
        };
    }

    @Override
    public byte[] read(String path) throws IOException {
        return Files.readAllBytes(resolve(path));
    }

    @Override
    public void delete(String path) throws IOException {
        Path target = resolve(path);
        if (!Files.exists(target)) {
            return;
        }
        if (!Files.isDirectory(target)) {
            Files.delete(target);
            return;
        }
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(target)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path file : paths) {
            Files.delete(file);
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(resolve(path));
    }

    @Override
    public List<String> list(String folder) throws IOException {
        Path target = folder.isEmpty() ? this.root : resolve(folder);
        if (!Files.isDirectory(target)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(target)) {
            return walk.filter(Files::isRegularFile).map(file -> this.root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/"))
                    .sorted().toList();
        }
    }
}
