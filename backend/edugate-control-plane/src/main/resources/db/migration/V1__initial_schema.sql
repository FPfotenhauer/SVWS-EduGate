-- Initiales Schema für SVWS-EduGate (Control Plane).
--
-- Ausgeführt vom PostgreSQL-Superuser (Flyway-Migrationsdatasource), NICHT von
-- edugate_control/edugate_operator/edugate_gateway_ro: Diese drei Anwendungsrollen
-- bleiben bewusst Nicht-Owner, damit Rechteeinschränkungen (insb. das Append-only-Modell
-- von audit_admin) tatsächlich wirken – ein Table-Owner hat in PostgreSQL immer alle
-- Privilegien, unabhängig von GRANT/REVOKE.
--
-- Mandantenmodell gemäß ADR-002, präzisiert durch ADR-008 (Wurzeltabelle schultraeger)
-- und ADR-009 (Operator-Zugriff, Audit).

-- ============================================================================
-- 1. Fachtabellen
-- ============================================================================

-- Wurzel der Mandantenhierarchie. Trägt bewusst KEINE tenant_id-Spalte: die eigene
-- id IST die Tenant-ID (ADR-008).
CREATE TABLE schultraeger (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name           text NOT NULL,
    traegernummer  text NOT NULL,
    aktiv          boolean NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT schultraeger_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT schultraeger_traegernummer_not_blank CHECK (btrim(traegernummer) <> ''),
    CONSTRAINT schultraeger_traegernummer_unique UNIQUE (traegernummer)
);

CREATE TABLE schule (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL REFERENCES schultraeger (id),
    schulnummer      text NOT NULL,
    name             text NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT schule_schulnummer_not_blank CHECK (btrim(schulnummer) <> ''),
    CONSTRAINT schule_name_not_blank CHECK (btrim(name) <> '')
);
CREATE INDEX schule_tenant_id_schulnummer_idx ON schule (tenant_id, schulnummer);

CREATE TABLE svws_instanz (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              uuid NOT NULL REFERENCES schultraeger (id),
    base_url               text NOT NULL,
    status                 text NOT NULL DEFAULT 'OK'
                               CHECK (status IN ('OK', 'DEGRADED', 'UNREACHABLE')),
    credentials_encrypted  bytea,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT svws_instanz_base_url_not_blank CHECK (btrim(base_url) <> '')
);
CREATE INDEX svws_instanz_tenant_id_idx ON svws_instanz (tenant_id);

CREATE TABLE schema (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL REFERENCES schultraeger (id),
    schule_id    uuid NOT NULL REFERENCES schule (id),
    instanz_id   uuid NOT NULL REFERENCES svws_instanz (id),
    schema_name  text NOT NULL,
    umgebung     text NOT NULL CHECK (umgebung IN ('PRODUKTIV', 'TEST')),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT schema_schema_name_not_blank CHECK (btrim(schema_name) <> '')
);
CREATE INDEX schema_tenant_id_idx ON schema (tenant_id, schule_id);

-- Admin-Audit gemäß ADR-009. Bewusst OHNE Row-Level-Security: audit_admin ist per
-- Definition mandantenübergreifend einsehbar (Dienstleister-Admin-Sicht) und enthält
-- absichtlich NULL-tenant_id-Zeilen (z. B. SCHULTRAEGER_LIST). Die ADR-008-Wächterregel
-- "jede Tabelle mit tenant_id-Spalte braucht RLS" bezieht sich auf die mandantenbezogenen
-- Fachtabellen; audit_admin ist explizit ausgenommen (siehe ADR-008-Präzisierung).
-- Isolation erfolgt stattdessen ausschließlich über Tabellenrechte: Anwendungsrollen
-- erhalten unten nur INSERT und SELECT, kein UPDATE/DELETE (Append-only).
CREATE TABLE audit_admin (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at    timestamptz NOT NULL DEFAULT now(),
    admin_subject  text NOT NULL,
    action         text NOT NULL,
    entity_type    text NOT NULL,
    entity_id      uuid,
    tenant_id      uuid,
    outcome        text NOT NULL CHECK (outcome IN ('SUCCESS', 'DENIED', 'ERROR')),
    details        jsonb
);
CREATE INDEX audit_admin_occurred_at_idx ON audit_admin (occurred_at);

-- ============================================================================
-- 2. Row-Level Security (ADR-002/ADR-008/ADR-009)
-- ============================================================================

-- --- schultraeger: Wurzel-Policy auf die eigene id (ADR-008) -----------------
ALTER TABLE schultraeger ENABLE ROW LEVEL SECURITY;
ALTER TABLE schultraeger FORCE ROW LEVEL SECURITY;

CREATE POLICY schultraeger_tenant ON schultraeger
    USING (id = current_setting('edugate.tenant_id', true)::uuid);

CREATE POLICY schultraeger_operator_all ON schultraeger
    FOR ALL TO edugate_operator USING (true) WITH CHECK (true);

-- --- schule -------------------------------------------------------------
ALTER TABLE schule ENABLE ROW LEVEL SECURITY;
ALTER TABLE schule FORCE ROW LEVEL SECURITY;

CREATE POLICY schule_tenant ON schule
    USING (tenant_id = current_setting('edugate.tenant_id', true)::uuid);

CREATE POLICY schule_operator_all ON schule
    FOR ALL TO edugate_operator USING (true) WITH CHECK (true);

-- --- svws_instanz ---------------------------------------------------------
ALTER TABLE svws_instanz ENABLE ROW LEVEL SECURITY;
ALTER TABLE svws_instanz FORCE ROW LEVEL SECURITY;

CREATE POLICY svws_instanz_tenant ON svws_instanz
    USING (tenant_id = current_setting('edugate.tenant_id', true)::uuid);

CREATE POLICY svws_instanz_operator_all ON svws_instanz
    FOR ALL TO edugate_operator USING (true) WITH CHECK (true);

-- --- schema ---------------------------------------------------------------
ALTER TABLE schema ENABLE ROW LEVEL SECURITY;
ALTER TABLE schema FORCE ROW LEVEL SECURITY;

CREATE POLICY schema_tenant ON schema
    USING (tenant_id = current_setting('edugate.tenant_id', true)::uuid);

CREATE POLICY schema_operator_all ON schema
    FOR ALL TO edugate_operator USING (true) WITH CHECK (true);

-- ============================================================================
-- 3. Tabellenrechte
-- ============================================================================

-- edugate_control: CRUD auf den Fachtabellen (unterliegt RLS via *_tenant-Policies);
-- Admin-Audit nur INSERT/SELECT (append-only).
GRANT SELECT, INSERT, UPDATE, DELETE ON schultraeger, schule, svws_instanz, schema TO edugate_control;
GRANT INSERT, SELECT ON audit_admin TO edugate_control;

-- edugate_operator: CRUD auf den Fachtabellen ausschließlich über die *_operator_all-
-- Policies (kein BYPASSRLS, ADR-009); Admin-Audit nur INSERT/SELECT (append-only).
GRANT SELECT, INSERT, UPDATE, DELETE ON schultraeger, schule, svws_instanz, schema TO edugate_operator;
GRANT INSERT, SELECT ON audit_admin TO edugate_operator;

-- edugate_gateway_ro: nur lesend, unterliegt RLS (ADR-001).
GRANT SELECT ON schultraeger, schule, svws_instanz, schema TO edugate_gateway_ro;

-- Kein UPDATE/DELETE-Grant auf audit_admin für irgendeine Anwendungsrolle: Append-only
-- ist damit für edugate_control und edugate_operator erzwungen (beide sind nicht Owner).
