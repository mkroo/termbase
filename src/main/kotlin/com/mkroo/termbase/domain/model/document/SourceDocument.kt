package com.mkroo.termbase.domain.model.document

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting
import java.time.Instant

@Document(indexName = "source_documents")
@Setting(settingPath = "elasticsearch/source-documents-settings.json")
data class SourceDocument(
    @Id
    val id: String,
    @Field(type = FieldType.Text, analyzer = "korean_analyzer", fielddata = true)
    val content: String,
    @Field(type = FieldType.Object)
    val metadata: SourceMetadata,
    @Field(type = FieldType.Date)
    val timestamp: Instant,
)
