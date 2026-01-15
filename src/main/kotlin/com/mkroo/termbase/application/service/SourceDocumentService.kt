package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.document.SourceDocumentPage
import com.mkroo.termbase.domain.repository.SourceDocumentRepository
import org.springframework.stereotype.Service

@Service
class SourceDocumentService(
    private val sourceDocumentRepository: SourceDocumentRepository,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }

    fun bulkInsert(documents: List<SourceDocument>): BulkInsertResult {
        if (documents.isEmpty()) {
            return BulkInsertResult.empty()
        }
        return sourceDocumentRepository.saveAll(documents)
    }

    fun saveDocument(document: SourceDocument): BulkInsertResult = bulkInsert(listOf(document))

    fun getDocuments(
        page: Int = 0,
        size: Int = DEFAULT_PAGE_SIZE,
    ): SourceDocumentPage {
        val validPage = page.coerceAtLeast(0)
        val validSize = size.coerceIn(1, MAX_PAGE_SIZE)
        return sourceDocumentRepository.findAll(validPage, validSize)
    }
}
