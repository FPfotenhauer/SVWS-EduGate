package de.svws_nrw.edugate.core.domain;

/** Herkunft eines {@link Schema}-Datensatzes (ADR-012: "source"/"Verwaltungsart"). */
public enum SchemaSource {
    /** Manuell über EduGate angelegt. */
    MANUELL,
    /** Über einen Abgleich mit der SVWS-Privileged-/Root-API gefunden/übernommen. */
    SYNCHRONISIERT
}
