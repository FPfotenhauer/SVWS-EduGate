-- Ergänzt svws_instanz um Beschreibung, Verbindungstest-Nachweis und Eindeutigkeit der
-- Kurzbezeichnung (name). Ändert weder tenant_id-Zuordnung noch RLS-Policies: Beide bleiben
-- unverändert aus V2 (ADR-011) - neue Spalten sind über die bestehenden, tabellenweiten
-- GRANTs und die unqualifizierten (USING (true)) Policies bereits abgedeckt.

ALTER TABLE svws_instanz
    ADD COLUMN beschreibung                text,
    ADD COLUMN credentials_updated_at      timestamptz,
    ADD COLUMN last_connection_test_at     timestamptz,
    ADD COLUMN last_connection_test_success boolean,
    ADD COLUMN last_connection_test_message text;

-- beschreibung ist optional (Freitext), darf aber nicht auf einen reinen Whitespace-Wert
-- gesetzt werden - analog zu svws_instanz_name_not_blank/svws_instanz_base_url_not_blank.
ALTER TABLE svws_instanz
    ADD CONSTRAINT svws_instanz_beschreibung_not_blank
        CHECK (beschreibung IS NULL OR btrim(beschreibung) <> '');

-- Kurzbezeichnung (name) muss eindeutig sein, analog zur bereits bestehenden Eindeutigkeit
-- von base_url (V2). Da svws_instanz laut ADR-011 keiner Schulträger-Wurzel unterliegt, ist
-- "eindeutig" hier zwangsläufig global gemeint, nicht je Schulträger.
ALTER TABLE svws_instanz
    ADD CONSTRAINT svws_instanz_name_unique UNIQUE (name);
