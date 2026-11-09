package com.aureliatransit.architecture.station.world;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a build changed, in write order: the state each cell had before and the state it had right after the write, plus the
 * block entity data the cell had before the write. Undo walks it backwards and only touches cells that still hold the written
 * block (same block, and the same {@code is_connected} for rail nodes; see {@link StationBuildJob#stillPlaced}).
 *
 * <p>It also remembers grass and mycelium the build covered. Vanilla turns them into dirt under a solid block, which is not a
 * write of ours; undo puts them back once the block above is gone, but only where the cell is still plain dirt.
 *
 * <p>Every build writes a journal to its own file under {@code <world>/data/ata_station_undo/<owner>/} (see {@link UndoStore}):
 * a header record, then one small record per changed cell, every lift, rail node, station and airport, and the covered soil.
 * Records are appended between batches, so an undo survives a server restart without rewriting a big file. The file format
 * reads the three-field {@link Entry} shape older builds used: a missing block entity is simply nothing to put back.
 */
public final class UndoLog {

	/** How often a running build may write its journal to disk, and how many records force a write sooner. */
	private static final long SAVE_INTERVAL_MS = 1000L;
	private static final int SAVE_RECORDS = 4096;

	/** @param placed the cell's state read back after the write, so neighbour updates during the write are included */
	public record Entry(BlockPos pos, BlockState previous, BlockState placed, @Nullable NbtCompound previousEntity) {

		public Entry {
			previousEntity = previousEntity == null ? null : previousEntity.copy();
		}

		/** The old three-field entry: no block entity data to put back. */
		public Entry(BlockPos pos, BlockState previous, BlockState placed) {
			this(pos, previous, placed, null);
		}
	}

	private final RegistryKey<World> dimension;
	private final List<Entry> entries = new ArrayList<>();
	/** Each written cell's rebuild key (block state and text, see {@link StationBuildJob#key}), last write wins, in write order. */
	private final Map<BlockPos, String> keys = new LinkedHashMap<>();
	private final Map<BlockPos, BlockState> coveredSoil = new HashMap<>();
	/** One stop of each MTR lift the build formed; undo deletes those lifts before the blocks. */
	private final List<BlockPos> lifts = new ArrayList<>();
	/** Both nodes of every MTR platform rail the build laid. */
	private final List<BlockPos> railNodes = new ArrayList<>();
	/** Ids of the MTR stations the build added; undo deletes them. */
	private final List<Long> stations = new ArrayList<>();
	/** The MTR ids the airport builds added (gate platforms, sidings, route, depot). */
	private final List<AirportMtr.Ids> airports = new ArrayList<>();
	/** Set once undo has taken the stations: one MTR answers after that is deleted at once instead of kept. */
	private boolean stationsTaken;
	/** Set once undo or a rebuild has taken the airports, as {@link #stationsTaken}. */
	private boolean airportsTaken;

	private final UUID build;
	private final long created;
	@Nullable
	private UUID owner;
	/** The world this log's build ran in, for its file path and a late save from an MTR callback; null for a log read back. */
	@Nullable
	private ServerWorld world;
	/** This build's file once it has been written; null until then (or when the server's save path is not writable). */
	@Nullable
	private File file;
	/** Records added since the last append; a batch's cells go to disk together, not one syscall each. */
	private final List<NbtCompound> pending = new ArrayList<>();
	private boolean savedOnce;
	private boolean saveFailed;
	private long lastSave;

	public UndoLog(RegistryKey<World> dimension) {
		this(dimension, UUID.randomUUID(), System.currentTimeMillis());
	}

	private UndoLog(RegistryKey<World> dimension, UUID build, long created) {
		this.dimension = dimension;
		this.build = build;
		this.created = created;
		// 0 rather than the creation time: the first batch of cells is written at once, then at the interval
		this.lastSave = 0;
	}

	public RegistryKey<World> dimension() {
		return dimension;
	}

	/** This build's id, also its file name after the start time. */
	public UUID buildId() {
		return build;
	}

	public long createdAt() {
		return created;
	}

	@Nullable
	UUID owner() {
		return owner;
	}

	@Nullable
	ServerWorld world() {
		return world;
	}

	/** Remembers whose build this is and where, opens its journal and writes the header; a no-op once attached. */
	void attach(ServerWorld world, UUID owner) {
		if (this.owner != null) {
			return;
		}
		this.world = world;
		this.owner = owner;
		saveFailed = !UndoStore.begin(this);
		journalHeld();
	}

	/**
	 * Writes what the log already holds when it is attached, as a rebuild's log is filled with the kept cells, soil and rails
	 * before it has an owner. Encoded and appended a batch at a time: encoding a whole airport's site at once ran the
	 * server out of memory (884,240 cells, AirportMtrRebuildGameTests).
	 */
	private void journalHeld() {
		for (final Entry entry : entries) {
			batched(cell(entry, keys.get(entry.pos())));
		}
		for (final Map.Entry<BlockPos, BlockState> soil : coveredSoil.entrySet()) {
			batched(soil(soil.getKey(), soil.getValue()));
		}
		for (final BlockPos stop : lifts) {
			batched(position("lift", stop));
		}
		for (final BlockPos node : railNodes) {
			batched(position("rail", node));
		}
		for (final long id : stations) {
			batched(station(id));
		}
		for (final AirportMtr.Ids ids : airports) {
			batched(airport(ids));
		}
		flushNow();
	}

	private void batched(@Nullable NbtCompound record) {
		record(record);
		if (pending.size() >= SAVE_RECORDS) {
			flushNow();
		}
	}

	/** True once the log has a journal to write to; before that it records nothing, and {@link #attach} writes it all. */
	private boolean journaling() {
		return owner != null && !saveFailed;
	}

	@Nullable
	File file() {
		return file;
	}

	void file(File file) {
		this.file = file;
	}

	boolean savedOnce() {
		return savedOnce;
	}

	void savedOnce(boolean saved) {
		savedOnce = saved;
	}

	public int size() {
		return entries.size();
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	void add(Entry entry) {
		add(entry, null);
	}

	void add(Entry entry, @Nullable String key) {
		entries.add(entry);
		if (key != null) {
			keys.remove(entry.pos());
			keys.put(entry.pos(), key);
		}
		if (journaling()) {
			record(cell(entry, key));
		}
	}

	/** What each written cell was written as, in write order; a rebuild at the same spot compares its plan with this. */
	Map<BlockPos, String> keys() {
		return keys;
	}

	/** Each cell's state before the build first wrote it, and the state its last write left. */
	Map<BlockPos, Entry> cells() {
		final Map<BlockPos, Entry> cells = new LinkedHashMap<>();
		for (final Entry entry : entries) {
			final Entry first = cells.get(entry.pos());
			cells.put(entry.pos(), new Entry(entry.pos(), first == null ? entry.previous() : first.previous(), entry.placed(),
					first == null ? entry.previousEntity() : first.previousEntity()));
		}
		return cells;
	}

	/** Covered soil carries over to the log of a rebuild at the same spot. */
	void copySoil(UndoLog from) {
		from.coveredSoil.forEach((pos, state) -> {
			if (coveredSoil.putIfAbsent(pos, state) == null && journaling()) {
				record(soil(pos, state));
			}
		});
	}

	/** Remembers soil that decays when covered, the first time the build writes above it. */
	void coverSoil(BlockPos pos, BlockState soil) {
		if (coveredSoil.putIfAbsent(pos, soil) == null && journaling()) {
			record(soil(pos, soil));
		}
	}

	/** The covered soil state at {@code pos}, or null. */
	BlockState coveredSoil(BlockPos pos) {
		return coveredSoil.get(pos);
	}

	void addLift(BlockPos stop) {
		lifts.add(stop);
		record(position("lift", stop));
	}

	/** How many MTR lifts the build formed (and undo will delete). */
	public int lifts() {
		return lifts.size();
	}

	/** The lifts' stops, once: undo deletes the lifts and forgets them. */
	List<BlockPos> takeLifts() {
		final List<BlockPos> taken = List.copyOf(lifts);
		lifts.clear();
		return taken;
	}

	void addRail(BlockPos a, BlockPos b) {
		railNodes.add(a);
		railNodes.add(b);
		record(position("rail", a));
		record(position("rail", b));
	}

	/** The nodes of the platform rails the build laid, once: undo removes the rails and forgets them. */
	List<BlockPos> takeRailNodes() {
		final List<BlockPos> taken = List.copyOf(railNodes);
		railNodes.clear();
		return taken;
	}

	/** Whether {@code pos} is a node of a platform rail the build laid. */
	boolean railNode(BlockPos pos) {
		return railNodes.contains(pos);
	}

	/** A rebuild keeps the last build's platform rails (MTR routes use them) and its undo still removes them. */
	void copyRails(UndoLog last) {
		for (final BlockPos node : last.railNodes) {
			railNodes.add(node);
			record(position("rail", node));
		}
	}

	/** @return false when undo already ran, so the caller deletes the station instead */
	boolean addStation(long id) {
		if (stationsTaken) {
			return false;
		}
		stations.add(id);
		persist(station(id));
		return true;
	}

	/** How many MTR stations the build added (and undo will delete). */
	public int stations() {
		return stations.size();
	}

	/** The added MTR stations' ids, once: undo deletes them and forgets them. */
	List<Long> takeStations() {
		final List<Long> taken = List.copyOf(stations);
		stations.clear();
		stationsTaken = true;
		return taken;
	}

	/** @return false when undo (or a rebuild) already took the airports, so the caller must not add this one to MTR */
	boolean addAirport(AirportMtr.Ids ids) {
		if (airportsTaken) {
			return false;
		}
		airports.add(ids);
		persist(airport(ids));
		return true;
	}

	/** True once undo or a rebuild took the airports: an MTR answer arriving after that adds nothing. */
	boolean airportsTaken() {
		return airportsTaken;
	}

	/** The airports' MTR ids, once: undo deletes them and forgets them. */
	List<AirportMtr.Ids> takeAirports() {
		final List<AirportMtr.Ids> taken = List.copyOf(airports);
		airports.clear();
		airportsTaken = true;
		return taken;
	}

	Entry last() {
		return entries.get(entries.size() - 1);
	}

	void removeLast() {
		entries.remove(entries.size() - 1);
	}

	// ---- the journal ------------------------------------------------------------------------------------------------

	/** A new record that did not fit the last batch; a record that cannot be encoded leaves the in-memory undo usable. */
	private void record(@Nullable NbtCompound record) {
		if (record != null && journaling()) {
			pending.add(record);
		}
	}

	/** Appends a late MTR answer at once; the rest of a batch is flushed by {@link #flush}. */
	private void persist(NbtCompound record) {
		record(record);
		flushNow();
	}

	/**
	 * Writes the records added since the last append when {@code force} is set, a second has passed or
	 * {@link #SAVE_RECORDS} arrived; the build calls it between batches, so a stopped server loses at most the last second of
	 * its undo. The first successful append with cells in it retires the player's older build file.
	 */
	void flush(ServerWorld world, boolean force) {
		final long now = System.currentTimeMillis();
		if (force || now - lastSave >= SAVE_INTERVAL_MS || pending.size() >= SAVE_RECORDS) {
			flushNow();
		}
	}

	/** Writes now for a late MTR answer (a station or airport id) that arrives after the build's last batch. */
	void persist() {
		flushNow();
	}

	private void flushNow() {
		if (owner == null || saveFailed) {
			return;
		}
		if (pending.isEmpty()) {
			return;
		}
		if (UndoStore.append(this)) {
			pending.clear();
			lastSave = System.currentTimeMillis();
			if (!savedOnce && !entries.isEmpty()) {
				UndoStore.retireOlder(this);
				savedOnce = true;
			}
		} else {
			// a read-only or missing save folder: keep the undo in memory, do not retry every batch
			saveFailed = true;
		}
	}

	/** The records waiting to be appended; {@link UndoStore} writes and clears them. */
	List<NbtCompound> pending() {
		return pending;
	}

	// ---- records ----------------------------------------------------------------------------------------------------

	@Nullable
	private static NbtCompound cell(Entry entry, @Nullable String key) {
		final NbtElement previous = state(entry.previous());
		final NbtElement placed = state(entry.placed());
		if (previous == null || placed == null) {
			return null;
		}
		final NbtCompound cell = new NbtCompound();
		cell.putString("type", "cell");
		cell.putLong("pos", entry.pos().asLong());
		cell.put("previous", previous);
		cell.put("placed", placed);
		if (entry.previousEntity() != null) {
			cell.put("previous_entity", entry.previousEntity().copy());
		}
		if (key != null) {
			cell.putString("key", key);
		}
		return cell;
	}

	@Nullable
	private static NbtCompound soil(BlockPos pos, BlockState state) {
		final NbtElement encoded = state(state);
		if (encoded == null) {
			return null;
		}
		final NbtCompound soil = new NbtCompound();
		soil.putString("type", "soil");
		soil.putLong("pos", pos.asLong());
		soil.put("state", encoded);
		return soil;
	}

	private static NbtCompound position(String type, BlockPos pos) {
		final NbtCompound record = new NbtCompound();
		record.putString("type", type);
		record.putLong("pos", pos.asLong());
		return record;
	}

	private static NbtCompound station(long id) {
		final NbtCompound record = new NbtCompound();
		record.putString("type", "station");
		record.putLong("id", id);
		return record;
	}

	private static NbtCompound airport(AirportMtr.Ids ids) {
		final NbtCompound record = new NbtCompound();
		record.putString("type", "airport");
		record.putLongArray("platforms", ids.platforms());
		record.putLongArray("sidings", ids.sidings());
		record.putLong("route", ids.route());
		record.putLong("depot", ids.depot());
		return record;
	}

	/** The header that starts a build's journal. */
	NbtCompound header() {
		final NbtCompound header = new NbtCompound();
		header.putString("type", "header");
		header.putInt("version", 1);
		header.putString("dimension", dimension.getValue().toString());
		header.putUuid("build", build);
		header.putLong("created", created);
		if (owner != null) {
			header.putUuid("owner", owner);
		}
		return header;
	}

	/** A log from its header, or null when the file is not a journal this version understands. */
	@Nullable
	static UndoLog fromHeader(NbtCompound header) {
		if (!"header".equals(header.getString("type")) || header.getInt("version") != 1
				|| !header.containsUuid("build") || !header.containsUuid("owner")) {
			return null;
		}
		final Identifier dimensionId = Identifier.tryParse(header.getString("dimension"));
		if (dimensionId == null) {
			return null;
		}
		final UndoLog log = new UndoLog(RegistryKey.of(RegistryKeys.WORLD, dimensionId), header.getUuid("build"), header.getLong("created"));
		log.owner = header.getUuid("owner");
		log.savedOnce = true;
		return log;
	}

	/** Adds one journal record to this log; unknown record types are ignored, so a newer file still partly reads. */
	void apply(NbtCompound record) {
		switch (record.getString("type")) {
			case "cell" -> applyCell(record);
			case "soil" -> {
				final BlockState state = state(record.get("state"));
				if (state != null) {
					coveredSoil.put(BlockPos.fromLong(record.getLong("pos")), state);
				}
			}
			case "lift" -> lifts.add(BlockPos.fromLong(record.getLong("pos")));
			case "rail" -> railNodes.add(BlockPos.fromLong(record.getLong("pos")));
			case "station" -> stations.add(record.getLong("id"));
			case "airport" -> airports.add(new AirportMtr.Ids(longs(record.getLongArray("platforms")),
					longs(record.getLongArray("sidings")), record.getLong("route"), record.getLong("depot")));
			default -> {
			}
		}
	}

	private void applyCell(NbtCompound record) {
		final BlockState previous = state(record.get("previous"));
		final BlockState placed = state(record.get("placed"));
		if (previous == null || placed == null) {
			return;
		}
		final BlockPos pos = BlockPos.fromLong(record.getLong("pos"));
		final NbtCompound entity = record.contains("previous_entity") ? record.getCompound("previous_entity") : null;
		entries.add(new Entry(pos, previous, placed, entity));
		if (record.contains("key")) {
			keys.remove(pos);
			keys.put(pos, record.getString("key"));
		}
	}

	private static List<Long> longs(long[] values) {
		final List<Long> list = new ArrayList<>(values.length);
		for (final long value : values) {
			list.add(value);
		}
		return list;
	}

	@Nullable
	private static NbtElement state(BlockState state) {
		return BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).result().orElse(null);
	}

	@Nullable
	private static BlockState state(@Nullable NbtElement nbt) {
		return nbt == null ? null : BlockState.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElse(null);
	}
}
