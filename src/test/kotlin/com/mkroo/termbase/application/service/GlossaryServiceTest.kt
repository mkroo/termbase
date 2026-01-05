package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.repository.TermRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * User Story 6: 용어 사전(glossary) 관리
 *
 * As a 관리자
 * I want to 용어 사전에 용어를 추가/수정/삭제할 수 있다
 * So that 용어 추출 정확도를 높일 수 있다
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GlossaryServiceTest : DescribeSpec() {
    @Autowired
    private lateinit var glossaryService: GlossaryService

    @Autowired
    private lateinit var synonymService: SynonymService

    @Autowired
    private lateinit var termRepository: TermRepository

    init {
        extension(SpringExtension())

        describe("용어 사전(glossary) 관리") {
            describe("용어 추가") {
                it("용어 사전에 정의와 함께 용어를 추가할 수 있다") {
                    val result =
                        glossaryService.addTerm(
                            name = "API",
                            definition = "Application Programming Interface의 약자",
                        )

                    result.shouldBeInstanceOf<TermAddResult.Success>()
                    result.term.name shouldBe "API"
                    result.term.definition shouldBe "Application Programming Interface의 약자"
                }

                it("이미 존재하는 용어를 추가하면 실패한다") {
                    glossaryService.addTerm(name = "API", definition = "기존 정의")

                    val result = glossaryService.addTerm(name = "API", definition = "새로운 정의")

                    result.shouldBeInstanceOf<TermAddResult.AlreadyExists>()
                    result.name shouldBe "API"
                }

                it("새 용어에 기존 용어가 포함되는 경우 충돌 경고를 반환한다") {
                    // "지능"이 이미 등록된 상태
                    glossaryService.addTerm(name = "지능", definition = "지적 능력")

                    // "인공지능" 추가 시 "지능"이 포함됨
                    val result = glossaryService.addTerm(name = "인공지능", definition = "인간의 지능을 모방한 기술")

                    result.shouldBeInstanceOf<TermAddResult.ConflictWithExistingTerms>()
                    result.name shouldBe "인공지능"
                    result.conflictingTerms shouldBe listOf("지능")
                }

                it("동의어로 등록된 용어를 대표어로 추가할 수 없다") {
                    // "API"의 동의어로 "인터페이스" 등록
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")
                    synonymService.addSynonym(termName = "API", synonymName = "인터페이스")

                    // "인터페이스"를 대표어로 추가 시도
                    val result = glossaryService.addTerm(name = "인터페이스", definition = "접점")

                    result.shouldBeInstanceOf<TermAddResult.AlreadyExistsAsSynonym>()
                    result.name shouldBe "인터페이스"
                }

                context("합성어 처리 (US-11)") {
                    it("공백이 포함된 합성어를 등록하면 공백 제거 버전이 동의어로 자동 추가된다") {
                        val result =
                            glossaryService.addTerm(
                                name = "공유 주차장",
                                definition = "여러 사람이 함께 사용하는 주차장",
                            )

                        result.shouldBeInstanceOf<TermAddResult.Success>()
                        result.term.name shouldBe "공유 주차장"
                        result.term.synonyms.size shouldBe 1
                        result.term.synonyms
                            .first()
                            .name shouldBe "공유주차장"
                    }

                    it("공백이 없는 용어를 등록하면 동의어가 자동 추가되지 않는다") {
                        val result =
                            glossaryService.addTerm(
                                name = "데이터베이스",
                                definition = "데이터를 저장하는 시스템",
                            )

                        result.shouldBeInstanceOf<TermAddResult.Success>()
                        result.term.synonyms.size shouldBe 0
                    }

                    it("공백 제거 버전이 이미 용어로 존재하면 동의어로 추가되지 않는다") {
                        // 먼저 "공유주차장"을 등록
                        glossaryService.addTerm(name = "공유주차장", definition = "기존 정의")

                        // "공유 주차장"을 등록 시도
                        val result =
                            glossaryService.addTerm(
                                name = "공유 주차장",
                                definition = "여러 사람이 함께 사용하는 주차장",
                            )

                        result.shouldBeInstanceOf<TermAddResult.Success>()
                        result.term.synonyms.size shouldBe 0
                    }

                    it("공백 제거 버전이 이미 동의어로 존재하면 동의어로 추가되지 않는다") {
                        // "파킹시스템" 용어에 "공유주차장"을 동의어로 등록 (충돌 방지를 위해 다른 용어 사용)
                        glossaryService.addTerm(name = "파킹시스템", definition = "주차 시스템")
                        synonymService.addSynonym(termName = "파킹시스템", synonymName = "공유주차장")

                        // "공유 주차장"을 등록 시도
                        val result =
                            glossaryService.addTerm(
                                name = "공유 주차장",
                                definition = "여러 사람이 함께 사용하는 주차장",
                            )

                        result.shouldBeInstanceOf<TermAddResult.Success>()
                        result.term.synonyms.size shouldBe 0
                    }

                    it("여러 공백이 포함된 합성어도 올바르게 처리된다") {
                        val result =
                            glossaryService.addTerm(
                                name = "인공 지능 시스템",
                                definition = "인공지능을 활용한 시스템",
                            )

                        result.shouldBeInstanceOf<TermAddResult.Success>()
                        result.term.synonyms.size shouldBe 1
                        result.term.synonyms
                            .first()
                            .name shouldBe "인공지능시스템"
                    }
                }
            }

            describe("용어 삭제") {
                it("용어 사전에서 용어를 삭제할 수 있다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")

                    glossaryService.deleteTerm(name = "API")

                    termRepository.findByName("API") shouldBe null
                }

                it("존재하지 않는 용어를 삭제하면 예외가 발생한다") {
                    val exception =
                        runCatching {
                            glossaryService.deleteTerm(name = "존재하지않는용어")
                        }.exceptionOrNull()

                    exception shouldNotBe null
                    exception!!.message shouldBe "존재하지 않는 용어입니다: 존재하지않는용어"
                }
            }

            describe("용어 정의 수정") {
                it("용어 사전에서 용어의 정의를 수정할 수 있다") {
                    glossaryService.addTerm(name = "API", definition = "기존 정의")

                    val updatedTerm =
                        glossaryService.updateDefinition(
                            name = "API",
                            newDefinition = "Application Programming Interface의 약자로, 소프트웨어 간 상호작용을 위한 인터페이스",
                        )

                    updatedTerm.definition shouldBe "Application Programming Interface의 약자로, 소프트웨어 간 상호작용을 위한 인터페이스"
                }

                it("존재하지 않는 용어의 정의를 수정하면 예외가 발생한다") {
                    val exception =
                        runCatching {
                            glossaryService.updateDefinition(name = "존재하지않는용어", newDefinition = "새 정의")
                        }.exceptionOrNull()

                    exception shouldNotBe null
                    exception!!.message shouldBe "존재하지 않는 용어입니다: 존재하지않는용어"
                }
            }

            describe("용어 조회") {
                it("모든 용어를 조회할 수 있다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")
                    glossaryService.addTerm(name = "SDK", definition = "Software Development Kit")

                    val terms = glossaryService.findAll()

                    terms.size shouldBe 2
                }

                it("용어가 없으면 빈 리스트를 반환한다") {
                    val terms = glossaryService.findAll()

                    terms.size shouldBe 0
                }

                it("이름으로 용어를 조회할 수 있다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")

                    val term = glossaryService.findByName("API")

                    term shouldNotBe null
                    term!!.name shouldBe "API"
                }

                it("존재하지 않는 용어를 조회하면 null을 반환한다") {
                    val term = glossaryService.findByName("존재하지않는용어")

                    term shouldBe null
                }
            }

            describe("용어 검색") {
                it("빈 쿼리로 검색하면 모든 용어를 반환한다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")
                    glossaryService.addTerm(name = "SDK", definition = "Software Development Kit")

                    val terms = glossaryService.search("")

                    terms.size shouldBe 2
                }

                it("공백 쿼리로 검색하면 모든 용어를 반환한다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")

                    val terms = glossaryService.search("   ")

                    terms.size shouldBe 1
                }

                it("이름으로 검색할 수 있다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")
                    glossaryService.addTerm(name = "SDK", definition = "Software Development Kit")

                    val terms = glossaryService.search("API")

                    terms.size shouldBe 1
                    terms.first().name shouldBe "API"
                }

                it("정의에 포함된 단어로 검색할 수 있다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")
                    glossaryService.addTerm(name = "SDK", definition = "Software Development Kit")

                    val terms = glossaryService.search("Programming")

                    terms.size shouldBe 1
                    terms.first().name shouldBe "API"
                }

                it("동의어로 검색할 수 있다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")
                    synonymService.addSynonym(termName = "API", synonymName = "에이피아이")

                    val terms = glossaryService.search("에이피아이")

                    terms.size shouldBe 1
                    terms.first().name shouldBe "API"
                }
            }
        }
    }
}
