package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.data.RailBuilder;

import java.util.function.Predicate;

public class RailBuilderTest {

	/**
	 * A straight rail from x 0 to 40 at y 64, along z 0.
	 */
	private static final Rail RAIL = Rail.newRail(new Position(0, 64, 0), Angle.E, new Position(40, 64, 0), Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), 80, 80, false, false, true, false, false, TransportMode.TRAIN);
	private static final int RADIUS = 2;
	private static final int HEIGHT = 4;

	@Test
	public void valleyGetsOnlyADeck() {
		final World world = build(pos -> false, true, true, true);
		Assertions.assertTrue(world.count(RailBuilder.Part.DECK) > 100, "deck under the whole rail");
		Assertions.assertEquals(0, world.count(RailBuilder.Part.WALL), "no walls in the air");
		Assertions.assertEquals(0, world.count(RailBuilder.Part.AIR), "nothing to clear in the air");
		world.forEach(RailBuilder.Part.DECK, pos -> Assertions.assertEquals(63, pos[1], "the deck is under the track"));
	}

	@Test
	public void rockGetsALinedTunnel() {
		final World world = build(pos -> true, true, true, true);
		Assertions.assertTrue(world.count(RailBuilder.Part.AIR) > 100, "the tunnel is dug");
		Assertions.assertTrue(world.count(RailBuilder.Part.WALL) > 100, "the tunnel is lined");
		world.forEach(RailBuilder.Part.AIR, pos -> Assertions.assertTrue(Math.abs(pos[2]) <= RADIUS && pos[1] >= 64 && pos[1] < 64 + HEIGHT, "dug inside the clearance: " + pos[0] + "," + pos[1] + "," + pos[2]));
		world.forEach(RailBuilder.Part.WALL, pos -> Assertions.assertTrue(Math.abs(pos[2]) == RADIUS + 1 || pos[1] == 64 + HEIGHT, "walls only on the outline: " + pos[0] + "," + pos[1] + "," + pos[2]));
		Assertions.assertTrue(world.any(RailBuilder.Part.WALL, pos -> pos[1] == 64 + HEIGHT && pos[2] == 0), "a roof inside rock");
	}

	@Test
	public void hillsideGetsWallsOnlyOnTheGroundSide() {
		final World world = build(pos -> pos[2] > 0, true, true, true);
		Assertions.assertTrue(world.any(RailBuilder.Part.WALL, pos -> pos[2] == RADIUS + 1), "wall against the hill");
		Assertions.assertFalse(world.any(RailBuilder.Part.WALL, pos -> pos[2] <= 0), "no wall on the open side");
		Assertions.assertFalse(world.any(RailBuilder.Part.AIR, pos -> pos[2] <= 0), "nothing dug on the open side");
		Assertions.assertTrue(world.any(RailBuilder.Part.AIR, pos -> pos[2] > 0), "the hill side of the clearance is dug");
	}

	@Test
	public void partsSwitchOff() {
		final World world = build(pos -> true, false, true, false);
		Assertions.assertEquals(0, world.count(RailBuilder.Part.DECK));
		Assertions.assertEquals(0, world.count(RailBuilder.Part.WALL));
		Assertions.assertTrue(world.count(RailBuilder.Part.AIR) > 100);
	}

	@Test
	public void blocksThatCannotChangeAreLeft() {
		final World world = new World(pos -> true) {
			@Override
			public boolean canChange(int x, int y, int z) {
				return x != 10;
			}
		};
		run(world, true, true, true);
		Assertions.assertFalse(world.any(null, pos -> pos[0] == 10), "rail nodes and block entities are never replaced");
	}

	@Test
	public void spreadsOverTicks() {
		final RailBuilder builder = new RailBuilder(RAIL, RADIUS, HEIGHT, false, true, true, true);
		final World world = new World(pos -> true);
		Assertions.assertFalse(builder.step(System.nanoTime() - 1, world), "a passed deadline still does a little and stops");
		int ticks = 1;
		while (!builder.step(System.nanoTime() + 200_000, world)) {
			ticks++;
		}
		Assertions.assertTrue(ticks > 1);
		Assertions.assertEquals(1, builder.progress(), 1E-9);
	}

	private static World build(Predicate<int[]> ground, boolean deck, boolean clear, boolean walls) {
		final World world = new World(ground);
		run(world, deck, clear, walls);
		return world;
	}

	private static void run(World world, boolean deck, boolean clear, boolean walls) {
		final RailBuilder builder = new RailBuilder(RAIL, RADIUS, HEIGHT, false, deck, clear, walls);
		while (!builder.step(Long.MAX_VALUE, world)) {
		}
	}

	private static class World implements RailBuilder.Terrain {

		private final Predicate<int[]> ground;
		private final Object2ObjectOpenHashMap<String, RailBuilder.Part> changed = new Object2ObjectOpenHashMap<>();

		private World(Predicate<int[]> ground) {
			this.ground = ground;
		}

		@Override
		public boolean isGround(int x, int y, int z) {
			final RailBuilder.Part part = changed.get(x + "," + y + "," + z);
			return part == null ? ground.test(new int[]{x, y, z}) : part != RailBuilder.Part.AIR;
		}

		@Override
		public boolean canChange(int x, int y, int z) {
			return true;
		}

		@Override
		public void set(int x, int y, int z, RailBuilder.Part part, boolean topHalf) {
			changed.put(x + "," + y + "," + z, part);
		}

		private long count(RailBuilder.Part part) {
			return changed.values().stream().filter(value -> value == part).count();
		}

		private boolean any(RailBuilder.Part part, Predicate<int[]> predicate) {
			return changed.entrySet().stream().anyMatch(entry -> (part == null || entry.getValue() == part) && predicate.test(parse(entry.getKey())));
		}

		private void forEach(RailBuilder.Part part, java.util.function.Consumer<int[]> consumer) {
			changed.forEach((key, value) -> {
				if (value == part) {
					consumer.accept(parse(key));
				}
			});
		}

		private static int[] parse(String key) {
			final String[] split = key.split(",");
			return new int[]{Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2])};
		}
	}
}
