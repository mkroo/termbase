package com.mkroo.termbase.domain.model.slack

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting
import java.time.Instant

@Document(indexName = "slack_messages")
@Setting(settingPath = "elasticsearch/slack-messages-settings.json")
data class SlackMessage(
    @Id
    val messageId: String,
    @Field(type = FieldType.Keyword)
    val channelId: String,
    @Field(type = FieldType.Keyword)
    val workspaceId: String,
    @Field(type = FieldType.Text, analyzer = "korean_analyzer", fielddata = true)
    val content: String,
    @Field(type = FieldType.Date)
    val timestamp: Instant,
)
