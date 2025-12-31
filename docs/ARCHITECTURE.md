# Architecture

## Data Storage Architecture

### Overview

```mermaid
flowchart TB
    subgraph App[Application]
        direction LR
    end

    subgraph MySQL["MySQL (JPA)"]
        Term["Term (용어 + 동의어)"]
        IgnoredTerm["IgnoredTerm (무시된 단어)"]
        SlackWorkspace["SlackWorkspace (슬랙 워크스페이스 + 채널)"]
        ReminderConfig["ReminderConfig (리마인더 설정)"]
    end

    subgraph ES["Elasticsearch"]
        SourceDocument["SourceDocument (원천 데이터)"]
        SynonymFilter["synonym filter"]
        NoriAnalyzer["nori 형태소 분석기"]
        UserDict["user_dictionary"]
    end

    App --> MySQL
    App --> ES
    Term -.->|name 동기화| UserDict
    Term -.->|synonyms 동기화| SynonymFilter
``` 

### Storage Responsibilities

| Storage       | Data           | Purpose                                                |
|---------------|----------------|--------------------------------------------------------|
| MySQL (JPA)   | Term           | 용어 + 동의어 관리, ES user_dictionary/synonym filter 원본 (AR) |
| MySQL (JPA)   | IgnoredTerm    | 무시된 단어 관리 (용어 추출 시 제외 대상)                              |
| MySQL (JPA)   | SlackWorkspace | 슬랙 워크스페이스 + 채널 모니터링 설정 (Aggregate Root)                |
| MySQL (JPA)   | ReminderConfig | 리마인더 설정 (cron 표현식, 상위 N개) 관리                           |
| Elasticsearch | SourceDocument | 원천 데이터 저장 및 용어 빈도 집계 (Slack, Gmail, Webhook 등)         |

---

## MySQL Schema (JPA Entities)

### Term (용어) - Aggregate Root

```kotlin
@Entity
@Table(name = "terms")
class Term(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val name: String,                    // 대표어

    @Column(nullable = false, columnDefinition = "TEXT")
    var definition: String,              // 정의

    @OneToMany(mappedBy = "term", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _synonyms: MutableList<Synonym> = mutableListOf(),

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime
) {
    val synonyms: List<Synonym> get() = _synonyms.toList()

    fun addSynonym(name: String): Synonym
    fun removeSynonym(name: String)
    fun updateDefinition(newDefinition: String)
}

@Entity
@Table(name = "synonyms")
class Synonym(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val name: String,                    // 동의어

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    val term: Term                       // 대표어 (canonical)
)
```

### IgnoredTerm (무시된 단어) - Independent Entity

```kotlin
@Entity
@Table(name = "ignored_terms")
class IgnoredTerm(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val name: String,                    // 무시할 단어

    @Column(nullable = false)
    var reason: String,                  // 무시 사유 (필수, 수정 가능)

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime         // 등록 시각
) {
    fun updateReason(newReason: String)
}
```

### SlackWorkspace (슬랙 워크스페이스) - Aggregate Root

```kotlin
@Entity
@Table(name = "slack_workspaces")
class SlackWorkspace(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val teamId: String,                  // 슬랙 팀 ID (T로 시작)

    @Column(nullable = false)
    val name: String,                    // 워크스페이스 이름

    @Column(nullable = false)
    val accessToken: String,             // OAuth 액세스 토큰 (암호화 저장)

    @OneToMany(mappedBy = "workspace", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _channels: MutableList<SlackChannel> = mutableListOf(),

    @Column(nullable = false, updatable = false)
    val connectedAt: LocalDateTime
) {
    val channels: List<SlackChannel> get() = _channels.toList()

    val monitoredChannels: List<SlackChannel>
        get() = _channels.filter { it.isMonitored }

    fun addChannel(channelId: String, name: String): SlackChannel
    fun removeChannel(channelId: String)
    fun enableMonitoring(channelId: String)
    fun disableMonitoring(channelId: String)
}

@Entity
@Table(name = "slack_channels")
class SlackChannel(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val channelId: String,               // 슬랙 채널 ID (C로 시작)

    @Column(nullable = false)
    var name: String,                    // 채널 이름

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    val workspace: SlackWorkspace,

    @Column(nullable = false)
    var isMonitored: Boolean = false     // 모니터링 대상 여부
)
```

