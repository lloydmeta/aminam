import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("io.quarkus") version "3.37.2" // Inlined for Dependabot version management
    id("com.diffplug.spotless") version "8.8.0"
    id("net.ltgt.errorprone") version "5.1.0"
}

repositories {
    mavenCentral()
}

group = "com.beachape.aminam"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val errorProneVersion = "2.50.0"
val errorProneSupportVersion = "0.30.0"
val nullAwayVersion = "0.13.6"

configurations.all {
    resolutionStrategy {
        // dev.cel 0.13.1's protobuf gencode targets protobuf-java 4.33.5; the Quarkus BOM forces the
        // older 4.33.2, which trips protobuf's gencode/runtime version check. The newer runtime is
        // backwards compatible with the BOM's gencode, so force it.
        force("com.google.protobuf:protobuf-java:4.35.1")
    }
}

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.37.2")) // Inlined for Dependabot version management
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-virtual-threads")
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")
    implementation("io.quarkus:quarkus-security")
    implementation("io.quarkus:quarkus-smallrye-jwt-build")
    implementation("io.quarkus:quarkus-redis-client")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.smallrye:smallrye-jwt")
    implementation("com.password4j:password4j:1.8.4")
    implementation("dev.cel:cel:0.13.1")
    implementation("org.jspecify:jspecify:1.0.0")
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.quarkus:quarkus-junit-mockito")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core:3.27.7")

    errorprone("com.google.errorprone:error_prone_core:$errorProneVersion")
    errorprone("com.uber.nullaway:nullaway:$nullAwayVersion")
    errorprone("tech.picnic.error-prone-support:error-prone-contrib:$errorProneSupportVersion")
}

spotless {
    java {
        importOrder()
        removeUnusedImports()
        googleJavaFormat()
        formatAnnotations()
        targetExclude("**/generated/**", "**/build/**")
    }
    kotlinGradle {
        ktlint()
    }
}

tasks.withType<JavaCompile>().configureEach {
    val isTest = name.contains("test", ignoreCase = true)

    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-Xlint:all",
            "-Xlint:-serial",
            "-Xlint:-classfile",
            "-Xlint:-processing",
        ) + if (isTest) emptyList() else listOf("-Werror"),
    )

    options.errorprone {
        allErrorsAsWarnings.set(false)
        disableWarningsInGeneratedCode.set(true)
        excludedPaths.set(".*/build/generated/.*")
        allDisabledChecksAsWarnings.set(true)

        disable(
            "Java8ApiChecker",
            "WildcardImport",
            "CanonicalAnnotationSyntax",
            "LexicographicalAnnotationListing",
            "LexicographicalAnnotationAttributeListing",
            "RemoveUnusedImports",
            "RequireExplicitNullMarking",
            "AddNullMarkedToClass",
            "LexicographicalPermitsListing",
            "ImmutableMemberCollection", // Don't want to pull in Guava at the moment
            "CollectorMutability", // ditto: it pushes Guava's toImmutableSet; plain toSet is fine
            *(if (isTest) arrayOf("IdentifierName", "CatchingUnchecked", "NonFinalStaticField") else emptyArray()),
        )

        option("NullAway:AnnotatedPackages", "com.beachape.aminam")
        option(
            "NullAway:ExcludedClassAnnotations",
            "javax.annotation.processing.Generated,jakarta.annotation.Generated",
        )
        option(
            "NullAway:ExcludedFieldAnnotations",
            "jakarta.inject.Inject,org.eclipse.microprofile.config.inject.ConfigProperty," +
                "jakarta.persistence.Id,jakarta.persistence.Column",
        )

        error("NullAway")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
