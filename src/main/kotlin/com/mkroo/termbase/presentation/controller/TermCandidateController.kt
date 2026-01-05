package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.GlossaryService
import com.mkroo.termbase.application.service.IgnoredTermAddResult
import com.mkroo.termbase.application.service.IgnoredTermService
import com.mkroo.termbase.application.service.TermAddResult
import com.mkroo.termbase.application.service.TermCandidateService
import com.mkroo.termbase.domain.model.document.TimeSeriesInterval
import com.mkroo.termbase.domain.service.SourceDocumentAnalyzer
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/candidates")
class TermCandidateController(
    private val termCandidateService: TermCandidateService,
    private val glossaryService: GlossaryService,
    private val ignoredTermService: IgnoredTermService,
    private val sourceDocumentAnalyzer: SourceDocumentAnalyzer,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        model: Model,
    ): String {
        val candidates =
            if (q.isNullOrBlank()) {
                termCandidateService.getTermCandidates()
            } else {
                termCandidateService.searchTermCandidates(q)
            }

        model.addAttribute("candidates", candidates)
        model.addAttribute("query", q ?: "")
        return "candidates/list"
    }

    @GetMapping("/detail")
    fun detail(
        @RequestParam name: String,
        @RequestParam(defaultValue = "week") interval: String,
        @RequestParam(defaultValue = "10") docSize: Int,
        model: Model,
    ): String {
        val timeSeriesInterval =
            when (interval.lowercase()) {
                "day" -> TimeSeriesInterval.DAY
                "month" -> TimeSeriesInterval.MONTH
                else -> TimeSeriesInterval.WEEK
            }
        val days =
            when (timeSeriesInterval) {
                TimeSeriesInterval.DAY -> 30
                TimeSeriesInterval.WEEK -> 12 * 7
                TimeSeriesInterval.MONTH -> 365
            }

        val timeSeries = sourceDocumentAnalyzer.getTermFrequencyTimeSeries(name, timeSeriesInterval, days)
        val documents = sourceDocumentAnalyzer.searchDocumentsByTerm(name, docSize.coerceIn(1, 100))
        val totalFrequency = sourceDocumentAnalyzer.getTermFrequency(name)

        model.addAttribute("termName", name)
        model.addAttribute("timeSeries", timeSeries)
        model.addAttribute("documents", documents)
        model.addAttribute("totalFrequency", totalFrequency)
        model.addAttribute("interval", interval)
        model.addAttribute("docSize", docSize)
        model.addAttribute("docSizeOptions", listOf(10, 20, 50, 100))
        return "candidates/detail"
    }

    @PostMapping
    fun registerTerm(
        @RequestParam name: String,
        @RequestParam definition: String,
        @RequestParam(required = false) q: String?,
        redirectAttributes: RedirectAttributes,
    ): String {
        val redirectUrl = if (q.isNullOrBlank()) "/candidates" else "/candidates?q=$q"

        when (val result = glossaryService.addTerm(name, definition)) {
            is TermAddResult.Success -> {
                redirectAttributes.addFlashAttribute("success", "용어가 등록되었습니다: ${result.term.name}")
            }

            is TermAddResult.AlreadyExists -> {
                redirectAttributes.addFlashAttribute("error", "이미 존재하는 용어입니다: ${result.name}")
            }

            is TermAddResult.AlreadyExistsAsSynonym -> {
                redirectAttributes.addFlashAttribute("error", "이미 동의어로 등록된 용어입니다: ${result.name}")
            }

            is TermAddResult.AlreadyExistsAsIgnored -> {
                redirectAttributes.addFlashAttribute("error", "이미 무시된 단어로 등록되어 있습니다: ${result.name}")
            }

            is TermAddResult.ConflictWithExistingTerms -> {
                redirectAttributes.addFlashAttribute(
                    "warning",
                    "용어가 추가되었지만, 기존 용어와 충돌이 있습니다: ${result.conflictingTerms.joinToString(", ")}",
                )
            }
        }

        return "redirect:$redirectUrl"
    }

    @PostMapping("/ignore")
    fun ignoreTerm(
        @RequestParam name: String,
        @RequestParam reason: String,
        @RequestParam(required = false) q: String?,
        redirectAttributes: RedirectAttributes,
    ): String {
        val redirectUrl = if (q.isNullOrBlank()) "/candidates" else "/candidates?q=$q"

        when (val result = ignoredTermService.addIgnoredTerm(name, reason)) {
            is IgnoredTermAddResult.Success -> {
                redirectAttributes.addFlashAttribute("success", "무시 처리되었습니다: ${result.ignoredTerm.name}")
            }

            is IgnoredTermAddResult.AlreadyExistsAsTerm -> {
                redirectAttributes.addFlashAttribute("error", "이미 용어 사전에 등록된 단어입니다: ${result.name}")
            }

            is IgnoredTermAddResult.AlreadyExistsAsSynonym -> {
                redirectAttributes.addFlashAttribute("error", "이미 동의어로 등록된 단어입니다: ${result.name}")
            }

            is IgnoredTermAddResult.AlreadyIgnored -> {
                redirectAttributes.addFlashAttribute("error", "이미 무시 처리된 단어입니다: ${result.name}")
            }

            is IgnoredTermAddResult.ReasonRequired -> {
                redirectAttributes.addFlashAttribute("error", "무시 사유를 입력해주세요.")
            }
        }

        return "redirect:$redirectUrl"
    }
}
