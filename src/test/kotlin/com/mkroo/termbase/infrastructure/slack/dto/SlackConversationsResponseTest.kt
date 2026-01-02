package com.mkroo.termbase.infrastructure.slack.dto

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue

class SlackConversationsResponseTest :
    DescribeSpec({
        val objectMapper =
            JsonMapper
                .builder()
                .addModule(KotlinModule.Builder().build())
                .build()

        describe("ResponseMetadata") {
            it("기본 생성자로 생성할 수 있다") {
                val metadata = ResponseMetadata()
                metadata.nextCursor shouldBe null
            }
        }

        describe("SlackConversationsHistoryResponse") {
            it("JSON에서 역직렬화할 수 있다") {
                val json =
                    """
                    {
                        "ok": true,
                        "messages": [
                            {
                                "type": "message",
                                "user": "U123456",
                                "text": "테스트 메시지",
                                "ts": "1704067200.123456"
                            }
                        ],
                        "has_more": true,
                        "response_metadata": {
                            "next_cursor": "next_cursor_value"
                        }
                    }
                    """.trimIndent()

                val response = objectMapper.readValue<SlackConversationsHistoryResponse>(json)

                response.ok shouldBe true
                response.messages?.size shouldBe 1
                response.hasMore shouldBe true
                response.responseMetadata?.nextCursor shouldBe "next_cursor_value"
            }

            it("에러 응답을 역직렬화할 수 있다") {
                val json =
                    """
                    {
                        "ok": false,
                        "error": "channel_not_found"
                    }
                    """.trimIndent()

                val response = objectMapper.readValue<SlackConversationsHistoryResponse>(json)

                response.ok shouldBe false
                response.error shouldBe "channel_not_found"
            }
        }

        describe("SlackApiMessage") {
            describe("프로퍼티 접근") {
                it("모든 프로퍼티에 접근할 수 있다") {
                    val message =
                        SlackApiMessage(
                            type = "message",
                            user = "U123456",
                            text = "테스트",
                            ts = "1704067200.123456",
                            subtype = "message_changed",
                            botId = "B123456",
                        )

                    message.type shouldBe "message"
                    message.subtype shouldBe "message_changed"
                    message.botId shouldBe "B123456"
                }
            }

            describe("isUserMessage") {
                it("일반 사용자 메시지인 경우 true를 반환한다") {
                    val message =
                        SlackApiMessage(
                            type = "message",
                            user = "U123456",
                            text = "테스트",
                            ts = "1704067200.123456",
                        )

                    message.isUserMessage() shouldBe true
                }

                it("bot_id가 있으면 false를 반환한다") {
                    val message =
                        SlackApiMessage(
                            type = "message",
                            user = "U123456",
                            text = "테스트",
                            ts = "1704067200.123456",
                            botId = "B123456",
                        )

                    message.isUserMessage() shouldBe false
                }

                it("subtype이 있으면 false를 반환한다") {
                    val message =
                        SlackApiMessage(
                            type = "message",
                            user = "U123456",
                            text = "테스트",
                            ts = "1704067200.123456",
                            subtype = "message_changed",
                        )

                    message.isUserMessage() shouldBe false
                }

                it("user가 없으면 false를 반환한다") {
                    val message =
                        SlackApiMessage(
                            type = "message",
                            text = "테스트",
                            ts = "1704067200.123456",
                        )

                    message.isUserMessage() shouldBe false
                }

                it("text가 없으면 false를 반환한다") {
                    val message =
                        SlackApiMessage(
                            type = "message",
                            user = "U123456",
                            ts = "1704067200.123456",
                        )

                    message.isUserMessage() shouldBe false
                }

                it("type이 message가 아니면 false를 반환한다") {
                    val message =
                        SlackApiMessage(
                            type = "channel_join",
                            user = "U123456",
                            text = "테스트",
                            ts = "1704067200.123456",
                        )

                    message.isUserMessage() shouldBe false
                }
            }
        }
    })
