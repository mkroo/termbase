package com.mkroo.termbase.infrastructure.slack

import com.mkroo.termbase.domain.model.slack.SlackMessage
import com.mkroo.termbase.infrastructure.config.SlackProperties
import com.mkroo.termbase.infrastructure.slack.dto.SlackApiMessage
import com.mkroo.termbase.infrastructure.slack.dto.SlackAuthTestResponse
import com.mkroo.termbase.infrastructure.slack.dto.SlackConversationsHistoryResponse
import com.mkroo.termbase.infrastructure.slack.dto.SlackConversationsListResponse
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

    fun authTest(botToken: String): SlackAuthTestResponse =
        restClient
            .get()
            .uri("$BASE_URL/auth.test")
            .header("Authorization", "Bearer $botToken")
            .retrieve()
            .body(SlackAuthTestResponse::class.java)
            ?: SlackAuthTestResponse(ok = false, error = "Empty response")

    fun conversationsList(
        botToken: String,
        cursor: String? = null,
    ): SlackConversationsListResponse {
        val uri =
            buildString {
                append("$BASE_URL/conversations.list?types=public_channel&exclude_archived=true&limit=1000")
                cursor?.let { append("&cursor=$it") }
            }
        return restClient
            .get()
            .uri(uri)
            .header("Authorization", "Bearer $botToken")
            .retrieve()
            .body(SlackConversationsListResponse::class.java)
            ?: SlackConversationsListResponse(ok = false, error = "Empty response")
    }

    fun fetchAllChannels(botToken: String): SlackConversationsListResponse {
        val allChannels = mutableListOf<com.mkroo.termbase.infrastructure.slack.dto.SlackChannel>()
        var cursor: String? = null

        do {
            val response = conversationsList(botToken, cursor)
            if (!response.ok) {
                return response
            }
            response.channels?.let { allChannels.addAll(it) }
            cursor = response.responseMetadata?.nextCursor?.takeIf { it.isNotBlank() }
        } while (cursor != null)

        return SlackConversationsListResponse(ok = true, channels = allChannels)
    }

    fun fetchConversationsHistory(
        channelId: String,
        oldest: String? = null,
        latest: String? = null,
        limit: Int = DEFAULT_LIMIT,
        cursor: String? = null,
    ): SlackConversationsHistoryResponse = fetchConversationsHistory(channelId, oldest, latest, limit, cursor, slackProperties.botToken)

    fun fetchConversationsHistory(
        channelId: String,
        oldest: String? = null,
        latest: String? = null,
        limit: Int = DEFAULT_LIMIT,
        cursor: String? = null,
        botToken: String,
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
            .header("Authorization", "Bearer $botToken")
            .retrieve()
            .body(SlackConversationsHistoryResponse::class.java)
            ?: SlackConversationsHistoryResponse(ok = false, error = "Empty response")
    }

    fun fetchAllMessages(
        channelId: String,
        workspaceId: String,
        oldest: String? = null,
        latest: String? = null,
    ): List<SlackMessage> = fetchAllMessages(channelId, workspaceId, oldest, latest, slackProperties.botToken)

    fun fetchAllMessages(
        channelId: String,
        workspaceId: String,
        oldest: String? = null,
        latest: String? = null,
        botToken: String,
    ): List<SlackMessage> {
        val allMessages = mutableListOf<SlackMessage>()
        var cursor: String? = null

        do {
            val response = fetchConversationsHistory(channelId, oldest, latest, DEFAULT_LIMIT, cursor, botToken)

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
