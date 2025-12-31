package com.mkroo.termbase.domain.model.term

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SynonymTest :
    DescribeSpec({
        describe("Synonym") {
            describe("생성") {
                it("동의어 이름과 대표어로 생성할 수 있다") {
                    val term = Term(id = 1L, name = "API", definition = "Application Programming Interface")
                    val synonym = Synonym(id = 1L, name = "인터페이스", term = term)

                    synonym.id shouldBe 1L
                    synonym.name shouldBe "인터페이스"
                    synonym.term shouldBe term
                }

                it("기본값으로 생성할 수 있다") {
                    val term = Term(name = "API", definition = "Application Programming Interface")
                    val synonym = Synonym(name = "인터페이스", term = term)

                    synonym.id shouldBe null
                    synonym.name shouldBe "인터페이스"
                    synonym.term shouldBe term
                }
            }
        }
    })
