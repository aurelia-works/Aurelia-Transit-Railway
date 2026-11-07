package com.aureliatransit.architecture.bridge.world;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * Block states as the strings a bridge template stores: {@code minecraft:oak_stairs[facing=east,half=top]}. Sorted
 * properties make the same state always write the same string, so a palette entry can be compared and diffed. Unknown
 * blocks or properties decode to null or are left out, so a template captured with an add-on block still loads and
 * skips only that block.
 */
public final class BridgeStateCodec {

	private BridgeStateCodec() {
	}

	public static String encode(BlockState state) {
		final StringBuilder text = new StringBuilder(Registries.BLOCK.getId(state.getBlock()).toString());
		final List<Property<?>> properties = new ArrayList<>(state.getProperties());
		if (!properties.isEmpty()) {
			properties.sort(Comparator.comparing(Property::getName));
			text.append('[');
			for (int i = 0; i < properties.size(); i++) {
				if (i > 0) {
					text.append(',');
				}
				final Property<?> property = properties.get(i);
				text.append(property.getName()).append('=').append(value(property, state.get(property)));
			}
			text.append(']');
		}
		return text.toString();
	}

	/**
	 * @return the state, or null when the block id is not registered in this world
	 */
	@Nullable
	public static BlockState decode(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		final int open = text.indexOf('[');
		final String idText = open < 0 ? text : text.substring(0, open);
		final Identifier id = Identifier.tryParse(idText);
		if (id == null) {
			return null;
		}
		final Optional<net.minecraft.block.Block> block = Registries.BLOCK.getOrEmpty(id);
		if (block.isEmpty()) {
			return null;
		}
		BlockState state = block.get().getDefaultState();
		if (open < 0) {
			return state;
		}
		final int close = text.lastIndexOf(']');
		if (close <= open) {
			return state;
		}
		final String properties = text.substring(open + 1, close);
		if (properties.isBlank()) {
			return state;
		}
		for (final String pair : properties.split(",")) {
			final int equals = pair.indexOf('=');
			if (equals <= 0) {
				continue;
			}
			final Property<?> property = state.getBlock().getStateManager().getProperty(pair.substring(0, equals).trim());
			if (property != null) {
				state = with(state, property, pair.substring(equals + 1).trim());
			}
		}
		return state;
	}

	/**
	 * Rotates a state clockwise by {@code quarterTurns}, as the template's own positions rotate. A block that cannot turn
	 * (a cube, a block without a facing property) is unchanged.
	 */
	public static BlockState rotate(BlockState state, int quarterTurns) {
		return switch (quarterTurns & 3) {
			case 1 -> state.rotate(BlockRotation.CLOCKWISE_90);
			case 2 -> state.rotate(BlockRotation.CLOCKWISE_180);
			case 3 -> state.rotate(BlockRotation.COUNTERCLOCKWISE_90);
			default -> state;
		};
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static String value(Property property, Comparable value) {
		return property.name(value);
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
		return property.parse(value).map(parsed -> state.with(property, parsed)).orElse(state);
	}
}
