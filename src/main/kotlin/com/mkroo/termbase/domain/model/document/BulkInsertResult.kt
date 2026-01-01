package com.mkroo.termbase.domain.model.document

data class BulkInsertResult(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val failures: List<BulkInsertFailure>,
) {
    companion object {
        fun empty() = BulkInsertResult(0, 0, 0, emptyList())
    }
}

data class BulkInsertFailure(
    val index: Int,
    val documentId: String?,
    val reason: String,
)
