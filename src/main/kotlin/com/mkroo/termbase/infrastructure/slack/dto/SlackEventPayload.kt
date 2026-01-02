package com.mkroo.termbase.infrastructure.slack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

data class SlackUrlVerification(
    val type: String,
    val token: String,
    val challenge: String,
)

data class SlackEventCallback(
    val type: String,
    val token: String,
    @JsonProperty("team_id")
    val teamId: String,
    val event: SlackEvent,
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = SlackMessageEvent::class, name = "message"),
)
sealed interface SlackEvent {
    val type: String
}

data class SlackMessageEvent(
    override val type: String = "message",
    val channel: String,
    val user: String? = null,
    val text: String? = null,
    val ts: String,
    @JsonProperty("team")
    val teamId: String? = null,
    val subtype: String? = null,
    @JsonProperty("bot_id")
    val botId: String? = null,
) : SlackEvent {
    fun isUserMessage(): Boolean = subtype == null && botId == null && user != null && text != null
}
