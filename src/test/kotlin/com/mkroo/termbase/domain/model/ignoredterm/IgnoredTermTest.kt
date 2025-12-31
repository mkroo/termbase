package com.mkroo.termbase.domain.model.ignoredterm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

class IgnoredTermTest :
    DescribeSpec({
        describe("IgnoredTerm") {
            describe("생성") {
                it("단어와 무시 사유로 생성할 수 있다") {
                    val createdAt = LocalDateTime.of(2024, 1, 1, 0, 0)
                    val ignoredTerm =
                        IgnoredTerm(
                            id = 1L,
                            name = "테스트",
                            reason = "일반적인 단어",
                            createdAt = createdAt,
                        )

                    ignoredTerm.id shouldBe 1L
                    ignoredTerm.name shouldBe "테스트"
                    ignoredTerm.reason shouldBe "일반적인 단어"
                    ignoredTerm.createdAt shouldBe createdAt
                }

                it("기본값으로 생성할 수 있다") {
                    val ignoredTerm = IgnoredTerm(name = "테스트", reason = "무시 사유")

                    ignoredTerm.id shouldBe null
                    ignoredTerm.name shouldBe "테스트"
                    ignoredTerm.reason shouldBe "무시 사유"
                    ignoredTerm.createdAt shouldNotBe null
                }
            }

            describe("무시 사유 수정") {
                it("무시 사유를 수정할 수 있다") {
                    val ignoredTerm = IgnoredTerm(name = "테스트", reason = "기존 사유")

                    ignoredTerm.updateReason("새로운 사유")

                    ignoredTerm.reason shouldBe "새로운 사유"
                }

                it("무시 사유를 직접 설정할 수 있다") {
                    val ignoredTerm = IgnoredTerm(name = "테스트", reason = "기존 사유")

                    ignoredTerm.reason = "직접 설정한 사유"

                    ignoredTerm.reason shouldBe "직접 설정한 사유"
                }

                it("무시 사유는 필수이다") {
                    val ignoredTerm = IgnoredTerm(name = "테스트", reason = "기존 사유")

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            ignoredTerm.updateReason("")
                        }

                    exception.message shouldBe "무시 사유는 필수입니다."
                }

                it("공백만 있는 무시 사유는 허용되지 않는다") {
                    val ignoredTerm = IgnoredTerm(name = "테스트", reason = "기존 사유")

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            ignoredTerm.updateReason("   ")
                        }

                    exception.message shouldBe "무시 사유는 필수입니다."
                }
            }
        }
    })
