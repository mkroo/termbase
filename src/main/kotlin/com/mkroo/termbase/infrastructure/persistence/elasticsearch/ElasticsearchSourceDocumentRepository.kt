package com.mkroo.termbase.infrastructure.persistence.elasticsearch

import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.repository.SourceDocumentRepository
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.stereotype.Repository

@Repository
class ElasticsearchSourceDocumentRepository(
    private val elasticsearchOperations: ElasticsearchOperations,
) : SourceDocumentRepository {
    override fun saveAll(documents: List<SourceDocument>): BulkInsertResult {
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
}
