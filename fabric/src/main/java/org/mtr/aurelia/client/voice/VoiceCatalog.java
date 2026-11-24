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
 * Licences, from each project's own page: sherpa-onnx and the Kokoro voices are Apache-2.0; the Piper voices are CC0
 * (Dutch, German, Spanish) or CC BY 4.0 (French, SIWIS, credited in the Sound tab tooltip). Both engines turn text into
 * sounds with espeak-ng (GPL-3), which comes inside those downloads and never inside the jar.
 */
public final class VoiceCatalog {

	private static final String ENGINE_BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/";
	private static final String MODEL_BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/";

	/** One downloadable archive. {@code folder} is the one top-level folder it unpacks to. */
	public record Download(String url, String sha256, long bytes, String folder) {
	}

	public enum Kind {KOKORO, PIPER}

	/**
	 * @param speaker    Kokoro's speaker number (Piper voices have one speaker, 0)
	 * @param kokoroLang espeak-ng language for Kokoro, empty when its word lists cover the language
	 * @param lexicons   Kokoro word lists inside the voice folder, empty when espeak-ng does the work
	 * @param piperModel the Piper model file inside the voice folder
	 */
	public record Voice(String id, String name, String language, Kind kind, Download download, int speaker, String kokoroLang, List<String> lexicons, String piperModel) {
	}

	private static final Download KOKORO = new Download(MODEL_BASE + "kokoro-int8-multi-lang-v1_0.tar.bz2", "72c347230107ef8a31098ec4d468a605244beef5f1ed00db97efa13518800653", 132_353_410, "kokoro-int8-multi-lang-v1_0");

	/** Jah's English picks (2026-11-23) first; the first voice of a language is its default. */
	public static final List<Voice> VOICES = List.of(
			kokoro("isabella", "Isabella", "en", 22, "", "lexicon-gb-en.txt"),
			kokoro("lewis", "Lewis", "en", 27, "", "lexicon-gb-en.txt"),
			kokoro("michael", "Michael", "en", 16, "", "lexicon-us-en.txt"),
			kokoro("xiaoxiao", "Xiaoxiao", "zh", 47, "", "lexicon-us-en.txt", "lexicon-zh.txt"),
			kokoro("sara", "Sara", "it", 35, "it"),
			kokoro("dora", "Dora", "pt", 42, "pt-br"),
			kokoro("alpha", "Alpha", "hi", 31, "hi"),
			piper("ronnie", "Ronnie", "nl", "nl_NL-ronnie-medium", 67_194_164, "d2cc688abf46054eba1d47b3b1406c742ccf66732cd47998a3ee727c1b96c09f"),
			piper("thorsten", "Thorsten", "de", "de_DE-thorsten-medium", 67_214_254, "50487d9c95fdf2191f31d2588569381063ba1591dcd4c7d4bdd30f12b2191714"),
			piper("siwis", "Siwis", "fr", "fr_FR-siwis-medium", 67_207_459, "375909aa30842b3a4efa10b1beb1d761af792960ae6873b4d53889f96c66195b"),
			piper("davefx", "Davefx", "es", "es_ES-davefx-medium", 67_184_952, "a3f6beb54a9cb893279f72978a22f807a4d9fc9c7848157b524d5cc7b7f58b22")
	);

	/** The line the Sound tab's "Test voice" button says, per language. */
	public static final java.util.Map<String, String> SAMPLES = java.util.Map.of(
			"en", "The next station is Clapham Junction. Please mind the gap between the train and the platform.",
			"zh", "下一站，人民广场。请从右侧车门下车。",
			"it", "Prossima fermata: Roma Termini.",
			"pt", "Próxima estação: Sé.",
			"hi", "अगला स्टेशन राजीव चौक है।",
			"nl", "De volgende halte is Utrecht Centraal.",
			"de", "Nächster Halt: Frankfurt Hauptbahnhof.",
			"fr", "Prochain arrêt : Châtelet.",
			"es", "Próxima estación: Sol."
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
		if (voice.kind == Kind.KOKORO) {
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
		} else {
			command.add("--vits-model=" + voiceFolder.resolve(voice.piperModel + ".onnx"));
			command.add("--vits-tokens=" + voiceFolder.resolve("tokens.txt"));
			command.add("--vits-data-dir=" + voiceFolder.resolve("espeak-ng-data"));
			command.add("--vits-length-scale=" + lengthScale);
		}
		command.add("--sid=" + voice.speaker);
		command.add("--num-threads=2");
		command.add("--output-filename=" + output);
		command.add(text);
		return command;
	}

	private static Voice kokoro(String id, String name, String language, int speaker, String kokoroLang, String... lexicons) {
		return new Voice(id, name, language, Kind.KOKORO, KOKORO, speaker, kokoroLang, List.of(lexicons), "");
	}

	private static Voice piper(String id, String name, String language, String model, long bytes, String sha256) {
		return new Voice(id, name, language, Kind.PIPER, new Download(MODEL_BASE + "vits-piper-" + model + ".tar.bz2", sha256, bytes, "vits-piper-" + model), 0, "", List.of(), model);
	}

	private static Download engine(String build, String sha256, long approximateBytes) {
		final String folder = "sherpa-onnx-v1.13.8-" + build;
		return new Download(ENGINE_BASE + folder + ".tar.bz2", sha256, approximateBytes, folder);
	}
}
