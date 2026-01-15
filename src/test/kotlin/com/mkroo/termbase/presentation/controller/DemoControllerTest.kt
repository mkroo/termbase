package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.ReindexingResult
import com.mkroo.termbase.application.service.ReindexingService
import com.mkroo.termbase.application.service.SlackConversationsBatchService
import com.mkroo.termbase.application.service.SourceDocumentService
import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.document.SourceDocumentPage
import com.mkroo.termbase.infrastructure.slack.SlackApiClient
import com.mkroo.termbase.infrastructure.slack.dto.SlackAuthTestResponse
import com.mkroo.termbase.infrastructure.slack.dto.SlackChannel
import com.mkroo.termbase.infrastructure.slack.dto.SlackConversationsListResponse
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DemoControllerTest.MockConfig::class)
class DemoControllerTest : DescribeSpec() {
    @TestConfiguration
    class MockConfig {
        companion object {
            val mockReindexingService = mockk<ReindexingService>(relaxed = true)
            val mockSlackApiClient = mockk<SlackApiClient>(relaxed = true)
            val mockSlackConversationsBatchService = mockk<SlackConversationsBatchService>(relaxed = true)
            val mockSourceDocumentService = mockk<SourceDocumentService>(relaxed = true)
        }

        @Bean
        @Primary
        fun reindexingServiceMock(): ReindexingService = mockReindexingService

        @Bean
        @Primary
        fun slackApiClientMock(): SlackApiClient = mockSlackApiClient

        @Bean
        @Primary
        fun slackConversationsBatchServiceMock(): SlackConversationsBatchService = mockSlackConversationsBatchService

        @Bean
        @Primary
        fun sourceDocumentServiceMock(): SourceDocumentService = mockSourceDocumentService
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        beforeEach {
            clearMocks(
                MockConfig.mockReindexingService,
                MockConfig.mockSlackApiClient,
                MockConfig.mockSlackConversationsBatchService,
                MockConfig.mockSourceDocumentService,
            )
        }

