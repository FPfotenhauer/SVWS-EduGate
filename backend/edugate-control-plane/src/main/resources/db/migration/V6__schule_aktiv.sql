-- Ergänzt schule um ein aktiv-Flag (Betreiber-UI, Schulseite: "Schule deaktivieren"), analog zu
-- schultraeger und svws_instanz. schule bleibt tenant-gebunden; RLS/Grants aus V1 sind unverändert
-- ausreichend (keine neue Policy nötig, das Feld ist einfach eine weitere Spalte der bestehenden
-- tenant-gebundenen Tabelle).

ALTER TABLE schule
    ADD COLUMN aktiv boolean NOT NULL DEFAULT true;
