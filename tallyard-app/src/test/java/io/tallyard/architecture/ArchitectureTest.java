package io.tallyard.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Layering rules that Spring Modulith does not express. */
@AnalyzeClasses(packages = "io.tallyard", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersDoNotTouchRepositories = noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .because("controllers go through services so that transaction boundaries stay explicit");

    @ArchTest
    static final ArchRule ratingEngineIsPure = noClasses()
            .that()
            .haveSimpleName("RatingEngine")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "java.sql..", "java.time.Clock")
            .because("rating must be deterministic and free of I/O");

    @ArchTest
    static final ArchRule publicApiTypesDoNotLeakEntities = noClasses()
            .that()
            .resideInAPackage("io.tallyard.(*)..")
            .and()
            .areAnnotatedWith(jakarta.persistence.Entity.class)
            .should()
            .bePublic()
            .because("entities are module internals; other modules consume immutable views");

    @ArchTest
    static final ArchRule sharedHasNoBusinessDependencies = noClasses()
            .that()
            .resideInAPackage("io.tallyard.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "io.tallyard.catalog..",
                    "io.tallyard.billing..",
                    "io.tallyard.metering..",
                    "io.tallyard.ingestion..",
                    "io.tallyard.credits..",
                    "io.tallyard.entitlements..",
                    "io.tallyard.outbox..",
                    "io.tallyard.webhooks..",
                    "io.tallyard.tenancy..",
                    "io.tallyard.dashboard..",
                    "io.tallyard.demo..");

    @ArchTest
    static final ArchRule kafkaListenersRunOnlyInWorkerRole = classes()
            .that()
            .containAnyMethodsThat(com.tngtech.archunit.base.DescribedPredicate.describe(
                    "annotated with @KafkaListener",
                    m -> m.isAnnotatedWith(org.springframework.kafka.annotation.KafkaListener.class)))
            .should()
            .beAnnotatedWith(io.tallyard.shared.role.WorkerRole.class)
            .because("API nodes must not consume; only workers do");
}
