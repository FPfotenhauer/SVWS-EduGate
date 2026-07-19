-- Schemaverwaltung und Schuldatenbanken (ADR-012).
--
-- `schema` bleibt tenant-gebunden (RLS/Grants aus V1 unverändert, ADR-012 bestätigt genau das
-- gegenüber ADR-011: nur svws_instanz ist mandantenübergreifend, schema nicht). Diese Migration
-- ergänzt die fachlichen Felder aus ADR-012 (Status, Aktiv-Flag, Beschreibung, Herkunft) und die
-- Geschäftsregeln (Eindeutigkeit des Schemanamens je Instanz, höchstens ein aktives
-- Produktiv-Schema je Schule). `umgebung` wird bewusst von einem geschlossenen CHECK
-- ('PRODUKTIV'|'TEST') auf freien Text umgestellt: ADR-012 verlangt erweiterbare Umgebungen
-- (SCHULUNG, ABNAHME, MIGRATION, ARCHIV, DEMO, lokale Varianten) ohne dass jede neue Umgebung
-- eine Migration braucht. Nur "PRODUKTIV" bleibt als Konstante fachlich ausgezeichnet (Namens­-
-- konvention ohne Suffix, Ein-Produktiv-Schema-Regel) - siehe SchemaNamingKonstanten.

-- ============================================================================
-- 1. umgebung: geschlossenes CHECK entfernen, auf freien (normalisierten) Text umstellen
-- ============================================================================
ALTER TABLE schema DROP CONSTRAINT schema_umgebung_check;

UPDATE schema SET umgebung = upper(btrim(umgebung));

ALTER TABLE schema
    ADD CONSTRAINT schema_umgebung_not_blank CHECK (btrim(umgebung) <> '');

-- ============================================================================
-- 2. Neue Verwaltungsfelder (ADR-012: status, aktiv, beschreibung, source, lastSyncedAt)
-- ============================================================================
ALTER TABLE schema
    ADD COLUMN status       text NOT NULL DEFAULT 'GEPLANT'
                                CHECK (status IN ('GEPLANT', 'VORHANDEN', 'AKTIV', 'DEAKTIVIERT',
                                                   'MIGRATION_ERFORDERLICH', 'FEHLER', 'ARCHIVIERT')),
    ADD COLUMN aktiv        boolean NOT NULL DEFAULT true,
    ADD COLUMN beschreibung text,
    ADD COLUMN source       text NOT NULL DEFAULT 'MANUELL' CHECK (source IN ('MANUELL', 'SYNCHRONISIERT')),
    ADD COLUMN last_synced_at timestamptz;

ALTER TABLE schema
    ADD CONSTRAINT schema_beschreibung_not_blank CHECK (beschreibung IS NULL OR btrim(beschreibung) <> '');

-- ============================================================================
-- 3. Geschäftsregeln aus ADR-012 als DB-Constraints (zweite Verteidigungslinie neben der Service-Ebene)
-- ============================================================================

-- schema_name ist technisch pro svws_instanz eindeutig.
ALTER TABLE schema
    ADD CONSTRAINT schema_instanz_id_schema_name_unique UNIQUE (instanz_id, schema_name);

-- Eine Schule darf höchstens ein aktives Produktiv-Schema haben; mehrere aktive Nicht-Produktiv-
-- Schemata (Test, Schulung, ...) und beliebig viele inaktive/archivierte Schemata bleiben erlaubt.
CREATE UNIQUE INDEX schema_schule_id_aktives_produktiv_unique ON schema (schule_id)
    WHERE aktiv AND umgebung = 'PRODUKTIV';
