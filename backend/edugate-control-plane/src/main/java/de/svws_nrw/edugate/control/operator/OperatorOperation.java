package de.svws_nrw.edugate.control.operator;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Fachliche Operation, die {@link OperatorAccess} innerhalb einer Transaktion auf der
 * {@code operator}-Datasource ausführt. Wirft die Operation {@link OperatorDeniedException}
 * oder {@link OperatorNotFoundException}, wird die Fachtransaktion zurückgerollt und ein
 * entsprechender Audit-Eintrag (DENIED bzw. ERROR) in einer neuen Transaktion geschrieben.
 */
@FunctionalInterface
public interface OperatorOperation<T> {

    OperatorOutcome<T> execute(Connection connection) throws SQLException;
}
