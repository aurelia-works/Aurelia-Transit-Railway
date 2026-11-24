/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.journal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;

/**
 * Everything the journal holds about one run, gathered from its records in order (F-2+): its {@code BEGIN}, its
 * pre-images (a later part's block replaces an earlier one at the same position: noted again late, F-2+), which blocks
 * it wrote, its {@code END}, its track, and what a repair or retiring did with it. Built by {@link JournalScan}.
 */
public final class RunLog {
    private final UUID run;
    private JournalRecord.Begin begin;
    private final Map<BlockPlan.SectionPos, Map<Long, BlockChange>> preimages = new LinkedHashMap<>();
    private final Map<BlockPlan.SectionPos, long[]> written = new LinkedHashMap<>();
    private JournalRecord.End end;
    private final List<JournalRecord.Track> asked = new ArrayList<>();
    private final List<JournalRecord.TrackDone> answered = new ArrayList<>();
    private JournalRecord.Recovered recovered;
    private boolean retired;
    private int dataVersion = -1;
    private final List<String> problems = new ArrayList<>();

    RunLog(UUID run) {
        this.run = run;
    }

    /** Takes one of the run's records, in journal order. */
    void add(JournalRecord record) {
        if (record instanceof JournalRecord.Begin b) {
            if (this.begin != null) {
                this.problems.add("it began twice");
            }
            this.begin = b;
        } else if (record instanceof JournalRecord.Preimage p) {
            if (this.dataVersion >= 0 && this.dataVersion != p.dataVersion()) {
                this.problems.add("its pre-images have two DataVersions, " + this.dataVersion + " and " + p.dataVersion());
            }
            this.dataVersion = p.dataVersion();
            Map<Long, BlockChange> section = this.preimages.computeIfAbsent(p.section(), s -> new LinkedHashMap<>());
            for (BlockChange change : p.changes()) {
                section.put(change.positionKey(), change);
            }
        } else if (record instanceof JournalRecord.SectionDone d) {
            long[] bits = this.written.computeIfAbsent(d.section(), s -> new long[64]);
            long[] more = d.written();
            for (int i = 0; i < 64; i++) {
                bits[i] |= more[i];
            }
        } else if (record instanceof JournalRecord.End e) {
            this.end = e;
        } else if (record instanceof JournalRecord.Track t) {
            this.asked.add(t);
        } else if (record instanceof JournalRecord.TrackDone t) {
            this.answered.add(t);
        } else if (record instanceof JournalRecord.Recovered r) {
            this.recovered = r;
        } else if (record instanceof JournalRecord.Retired) {
            this.retired = true;
        }
    }

    /** Notes why the run can't be fully trusted (damage in the journal). */
    void problem(String problem) {
        this.problems.add(problem);
    }

    public UUID run() {
        return this.run;
    }

    /** Its {@code BEGIN}; empty if that record is lost (damage, or deleted with a retired run's first segment). */
    public Optional<JournalRecord.Begin> begin() {
        return Optional.ofNullable(this.begin);
    }

    /** Its pre-images by section, each block once (the latest noted), in the order first seen. */
    public Map<BlockPlan.SectionPos, List<BlockChange>> preimages() {
        Map<BlockPlan.SectionPos, List<BlockChange>> all = new LinkedHashMap<>();
        this.preimages.forEach((section, changes) -> all.put(section, List.copyOf(changes.values())));
        return Collections.unmodifiableMap(all);
    }

    /** Which blocks of each section it wrote ({@code SECTION_DONE}), where it said so. */
    public Map<BlockPlan.SectionPos, long[]> written() {
        Map<BlockPlan.SectionPos, long[]> all = new LinkedHashMap<>();
        this.written.forEach((section, bits) -> all.put(section, bits.clone()));
        return Collections.unmodifiableMap(all);
    }

    /** Its {@code END}, if its job ended before the crash. */
    public Optional<JournalRecord.End> end() {
        return Optional.ofNullable(this.end);
    }

    /** What it asked the side-effect writer, in order. */
    public List<JournalRecord.Track> asked() {
        return List.copyOf(this.asked);
    }

    /** The writer's answers, in order. */
    public List<JournalRecord.TrackDone> answered() {
        return List.copyOf(this.answered);
    }

    /** What an earlier repair did with it, if one did. */
    public Optional<JournalRecord.Recovered> recovered() {
        return Optional.ofNullable(this.recovered);
    }

    public boolean retired() {
        return this.retired;
    }

    /** Its pre-images' DataVersion; -1 if it has none. */
    public int dataVersion() {
        return this.dataVersion;
    }

    /** Why it can't be fully trusted; empty if nothing is wrong. */
    public List<String> problems() {
        List<String> all = new ArrayList<>(this.problems);
        if (this.begin == null) {
            all.add("its BEGIN record is missing");
        }
        return all;
    }

    /** Whether the bit of the block at local index {@code local} ({@code y << 8 | z << 4 | x}) is set in {@code bits}. */
    static boolean bit(long[] bits, int local) {
        return (bits[local >>> 6] & (1L << (local & 63))) != 0;
    }

    /** The local index of a block in its section. */
    static int local(int x, int y, int z) {
        return (StrictMath.floorMod(y, 16) << 8) | (StrictMath.floorMod(z, 16) << 4) | StrictMath.floorMod(x, 16);
    }
}
