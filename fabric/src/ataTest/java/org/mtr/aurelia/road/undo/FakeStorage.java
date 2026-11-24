/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.mtr.aurelia.road.spi.Storage;

/**
 * Storage in memory, for tests. Can be told to fail writes. Safe for two threads at once, as the real storage must be
 * (F-2b: the helper writes undo images while the server thread saves).
 *
 * <p>From F-2+ it has append-only logs (the journal's segments), whose appends count as written only once flushed:
 * {@link #crash} drops what was appended since each log's last flush, as a crash would.
 */
public final class FakeStorage implements Storage {
    public final Map<String, byte[]> files = Collections.synchronizedSortedMap(new TreeMap<>());
    public volatile boolean failWrites;
    public int writes;
    /** Every path written, in order. */
    public final List<String> written = Collections.synchronizedList(new ArrayList<>());
    public int reads;
    public int lists;
    public int deletes;
    /** Flushes of logs (F-2+). */
    public int logFlushes;
    /** Per log, how many of its bytes were flushed (F-2+). */
    private final Map<String, Integer> flushed = new HashMap<>();

    @Override
    public synchronized void write(String path, byte[] data) throws IOException {
        Storage.checkPath(path);
        if (this.failWrites) {
            throw new IOException("disk full (test)");
        }
        this.writes++;
        this.written.add(path);
        this.files.put(path, data.clone());
    }

    @Override
    public synchronized Log openLog(String path) throws IOException {
        Storage.checkPath(path);
        if (this.failWrites) {
            throw new IOException("disk full (test)");
        }
        if (this.files.containsKey(path)) {
            throw new FileAlreadyExistsException(path);
        }
        this.files.put(path, new byte[0]);
        this.flushed.put(path, 0);
        return new Log() {
            private boolean closed;

            @Override
            public void append(byte[] data) throws IOException {
                synchronized (FakeStorage.this) {
                    check();
                    byte[] old = FakeStorage.this.files.getOrDefault(path, new byte[0]);
                    byte[] now = Arrays.copyOf(old, old.length + data.length);
                    System.arraycopy(data, 0, now, old.length, data.length);
                    FakeStorage.this.files.put(path, now);
                }
            }

            @Override
            public void flush() throws IOException {
                synchronized (FakeStorage.this) {
                    check();
                    FakeStorage.this.logFlushes++;
                    FakeStorage.this.flushed.put(path, FakeStorage.this.files.getOrDefault(path, new byte[0]).length);
                }
            }

            @Override
            public void close() {
                this.closed = true;
            }

            private void check() throws IOException {
                if (this.closed) {
                    throw new IOException("closed");
                }
                if (FakeStorage.this.failWrites) {
                    throw new IOException("disk full (test)");
                }
            }
        };
    }

    /**
     * A crash (F-2+): every log loses what was appended since it was last flushed, but for its first {@code keep}
     * bytes of that (a record cut short).
     */
    public synchronized void crash(int keep) {
        for (Map.Entry<String, Integer> log : this.flushed.entrySet()) {
            byte[] bytes = this.files.get(log.getKey());
            if (bytes != null) {
                int length = StrictMath.min(bytes.length, log.getValue() + keep);
                this.files.put(log.getKey(), Arrays.copyOf(bytes, length));
            }
        }
        this.flushed.clear();
    }

    @Override
    public synchronized byte[] read(String path) throws IOException {
        Storage.checkPath(path);
        this.reads++;
        byte[] data = this.files.get(path);
        if (data == null) {
            throw new NoSuchFileException(path);
        }
        return data.clone();
    }

    @Override
    public synchronized void delete(String path) {
        Storage.checkPath(path);
        this.deletes++;
        synchronized (this.files) {
            this.files.keySet().removeIf(file -> file.equals(path) || file.startsWith(path + "/"));
        }
        this.flushed.keySet().removeIf(file -> file.equals(path) || file.startsWith(path + "/"));
    }

    @Override
    public synchronized boolean exists(String path) {
        Storage.checkPath(path);
        synchronized (this.files) {
            return this.files.keySet().stream().anyMatch(file -> file.equals(path) || file.startsWith(path + "/"));
        }
    }

    @Override
    public synchronized List<String> list(String folder) {
        if (!folder.isEmpty()) {
            Storage.checkPath(folder);
        }
        this.lists++;
        synchronized (this.files) {
            return this.files.keySet().stream().filter(file -> folder.isEmpty() || file.startsWith(folder + "/")).sorted().toList();
        }
    }

    /** How many files are under {@code folder}. */
    public long count(String folder) {
        synchronized (this.files) {
            return this.files.keySet().stream().filter(file -> file.startsWith(folder + "/")).count();
        }
    }
}