### ReminderConfig (리마인더 설정)

```kotlin
@Entity
@Table(name = "reminder_configs")
class ReminderConfig(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    val workspace: SlackWorkspace,

    @Column(nullable = false)
    val targetChannelId: String,         // 리마인더 발송 슬랙 채널 ID

    @Column(nullable = false)
    val cronExpression: String,          // cron 표현식 (예: "0 0 9 * * MON")

    @Column(nullable = false)
    val topN: Int,                       // 상위 N개 용어

    @Column(nullable = false)
    var isEnabled: Boolean = true
)
```

### SourceDocument (원천 문서) - Elasticsearch Document

```kotlin
@Document(indexName = "source_documents")
data class SourceDocument(
    @Id
    val id: String? = null,

    @Field(type = FieldType.Text, analyzer = "korean_analyzer")
    val content: String,                 // 원천 텍스트 (형태소 분석 대상)

    @Field(type = FieldType.Object)
    val metadata: SourceMetadata,        // 소스별 메타데이터 (sealed interface)

    @Field(type = FieldType.Date)
    val timestamp: Instant               // 원본 생성 시각
)
```

### SourceMetadata (메타데이터) - Sealed Interface

```kotlin
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "source"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = SlackMetadata::class, name = "slack"),
    JsonSubTypes.Type(value = GmailMetadata::class, name = "gmail"),
    JsonSubTypes.Type(value = WebhookMetadata::class, name = "webhook")
)
sealed interface SourceMetadata {
    val source: String
}

data class SlackMetadata(
    override val source: String = "slack",
    val workspaceId: String,             // 슬랙 팀 ID
    val channelId: String,               // 슬랙 채널 ID
    val messageId: String,               // 슬랙 메시지 ID (ts)
    val userId: String                   // 슬랙 사용자 ID
) : SourceMetadata

data class GmailMetadata(
    override val source: String = "gmail",
    val messageId: String,               // Gmail 메시지 ID
    val threadId: String,                // Gmail 스레드 ID
    val from: String,                    // 발신자
    val to: List<String>,                // 수신자 목록
    val cc: List<String>,                // 참조 목록
    val subject: String                  // 제목
) : SourceMetadata

data class WebhookMetadata(
    override val source: String = "webhook",
    val webhookId: String,               // 웹훅 ID
    val eventType: String                // 이벤트 타입
) : SourceMetadata
```

---

## Elasticsearch Index Design

### source_documents Index

다양한 소스(Slack, Gmail, Webhook 등)의 원천 데이터를 저장하는 범용 인덱스입니다.

```json
{
  "settings": {
    "analysis": {
      "tokenizer": {
        "nori_user_dict_tokenizer": {
          "type": "nori_tokenizer",
          "user_dictionary_rules": []
        }
      },
      "filter": {
        "synonym_filter": {
          "type": "synonym",
          "synonyms": []
        }
      },
      "analyzer": {
        "korean_analyzer": {
          "type": "custom",
          "tokenizer": "nori_user_dict_tokenizer",
          "filter": [
            "synonym_filter",
            "lowercase"
          ]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "content": {
        "type": "text",
        "analyzer": "korean_analyzer"
      },
      "metadata": {
        "type": "object",
        "enabled": true
      },
      "timestamp": {
        "type": "date"
      }
    }
  }
}
```

### Metadata Examples by Source

**Slack:**

