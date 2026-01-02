package com.mkroo.termbase.domain.model.slack

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class SlackCollectionCheckpointTest :
    DescribeSpec({
        describe("SlackCollectionCheckpoint") {
            describe("생성") {
                it("채널 ID와 마지막 수집 시점으로 생성할 수 있다") {
                    val collectedAt = Instant.parse("2024-01-01T00:00:00Z")
                    val checkpoint =
                        SlackCollectionCheckpoint(
                            id = 1L,
                            channelId = "C123456",
                            lastCollectedTs = "1704067200.000000",
                            lastCollectedAt = collectedAt,
                        )

                    checkpoint.id shouldBe 1L
                    checkpoint.channelId shouldBe "C123456"
                    checkpoint.lastCollectedTs shouldBe "1704067200.000000"
                    checkpoint.lastCollectedAt shouldBe collectedAt
                }

                it("기본값으로 생성할 수 있다") {
                    val collectedAt = Instant.now()
                    val checkpoint =
                        SlackCollectionCheckpoint(
                            channelId = "C123456",
                            lastCollectedTs = "1704067200.000000",
                            lastCollectedAt = collectedAt,
                        )

                    checkpoint.id shouldBe null
                    checkpoint.channelId shouldBe "C123456"
                }
            }

            describe("체크포인트 업데이트") {
                it("마지막 수집 시점을 업데이트할 수 있다") {
                    val initialCollectedAt = Instant.parse("2024-01-01T00:00:00Z")
                    val checkpoint =
                        SlackCollectionCheckpoint(
                            channelId = "C123456",
                            lastCollectedTs = "1704067200.000000",
                            lastCollectedAt = initialCollectedAt,
                        )

                    val newCollectedAt = Instant.parse("2024-01-02T00:00:00Z")
                    checkpoint.updateCheckpoint("1704153600.000000", newCollectedAt)

                    checkpoint.lastCollectedTs shouldBe "1704153600.000000"
                    checkpoint.lastCollectedAt shouldBe newCollectedAt
                }
            }
        }
    })
