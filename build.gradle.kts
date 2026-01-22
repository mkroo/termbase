plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    kotlin("plugin.jpa") version "2.3.0"
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    jacoco
}

group = "com.mkroo"
version = "0.0.1-SNAPSHOT"
description = "termbase"

springBoot {
    mainClass.set("com.mkroo.termbase.TermbaseApplicationKt")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

extra["snippetsDir"] = file("build/generated-snippets")

dependencies {
    implementation("org.springframework.boot:spring-boot-h2console")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation("tools.jackson.module:jackson-module-kotlin")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-restdocs")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:elasticsearch")
    testImplementation("io.kotest:kotest-runner-junit5:6.0.7")
    testImplementation("io.kotest:kotest-assertions-core:6.0.7")
    testImplementation("io.kotest:kotest-extensions-spring:6.0.7")
    testImplementation("io.mockk:mockk:1.13.16")
    // Lucene Nori - ES와 동일한 한국어 형태소 분석기 (standalone 사용)
    implementation("org.apache.lucene:lucene-analysis-nori:10.1.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // JVM 메모리 최적화
    jvmArgs("-XX:+UseParallelGC", "-XX:MaxMetaspaceSize=512m")
}

tasks.test {
    outputs.dir(project.extra["snippetsDir"]!!)
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required = true
        xml.required = true
    }
}

val jacocoExcludes =
    listOf(
        "**/TermbaseApplication*",
        "**/presentation/controller/*Request*",
        "**/presentation/controller/*Response*",
        "**/presentation/controller/ApiResponse*",
        "**/presentation/controller/TermNotFoundException*",
        "**/presentation/controller/IgnoredTermNotFoundException*",
        "**/presentation/controller/DemoController*",
        "**/presentation/controller/ConfluenceController*",
        "**/domain/model/document/BulkInsertFailure*",
        "**/application/service/ReindexingResult*",
        "**/application/service/ReindexingService\$Companion*",
        "**/domain/model/reindex/ReindexingStatus*",
        // Kotlin interface default parameter synthetic classes
        "**/domain/service/SourceDocumentAnalyzer\$DefaultImpls*",
        // Term candidate extraction internal data classes and sealed interface subclasses
        "**/application/service/ExtractionConfig*",
        "**/application/service/ExtractionResult\$*",
        "**/application/service/CandidateReviewResult\$*",
        "**/application/service/CandidateStatistics*",
        "**/application/service/TermCandidateExtractionService\$ProcessingResult*",
        "**/application/service/TermCandidateExtractionService\$CandidateScore*",
        // Standalone CLI tools (not unit-testable, require manual execution)
        "**/tool/extraction/*",
        // NoriNounSequenceExtractor internal companion and data class
        "**/tool/analyzer/NoriNounSequenceExtractor\$*",
        // Confluence-related classes (external API integration - difficult to unit test)
        "**/infrastructure/confluence/**",
        "**/domain/model/confluence/**",
        "**/application/service/ConfluenceBatchService*",
        "**/application/service/ConfluenceOAuthService*",
        "**/application/service/ConfluenceTokenRefresher*",
        // Domain data classes used internally (mostly auto-generated methods)
        "**/domain/service/DictionaryCandidateStat*",
        "**/domain/service/NgramStat*",
        "**/domain/service/UnigramStat*",
        "**/domain/service/TermExtractionConfig*",
        "**/domain/service/TermExtractionResult*",
    )

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                // 0.97 to account for:
                // - Defensive null checks in Elasticsearch operations that cannot be triggered in tests
                // - Auto-generated data class methods (equals, hashCode, copy, componentN)
                // - Exception handling paths in resource management (use blocks)
                minimum = "0.97".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(jacocoExcludes)
                }
            },
        ),
    )
}

tasks.jacocoTestReport {
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(jacocoExcludes)
                }
            },
        ),
    )
}

tasks.asciidoctor {
    inputs.dir(project.extra["snippetsDir"]!!)
    dependsOn(tasks.test)
}

// 독립 실행 용어 후보 추출 (Spring/ES 없이 Nori 사용)
tasks.register<JavaExec>("runStandaloneExtraction") {
    group = "tool"
    description = "Run standalone term extraction without Spring/ES (uses Nori)"
    mainClass.set("com.mkroo.termbase.tool.extraction.StandaloneTermCandidateExtractionKt")
    classpath = sourceSets["main"].runtimeClasspath
}
