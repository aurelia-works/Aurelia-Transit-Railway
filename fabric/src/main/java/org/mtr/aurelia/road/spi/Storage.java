/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.spi;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Stratos's files inside a world, under {@code <world>/aurelia_roads/} (spec mod-suite-v1 §5, world-data-format §2). Paths are
 * relative to that folder, with forward slashes, such as {@code undo/<operationId>/s.0.4.-2.bin}; {@link #checkPath}
 * refuses anything that could leave it. Writes are atomic: a temp file, flushed to disk, then a rename (world-data-format §5).
 *
 * <p><b>From F-2+</b> the journal's segments are the one exception: append-only {@link Log logs}, whose records carry
 * their own checksums, so a crash can only cut the last one short.
 */
public interface Storage {
    /**
     * An append-only file (F-2+: a journal segment), used by one thread at a time. Appends land at its end; {@link #flush}
     * puts everything appended so far on disk ({@code FileChannel.force}).
     */
    interface Log extends AutoCloseable {
        void append(byte[] data) throws IOException;

        void flush() throws IOException;

        @Override
        void close() throws IOException;
    }

    /**
     * Creates {@code path} (and its folders) and opens it for appending (F-2+). Fails if it exists already: a segment
     * cut short by a crash is never appended to.
     */
    Log openLog(String path) throws IOException;

    /** Writes {@code data} to {@code path}, replacing any old file, atomically. Creates folders as needed. */
    void write(String path, byte[] data) throws IOException;

    /** The bytes at {@code path}. */
    byte[] read(String path) throws IOException;

    /** Deletes the file or the whole folder at {@code path}; nothing happens if it doesn't exist. */
    void delete(String path) throws IOException;

    boolean exists(String path);

    /**
     * Every file under the folder {@code folder}, at any depth, as paths relative to the storage root (such as
     * {@code undo/<id>/1/s.0.4.0.bin}), sorted; empty if the folder doesn't exist. An empty {@code folder} lists
     * everything (task F-5).
     */
    List<String> list(String folder) throws IOException;

    /**
     * {@code path} if it is a safe relative path: lowercase-safe segments of letters, digits, {@code . _ - @} separated by
     * single forward slashes, never {@code .} or {@code ..}, never absolute, never a drive letter. Otherwise throws.
     */
    static String checkPath(String path) {
        if (path == null || path.isEmpty() || !Paths.SAFE.matcher(path).matches()) {
            throw new IllegalArgumentException("Refusing storage path '" + path + "': use relative paths of letters, digits, . _ - @ and single /");
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Refusing storage path '" + path + "': '.' and '..' aren't allowed");
            }
        }
        return path;
    }

    /** Holder for the path pattern (interfaces can't have private static fields). */
    final class Paths {
        static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._@-]+(/[A-Za-z0-9._@-]+)*");

        private Paths() {
        }
    }
}
