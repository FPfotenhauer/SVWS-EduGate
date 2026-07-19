-- Verwaltete Liste der Schema-Umgebungen (Betreiber-Einstellungen).
--
-- ADR-012 verlangt, dass "Umgebung" (Produktiv/Test/Schulung/...) kein geschlossenes Enum ist,
-- sondern durch den Betreiber konfigurierbar bleibt ("Betreiber können die Konvention später
-- konfigurieren oder durch Regeln erweitern"). Bisher war das ein rein clientseitiger
-- Vorschlagswert (STANDARD_UMGEBUNGEN); diese Migration macht daraus eine echte, vom
-- Dienstleister-Admin gepflegte Stammdatentabelle.
--
-- schema_umgebung ist wie svws_instanz (ADR-011) eine mandantenübergreifende Betriebsressource,
-- keine tenant-gebundene Fachtabelle: Die Namenskonvention ist eine Betreiber-Konvention, nicht
-- je Schulträger unterschiedlich. Sie folgt daher demselben RLS-/Grant-Muster wie svws_instanz -
-- ausschließlich über den auditierten Operator-Pfad verwaltet; edugate_control bekommt bewusst
-- keine Policy. Tenant-gebundene Lesezugriffe (z. B. das Schema-Anlegen-Formular) lesen über den
-- eigenen, für alle Dienstleister-Admins erreichbaren Read-Endpunkt (analog zur Instanzliste),
-- nicht über direkten DB-Zugriff aus dem tenant-gebundenen Pfad.
--
-- "PRODUKTIV" bleibt fachlich ausgezeichnet (ADR-012: kein Namenssuffix, höchstens ein aktives
-- Schema je Schule) und ist deshalb als system-Eintrag reserviert: Name und Aktiv-Status sind
-- unveränderlich, nur die Beschreibung darf angepasst werden (siehe SchemaUmgebungService).

CREATE TABLE schema_umgebung (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name         text NOT NULL,
    system       boolean NOT NULL DEFAULT false,
    beschreibung text,
    aktiv        boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT schema_umgebung_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT schema_umgebung_name_unique UNIQUE (name),
    CONSTRAINT schema_umgebung_beschreibung_not_blank
        CHECK (beschreibung IS NULL OR btrim(beschreibung) <> '')
);

ALTER TABLE schema_umgebung ENABLE ROW LEVEL SECURITY;
ALTER TABLE schema_umgebung FORCE ROW LEVEL SECURITY;

CREATE POLICY schema_umgebung_operator_all ON schema_umgebung
    FOR ALL TO edugate_operator USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON schema_umgebung TO edugate_operator;

-- Startwerte (ADR-012: Produktiv/Test/Schulung als Startwerte, frei erweiterbar).
INSERT INTO schema_umgebung (name, system, beschreibung) VALUES
    ('PRODUKTIV', true, 'Produktivbetrieb - je Schule höchstens ein aktives Schema (ADR-012).'),
    ('TEST', false, null),
    ('SCHULUNG', false, null);
