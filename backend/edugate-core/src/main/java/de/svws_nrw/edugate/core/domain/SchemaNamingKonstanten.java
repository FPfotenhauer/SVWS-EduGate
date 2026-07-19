package de.svws_nrw.edugate.core.domain;

import java.util.List;

/**
 * Konstanten der EduGate-Standardnamenskonvention für Schemata/Schuldatenbanken (ADR-012).
 * {@code umgebung} ist bewusst kein geschlossenes Enum, damit Betreiber sie später erweitern
 * können - {@link #PRODUKTIV} bleibt trotzdem als fachlich ausgezeichneter Wert benannt, weil die
 * Namenskonvention (kein Suffix) und die Regel "höchstens ein aktives Produktiv-Schema je Schule"
 * ausschließlich daran hängen. {@link #STANDARD_VORSCHLAEGE} sind Startwerte für die
 * Umgebungs-Auswahl in der Oberfläche, keine Einschränkung.
 */
public final class SchemaNamingKonstanten {

    /** Einzige fachlich ausgezeichnete Umgebung: keine Namenssuffix, höchstens ein aktives Schema je Schule. */
    public static final String PRODUKTIV = "PRODUKTIV";

    /** Startvorschläge für die Umgebungsauswahl (ADR-012); frei erweiterbar, keine Enum-Schranke. */
    public static final List<String> STANDARD_VORSCHLAEGE = List.of("PRODUKTIV", "TEST", "SCHULUNG");

    private SchemaNamingKonstanten() {
    }
}
