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

## REQUIREMENTS.md Auto-Update Hook

이 프로젝트에는 구현 코드 변경 시 자동으로 REQUIREMENTS.md 업데이트를 요청하는 **Stop hook**이 설정되어 있습니다.

### 동작 방식

1. Claude가 Kotlin 구현 파일(`src/main/kotlin/**/*.kt`)을 작성/수정하고 작업을 완료하면
2. Stop hook이 트리거되어 Claude의 작업을 일시 중단합니다
3. Claude는 **AskUserQuestion 도구**를 사용하여 사용자에게 REQUIREMENTS.md 업데이트 여부를 물어봅니다

### Claude의 행동 지침

Stop hook에서 `"Implementation changed - REQUIREMENTS.md review needed"` 메시지를 받으면:

1. **AskUserQuestion 도구 사용**: 사용자에게 다음을 물어봅니다:
   - 새로운 정책/인수조건을 추가해야 하는지
   - 기존 정책을 수정해야 하는지
   - 업데이트가 필요 없는지

2. **사용자 응답 반영**: 사용자가 정책 변경을 요청하면:
   - `docs/REQUIREMENTS.md` 파일을 업데이트합니다
   - ARCHITECTURE.md와의 일관성을 확인합니다

3. **테스트 파일은 제외**: `src/test/kotlin` 경로의 파일 변경은 트리거하지 않습니다

## Development Workflow

After writing new logic, you MUST:

1. **Write tests with 100% coverage** - Create Kotest tests covering all branches, edge cases, and error scenarios for
   the new code
2. **Run tests** - Execute `./gradlew test` and ensure all tests pass
3. **Verify coverage** - Execute `./gradlew jacocoTestCoverageVerification` and ensure coverage meets the minimum
   threshold (100%)
4. **Run build** - Execute `./gradlew build` and verify there are no compilation or test failures

Do not consider a task complete until all tests pass, coverage is verified, and the build succeeds.

## Commit Guidelines

커밋은 기능/구현 단위로 분리하여 step-by-step으로 수행합니다. 사용자가 별도로 요청하지 않아도 이 원칙을 따릅니다.

### 커밋 분리 원칙

1. **레이어별 분리**: 같은 기능이라도 레이어가 다르면 분리
   - Domain 레이어 (인터페이스, DTO, 엔티티)
   - Application 레이어 (서비스 구현)
   - Presentation 레이어 (컨트롤러, 템플릿)

2. **기능별 분리**: 독립적인 기능은 별도 커밋
   - 예: 검색 기능과 정렬 기능이 별개라면 분리
   - 예: UI 개선과 버그 수정은 분리

3. **설정/인프라 분리**: 빌드 설정, 테스트 설정 등은 별도 커밋
   - 예: JaCoCo 설정, ES 설정 등

### 커밋 메시지 형식

```
<type>: <한글 제목> (<관련 User Story>)

<본문 - 변경 내용 상세>

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

**Type 종류:**
- `feat`: 새로운 기능
- `fix`: 버그 수정
- `refactor`: 리팩토링
- `chore`: 빌드, 설정 변경
- `docs`: 문서 변경
- `test`: 테스트 추가/수정

### 커밋 순서 예시

복잡한 기능 구현 시 권장 순서:

1. Domain 레이어: 인터페이스, DTO, 엔티티
2. Infrastructure/Application: 구현체, 서비스
3. Presentation: 컨트롤러, 뷰
4. Configuration: 빌드 설정, 환경 설정

```bash
# 예시: 용어 검색 기능 구현
git commit -m "feat: SourceDocumentAnalyzer 인터페이스에 검색 메서드 추가"
git commit -m "feat: ElasticsearchSourceDocumentAnalyzer 검색 구현"
git commit -m "feat: GlossaryService 검색 기능 추가"
git commit -m "feat: 용어 검색 UI 구현"
```

## Kotlin Code Style

**IMPORTANT**: 사용자가 코드 스타일에 대해 피드백을 주면, 해당 내용을 이 섹션에 추가할지 물어보세요. 동일한 스타일 이슈가 반복되지 않도록 문서화합니다.

### Import Guidelines

Always use `import` statements instead of fully qualified class names in code.

```kotlin
// Good: import 사용
import java.time.Instant

val now = Instant.now()

// Avoid: fully qualified name 사용
val now = java.time.Instant.now()
```

**이유:**

- 코드 가독성 향상
- 일관된 코드 스타일 유지
- 클래스 이름 충돌 시에만 fully qualified name 사용

## Documentation Guidelines

- **Diagrams**: Always use **Mermaid** syntax for all diagrams (flowcharts, sequence diagrams, class diagrams, ER
  diagrams, etc.)
- Do NOT use ASCII art or plain text box diagrams
- Mermaid diagrams render properly in GitHub, IDE previews, and documentation sites
