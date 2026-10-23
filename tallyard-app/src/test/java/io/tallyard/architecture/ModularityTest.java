package io.tallyard.architecture;

import io.tallyard.TallyardApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the module structure: no cycles, no access to another module's {@code internal} package,
 * dependencies only through public API types. Also regenerates the module documentation
 * (PlantUML + Asciidoc) into {@code target/spring-modulith-docs}.
 */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(TallyardApplication.class);

    @Test
    void moduleStructureIsValid() {
        MODULES.verify();
    }

    @Test
    void writeDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
