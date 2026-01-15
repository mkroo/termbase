package com.mkroo.termbase.domain.model.confluence

import com.mkroo.termbase.domain.model.document.ConfluenceMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import java.time.Instant

data class ConfluencePage(
    val cloudId: String,
    val spaceKey: String,
    val pageId: String,
    val title: String,
    val content: String,
    val lastModified: Instant,
) {
    fun toSourceDocument(): SourceDocument {
        val metadata =
            ConfluenceMetadata(
                cloudId = cloudId,
                spaceKey = spaceKey,
                pageId = pageId,
                pageTitle = title,
            )
        return SourceDocument(
            id = metadata.generateDocumentId(),
            content = "$title\n\n$content",
            metadata = metadata,
            timestamp = lastModified,
        )
    }
}
