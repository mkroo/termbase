package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.slack.SlackCollectionCheckpoint
import com.mkroo.termbase.domain.model.slack.SlackMessage
import com.mkroo.termbase.domain.repository.SlackCollectionCheckpointRepository
import com.mkroo.termbase.infrastructure.slack.SlackApiClient
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SlackConversationsBatchServiceTest :
    DescribeSpec({
        val slackApiClient = mockk<SlackApiClient>()
        val sourceDocumentService = mockk<SourceDocumentService>()
        val checkpointRepository = mockk<SlackCollectionCheckpointRepository>()
        val fixedInstant = Instant.parse("2024-01-02T12:00:00Z")
        val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

        val service =
            SlackConversationsBatchService(
                slackApiClient,
                sourceDocumentService,
                checkpointRepository,
                clock,
            )

        beforeEach {
            clearMocks(slackApiClient, sourceDocumentService, checkpointRepository)
        }

        describe("SlackConversationsBatchService") {
            describe("collectMessages") {
                it("메시지를 수집하여 저장한다") {
                    val messages =
                        listOf(
                            SlackMessage(
                                workspaceId = "T000001",
                                channelId = "C123456",
                                messageTs = "1704067200.123456",
                                userId = "U123456",
                                text = "테스트 메시지",
                                timestamp = Instant.parse("2024-01-01T00:00:00Z"),
                            ),
                        )

                    every {
                        slackApiClient.fetchAllMessages("C123456", "T000001", null, null)
                    } returns messages

                    val documentSlot = slot<List<SourceDocument>>()
                    every { sourceDocumentService.bulkInsert(capture(documentSlot)) } returns
                        BulkInsertResult(1, 1, 0, emptyList())

                    val result = service.collectMessages("T000001", "C123456")

                    result.successCount shouldBe 1
                    documentSlot.captured.size shouldBe 1
                    documentSlot.captured[0].content shouldBe "테스트 메시지"
                }

                it("oldest와 latest 파라미터를 전달한다") {
                    every {
                        slackApiClient.fetchAllMessages("C123456", "T000001", "1704067200.000000", "1704153600.000000")
                    } returns emptyList()

                    val result =
                        service.collectMessages(
                            workspaceId = "T000001",
                            channelId = "C123456",
                            oldest = "1704067200.000000",
                            latest = "1704153600.000000",
                        )

                    result.successCount shouldBe 0
                    verify { slackApiClient.fetchAllMessages("C123456", "T000001", "1704067200.000000", "1704153600.000000") }
                }

                it("메시지가 없으면 빈 결과를 반환한다") {
                    every { slackApiClient.fetchAllMessages("C123456", "T000001", null, null) } returns emptyList()

                    val result = service.collectMessages("T000001", "C123456")

                    result.successCount shouldBe 0
                    result.failureCount shouldBe 0
                    verify(exactly = 0) { sourceDocumentService.bulkInsert(any()) }
                }
            }

            describe("collectIncrementalMessages") {
                it("체크포인트가 없으면 전체 메시지를 수집하고 새 체크포인트를 생성한다") {
                    val messages =
                        listOf(
                            SlackMessage(
                                workspaceId = "T000001",
                                channelId = "C123456",
                                messageTs = "1704153600.123456",
                                userId = "U123456",
                                text = "최신 메시지",
                                timestamp = Instant.parse("2024-01-02T00:00:00Z"),
                            ),
                        )

                    every { checkpointRepository.findByChannelId("C123456") } returns null
                    every { slackApiClient.fetchAllMessages("C123456", "T000001", null) } returns messages
                    every { sourceDocumentService.bulkInsert(any()) } returns BulkInsertResult(1, 1, 0, emptyList())

                    val checkpointSlot = slot<SlackCollectionCheckpoint>()
                    every { checkpointRepository.save(capture(checkpointSlot)) } answers { checkpointSlot.captured }

                    val result = service.collectIncrementalMessages("T000001", "C123456")

                    result.successCount shouldBe 1
                    checkpointSlot.captured.channelId shouldBe "C123456"
                    checkpointSlot.captured.lastCollectedTs shouldBe "1704153600.123456"
                    checkpointSlot.captured.lastCollectedAt shouldBe fixedInstant
                }

                it("체크포인트가 있으면 마지막 수집 이후 메시지만 수집하고 체크포인트를 업데이트한다") {
                    val existingCheckpoint =
                        SlackCollectionCheckpoint(
                            id = 1L,
                            channelId = "C123456",
                            lastCollectedTs = "1704067200.000000",
                            lastCollectedAt = Instant.parse("2024-01-01T00:00:00Z"),
                        )

                    val messages =
                        listOf(
                            SlackMessage(
                                workspaceId = "T000001",
                                channelId = "C123456",
                                messageTs = "1704153600.123456",
                                userId = "U789012",
                                text = "새로운 메시지",
                                timestamp = Instant.parse("2024-01-02T00:00:00Z"),
                            ),
                        )

                    every { checkpointRepository.findByChannelId("C123456") } returns existingCheckpoint
                    every { slackApiClient.fetchAllMessages("C123456", "T000001", "1704067200.000000") } returns messages
                    every { sourceDocumentService.bulkInsert(any()) } returns BulkInsertResult(1, 1, 0, emptyList())
                    every { checkpointRepository.save(any()) } answers { firstArg() }

                    val result = service.collectIncrementalMessages("T000001", "C123456")

                    result.successCount shouldBe 1
                    verify { slackApiClient.fetchAllMessages("C123456", "T000001", "1704067200.000000") }
                    existingCheckpoint.lastCollectedTs shouldBe "1704153600.123456"
                    existingCheckpoint.lastCollectedAt shouldBe fixedInstant
                }

                it("메시지가 없으면 체크포인트를 업데이트하지 않는다") {
                    val existingCheckpoint =
                        SlackCollectionCheckpoint(
                            id = 1L,
                            channelId = "C123456",
                            lastCollectedTs = "1704067200.000000",
                            lastCollectedAt = Instant.parse("2024-01-01T00:00:00Z"),
                        )

                    every { checkpointRepository.findByChannelId("C123456") } returns existingCheckpoint
                    every { slackApiClient.fetchAllMessages("C123456", "T000001", "1704067200.000000") } returns emptyList()

                    val result = service.collectIncrementalMessages("T000001", "C123456")

                    result.successCount shouldBe 0
                    verify(exactly = 0) { checkpointRepository.save(any()) }
                    existingCheckpoint.lastCollectedTs shouldBe "1704067200.000000"
                }

                it("여러 메시지 중 가장 최신 타임스탬프를 체크포인트에 저장한다") {
                    val messages =
                        listOf(
                            SlackMessage(
                                workspaceId = "T000001",
                                channelId = "C123456",
                                messageTs = "1704067200.123456",
                                userId = "U123456",
                                text = "첫 번째 메시지",
                                timestamp = Instant.parse("2024-01-01T00:00:00Z"),
                            ),
                            SlackMessage(
                                workspaceId = "T000001",
                                channelId = "C123456",
                                messageTs = "1704153600.123456",
                                userId = "U789012",
                                text = "두 번째 메시지",
                                timestamp = Instant.parse("2024-01-02T00:00:00Z"),
                            ),
                        )

                    every { checkpointRepository.findByChannelId("C123456") } returns null
                    every { slackApiClient.fetchAllMessages("C123456", "T000001", null) } returns messages
                    every { sourceDocumentService.bulkInsert(any()) } returns BulkInsertResult(2, 2, 0, emptyList())

                    val checkpointSlot = slot<SlackCollectionCheckpoint>()
                    every { checkpointRepository.save(capture(checkpointSlot)) } answers { checkpointSlot.captured }

                    service.collectIncrementalMessages("T000001", "C123456")

                    checkpointSlot.captured.lastCollectedTs shouldBe "1704153600.123456"
                }
            }
        }
    })
