package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.slack.SlackMessage
import com.mkroo.termbase.domain.service.ConversationAnalyzer
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
class SlackConversationAnalyzerTest : DescribeSpec() {
    @Autowired
    private lateinit var conversationAnalyzer: ConversationAnalyzer

    @Autowired
    private lateinit var elasticsearchOperations: ElasticsearchOperations

    init {
        extension(SpringExtension())

        beforeEach {
            val indexOps = elasticsearchOperations.indexOps(SlackMessage::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
            indexOps.createWithMapping()
        }

        afterEach {
            val indexOps = elasticsearchOperations.indexOps(SlackMessage::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
        }

        describe("SlackConversationAnalyzer") {
            describe("getTopFrequentTerms") {
                it("should return empty list when no messages exist") {
                    val result = conversationAnalyzer.getTopFrequentTerms(10)

                    result.shouldBeEmpty()
                }

                it("should return top frequent terms") {
                    val timestamp = Instant.now()
                    val message1 =
                        SlackMessage(
                            messageId = "msg-001",
                            channelId = "channel-001",
                            workspaceId = "workspace-001",
                            content = "API 개발을 시작합니다",
                            timestamp = timestamp,
                        )
                    message1.messageId shouldBe "msg-001"
                    message1.channelId shouldBe "channel-001"
                    message1.workspaceId shouldBe "workspace-001"
                    message1.content shouldBe "API 개발을 시작합니다"
                    message1.timestamp shouldBe timestamp

                    elasticsearchOperations.save(message1)
                    elasticsearchOperations.save(
                        SlackMessage(
                            messageId = "msg-002",
                            channelId = "channel-001",
                            workspaceId = "workspace-001",
                            content = "API 문서를 작성합니다",
                            timestamp = Instant.now(),
                        ),
                    )
                    elasticsearchOperations.save(
                        SlackMessage(
                            messageId = "msg-003",
                            channelId = "channel-001",
                            workspaceId = "workspace-001",
                            content = "API 테스트를 진행합니다",
                            timestamp = Instant.now(),
                        ),
                    )

                    elasticsearchOperations.indexOps(SlackMessage::class.java).refresh()

                    val result = conversationAnalyzer.getTopFrequentTerms(10)

                    result.shouldNotBeEmpty()
                    result.shouldHaveAtLeastSize(1)
                    result.first().term shouldBe "api"
                    result.first().count shouldBe 3
                }

                it("should limit results to specified size") {
                    elasticsearchOperations.save(
                        SlackMessage(
                            messageId = "msg-001",
                            channelId = "channel-001",
                            workspaceId = "workspace-001",
                            content = "용어1 용어2 용어3 용어4 용어5",
                            timestamp = Instant.now(),
                        ),
                    )

                    elasticsearchOperations.indexOps(SlackMessage::class.java).refresh()

                    val result = conversationAnalyzer.getTopFrequentTerms(3)

                    result.shouldHaveSize(3)
                }
            }
        }
    }
}
