package com.mkroo.termbase.infrastructure.persistence.elasticsearch

import co.elastic.clients.elasticsearch._types.SortOrder
import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.document.SourceDocumentPage
import com.mkroo.termbase.domain.repository.SourceDocumentRepository
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.stereotype.Repository
import kotlin.math.ceil

@Repository
class ElasticsearchSourceDocumentRepository(
    private val elasticsearchOperations: ElasticsearchOperations,
) : SourceDocumentRepository {
    override fun saveAll(documents: List<SourceDocument>): BulkInsertResult {
        ensureIndexExists()

        val indexQueries =
            documents.map { document ->
                val builder = IndexQueryBuilder().withObject(document)
                document.id?.let { builder.withId(it) }
                builder.build()
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
        val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
        if (!indexOps.exists()) {
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

    private fun ensureIndexExists() {
        val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
        if (!indexOps.exists()) {
            indexOps.create()
            indexOps.putMapping()
        }
    }
}
