package com.mkroo.termbase.infrastructure.slack.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class SlackConversationsHistoryResponse(
    val ok: Boolean,
    val messages: List<SlackApiMessage>? = null,
    val error: String? = null,
    @JsonProperty("has_more")
    val hasMore: Boolean = false,
    @JsonProperty("response_metadata")
    val responseMetadata: ResponseMetadata? = null,
)

data class SlackApiMessage(
    val type: String,
    val user: String? = null,
    val text: String? = null,
    val ts: String,
    val subtype: String? = null,
    @JsonProperty("bot_id")
    val botId: String? = null,
) {
    fun isUserMessage(): Boolean = type == "message" && subtype == null && botId == null && user != null && text != null
}

data class ResponseMetadata(
    @JsonProperty("next_cursor")
    val nextCursor: String? = null,
)

data class SlackAuthTestResponse(
    val ok: Boolean,
    val error: String? = null,
    @JsonProperty("team_id")
    val teamId: String? = null,
    val team: String? = null,
    @JsonProperty("user_id")
    val userId: String? = null,
    val user: String? = null,
)

data class SlackConversationsListResponse(
    val ok: Boolean,
    val error: String? = null,
    val channels: List<SlackChannel>? = null,
    @JsonProperty("response_metadata")
    val responseMetadata: ResponseMetadata? = null,
)

data class SlackChannel(
    val id: String,
    val name: String,
    @JsonProperty("is_private")
    val isPrivate: Boolean = false,
    @JsonProperty("is_member")
    val isMember: Boolean = false,
)
