/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.fabric.client.editor.StratosEditorClient;
import org.mtr.aurelia.road.fabric.client.hub.HubScreen;
import org.mtr.aurelia.road.fabric.client.nodes.StratosNodes;
import org.mtr.aurelia.road.fabric.client.preview.StratosPreview;
import org.mtr.aurelia.road.fabric.client.tool.StratosTools;

/**
 * The road builder's client side, set up once from ATR's client initialiser: the drawn road paint, the editor protocol,
 * the preview, the tools (their keys and HUD), the node aids and the hub, which {@link #OPEN_HUB} opens.
 */
public final class StratosClient {
    /**
     * Opens and closes the hub. A vanilla keybind, rebindable under Controls, looked up by name. The default is B, which
     * ATR leaves free (Stratos used G, ATR's crew key). The tick handler below opens it; while it is open the key reaches
     * the screen instead, and {@link HubScreen#keyPressed} closes it.
     */
    public static final KeyBinding OPEN_HUB = new KeyBinding("key.stratos.open_hub", InputUtil.Type.KEYSYM,
            InputUtil.fromTranslationKey("key.keyboard.b").getCode(), "category.mtr.keybinding");

    private StratosClient() {
    }

    public static void register() {
        if (StratosMod.standsDown()) {
            return; // the standalone Stratos mod has the client; StratosMod logged why
        }
        PaintRenderer.register();
        StratosEditorClient.register();
        StratosPreview.register();
        StratosTools.register();
        StratosNodes.register();
        KeyBindingHelper.registerKeyBinding(OPEN_HUB);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_HUB.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new HubScreen());
                }
            }
        });
        StratosMod.LOGGER.info("[stratos] client initialised; key B opens the hub");
    }
}
