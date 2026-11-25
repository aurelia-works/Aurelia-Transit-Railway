/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;

/** The server's decision on a plan request (editor-protocol §5, task F-3). */
class PlanCheckTest {
    private static final BlockPlan PLAN = BlockPlan.builder(3465).set(1, 64, 2, "minecraft:stone").set(2, 64, 2, "minecraft:dirt").build();
    private static final Digest HASH = Digest.of(PlanHash.of(PLAN, "0.1.0"));

    @Test
    void aMatchingHashIsAccepted() {
        PlanCheck.Decision decision = PlanCheck.check(PlanResult.planned(PLAN), HASH, "0.1.0");
        assertTrue(decision.isAccepted());
        assertEquals(Optional.of(PLAN), decision.plan());
        assertEquals(Optional.of(HASH), decision.serverPlanHash());
        assertEquals(Optional.empty(), decision.code());
    }

    @Test
    void aDifferentHashIsRefusedWithTheServersHash() {
        Digest other = Digest.of(new byte[Digest.LENGTH]);
        PlanCheck.Decision decision = PlanCheck.check(PlanResult.planned(PLAN), other, "0.1.0");
        assertFalse(decision.isAccepted());
        assertEquals(Optional.of(Protocol.PLAN_HASH_MISMATCH), decision.code());
        assertEquals(Optional.of(HASH), decision.serverPlanHash(), "the client learns the server's hash");
    }

    @Test
    void aDifferentCoreVersionIsAMismatch() {
        // The client hashed with core 0.1.0, the server runs 0.2.0: identical blocks, different hashes (editor-protocol §3).
        PlanCheck.Decision decision = PlanCheck.check(PlanResult.planned(PLAN), HASH, "0.2.0");
        assertEquals(Optional.of(Protocol.PLAN_HASH_MISMATCH), decision.code());
    }

    @Test
    void aPlannerRefusalPassesThrough() {
        PlanCheck.Decision decision = PlanCheck.check(PlanResult.refused("UNKNOWN_PROFILE", Map.of("profile", "x")), HASH, "0.1.0");
        assertFalse(decision.isAccepted());
        assertEquals(Optional.of("UNKNOWN_PROFILE"), decision.code());
        assertEquals(Map.of("profile", "x"), decision.args());
        assertEquals(Optional.empty(), decision.serverPlanHash(), "no plan, so no hash");
    }
}
