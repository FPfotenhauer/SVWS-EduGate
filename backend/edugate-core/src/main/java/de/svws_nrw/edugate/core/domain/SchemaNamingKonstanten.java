package de.svws_nrw.edugate.core.domain;

/**
 * Konstanten der EduGate-Standardnamenskonvention für Schemata/Schuldatenbanken (ADR-012).
 * {@code umgebung} ist bewusst kein geschlossenes Enum, damit Betreiber sie später erweitern
 * können - {@link #PRODUKTIV} bleibt trotzdem als fachlich ausgezeichneter Wert benannt, weil die
 * Namenskonvention (kein Suffix) und die Regel "höchstens ein aktives Produktiv-Schema je Schule"
 * ausschließlich daran hängen. Startwerte für die Umgebungsauswahl selbst sind seit der
 * Betreiber-Einstellungsseite "Umgebungen verwalten" keine Konstante mehr, sondern die Tabelle
 * {@code schema_umgebung} (Migration V7, verwaltet über {@code SchemaUmgebungService}).
 */
public final class SchemaNamingKonstanten {

    /** Einzige fachlich ausgezeichnete Umgebung: keine Namenssuffix, höchstens ein aktives Schema je Schule. */
    public static final String PRODUKTIV = "PRODUKTIV";

    private SchemaNamingKonstanten() {
    }
}
