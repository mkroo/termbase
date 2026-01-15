package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.ConfluenceBatchService
import com.mkroo.termbase.application.service.ConfluenceOAuthService
import com.mkroo.termbase.domain.repository.ConfluenceWorkspaceRepository
import jakarta.servlet.http.HttpSession
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/confluence")
class ConfluenceController(
    private val oauthService: ConfluenceOAuthService,
    private val batchService: ConfluenceBatchService,
    private val workspaceRepository: ConfluenceWorkspaceRepository,
) {
    companion object {
        private const val OAUTH_STATE_SESSION_KEY = "confluence_oauth_state"
    }

    @GetMapping("/settings")
    fun settings(model: Model): String {
        val workspaces = workspaceRepository.findAll()
        model.addAttribute("workspaces", workspaces)
        return "confluence/settings"
    }

    @GetMapping("/oauth/authorize")
    fun authorize(session: HttpSession): String {
        val result = oauthService.buildAuthorizationUrl()
        session.setAttribute(OAUTH_STATE_SESSION_KEY, result.state)
        return "redirect:${result.url}"
    }

    @GetMapping("/oauth/callback")
    fun callback(
        @RequestParam code: String,
        @RequestParam state: String,
        session: HttpSession,
        redirectAttributes: RedirectAttributes,
    ): String {
        val expectedState = session.getAttribute(OAUTH_STATE_SESSION_KEY) as? String
        session.removeAttribute(OAUTH_STATE_SESSION_KEY)

        if (expectedState == null || expectedState != state) {
            redirectAttributes.addFlashAttribute("error", "Invalid OAuth state. Please try again.")
            return "redirect:/confluence/settings"
        }

        return try {
            oauthService.handleCallback(code, state)
            redirectAttributes.addFlashAttribute("success", "Confluence 연동이 완료되었습니다.")
            "redirect:/confluence/settings"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "연동 중 오류가 발생했습니다: ${e.message}")
            "redirect:/confluence/settings"
        }
    }

    @PostMapping("/disconnect/{cloudId}")
    fun disconnect(
        @PathVariable cloudId: String,
        redirectAttributes: RedirectAttributes,
    ): String =
        try {
            oauthService.disconnect(cloudId)
            redirectAttributes.addFlashAttribute("success", "Confluence 연동이 해제되었습니다.")
            "redirect:/confluence/settings"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "연동 해제 중 오류가 발생했습니다: ${e.message}")
            "redirect:/confluence/settings"
        }

    @GetMapping("/spaces/{cloudId}")
    fun spaces(
        @PathVariable cloudId: String,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String =
        try {
            val workspace = batchService.syncSpaces(cloudId)
            model.addAttribute("workspace", workspace)
            "confluence/spaces"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Space 목록 조회 중 오류가 발생했습니다: ${e.message}")
            "redirect:/confluence/settings"
        }

    @PostMapping("/spaces/{cloudId}/select")
    fun selectSpaces(
        @PathVariable cloudId: String,
        @RequestParam(required = false) spaceKeys: List<String>?,
        redirectAttributes: RedirectAttributes,
    ): String =
        try {
            batchService.updateSpaceSelection(cloudId, spaceKeys ?: emptyList())
            redirectAttributes.addFlashAttribute("success", "Space 선택이 저장되었습니다.")
            "redirect:/confluence/spaces/$cloudId"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Space 선택 저장 중 오류가 발생했습니다: ${e.message}")
            "redirect:/confluence/spaces/$cloudId"
        }

    @PostMapping("/collect/{cloudId}")
    @ResponseBody
    fun collectPages(
        @PathVariable cloudId: String,
    ): ResponseEntity<CollectionResponse> =
        try {
            val result = batchService.collectPages(cloudId)
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

    data class CollectionResponse(
        val success: Boolean,
        val successCount: Int,
        val failureCount: Int,
        val message: String,
    )
}
