#!/usr/bin/env bash
# Legt die drei anwendungsseitigen PostgreSQL-Rollen gemäß ADR-002/ADR-009 an.
#
# - edugate_control:    Standard-Rolle der Control Plane; unterliegt RLS.
# - edugate_operator:   Rolle für OperatorAccess; unterliegt ebenfalls RLS (kein BYPASSRLS,
#                        ADR-009) – Cross-Tenant-Zugriff läuft über explizite Operator-Policies.
# - edugate_gateway_ro: Read-only-Rolle des künftigen Gateway-Service (ADR-001).
#
# Keine der drei Rollen besitzt die Fachtabellen: Owner bleibt der PostgreSQL-Superuser
# ($POSTGRES_USER), der ausschließlich die Flyway-Migration ausführt und von der
# Anwendung zur Laufzeit nie verwendet wird. Grund: Ein Table-Owner besitzt in PostgreSQL
# implizit ALLE Privilegien auf "seiner" Tabelle – GRANT/REVOKE kann das nicht einschränken.
# Für audit_admin ("Anwendungsrollen erhalten nur INSERT und SELECT, kein UPDATE/DELETE",
# ADR-009) muss deshalb keine der Anwendungsrollen Owner sein, sonst wäre die
# Append-only-Garantie wirkungslos.
#
# Passwörter kommen ausschließlich aus Umgebungsvariablen (.env, niemals hartkodiert).
set -euo pipefail

: "${EDUGATE_CONTROL_DB_PASSWORD:?EDUGATE_CONTROL_DB_PASSWORD muss gesetzt sein}"
: "${EDUGATE_OPERATOR_DB_PASSWORD:?EDUGATE_OPERATOR_DB_PASSWORD muss gesetzt sein}"
: "${EDUGATE_GATEWAY_DB_PASSWORD:?EDUGATE_GATEWAY_DB_PASSWORD muss gesetzt sein}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE edugate_control LOGIN PASSWORD '${EDUGATE_CONTROL_DB_PASSWORD}';
    CREATE ROLE edugate_operator LOGIN PASSWORD '${EDUGATE_OPERATOR_DB_PASSWORD}';
    CREATE ROLE edugate_gateway_ro LOGIN PASSWORD '${EDUGATE_GATEWAY_DB_PASSWORD}';

    -- Alle drei Rollen greifen nur auf bereits bestehende Objekte zu (angelegt vom
    -- Superuser über die Flyway-Migration); keine benötigt CREATE auf dem Schema.
    GRANT USAGE ON SCHEMA public TO edugate_control;
    GRANT USAGE ON SCHEMA public TO edugate_operator;
    GRANT USAGE ON SCHEMA public TO edugate_gateway_ro;

    -- Explizit kein BYPASSRLS für edugate_operator (ADR-009) und keine Superuser-Attribute
    -- für irgendeine Anwendungsrolle.
EOSQL
