package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.slack.SlackMessage
import com.mkroo.termbase.infrastructure.slack.dto.SlackMessageEvent
import org.springframework.stereotype.Service

@Service
class SlackEventService(
    private val sourceDocumentService: SourceDocumentService,
) {
    fun processMessageEvent(
        event: SlackMessageEvent,
        workspaceId: String,
    ): BulkInsertResult {
        if (!event.isUserMessage()) {
            return BulkInsertResult.empty()
        }

        val message =
            SlackMessage(
                workspaceId = workspaceId,
                channelId = event.channel,
                messageTs = event.ts,
                userId = event.user!!,
                text = event.text!!,
                timestamp = SlackMessage.fromSlackTs(event.ts),
            )

        return sourceDocumentService.bulkInsert(listOf(message.toSourceDocument()))
    }
}
