package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.candidate.CandidateStatus
import com.mkroo.termbase.domain.model.candidate.TermCandidate
import com.mkroo.termbase.domain.repository.TermCandidateRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
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
class TermCandidateServiceTest : DescribeSpec() {
    @Autowired
    private lateinit var termCandidateService: TermCandidateService

    @Autowired
    private lateinit var termCandidateRepository: TermCandidateRepository

    init {
        extension(SpringExtension())

        describe("TermCandidateService") {
            describe("getTermCandidates") {
                it("PENDING 상태의 후보만 반환한다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API", CandidateStatus.PENDING, relevanceScore = 0.8))
                    termCandidateRepository.save(createCandidate("SDK", CandidateStatus.APPROVED, relevanceScore = 0.9))
                    termCandidateRepository.save(createCandidate("REST", CandidateStatus.REJECTED, relevanceScore = 0.7))

                    // When
                    val result = termCandidateService.getTermCandidates()

                    // Then
                    result.totalElements shouldBe 1
                    result.content[0].term shouldBe "API"
                }

                it("relevanceScore 내림차순으로 정렬한다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API", relevanceScore = 0.5))
                    termCandidateRepository.save(createCandidate("SDK", relevanceScore = 0.9))
                    termCandidateRepository.save(createCandidate("REST", relevanceScore = 0.7))

                    // When
                    val result = termCandidateService.getTermCandidates()

                    // Then
                    result.content.map { it.term } shouldBe listOf("SDK", "REST", "API")
                }

                it("지정된 size만큼만 반환한다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API", relevanceScore = 0.9))
                    termCandidateRepository.save(createCandidate("SDK", relevanceScore = 0.8))
                    termCandidateRepository.save(createCandidate("REST", relevanceScore = 0.7))

                    // When
                    val result = termCandidateService.getTermCandidates(size = 2)

                    // Then
                    result.content.size shouldBe 2
                    result.content.map { it.term } shouldBe listOf("API", "SDK")
                    result.totalElements shouldBe 3
                }

                it("page 파라미터로 페이지를 이동할 수 있다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API", relevanceScore = 0.9))
                    termCandidateRepository.save(createCandidate("SDK", relevanceScore = 0.8))
                    termCandidateRepository.save(createCandidate("REST", relevanceScore = 0.7))

                    // When
                    val page0 = termCandidateService.getTermCandidates(page = 0, size = 2)
                    val page1 = termCandidateService.getTermCandidates(page = 1, size = 2)

                    // Then
                    page0.content.map { it.term } shouldBe listOf("API", "SDK")
                    page1.content.map { it.term } shouldBe listOf("REST")
                }

                it("후보가 없으면 빈 Page를 반환한다") {
                    // When
                    val result = termCandidateService.getTermCandidates()

                    // Then
                    result.content.isEmpty() shouldBe true
                    result.totalElements shouldBe 0
                }

                it("count를 올바르게 변환한다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API", count = 10))

                    // When
                    val result = termCandidateService.getTermCandidates()

                    // Then
                    result.content[0].count shouldBe 10L
                }

                it("score에 relevanceScore를 반환한다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API", relevanceScore = 0.75))

                    // When
                    val result = termCandidateService.getTermCandidates()

                    // Then
                    result.content[0].score shouldBe 0.75
                }

                it("hasNext와 hasPrevious를 올바르게 반환한다") {
                    // Given
                    repeat(5) { i ->
                        termCandidateRepository.save(createCandidate("Term$i", relevanceScore = 0.9 - i * 0.1))
                    }

                    // When
                    val page0 = termCandidateService.getTermCandidates(page = 0, size = 2)
                    val page1 = termCandidateService.getTermCandidates(page = 1, size = 2)
                    val page2 = termCandidateService.getTermCandidates(page = 2, size = 2)

                    // Then
                    page0.hasPrevious() shouldBe false
                    page0.hasNext() shouldBe true

                    page1.hasPrevious() shouldBe true
                    page1.hasNext() shouldBe true

                    page2.hasPrevious() shouldBe true
                    page2.hasNext() shouldBe false
                }
            }

            describe("searchTermCandidates") {
                it("검색어가 포함된 용어만 반환한다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API Gateway"))
                    termCandidateRepository.save(createCandidate("Application"))
                    termCandidateRepository.save(createCandidate("REST"))

                    // When
                    val result = termCandidateService.searchTermCandidates("A")

                    // Then
                    result.content.map { it.term } shouldContainAll listOf("API Gateway", "Application")
                    result.content.none { it.term == "REST" } shouldBe true
                }

                it("대소문자를 구분하지 않는다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API Gateway"))

                    // When
                    val resultLower = termCandidateService.searchTermCandidates("api")
                    val resultUpper = termCandidateService.searchTermCandidates("API")

                    // Then
                    resultLower.content.any { it.term == "API Gateway" } shouldBe true
                    resultUpper.content.any { it.term == "API Gateway" } shouldBe true
                }

                it("검색 결과가 없으면 빈 Page를 반환한다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API"))

                    // When
                    val result = termCandidateService.searchTermCandidates("없는검색어")

                    // Then
                    result.content.isEmpty() shouldBe true
                    result.totalElements shouldBe 0
                }

                it("지정된 size만큼만 반환한다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API 1", relevanceScore = 0.9))
                    termCandidateRepository.save(createCandidate("API 2", relevanceScore = 0.8))
                    termCandidateRepository.save(createCandidate("API 3", relevanceScore = 0.7))

                    // When
                    val result = termCandidateService.searchTermCandidates("API", size = 2)

                    // Then
                    result.content.size shouldBe 2
                    result.totalElements shouldBe 3
                }

                it("page 파라미터로 검색 결과를 페이지네이션할 수 있다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API 1", relevanceScore = 0.9))
                    termCandidateRepository.save(createCandidate("API 2", relevanceScore = 0.8))
                    termCandidateRepository.save(createCandidate("API 3", relevanceScore = 0.7))

                    // When
                    val page0 = termCandidateService.searchTermCandidates("API", page = 0, size = 2)
                    val page1 = termCandidateService.searchTermCandidates("API", page = 1, size = 2)

                    // Then
                    page0.content.map { it.term } shouldBe listOf("API 1", "API 2")
                    page1.content.map { it.term } shouldBe listOf("API 3")
                }

                it("PENDING 상태의 후보만 검색한다") {
                    // Given
                    termCandidateRepository.save(createCandidate("API 1", CandidateStatus.PENDING))
                    termCandidateRepository.save(createCandidate("API 2", CandidateStatus.APPROVED))
                    termCandidateRepository.save(createCandidate("API 3", CandidateStatus.REJECTED))

                    // When
                    val result = termCandidateService.searchTermCandidates("API")

                    // Then
                    result.totalElements shouldBe 1
                    result.content[0].term shouldBe "API 1"
                }
            }
        }
    }

    private fun createCandidate(
        term: String,
        status: CandidateStatus = CandidateStatus.PENDING,
        count: Int = 5,
        relevanceScore: Double = 0.5,
    ): TermCandidate =
        TermCandidate(
            term = term,
            components = """["$term"]""",
            count = count,
            pmi = BigDecimal("0.5"),
            npmi = BigDecimal("0.3"),
            docCount = 3,
            idf = BigDecimal("1.5"),
            avgTfidf = BigDecimal("0.1"),
            relevanceScore = BigDecimal.valueOf(relevanceScore),
            status = status,
        )
}
