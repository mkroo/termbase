package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.document.SlackMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TermCandidateServiceTest : DescribeSpec() {
    @Autowired
    private lateinit var termCandidateService: TermCandidateService

    @Autowired
    private lateinit var glossaryService: GlossaryService

    @Autowired
    private lateinit var synonymService: SynonymService

    @Autowired
    private lateinit var elasticsearchOperations: ElasticsearchOperations

    init {
        extension(SpringExtension())

        beforeEach {
            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
            indexOps.createWithMapping()
        }

        afterEach {
            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
        }

        describe("TermCandidateService") {
            describe("getTermCandidates") {
                it("등록된 용어를 제외한 빈도 높은 용어를 반환한다") {
                    // Given: 문서에 API, SDK, REST가 포함
                    indexDocuments(
                        createDocument("1", "API API API SDK SDK REST"),
                    )
                    glossaryService.addTerm("API", "Application Programming Interface")

                    // When
                    val result = termCandidateService.getTermCandidates()

                    // Then: API는 제외되고 SDK, REST만 반환
                    result.map { it.term.lowercase() } shouldContainAll listOf("sdk", "rest")
                    result.none { it.term.lowercase() == "api" } shouldBe true
                }

                it("동의어로 등록된 용어도 제외한다") {
                    // Given: 문서에 API, 인터페이스, REST가 포함
                    indexDocuments(
                        createDocument("1", "API API API 인터페이스 인터페이스 REST"),
                    )
                    glossaryService.addTerm("API", "Application Programming Interface")
                    synonymService.addSynonym("API", "인터페이스")

                    // When
                    val result = termCandidateService.getTermCandidates()

                    // Then: API와 인터페이스는 제외되고 REST만 반환
                    result.map { it.term.lowercase() } shouldContainAll listOf("rest")
                    result.none { it.term.lowercase() == "api" } shouldBe true
                    result.none { it.term == "인터페이스" } shouldBe true
                }

                it("등록된 용어가 없으면 모든 빈도 용어를 반환한다") {
                    // Given: 문서에 API, SDK가 포함되지만 등록된 용어 없음
                    indexDocuments(
                        createDocument("1", "API API SDK"),
                    )

                    // When
                    val result = termCandidateService.getTermCandidates()

                    // Then: 모든 용어 반환
                    result.map { it.term.lowercase() } shouldContainAll listOf("api", "sdk")
                }

                it("지정된 size만큼만 반환한다") {
                    // Given: 문서에 여러 용어 포함
                    indexDocuments(
                        createDocument("1", "API API API SDK SDK REST"),
                    )

                    // When
                    val result = termCandidateService.getTermCandidates(size = 2)

                    // Then
                    result shouldHaveSize 2
                }
            }

            describe("searchTermCandidates") {
                it("검색어가 포함된 용어만 반환한다") {
                    // Given
                    indexDocuments(
                        createDocument("1", "API Application REST"),
                    )

                    // When
                    val result = termCandidateService.searchTermCandidates("A")

                    // Then
                    result.map { it.term.lowercase() } shouldContainAll listOf("api", "application")
                    result.none { it.term.lowercase() == "rest" } shouldBe true
                }

                it("대소문자를 구분하지 않는다") {
                    // Given
                    indexDocuments(
                        createDocument("1", "API"),
                    )

                    // When
                    val resultLower = termCandidateService.searchTermCandidates("api")
                    val resultUpper = termCandidateService.searchTermCandidates("API")

                    // Then: 대소문자 구분 없이 검색됨
                    resultLower.any { it.term.lowercase() == "api" } shouldBe true
                    resultUpper.any { it.term.lowercase() == "api" } shouldBe true
                }

                it("검색 결과가 없으면 빈 리스트를 반환한다") {
                    // Given
                    indexDocuments(
                        createDocument("1", "API"),
                    )

                    // When
                    val result = termCandidateService.searchTermCandidates("없는검색어")

                    // Then
                    result.shouldBeEmpty()
                }
            }
        }
    }

    private fun createDocument(
        id: String,
        content: String,
    ): SourceDocument =
        SourceDocument(
            id = id,
            content = content,
            metadata =
                SlackMetadata(
                    workspaceId = "T123456",
                    channelId = "C789012",
                    messageId = "msg-$id",
                    userId = "U456789",
                ),
            timestamp = Instant.now(),
        )

    private fun indexDocuments(vararg documents: SourceDocument) {
        documents.forEach { elasticsearchOperations.save(it) }
        elasticsearchOperations.indexOps(SourceDocument::class.java).refresh()
    }
}
