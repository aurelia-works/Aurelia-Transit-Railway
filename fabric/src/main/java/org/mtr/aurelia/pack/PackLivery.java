package org.mtr.aurelia.pack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A paint scheme owned by an operator. {@code vehicles} lists the MTR vehicle resource ids it applies to and
 * {@code textures} maps a vehicle id to the texture resource that replaces its own. At least one of the two is non-empty.
 */
public record PackLivery(String id, String operator, String name, List<String> vehicles, Map<String, String> textures) {

	public PackLivery {
		vehicles = List.copyOf(vehicles);
		textures = Collections.unmodifiableMap(new LinkedHashMap<>(textures));
	}
}
