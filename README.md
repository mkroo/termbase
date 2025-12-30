# Termbase

레거시 서비스의 도메인 용어를 쉽게 정리할 수 있도록 도와주는 오픈소스 프로젝트

## 소개

오래된 서비스를 운영하다 보면 시간이 지나면서 도메인 용어가 자연스럽게 늘어납니다. 초기 멤버들 사이에서는 암묵적으로 통용되던 용어들이 새로운 팀원에게는 진입 장벽이 되고, 같은 개념을 서로 다른 이름으로 부르면서
커뮤니케이션 비용이 증가합니다.

**Termbase**는 팀의 실제 커뮤니케이션(슬랙 메시지)을 분석하여 자주 사용되는 용어를 자동으로 추출하고, 빈도순으로 정렬하여 용어 사전 등록을 유도합니다.

### 문제점

- **용어의 파편화**: 같은 개념에 대해 팀원마다 다른 용어를 사용 (예: "주문", "오더", "Order")
- **암묵지의 증가**: 문서화되지 않은 도메인 지식이 특정 인원에게만 집중
- **온보딩 비용 증가**: 신규 입사자가 도메인을 이해하는 데 오랜 시간 소요
- **용어 정리의 어려움**: 어떤 용어부터 정리해야 할지 우선순위 판단이 어려움

### 솔루션

Termbase는 팀의 실제 커뮤니케이션(슬랙 메시지)을 분석하여 자주 사용되는 용어를 자동으로 추출하고, 빈도순으로 정렬하여 용어 사전 등록을 유도합니다.

```mermaid
flowchart LR
    Slack[슬랙 메시지] --> Extract[용어 추출]
    Extract --> Frequency[빈도 집계]
    Frequency --> Reminder[리마인더 발송]
    Reminder --> Register[용어 사전 등록]
    Register --> Share[팀 전체 공유]
```

### 주요 기능

- **슬랙 연동** - OAuth를 통해 워크스페이스를 연동하고 모니터링할 채널 선택
- **용어 자동 추출** - 형태소 분석을 통해 메시지에서 명사(용어 후보) 자동 추출
- **빈도 기반 우선순위** - 자주 등장하는 용어를 우선적으로 정리할 수 있도록 빈도순 정렬
- **용어 등록 리마인더** - 미등록 빈출 용어를 주기적으로 슬랙으로 알림
- **용어 사전 관리** - 용어의 정의, 동의어, 실제 사용 예시를 한 곳에서 관리하여 Single Source of Truth 제공
- **동의어 통합** - 동의어를 대표어로 치환하여 분산된 빈도를 통합 집계

### 기대 효과

- 실제 사용 빈도 기반으로 **우선순위가 높은 용어부터** 정리 가능
- 용어 사전을 통해 **팀 전체의 도메인 언어 통일**
- 신규 입사자의 **온보딩 시간 단축**
- 암묵지를 형식지로 전환하여 **지식의 공유 및 보존**

## 기술 스택

| Category      | Technology               |
|---------------|--------------------------|
| Language      | Kotlin 2.3.0, Java 25    |
| Framework     | Spring Boot 4.0.1        |
| Database      | MySQL 8, Elasticsearch 8 |
| Testing       | Kotest 6, Testcontainers |
| Documentation | Spring REST Docs         |

## 시작하기

### 요구 사항

- JDK 25+
- Docker

### 로컬 개발

```bash
# 프로젝트 빌드
./gradlew build

# 애플리케이션 실행 (docker-compose 자동 시작)
./gradlew bootRun
```

`bootRun` 실행 시 Spring Boot Docker Compose가 자동으로 MySQL과 Elasticsearch를 시작합니다.

### 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 테스트 커버리지 확인
./gradlew jacocoTestReport
```

테스트는 H2(MySQL 호환 모드)와 Testcontainers(Elasticsearch)를 사용하여 외부 의존성 없이 실행됩니다.

### 프로덕션 실행

```bash
SPRING_PROFILES_ACTIVE=production \
DATASOURCE_URL="jdbc:mysql://db-host:3306/termbase?user=app&password=secret" \
ELASTICSEARCH_URIS="http://es-host:9200" \
java -jar build/libs/termbase-0.0.1-SNAPSHOT.jar
```

| 환경변수                 | 설명                        | 예시                                            |
|----------------------|---------------------------|-----------------------------------------------|
| `DATASOURCE_URL`     | MySQL JDBC URL (인증 정보 포함) | `jdbc:mysql://host:3306/db?user=u&password=p` |
| `ELASTICSEARCH_URIS` | Elasticsearch URI         | `http://es-host:9200`                         |

## 문서

- [Requirements](docs/REQUIREMENTS.md) - 기능 및 비기능 요구사항
- [Architecture](docs/ARCHITECTURE.md) - 시스템 아키텍처 및 데이터 모델
- [Clean Code Guidelines](docs/CLEAN_CODE.md) - 코드 작성 가이드라인

## 라이선스

MIT License
