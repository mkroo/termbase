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
class IgnoredTermControllerTest : DescribeSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var ignoredTermService: IgnoredTermService

    @Autowired
    private lateinit var glossaryService: GlossaryService

    @Autowired
    private lateinit var synonymService: SynonymService

    init {
        extension(SpringExtension())

        describe("IgnoredTermController") {
            describe("GET /ignored") {
                it("무시된 단어 목록 페이지를 반환한다") {
                    mockMvc
                        .perform(get("/ignored"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("ignored/list"))
                        .andExpect(model().attributeExists("ignoredTerms"))
                }
            }

            describe("GET /ignored/new") {
                it("무시된 단어 추가 폼 페이지를 반환한다") {
                    mockMvc
                        .perform(get("/ignored/new"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("ignored/form"))
                }
            }

            describe("POST /ignored") {
                it("새 무시된 단어를 추가하고 목록 페이지로 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/ignored")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("reason", "일반적인 단어"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/ignored"))
                        .andExpect(flash().attributeExists("success"))
                }

                it("무시 사유 없이 추가하면 에러와 함께 폼으로 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/ignored")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("reason", ""),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/ignored/new"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("이미 용어로 등록된 단어를 무시하면 에러와 함께 폼으로 리다이렉트한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(
                            post("/ignored")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("reason", "사유"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/ignored/new"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("이미 동의어로 등록된 단어를 무시하면 에러와 함께 폼으로 리다이렉트한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")
                    synonymService.addSynonym("API", "인터페이스")

                    mockMvc
                        .perform(
                            post("/ignored")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "인터페이스")
                                .param("reason", "사유"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/ignored/new"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("이미 무시된 단어를 다시 추가하면 에러와 함께 폼으로 리다이렉트한다") {
                    ignoredTermService.addIgnoredTerm("테스트", "첫 번째 사유")

                    mockMvc
                        .perform(
                            post("/ignored")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("reason", "두 번째 사유"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/ignored/new"))
                        .andExpect(flash().attributeExists("error"))
                }
            }

            describe("GET /ignored/{name}") {
                it("무시된 단어 상세 페이지를 반환한다") {
                    ignoredTermService.addIgnoredTerm("테스트", "일반적인 단어")

                    mockMvc
                        .perform(get("/ignored/테스트"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("ignored/detail"))
                        .andExpect(model().attributeExists("ignoredTerm"))
                }

                it("존재하지 않는 무시된 단어를 조회하면 예외가 발생한다") {
                    mockMvc
                        .perform(get("/ignored/없는단어"))
                        .andExpect(status().isNotFound)
                }
            }

            describe("POST /ignored/{name}/reason") {
                it("무시 사유를 수정하고 상세 페이지로 리다이렉트한다") {
                    ignoredTermService.addIgnoredTerm("테스트", "기존 사유")

                    mockMvc
                        .perform(
                            post("/ignored/테스트/reason")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("reason", "새로운 사유"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/ignored/테스트"))
                        .andExpect(flash().attributeExists("success"))
                }
            }

            describe("POST /ignored/{name}/delete") {
                it("무시된 단어를 삭제하고 목록 페이지로 리다이렉트한다") {
                    ignoredTermService.addIgnoredTerm("테스트", "일반적인 단어")

                    mockMvc
                        .perform(post("/ignored/테스트/delete"))
                        .andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/ignored"))
                        .andExpect(flash().attributeExists("success"))
                }
            }
        }
    }
}
