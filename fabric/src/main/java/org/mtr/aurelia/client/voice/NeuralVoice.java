package org.mtr.aurelia.client.voice;

import net.fabricmc.loader.api.FabricLoader;
import org.mtr.aurelia.client.AureliaClient;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Speaks with a downloaded voice. The engine runs as its own program per sentence, on one worker thread; each sentence
 * is kept as a .wav, since station names repeat, so a line heard before plays at once. A newer line replaces an older
 * one still being made or spoken, as the computer's voice does.
 */
public final class NeuralVoice {

	private static final int CACHE_FILES = 300;
	private static final long TIMEOUT_SECONDS = 30;
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "ATR voice");
		thread.setDaemon(true);
		return thread;
	});
	private static final AtomicLong LATEST = new AtomicLong();

	/** Only touched on the worker thread. */
	private static Clip playing;
	private static boolean warned;

	private NeuralVoice() {
	}

	public static Path root() {
		return FabricLoader.getInstance().getConfigDir().resolve(AureliaClient.MOD_ID).resolve("voice");
	}

	/** The engine build for this computer, or null where there is none. */
	public static VoiceCatalog.Download engine() {
		return VoiceCatalog.engine(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
	}

	public static boolean ready(VoiceCatalog.Voice voice) {
		final VoiceCatalog.Download engine = engine();
		return voice != null && VoiceInstaller.installed(root(), engine) && VoiceInstaller.installed(root(), voice.download());
	}

	/**
	 * @param volume the player's Master x Voice volume, 0 to 1
	 */
	public static void say(VoiceCatalog.Voice voice, double speed, String text, float volume) {
		final long ticket = LATEST.incrementAndGet();
		WORKER.execute(() -> {
			try {
				final Path wav = render(voice, speed, text);
				if (wav != null && ticket == LATEST.get()) {
					play(wav, volume);
				}
			} catch (Exception e) {
				if (!warned) {
					warned = true;
					AureliaClient.LOGGER.warn("[ATR voice] could not speak; check config/aurelia/voice", e);
				}
			}
		});
	}

	private static Path render(VoiceCatalog.Voice voice, double speed, String text) throws Exception {
		final Path cache = root().resolve("cache");
		Files.createDirectories(cache);
		final String key = voice.id() + "|" + String.format(Locale.ROOT, "%.2f", speed) + "|" + text;
		final Path wav = cache.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))).substring(0, 32) + ".wav");
		if (Files.isRegularFile(wav)) {
			wav.toFile().setLastModified(System.currentTimeMillis());
			return wav;
		}
		final Path engineFolder = root().resolve(engine().folder());
		final List<String> command = VoiceCatalog.command(VoiceCatalog.program(engineFolder, WINDOWS), voice, root().resolve(voice.download().folder()), speed, wav, text);
		final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
		// the engine's own libraries sit next to it
		final Map<String, String> environment = builder.environment();
		final String libraries = engineFolder.resolve("lib") + File.pathSeparator + engineFolder.resolve("bin");
		final String variable = WINDOWS ? "PATH" : System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac") ? "DYLD_LIBRARY_PATH" : "LD_LIBRARY_PATH";
		environment.merge(variable, libraries, (old, added) -> added + File.pathSeparator + old);
		final Process process = builder.start();
		if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			Files.deleteIfExists(wav);
			throw new IllegalStateException("the voice engine took longer than " + TIMEOUT_SECONDS + " s");
		}
		if (process.exitValue() != 0 || !Files.isRegularFile(wav)) {
			Files.deleteIfExists(wav);
			throw new IllegalStateException("the voice engine stopped with code " + process.exitValue());
		}
		prune(cache);
		return wav;
	}

	private static void play(Path wav, float volume) throws Exception {
		if (playing != null) {
			playing.close();
			playing = null;
		}
		final Clip clip = AudioSystem.getClip();
		try (AudioInputStream stream = AudioSystem.getAudioInputStream(wav.toFile())) {
			clip.open(stream);
		}
		if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			final FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
			final float decibels = (float) (20 * Math.log10(Math.max(0.0001, volume)));
			gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
		}
		clip.addLineListener(event -> {
			if (event.getType() == LineEvent.Type.STOP) {
				event.getLine().close();
			}
		});
		playing = clip;
		clip.start();
	}

	/** Keeps the newest {@link #CACHE_FILES} sentences. */
	private static void prune(Path cache) throws Exception {
		final List<Path> files;
		try (Stream<Path> stream = Files.list(cache)) {
			files = stream.filter(path -> path.toString().endsWith(".wav")).sorted(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed()).toList();
		}
		for (int i = CACHE_FILES; i < files.size(); i++) {
			Files.deleteIfExists(files.get(i));
		}
	}
}
