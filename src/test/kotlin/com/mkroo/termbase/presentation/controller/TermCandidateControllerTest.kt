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

            describe("GET /candidates/detail") {
                it("용어 후보 상세 페이지를 반환한다") {
                    mockMvc
                        .perform(get("/candidates/detail").param("name", "API"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("candidates/detail"))
                        .andExpect(model().attribute("termName", "API"))
                        .andExpect(model().attributeExists("timeSeries"))
                        .andExpect(model().attributeExists("documents"))
                        .andExpect(model().attributeExists("totalFrequency"))
                        .andExpect(model().attribute("interval", "week"))
                        .andExpect(model().attribute("docSize", 10))
                }

                it("일간 간격으로 빈도 데이터를 조회한다") {
                    mockMvc
                        .perform(
                            get("/candidates/detail")
                                .param("name", "API")
                                .param("interval", "day"),
                        ).andExpect(status().isOk)
                        .andExpect(view().name("candidates/detail"))
                        .andExpect(model().attribute("interval", "day"))
                }

                it("월간 간격으로 빈도 데이터를 조회한다") {
                    mockMvc
                        .perform(
                            get("/candidates/detail")
                                .param("name", "API")
                                .param("interval", "month"),
                        ).andExpect(status().isOk)
                        .andExpect(view().name("candidates/detail"))
                        .andExpect(model().attribute("interval", "month"))
                }

                it("문서 개수를 지정하여 조회한다") {
                    mockMvc
                        .perform(
                            get("/candidates/detail")
                                .param("name", "API")
                                .param("docSize", "20"),
                        ).andExpect(status().isOk)
                        .andExpect(view().name("candidates/detail"))
                        .andExpect(model().attribute("docSize", 20))
                }

                it("문서 개수가 범위를 벗어나면 1-100으로 제한된다") {
                    mockMvc
                        .perform(
                            get("/candidates/detail")
                                .param("name", "API")
                                .param("docSize", "200"),
                        ).andExpect(status().isOk)
                        .andExpect(view().name("candidates/detail"))
                }
            }

            describe("POST /candidates/ignore") {
                it("용어를 무시 처리하고 목록 페이지로 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/candidates/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("reason", "일반적인 단어"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates"))
                        .andExpect(flash().attributeExists("success"))
                }

                it("검색어가 있을 때 무시 처리하면 검색어를 유지한 채 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/candidates/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("reason", "일반적인 단어")
                                .param("q", "테스트"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates?q=테스트"))
                }

                it("이미 용어로 등록된 단어를 무시 처리하면 에러와 함께 리다이렉트한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(
                            post("/candidates/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("reason", "테스트"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("이미 동의어로 등록된 단어를 무시 처리하면 에러와 함께 리다이렉트한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")
                    synonymService.addSynonym("API", "에이피아이")

                    mockMvc
                        .perform(
                            post("/candidates/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "에이피아이")
                                .param("reason", "테스트"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("이미 무시 처리된 단어를 다시 무시 처리하면 에러와 함께 리다이렉트한다") {
                    ignoredTermService.addIgnoredTerm("테스트", "기존 사유")

                    mockMvc
                        .perform(
                            post("/candidates/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("reason", "새로운 사유"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("무시 사유가 비어있으면 에러와 함께 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/candidates/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("reason", ""),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/candidates"))
                        .andExpect(flash().attributeExists("error"))
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
