package com.mkroo.termbase.infrastructure.slack

import com.mkroo.termbase.domain.model.slack.SlackMessage
import com.mkroo.termbase.infrastructure.config.SlackProperties
import com.mkroo.termbase.infrastructure.slack.dto.SlackApiMessage
import com.mkroo.termbase.infrastructure.slack.dto.SlackConversationsHistoryResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SlackApiClient(
    private val slackProperties: SlackProperties,
    private val restClient: RestClient = RestClient.create(),
) {
    companion object {
        private const val BASE_URL = "https://slack.com/api"
        private const val DEFAULT_LIMIT = 200
    }

    fun fetchConversationsHistory(
        channelId: String,
        oldest: String? = null,
        latest: String? = null,
        limit: Int = DEFAULT_LIMIT,
        cursor: String? = null,
    ): SlackConversationsHistoryResponse {
        val uri =
            buildString {
                append("$BASE_URL/conversations.history?channel=$channelId&limit=$limit")
                oldest?.let { append("&oldest=$it") }
                latest?.let { append("&latest=$it") }
                cursor?.let { append("&cursor=$it") }
            }

        return restClient
            .get()
            .uri(uri)
            .header("Authorization", "Bearer ${slackProperties.botToken}")
            .retrieve()
            .body(SlackConversationsHistoryResponse::class.java)
            ?: SlackConversationsHistoryResponse(ok = false, error = "Empty response")
    }

    fun fetchAllMessages(
        channelId: String,
        workspaceId: String,
        oldest: String? = null,
        latest: String? = null,
    ): List<SlackMessage> {
        val allMessages = mutableListOf<SlackMessage>()
        var cursor: String? = null

        do {
            val response = fetchConversationsHistory(channelId, oldest, latest, DEFAULT_LIMIT, cursor)

            if (!response.ok) {
                throw SlackApiException("Slack API error: ${response.error}")
            }

            val userMessages =
                response.messages
                    ?.filter { it.isUserMessage() }
                    ?.map { it.toSlackMessage(workspaceId, channelId) }
                    ?: emptyList()

            allMessages.addAll(userMessages)
            cursor = response.responseMetadata?.nextCursor?.takeIf { it.isNotBlank() }
        } while (cursor != null)

        return allMessages
    }

    private fun SlackApiMessage.toSlackMessage(
        workspaceId: String,
        channelId: String,
    ): SlackMessage =
        SlackMessage(
            workspaceId = workspaceId,
            channelId = channelId,
            messageTs = ts,
            userId = user!!,
            text = text!!,
            timestamp = SlackMessage.fromSlackTs(ts),
        )
}

class SlackApiException(
    message: String,
) : RuntimeException(message)
