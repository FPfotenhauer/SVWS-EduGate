-- SVWS-Instanz als geteilte, mandantenübergreifende Betriebsressource (ADR-011).
--
-- V1 modellierte svws_instanz fälschlich als gewöhnliche mandantenbezogene Fachtabelle
-- (tenant_id-Spalte, generische Tenant-RLS-Policy, ADR-002). Eine SVWS-Instanz ist jedoch eine
-- rein technische Betriebsressource des Dienstleisters: Mehrere Schulträger können dieselbe
-- Instanz über ihre jeweiligen Schemas gemeinsam nutzen. Die Mandantenzuordnung entsteht künftig
-- ausschließlich über schema.tenant_id/schema.schule_id/schema.instanz_id, nicht über eine
-- tenant_id auf der Instanz selbst. Siehe ADR-011 für die vollständige Begründung.

-- ============================================================================
-- 1. Row-Level Security: Tenant-Policy zuerst entfernen (ADR-011)
-- ============================================================================
-- Muss vor dem Entfernen der Spalte tenant_id geschehen: PostgreSQL löscht eine Policy nicht
-- automatisch, nur weil eine von ihr referenzierte Spalte gelöscht wird ("DROP COLUMN" bricht
-- sonst mit "other objects depend on it" ab). svws_instanz verliert die generische Tenant-Policy:
-- Es gibt keine tenant_id mehr, gegen die geprüft werden könnte. edugate_control
-- (Standard-Datasource, tenant-gebunden) erhält bewusst KEINE Policy für diese Tabelle - Zugriff
-- läuft ausschließlich über den auditierten Operator-Pfad (edugate_operator, OperatorAccess,
-- ADR-009), identisch zum Modell der Mandanten-Wurzel schultraeger (ADR-008).
-- edugate_gateway_ro erhält eine eigene, mandantenübergreifende Read-Policy für die spätere
-- Schema-/Routing-Auflösung (ADR-004). Die bestehende svws_instanz_operator_all-Policy
-- (FOR ALL TO edugate_operator, aus V1) bleibt unverändert bestehen.
DROP POLICY svws_instanz_tenant ON svws_instanz;

CREATE POLICY svws_instanz_gateway_read ON svws_instanz
    FOR SELECT TO edugate_gateway_ro USING (true);

-- ============================================================================
-- 2. Fachtabelle: tenant_id entfernen, Verwaltungsfelder ergänzen
-- ============================================================================

-- Entfernt zusammen mit der Spalte automatisch die zugehörige (inline deklarierte) FK-Constraint
-- auf schultraeger(id); der explizite Index muss separat entfernt werden.
DROP INDEX svws_instanz_tenant_id_idx;
ALTER TABLE svws_instanz DROP COLUMN tenant_id;

-- name: Default nur für die Migration selbst (falls bereits Zeilen ohne Namen existieren sollten);
-- unmittelbar danach entfernt, damit künftige INSERTs einen Namen liefern müssen.
ALTER TABLE svws_instanz
    ADD COLUMN name  text NOT NULL DEFAULT '',
    ADD COLUMN aktiv boolean NOT NULL DEFAULT true;

ALTER TABLE svws_instanz ALTER COLUMN name DROP DEFAULT;

ALTER TABLE svws_instanz
    ADD CONSTRAINT svws_instanz_name_not_blank CHECK (btrim(name) <> ''),
    ADD CONSTRAINT svws_instanz_base_url_unique UNIQUE (base_url);

-- ============================================================================
-- 3. Tabellenrechte: edugate_control verliert den Zugriff (Least Privilege, ADR-011)
-- ============================================================================
-- edugate_control wird von der Control-Plane-Anwendung für svws_instanz nicht mehr verwendet
-- (alle Operationen laufen über OperatorAccess/edugate_operator); der Grant wird konsequent
-- entzogen, damit RLS ohne Policy für diese Rolle nicht nur theoretisch, sondern auch über die
-- Tabellenrechte zusätzlich abgesichert ist.
REVOKE SELECT, INSERT, UPDATE, DELETE ON svws_instanz FROM edugate_control;
