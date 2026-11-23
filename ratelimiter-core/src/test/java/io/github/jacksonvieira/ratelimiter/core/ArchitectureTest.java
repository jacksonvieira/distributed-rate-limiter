package io.github.jacksonvieira.ratelimiter.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The core is the hexagon: it defines the ports and must not know about any adapter or framework. */
@AnalyzeClasses(packages = "io.github.jacksonvieira.ratelimiter", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule coreDoesNotDependOnAdaptersOrFrameworks = noClasses()
            .that().resideInAPackage("io.github.jacksonvieira.ratelimiter.core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.github.jacksonvieira.ratelimiter.redis..",
                    "io.github.jacksonvieira.ratelimiter.gateway..",
                    "org.springframework..",
                    "io.lettuce..",
                    "reactor..");
}
