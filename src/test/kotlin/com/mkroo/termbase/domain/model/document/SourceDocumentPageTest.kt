package com.mkroo.termbase.domain.model.document

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.Instant

class SourceDocumentPageTest :
    DescribeSpec({
        describe("SourceDocumentPage") {
            describe("hasNext") {
                it("현재 페이지가 마지막 페이지보다 작으면 true를 반환한다") {
                    val page =
                        SourceDocumentPage(
                            documents = emptyList(),
                            totalElements = 30,
                            totalPages = 3,
                            currentPage = 1,
                            size = 10,
                        )

                    page.hasNext shouldBe true
                }

                it("현재 페이지가 마지막 페이지와 같으면 false를 반환한다") {
                    val page =
                        SourceDocumentPage(
                            documents = emptyList(),
                            totalElements = 30,
                            totalPages = 3,
                            currentPage = 2,
                            size = 10,
                        )

                    page.hasNext shouldBe false
                }
            }

            describe("hasPrevious") {
                it("현재 페이지가 0보다 크면 true를 반환한다") {
                    val page =
                        SourceDocumentPage(
                            documents = emptyList(),
                            totalElements = 30,
                            totalPages = 3,
                            currentPage = 1,
                            size = 10,
                        )

                    page.hasPrevious shouldBe true
                }

                it("현재 페이지가 0이면 false를 반환한다") {
                    val page =
                        SourceDocumentPage(
                            documents = emptyList(),
                            totalElements = 30,
                            totalPages = 3,
                            currentPage = 0,
                            size = 10,
                        )

                    page.hasPrevious shouldBe false
                }
            }

            describe("empty") {
                it("빈 페이지를 생성한다") {
                    val page = SourceDocumentPage.empty()

                    page.documents.shouldBeEmpty()
                    page.totalElements shouldBe 0
                    page.totalPages shouldBe 0
                    page.currentPage shouldBe 0
                    page.size shouldBe 20
                    page.hasNext shouldBe false
                    page.hasPrevious shouldBe false
                }

                it("지정된 페이지와 사이즈로 빈 페이지를 생성한다") {
                    val page = SourceDocumentPage.empty(page = 5, size = 50)

                    page.documents.shouldBeEmpty()
                    page.totalElements shouldBe 0
                    page.totalPages shouldBe 0
                    page.currentPage shouldBe 5
                    page.size shouldBe 50
                }
            }

            describe("data class") {
                it("equals, hashCode, toString, copy가 정상 동작한다") {
                    val doc =
                        SourceDocument(
                            id = "test-id",
                            content = "test content",
                            metadata =
                                SlackMetadata(
                                    workspaceId = "W1",
                                    channelId = "C1",
                                    messageId = "M1",
                                    userId = "U1",
                                ),
                            timestamp = Instant.now(),
                        )

                    val page1 =
                        SourceDocumentPage(
                            documents = listOf(doc),
                            totalElements = 1,
                            totalPages = 1,
                            currentPage = 0,
                            size = 20,
                        )

                    val page2 =
                        SourceDocumentPage(
                            documents = listOf(doc),
                            totalElements = 1,
                            totalPages = 1,
                            currentPage = 0,
                            size = 20,
                        )

                    (page1 == page2) shouldBe true
                    page1.hashCode() shouldBe page2.hashCode()
                    page1.toString().contains("SourceDocumentPage") shouldBe true

                    val copied = page1.copy(currentPage = 5)
                    copied.currentPage shouldBe 5
                    copied.documents shouldBe page1.documents
                }
            }
        }
    })
