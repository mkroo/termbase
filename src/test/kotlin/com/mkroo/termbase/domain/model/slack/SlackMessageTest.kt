package com.mkroo.termbase.domain.model.slack

import com.mkroo.termbase.domain.model.document.SlackMetadata
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

class SlackMessageTest :
    DescribeSpec({
        describe("SlackMessage") {
            describe("생성") {
                it("Slack 메시지 정보로 생성할 수 있다") {
                    val timestamp = Instant.parse("2024-01-01T00:00:00Z")
                    val message =
                        SlackMessage(
                            workspaceId = "T123456",
                            channelId = "C789012",
                            messageTs = "1704067200.000000",
                            userId = "U456789",
                            text = "안녕하세요",
                            timestamp = timestamp,
                        )

                    message.workspaceId shouldBe "T123456"
                    message.channelId shouldBe "C789012"
                    message.messageTs shouldBe "1704067200.000000"
                    message.userId shouldBe "U456789"
                    message.text shouldBe "안녕하세요"
                    message.timestamp shouldBe timestamp
                }
            }

            describe("toSourceDocument") {
                it("SourceDocument로 변환할 수 있다") {
                    val timestamp = Instant.parse("2024-01-01T00:00:00Z")
                    val message =
                        SlackMessage(
                            workspaceId = "T123456",
                            channelId = "C789012",
                            messageTs = "1704067200.000000",
                            userId = "U456789",
                            text = "테스트 메시지입니다",
                            timestamp = timestamp,
                        )

                    val sourceDocument = message.toSourceDocument()

                    sourceDocument.id shouldBe null
                    sourceDocument.content shouldBe "테스트 메시지입니다"
                    sourceDocument.timestamp shouldBe timestamp
                    val metadata = sourceDocument.metadata.shouldBeInstanceOf<SlackMetadata>()
                    metadata.source shouldBe "slack"
                    metadata.workspaceId shouldBe "T123456"
                    metadata.channelId shouldBe "C789012"
                    metadata.messageId shouldBe "1704067200.000000"
                    metadata.userId shouldBe "U456789"
                }
            }

            describe("fromSlackTs") {
                it("Slack timestamp를 Instant로 변환할 수 있다") {
                    val instant = SlackMessage.fromSlackTs("1704067200.123456")

                    instant.epochSecond shouldBe 1704067200L
                    instant.nano shouldBe 123456000
                }

                it("마이크로초가 6자리 미만인 경우에도 변환할 수 있다") {
                    val instant = SlackMessage.fromSlackTs("1704067200.123")

                    instant.epochSecond shouldBe 1704067200L
                    instant.nano shouldBe 123000000
                }

                it("마이크로초가 없는 경우에도 변환할 수 있다") {
                    val instant = SlackMessage.fromSlackTs("1704067200.000000")

                    instant.epochSecond shouldBe 1704067200L
                    instant.nano shouldBe 0
                }
            }
        }
    })
