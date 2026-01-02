package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.SlackConversationsBatchService
import com.mkroo.termbase.domain.model.document.BulkInsertResult
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SlackBatchControllerTest.MockConfig::class)
class SlackBatchControllerTest : DescribeSpec() {
    @TestConfiguration
    class MockConfig {
        companion object {
            val mockService = mockk<SlackConversationsBatchService>(relaxed = true)
        }

        @Bean
        @Primary
        fun slackConversationsBatchServiceMock(): SlackConversationsBatchService = mockService
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        beforeEach {
            clearMocks(MockConfig.mockService)
        }

        describe("SlackBatchController") {
            describe("POST /api/slack/batch/collect") {
                it("메시지를 수집하고 결과를 반환한다") {
                    every {
                        MockConfig.mockService.collectMessages(
                            workspaceId = "T000001",
                            channelId = "C123456",
                            oldest = null,
                            latest = null,
                        )
                    } returns BulkInsertResult(10, 10, 0, emptyList())

                    val body =
                        """
                        {
                            "workspaceId": "T000001",
                            "channelId": "C123456"
                        }
                        """.trimIndent()

                    mockMvc
                        .perform(
                            post("/api/slack/batch/collect")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalCount").value(10))
                        .andExpect(jsonPath("$.successCount").value(10))
                        .andExpect(jsonPath("$.failureCount").value(0))
                }

                it("oldest와 latest 파라미터를 전달할 수 있다") {
                    every {
                        MockConfig.mockService.collectMessages(
                            workspaceId = "T000001",
                            channelId = "C123456",
                            oldest = "1704067200.000000",
                            latest = "1704153600.000000",
                        )
                    } returns BulkInsertResult(5, 5, 0, emptyList())

                    val body =
                        """
                        {
                            "workspaceId": "T000001",
                            "channelId": "C123456",
                            "oldest": "1704067200.000000",
                            "latest": "1704153600.000000"
                        }
                        """.trimIndent()

                    mockMvc
                        .perform(
                            post("/api/slack/batch/collect")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalCount").value(5))
                }
            }

            describe("POST /api/slack/batch/collect/{channelId}/incremental") {
                it("증분 수집을 수행하고 결과를 반환한다") {
                    every {
                        MockConfig.mockService.collectIncrementalMessages(
                            workspaceId = "T000001",
                            channelId = "C123456",
                        )
                    } returns BulkInsertResult(3, 3, 0, emptyList())

                    val body =
                        """
                        {
                            "workspaceId": "T000001"
                        }
                        """.trimIndent()

                    mockMvc
                        .perform(
                            post("/api/slack/batch/collect/C123456/incremental")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalCount").value(3))
                        .andExpect(jsonPath("$.successCount").value(3))
                        .andExpect(jsonPath("$.failureCount").value(0))
                }
            }
        }
    }
}
