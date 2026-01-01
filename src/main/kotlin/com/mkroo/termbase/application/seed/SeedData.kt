package com.mkroo.termbase.application.seed

import com.mkroo.termbase.domain.model.document.SourceMetadata
import java.time.Instant

data class TermSeedData(
    val name: String,
    val definition: String,
    val synonyms: List<String> = emptyList(),
)

data class IgnoredTermSeedData(
    val name: String,
    val reason: String,
)

data class SourceDocumentSeedData(
    val id: String,
    val content: String,
    val metadata: SourceMetadata,
    val timestamp: Instant,
)
