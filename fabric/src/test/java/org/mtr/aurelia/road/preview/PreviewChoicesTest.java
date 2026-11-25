/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.preview.PreviewChoices.ShaderFaces;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.tool.RoadChoices;

/** The preview's settings kept across restarts (task UI-1c): faces or outlines only, and the shader-pack face draw. */
class PreviewChoicesTest {
    @Test
    void keptBesideTheRoadChoicesInOneFile() {
        RoadChoices roads = new RoadChoices("alley-7", "vanilla", RoadPlanParams.NodeAids.WOOL);
        PreviewChoices preview = new PreviewChoices(true, ShaderFaces.ONE);
        JsonObject object = roads.toJsonObject();
        preview.writeTo(object);
        String json = StrictJson.write(object);
        assertEquals("{\n  \"layout\": \"alley-7\",\n  \"palette\": \"vanilla\",\n  \"nodeAids\": \"wool\",\n  \"faces\": \"outlines_only\",\n"
                + "  \"shaderFaces\": \"one\"\n}\n", json);
        assertEquals(preview, PreviewChoices.fromJson(json));
        assertEquals(roads, RoadChoices.fromJson(json), "the road choices read past the preview's");
    }

    @Test
    void anythingMissingOrUnreadableIsTheDefault() {
        assertEquals(PreviewChoices.DEFAULT, PreviewChoices.fromJson(""));
        assertEquals(PreviewChoices.DEFAULT, PreviewChoices.fromJson("not json {"));
        assertEquals(PreviewChoices.DEFAULT, PreviewChoices.fromJson("[1]"));
        assertEquals(PreviewChoices.DEFAULT, PreviewChoices.fromJson("{\"layout\": \"city-17\"}"), "a file from before UI-1c");
        assertEquals(new PreviewChoices(false, ShaderFaces.BOTH), PreviewChoices.fromJson("{\"faces\": \"sometimes\", \"shaderFaces\": \"BOTH\"}"));
        assertEquals(new PreviewChoices(true, ShaderFaces.AUTO), PreviewChoices.fromJson("{\"faces\": \"outlines_only\", \"shaderFaces\": 2}"));
    }

    @Test
    void autoDrawsOnceForBslAndBlissAndTwiceForAnyOtherPack() {
        assertTrue(ShaderFaces.AUTO.once("BSL_v8.2.09.zip"));
        assertTrue(ShaderFaces.AUTO.once("Bliss_v2.0.4_(Chocapic13_Shaders_edit).zip"));
        assertFalse(ShaderFaces.AUTO.once("ComplementaryReimagined_r5.2.2.zip"));
        assertFalse(ShaderFaces.AUTO.once("IndusShaders"));
        assertFalse(ShaderFaces.AUTO.once(null), "a pack Iris can't name");
        assertFalse(ShaderFaces.BOTH.once("BSL_v8.2.09.zip"));
        assertTrue(ShaderFaces.ONE.once("ComplementaryReimagined_r5.2.2.zip"));
    }
}
