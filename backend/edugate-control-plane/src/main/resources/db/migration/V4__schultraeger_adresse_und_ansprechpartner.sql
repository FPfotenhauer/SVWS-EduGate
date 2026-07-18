-- Erweitert schultraeger um Adresse/Beschreibung und führt ansprechpartner als erste echte
-- tenant-gebundene Kind-Entität ein (ADR-009: "Schulen" als Beispielfall für Ressourcen
-- unterhalb der Mandanten-Wurzel, verwaltet über edugate_control statt OperatorAccess).

-- ============================================================================
-- 1. schultraeger: Adresse und Beschreibung (optional, analog svws_instanz.beschreibung)
-- ============================================================================
ALTER TABLE schultraeger
    ADD COLUMN strasse      text,
    ADD COLUMN plz          text,
    ADD COLUMN ort          text,
    ADD COLUMN beschreibung text;

ALTER TABLE schultraeger
    ADD CONSTRAINT schultraeger_strasse_not_blank CHECK (strasse IS NULL OR btrim(strasse) <> ''),
    ADD CONSTRAINT schultraeger_plz_not_blank CHECK (plz IS NULL OR btrim(plz) <> ''),
    ADD CONSTRAINT schultraeger_ort_not_blank CHECK (ort IS NULL OR btrim(ort) <> ''),
    ADD CONSTRAINT schultraeger_beschreibung_not_blank CHECK (beschreibung IS NULL OR btrim(beschreibung) <> '');

-- ============================================================================
-- 2. ansprechpartner: tenant-gebundene Fachtabelle, gleiches Muster wie schule (V1)
-- ============================================================================
CREATE TABLE ansprechpartner (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid NOT NULL REFERENCES schultraeger (id),
    name               text NOT NULL,
    vorname            text NOT NULL,
    titel              text,
    abteilung          text,
    funktion           text,
    email              text,
    telefon_festnetz   text,
    telefon_mobil      text,
    beschreibung       text,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ansprechpartner_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ansprechpartner_vorname_not_blank CHECK (btrim(vorname) <> '')
);
CREATE INDEX ansprechpartner_tenant_id_idx ON ansprechpartner (tenant_id);

ALTER TABLE ansprechpartner ENABLE ROW LEVEL SECURITY;
ALTER TABLE ansprechpartner FORCE ROW LEVEL SECURITY;

-- Tenant-Policy (ADR-002/ADR-008-Muster): edugate_control sieht/ändert nur Zeilen des per
-- SET LOCAL edugate.tenant_id gesetzten Mandanten.
CREATE POLICY ansprechpartner_tenant ON ansprechpartner
    USING (tenant_id = current_setting('edugate.tenant_id', true)::uuid);

-- Operator-Policy (ADR-009): unverändert auch für den Dienstleister-Admin-Vollzugriff verfügbar,
-- falls künftig eine mandantenübergreifende Auswertung nötig wird.
CREATE POLICY ansprechpartner_operator_all ON ansprechpartner
    FOR ALL TO edugate_operator USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON ansprechpartner TO edugate_control;
GRANT SELECT, INSERT, UPDATE, DELETE ON ansprechpartner TO edugate_operator;
