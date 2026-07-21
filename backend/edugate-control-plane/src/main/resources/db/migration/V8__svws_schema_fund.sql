-- Sync-/Fund-Daten aus der SVWS-Privileged-API "GET /api/schema/liste/svws" (ADR-014, Stufe 1:
-- Read-only Sync). Persistiert die zuletzt bekannten SVWS-Schemata je svws_instanz, damit die
-- Instanzsicht (ADR-013) die gespeicherten Sync-Ergebnisse anzeigen kann, statt bei jedem
-- Seitenaufruf live gegen den SVWS-Server zu sprechen (ADR-012 "Nutze vorhandene Sync-/
-- Fund-Daten").
--
-- Wie svws_instanz (ADR-011) eine mandantenübergreifende Betriebsressource ohne tenant_id: Ein
-- Fund gehört zunächst nur zur Instanz, nicht zu einem Schulträger. Die fachliche Zuordnung zu
-- einer Schule (und damit einem Schulträger) entsteht erst über den in einem späteren Auftrag
-- umzusetzenden Zuordnungsworkflow (ADR-012 "Unzugeordnete und importierte Schemata") - hier wird
-- nur der reine Fund persistiert, keine geratene Zuordnung.
CREATE TABLE svws_schema_fund (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instanz_id       uuid NOT NULL REFERENCES svws_instanz (id) ON DELETE CASCADE,
    schema_name      text NOT NULL,
    username         text NOT NULL,
    is_svws          boolean,
    revision         bigint,
    is_tainted       boolean,
    is_in_config     boolean,
    is_deactivated   boolean,
    first_seen_at    timestamptz NOT NULL DEFAULT now(),
    last_seen_at     timestamptz NOT NULL DEFAULT now(),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT svws_schema_fund_schema_name_not_blank CHECK (btrim(schema_name) <> ''),
    CONSTRAINT svws_schema_fund_username_not_blank CHECK (btrim(username) <> ''),
    CONSTRAINT svws_schema_fund_instanz_id_schema_name_unique UNIQUE (instanz_id, schema_name)
);

-- ============================================================================
-- Row-Level Security: identisches Muster zu svws_instanz (ADR-011/V2) - ausschließlich über den
-- auditierten Operator-Pfad erreichbar. edugate_control erhält bewusst keine Policy/keine
-- Tabellenrechte (Sync ist ausschließlich eine Betreiber-Operation über OperatorAccess, ADR-009).
-- edugate_gateway_ro braucht (anders als bei svws_instanz) keine Read-Policy: Funde sind reine
-- Sync-/Review-Daten für die Admin-UI, keine Grundlage für das spätere Gateway-Routing (ADR-011).
-- ============================================================================
ALTER TABLE svws_schema_fund ENABLE ROW LEVEL SECURITY;
ALTER TABLE svws_schema_fund FORCE ROW LEVEL SECURITY;

CREATE POLICY svws_schema_fund_operator_all ON svws_schema_fund
    FOR ALL TO edugate_operator USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON svws_schema_fund TO edugate_operator;
