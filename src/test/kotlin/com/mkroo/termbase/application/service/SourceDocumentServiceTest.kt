package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.document.SlackMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
class SourceDocumentServiceTest : DescribeSpec() {
    @Autowired
    private lateinit var sourceDocumentService: SourceDocumentService

    @Autowired
    private lateinit var elasticsearchOperations: ElasticsearchOperations

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

        describe("SourceDocumentService") {
            describe("bulkInsert") {
                it("should return empty result when documents list is empty") {
                    val result = sourceDocumentService.bulkInsert(emptyList())

                    result.totalCount shouldBe 0
                    result.successCount shouldBe 0
                    result.failureCount shouldBe 0
                    result.failures.shouldBeEmpty()
                }

                it("should save single document and return success result") {
                    val documents = listOf(createSourceDocument("doc-001", "테스트 내용"))

                    val result = sourceDocumentService.bulkInsert(documents)

                    result.totalCount shouldBe 1
                    result.successCount shouldBe 1
                    result.failureCount shouldBe 0
                    result.failures.shouldBeEmpty()

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()
                    val saved = elasticsearchOperations.get("doc-001", SourceDocument::class.java)
                    saved?.content shouldBe "테스트 내용"
                }

                it("should save multiple documents and return success result") {
                    val documents =
                        listOf(
                            createSourceDocument("doc-001", "첫 번째 문서"),
                            createSourceDocument("doc-002", "두 번째 문서"),
                            createSourceDocument("doc-003", "세 번째 문서"),
                        )

                    val result = sourceDocumentService.bulkInsert(documents)

                    result.totalCount shouldBe 3
                    result.successCount shouldBe 3
                    result.failureCount shouldBe 0
                    result.failures.shouldBeEmpty()

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val saved1 = elasticsearchOperations.get("doc-001", SourceDocument::class.java)
                    val saved2 = elasticsearchOperations.get("doc-002", SourceDocument::class.java)
                    val saved3 = elasticsearchOperations.get("doc-003", SourceDocument::class.java)

                    saved1?.content shouldBe "첫 번째 문서"
                    saved2?.content shouldBe "두 번째 문서"
                    saved3?.content shouldBe "세 번째 문서"
                }

                it("should save document without id and generate id automatically") {
                    val documents = listOf(createSourceDocument(id = null, content = "ID 없는 문서"))

                    val result = sourceDocumentService.bulkInsert(documents)

                    result.totalCount shouldBe 1
                    result.successCount shouldBe 1
                    result.failureCount shouldBe 0
                }

                it("should update existing document when id already exists") {
                    val originalDocument = createSourceDocument("doc-001", "원본 내용")
                    sourceDocumentService.bulkInsert(listOf(originalDocument))
                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val updatedDocument = createSourceDocument("doc-001", "수정된 내용")
                    val result = sourceDocumentService.bulkInsert(listOf(updatedDocument))

                    result.totalCount shouldBe 1
                    result.successCount shouldBe 1
                    result.failureCount shouldBe 0

                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()
                    val saved = elasticsearchOperations.get("doc-001", SourceDocument::class.java)
                    saved?.content shouldBe "수정된 내용"
                }
            }

            describe("getDocuments") {
                it("should return empty page when no documents exist") {
                    val result = sourceDocumentService.getDocuments()

                    result.documents.shouldBeEmpty()
                    result.totalElements shouldBe 0
                    result.totalPages shouldBe 0
                    result.currentPage shouldBe 0
                    result.size shouldBe 20
                    result.hasNext shouldBe false
                    result.hasPrevious shouldBe false
                }

                it("should return documents sorted by timestamp descending") {
                    val documents =
                        listOf(
                            createSourceDocumentWithTimestamp("doc-001", "첫 번째", Instant.parse("2024-01-01T00:00:00Z")),
                            createSourceDocumentWithTimestamp("doc-002", "두 번째", Instant.parse("2024-01-02T00:00:00Z")),
                            createSourceDocumentWithTimestamp("doc-003", "세 번째", Instant.parse("2024-01-03T00:00:00Z")),
                        )
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentService.getDocuments()

                    result.documents.size shouldBe 3
                    result.documents[0].id shouldBe "doc-003"
                    result.documents[1].id shouldBe "doc-002"
                    result.documents[2].id shouldBe "doc-001"
                    result.totalElements shouldBe 3
                }

                it("should return paginated results") {
                    val documents =
                        (1..25).map { i ->
                            createSourceDocumentWithTimestamp(
                                "doc-${i.toString().padStart(3, '0')}",
                                "문서 $i",
                                Instant.parse("2024-01-${i.toString().padStart(2, '0')}T00:00:00Z"),
                            )
                        }
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val page0 = sourceDocumentService.getDocuments(page = 0, size = 10)
                    page0.documents.size shouldBe 10
                    page0.totalElements shouldBe 25
                    page0.totalPages shouldBe 3
                    page0.currentPage shouldBe 0
                    page0.hasNext shouldBe true
                    page0.hasPrevious shouldBe false

                    val page1 = sourceDocumentService.getDocuments(page = 1, size = 10)
                    page1.documents.size shouldBe 10
                    page1.currentPage shouldBe 1
                    page1.hasNext shouldBe true
                    page1.hasPrevious shouldBe true

                    val page2 = sourceDocumentService.getDocuments(page = 2, size = 10)
                    page2.documents.size shouldBe 5
                    page2.currentPage shouldBe 2
                    page2.hasNext shouldBe false
                    page2.hasPrevious shouldBe true
                }

                it("should coerce negative page to 0") {
                    val documents = listOf(createSourceDocument("doc-001", "테스트"))
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val result = sourceDocumentService.getDocuments(page = -5, size = 10)

                    result.currentPage shouldBe 0
                }

                it("should coerce size to valid range") {
                    val documents = listOf(createSourceDocument("doc-001", "테스트"))
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val tooSmall = sourceDocumentService.getDocuments(page = 0, size = 0)
                    tooSmall.size shouldBe 1

                    val tooLarge = sourceDocumentService.getDocuments(page = 0, size = 500)
                    tooLarge.size shouldBe 100
                }
            }
        }
    }

    private fun createSourceDocument(
        id: String?,
        content: String,
    ): SourceDocument =
        SourceDocument(
            id = id,
            content = content,
            metadata =
                SlackMetadata(
                    workspaceId = "T123456",
                    channelId = "C789012",
                    messageId = "msg-${id ?: "new"}",
                    userId = "U456789",
                ),
            timestamp = Instant.now(),
        )

    private fun createSourceDocumentWithTimestamp(
        id: String,
        content: String,
        timestamp: Instant,
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
            timestamp = timestamp,
        )
}
