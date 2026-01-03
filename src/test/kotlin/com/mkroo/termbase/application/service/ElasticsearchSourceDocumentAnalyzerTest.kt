package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.document.SlackMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.document.TimeSeriesInterval
import com.mkroo.termbase.domain.model.ignoredterm.IgnoredTerm
import com.mkroo.termbase.domain.model.term.Term
import com.mkroo.termbase.domain.repository.IgnoredTermRepository
import com.mkroo.termbase.domain.repository.TermRepository
import com.mkroo.termbase.domain.service.SourceDocumentAnalyzer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDateTime

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
class ElasticsearchSourceDocumentAnalyzerTest : DescribeSpec() {
    @Autowired
    private lateinit var sourceDocumentAnalyzer: SourceDocumentAnalyzer

    @Autowired
    private lateinit var elasticsearchOperations: ElasticsearchOperations

    @Autowired
    private lateinit var ignoredTermRepository: IgnoredTermRepository

    @Autowired
    private lateinit var termRepository: TermRepository

    init {
        extension(SpringExtension())

        beforeEach {
            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
            indexOps.createWithMapping()

            ignoredTermRepository.findAll().forEach { ignoredTermRepository.delete(it) }
            termRepository.findAll().forEach { termRepository.delete(it) }
        }

        afterEach {
            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }

            ignoredTermRepository.findAll().forEach { ignoredTermRepository.delete(it) }
            termRepository.findAll().forEach { termRepository.delete(it) }
        }

