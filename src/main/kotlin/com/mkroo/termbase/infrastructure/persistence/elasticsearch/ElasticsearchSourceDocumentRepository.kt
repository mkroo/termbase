package com.mkroo.termbase.infrastructure.persistence.elasticsearch

import co.elastic.clients.elasticsearch._types.SortOrder
import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.document.SourceDocumentPage
import com.mkroo.termbase.domain.repository.SourceDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.stereotype.Repository
import kotlin.math.ceil

@Repository
class ElasticsearchSourceDocumentRepository(
    private val elasticsearchOperations: ElasticsearchOperations,
) : SourceDocumentRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_RETRIES = 5
        private const val INITIAL_DELAY_MS = 500L
    }

    // 인덱스 존재 여부 캐싱 (매 배치마다 체크하지 않도록)
    @Volatile
    private var indexInitialized = false

    override fun saveAll(documents: List<SourceDocument>): BulkInsertResult {
        ensureIndexExistsOnce()

        val indexQueries =
            documents.map { document ->
                IndexQueryBuilder()
                    .withId(document.id)
                    .withObject(document)
                    .build()
            }

        elasticsearchOperations.bulkIndex(indexQueries, SourceDocument::class.java)

        return BulkInsertResult(
            totalCount = documents.size,
            successCount = documents.size,
            failureCount = 0,
            failures = emptyList(),
        )
    }

    override fun findAll(
        page: Int,
        size: Int,
    ): SourceDocumentPage {
        if (!checkIndexExistsOnce()) {
            return SourceDocumentPage.empty(page, size)
        }

        val query =
            NativeQuery
                .builder()
                .withSort { s ->
                    s.field { f ->
                        f.field("timestamp").order(SortOrder.Desc)
                    }
                }.withPageable(
                    org.springframework.data.domain.PageRequest
                        .of(page, size),
                ).build()

        val searchHits = elasticsearchOperations.search(query, SourceDocument::class.java)

        val totalElements = searchHits.totalHits
        val totalPages = ceil(totalElements.toDouble() / size).toInt()

        return SourceDocumentPage(
            documents = searchHits.searchHits.map { it.content },
            totalElements = totalElements,
            totalPages = totalPages,
            currentPage = page,
            size = size,
        )
    }

    /**
     * 인덱스 존재 여부를 한 번만 확인하고 캐싱합니다.
     * 인덱스가 없으면 생성합니다.
     */
    private fun ensureIndexExistsOnce() {
        if (indexInitialized) return

        synchronized(this) {
            if (indexInitialized) return

            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (!withRetry { indexOps.exists() }) {
                withRetry { indexOps.create() }
                withRetry { indexOps.putMapping() }
            }
            indexInitialized = true
        }
    }

    /**
     * 인덱스 존재 여부를 한 번만 확인하고 캐싱합니다.
     * 인덱스가 없어도 생성하지 않고 false를 반환합니다.
     */
    private fun checkIndexExistsOnce(): Boolean {
        if (indexInitialized) return true

        synchronized(this) {
            if (indexInitialized) return true

            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (withRetry { indexOps.exists() }) {
                indexInitialized = true
                return true
            }
            return false
        }
    }

    /**
     * 인덱스 캐시를 리셋합니다. (테스트용)
     */
    internal fun resetIndexCache() {
        indexInitialized = false
    }

    /**
     * 429 오류 발생 시 지수 백오프로 재시도합니다.
     */
    private fun <T> withRetry(action: () -> T): T {
        var lastException: Exception? = null
        var delay = INITIAL_DELAY_MS

        repeat(MAX_RETRIES) { attempt ->
            try {
                return action()
            } catch (e: DataAccessResourceFailureException) {
                if (e.message?.contains("429") == true) {
                    lastException = e
                    log.warn("ES 429 오류 발생, {}번째 재시도 (delay: {}ms)", attempt + 1, delay)
                    Thread.sleep(delay)
                    delay *= 2
                } else {
                    throw e
                }
            }
        }

        throw lastException ?: IllegalStateException("Retry failed without exception")
    }
}
