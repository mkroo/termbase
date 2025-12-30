package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.document.SlackMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.service.SourceDocumentAnalyzer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
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
class ElasticsearchSourceDocumentAnalyzerTest : DescribeSpec() {
    @Autowired
    private lateinit var sourceDocumentAnalyzer: SourceDocumentAnalyzer

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

        describe("ElasticsearchSourceDocumentAnalyzer") {
            describe("getTopFrequentTerms") {
                it("should return empty list when no documents exist") {
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
                            content = "용어1 용어2 용어3 용어4 용어5",
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
            }
        }
    }
}