        describe("ElasticsearchSourceDocumentAnalyzer") {
            describe("getTopFrequentTerms") {
                it("should return empty list when no documents exist") {
                    val result = sourceDocumentAnalyzer.getTopFrequentTerms(10)

                    result.shouldBeEmpty()
                }

                it("should return empty list when index does not exist") {
                    val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
                    indexOps.delete()

                    val result = sourceDocumentAnalyzer.getTopFrequentTerms(10)

                    result.shouldBeEmpty()
                }

                it("should return top frequent terms from slack messages") {
                    val timestamp = Instant.now()
                    val slackMetadata =
                        SlackMetadata(
                            workspaceId = "T123456",
                            channelId = "C789012",
                            messageId = "msg-001",
                            userId = "U456789",
                        )

                    val document1 =
                        SourceDocument(
                            id = "doc-001",
                            content = "API 개발을 시작합니다",
                            metadata = slackMetadata,
                            timestamp = timestamp,
                        )
                    document1.id shouldBe "doc-001"
                    document1.content shouldBe "API 개발을 시작합니다"
                    document1.metadata shouldBe slackMetadata
                    document1.timestamp shouldBe timestamp

                    elasticsearchOperations.save(document1)
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-002",
                            content = "API 문서를 작성합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-002",
                                    userId = "U456789",
                                ),
                            timestamp = Instant.now(),
                        ),
                    )
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-003",
                            content = "API 테스트를 진행합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-003",
                                    userId = "U456789",
                                ),
                            timestamp = Instant.now(),
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.getTopFrequentTerms(10)

                    result.shouldNotBeEmpty()
                    result.shouldHaveAtLeastSize(1)
                    result.first().term shouldBe "api"
                    result.first().count shouldBe 3
                }

                it("should limit results to specified size") {
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "프로젝트 개발 서비스 시스템 데이터 프로세스 모듈",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = Instant.now(),
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.getTopFrequentTerms(3)

                    result.shouldHaveSize(3)
                }

                context("무시된 단어 제외") {
                    it("should exclude ignored terms from results") {
                        ignoredTermRepository.save(
                            IgnoredTerm(
                                name = "api",
                                reason = "너무 일반적인 용어",
                                createdAt = LocalDateTime.now(),
                            ),
                        )

                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-001",
                                content = "API 개발을 시작합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-001",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-002",
                                content = "API 문서를 작성합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-002",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )

                        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                        val result = sourceDocumentAnalyzer.getTopFrequentTerms(10)

                        result.none { it.term == "api" } shouldBe true
                    }

                    it("should exclude multiple ignored terms from results") {
                        ignoredTermRepository.save(
                            IgnoredTerm(
                                name = "api",
                                reason = "너무 일반적인 용어",
                                createdAt = LocalDateTime.now(),
                            ),
                        )
                        ignoredTermRepository.save(
                            IgnoredTerm(
                                name = "개발",
                                reason = "너무 일반적인 용어",
                                createdAt = LocalDateTime.now(),
                            ),
                        )

                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-001",
                                content = "API 개발을 시작합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-001",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )

                        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                        val result = sourceDocumentAnalyzer.getTopFrequentTerms(10)

                        result.none { it.term == "api" } shouldBe true
                        result.none { it.term == "개발" } shouldBe true
                    }

                    it("should return all terms when no ignored terms exist") {
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-001",
                                content = "API 개발을 시작합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-001",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )

                        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                        val result = sourceDocumentAnalyzer.getTopFrequentTerms(10)

                        result.shouldNotBeEmpty()
                        result.any { it.term == "api" } shouldBe true
                    }
                }
            }

            describe("getTermFrequency") {
                it("should return 0 when index does not exist") {
                    val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
                    indexOps.delete()

                    val result = sourceDocumentAnalyzer.getTermFrequency("api")

                    result shouldBe 0
                }

                it("should return 0 when no documents contain the term") {
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "프로젝트를 시작합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = Instant.now(),
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.getTermFrequency("api")

                    result shouldBe 0
                }

                it("should return count of documents containing the term") {
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "API 개발을 시작합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = Instant.now(),
                        ),
                    )
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-002",
                            content = "API 문서를 작성합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-002",
                                    userId = "U456789",
                                ),
                            timestamp = Instant.now(),
                        ),
                    )
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-003",
                            content = "프로젝트를 완료합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-003",
                                    userId = "U456789",
                                ),
                            timestamp = Instant.now(),
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.getTermFrequency("api")

                    result shouldBe 2
                }

                context("동의어 검색") {
                    it("should include documents containing synonyms in frequency count") {
                        // Given: Term with synonyms
                        val term = Term(name = "Kubernetes", definition = "컨테이너 오케스트레이션 플랫폼")
                        term.addSynonym("K8s")
                        term.addSynonym("쿠버네티스")
                        termRepository.save(term)

                        // Document with exact term name
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-001",
                                content = "Kubernetes 클러스터를 설정합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-001",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )
                        // Document with synonym "K8s"
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-002",
                                content = "K8s 클러스터 헬스체크 스크립트입니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-002",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )
                        // Document with synonym "쿠버네티스"
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-003",
                                content = "쿠버네티스 노드 상태를 확인합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-003",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )
                        // Document without term or synonyms
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-004",
                                content = "Docker 컨테이너를 빌드합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-004",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )

                        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                        // When: Get frequency by term name
                        val result = sourceDocumentAnalyzer.getTermFrequency("Kubernetes")

                        // Then: Should count documents with term and synonyms
                        result shouldBe 3
                    }
                }
            }

            describe("getTermFrequencyTimeSeries") {
                it("should return empty list when index does not exist") {
                    val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
                    indexOps.delete()

                    val result = sourceDocumentAnalyzer.getTermFrequencyTimeSeries("api", TimeSeriesInterval.DAY, 30)

                    result.shouldBeEmpty()
                }

                it("should use default days parameter") {
                    val now = Instant.now()
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "API 개발을 시작합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = now,
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    // Call without days parameter to use default value (30)
                    val result = sourceDocumentAnalyzer.getTermFrequencyTimeSeries("api", TimeSeriesInterval.DAY)

                    result.shouldNotBeEmpty()
                }

                it("should return empty list when no documents match the term") {
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "프로젝트를 시작합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = Instant.now(),
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.getTermFrequencyTimeSeries("api", TimeSeriesInterval.DAY, 30)

                    result.shouldBeEmpty()
                }

                it("should return time series with DAY interval") {
                    val now = Instant.now()
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "API 개발을 시작합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = now,
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.getTermFrequencyTimeSeries("api", TimeSeriesInterval.DAY, 30)

                    result.shouldNotBeEmpty()
                    result.first().count shouldBeGreaterThanOrEqual 1
                }

                it("should return time series with WEEK interval") {
                    val now = Instant.now()
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "API 개발을 시작합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = now,
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.getTermFrequencyTimeSeries("api", TimeSeriesInterval.WEEK, 90)

                    result.shouldNotBeEmpty()
                    result.first().count shouldBeGreaterThanOrEqual 1
                }

                it("should return time series with MONTH interval") {
                    val now = Instant.now()
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "API 개발을 시작합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = now,
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.getTermFrequencyTimeSeries("api", TimeSeriesInterval.MONTH, 365)

                    result.shouldNotBeEmpty()
                    result.first().count shouldBeGreaterThanOrEqual 1
                }

                context("동의어 검색") {
                    it("should include documents containing synonyms in time series count") {
                        // Given: Term with synonyms
                        val term = Term(name = "Kubernetes", definition = "컨테이너 오케스트레이션 플랫폼")
                        term.addSynonym("K8s")
                        term.addSynonym("쿠버네티스")
                        termRepository.save(term)

                        val now = Instant.now()
                        // Document with exact term name
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-001",
                                content = "Kubernetes 클러스터를 설정합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-001",
                                        userId = "U456789",
                                    ),
                                timestamp = now,
                            ),
                        )
                        // Document with synonym "K8s"
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-002",
                                content = "K8s 클러스터 헬스체크 스크립트입니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-002",
                                        userId = "U456789",
                                    ),
                                timestamp = now.minusSeconds(60),
                            ),
                        )
                        // Document with synonym "쿠버네티스"
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-003",
                                content = "쿠버네티스 노드 상태를 확인합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-003",
                                        userId = "U456789",
                                    ),
                                timestamp = now.minusSeconds(120),
                            ),
                        )

                        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                        // When: Get time series by term name
                        val result = sourceDocumentAnalyzer.getTermFrequencyTimeSeries("Kubernetes", TimeSeriesInterval.DAY, 30)

                        // Then: Should count documents with term and synonyms
                        result.shouldNotBeEmpty()
                        result.sumOf { it.count } shouldBe 3
                    }
                }
            }

            describe("searchDocumentsByTerm") {
                it("should return empty list when index does not exist") {
                    val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
                    indexOps.delete()

                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("api", 10)

                    result.shouldBeEmpty()
                }

                it("should use default size parameter") {
                    repeat(5) { i ->
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-00$i",
                                content = "API 문서 $i",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-00$i",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )
                    }

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    // Call without size parameter to use default value (10)
                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("API")

                    // Should return all 5 documents (less than default 10)
                    result.shouldHaveSize(5)
                }

                it("should return empty list when no documents match the term") {
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "프로젝트를 시작합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = Instant.now(),
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("api", 10)

                    result.shouldBeEmpty()
                }

                it("should return documents with highlighted content") {
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "API 개발을 시작합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = Instant.now(),
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("API", 10)

                    result.shouldNotBeEmpty()
                    result.first().content shouldContain "API"
                    result.first().highlightedContent shouldContain "<em>"
                }

                it("should limit results to specified size") {
                    repeat(5) { i ->
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-00$i",
                                content = "API 문서 $i",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-00$i",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )
                    }

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("API", 3)

                    result.shouldHaveSize(3)
                }

                it("should coerce size to maximum limit") {
                    repeat(5) { i ->
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-00$i",
                                content = "API 문서 $i",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-00$i",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )
                    }

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    // Requesting more than MAX_DOCUMENT_SIZE (100) should be coerced
                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("API", 200)

                    // Should return all 5 documents (less than 100)
                    result.shouldHaveSize(5)
                }

                it("should coerce size to minimum of 1") {
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "API 개발을 시작합니다",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = Instant.now(),
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    // Requesting 0 or negative should be coerced to 1
                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("API", 0)

                    result.shouldHaveSize(1)
                }

                it("should sort documents by timestamp descending") {
                    val now = Instant.now()
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-001",
                            content = "API 첫 번째 문서",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-001",
                                    userId = "U456789",
                                ),
                            timestamp = now.minusSeconds(3600),
                        ),
                    )
                    elasticsearchOperations.save(
                        SourceDocument(
                            id = "doc-002",
                            content = "API 두 번째 문서",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "T123456",
                                    channelId = "C789012",
                                    messageId = "msg-002",
                                    userId = "U456789",
                                ),
                            timestamp = now,
                        ),
                    )

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("API", 10)

                    result.shouldHaveSize(2)
                    result.first().id shouldBe "doc-002" // More recent document first
                    result.last().id shouldBe "doc-001"
                }

                context("동의어 검색") {
                    it("should include documents containing synonyms when searching by term name") {
                        // Given: Term with synonyms
                        val term = Term(name = "Kubernetes", definition = "컨테이너 오케스트레이션 플랫폼")
                        term.addSynonym("K8s")
                        term.addSynonym("쿠버네티스")
                        termRepository.save(term)

                        val now = Instant.now()
                        // Document with exact term name
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-001",
                                content = "Kubernetes 클러스터를 설정합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-001",
                                        userId = "U456789",
                                    ),
                                timestamp = now,
                            ),
                        )
                        // Document with synonym "K8s"
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-002",
                                content = "K8s 클러스터 헬스체크 스크립트 공유합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-002",
                                        userId = "U456789",
                                    ),
                                timestamp = now.minusSeconds(60),
                            ),
                        )
                        // Document with synonym "쿠버네티스"
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-003",
                                content = "쿠버네티스 노드 상태를 확인해요",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-003",
                                        userId = "U456789",
                                    ),
                                timestamp = now.minusSeconds(120),
                            ),
                        )
                        // Document without term or synonyms
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-004",
                                content = "Docker 컨테이너를 빌드합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-004",
                                        userId = "U456789",
                                    ),
                                timestamp = now.minusSeconds(180),
                            ),
                        )

                        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                        // When: Search by term name
                        val result = sourceDocumentAnalyzer.searchDocumentsByTerm("Kubernetes", 10)

                        // Then: Should include documents with term and synonyms
                        result.shouldHaveSize(3)
                        result.map { it.id } shouldBe listOf("doc-001", "doc-002", "doc-003")
                    }

                    it("should highlight synonyms in the content") {
                        // Given: Term with synonyms
                        val term = Term(name = "API", definition = "Application Programming Interface")
                        term.addSynonym("에이피아이")
                        termRepository.save(term)

                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-001",
                                content = "에이피아이 연동 작업을 시작합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-001",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )

                        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                        // When: Search by term name
                        val result = sourceDocumentAnalyzer.searchDocumentsByTerm("API", 10)

                        // Then: Should include document with synonym and highlight it
                        result.shouldHaveSize(1)
                        result.first().highlightedContent shouldContain "<em>"
                    }

                    it("should return empty list when term has no registered synonyms and document does not contain term") {
                        // Given: Term without synonyms
                        val term = Term(name = "Docker", definition = "컨테이너 플랫폼")
                        termRepository.save(term)

                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-001",
                                content = "Kubernetes 설정을 변경합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-001",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )

                        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                        // When: Search by term name
                        val result = sourceDocumentAnalyzer.searchDocumentsByTerm("Docker", 10)

                        // Then: Should not find any documents
                        result.shouldBeEmpty()
                    }

                    it("should search only by term name when term is not registered") {
                        // Given: No term registered, just documents
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-001",
                                content = "Kubernetes 클러스터를 설정합니다",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-001",
                                        userId = "U456789",
                                    ),
                                timestamp = Instant.now(),
                            ),
                        )

                        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                        // When: Search by unregistered term
                        val result = sourceDocumentAnalyzer.searchDocumentsByTerm("Kubernetes", 10)

                        // Then: Should find document by exact match
                        result.shouldHaveSize(1)
                    }
                }

                context("합성어 검색 (US-11)") {
                    it("합성어로 검색하면 해당 합성어를 포함하는 문서만 반환한다") {
                        val now = Instant.now()
                        // "공유 주차장"을 포함하는 문서
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-001",
                                content = "공유 주차장에서 만나요",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-001",
                                        userId = "U456789",
                                    ),
                                timestamp = now,
                            ),
                        )
                        // "공유"만 포함하는 문서 (검색에서 제외되어야 함)
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-002",
                                content = "공유 자전거 대여",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-002",
                                        userId = "U456789",
                                    ),
                                timestamp = now.minusSeconds(60),
                            ),
                        )
                        // "주차장"만 포함하는 문서 (검색에서 제외되어야 함)
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-003",
                                content = "주차장 위치 알려주세요",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-003",
                                        userId = "U456789",
                                    ),
                                timestamp = now.minusSeconds(120),
                            ),
                        )

                        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                        // When: "공유 주차장"으로 검색
                        val result = sourceDocumentAnalyzer.searchDocumentsByTerm("공유 주차장", 10)

                        // Then: "공유 주차장"을 포함하는 문서만 반환
                        result.shouldHaveSize(1)
                        result.first().id shouldBe "doc-001"
                    }

                    it("순서가 다른 단어 조합은 검색되지 않는다") {
                        val now = Instant.now()
                        // "공유 주차장"을 포함하는 문서
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-001",
                                content = "공유 주차장에서 만나요",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-001",
                                        userId = "U456789",
                                    ),
                                timestamp = now,
                            ),
                        )
                        // 순서가 반대인 문서 (검색에서 제외되어야 함)
                        elasticsearchOperations.save(
                            SourceDocument(
                                id = "doc-002",
                                content = "주차장 공유 서비스",
                                metadata =
                                    SlackMetadata(
                                        workspaceId = "T123456",
                                        channelId = "C789012",
                                        messageId = "msg-002",
                                        userId = "U456789",
                                    ),
                                timestamp = now.minusSeconds(60),
                            ),
                        )

                        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                        // When: "공유 주차장"으로 검색
                        val result = sourceDocumentAnalyzer.searchDocumentsByTerm("공유 주차장", 10)

                        // Then: "공유 주차장" 순서로 포함하는 문서만 반환
                        result.shouldHaveSize(1)
                        result.first().id shouldBe "doc-001"
                    }
                }
            }
        }
    }
}