```json
{
  "content": "오늘 API 연동 작업 완료했습니다.",
  "metadata": {
    "source": "slack",
    "workspaceId": "T123456",
    "channelId": "C789012",
    "messageId": "1234567890.123456",
    "userId": "U456789"
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Gmail:**

```json
{
  "content": "프로젝트 진행 상황 공유드립니다.",
  "metadata": {
    "source": "gmail",
    "messageId": "msg-abc123",
    "threadId": "thread-xyz789",
    "from": "sender@example.com",
    "to": [
      "receiver@example.com"
    ],
    "cc": [
      "cc@example.com"
    ],
    "subject": "프로젝트 진행 상황"
  },
  "timestamp": "2024-01-15T09:00:00Z"
}
```

**Webhook:**

```json
{
  "content": "배포가 완료되었습니다.",
  "metadata": {
    "source": "webhook",
    "webhookId": "hook-123",
    "eventType": "deployment"
  },
  "timestamp": "2024-01-15T11:00:00Z"
}
```

### Term Frequency Aggregation

용어 빈도 집계는 Elasticsearch의 `terms` aggregation을 사용합니다.
`exclude` 목록은 `ignored_terms` 테이블에서 조회한 무시된 단어입니다.

```json
GET /source_documents/_search
{
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        {
          "term": {
            "metadata.source": "slack"
          }
        },
        {
          "term": {
            "metadata.workspaceId": "T123456"
          }
        },
        {
          "range": {
            "timestamp": {
              "gte": "now-7d/d",
              "lte": "now/d"
            }
          }
        }
      ]
    }
  },
  "aggs": {
    "top_terms": {
      "terms": {
        "field": "content",
        "size": 100,
        "exclude": [
          "무시된용어1",
          "무시된용어2"
        ]
      }
    }
  }
}
```

---

## Elasticsearch Synchronization

MySQL의 용어/동의어 데이터를 Elasticsearch의 user_dictionary와 synonym filter에 적용하는 흐름:

```mermaid
sequenceDiagram
    participant Admin as 관리자
    participant App as Application
    participant MySQL as MySQL
    participant ES as Elasticsearch
    Admin ->> App: 용어/동의어 추가/삭제
    App ->> MySQL: 저장
    App ->> MySQL: reindexingRequired = true
    Note over App: 새벽 2시 스케줄러
    App ->> MySQL: reindexingRequired 확인
    alt 변경됨
        App ->> MySQL: 용어/동의어 조회
        App ->> ES: 새 인덱스 생성 (user_dictionary + synonym filter)
        App ->> ES: 원천 데이터 재인덱싱
        App ->> ES: alias 전환
        App ->> ES: 이전 인덱스 삭제
        App ->> MySQL: reindexingRequired = false
    end
```

**Synonym Format (MySQL → Elasticsearch)**:

```kotlin
// MySQL: Term(name="API") -> Synonym("에이피아이"), Synonym("인터페이스")
// ES format: "에이피아이, 인터페이스 => API"

fun buildSynonymRules(): List<String> {
    return termRepository.findAllWithSynonyms()
        .filter { it.synonyms.isNotEmpty() }
        .map { term ->
            val synonymNames = term.synonyms.joinToString(", ") { it.name }
            "$synonymNames => ${term.name}"
        }
}
```

**User Dictionary (MySQL → Elasticsearch)**:

Term의 name을 nori tokenizer의 user_dictionary_rules로 사용합니다.

```kotlin
// MySQL: Term(name="삼성전자"), Term(name="인공지능")
// ES format: ["삼성전자", "인공지능"]

fun buildUserDictionaryRules(): List<String> {
    return termRepository.findAll()
        .map { it.name }
}
```

**Ignored Terms (용어 추출 시 제외)**:

무시된 단어는 ES 인덱싱이 아닌 용어 빈도 집계 시 제외합니다.

```kotlin
// 용어 빈도 집계 시 무시된 단어 제외
fun getIgnoredTermNames(): List<String> {
    return ignoredTermRepository.findAll()
        .map { it.name }
}
```

---

## Reindexing Strategy (Zero-Downtime)

재인덱싱 시 무중단 서비스를 위해 **Alias Swap** 전략 사용:

```mermaid
flowchart LR
    subgraph s1["1. 현재 상태"]
        A1[source_documents_v1] <-.->|alias| AL1[source_documents]
    end

    subgraph s2["2. 새 인덱스 생성 + 재인덱싱"]
        A2[source_documents_v1] <-.->|alias| AL2[source_documents]
        B2[source_documents_v2]
        A2 -->|reindex| B2
    end

    subgraph s3["3. Alias 전환 + 이전 삭제"]
        B3[source_documents_v2] <-.->|alias| AL3[source_documents]
    end

    s1 --> s2 --> s3
