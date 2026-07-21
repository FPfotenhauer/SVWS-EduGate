package de.svws_nrw.edugate.control.svwsinstanz;

/**
 * Klassifiziert einen {@code svws_schema_fund}-Eintrag gegenüber dem EduGate-eigenen
 * {@code schema}-Bestand (ADR-012 "Unzugeordnete und importierte Schemata", ADR-013 "Betreiber
 * müssen bekannte EduGate-Schemata, unzugeordnete Funde und Konflikte klar unterscheidbar
 * sehen"). Wird beim Lesen berechnet, nicht persistiert.
 */
public enum SchemaFundZuordnungsStatus {
    /** Es existiert ein EduGate-{@code schema}-Datensatz für (Instanz, Schemaname), Zustände stimmen überein. */
    BEKANNT,
    /** Kein EduGate-{@code schema}-Datensatz für (Instanz, Schemaname) - noch keiner Schule zugeordnet. */
    UNZUGEORDNET,
    /**
     * Es existiert ein EduGate-{@code schema}-Datensatz, aber der Aktiv-/Deaktiviert-Zustand
     * weicht vom SVWS-Fund ab (z. B. EduGate zeigt das Schema als aktiv, SVWS meldet
     * {@code isDeactivated=true}, oder umgekehrt).
     */
    KONFLIKT
}
