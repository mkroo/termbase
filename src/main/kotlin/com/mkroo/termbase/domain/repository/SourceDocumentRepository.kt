package com.mkroo.termbase.domain.repository

import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.document.SourceDocument

interface SourceDocumentRepository {
    fun saveAll(documents: List<SourceDocument>): BulkInsertResult
}
