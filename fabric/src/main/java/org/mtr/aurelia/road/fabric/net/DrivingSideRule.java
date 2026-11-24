package org.mtr.aurelia.road.fabric.net;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.gamerule.v1.rule.EnumRule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;
import org.mtr.aurelia.road.road.RoadPlanParams;

/**
 * The world's driving side for Stratos roads (P9): the game rule {@code stratosDrivingSide}, {@code LHT} (left-hand, UK)
 * or {@code RHT}. It is saved with the world's other game rules in {@code level.dat}; a world from before P9 has no value
 * and reads as {@code LHT}, the side every road was built for until now. Operators change it with
 * {@code /gamerule stratosDrivingSide RHT} or on the Edit Game Rules screen.
 *
 * <p>Only roads drawn after a change take the new side: each placed road keeps the side its saved parameters name, so
 * rebuilding, undoing or baking it never flips it.
 */
public final class DrivingSideRule {
    public static final String NAME = "stratosDrivingSide";

    private static GameRules.Key<EnumRule<RoadPlanParams.DrivingSide>> key;

    private DrivingSideRule() {
    }

    /** Registers the rule. Called once, from the mod initialiser (client and server). */
    static void register() {
        key = GameRuleRegistry.register(NAME, GameRules.Category.MISC,
                GameRuleFactory.createEnumRule(RoadPlanParams.DrivingSide.LHT, (server, rule) -> EditorNetworking.trafficSide(rule.get())));
    }

    /** The world's driving side now. */
    static RoadPlanParams.DrivingSide get(MinecraftServer server) {
        return server.getGameRules().get(key).get();
    }
}
