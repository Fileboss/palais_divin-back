package fr.lepgu.palaisdivin.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

@AnalyzeClasses(
    packages = "fr.lepgu.palaisdivin.backend",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

  @ArchTest
  static final ArchRule domainStaysFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta..", "org.neo4j..", "io.minio..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule domainOnlyDependsOnJdkAndDomain =
      classes()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "..domain..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule applicationShouldNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapters..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule adaptersDoNotDependOnOtherComponentsAdapters =
      classes()
          .that()
          .resideInAPackage("..adapters..")
          .and()
          .resideOutsideOfPackage("..shared.adapters..")
          .should(onlyDependOnOwnComponentOrSharedAdapters())
          .allowEmptyShould(true);

  private static ArchCondition<JavaClass> onlyDependOnOwnComponentOrSharedAdapters() {
    return new ArchCondition<JavaClass>(
        "only depend on their own component's adapters or shared/adapters") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        String ownComponent = componentOf(item.getPackageName());
        for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
          String targetPackage = dependency.getTargetClass().getPackageName();
          if (!targetPackage.contains(".adapters.")
              || targetPackage.contains(".shared.adapters.")) {
            continue;
          }
          String targetComponent = componentOf(targetPackage);
          if (!targetComponent.equals(ownComponent)) {
            events.add(
                SimpleConditionEvent.violated(
                    item,
                    dependency.getDescription()
                        + " crosses component adapter boundaries ("
                        + ownComponent
                        + " -> "
                        + targetComponent
                        + ")"));
          }
        }
      }
    };
  }

  private static String componentOf(String packageName) {
    String[] parts = packageName.split("\\.");
    return parts.length > 4 ? parts[4] : "";
  }
}
