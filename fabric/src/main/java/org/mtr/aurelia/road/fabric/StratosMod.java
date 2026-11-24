/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric;

import net.fabricmc.loader.api.FabricLoader;
import org.mtr.aurelia.road.fabric.apply.ApplyService;
import org.mtr.aurelia.road.fabric.net.EditorNetworking;
import org.mtr.aurelia.road.fabric.palette.GamePalettes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Stratos road editor's server side inside Aurelia Transit Railway. Stratos was its own mod; here it rides on ATR's
 * {@code mtr} mod and starts from ATR's own initialiser, so it adds no entry point and no mod id of its own. Its channel,
 * world folder, saved data and config are ATR's own ({@code aurelia:roads}, {@code <world>/aurelia_roads/},
 * {@code aurelia_roads_meta}, {@code config/aurelia/roads/}). If the standalone Stratos mod is installed as well, ATR's
 * roads stand down and leave the game to it, so the two never register the same things twice.
 */
public final class StratosMod {
    public static final String MOD_ID = "stratos";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private StratosMod() {
    }

    /** Called once from ATR's common initialiser, on both the client and a dedicated server. */
    public static void register() {
        if (standsDown()) {
            LOGGER.warn("[stratos] the standalone Stratos mod is installed, so ATR's own roads are off");
            return;
        }
        ApplyService.register();
        GamePalettes.register();
        EditorNetworking.register();
        LOGGER.info("[stratos] Stratos roads {} initialised", version());
    }

    /** Whether the standalone Stratos mod is installed, so ATR's roads must not start (client and server). */
    public static boolean standsDown() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    /** The version Stratos reports: ATR's own, since the roads ship inside it. */
    public static String version() {
        return FabricLoader.getInstance().getModContainer("mtr").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }
}
