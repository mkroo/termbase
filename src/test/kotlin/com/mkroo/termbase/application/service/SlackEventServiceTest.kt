package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.document.SlackMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.infrastructure.slack.dto.SlackMessageEvent
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class SlackEventServiceTest :
    DescribeSpec({
        val sourceDocumentService = mockk<SourceDocumentService>(relaxed = true)
        val slackEventService = SlackEventService(sourceDocumentService)

        beforeEach {
            clearMocks(sourceDocumentService)
        }

        describe("SlackEventService") {
            describe("processMessageEvent") {
                it("일반 사용자 메시지를 처리하여 저장한다") {
                    val event =
                        SlackMessageEvent(
                            channel = "C123456",
                            user = "U789012",
                            text = "테스트 메시지",
                            ts = "1704067200.123456",
                        )
                    val workspaceId = "T000001"

                    val documentsSlot = slot<List<SourceDocument>>()
                    every { sourceDocumentService.bulkInsert(capture(documentsSlot)) } returns
                        BulkInsertResult(
                            totalCount = 1,
                            successCount = 1,
                            failureCount = 0,
                            failures = emptyList(),
                        )

                    val result = slackEventService.processMessageEvent(event, workspaceId)

                    result.successCount shouldBe 1
                    documentsSlot.captured.size shouldBe 1

                    val savedDocument = documentsSlot.captured[0]
                    savedDocument.content shouldBe "테스트 메시지"
                    val metadata = savedDocument.metadata as SlackMetadata
                    metadata.workspaceId shouldBe "T000001"
                    metadata.channelId shouldBe "C123456"
                    metadata.messageId shouldBe "1704067200.123456"
                    metadata.userId shouldBe "U789012"
                }

                it("bot 메시지는 저장하지 않는다") {
                    val event =
                        SlackMessageEvent(
                            channel = "C123456",
                            user = "U789012",
                            text = "봇 메시지",
                            ts = "1704067200.123456",
                            botId = "B123456",
                        )
                    val workspaceId = "T000001"

                    val result = slackEventService.processMessageEvent(event, workspaceId)

                    result.totalCount shouldBe 0
                    result.successCount shouldBe 0
                    verify(exactly = 0) { sourceDocumentService.bulkInsert(any()) }
                }

                it("subtype이 있는 메시지는 저장하지 않는다") {
                    val event =
                        SlackMessageEvent(
                            channel = "C123456",
                            user = "U789012",
                            text = "수정된 메시지",
                            ts = "1704067200.123456",
                            subtype = "message_changed",
                        )
                    val workspaceId = "T000001"

                    val result = slackEventService.processMessageEvent(event, workspaceId)

                    result.totalCount shouldBe 0
                    verify(exactly = 0) { sourceDocumentService.bulkInsert(any()) }
                }

                it("user가 없는 메시지는 저장하지 않는다") {
                    val event =
                        SlackMessageEvent(
                            channel = "C123456",
                            text = "사용자 없는 메시지",
                            ts = "1704067200.123456",
                        )
                    val workspaceId = "T000001"

                    val result = slackEventService.processMessageEvent(event, workspaceId)

                    result.totalCount shouldBe 0
                    verify(exactly = 0) { sourceDocumentService.bulkInsert(any()) }
                }

                it("text가 없는 메시지는 저장하지 않는다") {
                    val event =
                        SlackMessageEvent(
                            channel = "C123456",
                            user = "U789012",
                            ts = "1704067200.123456",
                        )
                    val workspaceId = "T000001"

                    val result = slackEventService.processMessageEvent(event, workspaceId)

                    result.totalCount shouldBe 0
                    verify(exactly = 0) { sourceDocumentService.bulkInsert(any()) }
                }
            }
        }
    })
