package de.svws_nrw.edugate.core.svws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Testet {@link MinimalJsonParser} gegen den JSON-Wertebaum, den die SVWS-Privileged-API
 * tatsächlich liefert (Arrays flacher {@code SchemaListeEintrag}-Objekte), plus grundlegende
 * Fehlerfälle.
 */
class MinimalJsonParserTest {

    @Test
    void parst_leeresArray() {
        assertThat(MinimalJsonParser.parse("[]")).isEqualTo(List.of());
    }

    @Test
    void parst_schemaListeEintragArray() {
        final String json = """
            [
              {"name":"123456","username":"123456","isSVWS":true,"revision":3,"isTainted":false,
               "isInConfig":true,"isDeactivated":false},
              {"name":"123456_test","username":"123456_test","isSVWS":true,"revision":2,
               "isTainted":true,"isInConfig":false,"isDeactivated":true}
            ]
            """;

        final Object result = MinimalJsonParser.parse(json);

        assertThat(result).isInstanceOf(List.class);
        final List<?> list = (List<?>) result;
        assertThat(list).hasSize(2);
        final Map<?, ?> first = (Map<?, ?>) list.get(0);
        assertThat(first.get("name")).isEqualTo("123456");
        assertThat(first.get("isSVWS")).isEqualTo(Boolean.TRUE);
        assertThat(first.get("revision")).isEqualTo(3.0);
    }

    @Test
    void parst_stringsMitEscapesUndUnicode() {
        final Object result = MinimalJsonParser.parse("\"a\\\"b\\\\c\\u00e4\"");
        assertThat(result).isEqualTo("a\"b\\cä");
    }

    @Test
    void parst_nullUndBooleans() {
        assertThat(MinimalJsonParser.parse("null")).isNull();
        assertThat(MinimalJsonParser.parse("true")).isEqualTo(Boolean.TRUE);
        assertThat(MinimalJsonParser.parse("false")).isEqualTo(Boolean.FALSE);
    }

    @Test
    void parst_negativeUndDezimalzahlen() {
        assertThat(MinimalJsonParser.parse("-3.5")).isEqualTo(-3.5);
    }

    @Test
    void wirftBeiUngueltigerEingabe() {
        assertThatThrownBy(() -> MinimalJsonParser.parse("{invalid")).isInstanceOf(JsonParseException.class);
    }

    @Test
    void wirftBeiUeberschuessigenZeichenNachDemWert() {
        assertThatThrownBy(() -> MinimalJsonParser.parse("[] extra")).isInstanceOf(JsonParseException.class);
    }

    @Test
    void wirftBeiUnterminiertemString() {
        assertThatThrownBy(() -> MinimalJsonParser.parse("\"abc")).isInstanceOf(JsonParseException.class);
    }
}
