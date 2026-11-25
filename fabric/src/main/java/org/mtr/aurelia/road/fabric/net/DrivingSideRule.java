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
 * <p>A placed road keeps the side its saved parameters name, so rebuilding, undoing or baking it never flips it. The side
 * changes with the world's: the hub's Driving side button ({@code DRIVE_LEFT} and {@code DRIVE_RIGHT}) sets this rule and
 * converts the roads and junctions of the player who pressed it ({@code SideSwitch}). Setting the rule by command doesn't
 * convert anything: the server then refuses roads for the other side ({@code DRIVING_SIDE_MISMATCH}) until the hub's button is
 * pressed for the same side, which carries on converting what is left.
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

    /** Sets the rule (the hub's switch of side); its callback then tells the editor server and the players. Server thread. */
    static void set(MinecraftServer server, RoadPlanParams.DrivingSide side) {
        server.getGameRules().get(key).set(side, server);
    }
}
