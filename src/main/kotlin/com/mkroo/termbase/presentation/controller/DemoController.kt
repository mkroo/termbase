package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.ReindexingService
import com.mkroo.termbase.application.service.SlackConversationsBatchService
import com.mkroo.termbase.application.service.SourceDocumentService
import com.mkroo.termbase.infrastructure.slack.SlackApiClient
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@RequestMapping("/demo")
class DemoController(
    private val reindexingService: ReindexingService,
    private val slackApiClient: SlackApiClient,
    private val slackConversationsBatchService: SlackConversationsBatchService,
    private val sourceDocumentService: SourceDocumentService,
) {
    @GetMapping
    fun showDemoPage(): String = "demo/index"

    @PostMapping("/reindex")
    @ResponseBody
    fun reindex(): ResponseEntity<ReindexResponse> =
        try {
            val result = reindexingService.reindex()
            ResponseEntity.ok(
                ReindexResponse(
                    success = true,
                    previousIndex = result.previousIndex,
                    newIndex = result.newIndex,
                    documentCount = result.documentCount,
                    userDictionarySize = result.userDictionarySize,
                    synonymRulesSize = result.synonymRulesSize,
                ),
            )
        } catch (e: Exception) {
            ResponseEntity.ok(
                ReindexResponse(
                    success = false,
                    error = e.message ?: "재인덱싱 중 오류가 발생했습니다.",
                ),
            )
        }

    @GetMapping("/reindex/status")
    @ResponseBody
    fun getReindexStatus(): ResponseEntity<ReindexStatusResponse> =
        ResponseEntity.ok(
            ReindexStatusResponse(
                reindexingRequired = reindexingService.isReindexingRequired(),
            ),
        )

    @PostMapping("/slack/workspace-info")
    @ResponseBody
    fun getWorkspaceInfo(
        @RequestParam botToken: String,
    ): ResponseEntity<WorkspaceInfoResponse> =
        try {
            val authResponse = slackApiClient.authTest(botToken)
            if (!authResponse.ok) {
                ResponseEntity.ok(
                    WorkspaceInfoResponse(ok = false, error = authResponse.error ?: "인증 실패"),
                )
            } else {
                val channelsResponse = slackApiClient.fetchAllChannels(botToken)
                if (!channelsResponse.ok) {
                    ResponseEntity.ok(
                        WorkspaceInfoResponse(ok = false, error = channelsResponse.error ?: "채널 목록 조회 실패"),
                    )
                } else {
                    ResponseEntity.ok(
                        WorkspaceInfoResponse(
                            ok = true,
                            workspaceId = authResponse.teamId,
                            workspaceName = authResponse.team,
                            channels =
                                channelsResponse.channels?.map { channel ->
                                    ChannelInfo(
                                        id = channel.id,
                                        name = channel.name,
                                        isPrivate = channel.isPrivate,
                                    )
                                } ?: emptyList(),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            ResponseEntity.ok(
                WorkspaceInfoResponse(ok = false, error = e.message ?: "알 수 없는 오류"),
            )
        }

    @PostMapping("/slack/collect-ajax")
    @ResponseBody
    fun collectMessagesAjax(
        @RequestParam botToken: String,
        @RequestParam workspaceId: String,
        @RequestParam channelId: String,
        @RequestParam(required = false) oldest: String?,
        @RequestParam(required = false) latest: String?,
    ): ResponseEntity<CollectMessagesResponse> =
        try {
            val result =
                slackConversationsBatchService.collectMessagesWithToken(
                    botToken = botToken,
                    workspaceId = workspaceId,
                    channelId = channelId,
                    oldest = oldest,
                    latest = latest,
                )
            ResponseEntity.ok(
                CollectMessagesResponse(
                    messageCount = result.totalCount,
                    successCount = result.successCount,
                    failureCount = result.failureCount,
                ),
            )
        } catch (e: Exception) {
            ResponseEntity.ok(
                CollectMessagesResponse(error = e.message ?: "수집 중 오류 발생"),
            )
        }

    @GetMapping("/source-documents/api")
    @ResponseBody
    fun getSourceDocuments(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<SourceDocumentsResponse> {
        val documentsPage = sourceDocumentService.getDocuments(page, size)
        return ResponseEntity.ok(
            SourceDocumentsResponse(
                documents =
                    documentsPage.documents.map { doc ->
                        SourceDocumentDto(
                            id = doc.id ?: "",
                            content = doc.content,
                            metadata =
                                MetadataDto(
                                    source = doc.metadata.source,
                                ),
                            timestamp = doc.timestamp.toString(),
                        )
                    },
                totalElements = documentsPage.totalElements,
                totalPages = documentsPage.totalPages,
                currentPage = documentsPage.currentPage,
                hasNext = documentsPage.hasNext,
                hasPrevious = documentsPage.hasPrevious,
            ),
        )
    }

    data class ReindexResponse(
        val success: Boolean,
        val previousIndex: String? = null,
        val newIndex: String? = null,
        val documentCount: Long = 0,
        val userDictionarySize: Int = 0,
        val synonymRulesSize: Int = 0,
        val error: String? = null,
    )

    data class ReindexStatusResponse(
        val reindexingRequired: Boolean,
    )

    data class WorkspaceInfoResponse(
        val ok: Boolean,
        val error: String? = null,
        val workspaceId: String? = null,
        val workspaceName: String? = null,
        val channels: List<ChannelInfo> = emptyList(),
    )

    data class ChannelInfo(
        val id: String,
        val name: String,
        val isPrivate: Boolean,
    )

    data class CollectMessagesResponse(
        val messageCount: Int = 0,
        val successCount: Int = 0,
        val failureCount: Int = 0,
        val error: String? = null,
    )

    data class SourceDocumentsResponse(
        val documents: List<SourceDocumentDto>,
        val totalElements: Long,
        val totalPages: Int,
        val currentPage: Int,
        val hasNext: Boolean,
        val hasPrevious: Boolean,
    )

    data class SourceDocumentDto(
        val id: String,
        val content: String,
        val metadata: MetadataDto,
        val timestamp: String,
    )

    data class MetadataDto(
        val source: String,
    )
}
