/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Matrix4f;

/**
 * What the road preview and the node overlay ask Iris: whether a shader pack is on, its name, and whether Iris is drawing
 * its shadow pass (nothing of Stratos's belongs in the shadows). ATR builds without Iris on its classpath, so every call
 * goes through reflection, looked up once; without Iris, or with an Iris whose API has moved, each answer is "no".
 */
public final class IrisCompat {
    public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("iris");

    private static final Object API;
    private static final Method SHADER_PACK_IN_USE;
    private static final Method RENDERING_SHADOW_PASS;
    private static final Method PACK_NAME;

    static {
        Object api = null;
        Method inUse = null;
        Method shadow = null;
        Method name = null;
        if (LOADED) {
            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = apiClass.getMethod("getInstance").invoke(null);
                inUse = apiClass.getMethod("isShaderPackInUse");
                shadow = apiClass.getMethod("isRenderingShadowPass");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                api = null;
            }
            try {
                name = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                name = null;
            }
        }
        API = api;
        SHADER_PACK_IN_USE = inUse;
        RENDERING_SHADOW_PASS = shadow;
        PACK_NAME = name;
    }

    private IrisCompat() {
    }

    public static boolean shaderPackInUse() {
        return ask(SHADER_PACK_IN_USE);
    }

    public static boolean renderingShadowPass() {
        return ask(RENDERING_SHADOW_PASS);
    }

    /** The shader pack's name, or null when there's none or Iris won't say. */
    public static String shaderPackName() {
        if (PACK_NAME == null) {
            return null;
        }
        try {
            return PACK_NAME.invoke(null) instanceof String name ? name : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Iris's projection for Distant Horizons' terrain. ATR draws no preview in DH's pass, so there's never one to use. */
    public static Matrix4f dhProjection() {
        return null;
    }

    private static boolean ask(Method method) {
        if (API == null || method == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(API));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
