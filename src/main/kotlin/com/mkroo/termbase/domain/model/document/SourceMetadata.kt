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
)
sealed interface SourceMetadata {
    val source: String
}

data class SlackMetadata(
    override val source: String = "slack",
    val workspaceId: String,
    val channelId: String,
    val messageId: String,
    val userId: String,
) : SourceMetadata

data class GmailMetadata(
    override val source: String = "gmail",
    val messageId: String,
    val threadId: String,
    val from: String,
    val to: List<String>,
    val cc: List<String>,
    val subject: String,
) : SourceMetadata

data class WebhookMetadata(
    override val source: String = "webhook",
    val webhookId: String,
    val eventType: String,
) : SourceMetadata
