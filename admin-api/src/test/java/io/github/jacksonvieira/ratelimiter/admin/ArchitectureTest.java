package io.github.jacksonvieira.ratelimiter.admin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Hexagonal layering: the domain knows nothing about frameworks, adapters or the application layer. */
@AnalyzeClasses(packages = "io.github.jacksonvieira.ratelimiter.admin", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..admin.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "..admin.adapter..", "..admin.application..");

    @ArchTest
    static final ArchRule applicationIsFrameworkFree = noClasses()
            .that().resideInAPackage("..admin.application..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "..admin.adapter..");

    @ArchTest
    static final ArchRule adaptersOnlyUseInboundPorts = noClasses()
            .that().resideInAPackage("..admin.adapter.in..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("UseCase");

    @ArchTest
    static final ArchRule webAndJdbcAdaptersAreIndependent = noClasses()
            .that().resideInAPackage("..admin.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..admin.adapter.out..");

    @ArchTest
    static final ArchRule jooqStaysInItsAdapter = noClasses()
            .that().resideOutsideOfPackage("..admin.adapter.out.jooq..")
            .should().dependOnClassesThat().resideInAnyPackage("org.jooq..", "..adapter.out.jooq.generated..");
}
