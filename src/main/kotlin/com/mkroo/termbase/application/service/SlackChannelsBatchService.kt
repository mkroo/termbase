package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.slack.RemoteChannel
import com.mkroo.termbase.domain.model.slack.SlackChannel
import com.mkroo.termbase.domain.model.slack.SlackWorkspace
import com.mkroo.termbase.domain.repository.SlackWorkspaceRepository
import com.mkroo.termbase.infrastructure.config.SlackProperties
import com.mkroo.termbase.infrastructure.slack.SlackApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SlackChannelsBatchService(
    private val workspaceRepository: SlackWorkspaceRepository,
    private val slackApiClient: SlackApiClient,
    private val slackConversationsBatchService: SlackConversationsBatchService,
    private val slackProperties: SlackProperties,
) {
    companion object {
        private const val MAX_CONCURRENT_CHANNELS = 5

        // ES 저장 배치 최대 메모리 크기 (50MB)
        private const val MAX_BATCH_SIZE_BYTES = 50L * 1024 * 1024
    }

    /**
     * 설정된 Bot Token으로 워크스페이스 정보를 가져오고 초기화합니다.
     */
    @Transactional
    fun initializeWorkspace(): SlackWorkspace {
        val authResponse = slackApiClient.authTest(slackProperties.botToken)
        require(authResponse.ok) { "Slack auth failed: ${authResponse.error}" }

        val teamId = authResponse.teamId ?: throw IllegalStateException("Team ID not found")
        val teamName = authResponse.team ?: "Unknown"

        val workspace =
            workspaceRepository.findByTeamId(teamId)
                ?: workspaceRepository.save(
                    SlackWorkspace(
                        teamId = teamId,
                        teamName = teamName,
                    ),
                )

        return syncChannels(workspace)
    }

    /**
     * 원격 채널 목록을 동기화합니다.
     * 봇이 멤버로 참여한 채널만 표시합니다.
     */
    @Transactional
    fun syncChannels(workspace: SlackWorkspace): SlackWorkspace {
        val channelsResponse = slackApiClient.fetchAllChannels(slackProperties.botToken)
        require(channelsResponse.ok) { "Failed to fetch channels: ${channelsResponse.error}" }

        val remoteChannels =
            channelsResponse.channels
                ?.filter { it.isMember }
                ?.map { channel ->
                    RemoteChannel(
                        id = channel.id,
                        name = channel.name,
                        isPrivate = channel.isPrivate,
                    )
                } ?: emptyList()

        workspace.syncChannels(remoteChannels)
        return workspaceRepository.save(workspace)
    }

    @Transactional
    fun syncChannels(teamId: String): SlackWorkspace {
        val workspace =
            workspaceRepository.findByTeamId(teamId)
                ?: throw IllegalArgumentException("Workspace not found: $teamId")
        return syncChannels(workspace)
    }

    @Transactional
    fun updateChannelSelection(
        teamId: String,
        channelIds: List<String>,
    ): SlackWorkspace {
        val workspace =
            workspaceRepository.findByTeamId(teamId)
                ?: throw IllegalArgumentException("Workspace not found: $teamId")

        workspace.channels.forEach { channel ->
            if (channelIds.contains(channel.channelId)) {
                workspace.selectChannel(channel.channelId)
            } else {
                workspace.deselectChannel(channel.channelId)
            }
        }

        return workspaceRepository.save(workspace)
    }

    fun getWorkspace(): SlackWorkspace? = workspaceRepository.findByTeamId(slackProperties.workspaceId)

    fun collectMessages(teamId: String): CollectionResult = collectMessages(teamId, null)

    fun collectMessages(
        teamId: String,
        onProgress: ((CollectionProgress) -> Unit)?,
    ): CollectionResult {
        val workspace =
            workspaceRepository.findByTeamId(teamId)
                ?: throw IllegalArgumentException("Workspace not found: $teamId")

        val selectedChannels = workspace.selectedChannels

        if (selectedChannels.isEmpty()) {
            return CollectionResult(successCount = 0, failureCount = 0, message = "No channels selected")
        }

        val totalChannels = selectedChannels.size
        var processedChannels = 0
        var totalSuccessCount = 0
        var totalFailureCount = 0

        onProgress?.invoke(
            CollectionProgress(
                phase = "collecting",
                current = 0,
                total = totalChannels,
                currentItem = null,
                successCount = 0,
                failureCount = 0,
            ),
        )

        val semaphore = Semaphore(MAX_CONCURRENT_CHANNELS)

        runBlocking(Dispatchers.IO) {
            val jobs =
                selectedChannels.map { channel ->
                    async {
                        semaphore.withPermit {
                            collectChannelMessages(workspace.teamId, channel, onProgress) {
                                processedChannels++
                                totalSuccessCount += it.first
                                totalFailureCount += it.second

                                onProgress?.invoke(
                                    CollectionProgress(
                                        phase = "processing",
                                        current = processedChannels,
                                        total = totalChannels,
                                        currentItem = channel.name,
                                        successCount = totalSuccessCount,
                                        failureCount = totalFailureCount,
                                    ),
                                )
                            }
                        }
                    }
                }
            jobs.awaitAll()
        }

        onProgress?.invoke(
            CollectionProgress(
                phase = "completed",
                current = totalChannels,
                total = totalChannels,
                currentItem = null,
                successCount = totalSuccessCount,
                failureCount = totalFailureCount,
            ),
        )

        return CollectionResult(
            successCount = totalSuccessCount,
            failureCount = totalFailureCount,
            message = "Collection completed",
        )
    }

    private fun collectChannelMessages(
        workspaceId: String,
        channel: SlackChannel,
        onProgress: ((CollectionProgress) -> Unit)?,
        onChannelComplete: (Pair<Int, Int>) -> Unit,
    ) {
        try {
            val result =
                slackConversationsBatchService.collectIncrementalMessages(
                    workspaceId = workspaceId,
                    channelId = channel.channelId,
                )
            onChannelComplete(result.successCount to result.failureCount)
        } catch (e: Exception) {
            onChannelComplete(0 to 1)
        }
    }

    data class CollectionProgress(
        val phase: String,
        val current: Int,
        val total: Int,
        val currentItem: String?,
        val successCount: Int,
        val failureCount: Int,
    )

    data class CollectionResult(
        val successCount: Int,
        val failureCount: Int,
        val message: String,
    )
}
