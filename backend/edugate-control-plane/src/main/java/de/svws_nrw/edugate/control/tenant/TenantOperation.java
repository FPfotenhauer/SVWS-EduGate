package de.svws_nrw.edugate.control.tenant;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Fachliche Operation, die {@link TenantAccess} innerhalb einer Transaktion auf der
 * {@code default}-Datasource (Rolle {@code edugate_control}) ausführt, nachdem der
 * Mandanten-Kontext gesetzt wurde (ADR-002/ADR-008). Analog zu
 * {@link de.svws_nrw.edugate.control.operator.OperatorOperation}, aber für den
 * tenant-gebundenen statt den mandantenübergreifenden Operator-Pfad.
 */
@FunctionalInterface
public interface TenantOperation<T> {

    T execute(Connection connection) throws SQLException;
}
