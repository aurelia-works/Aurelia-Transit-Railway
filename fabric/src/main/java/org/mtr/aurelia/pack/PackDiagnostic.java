package org.mtr.aurelia.pack;

/** One reason a pack file or entry was skipped, or a warning about a value that was kept. */
public record PackDiagnostic(String file, int line, Severity severity, String message) {

	public enum Severity {
		ERROR, WARNING
	}

	public static PackDiagnostic error(String file, int line, String message) {
		return new PackDiagnostic(file, line, Severity.ERROR, message);
	}

	public static PackDiagnostic warning(String file, int line, String message) {
		return new PackDiagnostic(file, line, Severity.WARNING, message);
	}

	public String location() {
		return line > 0 ? file + ":" + line : file;
	}

	/** {@code file:line: message}, the form the packs screen prints. */
	public String text() {
		return location() + ": " + message;
	}
}
