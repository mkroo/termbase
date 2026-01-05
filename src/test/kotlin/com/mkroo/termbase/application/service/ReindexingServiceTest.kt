package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.document.SlackMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.reindex.ReindexingStatus
import com.mkroo.termbase.domain.model.term.Term
import com.mkroo.termbase.domain.repository.ReindexingStatusRepository
import com.mkroo.termbase.domain.repository.TermRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
class ReindexingServiceTest : DescribeSpec() {
    @Autowired
    private lateinit var reindexingService: ReindexingService

    @Autowired
    private lateinit var termRepository: TermRepository

    @Autowired
    private lateinit var reindexingStatusRepository: ReindexingStatusRepository

    @Autowired
    private lateinit var elasticsearchTemplate: ElasticsearchTemplate

    @Autowired
    private lateinit var sourceDocumentService: SourceDocumentService

    init {
        extension(SpringExtension())

        beforeEach {
            cleanupIndices()
            cleanupDatabase()
        }

        afterEach {
            cleanupIndices()
            cleanupDatabase()
        }

        describe("ReindexingService") {
            describe("reindex") {
                it("should create new index with alias when no previous index exists") {
                    val result = reindexingService.reindex()

                    result.previousIndex.shouldBeNull()
                    result.newIndex shouldStartWith "source_documents_v"
                    result.documentCount shouldBe 0
                    result.userDictionarySize shouldBe 0

                    val status = reindexingStatusRepository.findById(1)
                    status.shouldNotBeNull()
                    status.currentIndexName shouldBe result.newIndex
                    status.reindexingRequired shouldBe false
                }

                it("should include term names in user dictionary") {
                    termRepository.save(Term(name = "삼성전자", definition = "대한민국의 대표 기업"))
                    termRepository.save(Term(name = "인공지능", definition = "AI"))

                    val result = reindexingService.reindex()

                    result.userDictionarySize shouldBe 2
                }

                it("should include synonym rules when terms have synonyms") {
                    val term = termRepository.save(Term(name = "API", definition = "Application Programming Interface"))
                    term.addSynonym("에이피아이")
                    term.addSynonym("인터페이스")
                    termRepository.save(term)

                    val result = reindexingService.reindex()

                    result.userDictionarySize shouldBe 1
                    result.synonymRulesSize shouldBe 1
                }

                it("should handle terms with spaces by removing spaces") {
                    termRepository.save(Term(name = "공유 주차장", definition = "여러 사람이 함께 사용하는 주차장"))
                    termRepository.save(Term(name = "삼성전자", definition = "대한민국의 대표 기업"))

                    val result = reindexingService.reindex()

                    // "공유 주차장" -> "공유주차장", "삼성전자" -> 2 terms in user dictionary
                    result.userDictionarySize shouldBe 2
                    // "공유 주차장 => 공유주차장" char_filter 매핑
                    result.compoundWordMappingsSize shouldBe 1
                    result.previousIndex.shouldBeNull()
                    result.newIndex shouldStartWith "source_documents_v"
                }

                it("should handle synonyms with spaces") {
                    val term = termRepository.save(Term(name = "공유 주차장", definition = "여러 사람이 함께 사용하는 주차장"))
                    term.addSynonym("공유 파킹")
                    term.addSynonym("셰어드 파킹")
                    termRepository.save(term)

                    val result = reindexingService.reindex()

                    result.userDictionarySize shouldBe 1
                    // synonym rule: "공유파킹, 셰어드파킹 => 공유주차장" (동의어 → 대표어)
                    result.synonymRulesSize shouldBe 1
                    // char_filter mappings:
                    // 1. "공유 주차장 => 공유주차장" (용어 합성어)
                    // 2. "공유 파킹 => 공유파킹" (동의어 합성어)
                    // 3. "셰어드 파킹 => 셰어드파킹" (동의어 합성어)
                    result.compoundWordMappingsSize shouldBe 3
                }

                it("should generate char_filter mapping for term with spaces (compound word)") {
                    // Given: 띄어쓰기가 포함된 용어
                    termRepository.save(Term(name = "삼성 전자", definition = "대한민국의 대표 기업"))

                    // When: 재인덱싱
                    val result = reindexingService.reindex()

                    // Then: "삼성 전자 => 삼성전자" char_filter 매핑이 생성됨
                    result.compoundWordMappingsSize shouldBe 1
                    result.synonymRulesSize shouldBe 0
                    result.userDictionarySize shouldBe 1
                }

                it("should generate char_filter mappings for multiple terms with spaces") {
                    // Given: 띄어쓰기가 포함된 여러 용어
                    termRepository.save(Term(name = "삼성 전자", definition = "대한민국의 대표 기업"))
                    termRepository.save(Term(name = "현대 자동차", definition = "자동차 제조사"))
                    termRepository.save(Term(name = "인공지능", definition = "AI")) // 띄어쓰기 없는 용어

                    // When: 재인덱싱
                    val result = reindexingService.reindex()

                    // Then: 띄어쓰기 있는 용어만 char_filter 매핑 생성
                    // "삼성 전자 => 삼성전자", "현대 자동차 => 현대자동차"
                    result.compoundWordMappingsSize shouldBe 2
                    result.synonymRulesSize shouldBe 0
                    result.userDictionarySize shouldBe 3
                }

                it("should generate both compound char_filter and synonym rules") {
                    // Given: 띄어쓰기가 포함된 용어와 동의어
                    val term = termRepository.save(Term(name = "삼성 전자", definition = "대한민국의 대표 기업"))
                    term.addSynonym("삼전")
                    termRepository.save(term)

                    // When: 재인덱싱
                    val result = reindexingService.reindex()

                    // Then: 두 가지 규칙 모두 생성
                    // char_filter: "삼성 전자 => 삼성전자" (띄어쓰기 합성어)
                    // synonym: "삼전 => 삼성전자" (동의어)
                    result.compoundWordMappingsSize shouldBe 1
                    result.synonymRulesSize shouldBe 1
                }

                it("should handle when source_documents index exists as real index (not alias)") {
                    // Create source_documents as a real index (simulating ensureIndexExists behavior)
                    elasticsearchTemplate.execute { client ->
                        client.indices().create { builder ->
                            builder.index("source_documents")
                        }
                    }

                    // Reindex should succeed by reindexing from real index, then deleting it
                    val result = reindexingService.reindex()

                    result.newIndex shouldStartWith "source_documents_v"
                    // previousIndex is the real index that was converted to alias
                    result.previousIndex shouldBe "source_documents"
                }

                it("should preserve documents when reindexing from real index to alias system") {
                    // Create source_documents as a real index with documents
                    elasticsearchTemplate.execute { client ->
                        client.indices().create { builder ->
                            builder.index("source_documents")
                        }
                    }

                    // Insert documents directly into the real index
                    val documents =
                        listOf(
                            createSourceDocument("doc-001", "테스트 문서 1"),
                            createSourceDocument("doc-002", "테스트 문서 2"),
                            createSourceDocument("doc-003", "테스트 문서 3"),
                        )
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchTemplate.indexOps(SourceDocument::class.java).refresh()

                    // Reindex - should preserve all documents
                    val result = reindexingService.reindex()

                    result.documentCount shouldBe 3
                    result.previousIndex shouldBe "source_documents"
                    result.newIndex shouldStartWith "source_documents_v"
                }

                it("should reindex documents from old index to new index") {
                    reindexingService.reindex()

                    val documents =
                        listOf(
                            createSourceDocument("doc-001", "삼성전자 주가 상승"),
                            createSourceDocument("doc-002", "인공지능 기술 발전"),
                        )
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchTemplate.indexOps(SourceDocument::class.java).refresh()

                    termRepository.save(Term(name = "삼성전자", definition = "대한민국의 대표 기업"))

                    val result = reindexingService.reindex()

                    result.documentCount shouldBe 2
                    result.userDictionarySize shouldBe 1
                }
            }

            describe("markReindexingRequired") {
                it("should set reindexingRequired flag to true") {
                    reindexingService.reindex()

                    reindexingService.markReindexingRequired()

                    val status = reindexingStatusRepository.findById(1)
                    status.shouldNotBeNull()
                    status.reindexingRequired shouldBe true
                }

                it("should do nothing when no status exists") {
                    reindexingService.markReindexingRequired()

                    val status = reindexingStatusRepository.findById(1)
                    status.shouldBeNull()
                }
            }

            describe("isReindexingRequired") {
                it("should return true when no status exists") {
                    val result = reindexingService.isReindexingRequired()

                    result shouldBe true
                }

                it("should return false after reindexing") {
                    reindexingService.reindex()

                    val result = reindexingService.isReindexingRequired()

                    result shouldBe false
                }

                it("should return true after marking reindexing required") {
                    reindexingService.reindex()
                    reindexingService.markReindexingRequired()

                    val result = reindexingService.isReindexingRequired()

                    result shouldBe true
                }
            }

            describe("initializeIfNeeded") {
                it("should create index when no status exists") {
                    val result = reindexingService.initializeIfNeeded()

                    result.shouldNotBeNull()
                    result.newIndex shouldStartWith "source_documents_v"
                }

                it("should return null when index already exists") {
                    reindexingService.reindex()

                    val result = reindexingService.initializeIfNeeded()

                    result.shouldBeNull()
                }

                it("should create index when status exists but index does not") {
                    // Create status with non-existent index name
                    val status =
                        ReindexingStatus(
                            currentIndexName = "source_documents_v_nonexistent",
                            reindexingRequired = false,
                        )
                    reindexingStatusRepository.save(status)

                    val result = reindexingService.initializeIfNeeded()

                    result.shouldNotBeNull()
                    result.newIndex shouldStartWith "source_documents_v"
                }
            }
        }
    }

    private fun cleanupIndices() {
        try {
            elasticsearchTemplate.execute { client ->
                // First, remove alias to avoid conflicts
                try {
                    client.indices().deleteAlias {
                        it.index("source_documents*").name("source_documents")
                    }
                } catch (e: Exception) {
                    // ignore - alias might not exist
                }
                // Then delete indices
                try {
                    client.indices().delete { it.index("source_documents*") }
                } catch (e: Exception) {
                    // ignore - index might not exist
                }
                Unit
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun cleanupDatabase() {
        termRepository.findAll().forEach { termRepository.delete(it) }
        reindexingStatusRepository.deleteById(1)
    }

    private fun createSourceDocument(
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
}
