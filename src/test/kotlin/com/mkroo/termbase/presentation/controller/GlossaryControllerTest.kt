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
                        .andExpect(model().attributeExists("candidates"))
                        .andExpect(model().attributeExists("q"))
                        .andExpect(model().attributeExists("sort"))
                }

                it("검색어로 용어를 필터링한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")
                    glossaryService.addTerm("SDK", "Software Development Kit")

                    mockMvc
                        .perform(get("/glossary").param("q", "API"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("glossary/list"))
                        .andExpect(model().attributeExists("terms"))
                }

                it("빈도순으로 정렬한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")
                    glossaryService.addTerm("SDK", "Software Development Kit")

                    mockMvc
                        .perform(get("/glossary").param("sort", "frequency"))
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
                        .andExpect(redirectedUrl("/glossary/detail?name=API"))
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
                        .andExpect(redirectedUrl("/glossary/detail?name=%EC%9D%B8%EA%B3%B5%EC%A7%80%EB%8A%A5"))
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

            describe("GET /glossary/detail") {
                it("용어 상세 페이지를 반환한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(get("/glossary/detail").param("name", "API"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("glossary/detail"))
                        .andExpect(model().attributeExists("term"))
                        .andExpect(model().attributeExists("timeSeries"))
                        .andExpect(model().attributeExists("documents"))
                        .andExpect(model().attributeExists("totalFrequency"))
                        .andExpect(model().attributeExists("interval"))
                        .andExpect(model().attributeExists("docSize"))
                        .andExpect(model().attributeExists("docSizeOptions"))
                }

                it("일간 간격으로 조회할 수 있다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(get("/glossary/detail").param("name", "API").param("interval", "day"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("glossary/detail"))
                        .andExpect(model().attribute("interval", "day"))
                }

                it("월간 간격으로 조회할 수 있다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(get("/glossary/detail").param("name", "API").param("interval", "month"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("glossary/detail"))
                        .andExpect(model().attribute("interval", "month"))
                }

                it("존재하지 않는 용어를 조회하면 예외가 발생한다") {
                    mockMvc
                        .perform(get("/glossary/detail").param("name", "없는용어"))
                        .andExpect(status().isNotFound)
                }

                it("슬래시가 포함된 용어를 조회할 수 있다") {
                    glossaryService.addTerm("CI/CD", "Continuous Integration/Continuous Deployment")

                    mockMvc
                        .perform(get("/glossary/detail").param("name", "CI/CD"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("glossary/detail"))
                        .andExpect(model().attributeExists("term"))
                }

                it("공백이 포함된 용어를 조회할 수 있다") {
                    glossaryService.addTerm("공영 주차장", "공공 주차장")

                    mockMvc
                        .perform(get("/glossary/detail").param("name", "공영 주차장"))
                        .andExpect(status().isOk)
                        .andExpect(view().name("glossary/detail"))
                        .andExpect(model().attributeExists("term"))
                }
            }

            describe("POST /glossary/definition") {
                it("용어 정의를 수정하고 상세 페이지로 리다이렉트한다") {
                    glossaryService.addTerm("API", "기존 정의")

                    mockMvc
                        .perform(
                            post("/glossary/definition")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("definition", "새로운 정의"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary/detail?name=API"))
                        .andExpect(flash().attributeExists("success"))
                }
            }

            describe("POST /glossary/delete") {
                it("용어를 삭제하고 목록 페이지로 리다이렉트한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(
                            post("/glossary/delete")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("success"))
                }
            }

            describe("POST /glossary/candidate") {
                it("용어 후보를 등록하고 목록 페이지로 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/glossary/candidate")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("definition", "Application Programming Interface"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("success"))
                }

                it("검색 쿼리가 있으면 쿼리를 포함하여 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/glossary/candidate")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "SDK")
                                .param("definition", "Software Development Kit")
                                .param("q", "SDK"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary?q=SDK"))
                        .andExpect(flash().attributeExists("success"))
                }

                it("이미 존재하는 용어를 등록하면 에러와 함께 리다이렉트한다") {
                    glossaryService.addTerm("API", "기존 정의")

                    mockMvc
                        .perform(
                            post("/glossary/candidate")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("definition", "새로운 정의"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("동의어로 등록된 용어를 추가하면 에러와 함께 리다이렉트한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")
                    synonymService.addSynonym("API", "인터페이스")

                    mockMvc
                        .perform(
                            post("/glossary/candidate")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "인터페이스")
                                .param("definition", "접점"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("기존 용어와 충돌하는 용어를 추가하면 경고와 함께 리다이렉트한다") {
                    glossaryService.addTerm("지능", "지적 능력")

                    mockMvc
                        .perform(
                            post("/glossary/candidate")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "인공지능")
                                .param("definition", "인간의 지능을 모방한 기술"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("warning"))
                }

                it("무시된 단어로 등록된 용어를 추가하면 에러와 함께 리다이렉트한다") {
                    ignoredTermService.addIgnoredTerm("테스트", "일반적인 단어")

                    mockMvc
                        .perform(
                            post("/glossary/candidate")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("definition", "시험"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("error"))
                }
            }

            describe("POST /glossary/candidate/ignore") {
                it("용어 후보를 무시 처리하고 목록 페이지로 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/glossary/candidate/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("reason", "일반적인 단어"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("success"))
                }

                it("검색 쿼리가 있으면 쿼리를 포함하여 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/glossary/candidate/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "일반")
                                .param("reason", "너무 일반적")
                                .param("q", "일반"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary?q=일반"))
                        .andExpect(flash().attributeExists("success"))
                }

                it("이미 용어 사전에 등록된 단어를 무시하면 에러와 함께 리다이렉트한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(
                            post("/glossary/candidate/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "API")
                                .param("reason", "이미 등록됨"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("동의어로 등록된 단어를 무시하면 에러와 함께 리다이렉트한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")
                    synonymService.addSynonym("API", "인터페이스")

                    mockMvc
                        .perform(
                            post("/glossary/candidate/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "인터페이스")
                                .param("reason", "이미 동의어"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("이미 무시 처리된 단어를 다시 무시하면 에러와 함께 리다이렉트한다") {
                    ignoredTermService.addIgnoredTerm("테스트", "일반적인 단어")

                    mockMvc
                        .perform(
                            post("/glossary/candidate/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("reason", "다시 무시"),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("error"))
                }

                it("무시 사유가 비어있으면 에러와 함께 리다이렉트한다") {
                    mockMvc
                        .perform(
                            post("/glossary/candidate/ignore")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "테스트")
                                .param("reason", ""),
                        ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/glossary"))
                        .andExpect(flash().attributeExists("error"))
                }
            }
        }
    }
}
