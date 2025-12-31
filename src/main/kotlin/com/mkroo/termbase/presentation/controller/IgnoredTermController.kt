package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.IgnoredTermAddResult
import com.mkroo.termbase.application.service.IgnoredTermService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/ignored")
class IgnoredTermController(
    private val ignoredTermService: IgnoredTermService,
) {
    @GetMapping
    fun list(model: Model): String {
        model.addAttribute("ignoredTerms", ignoredTermService.findAll())
        return "ignored/list"
    }

    @GetMapping("/new")
    fun newIgnoredTermForm(): String = "ignored/form"

    @PostMapping
    fun createIgnoredTerm(
        @RequestParam name: String,
        @RequestParam reason: String,
        redirectAttributes: RedirectAttributes,
    ): String =
        when (val result = ignoredTermService.addIgnoredTerm(name, reason)) {
            is IgnoredTermAddResult.Success -> {
                redirectAttributes.addFlashAttribute("success", "무시된 단어가 추가되었습니다: ${result.ignoredTerm.name}")
                "redirect:/ignored"
            }

            is IgnoredTermAddResult.ReasonRequired -> {
                redirectAttributes.addFlashAttribute("error", "무시 사유는 필수입니다.")
                "redirect:/ignored/new"
            }

            is IgnoredTermAddResult.AlreadyExistsAsTerm -> {
                redirectAttributes.addFlashAttribute("error", "이미 용어로 등록된 단어입니다: ${result.name}")
                "redirect:/ignored/new"
            }

            is IgnoredTermAddResult.AlreadyExistsAsSynonym -> {
                redirectAttributes.addFlashAttribute("error", "이미 동의어로 등록된 단어입니다: ${result.name}")
                "redirect:/ignored/new"
            }

            is IgnoredTermAddResult.AlreadyIgnored -> {
                redirectAttributes.addFlashAttribute("error", "이미 무시된 단어입니다: ${result.name}")
                "redirect:/ignored/new"
            }
        }

    @GetMapping("/{name}")
    fun detail(
        @PathVariable name: String,
        model: Model,
    ): String {
        val ignoredTerm = ignoredTermService.findByName(name) ?: throw IgnoredTermNotFoundException(name)
        model.addAttribute("ignoredTerm", ignoredTerm)
        return "ignored/detail"
    }

    @PostMapping("/{name}/reason")
    fun updateReason(
        @PathVariable name: String,
        @RequestParam reason: String,
        redirectAttributes: RedirectAttributes,
    ): String {
        ignoredTermService.updateReason(name, reason)
        redirectAttributes.addFlashAttribute("success", "무시 사유가 수정되었습니다.")
        return "redirect:/ignored/$name"
    }

    @PostMapping("/{name}/delete")
    fun deleteIgnoredTerm(
        @PathVariable name: String,
        redirectAttributes: RedirectAttributes,
    ): String {
        ignoredTermService.removeIgnoredTerm(name)
        redirectAttributes.addFlashAttribute("success", "무시된 단어가 삭제되었습니다: $name")
        return "redirect:/ignored"
    }
}

class IgnoredTermNotFoundException(
    val termName: String,
) : RuntimeException("무시 처리되지 않은 단어입니다: $termName")
