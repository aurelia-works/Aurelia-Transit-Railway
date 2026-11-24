/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.journal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mtr.aurelia.road.spi.Storage;

/**
 * Every segment of a world's journal, read in order when the world opens (F-2+): each run's records gathered into a
 * {@link RunLog}, which runs have records in which segment, and what is wrong.
 * <ul>
 *   <li>A segment that ends <b>torn</b> stopped where a crash cut it off: the record cut short was never on disk
 *       before anything it covers changed, so it is ignored.</li>
 *   <li>A <b>damaged</b> segment (a bad record with a whole one after it) can't be trusted from there on: every run not
 *       yet ended by then is noted as a problem, and so is every later record whose run's {@code BEGIN} is missing.</li>
 * </ul>
 */
public final class JournalScan {
    private static final Pattern SEGMENT = Pattern.compile(Journal.FOLDER + "/(\\d{8})\\.log");

    private final Map<UUID, RunLog> runs = new LinkedHashMap<>();
    private final SortedMap<Integer, Set<UUID>> segments = new TreeMap<>();
    private final List<String> problems = new ArrayList<>();
    private int torn;

    private JournalScan() {
    }

    /** An empty journal: a world with none yet. */
    public static JournalScan empty() {
        return new JournalScan();
    }

    /** Reads every segment under {@code operations/journal/}, oldest first. Files with other names are left alone. */
    public static JournalScan read(Storage storage) throws IOException {
        JournalScan scan = new JournalScan();
        SortedMap<Integer, String> files = new TreeMap<>();
        for (String file : storage.list(Journal.FOLDER)) {
            Matcher matcher = SEGMENT.matcher(file);
            if (matcher.matches()) {
                files.put(Integer.parseInt(matcher.group(1)), file);
            }
        }
        for (Map.Entry<Integer, String> file : files.entrySet()) {
            JournalCodec.Segment segment = JournalCodec.read(storage.read(file.getValue()));
            scan.take(file.getKey(), segment);
            if (segment.ending() == JournalCodec.Ending.TORN) {
                scan.torn++;
            } else if (segment.ending() == JournalCodec.Ending.DAMAGED) {
                String where = "journal segment " + file.getValue() + " is damaged after byte " + segment.readUpTo();
                scan.problems.add(where);
                for (RunLog run : scan.runs.values()) {
                    if (run.end().isEmpty() && !run.retired()) {
                        run.problem(where + ", before this run's END");
                    }
                }
            }
        }
        return scan;
    }

    private void take(int number, JournalCodec.Segment segment) {
        Set<UUID> here = this.segments.computeIfAbsent(number, n -> new LinkedHashSet<>());
        for (JournalRecord record : segment.records()) {
            this.runs.computeIfAbsent(record.run(), RunLog::new).add(record);
            here.add(record.run());
        }
    }

    /** Every run with a record, in the order of its first record. */
    public List<RunLog> runs() {
        return List.copyOf(this.runs.values());
    }

    /** The runs not yet retired, in the order of their first record: those a repair must deal with. */
    public List<RunLog> unretired() {
        return this.runs.values().stream().filter(run -> !run.retired()).toList();
    }

    /** Per segment number, oldest first, the runs with records in it. */
    public SortedMap<Integer, Set<UUID>> segments() {
        SortedMap<Integer, Set<UUID>> copy = new TreeMap<>();
        this.segments.forEach((number, runs) -> copy.put(number, Collections.unmodifiableSet(new LinkedHashSet<>(runs))));
        return Collections.unmodifiableSortedMap(copy);
    }

    /** The highest segment number; -1 if there are none. */
    public int lastSegment() {
        return this.segments.isEmpty() ? -1 : this.segments.lastKey();
    }

    /** What is wrong with the journal itself (damaged segments). */
    public List<String> problems() {
        return List.copyOf(this.problems);
    }

    /** How many segments ended torn (cut off by a crash). */
    public int tornSegments() {
        return this.torn;
    }
}
