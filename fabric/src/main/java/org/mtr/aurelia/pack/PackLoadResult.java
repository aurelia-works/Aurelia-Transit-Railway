package org.mtr.aurelia.pack;

import java.util.ArrayList;
import java.util.List;

/** The outcome of one load: the packs that survived and every error or warning met on the way. */
public record PackLoadResult(List<LoadedPack> packs, List<PackDiagnostic> diagnostics) {

	public static final PackLoadResult EMPTY = new PackLoadResult(List.of(), List.of());

	public PackLoadResult {
		packs = List.copyOf(packs);
		diagnostics = List.copyOf(diagnostics);
	}

	public int errorCount() {
		return count(PackDiagnostic.Severity.ERROR);
	}

	public int warningCount() {
		return count(PackDiagnostic.Severity.WARNING);
	}

	private int count(PackDiagnostic.Severity severity) {
		int count = 0;
		for (final PackDiagnostic diagnostic : diagnostics) {
			if (diagnostic.severity() == severity) {
				count++;
			}
		}
		return count;
	}

	public PackLoadResult and(PackLoadResult other) {
		final List<LoadedPack> allPacks = new ArrayList<>(packs);
		allPacks.addAll(other.packs);
		final List<PackDiagnostic> allDiagnostics = new ArrayList<>(diagnostics);
		allDiagnostics.addAll(other.diagnostics);
		return new PackLoadResult(allPacks, allDiagnostics);
	}
}
