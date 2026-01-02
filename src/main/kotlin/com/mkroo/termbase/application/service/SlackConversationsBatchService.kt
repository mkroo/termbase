package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.document.BulkInsertResult
import com.mkroo.termbase.domain.model.slack.SlackCollectionCheckpoint
import com.mkroo.termbase.domain.repository.SlackCollectionCheckpointRepository
import com.mkroo.termbase.infrastructure.slack.SlackApiClient
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class SlackConversationsBatchService(
    private val slackApiClient: SlackApiClient,
    private val sourceDocumentService: SourceDocumentService,
    private val checkpointRepository: SlackCollectionCheckpointRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun collectMessages(
        workspaceId: String,
        channelId: String,
        oldest: String? = null,
        latest: String? = null,
    ): BulkInsertResult {
        val messages = slackApiClient.fetchAllMessages(channelId, workspaceId, oldest, latest)

        if (messages.isEmpty()) {
            return BulkInsertResult.empty()
        }

        val documents = messages.map { it.toSourceDocument() }
        return sourceDocumentService.bulkInsert(documents)
    }

    fun collectMessagesWithToken(
        botToken: String,
        workspaceId: String,
        channelId: String,
        oldest: String? = null,
        latest: String? = null,
    ): BulkInsertResult {
        val messages = slackApiClient.fetchAllMessages(channelId, workspaceId, oldest, latest, botToken)

        if (messages.isEmpty()) {
            return BulkInsertResult.empty()
        }

        val documents = messages.map { it.toSourceDocument() }
        return sourceDocumentService.bulkInsert(documents)
    }

    fun collectIncrementalMessages(
        workspaceId: String,
        channelId: String,
    ): BulkInsertResult {
        val checkpoint = checkpointRepository.findByChannelId(channelId)
        val oldest = checkpoint?.lastCollectedTs

        val messages = slackApiClient.fetchAllMessages(channelId, workspaceId, oldest)

        if (messages.isEmpty()) {
            return BulkInsertResult.empty()
        }

        val documents = messages.map { it.toSourceDocument() }
        val result = sourceDocumentService.bulkInsert(documents)

        val latestTs = messages.maxOf { it.messageTs }
        val now = Instant.now(clock)

        if (checkpoint != null) {
            checkpoint.updateCheckpoint(latestTs, now)
            checkpointRepository.save(checkpoint)
        } else {
            checkpointRepository.save(
                SlackCollectionCheckpoint(
                    channelId = channelId,
                    lastCollectedTs = latestTs,
                    lastCollectedAt = now,
                ),
            )
        }

        return result
    }
}
