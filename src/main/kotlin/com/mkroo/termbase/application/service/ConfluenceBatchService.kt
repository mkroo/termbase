package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.confluence.ConfluencePage
import com.mkroo.termbase.domain.model.confluence.ConfluenceSpace
import com.mkroo.termbase.domain.model.confluence.ConfluenceWorkspace
import com.mkroo.termbase.domain.repository.ConfluenceWorkspaceRepository
import com.mkroo.termbase.infrastructure.config.ConfluenceProperties
import com.mkroo.termbase.infrastructure.confluence.ConfluenceApiClient
import com.mkroo.termbase.infrastructure.confluence.ConfluenceHtmlParser
import com.mkroo.termbase.infrastructure.confluence.dto.ConfluencePageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ConfluenceBatchService(
    private val workspaceRepository: ConfluenceWorkspaceRepository,
    private val apiClient: ConfluenceApiClient,
    private val htmlParser: ConfluenceHtmlParser,
    private val sourceDocumentService: SourceDocumentService,
    private val confluenceProperties: ConfluenceProperties,
) {
    companion object {
        // 동시 API 요청 수 제한 (Confluence API rate limit: 약 100-200 req/min 고려)
        private const val MAX_CONCURRENT_REQUESTS = 30

        // API 조회 배치 크기 (동시에 조회할 페이지 수)
        private const val FETCH_BATCH_SIZE = 50

        // ES 저장 배치 최대 메모리 크기 (50MB - ES 힙 제한의 약 20%)
        private const val MAX_BATCH_SIZE_BYTES = 50L * 1024 * 1024
    }

    @Transactional
    fun initializeWorkspace(): ConfluenceWorkspace {
        require(confluenceProperties.isConfigured()) { "Confluence API 설정이 필요합니다." }

        val siteId = confluenceProperties.getSiteId()
        val existingWorkspace = workspaceRepository.findBySiteId(siteId)
        if (existingWorkspace != null) {
            return syncSpaces(existingWorkspace)
        }

        val workspace =
            workspaceRepository.save(
                ConfluenceWorkspace(
                    siteId = siteId,
                    siteName = siteId,
                ),
            )

        return syncSpaces(workspace)
    }

    @Transactional
    fun syncSpaces(siteId: String): ConfluenceWorkspace {
        val workspace =
            workspaceRepository.findBySiteId(siteId)
                ?: throw IllegalArgumentException("Workspace not found: $siteId")

        return syncSpaces(workspace)
    }

    @Transactional
    fun syncSpaces(workspace: ConfluenceWorkspace): ConfluenceWorkspace {
        val remoteSpaces = apiClient.fetchAllSpaces()
        workspace.syncSpaces(remoteSpaces)
        return workspaceRepository.save(workspace)
    }

    @Transactional
    fun updateSpaceSelection(
        siteId: String,
        spaceKeys: List<String>,
    ): ConfluenceWorkspace {
        val workspace =
            workspaceRepository.findBySiteId(siteId)
                ?: throw IllegalArgumentException("Workspace not found: $siteId")

        workspace.spaces.forEach { space ->
            if (spaceKeys.contains(space.spaceKey)) {
                workspace.selectSpace(space.spaceKey)
            } else {
                workspace.deselectSpace(space.spaceKey)
            }
        }

        return workspaceRepository.save(workspace)
    }

    fun collectPages(siteId: String): CollectionResult = collectPages(siteId, null)

    fun collectPages(
        siteId: String,
        onProgress: ((CollectionProgress) -> Unit)?,
    ): CollectionResult {
        val workspace =
            workspaceRepository.findBySiteId(siteId)
                ?: throw IllegalArgumentException("Workspace not found: $siteId")

        val selectedSpaces = workspace.selectedSpaces

        if (selectedSpaces.isEmpty()) {
            return CollectionResult(successCount = 0, failureCount = 0, message = "No spaces selected")
        }

        // 1. 모든 Space에서 페이지 목록 수집 (병렬)
        val allPages = collectAllPageListsParallel(selectedSpaces)

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

        // 2. 페이지 본문 조회 및 저장 (병렬 조회 + 메모리 기반 배치 저장)
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        val pendingDocuments = mutableListOf<ConfluencePage>()
        var pendingBatchSizeBytes = 0L

        /**
         * 현재 누적된 문서들을 ES에 저장하고 버퍼를 초기화합니다.
         */
        fun flushPendingDocuments(): Pair<Int, Int> {
            if (pendingDocuments.isEmpty()) return 0 to 0

            val documents = pendingDocuments.map { it.toSourceDocument() }
            val bulkResult = sourceDocumentService.bulkInsert(documents)
            pendingDocuments.clear()
            pendingBatchSizeBytes = 0L

            return bulkResult.successCount to bulkResult.failureCount
        }

        runBlocking(Dispatchers.IO) {
            allPages.chunked(FETCH_BATCH_SIZE).forEach { batch ->
                val results =
                    batch
                        .map { (space, pageDto) ->
                            async {
                                semaphore.withPermit {
                                    fetchPageSafely(siteId, space, pageDto)
                                }
                            }
                        }.awaitAll()

                // 배치 내 성공한 페이지들을 메모리 기반으로 누적
                val successfulPages = results.filterNotNull()
                for (page in successfulPages) {
                    val estimatedSize = page.estimateSizeBytes()
                    pendingDocuments.add(page)
                    pendingBatchSizeBytes += estimatedSize

                    // 메모리 임계값 도달 시 저장
                    if (pendingBatchSizeBytes >= MAX_BATCH_SIZE_BYTES) {
                        val (success, failure) = flushPendingDocuments()
                        successCount += success
                        failureCount += failure
                    }
                }

                // 실패 카운트 추가
                failureCount += results.count { it == null }
                processedPages += batch.size

                onProgress?.invoke(
                    CollectionProgress(
                        phase = "processing",
                        current = processedPages,
                        total = totalPages,
                        currentItem = batch.lastOrNull()?.second?.title,
                        successCount = successCount,
                        failureCount = failureCount,
                    ),
                )
            }

            // 남은 문서들 저장
            val (success, failure) = flushPendingDocuments()
            successCount += success
            failureCount += failure
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

    /**
     * 모든 Space에서 페이지 목록을 병렬로 수집
     */
    private fun collectAllPageListsParallel(spaces: List<ConfluenceSpace>): List<Pair<ConfluenceSpace, ConfluencePageDto>> =
        runBlocking(Dispatchers.IO) {
            spaces
                .map { space ->
                    async {
                        val pages = apiClient.fetchAllPagesInSpace(space.spaceId)
                        pages.map { page -> space to page }
                    }
                }.awaitAll()
                .flatten()
        }

    /**
     * 페이지 본문을 안전하게 조회 (예외 발생 시 null 반환)
     */
    private fun fetchPageSafely(
        siteId: String,
        space: ConfluenceSpace,
        pageDto: ConfluencePageDto,
    ): ConfluencePage? =
        try {
            val pageWithBody = apiClient.fetchPageWithBody(pageDto.id)
            if (pageWithBody != null) {
                val plainText = htmlParser.toPlainText(pageWithBody.body?.storage?.value)
                ConfluencePage(
                    cloudId = siteId,
                    spaceKey = space.spaceKey,
                    pageId = pageDto.id,
                    title = pageDto.title,
                    content = plainText,
                    lastModified = pageWithBody.version?.createdAt ?: Instant.now(),
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
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
