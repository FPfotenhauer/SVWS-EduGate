-- Landes-Schuldatei als Referenzkatalog (ADR-020, Nachtrag "Grundlagenrecherche vor Umsetzung").
--
-- schultraeger_katalog und schule_katalog sind wie svws_instanz/schema_umgebung/svws_schema_fund
-- mandantenübergreifende Betriebsressourcen (ADR-011-Muster): keine tenant_id, RLS nur mit
-- Operator-Policy, kein Zugriff für edugate_control/edugate_gateway_ro. Die Verknüpfung
-- schule_katalog.schultraegernummer -> schultraeger_katalog.traegernummer ist eine fachliche
-- Referenz ohne erzwingenden Fremdschlüssel, damit ein Schul-Datensatz auch importierbar bleibt,
-- falls der zugehörige Trägerdatensatz zum Importzeitpunkt fehlt oder verworfen wurde.
--
-- Kataloge werden per Upsert über den fachlichen Schlüssel (bundeslandkennung + Quellen-Nummer)
-- aktualisiert, nicht per Clear-and-Insert - siehe ADR-020-Nachtrag ("stabile Identität von
-- Katalogeinträgen über Refreshs hinweg", Konsequenz aus einer bei SVWS-Main-Server beobachteten
-- Schwäche). last_seen_at markiert implizit, ob ein Eintrag im letzten Refresh noch geliefert
-- wurde, ohne ihn zu löschen.

CREATE TABLE schultraeger_katalog (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bundeslandkennung  text NOT NULL,
    traegernummer      text NOT NULL,
    traegername        text NOT NULL,
    traegerschaftsart  text,
    strasse            text,
    plz                text,
    ort                text,
    aufloesung         date,
    last_seen_at       timestamptz NOT NULL DEFAULT now(),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT schultraeger_katalog_traegername_not_blank CHECK (btrim(traegername) <> ''),
    CONSTRAINT schultraeger_katalog_bundesland_traegernummer_unique UNIQUE (bundeslandkennung, traegernummer)
);

CREATE TABLE schule_katalog (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bundeslandkennung    text NOT NULL,
    schulnummer          text NOT NULL,
    schulname            text NOT NULL,
    schultraegernummer   text,
    schulform            text,
    strasse              text,
    plz                  text,
    ort                  text,
    kreis                text,
    telefon              text,
    fax                  text,
    email                text,
    homepage             text,
    aufloesung           date,
    last_seen_at         timestamptz NOT NULL DEFAULT now(),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT schule_katalog_schulname_not_blank CHECK (btrim(schulname) <> ''),
    CONSTRAINT schule_katalog_bundesland_schulnummer_unique UNIQUE (bundeslandkennung, schulnummer)
);
CREATE INDEX schule_katalog_schultraegernummer_idx ON schule_katalog (bundeslandkennung, schultraegernummer);

-- Import-Historie für die Schuldatei-Kachel (ADR-020: "Stand der zuletzt importierten/
-- synchronisierten Landes-Schuldatei", "Anzeige von Importfehlern"). Bewusst eine eigene
-- Tabelle statt Ableitung aus audit_admin, weil Zähler (anzahl_schulen/-schultraeger) dort nicht
-- abgebildet werden können - analog zu den eigenen last_connection_test_*-Spalten auf
-- svws_instanz statt einer Herleitung aus dem Audit-Log.
CREATE TABLE schuldatei_import (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    gestartet_am         timestamptz NOT NULL,
    beendet_am           timestamptz,
    erfolgreich          boolean NOT NULL,
    fehlermeldung        text,
    anzahl_schulen       integer,
    anzahl_schultraeger  integer,
    ausgeloest_von       text NOT NULL
);
CREATE INDEX schuldatei_import_gestartet_am_idx ON schuldatei_import (gestartet_am DESC);

-- ============================================================================
-- Row-Level Security: identisches Muster zu schema_umgebung/svws_schema_fund (ADR-011) -
-- ausschließlich über den auditierten Operator-Pfad erreichbar.
-- ============================================================================
ALTER TABLE schultraeger_katalog ENABLE ROW LEVEL SECURITY;
ALTER TABLE schultraeger_katalog FORCE ROW LEVEL SECURITY;
CREATE POLICY schultraeger_katalog_operator_all ON schultraeger_katalog
    FOR ALL TO edugate_operator USING (true) WITH CHECK (true);
GRANT SELECT, INSERT, UPDATE, DELETE ON schultraeger_katalog TO edugate_operator;

ALTER TABLE schule_katalog ENABLE ROW LEVEL SECURITY;
ALTER TABLE schule_katalog FORCE ROW LEVEL SECURITY;
CREATE POLICY schule_katalog_operator_all ON schule_katalog
    FOR ALL TO edugate_operator USING (true) WITH CHECK (true);
GRANT SELECT, INSERT, UPDATE, DELETE ON schule_katalog TO edugate_operator;

ALTER TABLE schuldatei_import ENABLE ROW LEVEL SECURITY;
ALTER TABLE schuldatei_import FORCE ROW LEVEL SECURITY;
CREATE POLICY schuldatei_import_operator_all ON schuldatei_import
    FOR ALL TO edugate_operator USING (true) WITH CHECK (true);
GRANT SELECT, INSERT, UPDATE, DELETE ON schuldatei_import TO edugate_operator;
