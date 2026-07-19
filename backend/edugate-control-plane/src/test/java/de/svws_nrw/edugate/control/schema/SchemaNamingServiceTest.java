package de.svws_nrw.edugate.control.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit-Tests der EduGate-Standardnamenskonvention für Schemata (ADR-012): {@code <schulnummer>}
 * für Produktiv, {@code <schulnummer>_<umgebung>} für alle anderen Umgebungen, kein Vorschlag bei
 * nicht belastbarer (nicht sechsstelliger) Schulnummer.
 */
class SchemaNamingServiceTest {

    private final SchemaNamingService service = new SchemaNamingService();

    @Test
    void produktivBekommtKeinenSuffix() {
        assertThat(service.vorschlagen("123456", "PRODUKTIV")).contains("123456");
    }

    @Test
    void nichtProduktivBekommtKleingeschriebenenSuffix() {
        assertThat(service.vorschlagen("123456", "TEST")).contains("123456_test");
        assertThat(service.vorschlagen("123456", "Schulung")).contains("123456_schulung");
    }

    @Test
    void umgebungWirdVorDemVergleichNormalisiert() {
        assertThat(service.vorschlagen("123456", "produktiv")).contains("123456");
        assertThat(service.vorschlagen("123456", "  test  ")).contains("123456_test");
    }

    @Test
    void nichtBelastbareSchulnummerLiefertKeinenVorschlag() {
        assertThat(service.vorschlagen("12345", "PRODUKTIV")).isEmpty();
        assertThat(service.vorschlagen("1234567", "PRODUKTIV")).isEmpty();
        assertThat(service.vorschlagen("ABCDEF", "PRODUKTIV")).isEmpty();
        assertThat(service.vorschlagen(null, "PRODUKTIV")).isEmpty();
        assertThat(service.vorschlagen("", "PRODUKTIV")).isEmpty();
    }

    @Test
    void istBelastbareSchulnummerErkenntGenauSechsstelligeZiffernfolgen() {
        assertThat(service.istBelastbareSchulnummer("123456")).isTrue();
        assertThat(service.istBelastbareSchulnummer(" 123456 ")).isTrue();
        assertThat(service.istBelastbareSchulnummer("12345")).isFalse();
        assertThat(service.istBelastbareSchulnummer("1234567")).isFalse();
        assertThat(service.istBelastbareSchulnummer("12345A")).isFalse();
        assertThat(service.istBelastbareSchulnummer(null)).isFalse();
    }

    @Test
    void leereUmgebungLiefertKeinenVorschlag() {
        final Optional<String> vorschlag = service.vorschlagen("123456", "   ");
        assertThat(vorschlag).isEmpty();
    }
}
