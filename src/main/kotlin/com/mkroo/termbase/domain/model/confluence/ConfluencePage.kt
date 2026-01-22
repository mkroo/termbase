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

    /**
     * ES 저장 시 예상 메모리 크기를 바이트 단위로 추정합니다.
     * UTF-8 인코딩 기준으로 한글은 3바이트, 영문은 1바이트로 계산됩니다.
     * 메타데이터 오버헤드로 약 500바이트를 추가합니다.
     */
    fun estimateSizeBytes(): Long {
        val contentBytes = content.toByteArray(Charsets.UTF_8).size.toLong()
        val titleBytes = title.toByteArray(Charsets.UTF_8).size.toLong()
        val metadataOverhead = 500L // cloudId, spaceKey, pageId, timestamp 등
        return contentBytes + titleBytes + metadataOverhead
    }
}
