package com.mkroo.termbase.domain.service

import com.mkroo.termbase.domain.model.document.HighlightedSourceDocument
import com.mkroo.termbase.domain.model.document.TermFrequency
import com.mkroo.termbase.domain.model.document.TimeSeriesFrequency
import com.mkroo.termbase.domain.model.document.TimeSeriesInterval

interface SourceDocumentAnalyzer {
    fun getTopFrequentTerms(size: Int): List<TermFrequency>

    fun getTermFrequency(term: String): Long

    fun getTermFrequencyTimeSeries(
        term: String,
        interval: TimeSeriesInterval,
        days: Int = 30,
    ): List<TimeSeriesFrequency>

    fun searchDocumentsByTerm(
        term: String,
        size: Int = 10,
    ): List<HighlightedSourceDocument>
}
