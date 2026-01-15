# CLAUDE.md

이 파일은 이 저장소에서 코드 작업 시 Claude Code(claude.ai/code)에게 가이드를 제공합니다.

## 빌드 명령어

```bash
# 프로젝트 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun

# Testcontainers로 애플리케이션 실행 (로컬 개발용)
./gradlew bootTestRun

# 전체 테스트 실행
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.mkroo.termbase.TermbaseApplicationTests"

# 단일 테스트 메서드 실행
./gradlew test --tests "com.mkroo.termbase.TermbaseApplicationTests.contextLoads"

# REST Docs 생성 (테스트 먼저 실행 후 asciidoctor)
./gradlew asciidoctor

# 테스트 커버리지 확인 (리포트: build/reports/jacoco/test/html/)
./gradlew jacocoTestReport

# 테스트 커버리지 최소 임계값(100%) 검증
./gradlew jacocoTestCoverageVerification
```

## 기술 스택

- **언어**: Kotlin 2.3.0 + Java 25 toolchain
- **프레임워크**: Spring Boot 4.0.1
- **웹**: Spring MVC + Thymeleaf 템플릿
- **데이터베이스**: Elasticsearch 8 (Spring Data Elasticsearch), MySQL 8 (Spring Data JPA + Hibernate)
- **보안**: Spring Security + Thymeleaf extras
- **테스트**: Kotest 6, Testcontainers, Spring REST Docs (MockMvc)
- **문서화**: Spring REST Docs + Asciidoctor

## 프로젝트 구조

- `src/main/kotlin/com/mkroo/termbase/` - 메인 애플리케이션 코드
- `src/main/resources/` - 설정 및 템플릿
- `src/test/kotlin/com/mkroo/termbase/` - 테스트 코드
- `build/generated-snippets/` - REST Docs 생성 스니펫 (테스트 실행 후)

## Kotlin 컴파일러 설정

이 프로젝트는 엄격한 JSR-305 null-safety 어노테이션(`-Xjsr305=strict`)과 param-property용 어노테이션 기본 타겟(`-Xannotation-default-target=param-property`)을 사용합니다.

## 스킬

`.claude/skills/` 디렉토리에 SKILL.md 형식의 가이드라인이 있습니다. Claude Code가 자동으로 인식합니다.

## 프로젝트 문서

작업 시작 전 반드시 다음 문서를 읽어야 합니다:

- `docs/REQUIREMENTS.md` - 기능 및 비기능 요구사항
- `docs/ARCHITECTURE.md` - 시스템 아키텍처, 클래스 다이어그램, 데이터 모델

**중요**:

- REQUIREMENTS.md 파일을 읽고 AskUserQuestionTool을 사용하여 기술적 구현, UI & UX, 우려 사항, 트레이드오프 등 모든 측면에 대해 저를 상세히 인터뷰해 주세요. 질문은 뻔하거나 상투적이지 않아야 하며, 매우 심층적으로 접근하여 내용이 완성될 때까지 인터뷰를 계속 이어가야 합니다. 인터뷰가 끝나면 스펙을 파일에 작성하세요.
- REQUIREMENTS.md 또는 ARCHITECTURE.md 수정이 필요한 경우, 변경 전 반드시 사용자 확인을 받아야 합니다.
- REQUIREMENTS.md와 ARCHITECTURE.md는 항상 동기화되어야 합니다. 하나의 문서가 수정되면 다른 문서와의 일관성을 확인하고 필요시 업데이트합니다.

## 개발 워크플로우

새로운 로직 작성 후 반드시:

1. **100% 커버리지 테스트 작성** - 새 코드의 모든 브랜치, 엣지 케이스, 에러 시나리오를 커버하는 Kotest 테스트 생성
2. **테스트 실행** - `./gradlew test` 실행하여 모든 테스트 통과 확인
3. **커버리지 검증** - `./gradlew jacocoTestCoverageVerification` 실행하여 최소 임계값(100%) 충족 확인
4. **빌드 실행** - `./gradlew build` 실행하여 컴파일 및 테스트 실패 없음 확인

모든 테스트 통과, 커버리지 검증, 빌드 성공 전까지 작업이 완료된 것으로 간주하지 않습니다.
