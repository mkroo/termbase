package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.SlackConversationsBatchService
import com.mkroo.termbase.domain.model.document.BulkInsertResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/slack/batch")
class SlackBatchController(
    private val slackConversationsBatchService: SlackConversationsBatchService,
) {
    @PostMapping("/collect")
    fun collectMessages(
        @RequestBody request: CollectMessagesRequest,
    ): ResponseEntity<BatchCollectResponse> {
        val result =
            slackConversationsBatchService.collectMessages(
                workspaceId = request.workspaceId,
                channelId = request.channelId,
                oldest = request.oldest,
                latest = request.latest,
            )

        return ResponseEntity.ok(BatchCollectResponse.from(result))
    }

    @PostMapping("/collect/{channelId}/incremental")
    fun collectIncrementalMessages(
        @PathVariable channelId: String,
        @RequestBody request: IncrementalCollectRequest,
    ): ResponseEntity<BatchCollectResponse> {
        val result =
            slackConversationsBatchService.collectIncrementalMessages(
                workspaceId = request.workspaceId,
                channelId = channelId,
            )

        return ResponseEntity.ok(BatchCollectResponse.from(result))
    }
}

data class CollectMessagesRequest(
    val workspaceId: String,
    val channelId: String,
    val oldest: String? = null,
    val latest: String? = null,
)

data class IncrementalCollectRequest(
    val workspaceId: String,
)

data class BatchCollectResponse(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
) {
    companion object {
        fun from(result: BulkInsertResult): BatchCollectResponse =
            BatchCollectResponse(
                totalCount = result.totalCount,
                successCount = result.successCount,
                failureCount = result.failureCount,
            )
    }
}
