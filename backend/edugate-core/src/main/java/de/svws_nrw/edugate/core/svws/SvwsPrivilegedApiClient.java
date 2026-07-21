package de.svws_nrw.edugate.core.svws;

/**
 * Port für gekapselte Aufrufe der SVWS-Privileged-/Root-API (ADR-014: "Alle echten SVWS-Aufrufe
 * laufen über dedizierte Ports/Adapter, z. B. {@code SvwsPrivilegedApiClient}"). Beginnt bewusst
 * klein mit read-only Endpunkten (ADR-014 Stufenmodell, Stufe 1: Sync-Liste; Schritt 2: optionale
 * Schulinformationen als Orientierung beim Zuordnen eines Funds); weitere privilegierte
 * Operationen (Anlegen, Deaktivieren, Migration, Export/Import) werden in späteren Aufträgen
 * ergänzt, sobald die jeweiligen geschützten Workflows dafür stehen.
 *
 * <p>Implementierungen werfen für Netzwerk-/Protokoll-/Parsingfehler niemals eine Exception,
 * sondern liefern immer ein sicheres {@code failure(...)}-Ergebnis mit einer sicheren,
 * deutschsprachigen Meldung ohne Secrets oder interne Details (ADR-006), analog zu
 * {@link SvwsConnectionTester}.
 */
public interface SvwsPrivilegedApiClient {

    /**
     * Ruft {@code GET /api/schema/liste/svws} auf und liefert die technische Liste der auf der
     * Instanz vorhandenen SVWS-Schemata (ADR-012/ADR-014: Grundlage für den Read-only-Sync-/
     * Fund-Workflow). Benötigt privilegierte Zugangsdaten mit root-Rechten auf der Datenbank.
     */
    SvwsSchemaListResult listSchemas(String baseUrl, String username, String password);

    /**
     * Ruft {@code GET /api/schema/liste/info/{schema}/schule} auf und liefert die im Schema
     * hinterlegten Schulstammdaten (ADR-014 Schritt 2: rein informativ beim Zuordnen eines
     * unzugeordneten Funds, niemals Grundlage einer automatischen Tenant-/Schulzuordnung).
     * Benötigt Datenbank-Rechte auf dem angegebenen Schema, nicht zwingend root-Rechte.
     */
    SvwsSchulInfoResult getSchulInfo(String baseUrl, String username, String password, String schemaName);
}
