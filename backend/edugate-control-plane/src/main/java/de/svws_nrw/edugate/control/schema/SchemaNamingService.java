package de.svws_nrw.edugate.control.schema;

import de.svws_nrw.edugate.core.domain.SchemaNamingKonstanten;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * EduGate-Standardnamenskonvention für Schemata/Schuldatenbanken (ADR-012):
 * {@code <schulnummer>} für die Produktiv-Umgebung, {@code <schulnummer>_<umgebung>} für alle
 * anderen Umgebungen - ohne technisches {@code svws_}-Präfix. Schulen ohne belastbare
 * Schulnummer (ADR-012: "in der Regel sechsstellig") erhalten bewusst <b>keinen</b> Vorschlag:
 * Der Sonderpfad verlangt einen explizit vergebenen technischen Schlüssel statt einer stillen
 * Ableitung aus Freitext (z. B. dem Schulnamen).
 */
@ApplicationScoped
public class SchemaNamingService {

    private static final Pattern BELASTBARE_SCHULNUMMER = Pattern.compile("^\\d{6}$");

    /** Ob {@code schulnummer} für eine automatische Namensableitung tragfähig ist (ADR-012). */
    public boolean istBelastbareSchulnummer(final String schulnummer) {
        return schulnummer != null && BELASTBARE_SCHULNUMMER.matcher(schulnummer.trim()).matches();
    }

    /**
     * Schlägt einen Schemanamen nach der Standardkonvention vor. Liefert {@link Optional#empty()},
     * wenn die Schulnummer nicht belastbar ist - der Aufrufer muss dann einen expliziten
     * technischen Schlüssel verlangen statt selbst aus Freitext zu raten.
     */
    public Optional<String> vorschlagen(final String schulnummer, final String umgebung) {
        if (!istBelastbareSchulnummer(schulnummer) || umgebung == null || umgebung.isBlank()) {
            return Optional.empty();
        }
        final String normalisierteUmgebung = normalisieren(umgebung);
        final String trimmedNummer = schulnummer.trim();
        if (SchemaNamingKonstanten.PRODUKTIV.equals(normalisierteUmgebung)) {
            return Optional.of(trimmedNummer);
        }
        return Optional.of(trimmedNummer + "_" + normalisierteUmgebung.toLowerCase(Locale.ROOT));
    }

    /** Normalisiert eine Umgebungsangabe auf die kanonische Großschreibung (ohne Enum-Schranke). */
    public String normalisieren(final String umgebung) {
        return umgebung.trim().toUpperCase(Locale.ROOT);
    }
}
