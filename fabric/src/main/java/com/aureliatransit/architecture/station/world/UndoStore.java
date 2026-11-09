package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One undo journal per build, under {@code <world>/data/ata_station_undo/<owner>/<start>-<build>.undo}. The path is new, so an
 * old world opens with no such directory and nothing to answer; a build that starts after the mod updates creates it. The file
 * is a header record and then one NBT record per changed cell (with its previous state and block entity data) and per MTR id,
 * appended in batches while the build runs, so a server restart keeps the undo of everything written so far without rewriting
 * the whole file each time. The newest non-empty file of a player is their last build; starting a new build removes the older
 * ones, so undo means the same as before (the last build) rather than a long history.
 */
final class UndoStore {

	/** New directory under the save's data folder; nothing of MTR 4.0.5 reads it. */
	static final String DIRECTORY = "ata_station_undo";
	private static final String SUFFIX = ".undo";

	private UndoStore() {
	}

	/** Creates the build's journal and writes its header; false when the save folder cannot be written. */
	static boolean begin(UndoLog log) {
		if (log.owner() == null || log.world() == null) {
			return false;
		}
		final File directory = directory(log.world().getServer(), log.owner());
		if (!directory.isDirectory() && !directory.mkdirs()) {
			AureliaTransitArchitecture.LOGGER.warn("Could not create the station undo folder {}", directory);
			return false;
		}
		final File file = new File(directory, name(log));
		try {
			write(file, List.of(log.header()), false);
			log.file(file);
			return true;
		} catch (IOException | RuntimeException e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not start the station undo file {}; undo of this build will not survive a restart", file, e);
			return false;
		}
	}

	/** Appends the log's pending records; false when the file cannot be written (the caller stops retrying). */
	static boolean append(UndoLog log) {
		final File file = log.file();
		if (file == null || log.pending().isEmpty()) {
			return file != null;
		}
		try {
			write(file, log.pending(), true);
			return true;
		} catch (IOException | RuntimeException e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not append to the station undo file {}; undo of this build will not survive a restart", file, e);
			return false;
		}
	}

	/** Removes the player's older build files once a new build has actually written cells. */
	static void retireOlder(UndoLog log) {
		final File file = log.file();
		if (file != null && file.getParentFile() != null) {
			deleteOlder(file.getParentFile(), file);
		}
	}

	/** The player's newest non-empty build file, or null when they have none (or none is readable). */
	@Nullable
	static UndoLog latest(MinecraftServer server, UUID owner) {
		final File[] files = directory(server, owner).listFiles((directory, name) -> name.endsWith(SUFFIX));
		if (files == null || files.length == 0) {
			return null;
		}
		Arrays.sort(files, Comparator.comparing(File::getName).reversed());
		for (final File file : files) {
			final List<NbtCompound> records = read(file);
			if (records.isEmpty()) {
				continue;
			}
			final UndoLog log = UndoLog.fromHeader(records.get(0));
			if (log == null) {
				continue;
			}
			for (int i = 1; i < records.size(); i++) {
				log.apply(records.get(i));
			}
			if (!log.isEmpty()) {
				log.file(file);
				return log;
			}
		}
		return null;
	}

	/** Removes the file of a log undo has finished with. */
	static void done(MinecraftServer server, UndoLog log) {
		final File file = log.file();
		if (file != null && file.delete()) {
			log.file(null);
		}
	}

	private static void write(File file, List<NbtCompound> records, boolean append) throws IOException {
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file, append)))) {
			for (final NbtCompound record : records) {
				NbtIo.write(record, out);
			}
			out.flush();
		}
	}

	/** Every record that parses; a truncated last record (a server stopped mid-write) is left out, the rest still undo. */
	private static List<NbtCompound> read(File file) {
		final List<NbtCompound> records = new ArrayList<>();
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
			try {
				while (true) {
					final NbtCompound record = NbtIo.read(in);
					if (record == null) {
						break;
					}
					records.add(record);
				}
			} catch (EOFException e) {
				// the end of the journal
			} catch (IOException | RuntimeException e) {
				AureliaTransitArchitecture.LOGGER.warn("Could not read the end of the station undo file {}; the rest still undoes", file, e);
			}
		} catch (IOException e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not read the station undo file {}", file, e);
		}
		return records;
	}

	private static File directory(MinecraftServer server, UUID owner) {
		return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(DIRECTORY).resolve(owner.toString()).toFile();
	}

	/** Zero-padded millis first, so a name sorts by build time; the build id keeps two builds in the same millisecond apart. */
	private static String name(UndoLog log) {
		return String.format(Locale.ROOT, "%019d-%s%s", log.createdAt(), log.buildId(), SUFFIX);
	}

	private static void deleteOlder(File directory, File keep) {
		final File[] files = directory.listFiles();
		if (files == null) {
			return;
		}
		for (final File file : files) {
			if (!file.equals(keep) && file.getName().endsWith(SUFFIX)) {
				if (!file.delete()) {
					AureliaTransitArchitecture.LOGGER.warn("Could not remove the older station undo file {}", file);
				}
			}
		}
	}
}
