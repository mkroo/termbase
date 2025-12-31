package com.mkroo.termbase.domain.model.term

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

class TermTest :
    DescribeSpec({
        describe("Term") {
            describe("생성") {
                it("용어 이름과 정의로 생성할 수 있다") {
                    val createdAt = LocalDateTime.of(2024, 1, 1, 0, 0)
                    val term =
                        Term(
                            id = 1L,
                            name = "API",
                            definition = "Application Programming Interface",
                            createdAt = createdAt,
                        )

                    term.id shouldBe 1L
                    term.name shouldBe "API"
                    term.definition shouldBe "Application Programming Interface"
                    term.createdAt shouldBe createdAt
                    term.synonyms shouldHaveSize 0
                }

                it("기본값으로 생성할 수 있다") {
                    val term = Term(name = "API", definition = "Application Programming Interface")

                    term.id shouldBe null
                    term.name shouldBe "API"
                    term.definition shouldBe "Application Programming Interface"
                    term.createdAt shouldNotBe null
                }
            }

            describe("동의어 추가") {
                it("동의어를 추가할 수 있다") {
                    val term = Term(name = "API", definition = "Application Programming Interface")

                    val synonym = term.addSynonym("인터페이스")

                    synonym.name shouldBe "인터페이스"
                    synonym.term shouldBe term
                    term.synonyms.map { it.name } shouldContain "인터페이스"
                }

                it("동의어는 대표어와 동일할 수 없다") {
                    val term = Term(name = "API", definition = "Application Programming Interface")

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            term.addSynonym("API")
                        }

                    exception.message shouldBe "동의어는 대표어와 동일할 수 없습니다."
                }

                it("이미 등록된 동의어는 다시 추가할 수 없다") {
                    val term = Term(name = "API", definition = "Application Programming Interface")
                    term.addSynonym("인터페이스")

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            term.addSynonym("인터페이스")
                        }

                    exception.message shouldBe "이미 등록된 동의어입니다: 인터페이스"
                }
            }

            describe("동의어 삭제") {
                it("동의어를 삭제할 수 있다") {
                    val term = Term(name = "API", definition = "Application Programming Interface")
                    term.addSynonym("인터페이스")

                    term.removeSynonym("인터페이스")

                    term.synonyms.map { it.name } shouldNotContain "인터페이스"
                }

                it("등록되지 않은 동의어를 삭제하면 예외가 발생한다") {
                    val term = Term(name = "API", definition = "Application Programming Interface")

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            term.removeSynonym("미등록")
                        }

                    exception.message shouldBe "등록되지 않은 동의어입니다: 미등록"
                }
            }

            describe("정의 수정") {
                it("정의를 수정할 수 있다") {
                    val term = Term(name = "API", definition = "기존 정의")

                    term.updateDefinition("새로운 정의")

                    term.definition shouldBe "새로운 정의"
                }

                it("정의를 직접 설정할 수 있다") {
                    val term = Term(name = "API", definition = "기존 정의")

                    term.definition = "직접 설정한 정의"

                    term.definition shouldBe "직접 설정한 정의"
                }
            }
        }
    })
