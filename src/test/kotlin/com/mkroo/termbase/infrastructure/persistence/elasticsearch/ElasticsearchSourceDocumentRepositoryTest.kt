package com.mkroo.termbase.infrastructure.persistence.elasticsearch

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.document.SlackMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
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
class ElasticsearchSourceDocumentRepositoryTest : DescribeSpec() {
    @Autowired
    private lateinit var repository: ElasticsearchSourceDocumentRepository

    @Autowired
    private lateinit var elasticsearchOperations: ElasticsearchOperations

    init {
        extension(SpringExtension())

        beforeEach {
            cleanupIndex()
        }

        afterEach {
            cleanupIndex()
        }

        describe("ElasticsearchSourceDocumentRepository") {
            describe("saveAll") {
                it("should save documents with id") {
                    val documents =
                        listOf(
                            createSourceDocument("doc-001", "테스트 문서 1"),
                            createSourceDocument("doc-002", "테스트 문서 2"),
                        )

                    val result = repository.saveAll(documents)

                    result.totalCount shouldBe 2
                    result.successCount shouldBe 2
                    result.failureCount shouldBe 0
                }

                it("should save documents without id") {
                    val documents =
                        listOf(
                            SourceDocument(
                                id = null,
                                content = "ID 없는 문서",
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

                    val result = repository.saveAll(documents)

                    result.totalCount shouldBe 1
                    result.successCount shouldBe 1
                    result.failureCount shouldBe 0
                }

                it("should create index if not exists") {
                    val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
                    indexOps.exists() shouldBe false

                    val documents = listOf(createSourceDocument("doc-001", "테스트"))
                    repository.saveAll(documents)

                    indexOps.exists() shouldBe true
                }
            }

            describe("findAll") {
                it("should return empty page when index does not exist") {
                    val result = repository.findAll(0, 10)

                    result.documents shouldBe emptyList()
                    result.totalElements shouldBe 0
                    result.totalPages shouldBe 0
                    result.currentPage shouldBe 0
                }

                it("should return paginated documents") {
                    val documents =
                        (1..15).map { i ->
                            createSourceDocument("doc-${i.toString().padStart(3, '0')}", "문서 $i")
                        }
                    repository.saveAll(documents)
                    elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()

                    val firstPage = repository.findAll(0, 10)

                    firstPage.documents.size shouldBe 10
                    firstPage.totalElements shouldBe 15
                    firstPage.totalPages shouldBe 2
                    firstPage.currentPage shouldBe 0

                    val secondPage = repository.findAll(1, 10)

                    secondPage.documents.size shouldBe 5
                    secondPage.currentPage shouldBe 1
                }
            }
        }
    }

    private fun cleanupIndex() {
        try {
            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
        } catch (e: Exception) {
            // ignore
        }
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
