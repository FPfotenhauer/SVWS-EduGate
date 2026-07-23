-- ADR-020: Analog zu V10 (Schulträger) bekommt jetzt auch die operative schule-Tabelle die
-- Provenienz-Spalten, die ADR-020 fuer schule vorsieht: Schulen koennen beim Anlegen aus dem
-- Schuldatei-Katalog uebernommen werden, Sonderfaelle (Schule ohne Katalogeintrag) bleiben
-- explizit erkennbar und begruendbar moeglich.
--
-- katalog_id ist wie bei schultraeger.katalog_id ein echter Fremdschluessel (anders als
-- schule_katalog.schultraegernummer -> schultraeger_katalog.traegernummer in V9): der Betreiber
-- verknuepft hier aktiv und einmalig beim Anlegen, es gibt keinen Importlauf, der die Referenz
-- nachtraeglich ungueltig machen koennte. ON DELETE SET NULL verhindert, dass ein spaeteres
-- Loeschen eines Katalogeintrags den operativen Schul-Datensatz mitreisst.
ALTER TABLE schule
    ADD COLUMN katalog_id         uuid REFERENCES schule_katalog (id) ON DELETE SET NULL,
    ADD COLUMN quelle             text NOT NULL DEFAULT 'MANUELL',
    ADD COLUMN sonderfall_hinweis text;

ALTER TABLE schule
    ADD CONSTRAINT schule_quelle_check CHECK (quelle IN ('LANDESLISTE', 'MANUELL', 'SONDERFALL')),
    ADD CONSTRAINT schule_sonderfall_hinweis_not_blank
        CHECK (sonderfall_hinweis IS NULL OR btrim(sonderfall_hinweis) <> '');
