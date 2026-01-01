package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.document.TermFrequency
import com.mkroo.termbase.domain.service.SourceDocumentAnalyzer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TermCandidateService(
    private val sourceDocumentAnalyzer: SourceDocumentAnalyzer,
) {
    fun getTermCandidates(size: Int = 100): List<TermFrequency> = sourceDocumentAnalyzer.getTopFrequentTerms(size)

    fun searchTermCandidates(
        query: String,
        size: Int = 100,
    ): List<TermFrequency> =
        getTermCandidates(size)
            .filter { it.term.contains(query, ignoreCase = true) }
}
