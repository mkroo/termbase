package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.ConfluenceBatchService
import com.mkroo.termbase.domain.repository.ConfluenceWorkspaceRepository
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.concurrent.Executors

@Controller
@RequestMapping("/confluence")
class ConfluenceController(
    private val batchService: ConfluenceBatchService,
    private val workspaceRepository: ConfluenceWorkspaceRepository,
) {
    private val executor = Executors.newCachedThreadPool()

    @GetMapping("/settings")
    fun settings(model: Model): String {
        val workspaces = workspaceRepository.findAll()
        model.addAttribute("workspaces", workspaces)
        return "confluence/settings"
    }

    @PostMapping("/disconnect/{siteId}")
    fun disconnect(
        @PathVariable siteId: String,
        redirectAttributes: RedirectAttributes,
    ): String =
        try {
            val workspace = workspaceRepository.findBySiteId(siteId)
            if (workspace != null) {
                workspaceRepository.delete(workspace)
            }
            redirectAttributes.addFlashAttribute("success", "Confluence 연동이 해제되었습니다.")
            "redirect:/confluence/settings"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "연동 해제 중 오류가 발생했습니다: ${e.message}")
            "redirect:/confluence/settings"
        }

    @GetMapping("/spaces/{siteId}")
    fun spaces(
        @PathVariable siteId: String,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String =
        try {
            val workspace = batchService.syncSpaces(siteId)
            model.addAttribute("workspace", workspace)
            "confluence/spaces"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Space 목록 조회 중 오류가 발생했습니다: ${e.message}")
            "redirect:/confluence/settings"
        }

    @PostMapping("/spaces/{siteId}/select")
    fun selectSpaces(
        @PathVariable siteId: String,
        @RequestParam(required = false) spaceKeys: List<String>?,
        redirectAttributes: RedirectAttributes,
    ): String =
        try {
            batchService.updateSpaceSelection(siteId, spaceKeys ?: emptyList())
            redirectAttributes.addFlashAttribute("success", "Space 선택이 저장되었습니다.")
            "redirect:/confluence/spaces/$siteId"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Space 선택 저장 중 오류가 발생했습니다: ${e.message}")
            "redirect:/confluence/spaces/$siteId"
        }

    @PostMapping("/collect/{siteId}")
    @ResponseBody
    fun collectPages(
        @PathVariable siteId: String,
    ): ResponseEntity<CollectionResponse> =
        try {
            val result = batchService.collectPages(siteId)
            ResponseEntity.ok(
                CollectionResponse(
                    success = true,
                    successCount = result.successCount,
                    failureCount = result.failureCount,
                    message = result.message,
                ),
            )
        } catch (e: Exception) {
            ResponseEntity.ok(
                CollectionResponse(
                    success = false,
                    successCount = 0,
                    failureCount = 0,
                    message = "수집 중 오류가 발생했습니다: ${e.message}",
                ),
            )
        }

    @GetMapping("/collect/{siteId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun collectPagesWithProgress(
        @PathVariable siteId: String,
    ): SseEmitter {
        val emitter = SseEmitter(0L) // timeout 무제한

        executor.execute {
            try {
                batchService.collectPages(siteId) { progress ->
                    try {
                        emitter.send(
                            SseEmitter
                                .event()
                                .name("progress")
                                .data(progress, MediaType.APPLICATION_JSON),
                        )
                    } catch (e: Exception) {
                        // 클라이언트 연결 끊김
                    }
                }
                emitter.complete()
            } catch (e: Exception) {
                try {
                    emitter.send(
                        SseEmitter
                            .event()
                            .name("error")
                            .data(mapOf("error" to e.message), MediaType.APPLICATION_JSON),
                    )
                } catch (_: Exception) {
                    // ignore
                }
                emitter.completeWithError(e)
            }
        }

        return emitter
    }

    data class CollectionResponse(
        val success: Boolean,
        val successCount: Int,
        val failureCount: Int,
        val message: String,
    )
}
