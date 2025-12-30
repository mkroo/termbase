package com.mkroo.termbase.domain.service

import com.mkroo.termbase.domain.model.slack.TermFrequency

interface ConversationAnalyzer {
    fun getTopFrequentTerms(size: Int): List<TermFrequency>
}
