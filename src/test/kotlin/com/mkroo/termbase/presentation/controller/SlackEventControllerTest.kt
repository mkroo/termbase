package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.infrastructure.config.SlackProperties
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SlackEventControllerTest : DescribeSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var slackProperties: SlackProperties

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

        describe("SlackEventController") {
            describe("POST /api/slack/events") {
                it("URL 검증 챌린지에 응답한다") {
                    val body =
                        """
                        {
                            "type": "url_verification",
                            "token": "test-token",
                            "challenge": "test-challenge-string"
                        }
                        """.trimIndent()

                    val timestamp = Instant.now().epochSecond.toString()
                    val signature = generateSignature(slackProperties.signingSecret, timestamp, body)

                    mockMvc
                        .perform(
                            post("/api/slack/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Slack-Request-Timestamp", timestamp)
                                .header("X-Slack-Signature", signature)
                                .content(body),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.challenge").value("test-challenge-string"))
                }

                it("메시지 이벤트를 처리한다") {
                    val body =
                        """
                        {
                            "type": "event_callback",
                            "token": "test-token",
                            "team_id": "T123456",
                            "event": {
                                "type": "message",
                                "channel": "C789012",
                                "user": "U456789",
                                "text": "테스트 메시지입니다",
                                "ts": "1704067200.123456",
                                "team": "T123456"
                            }
                        }
                        """.trimIndent()

                    val timestamp = Instant.now().epochSecond.toString()
                    val signature = generateSignature(slackProperties.signingSecret, timestamp, body)

                    mockMvc
                        .perform(
                            post("/api/slack/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Slack-Request-Timestamp", timestamp)
                                .header("X-Slack-Signature", signature)
                                .content(body),
                        ).andExpect(status().isOk)
                }

                it("잘못된 서명은 401을 반환한다") {
                    val body =
                        """
                        {
                            "type": "url_verification",
                            "token": "test-token",
                            "challenge": "test-challenge"
                        }
                        """.trimIndent()

                    val timestamp = Instant.now().epochSecond.toString()

                    mockMvc
                        .perform(
                            post("/api/slack/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Slack-Request-Timestamp", timestamp)
                                .header("X-Slack-Signature", "v0=invalid_signature")
                                .content(body),
                        ).andExpect(status().isUnauthorized)
                }

                it("오래된 타임스탬프는 401을 반환한다") {
                    val body =
                        """
                        {
                            "type": "url_verification",
                            "token": "test-token",
                            "challenge": "test-challenge"
                        }
                        """.trimIndent()

                    val oldTimestamp = (Instant.now().epochSecond - 400).toString()
                    val signature = generateSignature(slackProperties.signingSecret, oldTimestamp, body)

                    mockMvc
                        .perform(
                            post("/api/slack/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Slack-Request-Timestamp", oldTimestamp)
                                .header("X-Slack-Signature", signature)
                                .content(body),
                        ).andExpect(status().isUnauthorized)
                }

                it("Bot 메시지는 저장하지 않고 200을 반환한다") {
                    val body =
                        """
                        {
                            "type": "event_callback",
                            "token": "test-token",
                            "team_id": "T123456",
                            "event": {
                                "type": "message",
                                "channel": "C789012",
                                "user": "U456789",
                                "text": "봇 메시지",
                                "ts": "1704067200.123456",
                                "bot_id": "B123456"
                            }
                        }
                        """.trimIndent()

                    val timestamp = Instant.now().epochSecond.toString()
                    val signature = generateSignature(slackProperties.signingSecret, timestamp, body)

                    mockMvc
                        .perform(
                            post("/api/slack/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Slack-Request-Timestamp", timestamp)
                                .header("X-Slack-Signature", signature)
                                .content(body),
                        ).andExpect(status().isOk)
                }
            }
        }
    }

    private fun generateSignature(
        secret: String,
        timestamp: String,
        body: String,
    ): String {
        val baseString = "v0:$timestamp:$body"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(baseString.toByteArray()).joinToString("") { "%02x".format(it) }
        return "v0=$hash"
    }
}
