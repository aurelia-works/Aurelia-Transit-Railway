package org.mtr.mod.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.obj.AtlasManager;
import org.mtr.mapping.render.obj.ObjModelLoader;
import org.mtr.mod.Init;
import org.mtr.mod.resource.AsyncModels;
import org.mtr.mod.resource.FastObjLoader;
import org.mtr.mod.resource.ModelLoadingSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The OBJ reader lives in the Minecraft-Mappings library, which ATR does not compile, so this is the one hook into it.
 * Reads OBJ files with {@link FastObjLoader} (the same meshes javagl gives MTR); files it does not take go to MTR's own
 * reader. A file already parsed in the background ({@link AsyncModels}) only needs its materials here.
 */
@Mixin(value = ObjModelLoader.class, remap = false)
public abstract class ObjModelLoaderMixin {

	@Unique
	private static volatile boolean atr$failed;

	@Inject(method = "loadModel(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;Lorg/mtr/mapping/render/obj/AtlasManager;Z)Ljava/util/Map;", at = @At("HEAD"), cancellable = true)
	private static void atr$fastParse(String objString, Function<String, String> mtlResolver, Function<String, Identifier> textureResolver, AtlasManager atlasManager, boolean splitModel, CallbackInfoReturnable<Map<String, List<RawMesh>>> cir) {
		if (atr$failed || objString == null || !ModelLoadingSettings.fasterModelLoading()) {
			return;
		}
		final FastObjLoader.Prepared prepared = RenderSystem.isOnRenderThread() ? AsyncModels.take(objString, splitModel) : null;
		try {
			final Map<String, List<RawMesh>> result = prepared == null
					? FastObjLoader.load(objString, mtlResolver, textureResolver, atlasManager, splitModel)
					: FastObjLoader.assemble(prepared, mtlResolver, textureResolver, atlasManager);
			if (result != null) {
				cir.setReturnValue(result);
			}
		} catch (RuntimeException e) {
			atr$failed = true;
			Init.LOGGER.error("Fast OBJ reading failed; using MTR's own OBJ reader for the rest of this session", e);
		}
	}
}
