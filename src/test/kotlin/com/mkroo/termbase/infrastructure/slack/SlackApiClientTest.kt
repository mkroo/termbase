package com.mkroo.termbase.infrastructure.slack

import com.mkroo.termbase.infrastructure.config.SlackProperties
import com.mkroo.termbase.infrastructure.slack.dto.ResponseMetadata
import com.mkroo.termbase.infrastructure.slack.dto.SlackApiMessage
import com.mkroo.termbase.infrastructure.slack.dto.SlackConversationsHistoryResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.web.client.RestClient

class SlackApiClientTest :
    DescribeSpec({
        val slackProperties = SlackProperties(botToken = "xoxb-test-token", signingSecret = "test-secret")
        val restClient = mockk<RestClient>()
        val requestHeadersUriSpec = mockk<RestClient.RequestHeadersUriSpec<*>>()
        val requestHeadersSpec = mockk<RestClient.RequestHeadersSpec<*>>()
        val responseSpec = mockk<RestClient.ResponseSpec>()

        val slackApiClient = SlackApiClient(slackProperties, restClient)

        beforeEach {
            clearMocks(restClient, requestHeadersUriSpec, requestHeadersSpec, responseSpec)
        }

        describe("SlackApiClient") {
            describe("fetchConversationsHistory") {
                it("채널의 메시지 히스토리를 가져온다") {
                    val expectedResponse =
                        SlackConversationsHistoryResponse(
                            ok = true,
                            messages =
                                listOf(
                                    SlackApiMessage(
                                        type = "message",
                                        user = "U123456",
                                        text = "테스트 메시지",
                                        ts = "1704067200.123456",
                                    ),
                                ),
                        )

                    every { restClient.get() } returns requestHeadersUriSpec
                    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersSpec
                    every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
                    every { requestHeadersSpec.retrieve() } returns responseSpec
                    every { responseSpec.body(SlackConversationsHistoryResponse::class.java) } returns expectedResponse

                    val response = slackApiClient.fetchConversationsHistory("C123456")

                    response.ok shouldBe true
                    response.messages!! shouldHaveSize 1
                    response.messages!![0].text shouldBe "테스트 메시지"

                    verify { requestHeadersSpec.header("Authorization", "Bearer xoxb-test-token") }
                }

                it("body가 null이면 에러 응답을 반환한다") {
                    every { restClient.get() } returns requestHeadersUriSpec
                    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersSpec
                    every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
                    every { requestHeadersSpec.retrieve() } returns responseSpec
                    every { responseSpec.body(SlackConversationsHistoryResponse::class.java) } returns null

                    val response = slackApiClient.fetchConversationsHistory("C123456")

                    response.ok shouldBe false
                    response.error shouldBe "Empty response"
                }

                it("oldest와 latest 파라미터를 포함하여 요청한다") {
                    val expectedResponse =
                        SlackConversationsHistoryResponse(
                            ok = true,
                            messages = emptyList(),
                        )

                    every { restClient.get() } returns requestHeadersUriSpec
                    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersSpec
                    every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
                    every { requestHeadersSpec.retrieve() } returns responseSpec
                    every { responseSpec.body(SlackConversationsHistoryResponse::class.java) } returns expectedResponse

                    slackApiClient.fetchConversationsHistory(
                        channelId = "C123456",
                        oldest = "1704067200.000000",
                        latest = "1704153600.000000",
                    )

                    verify {
                        requestHeadersUriSpec.uri(match<String> {
                            it.contains("oldest=1704067200.000000") && it.contains("latest=1704153600.000000")
                        })
                    }
                }
            }

            describe("fetchAllMessages") {
                it("모든 사용자 메시지를 가져온다") {
                    val response =
                        SlackConversationsHistoryResponse(
                            ok = true,
                            messages =
                                listOf(
                                    SlackApiMessage(
                                        type = "message",
                                        user = "U123456",
                                        text = "사용자 메시지",
                                        ts = "1704067200.123456",
                                    ),
                                    SlackApiMessage(
                                        type = "message",
                                        user = "U123456",
                                        text = "봇 메시지",
                                        ts = "1704067201.123456",
                                        botId = "B123456",
                                    ),
                                ),
                            hasMore = false,
                        )

                    every { restClient.get() } returns requestHeadersUriSpec
                    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersSpec
                    every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
                    every { requestHeadersSpec.retrieve() } returns responseSpec
                    every { responseSpec.body(SlackConversationsHistoryResponse::class.java) } returns response

                    val messages = slackApiClient.fetchAllMessages("C123456", "T000001")

                    messages shouldHaveSize 1
                    messages[0].text shouldBe "사용자 메시지"
                    messages[0].workspaceId shouldBe "T000001"
                    messages[0].channelId shouldBe "C123456"
                }

                it("페이지네이션을 처리한다") {
                    val firstResponse =
                        SlackConversationsHistoryResponse(
                            ok = true,
                            messages =
                                listOf(
                                    SlackApiMessage(
                                        type = "message",
                                        user = "U123456",
                                        text = "첫 번째 메시지",
                                        ts = "1704067200.123456",
                                    ),
                                ),
                            hasMore = true,
                            responseMetadata = ResponseMetadata(nextCursor = "next_cursor"),
                        )

                    val secondResponse =
                        SlackConversationsHistoryResponse(
                            ok = true,
                            messages =
                                listOf(
                                    SlackApiMessage(
                                        type = "message",
                                        user = "U789012",
                                        text = "두 번째 메시지",
                                        ts = "1704067201.123456",
                                    ),
                                ),
                            hasMore = false,
                        )

                    every { restClient.get() } returns requestHeadersUriSpec
                    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersSpec
                    every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
                    every { requestHeadersSpec.retrieve() } returns responseSpec
                    every {
                        responseSpec.body(SlackConversationsHistoryResponse::class.java)
                    } returns firstResponse andThen secondResponse

                    val messages = slackApiClient.fetchAllMessages("C123456", "T000001")

                    messages shouldHaveSize 2
                    messages[0].text shouldBe "첫 번째 메시지"
                    messages[1].text shouldBe "두 번째 메시지"
                }

                it("API 오류 시 예외를 던진다") {
                    val errorResponse =
                        SlackConversationsHistoryResponse(
                            ok = false,
                            error = "channel_not_found",
                        )

                    every { restClient.get() } returns requestHeadersUriSpec
                    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersSpec
                    every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
                    every { requestHeadersSpec.retrieve() } returns responseSpec
                    every { responseSpec.body(SlackConversationsHistoryResponse::class.java) } returns errorResponse

                    val exception =
                        shouldThrow<SlackApiException> {
                            slackApiClient.fetchAllMessages("C123456", "T000001")
                        }

                    exception.message shouldBe "Slack API error: channel_not_found"
                }

                it("messages가 null이면 빈 리스트를 반환한다") {
                    val response =
                        SlackConversationsHistoryResponse(
                            ok = true,
                            messages = null,
                            hasMore = false,
                        )

                    every { restClient.get() } returns requestHeadersUriSpec
                    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersSpec
                    every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
                    every { requestHeadersSpec.retrieve() } returns responseSpec
                    every { responseSpec.body(SlackConversationsHistoryResponse::class.java) } returns response

                    val messages = slackApiClient.fetchAllMessages("C123456", "T000001")

                    messages shouldHaveSize 0
                }

                it("nextCursor가 빈 문자열이면 페이지네이션을 중단한다") {
                    val response =
                        SlackConversationsHistoryResponse(
                            ok = true,
                            messages =
                                listOf(
                                    SlackApiMessage(
                                        type = "message",
                                        user = "U123456",
                                        text = "메시지",
                                        ts = "1704067200.123456",
                                    ),
                                ),
                            hasMore = true,
                            responseMetadata = ResponseMetadata(nextCursor = ""),
                        )

                    every { restClient.get() } returns requestHeadersUriSpec
                    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersSpec
                    every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
                    every { requestHeadersSpec.retrieve() } returns responseSpec
                    every { responseSpec.body(SlackConversationsHistoryResponse::class.java) } returns response

                    val messages = slackApiClient.fetchAllMessages("C123456", "T000001")

                    messages shouldHaveSize 1
                }

                it("oldest와 latest 파라미터를 전달한다") {
                    val response =
                        SlackConversationsHistoryResponse(
                            ok = true,
                            messages =
                                listOf(
                                    SlackApiMessage(
                                        type = "message",
                                        user = "U123456",
                                        text = "메시지",
                                        ts = "1704067200.123456",
                                    ),
                                ),
                            hasMore = false,
                        )

                    every { restClient.get() } returns requestHeadersUriSpec
                    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersSpec
                    every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
                    every { requestHeadersSpec.retrieve() } returns responseSpec
                    every { responseSpec.body(SlackConversationsHistoryResponse::class.java) } returns response

                    slackApiClient.fetchAllMessages(
                        channelId = "C123456",
                        workspaceId = "T000001",
                        oldest = "1704067200.000000",
                        latest = "1704153600.000000",
                    )

                    verify {
                        requestHeadersUriSpec.uri(match<String> {
                            it.contains("oldest=1704067200.000000") && it.contains("latest=1704153600.000000")
                        })
                    }
                }
            }
        }
    })
