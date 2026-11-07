package org.mtr.aurelia.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.IdentityHashMap;

/**
 * Strict JSON reader that remembers the line each value starts on, so a bad field can be reported as
 * {@code file:line} instead of a bare message. Gson's tree keeps no positions, and its reader does not expose them,
 * hence this small parser. It accepts the same JSON Gson does (no comments, no trailing commas) and rejects trailing
 * content.
 */
final class PackJson {

	static final class SyntaxError extends Exception {

		private final int line;
		private final int column;

		private SyntaxError(String message, int line, int column) {
			super(message);
			this.line = line;
			this.column = column;
		}

		int line() {
			return line;
		}

		int column() {
			return column;
		}
	}

	static final class Parsed {

		private final JsonElement root;
		private final IdentityHashMap<JsonElement, Integer> lines;

		private Parsed(JsonElement root, IdentityHashMap<JsonElement, Integer> lines) {
			this.root = root;
			this.lines = lines;
		}

		JsonElement root() {
			return root;
		}

		/** The line the value starts on, or 1 for a value the parser did not record. */
		int line(JsonElement element) {
			if (element == null) {
				return 1;
			}
			return lines.getOrDefault(element, 1);
		}
	}

	private final String text;
	private int index;
	private int line = 1;
	private int column = 1;
	private int depth;
	private final IdentityHashMap<JsonElement, Integer> lines = new IdentityHashMap<>();

	private PackJson(String text) {
		this.text = text;
	}

	static Parsed parse(String text) throws SyntaxError {
		final PackJson parser = new PackJson(text.startsWith("\uFEFF") ? text.substring(1) : text);
		parser.skipWhitespace();
		final JsonElement root = parser.value();
		parser.skipWhitespace();
		if (!parser.atEnd()) {
			throw parser.error("Unexpected content after the JSON value");
		}
		return new Parsed(root, parser.lines);
	}

	private JsonElement value() throws SyntaxError {
		skipWhitespace();
		if (atEnd()) {
			throw error("Unexpected end of file");
		}
		if (++depth > 64) {
			depth--;
			throw error("JSON is nested more than 64 levels deep");
		}
		try {
			final char c = peek();
			return switch (c) {
				case '{' -> object();
				case '[' -> array();
				case '"' -> new JsonPrimitive(string());
				case 't' -> literal("true", new JsonPrimitive(true));
				case 'f' -> literal("false", new JsonPrimitive(false));
				case 'n' -> literal("null", JsonNull.INSTANCE);
				default -> {
					if (c == '-' || c >= '0' && c <= '9') {
						yield number();
					}
					throw error("Unexpected character '" + c + "'");
				}
			};
		} finally {
			depth--;
		}
	}

	private JsonObject object() throws SyntaxError {
		expect('{');
		final JsonObject object = new JsonObject();
		skipWhitespace();
		if (take('}')) {
			return object;
		}
		while (true) {
			skipWhitespace();
			if (atEnd() || peek() != '"') {
				throw error("Expected a quoted object key");
			}
			final String key = string();
			skipWhitespace();
			expect(':');
			skipWhitespace();
			final int valueLine = line;
			final JsonElement value = value();
			lines.put(value, valueLine);
			object.add(key, value);
			skipWhitespace();
			if (take('}')) {
				return object;
			}
			expect(',');
		}
	}

	private JsonArray array() throws SyntaxError {
		expect('[');
		final JsonArray array = new JsonArray();
		skipWhitespace();
		if (take(']')) {
			return array;
		}
		while (true) {
			skipWhitespace();
			final int valueLine = line;
			final JsonElement value = value();
			lines.put(value, valueLine);
			array.add(value);
			skipWhitespace();
			if (take(']')) {
				return array;
			}
			expect(',');
		}
	}

	private String string() throws SyntaxError {
		expect('"');
		final StringBuilder builder = new StringBuilder();
		while (!atEnd()) {
			final char c = next();
			if (c == '"') {
				return builder.toString();
			}
			if (c == '\\') {
				if (atEnd()) {
					throw error("Unterminated escape");
				}
				final char escape = next();
				switch (escape) {
					case '"', '\\', '/' -> builder.append(escape);
					case 'b' -> builder.append('\b');
					case 'f' -> builder.append('\f');
					case 'n' -> builder.append('\n');
					case 'r' -> builder.append('\r');
					case 't' -> builder.append('\t');
					case 'u' -> {
						if (index + 4 > text.length()) {
							throw error("Unterminated unicode escape");
						}
						final String hex = text.substring(index, index + 4);
						for (int i = 0; i < 4; i++) {
							if (Character.digit(hex.charAt(i), 16) < 0) {
								throw error("Invalid unicode escape");
							}
						}
						builder.append((char) Integer.parseInt(hex, 16));
						for (int i = 0; i < 4; i++) {
							next();
						}
					}
					default -> throw error("Invalid escape '\\" + escape + "'");
				}
			} else if (c < ' ') {
				throw error("Control character in string");
			} else {
				builder.append(c);
			}
		}
		throw error("Unterminated string");
	}

	private JsonPrimitive number() throws SyntaxError {
		final int start = index;
		if (take('-') && atEnd()) {
			throw error("Incomplete number");
		}
		if (take('0')) {
			// A leading zero is only valid alone.
		} else {
			while (!atEnd() && isDigit(peek())) {
				next();
			}
		}
		boolean integer = true;
		if (take('.')) {
			integer = false;
			if (atEnd() || !isDigit(peek())) {
				throw error("Expected a digit after the decimal point");
			}
			while (!atEnd() && isDigit(peek())) {
				next();
			}
		}
		if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
			integer = false;
			next();
			if (!atEnd() && (peek() == '+' || peek() == '-')) {
				next();
			}
			if (atEnd() || !isDigit(peek())) {
				throw error("Expected a digit in the exponent");
			}
			while (!atEnd() && isDigit(peek())) {
				next();
			}
		}
		final String token = text.substring(start, index);
		try {
			if (integer) {
				return new JsonPrimitive(Long.parseLong(token));
			}
			final double value = Double.parseDouble(token);
			if (!Double.isFinite(value)) {
				throw error("Number is too large");
			}
			return new JsonPrimitive(value);
		} catch (NumberFormatException e) {
			throw error("Invalid number '" + token + "'");
		}
	}

	private JsonElement literal(String word, JsonElement value) throws SyntaxError {
		for (int i = 0; i < word.length(); i++) {
			if (atEnd() || next() != word.charAt(i)) {
				throw error("Expected '" + word + "'");
			}
		}
		return value;
	}

	private void skipWhitespace() {
		while (!atEnd()) {
			final char c = peek();
			if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
				next();
			} else {
				return;
			}
		}
	}

	private void expect(char wanted) throws SyntaxError {
		if (atEnd()) {
			throw error("Expected '" + wanted + "'");
		}
		final char c = next();
		if (c != wanted) {
			throw error("Expected '" + wanted + "' but found '" + c + "'");
		}
	}

	private boolean take(char wanted) {
		if (!atEnd() && peek() == wanted) {
			next();
			return true;
		}
		return false;
	}

	private char peek() {
		return text.charAt(index);
	}

	private char next() {
		final char c = text.charAt(index++);
		if (c == '\n') {
			line++;
			column = 1;
		} else {
			column++;
		}
		return c;
	}

	private boolean atEnd() {
		return index >= text.length();
	}

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private SyntaxError error(String message) {
		return new SyntaxError(message, line, Math.max(1, column));
	}
}
