package com.mkroo.termbase.domain.repository

import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.document.SourceDocumentPage

interface SourceDocumentRepository {
    fun saveAll(documents: List<SourceDocument>): BulkInsertResult

    fun findAll(
        page: Int,
        size: Int,
    ): SourceDocumentPage
}
