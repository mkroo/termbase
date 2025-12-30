# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run the application with Testcontainers (for local development)
./gradlew bootTestRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.mkroo.termbase.TermbaseApplicationTests"

# Run a single test method
./gradlew test --tests "com.mkroo.termbase.TermbaseApplicationTests.contextLoads"

# Generate REST Docs (runs tests first, then asciidoctor)
./gradlew asciidoctor

# Check test coverage (generates report in build/reports/jacoco/test/html/)
./gradlew jacocoTestReport

# Verify test coverage meets minimum threshold (100%)
./gradlew jacocoTestCoverageVerification
```

## Tech Stack

- **Language**: Kotlin 2.3.0 with Java 25 toolchain
- **Framework**: Spring Boot 4.0.1
- **Web**: Spring MVC with Thymeleaf templates
- **Database**: Elasticsearch 8 (via Spring Data Elasticsearch), MySQL 8 (via Spring Data JPA + Hibernate)
- **Security**: Spring Security with Thymeleaf extras
- **Testing**: Kotest 6, Testcontainers, Spring REST Docs (MockMvc)
- **Documentation**: Spring REST Docs with Asciidoctor

## Project Structure

- `src/main/kotlin/com/mkroo/termbase/` - Main application code
- `src/main/resources/` - Configuration and templates
- `src/test/kotlin/com/mkroo/termbase/` - Test code
- `build/generated-snippets/` - REST Docs generated snippets (after test run)

## Kotlin Compiler Settings

The project uses strict JSR-305 null-safety annotations (`-Xjsr305=strict`) and annotation default target for
param-property (`-Xannotation-default-target=param-property`).

## Testing Guidelines

Write all tests using **Kotest DescribeSpec** style. Use `describe` for grouping and `it` for individual test cases.

```kotlin
@Import(TestcontainersConfiguration::class)
@SpringBootTest
class ExampleTests : DescribeSpec() {
    init {
        extension(SpringExtension())

        describe("FeatureName") {
            it("should do something") {
                // test code
            }

            context("when some condition") {
                it("should behave differently") {
                    // test code
                }
            }
        }
    }
}
```

Key points:

- Use `DescribeSpec` as the base class
- Register `SpringExtension()` for Spring integration tests
- Use `describe` to group related tests by feature/class
- Use `context` for conditional scenarios
- Use `it` for individual test cases
- Use Kotest assertions (`shouldBe`, `shouldThrow`, etc.)

## Spring Data JPA Guidelines

Use the minimal `Repository<T, ID>` interface instead of `JpaRepository` or `CrudRepository`. Define only the methods you actually need.

```kotlin
// Good: 필요한 메서드만 정의
interface TermRepository : Repository<Term, Long> {
    fun save(term: Term): Term
    fun findById(id: Long): Term?
    fun findByName(name: String): Term?
    fun existsByName(name: String): Boolean
}

// Avoid: 불필요한 메서드까지 노출
interface TermRepository : JpaRepository<Term, Long>
```

**이유:**

- 실제 사용하는 메서드만 노출하여 인터페이스 명확성 향상
- 불필요한 `deleteAll()`, `flush()` 등의 위험한 메서드 노출 방지
- 테스트 시 mock 범위 최소화

## Project Documentation

Before starting any task, you MUST read:

- `docs/REQUIREMENTS.md` - Functional and non-functional requirements
- `docs/ARCHITECTURE.md` - System architecture, class diagrams, and data models
- `docs/CLEAN_CODE.md` - Clean code guidelines for Kotlin + Spring Boot

**IMPORTANT**:

- If modifications to REQUIREMENTS.md or ARCHITECTURE.md are needed, you MUST ask for user confirmation before making
  any changes.
- REQUIREMENTS.md and ARCHITECTURE.md MUST always be in sync. When one document is modified, verify consistency with the
  other document and update it if needed to maintain alignment.

## Development Workflow

After writing new logic, you MUST:

1. **Write tests with 100% coverage** - Create Kotest tests covering all branches, edge cases, and error scenarios for
   the new code
2. **Run tests** - Execute `./gradlew test` and ensure all tests pass
3. **Verify coverage** - Execute `./gradlew jacocoTestCoverageVerification` and ensure coverage meets the minimum
   threshold (100%)
4. **Run build** - Execute `./gradlew build` and verify there are no compilation or test failures

Do not consider a task complete until all tests pass, coverage is verified, and the build succeeds.

## Documentation Guidelines

- **Diagrams**: Always use **Mermaid** syntax for all diagrams (flowcharts, sequence diagrams, class diagrams, ER
  diagrams, etc.)
- Do NOT use ASCII art or plain text box diagrams
- Mermaid diagrams render properly in GitHub, IDE previews, and documentation sites
