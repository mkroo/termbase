package com.mkroo.termbase.domain.model.confluence

import com.mkroo.termbase.domain.model.document.ConfluenceMetadata
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

class ConfluencePageTest :
    DescribeSpec({
        describe("ConfluencePage") {
            val fixedInstant = Instant.parse("2024-01-15T10:00:00Z")

            fun createPage() =
                ConfluencePage(
                    cloudId = "cloud-123",
                    spaceKey = "DEV",
                    pageId = "page-456",
                    title = "Test Page Title",
                    content = "This is the page content.",
                    lastModified = fixedInstant,
                )

            describe("toSourceDocument") {
                it("should convert to SourceDocument with correct id") {
                    val page = createPage()

                    val document = page.toSourceDocument()

                    document.id shouldBe "confluence:cloud-123:page-456"
                }

                it("should combine title and content") {
                    val page = createPage()

                    val document = page.toSourceDocument()

                    document.content shouldBe "Test Page Title\n\nThis is the page content."
                }

                it("should create ConfluenceMetadata") {
                    val page = createPage()

                    val document = page.toSourceDocument()

                    document.metadata.shouldBeInstanceOf<ConfluenceMetadata>()
                    val metadata = document.metadata as ConfluenceMetadata
                    metadata.cloudId shouldBe "cloud-123"
                    metadata.spaceKey shouldBe "DEV"
                    metadata.pageId shouldBe "page-456"
                    metadata.pageTitle shouldBe "Test Page Title"
                }

                it("should use lastModified as timestamp") {
                    val page = createPage()

                    val document = page.toSourceDocument()

                    document.timestamp shouldBe fixedInstant
                }
            }

            describe("data class properties") {
                it("should support equality") {
                    val page1 = createPage()
                    val page2 = createPage()

                    page1 shouldBe page2
                }

                it("should support copy") {
                    val page = createPage()

                    val copied = page.copy(title = "New Title")

                    copied.title shouldBe "New Title"
                    copied.pageId shouldBe "page-456"
                }
            }
        }
    })