```

---

## Environment Configuration

### Overview

```mermaid
flowchart TB
    subgraph Local["Local Development"]
        App1[Application]
        DC[docker-compose]
        MySQL1[(MySQL 8)]
        ES1[(Elasticsearch 8 + Nori)]
        App1 --> MySQL1
        App1 --> ES1
        DC -.->|manages| MySQL1
        DC -.->|manages| ES1
    end

    subgraph Test["Test Environment"]
        App2[Application]
        TC[Testcontainers]
        H2[(H2 MySQL mode)]
        ES2[(Elasticsearch 8 + Nori)]
        App2 --> H2
        App2 --> ES2
        TC -.->|manages| ES2
    end
```

### Environment Details

| Environment | RDB             | Search Engine          | Management     |
|-------------|-----------------|------------------------|----------------|
| Local       | MySQL 8         | Elasticsearch 8 + Nori | docker-compose |
| Test        | H2 (MySQL mode) | Elasticsearch 8 + Nori | Testcontainers |

---

## Layered Architecture

```mermaid
flowchart TB
    subgraph Presentation["Presentation Layer"]
        Controller[Controller]
        DTO[Request/Response DTO]
    end

    subgraph Application["Application Layer"]
        Service[Application Service]
        Command[Command/Query]
    end

    subgraph Domain["Domain Layer"]
        Entity[Entity]
        DomainService[Domain Service]
        Repository[Repository Interface]
    end

    subgraph Infrastructure["Infrastructure Layer"]
        JPA[JPA Repository]
        ESRepo[Elasticsearch Repository]
        SlackAPI[Slack API Client]
        Scheduler[Scheduler]
    end

    Presentation --> Application
    Application --> Domain
    Application --> Infrastructure
    Infrastructure -.->|implements| Repository
```

### Layer Responsibilities

| Layer          | Responsibility       | Components                         |
|----------------|----------------------|------------------------------------|
| Presentation   | HTTP 요청/응답 처리, 입력 검증 | Controller, Request/Response DTO   |
| Application    | 유스케이스 조율, 트랜잭션 관리    | Application Service, Command/Query |
| Domain         | 핵심 비즈니스 규칙, 도메인 모델   | Entity, Domain Service, Repository |
| Infrastructure | 기술적 구현, 외부 시스템 연동    | JPA Repository, ES Repository, API |

---

## Package Structure

```
com.mkroo.termbase/
├── presentation/
│   ├── controller/
│   │   ├── TermController.kt
│   │   ├── IgnoredTermController.kt
│   │   ├── SlackWorkspaceController.kt
│   │   └── ReminderController.kt
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   └── advice/
│       └── GlobalExceptionHandler.kt
│
├── application/
│   └── service/
│       ├── TermService.kt
│       ├── IgnoredTermService.kt
│       ├── SlackWorkspaceService.kt
│       ├── ReminderService.kt
│       └── ReindexingService.kt
│
├── domain/
│   ├── model/
│   │   ├── term/
│   │   │   ├── Term.kt                // Aggregate Root (동의어 포함)
│   │   │   └── Synonym.kt
│   │   ├── ignoredterm/
│   │   │   └── IgnoredTerm.kt         // Independent Entity
│   │   ├── slack/
│   │   │   ├── SlackWorkspace.kt      // Aggregate Root (채널 포함)
│   │   │   └── SlackChannel.kt
│   │   ├── document/
│   │   │   ├── SourceDocument.kt      // ES 문서 (@Document)
│   │   │   ├── SourceMetadata.kt      // sealed interface + @JsonTypeInfo
│   │   │   ├── SlackMetadata.kt
│   │   │   ├── GmailMetadata.kt
│   │   │   └── WebhookMetadata.kt
│   │   └── reminder/
│   │       └── ReminderConfig.kt
│   ├── repository/
│   │   ├── TermRepository.kt
│   │   ├── IgnoredTermRepository.kt   // IgnoredTerm 전용 Repository
│   │   ├── SlackWorkspaceRepository.kt
│   │   ├── SourceDocumentRepository.kt
│   │   └── ReminderConfigRepository.kt
│   ├── service/
│   │   └── TermExtractor.kt
│   └── exception/
│       └── TermException.kt
│
└── infrastructure/
    ├── persistence/
    │   ├── jpa/
    │   │   ├── JpaTermRepository.kt
    │   │   ├── JpaIgnoredTermRepository.kt
    │   │   ├── JpaSlackWorkspaceRepository.kt
    │   │   └── JpaReminderConfigRepository.kt
    │   └── elasticsearch/
    │       └── EsSourceDocumentRepository.kt
    ├── external/
    │   └── slack/
    │       └── SlackApiClient.kt
    ├── scheduler/
    │   ├── ReindexingScheduler.kt
    │   └── ReminderScheduler.kt
    └── config/
        ├── JpaConfig.kt
        ├── ElasticsearchConfig.kt
        └── SlackConfig.kt
