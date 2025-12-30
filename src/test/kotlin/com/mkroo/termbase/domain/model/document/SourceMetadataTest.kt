package com.mkroo.termbase.domain.model.document

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SourceMetadataTest :
    DescribeSpec({
        describe("SlackMetadata") {
            it("should have correct default source value") {
                val metadata =
                    SlackMetadata(
                        workspaceId = "T123456",
                        channelId = "C789012",
                        messageId = "msg-001",
                        userId = "U456789",
                    )

                metadata.source shouldBe "slack"
                metadata.workspaceId shouldBe "T123456"
                metadata.channelId shouldBe "C789012"
                metadata.messageId shouldBe "msg-001"
                metadata.userId shouldBe "U456789"
            }

            it("should support equality") {
                val metadata1 =
                    SlackMetadata(
                        workspaceId = "T123456",
                        channelId = "C789012",
                        messageId = "msg-001",
                        userId = "U456789",
                    )
                val metadata2 =
                    SlackMetadata(
                        workspaceId = "T123456",
                        channelId = "C789012",
                        messageId = "msg-001",
                        userId = "U456789",
                    )
                val metadata3 =
                    SlackMetadata(
                        workspaceId = "T999999",
                        channelId = "C789012",
                        messageId = "msg-001",
                        userId = "U456789",
                    )

                metadata1 shouldBe metadata2
                metadata1.hashCode() shouldBe metadata2.hashCode()
                metadata1 shouldNotBe metadata3
            }

            it("should support copy") {
                val metadata =
                    SlackMetadata(
                        workspaceId = "T123456",
                        channelId = "C789012",
                        messageId = "msg-001",
                        userId = "U456789",
                    )

                val copied = metadata.copy(workspaceId = "T999999")

                copied.workspaceId shouldBe "T999999"
                copied.channelId shouldBe "C789012"
            }

            it("should have proper toString") {
                val metadata =
                    SlackMetadata(
                        workspaceId = "T123456",
                        channelId = "C789012",
                        messageId = "msg-001",
                        userId = "U456789",
                    )

                metadata.toString() shouldBe
                    "SlackMetadata(source=slack, workspaceId=T123456, channelId=C789012, messageId=msg-001, userId=U456789)"
            }
        }

        describe("GmailMetadata") {
            it("should have correct default source value") {
                val metadata =
                    GmailMetadata(
                        messageId = "msg-abc123",
                        threadId = "thread-xyz789",
                        from = "sender@example.com",
                        to = listOf("receiver@example.com"),
                        cc = listOf("cc@example.com"),
                        subject = "Test Subject",
                    )

                metadata.source shouldBe "gmail"
                metadata.messageId shouldBe "msg-abc123"
                metadata.threadId shouldBe "thread-xyz789"
                metadata.from shouldBe "sender@example.com"
                metadata.to shouldBe listOf("receiver@example.com")
                metadata.cc shouldBe listOf("cc@example.com")
                metadata.subject shouldBe "Test Subject"
            }

            it("should support equality") {
                val metadata1 =
                    GmailMetadata(
                        messageId = "msg-abc123",
                        threadId = "thread-xyz789",
                        from = "sender@example.com",
                        to = listOf("receiver@example.com"),
                        cc = listOf("cc@example.com"),
                        subject = "Test Subject",
                    )
                val metadata2 =
                    GmailMetadata(
                        messageId = "msg-abc123",
                        threadId = "thread-xyz789",
                        from = "sender@example.com",
                        to = listOf("receiver@example.com"),
                        cc = listOf("cc@example.com"),
                        subject = "Test Subject",
                    )
                val metadata3 =
                    GmailMetadata(
                        messageId = "msg-different",
                        threadId = "thread-xyz789",
                        from = "sender@example.com",
                        to = listOf("receiver@example.com"),
                        cc = listOf("cc@example.com"),
                        subject = "Test Subject",
                    )

                metadata1 shouldBe metadata2
                metadata1.hashCode() shouldBe metadata2.hashCode()
                metadata1 shouldNotBe metadata3
            }

            it("should support copy") {
                val metadata =
                    GmailMetadata(
                        messageId = "msg-abc123",
                        threadId = "thread-xyz789",
                        from = "sender@example.com",
                        to = listOf("receiver@example.com"),
                        cc = listOf("cc@example.com"),
                        subject = "Test Subject",
                    )

                val copied = metadata.copy(subject = "New Subject")

                copied.subject shouldBe "New Subject"
                copied.from shouldBe "sender@example.com"
            }

            it("should have proper toString") {
                val metadata =
                    GmailMetadata(
                        messageId = "msg-abc123",
                        threadId = "thread-xyz789",
                        from = "sender@example.com",
                        to = listOf("receiver@example.com"),
                        cc = listOf("cc@example.com"),
                        subject = "Test Subject",
                    )

                metadata.toString() shouldBe
                    "GmailMetadata(source=gmail, messageId=msg-abc123, threadId=thread-xyz789, from=sender@example.com, to=[receiver@example.com], cc=[cc@example.com], subject=Test Subject)"
            }
        }

        describe("WebhookMetadata") {
            it("should have correct default source value") {
                val metadata =
                    WebhookMetadata(
                        webhookId = "hook-123",
                        eventType = "deployment",
                    )

                metadata.source shouldBe "webhook"
                metadata.webhookId shouldBe "hook-123"
                metadata.eventType shouldBe "deployment"
            }

            it("should support equality") {
                val metadata1 =
                    WebhookMetadata(
                        webhookId = "hook-123",
                        eventType = "deployment",
                    )
                val metadata2 =
                    WebhookMetadata(
                        webhookId = "hook-123",
                        eventType = "deployment",
                    )
                val metadata3 =
                    WebhookMetadata(
                        webhookId = "hook-999",
                        eventType = "deployment",
                    )

                metadata1 shouldBe metadata2
                metadata1.hashCode() shouldBe metadata2.hashCode()
                metadata1 shouldNotBe metadata3
            }

            it("should support copy") {
                val metadata =
                    WebhookMetadata(
                        webhookId = "hook-123",
                        eventType = "deployment",
                    )

                val copied = metadata.copy(eventType = "build")

                copied.eventType shouldBe "build"
                copied.webhookId shouldBe "hook-123"
            }

            it("should have proper toString") {
                val metadata =
                    WebhookMetadata(
                        webhookId = "hook-123",
                        eventType = "deployment",
                    )

                metadata.toString() shouldBe "WebhookMetadata(source=webhook, webhookId=hook-123, eventType=deployment)"
            }
        }
    })
