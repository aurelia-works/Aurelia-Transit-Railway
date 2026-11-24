/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client;

import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.fabric.client.editor.StratosEditorClient;
import org.mtr.aurelia.road.fabric.client.nodes.StratosNodes;
import org.mtr.aurelia.road.fabric.client.preview.StratosPreview;
import org.mtr.aurelia.road.fabric.client.tool.StratosTools;

/**
 * The road builder's client side, set up once from ATR's client initialiser: the drawn road paint, the editor protocol,
 * the preview, the tools (their keys and HUD) and the node aids. Until the hub comes, the tool key (`) takes the
 * straight road tool.
 */
public final class StratosClient {
    private StratosClient() {
    }

    public static void register() {
        PaintRenderer.register();
        StratosEditorClient.register();
        StratosPreview.register();
        StratosTools.register();
        StratosNodes.register();
        StratosMod.LOGGER.info("[stratos] client initialised");
    }
}
