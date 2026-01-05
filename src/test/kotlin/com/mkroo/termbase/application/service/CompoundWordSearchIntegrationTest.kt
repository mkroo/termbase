package com.mkroo.termbase.application.service

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.document.SlackMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.term.Term
import com.mkroo.termbase.domain.repository.TermRepository
import com.mkroo.termbase.domain.service.SourceDocumentAnalyzer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

/**
 * 띄어쓰기 합성어 검색 통합 테스트 (US-11)
 *
 * 이 테스트는 Elasticsearch의 synonym filter를 사용하여
 * 띄어쓰기가 포함된 합성어가 올바르게 검색되는지 확인합니다.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
class CompoundWordSearchIntegrationTest : DescribeSpec() {
    @Autowired
    private lateinit var reindexingService: ReindexingService

    @Autowired
    private lateinit var sourceDocumentService: SourceDocumentService

    @Autowired
    private lateinit var sourceDocumentAnalyzer: SourceDocumentAnalyzer

    @Autowired
    private lateinit var termRepository: TermRepository

    @Autowired
    private lateinit var elasticsearchTemplate: ElasticsearchTemplate

    init {
        extension(SpringExtension())

        beforeEach {
            cleanupIndices()
            cleanupDatabase()
        }

        afterEach {
            cleanupIndices()
            cleanupDatabase()
        }

        describe("띄어쓰기 합성어 검색 (US-11)") {
            context("용어 사전에 띄어쓰기가 포함된 합성어가 등록된 경우") {
                it("띄어쓰기가 있는 검색어로 검색하면 해당 합성어를 포함하는 문서를 찾는다") {
                    // Given: 띄어쓰기가 포함된 용어 등록
                    termRepository.save(Term(name = "공유 주차장", definition = "여러 사람이 함께 사용하는 주차장"))

                    // 재인덱싱하여 synonym filter 적용
                    reindexingService.reindex()

                    // 문서 인덱싱
                    val documents =
                        listOf(
                            createSourceDocument("doc-001", "공유 주차장에서 만나요"),
                            createSourceDocument("doc-002", "공유 자전거 대여"),
                            createSourceDocument("doc-003", "주차장 위치 알려주세요"),
                        )
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchTemplate.indexOps(SourceDocument::class.java).refresh()

                    // When: 띄어쓰기가 있는 검색어로 검색
                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("공유 주차장", 10)

                    // Then: "공유 주차장"을 포함하는 문서만 반환
                    result.shouldHaveSize(1)
                    result.first().id shouldBe "doc-001"
                }

                it("띄어쓰기가 없는 검색어로 검색해도 해당 합성어를 포함하는 문서를 찾는다") {
                    // Given: 띄어쓰기가 포함된 용어 등록
                    termRepository.save(Term(name = "공유 주차장", definition = "여러 사람이 함께 사용하는 주차장"))

                    // 재인덱싱하여 synonym filter 적용
                    reindexingService.reindex()

                    // 문서 인덱싱
                    val documents =
                        listOf(
                            createSourceDocument("doc-001", "공유 주차장에서 만나요"),
                            createSourceDocument("doc-002", "공유 자전거 대여"),
                            createSourceDocument("doc-003", "주차장 위치 알려주세요"),
                        )
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchTemplate.indexOps(SourceDocument::class.java).refresh()

                    // When: 띄어쓰기가 없는 검색어로 검색
                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("공유주차장", 10)

                    // Then: "공유 주차장"을 포함하는 문서를 찾음
                    result.shouldHaveSize(1)
                    result.first().id shouldBe "doc-001"
                }

                it("띄어쓰기 유무와 관계없이 동일한 빈도수를 반환한다") {
                    // Given: 띄어쓰기가 포함된 용어 등록
                    termRepository.save(Term(name = "삼성 전자", definition = "대한민국의 대표 기업"))

                    // 재인덱싱하여 synonym filter 적용
                    reindexingService.reindex()

                    // 문서 인덱싱
                    val documents =
                        listOf(
                            createSourceDocument("doc-001", "삼성 전자 주가 상승"),
                            createSourceDocument("doc-002", "삼성 전자 신제품 출시"),
                            createSourceDocument("doc-003", "현대자동차 뉴스"),
                        )
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchTemplate.indexOps(SourceDocument::class.java).refresh()

                    // When: 띄어쓰기 있는 검색어와 없는 검색어로 각각 검색
                    val resultWithSpace = sourceDocumentAnalyzer.getTermFrequency("삼성 전자")
                    val resultWithoutSpace = sourceDocumentAnalyzer.getTermFrequency("삼성전자")

                    // Then: 동일한 빈도수 반환
                    resultWithSpace shouldBe 2
                    resultWithoutSpace shouldBe 2
                }
            }

            context("동의어와 함께 띄어쓰기 합성어가 등록된 경우") {
                it("동의어로도 검색이 가능하다") {
                    // Given: 띄어쓰기가 포함된 용어와 동의어 등록
                    val term = termRepository.save(Term(name = "삼성 전자", definition = "대한민국의 대표 기업"))
                    term.addSynonym("삼전")
                    termRepository.save(term)

                    // 재인덱싱하여 synonym filter 적용
                    reindexingService.reindex()

                    // 문서 인덱싱
                    val documents =
                        listOf(
                            createSourceDocument("doc-001", "삼성 전자 주가 상승"),
                            createSourceDocument("doc-002", "삼전 실적 발표"),
                            createSourceDocument("doc-003", "현대자동차 뉴스"),
                        )
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchTemplate.indexOps(SourceDocument::class.java).refresh()

                    // When: 대표어로 검색
                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("삼성 전자", 10)

                    // Then: 대표어와 동의어 포함 문서 모두 반환
                    result.shouldHaveSize(2)
                    result.map { it.id }.toSet() shouldBe setOf("doc-001", "doc-002")
                }

                it("띄어쓰기가 포함된 동의어도 올바르게 처리된다") {
                    // Given: 용어와 띄어쓰기 포함 동의어 등록
                    val term = termRepository.save(Term(name = "공유 주차장", definition = "여러 사람이 함께 사용하는 주차장"))
                    term.addSynonym("공유 파킹")
                    termRepository.save(term)

                    // 재인덱싱하여 synonym filter 적용
                    reindexingService.reindex()

                    // 문서 인덱싱
                    val documents =
                        listOf(
                            createSourceDocument("doc-001", "공유 주차장에서 만나요"),
                            createSourceDocument("doc-002", "공유 파킹 서비스 이용"),
                            createSourceDocument("doc-003", "일반 주차장 이용"),
                        )
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchTemplate.indexOps(SourceDocument::class.java).refresh()

                    // When: 대표어로 검색
                    val result = sourceDocumentAnalyzer.searchDocumentsByTerm("공유 주차장", 10)

                    // Then: 대표어와 동의어 포함 문서 모두 반환
                    result.shouldHaveSize(2)
                    result.map { it.id }.toSet() shouldBe setOf("doc-001", "doc-002")
                }
            }

            context("여러 띄어쓰기 합성어가 등록된 경우") {
                it("각 합성어별로 정확하게 검색된다") {
                    // Given: 여러 띄어쓰기 합성어 등록
                    termRepository.save(Term(name = "삼성 전자", definition = "대한민국의 대표 기업"))
                    termRepository.save(Term(name = "현대 자동차", definition = "자동차 제조사"))

                    // 재인덱싱하여 synonym filter 적용
                    reindexingService.reindex()

                    // 문서 인덱싱
                    val documents =
                        listOf(
                            createSourceDocument("doc-001", "삼성 전자 주가 상승"),
                            createSourceDocument("doc-002", "현대 자동차 신차 출시"),
                            createSourceDocument("doc-003", "전자 부품 시장"),
                            createSourceDocument("doc-004", "자동차 보험 가입"),
                        )
                    sourceDocumentService.bulkInsert(documents)
                    elasticsearchTemplate.indexOps(SourceDocument::class.java).refresh()

                    // When: 각 합성어로 검색
                    val samsungResult = sourceDocumentAnalyzer.searchDocumentsByTerm("삼성 전자", 10)
                    val hyundaiResult = sourceDocumentAnalyzer.searchDocumentsByTerm("현대 자동차", 10)

                    // Then: 각각 해당 합성어만 포함된 문서 반환
                    samsungResult.shouldHaveSize(1)
                    samsungResult.first().id shouldBe "doc-001"

                    hyundaiResult.shouldHaveSize(1)
                    hyundaiResult.first().id shouldBe "doc-002"
                }
            }
        }
    }

    private fun cleanupIndices() {
        try {
            elasticsearchTemplate.execute { client ->
                try {
                    client.indices().deleteAlias {
                        it.index("source_documents*").name("source_documents")
                    }
                } catch (_: Exception) {
                }
                try {
                    client.indices().delete { it.index("source_documents*") }
                } catch (_: Exception) {
                }
                Unit
            }
        } catch (_: Exception) {
        }
    }

    private fun cleanupDatabase() {
        termRepository.findAll().forEach { termRepository.delete(it) }
    }

    private fun createSourceDocument(
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
}
