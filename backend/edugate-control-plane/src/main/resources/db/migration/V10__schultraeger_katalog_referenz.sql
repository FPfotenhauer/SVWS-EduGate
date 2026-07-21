-- ADR-020 "Schritt 2": Schulträger können beim Anlegen aus dem Schuldatei-Katalog übernommen
-- werden. Damit diese Herkunft auditierbar bleibt und Sonderfälle (Schulträger ohne
-- Katalogeintrag) explizit erkennbar sind, bekommt die operative schultraeger-Tabelle dieselben
-- Provenienz-Spalten, die ADR-020 für schule vorsieht (spiegelbildliches Vorgehen, siehe
-- ADR-020-Nachtrag).
--
-- katalog_id ist bewusst ein echter Fremdschlüssel (anders als schule_katalog.schultraegernummer
-- -> schultraeger_katalog.traegernummer in V9): hier verknüpft der Betreiber aktiv und einmalig
-- beim Anlegen, es gibt keinen Importlauf, der die Referenz nachträglich ungültig machen könnte.
-- ON DELETE SET NULL verhindert, dass ein späteres Löschen eines Katalogeintrags den operativen
-- Schulträger-Datensatz mitreißt.
ALTER TABLE schultraeger
    ADD COLUMN katalog_id         uuid REFERENCES schultraeger_katalog (id) ON DELETE SET NULL,
    ADD COLUMN quelle             text NOT NULL DEFAULT 'MANUELL',
    ADD COLUMN sonderfall_hinweis text;

ALTER TABLE schultraeger
    ADD CONSTRAINT schultraeger_quelle_check CHECK (quelle IN ('LANDESLISTE', 'MANUELL', 'SONDERFALL')),
    ADD CONSTRAINT schultraeger_sonderfall_hinweis_not_blank
        CHECK (sonderfall_hinweis IS NULL OR btrim(sonderfall_hinweis) <> '');
