package com.mkroo.termbase.domain.repository

import com.mkroo.termbase.domain.model.candidate.CandidateStatus
import com.mkroo.termbase.domain.model.candidate.TermCandidate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.Repository

interface TermCandidateRepository : Repository<TermCandidate, Long> {
    fun save(termCandidate: TermCandidate): TermCandidate

    fun <S : TermCandidate> saveAll(entities: Iterable<S>): List<S>

    fun findById(id: Long): TermCandidate?

    fun findByTerm(term: String): TermCandidate?

    fun existsByTerm(term: String): Boolean

    @org.springframework.data.jpa.repository.Query("SELECT c.term FROM TermCandidate c")
    fun findAllTerms(): List<String>

    fun findAll(): List<TermCandidate>

    fun findByStatus(status: CandidateStatus): List<TermCandidate>

    fun findByStatusOrderByRelevanceScoreDesc(
        status: CandidateStatus,
        pageable: Pageable,
    ): Page<TermCandidate>

    fun findByStatusAndTermContainingIgnoreCaseOrderByRelevanceScoreDesc(
        status: CandidateStatus,
        term: String,
        pageable: Pageable,
    ): Page<TermCandidate>

    fun deleteAll()

    fun delete(termCandidate: TermCandidate)

    fun count(): Long

    fun countByStatus(status: CandidateStatus): Long
}
