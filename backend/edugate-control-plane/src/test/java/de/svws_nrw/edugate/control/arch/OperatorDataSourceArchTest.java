package de.svws_nrw.edugate.control.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Erzwingt ADR-009: Die {@code operator}-Datasource (Rolle {@code edugate_operator}, kein
 * BYPASSRLS) darf ausschließlich innerhalb von {@code OperatorAccess} im Package
 * {@code de.svws_nrw.edugate.control.operator} injiziert werden.
 */
class OperatorDataSourceArchTest {

    private static final String OPERATOR_PACKAGE = "..control.operator..";
    private static final String OPERATOR_QUALIFIER = "operator";

    @Test
    void operatorDatasourceWirdNurInnerhalbDesOperatorPackagesInjiziert() {
        final JavaClasses importedClasses = new ClassFileImporter().importPackages("de.svws_nrw.edugate.control");

        final ArchRule rule = classes()
            .that().resideOutsideOfPackage(OPERATOR_PACKAGE)
            .should(notInjectOperatorDataSource());

        rule.check(importedClasses);
    }

    private static ArchCondition<JavaClass> notInjectOperatorDataSource() {
        return new ArchCondition<>("nicht die operator-Datasource injizieren") {
            @Override
            public void check(final JavaClass javaClass, final ConditionEvents events) {
                javaClass.getAllFields().forEach(field -> {
                    final boolean isAgroalDataSource = field.getRawType().isEquivalentTo(AgroalDataSource.class);
                    final boolean isOperatorQualified = field.getAnnotations().stream()
                        .anyMatch(annotation -> annotation.getRawType().isEquivalentTo(DataSource.class)
                            && OPERATOR_QUALIFIER.equals(annotation.as(DataSource.class).value()));

                    if (isAgroalDataSource && isOperatorQualified) {
                        events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getFullName() + " injiziert die operator-Datasource außerhalb von "
                                + OPERATOR_PACKAGE));
                    }
                });
            }
        };
    }
}
