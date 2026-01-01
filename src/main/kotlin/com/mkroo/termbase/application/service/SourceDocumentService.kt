package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.repository.SourceDocumentRepository
import org.springframework.stereotype.Service

@Service
class SourceDocumentService(
    private val sourceDocumentRepository: SourceDocumentRepository,
) {
    fun bulkInsert(documents: List<SourceDocument>): BulkInsertResult {
        if (documents.isEmpty()) {
            return BulkInsertResult.empty()
        }
        return sourceDocumentRepository.saveAll(documents)
    }
}
