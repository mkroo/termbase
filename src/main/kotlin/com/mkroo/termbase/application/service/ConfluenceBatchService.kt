package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.confluence.ConfluencePage
import com.mkroo.termbase.domain.model.confluence.ConfluenceWorkspace
import com.mkroo.termbase.domain.repository.ConfluenceWorkspaceRepository
import com.mkroo.termbase.infrastructure.confluence.ConfluenceApiClient
import com.mkroo.termbase.infrastructure.confluence.ConfluenceHtmlParser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ConfluenceBatchService(
    private val workspaceRepository: ConfluenceWorkspaceRepository,
    private val apiClient: ConfluenceApiClient,
    private val tokenRefresher: ConfluenceTokenRefresher,
    private val htmlParser: ConfluenceHtmlParser,
    private val sourceDocumentService: SourceDocumentService,
) {
    @Transactional
    fun syncSpaces(cloudId: String): ConfluenceWorkspace {
        val workspace =
            workspaceRepository.findByCloudId(cloudId)
                ?: throw IllegalArgumentException("Workspace not found: $cloudId")

        val refreshedWorkspace = tokenRefresher.ensureValidToken(workspace)
        val remoteSpaces = apiClient.fetchAllSpaces(cloudId, refreshedWorkspace.accessToken)
        refreshedWorkspace.syncSpaces(remoteSpaces)

        return workspaceRepository.save(refreshedWorkspace)
    }

    @Transactional
    fun updateSpaceSelection(
        cloudId: String,
        spaceKeys: List<String>,
    ): ConfluenceWorkspace {
        val workspace =
            workspaceRepository.findByCloudId(cloudId)
                ?: throw IllegalArgumentException("Workspace not found: $cloudId")

        workspace.spaces.forEach { space ->
            if (spaceKeys.contains(space.spaceKey)) {
                workspace.selectSpace(space.spaceKey)
            } else {
                workspace.deselectSpace(space.spaceKey)
            }
        }

        return workspaceRepository.save(workspace)
    }

    fun collectPages(cloudId: String): CollectionResult = collectPages(cloudId, null)

    fun collectPages(
        cloudId: String,
        onProgress: ((CollectionProgress) -> Unit)?,
    ): CollectionResult {
        val workspace =
            workspaceRepository.findByCloudId(cloudId)
                ?: throw IllegalArgumentException("Workspace not found: $cloudId")

        val refreshedWorkspace = tokenRefresher.ensureValidToken(workspace)
        val selectedSpaces = refreshedWorkspace.selectedSpaces

        if (selectedSpaces.isEmpty()) {
            return CollectionResult(successCount = 0, failureCount = 0, message = "No spaces selected")
        }

        // 먼저 전체 페이지 수를 계산
        val allPages =
            selectedSpaces.flatMap { space ->
                apiClient
                    .fetchAllPagesInSpace(cloudId, space.spaceId, refreshedWorkspace.accessToken)
                    .map { page -> space to page }
            }

        val totalPages = allPages.size
        var processedPages = 0
        var successCount = 0
        var failureCount = 0

        onProgress?.invoke(
            CollectionProgress(
                phase = "collecting",
                current = 0,
                total = totalPages,
                currentItem = null,
                successCount = 0,
                failureCount = 0,
            ),
        )

        allPages.forEach { (space, pageDto) ->
            try {
                onProgress?.invoke(
                    CollectionProgress(
                        phase = "processing",
                        current = processedPages,
                        total = totalPages,
                        currentItem = pageDto.title,
                        successCount = successCount,
                        failureCount = failureCount,
                    ),
                )

                val pageWithBody = apiClient.fetchPageWithBody(cloudId, pageDto.id, refreshedWorkspace.accessToken)
                if (pageWithBody != null) {
                    val plainText = htmlParser.toPlainText(pageWithBody.body?.storage?.value)
                    val page =
                        ConfluencePage(
                            cloudId = cloudId,
                            spaceKey = space.spaceKey,
                            pageId = pageDto.id,
                            title = pageDto.title,
                            content = plainText,
                            lastModified = pageWithBody.version?.createdAt ?: Instant.now(),
                        )
                    sourceDocumentService.saveDocument(page.toSourceDocument())
                    successCount++
                } else {
                    failureCount++
                }
            } catch (e: Exception) {
                failureCount++
            }
            processedPages++
        }

        onProgress?.invoke(
            CollectionProgress(
                phase = "completed",
                current = totalPages,
                total = totalPages,
                currentItem = null,
                successCount = successCount,
                failureCount = failureCount,
            ),
        )

        return CollectionResult(
            successCount = successCount,
            failureCount = failureCount,
            message = "Collection completed",
        )
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
