package com.mkroo.termbase.domain.model.candidate

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal

class TermCandidateTest :
    DescribeSpec({
        describe("TermCandidate") {
            fun createCandidate(status: CandidateStatus = CandidateStatus.PENDING): TermCandidate =
                TermCandidate(
                    id = 1L,
                    term = "공유 주차장",
                    components = "[\"공유\", \"주차장\"]",
                    count = 10,
                    pmi = BigDecimal("3.5"),
                    npmi = BigDecimal("0.7"),
                    docCount = 5,
                    idf = BigDecimal("2.3"),
                    avgTfidf = BigDecimal("0.5"),
                    relevanceScore = BigDecimal("0.65"),
                    status = status,
                )

            describe("생성") {
                it("용어 후보를 생성할 수 있다") {
                    val candidate = createCandidate()

                    candidate.id shouldBe 1L
                    candidate.term shouldBe "공유 주차장"
                    candidate.components shouldBe "[\"공유\", \"주차장\"]"
                    candidate.count shouldBe 10
                    candidate.pmi shouldBe BigDecimal("3.5")
                    candidate.npmi shouldBe BigDecimal("0.7")
                    candidate.docCount shouldBe 5
                    candidate.idf shouldBe BigDecimal("2.3")
                    candidate.avgTfidf shouldBe BigDecimal("0.5")
                    candidate.relevanceScore shouldBe BigDecimal("0.65")
                    candidate.status shouldBe CandidateStatus.PENDING
                    candidate.reviewedBy shouldBe null
                    candidate.reviewedAt shouldBe null
                    candidate.createdAt shouldNotBe null
                }
            }

            describe("상태 속성") {
                it("PENDING 상태인 경우 isPending이 true이다") {
                    val candidate = createCandidate(CandidateStatus.PENDING)

                    candidate.isPending shouldBe true
                    candidate.isApproved shouldBe false
                    candidate.isRejected shouldBe false
                }

                it("APPROVED 상태인 경우 isApproved가 true이다") {
                    val candidate = createCandidate(CandidateStatus.APPROVED)

                    candidate.isPending shouldBe false
                    candidate.isApproved shouldBe true
                    candidate.isRejected shouldBe false
                }

                it("REJECTED 상태인 경우 isRejected가 true이다") {
                    val candidate = createCandidate(CandidateStatus.REJECTED)

                    candidate.isPending shouldBe false
                    candidate.isApproved shouldBe false
                    candidate.isRejected shouldBe true
                }
            }

            describe("approve") {
                it("PENDING 상태의 후보를 승인할 수 있다") {
                    val candidate = createCandidate(CandidateStatus.PENDING)

                    candidate.approve("admin")

                    candidate.status shouldBe CandidateStatus.APPROVED
                    candidate.reviewedBy shouldBe "admin"
                    candidate.reviewedAt shouldNotBe null
                }

                it("APPROVED 상태의 후보는 승인할 수 없다") {
                    val candidate = createCandidate(CandidateStatus.APPROVED)

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            candidate.approve("admin")
                        }

                    exception.message shouldBe "PENDING 상태의 후보만 승인할 수 있습니다."
                }

                it("REJECTED 상태의 후보는 승인할 수 없다") {
                    val candidate = createCandidate(CandidateStatus.REJECTED)

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            candidate.approve("admin")
                        }

                    exception.message shouldBe "PENDING 상태의 후보만 승인할 수 있습니다."
                }
            }

            describe("reject") {
                it("PENDING 상태의 후보를 거절할 수 있다") {
                    val candidate = createCandidate(CandidateStatus.PENDING)

                    candidate.reject("admin")

                    candidate.status shouldBe CandidateStatus.REJECTED
                    candidate.reviewedBy shouldBe "admin"
                    candidate.reviewedAt shouldNotBe null
                }

                it("APPROVED 상태의 후보는 거절할 수 없다") {
                    val candidate = createCandidate(CandidateStatus.APPROVED)

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            candidate.reject("admin")
                        }

                    exception.message shouldBe "PENDING 상태의 후보만 거절할 수 있습니다."
                }

                it("REJECTED 상태의 후보는 거절할 수 없다") {
                    val candidate = createCandidate(CandidateStatus.REJECTED)

                    val exception =
                        shouldThrow<IllegalArgumentException> {
                            candidate.reject("admin")
                        }

                    exception.message shouldBe "PENDING 상태의 후보만 거절할 수 있습니다."
                }
            }
        }
    })
