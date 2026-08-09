package de.tudarmstadt.campus.admin.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the layering of spec section 3 (NFA-02): Controller to Service to Repository, entities never
 * leave the service layer.
 * <p>
 * Every pattern is anchored on our own base package. Bare patterns such as {@code ..domain..} would also
 * match {@code org.springframework.data.domain}, which turned a Pageable parameter into a false
 * architecture violation.
 * <p>
 * {@code allowEmptyShould(true)} keeps the rules green while a layer is still empty: the packages fill
 * up over the phases, and a rule without matching classes must not be a failure.
 */
@AnalyzeClasses(
        packages = ArchitectureTest.BASE_PACKAGE,
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE_PACKAGE = "de.tudarmstadt.campus.admin";

    private static final String WEB = BASE_PACKAGE + "..web..";
    private static final String DOMAIN = BASE_PACKAGE + "..domain..";
    private static final String REPOSITORY = BASE_PACKAGE + "..repository..";
    private static final String SERVICE = BASE_PACKAGE + "..service..";
    private static final String SECURITY = BASE_PACKAGE + ".security..";

    @ArchTest
    static final ArchRule controllers_must_not_use_repositories = noClasses()
            .that().resideInAPackage(WEB)
            .should().dependOnClassesThat().resideInAPackage(REPOSITORY)
            .because("controllers go through services (spec section 3)")
            .allowEmptyShould(true);

    /**
     * Not even a mapping helper may see an entity: DTOs are plain data, the translation happens in the
     * service layer.
     */
    @ArchTest
    static final ArchRule entities_must_not_leave_the_service_layer = noClasses()
            .that().resideInAPackage(WEB)
            .should().dependOnClassesThat().resideInAPackage(DOMAIN)
            .because("the API exposes DTOs only, never entities (spec section 3)")
            .allowEmptyShould(true);

    /**
     * The security infrastructure is a legitimate consumer of repositories: the specification places
     * {@code CampusUserDetailsService} and {@code TokenVersionService} in {@code security} (section 3),
     * and both have to read accounts. The rule that matters — controllers never touch repositories —
     * stays strict above.
     */
    @ArchTest
    static final ArchRule repositories_are_only_used_by_services_and_security = noClasses()
            .that().resideOutsideOfPackages(SERVICE, REPOSITORY, SECURITY)
            .should().dependOnClassesThat().resideInAPackage(REPOSITORY)
            .because("repository access belongs in the service layer (spec section 3)")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllers_reside_in_a_web_package = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage(WEB)
            .allowEmptyShould(true);

    /**
     * Services return DTOs, and the specification places those under {@code web/dto} (section 3), so a
     * blanket ban on the web package would forbid the intended design. What must not happen is a service
     * reaching back into a controller.
     */
    @ArchTest
    static final ArchRule services_do_not_depend_on_controllers = noClasses()
            .that().resideInAPackage(SERVICE)
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller")
            .because("dependencies point inwards, not back at the web layer")
            .allowEmptyShould(true);
}
