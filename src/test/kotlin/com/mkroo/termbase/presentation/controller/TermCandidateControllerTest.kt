package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.application.service.GlossaryService
import com.mkroo.termbase.application.service.IgnoredTermService
import com.mkroo.termbase.application.service.SynonymService
import com.mkroo.termbase.domain.model.document.SlackMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TermCandidateControllerTest : DescribeSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var glossaryService: GlossaryService

    @Autowired
    private lateinit var synonymService: SynonymService

    @Autowired
    private lateinit var ignoredTermService: IgnoredTermService

    @Autowired
    private lateinit var elasticsearchOperations: ElasticsearchOperations

    init {
        extension(SpringExtension())

        beforeEach {
            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
            indexOps.createWithMapping()
        }

        afterEach {
            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
        }

        describe("TermCandidateController") {
            describe("GET /candidates") {
                it("용어 후보 목록 페이지를 반환한다") {
                    mockMvc
                        .perform(get("/candidates"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("candidates/list"))
                        .andExpect(model().attributeExists("candidates"))
                        .andExpect(model().attributeExists("query"))
                }

                it("검색어가 있으면 필터링된 결과를 반환한다") {
                    mockMvc
                        .perform(get("/candidates").param("q", "API"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("candidates/list"))
                        .andExpect(model().attribute("query", "API"))
                }
            }

            describe("POST /candidates") {
                it("용어를 등록하고 목록 페이지로 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/candidates")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("definition", "Application Programming Interface"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates"))
                        .andExpect(flash().attributeExists("success"))
                }

                it("검색어가 있을 때 등록하면 검색어를 유지한 채 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/candidates")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("definition", "Application Programming Interface")
                                .param("q", "API"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates?q=API"))
                }

                it("이미 존재하는 용어를 등록하면 에러와 함께 리다이렉트한다") {
                    glossaryService.addTerm("API", "기존 정의")

                    mockMvc
                        .perform(
                            post("/candidates")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("definition", "새로운 정의"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("이미 동의어로 등록된 용어를 등록하면 에러와 함께 리다이렉트한다") {
                    glossaryService.addTerm("API", "기존 정의")
                    synonymService.addSynonym("API", "인터페이스")

                    mockMvc
                        .perform(
                            post("/candidates")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "인터페이스")
                                .param("definition", "새로운 정의"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("이미 무시된 단어로 등록된 용어를 등록하면 에러와 함께 리다이렉트한다") {
                    ignoredTermService.addIgnoredTerm("API", "테스트용")

                    mockMvc
                        .perform(
                            post("/candidates")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("definition", "새로운 정의"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("기존 용어와 충돌하는 용어를 등록하면 경고와 함께 리다이렉트한다") {
                    glossaryService.addTerm("지능", "지적 능력")

                    mockMvc
                        .perform(
                            post("/candidates")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "인공지능")
                                .param("definition", "인간의 지능을 모방한 기술"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates"))
                        .andExpect(flash().attributeExists("warning"))
                }
            }
        }
    }

    private fun createSourceDocument(
        id: String,
        content: String,
    ): SourceDocument =
        SourceDocument(
            id = id,
            content = content,
            metadata =
                SlackMetadata(
                    workspaceId = "T123456",
                    channelId = "C789012",
                    messageId = "msg-$id",
                    userId = "U456789",
                ),
            timestamp = Instant.now(),
        )
}
