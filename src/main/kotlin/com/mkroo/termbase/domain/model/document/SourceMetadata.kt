package com.mkroo.termbase.domain.model.document

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "source",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = SlackMetadata::class, name = "slack"),
    JsonSubTypes.Type(value = GmailMetadata::class, name = "gmail"),
    JsonSubTypes.Type(value = WebhookMetadata::class, name = "webhook"),
    JsonSubTypes.Type(value = ConfluenceMetadata::class, name = "confluence"),
)
sealed interface SourceMetadata {
    val source: String

    fun generateDocumentId(): String
}

data class SlackMetadata(
    override val source: String = "slack",
    val workspaceId: String,
    val channelId: String,
    val messageId: String,
    val userId: String,
) : SourceMetadata {
    override fun generateDocumentId(): String = "$source:$workspaceId:$channelId:$messageId"
}

data class GmailMetadata(
    override val source: String = "gmail",
    val messageId: String,
    val threadId: String,
    val from: String,
    val to: List<String>,
    val cc: List<String>,
    val subject: String,
) : SourceMetadata {
    override fun generateDocumentId(): String = "$source:$messageId"
}

data class WebhookMetadata(
    override val source: String = "webhook",
    val webhookId: String,
    val eventType: String,
) : SourceMetadata {
    override fun generateDocumentId(): String = "$source:$webhookId:$eventType"
}

data class ConfluenceMetadata(
    override val source: String = "confluence",
    val cloudId: String,
    val spaceKey: String,
    val pageId: String,
    val pageTitle: String,
) : SourceMetadata {
    override fun generateDocumentId(): String = "$source:$cloudId:$pageId"
}
