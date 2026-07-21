package de.svws_nrw.edugate.core.svws;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimaler, abhängigkeitsfreier JSON-Parser für Antworten der SVWS-Privileged-API.
 * {@code edugate-core} hat bewusst keine Quarkus-/Jackson-Laufzeitabhängigkeiten (siehe
 * README), analog zur handgebauten JSON-Body-Erzeugung in {@link HttpSvwsConnectionTester}.
 *
 * <p>Unterstützt den vollen, aber bewusst einfachen JSON-Wertebaum ({@code object}, {@code array},
 * {@code string}, {@code number}, {@code boolean}, {@code null}) - ausreichend für die
 * Privileged-API-Antworten (z. B. {@code SchemaListeEintrag}-Arrays), ohne Anspruch auf
 * Vollständigkeit gegenüber einer allgemeinen JSON-Bibliothek. Zahlen werden immer als
 * {@link Double} geliefert; Objekte als {@link Map}, Arrays als {@link List}.
 */
public final class MinimalJsonParser {

    private final String input;
    private int pos;

    private MinimalJsonParser(final String input) {
        this.input = input;
        this.pos = 0;
    }

    /** Parst {@code input} vollständig zu einem JSON-Wert; wirft {@link JsonParseException} bei ungültiger Eingabe. */
    public static Object parse(final String input) {
        final MinimalJsonParser parser = new MinimalJsonParser(input);
        parser.skipWhitespace();
        final Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos != parser.input.length()) {
            throw new JsonParseException("Unerwartete Zeichen nach dem JSON-Wert.");
        }
        return value;
    }

    private Object parseValue() {
        if (pos >= input.length()) {
            throw new JsonParseException("Unerwartetes Ende der JSON-Eingabe.");
        }
        final char c = input.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        expect('{');
        final Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            final String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            result.put(key, parseValue());
            skipWhitespace();
            final char next = peek();
            if (next == ',') {
                pos++;
                continue;
            }
            if (next == '}') {
                pos++;
                return result;
            }
            throw new JsonParseException("Erwartete ',' oder '}' in JSON-Objekt.");
        }
    }

    private List<Object> parseArray() {
        expect('[');
        final List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            result.add(parseValue());
            skipWhitespace();
            final char next = peek();
            if (next == ',') {
                pos++;
                continue;
            }
            if (next == ']') {
                pos++;
                return result;
            }
            throw new JsonParseException("Erwartete ',' oder ']' in JSON-Array.");
        }
    }

    private String parseString() {
        expect('"');
        final StringBuilder result = new StringBuilder();
        while (true) {
            if (pos >= input.length()) {
                throw new JsonParseException("Unterminierter JSON-String.");
            }
            final char c = input.charAt(pos++);
            if (c == '"') {
                return result.toString();
            }
            if (c == '\\') {
                if (pos >= input.length()) {
                    throw new JsonParseException("Unterminiertes Escape in JSON-String.");
                }
                final char escaped = input.charAt(pos++);
                switch (escaped) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (pos + 4 > input.length()) {
                            throw new JsonParseException("Unvollständiges Unicode-Escape in JSON-String.");
                        }
                        final String hex = input.substring(pos, pos + 4);
                        pos += 4;
                        result.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw new JsonParseException("Ungültiges Escape-Zeichen im JSON-String.");
                }
            } else {
                result.append(c);
            }
        }
    }

    private Boolean parseBoolean() {
        if (input.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (input.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new JsonParseException("Ungültiger boolescher JSON-Wert.");
    }

    private Object parseNull() {
        if (input.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new JsonParseException("Ungültiger JSON-Wert.");
    }

    private Double parseNumber() {
        final int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (pos < input.length() && isNumberChar(input.charAt(pos))) {
            pos++;
        }
        if (pos == start || (pos == start + 1 && input.charAt(start) == '-')) {
            throw new JsonParseException("Ungültiger numerischer JSON-Wert.");
        }
        try {
            return Double.parseDouble(input.substring(start, pos));
        } catch (final NumberFormatException e) {
            throw new JsonParseException("Ungültiger numerischer JSON-Wert.");
        }
    }

    private boolean isNumberChar(final char c) {
        return Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
    }

    private void expect(final char c) {
        if (pos >= input.length() || input.charAt(pos) != c) {
            throw new JsonParseException("Erwartete '" + c + "' an Position " + pos + ".");
        }
        pos++;
    }

    private char peek() {
        if (pos >= input.length()) {
            throw new JsonParseException("Unerwartetes Ende der JSON-Eingabe.");
        }
        return input.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }
}
