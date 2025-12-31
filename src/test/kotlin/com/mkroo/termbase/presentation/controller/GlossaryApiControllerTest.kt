package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.application.service.GlossaryService
import com.mkroo.termbase.application.service.IgnoredTermService
import com.mkroo.termbase.application.service.SynonymService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GlossaryApiControllerTest : DescribeSpec() {
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

        describe("GlossaryApiController") {
            describe("POST /api/glossary/{termName}/synonyms") {
                it("동의어를 추가한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(
                            post("/api/glossary/API/synonyms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"synonymName": "인터페이스"}"""),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))

                    val term = glossaryService.findByName("API")
                    term!!.synonyms.map { it.name } shouldBe listOf("인터페이스")
                }

                it("존재하지 않는 용어에 동의어를 추가하면 실패한다") {
                    mockMvc
                        .perform(
                            post("/api/glossary/없는용어/synonyms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"synonymName": "동의어"}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }

                it("동의어가 대표어와 같으면 실패한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(
                            post("/api/glossary/API/synonyms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"synonymName": "API"}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }

                it("이미 용어로 등록된 단어를 동의어로 추가하면 실패한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")
                    glossaryService.addTerm("인터페이스", "접점")

                    mockMvc
                        .perform(
                            post("/api/glossary/API/synonyms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"synonymName": "인터페이스"}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }

                it("이미 동의어로 등록된 단어를 동의어로 추가하면 실패한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")
                    glossaryService.addTerm("REST", "Representational State Transfer")
                    synonymService.addSynonym("API", "인터페이스")

                    mockMvc
                        .perform(
                            post("/api/glossary/REST/synonyms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"synonymName": "인터페이스"}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }

                it("이미 무시된 단어로 등록된 단어를 동의어로 추가하면 실패한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")
                    ignoredTermService.addIgnoredTerm("인터페이스", "일반적인 단어")

                    mockMvc
                        .perform(
                            post("/api/glossary/API/synonyms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"synonymName": "인터페이스"}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.message").value("이미 무시된 단어로 등록되어 있습니다: 인터페이스"))
                }
            }

            describe("DELETE /api/glossary/{termName}/synonyms/{synonymName}") {
                it("동의어를 삭제한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")
                    synonymService.addSynonym("API", "인터페이스")

                    mockMvc
                        .perform(delete("/api/glossary/API/synonyms/인터페이스"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))

                    val term = glossaryService.findByName("API")
                    term!!.synonyms.size shouldBe 0
                }

                it("존재하지 않는 동의어를 삭제하면 실패한다") {
                    glossaryService.addTerm("API", "Application Programming Interface")

                    mockMvc
                        .perform(delete("/api/glossary/API/synonyms/없는동의어"))
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }
        }
    }
}
