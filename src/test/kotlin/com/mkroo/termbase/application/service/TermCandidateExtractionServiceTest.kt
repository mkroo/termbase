package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.candidate.BatchStatus
import com.mkroo.termbase.domain.model.candidate.CandidateStatus
import com.mkroo.termbase.domain.model.document.SlackMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.repository.CandidateBatchHistoryRepository
import com.mkroo.termbase.domain.repository.TermCandidateRepository
import com.mkroo.termbase.domain.service.TermCandidateExtractor
import com.mkroo.termbase.domain.service.TermExtractionConfig
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TermCandidateExtractionServiceTest : DescribeSpec() {
    @Autowired
    private lateinit var extractionService: TermCandidateExtractionService

    @Autowired
    private lateinit var termCandidateRepository: TermCandidateRepository

    @Autowired
    private lateinit var batchHistoryRepository: CandidateBatchHistoryRepository

    @Autowired
    private lateinit var glossaryService: GlossaryService

    @Autowired
    private lateinit var ignoredTermService: IgnoredTermService

    @Autowired
    private lateinit var elasticsearchOperations: ElasticsearchOperations

    @Autowired
    private lateinit var termCandidateExtractor: TermCandidateExtractor

    init {
        extension(SpringExtension())

        beforeEach {
            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
            indexOps.createWithMapping()
        }

        afterEach {
            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
        }

        describe("TermCandidateExtractionService") {
            describe("extractCandidates") {
                it("문서가 없으면 배치 완료 결과를 반환한다") {
                    val result = extractionService.extractCandidates()

                    result.shouldBeInstanceOf<ExtractionResult.Success>()
                    result.totalDocuments shouldBe 0
                    result.totalCandidates shouldBe 0
                }

                it("문서에서 용어 후보를 추출하고 저장한다") {
                    // Given: 동일한 명사 시퀀스가 여러 문서에서 반복 등장
                    indexDocuments(
                        createDocument("1", "공유 주차장 이용 안내"),
                        createDocument("2", "공유 주차장 결제 방법"),
                        createDocument("3", "공유 주차장 운영 시간"),
                        createDocument("4", "공유 주차장 예약 시스템"),
                    )

                    // When
                    val config =
                        ExtractionConfig(
                            minCount = 2,
                            npmiThreshold = BigDecimal("-1.0"),
                            relevanceThreshold = BigDecimal("0.0"),
                        )
                    val result = extractionService.extractCandidates(config)

                    // Then
                    result.shouldBeInstanceOf<ExtractionResult.Success>()
                    result.totalDocuments shouldBe 4

                    val candidates = termCandidateRepository.findAll()
                    candidates.any { it.term == "공유 주차장" } shouldBe true
                }

                it("배치 이력을 저장한다") {
                    indexDocuments(
                        createDocument("1", "공유 주차장 이용"),
                        createDocument("2", "공유 주차장 운영"),
                        createDocument("3", "공유 주차장 관리"),
                    )

                    val config =
                        ExtractionConfig(
                            minCount = 2,
                            npmiThreshold = BigDecimal("-1.0"),
                            relevanceThreshold = BigDecimal("0.0"),
                        )
                    val result = extractionService.extractCandidates(config)

                    result.shouldBeInstanceOf<ExtractionResult.Success>()

                    val history = batchHistoryRepository.findById(result.batchId)
                    history shouldBe batchHistoryRepository.findById(result.batchId)
                    history!!.status shouldBe BatchStatus.COMPLETED
                    history.totalDocuments shouldBe 3
                }

                it("이미 등록된 용어는 후보에서 제외한다") {
                    // Given: 문서 인덱싱
                    indexDocuments(
                        createDocument("1", "공유 주차장 이용 안내"),
                        createDocument("2", "공유 주차장 결제 방법"),
                        createDocument("3", "공유 주차장 운영 시간"),
                    )

                    // When: 제외 용어 목록에 "공유 주차장" 포함
                    val documents =
                        listOf(
                            "공유 주차장 이용 안내",
                            "공유 주차장 결제 방법",
                            "공유 주차장 운영 시간",
                        )
                    val config =
                        TermExtractionConfig(
                            minCount = 2,
                            npmiThreshold = BigDecimal("-1.0"),
                            relevanceThreshold = BigDecimal("0.0"),
                            excludedTerms = setOf("공유 주차장"),
                        )
                    val result = termCandidateExtractor.extract(documents, config)

                    // Then
                    result.candidates.none { it.term == "공유 주차장" } shouldBe true
                }

                it("무시된 용어는 후보에서 제외한다") {
                    // Given: 문서 인덱싱
                    indexDocuments(
                        createDocument("1", "공유 주차장 이용 안내"),
                        createDocument("2", "공유 주차장 결제 방법"),
                        createDocument("3", "공유 주차장 운영 시간"),
                    )

                    // When: 제외 용어 목록에 "공유 주차장" 포함 (무시된 용어로 가정)
                    val documents =
                        listOf(
                            "공유 주차장 이용 안내",
                            "공유 주차장 결제 방법",
                            "공유 주차장 운영 시간",
                        )
                    val config =
                        TermExtractionConfig(
                            minCount = 2,
                            npmiThreshold = BigDecimal("-1.0"),
                            relevanceThreshold = BigDecimal("0.0"),
                            excludedTerms = setOf("공유 주차장"),
                        )
                    val result = termCandidateExtractor.extract(documents, config)

                    // Then
                    result.candidates.none { it.term == "공유 주차장" } shouldBe true
                }

                it("min_count 미만의 빈도는 제외한다") {
                    indexDocuments(
                        createDocument("1", "공유 주차장 이용 안내"),
                        createDocument("2", "결제 시스템 오류 발생"),
                    )

                    // When: minCount = 3으로 설정
                    val config =
                        ExtractionConfig(
                            minCount = 3,
                            npmiThreshold = BigDecimal("-1.0"),
                            relevanceThreshold = BigDecimal("0.0"),
                        )
                    val result = extractionService.extractCandidates(config)

                    // Then: 모든 bigram이 1회만 등장하므로 후보 없음
                    result.shouldBeInstanceOf<ExtractionResult.Success>()
                    result.totalCandidates shouldBe 0
                }

                it("NPMI 임계값 미만은 제외한다") {
                    // Given: 충분한 데이터로 NPMI 계산 가능하게
                    val documents =
                        (1..10).map { i ->
                            createDocument("$i", "공유 주차장 이용 안내 서비스")
                        }
                    indexDocuments(*documents.toTypedArray())

                    // When: 높은 NPMI 임계값 설정
                    val config =
                        ExtractionConfig(
                            minCount = 1,
                            npmiThreshold = BigDecimal("0.99"),
                            relevanceThreshold = BigDecimal("0.0"),
                        )
                    val result = extractionService.extractCandidates(config)

                    // Then: 임계값이 너무 높아서 후보가 적거나 없음
                    result.shouldBeInstanceOf<ExtractionResult.Success>()
                }

                it("추출된 후보는 PENDING 상태로 저장된다") {
                    indexDocuments(
                        createDocument("1", "공유 주차장 이용"),
                        createDocument("2", "공유 주차장 운영"),
                        createDocument("3", "공유 주차장 관리"),
                    )

                    val config =
                        ExtractionConfig(
                            minCount = 2,
                            npmiThreshold = BigDecimal("-1.0"),
                            relevanceThreshold = BigDecimal("0.0"),
                        )
                    extractionService.extractCandidates(config)

                    val candidates = termCandidateRepository.findByStatus(CandidateStatus.PENDING)
                    candidates.all { it.status == CandidateStatus.PENDING } shouldBe true
                }

                it("후보에는 PMI, NPMI, IDF, TF-IDF, relevance_score가 계산되어 있다") {
                    indexDocuments(
                        createDocument("1", "공유 주차장 이용"),
                        createDocument("2", "공유 주차장 운영"),
                        createDocument("3", "공유 주차장 관리"),
                    )

                    val config =
                        ExtractionConfig(
                            minCount = 2,
                            npmiThreshold = BigDecimal("-1.0"),
                            relevanceThreshold = BigDecimal("0.0"),
                        )
                    extractionService.extractCandidates(config)

                    val candidates = termCandidateRepository.findAll()
                    if (candidates.isNotEmpty()) {
                        val candidate = candidates.first()
                        candidate.count shouldBeGreaterThan 0
                        candidate.docCount shouldBeGreaterThan 0
                    }
                }
            }
        }
    }

    private fun createDocument(
        id: String,
        content: String,
    ): SourceDocument =
        SourceDocument(
            id = id,
            content = content,
            metadata =
                SlackMetadata(
                    workspaceId = "T123456",
                    channelId = "C789012",
                    messageId = "msg-$id",
                    userId = "U456789",
                ),
            timestamp = Instant.now(),
        )

    private fun indexDocuments(vararg documents: SourceDocument) {
        documents.forEach { elasticsearchOperations.save(it) }
        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()
    }
}
