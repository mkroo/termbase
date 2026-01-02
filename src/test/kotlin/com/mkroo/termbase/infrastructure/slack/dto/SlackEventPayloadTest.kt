package com.mkroo.termbase.infrastructure.slack.dto

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue

class SlackEventPayloadTest :
    DescribeSpec({
        val objectMapper =
            JsonMapper
                .builder()
                .addModule(KotlinModule.Builder().build())
                .build()

        describe("SlackUrlVerification") {
            it("JSON에서 역직렬화할 수 있다") {
                val json =
                    """
                    {
                        "type": "url_verification",
                        "token": "test-token",
                        "challenge": "test-challenge"
                    }
                    """.trimIndent()

                val payload = objectMapper.readValue<SlackUrlVerification>(json)

                payload.type shouldBe "url_verification"
                payload.token shouldBe "test-token"
                payload.challenge shouldBe "test-challenge"
            }
        }

        describe("SlackEventCallback") {
            it("message 이벤트를 역직렬화할 수 있다") {
                val json =
                    """
                    {
                        "type": "event_callback",
                        "token": "test-token",
                        "team_id": "T123456",
                        "event": {
                            "type": "message",
                            "channel": "C789012",
                            "user": "U456789",
                            "text": "안녕하세요",
                            "ts": "1704067200.000000",
                            "team": "T123456"
                        }
                    }
                    """.trimIndent()

                val payload = objectMapper.readValue<SlackEventCallback>(json)

                payload.type shouldBe "event_callback"
                payload.token shouldBe "test-token"
                payload.teamId shouldBe "T123456"
                val messageEvent = payload.event.shouldBeInstanceOf<SlackMessageEvent>()
                messageEvent.type shouldBe "message"
                messageEvent.channel shouldBe "C789012"
                messageEvent.user shouldBe "U456789"
                messageEvent.text shouldBe "안녕하세요"
                messageEvent.ts shouldBe "1704067200.000000"
                messageEvent.teamId shouldBe "T123456"
            }
        }

        describe("SlackMessageEvent") {
            describe("프로퍼티 접근") {
                it("모든 프로퍼티에 접근할 수 있다") {
                    val event =
                        SlackMessageEvent(
                            channel = "C123456",
                            user = "U789012",
                            text = "테스트 메시지",
                            ts = "1704067200.000000",
                            subtype = "message_changed",
                            botId = "B123456",
                        )

                    event.subtype shouldBe "message_changed"
                    event.botId shouldBe "B123456"
                }
            }

            describe("isUserMessage") {
                it("일반 사용자 메시지인 경우 true를 반환한다") {
                    val event =
                        SlackMessageEvent(
                            channel = "C123456",
                            user = "U789012",
                            text = "테스트 메시지",
                            ts = "1704067200.000000",
                        )

                    event.isUserMessage() shouldBe true
                }

                it("subtype이 있는 경우 false를 반환한다") {
                    val event =
                        SlackMessageEvent(
                            channel = "C123456",
                            user = "U789012",
                            text = "테스트 메시지",
                            ts = "1704067200.000000",
                            subtype = "message_changed",
                        )

                    event.isUserMessage() shouldBe false
                }

                it("bot_id가 있는 경우 false를 반환한다") {
                    val event =
                        SlackMessageEvent(
                            channel = "C123456",
                            user = "U789012",
                            text = "테스트 메시지",
                            ts = "1704067200.000000",
                            botId = "B123456",
                        )

                    event.isUserMessage() shouldBe false
                }

                it("user가 없는 경우 false를 반환한다") {
                    val event =
                        SlackMessageEvent(
                            channel = "C123456",
                            text = "테스트 메시지",
                            ts = "1704067200.000000",
                        )

                    event.isUserMessage() shouldBe false
                }

                it("text가 없는 경우 false를 반환한다") {
                    val event =
                        SlackMessageEvent(
                            channel = "C123456",
                            user = "U789012",
                            ts = "1704067200.000000",
                        )

                    event.isUserMessage() shouldBe false
                }
            }
        }
    })
