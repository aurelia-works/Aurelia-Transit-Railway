package org.mtr.mod.resource;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.tool.GlStateTracker;
import org.mtr.mod.data.IGui;

import javax.annotation.Nullable;

public final class OptimizedRendererWrapper implements IGui {

	@Nullable
	private final OptimizedRenderer optimizedRenderer;
	/** The shader programs were compiled by an earlier {@link #beginReload()} of this renderer. */
	private boolean shadersCompiled;
	private static final boolean JOBAN_CLIENT_LOADED = FabricLoader.getInstance().isModLoaded("jsblock");

	public OptimizedRendererWrapper() {
		this.optimizedRenderer = OptimizedRenderer.hasOptimizedRendering() ? new OptimizedRenderer() : null;
	}

	/**
	 * MTR recompiles its shader programs every time it builds a model (9-17 ms each), but they only change on a
	 * resource reload. With "faster model loading" on, only the first begin (and any during the loading overlay) compiles;
	 * the others just save the GL state. Joban Client Mod does this itself, so it is left alone when that is loaded.
	 */
	public void beginReload() {
		if (optimizedRenderer != null) {
			if (ModelLoadingSettings.shouldSkipShaderRecompile(ModelLoadingSettings.fasterModelLoading(), JOBAN_CLIENT_LOADED, shadersCompiled, MinecraftClient.getInstance().getOverlay() != null)) {
				GlStateTracker.capture();
			} else {
				optimizedRenderer.beginReload();
				shadersCompiled = true;
			}
		}
	}


	public void finishReload() {
		if (optimizedRenderer != null) {
			optimizedRenderer.finishReload();
		}
	}

	public void queue(OptimizedModelWrapper optimizedModel, GraphicsHolder graphicsHolder, int light) {
		if (optimizedRenderer != null && optimizedModel.optimizedModel != null) {
			optimizedRenderer.queue(optimizedModel.optimizedModel, graphicsHolder, ARGB_WHITE, light);
		}
	}

	public void render(boolean renderTranslucent) {
		if (optimizedRenderer != null) {
			optimizedRenderer.render(renderTranslucent);
		}
	}
}
