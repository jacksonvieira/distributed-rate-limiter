package io.github.jacksonvieira.ratelimiter.redis;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The Redis adapter implements a core port; it must stay independent of the gateway and of Spring. */
@AnalyzeClasses(packages = "io.github.jacksonvieira.ratelimiter", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule redisAdapterDoesNotDependOnTheGatewayOrSpring = noClasses()
            .that().resideInAPackage("io.github.jacksonvieira.ratelimiter.redis..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.github.jacksonvieira.ratelimiter.gateway..",
                    "org.springframework..");
}
