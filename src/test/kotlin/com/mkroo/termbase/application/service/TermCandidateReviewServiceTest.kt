package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.candidate.CandidateStatus
import com.mkroo.termbase.domain.model.candidate.TermCandidate
import com.mkroo.termbase.domain.repository.TermCandidateRepository
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
import java.math.BigDecimal

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TermCandidateReviewServiceTest : DescribeSpec() {
    @Autowired
    private lateinit var reviewService: TermCandidateReviewService

    @Autowired
    private lateinit var termCandidateRepository: TermCandidateRepository

    @Autowired
    private lateinit var termRepository: TermRepository

    @Autowired
    private lateinit var glossaryService: GlossaryService

    @Autowired
    private lateinit var synonymService: SynonymService

    @Autowired
    private lateinit var ignoredTermService: IgnoredTermService

    init {
        extension(SpringExtension())

        beforeEach {
            termCandidateRepository.deleteAll()
        }

        describe("TermCandidateReviewService") {
            describe("getPendingCandidates") {
                it("PENDING 상태의 후보를 관련성 점수 내림차순으로 반환한다") {
                    // Given
                    val candidate1 = createAndSaveCandidate("용어A", BigDecimal("0.5"))
                    val candidate2 = createAndSaveCandidate("용어B", BigDecimal("0.8"))
                    val candidate3 = createAndSaveCandidate("용어C", BigDecimal("0.3"))

                    // When
                    val result = reviewService.getPendingCandidates(page = 0, size = 10)

                    // Then
                    result.content shouldBe listOf(candidate2, candidate1, candidate3)
                }

                it("페이지네이션이 적용된다") {
                    // Given
                    (1..25).forEach { i ->
                        createAndSaveCandidate("용어$i", BigDecimal("0.$i"))
                    }

                    // When
                    val page1 = reviewService.getPendingCandidates(page = 0, size = 10)
                    val page2 = reviewService.getPendingCandidates(page = 1, size = 10)

                    // Then
                    page1.content.size shouldBe 10
                    page2.content.size shouldBe 10
                    page1.totalElements shouldBe 25
                    page1.totalPages shouldBe 3
                }

                it("PENDING 상태가 아닌 후보는 제외한다") {
                    // Given
                    val pending = createAndSaveCandidate("대기중", BigDecimal("0.5"))
                    createAndSaveCandidate("승인됨", BigDecimal("0.5"), CandidateStatus.APPROVED)
                    createAndSaveCandidate("거절됨", BigDecimal("0.5"), CandidateStatus.REJECTED)

                    // When
                    val result = reviewService.getPendingCandidates()

                    // Then
                    result.content.size shouldBe 1
                    result.content.first().term shouldBe pending.term
                }
            }

            describe("findById") {
                it("ID로 후보를 조회할 수 있다") {
                    val candidate = createAndSaveCandidate("테스트 용어", BigDecimal("0.5"))

                    val found = reviewService.findById(candidate.id!!)

                    found shouldNotBe null
                    found!!.term shouldBe "테스트 용어"
                }

                it("존재하지 않는 ID는 null을 반환한다") {
                    val found = reviewService.findById(99999L)

                    found shouldBe null
                }
            }

            describe("findByTerm") {
                it("용어로 후보를 조회할 수 있다") {
                    createAndSaveCandidate("공유 주차장", BigDecimal("0.5"))

                    val found = reviewService.findByTerm("공유 주차장")

                    found shouldNotBe null
                    found!!.term shouldBe "공유 주차장"
                }

                it("존재하지 않는 용어는 null을 반환한다") {
                    val found = reviewService.findByTerm("존재하지않는용어")

                    found shouldBe null
                }
            }

            describe("approve") {
                it("PENDING 상태의 후보를 승인하면 용어가 등록된다") {
                    val candidate = createAndSaveCandidate("공유 주차장", BigDecimal("0.5"))

                    val result =
                        reviewService.approve(
                            candidateId = candidate.id!!,
                            reviewer = "admin",
                            definition = "공유 주차장의 정의",
                        )

                    result.shouldBeInstanceOf<CandidateReviewResult.Approved>()
                    result.candidate.status shouldBe CandidateStatus.APPROVED
                    result.candidate.reviewedBy shouldBe "admin"
                    result.candidate.reviewedAt shouldNotBe null

                    val term = termRepository.findByName("공유 주차장")
                    term shouldNotBe null
                    term!!.definition shouldBe "공유 주차장의 정의"
                }

                it("존재하지 않는 후보는 NotFound를 반환한다") {
                    val result =
                        reviewService.approve(
                            candidateId = 99999L,
                            reviewer = "admin",
                            definition = "정의",
                        )

                    result.shouldBeInstanceOf<CandidateReviewResult.NotFound>()
                    result.candidateId shouldBe 99999L
                }

                it("이미 승인된 후보는 AlreadyReviewed를 반환한다") {
                    val candidate = createAndSaveCandidate("테스트", BigDecimal("0.5"), CandidateStatus.APPROVED)

                    val result =
                        reviewService.approve(
                            candidateId = candidate.id!!,
                            reviewer = "admin",
                            definition = "정의",
                        )

                    result.shouldBeInstanceOf<CandidateReviewResult.AlreadyReviewed>()
                    result.currentStatus shouldBe CandidateStatus.APPROVED
                }

                it("이미 거절된 후보는 AlreadyReviewed를 반환한다") {
                    val candidate = createAndSaveCandidate("테스트", BigDecimal("0.5"), CandidateStatus.REJECTED)

                    val result =
                        reviewService.approve(
                            candidateId = candidate.id!!,
                            reviewer = "admin",
                            definition = "정의",
                        )

                    result.shouldBeInstanceOf<CandidateReviewResult.AlreadyReviewed>()
                    result.currentStatus shouldBe CandidateStatus.REJECTED
                }

                it("이미 등록된 용어와 같은 이름이면 TermAlreadyExists를 반환한다") {
                    glossaryService.addTerm("공유 주차장", "기존 정의")
                    val candidate = createAndSaveCandidate("공유 주차장", BigDecimal("0.5"))

                    val result =
                        reviewService.approve(
                            candidateId = candidate.id!!,
                            reviewer = "admin",
                            definition = "새 정의",
                        )

                    result.shouldBeInstanceOf<CandidateReviewResult.TermAlreadyExists>()
                    result.term shouldBe "공유 주차장"
                }

                it("동의어로 등록된 용어와 같은 이름이면 TermAlreadyExistsAsSynonym을 반환한다") {
                    glossaryService.addTerm("주차장", "주차장 정의")
                    synonymService.addSynonym("주차장", "공유 주차장")
                    val candidate = createAndSaveCandidate("공유 주차장", BigDecimal("0.5"))

                    val result =
                        reviewService.approve(
                            candidateId = candidate.id!!,
                            reviewer = "admin",
                            definition = "정의",
                        )

                    result.shouldBeInstanceOf<CandidateReviewResult.TermAlreadyExistsAsSynonym>()
                    result.term shouldBe "공유 주차장"
                }

                it("무시된 용어와 같은 이름이면 TermAlreadyExistsAsIgnored를 반환한다") {
                    ignoredTermService.addIgnoredTerm("공유 주차장", "무시 사유")
                    val candidate = createAndSaveCandidate("공유 주차장", BigDecimal("0.5"))

                    val result =
                        reviewService.approve(
                            candidateId = candidate.id!!,
                            reviewer = "admin",
                            definition = "정의",
                        )

                    result.shouldBeInstanceOf<CandidateReviewResult.TermAlreadyExistsAsIgnored>()
                    result.term shouldBe "공유 주차장"
                }

                it("기존 용어를 포함하면 ConflictWithExistingTerms를 반환한다") {
                    glossaryService.addTerm("주차장", "주차장 정의")
                    val candidate = createAndSaveCandidate("공유 주차장", BigDecimal("0.5"))

                    val result =
                        reviewService.approve(
                            candidateId = candidate.id!!,
                            reviewer = "admin",
                            definition = "정의",
                        )

                    result.shouldBeInstanceOf<CandidateReviewResult.ConflictWithExistingTerms>()
                    result.term shouldBe "공유 주차장"
                    result.conflictingTerms shouldBe listOf("주차장")
                }
            }

            describe("reject") {
                it("PENDING 상태의 후보를 거절할 수 있다") {
                    val candidate = createAndSaveCandidate("공유 주차장", BigDecimal("0.5"))

                    val result = reviewService.reject(candidate.id!!, "admin")

                    result.shouldBeInstanceOf<CandidateReviewResult.Rejected>()
                    result.candidate.status shouldBe CandidateStatus.REJECTED
                    result.candidate.reviewedBy shouldBe "admin"
                    result.candidate.reviewedAt shouldNotBe null
                }

                it("존재하지 않는 후보는 NotFound를 반환한다") {
                    val result = reviewService.reject(99999L, "admin")

                    result.shouldBeInstanceOf<CandidateReviewResult.NotFound>()
                    result.candidateId shouldBe 99999L
                }

                it("이미 승인된 후보는 AlreadyReviewed를 반환한다") {
                    val candidate = createAndSaveCandidate("테스트", BigDecimal("0.5"), CandidateStatus.APPROVED)

                    val result = reviewService.reject(candidate.id!!, "admin")

                    result.shouldBeInstanceOf<CandidateReviewResult.AlreadyReviewed>()
                    result.currentStatus shouldBe CandidateStatus.APPROVED
                }

                it("이미 거절된 후보는 AlreadyReviewed를 반환한다") {
                    val candidate = createAndSaveCandidate("테스트", BigDecimal("0.5"), CandidateStatus.REJECTED)

                    val result = reviewService.reject(candidate.id!!, "admin")

                    result.shouldBeInstanceOf<CandidateReviewResult.AlreadyReviewed>()
                    result.currentStatus shouldBe CandidateStatus.REJECTED
                }
            }

            describe("getStatistics") {
                it("상태별 후보 수를 반환한다") {
                    createAndSaveCandidate("대기1", BigDecimal("0.5"), CandidateStatus.PENDING)
                    createAndSaveCandidate("대기2", BigDecimal("0.5"), CandidateStatus.PENDING)
                    createAndSaveCandidate("승인", BigDecimal("0.5"), CandidateStatus.APPROVED)
                    createAndSaveCandidate("거절1", BigDecimal("0.5"), CandidateStatus.REJECTED)
                    createAndSaveCandidate("거절2", BigDecimal("0.5"), CandidateStatus.REJECTED)
                    createAndSaveCandidate("거절3", BigDecimal("0.5"), CandidateStatus.REJECTED)

                    val stats = reviewService.getStatistics()

                    stats.pending shouldBe 2
                    stats.approved shouldBe 1
                    stats.rejected shouldBe 3
                    stats.total shouldBe 6
                }

                it("후보가 없으면 모두 0을 반환한다") {
                    val stats = reviewService.getStatistics()

                    stats.pending shouldBe 0
                    stats.approved shouldBe 0
                    stats.rejected shouldBe 0
                    stats.total shouldBe 0
                }
            }
        }
    }

    private fun createAndSaveCandidate(
        term: String,
        relevanceScore: BigDecimal,
        status: CandidateStatus = CandidateStatus.PENDING,
    ): TermCandidate {
        val candidate =
            TermCandidate(
                term = term,
                components = "[\"$term\"]",
                count = 10,
                pmi = BigDecimal("3.5"),
                npmi = BigDecimal("0.7"),
                docCount = 5,
                idf = BigDecimal("2.3"),
                avgTfidf = BigDecimal("0.5"),
                relevanceScore = relevanceScore,
                status = status,
            )
        return termCandidateRepository.save(candidate)
    }
}
