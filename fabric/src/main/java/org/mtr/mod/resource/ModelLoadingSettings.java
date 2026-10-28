package org.mtr.mod.resource;

import org.mtr.mod.config.Config;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

/**
 * The three client switches of the model-loading speed-ups, read at the moment they are used. All off is MTR 4.0.5's
 * own behaviour. Tests replace the source with {@link #override}.
 */
public final class ModelLoadingSettings {

	@Nullable
	private static volatile boolean[] override;

	private ModelLoadingSettings() {
	}

	/** Faster OBJ reading, and no shader recompile for every model build. */
	public static boolean fasterModelLoading() {
		return read(0, () -> Config.getClient().getFasterModelLoading());
	}

	/** Train OBJ files are read on a worker thread (needs {@link #fasterModelLoading()}). */
	public static boolean backgroundModelLoading() {
		return read(1, () -> Config.getClient().getBackgroundModelLoading());
	}

	/** Model caches nobody can reach any more are dropped instead of kept for the whole session. */
	public static boolean freeUnusedModels() {
		return read(2, () -> Config.getClient().getFreeUnusedModels());
	}

	/** Whether a model build may keep the shader programs it has instead of recompiling them (they change only on a resource reload). */
	public static boolean shouldSkipShaderRecompile(boolean fasterModelLoading, boolean jobanClientLoaded, boolean shadersCompiled, boolean overlayShown) {
		return fasterModelLoading && !jobanClientLoaded && shadersCompiled && !overlayShown;
	}

	/** For tests: fixed values, or null to read the client config again. */
	public static void override(@Nullable boolean[] values) {
		override = values;
	}

	private static boolean read(int index, BooleanSupplier config) {
		final boolean[] values = override;
		return values == null ? config.getAsBoolean() : values[index];
	}
}
