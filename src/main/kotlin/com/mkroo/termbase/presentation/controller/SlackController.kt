package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.SlackChannelsBatchService
import com.mkroo.termbase.domain.repository.SlackWorkspaceRepository
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
@RequestMapping("/slack")
class SlackController(
    private val batchService: SlackChannelsBatchService,
    private val workspaceRepository: SlackWorkspaceRepository,
) {
    private val executor = Executors.newCachedThreadPool()

    @GetMapping("/settings")
    fun settings(model: Model): String {
        val workspaces = workspaceRepository.findAll()
        model.addAttribute("workspaces", workspaces)
        return "slack/settings"
    }

    @PostMapping("/disconnect/{teamId}")
    fun disconnect(
        @PathVariable teamId: String,
        redirectAttributes: RedirectAttributes,
    ): String =
        try {
            val workspace = workspaceRepository.findByTeamId(teamId)
            if (workspace != null) {
                workspaceRepository.delete(workspace)
            }
            redirectAttributes.addFlashAttribute("success", "Slack 연동이 해제되었습니다.")
            "redirect:/slack/settings"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "연동 해제 중 오류가 발생했습니다: ${e.message}")
            "redirect:/slack/settings"
        }

    @GetMapping("/channels/{teamId}")
    fun channels(
        @PathVariable teamId: String,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String =
        try {
            val workspace = batchService.syncChannels(teamId)
            model.addAttribute("workspace", workspace)
            "slack/channels"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "채널 목록 조회 중 오류가 발생했습니다: ${e.message}")
            "redirect:/slack/settings"
        }

    @PostMapping("/channels/{teamId}/select")
    fun selectChannels(
        @PathVariable teamId: String,
        @RequestParam(required = false) channelIds: List<String>?,
        redirectAttributes: RedirectAttributes,
    ): String =
        try {
            batchService.updateChannelSelection(teamId, channelIds ?: emptyList())
            redirectAttributes.addFlashAttribute("success", "채널 선택이 저장되었습니다.")
            "redirect:/slack/channels/$teamId"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "채널 선택 저장 중 오류가 발생했습니다: ${e.message}")
            "redirect:/slack/channels/$teamId"
        }

    @PostMapping("/collect/{teamId}")
    @ResponseBody
    fun collectMessages(
        @PathVariable teamId: String,
    ): ResponseEntity<CollectionResponse> =
        try {
            val result = batchService.collectMessages(teamId)
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

    @GetMapping("/collect/{teamId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun collectMessagesWithProgress(
        @PathVariable teamId: String,
    ): SseEmitter {
        val emitter = SseEmitter(0L) // timeout 무제한

        executor.execute {
            try {
                batchService.collectMessages(teamId) { progress ->
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
