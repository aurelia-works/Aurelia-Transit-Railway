package org.mtr.aurelia.client.voice;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The voices ATR can download, and the speech engine that reads them (sherpa-onnx 1.13.8, Apache-2.0, run as its own
 * program so a crash in it never takes the game down). Every file is pinned by size and SHA-256. Nothing here is in
 * the mod jar: the player downloads the engine and one voice from the Sound tab.
 * <p>
 * Licences, from each project's own page: sherpa-onnx and the Kokoro voices are Apache-2.0. Kokoro turns text into sounds
 * with espeak-ng (GPL-3), which comes inside those downloads and never inside the jar.
 */
public final class VoiceCatalog {

	private static final String ENGINE_BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/";
	private static final String MODEL_BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/";

	/** One downloadable archive. {@code folder} is the one top-level folder it unpacks to. */
	public record Download(String url, String sha256, long bytes, String folder) {
	}

	/**
	 * @param speaker    Kokoro's speaker number
	 * @param kokoroLang espeak-ng language for Kokoro, empty when its word lists cover the language
	 * @param lexicons   Kokoro word lists inside the voice folder, empty when espeak-ng does the work
	 */
	public record Voice(String id, String name, String language, Download download, int speaker, String kokoroLang, List<String> lexicons) {
	}

	private static final Download KOKORO = new Download(MODEL_BASE + "kokoro-int8-multi-lang-v1_0.tar.bz2", "72c347230107ef8a31098ec4d468a605244beef5f1ed00db97efa13518800653", 132_353_410, "kokoro-int8-multi-lang-v1_0");

	/**
	 * Jah's English picks (2026-11-23); the first is the default. English only (Jah, 2026-11-24): a game in another language uses
	 * the computer's own voice.
	 */
	public static final List<Voice> VOICES = List.of(
			kokoro("isabella", "Isabella", "en", 22, "", "lexicon-gb-en.txt"),
			kokoro("lewis", "Lewis", "en", 27, "", "lexicon-gb-en.txt"),
			kokoro("michael", "Michael", "en", 16, "", "lexicon-us-en.txt")
	);

	/** The line the Sound tab's "Test voice" button says, per language. */
	public static final java.util.Map<String, String> SAMPLES = java.util.Map.of(
			"en", "The next station is Clapham Junction. Please mind the gap between the train and the platform."
	);

	private VoiceCatalog() {
	}

	/**
	 * The language of Minecraft's language code ({@code en_gb}, {@code nl_be}, {@code zh_tw}), or null when no voice
	 * speaks it (the computer's own voice reads those).
	 */
	public static String language(String minecraftLanguage) {
		final String code = minecraftLanguage == null ? "" : minecraftLanguage.toLowerCase(Locale.ROOT);
		final int underscore = code.indexOf('_');
		final String language = underscore < 0 ? code : code.substring(0, underscore);
		for (final Voice voice : VOICES) {
			if (voice.language.equals(language)) {
				return language;
			}
		}
		return null;
	}

	public static List<Voice> voices(String language) {
		final List<Voice> voices = new ArrayList<>();
		for (final Voice voice : VOICES) {
			if (voice.language.equals(language)) {
				voices.add(voice);
			}
		}
		return voices;
	}

	public static Voice byId(String id) {
		for (final Voice voice : VOICES) {
			if (voice.id.equals(id)) {
				return voice;
			}
		}
		return null;
	}

	/** The engine for this computer, or null where sherpa-onnx has no ready-made build. */
	public static Download engine(String osName, String arch) {
		final String os = osName.toLowerCase(Locale.ROOT);
		final boolean arm = arch.toLowerCase(Locale.ROOT).contains("aarch64") || arch.toLowerCase(Locale.ROOT).contains("arm64");
		if (os.contains("mac")) {
			return arm ? engine("osx-arm64-shared", "b10e5c7e2c30ea03de9c442655d14860d9edc475c6251d58a8f5f06e913a1d56", 19_920_000)
					: engine("osx-x64-shared", "54aad64acee9d2d596535a6080d6f22602a720af5460e1d50461b1e1b06bee40", 22_020_000);
		} else if (os.contains("win")) {
			return arm ? null : engine("win-x64-shared-MT-Release", "6dffdc715a4465b989446a6105265d2cb345e7101591a17d35534b6758f6e8df", 24_120_000);
		} else if (os.contains("linux")) {
			return arm ? engine("linux-aarch64-shared-cpu", "4e3734f82bc1379fd91f219f5869c7e9d03b7a4f7561907d8abca4849c51a789", 27_260_000)
					: engine("linux-x64-shared", "c0bdb7907d3a74bba1d55d22bf4d9fa75586cf1530614ebe88a27b9118e015c4", 27_260_000);
		}
		return null;
	}

	/** The engine program inside its unpacked folder. */
	public static Path program(Path engineFolder, boolean windows) {
		return engineFolder.resolve("bin").resolve(windows ? "sherpa-onnx-offline-tts.exe" : "sherpa-onnx-offline-tts");
	}

	/**
	 * The engine's command line for one sentence.
	 *
	 * @param speed 1 is normal; higher is faster
	 */
	public static List<String> command(Path program, Voice voice, Path voiceFolder, double speed, Path output, String text) {
		final List<String> command = new ArrayList<>();
		command.add(program.toString());
		final String lengthScale = String.format(Locale.ROOT, "%.3f", 1 / Math.max(0.5, Math.min(2, speed)));
		command.add("--kokoro-model=" + voiceFolder.resolve("model.int8.onnx"));
		command.add("--kokoro-voices=" + voiceFolder.resolve("voices.bin"));
		command.add("--kokoro-tokens=" + voiceFolder.resolve("tokens.txt"));
		command.add("--kokoro-data-dir=" + voiceFolder.resolve("espeak-ng-data"));
		command.add("--kokoro-dict-dir=" + voiceFolder.resolve("dict"));
		if (!voice.lexicons.isEmpty()) {
			final List<String> lexicons = new ArrayList<>();
			for (final String lexicon : voice.lexicons) {
				lexicons.add(voiceFolder.resolve(lexicon).toString());
			}
			command.add("--kokoro-lexicon=" + String.join(",", lexicons));
		}
		if (!voice.kokoroLang.isEmpty()) {
			command.add("--kokoro-lang=" + voice.kokoroLang);
		}
		command.add("--kokoro-length-scale=" + lengthScale);
		command.add("--sid=" + voice.speaker);
		command.add("--num-threads=2");
		command.add("--output-filename=" + output);
		command.add(text);
		return command;
	}

	private static Voice kokoro(String id, String name, String language, int speaker, String kokoroLang, String... lexicons) {
		return new Voice(id, name, language, KOKORO, speaker, kokoroLang, List.of(lexicons));
	}

	private static Download engine(String build, String sha256, long approximateBytes) {
		final String folder = "sherpa-onnx-v1.13.8-" + build;
		return new Download(ENGINE_BASE + folder + ".tar.bz2", sha256, approximateBytes, folder);
	}
}