```

---

## Key Design Decisions

### 1. IgnoredTerm은 Term과 독립된 엔티티

`IgnoredTerm`은 `Term`과 완전히 독립된 엔티티입니다. 용어 사전에 등록되지 않은 단어도 무시 처리할 수 있습니다.

**설계:**

- IgnoredTerm은 독자적인 PK를 가짐
- Term, Synonym, IgnoredTerm 간의 name 중복 불허
- `IgnoredTermRepository`를 통해 직접 관리

**장점:**

- 용어 사전에 등록되지 않은 일반 단어도 무시 처리 가능
- 무시된 단어의 수정/삭제가 용어 사전에 영향 없음
- 용어 추출 시 무시 목록 조회가 단순해짐

**제약조건:**

- 무시할 단어는 용어 사전(Term)에 정의되지 않은 용어여야 함
- 무시할 단어는 동의어(Synonym)로 등록되지 않은 용어여야 함
- 무시 사유는 필수

### 2. Term은 Aggregate Root (동의어만 포함)

`Term`이 Aggregate Root이며, `Synonym`만 이에 종속됩니다.

**설계 원칙:**

- 동의어는 Term을 통해서만 접근/수정 가능
- `TermRepository`만 제공 (별도의 SynonymRepository 없음)
- 동의어 추가/삭제는 Term의 메서드를 통해 변경

**동의어 제약조건:**

- 동의어는 용어 사전에 정의되지 않은 용어여야 함
- 동의어는 다른 대표어의 동의어로 이미 등록되지 않아야 함
- 동의어는 대표어와 동일할 수 없음
- 동의어는 무시된 단어로 등록되지 않은 용어여야 함

### 3. SlackWorkspace는 Aggregate Root

`SlackWorkspace`가 Aggregate Root이며, `SlackChannel`은 이에 종속됩니다.

**설계 원칙:**

- 채널은 워크스페이스를 통해서만 접근/수정 가능
- `SlackWorkspaceRepository`만 제공 (별도의 ChannelRepository 없음)
- 채널 모니터링 설정은 워크스페이스의 메서드를 통해 변경

### 4. 재인덱싱 트리거 조건

재인덱싱은 아래 조건에서만 수행됩니다:

- 동의어 추가/삭제 시 (synonym filter 변경)
- 용어 추가/삭제 시 (user_dictionary 변경)
- `reindexingRequired` 플래그로 관리
- 매일 새벽 2시 스케줄러에서 플래그 확인

### 5. Repository 인터페이스 스타일

Spring Data의 최소 `Repository<T, ID>` 인터페이스를 사용합니다.

```kotlin
interface JpaTermRepository : Repository<Term, Long>, TermRepository {
    override fun save(term: Term): Term
    override fun findByName(name: String): Term?
    // ...
}

interface JpaIgnoredTermRepository : Repository<IgnoredTerm, Long>, IgnoredTermRepository {
    override fun save(ignoredTerm: IgnoredTerm): IgnoredTerm
    override fun findByName(name: String): IgnoredTerm?
    override fun deleteByName(name: String)
    override fun existsByName(name: String): Boolean
    // ...
}
```