        describe("DemoController") {
            describe("GET /demo") {
                it("통합 데모 페이지를 보여준다") {
                    mockMvc
                        .perform(get("/demo"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("demo/index"))
                }
            }

            describe("POST /demo/reindex") {
                it("재인덱싱을 실행하고 결과를 반환한다") {
                    every {
                        MockConfig.mockReindexingService.reindex()
                    } returns
                        ReindexingResult(
                            previousIndex = "source_documents_v20240101",
                            newIndex = "source_documents_v20240102",
                            documentCount = 100,
                            userDictionarySize = 10,
                            synonymRulesSize = 5,
                            compoundWordMappingsSize = 3,
                        )

                    mockMvc
                        .perform(post("/demo/reindex"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.previousIndex").value("source_documents_v20240101"))
                        .andExpect(jsonPath("$.newIndex").value("source_documents_v20240102"))
                        .andExpect(jsonPath("$.documentCount").value(100))
                        .andExpect(jsonPath("$.userDictionarySize").value(10))
                        .andExpect(jsonPath("$.synonymRulesSize").value(5))

                    verify { MockConfig.mockReindexingService.reindex() }
                }

                it("이전 인덱스가 없는 경우에도 정상 동작한다") {
                    every {
                        MockConfig.mockReindexingService.reindex()
                    } returns
                        ReindexingResult(
                            previousIndex = null,
                            newIndex = "source_documents_v20240102",
                            documentCount = 0,
                            userDictionarySize = 0,
                            synonymRulesSize = 0,
                            compoundWordMappingsSize = 0,
                        )

                    mockMvc
                        .perform(post("/demo/reindex"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.previousIndex").doesNotExist())
                        .andExpect(jsonPath("$.newIndex").value("source_documents_v20240102"))
                }

                it("재인덱싱 실패 시 에러를 반환한다") {
                    every {
                        MockConfig.mockReindexingService.reindex()
                    } throws RuntimeException("Elasticsearch connection failed")

                    mockMvc
                        .perform(post("/demo/reindex"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error").value("Elasticsearch connection failed"))
                }
            }

            describe("GET /demo/reindex/status") {
                it("재인덱싱이 필요한 경우 true를 반환한다") {
                    every {
                        MockConfig.mockReindexingService.isReindexingRequired()
                    } returns true

                    mockMvc
                        .perform(get("/demo/reindex/status"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.reindexingRequired").value(true))
                }

                it("재인덱싱이 필요하지 않은 경우 false를 반환한다") {
                    every {
                        MockConfig.mockReindexingService.isReindexingRequired()
                    } returns false

                    mockMvc
                        .perform(get("/demo/reindex/status"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.reindexingRequired").value(false))
                }
            }

            describe("GET /demo/slack/workspace-info") {
                it("워크스페이스 정보와 채널 목록을 반환한다") {
                    every {
                        MockConfig.mockSlackApiClient.authTest(any())
                    } returns SlackAuthTestResponse(ok = true, teamId = "T123", team = "test-workspace")

                    every {
                        MockConfig.mockSlackApiClient.fetchAllChannels(any())
                    } returns
                        SlackConversationsListResponse(
                            ok = true,
                            channels =
                                listOf(
                                    SlackChannel(id = "C001", name = "general", isPrivate = false),
                                    SlackChannel(id = "C002", name = "random", isPrivate = false),
                                ),
                        )

                    mockMvc
                        .perform(get("/demo/slack/workspace-info"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.ok").value(true))
                        .andExpect(jsonPath("$.workspaceId").value("T-test-workspace"))
                        .andExpect(jsonPath("$.workspaceName").value("test-workspace"))
                        .andExpect(jsonPath("$.channels.length()").value(2))
                        .andExpect(jsonPath("$.channels[0].id").value("C001"))
                        .andExpect(jsonPath("$.channels[0].name").value("general"))
                }

                it("인증 실패 시 에러를 반환한다") {
                    every {
                        MockConfig.mockSlackApiClient.authTest(any())
                    } returns SlackAuthTestResponse(ok = false, error = "invalid_auth")

                    mockMvc
                        .perform(get("/demo/slack/workspace-info"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.ok").value(false))
                        .andExpect(jsonPath("$.error").value("invalid_auth"))
                }
            }

            describe("POST /demo/slack/collect-ajax") {
                it("메시지를 수집하고 결과를 반환한다") {
                    every {
                        MockConfig.mockSlackConversationsBatchService.collectMessagesWithToken(
                            botToken = any(),
                            workspaceId = any(),
                            channelId = any(),
                            oldest = any(),
                            latest = any(),
                        )
                    } returns BulkInsertResult(totalCount = 50, successCount = 48, failureCount = 2, failures = emptyList())

                    mockMvc
                        .perform(
                            post("/demo/slack/collect-ajax")
                                .param("workspaceId", "T123")
                                .param("channelId", "C001"),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.messageCount").value(50))
                        .andExpect(jsonPath("$.successCount").value(48))
                        .andExpect(jsonPath("$.failureCount").value(2))
                }

                it("수집 실패 시 에러를 반환한다") {
                    every {
                        MockConfig.mockSlackConversationsBatchService.collectMessagesWithToken(
                            botToken = any(),
                            workspaceId = any(),
                            channelId = any(),
                            oldest = any(),
                            latest = any(),
                        )
                    } throws RuntimeException("channel_not_found")

                    mockMvc
                        .perform(
                            post("/demo/slack/collect-ajax")
                                .param("workspaceId", "T123")
                                .param("channelId", "C999"),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.error").value("channel_not_found"))
                }
            }

            describe("GET /demo/source-documents/api") {
                it("문서 목록을 페이지네이션하여 반환한다") {
                    every {
                        MockConfig.mockSourceDocumentService.getDocuments(page = 0, size = 10)
                    } returns
                        SourceDocumentPage(
                            documents = emptyList(),
                            totalElements = 100,
                            totalPages = 10,
                            currentPage = 0,
                            size = 10,
                        )

                    mockMvc
                        .perform(get("/demo/source-documents/api").param("page", "0").param("size", "10"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(100))
                        .andExpect(jsonPath("$.totalPages").value(10))
                        .andExpect(jsonPath("$.currentPage").value(0))
                        .andExpect(jsonPath("$.hasNext").value(true))
                        .andExpect(jsonPath("$.hasPrevious").value(false))
                }
            }
        }
    }
}
