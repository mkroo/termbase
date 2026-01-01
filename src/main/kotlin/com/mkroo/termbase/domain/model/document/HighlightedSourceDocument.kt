package com.mkroo.termbase.domain.model.document

import java.time.Instant

data class HighlightedSourceDocument(
    val id: String,
    val content: String,
    val highlightedContent: String,
    val timestamp: Instant,
)
