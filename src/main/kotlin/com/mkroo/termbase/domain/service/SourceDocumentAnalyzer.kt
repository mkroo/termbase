package com.mkroo.termbase.domain.service

import com.mkroo.termbase.domain.model.document.TermFrequency

interface SourceDocumentAnalyzer {
    fun getTopFrequentTerms(size: Int): List<TermFrequency>
}
