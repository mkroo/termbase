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
