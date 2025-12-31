package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
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
 * User Story 8: 무시 용어 관리
 *
 * As a 관리자
 * I want to 무시된 단어(ignored)를 추가/수정/삭제할 수 있다
 * So that 용어 추출 시 무시된 단어를 제외할 수 있다
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IgnoredTermServiceTest : DescribeSpec() {
    @Autowired
    private lateinit var ignoredTermService: IgnoredTermService

    @Autowired
    private lateinit var glossaryService: GlossaryService

    @Autowired
    private lateinit var synonymService: SynonymService

    init {
        extension(SpringExtension())

        describe("무시된 단어 관리") {
            describe("무시된 단어 추가") {
                it("무시할 단어를 이유와 함께 추가할 수 있다") {
                    val result =
                        ignoredTermService.addIgnoredTerm(
                            name = "테스트",
                            reason = "일반적인 단어로 용어 추출에서 제외",
                        )

                    result.shouldBeInstanceOf<IgnoredTermAddResult.Success>()
                    val ignoredTerm = result.ignoredTerm
                    ignoredTerm.name shouldBe "테스트"
                    ignoredTerm.reason shouldBe "일반적인 단어로 용어 추출에서 제외"
                    ignoredTerm.createdAt shouldNotBe null
                }

                it("무시 사유 없이 추가하면 ReasonRequired를 반환한다") {
                    val result = ignoredTermService.addIgnoredTerm(name = "테스트", reason = "")

                    result.shouldBeInstanceOf<IgnoredTermAddResult.ReasonRequired>()
                }

                it("무시 사유가 공백만 있는 경우에도 ReasonRequired를 반환한다") {
                    val result = ignoredTermService.addIgnoredTerm(name = "테스트", reason = "   ")

                    result.shouldBeInstanceOf<IgnoredTermAddResult.ReasonRequired>()
                }

                it("이미 무시된 단어를 다시 추가하면 AlreadyIgnored를 반환한다") {
                    ignoredTermService.addIgnoredTerm(name = "테스트", reason = "첫 번째 사유")

                    val result = ignoredTermService.addIgnoredTerm(name = "테스트", reason = "두 번째 사유")

                    result.shouldBeInstanceOf<IgnoredTermAddResult.AlreadyIgnored>()
                    result.name shouldBe "테스트"
                }

                it("용어 사전에 등록된 용어를 무시하면 AlreadyExistsAsTerm을 반환한다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")

                    val result = ignoredTermService.addIgnoredTerm(name = "API", reason = "사유")

                    result.shouldBeInstanceOf<IgnoredTermAddResult.AlreadyExistsAsTerm>()
                    result.name shouldBe "API"
                }

                it("동의어로 등록된 단어를 무시하면 AlreadyExistsAsSynonym을 반환한다") {
                    glossaryService.addTerm(name = "API", definition = "Application Programming Interface")
                    synonymService.addSynonym(termName = "API", synonymName = "인터페이스")

                    val result = ignoredTermService.addIgnoredTerm(name = "인터페이스", reason = "사유")

                    result.shouldBeInstanceOf<IgnoredTermAddResult.AlreadyExistsAsSynonym>()
                    result.name shouldBe "인터페이스"
                }
            }

            describe("무시된 단어 삭제") {
                it("무시된 단어를 삭제할 수 있다") {
                    ignoredTermService.addIgnoredTerm(name = "테스트", reason = "무시 사유")

                    val removed = ignoredTermService.removeIgnoredTerm(name = "테스트")

                    removed.name shouldBe "테스트"
                    ignoredTermService.existsByName("테스트") shouldBe false
                }

                it("무시되지 않은 단어를 삭제하면 예외가 발생한다") {
                    val exception =
                        runCatching {
                            ignoredTermService.removeIgnoredTerm(name = "존재하지않는단어")
                        }.exceptionOrNull()

                    exception shouldNotBe null
                    exception!!.message shouldBe "무시 처리되지 않은 단어입니다: 존재하지않는단어"
                }
            }

            describe("무시 사유 수정") {
                it("무시된 단어의 사유를 수정할 수 있다") {
                    ignoredTermService.addIgnoredTerm(name = "테스트", reason = "기존 사유")

                    val updated = ignoredTermService.updateReason(name = "테스트", newReason = "새로운 사유")

                    updated.reason shouldBe "새로운 사유"
                }

                it("무시되지 않은 단어의 사유를 수정하면 예외가 발생한다") {
                    val exception =
                        runCatching {
                            ignoredTermService.updateReason(name = "존재하지않는단어", newReason = "새 사유")
                        }.exceptionOrNull()

                    exception shouldNotBe null
                    exception!!.message shouldBe "무시 처리되지 않은 단어입니다: 존재하지않는단어"
                }

                it("빈 사유로 수정하면 예외가 발생한다") {
                    ignoredTermService.addIgnoredTerm(name = "테스트", reason = "기존 사유")

                    val exception =
                        runCatching {
                            ignoredTermService.updateReason(name = "테스트", newReason = "")
                        }.exceptionOrNull()

                    exception shouldNotBe null
                    exception!!.message shouldBe "무시 사유는 필수입니다."
                }
            }

            describe("무시된 단어 조회") {
                it("무시된 단어를 이름으로 조회할 수 있다") {
                    ignoredTermService.addIgnoredTerm(name = "테스트", reason = "사유")

                    val found = ignoredTermService.findByName("테스트")

                    found shouldNotBe null
                    found!!.name shouldBe "테스트"
                }

                it("존재하지 않는 무시된 단어를 조회하면 null을 반환한다") {
                    val found = ignoredTermService.findByName("존재하지않는단어")

                    found shouldBe null
                }

                it("모든 무시된 단어를 조회할 수 있다") {
                    ignoredTermService.addIgnoredTerm(name = "단어1", reason = "사유1")
                    ignoredTermService.addIgnoredTerm(name = "단어2", reason = "사유2")

                    val all = ignoredTermService.findAll()

                    all.size shouldBe 2
                    all.map { it.name } shouldBe listOf("단어1", "단어2")
                }

                it("무시된 단어 존재 여부를 확인할 수 있다") {
                    ignoredTermService.addIgnoredTerm(name = "테스트", reason = "사유")

                    ignoredTermService.existsByName("테스트") shouldBe true
                    ignoredTermService.existsByName("존재하지않는단어") shouldBe false
                }
            }
        }
    }
}
