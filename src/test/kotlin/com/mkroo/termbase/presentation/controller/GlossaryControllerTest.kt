package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.application.service.GlossaryService
import com.mkroo.termbase.application.service.IgnoredTermService
import com.mkroo.termbase.application.service.SynonymService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
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

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GlossaryControllerTest : DescribeSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var glossaryService: GlossaryService

    @Autowired
    private lateinit var synonymService: SynonymService

    @Autowired
    private lateinit var ignoredTermService: IgnoredTermService

    init {
        extension(SpringExtension())

        describe("GlossaryController") {
            describe("GET /glossary") {
                it("용어 목록 페이지를 반환한다") {
                    mockMvc
                        .perform(get("/glossary"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("glossary/list"))
                        .andExpect(model().attributeExists("terms"))
                }
            }

            describe("GET /glossary/new") {
                it("용어 추가 폼 페이지를 반환한다") {
                    mockMvc
                        .perform(get("/glossary/new"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("glossary/form"))
                }
            }

            describe("POST /glossary") {
                it("새 용어를 추가하고 상세 페이지로 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/glossary")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("definition", "Application Programming Interface"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary/API"))
                }

                it("이미 존재하는 용어를 추가하면 에러와 함께 폼으로 리다이렉트한다") {
                    glossaryService.addTerm("API", "기존 정의")

                    mockMvc
                        .perform(
                            post("/glossary")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("definition", "새로운 정의"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary/new"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("동의어로 등록된 용어를 추가하면 에러와 함께 폼으로 리다이렉트한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")
                    synonymService.addSynonym("API", "인터페이스")

                    mockMvc
                        .perform(
                            post("/glossary")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "인터페이스")
                                .param("definition", "접점"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary/new"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("기존 용어와 충돌하는 용어를 추가하면 경고와 함께 상세 페이지로 리다이렉트한다") {
                    glossaryService.addTerm("지능", "지적 능력")

                    mockMvc
                        .perform(
                            post("/glossary")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "인공지능")
                                .param("definition", "인간의 지능을 모방한 기술"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary/인공지능"))
                        .andExpect(flash().attributeExists("warning"))
                }

                it("무시된 단어로 등록된 용어를 추가하면 에러와 함께 폼으로 리다이렉트한다") {
                    ignoredTermService.addIgnoredTerm("테스트", "일반적인 단어")

                    mockMvc
                        .perform(
                            post("/glossary")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("definition", "시험"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary/new"))
                        .andExpect(flash().attributeExists("error"))
                }
            }

            describe("GET /glossary/{name}") {
                it("용어 상세 페이지를 반환한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(get("/glossary/API"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("glossary/detail"))
                        .andExpect(model().attributeExists("term"))
                }

                it("존재하지 않는 용어를 조회하면 예외가 발생한다") {
                    mockMvc
                        .perform(get("/glossary/없는용어"))
                        .andExpect(status().isNotFound)
                }
            }

            describe("POST /glossary/{name}/definition") {
                it("용어 정의를 수정하고 상세 페이지로 리다이렉트한다") {
                    glossaryService.addTerm("API", "기존 정의")

                    mockMvc
                        .perform(
                            post("/glossary/API/definition")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("definition", "새로운 정의"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary/API"))
                        .andExpect(flash().attributeExists("success"))
                }
            }

            describe("POST /glossary/{name}/delete") {
                it("용어를 삭제하고 목록 페이지로 리다이렉트한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(post("/glossary/API/delete"))
                        .andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("success"))
                }
            }
        }
    }
}
