package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * User Story 7: 동의어 관리
 *
 * As a 관리자
 * I want to 동의어 사전에 동의어 쌍을 추가/수정/삭제할 수 있다
 * So that 용어 추출 시 동의어를 대표어로 치환할 수 있다
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SynonymServiceTest : DescribeSpec() {
    @Autowired
    private lateinit var synonymService: SynonymService

    @Autowired
    private lateinit var glossaryService: GlossaryService

    init {
        extension(SpringExtension())

        describe("동의어 관리") {
            describe("동의어 추가") {
                it("동의어 쌍을 추가할 수 있다 (예: 대표어: AI, 동의어: 인공지능)") {
                    glossaryService.addTerm(name = "AI", definition = "Artificial Intelligence")

                    val result = synonymService.addSynonym(termName = "AI", synonymName = "인공지능")

                    result.shouldBeInstanceOf<SynonymAddResult.Success>()
                    result.term.synonyms.map { it.name } shouldContain "인공지능"
                }

                it("대표어는 이미 용어 사전에 정의된 용어여야 한다") {
                    // "미등록용어"는 용어 사전에 없음
                    val result = synonymService.addSynonym(termName = "미등록용어", synonymName = "동의어")

                    result.shouldBeInstanceOf<SynonymAddResult.TermNotFound>()
                    result.termName shouldBe "미등록용어"
                }

                it("동의어는 용어 사전에 정의되지 않은 용어여야 한다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")
                    glossaryService.addTerm(name = "인터페이스", definition = "접점")

                    // "인터페이스"는 이미 용어 사전에 정의된 용어
                    val result = synonymService.addSynonym(termName = "API", synonymName = "인터페이스")

                    result.shouldBeInstanceOf<SynonymAddResult.AlreadyExistsAsTerm>()
                    result.synonymName shouldBe "인터페이스"
                }

                it("동의어는 다른 동의어 쌍에 이미 포함되지 않은 용어여야 한다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")
                    glossaryService.addTerm(name = "REST", definition = "Representational State Transfer")
                    synonymService.addSynonym(termName = "API", synonymName = "인터페이스")

                    // "인터페이스"는 이미 API의 동의어로 등록됨
                    val result = synonymService.addSynonym(termName = "REST", synonymName = "인터페이스")

                    result.shouldBeInstanceOf<SynonymAddResult.AlreadyExistsAsSynonym>()
                    result.synonymName shouldBe "인터페이스"
                }

                it("동의어는 대표어와 동일한 용어일 수 없다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")

                    val result = synonymService.addSynonym(termName = "API", synonymName = "API")

                    result.shouldBeInstanceOf<SynonymAddResult.SameAsCanonical>()
                    result.synonymName shouldBe "API"
                }

                it("하나의 대표어에 여러 동의어를 추가할 수 있다") {
                    glossaryService.addTerm(name = "AI", definition = "Artificial Intelligence")

                    synonymService.addSynonym(termName = "AI", synonymName = "인공지능")
                    synonymService.addSynonym(termName = "AI", synonymName = "에이아이")
                    synonymService.addSynonym(termName = "AI", synonymName = "Artificial Intelligence")

                    val term = glossaryService.findByName("AI")
                    term shouldNotBe null
                    term!!.synonyms shouldHaveSize 3
                    term.synonyms.map { it.name } shouldContain "인공지능"
                    term.synonyms.map { it.name } shouldContain "에이아이"
                    term.synonyms.map { it.name } shouldContain "Artificial Intelligence"
                }
            }

            describe("동의어 삭제") {
                it("동의어 쌍을 삭제할 수 있다") {
                    glossaryService.addTerm(name = "AI", definition = "Artificial Intelligence")
                    synonymService.addSynonym(termName = "AI", synonymName = "인공지능")

                    synonymService.removeSynonym(termName = "AI", synonymName = "인공지능")

                    val term = glossaryService.findByName("AI")
                    term shouldNotBe null
                    term!!.synonyms.map { it.name } shouldNotContain "인공지능"
                }

                it("등록되지 않은 동의어를 삭제하면 예외가 발생한다") {
                    glossaryService.addTerm(name = "AI", definition = "Artificial Intelligence")

                    val exception =
                        runCatching {
                            synonymService.removeSynonym(termName = "AI", synonymName = "미등록동의어")
                        }.exceptionOrNull()

                    exception shouldNotBe null
                    exception!!.message shouldBe "등록되지 않은 동의어입니다: 미등록동의어"
                }

                it("존재하지 않는 용어에서 동의어를 삭제하면 예외가 발생한다") {
                    val exception =
                        runCatching {
                            synonymService.removeSynonym(termName = "존재하지않는용어", synonymName = "동의어")
                        }.exceptionOrNull()

                    exception shouldNotBe null
                    exception!!.message shouldBe "존재하지 않는 용어입니다: 존재하지않는용어"
                }
            }

            describe("동의어 조회") {
                it("용어의 동의어 목록을 조회할 수 있다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")
                    synonymService.addSynonym(termName = "API", synonymName = "인터페이스")
                    synonymService.addSynonym(termName = "API", synonymName = "에이피아이")

                    val term = glossaryService.findByName("API")

                    term shouldNotBe null
                    term!!.synonyms shouldHaveSize 2
                }
            }
        }
    }
}
