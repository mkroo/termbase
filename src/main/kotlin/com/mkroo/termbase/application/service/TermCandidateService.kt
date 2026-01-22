package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.candidate.CandidateStatus
import com.mkroo.termbase.domain.model.document.TermFrequency
import com.mkroo.termbase.domain.repository.TermCandidateRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TermCandidateService(
    private val termCandidateRepository: TermCandidateRepository,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }

    fun getTermCandidates(
        page: Int = 0,
        size: Int = DEFAULT_PAGE_SIZE,
    ): Page<TermFrequency> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "relevanceScore"))
        return termCandidateRepository
            .findByStatusOrderByRelevanceScoreDesc(CandidateStatus.PENDING, pageable)
            .map { candidate ->
                TermFrequency(
                    term = candidate.term,
                    count = candidate.count.toLong(),
                    score = candidate.relevanceScore.toDouble(),
                )
            }
    }

    fun searchTermCandidates(
        query: String,
        page: Int = 0,
        size: Int = DEFAULT_PAGE_SIZE,
    ): Page<TermFrequency> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "relevanceScore"))
        return termCandidateRepository
            .findByStatusAndTermContainingIgnoreCaseOrderByRelevanceScoreDesc(
                CandidateStatus.PENDING,
                query,
                pageable,
            ).map { candidate ->
                TermFrequency(
                    term = candidate.term,
                    count = candidate.count.toLong(),
                    score = candidate.relevanceScore.toDouble(),
                )
            }
    }
}
