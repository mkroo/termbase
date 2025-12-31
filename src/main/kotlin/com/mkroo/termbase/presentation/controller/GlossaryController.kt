package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.GlossaryService
import com.mkroo.termbase.application.service.TermAddResult
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/glossary")
class GlossaryController(
    private val glossaryService: GlossaryService,
) {
    @GetMapping
    fun list(model: Model): String {
        model.addAttribute("terms", glossaryService.findAll())
        return "glossary/list"
    }

    @GetMapping("/new")
    fun newTermForm(): String = "glossary/form"

    @PostMapping
    fun createTerm(
        @RequestParam name: String,
        @RequestParam definition: String,
        redirectAttributes: RedirectAttributes,
    ): String =
        when (val result = glossaryService.addTerm(name, definition)) {
            is TermAddResult.Success -> {
                "redirect:/glossary/${result.term.name}"
            }

            is TermAddResult.AlreadyExists -> {
                redirectAttributes.addFlashAttribute("error", "이미 존재하는 용어입니다: ${result.name}")
                "redirect:/glossary/new"
            }

            is TermAddResult.AlreadyExistsAsSynonym -> {
                redirectAttributes.addFlashAttribute("error", "이미 동의어로 등록된 용어입니다: ${result.name}")
                "redirect:/glossary/new"
            }

            is TermAddResult.AlreadyExistsAsIgnored -> {
                redirectAttributes.addFlashAttribute("error", "이미 무시된 단어로 등록되어 있습니다: ${result.name}")
                "redirect:/glossary/new"
            }

            is TermAddResult.ConflictWithExistingTerms -> {
                redirectAttributes.addFlashAttribute(
                    "warning",
                    "용어가 추가되었지만, 기존 용어와 충돌이 있습니다: ${result.conflictingTerms.joinToString(", ")}",
                )
                "redirect:/glossary/${result.name}"
            }
        }

    @GetMapping("/{name}")
    fun detail(
        @PathVariable name: String,
        model: Model,
    ): String {
        val term = glossaryService.findByName(name) ?: throw TermNotFoundException(name)
        model.addAttribute("term", term)
        return "glossary/detail"
    }

    @PostMapping("/{name}/definition")
    fun updateDefinition(
        @PathVariable name: String,
        @RequestParam definition: String,
        redirectAttributes: RedirectAttributes,
    ): String {
        glossaryService.updateDefinition(name, definition)
        redirectAttributes.addFlashAttribute("success", "정의가 수정되었습니다.")
        return "redirect:/glossary/$name"
    }

    @PostMapping("/{name}/delete")
    fun deleteTerm(
        @PathVariable name: String,
        redirectAttributes: RedirectAttributes,
    ): String {
        glossaryService.deleteTerm(name)
        redirectAttributes.addFlashAttribute("success", "용어가 삭제되었습니다: $name")
        return "redirect:/glossary"
    }
}

class TermNotFoundException(
    val termName: String,
) : RuntimeException("존재하지 않는 용어입니다: $termName")
