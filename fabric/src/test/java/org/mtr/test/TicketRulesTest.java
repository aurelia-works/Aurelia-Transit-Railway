package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.mod.data.TicketRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Currency ids and free passage, without a Minecraft registry. */
class TicketRulesTest {

	@Test
	void canonicalItemIdsMatchMinecraftRules() {
		assertEquals("minecraft:emerald", TicketRules.canonicalItemId("minecraft:emerald"));
		assertEquals("minecraft:emerald", TicketRules.canonicalItemId("  emerald  "));
		assertEquals("minecraft:gold_ingot", TicketRules.canonicalItemId(":gold_ingot"));
		assertEquals("coins:gold", TicketRules.canonicalItemId("coins:gold"));
		assertEquals("mod:path/coin", TicketRules.canonicalItemId("mod:path/coin"));
		assertEquals("mtr:ticket", TicketRules.canonicalItemId("mtr:ticket"));
		assertNull(TicketRules.canonicalItemId(null));
		assertNull(TicketRules.canonicalItemId(""));
		assertNull(TicketRules.canonicalItemId("   "));
		assertNull(TicketRules.canonicalItemId("Minecraft:Emerald"));
		assertNull(TicketRules.canonicalItemId("minecraft:"));
		assertNull(TicketRules.canonicalItemId("a:b:c"));
		assertNull(TicketRules.canonicalItemId("mod id:coin"));
		assertNull(TicketRules.canonicalItemId("minecraft:emerald block"));
		assertNull(TicketRules.canonicalItemId("mod:Coin"));
	}

	@Test
	void missingOrInvalidCurrencyFallsBackToEmerald() {
		assertFallback(null, TicketRules.Problem.BLANK);
		assertFallback("  ", TicketRules.Problem.BLANK);
		assertFallback("NOT AN ID", TicketRules.Problem.INVALID_ID);
		assertFallback("Minecraft:Emerald", TicketRules.Problem.INVALID_ID);
		assertFallback("minecraft:air", TicketRules.Problem.AIR);
		assertFallback("minecraft:missing", TicketRules.Problem.UNKNOWN_ITEM);
	}

	@Test
	void knownModdedItemIsAccepted() {
		final TicketRules.CurrencyResolution resolution = TicketRules.resolve("coins:gold", id -> id.equals("coins:gold"));
		assertEquals(TicketRules.Problem.NONE, resolution.problem);
		assertEquals("coins:gold", resolution.id);
	}

	@Test
	void bareEmeraldNameIsTheDefaultItem() {
		final TicketRules.CurrencyResolution resolution = TicketRules.resolve("emerald", id -> true);
		assertEquals(TicketRules.Problem.NONE, resolution.problem);
		assertEquals(TicketRules.DEFAULT_CURRENCY_ID, resolution.id);
	}

	@Test
	void airIsRejectedEvenWhenTheRegistryContainsIt() {
		final TicketRules.CurrencyResolution resolution = TicketRules.resolve("minecraft:air", id -> true);
		assertEquals(TicketRules.Problem.AIR, resolution.problem);
		assertEquals(TicketRules.DEFAULT_CURRENCY_ID, resolution.id);
	}

	@Test
	void freePassageOpensWithoutLookingAtBalanceOrAStation() {
		assertNull(TicketRules.freePassage(false, true, false, false, true));
		final TicketRules.FreePassage entrance = TicketRules.freePassage(true, true, false, false, false);
		assertTrue(entrance.entering);
		assertFalse(entrance.concessionary);
		final TicketRules.FreePassage exit = TicketRules.freePassage(true, false, true, false, false);
		assertFalse(exit.entering);
		final TicketRules.FreePassage bothIn = TicketRules.freePassage(true, true, true, false, true);
		assertTrue(bothIn.entering);
		assertTrue(bothIn.concessionary);
		final TicketRules.FreePassage bothOut = TicketRules.freePassage(true, true, true, true, false);
		assertFalse(bothOut.entering);
		assertFalse(bothOut.concessionary);
	}

	private static void assertFallback(String configured, TicketRules.Problem problem) {
		final TicketRules.CurrencyResolution resolution = TicketRules.resolve(configured, id -> "minecraft:emerald".equals(id) || "coins:gold".equals(id));
		assertEquals(problem, resolution.problem);
		assertEquals(TicketRules.DEFAULT_CURRENCY_ID, resolution.id);
	}
}
