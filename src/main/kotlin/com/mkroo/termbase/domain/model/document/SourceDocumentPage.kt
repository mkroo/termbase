package com.mkroo.termbase.domain.model.document

data class SourceDocumentPage(
    val documents: List<SourceDocument>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int,
    val size: Int,
) {
    val hasNext: Boolean get() = currentPage < totalPages - 1
    val hasPrevious: Boolean get() = currentPage > 0

    companion object {
        fun empty(
            page: Int = 0,
            size: Int = 20,
        ) = SourceDocumentPage(
            documents = emptyList(),
            totalElements = 0,
            totalPages = 0,
            currentPage = page,
            size = size,
        )
    }
}
