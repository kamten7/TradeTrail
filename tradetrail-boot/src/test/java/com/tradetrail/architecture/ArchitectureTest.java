package com.tradetrail.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    @Test
    void domainDoesNotDependOnFrameworksOrOuterLayers() {
        var classes = new ClassFileImporter().importPackages("com.tradetrail");
        noClasses().that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "org.apache.ibatis..", "com.baomidou..", "..application..",
                        "..api..", "..persistence..")
                .check(classes);
    }

    @Test
    void controllersAndApplicationsDoNotDependOnPersistence() {
        var classes = new ClassFileImporter().withImportOption(new DoNotIncludeTests())
                .importPackages("com.tradetrail");
        noClasses().that()
                .resideInAnyPackage("..api..", "..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..persistence..")
                .check(classes);
    }

    @Test
    void businessDomainsOwnTheirStores() {
        var classes = new ClassFileImporter().importPackages("com.tradetrail.application");
        // 用逐条依赖检查同时约束包和仓储名称，避免把公开领域值对象误判为越界。
        for (var type : classes) {
            for (var dependency : type.getDirectDependenciesFromSelf()) {
                var target = dependency.getTargetClass();
                if (!target.getSimpleName().endsWith("Store") || !target.getPackageName().contains(".domain.")) {
                    continue;
                }
                String owner = target.getPackageName().split("\\.domain\\.")[1].split("\\.")[0];
                Assertions.assertTrue(type.getPackageName().contains(".application." + owner),
                        dependency.getDescription());
            }
        }
    }
}
