package com.mkroo.termbase.domain.model.candidate

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CandidateBatchHistoryTest :
    DescribeSpec({
        describe("CandidateBatchHistory") {
            describe("생성") {
                it("배치 이력을 생성할 수 있다") {
                    val history = CandidateBatchHistory()

                    history.id shouldBe null
                    history.totalDocuments shouldBe 0
                    history.totalCandidates shouldBe 0
                    history.startedAt shouldNotBe null
                    history.completedAt shouldBe null
                    history.status shouldBe BatchStatus.RUNNING
                    history.errorMessage shouldBe null
                }
            }

            describe("상태 속성") {
                it("RUNNING 상태인 경우 isRunning이 true이다") {
                    val history = CandidateBatchHistory(status = BatchStatus.RUNNING)

                    history.isRunning shouldBe true
                    history.isCompleted shouldBe false
                    history.isFailed shouldBe false
                }

                it("COMPLETED 상태인 경우 isCompleted가 true이다") {
                    val history = CandidateBatchHistory(status = BatchStatus.COMPLETED)

                    history.isRunning shouldBe false
                    history.isCompleted shouldBe true
                    history.isFailed shouldBe false
                }

                it("FAILED 상태인 경우 isFailed가 true이다") {
                    val history = CandidateBatchHistory(status = BatchStatus.FAILED)

                    history.isRunning shouldBe false
                    history.isCompleted shouldBe false
                    history.isFailed shouldBe true
                }
            }

            describe("complete") {
                it("RUNNING 상태의 배치를 완료할 수 있다") {
                    val history = CandidateBatchHistory()

                    history.complete(totalDocuments = 100, totalCandidates = 50)

                    history.status shouldBe BatchStatus.COMPLETED
                    history.totalDocuments shouldBe 100
                    history.totalCandidates shouldBe 50
                    history.completedAt shouldNotBe null
                }

                it("COMPLETED 상태의 배치는 완료할 수 없다") {
                    val history = CandidateBatchHistory(status = BatchStatus.COMPLETED)

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            history.complete(100, 50)
                        }

                    exception.message shouldBe "RUNNING 상태의 배치만 완료할 수 있습니다."
                }

                it("FAILED 상태의 배치는 완료할 수 없다") {
                    val history = CandidateBatchHistory(status = BatchStatus.FAILED)

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            history.complete(100, 50)
                        }

                    exception.message shouldBe "RUNNING 상태의 배치만 완료할 수 있습니다."
                }
            }

            describe("fail") {
                it("RUNNING 상태의 배치를 실패 처리할 수 있다") {
                    val history = CandidateBatchHistory()

                    history.fail("처리 중 오류 발생")

                    history.status shouldBe BatchStatus.FAILED
                    history.errorMessage shouldBe "처리 중 오류 발생"
                    history.completedAt shouldNotBe null
                }

                it("COMPLETED 상태의 배치는 실패 처리할 수 없다") {
                    val history = CandidateBatchHistory(status = BatchStatus.COMPLETED)

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            history.fail("오류")
                        }

                    exception.message shouldBe "RUNNING 상태의 배치만 실패 처리할 수 있습니다."
                }

                it("FAILED 상태의 배치는 실패 처리할 수 없다") {
                    val history = CandidateBatchHistory(status = BatchStatus.FAILED)

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            history.fail("오류")
                        }

                    exception.message shouldBe "RUNNING 상태의 배치만 실패 처리할 수 있습니다."
                }
            }
        }
    })
