package com.mkroo.termbase.domain.model.document

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class SourceDocumentTest :
    DescribeSpec({
        describe("SourceDocument") {
            val timestamp = Instant.parse("2024-01-15T10:30:00Z")
            val slackMetadata =
                SlackMetadata(
                    workspaceId = "T123456",
                    channelId = "C789012",
                    messageId = "msg-001",
                    userId = "U456789",
                )

            it("should create document with all properties") {
                val document =
                    SourceDocument(
                        id = "doc-001",
                        content = "테스트 메시지입니다",
                        metadata = slackMetadata,
                        timestamp = timestamp,
                    )

                document.id shouldBe "doc-001"
                document.content shouldBe "테스트 메시지입니다"
                document.metadata shouldBe slackMetadata
                document.timestamp shouldBe timestamp
            }

            it("should support equality") {
                val document1 =
                    SourceDocument(
                        id = "doc-001",
                        content = "테스트 메시지입니다",
                        metadata = slackMetadata,
                        timestamp = timestamp,
                    )
                val document2 =
                    SourceDocument(
                        id = "doc-001",
                        content = "테스트 메시지입니다",
                        metadata = slackMetadata,
                        timestamp = timestamp,
                    )
                val document3 =
                    SourceDocument(
                        id = "doc-002",
                        content = "테스트 메시지입니다",
                        metadata = slackMetadata,
                        timestamp = timestamp,
                    )

                document1 shouldBe document2
                document1.hashCode() shouldBe document2.hashCode()
                document1 shouldNotBe document3
            }

            it("should support copy") {
                val document =
                    SourceDocument(
                        id = "doc-001",
                        content = "원본 메시지",
                        metadata = slackMetadata,
                        timestamp = timestamp,
                    )

                val copied = document.copy(content = "복사된 메시지")

                copied.id shouldBe "doc-001"
                copied.content shouldBe "복사된 메시지"
                copied.metadata shouldBe slackMetadata
            }

            it("should have proper toString") {
                val document =
                    SourceDocument(
                        id = "doc-001",
                        content = "테스트",
                        metadata = slackMetadata,
                        timestamp = timestamp,
                    )

                val toString = document.toString()
                toString shouldBe "SourceDocument(id=doc-001, content=테스트, metadata=$slackMetadata, timestamp=$timestamp)"
            }

            it("should work with different metadata types") {
                val gmailMetadata =
                    GmailMetadata(
                        messageId = "msg-abc",
                        threadId = "thread-123",
                        from = "test@example.com",
                        to = listOf("to@example.com"),
                        cc = emptyList(),
                        subject = "Test",
                    )

                val webhookMetadata =
                    WebhookMetadata(
                        webhookId = "hook-001",
                        eventType = "push",
                    )

                val gmailDoc =
                    SourceDocument(
                        id = "gmail-001",
                        content = "Gmail content",
                        metadata = gmailMetadata,
                        timestamp = timestamp,
                    )

                val webhookDoc =
                    SourceDocument(
                        id = "webhook-001",
                        content = "Webhook content",
                        metadata = webhookMetadata,
                        timestamp = timestamp,
                    )

                gmailDoc.metadata shouldBe gmailMetadata
                webhookDoc.metadata shouldBe webhookMetadata
            }
        }
    })
