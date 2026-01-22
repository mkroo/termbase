package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.candidate.CandidateStatus
import com.mkroo.termbase.domain.model.candidate.TermCandidate
import com.mkroo.termbase.domain.repository.TermCandidateRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TermCandidateReviewService(
    private val termCandidateRepository: TermCandidateRepository,
    private val glossaryService: GlossaryService,
) {
    @Transactional(readOnly = true)
    fun getPendingCandidates(
        page: Int = 0,
        size: Int = 20,
    ): Page<TermCandidate> =
        termCandidateRepository.findByStatusOrderByRelevanceScoreDesc(
            CandidateStatus.PENDING,
            PageRequest.of(page, size),
        )

    @Transactional(readOnly = true)
    fun findById(id: Long): TermCandidate? = termCandidateRepository.findById(id)

    @Transactional(readOnly = true)
    fun findByTerm(term: String): TermCandidate? = termCandidateRepository.findByTerm(term)

    fun approve(
        candidateId: Long,
        reviewer: String,
        definition: String,
    ): CandidateReviewResult {
        val candidate =
            termCandidateRepository.findById(candidateId)
                ?: return CandidateReviewResult.NotFound(candidateId)

        if (!candidate.isPending) {
            return CandidateReviewResult.AlreadyReviewed(candidateId, candidate.status)
        }

        val termAddResult = glossaryService.addTerm(candidate.term, definition)

        return when (termAddResult) {
            is TermAddResult.Success -> {
                candidate.approve(reviewer)
                termCandidateRepository.save(candidate)
                CandidateReviewResult.Approved(candidate)
            }
            is TermAddResult.AlreadyExists -> {
                CandidateReviewResult.TermAlreadyExists(candidate.term)
            }
            is TermAddResult.AlreadyExistsAsSynonym -> {
                CandidateReviewResult.TermAlreadyExistsAsSynonym(candidate.term)
            }
            is TermAddResult.AlreadyExistsAsIgnored -> {
                CandidateReviewResult.TermAlreadyExistsAsIgnored(candidate.term)
            }
            is TermAddResult.ConflictWithExistingTerms -> {
                CandidateReviewResult.ConflictWithExistingTerms(
                    candidate.term,
                    termAddResult.conflictingTerms,
                )
            }
        }
    }

    fun reject(
        candidateId: Long,
        reviewer: String,
    ): CandidateReviewResult {
        val candidate =
            termCandidateRepository.findById(candidateId)
                ?: return CandidateReviewResult.NotFound(candidateId)

        if (!candidate.isPending) {
            return CandidateReviewResult.AlreadyReviewed(candidateId, candidate.status)
        }

        candidate.reject(reviewer)
        termCandidateRepository.save(candidate)

        return CandidateReviewResult.Rejected(candidate)
    }

    @Transactional(readOnly = true)
    fun getStatistics(): CandidateStatistics =
        CandidateStatistics(
            pending = termCandidateRepository.countByStatus(CandidateStatus.PENDING),
            approved = termCandidateRepository.countByStatus(CandidateStatus.APPROVED),
            rejected = termCandidateRepository.countByStatus(CandidateStatus.REJECTED),
        )
}

sealed interface CandidateReviewResult {
    data class Approved(
        val candidate: TermCandidate,
    ) : CandidateReviewResult

    data class Rejected(
        val candidate: TermCandidate,
    ) : CandidateReviewResult

    data class NotFound(
        val candidateId: Long,
    ) : CandidateReviewResult

    data class AlreadyReviewed(
        val candidateId: Long,
        val currentStatus: CandidateStatus,
    ) : CandidateReviewResult

    data class TermAlreadyExists(
        val term: String,
    ) : CandidateReviewResult

    data class TermAlreadyExistsAsSynonym(
        val term: String,
    ) : CandidateReviewResult

    data class TermAlreadyExistsAsIgnored(
        val term: String,
    ) : CandidateReviewResult

    data class ConflictWithExistingTerms(
        val term: String,
        val conflictingTerms: List<String>,
    ) : CandidateReviewResult
}

data class CandidateStatistics(
    val pending: Long,
    val approved: Long,
    val rejected: Long,
) {
    val total: Long get() = pending + approved + rejected
}
